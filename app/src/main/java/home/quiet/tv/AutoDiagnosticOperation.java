package home.quiet.tv;

import android.os.SystemClock;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Only program-defined labels and numerical observations; no event text or payload. */
final class AutoDiagnosticOperation {
    private static final AtomicLong ids=new AtomicLong();
    final String id=Long.toString(ids.incrementAndGet(),36);
    final String kind;
    final long started=SystemClock.elapsedRealtime();
    private final long threshold;
    private final boolean timed;
    private long inputAge;
    private String lastPhase="start", reason;
    private boolean closed;
    private ScheduledFuture<?> alarm;

    private AutoDiagnosticOperation(String kind,long threshold,boolean timed){
        this.kind=kind;this.threshold=threshold;this.timed=timed;
    }

    static AutoDiagnosticOperation begin(String kind,long thresholdMs){
        AutoDiagnosticOperation op=new AutoDiagnosticOperation(kind,thresholdMs,true);
        op.stage("start");
        if(thresholdMs>0&&DiagnosticLog.recording())op.alarm=AutoIncidentStore.schedule(op::timeout,thresholdMs);
        return op;
    }

    static void point(String kind,String phase){
        AutoIncidentStore.event("",kind,phase,null,0);
    }

    static AutoDiagnosticOperation beginInput(String surface,long ageMs){
        AutoDiagnosticOperation op=new AutoDiagnosticOperation("input_"+surface,500,false);
        op.inputAge=ageMs;
        return op;
    }

    static void input(String surface,long ageMs,long handlerMs){
        beginInput(surface,ageMs).endInput(handlerMs);
    }

    synchronized void endInput(long handlerMs){
        if(closed)return;
        if(inputAge>=100||handlerMs>=100||reason!=null){
            metric("delivery_age_ms",inputAge);
            metric("handler_ms",handlerMs);
            if(reason==null&&(inputAge>=500||handlerMs>=500))
                capture(inputAge>=500&&handlerMs>=500?"input_delivery_and_handler_slow":handlerMs>=500?"input_handler_slow":"input_delivery_late");
            finish();
        }else closed=true;
    }

    synchronized void stage(String phase){
        if(closed)return;
        lastPhase=phase;
        AutoIncidentStore.event(id,kind,phase,null,0);
    }

    synchronized void metric(String key,long value){
        if(!closed)AutoIncidentStore.event(id,kind,lastPhase,key,value);
    }

    synchronized void frameSummary(int frames,int slow,long maxMs,int dropped){
        if(frames<=0)return;
        AutoIncidentStore.event(id,kind,"frame_sample","frames",frames);
        AutoIncidentStore.event(id,kind,"frame_sample","slow_frames",slow);
        AutoIncidentStore.event(id,kind,"frame_sample","max_frame_ms",maxMs);
        AutoIncidentStore.event(id,kind,"frame_sample","dropped_callbacks",dropped);
    }

    synchronized void finish(){
        if(closed)return;
        long duration=SystemClock.elapsedRealtime()-started;
        if(timed&&threshold>0&&duration>=threshold&&reason==null)capture("slow_after_"+lastPhase);
        stage("complete");
        metric("duration_ms",duration);
        close();
    }

    synchronized void cancel(){
        if(closed)return;
        stage("cancelled");
        close();
    }

    private void close(){
        closed=true;
        if(alarm!=null)AutoIncidentStore.cancel(alarm);
        if(reason!=null)AutoIncidentStore.resolved(id);
    }

    private synchronized void timeout(){
        if(!closed&&reason==null)capture("waiting_after_"+lastPhase);
    }

    synchronized boolean mainStall(StackTraceElement[] frames){
        if(closed)return false;
        if(!timed)metric("delivery_age_ms",inputAge);
        if(reason==null)capture("main_callback_slow");
        AutoIncidentStore.stack(id,frames);
        return true;
    }

    void resource(JSONObject sample){AutoIncidentStore.resource(id,sample);}

    private void capture(String why){
        reason=why;
        AutoIncidentStore.capture(id,kind,why);
        DiagnosticProbe.slowResource("incident_"+kind,this);
    }

    static JSONObject evidence(boolean stack,boolean resource,int sdk)throws JSONException{
        return new JSONObject().put("operationTimeline","observed")
            .put("mainThreadStack",stack?"observed":"missing")
            .put("resourceSample",resource?"observed":"missing")
            .put("physicalDisplay","unsupported").put("externalAppInternals","unsupported")
            .put("systemExit",sdk>=30?"missing":"unsupported");
    }
}
