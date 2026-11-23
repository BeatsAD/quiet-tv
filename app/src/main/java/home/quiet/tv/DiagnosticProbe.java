package home.quiet.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** Bounded extra evidence. The existing user incident starts one 30-second probe window. */
final class DiagnosticProbe {
    private static final DiagnosticBudget budget=new DiagnosticBudget();
    private static final ScheduledThreadPoolExecutor watchdog=new ScheduledThreadPoolExecutor(1,r->{
        Thread t=new Thread(r,"quiet-stall-watch");t.setDaemon(true);return t;
    });
    static{watchdog.setKeepAliveTime(15,TimeUnit.SECONDS);watchdog.allowCoreThreadTimeOut(true);}
    private static volatile long lastStackAt=-15000;
    private static Handler main;
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"quiet-diagnostic-probe");t.setDaemon(true);return t;});
    @android.annotation.SuppressLint("StaticFieldLeak") // init stores getApplicationContext(), never an Activity.
    private static Context context;
    private static volatile String incident="none";
    private static volatile long until,lastScreenOnUptime=-1;
    private static volatile int beats;
    private static volatile long maxDelay,totalDelay;
    static void init(Context value){context=value.getApplicationContext();if(main==null)main=new Handler(Looper.getMainLooper());}
    static boolean active(){return DiagnosticLog.recording()&&context!=null&&SystemClock.elapsedRealtime()<until;}
    static long remaining(){return until-SystemClock.elapsedRealtime();}
    static String window(){return active()?incident:"none";}
    static boolean allow(String event,long interval){if(!DiagnosticLog.recording()||context==null)return false;try{return budget.take(event,SystemClock.elapsedRealtime(),active()?interval:Math.max(2000,interval),active()?120:15);}catch(RuntimeException unavailable){return false;}}
    static void observed(String event,String detail){try{DiagnosticLog.info(event,"incident="+window()+" "+detail);}catch(RuntimeException unavailable){}}
    static synchronized void start(String id){
        if(!DiagnosticLog.recording()||main==null)return;
        if(active()&&incident.equals(id))return;
        incident=id;long now=SystemClock.elapsedRealtime();until=now+DiagnosticIncident.AFTER_MS;budget.beginWindow(now);beats=0;maxDelay=totalDelay=0;
        main.post(()->{if(!incident.equals(id))return;InputDiagnostics.windowStarted();sample("window_begin");heartbeat(id,SystemClock.uptimeMillis()+1000);});
    }
    static synchronized void stop(){until=0;incident="none";}
    private static void heartbeat(String id,long expected){main.postDelayed(()->{
        if(!incident.equals(id))return;
        long delay=Math.max(0,SystemClock.uptimeMillis()-expected);beats++;totalDelay+=delay;maxDelay=Math.max(maxDelay,delay);
        if(active()&&beats<30)heartbeat(id,SystemClock.uptimeMillis()+1000);
        else{SettingsPageCapture.stopCurrent("diagnostic_window_end");InputDiagnostics.windowEnded(id);sample("window_end");observed("diagnostic.probe.result","markedIncident="+id+" samples="+beats+" maxMainDelayMs="+maxDelay+" totalMainDelayMs="+totalDelay+" evidence=handler_scheduling_not_frame_or_anr");}
    },1000);}
    // No recurring polling. One sample while a monitored main-thread callback is still in progress.
    static final class StallWatch implements Runnable{
        private final String stage;
        private final AutoDiagnosticOperation operation;
        private final long began=SystemClock.uptimeMillis();
        private volatile boolean finished;
        private ScheduledFuture<?> alarm;
        private StallWatch(String stage,AutoDiagnosticOperation operation){this.stage=stage;this.operation=operation;}
        void end(){
            finished=true;alarm.cancel(false);
            if(alarm instanceof Runnable)watchdog.remove((Runnable)alarm);
        }
        @Override public void run(){
            if(finished||!allow("main.stall_stack",15000))return;
            try{
                StackTraceElement[] stack=Looper.getMainLooper().getThread().getStackTrace();
                if(finished||stack.length==0)return;
                StringBuilder path=new StringBuilder();
                for(int n=0;n<Math.min(7,stack.length);n++){
                    StackTraceElement frame=stack[n];
                    if(n!=0)path.append("<-");
                    path.append(frame.getClassName()).append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
                }
                long at=SystemClock.uptimeMillis();lastStackAt=at;
                observed("diagnostic.main_stall.result","stage="+stage+" beganUptimeMs="+began+" sampleUptimeMs="+at
                    +" elapsedMs="+(at-began)+" stack="+path+" evidence=java_stack_sample_not_anr");
                if(operation==null||!operation.mainStall(stack))slowResource("main_stall_sample");
            }catch(RuntimeException unavailable){observed("diagnostic.main_stall.result","stage="+stage+" stack=unavailable");}
        }
    }
    static StallWatch watchMain(String stage){return watchMain(stage,null);}
    static StallWatch watchMain(String stage,AutoDiagnosticOperation operation){
        if(context==null||!DiagnosticLog.recording()||Looper.myLooper()!=Looper.getMainLooper()||SystemClock.uptimeMillis()-lastStackAt<15000)return null;
        StallWatch watch=new StallWatch(stage,operation);
        watch.alarm=watchdog.schedule(watch,500,TimeUnit.MILLISECONDS);return watch;
    }
    static void screenOn(){lastScreenOnUptime=SystemClock.uptimeMillis();}
    static long wakeAgeMs(){
        long wake=lastScreenOnUptime;
        return wake<0?-1:Math.max(0,SystemClock.uptimeMillis()-wake);
    }
    // Shared rate limit covers all automatic stall sources; expensive reads stay off the UI thread.
    static void slowResource(String stage){slowResource(stage,null);}
    static void slowResource(String stage,AutoDiagnosticOperation operation){if(allow("resource.stall",30000))sample(stage,operation);}
    static void failure(String event){if(allow("resource.failure",30000))sample("failure_"+event);}
    static void trim(int level){observed("diagnostic.memory","trimLevel="+level);if(allow("resource.trim",30000))sample("trim");}
    private static void sample(String reason){sample(reason,null);}
    @android.annotation.SuppressLint("UsableSpace") // Observe the same filesystem measure as admission, not allocatable cache.
    private static void sample(String reason,AutoDiagnosticOperation operation){
        if(context==null)return;final long queued=SystemClock.elapsedRealtime();final String marked=incident;final int epoch=DiagnosticLog.recordingEpoch();
        try{worker.execute(()->{
            if(!DiagnosticLog.recording(epoch))return;
            long started=SystemClock.elapsedRealtime();Runtime runtime=Runtime.getRuntime();
            final long javaUsed=runtime.totalMemory()-runtime.freeMemory(),javaMax=runtime.maxMemory();
            JSONObject auto=operation==null?null:new JSONObject();
            put(auto,"sampleStartedElapsedMs",started);put(auto,"javaUsedBytes",javaUsed);put(auto,"javaMaxBytes",javaMax);
            String detail="reason="+reason+" markedIncident="+marked+" role=main thread=diagnostic_worker queueWaitMs="+(started-queued)
                +" javaUsedBytes=" +javaUsed+" javaMaxBytes="+javaMax;
            try{
                long pss=Debug.getPss(),nativeBytes=Debug.getNativeHeapAllocatedSize(),disk=context.getFilesDir().getUsableSpace();
                put(auto,"selfPssKiB",pss);put(auto,"nativeAllocatedBytes",nativeBytes);
                detail+=" selfPssKiB="+pss+" nativeAllocatedBytes="+nativeBytes+" internalAvailableBytes="+disk;
            }
            catch(RuntimeException unavailable){detail+=" resourceRead=unknown type="+unavailable.getClass().getSimpleName();}
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager!=null)try{
                ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);
                put(auto,"systemAvailableBytes",memory.availMem);put(auto,"systemLowThresholdBytes",memory.threshold);
                detail+=" systemAvailableBytes="+memory.availMem+" systemLowThresholdBytes="+memory.threshold+" systemLowMemory="+memory.lowMemory;
                if(Build.VERSION.SDK_INT>=16)detail+=" systemTotalBytes="+memory.totalMem;
            }catch(RuntimeException unavailable){detail+=" systemMemory=unavailable";}
            if(!DiagnosticLog.recording(epoch))return;
            if(operation!=null)operation.resource(auto);
            observed("diagnostic.resource",detail+" sampleStartedElapsedMs="+started+" captureMs="+(SystemClock.elapsedRealtime()-started)+" evidence=sample_after_trigger_not_at_stall");
        });}catch(RejectedExecutionException full){observed("diagnostic.resource","status=queue_full reason="+reason);}
    }
    private static void put(JSONObject data,String field,long value){if(data!=null)try{data.put(field,value);}catch(JSONException ignored){}}
    static JSONObject snapshot()throws JSONException{return new JSONObject().put("scope","current_process").put("incident",incident).put("active",active()).put("deadlineElapsedMs",until)
        .put("mainDelaySamples",beats).put("maxMainDelayMs",maxDelay).put("totalMainDelayMs",totalDelay).put("extraProbeAdmission",budget.json());}
    private DiagnosticProbe(){}
}
