package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import androidx.core.util.AtomicFile;
import org.json.*;
import java.io.*;

/** Atomic, per-user/per-firmware baseline. An interrupted run is evidence, never a replay queue. */
final class SetupStore {
    private static final Object LOCK=new Object();
    private final AtomicFile file;
    private final String scope;
    SetupStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"setup-state.json"));scope=android.os.Process.myUid()/100000+":"+Build.FINGERPRINT;}
    private JSONObject read() throws Exception {
        if(!file.getBaseFile().exists())return new JSONObject();
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int size;while((size=in.read(buffer))!=-1)out.write(buffer,0,size);return new JSONObject(out.toString("UTF-8"));}
    }
    JSONObject get() throws Exception {synchronized(LOCK){JSONObject record=read().optJSONObject(scope);return record==null?new JSONObject():record;}}
    void put(String key,Object value) throws Exception {synchronized(LOCK){JSONObject root=read(),record=root.optJSONObject(scope);if(record==null)record=new JSONObject();record.put(key,value);root.put(scope,record);write(root);}}
    private void write(JSONObject root) throws Exception {
        FileOutputStream out=file.startWrite();try{out.write(root.toString().getBytes(home.quiet.tv.Charsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
    }
    void baseline(String previousHome,String originalHome) throws Exception {synchronized(LOCK){JSONObject record=get();if(!record.has("baseline"))put("baseline",new JSONObject().put("previousHome",previousHome).put("originalHome",originalHome).put("time",System.currentTimeMillis()));
        else if(record.getJSONObject("baseline").optString("originalHome").isEmpty() && !originalHome.isEmpty())put("baseline",record.getJSONObject("baseline").put("originalHome",originalHome));}}
    String originalHome() throws Exception {
        JSONObject record=get(),takeover=record.optJSONObject("takeover");
        if(takeover!=null && !takeover.optString("original").isEmpty())return takeover.getString("original");
        JSONObject baseline=record.optJSONObject("baseline");return baseline==null?"":baseline.optString("originalHome");
    }
    void begin(String id,JSONObject baseline) throws Exception {put("run",new JSONObject().put("id",id).put("state","running").put("baseline",baseline).put("results",new JSONArray()));}
    void result(String id,String pkg,String action,String result) throws Exception {synchronized(LOCK){JSONObject run=get().optJSONObject("run");if(run==null || !id.equals(run.optString("id")))throw new IOException("设置任务已改变");run.getJSONArray("results").put(new JSONObject().put("package",pkg).put("action",action).put("result",result));put("run",run);}}
    void end(String id,String state) throws Exception {synchronized(LOCK){JSONObject run=get().optJSONObject("run");if(run!=null && id.equals(run.optString("id"))){run.put("state",state);put("run",run);}}}
    void homeSwitch(String previous,String desired,int user,String state,String observed) throws Exception {
        put("homeSwitch",new JSONObject().put("previousHome",previous).put("desiredHome",desired)
                .put("user",user).put("state",state).put("observedHome",observed).put("time",System.currentTimeMillis()));
    }
    JSONArray bootRuns() throws Exception {JSONArray runs=get().optJSONArray("bootAds");return runs==null?new JSONArray():runs;}
    void bootBegin(String id,int user,JSONArray apps) throws Exception {synchronized(LOCK){
        JSONArray runs=bootRuns();runs.put(new JSONObject().put("id",id).put("user",user).put("firmware",Build.FINGERPRINT).put("time",System.currentTimeMillis()).put("state","running").put("apps",apps).put("observations",new JSONObject()));put("bootAds",runs);
    }}
    private JSONObject bootRun(JSONArray runs,String id) throws Exception {
        for(int i=0;i<runs.length();i++){JSONObject run=runs.getJSONObject(i);if(id.equals(run.getString("id")))return run;}throw new IOException("开机广告任务记录缺失");
    }
    void bootItem(String id,String pkg,String state,boolean attempted,String result) throws Exception {synchronized(LOCK){
        JSONArray runs=bootRuns(),apps=bootRun(runs,id).getJSONArray("apps");boolean found=false;
        for(int i=0;i<apps.length();i++){JSONObject app=apps.getJSONObject(i);if(pkg.equals(app.getString("package"))){app.put("state",state).put("attempted",attempted).put("result",result);found=true;break;}}
        if(!found)throw new IOException("开机广告项目记录缺失");put("bootAds",runs);
    }}
    void bootEnd(String id,String state) throws Exception {synchronized(LOCK){JSONArray runs=bootRuns();bootRun(runs,id).put("state",state);put("bootAds",runs);}}
    void bootObservation(String id,String scene,String result) throws Exception {synchronized(LOCK){
        if(!java.util.Arrays.asList("reboot","coldBoot","wake").contains(scene) || !java.util.Arrays.asList("absent","present","unknown").contains(result))throw new IOException("验证结果无效");
        JSONArray runs=bootRuns();bootRun(runs,id).getJSONObject("observations").put(scene,new JSONObject().put("result",result).put("source","user").put("time",System.currentTimeMillis()));put("bootAds",runs);
    }}
}
