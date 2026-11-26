package home.quiet.tv;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Correlates finite application operations. Callers supply monotonic time; never store input or exception messages. */
final class DiagnosticOperation {
    interface Sink {void write(String event,String detail);}
    private static final AtomicLong sequence=new AtomicLong();
    private static final ThreadLocal<String> parent=new ThreadLocal<>();
    final String id;
    private final String kind;
    private final long startedAt,startedElapsed;
    private final Sink sink;
    private String stage="START",result="RUNNING",reason="";
    private long stageElapsed,endedElapsed,endedAt;
    DiagnosticOperation(String kind,long wall,long elapsed,Sink sink){
        this.kind=kind;this.startedAt=wall;this.startedElapsed=elapsed;this.stageElapsed=elapsed;String inherited=parentDetail();this.sink=(event,detail)->sink.write(event,detail+inherited);
        id=nextId(wall);
        this.sink.write(kind+".begin","attempt="+id+" startedAt="+wall);
    }
    DiagnosticOperation(String kind,String detail){
        this(kind,System.currentTimeMillis(),clock(),sink(detail));
    }
    private static long clock(){return DiagnosticLog.initialized()?android.os.SystemClock.elapsedRealtime():0;}
    private static Sink sink(String detail){
        int epoch=DiagnosticLog.recordingEpoch();
        return DiagnosticLog.recording(epoch)?(event,value)->{
            if(DiagnosticLog.recording(epoch))DiagnosticLog.info(event,value+" "+detail);
        }:(event,value)->{};
    }
    private static String parentDetail(){String value=parent.get();return value==null?"":" parent="+value;}
    static String current(){String value=parent.get();return value==null?"none":value;}
    static String enter(String id){String previous=parent.get();parent.set(id);return previous;}
    static void restore(String previous){if(previous==null)parent.remove();else parent.set(previous);}
    void stage(String next){stage(next,clock());}
    void finish(String outcome,String why){finish(outcome,why,System.currentTimeMillis(),clock());}
    static String domain(String value){return java.util.Arrays.asList("appearance","feedback","greeting","wallpaper","dock","buttons","weather","catalog","quick","screensaver","uploads","tasks","audio","apps","child","childUsage","quiz","parent","weatherInfo","boot","guard","management","inputs","systemSettings","files","memos","backup","update","diagnostics","components","desktopSwitch","icons").contains(value)?value:"other";}
    static String nextId(long wall){return Long.toHexString(wall)+"-"+sequence.incrementAndGet();}
    synchronized void stage(String next,long now){
        if(!"RUNNING".equals(result) || stage.equals(next))return;
        sink.write(kind+".phase","attempt="+id+" stage="+next+" previous="+stage+" previousMs="+Math.max(0,now-stageElapsed)+" elapsedMs="+elapsed(now));
        stage=next;stageElapsed=now;
    }
    synchronized boolean finish(String outcome,String why,long wall,long now){
        if(!"RUNNING".equals(result))return false;
        result=outcome;reason=why;endedAt=wall;endedElapsed=now;
        sink.write(kind+".result",snapshot(now));return true;
    }
    synchronized String snapshot(long now){
        long end="RUNNING".equals(result)?now:endedElapsed;
        return "attempt="+id+" startedAt="+startedAt+" stage="+stage+" stageMs="+Math.max(0,end-stageElapsed)
                +" elapsedMs="+elapsed(end)+" result="+result+" reason="+reason+(endedAt==0?"":" endedAt="+endedAt);
    }
    synchronized boolean running(){return "RUNNING".equals(result);}
    private long elapsed(long now){return Math.max(0,now-startedElapsed);}
    static String failureResult(Throwable error){
        for(int depth=0;error!=null && depth<8;depth++,error=error.getCause())
            if(error instanceof SocketTimeoutException || error instanceof TimeoutException)return "TIMEOUT";
        return "FAILED";
    }
}
