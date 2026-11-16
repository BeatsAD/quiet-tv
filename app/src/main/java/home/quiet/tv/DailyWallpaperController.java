package home.quiet.tv;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Foreground-only updates shared by every TV surface, with guarded cache commits. */
final class DailyWallpaperController {
    interface Completed {void done(boolean success,String message);}
    static final long COOLDOWN_MS=15*60*1000L;
    static final long AUTO_RETRY_MS=15*60*1000L;
    private final WallpaperStore store;
    private final boolean chinese;
    private final Runnable changed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final DailyWallpaperClient client;
    private final DailyWallpaperLoader loader;
    private Future<?> task,prefetchTask;
    private boolean warming;
    private String warmingDay="",loadingDay="";
    private volatile int generation;
    private boolean active,busy,lastSuccess=true;
    private long operation;
    private DiagnosticOperation diagnostic;
    private String message="",messageSource="";
    private final Runnable dayCheck=new Runnable(){public void run(){if(active){environmentChanged();scheduleDay();}}};
    DailyWallpaperController(Context context,WallpaperStore store,boolean chinese,Runnable changed){
        this.store=store;this.chinese=chinese;this.changed=changed;client=new DailyWallpaperClient(context.getApplicationContext());loader=new DailyWallpaperLoader(context.getApplicationContext(),store,client);
    }
    private String tr(String zh,String en){return chinese?zh:en;}
    static String today(){return today(WallpaperCatalog.BING);}
    static String today(String source){return day(source,System.currentTimeMillis());}
    static String day(String source,long now){SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US);if(WallpaperCatalog.MOVIE.equals(source))format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));return format.format(new Date(now));}
    static boolean crossedDay(String source,String requestedDay,long now){return WallpaperCatalog.MOVIE.equals(source)&&!requestedDay.equals(day(source,now));}
    boolean busy(){return busy;}
    String error(){return !lastSuccess&&messageSource.equals(DailyWallpaperLoader.source(store.snapshot()))?message:"";}
    String status(String source){
        if(source.equals(store.dailyPending()))return tr("正在获取壁纸…","Getting wallpaper…");
        if(source.equals(messageSource)&&!message.isEmpty())return message;
        WallpaperCatalog.DailyImage image=store.snapshot().dailyImages.get(source);
        if(!store.cached(image))return tr("启用后每天自动更新，断网保留上一张","Updates daily; keeps the previous image offline");
        String date=image.dateLabel();return date.isEmpty()?tr("壁纸已保存到本地  断网可用","Wallpaper cached  Available offline"):tr("图片日期 "+date+"  断网可用","Image date "+date+"  Available offline");
    }
    void resume(){active=true;environmentChanged();scheduleDay();}
    private void scheduleDay(){main.removeCallbacks(dayCheck);WallpaperCatalog state=store.snapshot();String source=DailyWallpaperLoader.source(state);long now=System.currentTimeMillis();Calendar next=Calendar.getInstance(WallpaperCatalog.MOVIE.equals(source)?TimeZone.getTimeZone("Asia/Shanghai"):TimeZone.getDefault());next.add(Calendar.DAY_OF_YEAR,1);next.set(Calendar.HOUR_OF_DAY,0);next.set(Calendar.MINUTE,0);next.set(Calendar.SECOND,1);next.set(Calendar.MILLISECOND,0);long delay=next.getTimeInMillis()-now;
        if(WallpaperCatalog.MOVIE.equals(source)){String day=today(source);boolean current=DailyWallpaperLoader.current(source,day,state.dailyImages.get(source));if(!current||!store.moviePrefetched(day)){long retry=store.movieNextCheck(day);delay=Math.min(delay,retry>now?retry-now:!current&&retry==0?1000:AUTO_RETRY_MS);}}
        if(active)main.postDelayed(dayCheck,Math.max(1000,delay));}
    void pause(){active=false;main.removeCallbacks(dayCheck);cancel();}
    void destroy(){pause();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    void cancel(){if(diagnostic!=null)diagnostic.finish("CANCELLED","inactive_or_superseded");++generation;client.cancel();if(task!=null)task.cancel(true);if(prefetchTask!=null)prefetchTask.cancel(true);task=null;prefetchTask=null;warming=false;store.finishDaily(operation);busy=false;}
    void cacheUnreadable(){message="";if(active&&!busy&&store.dailyPending().isEmpty())start(DailyWallpaperLoader.source(store.snapshot()),false,true,null);}
    void environmentChanged(){
        WallpaperCatalog state=store.snapshot();String source=DailyWallpaperLoader.source(state);
        boolean movie=WallpaperCatalog.MOVIE.equals(source);String day=today(source);
        if(active&&busy&&movie&&!day.equals(loadingDay))cancel();
        if(!active||busy||!store.dailyPending().isEmpty()||!WallpaperCatalog.DAILY.equals(state.current)||!movie&&SystemClock.elapsedRealtime()<store.dailyRetry(source))return;
        if(warming){if(movie&&day.equals(warmingDay))return;cancel();}
        WallpaperCatalog.DailyImage image=state.dailyImages.get(source);
        if(store.cached(image)&&DailyWallpaperLoader.current(source,day,image)){
            if(movie&&!store.moviePrefetched(day)&&loader.online()&&System.currentTimeMillis()>=store.movieNextCheck(day)){store.movieChecked(day,System.currentTimeMillis());prefetch(day);scheduleDay();}
            return;
        }
        if(movie?System.currentTimeMillis()<store.movieNextCheck(day):!loader.online())return;
        start(source,false,false,null);
    }
    void apply(Completed completed){apply(DailyWallpaperLoader.source(store.snapshot()),completed);}
    void apply(String source,Completed completed){start(source,true,false,completed);}
    void prepare(Completed completed){start(DailyWallpaperLoader.source(store.snapshot()),false,false,completed);}
    void refresh(Completed completed){start(DailyWallpaperLoader.source(store.snapshot()),false,true,completed);}
    private void start(String source,boolean apply,boolean force,Completed completed){
        if(!active){finish(source,tr("页面已关闭，请重新打开","Reopen this page to continue"),false,completed);return;}
        if(warming)cancel();
        final long lease,expected;
        try{synchronized(store){expected=store.manualVersion();lease=store.beginDaily(source);if(!apply&&!force&&completed==null&&WallpaperCatalog.MOVIE.equals(source))store.movieChecked(today(source),System.currentTimeMillis());}}catch(IOException error){finish(source,error.getMessage(),false,completed);return;}
        if(diagnostic!=null)diagnostic.finish("CANCELLED","superseded");
        DiagnosticOperation trace=new DiagnosticOperation("wallpaper.daily.operation","source="+(WallpaperCatalog.MOVIE.equals(source)?"movie":WallpaperCatalog.BING.equals(source)?"bing":"other")+" apply="+apply+" force="+force);diagnostic=trace;
        final String requestedDay=today(source);loadingDay=requestedDay;DiagnosticLog.info("wallpaper.daily.context","attempt="+trace.id+" requestedDay="+requestedDay);
        final int attempt=++generation;operation=lease;busy=true;message="";messageSource=source;changed.run();
        task=worker.submit(()->{
            try{
                trace.stage("LOAD_CACHE_OR_NETWORK");DailyWallpaperLoader.Check check=()->valid(attempt,lease);DailyWallpaperLoader.Result result=apply&&!force?loader.select(source,check,requestedDay):loader.load(source,force,check,requestedDay);
                main.post(()->{
                    try{
                        valid(attempt,lease);
                        if(crossedDay(source,requestedDay,System.currentTimeMillis())){
                            if(store.manualVersion()!=expected)throw new InterruptedIOException("背景已变化，请重新选择");
                            trace.finish("CANCELLED","crossed_day");cancel();start(source,apply,force,completed);return;
                        }
                        trace.stage("COMMIT");if(!store.commitDailyIfCurrent(result.image,result.prepared,apply,expected,lease))throw new InterruptedIOException("背景已变化，请重新选择");
                        String contentDay=result.image.dateLabel();DiagnosticLog.info("wallpaper.daily.context","attempt="+trace.id+" contentDay="+(contentDay.matches("[0-9-]{8,10}")?contentDay:"unknown"));trace.finish("COMPLETED",result.offline?"offline_cache":"committed");store.dailyRetry(source,0);store.finishDaily(lease);busy=false;task=null;
                        finish(source,result.offline?tr("电视离线，正在使用缓存","TV offline. Using cached wallpaper"):!apply&&WallpaperCatalog.MOVIE.equals(source)&&!DailyWallpaperLoader.current(source,today(source),result.image)?tr("暂未获取到今日电影，沿用上一张","Today's movie is not available yet. Keeping the previous image"):force&&result.unchanged?tr("暂无新图片，保留当前背景","No new image. Current background kept"):tr("壁纸已保存到本地  断网可用","Wallpaper cached  Available offline"),true,completed);
                        if(WallpaperCatalog.MOVIE.equals(source))prefetch(today(source));
                        if(apply&&!force)environmentChanged();
                    }catch(IOException error){failed(attempt,lease,source,error,completed);}
                    finally{result.close();store.finishDaily(lease);}
                });
            }catch(IOException|OutOfMemoryError error){main.post(()->failed(attempt,lease,source,error,completed));}
        });
    }
    private void prefetch(String day){
        WallpaperCatalog state=store.snapshot();
        if(!active||warming||store.moviePrefetched(day)||!loader.online()||SystemClock.elapsedRealtime()<store.dailyRetry(WallpaperCatalog.MOVIE)||!WallpaperCatalog.DAILY.equals(state.current)||!WallpaperCatalog.MOVIE.equals(DailyWallpaperLoader.source(state)))return;
        warming=true;warmingDay=day;final int attempt=generation;final long expected=store.manualVersion();
        prefetchTask=worker.submit(()->{
            boolean success=false;
            try{
                loader.prefetchMovie(day,()->{
                    WallpaperCatalog current=store.snapshot();
                    if(attempt!=generation||Thread.currentThread().isInterrupted()||!day.equals(today(WallpaperCatalog.MOVIE))||store.manualVersion()!=expected||!WallpaperCatalog.DAILY.equals(current.current)||!WallpaperCatalog.MOVIE.equals(DailyWallpaperLoader.source(current)))throw new InterruptedIOException("Cinema preparation cancelled");
                });success=true;
            }catch(IOException|OutOfMemoryError failure){DiagnosticLog.info("wallpaper.cinema.prepare_failed","error="+failure.getClass().getSimpleName());}
            final boolean complete=success;
            main.post(()->{if(attempt!=generation)return;warming=false;prefetchTask=null;if(complete&&active&&day.equals(today(WallpaperCatalog.MOVIE))&&store.manualVersion()==expected)store.moviePrefetchedDay(day);scheduleDay();});
        });
    }
    private void valid(int attempt,long lease)throws InterruptedIOException{if(attempt!=generation||Thread.currentThread().isInterrupted()||!store.dailyActive(lease))throw new InterruptedIOException("壁纸操作已取消");}
    private void failed(int attempt,long lease,String source,Throwable error,Completed completed){
        if(attempt==generation&&diagnostic!=null)diagnostic.finish(error instanceof InterruptedIOException?"CANCELLED":"FAILED",error.getClass().getSimpleName());store.finishDaily(lease);if(attempt!=generation||!active)return;busy=false;task=null;
        String reason=error instanceof InterruptedIOException?tr("背景已变化，请重新选择","Background changed. Select again"):error instanceof OutOfMemoryError?tr("内存不足，无法处理壁纸","Not enough memory to process wallpaper"):error instanceof DailyWallpaperClient.Unavailable?tr("近期必应图片均未开放壁纸下载","Recent Bing images are unavailable for wallpaper download"):error instanceof DailyWallpaperClient.Limited?tr("壁纸来源请求过于频繁，请稍后重试","Wallpaper source rate limited. Try later"):tr("壁纸获取失败，请检查电视网络或稍后重试","Unable to get wallpaper. Check the TV network or try later");
        DiagnosticLog.info("wallpaper.daily.failed","source="+source+" error="+error.getClass().getSimpleName());
        finish(source,reason+"  "+tr("已保留当前背景","Current background kept"),false,completed);
    }
    private void finish(String source,String value,boolean success,Completed completed){scheduleDay();messageSource=source;message=value;lastSuccess=success;changed.run();if(completed!=null)completed.done(success,value);}
}
