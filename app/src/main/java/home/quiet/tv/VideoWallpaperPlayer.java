package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;

/** UI owns each texture; one Looper owns the decoder and retires its output before the texture. */
final class VideoWallpaperPlayer {
    interface Listener {void ready();void failed();void stopped();}
    private static final String TAG="QuietTVWallpaper";
    private final Activity activity;
    private final FrameLayout host,frame;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread playback=new HandlerThread("QuietTV-wallpaper");
    private final Handler worker;
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());if(screenOff)release();else start();}};
    // Only the playback Looper reads or writes the decoder and its Surface.
    private MediaPlayer player;
    private Surface output;
    private Target playerTarget;
    private WallpaperCatalog.Entry entry;
    private Listener listener;
    private Target target;
    private boolean enabled,screenOff,rendered,destroyed;
    private volatile int generation;
    private final Runnable timeout=()->fail(generation);

    VideoWallpaperPlayer(Activity activity,FrameLayout host){
        this.activity=activity;this.host=host;playback.start();worker=new Handler(playback.getLooper());
        frame=new FrameLayout(activity);frame.setClipChildren(true);frame.setVisibility(View.GONE);host.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layout());
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);activity.registerReceiver(screen,filter);
    }
    void play(WallpaperCatalog.Entry entry,Listener listener){
        if(destroyed)return;
        if(requested(entry.id)){this.listener=listener;if(rendered)listener.ready();else start();return;}
        release();this.entry=entry;this.listener=listener;enabled=true;
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);screenOff=power==null||!(Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn());
        frame.setVisibility(View.VISIBLE);start();
    }
    boolean requested(String id){return enabled&&entry!=null&&entry.id.equals(id);}
    boolean ready(){return rendered;}
    boolean visible(){return frame.getVisibility()==View.VISIBLE;}
    void stop(){enabled=false;release();frame.setVisibility(View.GONE);entry=null;listener=null;}
    void destroy(){
        if(destroyed)return;stop();destroyed=true;activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);
        worker.post(()->{releasePlayer();playback.quitSafely();});
    }
    private boolean current(Target candidate){return candidate==target&&candidate.request==generation&&!candidate.retired&&enabled&&!destroyed;}
    private void layout(){
        if(entry==null||target==null||host.getWidth()==0||host.getHeight()==0)return;
        int w=entry.rotation%180==0?entry.width:entry.height,h=entry.rotation%180==0?entry.height:entry.width;
        float videoAspect=w/(float)h,viewAspect=host.getWidth()/(float)host.getHeight();
        Matrix crop=new Matrix();
        if(target.unappliedRotation!=0){
            crop.setRotate(target.unappliedRotation,host.getWidth()/2f,host.getHeight()/2f);
            android.graphics.RectF bounds=new android.graphics.RectF(0,0,host.getWidth(),host.getHeight());crop.mapRect(bounds);
            crop.postScale(host.getWidth()/bounds.width(),host.getHeight()/bounds.height(),host.getWidth()/2f,host.getHeight()/2f);
        }
        crop.postScale(Math.max(1f,videoAspect/viewAspect),Math.max(1f,viewAspect/videoAspect),host.getWidth()/2f,host.getHeight()/2f);
        // Crop the texture, not an oversized View/layer (portrait video on a 4K UI can exceed GPU limits).
        target.view.setTransform(crop);
    }
    private void start(){
        if(destroyed||!enabled||screenOff||entry==null||target!=null)return;
        Target next=new Target(++generation);target=next;
        frame.addView(next.view,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));layout();
        next.view.post(()->{if(current(next)&&!next.view.isHardwareAccelerated())fail(next.request);});
        main.postDelayed(timeout,8000);
    }
    private final class Target implements TextureView.SurfaceTextureListener {
        final int request;
        final TextureView view;
        volatile boolean retired,released;
        volatile SurfaceTexture texture;
        boolean started,updated;
        int unappliedRotation;
        final float[] transform=new float[16];
        Target(int request){
            this.request=request;view=new TextureView(activity);view.setKeepScreenOn(false);view.setFocusable(false);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);view.setSurfaceTextureListener(this);
        }
        @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){
            this.texture=texture;
            if(!current(this)){retire(this);return;}
            if(!view.isHardwareAccelerated()){fail(request);return;}
            begin(this,entry);
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
            // Returning false keeps the producer's queue alive until its owning Looper has released it.
            if(current(this)){
                ++generation;target=null;rendered=false;main.removeCallbacks(timeout);
                if(listener!=null)listener.stopped();
                main.post(()->{frame.removeView(view);start();});
            }
            retire(this);return false;
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){
            if(this.texture!=texture||!current(this))return;
            if(entry.rotation!=0){
                texture.getTransformMatrix(transform);
                // Producers may apply metadata rotation after their first update, or omit it entirely.
                int rotation=(entry.rotation%180!=0?Math.abs(transform[0])>.001f:transform[0]>0)?entry.rotation:0;
                if(unappliedRotation!=rotation){unappliedRotation=rotation;layout();}
            }
            updated=true;firstFrame(this);
        }
        void releaseTexture(){
            if(released||texture==null)return;released=true;texture.release();
        }
    }
    private void begin(Target next,WallpaperCatalog.Entry selected){
        File file=selected.custom()?WallpaperStore.get(activity).file(selected.id):null;
        SurfaceTexture texture=next.texture;
        Log.i(TAG,"prepare texture request="+next.request);DiagnosticLog.info("wallpaper.video.prepare","requested");
        worker.post(()->{
            if(next.request!=generation||next.retired)return;
            releasePlayer();if(next.request!=generation||next.retired)return;
            try{
                output=new Surface(texture);playerTarget=next;MediaPlayer created=new MediaPlayer();player=created;
                created.setSurface(output);created.setScreenOnWhilePlaying(false);created.setVolume(0,0);created.setLooping(true);
                if(file!=null){try(FileInputStream input=new FileInputStream(file)){created.setDataSource(input.getFD());}}
                else try(AssetFileDescriptor input=activity.getAssets().openFd("wallpapers/"+selected.id+".mp4")){created.setDataSource(input.getFileDescriptor(),input.getStartOffset(),input.getLength());}
                created.setOnPreparedListener(p->{if(player==p&&next.request==generation)try{
                    p.start();main.post(()->{if(current(next)){next.started=true;firstFrame(next);}});
                }catch(RuntimeException e){main.post(()->fail(next.request));}});
                // MediaPlayer's rendering notification alone does not establish a composited frame.
                created.setOnErrorListener((p,what,extra)->{DiagnosticLog.info("wallpaper.video.player_error","what="+what+" extra="+extra);main.post(()->fail(next.request));return true;});
                created.prepareAsync();
            }catch(IOException|RuntimeException e){DiagnosticLog.error("wallpaper.video.prepare_failed",e);main.post(()->fail(next.request));}
        });
    }
    private void firstFrame(Target next){
        if(!current(next)||!next.started||!next.updated||rendered)return;
        rendered=true;main.removeCallbacks(timeout);Log.i(TAG,"texture updated request="+next.request);DiagnosticLog.info("wallpaper.video.rendered","first frame");
        main.post(()->{if(current(next)&&rendered&&listener!=null)listener.ready();});
    }
    private void fail(int request){
        if(request!=generation||!enabled||destroyed)return;
        enabled=false;rendered=false;main.removeCallbacks(timeout);Log.w(TAG,"texture unavailable request="+request);DiagnosticLog.info("wallpaper.video.failed","rendering unavailable");
        if(listener!=null)listener.failed();else stop();
    }
    private void release(){
        ++generation;main.removeCallbacks(timeout);rendered=false;
        if(listener!=null)listener.stopped();
        Target old=target;target=null;
        if(old!=null){old.retired=true;frame.removeView(old.view);retire(old);}
        else worker.post(this::releasePlayer);
    }
    private void retire(Target old){
        old.retired=true;
        Runnable cleanup=()->{if(playerTarget==old)releasePlayer();old.releaseTexture();};
        // A late detach after destroy has no decoder left; its texture still belongs to this target.
        if(!worker.post(cleanup))old.releaseTexture();
    }
    private void releasePlayer(){
        MediaPlayer old=player;player=null;playerTarget=null;Surface oldOutput=output;output=null;
        try{if(old!=null){old.setOnPreparedListener(null);old.setOnErrorListener(null);old.release();}}
        catch(RuntimeException e){Log.w(TAG,"decoder release failed",e);}
        finally{if(oldOutput!=null)oldOutput.release();}
    }
}
