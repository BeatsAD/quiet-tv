package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One playback Looper owns the decoder; surfaces are retired after their decoder. */
final class VideoWallpaperPlayer {
    interface Listener {void ready();void failed(Failure reason);void stopped();}
    interface PlaybackEvents {void ready();void failed(Failure reason);void size(int width,int height,float pixelRatio);}
    enum Failure {
        UNSUPPORTED,DECODE,FILE,TIMEOUT,OUTPUT,UNKNOWN;
        String message(boolean chinese){
            switch(this){
                case UNSUPPORTED:return chinese?"电视不支持此视频编码或规格":"Video encoding or format unsupported by this TV";
                case DECODE:return chinese?"视频解码失败":"Video decoding failed";
                case FILE:return chinese?"视频文件无法读取":"Unable to read video file";
                case TIMEOUT:return chinese?"视频加载超时":"Video loading timed out";
                case OUTPUT:return chinese?"视频画面输出失败":"Video output failed";
                default:return chinese?"视频播放失败":"Video playback failed";
            }
        }
        String hint(boolean chinese){return message(chinese)+(this==UNSUPPORTED?(chinese?"，请尝试 H.264 SDR 视频":". Try H.264 SDR video."):(chinese?"，按确定键重试":". Press OK to retry."));}
    }
    private static final String TAG="QuietTVWallpaper";
    private final Activity activity;
    private final FrameLayout host,frame;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread playback=new HandlerThread("QuietTV-wallpaper");
    private final Handler worker;
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());if(screenOff)release();else start();}};
    // Only the playback Looper reads or writes the decoder and its Surface.
    private WallpaperPlayback player;
    private Surface output;
    private boolean ownsOutput;
    private Target playerTarget;
    private WallpaperCatalog.Entry entry;
    private Listener listener;
    private Target target;
    private boolean enabled,screenOff,rendered,destroyed;
    private volatile int generation;
    private final Runnable timeout=()->fail(generation,Failure.TIMEOUT);

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
        // Drain Media3's queued release callbacks before retiring their application Looper.
        worker.post(()->{releasePlayer();worker.post(playback::quitSafely);});
    }
    private boolean current(Target candidate){return candidate==target&&candidate.request==generation&&!candidate.retired&&enabled&&!destroyed;}
    private void layout(){
        if(entry==null||target==null||host.getWidth()==0||host.getHeight()==0)return;
        float videoAspect=target.aspect,viewAspect=host.getWidth()/(float)host.getHeight();
        if(!target.textureOutput){
            int width=host.getWidth(),height=host.getHeight();
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)target.view.getLayoutParams();
            int croppedWidth=Math.round(width*Math.max(1f,videoAspect/viewAspect)),croppedHeight=Math.round(height*Math.max(1f,viewAspect/videoAspect));
            if(params.width!=croppedWidth||params.height!=croppedHeight){params.width=croppedWidth;params.height=croppedHeight;target.view.setLayoutParams(params);}return;
        }
        Matrix crop=new Matrix();
        if(target.unappliedRotation!=0){
            crop.setRotate(target.unappliedRotation,host.getWidth()/2f,host.getHeight()/2f);
            android.graphics.RectF bounds=new android.graphics.RectF(0,0,host.getWidth(),host.getHeight());crop.mapRect(bounds);
            crop.postScale(host.getWidth()/bounds.width(),host.getHeight()/bounds.height(),host.getWidth()/2f,host.getHeight()/2f);
        }
        crop.postScale(Math.max(1f,videoAspect/viewAspect),Math.max(1f,viewAspect/videoAspect),host.getWidth()/2f,host.getHeight()/2f);
        // Crop the texture, not an oversized View/layer (portrait video on a 4K UI can exceed GPU limits).
        ((TextureView)target.view).setTransform(crop);
    }
    private void start(){
        if(destroyed||!enabled||screenOff||entry==null||target!=null)return;
        Target next=new Target(++generation);target=next;
        frame.addView(next.view,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));layout();
        next.view.post(()->{if(next.textureOutput&&current(next)&&!next.view.isHardwareAccelerated())fail(next.request,Failure.OUTPUT);});
        main.postDelayed(timeout,8000);
    }
    private final class Target implements TextureView.SurfaceTextureListener,SurfaceHolder.Callback {
        final int request;
        final View view;
        final boolean textureOutput;
        volatile boolean retired,released;
        volatile SurfaceTexture texture;
        volatile Surface surface;
        boolean started,updated;
        int unappliedRotation;
        float aspect;
        final float[] transform=new float[16];
        Target(int request){
            this.request=request;aspect=VideoGeometry.aspect(entry.width,entry.height,entry.pixelRatio,entry.rotation);
            // On old native-window paths, rotate explicitly in a viewport-sized texture.
            textureOutput=BuildConfig.LEGACY_19||VideoGeometry.manualRotation(Build.VERSION.SDK_INT,entry.rotation);
            if(!BuildConfig.LEGACY_19&&textureOutput)unappliedRotation=entry.rotation;
            if(textureOutput){TextureView textureView=new TextureView(activity);textureView.setSurfaceTextureListener(this);view=textureView;}
            else{SurfaceView surfaceView=new SurfaceView(activity);surfaceView.getHolder().addCallback(this);view=surfaceView;}
            view.setKeepScreenOn(false);view.setFocusable(false);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override public void surfaceCreated(SurfaceHolder holder){surface=holder.getSurface();if(current(this))begin(this,entry);}
        @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
        @Override public void surfaceDestroyed(SurfaceHolder holder){
            detached();
            // SurfaceHolder requires the producer to stop before this callback returns.
            // Unlike SurfaceTexture, its Surface cannot be retained after destruction.
            CountDownLatch released=new CountDownLatch(1);
            if(worker.post(released::countDown))try{
                if(!released.await(3,TimeUnit.SECONDS))DiagnosticLog.info("wallpaper.video.release_timeout","surface retirement");
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
        @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){
            this.texture=texture;
            if(!current(this)){retire(this);return;}
            if(!view.isHardwareAccelerated()){fail(request,Failure.OUTPUT);return;}
            begin(this,entry);
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
            // Returning false keeps the producer's queue alive until its owning Looper has released it.
            detached();return false;
        }
        private void detached(){
            if(current(this)){
                ++generation;target=null;rendered=false;main.removeCallbacks(timeout);
                if(listener!=null)listener.stopped();
                main.post(()->{frame.removeView(view);start();});
            }
            retire(this);
        }
        @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){
            if(this.texture!=texture||!current(this))return;
            if(BuildConfig.LEGACY_19&&entry.rotation!=0){
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
        Log.i(TAG,"prepare request="+next.request);DiagnosticLog.info("wallpaper.video.prepare","mime="+selected.videoMime+" size="+selected.width+"x"+selected.height+" fps="+selected.fps+" backend="+(BuildConfig.LEGACY_19?"platform":"media3"));
        worker.post(()->{
            if(next.request!=generation||next.retired)return;
            releasePlayer();if(next.request!=generation||next.retired)return;
            try{
                ownsOutput=next.textureOutput;output=ownsOutput?new Surface(next.texture):next.surface;playerTarget=next;
                if(output==null||!output.isValid()){main.post(()->fail(next.request,Failure.OUTPUT));return;}
                player=new WallpaperPlayback(activity,worker,output,file,selected,new PlaybackEvents(){
                    @Override public void ready(){main.post(()->{if(current(next)){next.started=true;if(!next.textureOutput)next.updated=true;firstFrame(next);}});}
                    @Override public void failed(Failure reason){main.post(()->fail(next.request,reason));}
                    @Override public void size(int width,int height,float pixelRatio){main.post(()->{if(current(next)&&width>0&&height>0){next.aspect=VideoGeometry.aspect(width,height,pixelRatio,0);layout();}});}
                });
                player.prepare();
            }catch(IOException|RuntimeException e){DiagnosticLog.error("wallpaper.video.prepare_failed",e);main.post(()->fail(next.request,e instanceof IOException?Failure.FILE:Failure.DECODE));}
        });
    }
    private void firstFrame(Target next){
        if(!current(next)||!next.started||!next.updated||rendered)return;
        rendered=true;main.removeCallbacks(timeout);Log.i(TAG,"texture updated request="+next.request);DiagnosticLog.info("wallpaper.video.rendered","first frame");
        main.post(()->{if(current(next)&&rendered&&listener!=null)listener.ready();});
    }
    private void fail(int request,Failure reason){
        if(request!=generation||!enabled||destroyed)return;
        enabled=false;rendered=false;main.removeCallbacks(timeout);Log.w(TAG,"video unavailable request="+request+" reason="+reason);DiagnosticLog.info("wallpaper.video.failed",reason.name());
        if(listener!=null)listener.failed(reason);else stop();
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
        WallpaperPlayback old=player;player=null;playerTarget=null;Surface oldOutput=output;output=null;boolean releaseOutput=ownsOutput;ownsOutput=false;
        try{if(old!=null)old.release();}
        catch(RuntimeException e){Log.w(TAG,"decoder release failed",e);}
        finally{if(oldOutput!=null&&releaseOutput)oldOutput.release();}
    }
}
