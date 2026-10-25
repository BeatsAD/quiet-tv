package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit application events only, with at most 32 queued entries and one idle-expiring worker. */
final class DiagnosticLog {
    private static volatile DiagnosticLog instance;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"quiet-diagnostic-log");t.setDaemon(true);return t;});
    private final AtomicInteger dropped=new AtomicInteger();
    private DiagnosticJournal journal;
    private IOException storageError;
    private int failedWrites;
    private final AtomicInteger droppedTotal=new AtomicInteger();
    private DiagnosticLog(Context supplied){
        worker.allowCoreThreadTimeOut(true);
        Context context=Build.VERSION.SDK_INT>=24?Api24.storage(supplied):supplied;
        worker.execute(()->{try{journal=new DiagnosticJournal(new File(context.getFilesDir(),"diagnostic-log"));}catch(IOException|RuntimeException e){storageError=new IOException("Diagnostic storage unavailable",e);}});
    }
    static synchronized void init(Context context){
        if(instance!=null)return;
        instance=new DiagnosticLog(context.getApplicationContext());
        info("process.start","version="+BuildConfig.VERSION_NAME+" build="+BuildConfig.VERSION_CODE+" api="+Build.VERSION.SDK_INT);
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                String crash=Long.toHexString(System.currentTimeMillis())+"-"+thread.getId();
                List<String> parts=crashTrace(error);
                Future<?> saved=instance.enqueue("ERROR","process.uncaught","crash="+crash+" parts="+parts.size()+" type="+bounded(error.getClass().getName(),96));
                for(int i=0;i<parts.size();i++){
                    Future<?> next=instance.enqueue("ERROR","process.uncaught.stack","crash="+crash+" part="+(i+1)+"/"+parts.size()+" "+parts.get(i));
                    if(next!=null)saved=next;
                }
                if(saved!=null)saved.get(500,TimeUnit.MILLISECONDS);
            }
            catch(Exception ignored){}
            finally{if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}
        });
    }
    static void info(String event,String detail){DiagnosticLog log=instance;if(log!=null)log.enqueue("INFO",event,detail);}
    static void error(String event,Throwable error){
        DiagnosticLog log=instance;if(log==null)return;
        if(event.startsWith("weather.")||event.startsWith("wallpaper.daily."))log.enqueue("ERROR",event+".reason",NetworkDiagnostics.failureDetails(error)
            +(error instanceof WeatherClient.ApiException?" status="+((WeatherClient.ApiException)error).status:""));
        log.enqueue("ERROR",event,trace(error));
    }
    static void networkTls(String event,javax.net.ssl.SSLSocketFactory factory){
        DiagnosticLog log=instance;if(log==null)return;
        try{log.enqueue("INFO",event,NetworkDiagnostics.tlsDetails(factory));}
        catch(IOException|RuntimeException e){log.enqueue("INFO",event,"probe_failed "+NetworkDiagnostics.failureDetails(e));}
    }
    static void error(String event,String detail,Throwable error){DiagnosticLog log=instance;if(log!=null)log.enqueue("ERROR",event,detail+" "+trace(error));}
    // Deliberately omit data, extras, labels and URI paths: they may contain user information.
    static String intent(android.content.Intent intent){
        return "action="+intent.getAction()+" component="+(intent.getComponent()==null?"implicit":intent.getComponent().flattenToShortString())
            +" package="+intent.getPackage()+" scheme="+intent.getScheme();
    }
    // Exception messages can include input, URLs and credentials: keep only types and bounded frames.
    private static String trace(Throwable error){
        StringBuilder text=new StringBuilder();
        for(int cause=0;error!=null&&cause<3;cause++,error=error.getCause()){
            text.append(error.getClass().getName());StackTraceElement[] frames=error.getStackTrace();
            for(int i=0;i<Math.min(8,frames.length);i++)text.append(" <- ").append(frames[i]);
            text.append(' ');if(text.length()>900)break;
        }
        return text.toString();
    }
    // Keep deeper call sites, including obfuscated frames, without exception messages or unbounded entries.
    static List<String> crashTrace(Throwable error){
        List<String> parts=new ArrayList<>();
        for(int cause=0;error!=null&&cause<3;cause++,error=error.getCause()){
            StackTraceElement[] frames=error.getStackTrace();int count=Math.min(24,frames.length);
            for(int start=0;start<Math.max(1,count);start+=3){
                int end=Math.min(start+3,count);
                StringBuilder part=new StringBuilder("cause="+cause+" type="+bounded(error.getClass().getName(),96)+" frameCount="+frames.length+" firstFrame="+start);
                for(int i=start;i<end;i++)part.append(" <- ").append(bounded(frames[i].toString(),200));
                if(end==count){
                    part.append(" omittedFrames=").append(frames.length-count);
                    if(cause==2&&error.getCause()!=null)part.append(" causesTruncated=true");
                }
                parts.add(part.toString());
            }
        }
        return parts;
    }
    private static String bounded(String value,int limit){return value.length()>limit?value.substring(0,limit-3)+"...":value;}
    private Future<?> enqueue(String level,String event,String detail){
        byte[] entry=DiagnosticJournal.entry(System.currentTimeMillis()+" "+level+" "+event+" "+detail);
        try{return worker.submit(()->{
            if(journal==null)return;
            try{reportDropped();journal.append(entry);}catch(IOException e){failedWrites++;}
        });}catch(RejectedExecutionException full){dropped.incrementAndGet();droppedTotal.incrementAndGet();return null;}
    }
    private void reportDropped() throws IOException {
        int count=dropped.getAndSet(0);
        if(count>0)journal.append(DiagnosticJournal.entry(System.currentTimeMillis()+" WARN log.queue_dropped count="+count));
    }
    static File export(Context supplied,String snapshot) throws Exception {
        DiagnosticLog log=instance;if(log==null)throw new IOException("Diagnostic log unavailable");
        Context context=supplied.getApplicationContext();
        Future<File> result=log.worker.submit(()->{
            if(log.journal==null)throw new IOException("Diagnostic log incomplete",log.storageError);
            log.reportDropped();File file=new File(context.getCacheDir(),"diagnostic-export.txt");
            String header="Quiet TV 诊断日志\n生成时间："+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date())
                +"\n"+snapshot
                +"\n本进程写入失败："+log.failedWrites+"  队列丢弃："+log.droppedTotal.get()+"\n范围：Quiet TV 自身关键事件及错误，包含首页恢复、窗口焦点、每次恢复的首次视图树绘制回调，以及三项系统入口的确认键和路由结果，不含完整系统日志或其他应用操作。绘制回调和窗口焦点不证明画面已经上屏；跳转记录不证明系统页面正常显示或物理信号已切换。\n时间字段为 Unix 毫秒；首页事件的 uptimeMs、elapsedMs 使用不含深度休眠的单调时钟；容量轮转会覆盖较早日志，进程停止期间不记录，异常终止可能丢失末尾记录。\n请检查内容后私下分享。\n\n[历史事件]\n";
            byte[] headerBytes=header.getBytes(StandardCharsets.UTF_8);
            if(headerBytes.length>32768)throw new IOException("Diagnostic snapshot exceeds limit");
            try(OutputStream output=new FileOutputStream(file)){output.write(headerBytes);log.journal.copyTo(output);}
            return file;
        });
        try{return result.get(5,TimeUnit.SECONDS);}catch(TimeoutException e){result.cancel(false);throw e;}
    }
    @androidx.annotation.RequiresApi(24) private static final class Api24 {static Context storage(Context context){return context.createDeviceProtectedStorageContext();}}
}
