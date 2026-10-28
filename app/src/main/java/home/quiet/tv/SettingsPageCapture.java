package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit, read-only, process-owned capture that survives leaving the diagnostics panel. */
final class SettingsPageCapture {
    private static final long LIMIT_MS=180000,INTERVAL_MS=2000;
    private static SettingsPageCapture current;
    private final Context context;
    private final AdbController adb;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final String id=Long.toHexString(System.currentTimeMillis());
    private final long started=SystemClock.elapsedRealtime();
    private final Set<String> inspected=new HashSet<>();
    private final Set<String> unavailable=new LinkedHashSet<>();
    private volatile boolean running=true;
    private String state="正在连接本机 ADB",previous="";
    private int samples,changes,unknown,capturedUser=-1;
    private final Runnable deadline=()->stop("time_limit");
    private SettingsPageCapture(Context context){this.context=context.getApplicationContext();adb=new AdbController(this.context,value->{});}
    static synchronized boolean running(){return current!=null&&current.running;}
    static synchronized String summary(){return current==null?"本进程未启动设置页面采集；历史结果见 settings.capture 事件，进程终止可能使采集中断。":current.describe();}
    static synchronized void start(Context context,Runnable ready){
        if(running())return;
        SettingsPageCapture capture=new SettingsPageCapture(context);current=capture;capture.connect(ready);
    }
    static synchronized void stopCurrent(String reason){if(current!=null)current.stop(reason);}
    private synchronized String describe(){return "采集状态："+state+"\n采集编号："+id+"\n采样次数："+samples+"  结构变化："+changes+"  前台未知："+unknown
        +"\nAndroid 用户："+(capturedUser<0?"未确认":Integer.toString(capturedUser))
        +"\n受阻查询："+(unavailable.isEmpty()?"未记录":unavailable)
        +"\n范围：本机当前 Android 用户，手动开始，最多180秒，每次快照后间隔2秒；仅组件、action、Fragment候选及组件访问状态。不保存原始转储、Intent数据/extra值、页面文字或网络列表。"
        +"\n限制：采样可能遗漏短暂页面；Fragment候选不证明可见，普通View内部切换可能不可见；未知不代表没有页面。组件可见不证明允许直接启动或真机显示成功。";}
    private void connect(Runnable ready){
        DiagnosticLog.info("settings.capture.begin","capture="+id+" limitMs="+LIMIT_MS+" intervalMs="+INTERVAL_MS);
        main.postDelayed(deadline,LIMIT_MS);
        final int request=adb.prepare();
        worker.execute(()->{
            try{
                adb.connect(context.getSharedPreferences("adb",0).getInt("port",5555),request);
                synchronized(this){if(!running)return;capturedUser=adb.user;state="正在采集，返回诊断日志后停止并生成";
                    DiagnosticLog.info("settings.capture.connected","capture="+id+" user="+capturedUser);}
                main.post(()->{if(running)ready.run();});
                sample();
            }catch(Exception failure){fail("connect",failure);}
        });
    }
    private void sample(){
        if(!running)return;
        if(SystemClock.elapsedRealtime()-started>=LIMIT_MS){stop("time_limit");return;}
        try{
            adb.validateUser();
            String activities=query("activities","dumpsys activity activities");
            String windows=query("windows","dumpsys window windows");
            // Client-side dump exposes Fragment class names on some firmware. Text is never logged.
            String top=query("top","dumpsys activity top");
            SettingsPageSnapshot snapshot=SettingsPageSnapshot.parse(activities,windows,top,adb.user);
            synchronized(this){
                if(!running)return;
                samples++;if(snapshot.resumed.isEmpty())unknown++;
                if(!snapshot.key().equals(previous)){
                    previous=snapshot.key();changes++;
                    DiagnosticLog.info("settings.capture.page","capture="+id+" sample="+samples+" user="+capturedUser+" elapsedMs="+(SystemClock.elapsedRealtime()-started)+" "+snapshot.page());
                    DiagnosticLog.info("settings.capture.fragments","capture="+id+" sample="+samples+" count="+snapshot.fragments.size()+" scope=active_or_added_not_verified_visible limit=8");
                    for(String fragment:snapshot.fragments)DiagnosticLog.info("settings.capture.fragment","capture="+id+" sample="+samples+" candidate="+fragment);
                    inspect(snapshot.resumed);if(!snapshot.focused.equals(snapshot.resumed))inspect(snapshot.focused);
                }
            }
            synchronized(this){if(running)worker.schedule(this::sample,INTERVAL_MS,TimeUnit.MILLISECONDS);}
        }catch(Exception failure){fail("snapshot",failure);}
    }
    private String query(String source,String command) throws Exception {
        try{
            String output=adb.diagnosticShell(command);
            // Some Android dumpsys services print denial/unknown-service text with exit code zero.
            if(output.contains("Permission Denial")||output.contains("Permission denied")||output.contains("Can't find service")||output.startsWith("Unknown command"))
                throw new java.io.IOException("Diagnostic query unavailable");
            return output;
        }
        catch(Exception failure){
            if(!adb.connected())throw failure;
            synchronized(this){if(running && unavailable.add(source+":"+failure.getClass().getSimpleName()))
                DiagnosticLog.info("settings.capture.query_failed","capture="+id+" source="+source+" type="+failure.getClass().getSimpleName());}
            return "";
        }
    }
    private void inspect(String value){
        if(value.isEmpty() || inspected.size()>=24 || !inspected.add(value))return;
        try{
            ComponentName name=ComponentName.unflattenFromString(value);PackageManager pm=context.getPackageManager();
            ActivityInfo info=pm.getActivityInfo(name,0);
            int app=pm.getApplicationEnabledSetting(info.packageName),component=pm.getComponentEnabledSetting(name);
            boolean enabled=(app==0?info.applicationInfo.enabled:app==1)&&(component==0?info.enabled:component==1);
            DiagnosticLog.info("settings.capture.component","capture="+id+" component="+value+" exported="+info.exported+" enabled="+enabled
                +" system="+((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)+" permission="+(info.permission==null?"none":info.permission)
                +" permissionGranted="+(info.permission==null||pm.checkPermission(info.permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED));
        }catch(PackageManager.NameNotFoundException|RuntimeException unavailable){DiagnosticLog.info("settings.capture.component","capture="+id+" component="+value+" metadata=unavailable");}
    }
    private void fail(String stage,Exception failure){
        synchronized(this){if(!running)return;DiagnosticLog.info("settings.capture.failed","capture="+id+" stage="+stage+" type="+failure.getClass().getSimpleName());}
        stop(stage+"_"+failure.getClass().getSimpleName());
    }
    private synchronized void stop(String reason){
        if(!running)return;running=false;
        state=reason.startsWith("connect_")?"连接失败，请检查本机 TCP ADB 与授权":reason.startsWith("snapshot_")?"采集受阻，详见日志":
            "time_limit".equals(reason)?"已达到3分钟，采集结束":"screen_off".equals(reason)?"息屏后已停止采集":"user_stop".equals(reason)?"已停止采集":"采集已取消";
        DiagnosticLog.info("settings.capture.result","capture="+id+" reason="+reason+" samples="+samples+" changes="+changes+" unknown="+unknown+" elapsedMs="+(SystemClock.elapsedRealtime()-started));
        main.removeCallbacks(deadline);adb.close();worker.shutdownNow();
    }
}
