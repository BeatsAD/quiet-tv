package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;

/** Every mutation is scoped to one validated package and the current user, with a durable journal. */
final class AppOperations {
    enum Action {
        ENABLE("启用"), DISABLE("禁用"), UNINSTALL("卸载"), RESTORE("恢复安装");
        final String label;Action(String label){this.label=label;}
    }
    private static final String SONY_FIRMWARE="Sony/BRAVIA_VH1_CN/BRAVIA_VH1:12/STT2.230505.001.S137/626241:user/release-keys";
    // Only packages previously investigated on this exact firmware. Unknown system packages remain read-only.
    private static final Set<String> MANAGED_SYSTEM=new HashSet<>(Arrays.asList(
        "com.sonyericsson.idd.agent","com.sony.dtv.sonybugreportsys","com.sony.dtv.sonyloglevelsettingsys",
        "com.sony.dtv.da.service","com.qterics.da.product","com.sony.dtv.braviashow","com.sony.dtv.demosupport",
        "com.sony.dtv.demosystemsupport","com.sony.dtv.multiscreendemo","com.sony.dtv.voicesearch",
        "com.jd.smartservicetwo","com.ktcp.tvvideo","com.newtv.sport","com.sony.dtv.osat.music",
        "com.sony.dtv.smartmediaapp","com.sony.dtv.timers","com.sony.dtv.mysony","com.sony.dtv.livingfit",
        "com.sony.dtv.smarthelp","com.sony.dtv.helpguidecontents"));
    private final Context context;
    private final AppRepository repository;
    AppOperations(Context context){this.context=context;repository=new AppRepository(context);}
    String protection(AppRepository.App app){
        if(app.pkg.equals(context.getPackageName()) || app.pkg.equals("home.quiet.tv.test"))return "Quiet TV 与管理验证组件只读";
        if(app.system && (!SONY_FIRMWARE.equals(Build.FINGERPRINT) || !MANAGED_SYSTEM.contains(app.pkg)))return "关键组件或用途未确认的预装包只读";
        return "";
    }
    List<Action> actions(AppRepository.App app){
        ArrayList<Action> actions=new ArrayList<>();if(!protection(app).isEmpty() || !app.verified)return actions;
        if(app.installed){actions.add(app.enabled?Action.DISABLE:Action.ENABLE);actions.add(Action.UNINSTALL);}
        else if(app.recoverable)actions.add(Action.RESTORE);
        return actions;
    }
    String explanation(AppRepository.App app,Action action){
        if(action==Action.DISABLE)return "仅禁用当前用户下的此应用，保留安装与数据；可以重新启用。";
        if(action==Action.ENABLE)return "重新启用当前用户下的此应用。";
        if(action==Action.RESTORE)return "系统仍保留安装包，将为当前用户恢复安装；此前删除的数据不会恢复。";
        return app.system?"为当前用户卸载此预装应用，删除该用户的应用数据；不删除系统分区安装包。系统保留安装包时可恢复安装，但不能恢复数据。":"卸载当前用户下的此应用，并删除该用户的应用数据。再次使用需要重新安装，数据不能恢复。";
    }
    String perform(AdbController adb,AppRepository.App confirmed,Action action) throws Exception {
        if(!adb.connected())throw new IOException("需要连接 ADB");
        if(!confirmed.pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IOException("包名不合法");
        int user=adb.user;
        if(!adb.shell("am get-current-user").trim().equals(String.valueOf(user)))throw new IOException("当前用户已改变，请重新连接");
        AppRepository.App before=repository.find(adb,confirmed.pkg);
        if(before==null || before.installed!=confirmed.installed || before.enabled!=confirmed.enabled || before.system!=confirmed.system || !before.version.equals(confirmed.version))throw new IOException("应用状态已变化，请刷新后重新确认");
        if(!actions(before).contains(action))throw new IOException("此应用不允许执行该操作");
        String id=UUID.randomUUID().toString();record(id,user,before,action,null,"执行前");
        String output="",failure="";
        try{
            String pkg=before.pkg;
            switch(action){
                case ENABLE:output=adb.shell("pm enable --user "+user+" "+pkg);break;
                case DISABLE:output=adb.shell("pm disable-user --user "+user+" "+pkg);break;
                case UNINSTALL:output=adb.shell("pm uninstall --user "+user+" "+pkg);break;
                case RESTORE:output=adb.shell("cmd package install-existing --user "+user+" "+pkg);break;
            }
        }catch(Exception e){failure=e.getMessage();}
        AppRepository.App after;
        try{after=repository.find(adb,before.pkg);}catch(Exception e){record(id,user,before,action,null,"结果不明，请重新连接查询；"+failure);throw new IOException("结果不明：请重新连接查询，不要重复操作",e);}
        boolean success=action==Action.UNINSTALL?(after==null || !after.installed):after!=null && after.installed && (action==Action.ENABLE?after.enabled:action==Action.DISABLE?!after.enabled:true);
        String result=success?"已核对实际状态":("未达到目标状态；"+failure+" "+output);
        record(id,user,before,action,after,result);
        if(!success)throw new IOException(result);
        if(action==Action.UNINSTALL)new DockStore(context).remove(before.pkg);
        return action.label+"成功 · "+before.pkg;
    }
    private JSONObject state(AppRepository.App app) throws JSONException {
        if(app==null)return null;return new JSONObject().put("installed",app.installed).put("enabled",app.enabled).put("system",app.system).put("version",app.version);
    }
    private void record(String id,int user,AppRepository.App before,Action action,AppRepository.App after,String result) throws Exception {
        JSONObject record=new JSONObject().put("id",id).put("time",System.currentTimeMillis()).put("user",user).put("package",before.pkg)
            .put("action",action.name()).put("before",state(before)).put("after",after==null?JSONObject.NULL:state(after)).put("result",result);
        try(FileOutputStream output=new FileOutputStream(new File(context.getFilesDir(),"app-operations.jsonl"),true)){
            output.write((record+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));output.getFD().sync();
        }
    }
    String history() throws IOException {
        File file=new File(context.getFilesDir(),"app-operations.jsonl");if(!file.exists())return "暂无操作记录。记录包含配置状态，不是应用数据备份。";
        StringBuilder result=new StringBuilder("配置记录（不是数据备份）\n\n");
        try(BufferedReader input=new BufferedReader(new InputStreamReader(new FileInputStream(file),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=input.readLine())!=null)try{JSONObject item=new JSONObject(line);result.append(item.optString("package")).append(" · ").append(item.optString("action")).append(" · 用户 ").append(item.optInt("user")).append("\n").append(item.optString("result")).append("\n\n");}catch(JSONException e){result.append("有一条记录不完整，请保留原文件\n");}
        }
        return result.toString();
    }
}
