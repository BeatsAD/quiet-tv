package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.KeyEvent;
import java.io.*;
import home.quiet.tv.Charsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Explicit application events only, with at most 32 queued entries and one idle-expiring worker. */
final class DiagnosticLog {
    private static volatile DiagnosticLog instance;
    private static final String RECORDING="recording";
    private final SharedPreferences preferences;
    private volatile boolean recording;
    private volatile int recordingEpoch;
    static boolean navigationKey(int code){return code==KeyEvent.KEYCODE_HOME||code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_MENU||code==KeyEvent.KEYCODE_TV_INPUT
        ||code>=KeyEvent.KEYCODE_DPAD_UP&&code<=KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER;}
    static void navigationKey(String source,KeyEvent event){
        if(!recording()||event==null||!navigationKey(event.getKeyCode())||event.getRepeatCount()!=0
            ||event.getAction()!=KeyEvent.ACTION_DOWN&&event.getAction()!=KeyEvent.ACTION_UP)return;
        info("input.navigation","source="+source+" keyCode="+event.getKeyCode()+" action="+event.getAction()+" eventTime="+event.getEventTime()+InputDiagnostics.key(event));
    }
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"quiet-diagnostic-log");t.setDaemon(true);return t;});
    private final DiagnosticEventBuffer buffer=new DiagnosticEventBuffer();
    private boolean drainScheduled;
    private DiagnosticJournal journal;
    private IOException storageError;
    private int failedWrites;
    private final DiagnosticSession session;
    private final DiagnosticCrashRecord crashes;
    private volatile UpdateCheck updateCheck;
    private DiagnosticIncident incident;
    private String incidentError="";
    private DiagnosticLog(Context supplied){
        worker.allowCoreThreadTimeOut(true);
        Context context=Build.VERSION.SDK_INT>=24?Api24.storage(supplied):supplied;
        preferences=context.getSharedPreferences("diagnostic-settings",Context.MODE_PRIVATE);
        recording=preferences.getBoolean(RECORDING,true);
        session=new DiagnosticSession(supplied);
        crashes=new DiagnosticCrashRecord(new File(context.getFilesDir(),"diagnostic-last-crash.json"));
        worker.execute(()->{
            try{journal=new DiagnosticJournal(new File(context.getFilesDir(),"diagnostic-log"));}catch(IOException|RuntimeException e){storageError=new IOException("Diagnostic storage unavailable",e);}
            try{incident=new DiagnosticIncident(new File(context.getFilesDir(),"diagnostic-incident"));}catch(IOException|RuntimeException e){incidentError=e.getClass().getSimpleName();}
        });
    }
    static synchronized void init(Context context){
        if(instance!=null)return;
        instance=new DiagnosticLog(context.getApplicationContext());
        DiagnosticProbe.init(context);
        Context incidentStorage=Build.VERSION.SDK_INT>=24?Api24.storage(context):context;
        AutoIncidentStore.init(context.getApplicationContext(),incidentStorage,instance.session);
        info("process.start","schema="+DiagnosticSession.SCHEMA+" version="+BuildConfig.VERSION_NAME+" build="+BuildConfig.VERSION_CODE+" api="+Build.VERSION.SDK_INT+" homeMode="+(BuildConfig.DECLARES_HOME?"standard":"relay"));
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                if(!recording())return;
                long wall=System.currentTimeMillis(),elapsed=android.os.SystemClock.elapsedRealtime();
                DiagnosticLog log=instance;
                UpdateCheck check=log.updateCheck;
                JSONObject record=DiagnosticCrashRecord.details(thread,error,check)
                    .put("timestampMs",wall).put("elapsedMs",elapsed)
                    .put("session",log.session.id).put("pid",android.os.Process.myPid()).put("build",BuildConfig.VERSION_CODE)
                    .put("sourceSha256",log.session.sourceSha256());
                log.crashes.write(record);
            }
            catch(IOException|JSONException|RuntimeException ignored){}
            finally{if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}
        });
    }
    static boolean initialized(){return instance!=null;}
    static boolean recording(){DiagnosticLog log=instance;return log!=null&&log.recording;}
    static int recordingEpoch(){DiagnosticLog log=instance;return log==null?0:log.recordingEpoch;}
    static boolean recording(int epoch){return recording()&&instance.recordingEpoch==epoch;}
    static void observeRecording(SharedPreferences.OnSharedPreferenceChangeListener listener){instance.preferences.registerOnSharedPreferenceChangeListener(listener);}
    static void forgetRecording(SharedPreferences.OnSharedPreferenceChangeListener listener){instance.preferences.unregisterOnSharedPreferenceChangeListener(listener);}
    static synchronized void setRecording(Context context,boolean enabled){
        DiagnosticLog log=instance;
        synchronized(log){
            if(log.recording==enabled)return;
            log.recordingEpoch++;
            log.recording=enabled;
            if(!enabled)log.worker.execute(()->log.flushBuffered(DiagnosticEventBuffer.CAPACITY));
        }
        if(enabled)BluetoothDiagnostics.init(context);
        else{
            DiagnosticProbe.stop();
            SettingsPageCapture.stopCurrent("recording_disabled");
            InputSourceObservation.stop("recording_disabled");
            BluetoothSystemCapture.stop();
        }
        log.preferences.edit().putBoolean(RECORDING,enabled).apply();
    }
    static void info(String event,String detail){DiagnosticLog log=instance;if(log!=null)log.enqueue("INFO",event,detail);}
    static void trim(int level){if(recording())DiagnosticProbe.trim(level);}
    static String anonymous(String scope,String value){if(value==null||value.isEmpty()||value.equals("unknown")||value.equals("null"))return "unknown";DiagnosticLog log=instance;return log==null?"unknown":log.session.anonymous(scope,value);}
    static void error(String event,Throwable error){
        DiagnosticLog log=instance;if(log==null||!log.recording)return;
        if(event.startsWith("weather.")||event.startsWith("wallpaper.daily."))log.enqueue("ERROR",event+".reason",NetworkDiagnostics.failureDetails(error)
            +(error instanceof WeatherClient.ApiException?" status="+((WeatherClient.ApiException)error).status:""));
        log.enqueue("ERROR",event,trace(error));
        DiagnosticProbe.failure(event);
    }
    static void networkTls(String event,javax.net.ssl.SSLSocketFactory factory){
        DiagnosticLog log=instance;if(log==null||!log.recording)return;
        try{log.enqueue("INFO",event,NetworkDiagnostics.tlsDetails(factory));}
        catch(IOException|RuntimeException e){log.enqueue("INFO",event,"probe_failed "+NetworkDiagnostics.failureDetails(e));}
    }
    static void error(String event,String detail,Throwable error){DiagnosticLog log=instance;if(log!=null&&log.recording){log.enqueue("ERROR",event,detail+" "+trace(error));DiagnosticProbe.failure(event);}}
    // Deliberately omit data, extras, labels and URI paths: they may contain user information.
    static String intent(android.content.Intent intent){
        return "action="+intent.getAction()+" component="+(intent.getComponent()==null?"implicit":intent.getComponent().flattenToShortString())
            +" package="+intent.getPackage()+" scheme="+intent.getScheme()+" flags=0x"+Integer.toHexString(intent.getFlags())
            +" categories="+categories(intent);
    }
    private static String categories(android.content.Intent intent){
        Set<String> categories=intent.getCategories();if(categories==null)return "none";
        List<String> values=new ArrayList<>();for(String category:categories)if(category.startsWith("android.intent.category.")&&category.matches("[A-Za-z0-9_.]{1,100}")&&values.size()<8)values.add(category);
        Collections.sort(values);return values+" total="+categories.size();
    }
    // Exception messages can include input, URLs and credentials: keep only types and bounded frames.
    static String trace(Throwable error){
        StringBuilder text=new StringBuilder();
        for(int cause=0;error!=null&&cause<3;cause++,error=error.getCause()){
            text.append(error.getClass().getName());StackTraceElement[] frames=error.getStackTrace();
            for(int i=0;i<Math.min(8,frames.length);i++)text.append(" <- ").append(frames[i]);
            text.append(' ');if(text.length()>900)break;
        }
        return text.toString();
    }
    static final class UpdateCheck {
        final int generation;
        final boolean manual;
        volatile String mainPhase="dispatch",workerPhase="key";
        volatile NetworkEvidence sourceAttempt;

        UpdateCheck(int generation,boolean manual){this.generation=generation;this.manual=manual;}

        JSONObject snapshot()throws JSONException{
            JSONObject result=new JSONObject().put("generation",generation).put("manual",manual)
                .put("mainPhase",mainPhase).put("workerPhase",workerPhase);
            NetworkEvidence attempt=sourceAttempt;
            if(attempt!=null)result.put("request",attempt.parent).put("attempt",attempt.attempt)
                .put("lastSource",attempt.source).put("sourcePhase",attempt.stage);
            return result;
        }
    }
    static UpdateCheck beginUpdateCheck(int generation,boolean manual){
        UpdateCheck check=new UpdateCheck(generation,manual);
        DiagnosticLog log=instance;if(log!=null)log.updateCheck=check;
        return check;
    }
    static UpdateCheck activeUpdateCheck(int generation){
        DiagnosticLog log=instance;UpdateCheck check=log==null?null:log.updateCheck;
        return check!=null&&check.generation==generation?check:null;
    }
    static void endUpdateCheck(int generation){
        if(activeUpdateCheck(generation)!=null)instance.updateCheck=null;
    }
    private synchronized void enqueue(String level,String event,String detail){
        if(!recording)return;
        long wall=System.currentTimeMillis(),elapsed=android.os.SystemClock.elapsedRealtime();
        // Mirror only vetted diagnostic channels so a computer stream survives APK replacement.
        if(event.equals("process.start")||event.startsWith("bluetooth.")||event.startsWith("system.operation.")||event.startsWith("system.configuration.")
                ||event.startsWith("input.route.")||event.startsWith("install.evidence.")||event.startsWith("app.launch.")||event.startsWith("app.autostart.")||event.startsWith("external_activity.")
                ||event.equals("input.window")||event.equals("home.window_focus")||event.startsWith("home.app_menu.")||event.equals("home_guard.key_filter")){
            android.util.Log.i("QuietTVDiagnostics",DiagnosticJournal.clean("wallMs="+wall+" event="+event+" "+session.envelope(detail,elapsed)));
        }
        if(buffer.offer(level,event,detail,wall,elapsed))scheduleDrain();
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
        if(entries.isEmpty())return;if(journal==null){failedWrites++;buffer.written(entries,false);return;}
        List<byte[]> encoded=new ArrayList<>(entries.size());
        for(DiagnosticEventBuffer.Entry entry:entries)encoded.add(DiagnosticJournal.entry(entry.at+" "+entry.level+" "+entry.event+" "+session.envelope(entry.detail,entry.elapsed<0?android.os.SystemClock.elapsedRealtime():entry.elapsed)));
        try{journal.appendBatch(encoded);buffer.written(entries,true);}catch(IOException failure){failedWrites++;buffer.written(entries,false);}
    }
    private JSONObject coverage()throws JSONException{
        return new JSONObject().put("scope","current_process").put("channels",buffer.coverage()).put("writeFailedBatches",failedWrites)
            .put("observerLimits","Only registered and permitted observers during process lifetime; absent channels are not evidence of absence")
            .put("incidentError",incidentError.isEmpty()?JSONObject.NULL:incidentError);
    }
    static File structured(Context supplied,String snapshot)throws Exception{
        DiagnosticLog log=instance;if(log==null)throw new IOException("Diagnostic log unavailable");
        return log.worker.submit(()->{log.flushBuffered(DiagnosticEventBuffer.CAPACITY);return log.writeStructured(supplied.getApplicationContext(),snapshot,log.crashes.read());}).get(10,TimeUnit.SECONDS);
    }
    private File writeStructured(Context context,String snapshot,JSONObject crash)throws Exception{
            if(this.journal==null)throw new IOException("Diagnostic log unavailable");
            DiagnosticJournal.Tail tail=this.journal.tail(1024*1024,1000);JSONArray events=new JSONArray();int unparsed=0;
            for(String line:tail.lines){JSONObject item=DiagnosticRecord.parse(line);if(item==null)unparsed++;else events.put(item);}
            JSONObject data=new JSONObject().put("schemaVersion",DiagnosticSession.SCHEMA).put("exportedAtMs",System.currentTimeMillis()).put("currentSession",this.session.json())
                .put("recordingEnabled",recording).put("snapshotAtExport",boundedSnapshot(snapshot)).put("coverage",this.coverage()).put("journalSegmentSequence",tail.sequence).put("events",events)
                .put("operationEvidence",DiagnosticFindings.summarize(events)).put("eventsOmitted",tail.omitted).put("unparsedLines",unparsed).put("historyIdentityPolicy","Legacy entries without an envelope keep unknown identity; never assign the current version")
                .put("lastJavaCrash",crash==null?JSONObject.NULL:crash)
                .put("incident",this.incident==null?JSONObject.NULL:this.incident.snapshot()).put("extraProbes",DiagnosticProbe.snapshot()).put("automaticIncidents",AutoIncidentStore.recentEvidence());
            File file=new File(context.getCacheDir(),"diagnostic-export.json");try(OutputStream output=new FileOutputStream(file)){output.write(data.toString().getBytes(Charsets.UTF_8));}return file;
    }
    static File bundle(Context supplied,String snapshot,int epoch)throws Exception{
        DiagnosticLog log=instance;if(log==null)throw new IOException("Diagnostic log unavailable");
        Context context=supplied.getApplicationContext();
        return log.worker.submit(()->{
            log.flushBuffered(DiagnosticEventBuffer.CAPACITY);
            // Both files observe the same serialized journal; no second drain between them.
            String evidence=recording(epoch)?snapshot:DiagnosticExports.HISTORY_ONLY;
            JSONObject crash=log.crashes.read();
            File text=log.writeText(context,evidence,crash);BluetoothSystemCapture.append(context,text);
            File json=log.writeStructured(context,evidence,crash);
            return DiagnosticBundle.create(text,json,new File(context.getCacheDir(),"diagnostic-export-"+UUID.randomUUID()+".zip"));
        }).get(15,TimeUnit.SECONDS);
    }
    static File export(Context supplied,String snapshot)throws Exception{
        DiagnosticLog log=instance;if(log==null)throw new IOException("Diagnostic log unavailable");
        return log.worker.submit(()->{log.flushBuffered(DiagnosticEventBuffer.CAPACITY);return log.writeText(supplied.getApplicationContext(),snapshot,log.crashes.read());}).get(10,TimeUnit.SECONDS);
    }
    static String boundedSnapshot(String value){
        if(value==null)return "未采集";
        return value.length()>16000?value.substring(0,16000)+"\n[snapshotTruncated=true]":value;
    }
    private File writeText(Context context,String snapshot,JSONObject crash)throws Exception{
            if(this.journal==null)throw new IOException("Diagnostic log incomplete",this.storageError);
            File file=new File(context.getCacheDir(),"diagnostic-export.txt");
            snapshot=boundedSnapshot(snapshot);
            String header="Quiet TV 诊断日志\n生成时间："+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date())
                +"\n"+snapshot
                +"\n本进程写入失败批次："+this.failedWrites+"  队列丢弃："+this.buffer.droppedTotal()+"  重复合并："+this.buffer.suppressedTotal()+"\n范围：Quiet TV 自身关键事件及错误，包含首页恢复、窗口焦点、每次恢复的首次视图树绘制回调，以及系统设置路由、ADB 连接尝试和原主页中转结果。旧版 settings.capture 历史可能包含本机 ADB 曾读取的前台组件、action、Fragment 候选和组件访问状态；input.navigation 仅表示应用或已有无障碍回调实际收到的导航按键，不代表完整实体按键历史。不含原始系统转储、用户正文、凭据或任意Intent数据，也不包含完整系统日志。attempt 关联一次操作，RUNNING 表示导出时尚未结束；设置 DISPATCHED_UNVERIFIED 仅为请求已发出，中转 VISIBILITY_CALLBACK 仅为收到首页回报，都不证明电视已上屏。场景 source=user 为用户确认，不是自动验收。绘制回调和窗口焦点不证明画面已经上屏；跳转记录不证明系统页面正常显示或物理信号已切换。\n动态壁纸诊断：upload 为本进程内单次视频上传编号，attempt 为上传连接会话；request 与 wallpaper 关联播放尝试及应用内部壁纸编号。stage/check 为失败阶段，reason 为原因码。no_video_track 表示系统未识别到视频轨道，不代表文件一定损坏；first_frame_timeout 表示限时内没有首帧回调；decoder/codec 与 diagnostic 为解码器及底层错误信息。封面生成失败标记 optional=true，不等同于视频导入失败。state 记录屏幕、窗口和输出状态，layers 记录封面、模糊与亮度遮罩状态；wake_rebind 表示亮屏后重新创建输出，counters 仅为解码器计数。首帧、图层状态及解码计数均不证明持续流畅或电视已上屏。只记录媒体技术参数和异常类型，不含文件名、路径、上传地址或原始异常消息。\n时间字段为 Unix 毫秒；首页事件的 uptimeMs、elapsedMs 使用不含深度休眠的单调时钟；连接和中转耗时使用包含深度休眠的 elapsedRealtime。容量轮转会覆盖较早日志，进程停止期间不记录，异常终止可能丢失末尾记录；没有结果事件不能直接判为超时或成功。\n请检查内容后私下分享。\n\n[历史事件]\n";
            header=header.replace("\n\n[历史事件]\n","\n")+"\n[导出时当前会话身份]\n"+this.session.json()+"\n[本进程按通道采集质量]\n"+this.coverage()+"\n[短时增强诊断覆盖]\n"+DiagnosticProbe.snapshot()+"\n2.0操作记录仅包含允许的类别、阶段、匿名关联和结果；completed或APPLIED_CALLBACK不证明物理画面正常。故障窗口标记为用户报告时刻，非自动故障判定。\n历史事件以diagSession／diagBoot／diagBuild／diagSource关联；缺少这些字段的旧记录身份未知，不赋予当前版本。上述快照与质量统计属于导出时／当前进程，不替代历史故障现场。\n\n[历史事件]\n";
            byte[] headerBytes=header.getBytes(Charsets.UTF_8);
            if(headerBytes.length>65536)throw new IOException("Diagnostic snapshot exceeds limit");
            try(OutputStream output=new FileOutputStream(file)){
                output.write(headerBytes);this.journal.copyTo(output);if(this.incident!=null)this.incident.appendTo(output);
                if(crash!=null)output.write(("\n[最近一次 Java 崩溃]\n"+crash.toString(2)+"\n").getBytes(Charsets.UTF_8));
            }
            return file;
    }
    @androidx.annotation.RequiresApi(24) private static final class Api24 {static Context storage(Context context){return context.createDeviceProtectedStorageContext();}}
}
