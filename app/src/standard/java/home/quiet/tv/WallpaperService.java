package home.quiet.tv;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.Surface;
import java.io.IOException;

/** Bound-only Media3 process. Its main Looper can retire a blocked codec without stopping HOME. */
public class WallpaperService extends Service {
    static final int OPEN=1,SOUND=2,PAUSE=3,RELEASE=4;
    static final int PREPARED=11,FRAME=12,SIZE=13,FAILED=14,RELEASED=15,NOTE=16,LOG=17;
    static final long OPERATION_TIMEOUT_MS=2500;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Handler worker;
    private HandlerThread thread;
    private Messenger endpoint;
    private volatile Messenger reply;
    private volatile int session;
    private boolean releasing,terminating,destroyed;
    // Only the codec Looper owns these fields.
    private WallpaperEngine player;
    private Surface output;
    @Override public void onCreate(){
        super.onCreate();thread=new HandlerThread("QuietTV-media3-codec");thread.start();worker=new Handler(thread.getLooper());
        endpoint=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message message){receive(message);}});
    }
    @Override public IBinder onBind(Intent intent){return endpoint.getBinder();}
    private void receive(Message message){
        if(terminating||destroyed||releasing)return;
        Bundle data=message.getData();data.setClassLoader(Surface.class.getClassLoader());
        if(message.what==OPEN){if(session!=0)return;session=message.arg1;reply=message.replyTo;}
        if(message.arg1!=session)return;
        switch(message.what){
            case OPEN:guarded("open",done->{open(data);done.run();});break;
            case SOUND:guarded("sound",done->{if(player!=null)player.setSound(data.getBoolean("enabled"));done.run();});break;
            case PAUSE:guarded("pause",done->{if(player!=null)player.setPaused(data.getBoolean("enabled"));done.run();});break;
            case RELEASE:
                releasing=true;
                guarded("release",done->{
                    Runnable retired=()->{
                        player=null;if(output!=null){output.release();output=null;}
                        main.post(()->{send(RELEASED,0,null);session=0;reply=null;releasing=false;done.run();});
                    };
                    if(player==null)retired.run();else player.release(retired);
                });break;
        }
    }
    private interface CodecCall {void run(Runnable completed)throws IOException;}
    private void guarded(String phase,CodecCall call){
        Runnable deadline=()->terminateProcess(phase+"_timeout");
        main.postDelayed(deadline,OPERATION_TIMEOUT_MS);
        worker.post(()->{
            try{call.run(()->main.removeCallbacks(deadline));}
            catch(IOException|RuntimeException e){
                diagnostic(true,"wallpaper.video.remote_error","phase="+phase+" "+VideoDiagnostics.failureDetails(e));
                send(FAILED,(e instanceof IOException?VideoWallpaperPlayer.Failure.FILE:VideoWallpaperPlayer.Failure.DECODE).ordinal(),null);
                main.post(()->terminateProcess(phase+"_failed"));
            }
        });
    }
    private void open(Bundle data)throws IOException{
        output=data.getParcelable("surface");
        if(output==null||!output.isValid())throw new IOException("Missing playback output");
        final int openedSession=session;
        player=new WallpaperEngine(this,worker,output,Uri.parse(data.getString("uri")),data.getInt("rotation"),data.getString("context"),new VideoWallpaperPlayer.PlaybackEvents(){
            @Override public void ready(){send(openedSession,FRAME,0,null);}
            @Override public void failed(VideoWallpaperPlayer.Failure reason){send(openedSession,FAILED,reason.ordinal(),null);}
            @Override public void size(int width,int height,float ratio){Bundle size=new Bundle();size.putInt("width",width);size.putInt("height",height);size.putFloat("pixelRatio",ratio);send(openedSession,SIZE,0,size);}
        },(error,event,detail)->diagnostic(openedSession,error,event,detail));
        player.setSound(data.getBoolean("sound"));player.setPaused(data.getBoolean("paused"));player.prepare();
    }
    private void diagnostic(boolean error,String event,String detail){diagnostic(session,error,event,detail);}
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
        Bundle data=new Bundle();data.putString("phase",reason);send(NOTE,0,data);
        android.os.Process.killProcess(android.os.Process.myPid());
    }
    @Override public void onDestroy(){
        destroyed=true;
        // A disconnected client cannot acknowledge release. Death retires all native ownership.
        if(session!=0)terminateProcess("client_unbound");else thread.quit();
        super.onDestroy();
    }
}
