package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;

/** UI owns the surface; one Looper owns every MediaPlayer operation, including release. */
final class VideoWallpaperPlayer implements SurfaceHolder.Callback {
    interface Listener {void ready();void failed();void stopped();}
    private final Activity activity;
    private final FrameLayout host,frame;
    private final SurfaceView surface;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread playback=new HandlerThread("QuietTV-wallpaper");
    private final Handler worker;
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());if(screenOff)release();else start();}};
    // Only the playback Looper reads or writes player.
    private MediaPlayer player;
    private WallpaperCatalog.Entry entry;
    private Listener listener;
    private boolean enabled,screenOff,rendered,starting,destroyed;
    private volatile int generation;
    private int bufferWidth,bufferHeight;
    private final Runnable timeout=()->fail(generation);
    VideoWallpaperPlayer(Activity activity,FrameLayout host){
        this.activity=activity;this.host=host;playback.start();worker=new Handler(playback.getLooper());
        frame=new FrameLayout(activity);frame.setClipChildren(true);frame.setVisibility(View.GONE);host.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        surface=new SurfaceView(activity);surface.setKeepScreenOn(false);surface.setFocusable(false);surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));surface.getHolder().addCallback(this);
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layout());
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);activity.registerReceiver(screen,filter);
    }
    void play(WallpaperCatalog.Entry entry,Listener listener){
        if(destroyed)return;
        if(requested(entry.id)){this.listener=listener;if(rendered)listener.ready();else start();return;}
        release();this.entry=entry;this.listener=listener;enabled=true;
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);screenOff=power==null||!(Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn());
        frame.setVisibility(View.VISIBLE);layout();start();
    }
    boolean requested(String id){return enabled&&entry!=null&&entry.id.equals(id);}
    boolean ready(){return rendered;}
    boolean visible(){return frame.getVisibility()==View.VISIBLE;}
    void stop(){enabled=false;release();frame.setVisibility(View.GONE);entry=null;listener=null;}
    void destroy(){
        if(destroyed)return;stop();destroyed=true;surface.getHolder().removeCallback(this);activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);
        worker.post(()->{releasePlayer();playback.quitSafely();});
    }
    private void layout(){
        if(entry==null||host.getWidth()==0||host.getHeight()==0)return;
        int w=entry.rotation%180==0?entry.width:entry.height,h=entry.rotation%180==0?entry.height:entry.width;
        float scale=Math.max(host.getWidth()/(float)w,host.getHeight()/(float)h);
        int width=Math.round(w*scale),height=Math.round(h*scale);
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)surface.getLayoutParams();
        if(params.width!=width||params.height!=height){params.width=width;params.height=height;surface.setLayoutParams(params);}
        if(bufferWidth!=w||bufferHeight!=h){bufferWidth=w;bufferHeight=h;surface.getHolder().setFixedSize(w,h);}
    }
    private void start(){
        if(destroyed||!enabled||screenOff||starting||entry==null||!surface.getHolder().getSurface().isValid())return;
        starting=true;final int request=generation;WallpaperCatalog.Entry selected=entry;Surface target=surface.getHolder().getSurface();
        // Resolve app storage on the UI thread; the worker receives only this request's input.
        File file=selected.custom()?WallpaperStore.get(activity).file(selected.id):null;
        worker.post(()->{
            if(request!=generation||!target.isValid())return;
            releasePlayer();if(request!=generation||!target.isValid())return;
            MediaPlayer created=new MediaPlayer();player=created;
            try{
                created.setSurface(target);created.setScreenOnWhilePlaying(false);created.setVolume(0,0);created.setLooping(true);
                if(file!=null){try(FileInputStream input=new FileInputStream(file)){created.setDataSource(input.getFD());}}
                else try(AssetFileDescriptor input=activity.getAssets().openFd("wallpapers/"+selected.id+".mp4")){created.setDataSource(input.getFileDescriptor(),input.getStartOffset(),input.getLength());}
                created.setOnPreparedListener(p->{if(player==p&&request==generation)try{p.start();}catch(RuntimeException e){main.post(()->fail(request));}});
                created.setOnInfoListener((p,what,extra)->{
                    if(player==p&&request==generation&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)main.post(()->{
                        if(request!=generation||!enabled||destroyed||rendered)return;
                        rendered=true;main.removeCallbacks(timeout);if(listener!=null)listener.ready();
                    });return false;
                });
                created.setOnErrorListener((p,what,extra)->{main.post(()->fail(request));return true;});
                created.prepareAsync();
            }catch(IOException|RuntimeException e){main.post(()->fail(request));}
        });
        main.postDelayed(timeout,8000);
    }
    private void fail(int request){
        if(request!=generation||!enabled||destroyed)return;
        enabled=false;rendered=false;starting=false;main.removeCallbacks(timeout);
        // The controller shows and commits its cover before calling stop().
        if(listener!=null)listener.failed();else stop();
    }
    private void release(){
        ++generation;main.removeCallbacks(timeout);rendered=false;starting=false;
        if(listener!=null)listener.stopped();
        worker.post(this::releasePlayer);
    }
    private void releasePlayer(){
        MediaPlayer old=player;player=null;
        if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnErrorListener(null);old.release();}
    }
    @Override public void surfaceCreated(SurfaceHolder holder){start();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){if(!destroyed)release();}
}
