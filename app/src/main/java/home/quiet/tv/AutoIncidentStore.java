package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import androidx.core.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small, local-only incident reports. The regular journal and manual enhanced probe are independent. */
final class AutoIncidentStore {
    private static final int MAX_BYTES=256*1024,MAX_EVENTS=192,MAX_REPORTS=3;
    private static final long BEFORE=15000,AFTER=10000,EXPIRES=7*24*60*60*1000L;
    private static final ArrayDeque<Event> events=new ArrayDeque<>();
    private static final LinkedHashMap<String,Pending> pending=new LinkedHashMap<>();
    private static final ScheduledThreadPoolExecutor io=new ScheduledThreadPoolExecutor(1,r->{
        Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"quiet-auto-incident");t.setDaemon(true);return t;
    });
    static{io.setKeepAliveTime(20,TimeUnit.SECONDS);io.allowCoreThreadTimeOut(true);}
    private static final class Event {
        final String id,kind,phase,metric;
        final long time,value;
        Event(String id,String kind,String phase,String metric,long time,long value){
            this.id=id;this.kind=kind;this.phase=phase;this.metric=metric;this.time=time;this.value=value;
        }
        JSONObject json()throws JSONException {
            JSONObject item=new JSONObject().put("elapsedMs",time).put("operationId",id).put("kind",kind).put("phase",phase);
            return metric==null?item:item.put("metric",metric).put("value",value);
        }
    }
    static final class Incident {
        final String id,summary;
        final long atMs;
        Incident(JSONObject report){
            id=report.optString("id");atMs=report.optLong("atMs");
            summary=title(report.optString("kind"),report.optString("reason"));
        }
    }
    private static final class Pending {
        final String id=UUID.randomUUID().toString(),operation,kind,reason;
        final long at=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
        final List<Event> evidence=new ArrayList<>();
        int count=1;
        boolean resolved;
        JSONArray stack;
        JSONObject resource;
        Pending(String operation,String kind,String reason){this.operation=operation;this.kind=kind;this.reason=reason;}
    }

    private static volatile File directory;
    private static DiagnosticSession session;
    private static String packageName;

    static void init(Context app,Context storage,DiagnosticSession value){
        session=value;packageName=app.getPackageName();
        io.execute(()->{
            try{
                File dir=new File(storage.getFilesDir(),"quiet-auto-incidents");
                if(!dir.isDirectory()&&!dir.mkdirs())return;
                directory=dir;
                prune();
                File[] abandoned=app.getCacheDir().listFiles((parent,name)->name.startsWith("quiet-incident-")&&name.endsWith(".zip"));
                if(abandoned!=null)for(File file:abandoned)file.delete();
                if(DiagnosticLog.recording())AutoIncidentExit.capture(app,dir,value,packageName);
            }catch(Exception error){failed();}
        });
    }

    static ScheduledFuture<?> schedule(Runnable action,long ms){return io.schedule(action,ms,TimeUnit.MILLISECONDS);}
    static void cancel(ScheduledFuture<?> alarm){
        alarm.cancel(false);
        if(alarm instanceof Runnable)io.remove((Runnable)alarm);
    }

    static synchronized void event(String id,String kind,String phase,String metric,long value){
        if(!DiagnosticLog.recording())return;
        long time=SystemClock.elapsedRealtime();
        Event event=new Event(id,kind,phase,metric,time,value);
        while(!events.isEmpty()&&(events.size()>=MAX_EVENTS||time-events.peekFirst().time>BEFORE))
            events.removeFirst();
        events.addLast(event);
        for(Pending item:pending.values()){
            if(time-item.elapsed<=AFTER&&item.evidence.size()<MAX_EVENTS*2)item.evidence.add(event);
        }
    }

    static synchronized String capture(String operation,String kind,String reason){
        if(!DiagnosticLog.recording())return "";
        long now=SystemClock.elapsedRealtime();
        // Coalesce rapid repeats while the first incident is still collecting evidence.
        for(Pending item:pending.values()){
            if(item.kind.equals(kind)&&item.reason.equals(reason)&&now-item.elapsed<=AFTER){
                item.count=Math.min(item.count+1,9999);return item.id;
            }
        }
        if(pending.size()>=MAX_REPORTS){
            Iterator<Pending> it=pending.values().iterator();
            Pending oldest=it.next();it.remove();
            // Scheduled final save is ignored if this copy is no longer active.
            io.execute(()->writePending(oldest,true));
        }
        Pending item=new Pending(operation,kind,reason);
        for(Event event:events)if(item.elapsed-event.time<=BEFORE)item.evidence.add(event);
        pending.put(operation,item);
        io.execute(()->writePending(item,false));
        schedule(()->{
            synchronized(AutoIncidentStore.class){
                if(pending.get(operation)!=item)return;
                pending.remove(operation);
            }
            writePending(item,true);
        },AFTER);
        return item.id;
    }

    static synchronized void stack(String operation,StackTraceElement[] frames){
        Pending item=pending.get(operation);
        if(item==null||item.stack!=null||frames.length==0)return;
        item.stack=new JSONArray();
        for(int i=0;i<Math.min(12,frames.length);i++){
            StackTraceElement frame=frames[i];
            item.stack.put("at "+bounded(frame.getClassName(),180)+"."+bounded(frame.getMethodName(),100)+"("
                +(frame.isNativeMethod()?"Native Method":frame.getFileName()==null?"Unknown Source":bounded(frame.getFileName(),90)
                    +(frame.getLineNumber()>=0?":"+frame.getLineNumber():""))+")");
        }
        io.execute(()->writePending(item,false));
    }

    static synchronized void resource(String operation,JSONObject resource){
        Pending item=pending.get(operation);
        if(item!=null&&item.resource==null)item.resource=resource;
    }

    static synchronized void resolved(String operation){
        Pending item=pending.get(operation);
        if(item!=null)item.resolved=true;
    }

    static void markRecent(Context context)throws Exception{
        String operation="manual-"+UUID.randomUUID();
        String id=capture(operation,"manual","user_report");
        io.submit(()->{requireDirectory();read(file(id));return null;}).get(5,TimeUnit.SECONDS);
    }

    static List<Incident> recent(Context context)throws Exception{
        return io.submit(()->{
            requireDirectory();prune();List<Incident> result=new ArrayList<>();
            for(File file:reports())try{result.add(new Incident(read(file)));}catch(IOException|JSONException ignored){}
            Collections.sort(result,(a,b)->a.atMs>b.atMs?-1:a.atMs==b.atMs?0:1);
            return result;
        }).get(5,TimeUnit.SECONDS);
    }

    static JSONArray recentEvidence()throws Exception{
        return io.submit(()->{
            JSONArray evidence=new JSONArray();
            if(directory!=null){
                prune();
                for(File file:reports())try{evidence.put(read(file));}catch(IOException|JSONException ignored){}
            }
            return evidence;
        }).get(5,TimeUnit.SECONDS);
    }

    static File export(Context context,String id)throws Exception{
        requireDirectory();
        if(id==null||!id.matches("[0-9a-f-]{36}"))throw new IOException("Invalid diagnostic report");
        return io.submit(()->{
            prune();
            JSONObject report=read(file(id));
            File output=File.createTempFile("quiet-incident-",".zip",context.getCacheDir());
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
                entry(zip,"summary.txt",summary(report).getBytes(Charsets.UTF_8));
                entry(zip,"incident.json",report.toString().getBytes(Charsets.UTF_8));
            }catch(IOException failure){output.delete();throw failure;}
            return output;
        }).get(5,TimeUnit.SECONDS);
    }

    private static void entry(ZipOutputStream zip,String name,byte[] content)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(content);zip.closeEntry();
    }

    private static File file(String id){return new File(directory,id+".json");}

    private static void requireDirectory()throws IOException{
        if(directory==null)throw new IOException("Diagnostic storage unavailable");
    }

    private static void writePending(Pending item,boolean sealed){
        if(directory==null)return;
        try{
            JSONObject report;
            List<Event> snapshot;
            synchronized(AutoIncidentStore.class){
                if(!sealed&&pending.get(item.operation)!=item)return;
                report=new JSONObject().put("schemaVersion",1).put("id",item.id).put("atMs",item.at).put("triggerElapsedMs",item.elapsed)
                    .put("build",BuildConfig.VERSION_CODE).put("version",BuildConfig.VERSION_NAME).put("package",packageName)
                    .put("sourceSha256",session.sourceSha256()).put("sdk",Build.VERSION.SDK_INT)
                    .put("model",bounded(Build.MODEL,100)).put("firmware",bounded(Build.DISPLAY,160))
                    .put("session",session.id).put("operationId",item.operation).put("kind",item.kind).put("reason",item.reason)
                    .put("count",item.count).put("state",sealed?"sealed":"partial").put("operationResolved",item.resolved)
                    .put("evidence",AutoDiagnosticOperation.evidence(item.stack!=null,item.resource!=null,Build.VERSION.SDK_INT));
                if(item.stack!=null)report.put("stack",item.stack);
                if(item.resource!=null)report.put("resource",item.resource);
                snapshot=new ArrayList<>(item.evidence);
            }
            JSONArray timeline=new JSONArray();
            for(Event event:snapshot)timeline.put(event.json());
            report.put("events",timeline);
            save(report);
        }catch(Exception error){failed();}
    }

    static void save(JSONObject report)throws IOException{
        requireDirectory();
        write(file(report.optString("id")),report);
        prune();
    }

    static void write(File file,JSONObject report)throws IOException{
        byte[] bytes=report.toString().getBytes(Charsets.UTF_8);
        if(bytes.length>MAX_BYTES)throw new IOException("Diagnostic report too large");
        AtomicFile atomic=new AtomicFile(file);
        FileOutputStream output=atomic.startWrite();
        try{output.write(bytes);atomic.finishWrite(output);}
        catch(IOException failure){atomic.failWrite(output);throw failure;}
    }

    static JSONObject read(File file)throws IOException,JSONException{
        try(InputStream input=new AtomicFile(file).openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[4096];
            for(int n;(n=input.read(buffer))!=-1;){
                if(bytes.size()+n>MAX_BYTES)throw new IOException("Diagnostic report too large");
                bytes.write(buffer,0,n);
            }
            return new JSONObject(bytes.toString("UTF-8"));
        }
    }

    private static List<File> reports(){
        File[] listed=directory.listFiles((dir,name)->name.matches("[0-9a-f-]{36}\\.json"));
        List<File> result=listed==null?new ArrayList<>():new ArrayList<>(Arrays.asList(listed));
        Collections.sort(result,(a,b)->{long x=a.lastModified(),y=b.lastModified();return x>y?-1:x==y?0:1;});
        return result;
    }

    private static void prune(){
        if(directory==null)return;
        long cutoff=System.currentTimeMillis()-EXPIRES;
        List<File> files=reports();
        for(int i=0;i<files.size();i++){
            File file=files.get(i);
            if(i>=MAX_REPORTS||file.lastModified()<cutoff)new AtomicFile(file).delete();
        }
    }

    static String bounded(String value,int limit){return value==null?"":value.substring(0,Math.min(limit,value.length()));}

    private static String title(String kind,String reason){
        String name=kind.startsWith("input_")?"遥控按键":kind.equals("home_resume")?"返回桌面或唤醒":kind.equals("home_wake")?"亮屏后应用回调":kind.equals("home_intent")?"HOME 请求回调":
            kind.equals("settings_enter")?"进入设置":kind.equals("settings_tab")?"设置切页":
            kind.equals("quick_controls")?"快捷控制":kind.equals("app_launch")?"应用启动请求":kind.equals("process_exit")?"QuietTV 异常退出":"用户标记";
        String finding=reason.equals("input_delivery_late")?"应用收到时已延迟":reason.equals("input_handler_slow")?"应用回调耗时较长":
            reason.equals("input_delivery_and_handler_slow")?"送达和应用回调均较慢":reason.equals("main_callback_slow")?"已在应用回调期间采样":
            reason.equals("user_report")?"保存最近已观测事件":kind.equals("process_exit")?"Android 退出记录":"操作阶段超时";
        return name+"  "+finding;
    }

    private static String summary(JSONObject data){
        StringBuilder result=new StringBuilder("QuietTV 最近异常\n").append(title(data.optString("kind"),data.optString("reason")))
            .append("\n发生时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date(data.optLong("atMs"))))
            .append("\n构建：").append(data.optString("version")).append(" / ").append(data.optInt("build"))
            .append("\n设备：").append(data.optString("model")).append("  Android API ").append(data.optInt("sdk"))
            .append("\n固件：").append(data.optString("firmware"))
            .append("\n触发：").append(data.optString("reason")).append("  次数：").append(data.optInt("count"))
            .append("\n记录：").append("partial".equals(data.optString("state"))?"已保存的现场片段，后续窗口尚未合并":"已封存")
            .append("\n现场主线程栈：").append(data.has("stack")?"已观测":"未取得")
            .append("\n资源：").append(data.has("resource")?"触发后采样，并非发生时峰值":"未取得")
            .append("\n限制：窗口焦点和绘制回调不代表画面已上屏。未观察外部播放器内部、系统合成或 HDMI 输出。\n");
        JSONObject exit=data.optJSONObject("exit");
        if(exit!=null)result.append("系统退出原因：").append(exit.optString("reason")).append("  关联：").append(exit.optString("match")).append('\n');
        JSONArray timeline=data.optJSONArray("events");
        String operation=data.optString("operationId");
        long start=Long.MIN_VALUE;
        if(timeline!=null)for(int i=0;i<timeline.length();i++){
            JSONObject event=timeline.optJSONObject(i);
            if(event==null||!operation.equals(event.optString("operationId")))continue;
            long t=event.optLong("elapsedMs");
            if(start==Long.MIN_VALUE)start=t;
            result.append(label(event.optString("phase"))).append(" +").append(t-start).append("ms");
            if(event.has("metric"))result.append("  ").append(label(event.optString("metric"))).append('=').append(event.optLong("value"));
            result.append('\n');
        }
        return result.toString();
    }

    private static String label(String key){
        switch(key){
            case "start":return "开始";
            case "complete":return "操作结束";
            case "cancelled":return "操作取消";
            case "SCREEN_ON_CALLBACK":return "应用收到亮屏回调（不等于面板已经点亮）";
            case "FIRST_DRAW_CALLBACK":return "首个绘制回调（不等于上屏）";
            case "WINDOW_FOCUS_CALLBACK":return "应用收到窗口焦点";
            case "FOCUSED_READY_DRAW":return "内容绘制回调与窗口焦点均已收到";
            case "RESUME_CALLBACK_COMPLETE":return "首页恢复回调完成";
            case "DISPATCHED_UNVERIFIED":return "启动请求已发出，外部应用是否已显示未核实";
            case "delivery_age_ms":return "收到按键时已等待（ms）";
            case "handler_ms":return "应用处理按键（ms）";
            case "duration_ms":return "操作耗时（ms）";
            case "frame_sample":return "应用窗口帧统计（不等于上屏）";
            default:return key;
        }
    }
    private static void failed(){DiagnosticLog.info("diagnostic.auto_incident","storage_or_exit_evidence_unavailable");}
    private AutoIncidentStore(){}
}
