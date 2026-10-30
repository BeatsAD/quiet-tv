package home.quiet.tv;

import android.app.Service;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.Surface;
import java.io.IOException;

/** Private, bound-only codec process. The service main Looper never calls MediaPlayer. */
public class LegacyWallpaperService extends Service {
    static final int OPEN=1,SOUND=2,PAUSE=3,RELEASE=4;
    static final int PREPARED=11,FRAME=12,SIZE=13,FAILED=14,RELEASED=15,NOTE=16;
    static final long OPERATION_TIMEOUT_MS=2500;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Handler worker;
    private HandlerThread thread;
    private Messenger endpoint;
    private volatile Messenger reply;
    private volatile int session;
    private boolean terminating,destroyed;
    // Only the codec Looper owns these fields.
    private MediaPlayer player;
    private Surface output;
    private boolean prepared,paused,sound,focus;
    private AudioManager audio;
    private AudioManager.OnAudioFocusChangeListener audioFocus;
    @Override public void onCreate(){
        super.onCreate();thread=new HandlerThread("QuietTV-legacy-codec");thread.start();worker=new Handler(thread.getLooper());
        endpoint=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message message){receive(message);}});
    }
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
    private void receive(Message message){
        Bundle data=message.getData();data.setClassLoader(Surface.class.getClassLoader());
        if(terminating||destroyed){closeInput(data);return;}
        if(message.what==OPEN){
            if(session!=0){closeInput(data);return;}
            session=message.arg1;reply=message.replyTo;
        }
        if(message.arg1!=session){closeInput(data);return;}
        switch(message.what){
            case OPEN:guarded("open",()->open(data));break;
            case SOUND:guarded("sound",()->{sound=data.getBoolean("enabled");updateFocus();});break;
            case PAUSE:guarded("pause",()->{paused=data.getBoolean("enabled");updateFocus();if(prepared){if(paused)player.pause();else player.start();}});break;
            case RELEASE:guarded("release",()->{releasePlayer();main.post(()->{
                send(RELEASED,0,null);session=0;reply=null;
            });});break;
        }
    }
    private interface CodecCall {void run()throws IOException;}
    private void guarded(String phase,CodecCall call){
        // Serialize submissions with onDestroy; late callbacks cannot arm a watchdog on a quit Looper.
        if(Looper.myLooper()!=main.getLooper()){main.post(()->guarded(phase,call));return;}
        if(terminating||destroyed&&!"destroy".equals(phase))return;
        Runnable deadline=()->terminateProcess(phase+"_timeout");
        main.postDelayed(deadline,OPERATION_TIMEOUT_MS);
        worker.post(()->{
            boolean finished=false;
            try{call.run();finished=true;}
            catch(IOException|RuntimeException e){
                Bundle detail=new Bundle();detail.putString("phase",phase+"_failed");send(NOTE,0,detail);
                send(FAILED,(e instanceof IOException?VideoWallpaperPlayer.Failure.FILE:VideoWallpaperPlayer.Failure.DECODE).ordinal(),null);
                main.post(()->terminateProcess(phase+"_failed"));
            }finally{if(finished)main.removeCallbacks(deadline);}
        });
    }
    private void open(Bundle data)throws IOException{
        output=data.getParcelable("surface");ParcelFileDescriptor input=data.getParcelable("file");
        try{
            if(output==null||!output.isValid()||input==null)throw new IOException("Missing playback input");
            player=new MediaPlayer();audio=(AudioManager)getSystemService(AUDIO_SERVICE);
            MediaPlayer owner=player;
            audioFocus=change->guarded("audio_focus",()->{if(player==owner){focus=change==AudioManager.AUDIOFOCUS_GAIN;volume();}});
            player.setSurface(output);player.setScreenOnWhilePlaying(false);player.setLooping(true);
            sound=data.getBoolean("sound");paused=data.getBoolean("paused");updateFocus();
            player.setOnPreparedListener(p->{
                // A prepared decoder is not a rendered frame on API17+.
                if(p==player)guarded("start",()->{if(p!=player)return;prepared=true;if(!paused)p.start();send(PREPARED,0,null);});
            });
            player.setOnInfoListener((p,what,extra)->{
                if(p==player&&Build.VERSION.SDK_INT>=17&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)send(FRAME,0,null);
                return false;
            });
            player.setOnVideoSizeChangedListener((p,width,height)->{if(p!=player)return;Bundle size=new Bundle();size.putInt("width",width);size.putInt("height",height);send(SIZE,0,size);});
            player.setOnErrorListener((p,what,extra)->{
                if(p!=player)return true;
                VideoWallpaperPlayer.Failure reason=extra==MediaPlayer.MEDIA_ERROR_UNSUPPORTED?VideoWallpaperPlayer.Failure.UNSUPPORTED:
                    extra==MediaPlayer.MEDIA_ERROR_IO?VideoWallpaperPlayer.Failure.FILE:
                    extra==MediaPlayer.MEDIA_ERROR_TIMED_OUT?VideoWallpaperPlayer.Failure.TIMEOUT:VideoWallpaperPlayer.Failure.DECODE;
                Bundle detail=new Bundle();detail.putString("phase","player_error_"+what+"_"+extra);send(NOTE,0,detail);
                send(FAILED,reason.ordinal(),null);return true;
            });
            player.setDataSource(input.getFileDescriptor(),data.getLong("offset"),data.getLong("length"));player.prepareAsync();
        }finally{closeInput(data);}
    }
    private void volume(){if(player!=null)player.setVolume(sound&&focus&&!paused?1:0,sound&&focus&&!paused?1:0);}
    private void updateFocus(){
        if(sound&&!paused&&!focus)focus=audio!=null&&audio.requestAudioFocus(audioFocus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if((!sound||paused)&&focus){if(audio!=null)audio.abandonAudioFocus(audioFocus);focus=false;}
        volume();
    }
    private void releasePlayer(){
        if(audio!=null)audio.abandonAudioFocus(audioFocus);focus=false;
        if(player!=null){player.setOnPreparedListener(null);player.setOnInfoListener(null);player.setOnVideoSizeChangedListener(null);player.setOnErrorListener(null);player.release();player=null;}
        prepared=false;
        if(output!=null){output.release();output=null;}
    }
    private void send(int what,int reason,Bundle data){
        Messenger callback=reply;if(callback==null)return;
        Message message=Message.obtain(null,what,session,reason);if(data!=null)message.setData(data);
        try{callback.send(message);}catch(RemoteException e){main.post(()->terminateProcess("client_died"));}
    }
    protected void terminateProcess(String reason){
        if(terminating)return;terminating=true;
        Bundle detail=new Bundle();detail.putString("phase",reason);send(NOTE,0,detail);
        // Only this manifest-private playback process is terminated. The HOME process stays alive.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    private static void closeInput(Bundle data){
        ParcelFileDescriptor input=data.getParcelable("file");if(input!=null){try{input.close();}catch(IOException ignored){}data.remove("file");}
    }
    @Override public void onDestroy(){
        destroyed=true;
        guarded("destroy",()->{releasePlayer();send(RELEASED,0,null);thread.quit();});super.onDestroy();
    }
}
