package home.quiet.tv;

import android.content.Context;
import android.os.*;
import java.util.concurrent.*;
import org.json.*;

/** Bounded extra evidence. The existing user incident starts one 30-second probe window. */
final class DiagnosticProbe {
    private static final DiagnosticBudget budget=new DiagnosticBudget();
    private static Handler main;
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"quiet-diagnostic-probe");t.setDaemon(true);return t;});
    @android.annotation.SuppressLint("StaticFieldLeak") // init stores getApplicationContext(), never an Activity.
    private static Context context;
    private static volatile String incident="none";
    private static volatile long until;
    private static volatile int beats;
    private static volatile long maxDelay,totalDelay;
    static void init(Context value){context=value.getApplicationContext();if(main==null)main=new Handler(Looper.getMainLooper());}
    static boolean active(){return context!=null&&SystemClock.elapsedRealtime()<until;}
    static String window(){return active()?incident:"none";}
    static boolean allow(String event,long interval){if(context==null)return false;try{return budget.take(event,SystemClock.elapsedRealtime(),active()?interval:Math.max(2000,interval),active()?120:15);}catch(RuntimeException unavailable){return false;}}
    static void observed(String event,String detail){try{DiagnosticLog.info(event,"incident="+window()+" "+detail);}catch(RuntimeException unavailable){}}
    static void start(String id){
        if(main==null)return;
        if(active()&&incident.equals(id))return;
        incident=id;long now=SystemClock.elapsedRealtime();until=now+DiagnosticIncident.AFTER_MS;budget.beginWindow(now);beats=0;maxDelay=totalDelay=0;
        main.post(()->{if(!incident.equals(id))return;InputDiagnostics.windowStarted();sample("window_begin");heartbeat(id,SystemClock.uptimeMillis()+1000);});
    }
    private static void heartbeat(String id,long expected){main.postDelayed(()->{
        if(!incident.equals(id))return;
        long delay=Math.max(0,SystemClock.uptimeMillis()-expected);beats++;totalDelay+=delay;maxDelay=Math.max(maxDelay,delay);
        if(active()&&beats<30)heartbeat(id,SystemClock.uptimeMillis()+1000);
        else{SettingsPageCapture.stopCurrent("diagnostic_window_end");InputDiagnostics.windowEnded(id);sample("window_end");observed("diagnostic.probe.result","markedIncident="+id+" samples="+beats+" maxMainDelayMs="+maxDelay+" totalMainDelayMs="+totalDelay+" evidence=handler_scheduling_not_frame_or_anr");}
    },1000);}
    static void failure(String event){if(allow("resource.failure",30000))sample("failure_"+event);}
    static void trim(int level){observed("diagnostic.memory","trimLevel="+level);if(allow("resource.trim",30000))sample("trim");}
    @android.annotation.SuppressLint("UsableSpace") // Observe the same filesystem measure as admission, not allocatable cache.
    private static void sample(String reason){
        if(context==null)return;final long queued=SystemClock.elapsedRealtime();final String marked=incident;
        try{worker.execute(()->{
            long started=SystemClock.elapsedRealtime();Runtime runtime=Runtime.getRuntime();
            String detail="reason="+reason+" markedIncident="+marked+" role=main thread=diagnostic_worker queueWaitMs="+(started-queued)
                +" javaUsedBytes="+(runtime.totalMemory()-runtime.freeMemory())+" javaMaxBytes="+runtime.maxMemory();
            try{detail+=" selfPssKiB="+Debug.getPss()+" nativeAllocatedBytes="+Debug.getNativeHeapAllocatedSize()+" internalAvailableBytes="+context.getFilesDir().getUsableSpace();}
            catch(RuntimeException unavailable){detail+=" resourceRead=unknown type="+unavailable.getClass().getSimpleName();}
            observed("diagnostic.resource",detail+" captureMs="+(SystemClock.elapsedRealtime()-started));
        });}catch(RejectedExecutionException full){observed("diagnostic.resource","status=queue_full reason="+reason);}
    }
    static JSONObject snapshot()throws JSONException{return new JSONObject().put("scope","current_process").put("incident",incident).put("active",active()).put("deadlineElapsedMs",until)
        .put("mainDelaySamples",beats).put("maxMainDelayMs",maxDelay).put("totalMainDelayMs",totalDelay).put("extraProbeAdmission",budget.json());}
    private DiagnosticProbe(){}
}
