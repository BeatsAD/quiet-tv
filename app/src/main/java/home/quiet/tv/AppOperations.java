package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Every mutation is scoped to one validated package and the current user, with a durable journal. */
final class AppOperations {
    private static final ReentrantLock MUTATION=new ReentrantLock();
    static void begin() throws IOException {if(!MUTATION.tryLock())throw new IOException("已有应用管理操作正在执行，请稍候");}
    static void end(){MUTATION.unlock();}
    enum Action {
        ENABLE("启用"), DISABLE("禁用"), UNINSTALL("卸载"), RESTORE("恢复安装");
        final String label;Action(String label){this.label=label;}
    }
    private final Context context;
    private final AppRepository repository;
    private final SetupPresets presets;
    AppOperations(Context context){this(context,SetupPresets.load(context));}
    AppOperations(Context context,SetupPresets presets){this.context=context;repository=new AppRepository(context);this.presets=presets;}
    String protection(AppRepository.App app){
        if(app.pkg.equals(context.getPackageName()) || app.pkg.equals("home.quiet.tv.test"))return "Quiet TV 与管理验证组件只读";
        if(home.quiet.proxy.ProxyContract.isPackage(app.pkg))return "HOME 代理请先恢复系统原桌面，再通过系统设置处理";
        if(new HomeGuardState(context).protects(app.pkg,presets))return "此原桌面或依赖必须保留";
        if(app.system && presets.app(app.pkg)==null)return "关键组件或用途未确认的预装包只读";
        if(isHome(app.pkg))return "桌面组件请通过首次设置与恢复原桌面处理";
        return "";
    }
    private boolean isHome(String pkg){
        if(presets.home().startsWith(pkg+"/"))return true;
        for(android.content.pm.ResolveInfo value:context.getPackageManager().queryIntentActivities(HomeController.homeIntent(),HomeController.METADATA))
            if(value.activityInfo!=null && pkg.equals(value.activityInfo.packageName))return true;
        return false;
    }
    List<Action> actions(AppRepository.App app){
        ArrayList<Action> actions=new ArrayList<>();if(!protection(app).isEmpty() || !app.verified || !app.metadataKnown)return actions;
        if(presets.bootAd(app.pkg) && !presets.bootAdMatches(app.pkg,app.versionCode))return actions;
        if(app.installed){actions.add(app.enabled?Action.DISABLE:Action.ENABLE);actions.add(Action.UNINSTALL);}
        else if(app.recoverable)actions.add(Action.RESTORE);
        for(Iterator<Action> it=actions.iterator();it.hasNext();){Action action=it.next();if(!app.commands.supports(action) || ((app.system || presets.bootAd(app.pkg)) && !presets.allows(app.pkg,action.name())) || (presets.bootAd(app.pkg) && action==Action.DISABLE && (app.enabledSetting!=0 && app.enabledSetting!=1 || app.enabledSetting==0 && !app.commands.defaultState)))it.remove();}
        return actions;
    }
    String explanation(AppRepository.App app,Action action){
        if(action==Action.DISABLE)return "仅禁用当前用户下的此应用，保留安装与数据；可以重新启用。";
        if(action==Action.ENABLE)return "重新启用当前用户下的此应用。";
        if(action==Action.RESTORE)return "系统仍保留安装包，将为当前用户恢复安装；此前删除的数据不会恢复。";
        return app.system?"为当前用户卸载此预装应用，删除该用户的应用数据；不删除系统分区安装包。系统保留安装包时可恢复安装，但不能恢复数据。":"卸载当前用户下的此应用，并删除该用户的应用数据。再次使用需要重新安装，数据不能恢复。";
    }
    String perform(AdbController adb,AppRepository.App confirmed,Action action) throws Exception {
        return perform(adb,confirmed,action,"",null,false);
    }
    String perform(AdbController adb,AppRepository.App confirmed,Action action,String runId,SetupOperations.Ticket ticket,boolean original) throws Exception {
        return perform(adb,confirmed,action,runId,ticket,original,-1);
    }
    String restoreEnabledState(AdbController adb,AppRepository.App confirmed,int setting,String runId,SetupOperations.Ticket ticket) throws Exception {
        if(!presets.bootAdMatches(confirmed.pkg,confirmed.versionCode) || (setting!=0 && setting!=1))throw new IOException("广告服务恢复基线或规则不匹配");
        return perform(adb,confirmed,Action.ENABLE,runId,ticket,false,setting);
    }
    private String perform(AdbController adb,AppRepository.App confirmed,Action action,String runId,SetupOperations.Ticket ticket,boolean original,int restoreSetting) throws Exception {
        begin();try{
            DiagnosticLog.info("app.operation.request",action.name()+" package="+(confirmed.pkg.matches("[A-Za-z0-9_.]+")?confirmed.pkg:"[invalid]"));
            String result=performLocked(adb,confirmed,action,runId,ticket,original,restoreSetting);
            DiagnosticLog.info("app.operation.verified",action.name());return result;
        }catch(Exception e){DiagnosticLog.error("app.operation.failed."+action.name(),e);throw e;}finally{end();}
    }
    private String performLocked(AdbController adb,AppRepository.App confirmed,Action action,String runId,SetupOperations.Ticket ticket,boolean original,int restoreSetting) throws Exception {
        if(!adb.connected())throw new IOException("需要连接 ADB");
        if(!confirmed.pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IOException("包名不合法");
        int user=adb.user;
        adb.validateUser();
        AppRepository.App before=repository.find(adb,confirmed.pkg);
        if(before==null || before.installed!=confirmed.installed || before.enabled!=confirmed.enabled || before.system!=confirmed.system || !before.version.equals(confirmed.version) || before.versionCode!=confirmed.versionCode || before.enabledSetting!=confirmed.enabledSetting)throw new IOException("应用状态已变化，请刷新后重新确认");
        if((action==Action.DISABLE || action==Action.UNINSTALL) && home.quiet.proxy.ProxyContract.isPackage(before.pkg))throw new IOException("请先恢复原桌面，不能直接停用 HOME 代理");
        if((action==Action.DISABLE || action==Action.UNINSTALL) && new HomeGuardState(context).protects(before.pkg,presets))throw new IOException("此原桌面或依赖必须保留，不能禁用或卸载");
        if(original){
            HomeController home=new HomeController(context,presets);String target=new SetupStore(context).originalHome();
            if(!before.metadataKnown || !target.equals(presets.home()) || !target.startsWith(before.pkg+"/") || !home.component(target,false))throw new IOException("原桌面身份未确认");
            if(action==Action.DISABLE && before.enabledSetting==0 && !adb.capabilities.defaultState)throw new IOException("当前系统无法精确恢复原桌面的默认启用状态，不执行禁用");
            if(action==Action.DISABLE || action==Action.UNINSTALL){
                if(!presets.homeIdentityMatches(before.versionCode,adb.user))throw new IOException("原桌面版本或用户与已验证规则不符");
                if(!home.inspect(adb).isOwn(context))throw new IOException("Quiet TV 尚未成为实际默认桌面");
                if(!presets.homeAllows(action==Action.DISABLE?"disableVerified":"uninstallVerified"))throw new IOException("当前机型的此项原桌面操作尚未验收");
                if(action==Action.UNINSTALL && !presets.homeAllows("restoreInstallVerified"))throw new IOException("当前机型的原桌面恢复安装尚未验收");
            }else if(action==Action.RESTORE && (!before.recoverable || !presets.homeAllows("restoreInstallVerified")))throw new IOException("原桌面恢复安装尚未验收或系统安装包缺失");
            else if(action==Action.ENABLE && !presets.homeAllows("disableVerified"))throw new IOException("原桌面启用能力尚未验收");
        }else if(!actions(before).contains(action))throw new IOException("此应用不允许执行该操作");
        if(!adb.capabilities.supports(action))throw new IOException("当前系统未确认支持此操作："+action.label);
        if(restoreSetting==0 && !adb.capabilities.defaultState)throw new IOException("当前系统不支持恢复默认启用状态");
        if(ticket!=null)ticket.check();
        String id=UUID.randomUUID().toString();record(id,user,before,action,null,"执行前",runId);
        String output="",failure="";int expectedEnabledSetting=presets.bootAd(before.pkg) && action==Action.DISABLE?3:-1;
        try{
            if(ticket!=null)ticket.check();adb.validateUser();if(ticket!=null)ticket.check();
            String pkg=before.pkg;
            switch(action){
                case ENABLE:
                    JSONObject baseline=original?new SetupStore(context).get().optJSONObject("originalState"):null;
                    boolean defaultState=restoreSetting==0 || baseline!=null && baseline.optInt("enabledSetting",-1)==0;
                    if(defaultState && !adb.capabilities.defaultState)throw new IOException("当前系统不支持恢复默认启用状态");
                    if(original)expectedEnabledSetting=defaultState?0:1;
                    if(restoreSetting>=0)expectedEnabledSetting=restoreSetting;
                    output=adb.shell((defaultState?adb.capabilities.defaultStateCommand:adb.capabilities.enableCommand)+" --user "+user+" "+pkg);break;
                case DISABLE:output=adb.shell(adb.capabilities.disableCommand+" --user "+user+" "+pkg);break;
                case UNINSTALL:output=adb.shell(adb.capabilities.uninstallCommand+" --user "+user+" "+pkg);break;
                case RESTORE:output=adb.shell(adb.capabilities.restore+" --user "+user+" "+pkg);break;
            }
        }catch(Exception e){failure=e.getMessage();}
        AppRepository.App after;
        try{if(!adb.connected())throw new IOException("ADB 已断开");after=repository.find(adb,before.pkg);if(!adb.connected() || after!=null && !after.verified)throw new IOException("未取得本机 ADB 状态核对结果");}catch(Exception e){record(id,user,before,action,null,"结果不明，请重新连接查询；"+failure,runId);throw new IOException("结果不明：请重新连接查询，不要重复操作",e);}
        boolean success=action==Action.UNINSTALL?(after==null || !after.installed):after!=null && after.installed && (action==Action.ENABLE?after.enabled:action==Action.DISABLE?!after.enabled:true);
        if(success && expectedEnabledSetting>=0)success=after.enabledSetting==expectedEnabledSetting;
        String result=success?"已核对实际状态":("未达到目标状态；"+failure+" "+output);
        record(id,user,before,action,after,result,runId);
        if(!success)throw new IOException(result);
        if(action==Action.UNINSTALL)new DockStore(context).remove(before.pkg);
        return action.label+"成功  "+before.pkg;
    }
    private JSONObject state(AppRepository.App app) throws JSONException {
        if(app==null)return null;return new JSONObject().put("installed",app.installed).put("enabled",app.enabled).put("system",app.system).put("version",app.version).put("versionCode",app.versionCode).put("enabledSetting",app.enabledSetting);
    }
    private void record(String id,int user,AppRepository.App before,Action action,AppRepository.App after,String result,String runId) throws Exception {
        JSONObject record=new JSONObject().put("id",id).put("time",System.currentTimeMillis()).put("user",user).put("package",before.pkg)
            .put("runId",runId).put("action",action.name()).put("before",state(before)).put("after",after==null?JSONObject.NULL:state(after)).put("result",result);
        try(FileOutputStream output=new FileOutputStream(new File(context.getFilesDir(),"app-operations.jsonl"),true)){
            output.write((record+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));output.getFD().sync();
        }
    }
    String history() throws IOException {
        File file=new File(context.getFilesDir(),"app-operations.jsonl");if(!file.exists())return "暂无操作记录。记录包含配置状态，不是应用数据备份。";
        StringBuilder result=new StringBuilder("配置记录（不是数据备份）\n\n");
        try(BufferedReader input=new BufferedReader(new InputStreamReader(new FileInputStream(file),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=input.readLine())!=null)try{JSONObject item=new JSONObject(line);result.append(item.optString("package")).append("  ").append(item.optString("action")).append("  用户 ").append(item.optInt("user")).append("\n").append(item.optString("result")).append("\n\n");}catch(JSONException e){result.append("有一条记录不完整，请保留原文件\n");}
        }
        return result.toString();
    }
}
