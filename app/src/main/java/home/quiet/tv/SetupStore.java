package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
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
        FileOutputStream out=file.startWrite();try{out.write(root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
    }
    void baseline(String previousHome,String originalHome) throws Exception {synchronized(LOCK){JSONObject record=get();if(!record.has("baseline"))put("baseline",new JSONObject().put("previousHome",previousHome).put("originalHome",originalHome).put("time",System.currentTimeMillis()));}}
    String originalHome() throws Exception {JSONObject baseline=get().optJSONObject("baseline");return baseline==null?"":baseline.optString("originalHome");}
    boolean shouldOffer(Context context,boolean alreadyHome) throws Exception {
        synchronized(LOCK){JSONObject record=get();if(record.has("status"))return false;
            android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            boolean upgraded=info.lastUpdateTime>info.firstInstallTime || context.getSharedPreferences("HomeActivity",0).contains("row");
            if(alreadyHome || upgraded){put("status","legacy");return false;}
            put("status","pending");return true;
        }
    }
    void begin(String id,JSONObject baseline) throws Exception {put("run",new JSONObject().put("id",id).put("state","running").put("baseline",baseline).put("results",new JSONArray()));}
    void result(String id,String pkg,String action,String result) throws Exception {synchronized(LOCK){JSONObject run=get().optJSONObject("run");if(run==null || !id.equals(run.optString("id")))throw new IOException("设置任务已改变");run.getJSONArray("results").put(new JSONObject().put("package",pkg).put("action",action).put("result",result));put("run",run);}}
    void end(String id,String state) throws Exception {synchronized(LOCK){JSONObject run=get().optJSONObject("run");if(run!=null && id.equals(run.optString("id"))){run.put("state",state);put("run",run);}}}
}
