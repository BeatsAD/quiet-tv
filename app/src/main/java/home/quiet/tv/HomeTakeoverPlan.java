package home.quiet.tv;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** Finite, reversible HOME changes. The journal is evidence, never a background retry queue. */
final class HomeTakeoverPlan {
    static final String GOOGLE="com.google.android.apps.tv.launcherx", SETUP="com.google.android.tungsten.setupwraith";
    static final String CLASSIC="com.google.android.tvlauncher", JM="persist.sys.home.selectpkg", VIDAA="persist.vt.use_home_pkg";
    static final String ADB_PORT="persist.adb.tcp.port", WRITE_SECURE="android.permission.WRITE_SECURE_SETTINGS";
    static boolean adbPort(String value){try{return value.matches("[0-9]{1,5}") && Integer.parseInt(value)>0 && Integer.parseInt(value)<=65535;}catch(NumberFormatException invalid){return false;}}
    static boolean adbPortState(String value){return value.isEmpty() || "-1".equals(value) || "0".equals(value) || adbPort(value);}
    interface Port {
        void check() throws Exception;
        boolean rollbackAllowed();
        String read(JSONObject step) throws Exception;
        void write(JSONObject step,String value) throws Exception;
        void save(JSONObject journal) throws Exception;
    }
    static List<String> competitors(String original,boolean setupHome){
        if(GOOGLE.equals(original))return setupHome?Arrays.asList(SETUP,GOOGLE):Collections.singletonList(GOOGLE);
        return CLASSIC.equals(original)?Collections.singletonList(CLASSIC):Collections.emptyList();
    }
    static boolean property(String value){return JM.equals(value) || VIDAA.equals(value);}
    static boolean packageName(String value){return value!=null && value.matches("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");}
    static boolean applied(JSONObject journal,Port port) throws Exception {
        JSONArray steps=journal.getJSONArray("steps");
        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);
            // Turning off optional ADB recovery does not invalidate the completed HOME transaction.
            if(!"preference".equals(step.getString("kind")) && !step.getString("after").equals(port.read(step)))return false;
        }
        return true;
    }
    static void apply(JSONObject journal,Port port) throws Exception {
        JSONArray steps=journal.getJSONArray("steps");
        port.check();
        for(int i=0;i<steps.length();i++){
            JSONObject step=steps.getJSONObject(i);
            if(!step.optString("observed",step.getString("before")).equals(port.read(step)))throw new IOException("桌面状态已变化，请重新检测");
        }
        boolean writeAttempted=false;
        try{
            journal.put("state","running");port.save(journal);
            for(int i=0;i<steps.length();i++){
                port.check();JSONObject step=steps.getJSONObject(i);
                // Package changes invalidate HOME preferences; that final step is deliberately reapplied.
                if(!"home".equals(step.getString("kind")) && !step.getString("before").equals(port.read(step)))throw new IOException("桌面状态已变化");
                step.put("attempted",true);port.save(journal);
                writeAttempted=true;
                port.write(step,step.getString("after"));
                if(!step.getString("after").equals(port.read(step)))throw new IOException("桌面操作未读回："+step.getString("key"));
                step.put("verified",true);port.save(journal);
            }
            port.check();journal.put("state","applied");port.save(journal);
        }catch(Exception failure){
            journal.put("state",writeAttempted?"incomplete":"rolled-back");
            try{port.save(journal);}catch(Exception save){failure.addSuppressed(save);}
            if(writeAttempted && port.rollbackAllowed())try{restore(journal,port,false);}catch(Exception rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
    }
    static void restore(JSONObject journal,Port port) throws Exception {
        restore(journal,port,true);
    }
    private static void restore(JSONObject journal,Port port,boolean original) throws Exception {
        JSONArray steps=journal.getJSONArray("steps");
        // Restore the real launcher before a competing recovery HOME, then restore the preference.
        for(int i=steps.length()-1;i>=0;i--){JSONObject step=steps.getJSONObject(i);String kind=step.getString("kind");if(!"home".equals(kind) && !"permission".equals(kind))restoreStep(step,port,original);}
        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);if("home".equals(step.getString("kind")))restoreStep(step,port,original);}
        // Revoking a development permission may stop this process: restore HOME first.
        for(int i=steps.length()-1;i>=0;i--){JSONObject step=steps.getJSONObject(i);if("permission".equals(step.getString("kind")))restoreStep(step,port,original);}
        journal.put("state",original?"restored":"rolled-back");port.save(journal);
    }
    private static void restoreStep(JSONObject step,Port port,boolean original) throws Exception {
        if(!step.optBoolean("attempted") && !"home".equals(step.getString("kind")))return;
        port.check();String actual=port.read(step),before=step.getString("before");
        if(original && "home".equals(step.getString("kind")))before=step.optString("restore",before);
        if(before.equals(actual) && !"home".equals(step.getString("kind")))return;
        if(!"home".equals(step.getString("kind")) && !step.getString("after").equals(actual))throw new IOException("状态被其他操作改变，停止覆盖："+step.getString("key"));
        port.write(step,before);
        if(!before.equals(port.read(step)))throw new IOException("恢复未读回："+step.getString("key"));
    }
    private HomeTakeoverPlan(){}
}
