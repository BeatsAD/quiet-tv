package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import androidx.core.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.json.JSONException;
import org.json.JSONObject;

/** Before legacy HOME tries an image, persist exactly which package and file are in flight. */
final class LegacyIconRecovery {
    private static final String VERIFIED="verified",SKIPPED="skipped";
    private static final String KEY_PATTERN="cover-[a-f0-9-]{36}\\.png";
    private static LegacyIconRecovery instance;
    private final AtomicFile stateFile;
    private volatile State state=new State(new JSONObject(),"","",0,false);
    private boolean prepared;
    private static final class State {
        final JSONObject records;
        final String pkg,key;
        final int version;
        final boolean available;
        State(JSONObject records,String pkg,String key,int version,boolean available){
            this.records=records;this.pkg=pkg;this.key=key;this.version=version;this.available=available;
        }
        boolean inFlight(){return !pkg.isEmpty();}
        boolean trial(String pkg,String key){return this.pkg.equals(pkg)&&this.key.equals(key)&&inFlight();}
        String status(String pkg,String key){
            JSONObject record=records.optJSONObject(pkg);
            return record!=null&&key.equals(record.optString("key"))?record.optString("status"):"";
        }
    }

    static boolean supported(){return Build.VERSION.SDK_INT<=19;}

    static synchronized LegacyIconRecovery get(Context context){
        if(instance==null)instance=new LegacyIconRecovery(new File(context.getApplicationContext().getFilesDir(),"legacy-icon-trial.json"));
        return instance;
    }

    LegacyIconRecovery(File path){stateFile=new AtomicFile(path);}

    /** Called on the catalog worker. Never replay an interrupted candidate at startup. */
    synchronized void prepare(){
        if(prepared)return;
        prepared=true;
        File base=stateFile.getBaseFile();
        if(!base.isFile()&&!new File(base.getPath()+".bak").isFile()){
            state=new State(new JSONObject(),"","",1,true);return;
        }
        try{
            if(base.length()>256*1024)throw new IOException("Legacy icons state size");
            JSONObject saved=new JSONObject(new String(stateFile.readFully(),Charsets.UTF_8));
            JSONObject records=saved.getJSONObject("records");
            String pkg=saved.optString("pkg"),key=saved.optString("key");
            State loaded=new State(records,pkg,key,1,true);
            if(!loaded.inFlight()){state=loaded;return;}
            if(!valid(pkg,key))throw new IOException("Legacy icon trial identity");
            State recovered=finish(loaded,pkg,key,SKIPPED);
            // This is not a crash diagnosis: Android 4.x cannot tell why that process stopped.
            if(!write(recovered))return;
            state=recovered;log(pkg,"previous_trial_not_confirmed");
        }catch(IOException|JSONException|RuntimeException failure){
            DiagnosticLog.info("icons.legacy","stage=state_unavailable");
        }
    }

    boolean confirmed(String pkg,String key){State s=state;return s.available&&VERIFIED.equals(s.status(pkg,key));}
    boolean allowed(String pkg,String key){State s=state;return s.available&&(s.trial(pkg,key)||VERIFIED.equals(s.status(pkg,key)));}
    boolean trial(String pkg,String key){return state.trial(pkg,key);}
    boolean eligible(String pkg,String key){
        State s=state;return s.available&&!s.inFlight()&&valid(pkg,key)&&s.status(pkg,key).isEmpty();
    }
    String signature(){return Integer.toString(state.version);}

    /** Durable marker first. Returning false leaves the original icon on screen. */
    synchronized boolean offer(String pkg,String key){
        if(!eligible(pkg,key))return false;
        State old=state,next=new State(old.records,pkg,key,old.version+1,true);
        if(!write(next)){
            // Do not retry a failing disk write on every visible tile frame.
            state=new State(old.records,"","",next.version,false);
            return false;
        }
        state=next;log(pkg,"trial_started");return true;
    }

    synchronized boolean completed(String pkg,String key,boolean success){
        State old=state;if(!old.trial(pkg,key))return false;
        try{
            State next=finish(old,pkg,key,success?VERIFIED:SKIPPED);
            if(!write(next)){
                // Stop using the unconfirmed bitmap even if persistence is unavailable.
                state=new State(next.records,"","",next.version,false);
                return true;
            }
            state=next;log(pkg,success?"draw_callback_returned":"using_original");return true;
        }catch(JSONException invalid){return false;}
    }

    synchronized boolean cancelUnshown(String pkg,String key){
        State old=state;if(!old.trial(pkg,key))return false;
        State next=new State(old.records,"","",old.version+1,true);
        if(!write(next)){
            state=new State(old.records,"","",next.version,false);
            return true;
        }
        state=next;log(pkg,"trial_cancelled");return true;
    }

    private static State finish(State old,String pkg,String key,String result)throws JSONException{
        JSONObject records=new JSONObject(old.records.toString());
        records.put(pkg,new JSONObject().put("key",key).put("status",result));
        return new State(records,"","",old.version+1,true);
    }

    private boolean write(State next){
        FileOutputStream output=null;
        try{
            byte[] bytes=new JSONObject().put("records",next.records).put("pkg",next.pkg).put("key",next.key)
                .toString().getBytes(Charsets.UTF_8);
            if(bytes.length>256*1024)return false;
            output=stateFile.startWrite();output.write(bytes);stateFile.finishWrite(output);output=null;
            return Arrays.equals(bytes,stateFile.readFully());
        }catch(IOException|JSONException|RuntimeException failure){
            if(output!=null)stateFile.failWrite(output);
            DiagnosticLog.info("icons.legacy","stage=save_unavailable");return false;
        }
    }

    private static boolean valid(String pkg,String key){
        return AppCoverStore.packageName(pkg)&&key!=null&&key.matches(KEY_PATTERN);
    }

    private static void log(String pkg,String stage){
        DiagnosticLog.info("icons.legacy","app="+DiagnosticLog.anonymous("app",pkg)+" stage="+stage);
    }
}
