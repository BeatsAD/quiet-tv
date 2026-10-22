package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import io.github.muntashirakon.adb.AdbStream;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.*;

/** One explicit, bounded recording. Leaving the page does not stop the read-only stream. */
final class DiagnosticsRecorder implements Application.ActivityLifecycleCallbacks {
    static final long MAX_LOG=4L*1024*1024,MAX_EVENTS=512L*1024,DURATION_MS=5L*60*1000;
    private static DiagnosticsRecorder instance;
    final Context context;
    final AdbController adb;
    private final Handler main=new Handler(Looper.getMainLooper());
    private File directory;
    private volatile boolean recording;
    private boolean eventLimitReached;
    private java.lang.Process process;
    private AdbStream stream;
    private Thread reader;
    private String logStatus="未开始",source="未采集";
    private final Runnable expiry=this::expire;
    private void expire(){stop("达到五分钟上限");adb.disconnect();}
    static synchronized DiagnosticsRecorder get(Context context){if(instance==null)instance=new DiagnosticsRecorder(context);return instance;}
    private DiagnosticsRecorder(Context value){
        context=value.getApplicationContext();adb=new AdbController(context,status->event("adb",status));
        String name=context.getSharedPreferences("diagnostics",0).getString("last","");
        if(name.matches("[a-f0-9-]{36}")){File saved=new File(new File(context.getFilesDir(),"diagnostics"),name);if(saved.isDirectory())directory=saved;}
        ((Application)context).registerActivityLifecycleCallbacks(this);
        if(directory!=null){source="上次进程记录，详见 session.json";logStatus="进程已重新启动，未自动续采";}
    }
    synchronized boolean recording(){return recording;}
    synchronized File directory(){return directory;}
    synchronized String summary(){return recording?"正在记录  最长五分钟":directory==null?"尚无诊断记录":"已有记录  可导出";}
    void start() throws Exception {
        if(recording())throw new IOException("正在记录，请先停止并导出");
        File next=new File(new File(context.getFilesDir(),"diagnostics"),UUID.randomUUID().toString());
        if(!next.mkdirs())throw new IOException("无法创建诊断目录");
        synchronized(this){directory=next;recording=true;eventLimitReached=false;source=adb.connected()?"ADB 系统 Logcat（未按 PID 过滤）":"应用权限 Logcat（系统日志受限）";logStatus="正在记录";}
        context.getSharedPreferences("diagnostics",0).edit().putString("last",next.getName()).commit();
        write(new File(next,"session.json"),new JSONObject().put("startedAt",System.currentTimeMillis()).put("startedElapsed",SystemClock.elapsedRealtime()).put("pid",android.os.Process.myPid()).put("source",source).toString(2));
        event("recording_started",source);
        try{
            InputStream input;
            if(adb.connected()){adb.validateUser();stream=adb.openLogcat();input=stream.openInputStream();}
            else{
                java.lang.Process help=new ProcessBuilder("logcat","--help").redirectErrorStream(true).start();StringBuilder options=new StringBuilder();
                try(BufferedReader lines=new BufferedReader(new InputStreamReader(help.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=lines.readLine())!=null && options.length()<65536)options.append(line).append('\n');}finally{help.destroy();}
                process=new ProcessBuilder(logcatCommand(options.toString()).split(" ")).redirectErrorStream(true).start();input=process.getInputStream();
            }
            reader=new Thread(()->capture(next,input),"diagnostic-logcat");reader.setDaemon(true);reader.start();
        }catch(Exception failure){synchronized(this){logStatus="未取得："+failure.getClass().getSimpleName();}event("logcat_unavailable",logStatus);}
        main.postDelayed(expiry,DURATION_MS);
        DiagnosticsSnapshot.capture(context,adb,next,"before.json");
    }
    static String logcatCommand(String help){return "logcat -v threadtime"+(java.util.regex.Pattern.compile("(?m)^\\s*-T\\s").matcher(help).find()?" -T 100":"");}
    private void capture(File folder,InputStream input){
        String result="流已结束";
        try(BufferedReader lines=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));FileOutputStream output=new FileOutputStream(new File(folder,"logcat.txt"))){
            long size=0;String line;
            while((line=lines.readLine())!=null){
                synchronized(this){if(!recording || directory!=folder){result="已停止";break;}}
                byte[] bytes=(DiagnosticText.redact(line)+"\n").getBytes(StandardCharsets.UTF_8);
                if(size+bytes.length>MAX_LOG){result="达到 4 MiB 上限，后续 Logcat 未保存";break;}
                output.write(bytes);size+=bytes.length;
            }
        }catch(IOException failure){result="流已关闭或中断："+failure.getClass().getSimpleName();}
        finally{
            synchronized(this){if(directory==folder){logStatus=result;closeStream();}}
        }
    }
    synchronized void stop(String reason){
        main.removeCallbacks(expiry);
        if(!recording)return;
        event("recording_stopped",reason);recording=false;closeStream();
        try{write(new File(directory,"stopped.json"),new JSONObject().put("stoppedAt",System.currentTimeMillis()).put("reason",reason).toString(2));}catch(Exception failure){android.util.Log.w("QuietTVDiagnostics","Stop receipt unavailable",failure);}
    }
    private void closeStream(){
        if(stream!=null){try{stream.close();}catch(IOException ignored){}stream=null;}
        if(process!=null){process.destroy();process=null;}
    }
    void event(String kind,String detail){
        synchronized(this){
            if(directory==null || !recording)return;
            File file=new File(directory,"events.jsonl");
            try(FileOutputStream output=new FileOutputStream(file,true)){
                JSONObject value=new JSONObject().put("time",System.currentTimeMillis()).put("elapsed",SystemClock.elapsedRealtime()).put("pid",android.os.Process.myPid()).put("event",kind).put("detail",DiagnosticText.redact(detail));
                byte[] bytes=(value+"\n").getBytes(StandardCharsets.UTF_8);if(file.length()+bytes.length>MAX_EVENTS){if(!eventLimitReached)write(new File(directory,"events-limited.txt"),"512 KiB limit reached; subsequent events omitted");eventLimitReached=true;return;}output.write(bytes);
            }catch(IOException|JSONException failure){android.util.Log.w("QuietTVDiagnostics","Event unavailable",failure);}
        }
    }
    File export() throws Exception {
        stop("用户导出");Thread pending=reader;if(pending!=null){pending.join(1500);if(pending.isAlive())throw new IOException("日志流仍在停止，请稍后重试导出");}
        File folder=directory();if(folder==null)throw new IOException("请先开始记录");
        DiagnosticsSnapshot.capture(context,adb,folder,"after.json");
        JSONObject report=new JSONObject().put("exportedAt",System.currentTimeMillis()).put("logcatSource",source).put("logcatStatus",logStatus).put("eventLimitReached",new File(folder,"events-limited.txt").isFile())
            .put("captureStopped",new File(folder,"stopped.json").exists()).put("limitations","系统 HOME 解析不代表实体 HOME、开机或待机验收；进程被杀、重启、日志权限限制或缓冲区覆盖会造成缺失。事件仅覆盖 Quiet TV 自身生命周期及已启用守护收到的窗口事件；不证明系统全局前台状态。未采集其他应用私有日志及独立助手私有 watch.log。常见标识和凭据已尽力脱敏，分享前仍需检查。");
        JSONObject contents=new JSONObject();
        for(String name:new String[]{"session.json","stopped.json","before.json","after.json","events.jsonl","logcat.txt","observation.json","setup-state.json","app-operations.jsonl"}){
            File file=new File(folder,name);contents.put(name,new JSONObject().put("status",file.isFile()?"present":"missing").put("bytes",file.length()).put("reason",file.isFile()?"":"未生成或采集中断；用户结果需手动填写，已有私有记录的复制状态见现场 JSON"));
        }
        report.put("files",contents);
        write(new File(folder,"report.json"),report.toString(2));
        File zip=new File(folder,"QuietTV-diagnostics.zip");
        synchronized(this){try(ZipOutputStream output=new ZipOutputStream(new FileOutputStream(zip))){
            for(String name:new String[]{"session.json","stopped.json","before.json","after.json","events.jsonl","events-limited.txt","logcat.txt","observation.json","report.json","setup-state.json","app-operations.jsonl"}){
                File file=new File(folder,name);if(!file.isFile())continue;
                output.putNextEntry(new ZipEntry(name));try(InputStream input=new FileInputStream(file)){byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);}output.closeEntry();
            }
        }}
        return zip;
    }
    void observe(String result) throws Exception {
        File folder=directory();if(folder==null)throw new IOException("请先开始记录");
        write(new File(folder,"observation.json"),new JSONObject().put("source","user").put("scene","physical_home").put("result",result).put("time",System.currentTimeMillis()).toString(2));
        event("user_physical_home",result);
    }
    static void write(File file,String text) throws IOException {try(FileOutputStream output=new FileOutputStream(file)){output.write(DiagnosticText.redact(text).getBytes(StandardCharsets.UTF_8));}}
    private void lifecycle(Activity activity,String event){event(event,activity.getComponentName().flattenToShortString());}
    @Override public void onActivityCreated(Activity activity,Bundle state){lifecycle(activity,"activity_created");}
    @Override public void onActivityResumed(Activity activity){lifecycle(activity,"activity_resumed");}
    @Override public void onActivityPaused(Activity activity){lifecycle(activity,"activity_paused");}
    @Override public void onActivityStopped(Activity activity){lifecycle(activity,"activity_stopped");}
    @Override public void onActivityDestroyed(Activity activity){lifecycle(activity,"activity_destroyed");}
    @Override public void onActivityStarted(Activity activity){}
    @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
}
