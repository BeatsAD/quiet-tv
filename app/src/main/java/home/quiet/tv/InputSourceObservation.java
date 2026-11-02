package home.quiet.tv;

import android.content.Context;
import android.os.*;
import androidx.core.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;

/** Explicit bounded source observation. Dumps are optional evidence, never executable configuration. */
final class InputSourceObservation {
    static final long LIMIT_MS=120000,MAX_BYTES=8L*1024*1024;
    private static final String LOG_QUERY="logcat -d -t 200 -v threadtime ActivityManager:I ActivityTaskManager:I TvInputManagerService:I HdmiControlService:I *:S";
    private static InputSourceObservation current;
    private static final Object FILE_LOCK=new Object();
    private final Context context;
    private final boolean raw;
    private final String id=UUID.randomUUID().toString();
    private final long started=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<SourceSearch.Candidate> known;
    private final JSONObject report=new JSONObject();
    private final JSONArray samples=new JSONArray(),rawParts=new JSONArray();
    private volatile boolean active=true,finalizing;

    private long rawBytes;
    private final Runnable deadline=()->finish("time_limit");
    private final Runnable baselineDeadline=()->finish("prepare_timeout");
    private InputSourceObservation(Context context,boolean raw,List<SourceSearch.Candidate> known)throws Exception{
        this.context=context.getApplicationContext();this.raw=raw;this.known=known;
        report.put("schemaVersion",1).put("captureId",id).put("startedAt",wall).put("limitMs",LIMIT_MS).put("state","PREPARING")
            .put("rawIncluded",raw).put("user",android.os.Process.myUid()/100000).put("fingerprint",Build.FINGERPRINT)
            .put("model",Build.MODEL).put("api",Build.VERSION.SDK_INT).put("appBuild",BuildConfig.VERSION_CODE).put("samples",samples);
    }
    static synchronized boolean running(){return current!=null&&current.active;}
    static synchronized boolean busy(){return current!=null&&(current.active||current.finalizing);}
    static synchronized String start(Context context,boolean raw)throws Exception{
        if(busy())throw new IllegalStateException("正在记录原厂信号源操作");
        if(!InputAdb.connected())throw new IllegalStateException("本机调试连接不可用，请先检查连接");
        current=new InputSourceObservation(context,raw,SourcePairing.get(context).candidates());
        InputSourceObservation capture=current;capture.persist();capture.main.postDelayed(capture.deadline,LIMIT_MS);capture.main.postDelayed(capture.baselineDeadline,30000);
        capture.worker.execute(capture::sample);return capture.id;
    }
    static synchronized void stopWithResult(String source,String outcome,int port,boolean physical)throws Exception{
        if(current==null||!current.active)throw new IllegalStateException("本次记录已结束，请读取结果");
        if(!Arrays.asList("user","agent").contains(source)||!Arrays.asList("menu","hdmi","failed","unknown").contains(outcome))throw new IllegalArgumentException("实际观察结果无效");
        if("hdmi".equals(outcome)&&(port<1||port>32||!physical||!"user".equals(source)))throw new IllegalArgumentException("请由用户确认实际 HDMI 端口、画面与声音");
        synchronized(current){current.report.put("originalOperation",new JSONObject().put("source",source).put("outcome",outcome)
            .put("hdmiPort","hdmi".equals(outcome)?port:0).put("pictureAudioConfirmed","hdmi".equals(outcome)&&physical).put("configurationVerified",false));}
        current.finish("finished");
    }
    static synchronized void stop(String reason){if(current!=null)current.finish(reason);}
    static JSONObject status(Context context){
        InputSourceObservation capture; synchronized(InputSourceObservation.class){capture=current;}
        if(capture!=null)return capture.summary();
        try{
            JSONObject saved=new JSONObject(context.getSharedPreferences("boot-app",0).getString("source-observation-status","{}"));
            if("RUNNING".equals(saved.optString("state"))||"PREPARING".equals(saved.optString("state")))saved.put("state","INTERRUPTED").put("complete",false);
            return saved;
        }catch(Exception unavailable){return new JSONObject();}
    }
    private synchronized JSONObject summary(){try{return summary(report,active?Math.max(0,LIMIT_MS-(SystemClock.elapsedRealtime()-started)):0).put("finalizing",finalizing);}catch(JSONException impossible){return new JSONObject();}}
    private static JSONObject summary(JSONObject value,long remaining)throws JSONException{
        return new JSONObject().put("captureId",value.optString("captureId")).put("state",value.optString("state","NONE"))
            .put("samples",value.optJSONArray("samples")==null?0:value.optJSONArray("samples").length()).put("remainingMs",remaining)
            .put("rawIncluded",value.optBoolean("rawIncluded")).put("complete",value.optBoolean("complete"))
            .put("analysis",value.optString("analysis","信息不足，暂不能自动配置"));
    }
    private void sample(){
        synchronized(this){if(!active)return;}
        JSONObject quality=new JSONObject();String activities=query("activities","dumpsys activity activities",quality);
        String windows=query("windows","dumpsys window windows",quality);
        String tv=query("tv_input","dumpsys tv_input",quality),hdmi=query("hdmi_control","dumpsys hdmi_control",quality);
        synchronized(this){
            if(!active)return;
            try{
                SettingsPageSnapshot page=SettingsPageSnapshot.parse(activities,windows,"",android.os.Process.myUid()/100000);
                JSONObject sample=new JSONObject().put("at",System.currentTimeMillis()).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                    .put("resumed",page.resumed).put("focused",page.focused).put("action",page.action).put("extrasPresent",page.intentExtras)
                    .put("queryQuality",quality).put("hdmi",hdmiState(hdmi)).put("tvInputBytes",tv.getBytes(Charsets.UTF_8).length);
                if(page.resumed.isEmpty()&&page.focused.isEmpty())sample.put("pageState","UNKNOWN");
                boolean baseline=samples.length()==0;sample.put("phase",baseline?"baseline":"observe");samples.put(sample);
                if(baseline){report.put("state","RUNNING").put("readyAt",System.currentTimeMillis());main.removeCallbacks(baselineDeadline);}
                for(SourceSearch.Candidate candidate:known)if(candidate.panel!=null&&candidate.panel.component!=null){
                    String component=candidate.panel.component.flattenToString();
                    if(component.equals(page.resumed)||component.equals(page.focused))report.put("analysis","发现已支持的入口，请搜索并测试后保存");
                }
                persist();
                DiagnosticLog.info("input.observation.sample","capture="+id+" sample="+samples.length()+" resumed="+page.resumed+" focused="+page.focused);
            }catch(Exception failure){DiagnosticLog.error("input.observation.record_failed",failure);}
            if(active)worker.schedule(this::sample,2000,TimeUnit.MILLISECONDS);
        }
    }
    private String query(String name,String command,JSONObject quality){
        synchronized(this){if(!active)return "";}
        long at=System.currentTimeMillis(),begin=SystemClock.elapsedRealtime();
        try{
            String value=InputAdb.query(context,command);
            synchronized(this){if(!active)return "";quality.put(name,new JSONObject().put("state","READ").put("at",at).put("durationMs",SystemClock.elapsedRealtime()-begin));
                appendRaw(name,at,value);}

            return value;
        }catch(Exception failure){try{quality.put(name,new JSONObject().put("state",InputAdb.quality(failure)).put("errorType",failure.getClass().getSimpleName())
                .put("at",at).put("durationMs",SystemClock.elapsedRealtime()-begin));}catch(JSONException ignored){}return "";}
    }
    static boolean complete(JSONArray samples,boolean truncated,String reason)throws JSONException{
        if(truncated||samples.length()==0||!("finished".equals(reason)||"time_limit".equals(reason)))return false;
        for(int i=0;i<samples.length();i++){
            JSONObject item=samples.getJSONObject(i),quality=item.getJSONObject("queryQuality");
            if("UNKNOWN".equals(item.optString("pageState")))return false;
            for(String channel:new String[]{"activities","windows","tv_input","hdmi_control"}){
                JSONObject read=quality.optJSONObject(channel);if(read==null||!"READ".equals(read.optString("state")))return false;
            }
        }
        return true;
    }
    static JSONObject hdmiState(String value)throws JSONException{
        JSONObject result=new JSONObject();Matcher match=Pattern.compile("(?m)^\\s*(mActivePortId|mActiveSource|mActiveRoutingPath)\\s*[:=]\\s*([0-9a-fA-FxX, .:-]{1,80})\\s*$").matcher(value);
        while(match.find())result.put(match.group(1),match.group(2).trim());
        if(result.length()==0)result.put("state","UNKNOWN");return result;
    }
    synchronized void finish(String reason){
        if(!active)return;active=false;finalizing=true;main.removeCallbacks(deadline);main.removeCallbacks(baselineDeadline);worker.shutdownNow();
        try{report.put("state",reason).put("sessionFinished","finished".equals(reason)||"time_limit".equals(reason)).put("complete",complete(samples,report.optBoolean("rawTruncated"),reason))
            .put("endedAt",System.currentTimeMillis()).put("durationMs",SystemClock.elapsedRealtime()-started);
            if(!report.has("analysis"))report.put("analysis","信息不足，暂不能自动配置；可导出反馈");
        }catch(JSONException failure){DiagnosticLog.error("input.observation.stop_failed",failure);}
        final long ended=System.currentTimeMillis();
        Thread tail=new Thread(()->{
            try{
                if("finished".equals(reason)||"time_limit".equals(reason)){
                    String help=InputAdb.query(context,"logcat --help");
                    if(!help.contains("-t"))throw new InputAdb.UnsupportedQuery("Bounded logcat unavailable");
                    try{
                        String text=InputAdb.query(context,LOG_QUERY),scoped=recentLogs(text,wall,ended);
                        synchronized(InputSourceObservation.this){
                            report.put("logcat",new JSONObject().put("state","READ").put("lines",scoped.isEmpty()?0:scoped.split("\\n").length).put("timeFiltered",true));
                            appendRaw("logcat",System.currentTimeMillis(),scoped);
                        }
                    }catch(Exception failure){synchronized(InputSourceObservation.this){report.put("logcat",SourceSearch.quality(failure));}}
                }
            }catch(Exception failure){synchronized(InputSourceObservation.this){try{report.put("logcat",SourceSearch.quality(failure));}catch(JSONException ignored){}}}
            finally{
                synchronized(InputSourceObservation.this){persist();finalizing=false;}
                synchronized(InputSourceObservation.class){if(current==InputSourceObservation.this)context.getSharedPreferences("boot-app",0).edit().putString("source-observation-status",summary().toString()).apply();}
            }
        },"input-observation-finish");tail.setDaemon(true);tail.start();
    }
    static String recentLogs(String text,long start,long end){
        StringBuilder result=new StringBuilder();SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.ROOT);format.setLenient(false);
        Calendar calendar=Calendar.getInstance();calendar.setTimeInMillis(start);int year=calendar.get(Calendar.YEAR);
        for(String line:text.split("\\r?\\n")){
            if(line.length()<18||!line.substring(0,18).matches("\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"))continue;
            for(int candidate=year-1;candidate<=year+1;candidate++)try{long at=format.parse(candidate+"-"+line.substring(0,18)).getTime();
                if(at>=start&&at<=end){result.append(line).append('\n');break;}}catch(java.text.ParseException ignored){}
        }
        return result.toString();
    }
    private synchronized void appendRaw(String query,long at,String text)throws JSONException{
        if(!raw||report.optBoolean("rawTruncated"))return;
        JSONObject part=new JSONObject().put("query",query).put("at",at).put("text",text);
        int encoded=part.toString().getBytes(Charsets.UTF_8).length;
        if(rawBytes+encoded>MAX_BYTES/2){report.put("rawTruncated",true);return;}
        rawBytes+=encoded;rawParts.put(part);
    }
    private synchronized void persist(){try{
        JSONObject snapshot=new JSONObject(report.toString());if(raw)snapshot.put("raw",rawParts);write(context,"source-observation.json",snapshot);
        context.getSharedPreferences("boot-app",0).edit().putString("source-observation-status",summary().toString()).apply();
    }catch(Exception failure){DiagnosticLog.error("input.observation.persist_failed",failure);}}
    static synchronized void savePairing(Context context,JSONObject snapshot)throws Exception{
        JSONObject copy=new JSONObject(snapshot.toString());copy.put("evidenceOnly",true).put("capturedAt",System.currentTimeMillis())
            .put("user",android.os.Process.myUid()/100000).put("fingerprint",Build.FINGERPRINT).put("model",Build.MODEL)
            .put("api",Build.VERSION.SDK_INT).put("appBuild",BuildConfig.VERSION_CODE);
        omitCustomLabels(copy);write(context,"source-pairing-evidence.json",copy);
    }
    private static void omitCustomLabels(Object value)throws JSONException{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;
            JSONObject labels=object.optJSONObject("labels");
            if(labels!=null){JSONObject safe=new JSONObject();int omitted=0;
                for(Iterator<String> keys=labels.keys();keys.hasNext();){String key=keys.next();Object item=labels.get(key);
                    if(item instanceof String&&!((String)item).matches("(?i)^HDMI\\s*[0-9]{1,2}$"))omitted++;else safe.put(key,item);
                }
                if(omitted>0)object.put("labels",safe).put("labelsOmitted",omitted);
            }
            if(object.has("observed")&&!Arrays.asList("用户确认原厂信号源菜单正常打开","用户确认实际端口、画面与声音正常","用户报告入口或画面声音异常").contains(object.optString("observed"))){
                object.put("observationRecorded",!object.optString("observed").isEmpty());object.remove("observed");
            }
            if(object.has("label")&&!object.optString("label").matches("(?i)^HDMI\\s*[0-9]{1,2}$")){object.remove("label");object.put("labelOmitted",true);}
            for(Iterator<String> keys=object.keys();keys.hasNext();)omitCustomLabels(object.get(keys.next()));
        }else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)omitCustomLabels(array.get(i));}
    }
    static JSONObject configurationSummary(Context context)throws JSONException{
        JSONObject value=new SourcePairingStore(context).value();omitCustomLabels(value);return value;
    }
    private static void write(Context context,String name,JSONObject value)throws Exception{
        byte[] bytes=value.toString().getBytes(Charsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IOException("Input evidence exceeds limit");
        synchronized(FILE_LOCK){
            AtomicFile file=new AtomicFile(new File(context.getFilesDir(),name));FileOutputStream output=file.startWrite();
            try{output.write(bytes);file.finishWrite(output);}catch(Exception failure){file.failWrite(output);throw failure;}
        }
    }
    private static JSONObject read(Context context,String name)throws Exception{
        synchronized(FILE_LOCK){
            AtomicFile file=new AtomicFile(new File(context.getFilesDir(),name));
            try(InputStream input=file.openRead();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1){if(output.size()+n>MAX_BYTES)throw new IOException("Input evidence exceeds limit");output.write(bytes,0,n);}
                return new JSONObject(output.toString("UTF-8"));
            }
        }
    }
    static JSONObject evidence(Context context)throws Exception {
        JSONObject result=new JSONObject();
        for(String name:new String[]{"source-pairing-evidence.json","source-observation.json"})if(new File(context.getFilesDir(),name).isFile()){
            JSONObject value=read(context,name);value.remove("raw");
            if(("RUNNING".equals(value.optString("state"))||"PREPARING".equals(value.optString("state")))&&!running())value.put("state","INTERRUPTED").put("complete",false);
            result.put(name,value);
        }
        return result;
    }
    static boolean hasEvidence(Context context){return new File(context.getFilesDir(),"source-pairing-evidence.json").isFile()||new File(context.getFilesDir(),"source-observation.json").isFile();}
    static File export(Context context)throws Exception{
        File output=new File(context.getCacheDir(),"QuietTV-inputs-"+System.currentTimeMillis()+".zip");
        JSONObject manifest=new JSONObject().put("schemaVersion",1);JSONArray files=new JSONArray();
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(output))){
            for(String name:new String[]{"source-pairing-evidence.json","source-observation.json"}){
                File input=new File(context.getFilesDir(),name);if(!input.isFile())continue;
                JSONObject data=read(context,name);if("RUNNING".equals(data.optString("state"))||"PREPARING".equals(data.optString("state")))data.put("state","INTERRUPTED").put("complete",false);
                byte[] bytes=data.toString(2).getBytes(Charsets.UTF_8);
                zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();
                files.put(new JSONObject().put("name",name).put("bytes",bytes.length).put("sha256",DeviceInputProfile.digest(new String(bytes,Charsets.UTF_8))));
            }
            manifest.put("files",files).put("scope","系统读取、程序解析与用户确认分别记录；请求派发不证明画面声音正常。原始片段仅在用户选择后附加，请检查后私下分享。");
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(manifest.toString(2).getBytes(Charsets.UTF_8));zip.closeEntry();
        }
        if(output.length()>MAX_BYTES)throw new IOException("Input export exceeds limit");return output;
    }
}
