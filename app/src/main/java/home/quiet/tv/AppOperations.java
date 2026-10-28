package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.json.*;

/** Fixed current-user operations; device rules are supplied by computer configuration. */
final class AppOperations {
    private final Context context;
    private final SetupPresets presets;
    AppOperations(Context context){this(context,SetupPresets.load(context));}
    AppOperations(Context context,SetupPresets presets){this.context=context;this.presets=presets;}
    String protection(AppRepository.App app){
        if(Arrays.asList("android","com.android.systemui","com.android.settings","com.android.tv.settings","com.android.packageinstaller","com.google.android.packageinstaller","com.android.permissioncontroller","com.google.android.permissioncontroller").contains(app.pkg))return "系统核心组件只读";
        if(app.pkg.equals(context.getPackageName()) || app.pkg.equals("home.quiet.tv.test"))return "Quiet TV 与管理验证组件只读";
        if(home.quiet.proxy.ProxyContract.isPackage(app.pkg))return "HOME 代理请先恢复系统原桌面，再通过系统设置处理";
        if(new HomeGuardState(context,false).protects(app.pkg,presets))return "此原桌面或依赖必须保留";
        if(app.system && presets.app(app.pkg)==null)return "关键组件或用途未确认的预装包只读";
        if(isHome(app.pkg))return "桌面组件请通过桌面配置与恢复原桌面处理";
        return "";
    }
    private boolean isHome(String pkg){
        if(presets.home().startsWith(pkg+"/"))return true;
        for(android.content.pm.ResolveInfo value:context.getPackageManager().queryIntentActivities(HomeController.homeIntent(),HomeController.METADATA))
            if(value.activityInfo!=null && pkg.equals(value.activityInfo.packageName))return true;
        return false;
    }
    enum Action {
        ENABLE("重新启用"), DISABLE("禁用"), UNINSTALL("卸载"), RESTORE("恢复安装");
        final String label;Action(String value){label=value;}
    }
    private static final ReentrantLock MUTATION=new ReentrantLock();
    List<Action> actions(AppRepository.App app){
        ArrayList<Action> result=new ArrayList<>();
        if(!protection(app).isEmpty() || !app.verified || !app.metadataKnown)return result;
        if(presets.bootAd(app.pkg) && !presets.bootAdMatches(app.pkg,app.versionCode))return result;
        if(app.installed){
            if(app.enabled && (app.enabledSetting==0 || app.enabledSetting==1))result.add(Action.DISABLE);
            else if(!app.enabled && app.enabledSetting==3)result.add(Action.ENABLE);
            result.add(Action.UNINSTALL);
        }else if(app.recoverable)result.add(Action.RESTORE);
        for(Iterator<Action> it=result.iterator();it.hasNext();){
            Action action=it.next();
            if(!app.commands.supports(action) || app.system && !presets.allows(app.pkg,action.name())
                || action==Action.DISABLE && (!app.commands.defaultState && app.enabledSetting==0 || app.system && !presets.allows(app.pkg,"ENABLE"))
                || action==Action.UNINSTALL && app.system && (!app.recoverable || app.commands.restore.isEmpty() || !presets.allows(app.pkg,"RESTORE")))it.remove();
        }
        return result;
    }
    String explanation(AppRepository.App app,Action action){
        if(action==Action.DISABLE)return "仅禁用当前用户下的此应用，保留安装与数据；可恢复原启用状态。";
        if(action==Action.ENABLE)return "重新启用此应用；有本机禁用记录时恢复记录中的启用状态。";
        if(action==Action.RESTORE)return "系统仍保留安装包，将为当前用户恢复安装。此前删除的数据不会恢复。";
        return "仅为当前用户卸载，应用数据将删除。"+(app.system?"系统保留的安装包可以恢复安装，但不能恢复已删除的数据。":"再次使用需要重新安装。");
    }
    String perform(AdbController adb,AppRepository.App confirmed,Action action) throws Exception {
        if(!MUTATION.tryLock())throw new IOException("已有应用操作正在执行");
        try{
            if(!confirmed.pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IOException("包名无效");
            adb.validateUser();int user=adb.user;
            AppRepository repository=new AppRepository(context);
            AppRepository.App before=repository.find(adb,confirmed.pkg);
            if(!same(confirmed,before))throw new IOException("应用状态已变化，请刷新后重新确认");
            // Rules may have changed since the list or confirmation dialog was opened.
            if(!new AppOperations(context).actions(before).contains(action))throw new IOException("本机规则或系统能力不允许此操作");
            int enabled=1;
            JSONObject baseline=baseline(before,user,Action.DISABLE);
            if(action==Action.ENABLE && baseline!=null)enabled=baseline.getInt("enabledSetting");
            if(enabled!=0 && enabled!=1)throw new IOException("恢复记录中的启用状态无效");
            if(action==Action.ENABLE && enabled==0 && !adb.capabilities.defaultState)throw new IOException("系统无法恢复原默认状态");
            String command=action==Action.DISABLE?adb.capabilities.disableCommand:action==Action.ENABLE?(enabled==0?adb.capabilities.defaultStateCommand:adb.capabilities.enableCommand):action==Action.UNINSTALL?adb.capabilities.uninstallCommand:adb.capabilities.restore;
            String id=UUID.randomUUID().toString();record(id,user,before,action,null,"执行前");
            adb.validateUser();String failure="";
            try{adb.shell(command+" --user "+user+" "+before.pkg);}catch(Exception e){failure=e.getMessage()==null?e.toString():e.getMessage();}
            AppRepository.App after;
            try{adb.validateUser();after=repository.find(adb,before.pkg);}
            catch(Exception e){record(id,user,before,action,null,"结果不明");throw new IOException("连接中断，结果未确认。请重新连接查询，不要重复操作",e);}
            boolean success=action==Action.UNINSTALL?(before.system?after!=null && after.verified && after.system && after.recoverable && !after.installed:after==null || !after.installed):after!=null && after.verified && after.installed
                && after.versionCode==before.versionCode && (action==Action.DISABLE?after.enabledSetting==3:action==Action.ENABLE?after.enabledSetting==enabled:after.system);
            record(id,user,before,action,after,success?"已核对实际状态":"未达到目标状态");
            if(!success)throw new IOException("操作未完成："+failure);
            if(!failure.isEmpty())throw new IOException("已观察到目标状态，但命令报错；请核对记录，不要重复操作");
            return action.label+"成功";
        }finally{MUTATION.unlock();}
    }
    static boolean same(AppRepository.App a,AppRepository.App b){return a!=null && b!=null && b.verified && b.metadataKnown && a.pkg.equals(b.pkg) && a.versionCode==b.versionCode && a.system==b.system && a.installed==b.installed && a.enabledSetting==b.enabledSetting;}
    private JSONObject baseline(AppRepository.App app,int user,Action action) throws Exception {
        File file=new File(context.getFilesDir(),"app-operations.jsonl");if(!file.exists())return null;
        JSONObject found=null;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){
            String line;while((line=reader.readLine())!=null){
                JSONObject row;try{row=new JSONObject(line);}catch(JSONException incomplete){continue;}
                if(row.optInt("user",-1)!=user || !app.pkg.equals(row.optString("package")) || !action.name().equals(row.optString("action")))continue;
                // Historical rows lack firmware identity and remain readable, but are not guessed baselines.
                if(!Build.FINGERPRINT.equals(row.optString("fingerprint")))continue;
                JSONObject before=row.optJSONObject("before");
                if(before!=null && before.optLong("versionCode",-1)==app.versionCode)found=before;
            }
        }
        return found;
    }
    private JSONObject state(AppRepository.App app) throws JSONException {
        return app==null?null:new JSONObject().put("installed",app.installed).put("enabled",app.enabled).put("system",app.system).put("version",app.version).put("versionCode",app.versionCode).put("enabledSetting",app.enabledSetting);
    }
    private void record(String id,int user,AppRepository.App before,Action action,AppRepository.App after,String result) throws Exception {
        JSONObject row=new JSONObject().put("id",id).put("time",System.currentTimeMillis()).put("user",user).put("fingerprint",Build.FINGERPRINT)
            .put("package",before.pkg).put("action",action.name()).put("before",state(before)).put("after",after==null?JSONObject.NULL:state(after)).put("result",result);
        try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"app-operations.jsonl"),true)){out.write((row+"\n").getBytes("UTF-8"));out.getFD().sync();}
    }
    String history() throws IOException {
        File file=new File(context.getFilesDir(),"app-operations.jsonl");if(!file.exists())return "暂无操作记录";
        ArrayDeque<String> rows=new ArrayDeque<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){String line;while((line=reader.readLine())!=null)try{JSONObject row=new JSONObject(line);rows.add(row.optString("package")+"  "+row.optString("action")+"  "+row.optString("result"));if(rows.size()>30)rows.removeFirst();}catch(JSONException ignored){}}
        StringBuilder text=new StringBuilder("最近操作（不是应用数据备份）\n");for(String row:rows)text.append('\n').append(row);return text.toString();
    }
}
