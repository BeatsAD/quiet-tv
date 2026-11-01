package home.quiet.tv;

import android.app.Service;
import android.content.*;
import android.media.*;
import android.os.*;
import android.view.Surface;
import java.io.IOException;

/** Private, bound-only codec process. The service main Looper never calls MediaPlayer. */
public class WallpaperService extends Service {
    static final int OPEN=1,SOUND=2,PAUSE=3,RELEASE=4;
    static final int PREPARED=11,FRAME=12,SIZE=13,FAILED=14,RELEASED=15,NOTE=16,LOG=17,SOFTWARE_AVAILABLE=18,RETRY_SOFTWARE=19;
    static final long OPERATION_TIMEOUT_MS=2500;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Handler worker;
    private HandlerThread thread;
    private Messenger endpoint;
    private volatile Messenger reply;
    private volatile int session;
    private boolean terminating,destroyed,releasing;
    // Only the codec Looper owns these fields.
    interface SoftwarePlayer {
        void prepare();void setSound(boolean sound);void setVolume(float value);void setPaused(boolean paused);void release(Runnable completed);
    }
    interface SoftwareDiagnostics {void record(boolean error,String event,String detail);}
    private MediaPlayer player;
    private SoftwarePlayer software;
    private boolean softwareAvailable;
    @androidx.annotation.ChecksSdkIntAtLeast(api=16,parameter=0) static boolean softwareSupported(int sdk){return sdk>=16;}
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
        if(terminating||destroyed||releasing){closeInput(data);return;}
        if(message.what==OPEN){
            if(session!=0){closeInput(data);return;}
            session=message.arg1;reply=message.replyTo;
        }
        if(message.arg1!=session){closeInput(data);return;}
        switch(message.what){
            case OPEN:guarded("open",()->open(data));break;
            case SOUND:guarded("sound",()->{sound=data.getBoolean("enabled");if(software!=null)software.setSound(sound);updateFocus();});break;
            case PAUSE:guarded("pause",()->{paused=data.getBoolean("enabled");updateFocus();if(software!=null)software.setPaused(paused);else if(prepared){if(paused)player.pause();else player.start();}});break;
            case RELEASE:
                releasing=true;
                guardedAsync("release",done->releasePlayer(()->main.post(()->{
                    send(RELEASED,0,null);session=0;reply=null;releasing=false;done.run();
                })));break;
        }
    }
    private interface CodecCall {void run()throws IOException;}
    private void guarded(String phase,CodecCall call){guardedAsync(phase,done->{call.run();done.run();});}
    private interface AsyncCodecCall {void run(Runnable completed)throws IOException;}
    private void guardedAsync(String phase,AsyncCodecCall call){
        if(Looper.myLooper()!=main.getLooper()){main.post(()->guardedAsync(phase,call));return;}
        if(terminating||destroyed&&!"destroy".equals(phase))return;
        Runnable deadline=()->terminateProcess(phase+"_timeout");main.postDelayed(deadline,OPERATION_TIMEOUT_MS);
        worker.post(()->{
            try{call.run(()->main.removeCallbacks(deadline));}
            catch(IOException|RuntimeException e){
                Bundle detail=new Bundle();detail.putString("phase",phase+"_failed");send(NOTE,0,detail);
                send(FAILED,(e instanceof IOException?VideoWallpaperPlayer.Failure.FILE:VideoWallpaperPlayer.Failure.DECODE).ordinal(),null);
                main.post(()->terminateProcess(phase+"_failed"));
            }
        });
    }
    private void open(Bundle data)throws IOException{
        output=data.getParcelable("surface");ParcelFileDescriptor input=data.getParcelable("file");
        try{
            if(output==null||!output.isValid()||input==null)throw new IOException("Missing playback input");
            audio=(AudioManager)getSystemService(AUDIO_SERVICE);sound=data.getBoolean("sound");paused=data.getBoolean("paused");softwareAvailable=false;
            final int openedSession=session;
            if(data.getBoolean("softwareOnly")){
                if(!softwareSupported(Build.VERSION.SDK_INT)){send(FAILED,VideoWallpaperPlayer.Failure.UNSUPPORTED.ordinal(),null);return;}
                software=Api16.create(this,worker,output,data,new VideoWallpaperPlayer.PlaybackEvents(){
                    @Override public void ready(){send(openedSession,FRAME,0,null);}
                    @Override public void failed(VideoWallpaperPlayer.Failure reason){send(openedSession,FAILED,reason.ordinal(),null);}
                    @Override public void size(int width,int height,float ratio){Bundle size=new Bundle();size.putInt("width",width);size.putInt("height",height);size.putFloat("pixelRatio",ratio);send(openedSession,SIZE,0,size);}
                },(error,event,detail)->diagnostic(openedSession,error,event,detail));
                SoftwarePlayer owner=software;
                audioFocus=change->guarded("audio_focus",()->{if(software==owner){focus=change==AudioManager.AUDIOFOCUS_GAIN;volume();}});
                software.setSound(sound);software.setPaused(paused);updateFocus();software.prepare();return;
            }
            if(softwareSupported(Build.VERSION.SDK_INT))try{softwareAvailable=Api16.available(input,data.getLong("offset"),data.getLong("length"));}
            catch(IOException|RuntimeException error){diagnostic(openedSession,false,"wallpaper.video.software_probe_failed",VideoDiagnostics.failureDetails(error));}
            send(SOFTWARE_AVAILABLE,softwareAvailable?1:0,null);
            player=new MediaPlayer();MediaPlayer owner=player;
            audioFocus=change->guarded("audio_focus",()->{if(player==owner){focus=change==AudioManager.AUDIOFOCUS_GAIN;volume();}});
            player.setSurface(output);player.setScreenOnWhilePlaying(false);player.setLooping(true);
            updateFocus();
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
                if(softwareAvailable&&extra!=MediaPlayer.MEDIA_ERROR_IO)send(RETRY_SOFTWARE,0,null);else send(FAILED,reason.ordinal(),null);return true;
            });
            player.setDataSource(input.getFileDescriptor(),data.getLong("offset"),data.getLong("length"));player.prepareAsync();
        }finally{closeInput(data);}
    }
    private void volume(){float value=sound&&focus&&!paused?1:0;if(software!=null)software.setVolume(value);else if(player!=null)player.setVolume(value,value);}
    private void updateFocus(){
        if(sound&&!paused&&!focus)focus=audio!=null&&audio.requestAudioFocus(audioFocus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if((!sound||paused)&&focus){if(audio!=null)audio.abandonAudioFocus(audioFocus);focus=false;}
        volume();
    }
    @android.annotation.TargetApi(16) private static final class Api16 {
        static boolean available(ParcelFileDescriptor input,long offset,long length)throws IOException{return LegacySoftwareEngine.available(input.getFileDescriptor(),offset,length);}
        static SoftwarePlayer create(Context source,Handler worker,Surface surface,Bundle data,VideoWallpaperPlayer.PlaybackEvents events,SoftwareDiagnostics log){
            return new LegacySoftwareEngine(source,worker,surface,android.net.Uri.parse(data.getString("uri")),data.getString("context"),events,log);
        }
    }
    private void releasePlayer(Runnable completed){
        if(audio!=null)audio.abandonAudioFocus(audioFocus);focus=false;
        Runnable retired=()->{prepared=false;if(output!=null){output.release();output=null;}completed.run();};
        if(software!=null){SoftwarePlayer old=software;software=null;old.release(retired);return;}
        if(player!=null){player.setOnPreparedListener(null);player.setOnInfoListener(null);player.setOnVideoSizeChangedListener(null);player.setOnErrorListener(null);player.release();player=null;}
        retired.run();
    }
    private void diagnostic(int expected,boolean error,String event,String detail){
        Bundle data=new Bundle();data.putBoolean("error",error);data.putString("event",event);data.putString("detail",detail);send(expected,LOG,0,data);
    }
    private void send(int what,int reason,Bundle data){send(session,what,reason,data);}
    private void send(int expected,int what,int reason,Bundle data){
        if(expected!=session)return;
        Messenger callback=reply;if(callback==null)return;
        Message message=Message.obtain(null,what,expected,reason);if(data!=null)message.setData(data);
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
        if(releasing){terminateProcess("client_unbound");super.onDestroy();return;}
        guardedAsync("destroy",done->releasePlayer(()->{send(RELEASED,0,null);thread.quit();done.run();}));super.onDestroy();
    }
}
