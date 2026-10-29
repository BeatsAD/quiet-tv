package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One playback Looper owns the decoder; each output acknowledges detachment separately. */
final class VideoWallpaperPlayer {
    interface Snapshot {void ready(Bitmap image);}
    interface Listener {void ready();void failed(Failure reason);void stopped();}
    interface PlaybackEvents {void ready();void failed(Failure reason);void size(int width,int height,float pixelRatio);}
    enum Failure {
        UNSUPPORTED,DECODE,FILE,TIMEOUT,OUTPUT,RELEASING,UNKNOWN;
        String message(boolean chinese){
            switch(this){
                case UNSUPPORTED:return chinese?"电视不支持此视频编码或规格":"Video encoding or format unsupported by this TV";
                case DECODE:return chinese?"视频解码失败":"Video decoding failed";
                case FILE:return chinese?"视频文件无法读取":"Unable to read video file";
                case TIMEOUT:return chinese?"视频加载超时":"Video loading timed out";
                case OUTPUT:return chinese?"视频画面输出失败":"Video output failed";
                case RELEASING:return chinese?"播放器资源尚未释放，暂时无法播放":"Player resources are still being released. Video is temporarily unavailable";
                default:return chinese?"视频播放失败":"Video playback failed";
            }
        }
        String hint(boolean chinese){if(this==RELEASING)return message(chinese);return message(chinese)+(this==UNSUPPORTED||this==DECODE||this==TIMEOUT?(chinese?"，请尝试 1080p 30 FPS H.264 SDR 视频":". Try 1080p 30 FPS H.264 SDR video."):(chinese?"，按确定键重试":". Press OK to retry."));}
    }
    private static final String TAG="QuietTVWallpaper";
    private final Activity activity;
    private final boolean compositeOutput;
    private final FrameLayout host,frame;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final Handler worker=playbackWorker();
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());if(screenOff)release();else start();}};
    // Only the playback Looper reads or writes the decoder and its Surface.
    private static final PlaybackHandoff handoff=new PlaybackHandoff();
    private static Decoder decoder;
    // A grace period allows ordinary switches; a stalled native call must not collect new requests.
    private static volatile long releasingSince=-1;
    private static boolean releaseBlocked(){long started=releasingSince;return started>=0&&SystemClock.elapsedRealtime()-started>=500;}
    private final Object owner=new Object();
    private static final class Decoder {
        final Object owner;
        final PlaybackHandoff.Output outputId;
        final CountDownLatch detached;
        final WallpaperPlayback player;
        final Surface output;
        final boolean ownsOutput;
        final String context;
        Decoder(Object owner,PlaybackHandoff.Output outputId,CountDownLatch detached,WallpaperPlayback player,Surface output,boolean ownsOutput,String context){
            this.owner=owner;this.outputId=outputId;this.detached=detached;this.player=player;this.output=output;this.ownsOutput=ownsOutput;this.context=context;
        }
    }
    private WallpaperCatalog.Entry entry;
    private Listener listener;
    private Target target;
    private boolean enabled,screenOff,rendered,destroyed;
    private volatile boolean paused;
    private volatile boolean sound;
    void setSound(boolean enabled){
        sound=enabled;final int request=generation;
        if(!releaseBlocked())worker.post(()->{if(request==generation&&decoder!=null&&decoder.owner==owner)try{decoder.player.setSound(sound);}catch(RuntimeException e){DiagnosticLog.error("wallpaper.video.sound_failed",e);main.post(()->fail(request,Failure.DECODE));}});
    }
    private volatile int generation;
    private final Runnable timeout=()->{
        Target pending=target;
        boolean blocked=releaseBlocked();
        DiagnosticLog.info("wallpaper.video.timeout",context(generation)+" reason="+(blocked?"resource_release_pending":"first_frame_timeout")+" paused="+paused+" screenOff="+screenOff
            +" output="+(pending==null?"missing":pending.textureOutput?"texture":"surface")+" surfaceValid="+(pending!=null&&(pending.textureOutput?pending.texture!=null:pending.surface!=null&&pending.surface.isValid()))+" started="+(pending!=null&&pending.started)+" updated="+(pending!=null&&pending.updated));
        fail(generation,blocked?Failure.RELEASING:Failure.TIMEOUT);
    };
    private String context(int request){return "request="+request+" wallpaper="+VideoDiagnostics.wallpaper(entry==null?null:entry.id);}
    private static Handler playbackWorker(){
        HandlerThread thread=new HandlerThread("QuietTV-wallpaper");thread.start();return new Handler(thread.getLooper());
    }

    VideoWallpaperPlayer(Activity activity,FrameLayout host){this(activity,host,false);}
    VideoWallpaperPlayer(Activity activity,FrameLayout host,boolean compositeOutput){
        this.activity=activity;this.host=host;this.compositeOutput=compositeOutput;
        frame=new FrameLayout(activity);frame.setClipChildren(true);frame.setVisibility(View.GONE);host.addView(frame,new FrameLayout.LayoutParams(-1,-1));
        host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layout());
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);activity.registerReceiver(screen,filter);
    }
    void play(WallpaperCatalog.Entry entry,Listener listener){
        if(destroyed)return;
        if(releaseBlocked()){listener.failed(Failure.RELEASING);return;}
        if(requested(entry.id)){this.listener=listener;if(rendered)listener.ready();else start();return;}
        release();this.entry=entry;this.listener=listener;enabled=true;
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);screenOff=power==null||!(Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn());
        frame.setVisibility(View.VISIBLE);start();
    }
    boolean requested(String id){return enabled&&!releaseBlocked()&&entry!=null&&entry.id.equals(id);}
    boolean ready(){return rendered;}
    boolean visible(){return enabled&&frame.getVisibility()==View.VISIBLE;}
    void setPaused(boolean value){
        paused=value;final int request=generation;final String diagnosticContext=context(request);
        main.removeCallbacks(timeout);
        if(!releaseBlocked())worker.post(()->{if(request==generation&&decoder!=null&&decoder.owner==owner)try{decoder.player.setPaused(value);}catch(RuntimeException e){DiagnosticLog.error("wallpaper.video.pause_failed",diagnosticContext+" paused="+value+" "+VideoDiagnostics.failureDetails(e),e);main.post(()->fail(request,Failure.DECODE));}});
        if(!value){start();if(enabled&&!rendered&&!screenOff)main.postDelayed(timeout,8000);}
    }
    void snapshot(Snapshot callback){
        Target captured=target;final int request=generation;
        if(captured==null||!rendered||!current(captured)||captured.view.getWidth()==0||captured.view.getHeight()==0){callback.ready(null);return;}
        float scale=Math.min(1f,WallpaperBackdropCache.MAX_EDGE/(float)Math.max(captured.view.getWidth(),captured.view.getHeight()));
        int width=Math.max(1,Math.round(captured.view.getWidth()*scale)),height=Math.max(1,Math.round(captured.view.getHeight()*scale));
        if(captured.textureOutput){
            Bitmap image=null;try{image=((TextureView)captured.view).getBitmap(width,height);}catch(RuntimeException|OutOfMemoryError ignored){}
            callback.ready(image);return;
        }
        if(Build.VERSION.SDK_INT<24){callback.ready(null);return;}
        Bitmap image;
        try{image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);}catch(OutOfMemoryError e){callback.ready(null);return;}
        try{Api24.copy((SurfaceView)captured.view,image,main,success->{
            if(success&&current(captured,request))callback.ready(image);else{image.recycle();callback.ready(null);}
        });}catch(RuntimeException e){image.recycle();callback.ready(null);}
    }
    @android.annotation.TargetApi(24) private static final class Api24 {
        interface Result {void ready(boolean success);}
        static void copy(SurfaceView view,Bitmap image,Handler handler,Result result){PixelCopy.request(view,image,status->result.ready(status==PixelCopy.SUCCESS),handler);}
    }
    void stop(){enabled=false;release();if(target==null)frame.setVisibility(View.GONE);entry=null;listener=null;}
    void destroy(){
        if(destroyed)return;destroyed=true;stop();activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);
    }
    private boolean current(Target candidate){return candidate==target&&candidate.request==generation&&!candidate.retired&&enabled&&!screenOff&&!destroyed;}
    private boolean current(Target candidate,int request){return candidate.request==request&&current(candidate);}
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
        if(destroyed||!enabled||screenOff||paused||entry==null)return;
        if(releaseBlocked()){fail(generation,Failure.RELEASING);return;}
        boolean textureOutput=compositeOutput||BuildConfig.LEGACY_19||VideoGeometry.manualRotation(Build.VERSION.SDK_INT,entry.rotation);
        if(target!=null){
            if(target.preparing||target.textureOutput)return;
            if(!target.textureOutput&&!textureOutput){
                target.aspect=VideoGeometry.aspect(entry.width,entry.height,entry.pixelRatio,entry.rotation);layout();
                if(target.surface!=null&&target.surface.isValid())begin(target,entry);
                main.removeCallbacks(timeout);main.postDelayed(timeout,8000);return;
            }
            Target old=target;target=null;retire(old);frame.removeView(old.view);
        }
        Target next=new Target(++generation);target=next;
        frame.addView(next.view,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));layout();
        next.view.post(()->{if(next.textureOutput&&current(next)&&!next.view.isHardwareAccelerated()){DiagnosticLog.info("wallpaper.video.output_failed",context(next.request)+" reason=hardware_acceleration_unavailable");fail(next.request,Failure.OUTPUT);}});
        main.postDelayed(timeout,8000);
    }
    private final class Target implements TextureView.SurfaceTextureListener,SurfaceHolder.Callback {
        final PlaybackHandoff.Output outputId=new PlaybackHandoff.Output();
        int request;
        final View view;
        final boolean textureOutput;
        volatile boolean retired,released;
        volatile SurfaceTexture texture;
        volatile Surface surface;
        boolean preparing,started,updated;
        int unappliedRotation;
        float aspect;
        final float[] transform=new float[16];
        Target(int request){
            this.request=request;aspect=VideoGeometry.aspect(entry.width,entry.height,entry.pixelRatio,entry.rotation);
            // Wallpaper must share UI composition with its cover; a separate native video plane can flash during window transitions.
            // Old native-window paths also rotate explicitly in this viewport-sized texture.
            textureOutput=compositeOutput||BuildConfig.LEGACY_19||VideoGeometry.manualRotation(Build.VERSION.SDK_INT,entry.rotation);
            if(!BuildConfig.LEGACY_19&&VideoGeometry.manualRotation(Build.VERSION.SDK_INT,entry.rotation))unappliedRotation=entry.rotation;
            if(textureOutput){TextureView textureView=new TextureView(activity);textureView.setSurfaceTextureListener(this);view=textureView;}
            else{SurfaceView surfaceView=new SurfaceView(activity);surfaceView.getHolder().addCallback(this);view=surfaceView;}
            view.setKeepScreenOn(false);view.setFocusable(false);androidx.core.view.ViewCompat.setImportantForAccessibility(view,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override public void surfaceCreated(SurfaceHolder holder){surface=holder.getSurface();if(current(this))begin(this,entry);}
        @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
        @Override public void surfaceDestroyed(SurfaceHolder holder){
            detached();
            // Wait only for this output's detachment, not the global decoder retirement queue.
            try{
                if(!outputId.retire().await(3,TimeUnit.SECONDS))DiagnosticLog.info("wallpaper.video.detach_timeout","surface destroyed before detachment confirmed");
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
        }
        @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){
            this.texture=texture;
            if(!current(this)){retire(this);return;}
            if(!view.isHardwareAccelerated()){DiagnosticLog.info("wallpaper.video.output_failed",context(request)+" reason=hardware_acceleration_unavailable");fail(request,Failure.OUTPUT);return;}
            begin(this,entry);
        }
        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
        @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){
            // Returning false keeps the producer's queue alive until its owning Looper has released it.
            detached();return false;
        }
        private void detached(){
            retire(this);
            if(this==target){
                ++generation;target=null;rendered=false;main.removeCallbacks(timeout);
                if(listener!=null)listener.stopped();
                main.post(()->{frame.removeView(view);start();});
            }
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
        if(!current(next)||next.preparing)return;
        if(releaseBlocked()){fail(generation,Failure.RELEASING);return;}
        next.preparing=true;final int request=next.request;final String diagnosticContext="request="+request+" wallpaper="+VideoDiagnostics.wallpaper(selected.id);
        File file=selected.custom()?WallpaperStore.get(activity).file(selected.id):null;
        Log.i(TAG,"prepare request="+next.request);DiagnosticLog.info("wallpaper.video.prepare",diagnosticContext+" mime="+VideoDiagnostics.mime(selected.videoMime)+" size="+selected.width+"x"+selected.height+" fps="+selected.fps+" backend="+(BuildConfig.LEGACY_19?"platform":"media3")+" output="+(next.textureOutput?"texture":"surface")+" source="+(file==null?"asset":"local")+" filePresent="+(file==null?"unknown":file.isFile())+" bytes="+(file==null?-1:file.length()));
        worker.post(()->handoff.submit(done->{
            if(request!=generation||next.retired){done.run();return;}
            releaseDecoder(()->{
                if(request!=generation||next.retired){done.run();return;}
                CountDownLatch detached=next.outputId.acquire();
                if(detached==null){main.post(()->fail(request,Failure.OUTPUT));done.run();return;}
                Surface output=null;
                try{
                    output=next.textureOutput?new Surface(next.texture):next.surface;
                    if(output==null||!output.isValid()){DiagnosticLog.info("wallpaper.video.output_failed",diagnosticContext+" reason=invalid_surface");main.post(()->fail(request,Failure.OUTPUT));return;}
                    WallpaperPlayback player=new WallpaperPlayback(activity.getApplicationContext(),worker,output,file,selected,diagnosticContext,new PlaybackEvents(){
                        @Override public void ready(){main.post(()->{if(current(next,request)){next.started=true;if(!next.textureOutput)next.updated=true;firstFrame(next);}});}
                        @Override public void failed(Failure reason){main.post(()->fail(request,reason));}
                        @Override public void size(int width,int height,float pixelRatio){main.post(()->{if(current(next,request)&&width>0&&height>0){next.aspect=VideoGeometry.aspect(width,height,pixelRatio,0);layout();}});}
                    });
                    decoder=new Decoder(owner,next.outputId,detached,player,output,next.textureOutput,diagnosticContext);
                    main.post(()->{if(current(next,request)&&!paused){main.removeCallbacks(timeout);main.postDelayed(timeout,8000);}});
                    player.setSound(sound);player.setPaused(paused);player.prepare();
                }catch(IOException|RuntimeException e){DiagnosticLog.error("wallpaper.video.prepare_failed",diagnosticContext+" "+VideoDiagnostics.failureDetails(e),e);main.post(()->fail(request,e instanceof IOException?Failure.FILE:Failure.DECODE));}
                finally{if(decoder==null){detached.countDown();if(next.textureOutput&&output!=null)output.release();}done.run();}
            });
        }));
    }
    private void firstFrame(Target next){
        if(!current(next)||!next.started||!next.updated||rendered)return;
        rendered=true;main.removeCallbacks(timeout);Log.i(TAG,"texture updated request="+next.request);DiagnosticLog.info("wallpaper.video.rendered",context(next.request)+" evidence="+(BuildConfig.LEGACY_19?"platform_prepared_and_texture_updated":"rendered_first_frame_callback"));
        final int request=next.request;
        main.post(()->{if(current(next,request)&&rendered&&listener!=null)listener.ready();});
    }
    private void fail(int request,Failure reason){
        if(request!=generation||!enabled||destroyed)return;
        enabled=false;rendered=false;main.removeCallbacks(timeout);Log.w(TAG,"video unavailable request="+request+" reason="+reason);DiagnosticLog.info("wallpaper.video.failed",context(request)+" reason="+reason.name()+" paused="+paused+" screenOff="+screenOff);
        if(listener!=null)listener.failed(reason);else stop();
    }
    private void release(){
        ++generation;main.removeCallbacks(timeout);rendered=false;
        if(listener!=null)listener.stopped();
        Target old=target;
        if(old!=null&&!old.textureOutput&&!destroyed){
            // Keep the native output attached under the opaque cover while retiring only the decoder.
            // Removing/readding SurfaceView also changes the window's transparent region.
            old.request=generation;old.preparing=false;old.started=false;old.updated=false;
            if(!releaseBlocked())worker.post(()->handoff.submit(this::releasePlayer));return;
        }
        target=null;
        if(old!=null){retire(old);frame.removeView(old.view);}
        else if(!releaseBlocked())worker.post(()->handoff.submit(this::releasePlayer));
    }
    private void retire(Target old){
        if(old.retired){if(old.outputId.retire().getCount()==0)old.releaseTexture();return;}
        old.retired=true;
        if(old.outputId.retire().getCount()==0){old.releaseTexture();return;}
        Runnable cleanup=()->handoff.submit(done->{
            if(decoder!=null&&decoder.outputId==old.outputId)releasePlayer(()->{try{old.releaseTexture();}finally{done.run();}});
            else{try{old.releaseTexture();}finally{done.run();}}
        });
        // A late detach after destroy has no decoder left; its texture still belongs to this target.
        if(!worker.post(cleanup))old.releaseTexture();
    }
    private void releasePlayer(Runnable completed){
        if(decoder==null||decoder.owner!=owner){completed.run();return;}releaseDecoder(completed);
    }
    private static void releaseDecoder(Runnable completed){
        Decoder old=decoder;decoder=null;
        if(old==null){completed.run();return;}
        releasingSince=SystemClock.elapsedRealtime();
        Runnable retired=()->{old.detached.countDown();try{if(old.ownsOutput)old.output.release();}finally{releasingSince=-1;completed.run();}};
        // A failed release must not be treated as acknowledgement of Surface ownership.
        try{if(old.player.detachOutput())old.detached.countDown();old.player.release(retired);}
        catch(RuntimeException e){DiagnosticLog.error("wallpaper.video.release_failed",old.context+" "+VideoDiagnostics.failureDetails(e),e);}
    }
}
