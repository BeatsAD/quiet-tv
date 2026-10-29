package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Foreground-only updates. A generation owns both the cache commit and its UI result. */
final class DailyWallpaperController {
    interface Completed { void done(boolean success,String message); }
    private static final long COOLDOWN_MS=15*60*1000L;
    private final Context context;
    private final WallpaperStore store;
    private final boolean chinese;
    private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final DailyWallpaperClient client;
    private static final class Failure {
        final long retryAt;
        final boolean limited;
        final String message;
        Failure(long retryAt,boolean limited,String message){this.retryAt=retryAt;this.limited=limited;this.message=message;}
    }
    private final Map<String,Failure> failures=new HashMap<>();
    private final Set<String> unreadable=new HashSet<>();
    private static final class CacheInvalid extends IOException {}
    private Future<?> task;
    private volatile int generation;
    private boolean active,busy;
    private String pendingSource,message="",messageSource="";

    DailyWallpaperController(Context context,WallpaperStore store,boolean chinese,Runnable changed){this.context=context.getApplicationContext();this.store=store;this.chinese=chinese;this.changed=changed;client=new DailyWallpaperClient(this.context);}
    private String tr(String zh,String en){return chinese?zh:en;}
    static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    boolean busy(){return busy;}
    String status(String source){
        if(busy&&source.equals(pendingSource))return tr("正在获取壁纸…","Getting wallpaper…");
        if(source.equals(messageSource)&&!message.isEmpty())return message;
        Failure failure=failures.get(source);if(failure!=null)return failureStatus(failure);
        WallpaperCatalog.DailyImage image=store.snapshot().dailyImages.get(source);
        return store.cached(image)?cachedStatus(image):tr("启用后每天自动更新，断网保留上一张","Updates daily; keeps the previous image offline");
    }
    private String cachedStatus(WallpaperCatalog.DailyImage image){
        String date=image.dateLabel();
        return date.isEmpty()?tr("壁纸已保存到本地  断网可用","Wallpaper cached  Available offline"):tr("图片日期 "+date+"  断网可用","Image date "+date+"  Available offline");
    }
    private boolean online(){
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info=manager==null?null:manager.getActiveNetworkInfo();return info!=null&&info.isConnected();
    }
    private long retryTime(String source){Failure failure=failures.get(source);return failure==null?0:failure.retryAt;}
    private String failureStatus(Failure failure){
        long remaining=failure.retryAt-SystemClock.elapsedRealtime();
        if(failure.limited&&remaining>0){long minutes=(remaining+59999)/60000;return failure.message+"  "+tr("请在 "+minutes+" 分钟后重试","Try again in "+minutes+" min");}
        return failure.message+"  "+tr("稍后重试","Try again later");
    }
    void resume(){active=true;environmentChanged();}
    void pause(){active=false;cancel();}
    void destroy(){pause();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    void cancel(){++generation;client.cancel();if(task!=null)task.cancel(true);task=null;busy=false;pendingSource=null;}
    void cacheUnreadable(){unreadable.add(WallpaperCatalog.BING);environmentChanged();}
    void environmentChanged(){
        WallpaperCatalog state=store.snapshot();
        if(!active||busy||!WallpaperCatalog.DAILY.equals(state.current)||!online())return;
        WallpaperCatalog.DailyImage image=state.dailyImages.get(WallpaperCatalog.BING);
        if(WallpaperCatalog.BING.equals(state.dailySource)&&store.cached(image)&&!unreadable.contains(WallpaperCatalog.BING)&&today().equals(image.day))return;
        if(SystemClock.elapsedRealtime()<retryTime(WallpaperCatalog.BING))return;
        start(false,null);
    }
    void apply(Completed completed){start(true,completed);}
    void prepare(Completed completed){start(false,completed);}
    private void start(boolean apply,Completed completed){
        if(!active||busy)return;
        final String source=WallpaperCatalog.BING;
        WallpaperCatalog state=store.snapshot();WallpaperCatalog.DailyImage old=state.dailyImages.get(source);
        boolean cached=store.cached(old)&&!unreadable.contains(source),connected=online();String day=today();
        boolean reuse=cached&&(!connected||day.equals(old.day));
        Failure previous=failures.get(source);
        if(!reuse&&previous!=null&&SystemClock.elapsedRealtime()<previous.retryAt){
            finishMessage(source,failureStatus(previous),false,completed);return;
        }
        if(!connected&&!reuse){finishMessage(source,tr("网络不可用  此来源暂无缓存","Offline  No cache for this source"),false,completed);return;}
        final int attempt=++generation;busy=true;pendingSource=source;message="";messageSource=source;changed.run();
        task=worker.submit(()->{
            String stage="image";File download=null;
            try{
                WallpaperCatalog.DailyImage ready=old;File prepared=null;
                if(reuse){validate(store.dailyFile(old.source,old.slot));}
                else{
                    stage="metadata";valid(attempt);DailyWallpaperClient.Candidate candidate=client.latest(source);valid(attempt);
                    boolean keepCached=cached&&candidate.reuses(old),duplicate=keepCached;String key=keepCached?old.key:null;
                    if(!keepCached){
                        stage="save";download=new File(store.dailyDirectory(),"download.tmp");
                        stage="download";key=client.download(candidate,download);valid(attempt);duplicate=cached&&old.key.equals(key);
                    }
                    if(duplicate){stage="image";validate(store.dailyFile(old.source,old.slot));}
                    else{
                        stage="save";if(store.dailyDirectory().getUsableSpace()<24L*1024*1024)throw new IOException("Insufficient wallpaper storage");
                        prepared=new File(store.dailyDirectory(),"prepared.tmp");
                        stage="image";int[] size=WallpaperImages.prepare(download,prepared,Math.min(1920,WallpaperImages.importLimit(context)));valid(attempt);
                        if(prepared.length()>DailyWallpaperClient.MAX_IMAGE_BYTES)throw new IOException("Prepared wallpaper too large");
                        ready=new WallpaperCatalog.DailyImage();ready.width=size[0];ready.height=size[1];
                    }
                    // Copy cached metadata before changing its date; the committed catalog is untouched.
                    if(duplicate)ready=copy(old);
                    if(!keepCached){ready.source=source;ready.key=key;ready.url=candidate.url;ready.title=candidate.title;ready.copyright=candidate.copyright;ready.link=candidate.link;ready.upstream=candidate.upstream;}
                    ready.day=day;ready.checked=System.currentTimeMillis();
                }
                valid(attempt);WallpaperCatalog.DailyImage image=ready;File file=prepared;
                main.post(()->{
                    if(attempt!=generation||!active)return;
                    try{
                        store.commitDaily(image,file,apply);failures.remove(source);unreadable.remove(source);busy=false;task=null;pendingSource=null;
                        finishMessage(source,cachedStatus(image),true,completed);
                    }catch(IOException e){failed(attempt,source,"save",e,completed);}
                });
            }catch(IOException|OutOfMemoryError e){String failedStage=stage;main.post(()->failed(attempt,source,failedStage,e,completed));}
            finally{if(download!=null&&download.exists()&&!download.delete())Log.w("QuietTV","Daily wallpaper download temporary file retained");}
        });
    }
    private static WallpaperCatalog.DailyImage copy(WallpaperCatalog.DailyImage image) throws IOException {
        try{return WallpaperCatalog.DailyImage.read(image.json());}catch(org.json.JSONException e){throw new IOException(e);}
    }
    private void valid(int attempt) throws InterruptedIOException {if(attempt!=generation||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    private static void validate(File file) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),options);
        if(bitmap==null)throw new CacheInvalid();bitmap.recycle();
    }
    private void failed(int attempt,String source,String stage,Throwable error,Completed completed){
        if(attempt!=generation||!active)return;
        if(error instanceof CacheInvalid)unreadable.add(source);
        long delay=error instanceof DailyWallpaperClient.Limited?Math.max(COOLDOWN_MS,((DailyWallpaperClient.Limited)error).retryMs):COOLDOWN_MS;
        boolean limited=error instanceof DailyWallpaperClient.Limited;
        String reason;
        if(error instanceof DailyWallpaperClient.Unavailable)reason=tr("近期必应图片均未开放壁纸下载","Recent Bing images are unavailable for wallpaper download");
        else if(limited)reason=tr("壁纸来源请求过于频繁","Wallpaper source rate limited");
        else if(error instanceof OutOfMemoryError)reason=tr("内存不足，无法处理壁纸","Not enough memory to process wallpaper");
        else if("save".equals(stage))reason=tr("壁纸保存失败，请检查存储空间","Unable to save wallpaper. Check storage");
        else if("image".equals(stage))reason=tr("壁纸图片处理失败","Unable to process wallpaper image");
        else reason=tr("壁纸请求失败，请检查网络或稍后重试","Wallpaper request failed. Check network or try later");
        Failure failure=new Failure(SystemClock.elapsedRealtime()+delay,limited,reason+"  "+tr("已保留当前壁纸","Current wallpaper kept"));
        failures.put(source,failure);busy=false;task=null;pendingSource=null;
        DiagnosticLog.info("wallpaper.daily.failed","source="+source+" stage="+stage);DiagnosticLog.error("wallpaper.daily.error",error);
        // Keep causes useful without logging URLs, response bodies or exception messages.
        Log.w("QuietTV","Daily wallpaper failed: source="+source+" stage="+stage+" error="+error.getClass().getSimpleName()
            +(error instanceof DailyWallpaperClient.HttpFailure?" http="+((DailyWallpaperClient.HttpFailure)error).status:limited?" http=429":""));
        finishMessage(source,failureStatus(failure),false,completed);
    }
    private void finishMessage(String source,String value,boolean success,Completed completed){messageSource=source;message=value;changed.run();if(completed!=null)completed.done(success,value);}
}
