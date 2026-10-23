package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.net.*;
import android.os.*;
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
    private final Map<String,Long> retryAt=new HashMap<>();
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
        return store.cached(store.snapshot().dailyImages.get(source))?tr("壁纸已保存到本地  断网可用","Wallpaper cached  Available offline"):tr("启用后每天自动更新，断网保留上一张","Updates daily; keeps the previous image offline");
    }
    private boolean online(){
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info=manager==null?null:manager.getActiveNetworkInfo();return info!=null&&info.isConnected();
    }
    private long retryTime(String source){Long value=retryAt.get(source);return value==null?0:value;}
    void resume(){active=true;environmentChanged();}
    void pause(){active=false;cancel();}
    void destroy(){pause();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    void cancel(){++generation;client.cancel();if(task!=null)task.cancel(true);task=null;busy=false;pendingSource=null;}
    void cacheUnreadable(){unreadable.add(store.snapshot().dailySource);environmentChanged();}
    void environmentChanged(){
        WallpaperCatalog state=store.snapshot();
        if(!active||busy||!WallpaperCatalog.DAILY.equals(state.current)||!online())return;
        WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);
        if(store.cached(image)&&!unreadable.contains(state.dailySource)&&today().equals(image.day))return;
        if(SystemClock.elapsedRealtime()<retryTime(state.dailySource))return;
        start(state.dailySource,false,false,null);
    }
    void apply(String source,Completed completed){start(source,true,false,completed);}
    void check(String source,Completed completed){start(source,false,true,completed);}
    private void start(String source,boolean apply,boolean manual,Completed completed){
        if(!active||!WallpaperCatalog.dailySource(source)||busy)return;
        WallpaperCatalog state=store.snapshot();WallpaperCatalog.DailyImage old=state.dailyImages.get(source);
        boolean cached=store.cached(old)&&!unreadable.contains(source),connected=online();String day=today();
        boolean reuse=cached&&(!connected||(!manual&&apply&&day.equals(old.day))||(WallpaperCatalog.BT.equals(source)&&day.equals(old.day)));
        if(!reuse&&SystemClock.elapsedRealtime()<retryTime(source)){
            finishMessage(source,tr("来源暂不可用，请稍后重试","Source unavailable. Try again later."),false,completed);return;
        }
        if(!connected&&!reuse){finishMessage(source,tr("网络不可用  此来源暂无缓存","Offline  No cache for this source"),false,completed);return;}
        final int attempt=++generation;busy=true;pendingSource=source;message="";messageSource=source;changed.run();
        task=worker.submit(()->{
            try{
                WallpaperCatalog.DailyImage ready=old;File prepared=null;
                if(reuse){validate(store.dailyFile(old.source,old.slot));}
                else{
                    DailyWallpaperClient.Candidate candidate=null;String key=null;boolean duplicate=false;
                    int attempts=WallpaperCatalog.BT.equals(source)&&old!=null&&!day.equals(old.day)?3:1;
                    File download=new File(store.dailyDirectory(),"download.tmp");
                    for(int n=0;n<attempts;n++){
                        valid(attempt);candidate=client.latest(source);valid(attempt);
                        if(cached&&candidate.url.equals(old.url)&&WallpaperCatalog.BING.equals(source)){key=old.key;duplicate=true;break;}
                        key=client.download(candidate,download);valid(attempt);duplicate=cached&&old.key.equals(key);
                        if(!duplicate||WallpaperCatalog.BING.equals(source)||day.equals(old.day))break;
                    }
                    if(duplicate&&WallpaperCatalog.BT.equals(source)&&!day.equals(old.day))throw new IOException("No different landscape available");
                    if(duplicate){validate(store.dailyFile(old.source,old.slot));}
                    else{
                        if(store.dailyDirectory().getUsableSpace()<24L*1024*1024)throw new IOException("Insufficient wallpaper storage");
                        prepared=new File(store.dailyDirectory(),"prepared.tmp");
                        int[] size=WallpaperImages.prepare(download,prepared,Math.min(1920,WallpaperImages.importLimit(context)));valid(attempt);
                        if(prepared.length()>DailyWallpaperClient.MAX_IMAGE_BYTES)throw new IOException("Prepared wallpaper too large");
                        ready=new WallpaperCatalog.DailyImage();ready.width=size[0];ready.height=size[1];
                    }
                    // Copy cached metadata before changing its date; the committed catalog is untouched.
                    if(duplicate)ready=copy(old);
                    ready.source=source;ready.key=key;ready.url=candidate.url;ready.title=candidate.title;ready.copyright=candidate.copyright;ready.link=candidate.link;ready.upstream=candidate.upstream;ready.day=day;ready.checked=System.currentTimeMillis();
                }
                valid(attempt);WallpaperCatalog.DailyImage image=ready;File file=prepared;
                main.post(()->{
                    if(attempt!=generation||!active)return;
                    try{
                        store.commitDaily(image,file,apply);retryAt.remove(source);unreadable.remove(source);busy=false;task=null;pendingSource=null;
                        finishMessage(source,tr("壁纸已保存到本地  断网可用","Wallpaper cached  Available offline"),true,completed);
                    }catch(IOException e){failed(attempt,source,e,completed);}
                });
            }catch(IOException|OutOfMemoryError e){main.post(()->failed(attempt,source,e,completed));}
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
    private void failed(int attempt,String source,Throwable error,Completed completed){
        if(attempt!=generation||!active)return;
        if(error instanceof CacheInvalid)unreadable.add(source);
        long delay=error instanceof DailyWallpaperClient.Limited?Math.max(COOLDOWN_MS,((DailyWallpaperClient.Limited)error).retryMs):COOLDOWN_MS;
        retryAt.put(source,SystemClock.elapsedRealtime()+delay);busy=false;task=null;pendingSource=null;
        finishMessage(source,tr("更新失败  保留当前壁纸，请稍后重试","Update failed  Current wallpaper kept. Try later."),false,completed);
    }
    private void finishMessage(String source,String value,boolean success,Completed completed){messageSource=source;message=value;changed.run();if(completed!=null)completed.done(success,value);}
}
