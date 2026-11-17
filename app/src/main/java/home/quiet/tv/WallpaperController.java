package home.quiet.tv;

import android.animation.ValueAnimator;
import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Native full-screen picker with separate preview and committed wallpaper identities. */
final class WallpaperController implements GlassStyle.Listener {
    interface Message {void show(String text);}
    private Message messages;
    interface PreviewResult {void result(long operation,String error);}
    private PreviewResult previewResult;
    private long previewOperation;
    private String previewSyncError="";
    private boolean previewQueued,previewDone;
    private Runnable previewDraw;

    private static final int CARD_RADIUS=16,CARD_INSET=5,CARD_STROKE=2;
    private final Activity activity;
    private final FrameLayout root,background;
    private final WallpaperStore store;
    private final DesktopSettingsStore desktopSettings;
    private final ScreensaverStore screensavers;
    private String temporaryMedia;
    private boolean temporarySound;
    private boolean temporaryWeather,temporaryDayNight;
    private final WallpaperBackdropCache backdropCache;
    private final WallpaperUploadPanel uploads;
    private TextView soundToggle,localFile,applySaver,saverTime,saverWeather;
    private final Map<Integer,TextView> saverWaits=new LinkedHashMap<>();
    private final DailyWallpaperController daily;
    private final float unit,homeUnit;
    private final WallpaperUi ui;
    private final boolean chinese,previewOnly;
    private String previewId,previewImageKey,previewGpuFailure="",gpuKind="",gpuImageKey="";
    private boolean previewWeather,previewDayNight=true,previewMuted=true,gpuRequested,gpuShowing;
    private WallpaperGpuView gpu;
    private boolean componentRemoving;
    private final home.quiet.components.ComponentRepository.Listener componentsChanged=this::componentChanged;
    private int ambientVersion;
    private Bitmap liveBackdrop,weatherStill;
    private final ImageView weatherCover;
    private String liveBackdropKey="",weatherStillKey="",phoneWeatherKey="",gpuWeatherKey="";
    private Bitmap phoneWeatherStill;private boolean weatherStillRaw;
    private boolean backdropQueued,backdropBusy;
    private long backdropAt;
    private final Runnable captureBackdrop=this::captureBackdrop;
    private Boolean progressiveOverride;
    private final Runnable weatherTick=new Runnable(){public void run(){if(active){updateGpuBackground();main.postDelayed(this,60000-System.currentTimeMillis()%60000);}}};
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final ImageView current,incoming;
    private final View shade;
    private final Ambient ambient;
    private final VideoWallpaperPlayer video;
    private final WallpaperBlur wallpaperBlur;
    private final GlassStyle glassStyle;
    private final WallpaperAppearance appearance;
    private int desktopBlur,firstBlur,secondBlur;
    private String diagnosticBlurContext="";
    private float desktopProgress;
    private int desktopBrightness=100,desktopFade=600;
    private boolean fadePending,desktopRevealed;
    private String playingVideo="",failedVideo="";
    private final Set<String> failedVideos=new HashSet<>();
    private long manualVersion=-1;
    private VideoWallpaperPlayer.Failure videoFailure=VideoWallpaperPlayer.Failure.UNKNOWN;
    private int videoRequest;
    private boolean commitPending,leaving,destroyed,restoreDesktopPending;
    private int rotationCommit;
    private PopupWindow rotationMenu;
    private Bitmap restoredCover;
    private boolean dynamicTab;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(6*1024){@Override protected int sizeOf(String id,Bitmap bitmap){return bitmap.getByteCount()/1024;}};
    private final Map<String,View> cards=new LinkedHashMap<>();
    private final Map<View,Integer> pickerVisibility=new LinkedHashMap<>();
    private Bitmap entry,currentBlur,incomingBlur;
    private Runnable dockInvalidation,backdropInvalidation;
    private boolean backdropPending;
    private final RectF dockScreen=new RectF();
    private volatile int request;
    private int saverApplyVersion;
    private int pickerVersion,themeColor=0xff557eac;
    private String preview,shown,shownKey;
    private boolean active;
    private Dialog picker,confirmation;
    private FrameLayout content;
    private LinearLayout rail,actions;
    private HorizontalScrollView scroll;
    private View pendingCardFocus;
    private TextView title,subtitle,auto,upload,staticTab,videoTab,dynamicToggle;
    private String pressedWallpaper;
    private int pressedKey=KeyEvent.KEYCODE_UNKNOWN;
    private long pressedAt;
    private boolean longPressed;
    private final Runnable holdCard=()->{
        View card=cards.get(pressedWallpaper);
        if(card==null||!card.hasFocus()||picker==null||!picker.isShowing())return;
        longPressed=true;confirmDelete(pressedWallpaper,pressedKey,pressedAt);
    };
    private ValueAnimator transition,videoFade;
    private Runnable pendingCover,coverCommit,pendingVideoStart;
    private ViewTreeObserver coverTree;
    private Runnable coverDraw,startupDraw;
    private final Runnable rotate=this::rotateWallpaper;
    private void rotateWallpaper(){
        WallpaperCatalog state=store.snapshot();if(previewOnly||!active||temporaryMedia!=null||picker!=null||!state.rotating)return;
        if(state.dynamic()){String next=state.nextPlayableVideo(state.current,failedVideos);if(next==null||next.equals(state.current)){schedule();return;}startVideo(next,true,state.rotationMinutes);return;}
        try{store.next();display(store.snapshot().current,true);}catch(IOException e){notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}schedule();
    }

    WallpaperController(Activity activity,FrameLayout root,float unit,boolean chinese){this(activity,root,unit,chinese,false);}
    WallpaperController(Activity activity,FrameLayout root,float unit,boolean chinese,boolean previewOnly){
        this.activity=activity;this.root=root;this.homeUnit=unit;this.unit=unit*960f/1040f;this.ui=new WallpaperUi(activity,this.unit);this.chinese=chinese;this.previewOnly=previewOnly;store=WallpaperStore.get(activity);screensavers=ScreensaverStore.get(activity);uploads=new WallpaperUploadPanel(activity,store,chinese);
        desktopSettings=new DesktopSettingsStore(activity);glassStyle=GlassStyle.get(activity);backdropCache=new WallpaperBackdropCache(activity);daily=new DailyWallpaperController(activity,store,chinese,this::dailyChanged);
        background=new FrameLayout(activity){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                if(getScaleX()==2f){
                    widthSpec=View.MeasureSpec.makeMeasureSpec((View.MeasureSpec.getSize(widthSpec)+1)/2,View.MeasureSpec.getMode(widthSpec));
                    heightSpec=View.MeasureSpec.makeMeasureSpec((View.MeasureSpec.getSize(heightSpec)+1)/2,View.MeasureSpec.getMode(heightSpec));
                }
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        background.setPivotX(0);background.setPivotY(0);
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        // Compose video and cover in the same View frame; ready waits for the texture update.
        video=new VideoWallpaperPlayer(activity,background,true);
        current=image();current.setBackgroundColor(Color.BLACK);incoming=image();incoming.setAlpha(0f);background.addView(current,new FrameLayout.LayoutParams(-1,-1));background.addView(incoming,new FrameLayout.LayoutParams(-1,-1));
        weatherCover=image();weatherCover.setVisibility(View.GONE);background.addView(weatherCover,new FrameLayout.LayoutParams(-1,-1));
        if(previewOnly&&store.snapshot().weather){weatherStill=activity.getIntent().getParcelableExtra("backdrop");if(weatherStill!=null)weatherCover.setImageBitmap(weatherStill);}
        if(WallpaperGpuSupport.available(activity))createGpu();
        shade=new View(activity);shade.setBackgroundDrawable(WallpaperAppearance.desktopShade());background.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ambient=new Ambient(activity,homeUnit);ambient.colorChanged=()->{++ambientVersion;invalidateDock();};background.addView(ambient,new FrameLayout.LayoutParams(-1,-1));ambient.setColor(themeColor);ambient.setVisibility(View.VISIBLE);
        wallpaperBlur=new WallpaperBlur(background,(width,height,result)->{
            if(video.visible()&&video.ready()&&current.getAlpha()<1f){
                // Before PixelCopy, native output can only blur its existing poster.
                if(!video.snapshotSupported()){
                    result.ready(blurVideoFrame(width,height,null));return;
                }
                video.snapshot(width,height,frame->{
                    try{result.ready(blurVideoFrame(width,height,frame));}finally{if(frame!=null)frame.recycle();}
                });
            }else if(gpuShowing&&gpu!=null){gpu.backdropSnapshot(width,height,frame->{try{result.ready(frame==null?null:WallpaperBlur.snapshot(width,height,frame,shade));}finally{if(frame!=null)frame.recycle();}});}
            else result.ready(WallpaperBlur.snapshot(width,height,null,current,incoming,shade,ambient));
        });video.observeFrames(()->{wallpaperBlur.invalidate();checkPreview();});
        appearance=new WallpaperAppearance(background,this::invalidateDock);
        String id=store.snapshot().current;
        android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
        String sourceKey=WallpaperBackdropCache.key(store,id,screen.widthPixels,screen.heightPixels);
        WallpaperBackdropCache.Picture cached=WallpaperCatalog.DEFAULT.equals(id)?null:WallpaperBackdropCache.prepared(sourceKey);
        if(cached==null&&!WallpaperCatalog.DEFAULT.equals(id)&&!previewOnly)cached=backdropCache.read(sourceKey,glassStyle.rendering());
        if(cached!=null){
            restoredCover=cached.fullResolution?null:cached.image;
            // The cached cover is a startup placeholder, not the full-resolution static image.
            current.setImageBitmap(cached.image);currentBlur=glassStyle.rendering()?cached.blur:null;shown=id;ambient.setVisibility(View.INVISIBLE);
            WallpaperCatalog.Entry selected=store.snapshot().find(id);
            if(selected!=null&&selected.video())shownKey=store.imageKey(id);
            // Install before the window's first draw; the regular cover takes over immediately.
            BitmapDrawable backgroundImage=new BitmapDrawable(activity.getResources(),cached.image);backgroundImage.setGravity(Gravity.FILL);
            activity.getWindow().setBackgroundDrawable(new LayerDrawable(new Drawable[]{backgroundImage,WallpaperAppearance.desktopShade()}));
            startupDraw=UiCompat.onDraw(root,()->root.post(this::finishStartupCover));
            DiagnosticLog.info("wallpaper.backdrop.restored","prepared cover");
        }
        display(id,false);
        glassStyle.observe(this);
        home.quiet.components.ComponentRepository.get(activity).observe(componentsChanged);
    }
    private void createGpu(){
        gpu=new WallpaperGpuView(activity,new WallpaperGpuView.Listener(){public void ready(){if(gpuRequested&&!destroyed){gpuShowing=true;gpu.displayView().setAlpha(1f);weatherCover.setVisibility(View.GONE);ambient.setVisibility(View.INVISIBLE);if(gpuKind.equals("weather"))shade.setVisibility(View.VISIBLE);updateBlurSource();if(dockInvalidation!=null)dockInvalidation.run();checkPreview();}}public void failed(String detail){previewGpuFailure=gpuKind;gpuRequested=false;hideGpu();if(active)notice(tr("背景效果暂不可用，已使用静态背景","Background effect unavailable; using the static background"));}});
        gpu.displayView().setAlpha(0f);gpu.displayView().setVisibility(View.GONE);background.addView(gpu.displayView(),background.indexOfChild(weatherCover)+1,new FrameLayout.LayoutParams(-1,-1));
    }
    private void componentChanged(){
        if(destroyed)return;org.json.JSONObject state=home.quiet.components.ComponentRepository.get(activity).state("weather");
        boolean removing=state.optString("state").equals("removing")||state.optString("state").equals("remove_failed");
        if(removing&&!componentRemoving){
            componentRemoving=true;temporaryWeather=previewWeather=false;gpuRequested=gpuShowing=false;gpuKind=gpuImageKey=gpuWeatherKey=previewGpuFailure="";
            weatherCover.setImageDrawable(null);weatherCover.setVisibility(View.GONE);weatherStill=null;weatherStillKey="";
            if(phoneWeatherStill!=null){phoneWeatherStill.recycle();phoneWeatherStill=null;}phoneWeatherKey="";
            if(gpu!=null){WallpaperGpuView old=gpu;gpu=null;background.removeView(old.displayView());old.destroy();createGpu();}
            settingsChanged();
        }else if(!removing&&componentRemoving){componentRemoving=false;settingsChanged();}
    }
    private void finishStartupCover(){if(startupDraw!=null){startupDraw.run();startupDraw=null;activity.getWindow().setBackgroundDrawable(null);}}
    private int px(float value){return Math.round(value*unit);}
    private String tr(String zh,String en){return chinese?zh:en;}
    void observeMessages(Message listener){messages=listener;}
    private void notice(String message){if(previewOnly&&!message.isEmpty())previewFailed(message);if(messages!=null)messages.show(message);else if(!message.isEmpty())Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();}
    void setThemeColor(int color){themeColor=color;if(ambient.getVisibility()==View.VISIBLE)ambient.setColor(color);}
    void observeDock(Runnable invalidation){dockInvalidation=invalidation;}
    MovieWallpaperFeed displayedMovie(){
        return temporaryMedia==null&&!(previewOnly?previewWeather:store.weatherEnabled())&&WallpaperCatalog.DAILY.equals(selectedId())&&WallpaperCatalog.DAILY.equals(shown)?store.displayedMovie(shownKey):null;
    }
    void observeBackdrop(Runnable invalidation){backdropInvalidation=invalidation;}
    // Source commits mark one pending snapshot; reveal frames only flush it when stable.
    private void backdropReady(){backdropPending=true;flushBackdrop();}
    private void flushBackdrop(){if(destroyed||!backdropPending||appearance==null||!appearance.revealed()||incomingBlur!=null)return;backdropPending=false;if(backdropInvalidation!=null)backdropInvalidation.run();}
    private void invalidateDock(){updateGpuBackground();checkPreview();if(dockInvalidation!=null)dockInvalidation.run();if(wallpaperBlur!=null)wallpaperBlur.invalidate();flushBackdrop();}
    private Bitmap blurVideoFrame(int width,int height,Bitmap frame){
        if(DiagnosticProbe.allow("wallpaper.frame_source",1000))DiagnosticProbe.observed("wallpaper.blur.source","source="+(frame!=null?"video_snapshot":"poster_fallback")+" frame="+(frame!=null?"present":"null")+" outputGeneration="+video.outputGeneration()+" preview="+(picker!=null)+" wallpaper="+VideoDiagnostics.wallpaper(shown));
        if(frame!=null)return WallpaperBlur.snapshot(width,height,frame,current,incoming,shade,ambient);
        // A playing TextureView may not allow readback. Its hidden cover is still a valid blur source.
        Bitmap poster=current.getDrawable() instanceof BitmapDrawable?((BitmapDrawable)current.getDrawable()).getBitmap():null;
        return poster==null?null:WallpaperBlur.snapshot(width,height,poster,shade,ambient);
    }
    void setDesktopEffects(int blur,int brightness,int fade){setDesktopEffects(blur,blur,0,brightness,fade);}
    void setDesktopEffects(int first,int second,float progress,int brightness,int fade){firstBlur=first;secondBlur=second;desktopProgress=progress;desktopBlur=Math.round(first+(second-first)*progress);desktopBrightness=brightness;desktopFade=fade;updateDesktopEffects();}
    private long interactionUntil;
    void interaction(){interaction(200);}
    void interaction(int duration){interactionUntil=SystemClock.uptimeMillis()+duration;wallpaperBlur.interaction();if(gpu!=null&&weatherSelected())gpu.interaction(duration);}
    private void updateDesktopEffects(){
        boolean separate=temporaryMedia!=null||picker!=null;boolean progressive=progressive();
        String context="scope="+(temporaryMedia!=null?"temporary_media":picker!=null?"picker_preview":"desktop")+" configuredFirst="+firstBlur+" configuredSecond="+secondBlur+" appliedFirst="+(separate?0:firstBlur)+" appliedSecond="+(separate?0:secondBlur);
        if(!context.equals(diagnosticBlurContext)){diagnosticBlurContext=context;DiagnosticLog.info("wallpaper.blur.context",context+" progress="+desktopProgress+" persistence=caller_values");}
        // Filter the moving texture at half size; sharp wallpaper and all foreground UI stay full size.
        float scale=Build.VERSION.SDK_INT>=31&&!separate&&video.visible()&&!video.nativeOutput()&&!progressive&&desktopBlur>0?2f:1f;
        if(background.getScaleX()!=scale){background.setScaleX(scale);background.setScaleY(scale);background.requestLayout();}
        // Reuse both page caches for live video; compiling blur filters during scroll stalls the render thread.
        updateBlurSource();
        video.progressive(!separate&&progressive,Math.round(firstBlur*(1-desktopProgress)));if(separate)wallpaperBlur.amount(0);else wallpaperBlur.blend(progressive?0:firstBlur,secondBlur,desktopProgress);updateGpuBackground();appearance.brightness(separate?100:desktopBrightness);if(separate){fadePending=false;appearance.finish();}
    }
    private void updateBlurSource(){
        if(wallpaperBlur==null)return;
        boolean nativeWeather=active&&gpuRequested&&gpu!=null&&gpu.nativeOutput();
        wallpaperBlur.nativeVideo(nativeWeather||video.visible()&&video.ready()&&!progressive(),nativeWeather?-1-gpu.outputGeneration():video.outputGeneration());
    }
    @Override public void glassChanged(){
        if(destroyed)return;
        if(transition!=null){transition.end();transition=null;}
        ++request;main.removeCallbacks(captureBackdrop);backdropQueued=false;
        currentBlur=incomingBlur=null;if(liveBackdrop!=null){liveBackdrop.recycle();liveBackdrop=null;}liveBackdropKey="";
        if(active)restoreSelected();
        if(dockInvalidation!=null)dockInvalidation.run();checkPreview();
    }
    private boolean progressive(){return WallpaperGpuSupport.available(activity)&&(progressiveOverride==null?"progressive".equals(desktopSettings.homeBlurMode()):progressiveOverride);}
    void previewProgressive(Boolean value){progressiveOverride=value;updateDesktopEffects();}
    /** Share prepared pixels without retaining HOME or starting its media players. */
    void seedPreview(WallpaperController source){
        if(!previewOnly||source==null||source.destroyed||source.temporaryMedia!=null||source.picker!=null)return;
        String id=store.currentId(),key=store.imageKey(id);
        if(weatherSelected()&&source.weatherStill!=null&&source.weatherStillKey.equals(source.weatherPreviewKey())){weatherStill=source.weatherStill;weatherStillKey=source.weatherStillKey;weatherStillRaw=source.weatherStillRaw;weatherCover.setImageBitmap(weatherStill);showWeatherCover();}
        if(!id.equals(source.shown)||!key.equals(source.shownKey)||!(source.current.getDrawable() instanceof BitmapDrawable))return;
        WallpaperCatalog.Entry entry=store.entry(id);if((entry==null||!entry.video())&&source.currentBlur==null&&glassStyle.rendering())return;
        ++request;current.setImageBitmap(((BitmapDrawable)source.current.getDrawable()).getBitmap());currentBlur=glassStyle.rendering()?source.currentBlur:null;shown=id;shownKey=key;
        ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
    }
    boolean previewCoverReady(){
        if(!previewSyncError.isEmpty())return true;
        if(active){
            if(gpuRequested&&!gpuShowing)return false;
            WallpaperCatalog.Entry selected=store.entry(selectedId());
            if(selected!=null&&selected.video()&&!weatherSelected()&&(!video.ready()||current.getAlpha()>0f))return false;
            if(!wallpaperBlur.previewReady())return false;
        }
        if(weatherSelected()&&weatherCover.getVisibility()==View.VISIBLE||gpuShowing&&weatherSelected())return true;
        String id=selectedId();WallpaperCatalog.Entry entry=store.entry(id);
        if(entry==null||WallpaperCatalog.DAILY.equals(id)&&!store.cached(store.snapshot().dailyImages.get(store.snapshot().dailySource)))return true;
        return id.equals(shown)&&store.imageKey(id).equals(shownKey)&&(WallpaperCatalog.DEFAULT.equals(id)||entry!=null&&entry.video()||!glassStyle.rendering()||currentBlur!=null);
    }
    private String selectedId(){return previewOnly&&previewId!=null?previewId:store.currentId();}
    private boolean weatherSelected(){return picker==null&&(temporaryMedia!=null?temporaryWeather:previewOnly?previewWeather:store.weatherEnabled())&&home.quiet.components.ComponentRepository.get(activity).isReady("weather");}
    private boolean audible(){WallpaperCatalog state=store.snapshot();return active&&(temporaryMedia!=null?temporarySound&&!temporaryWeather:!(previewOnly?previewMuted:state.weather||state.rotating||!state.sound));}
    void previewWallpaper(String id,boolean weather,boolean dayNight,boolean muted){
        if(!previewOnly)throw new IllegalStateException("Not a settings preview");String imageKey=store.imageKey(id);boolean changed=store.manualVersion()!=manualVersion||!java.util.Objects.equals(previewId,id)||!java.util.Objects.equals(previewImageKey,imageKey)||previewWeather!=weather||previewDayNight!=dayNight;refreshManualSelection();previewId=id;previewImageKey=imageKey;previewWeather=weather;previewDayNight=dayNight;previewMuted=muted;video.setSound(audible());if(changed){previewSyncError="";if(messages!=null)messages.show("");if(active){restoreSelected();updateDesktopEffects();daily.environmentChanged();}}
    }
    void awaitPreview(long operation,PreviewResult listener){
        cancelPreview();if(!previewGpuFailure.isEmpty()&&!weatherSelected()&&!(progressive()&&firstBlur>0))previewSyncError="";previewOperation=operation;previewResult=listener;checkPreview();
    }
    private void cancelPreview(){
        previewResult=null;previewQueued=false;previewDone=false;
        if(previewDraw!=null){previewDraw.run();previewDraw=null;}if(gpu!=null)gpu.afterFrame(null);
    }
    private void previewFailed(String error){
        previewSyncError=error;
        if(previewResult!=null&&!error.isEmpty()){previewDone=true;previewResult.result(previewOperation,error);}
    }
    private void checkPreview(){
        if(!previewOnly||previewResult==null||!active||destroyed||previewDone||previewQueued)return;
        if(!previewSyncError.isEmpty()){previewFailed(previewSyncError);return;}
        String id=selectedId();WallpaperCatalog.Entry selected=store.snapshot().find(id);
        if(weatherSelected()){
            WeatherData data=new WeatherStore(activity).cached(chinese);
            if(gpu==null||!previewGpuFailure.isEmpty()||data==null||!data.usable(System.currentTimeMillis())){previewFailed(tr("天气效果暂不可用，预览未同步","Weather preview unavailable"));return;}
            if(!gpuRequested||!gpuShowing)return;
        }else if(selected!=null&&selected.video()){
            if(id.equals(failedVideo)){previewFailed(videoFailure.hint(chinese));return;}
            if(!id.equals(playingVideo)||!video.ready())return;
        }else{
            if(selected==null&&!WallpaperCatalog.DEFAULT.equals(id)){previewFailed(tr("背景素材不可用","Background media unavailable"));return;}
            if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(store.snapshot().dailyImages.get(store.snapshot().dailySource))){previewFailed(tr("每日壁纸尚未就绪","Daily wallpaper is not ready"));return;}
            if(!id.equals(shown)||!store.imageKey(id).equals(shownKey)&&!WallpaperCatalog.DEFAULT.equals(id))return;
            if(!previewGpuFailure.isEmpty()&&progressive()&&firstBlur>0){previewFailed(tr("背景效果暂不可用，预览未同步","Background effect unavailable"));return;}
            if(gpuRequested&&!gpuShowing)return;
        }
        previewQueued=true;long operation=previewOperation;
        if(gpuRequested&&gpu!=null)gpu.afterFrame(()->previewDraw(operation));else previewDraw(operation);
    }
    private void previewDraw(long operation){
        if(previewResult==null||previewOperation!=operation||previewDone||!active)return;
        previewDraw=UiCompat.onDraw(root,()->root.post(()->{
            if(previewResult==null||previewOperation!=operation||previewDone||!active)return;
            if(previewDraw!=null){previewDraw.run();previewDraw=null;}previewDone=true;previewResult.result(operation,"");
        }));root.invalidate();
    }
    void previewFade(int millis){if(active)appearance.fadeIn(millis);}
    void previewFinish(){appearance.finish();}
    void weatherChanged(){updateGpuBackground();}
    private void showWeatherCover(){
        if(weatherStill==null||!weatherSelected())return;
        weatherCover.setVisibility(View.VISIBLE);ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
    }
    private void hideGpu(){
        if(gpu==null)return;gpu.active(false);gpu.displayView().setVisibility(View.GONE);gpu.displayView().setAlpha(0f);if(gpuShowing){gpuShowing=false;boolean plain=current.getDrawable()==null&&!video.ready()&&weatherCover.getVisibility()!=View.VISIBLE;ambient.setVisibility(plain?View.VISIBLE:View.INVISIBLE);if(plain)shade.setVisibility(View.INVISIBLE);}updateBlurSource();
    }
    private void updateGpuBackground(){
        if(gpu==null||destroyed||ambient==null)return;boolean separate=temporaryMedia!=null||picker!=null;boolean weather=weatherSelected();WeatherData weatherData=weather?new WeatherStore(activity).cached(chinese):null;if(weatherData==null||!weatherData.usable(System.currentTimeMillis()))weather=false;String kind=weather?"weather":"image";
        WallpaperCatalog.Entry entry=store.entry(selectedId());boolean picture=entry==null||!entry.video();boolean need=weather||!separate&&progressive()&&firstBlur>0&&desktopProgress<1&&picture;
        if(!weather)weatherCover.setVisibility(View.GONE);
        if(!weather&&picture&&!selectedId().equals(shown)){gpuRequested=false;hideGpu();return;}
        if(!need||previewGpuFailure.equals(kind)){gpuRequested=false;hideGpu();return;}
        if(weather&&!gpuShowing)showWeatherCover();
        // Losing focus to a translucent panel stops updates without destroying its visible Surface.
        if(!active){gpu.active(false);return;}
        gpuKind=kind;gpuRequested=true;gpu.displayView().setVisibility(View.VISIBLE);gpu.strength(!separate&&progressive()?Math.round(firstBlur*(1-desktopProgress)):0);
        if(weather){if(gpuShowing){ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);}boolean follow=temporaryMedia!=null?temporaryDayNight:previewOnly?previewDayNight:store.weatherDayNight();gpu.weather(WeatherWallpaperScene.key(weatherData.description,weatherData.icon,false,12),WeatherIllumination.at(System.currentTimeMillis(),weatherData.latitude,weatherData.longitude,follow),weatherData.atmosphere);gpuWeatherKey=weatherPreviewKey();}
        else{gpu.weather("");if(video.visible()&&!playingVideo.isEmpty())return;String key=System.identityHashCode(current.getDrawable())+":"+System.identityHashCode(incoming.getDrawable())+":"+current.getAlpha()+":"+incoming.getAlpha()+":"+ambientVersion;
            if(!key.equals(gpuImageKey)&&background.getWidth()>0){Bitmap image=null;try{if(current.getDrawable()!=null)image=WallpaperBlur.snapshot(Math.min(1280,background.getWidth()),Math.min(720,background.getHeight()),null,current,incoming);else{int w=Math.min(960,background.getWidth()),h=Math.max(1,Math.round(w*background.getHeight()/(float)background.getWidth()));image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.scale(w/(float)Math.max(1,ambient.getWidth()),h/(float)Math.max(1,ambient.getHeight()));ambient.draw(canvas);}gpu.image(image);gpuImageKey=key;}catch(RuntimeException|OutOfMemoryError error){DiagnosticLog.error("wallpaper.gpu.image_failed",error);previewGpuFailure=kind;gpuRequested=false;hideGpu();}finally{if(image!=null)image.recycle();}}}
        gpu.active(active);updateBlurSource();
    }
    private int fadeDuration(int original){return temporaryMedia!=null||picker!=null?original:desktopFade;}
    private void revealDesktop(boolean changed){if(!active||temporaryMedia!=null||picker!=null)return;if(fadePending||changed){fadePending=false;desktopRevealed=true;appearance.fadeIn(desktopFade);}}
    /** Sample the current background source, before desktop dimming and the phone's own blur. */
    void phoneBackground(WallpaperGpuView.Capture callback){
        if(destroyed||temporaryMedia!=null||picker!=null||root.getWidth()<1){callback.ready(null);return;}
        int width=Math.min(1280,root.getWidth()),height=Math.max(1,Math.round(width*root.getHeight()/(float)root.getWidth()));
        if(weatherSelected()){
            // The QR panel pauses HOME. Its unchanged native frame remains a valid preview.
            if(gpu!=null&&gpuWeatherKey.equals(weatherPreviewKey())){String sourceKey=weatherPreviewKey();gpu.sourceSnapshot(width,height,image->{if(destroyed||!sourceKey.equals(weatherPreviewKey())){if(image!=null)image.recycle();callback.ready(null);return;}if(image!=null){try{Bitmap cached=image.copy(Bitmap.Config.ARGB_8888,false);if(cached!=null){if(phoneWeatherStill!=null)phoneWeatherStill.recycle();phoneWeatherStill=cached;phoneWeatherKey=sourceKey;}}catch(OutOfMemoryError unavailable){}callback.ready(image);}else callback.ready(phoneWeatherFrame(width,height));});}
            else callback.ready(phoneWeatherFrame(width,height));
        }else if(video.visible()&&video.ready())video.snapshot(width,height,callback::ready);
        else callback.ready(WallpaperBlur.snapshot(width,height,null,current,incoming,ambient));
    }
    private Bitmap phoneWeatherFrame(int width,int height){String key=weatherPreviewKey();Bitmap source=phoneWeatherStill!=null&&phoneWeatherKey.equals(key)?phoneWeatherStill:weatherStill!=null&&weatherStillRaw&&weatherStillKey.equals(key)?weatherStill:null;if(source==null)return null;Bitmap image=Bitmap.createScaledBitmap(source,width,height,true);return image==source?image.copy(Bitmap.Config.ARGB_8888,false):image;}
    private String weatherPreviewKey(){WeatherStore weather=new WeatherStore(activity);WeatherData data=weather.cached(chinese);return weather.key(chinese)+":"+(data==null?0:data.fetchedAt)+":"+store.weatherDayNight()+":"+(data!=null&&data.usable(System.currentTimeMillis()));}
    void drawDockBackground(Canvas canvas,Paint paint){
        canvas.drawColor(Color.BLACK);
        if(!glassStyle.rendering())return;
        if(liveBackdropVisible()){
            requestBackdrop();
            if(liveBackdrop!=null&&liveBackdropKey.equals(backdropKey())){
                dockScreen.set(0,0,root.getWidth(),root.getHeight());paint.setAlpha(255);canvas.drawBitmap(liveBackdrop,null,dockScreen,paint);
                if(shade.getVisibility()==View.VISIBLE)shade.draw(canvas);appearance.drawShade(canvas);return;
            }
        }
        if(weatherSelected()&&weatherStill!=null){dockScreen.set(0,0,root.getWidth(),root.getHeight());paint.setAlpha(255);canvas.drawBitmap(weatherStill,null,dockScreen,paint);if(shade.getVisibility()==View.VISIBLE)shade.draw(canvas);appearance.drawShade(canvas);return;}
        if(ambient.getVisibility()==View.VISIBLE||gpuShowing&&current.getDrawable()==null){ambient.draw(canvas);appearance.drawShade(canvas);return;}
        dockScreen.set(0,0,root.getWidth(),root.getHeight());paint.setAlpha(255);
        if(currentBlur!=null)canvas.drawBitmap(currentBlur,null,dockScreen,paint);
        if(incomingBlur!=null){paint.setAlpha(Math.round(incoming.getAlpha()*255));canvas.drawBitmap(incomingBlur,null,dockScreen,paint);paint.setAlpha(255);}
        if(shade.getVisibility()==View.VISIBLE)shade.draw(canvas);
        appearance.drawShade(canvas);
    }
    private boolean liveBackdropVisible(){return active&&!destroyed&&root.isShown()&&root.hasWindowFocus()&&glassStyle.rendering()&&(gpuShowing&&weatherSelected()||video.visible()&&video.ready()&&current.getAlpha()<1f);}
    private String backdropKey(){return request+":"+videoRequest+":"+video.outputGeneration()+":"+(gpuShowing&&weatherSelected());}
    private void requestBackdrop(){if(backdropQueued||backdropBusy||!liveBackdropVisible())return;backdropQueued=true;main.postDelayed(captureBackdrop,Math.max(0,Math.max(backdropAt+100,interactionUntil)-SystemClock.uptimeMillis()));}
    private void captureBackdrop(){
        backdropQueued=false;if(!liveBackdropVisible()||root.getWidth()==0)return;backdropBusy=true;backdropAt=SystemClock.uptimeMillis();String key=backdropKey();
        int width=Math.min(320,root.getWidth()),height=Math.max(1,Math.round(width*root.getHeight()/(float)root.getWidth()));String weatherKey=weatherPreviewKey();boolean raw=!progressive()||Math.round(firstBlur*(1-desktopProgress))==0;
        if(gpuShowing&&weatherSelected()){
            gpu.backdropSnapshot(width,height,image->prepareBackdrop(image,key,weatherKey,raw));
        }else video.snapshot(width,height,image->prepareBackdrop(image,key,weatherKey,raw));
    }
    private void prepareBackdrop(Bitmap image,String key,String weatherKey,boolean raw){
        if(image==null||!liveBackdropVisible()||!key.equals(backdropKey())){if(image!=null)image.recycle();backdropBusy=false;return;}
        boolean weatherFrame=gpuShowing&&weatherSelected();
        decoder.execute(()->{
            if(!glassStyle.rendering()||!key.equals(backdropKey())){main.post(()->{image.recycle();backdropBusy=false;if(glassStyle.rendering()&&dockInvalidation!=null)dockInvalidation.run();});return;}
            Bitmap blurred=null;try{blurred=DockBackdropDrawable.prepare(image,root.getWidth(),root.getHeight(),glassStyle);}catch(RuntimeException|OutOfMemoryError error){DiagnosticLog.error("wallpaper.backdrop.filter_failed",error);}
            Bitmap ready=blurred;main.post(()->{backdropBusy=false;if(!liveBackdropVisible()||!key.equals(backdropKey())){image.recycle();if(ready!=null)ready.recycle();return;}if(weatherFrame){weatherStill=image;weatherStillKey=weatherKey;weatherStillRaw=raw;weatherCover.setImageBitmap(image);}else image.recycle();if(ready==null)return;boolean changed=!key.equals(liveBackdropKey);liveBackdrop=ready;liveBackdropKey=key;if(dockInvalidation!=null)dockInvalidation.run();if(changed)backdropReady();});
        });
    }
    private ImageView image(){ImageView view=new ImageView(activity);view.setScaleType(ImageView.ScaleType.CENTER_CROP);androidx.core.view.ViewCompat.setImportantForAccessibility(view,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;}
    private void cacheBackdrop(String id,Bitmap image,Bitmap blur){
        if(!glassStyle.rendering()||image==null||blur==null||image==restoredCover||!id.equals(store.snapshot().current))return;
        if(id.equals(shown)&&!store.imageKey(id).equals(shownKey))return;
        restoredCover=null;
        android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();int width=screen.widthPixels,height=screen.heightPixels;
        String key=WallpaperBackdropCache.key(store,id,width,height);int styleRevision=glassStyle.revision();
        decoder.execute(()->{
            if(!glassStyle.valid(styleRevision)||!id.equals(store.snapshot().current)||!key.equals(WallpaperBackdropCache.key(store,id,width,height)))return;
            try{Bitmap cover=WallpaperBackdropCache.cover(image,width,height);if(glassStyle.valid(styleRevision))backdropCache.write(key,cover,blur);cover.recycle();}
            catch(RuntimeException|OutOfMemoryError e){DiagnosticLog.info("wallpaper.backdrop.save_failed","cover preparation");}
        });
    }
    private void display(String id,boolean animate){display(id,animate,null);}
    private void display(String id,boolean animate,Runnable prepared){
        if(destroyed||leaving)return;
        WallpaperCatalog.Entry selected=store.snapshot().find(id);
        if(selected!=null&&selected.custom())DiagnosticLog.info("media.evidence.use","wallpaper="+selected.id+" transfer="+selected.diagnosticTransfer+" fingerprint="+selected.diagnosticFingerprint+" fingerprintStage=imported_source_bytes actualFileContent=not_rehashed preview="+(picker!=null));
        if(selected!=null&&selected.video()&&id.equals(playingVideo)&&(prepared==null||video.requested(id))){if(prepared!=null)prepared.run();return;}
        final int version=++request;
        if(transition!=null){transition.end();transition=null;}
        if(WallpaperCatalog.DEFAULT.equals(id)){
            boolean changed=!id.equals(shown);current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;shade.setVisibility(View.INVISIBLE);ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shown=id;shownKey=id;wallpaperBlur.sourceChanged();invalidateDock();revealDesktop(changed&&animate);if(changed)backdropReady();stopVideo(prepared);return;
        }
        WallpaperCatalog state=store.snapshot();
        if(selected!=null&&selected.video()){
            String key=store.imageKey(id);
            if(id.equals(shown)&&key.equals(shownKey)&&current.getDrawable() instanceof BitmapDrawable){if(DiagnosticProbe.allow("wallpaper.cache",1000))DiagnosticProbe.observed("wallpaper.cache","request="+version+" outcome=poster_cache_hit revision="+DiagnosticLog.anonymous("cache",key));showPoster(id,((BitmapDrawable)current.getDrawable()).getBitmap(),currentBlur,key,prepared);return;}
            if(DiagnosticProbe.allow("wallpaper.cache",1000))DiagnosticProbe.observed("wallpaper.cache","request="+version+" outcome=poster_cache_miss revision="+DiagnosticLog.anonymous("cache",key)+" previousRevision="+DiagnosticLog.anonymous("cache",String.valueOf(shownKey)));
            WallpaperCatalog.Entry posterEntry=!selected.custom()||store.poster(id).isFile()?selected:state.find(state.staticBeforeVideo);
            // Keep the last valid background while the new cover is decoded.
            decoder.execute(()->{try{
                if(version!=request)return;
                Bitmap image=null,blurred=null;
                if(posterEntry!=null&&!WallpaperCatalog.DEFAULT.equals(posterEntry.id))try{image=WallpaperImages.decode(activity,store,posterEntry,960,540);}catch(IOException ignored){}
                if(version!=request)return;
                if(image!=null){android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();blurred=DockBackdropDrawable.prepare(image,screen.widthPixels,screen.heightPixels,glassStyle);}
                Bitmap ready=image,blur=blurred;
                main.post(()->{if(version!=request||destroyed||UiCompat.destroyed(activity))return;showPoster(id,ready,blur,key,prepared);});
            }catch(OutOfMemoryError e){main.post(()->{if(version==request&&!destroyed)showPoster(id,null,null,key,prepared);});}});return;
        }
        String key=store.imageKey(id);
        if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(state.dailyImages.get(state.dailySource))){
            if(prepared!=null)display(WallpaperCatalog.DEFAULT,false,prepared);else if(shown==null)display(state.lastLocal,false);return;
        }
        boolean fixedStatic=temporaryMedia==null&&!WallpaperCatalog.DAILY.equals(id)&&!state.weather&&!state.rotating&&id.equals(state.current);
        android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
        String preparedKey=WallpaperBackdropCache.key(store,id,screen.widthPixels,screen.heightPixels);
        if(id.equals(shown)&&key.equals(shownKey)&&current.getDrawable()!=null&&(!fixedStatic||!glassStyle.rendering()||currentBlur!=null)){if(fixedStatic&&current.getDrawable() instanceof BitmapDrawable)WallpaperBackdropCache.prepare(preparedKey,((BitmapDrawable)current.getDrawable()).getBitmap(),currentBlur,true);if(DiagnosticProbe.allow("wallpaper.cache",1000))DiagnosticProbe.observed("wallpaper.cache","request="+version+" outcome=image_cache_hit revision="+DiagnosticLog.anonymous("cache",key));backdropReady();ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);invalidateDock();revealDesktop(false);stopVideo(prepared);return;}
        if(DiagnosticProbe.allow("wallpaper.cache",1000))DiagnosticProbe.observed("wallpaper.cache","request="+version+" outcome=image_cache_miss revision="+DiagnosticLog.anonymous("cache",key));
        WallpaperCatalog.Entry wallpaper=state.find(id);if(wallpaper==null){display(WallpaperCatalog.DEFAULT,false,prepared);return;}
        WallpaperBackdropCache.Picture cached=fixedStatic?WallpaperBackdropCache.prepared(preparedKey):null;
        if(cached!=null){presentStatic(id,key,cached.image,cached.blur,cached.fullResolution,prepared);if(cached.fullResolution&&(!glassStyle.rendering()||cached.blur!=null))return;}
        decoder.execute(()->{
            if(version!=request){if(DiagnosticProbe.allow("wallpaper.image_discard",1000))DiagnosticProbe.observed("wallpaper.image.discard","request="+version+" stage=before_decode reason=generation_changed");return;}
            try{
                Bitmap bitmap=cached!=null&&cached.fullResolution?cached.image:WallpaperImages.decode(activity,store,wallpaper,screen.widthPixels,screen.heightPixels);
                if(version!=request){if(DiagnosticProbe.allow("wallpaper.image_discard",1000))DiagnosticProbe.observed("wallpaper.image.discard","request="+version+" stage=after_decode reason=generation_changed");return;}
                if(fixedStatic){
                    if(id.equals(store.currentId()))WallpaperBackdropCache.prepare(preparedKey,bitmap,null,true);
                    main.post(()->{if(version==request&&!destroyed&&!UiCompat.destroyed(activity))presentStatic(id,key,bitmap,null,true,prepared);});
                }
                Bitmap blurred=DockBackdropDrawable.prepare(bitmap,screen.widthPixels,screen.heightPixels,glassStyle);
                if(version!=request)return;
                if(fixedStatic){
                    if(id.equals(store.currentId()))WallpaperBackdropCache.prepare(preparedKey,bitmap,blurred,true);
                    main.post(()->{if(version!=request||destroyed||UiCompat.destroyed(activity))return;currentBlur=glassStyle.rendering()?blurred:null;wallpaperBlur.sourceChanged();invalidateDock();cacheBackdrop(id,bitmap,blurred);});
                    return;
                }
                main.post(()->{
                    if(version!=request||destroyed||UiCompat.destroyed(activity)){if(DiagnosticProbe.allow("wallpaper.image_discard",1000))DiagnosticProbe.observed("wallpaper.image.discard","request="+version+" stage=before_submit reason="+(destroyed?"destroyed":"generation_or_activity_changed"));return;}
                    wallpaperBlur.sourceChanged();
                    ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
                    if(!animate||shown==null||WallpaperCatalog.DEFAULT.equals(shown)||fadeDuration(450)==0){current.setImageBitmap(bitmap);currentBlur=glassStyle.rendering()?blurred:null;incomingBlur=null;shown=id;shownKey=key;invalidateDock();revealDesktop(animate);backdropReady();}
                    else{
                        incoming.setImageBitmap(bitmap);incomingBlur=glassStyle.rendering()?blurred:null;incoming.setAlpha(0f);
                        transition=ValueAnimator.ofFloat(0,1);transition.setDuration(fadeDuration(450));
                        // end() can repeat the final update after the incoming cache has been cleared.
                        transition.addUpdateListener(a->{float fraction=(float)a.getAnimatedValue();boolean changed=fraction==1f&&currentBlur!=blurred;incoming.setAlpha(fraction);if(fraction==1f){current.setImageBitmap(bitmap);currentBlur=glassStyle.rendering()?blurred:null;incomingBlur=null;incoming.setImageDrawable(null);incoming.setAlpha(0f);shown=id;shownKey=key;}invalidateDock();if(changed)backdropReady();});transition.start();
                    }
                    cacheBackdrop(id,bitmap,blurred);stopVideo(prepared);
                });
            }catch(IOException|OutOfMemoryError e){DiagnosticLog.error("wallpaper.decode.failed",e);main.post(()->{if(version!=request||destroyed)return;if(previewOnly)previewFailed(tr("无法读取这张壁纸","Unable to read this wallpaper"));if(temporaryMedia!=null){display(WallpaperCatalog.DEFAULT,false,prepared);return;}if(WallpaperCatalog.DAILY.equals(id)){daily.cacheUnreadable();if(prepared!=null)display(WallpaperCatalog.DEFAULT,false,prepared);else if(current.getDrawable()==null)display(store.snapshot().lastLocal,false);return;}notice(tr("无法读取这张壁纸","Unable to read this wallpaper"));if(!previewOnly&&id.equals(store.snapshot().current))try{store.select(WallpaperCatalog.DEFAULT);}catch(IOException ignored){}display(WallpaperCatalog.DEFAULT,false,prepared);});}
        });
    }
    private void presentStatic(String id,String key,Bitmap image,Bitmap blur,boolean fullResolution,Runnable prepared){
        wallpaperBlur.sourceChanged();ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
        current.setImageBitmap(image);current.setAlpha(1f);currentBlur=glassStyle.rendering()?blur:null;incomingBlur=null;incoming.setImageDrawable(null);incoming.setAlpha(0f);
        shown=id;shownKey=fullResolution?key:null;invalidateDock();revealDesktop(false);backdropReady();stopVideo(prepared);
    }
    private void showPoster(String id,Bitmap image,Bitmap blur,String key,Runnable prepared){
        boolean changed=currentBlur!=blur||!id.equals(shown)||!key.equals(shownKey);
        if(image!=null){current.setImageBitmap(image);currentBlur=glassStyle.rendering()?blur:null;ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);}
        else if(current.getDrawable()==null){ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shade.setVisibility(View.INVISIBLE);}
        incoming.setImageDrawable(null);incoming.setAlpha(0f);incomingBlur=null;shown=id;shownKey=key;wallpaperBlur.sourceChanged();invalidateDock();
        revealDesktop(false);if(changed)backdropReady();
        cacheBackdrop(id,image,blur);if(prepared!=null)prepared.run();else stopVideo();
    }
    private void withCover(Runnable action){withCover(action,true);}
    private void withCover(Runnable action,boolean awaitFrame){
        cancelCover();cancelVideoFade();current.setAlpha(1f);
        if(current.getDrawable()==null&&ambient.getVisibility()!=View.VISIBLE){ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shade.setVisibility(View.INVISIBLE);}
        // A native Surface is independent of the cover. Submit the opaque cover before changing it.
        // Covered/paused windows may not draw; they must never hold up navigation or decoder release.
        boolean previewVisible=picker!=null&&picker.isShowing();
        if(!awaitFrame||!active||!video.displayReady()||!root.isShown()||(!root.hasWindowFocus()&&!previewVisible)||root.getWindowVisibility()!=View.VISIBLE){action.run();return;}
        final int version=videoRequest;
        pendingCover=()->{cancelCover();if(version==videoRequest&&!destroyed)action.run();};
        DiagnosticLog.info("wallpaper.video.cover","transition="+version+" phase=awaiting_frame alpha=1");
        Runnable pending=pendingCover;coverTree=root.getViewTreeObserver();
        if(Build.VERSION.SDK_INT>=29&&root.isHardwareAccelerated()){
            coverCommit=()->main.post(()->{if(pendingCover==pending){DiagnosticLog.info("wallpaper.video.cover","transition="+version+" phase=frame_submitted evidence=frame_commit_callback");pending.run();}});
            coverTree.registerFrameCommitCallback(coverCommit);
        }else{
            coverDraw=UiCompat.onDraw(root,()->androidx.core.view.ViewCompat.postOnAnimation(root,()->{if(pendingCover==pending){DiagnosticLog.info("wallpaper.video.cover","transition="+version+" phase=draw_callback evidence=draw_then_animation_not_presentation");pending.run();}}));
        }
        root.invalidate();
    }
    private void cancelCover(){
        pendingCover=null;
        if(coverTree!=null&&coverTree.isAlive()){
            if(coverDraw!=null)coverDraw.run();
            if(Build.VERSION.SDK_INT>=29&&coverCommit!=null)coverTree.unregisterFrameCommitCallback(coverCommit);
        }
        coverTree=null;coverDraw=null;coverCommit=null;
    }
    private void recordVideoState(String phase){
        if(!video.visible())return;
        video.diagnosticState(phase);
        DiagnosticLog.info("wallpaper.video.layers","transition="+videoRequest+" outputGeneration="+video.outputGeneration()+" phase="+phase
            +" active="+active+" temporary="+(temporaryMedia!=null)+" picker="+(picker!=null)+" coverPending="+(pendingCover!=null)
            +" coverVisible="+(current.getVisibility()==View.VISIBLE)+" coverAlpha="+current.getAlpha()+" poster="+(current.getDrawable()!=null)
            +" incomingAlpha="+incoming.getAlpha()+" ambientVisible="+(ambient.getVisibility()==View.VISIBLE)+" shadeVisible="+(shade.getVisibility()==View.VISIBLE)
            +" "+wallpaperBlur.diagnostics()+" "+appearance.diagnostics()+" evidence=layer_state_not_presentation");
    }
    void windowFocusChanged(boolean focused){
        if(!focused&&pendingCover!=null&&(picker==null||!picker.isShowing())){DiagnosticLog.info("wallpaper.video.cover","transition="+videoRequest+" phase=window_obscured evidence=state_only");pendingCover.run();}
        if(focused&&active&&restoreDesktopPending){restoreDesktopPending=false;restoreSelected();}
        if(focused&&pendingVideoStart!=null){Runnable start=pendingVideoStart;pendingVideoStart=null;start.run();}
        if(focused&&active&&video.ready()&&videoFade==null&&current.getAlpha()>0f)revealVideo(videoRequest);
        recordVideoState(focused?"focus_gain":"focus_loss");
    }
    private void cancelVideoFade(){if(videoFade!=null){videoFade.cancel();videoFade.removeAllUpdateListeners();videoFade=null;}}
    private void revealVideo(int version){
        if(version!=videoRequest||!video.ready()||!active||destroyed||!root.isShown()||(!root.hasWindowFocus()&&(picker==null||!picker.isShowing())))return;
        DiagnosticLog.info("wallpaper.video.cover","transition="+version+" phase=reveal_requested evidence=ready_callbacks_only");
        cancelVideoFade();int duration=fadeDuration(200);
        if(duration==0){
            if(pendingCover!=null)return;
            current.setAlpha(0f);wallpaperBlur.invalidate();recordVideoState("reveal_complete");return;
        }
        videoFade=ValueAnimator.ofFloat(current.getAlpha(),0f);videoFade.setDuration(duration);
        videoFade.addUpdateListener(a->{if(version==videoRequest&&active&&!destroyed){current.setAlpha((float)a.getAnimatedValue());wallpaperBlur.invalidate();if(current.getAlpha()==0f)recordVideoState("reveal_complete");}});videoFade.start();
    }
    void beforeLeave(Runnable launch){
        if(destroyed||leaving)return;
        wallpaperBlur.suspended(true);
        Runnable next=()->{try{launch.run();}finally{wallpaperBlur.interaction();wallpaperBlur.suspended(false);}};
        if(!video.visible()){next.run();return;}
        leaving=true;++request;
        // The existing poster/cache is already drawable. Navigation never waits for PixelCopy.
        finishLeave(next);
    }
    private void finishLeave(Runnable launch){leaving=false;if(active&&!destroyed)launch.run();}
    private void restoreSelected(){String id=selectedId();WallpaperCatalog.Entry selected=store.entry(id);if(selected!=null&&selected.video()&&!id.equals(failedVideo))startVideo(id,false);else display(id,false);}
    private void schedule(){main.removeCallbacks(rotate);WallpaperCatalog state=store.snapshot();if(active&&!previewOnly&&temporaryMedia==null&&state.rotating&&picker==null)main.postDelayed(rotate,state.rotationMinutes*60*1000L);}
    private void refreshManualSelection(){long revision=store.manualVersion();if(revision!=manualVersion){manualVersion=revision;failedVideos.clear();failedVideo="";}}
    void settingsChanged(){if(destroyed)return;refreshManualSelection();updateGpuBackground();video.setSound(audible());if(active){restoreSelected();daily.resume();schedule();}else display(selectedId(),false);}
    void resume(){
        refreshManualSelection();
        // Losing the playback service is not a permanent failure of the selected video.
        // Retry on the next foreground session, without looping while HOME stays active.
        if(!active&&!failedVideo.isEmpty()&&videoFailure==VideoWallpaperPlayer.Failure.INTERRUPTED){
            DiagnosticLog.info("wallpaper.video.recover","wallpaper="+VideoDiagnostics.wallpaper(failedVideo)+" reason=interrupted_on_resume");failedVideo="";
        }
        if(!active&&!desktopRevealed&&!previewOnly)fadePending=true;active=true;wallpaperBlur.suspended(false);wallpaperBlur.interaction();wallpaperBlur.active(true);updateDesktopEffects();restoreDesktopPending=false;video.setSound(audible());leaving=false;video.setPaused(false);restoreSelected();main.removeCallbacks(weatherTick);weatherTick.run();if(video.ready()&&videoFade==null&&current.getAlpha()>0f)revealVideo(videoRequest);daily.resume();schedule();recordVideoState("resume");
    }
    void environmentChanged(){daily.environmentChanged();}
    boolean pickerShowing(){return picker!=null;}
    void showTemporary(String id,Runnable prepared){showTemporary(id,false,true,prepared);}
    void showTemporary(String id,boolean weather,boolean dayNight,Runnable prepared){showTemporary(id,weather,dayNight,false,prepared);}
    void showTemporary(String id,boolean weather,boolean dayNight,boolean sound,Runnable prepared){
        temporaryWeather=weather;temporaryDayNight=dayNight;temporaryMedia=id;temporarySound=sound;updateDesktopEffects();main.removeCallbacks(rotate);if(WallpaperCatalog.DAILY.equals(store.currentId()))daily.resume();else daily.pause();video.setSound(false);
        shade.setBackgroundColor(Color.TRANSPARENT);present(id,()->{video.setSound(audible());if(prepared!=null)prepared.run();});
    }
    void restoreDesktop(Runnable prepared){
        cancelTemporary();restoreDesktopPending=false;
        present(store.snapshot().current,()->{video.setSound(audible());prepared.run();daily.resume();schedule();});
    }
    void temporarySound(boolean sound){temporarySound=sound;video.setSound(audible());}
    void cancelTemporary(){restoreDesktopPending|=temporaryMedia!=null;temporaryMedia=null;temporaryWeather=false;temporarySound=false;updateDesktopEffects();video.setSound(false);shade.setBackgroundDrawable(WallpaperAppearance.desktopShade());}
    private void present(String id,Runnable prepared){
        WallpaperCatalog.Entry entry=store.snapshot().find(id);
        if(entry!=null&&entry.video())startVideo(id,false,prepared);else display(id,false,prepared);
    }
    private void dailyChanged(){
        if(temporaryMedia!=null)return;
        if(picker!=null){
            if(daily.busy()){details();return;}
            boolean cardFocused=false;for(View card:cards.values())if(card.hasFocus())cardFocused=true;
            refreshCards(preview,cardFocused);
        }else if(active)display(selectedId(),true);
    }
    // HOME can pause a still-visible Activity; hidden() stops playback when it actually leaves.
    void pause(){video.setSound(false);cancelPreview();recordVideoState("before_pause");active=false;main.removeCallbacks(captureBackdrop);backdropQueued=false;if(gpu!=null)gpu.active(false);main.removeCallbacks(weatherTick);fadePending=false;wallpaperBlur.active(false);appearance.finish();leaving=false;++request;cancelCover();pendingVideoStart=null;cancelVideoFade();daily.pause();cancelCardPress();main.removeCallbacks(rotate);uploads.close();if(confirmation!=null)confirmation.dismiss();if(picker!=null)picker.dismiss();if(transition!=null)transition.end();}
    void hidden(){if(gpuShowing&&"weather".equals(gpuKind))showWeatherCover();hideGpu();stopVideo();}
    void destroy(){home.quiet.components.ComponentRepository.get(activity).remove(componentsChanged);glassStyle.forget(this);cancelPreview();destroyed=true;if(gpu!=null)gpu.destroy();appearance.finish();wallpaperBlur.destroy();finishStartupCover();++request;pendingVideoStart=null;cancelCover();cancelVideoFade();video.destroy();restoredCover=null;daily.destroy();uploads.destroy();decoder.shutdownNow();main.removeCallbacksAndMessages(null);if(transition!=null){transition.cancel();transition.removeAllUpdateListeners();}current.setImageDrawable(null);incoming.setImageDrawable(null);weatherCover.setImageDrawable(null);weatherStill=null;if(phoneWeatherStill!=null){phoneWeatherStill.recycle();phoneWeatherStill=null;}currentBlur=null;incomingBlur=null;dockInvalidation=null;backdropInvalidation=null;liveBackdrop=null;thumbnails.evictAll();}

    synchronized Bitmap entryBanner(){
        if(entry==null)entry=HomeEntryArtwork.render(activity,tr("更换壁纸","Wallpaper Settings"),HomeAppCatalog.WALLPAPER);
        return entry;
    }
    private TextView label(String value,int size){return ui.text(value,size);}
    private GradientDrawable border(boolean focus,boolean primary){
        GradientDrawable border=ui.surface(primary?0xe6ffffff:0xe6000000,0,focus?Color.WHITE:0x30ffffff,CARD_STROKE);
        // GradientDrawable insets its stroke path by half the stroke width.
        border.setCornerRadius(px(CARD_RADIUS)-px(CARD_STROKE)/2f);return border;
    }
    private TextView button(String value,boolean primary,Runnable action){return ui.button(value,primary,action);}
    private void gap(LinearLayout parent,int n){parent.addView(new View(activity),new LinearLayout.LayoutParams(px(n),1));}
    private Drawable toggleDrawable(boolean checked){
        Drawable drawable=new Drawable(){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){Rect b=getBounds();float radius=b.height()/2f;paint.setColor(checked?UiPalette.SWITCH_ON:0x26ffffff);canvas.drawRoundRect(new RectF(b),radius,radius,paint);paint.setColor(Color.WHITE);float inset=px(2);canvas.drawCircle(checked?b.right-radius:b.left+radius,b.centerY(),radius-inset,paint);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };drawable.setBounds(0,0,px(28),px(16));return drawable;
    }
    private LinearLayout row(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private void runSaved(IoAction action){try{action.run();}catch(IOException e){DiagnosticLog.error("wallpaper.operation.failed",e);notice(tr("操作失败，请检查电视存储后重试","Operation failed. Check TV storage and retry."));}}
    private interface IoAction {void run()throws IOException;}
    private String rotationLabel(int minutes){return minutes==0?tr("关闭","Off"):minutes==60?tr("1 小时","1 hour"):tr(minutes+" 分钟",minutes+" min");}
    private void showRotationMenu(){
        if(picker==null||rotationMenu!=null)return;
        WallpaperCatalog state=store.snapshot();List<WallpaperCatalog.Entry> pool=dynamicTab?state.videos():state.pictures();
        if(pool.size()<2){notice(tr(dynamicTab?"至少需要两个视频才能轮播":"至少需要两张图片才能轮播",dynamicTab?"Add at least two videos for rotation":"Add at least two photos for rotation"));return;}
        int selected=state.rotating&&state.dynamic()==dynamicTab?state.rotationMinutes:0;
        LinearLayout menu=new LinearLayout(activity);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(6),px(6),px(6),px(6));menu.setBackgroundDrawable(ui.surface(0xe6000000,14,0x38ffffff,1));
        PopupWindow popup=new PopupWindow(menu,Math.max(auto.getWidth(),px(150)),-2,true);rotationMenu=popup;
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);if(Build.VERSION.SDK_INT>=21)popup.setElevation(px(10));
        TextView focused=null;List<TextView> choices=new ArrayList<>();
        for(int minutes:new int[]{0,1,5,15,30,60}){
            TextView option=button(rotationLabel(minutes),false,()->{popup.dismiss();setRotation(minutes);});option.setGravity(Gravity.CENTER);option.setPadding(px(12),0,px(12),0);option.setBackgroundDrawable(ControlStyle.listRow(unit,10));option.setSelected(minutes==selected);option.setText(SettingsSelectionMark.label(rotationLabel(minutes),minutes==selected,unit));option.setContentDescription(rotationLabel(minutes)+(minutes==selected?tr("，已选",", selected"):""));menu.addView(option,new LinearLayout.LayoutParams(-1,px(38)));choices.add(option);if(minutes==selected)focused=option;
        }
        choices.get(0).setNextFocusUpId(choices.get(0).getId());choices.get(choices.size()-1).setNextFocusDownId(choices.get(choices.size()-1).getId());
        popup.setOnDismissListener(()->{if(rotationMenu==popup)rotationMenu=null;auto.post(()->{if(picker!=null&&auto.isShown())auto.requestFocusFromTouch();});});
        popup.showAsDropDown(auto,0,px(5));if(focused!=null)focused.requestFocusFromTouch();
    }
    private void setRotation(int minutes){
        WallpaperCatalog state=store.snapshot();
        if(minutes==0){rotationCommit=0;commitPending=false;if(state.rotating&&state.dynamic()==dynamicTab)runSaved(()->{store.rotation(dynamicTab,0,preview);details();});return;}
        List<WallpaperCatalog.Entry> pool=dynamicTab?state.videos():state.pictures();if(pool.size()<2)return;
        WallpaperCatalog.Entry entry=state.find(preview);String first=entry!=null&&entry.custom()&&entry.video()==dynamicTab?preview:pool.get(0).id;
        if(dynamicTab)startVideo(first,true,minutes);
        else runSaved(()->{daily.cancel();store.rotation(false,minutes,first);preview=first;display(first,true);refreshCards(first,false);});
    }

    private ScreensaverSettings saverSettings(){
        try{return screensavers.snapshot(store.snapshot());}
        catch(IOException e){DiagnosticLog.error("screensaver.load.failed",e);return null;}
    }
    private void saveSaver(ScreensaverSettings next,boolean marks){
        if(next==null)return;
        try{screensavers.save(next);if(marks)refreshCards(preview,false);else updateSaverToolbar();}
        catch(IOException e){updateSaverToolbar();notice(tr("屏保设置保存失败，请检查存储空间","Unable to save screensaver settings. Check storage."));}
    }
    private void applySaver(){
        ScreensaverSettings state=saverSettings();String id=preview;if(state==null||id==null)return;int version=++saverApplyVersion;
        if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(store.snapshot().dailyImages.get(store.snapshot().dailySource))){
            daily.prepare((success,message)->{if(version!=saverApplyVersion||picker==null)return;if(success){ScreensaverSettings latest=saverSettings();if(latest!=null)saveSaver(latest.media(id),true);}else notice(message);});
        }else saveSaver(state.media(id),true);
    }
    private List<TextView> saverControls(){
        List<TextView> result=new ArrayList<>();result.add(applySaver);result.addAll(saverWaits.values());result.add(saverTime);result.add(saverWeather);return result;
    }
    private void addScreensaverToolbar(LinearLayout parent){
        LinearLayout bar=row();bar.setPadding(px(14),px(4),px(14),px(4));bar.setBackgroundDrawable(ui.capsule());
        applySaver=ui.compactButton(tr("设为屏保","Set saver"),this::applySaver);bar.addView(applySaver,new LinearLayout.LayoutParams(px(chinese?88:95),px(27)));gap(bar,16);
        TextView wait=label(tr("自动进入","Idle"),11);wait.setTextColor(WallpaperUi.MUTED);wait.setSingleLine(true);bar.addView(wait);gap(bar,8);
        LinearLayout choices=row();choices.setPadding(px(2),px(2),px(2),px(2));choices.setBackgroundDrawable(ui.surface(0x66000000,100,Color.TRANSPARENT,0));saverWaits.clear();
        for(int minutes:ScreensaverSettings.WAITS){
            TextView option=ui.compactButton(minutes==0?tr("关闭","Off"):String.valueOf(minutes),()->{ScreensaverSettings state=saverSettings();if(state!=null)saveSaver(state.waitMinutes(minutes),false);});
            option.setContentDescription(minutes==0?tr("关闭自动进入屏保","Disable automatic screensaver"):tr(minutes+" 分钟后进入屏保","Screensaver after "+minutes+" minutes"));
            ControlStyle.choice(option,unit,100,Color.TRANSPARENT,0);saverWaits.put(minutes,option);option.setPadding(px(9.6f),0,px(9.6f),0);choices.addView(option,new LinearLayout.LayoutParams(px(minutes==0?60:50),px(23)));
        }
        bar.addView(choices);gap(bar,8);TextView minutes=label(tr("分钟","min"),10);minutes.setTextColor(WallpaperUi.MUTED);minutes.setSingleLine(true);bar.addView(minutes);gap(bar,16);
        saverTime=ui.compactButton(tr("时间","Time"),()->{ScreensaverSettings state=saverSettings();if(state!=null)saveSaver(state.time(!state.showTime),false);});
        saverWeather=ui.compactButton(tr("天气","Weather"),()->{ScreensaverSettings state=saverSettings();if(state!=null)saveSaver(state.weather(!state.showWeather),false);});
        for(TextView toggle:new TextView[]{saverTime,saverWeather}){toggle.setPadding(px(9.6f),0,px(9.6f),0);toggle.setBackgroundDrawable(ControlStyle.action(unit,100,Color.TRANSPARENT,0));bar.addView(toggle,new LinearLayout.LayoutParams(-2,px(27)));if(toggle==saverTime)gap(bar,12);}
        List<TextView> controls=saverControls();for(int n=0;n<controls.size();n++){TextView control=controls.get(n);control.setNextFocusLeftId(controls.get(Math.max(0,n-1)).getId());control.setNextFocusRightId(controls.get(Math.min(controls.size()-1,n+1)).getId());control.setNextFocusDownId(control.getId());}
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.gravity=Gravity.CENTER_HORIZONTAL;bp.topMargin=px(16);parent.addView(bar,bp);
    }
    private void updateSaverToolbar(){
        if(applySaver==null)return;ScreensaverSettings state=saverSettings();boolean enabled=state!=null;
        for(TextView control:saverControls())control.setEnabled(enabled);
        boolean selected=enabled&&state.mediaId.equals(preview);applySaver.setText(selected?tr("当前屏保","Current saver"):tr("设为屏保","Set saver"));applySaver.setEnabled(enabled&&preview!=null&&!selected);
        for(Map.Entry<Integer,TextView> option:saverWaits.entrySet()){boolean chosen=enabled&&state.idleMinutes==option.getKey();TextView view=option.getValue();view.setSelected(chosen);view.setText(SettingsSelectionMark.label(option.getKey()==0?tr("关闭","Off"):String.valueOf(option.getKey()),chosen,unit));}
        for(TextView toggle:new TextView[]{saverTime,saverWeather}){
            boolean on=enabled&&(toggle==saverTime?state.showTime:state.showWeather);Drawable art=toggleDrawable(on);art.setBounds(0,0,px(22),px(13));toggle.setCompoundDrawables(null,null,art,null);toggle.setCompoundDrawablePadding(px(6));
            toggle.setContentDescription((toggle==saverTime?tr("屏保时间","Screensaver time"):tr("屏保天气","Screensaver weather"))+(on?tr("，开",", on"):tr("，关",", off")));
        }
    }
    private void addUsageMark(LinearLayout caption,String value){
        TextView mark=label(value,9);mark.setTextColor(UiPalette.INK);mark.setPadding(px(4),px(1),px(4),px(1));mark.setBackgroundDrawable(ui.surface(0xe6ffffff,100,Color.TRANSPARENT,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=px(3);caption.addView(mark,p);
    }

    void imported(String id){
        if(id==null||store.snapshot().find(id)==null)return;
        showPicker();preview=id;dynamicTab=store.snapshot().find(id).video();refreshCards(id,true);
    }
    void showPicker(){
        if(picker!=null||leaving)return;stopVideo();main.removeCallbacks(rotate);preview=store.snapshot().current;dynamicTab=store.snapshot().dynamic();
        pickerVisibility.clear();for(int n=1;n<root.getChildCount();n++){View view=root.getChildAt(n);pickerVisibility.put(view,view.getVisibility());view.setVisibility(View.INVISIBLE);}
        picker=new androidx.activity.ComponentDialog(activity){@Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)focusPendingCard();else cancelCardPress();}
            @Override public boolean dispatchKeyEvent(KeyEvent key){return KeyFeedback.dispatch(getWindow().getDecorView(),key,()->super.dispatchKeyEvent(key));}};
        updateDesktopEffects();
        ((androidx.activity.ComponentDialog)picker).getOnBackPressedDispatcher().addCallback((androidx.activity.ComponentDialog)picker,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){handlePickerBack();}});
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);content=new FrameLayout(activity);
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(px(36),px(32),px(36),px(32));content.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();TextView back=button("",false,this::handlePickerBack);back.setPadding(px(9),px(9),px(9),px(9));back.setCompoundDrawables(ui.icon("arrow-left",18,WallpaperUi.INK),null,null,null);back.setBackgroundDrawable(ui.focusSurface(0xa6000000,100));back.setContentDescription(tr("返回桌面","Back to home"));header.addView(back,new LinearLayout.LayoutParams(px(37),px(37)));gap(header,13);
        TextView heading=label(tr("更换壁纸","Wallpaper Settings"),28);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout categories=row();int categoryPadding=px(WallpaperUi.CATEGORY_PADDING);categories.setPadding(categoryPadding,categoryPadding,categoryPadding,categoryPadding);categories.setBackgroundDrawable(ui.surface(0xe6000000,100,0x38ffffff,1));
        staticTab=ui.categoryTab(tr("静态壁纸","Static"),()->category(false));videoTab=ui.categoryTab(tr("动态壁纸","Video"),()->category(true));
        categories.addView(staticTab,new LinearLayout.LayoutParams(px(WallpaperUi.TOOLBAR_WIDTH),px(WallpaperUi.TOOLBAR_HEIGHT)));gap(categories,4);categories.addView(videoTab,new LinearLayout.LayoutParams(px(WallpaperUi.TOOLBAR_WIDTH),px(WallpaperUi.TOOLBAR_HEIGHT)));
        staticTab.setNextFocusLeftId(back.getId());staticTab.setNextFocusRightId(videoTab.getId());videoTab.setNextFocusLeftId(staticTab.getId());back.setNextFocusRightId(staticTab.getId());
        header.addView(categories);gap(header,8);
        auto=button("",false,this::showRotationMenu);header.addView(auto);
        dynamicToggle=button("",false,this::toggleVideo);header.addView(dynamicToggle);
        soundToggle=button("",false,()->runSaved(()->{store.sound(!store.snapshot().sound);video.setSound(audible());details();}));header.addView(soundToggle);
        localFile=button(tr("本地 / U盘","Files / USB"),false,()->{picker.dismiss();activity.startActivityForResult(new android.content.Intent(activity,WallpaperFilesActivity.class),WallpaperFilesActivity.REQUEST);});header.addView(localFile);
        gap(header,8);upload=button(tr("上传壁纸","Upload"),false,()->{daily.cancel();stopVideo();uploads.show((canvas,paint)->{drawDockBackground(canvas,paint);if(content!=null)content.draw(canvas);},id->{if(picker==null)return;if(id!=null&&store.snapshot().find(id)!=null){preview=id;dynamicTab=store.snapshot().find(id).video();}refreshCards(preview,true);});});ui.icon(upload,"upload");header.addView(upload);
        heading.setSingleLine();heading.setEllipsize(android.text.TextUtils.TruncateAt.END);for(TextView control:new TextView[]{auto,dynamicToggle,soundToggle,localFile,upload})ui.toolbarSize(control);
        layout.addView(header);
        layout.addView(new View(activity),new LinearLayout.LayoutParams(1,0,1));
        actions=row();LinearLayout titles=new LinearLayout(activity);titles.setOrientation(LinearLayout.VERTICAL);title=label("",27);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(title,new LinearLayout.LayoutParams(-1,-2));
        subtitle=label("",12);subtitle.setTextColor(WallpaperUi.MUTED);subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);subtitle.setPadding(0,px(7),0,0);titles.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));actions.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        // Together with the rail's top padding and focused-card lift, leave about 36px at 1080p.
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.setMargins(px(4),0,px(4),px(16));layout.addView(actions,ap);
        scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);rail=row();rail.setClipToPadding(false);rail.setClipChildren(false);rail.setGravity(Gravity.BOTTOM);rail.setPadding(px(4),px(7),px(4),0);scroll.addView(rail);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,px(154));layout.addView(scroll,rp);
        addScreensaverToolbar(layout);
        picker.setContentView(content);picker.setOnDismissListener(d->{++saverApplyVersion;commitPending=false;cancelCardPress();pendingCardFocus=null;++pickerVersion;uploads.close();picker=null;updateDesktopEffects();if(rotationMenu!=null)rotationMenu.dismiss();daily.cancel();cards.clear();for(Map.Entry<View,Integer> item:pickerVisibility.entrySet())item.getKey().setVisibility(item.getValue());pickerVisibility.clear();shade.setBackgroundDrawable(WallpaperAppearance.desktopShade());if(active)restoreSelected();schedule();});
        Window window=picker.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setSystemUiVisibility(5894);window.setDimAmount(0);}
        shade.setBackgroundDrawable(WallpaperUi.shade());picker.show();if(window!=null)window.setLayout(-1,-1);refreshCards(preview,true);
    }
    private void stopVideo(){stopVideo(null);}
    private void stopVideo(Runnable after){
        ++videoRequest;playingVideo="";commitPending=false;rotationCommit=0;pendingVideoStart=null;
        withCover(()->{video.stop();if(after!=null)after.run();},video.visible());
    }
    private void startVideo(String id,boolean commit){startVideo(id,commit,0,null);}
    private void startVideo(String id,boolean commit,int minutes){startVideo(id,commit,minutes,null);}
    private void startVideo(String id,boolean commit,Runnable covered){startVideo(id,commit,0,covered);}
    private void startVideo(String id,boolean commit,int minutes,Runnable covered){
        WallpaperCatalog.Entry entry=store.snapshot().find(id);if(entry==null||!entry.video()||!active||leaving)return;
        rotationCommit=minutes;
        if(id.equals(playingVideo)&&video.requested(id)){commitPending|=commit;if(commitPending&&video.ready()){commitPending=false;commitVideo(id);}if(covered!=null)covered.run();return;}
        cancelVideoFade();pendingVideoStart=null;failedVideo="";playingVideo=id;commitPending=commit;final int version=++videoRequest;
        display(id,false,()->{
            if(version!=videoRequest||!active||destroyed)return;
            if(covered!=null)covered.run();
            Runnable start=()->withCover(()->{
                if(version!=videoRequest||!active||destroyed)return;
                video.play(entry,new VideoWallpaperPlayer.Listener(){
                    @Override public void cover(Runnable change){if(version==videoRequest&&active&&!destroyed){appearance.finish();withCover(change);recordVideoState("wake_cover");}}
                    @Override public void stopped(){if(version==videoRequest){cancelVideoFade();current.setAlpha(1f);updateDesktopEffects();recordVideoState("video_stopped");}}
                    @Override public void ready(){
                        if(version!=videoRequest||!active||destroyed)return;checkPreview();if(messages!=null)messages.show("");
                        ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);updateDesktopEffects();revealVideo(version);
                        captureVideoPoster(id,version);
                        if(commitPending){commitPending=false;commitVideo(id);}else if(picker!=null)details();
                    }
                    @Override public void failed(VideoWallpaperPlayer.Failure reason){if(version!=videoRequest)return;videoFailed(id,reason);}
                });
            });
            // Resume may run before the window is drawable. Keep the cover until focus returns.
            if(!root.hasWindowFocus()&&picker==null)pendingVideoStart=start;else start.run();
        });
    }
    private void videoFailed(String id,VideoWallpaperPlayer.Failure reason){
        WallpaperCatalog state=store.snapshot();long revision=store.manualVersion();boolean managed=active&&!previewOnly&&temporaryMedia==null&&picker==null&&(id.equals(state.current)||state.rotating&&rotationCommit>0);main.removeCallbacks(rotate);
        failedVideo=id;videoFailure=reason;if(managed)failedVideos.add(id);
        stopVideo(()->{
            if(managed&&active&&!destroyed&&revision==store.manualVersion()){try{boolean skipped=store.failedVideo(id,failedVideos);restoreSelected();schedule();notice(skipped?tr("已跳过无法播放的壁纸","Skipped an unplayable wallpaper"):tr("动态壁纸暂不可用，已恢复静态壁纸","Video wallpaper unavailable; static wallpaper restored"));}catch(IOException error){notice(tr("背景保存失败，已保留原设置","Unable to save background. Current settings kept."));}}
            else if(active){if(picker!=null)details();notice(reason.hint(chinese));}
        });
    }
    private void captureVideoPoster(String id,int version){
        if(!id.startsWith("user-")||store.poster(id).isFile())return;
        video.sourceSnapshot(image->{
            if(image==null)return;
            if(version!=videoRequest||destroyed){image.recycle();return;}
            decoder.execute(()->{
                try{
                    if(!store.saveVideoPoster(id,image)){image.recycle();return;}
                    android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                    Bitmap blur=DockBackdropDrawable.prepare(image,screen.widthPixels,screen.heightPixels,glassStyle);
                    main.post(()->{
                        if(version!=videoRequest||destroyed){image.recycle();if(blur!=null)blur.recycle();return;}
                        current.setImageBitmap(image);currentBlur=glassStyle.rendering()?blur:null;shownKey=store.imageKey(id);wallpaperBlur.sourceChanged();invalidateDock();backdropReady();cacheBackdrop(id,image,blur);
                        // The poster and thumbnail cache own the captured bitmap after this point.
                        thumbnails.put(store.imageKey(id),image);View card=cards.get(id);
                        if(card instanceof FrameLayout)((ImageView)((FrameLayout)card).getChildAt(0)).setImageBitmap(image);
                    });
                }catch(IOException|RuntimeException|OutOfMemoryError e){
                    image.recycle();DiagnosticLog.error("wallpaper.video.poster_failed","optional=true "+VideoDiagnostics.failureDetails(e),e);
                }
            });
        });
    }
    private void commitVideo(String id){
        try{daily.cancel();boolean rotating=rotationCommit>0;if(rotating)store.rotation(true,rotationCommit,id);else store.select(id);rotationCommit=0;if(id.equals(shown)&&current.getDrawable() instanceof BitmapDrawable)cacheBackdrop(id,((BitmapDrawable)current.getDrawable()).getBitmap(),currentBlur);if(picker!=null)refreshCards(id,cards.get(id)!=null&&cards.get(id).hasFocus());schedule();if(!rotating)notice(tr("已应用动态壁纸","Video wallpaper applied"));}
        catch(IOException e){stopVideo();restoreSelected();schedule();notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}
    }
    private void applySelected(){WallpaperCatalog.Entry entry=store.snapshot().find(preview);if(entry==null)return;
        if(entry.video())startVideo(preview,true);
        else if(WallpaperCatalog.DAILY.equals(preview))daily.apply((success,message)->{if(picker!=null){refreshCards(WallpaperCatalog.DAILY,true);notice(success?tr("已应用每日壁纸","Daily wallpaper applied"):message);}});
        else runSaved(()->{daily.cancel();store.select(preview);display(preview,true);refreshCards(preview,true);notice(tr("已应用壁纸","Wallpaper applied"));});
    }
    private void category(boolean videos){if(dynamicTab==videos){focusCard(preview);return;}stopVideo();dynamicTab=videos;refreshCards(store.snapshot().current,true);}
    private void toggleVideo(){
        WallpaperCatalog state=store.snapshot();if(state.dynamic()){stopVideo();runSaved(()->{store.stopVideo();dynamicTab=false;refreshCards(store.snapshot().current,true);daily.environmentChanged();schedule();});}
        else if(state.find(state.lastVideo)!=null){dynamicTab=true;refreshCards(state.lastVideo,false);startVideo(state.lastVideo,true);}
        else {dynamicTab=true;refreshCards(null,false);upload.requestFocus();}
    }
    private void handlePickerBack(){if(picker==null)return;commitPending=false;picker.dismiss();KeyFeedback.returned(activity);}
    private void refreshCards(String preferred,boolean focusCard){
        if(picker==null)return;cancelCardPress();pendingCardFocus=null;int version=++pickerVersion;WallpaperCatalog state=store.snapshot();
        List<WallpaperCatalog.Entry> entries=new ArrayList<>();for(WallpaperCatalog.Entry e:state.entries())if(e.video()==dynamicTab)entries.add(e);
        boolean found=false;for(WallpaperCatalog.Entry e:entries)if(e.id.equals(preferred))found=true;
        if(!found)preferred=entries.isEmpty()?null:entries.get(0).id;
        preview=preferred;
        rail.removeAllViews();cards.clear();
        for(WallpaperCatalog.Entry wallpaper:entries){
            String id=wallpaper.id;FrameLayout card=new FrameLayout(activity);card.setId(androidx.core.view.ViewCompat.generateViewId());card.setFocusable(true);card.setContentDescription(wallpaper.label(chinese));card.setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));card.setBackgroundDrawable(border(false,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(184),px(147));cp.rightMargin=px(14);rail.addView(card,cp);cards.put(id,card);
            ImageView image=ui.image(CARD_RADIUS,CARD_INSET);card.addView(image,new FrameLayout.LayoutParams(-1,px(95)));
            if(wallpaper.video()){image.setImageBitmap(defaultThumbnail());ImageView play=new ImageView(activity);QuickControlIcon playIcon=new QuickControlIcon("play");playIcon.color(WallpaperUi.INK);play.setImageDrawable(playIcon);play.setScaleType(ImageView.ScaleType.FIT_CENTER);androidx.core.view.ViewCompat.setImportantForAccessibility(play,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(px(18),px(18),Gravity.TOP|Gravity.CENTER_HORIZONTAL);pp.topMargin=(px(95)-px(18))/2;card.addView(play,pp);}
            if(WallpaperCatalog.DEFAULT.equals(id))image.setImageBitmap(defaultThumbnail());else{
                String thumbnailKey=store.imageKey(id);Bitmap cached=thumbnails.get(thumbnailKey);if(cached!=null)image.setImageBitmap(cached);
                else if(!WallpaperCatalog.DAILY.equals(id)||store.cached(state.dailyImages.get(state.dailySource)))decoder.execute(()->{try{Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,360,203);thumbnails.put(thumbnailKey,bitmap);main.post(()->{if(picker!=null&&version==pickerVersion)image.setImageBitmap(bitmap);});}catch(IOException|OutOfMemoryError ignored){}});
            }
            if(WallpaperCatalog.DAILY.equals(id)){
                if(!store.cached(state.dailyImages.get(state.dailySource)))image.setImageBitmap(defaultThumbnail());
                ImageView calendar=new ImageView(activity);calendar.setImageDrawable(ui.calendar(18));calendar.setPadding(px(7),px(7),px(7),px(7));calendar.setBackgroundDrawable(ui.surface(0xcc000000,100,Color.TRANSPARENT,0));androidx.core.view.ViewCompat.setImportantForAccessibility(calendar,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(px(32),px(32),Gravity.TOP|Gravity.RIGHT);dp.setMargins(0,px(7),px(7),0);card.addView(calendar,dp);
            }
            LinearLayout caption=row();FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,px(34),Gravity.BOTTOM);np.leftMargin=px(4);np.rightMargin=px(4);card.addView(caption,np);
            TextView name=label(wallpaper.label(chinese),12);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            if(id.equals(state.current))addUsageMark(caption,tr("壁纸","Wall"));
            ScreensaverSettings saver=saverSettings();
            if(saver!=null&&id.equals(saver.mediaId))addUsageMark(caption,tr("屏保","Saver"));
            card.setOnFocusChangeListener((v,focus)->{if(focus){if(!id.equals(preview))commitPending=false;preview=id;if(wallpaper.video())startVideo(id,false);else display(id,true);details();scroll.requestChildFocus(rail,card);}else if(id.equals(pressedWallpaper))cancelCardPress();});
            card.setOnClickListener(v->{commitPending=false;preview=id;applySelected();KeyFeedback.confirm(activity);});
            card.setOnLongClickListener(v->{confirmDelete(id,KeyEvent.KEYCODE_UNKNOWN,0);return true;});
            card.setOnKeyListener((v,key,event)->{if(confirmKey(key))return cardKey(id,key,event);if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){cancelCardPress();List<String> ids=focusableCards();int index=ids.indexOf(id)+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);if(index>=0&&index<ids.size())cards.get(ids.get(index)).requestFocus();}return true;});
        }
        if(entries.isEmpty()){TextView empty=label(tr("上传视频作为动态壁纸","Upload a video wallpaper"),16);empty.setTextColor(WallpaperUi.MUTED);rail.addView(empty);display(store.snapshot().staticBeforeVideo,false);}else if(state.find(preview).video())startVideo(preview,false);else display(preview,true);
        details();if(focusCard)focusCard(preview);
    }
    private List<String> focusableCards(){List<String> ids=new ArrayList<>();for(Map.Entry<String,View> item:cards.entrySet())if(item.getValue().isFocusable())ids.add(item.getKey());return ids;}
    private void focusCard(String id){
        if(picker==null)return;View card=cards.get(id);
        pendingCardFocus=card!=null&&card.isFocusable()?card:upload;
        pendingCardFocus.post(this::focusPendingCard);
    }
    private void focusPendingCard(){
        // Dialog dismissal can regain window focus after its callback has returned.
        if(picker==null||pendingCardFocus==null||!pendingCardFocus.hasWindowFocus()||!pendingCardFocus.isShown())return;
        View target=pendingCardFocus;pendingCardFocus=null;target.requestFocusFromTouch();
    }
    private static boolean confirmKey(int key){return key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_SELECT;}
    private void cancelCardPress(){main.removeCallbacks(holdCard);View card=cards.get(pressedWallpaper);if(card!=null)card.setPressed(false);pressedWallpaper=null;pressedKey=KeyEvent.KEYCODE_UNKNOWN;longPressed=false;}
    private boolean cardKey(String id,int key,KeyEvent event){
        // Own the complete gesture so a long press cannot also apply on release.
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()==0){cancelCardPress();pressedWallpaper=id;pressedKey=key;pressedAt=event.getDownTime();cards.get(id).setPressed(true);main.postDelayed(holdCard,ViewConfiguration.getLongPressTimeout());}
        }else if(event.getAction()==KeyEvent.ACTION_UP){
            boolean click=id.equals(pressedWallpaper)&&key==pressedKey&&!longPressed&&!event.isCanceled();cancelCardPress();if(click)cards.get(id).performClick();
        }
        return true;
    }
    private void details(){
        if(picker==null)return;WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(preview);
        updateSaverToolbar();
        staticTab.setSelected(!dynamicTab);videoTab.setSelected(dynamicTab);
        soundToggle.setVisibility(dynamicTab?View.VISIBLE:View.GONE);soundToggle.setText(tr("声音","Sound"));soundToggle.setCompoundDrawables(toggleDrawable(state.sound),null,null,null);soundToggle.setCompoundDrawablePadding(px(7));soundToggle.setContentDescription(state.sound?tr("壁纸声音，开","Wallpaper sound, on"):tr("壁纸声音，关","Wallpaper sound, off"));
        dynamicToggle.setVisibility(dynamicTab?View.VISIBLE:View.GONE);dynamicToggle.setText(tr("启用动态","Enable video"));dynamicToggle.setCompoundDrawables(toggleDrawable(state.dynamic()),null,null,null);dynamicToggle.setCompoundDrawablePadding(px(7));dynamicToggle.setContentDescription(state.dynamic()?tr("启用动态，开","Enable video, on"):tr("启用动态，关","Enable video, off"));
        for(TextView toggle:new TextView[]{dynamicToggle,soundToggle}){Drawable icon=toggle.getCompoundDrawables()[0];icon.setBounds(0,0,px(22),px(13));toggle.setCompoundDrawables(icon,null,null,null);toggle.setCompoundDrawablePadding(px(4));}
        int count=dynamicTab?state.videos().size():state.pictures().size();
        boolean rotating=state.rotating&&state.dynamic()==dynamicTab;String unavailable=tr(dynamicTab?"至少需要两个视频才能轮播":"至少需要两张图片才能轮播",dynamicTab?"Add at least two videos for rotation":"Add at least two photos for rotation");
        auto.setText(tr("轮播：","Rotate: ")+rotationLabel(rotating?state.rotationMinutes:0));auto.setCompoundDrawables(null,null,ui.icon("chevron-down",12,WallpaperUi.INK),null);auto.setCompoundDrawablePadding(px(4));auto.setEnabled(count>=2);auto.setAlpha(count>=2?1:.4f);auto.setContentDescription(count<2?unavailable:auto.getText());
        WallpaperUi.toolbarFocus(staticTab,videoTab,auto,dynamicToggle,soundToggle,localFile,upload);
        if(entry==null){title.setText(tr("还没有动态壁纸","No video wallpapers"));subtitle.setText(unavailable);subtitle.setVisibility(View.VISIBLE);for(TextView control:new TextView[]{staticTab,videoTab,dynamicToggle,soundToggle,localFile,upload})control.setNextFocusDownId(upload.getId());return;}
        title.setText(entry.label(chinese));boolean isDaily=WallpaperCatalog.DAILY.equals(preview);
        subtitle.setVisibility(isDaily||entry.custom()?View.VISIBLE:View.GONE);
        if(entry.custom()){
            String description=entry.width+" × "+entry.height;
            if(entry.video())description+="  "+Math.round(entry.fps)+" FPS  "+Math.round(entry.durationMs/1000f)+tr(" 秒"," s");
            description+="  "+Formatter.formatShortFileSize(activity,entry.bytes);
            subtitle.setText(entry.video()&&preview.equals(failedVideo)?videoFailure.hint(chinese):description);
        }
        if(isDaily){
            WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);
            String description=WallpaperCatalog.sourceLabel(state.dailySource,chinese)+"  "+tr("每天自动更新","Updates daily");
            if(store.cached(image)){
                description=image.width+" × "+image.height+"  "+Formatter.formatShortFileSize(activity,store.dailyFile(image.source,image.slot).length())+"  "+description;
                if(!image.title.isEmpty())description+="  "+image.title;
            }
            subtitle.setText(daily.busy()?daily.status(state.dailySource):description);
        }
        if(count<2){subtitle.setText((subtitle.getVisibility()==View.VISIBLE?subtitle.getText()+"  ":"")+unavailable);subtitle.setVisibility(View.VISIBLE);}
        View card=cards.get(preview);if(card==null)return;
        for(Map.Entry<String,View> item:cards.entrySet()){boolean selected=item.getKey().equals(preview);item.getValue().setBackgroundDrawable(border(selected,false));item.getValue().setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));item.getValue().setTranslationY(selected?-px(3):0);item.getValue().setNextFocusUpId(dynamicTab?videoTab.getId():staticTab.getId());}
        int lower=card.getId();
        for(View item:cards.values())item.setNextFocusDownId(applySaver.getId());
        for(TextView control:saverControls())control.setNextFocusUpId(lower);
        for(TextView control:new TextView[]{auto,upload,staticTab,videoTab,dynamicToggle,soundToggle,localFile})control.setNextFocusDownId(lower);
    }
    private void confirmDelete(String id,int initiatingKey,long downTime){
        if(picker==null||confirmation!=null&&confirmation.isShowing())return;
        WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(id);if(entry==null)return;
        if(WallpaperCatalog.DAILY.equals(id))return;
        if(WallpaperCatalog.DEFAULT.equals(id)){notice(tr("默认颜色主题不能删除","The default color theme cannot be deleted"));return;}
        stopVideo();List<String> before=focusableCards();int index=before.indexOf(id);String[] returnTo={id};
        AlertDialog dialog=new KeyFeedback.Alert(activity);dialog.setTitle(tr("删除「","Delete “")+entry.label(chinese)+tr("」？","”?"));
        dialog.setMessage(tr("删除电视中保存的壁纸，手机或电脑中的原文件不受影响。","Delete this TV copy. The original on your phone or computer is unchanged.")+(entry.id.equals(state.current)?entry.video()?tr("\n删除后恢复之前的静态壁纸。","\nRestores your previous static wallpaper."):state.rotating?tr("\n将切换到下一张有效图片；不足两张时关闭轮播。","\nSwitch to the next available photo; stop slideshow below two images."):tr("\n这张壁纸正在使用，删除后切回默认颜色主题。","\nThis wallpaper is in use. Deleting it restores the default color theme."):""));
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,tr("取消","Cancel"),(android.content.DialogInterface.OnClickListener)null);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,tr("删除","Delete"),(android.content.DialogInterface.OnClickListener)null);
        // The opening hold can continue in the new window. Consume its repeats and release.
        boolean[] releasing={initiatingKey!=KeyEvent.KEYCODE_UNKNOWN};
        dialog.setOnKeyListener((d,key,event)->{if(!releasing[0]||key!=initiatingKey)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&event.getDownTime()>downTime){releasing[0]=false;return false;}if(event.getAction()==KeyEvent.ACTION_UP)releasing[0]=false;return true;});
        dialog.setOnDismissListener(d->{if(confirmation==dialog)confirmation=null;if(active)focusCard(returnTo[0]);});
        confirmation=dialog;dialog.show();ControlStyle.dialog(dialog,unit,(canvas,paint)->{drawDockBackground(canvas,paint);if(content!=null)content.draw(canvas);},decoder);View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setOnClickListener(v->{dialog.dismiss();KeyFeedback.back(activity);});cancel.post(()->{if(dialog.isShowing())cancel.requestFocusFromTouch();});
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                String thumbnailKey=store.imageKey(entry.id);store.remove(entry.id);thumbnails.remove(thumbnailKey);before.remove(entry.id);returnTo[0]=before.isEmpty()?WallpaperCatalog.DEFAULT:before.get(Math.max(0,Math.min(index,before.size()-1)));
                refreshCards(returnTo[0],false);dialog.dismiss();KeyFeedback.confirm(activity);
            }catch(IOException e){notice(tr("删除失败，请检查电视存储后重试","Unable to delete. Check TV storage and retry."));}
        });
    }
    static Bitmap defaultThumbnail(){Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);Paint paint=new Paint(3);paint.setShader(new RadialGradient(116,59,232,new int[]{0xac557eac,0x40557eac,Color.TRANSPARENT},new float[]{0,.5f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,360,203,paint);return bitmap;}
    static final class Ambient extends View {
        private final float homeUnit;
        Runnable colorChanged;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
        Shader bloom,spill;int color=Color.TRANSPARENT,targetColor=Color.TRANSPARENT;ValueAnimator animation;
        final float[] stops={0,.12f,.26f,.42f,.58f,.74f,.88f,1};
        final float[] falloff={1,.96f,.80f,.54f,.28f,.10f,.015f,0};
        Ambient(android.content.Context context,float homeUnit){super(context);this.homeUnit=homeUnit;}
        void setColor(int value){
            float[] hsv=new float[3];Color.colorToHSV(value,hsv);
            hsv[1]=Math.min(.88f,hsv[1]);hsv[2]=.95f;value=Color.HSVToColor(hsv);
            if(value==targetColor)return;targetColor=value;
            if(animation!=null)animation.cancel();
            if(color==Color.TRANSPARENT){color=value;updateGlow();return;}
            animation=ValueAnimator.ofObject(new android.animation.ArgbEvaluator(),color,value);animation.setDuration(650);
            animation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animation.addUpdateListener(a->{color=(int)a.getAnimatedValue();updateGlow();});animation.start();
        }
        private Shader glow(float x,float y,float radius,float flatten,int alpha){
            int[] colors=new int[stops.length];
            for(int n=0;n<colors.length;n++)colors[n]=(color&0xffffff)|(Math.round(alpha*falloff[n])<<24);
            RadialGradient gradient=new RadialGradient(x*homeUnit,y*homeUnit,radius*homeUnit,colors,stops,Shader.TileMode.CLAMP);
            Matrix ellipse=new Matrix();ellipse.setScale(1,flatten,x*homeUnit,y*homeUnit);gradient.setLocalMatrix(ellipse);
            return gradient;
        }
        private void updateGlow(){
            bloom=glow(310,158,620,.68f,172);
            spill=glow(100,310,480,.72f,48);
            invalidate();if(colorChanged!=null)colorChanged.run();
        }
        @Override protected void onDraw(Canvas c){
            if(bloom==null)return;
            paint.setShader(bloom);c.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(spill);c.drawRect(0,0,getWidth(),getHeight(),paint);
        }
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }

}
