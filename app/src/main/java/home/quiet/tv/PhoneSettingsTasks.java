package home.quiet.tv;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Bounded long-running work, separate from the four HTTP client threads. */
final class PhoneSettingsTasks {
    interface Work {JSONObject run(Job job)throws Exception;}
    static final class Problem extends IOException {
        final boolean unknown;
        Problem(String message,boolean unknown){super(message);this.unknown=unknown;}
    }
    final class Job {
        final String id=UUID.randomUUID().toString(),key,title,fingerprint,domain,kind;
        private final PhoneSettingsServer.Active authority;
        volatile String state="queued",message="等待处理";
        volatile int progress=-1;
        volatile boolean cancelled,started,scheduled,committed,cancellable=true;
        private JSONObject result;
        final DiagnosticOperation diagnostic;
        private int diagnosticProgress=-1;
        Job(String key,String title,String fingerprint,PhoneSettingsServer.Active authority,String domain,String kind){this.key=key;this.title=title;this.fingerprint=fingerprint;this.authority=authority;this.domain=domain;this.kind=kind;diagnostic=new DiagnosticOperation("phone.task","task="+DiagnosticLog.anonymous("task",id)+" domain="+DiagnosticOperation.domain(domain)+" kind="+safeKind(kind)+("backup".equals(domain)&&"restore".equals(kind)?" backup="+DiagnosticLog.anonymous("backup",fingerprint):""));}
        boolean active(){return !cancelled&&(committed||authority.valid());}
        void check()throws InterruptedIOException{if(!active()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("任务已取消或连接已结束");}
        void stage(String message,int progress){this.message=message;this.progress=progress;int bucket=progress<0?-1:Math.min(4,progress/25);if(bucket!=diagnosticProgress){diagnosticProgress=bucket;diagnostic.stage("PROGRESS_"+(bucket<0?"INDETERMINATE":bucket*25));}}
        boolean terminal(){return Arrays.asList("completed","failed","cancelled","unknown").contains(state);}
        synchronized JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("domain",domain).put("kind",kind).put("title",title).put("state",state).put("message",message).put("progress",progress).put("cancellable",cancellable).put("result",result);}
    }
    private static String safeKind(String value){return Arrays.asList("apply","restore","batch","delete","rename","export","mark","import","refresh","check","download","install","uninstall","hide","show","disable","enable","open","upload","admit","automatic","retry").contains(value)?value:"other";}
    private final LinkedHashMap<String,Job> jobs=new LinkedHashMap<>();
    private final ExecutorService worker=Executors.newFixedThreadPool(2,r->{Thread thread=new Thread(r,"phone-settings-task");thread.setDaemon(true);return thread;});
    synchronized Job begin(String key,String title,String fingerprint,PhoneSettingsServer.Active active)throws IOException{
        String[] route=key.split("/",3);String domain=route.length>1?route[1]:"",kind=route.length>2?route[2]:fingerprint;
        if(domain.equals("daily")||domain.equals("file-wallpaper"))domain="wallpaper";
        return begin(key,title,fingerprint,active,domain,kind);
    }
    synchronized Job begin(String key,String title,String fingerprint,PhoneSettingsServer.Active active,String domain,String kind)throws IOException{
        for(Job job:jobs.values())if(job.key.equals(key)&&!job.terminal()){
            if(!job.fingerprint.equals(fingerprint))throw new PhoneSettingsServer.Conflict();return job;
        }
        if(jobs.size()>=16){String remove=null;for(Map.Entry<String,Job> entry:jobs.entrySet())if(entry.getValue().terminal()){remove=entry.getKey();break;}if(remove==null)throw new IOException("已有任务正在处理，请稍后重试");jobs.remove(remove);}
        if(!active.valid())throw new PhoneSettingsServer.Ended();Job job=new Job(key,title,fingerprint,active,domain,kind);jobs.put(job.id,job);return job;
    }
    synchronized Job previous(String key,String fingerprint)throws IOException{
        for(Job job:jobs.values())if(job.key.equals(key)){if(!job.fingerprint.equals(fingerprint))throw new PhoneSettingsServer.Conflict();return job;}return null;
    }
    synchronized Job find(String id){return jobs.get(id);}
    synchronized boolean commitRestore(Job job){
        if(restoring())return false;
        synchronized(job){if(job.scheduled||job.terminal()||!job.active())return false;job.committed=true;job.cancellable=false;return true;}
    }
    synchronized boolean restoring(){for(Job job:jobs.values())if(job.committed&&!job.terminal())return true;return false;}
    synchronized JSONObject activeRestore()throws JSONException{for(Job job:jobs.values())if(job.committed&&!job.terminal())return new JSONObject().put("state",job.state).put("message",job.message).put("progress",job.progress);return new JSONObject();}
    synchronized boolean cancel(String id){Job job=jobs.get(id);if(job==null)return false;synchronized(job){if(job.terminal()||!job.cancellable)return false;job.cancelled=true;job.message=job.started?"已请求取消，正在核对结果":"已取消";if(!job.started){job.state="cancelled";job.diagnostic.finish("CANCELLED","before_start");}else job.diagnostic.stage("CANCEL_REQUESTED");return true;}}
    void fail(Job job,String message){job.state=job.cancelled||!job.authority.valid()?"cancelled":"failed";job.message=message;job.diagnostic.finish(job.state.toUpperCase(java.util.Locale.ROOT),"explicit_failure");}
    void complete(Job job,JSONObject result,String message){synchronized(job){job.result=result;job.message=message;job.progress=100;job.state="completed";job.diagnostic.finish("COMPLETED","explicit_completion");}}
    void unknown(Job job,JSONObject result,String message){synchronized(job){job.result=result;job.message=message;job.state="unknown";job.diagnostic.finish("UNKNOWN","result_unconfirmed");}}
    void start(Job job,Work work){
        synchronized(job){if(job.scheduled||job.terminal())return;job.scheduled=true;}
        worker.execute(()->{
            String previousParent=DiagnosticOperation.enter(job.diagnostic.id);
            try{job.diagnostic.stage("RUNNING");job.check();job.started=true;job.state="running";JSONObject result=work.run(job);synchronized(job){job.result=result;job.state="completed";job.progress=100;if(job.cancelled)job.message="任务已完成，取消未生效";else if(job.message.isEmpty()||job.message.equals("等待处理"))job.message="已完成";}}
            catch(InterruptedIOException e){job.state="cancelled";job.message="已取消或连接已结束";}
            catch(Problem e){job.state=e.unknown?"unknown":"failed";job.message=e.getMessage();}
            catch(Exception|OutOfMemoryError e){DiagnosticLog.error("phone.task.failed",e);job.state=job.cancelled?"cancelled":"failed";job.message=job.cancelled?"已取消":"操作未完成，请检查电视状态后重新核对";}
            finally{job.diagnostic.finish(job.state.toUpperCase(java.util.Locale.ROOT),job.committed?"committed":job.cancelled?"cancel_requested":"worker_returned");DiagnosticOperation.restore(previousParent);}
        });
    }
    synchronized JSONObject snapshot(String scope)throws IOException{
        try{JSONArray entries=new JSONArray();for(Job job:jobs.values()){
            if(!job.terminal()&&!job.started&&!job.active()){job.state="cancelled";job.message="连接已结束";job.diagnostic.finish("CANCELLED","connection_ended_before_start");}
            if(job.key.startsWith(scope+"/"))entries.put(job.json());
        }return new JSONObject().put("jobs",entries);}catch(JSONException e){throw new IOException(e);}
    }
    JSONObject receipt(Job job)throws IOException{try{return new JSONObject().put("taskId",job.id).put("message",job.message).put("state",job.state);}catch(JSONException e){throw new IOException(e);}}
}
