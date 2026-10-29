package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;
import java.io.*;
import home.quiet.tv.Charsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Explicit application events only, with at most 32 queued entries and one idle-expiring worker. */
final class DiagnosticLog {
    private static volatile DiagnosticLog instance;
    static boolean navigationKey(int code){return code==KeyEvent.KEYCODE_HOME||code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_MENU||code==KeyEvent.KEYCODE_TV_INPUT
        ||code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER;}
    static void navigationKey(String source,KeyEvent event){
        if(event==null||!navigationKey(event.getKeyCode())||event.getRepeatCount()!=0
            ||event.getAction()!=KeyEvent.ACTION_DOWN&&event.getAction()!=KeyEvent.ACTION_UP)return;
        info("input.navigation","source="+source+" keyCode="+event.getKeyCode()+" action="+event.getAction()+" eventTime="+event.getEventTime()+InputDiagnostics.key(event));
    }
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"quiet-diagnostic-log");t.setDaemon(true);return t;});
    private final DiagnosticEventBuffer buffer=new DiagnosticEventBuffer();
    private boolean drainScheduled;
    private DiagnosticJournal journal;
    private IOException storageError;
    private int failedWrites;
    private DiagnosticLog(Context supplied){
        worker.allowCoreThreadTimeOut(true);
        Context context=Build.VERSION.SDK_INT>=24?Api24.storage(supplied):supplied;
        worker.execute(()->{try{journal=new DiagnosticJournal(new File(context.getFilesDir(),"diagnostic-log"));}catch(IOException|RuntimeException e){storageError=new IOException("Diagnostic storage unavailable",e);}});
    }
    static synchronized void init(Context context){
        if(instance!=null)return;
        instance=new DiagnosticLog(context.getApplicationContext());
        info("process.start","version="+BuildConfig.VERSION_NAME+" build="+BuildConfig.VERSION_CODE+" api="+Build.VERSION.SDK_INT+" homeMode="+(BuildConfig.DECLARES_HOME?"standard":"relay"));
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                String crash=Long.toHexString(System.currentTimeMillis())+"-"+thread.getId();
                List<String> parts=crashTrace(error);
                instance.enqueue("ERROR","process.uncaught","crash="+crash+" parts="+parts.size()+" type="+bounded(error.getClass().getName(),96));
                for(int i=0;i<parts.size();i++){
                    instance.enqueue("ERROR","process.uncaught.stack","crash="+crash+" part="+(i+1)+"/"+parts.size()+" "+parts.get(i));
                }
                instance.worker.submit(()->instance.flushBuffered(DiagnosticEventBuffer.CAPACITY)).get(500,TimeUnit.MILLISECONDS);
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
    private void enqueue(String level,String event,String detail){
        if(buffer.offer(level,event,detail,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime()))scheduleDrain();
    }
    private synchronized void scheduleDrain(){
        if(drainScheduled)return;drainScheduled=true;
        try{worker.execute(()->{
            try{flushBuffered(16);}
            finally{synchronized(DiagnosticLog.this){drainScheduled=false;if(buffer.hasEntries())scheduleDrain();}}
        });}catch(RejectedExecutionException full){drainScheduled=false;buffer.discard();}
    }
    private void flushBuffered(int limit){
        List<DiagnosticEventBuffer.Entry> entries=buffer.take(limit,System.currentTimeMillis());
        if(journal==null || entries.isEmpty())return;
        List<byte[]> encoded=new ArrayList<>(entries.size());
        for(DiagnosticEventBuffer.Entry entry:entries)encoded.add(DiagnosticJournal.entry(entry.line()));
        try{journal.appendBatch(encoded);}catch(IOException failure){failedWrites++;}
    }
    static File export(Context supplied,String snapshot) throws Exception {
        DiagnosticLog log=instance;if(log==null)throw new IOException("Diagnostic log unavailable");
        Context context=supplied.getApplicationContext();
        Future<File> result=log.worker.submit(()->{
            if(log.journal==null)throw new IOException("Diagnostic log incomplete",log.storageError);
            log.flushBuffered(DiagnosticEventBuffer.CAPACITY);File file=new File(context.getCacheDir(),"diagnostic-export.txt");
            String header="Quiet TV 诊断日志\n生成时间："+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date())
                +"\n"+snapshot+"\n桌面配置："+new DesktopRoutes(context,false).snapshot()
                +"\n本进程写入失败批次："+log.failedWrites+"  队列丢弃："+log.buffer.droppedTotal()+"  重复合并："+log.buffer.suppressedTotal()+"\n范围：Quiet TV 自身关键事件及错误，包含首页恢复、窗口焦点、每次恢复的首次视图树绘制回调，以及系统设置路由、ADB 连接尝试和原主页中转结果。打开系统设置后自动触发的 settings.capture 另记录本机 ADB 可见的前台组件、action、Fragment候选和组件访问状态；input.navigation 仅表示应用或已有无障碍回调实际收到的导航按键，不代表完整实体按键历史。不含原始系统转储、页面文字、Intent数据或extra值，也不包含完整系统日志。attempt 关联一次操作，RUNNING 表示导出时尚未结束；设置 DISPATCHED_UNVERIFIED 仅为请求已发出，中转 VISIBILITY_CALLBACK 仅为收到首页回报，都不证明电视已上屏。场景 source=user 为用户确认，不是自动验收。绘制回调和窗口焦点不证明画面已经上屏；跳转记录不证明系统页面正常显示或物理信号已切换。\n动态壁纸诊断：upload 为本进程内单次视频上传编号，attempt 为上传连接会话；request 与 wallpaper 关联播放尝试及应用内部壁纸编号。stage/check 为失败阶段，reason 为原因码。no_video_track 表示系统未识别到视频轨道，不代表文件一定损坏；first_frame_timeout 表示限时内没有首帧回调；decoder/codec 与 diagnostic 为解码器及底层错误信息。封面生成失败标记 optional=true，不等同于视频导入失败。首帧回调不证明持续流畅或电视已上屏。只记录媒体技术参数和异常类型，不含文件名、路径、上传地址或原始异常消息。\n时间字段为 Unix 毫秒；首页事件的 uptimeMs、elapsedMs 使用不含深度休眠的单调时钟；连接和中转耗时使用包含深度休眠的 elapsedRealtime。容量轮转会覆盖较早日志，进程停止期间不记录，异常终止可能丢失末尾记录；没有结果事件不能直接判为超时或成功。\n请检查内容后私下分享。\n\n[历史事件]\n";
            byte[] headerBytes=header.getBytes(Charsets.UTF_8);
            if(headerBytes.length>32768)throw new IOException("Diagnostic snapshot exceeds limit");
            try(OutputStream output=new FileOutputStream(file)){output.write(headerBytes);log.journal.copyTo(output);}
            return file;
        });
        try{return result.get(5,TimeUnit.SECONDS);}catch(TimeoutException e){result.cancel(false);throw e;}
    }
    @androidx.annotation.RequiresApi(24) private static final class Api24 {static Context storage(Context context){return context.createDeviceProtectedStorageContext();}}
}
