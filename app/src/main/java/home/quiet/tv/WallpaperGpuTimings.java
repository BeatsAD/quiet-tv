package home.quiet.tv;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import java.util.Locale;
import static android.opengl.GLES30.*;

/** Opt-in, bounded asynchronous GPU diagnostics. Never waits for a query result. */
@androidx.annotation.RequiresApi(21)
final class WallpaperGpuTimings {
    private static final int TIME_ELAPSED=0x88bf, DISJOINT=0x8fbb, COUNTER_BITS=0x8864;
    private final int[] queries=new int[8],value=new int[1];
    private final String[] names=new String[8];
    private boolean enabled,recording,open,pending,requested;private int longFrames;
    private int frames,count,samples,attempts;
    private long issued;
    private int summaryFrames,summarySlow;private long summaryTotal,summaryMax;private String summaryWindow="";
    private String scene;
    private int quality;

    WallpaperGpuTimings(Context context){
        if((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0||!context.getSharedPreferences("wallpaper-diagnostics",0).getBoolean("weather-gpu-timing",false))return;
        requested=true;
        String extensions=glGetString(GL_EXTENSIONS);
        if(extensions==null||!extensions.contains("GL_EXT_disjoint_timer_query")){DiagnosticLog.info("wallpaper.gpu.timing","timer query unsupported");return;}
        // GLES3 query entry points alias the EXT entry points. Probe before using them.
        glGetQueryiv(TIME_ELAPSED,COUNTER_BITS,value,0);
        int error=glGetError();
        if(error!=GL_NO_ERROR||value[0]<32){DiagnosticLog.info("wallpaper.gpu.timing","timer query unavailable; bits="+value[0]+" error="+error);return;}
        glGenQueries(queries.length,queries,0);enabled=true;
    }
    void completed(String key,int level,long stamp,long elapsed,boolean cloud,long samples,long moonBuilds,long celestialBuilds,int cameraRevision){
        if(DiagnosticProbe.active()){
            String window=DiagnosticProbe.window();if(!window.equals(summaryWindow)){summaryWindow=window;summaryFrames=summarySlow=0;summaryTotal=summaryMax=0;}
            summaryFrames++;summaryTotal+=elapsed;summaryMax=Math.max(summaryMax,elapsed);if(elapsed>=50000000L)summarySlow++;
            if(summaryFrames>=120&&DiagnosticProbe.allow("wallpaper.frame.summary",5000)){DiagnosticLog.info("wallpaper.frame.summary","incident="+window+" frames="+summaryFrames+" meanNs="+(summaryTotal/summaryFrames)+" maxNs="+summaryMax+" over50ms="+summarySlow+" quality="+level+" evidence=render_completion_not_gpu_or_presentation");summaryFrames=summarySlow=0;summaryTotal=summaryMax=0;}
        }
        if(!requested||elapsed<50000000L||longFrames>=16)return;
        longFrames++;DiagnosticLog.info("wallpaper.frame.long","scene="+key+" q="+level+" stampNs="+stamp+" completionWallMs="+(elapsed/1e6)+" cloud="+cloud+" samples="+samples+" moonBuilds="+moonBuilds+" celestialBuilds="+celestialBuilds+" camera="+cameraRevision);
    }
    void event(String name){if(requested)DiagnosticLog.info("wallpaper.frame.event",name+" atNs="+SystemClock.elapsedRealtimeNanos());}
    void beginFrame(String key,int level){
        if(!enabled)return;
        poll();
        if(samples>=8||attempts>=16||pending||++frames<90||frames%31!=0)return;
        glGetIntegerv(DISJOINT,value,0); // Start a fresh disjoint interval.
        recording=true;count=0;scene=key;quality=level;issued=SystemClock.elapsedRealtimeNanos();attempts++;
    }
    void stage(String name){
        if(!recording)return;
        if(open){glEndQuery(TIME_ELAPSED);open=false;}
        if(count==queries.length)return;
        names[count]=name;glBeginQuery(TIME_ELAPSED,queries[count++]);open=true;
    }
    void endFrame(){
        if(!recording)return;
        if(open){glEndQuery(TIME_ELAPSED);open=false;}
        recording=false;pending=count>0;
    }
    private void poll(){
        if(!pending)return;
        glGetQueryObjectuiv(queries[count-1],GL_QUERY_RESULT_AVAILABLE,value,0);
        if(value[0]==0){if(SystemClock.elapsedRealtimeNanos()-issued>=4000000000L){DiagnosticLog.info("wallpaper.gpu.timing","query expired; diagnostics stopped");destroy();}return;}
        glGetIntegerv(DISJOINT,value,0);boolean valid=value[0]==0&&SystemClock.elapsedRealtimeNanos()-issued<4000000000L;
        // Java exposes the 32-bit result. Reject samples older than its 4.29s range.
        StringBuilder result=new StringBuilder(scene).append(" q=").append(quality);
        for(int i=0;i<count;i++){
            glGetQueryObjectuiv(queries[i],GL_QUERY_RESULT_AVAILABLE,value,0);
            if(value[0]==0)return;
            glGetQueryObjectuiv(queries[i],GL_QUERY_RESULT,value,0);
            result.append(' ').append(names[i]).append("Ms=").append(String.format(Locale.ROOT,"%.3f",(value[0]&0xffffffffL)/1e6));
        }
        glGetIntegerv(DISJOINT,value,0);valid&=value[0]==0;
        DiagnosticLog.info("wallpaper.gpu.pass",valid?result.toString():"discarded disjoint or expired sample");
        if(valid)samples++;
        pending=false;
    }
    void destroy(){endFrame();if(enabled)glDeleteQueries(queries.length,queries,0);enabled=false;}
}
