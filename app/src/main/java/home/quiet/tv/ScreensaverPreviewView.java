package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** The settings preview uses the same media and the process-wide decoder handoff. */
final class ScreensaverPreviewView extends FrameLayout {
    interface Message {void show(String message);}
    private final ManagementUi ui;
    private final WallpaperStore store;
    private final ImageView image;
    private final WallpaperWallView wall;
    private final TextView wallEmpty;
    private boolean wallReady;
    private List<String> wallImages=Collections.emptyList();
    private final ScreensaverInformationView information;
    private final VideoWallpaperPlayer video;
    private WallpaperGpuView sky;
    private FlipClockSound flipSound;
    private final Message message;
    final PreviewSyncState sync=new PreviewSyncState();
    private Runnable readyDraw;
    private boolean usingWeather,weatherReady;
    private long gpuFrameOperation=-1;
    private final Runnable mediaUpdated;
    private String capturing="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ScreensaverSettings settings;
    private String candidate="",loaded="",pending="";
    private boolean active,destroyed;
    private int generation;
    private long renderedMinute=-1;
    private final Runnable tick=this::tick;
    private final BroadcastReceiver timeChanges=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){information.resetTime();refreshTime();}
    };
    private void tick(){
        if(!active||!hasWindowFocus()||settings==null)return;
        long now=System.currentTimeMillis();
        if(now/60000!=renderedMinute){
            renderInformation(now);
            if(sky!=null&&sky.displayView().getVisibility()==VISIBLE)load();
        }else information.updateTime(now);
        scheduleTick(now);
    }
    private void scheduleTick(long now){
        main.removeCallbacks(tick);
        if(active&&hasWindowFocus()&&settings!=null){
            long period=settings.tickMillis();
            main.postDelayed(tick,period-now%period);
        }
    }
    private void refreshTime(){
        long now=System.currentTimeMillis();
        renderInformation(now);
        scheduleTick(now);
    }
    ScreensaverPreviewView(ManagementUi ui,Message message,Runnable mediaUpdated){
        super(ui);this.ui=ui;this.message=message;this.mediaUpdated=mediaUpdated;store=WallpaperStore.get(ui);setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        image=new ImageView(ui);image.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(image,new LayoutParams(-1,-1));
        video=new VideoWallpaperPlayer(ui,this,true);video.setSound(false);if(WallpaperGpuSupport.available(ui)){sky=new WallpaperGpuView(ui,new WallpaperGpuView.Listener(){public void ready(){if(usingWeather){weatherReady=true;readyAfterDraw(sync.request());}message.show("");}public void failed(String detail){if(usingWeather)sync.failed(sync.request(),ui.tr("天气效果暂不可用，预览未同步","Weather preview unavailable"));if(sky!=null){sky.active(false);sky.displayView().setVisibility(GONE);}message.show(ui.tr("天气效果暂不可用，已使用静态背景","Weather effect unavailable; using the static background"));}});sky.displayView().setVisibility(GONE);addView(sky.displayView(),new LayoutParams(-1,-1));}
        wall=new WallpaperWallView(ui,store,available->{
            wallReady=available;
            wallEmptyStatus(available);
            readyAfterDraw(sync.request());
        });
        wall.setVisibility(GONE);
        addView(wall,new LayoutParams(-1,-1));
        wallEmpty=ui.label(ui.tr("上传静态壁纸后即可预览","Upload wallpapers to preview"),16);
        wallEmpty.setGravity(Gravity.CENTER);
        wallEmpty.setVisibility(GONE);
        addView(wallEmpty,new LayoutParams(-1,-1));
        information=new ScreensaverInformationView(ui,67.2f*ui.unit,10.56f*ui.unit);addView(information,new LayoutParams(-1,-1));
        information.onLanded(()->{if(flipSound!=null&&flipSoundAllowed())flipSound.play();});
    }
    private void wallEmptyStatus(boolean available){
        wallEmpty.setVisibility(available?GONE:VISIBLE);
        information.setVisibility(available?VISIBLE:INVISIBLE);
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(wall!=null)wall.active(active&&focused&&wall.getVisibility()==VISIBLE);if(information!=null)information.active(active&&focused);updateFlipSound();if(video!=null&&active)video.setPaused(!focused);if(active&&focused)refreshTime();else if(main!=null)main.removeCallbacks(tick);}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateFlipSound();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);updateFlipSound();}
    private boolean flipSoundAllowed(){
        return active&&!destroyed&&isShown()&&getWindowVisibility()==VISIBLE&&hasWindowFocus()
                &&settings!=null&&settings.flipping()&&settings.flipSound;
    }
    private void updateFlipSound(){
        if(!flipSoundAllowed()){if(flipSound!=null)flipSound.stop();return;}
        if(flipSound==null)try{flipSound=new FlipClockSound(ui);}
        catch(RuntimeException error){DiagnosticLog.error("screensaver.preview.flip_sound.unavailable",error);}
    }
    @Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(Math.round(MeasureSpec.getSize(width)*9f/16),MeasureSpec.EXACTLY));}
    void show(ScreensaverSettings settings,String candidate){this.settings=settings;this.candidate=candidate==null?"":candidate;updateFlipSound();refreshTime();load();}
    private final Runnable dailyQuoteChanged=()->refreshQuote(System.currentTimeMillis(),false);
    private void refreshQuote(long now,boolean retry){
        information.refreshQuote(now,new DesktopSettingsStore(ui).showScreensaverQuote(),active&&isShown()&&hasWindowFocus(),retry);
    }
    private void renderInformation(long now){
        if(settings==null)return;renderedMinute=now/60000;refreshQuote(now,false);WeatherData data=new WeatherStore(ui).cached(ui.chinese());boolean available=data!=null&&data.usable(now);
        information.show(settings,now,available?data.city+"  "+data.temperatureText()+"  "+data.description:"");
    }
    private void load(){
        if(!active||settings==null||destroyed)return;
        WallpaperCatalog current=store.snapshot();String wanted=ScreensaverSettings.CUSTOM.equals(settings.mode)&&!candidate.isEmpty()?settings.media(candidate).resolved(current):settings.resolved(current);
        List<String> wallIds=settings.wall()?WallpaperWall.ids(current):Collections.emptyList();
        long previous=sync.request();long operation=sync.begin(Arrays.asList(wallIds,settings.mode,settings.showTime,settings.showWeather,settings.flipShowSeconds,settings.clockPosition(),settings.weatherPosition,settings.clock,wanted,store.imageKey(wanted),current.weather,current.weatherDayNight),PreviewSyncState.BACKGROUND);
        if(previous!=operation&&readyDraw!=null){readyDraw.run();readyDraw=null;}
        if(settings.wall()){
            if(wall.getVisibility()!=VISIBLE||previous!=operation){
                ++generation;pending="";video.stop();
                if(sky!=null){sky.active(false);sky.displayView().setVisibility(GONE);}
                usingWeather=false;weatherReady=false;image.setVisibility(GONE);
                message.show("");
            }
            wall.setVisibility(VISIBLE);
            if(wallIds.isEmpty()){wall.clear();wallReady=false;wallEmptyStatus(false);readyAfterDraw(operation);return;}
            if(!wallImages.equals(wallIds)){
                wallImages=wallIds;
                wallReady=false;
                wallEmpty.setVisibility(GONE);
                information.setVisibility(INVISIBLE);
            }
            wall.show(wallIds);
            wall.active(hasWindowFocus());
            if(wallReady)readyAfterDraw(operation);
            return;
        }
        if(wall.getVisibility()==VISIBLE){wall.clear();wall.setVisibility(GONE);wallReady=false;}
        wallEmpty.setVisibility(GONE);
        information.setVisibility(VISIBLE);
        boolean clockOnly=settings.clockOnly()||candidate.isEmpty()&&settings.customBlank(current);usingWeather=false;setBackgroundColor(0xff000000);image.setVisibility(clockOnly?GONE:VISIBLE);
        if(clockOnly){if(sky!=null){sky.active(false);sky.displayView().setVisibility(GONE);}++generation;pending="";video.stop();readyAfterDraw(operation);return;}
        WallpaperCatalog catalog=store.snapshot();WeatherData weatherData=new WeatherStore(ui).cached(ui.chinese());boolean weather=ScreensaverSettings.FOLLOW.equals(settings.mode)&&catalog.weather&&sky!=null&&weatherData!=null&&weatherData.usable(System.currentTimeMillis());if(sky!=null){sky.displayView().setVisibility(weather?VISIBLE:GONE);sky.active(active&&weather);if(weather){sky.strength(0);sky.weather(WeatherWallpaperScene.key(weatherData.description,weatherData.icon,false,12),WeatherIllumination.at(System.currentTimeMillis(),weatherData.latitude,weatherData.longitude,catalog.weatherDayNight),weatherData.atmosphere);}}usingWeather=weather;if(!weather)weatherReady=false;if(weather){video.stop();if(gpuFrameOperation!=operation){gpuFrameOperation=operation;sky.afterFrame(()->{if(weatherReady)readyAfterDraw(operation);});}}
        else if(ScreensaverSettings.FOLLOW.equals(settings.mode)&&catalog.weather)sync.failed(operation,ui.tr("天气效果暂不可用，预览未同步","Weather preview unavailable"));
        String id=ScreensaverSettings.CUSTOM.equals(settings.mode)&&!candidate.isEmpty()?settings.media(candidate).resolved(catalog):settings.resolved(catalog);WallpaperCatalog.Entry entry=catalog.find(id);if(entry==null){sync.failed(operation,ui.tr("背景素材不可用","Background media unavailable"));return;}
        String key=store.imageKey(id);if(key.equals(loaded)){play(entry);if(!entry.video()&&!usingWeather)readyAfterDraw(operation);return;}if(key.equals(pending))return;pending=key;video.stop();int request=++generation;
        worker.execute(()->{
            Bitmap decoded=null;String failure="";
            try{decoded=WallpaperCatalog.DEFAULT.equals(entry.id)?WallpaperController.defaultThumbnail():WallpaperImages.decode(ui,store,entry,960,540);}catch(IOException|RuntimeException|OutOfMemoryError error){failure=entry.video()?"":"背景预览无法读取";DiagnosticLog.info("screensaver.preview.unavailable",entry.id);}
            Bitmap ready=decoded;String notice=failure;main.post(()->{if(destroyed||request!=generation||!active){if(ready!=null)ready.recycle();return;}pending="";loaded=key;image.setImageBitmap(ready);message.show(notice);if(!notice.isEmpty())sync.failed(sync.request(),notice);play(entry);if(!entry.video()&&!usingWeather)readyAfterDraw(sync.request());});
        });
    }
    private void play(WallpaperCatalog.Entry entry){if(!active||!entry.video()||usingWeather)return;long operation=sync.request();video.setSound(false);video.play(entry,new VideoWallpaperPlayer.Listener(){public void ready(){message.show("");readyAfterDraw(operation);capturePoster(entry);}public void failed(VideoWallpaperPlayer.Failure failure){video.stop();sync.failed(operation,failure.hint(ui.chinese()));message.show(failure.hint(ui.chinese()));}public void stopped(){}});if(video.ready())readyAfterDraw(operation);}
    private void readyAfterDraw(long operation){
        if(!active||operation!=sync.request()||sync.status()!=PreviewSyncState.Status.SYNCING)return;
        if(readyDraw!=null){readyDraw.run();readyDraw=null;}
        readyDraw=UiCompat.onDraw(this,()->post(()->{if(operation!=sync.request()||!active)return;if(readyDraw!=null){readyDraw.run();readyDraw=null;}sync.ready(operation,PreviewSyncState.BACKGROUND);}));invalidate();
    }
    private void capturePoster(WallpaperCatalog.Entry entry){
        if(destroyed||!active||!entry.custom()||store.poster(entry.id).isFile()||capturing.equals(entry.id))return;capturing=entry.id;int request=generation;
        video.sourceSnapshot(bitmap->{if(bitmap==null){capturing="";return;}if(destroyed||!active||request!=generation){capturing="";bitmap.recycle();return;}worker.execute(()->{boolean saved=false;try{saved=store.saveVideoPoster(entry.id,bitmap);}catch(IOException|RuntimeException error){DiagnosticLog.error("screensaver.preview.poster_failed",error);}boolean ready=saved;main.post(()->{capturing="";if(destroyed||!active||request!=generation){bitmap.recycle();return;}if(ready){loaded=store.imageKey(entry.id);image.setImageBitmap(bitmap);mediaUpdated.run();}else bitmap.recycle();});});});
    }
    void resume(){
        if(active)return;
        active=true;
        DailyQuote.get(ui).observe(dailyQuoteChanged);
        IntentFilter filter=new IntentFilter(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ui.registerReceiver(timeChanges,filter);
        information.active(hasWindowFocus());
        updateFlipSound();
        refreshTime();
        refreshQuote(System.currentTimeMillis(),true);
        load();
    }
    void pause(){if(!active)return;wall.clear();wallReady=false;information.active(false);active=false;DailyQuote.get(ui).forget(dailyQuoteChanged);updateFlipSound();ui.unregisterReceiver(timeChanges);weatherReady=false;sync.invalidate();gpuFrameOperation=-1;if(readyDraw!=null){readyDraw.run();readyDraw=null;}if(sky!=null)sky.afterFrame(null);++generation;pending="";main.removeCallbacks(tick);video.stop();if(sky!=null)sky.active(false);}
    void destroy(){pause();wall.destroy();destroyed=true;information.onLanded(null);if(flipSound!=null){flipSound.destroy();flipSound=null;}worker.shutdownNow();video.destroy();if(sky!=null)sky.destroy();image.setImageDrawable(null);}
}
