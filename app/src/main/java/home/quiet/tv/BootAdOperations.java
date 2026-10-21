package home.quiet.tv;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;

/** Advertising changes have their own baseline and do not require changing HOME. */
final class BootAdOperations {
    static final class RestoreItem {
        final String runId;
        final JSONObject baseline;
        final AppRepository.App current;
        RestoreItem(String runId,JSONObject baseline,AppRepository.App current){this.runId=runId;this.baseline=baseline;this.current=current;}
        String pkg(){return baseline.optString("package");}
    }
    final SetupPresets presets;
    private final SetupStore store;
    private final AppRepository repository;
    private final AppOperations operations;
    BootAdOperations(Context context){this(context,SetupPresets.load(context));}
    BootAdOperations(Context context,SetupPresets presets){this.presets=presets;store=new SetupStore(context);repository=new AppRepository(context);operations=new AppOperations(context,presets);}
    String reason(AppRepository.App app,boolean requireConnection){
        if(app==null)return "本机未找到该应用";
        if(!presets.bootAdMatches(app.pkg,app.versionCode))return "当前应用版本尚未适配";
        if(!app.metadataKnown || !app.installed)return "应用未安装或信息不完整";
        String protection=operations.protection(app);if(!protection.isEmpty())return protection;
        if(!app.enabled)return "当前已停用，无需重复操作";
        if(app.enabledSetting!=0 && app.enabledSetting!=1)return "操作前的启用状态无法精确恢复";
        if(!app.verified)return requireConnection?"请先连接本机 ADB":"";
        if(!operations.actions(app).contains(AppOperations.Action.DISABLE))return "当前系统未确认支持停用及精确恢复";
        return "";
    }
    static boolean unchanged(AppRepository.App before,AppRepository.App now){
        return before!=null && now!=null && now.metadataKnown && now.verified && before.pkg.equals(now.pkg) && before.versionCode==now.versionCode && before.system==now.system && before.installed==now.installed && before.enabled==now.enabled && before.enabledSetting==now.enabledSetting;
    }
    static JSONObject baseline(AppRepository.App app) throws JSONException {
        return new JSONObject().put("package",app.pkg).put("versionCode",app.versionCode).put("system",app.system).put("enabledSetting",app.enabledSetting).put("installed",app.installed).put("enabled",app.enabled).put("attempted",false).put("state","pending").put("result","尚未执行");
    }
    static List<RestoreItem> restoreItems(JSONArray runs,List<AppRepository.App> apps,int user,String firmware) throws JSONException {
        Map<String,AppRepository.App> actual=new HashMap<>();for(AppRepository.App app:apps)actual.put(app.pkg,app);
        ArrayList<RestoreItem> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=runs.length()-1;i>=0;i--){JSONObject run=runs.getJSONObject(i);if(run.getInt("user")!=user || !firmware.equals(run.getString("firmware")))continue;
            JSONArray entries=run.getJSONArray("apps");for(int j=0;j<entries.length();j++){JSONObject item=entries.getJSONObject(j);String pkg=item.getString("package");
                if(!seen.add(pkg) || !item.optBoolean("attempted") || "restored".equals(item.optString("state")))continue;
                result.add(new RestoreItem(run.getString("id"),item,actual.get(pkg)));
            }
        }
        return result;
    }
    static String baselineProblem(JSONObject baseline,AppRepository.App now){
        int setting=baseline.optInt("enabledSetting",-1);
        if(!baseline.optBoolean("attempted") || !baseline.optBoolean("enabled") || !baseline.optBoolean("installed") || (setting!=0 && setting!=1))return "缺少本次修改前的有效基线";
        if(now==null || !now.metadataKnown || !now.installed || now.versionCode!=baseline.optLong("versionCode",-1) || now.system!=baseline.optBoolean("system"))return "应用版本、安装状态或类型已变化";
        if(now.enabledSetting==setting && now.enabled)return ""; // Already at the exact baseline: record only, no shell write.
        return !now.enabled && now.enabledSetting==3?"":"应用状态已被其他操作改变，请先核对";
    }
    String restoreReason(RestoreItem item,boolean requireConnection){
        String problem=baselineProblem(item.baseline,item.current);if(!problem.isEmpty())return problem;
        AppRepository.App app=item.current;
        if(!presets.bootAdMatches(app.pkg,app.versionCode))return "当前固件或应用版本的恢复规则不匹配";
        String protection=operations.protection(app);if(!protection.isEmpty())return protection;
        if(!app.verified)return requireConnection?"请先连接本机 ADB":"";
        if(app.enabled && app.enabledSetting==item.baseline.optInt("enabledSetting",-1))return "";
        if(!operations.actions(app).contains(AppOperations.Action.ENABLE) || item.baseline.optInt("enabledSetting",-1)==0 && !app.commands.defaultState)return "当前系统不支持精确恢复原状态";
        return "";
    }
    String disable(AdbController adb,List<AppRepository.App> selected,SetupOperations.Ticket ticket,SetupOperations.Progress progress) throws Exception {
        AppOperations.begin();String id=UUID.randomUUID().toString();boolean begun=false;
        try{
            ticket.check();adb.validateUser();if(selected.isEmpty())throw new IOException("请先选择已适配的广告服务");
            Map<String,AppRepository.App> actual=new HashMap<>();for(AppRepository.App app:repository.load(adb))actual.put(app.pkg,app);
            JSONArray baseline=new JSONArray();Set<String> seen=new HashSet<>();
            for(AppRepository.App app:selected){AppRepository.App now=actual.get(app.pkg);if(!seen.add(app.pkg) || !unchanged(app,now) || !reason(now,true).isEmpty())throw new IOException("应用状态或适配资格已变化，请刷新后重新选择");baseline.put(baseline(now));}
            store.bootBegin(id,adb.user,baseline);begun=true;
            for(AppRepository.App app:selected){
                ticket.check();progress.changed("正在停用  "+app.label);
                store.bootItem(id,app.pkg,"unknown",true,"即将执行；若中断，请重新查询实际状态");
                try{operations.perform(adb,app,AppOperations.Action.DISABLE,id,ticket,false);store.bootItem(id,app.pkg,"disabled",true,"已查询确认广告服务停用；实际开机效果待验证");}
                catch(Exception e){store.bootItem(id,app.pkg,"unknown",true,"未完成或结果未确认，请重新连接查询");throw e;}
            }
            ticket.check();store.bootEnd(id,"complete");return "所选服务已停用，实际开机效果待验证";
        }catch(Exception e){if(begun)store.bootEnd(id,ticket.active()?"incomplete":"cancelled");throw e;}
        finally{AppOperations.end();}
    }
    String restore(AdbController adb,List<RestoreItem> selected,SetupOperations.Ticket ticket,SetupOperations.Progress progress) throws Exception {
        AppOperations.begin();try{
            ticket.check();adb.validateUser();if(selected.isEmpty())throw new IOException("请先选择需要恢复的记录");
            List<RestoreItem> latest=restoreItems(store.bootRuns(),repository.load(adb),adb.user,android.os.Build.FINGERPRINT);
            Map<String,RestoreItem> actual=new HashMap<>();for(RestoreItem item:latest)actual.put(item.runId+":"+item.pkg(),item);
            ArrayList<RestoreItem> confirmed=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(RestoreItem chosen:selected){RestoreItem now=actual.get(chosen.runId+":"+chosen.pkg());if(now==null || !seen.add(now.pkg()) || !unchanged(chosen.current,now.current) || !restoreReason(now,true).isEmpty())throw new IOException("恢复记录或应用状态已变化，请刷新后重新选择");confirmed.add(now);}
            for(RestoreItem item:confirmed){
                ticket.check();progress.changed("正在恢复  "+item.current.label);
                int setting=item.baseline.getInt("enabledSetting");
                if(!item.current.enabled || item.current.enabledSetting!=setting){
                    store.bootItem(item.runId,item.pkg(),"unknown",true,"正在恢复；若中断，请重新查询实际状态");
                    try{operations.restoreEnabledState(adb,item.current,setting,item.runId,ticket);}
                    catch(Exception e){store.bootItem(item.runId,item.pkg(),"unknown",true,"恢复未完成或结果未确认，请重新连接查询");throw e;}
                }
                store.bootItem(item.runId,item.pkg(),"restored",true,"已核对并恢复到本次操作前的启用状态");
            }
            return "所选项目已恢复到操作前状态";
        }finally{AppOperations.end();}
    }
}
