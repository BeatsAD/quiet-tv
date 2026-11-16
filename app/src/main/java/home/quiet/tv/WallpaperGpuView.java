package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One visible GLES source. Images, weather and a decoder's existing Surface use the same pyramid. */
@android.annotation.TargetApi(21)
final class WallpaperGpuView extends TextureView implements TextureView.SurfaceTextureListener {
    private static int learnedWeatherQuality;
    private static synchronized int rememberWeatherQuality(int quality){learnedWeatherQuality=Math.max(learnedWeatherQuality,quality);return learnedWeatherQuality;}
    private static synchronized void recoveredWeatherQuality(int quality){learnedWeatherQuality=quality;}
    interface Listener {void ready();void failed(String detail);}
    interface Capture {void ready(Bitmap image);}
    interface VideoSurface {
        void available(SurfaceTexture input,int width,int height);
        void presented(SurfaceTexture input);
        void destroyed(SurfaceTexture input);
        void failed(String detail);
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Engine engine;
    private final Listener listener;
    private final VideoSurface video;
    private FrameLayout host;
    private SurfaceView nativeView;
    private volatile boolean nativeMode;
    private volatile int nativeGeneration;
    private boolean active,presented;
    private volatile boolean destroyed;
    private int strength;
    private String scene="";
    private WeatherIllumination illumination;
    private WeatherAtmosphere atmosphere=WeatherAtmosphere.EMPTY;
    private Bitmap retained;
    WallpaperGpuView(Context context,Listener listener){this(context,listener,null);}
    WallpaperGpuView(Context context,Listener listener,VideoSurface video){
        super(context);this.listener=listener;this.video=video;setOpaque(true);setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        engine=new Engine(context.getApplicationContext());setSurfaceTextureListener(this);
        if(video==null&&Build.VERSION.SDK_INT>=27){
            host=new FrameLayout(context){
                @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);update();}
                @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);update();}
                @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);update();}
                @Override public void setAlpha(float alpha){super.setAlpha(alpha);if(nativeView!=null)nativeView.setAlpha(alpha);}
                @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);nativeSize();}
                @Override public void draw(Canvas canvas){if(canvas.isHardwareAccelerated()||!nativeMode)super.draw(canvas);}
            };
            host.setFocusable(false);host.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nativeView=new SurfaceView(context);nativeView.setFocusable(false);nativeView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nativeView.getHolder().setFormat(PixelFormat.RGBA_8888);nativeView.setVisibility(GONE);
            nativeView.getHolder().addCallback(new SurfaceHolder.Callback(){
                @Override public void surfaceCreated(SurfaceHolder holder){nativeSize();}
                @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){
                    int wanted=Math.max(1,Math.min(1280,host.getWidth())),height=Math.max(1,Math.round(wanted*host.getHeight()/(float)Math.max(1,host.getWidth())));
                    if(!nativeMode||w!=wanted||h!=height){nativeSize();return;}
                    presented=false;engine.attachNative(holder.getSurface(),w,h,++nativeGeneration);update();
                }
                @Override public void surfaceDestroyed(SurfaceHolder holder){int old=nativeGeneration++;engine.detachNative(old);}
            });
            host.addView(this,new FrameLayout.LayoutParams(-1,-1));host.addView(nativeView,new FrameLayout.LayoutParams(-1,-1));
        }
    }
    /** Root for wallpaper and preview hosts, including the separate native weather output. */
    View displayView(){return host==null?this:host;}
    boolean nativeOutput(){return nativeMode;}
    int outputGeneration(){return nativeGeneration;}
    private void nativeSize(){if(nativeView==null||host.getWidth()==0||host.getHeight()==0)return;int w=Math.min(1280,host.getWidth()),h=Math.max(1,Math.round(w*host.getHeight()/(float)host.getWidth()));nativeView.getHolder().setFixedSize(w,h);}
    void active(boolean value){if(value&&!active)presented=false;active=value;update();}
    void strength(int value){strength=Math.max(0,Math.min(100,value));update();}
    void weather(String key){weather(key,null);}
    void weather(String key,WeatherIllumination light){weather(key,light,WeatherAtmosphere.EMPTY);}
    void weather(String key,WeatherIllumination light,WeatherAtmosphere air){atmosphere=air;illumination=light;scene=key==null?"":key;boolean next=host!=null&&!scene.isEmpty();if(nativeMode!=next){nativeMode=next;presented=false;setVisibility(next?GONE:VISIBLE);nativeView.setVisibility(next?VISIBLE:GONE);}update();}
    void interaction(){interaction(200);}
    void interaction(int duration){engine.interaction(duration);}
    /** A one-shot acknowledgement after the queued settings have produced a frame. */
    void afterFrame(Runnable callback){engine.afterFrame(callback);}
    void image(Bitmap bitmap){
        if(destroyed)return;Bitmap owned=null;
        try{if(bitmap!=null)owned=bitmap.copy(Bitmap.Config.ARGB_8888,false);}catch(RuntimeException|OutOfMemoryError error){listener.failed("Background texture unavailable");return;}
        Bitmap old=retained;retained=owned;engine.image(owned);if(old!=null)engine.recycle(old);
    }
    private void update(){View view=displayView();if(!destroyed&&engine!=null)engine.settings(active&&view.hasWindowFocus()&&view.getWindowVisibility()==VISIBLE&&view.isShown(),scene,strength,illumination,atmosphere);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(engine!=null)update();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(engine!=null)update();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(engine!=null)update();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture output,int width,int height){presented=false;engine.attach(output,width,height);if(retained!=null)engine.image(retained);update();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture output,int width,int height){engine.resize(width,height);}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture output){SurfaceTexture input=engine.input;if(video!=null&&input!=null)video.destroyed(input);engine.detach(output);return false;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture output){SurfaceTexture input=engine.input;if(!destroyed&&!nativeMode&&engine.output==output&&engine.submitted){if(!presented){presented=true;listener.ready();}if(video!=null&&input!=null)video.presented(input);}}
    @Override public void setTransform(Matrix transform){if(video==null){super.setTransform(transform);return;}if(engine!=null)engine.transform(transform);}
    void sourceSnapshot(int width,int height,Capture callback){engine.sourceSnapshot(width,height,callback);}
    void backdropSnapshot(int width,int height,Capture callback){engine.backdropSnapshot(width,height,callback);}
    void releaseInput(SurfaceTexture texture){engine.releaseInput(texture);}
    void destroy(){if(destroyed)return;destroyed=true;active=false;engine.close();retained=null;}
    private final class Engine {
        private final Context context;
        private final HandlerThread thread=new HandlerThread("QuietWallpaperGpu");
        private final Handler worker;
        private Runnable frameReady;
        private android.opengl.EGLDisplay display=android.opengl.EGL14.EGL_NO_DISPLAY;
        private android.opengl.EGLContext eglContext=android.opengl.EGL14.EGL_NO_CONTEXT;
        private android.opengl.EGLSurface eglSurface=android.opengl.EGL14.EGL_NO_SURFACE;
        private Surface outputSurface;
        private Surface nativeSurface;
        private boolean ownsSurface,nativePresented,presentationTime,delayedPresentation;
        private int nativeEpoch;
        private volatile SurfaceTexture output;
        volatile SurfaceTexture input;
        volatile boolean submitted;
        private WallpaperGl gl;
        private WallpaperGpuTimings timings;
        private Choreographer clock;
        private boolean clockQueued;
        private long nextWeatherFrame,weatherFrameStamp,submittedWeatherStamp;
        private final Choreographer.FrameCallback weatherFrame=stamp->{clockQueued=false;if(stamp+500000L<nextWeatherFrame){queueWeatherFrame();return;}weatherFrameStamp=stamp;draw();};
        private ProgressiveWallpaperRenderer blur;
        private WeatherWallpaperRenderer sky;
        private WeatherCloudField cloudState;
        private WallpaperFrameReadback readback;
        private Capture pendingCapture;
        private int captureWidth,captureHeight;
        private boolean captureIssued;
        private final Runnable pollCapture=this::pollCapture;
        private WallpaperGl.Program external,snapshotCopy;
        private WallpaperGl.Target weatherTarget,videoTarget;
        private Bitmap image;
        private int imageTexture,externalTexture,width,height,viewWidth,viewHeight,blurAmount;
        private boolean visible,closed,failed,framePending,hasVideoFrame,ready;
        private volatile boolean inputPublished;
        private String weather="";
        private WeatherIllumination lighting;
        private WeatherAtmosphere observations=WeatherAtmosphere.EMPTY;
        private final float[] textureMatrix=new float[16],imageMatrix={1,0,0,0,1,0,0,0,1};
        private final Matrix videoTransform=new Matrix();
        private long lastTick;
        private long weatherFence;
        private long weatherFrameStartedAt;
        private boolean weatherCloudUpdated;
        private final WeatherFrameBudget frameBudget=new WeatherFrameBudget();
        private int weatherQuality=rememberWeatherQuality(0);
        private long interactionUntil;
        private double time;
        private final Runnable draw=this::draw;
        Engine(Context context){this.context=context;thread.start();worker=new Handler(thread.getLooper());worker.post(()->clock=Choreographer.getInstance());}
        void recycle(Bitmap bitmap){worker.post(()->{if(image!=bitmap&&!bitmap.isRecycled())bitmap.recycle();});}
        void afterFrame(Runnable callback){worker.post(()->{frameReady=callback;if(callback!=null)request();});}
        void image(Bitmap bitmap){worker.post(()->{if(closed)return;image=bitmap;upload();request();});}
        private void upload(){if(gl==null)return;if(imageTexture!=0){gl.removeTexture(imageTexture);imageTexture=0;}if(image!=null&&!image.isRecycled())imageTexture=gl.bitmap(image);if(blur!=null)blur.sourceChanged();}
        void settings(boolean active,String key,int amount,WeatherIllumination light,WeatherAtmosphere air){worker.post(()->{if(closed)return;boolean changed=!weather.equals(key);visible=active;if(changed||!visible){cancelCapture();if(!visible)nativePresented=false;}if(changed){lastTick=0;weatherFrameStartedAt=0;frameBudget.reset();if(weather.isEmpty()&&!key.isEmpty()){if(sky!=null)sky.restart();}}weather=key;lighting=light;observations=air;blurAmount=amount;if(!visible){cancelDraw();lastTick=0;weatherFrameStartedAt=0;frameBudget.reset();return;}if(sky!=null&&!weather.isEmpty())try{sky.atmosphere(observations);sky.illumination(lighting);if(changed)sky.scene(weather);}catch(Exception error){fail(error);}request();});}
        void transform(Matrix transform){Matrix copy=transform==null?new Matrix():new Matrix(transform);worker.post(()->{videoTransform.set(copy);request();});}
        void attach(SurfaceTexture texture,int w,int h){worker.post(()->{if(closed){texture.release();return;}teardown();output=texture;viewWidth=w;viewHeight=h;failed=ready=submitted=false;try{create();upload();request();}catch(Exception|OutOfMemoryError error){fail(error);}});}
        void attachNative(Surface surface,int w,int h,int epoch){worker.post(()->{
            if(closed||epoch!=nativeGeneration||!nativeMode)return;
            teardown();output=null;nativeSurface=surface;nativeEpoch=epoch;viewWidth=w;viewHeight=h;failed=ready=submitted=false;
            try{create();upload();request();}catch(Exception|OutOfMemoryError error){fail(error);}
        });}
        void detachNative(int epoch){
            CountDownLatch detached=new CountDownLatch(1);
            if(!worker.post(()->{try{if(nativeSurface!=null&&nativeEpoch==epoch){teardown();nativeSurface=null;}}finally{detached.countDown();}}))return;
            try{if(!detached.await(3,TimeUnit.SECONDS))DiagnosticLog.info("wallpaper.gpu.detach_timeout","native output detachment unconfirmed");}
            catch(InterruptedException error){Thread.currentThread().interrupt();}
        }
        void resize(int w,int h){worker.post(()->{viewWidth=w;viewHeight=h;size();if(blur!=null)blur.sourceChanged();request();});}
        void detach(SurfaceTexture texture){worker.post(()->{if(output==texture){teardown();output=null;}texture.release();});}
        void releaseInput(SurfaceTexture texture){if(!worker.post(texture::release))texture.release();}
        void close(){worker.post(()->{closed=true;visible=false;frameReady=null;SurfaceTexture old=output;teardown();output=null;if(old!=null)old.release();if(image!=null&&!image.isRecycled())image.recycle();image=null;thread.quitSafely();});}
        private void size(){width=Math.max(1,Math.min(1920,viewWidth));height=Math.max(1,Math.round(width*viewHeight/(float)Math.max(1,viewWidth)));if(output!=null)output.setDefaultBufferSize(width,height);}
        private void create()throws IOException{
            size();display=android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY);int[] versions=new int[2];if(display==android.opengl.EGL14.EGL_NO_DISPLAY||!android.opengl.EGL14.eglInitialize(display,versions,0,versions,1))throw new IOException("GLES display unavailable");
            int[] attributes={android.opengl.EGL14.EGL_RED_SIZE,8,android.opengl.EGL14.EGL_GREEN_SIZE,8,android.opengl.EGL14.EGL_BLUE_SIZE,8,android.opengl.EGL14.EGL_ALPHA_SIZE,8,android.opengl.EGL14.EGL_RENDERABLE_TYPE,0x40,android.opengl.EGL14.EGL_SURFACE_TYPE,android.opengl.EGL14.EGL_WINDOW_BIT,android.opengl.EGL14.EGL_NONE};android.opengl.EGLConfig[] configurations=new android.opengl.EGLConfig[1];int[] count={0};
            if(!android.opengl.EGL14.eglChooseConfig(display,attributes,0,configurations,0,1,count,0)||count[0]==0)throw new IOException("GLES3 output unavailable");
            eglContext=android.opengl.EGL14.eglCreateContext(display,configurations[0],android.opengl.EGL14.EGL_NO_CONTEXT,new int[]{android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION,3,android.opengl.EGL14.EGL_NONE},0);ownsSurface=output!=null;outputSurface=ownsSurface?new Surface(output):nativeSurface;eglSurface=android.opengl.EGL14.eglCreateWindowSurface(display,configurations[0],outputSurface,new int[]{android.opengl.EGL14.EGL_NONE},0);if(eglContext==android.opengl.EGL14.EGL_NO_CONTEXT||eglSurface==android.opengl.EGL14.EGL_NO_SURFACE||!android.opengl.EGL14.eglMakeCurrent(display,eglSurface,eglSurface,eglContext))throw new IOException("GLES3 context unavailable");
            String extensions=android.opengl.EGL14.eglQueryString(display,android.opengl.EGL14.EGL_EXTENSIONS);presentationTime=extensions!=null&&extensions.contains("EGL_ANDROID_presentation_time");
            gl=new WallpaperGl(context);timings=new WallpaperGpuTimings(context);if(video!=null){external=gl.new Program("vertex.glsl","external-copy.glsl");externalTexture=gl.texture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES);SurfaceTexture source=new SurfaceTexture(externalTexture);input=source;inputPublished=false;source.setOnFrameAvailableListener(st->{framePending=true;request();},worker);main.post(()->{if(!destroyed&&input==source){inputPublished=true;video.available(source,viewWidth,viewHeight);}else releaseInput(source);});}
        }
        void interaction(int duration){worker.post(()->{if(closed||weather.isEmpty())return;interactionUntil=SystemClock.uptimeMillis()+duration;weatherFrameStartedAt=0;request();});}
        private void cancelDraw(){worker.removeCallbacks(draw);if(clockQueued){clock.removeFrameCallback(weatherFrame);clockQueued=false;}nextWeatherFrame=0;}
        private void queueWeatherFrame(){if(!clockQueued&&!closed&&!failed&&visible&&gl!=null){clockQueued=true;clock.postFrameCallback(weatherFrame);}}
        private void scheduleDraw(long delay){if(!weather.isEmpty()){nextWeatherFrame=System.nanoTime()+delay*1000000L;queueWeatherFrame();}else worker.postDelayed(draw,delay);}
        private void request(){if(!closed&&!failed&&visible&&gl!=null){if(!weather.isEmpty())queueWeatherFrame();else{cancelDraw();worker.post(draw);}}}
        private void draw(){
            if(closed||failed||!visible||gl==null||output==null&&nativeSurface==null||nativeMode!=(nativeSurface!=null)||nativeSurface!=null&&nativeEpoch!=nativeGeneration)return;long started=SystemClock.elapsedRealtimeNanos();
            try{
                long defer=interactionUntil-SystemClock.uptimeMillis();
                if(!weather.isEmpty()&&defer>0){scheduleDraw(defer);return;}
                if(weatherFence!=0){
                    int status=android.opengl.GLES30.glClientWaitSync(weatherFence,0,0);
                    if(status==android.opengl.GLES30.GL_TIMEOUT_EXPIRED){scheduleDraw(0);return;}
                    if(status==android.opengl.GLES30.GL_WAIT_FAILED)throw new IOException("Weather frame fence failed");
                    android.opengl.GLES30.glDeleteSync(weatherFence);weatherFence=0;
                    nativeReady();
                    if(sky!=null&&weatherFrameStartedAt!=0){
                        sky.completed(weatherQuality,submittedWeatherStamp,started-weatherFrameStartedAt);
                        int next=frameBudget.quality(weatherQuality,started-weatherFrameStartedAt,weatherCloudUpdated,started);
                        if(next!=weatherQuality){weatherQuality=next;recoveredWeatherQuality(next);sky.quality(next);DiagnosticLog.info("wallpaper.weather.quality","completion budget quality="+next);}
                    }
                }
                timings.beginFrame(weather,weatherQuality);
                int texture=imageTexture;float[] crop={0,0,1,1};boolean animated=!weather.isEmpty();
                if(video!=null){if(framePending&&input!=null){input.updateTexImage();input.getTransformMatrix(textureMatrix);framePending=false;hasVideoFrame=true;}if(!hasVideoFrame)return;texture=videoImage();}
                else if(animated){if(sky==null){sky=new WeatherWallpaperRenderer(gl,timings);sky.cloudState(cloudState);weatherQuality=rememberWeatherQuality(weatherQuality);for(int q=0;q<weatherQuality;q++)sky.reduceLoad();sky.atmosphere(observations);sky.illumination(lighting);sky.scene(weather);if(weatherQuality>0)DiagnosticLog.info("wallpaper.weather.reduced","reuse quality="+weatherQuality);}if(lastTick!=0)time+=Math.min(.1,(started-lastTick)/1e9);lastTick=started;int w=Math.max(320,Math.min(weatherQuality>=3?480:weatherQuality==2?640:weatherQuality==1?960:1280,Math.round(width/16f)*16)),h=Math.max(1,Math.round(w*9f/16));int foregroundWidth=Math.min(width,1280),foregroundHeight=Math.max(1,Math.round(foregroundWidth*h/(float)w));if(weatherTarget==null||weatherTarget.w!=foregroundWidth||weatherTarget.h!=foregroundHeight){if(weatherTarget!=null)weatherTarget.destroy();weatherTarget=gl.new Target(foregroundWidth,foregroundHeight,1,false);}sky.render(time,w,h,foregroundWidth,foregroundHeight,weatherTarget.fbo);weatherCloudUpdated=sky.updatedCloud();texture=weatherTarget.textures[0];}
                else if(image!=null){float ratio=(image.getWidth()/(float)image.getHeight())/(width/(float)height);if(ratio>1){crop[0]=(1-1/ratio)/2;crop[2]=1/ratio;}else{crop[1]=(1-ratio)/2;crop[3]=ratio;}}
                if(texture==0)return;timings.stage("output");if(blur==null)blur=new ProgressiveWallpaperRenderer(gl);if(animated||video!=null)blur.sourceChanged();blur.render(texture,width,height,crop,blurAmount,1,0);
                if(pendingCapture!=null&&!captureIssued){
                    try{timings.stage("readback");if(readback==null)readback=new WallpaperFrameReadback(gl);readback.capture(width,height,captureWidth,captureHeight);captureIssued=true;worker.post(pollCapture);}
                    catch(RuntimeException|OutOfMemoryError error){DiagnosticLog.error("wallpaper.backdrop.capture_failed",error);cancelCapture();}
                }
                timings.endFrame();
                // Keep at most one weather frame in flight so a slow GPU does
                // not accumulate background work ahead of the interactive UI.
                if(animated||nativeSurface!=null&&!nativePresented){weatherFrameStartedAt=started;submittedWeatherStamp=weatherFrameStamp;weatherFence=android.opengl.GLES30.glFenceSync(android.opengl.GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(weatherFence==0)throw new IOException("Weather frame fence unavailable");}
                // High-cost tiers need an explicit 30 Hz presentation deadline.
                // Lightweight tiers already pace at swap; restore Android's
                // NATIVE_WINDOW_TIMESTAMP_AUTO when leaving the delayed mode.
                boolean delayPresentation=animated&&weatherQuality<2;
                if(presentationTime&&(delayPresentation||delayedPresentation)){
                    long stamp=delayPresentation?weatherFrameStamp+33333333L:Long.MIN_VALUE;
                    if(android.opengl.EGLExt.eglPresentationTimeANDROID(display,eglSurface,stamp))delayedPresentation=delayPresentation;
                    else{presentationTime=false;DiagnosticLog.info("wallpaper.gpu.presentation_time","hint disabled error="+android.opengl.EGL14.eglGetError());}
                }
                submitted=true;if(!android.opengl.EGL14.eglSwapBuffers(display,eglSurface))throw new IOException("GLES output interrupted");
                ready=true;
                Runnable confirmed=frameReady;frameReady=null;if(confirmed!=null)main.post(()->{if(!destroyed&&active)confirmed.run();});
                if(animated){nextWeatherFrame=weatherFrameStamp+33333333L;queueWeatherFrame();}else if(nativeSurface!=null&&!nativePresented)scheduleDraw(16);
            }catch(Exception|OutOfMemoryError error){fail(error);}finally{if(timings!=null)timings.endFrame();}
        }
        private void nativeReady(){
            if(nativeSurface==null||nativePresented)return;nativePresented=true;int epoch=nativeEpoch;
            main.post(()->{if(!destroyed&&nativeMode&&nativeGeneration==epoch&&active&&displayView().hasWindowFocus()){presented=true;listener.ready();}});
        }
        void backdropSnapshot(int w,int h,Capture callback){
            if(!worker.post(()->{
                if(closed||failed||!visible||!ready||gl==null||pendingCapture!=null){main.post(()->callback.ready(null));return;}
                pendingCapture=callback;captureWidth=w;captureHeight=h;captureIssued=false;if(weather.isEmpty())request();
            }))main.post(()->callback.ready(null));
        }
        private void pollCapture(){
            if(pendingCapture==null)return;
            try{
                Bitmap image=readback.takeIfReady();
                if(image==null){worker.postDelayed(pollCapture,16);return;}
                Capture callback=pendingCapture;pendingCapture=null;captureIssued=false;
                main.post(()->callback.ready(image));
            }catch(RuntimeException|OutOfMemoryError error){DiagnosticLog.error("wallpaper.backdrop.capture_failed",error);cancelCapture();}
        }
        private void cancelCapture(){
            worker.removeCallbacks(pollCapture);if(readback!=null)readback.cancel();captureIssued=false;
            Capture callback=pendingCapture;pendingCapture=null;if(callback!=null)main.post(()->callback.ready(null));
        }
        private int videoImage(){
            if(videoTarget==null||videoTarget.w!=width||videoTarget.h!=height){if(videoTarget!=null)videoTarget.destroy();videoTarget=gl.new Target(width,height,1,false);}videoTarget.bind();external.use();gl.bind(0,android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES,externalTexture);external.i("source",0);android.opengl.GLES30.glUniformMatrix4fv(external.u("textureTransform"),1,false,textureMatrix,0);
            Matrix inverse=new Matrix();videoTransform.invert(inverse);float[] m=new float[9];inverse.getValues(m);float w=Math.max(1,viewWidth),h=Math.max(1,viewHeight);float[] normalized={m[0],m[3]*w/h,m[6]*w,m[1]*h/w,m[4],m[7]*h,m[2]/w,m[5]/h,m[8]};android.opengl.GLES30.glUniformMatrix3fv(external.u("imageTransform"),1,false,normalized,0);gl.draw();return videoTarget.textures[0];
        }
        void sourceSnapshot(int w,int h,Capture callback){
            if(!worker.post(()->{Bitmap bitmap=null;WallpaperGl.Target target=null,source=weather.isEmpty()?videoTarget:weatherTarget;
                try{if(!closed&&gl!=null&&source!=null){target=gl.new Target(w,h,1,false);target.bind();if(snapshotCopy==null)snapshotCopy=gl.new Program("vertex.glsl","copy.glsl");snapshotCopy.use();gl.bind(0,android.opengl.GLES30.GL_TEXTURE_2D,source.textures[0]);snapshotCopy.i("source",0);snapshotCopy.f("crop",0,0,1,1);gl.draw();java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocateDirect(w*h*4);android.opengl.GLES30.glReadPixels(0,0,w,h,android.opengl.GLES30.GL_RGBA,android.opengl.GLES30.GL_UNSIGNED_BYTE,bytes);gl.check();byte[] raw=new byte[w*h*4];bytes.position(0);bytes.get(raw);int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=(y*w+x)*4;pixels[(h-1-y)*w+x]=0xff000000|((raw[p]&255)<<16)|((raw[p+1]&255)<<8)|(raw[p+2]&255);}bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);}}
                catch(Exception|OutOfMemoryError error){DiagnosticLog.error("wallpaper.gpu.snapshot_failed",error);}finally{if(target!=null)target.destroy();}
                Bitmap result=bitmap;main.post(()->callback.ready(result));
            }))main.post(()->callback.ready(null));
        }
        private void fail(Throwable error){if(nativeSurface!=null&&(nativeEpoch!=nativeGeneration||!nativeMode)){cancelDraw();cancelCapture();return;}if(failed)return;failed=true;cancelDraw();cancelCapture();DiagnosticLog.error("wallpaper.gpu.failed",error);main.post(()->{if(!destroyed){listener.failed(error.getMessage()==null?"GLES effect unavailable":error.getMessage());if(video!=null)video.failed(error.getMessage());}});}
        private void teardown(){
            cancelDraw();lastTick=0;framePending=false;submitted=hasVideoFrame=false;delayedPresentation=false;
            cancelCapture();if(readback!=null){readback.destroy();readback=null;}
            if(weatherFence!=0){android.opengl.GLES30.glDeleteSync(weatherFence);weatherFence=0;}
            frameBudget.reset();interactionUntil=0;weatherFrameStartedAt=0;
            if(input!=null){try{input.setOnFrameAvailableListener(null);input.detachFromGLContext();}catch(RuntimeException ignored){}if(!inputPublished)input.release();input=null;}
            if(timings!=null){timings.destroy();timings=null;}if(gl!=null)gl.destroy();gl=null;blur=null;if(sky!=null)cloudState=sky.cloudState();sky=null;weatherTarget=videoTarget=null;imageTexture=externalTexture=0;external=snapshotCopy=null;
            if(display!=android.opengl.EGL14.EGL_NO_DISPLAY){android.opengl.EGL14.eglMakeCurrent(display,android.opengl.EGL14.EGL_NO_SURFACE,android.opengl.EGL14.EGL_NO_SURFACE,android.opengl.EGL14.EGL_NO_CONTEXT);if(eglSurface!=android.opengl.EGL14.EGL_NO_SURFACE)android.opengl.EGL14.eglDestroySurface(display,eglSurface);if(eglContext!=android.opengl.EGL14.EGL_NO_CONTEXT)android.opengl.EGL14.eglDestroyContext(display,eglContext);android.opengl.EGL14.eglReleaseThread();android.opengl.EGL14.eglTerminate(display);}eglSurface=android.opengl.EGL14.EGL_NO_SURFACE;eglContext=android.opengl.EGL14.EGL_NO_CONTEXT;display=android.opengl.EGL14.EGL_NO_DISPLAY;if(outputSurface!=null){if(ownsSurface)outputSurface.release();outputSurface=null;}nativeSurface=null;nativePresented=false;
        }
    }
}
