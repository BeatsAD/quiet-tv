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
            try{Future<?> saved=instance.enqueue("ERROR","process.uncaught",trace(error));if(saved!=null)saved.get(500,TimeUnit.MILLISECONDS);}
            catch(Exception ignored){}
            finally{if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}
        });
    }
    static void info(String event,String detail){DiagnosticLog log=instance;if(log!=null)log.enqueue("INFO",event,detail);}
    static void error(String event,Throwable error){DiagnosticLog log=instance;if(log!=null)log.enqueue("ERROR",event,trace(error));}
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
                +"\n本进程写入失败："+log.failedWrites+"  队列丢弃："+log.droppedTotal.get()+"\n范围：Quiet TV 自身关键事件及错误，不含完整系统日志、其他应用操作或逐次按键。\n时间字段为 Unix 毫秒；容量轮转会覆盖较早日志，进程停止期间不记录，异常终止可能丢失末尾记录。\n请检查内容后私下分享。\n\n[历史事件]\n";
            byte[] headerBytes=header.getBytes(StandardCharsets.UTF_8);
            if(headerBytes.length>32768)throw new IOException("Diagnostic snapshot exceeds limit");
            try(OutputStream output=new FileOutputStream(file)){output.write(headerBytes);log.journal.copyTo(output);}
            return file;
        });
        try{return result.get(5,TimeUnit.SECONDS);}catch(TimeoutException e){result.cancel(false);throw e;}
    }
    @androidx.annotation.RequiresApi(24) private static final class Api24 {static Context storage(Context context){return context.createDeviceProtectedStorageContext();}}
}
