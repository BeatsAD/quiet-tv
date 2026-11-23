package home.quiet.tv;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A bounded read-only connection reuses existing authorization without blocking management. */
final class BluetoothSystemCapture {
    static final String LOGCAT="logcat -d -t 1000 -v threadtime -b main -b system -b events *:V";
    static final String LOGCAT_CRASH="logcat -d -t 1000 -v threadtime -b main -b system -b events -b crash *:V";
    static final String LOGCAT_DEFAULT="logcat -d -t 1000 -v threadtime *:V";
    private static Future<?> pending;
    private static long requested=-1;
    private static String state="尚未采集；需要已有且已连接的本机ADB";
    private static DiagnosticJournal journal;
    private static String lastLog="";
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),task->{Thread thread=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);task.run();},"quiet-bluetooth-evidence");thread.setDaemon(true);return thread;});
    static synchronized String status(){return DiagnosticLog.recording()?state:"已关闭诊断日志记录";}
    static synchronized void stop(){
        if(pending!=null)pending.cancel(true);
        pending=null;
        worker.purge();
        requested=-1;
        state="尚未恢复采集";
    }
    static synchronized Future<?> request(Context supplied,String reason,boolean force){
        int epoch=DiagnosticLog.recordingEpoch();
        if(!DiagnosticLog.recording(epoch))return null;
        if(pending!=null&&!pending.isDone())return pending;
        long now=SystemClock.elapsedRealtime();if(!force&&requested>=0&&now-requested<3000)return null;
        ManagementAdb management=ManagementAdb.existing();
        if(management==null||!management.adb.connected()){state="未采集：已有本机ADB未连接；不会自动连接或申请授权";return null;}
        Context context=supplied.getApplicationContext();int port=management.adb.connectedPort(),user=management.adb.user;requested=now;state="正在采集蓝牙系统证据";
        pending=worker.submit(()->capture(context,port,user,reason,epoch));return pending;
    }
    static void prepareExport(Context context){
        Future<?> task=request(context,"export",true);if(task==null)return;
        try{task.get(12,TimeUnit.SECONDS);}
        catch(TimeoutException waiting){synchronized(BluetoothSystemCapture.class){state="本次采集尚未结束；导出仅包含已保存证据";}}
        catch(CancellationException stopped){}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        catch(ExecutionException unavailable){synchronized(BluetoothSystemCapture.class){state="本次采集失败："+unavailable.getCause().getClass().getSimpleName();}}
    }
    private static void capture(Context context,int port,int expectedUser,String reason,int epoch){
        if(!DiagnosticLog.recording(epoch))return;
        String capture=Long.toHexString(System.currentTimeMillis());int complete=0,failed=0;
        AdbController adb=new AdbController(context,value->{});
        try{
            String salt=BluetoothDiagnostics.salt(context);
            save(context,epoch,"capture="+capture+" event=begin reason="+reason+" expectedUser="+expectedUser+" elapsedRealtimeMs="+SystemClock.elapsedRealtime());
            adb.connect(port,adb.prepare(),false);adb.validateUser();
            if(adb.user!=expectedUser)throw new IOException("Diagnostic user changed");
            String help="";
            try{help=adb.bluetoothDiagnosticShell("logcat --help");}
            catch(Exception unavailable){save(context,epoch,"capture="+capture+" source=logcat_help status=unavailable type="+unavailable.getClass().getSimpleName());}
            String log=help.matches("(?s).*\\s-t[ \\t<].*")?(help.matches("(?s).*\\s-b[ \\t<].*")?(help.contains("crash")?LOGCAT_CRASH:LOGCAT):LOGCAT_DEFAULT):"";
            LinkedHashMap<String,String> queries=new LinkedHashMap<>();
            if(!log.isEmpty())queries.put("logcat",log);else{failed++;save(context,epoch,"capture="+capture+" source=logcat status=unsupported_tail_option");}
            queries.put("bluetooth_manager","dumpsys bluetooth_manager");queries.put("bluetooth","dumpsys bluetooth");
            queries.put("input","dumpsys input");queries.put("accessibility","dumpsys accessibility");
            queries.put("bluetooth_services","dumpsys activity services com.android.bluetooth");
            String original=DeviceMaintenanceReceiver.original(context);String stock=original.contains("/")?original.substring(0,original.indexOf('/')):"";
            if(stock.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")){queries.put("stock_services","dumpsys activity services "+stock);queries.put("stock_package","dumpsys package "+stock);}
            if(!log.isEmpty())queries.put("followup_logcat",log);
            for(Map.Entry<String,String> query:queries.entrySet()){
                if(!DiagnosticLog.recording(epoch))return;
                if(!adb.connected()){failed++;save(context,epoch,"capture="+capture+" source="+query.getKey()+" status=connection_lost query=not_run");continue;}
                try{
                    String output=adb.bluetoothDiagnosticShell(query.getValue());
                    boolean logs=query.getKey().endsWith("logcat");
                    if(logs&&output.equals(lastLog)){save(context,epoch,"capture="+capture+" source="+query.getKey()+" status=unchanged");complete++;continue;}
                    BluetoothDiagnosticEvidence.Result evidence=BluetoothDiagnosticEvidence.parse(logs?"logcat":query.getKey(),output,salt);
                    List<String> records=new ArrayList<>();records.add("capture="+capture+" source="+query.getKey()+" status=complete fields="+evidence.lines.size()+" omitted="+evidence.omitted+" unparsed="+evidence.unparsed);
                    for(String line:evidence.lines)records.add("capture="+capture+" source="+query.getKey()+" "+line);save(context,epoch,records);
                    if(query.getKey().endsWith("_services"))componentMetadata(context,epoch,capture,evidence);
                    if(logs)lastLog=output;
                    complete++;
                }catch(Exception unavailable){failed++;save(context,epoch,"capture="+capture+" source="+query.getKey()+" status=unavailable type="+unavailable.getClass().getSimpleName());}
            }
        }catch(Exception unavailable){failed++;try{save(context,epoch,"capture="+capture+" status=unavailable type="+unavailable.getClass().getSimpleName());}catch(IOException ignored){}}
        finally{adb.close();}
        synchronized(BluetoothSystemCapture.class){if(DiagnosticLog.recording(epoch))state="最近采集：完成 "+complete+" 项  受阻 "+failed+" 项  时间 "+System.currentTimeMillis();}
    }
    private static synchronized void save(Context context,int epoch,String detail)throws IOException{
        save(context,epoch,Collections.singletonList(detail));
    }
    @SuppressWarnings("deprecation")
    private static void componentMetadata(Context context,int epoch,String capture,BluetoothDiagnosticEvidence.Result evidence)throws IOException{
        java.util.Set<String> components=new java.util.LinkedHashSet<>();java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("(?:^| )service=([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)(?: |$)");
        for(String line:evidence.lines){java.util.regex.Matcher match=pattern.matcher(line);if(match.find())components.add(match.group(1));}
        List<String> records=new ArrayList<>();int count=0;
        for(String component:components){if(count++>=8)break;String detail="capture="+capture+" source=component_metadata component="+component+" scope=application_user ";
            try{android.content.ComponentName name=android.content.ComponentName.unflattenFromString(component);android.content.pm.PackageManager pm=context.getPackageManager();
                android.content.pm.ServiceInfo info=pm.getServiceInfo(name,android.content.pm.PackageManager.GET_DISABLED_COMPONENTS);
                detail+="status=read appEnabledSetting="+pm.getApplicationEnabledSetting(name.getPackageName())+" componentEnabledSetting="+pm.getComponentEnabledSetting(name)+" manifestEnabled="+info.enabled+" manifestApplicationEnabled="+info.applicationInfo.enabled;
            }catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException unavailable){detail+="status=unknown type="+unavailable.getClass().getSimpleName();}
            records.add(detail);
        }
        records.add("capture="+capture+" source=component_metadata inspected="+Math.min(count,8)+" omitted="+Math.max(0,components.size()-8)+" limits=visible_services_only_not_dependency_proof");save(context,epoch,records);
    }
    private static synchronized void save(Context context,int epoch,List<String> details)throws IOException{
        if(!DiagnosticLog.recording(epoch))return;
        if(journal==null)journal=new DiagnosticJournal(new File(context.getFilesDir(),"bluetooth-system-log"));
        List<byte[]> entries=new ArrayList<>();for(String detail:details)entries.add(DiagnosticJournal.entry(System.currentTimeMillis()+" "+detail));journal.appendBatch(entries);
    }
    static synchronized void append(Context context,File file)throws IOException{
        try(OutputStream output=new BufferedOutputStream(new FileOutputStream(file,true))){
            output.write(("\n[蓝牙相关系统日志]\n采集状态："+status()+"\n范围：蓝牙事件触发及专项排查时读取已有ADB可见的近期日志与状态，未持续录制；进程退出和未连接期间可能缺失。每项最多64条结构化记录，两段日志合计最多2MiB。\n隐私：只保存状态码、固定事件、服务类名、进程与匿名设备编号；任意消息、设备名称、MAC、配对码和密钥不保存。unparsed和omitted表示未保留部分，不代表没有故障。\n").getBytes(Charsets.UTF_8));
            if(journal==null){File directory=new File(context.getFilesDir(),"bluetooth-system-log");if(directory.isDirectory())journal=new DiagnosticJournal(directory);}
            if(journal!=null)journal.copyTo(output);
        }
    }
    private BluetoothSystemCapture(){}
}
