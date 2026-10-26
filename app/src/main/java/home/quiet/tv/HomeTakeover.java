package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Foreground-only upstream recipes, selected from installed capabilities, not brand-wide disable lists. */
final class HomeTakeover {
    private final Context context;
    private final HomeController home;
    private final SetupStore store;
    private final AppRepository apps;
    HomeTakeover(Context context){this.context=context;home=new HomeController(context);store=new SetupStore(context);apps=new AppRepository(context);}
    boolean pending() throws Exception {JSONObject record=store.get().optJSONObject("takeover");return record!=null && !"restored".equals(record.optString("state")) && !"rolled-back".equals(record.optString("state"));}
    boolean applied(AdbController adb,SetupOperations.Ticket task) throws Exception {
        JSONObject record=store.get().optJSONObject("takeover");if(record==null || !"applied".equals(record.optString("state")))return false;
        validate(record,adb);return HomeTakeoverPlan.applied(record,port(adb,task));
    }
    boolean keepAdbAvailable(AdbController adb){return home.presets.homeAllows("keepAdb") && HomeGuardAdb.supported(context) && Build.VERSION.SDK_INT<=29 && adb.user==0 && HomeTakeoverPlan.adbPort(Integer.toString(adb.connectedPort()));}
    JSONObject inspect(AdbController adb) throws Exception {return inspect(adb,false);}
    JSONObject inspect(AdbController adb,boolean keepAdb) throws Exception {
        if(!BuildConfig.DECLARES_HOME || BuildConfig.LEGACY_19 || pending())return null;
        adb.validateUser();HomeController.State current=home.inspect(adb);
        if(!current.ownUsable || adb.capabilities.home.isEmpty() || adb.capabilities.resolve.isEmpty())return null;
        String original=current.original;
        if(original.isEmpty() && current.known && !current.isOwn(context) && !current.isProxy(context))original=current.actual;
        if(original.isEmpty())original=new HomeGuardState(context).selected();
        if(original.startsWith(HomeTakeoverPlan.SETUP+"/")){
            List<ResolveInfo> launchers=context.getPackageManager().queryIntentActivities(HomeController.homeIntent().setPackage(HomeTakeoverPlan.GOOGLE),0);
            if(launchers.size()!=1 || launchers.get(0).activityInfo==null)return null;
            ActivityInfo info=launchers.get(0).activityInfo;original=new ComponentName(info.packageName,info.name).flattenToString();
        }
        ComponentName previous=ComponentName.unflattenFromString(original);
        if(previous==null || !home.component(original,true))return null;
        AppRepository.App old=apps.find(adb,previous.getPackageName());
        if(old==null || !old.system || !old.installed || !old.enabled || !old.verified)return null;
        String own=new ComponentName(context,HomeActivity.class).flattenToString();
        JSONArray steps=new JSONArray();String recipe="",title="";
        // These firmware properties are global, so offer them only to the primary Android user.
        if(adb.user==0 && hasJm()){
            String value=adb.shell("getprop "+HomeTakeoverPlan.JM).trim();
            if(validPropertyValue(value) && !context.getPackageName().equals(value)){
                recipe="jm-property";title="修复遥控器主页路由";steps.put(step("property",HomeTakeoverPlan.JM,value,context.getPackageName()));
            }
        }else if(adb.user==0 && "com.jamdeo.tv.vod".equals(old.pkg)){
            String value=adb.shell("getprop "+HomeTakeoverPlan.VIDAA).trim();
            if(HomeTakeoverPlan.packageName(value) && !context.getPackageName().equals(value)){
                recipe="vidaa-property";title="修复海信主页路由";steps.put(step("property",HomeTakeoverPlan.VIDAA,value,context.getPackageName()));
            }
        }
        if(steps.length()==0){
            if(home.presets.protectedHomePackage(old.pkg) || new HomeGuardState(context).helperActive())return null;
            if(Settings.Global.getInt(context.getContentResolver(),Settings.Global.DEVICE_PROVISIONED,0)!=1)return null;
            List<String> packages=HomeTakeoverPlan.competitors(old.pkg,claims(HomeTakeoverPlan.SETUP));
            boolean verifiedProfile=home.presets.homeAllows("disableVerified") && home.presets.homeIdentityMatches(old.versionCode,adb.user) && original.equals(home.presets.home());
            if(packages.isEmpty() && verifiedProfile)packages=Collections.singletonList(old.pkg);
            if(packages.isEmpty() || !adb.capabilities.disable || !adb.capabilities.enable)return null;
            for(String pkg:packages){
                AppRepository.App app=apps.find(adb,pkg);
                if(app==null || !app.verified || !app.system || !app.installed || !app.enabled || !claims(pkg)
                        || app.enabledSetting!=0 && app.enabledSetting!=1 || app.enabledSetting==0 && !adb.capabilities.defaultState)return null;
                steps.put(step("package",pkg,Integer.toString(app.enabledSetting),"3").put("version",app.version).put("versionCode",app.versionCode));
            }
            recipe=verifiedProfile?"verified-profile":"google-home";title="尝试直接接管桌面";
        }
        if(keepAdb){
            if(!keepAdbAvailable(adb) || !"verified-profile".equals(recipe))throw new IOException("当前直接接管方案未开放 ADB 常开");
            if(!"1".equals(adb.shell("settings get global adb_enabled").trim()))throw new IOException("系统调试开关未确认开启，不能配置常开");
            JSONArray combined=new JSONArray();
            if(!HomeGuardRecovery.writeAllowed(context)){
                String help=ShellCapabilities.permissionHelp("pm",adb::shell);
                String grantUser=ShellCapabilities.userOption(help,"grant",Build.VERSION.SDK_INT,adb.user);
                String revokeUser=ShellCapabilities.userOption(help,"revoke",Build.VERSION.SDK_INT,adb.user);
                combined.put(step("permission",HomeTakeoverPlan.WRITE_SECURE,"denied","granted").put("grantUser",grantUser).put("revokeUser",revokeUser));
            }
            String before=adb.shell("getprop "+HomeTakeoverPlan.ADB_PORT).trim();
            if(!HomeTakeoverPlan.adbPortState(before))throw new IOException("原 ADB 端口属性无法核对，未修改");
            combined.put(step("property",HomeTakeoverPlan.ADB_PORT,before,Integer.toString(adb.connectedPort())));
            combined.put(step("preference","keepAdb",Boolean.toString(HomeGuardAdb.enabled(context)),"true"));
            for(int i=0;i<steps.length();i++)combined.put(steps.getJSONObject(i));
            steps=combined;
        }
        steps.put(step("home",own,current.known?current.actual:original,own).put("observed",current.actual).put("restore",original));
        return new JSONObject().put("recipe",recipe).put("title",title).put("original",original).put("user",adb.user)
                .put("fingerprint",Build.FINGERPRINT).put("target",own).put("keepAdb",keepAdb).put("state","proposed").put("steps",steps);
    }
    private boolean claims(String pkg){return !context.getPackageManager().queryIntentActivities(HomeController.homeIntent().setPackage(pkg),0).isEmpty();}
    private boolean hasJm(){
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.HOME.JM").setPackage("com.jm.jmlauncher");
        for(ResolveInfo item:context.getPackageManager().queryIntentActivities(query,0))if(item.activityInfo!=null && item.activityInfo.enabled
                && item.activityInfo.exported && item.activityInfo.applicationInfo.enabled && (item.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return true;
        return false;
    }
    private static boolean validPropertyValue(String value){return value.isEmpty() || HomeTakeoverPlan.packageName(value);}
    private static JSONObject step(String kind,String key,String before,String after) throws JSONException {return new JSONObject().put("kind",kind).put("key",key).put("before",before).put("after",after);}
    String description(JSONObject plan) throws JSONException {
        StringBuilder text=new StringBuilder();JSONArray steps=plan.getJSONArray("steps");
        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);if("package".equals(step.getString("kind")))text.append("禁用 ").append(step.getString("key")).append('\n');}
        if(text.length()==0)text.append("保留原桌面，修正固件的主页启动目标。\n");
        else text.append("按以上顺序处理，再绑定 Quiet TV；保留应用和数据。原桌面提供的专用遥控键或语音入口可能受影响，需一并验收。\n");
        if(plan.optBoolean("keepAdb"))for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);if(HomeTakeoverPlan.ADB_PORT.equals(step.getString("key")))text.append("选择保持 ADB 时，将保留当前 TCP 端口 ").append(step.getString("after")).append("，授权调试设置恢复；无需开启无障碍中转。\n");}
        return text+"方案依据上游或同固件用户反馈；本机实体 HOME、重启和唤醒仍需验收。失败时尝试恢复，连接中断可在“恢复原桌面”继续恢复。";
    }
    void apply(AdbController adb,JSONObject confirmed,SetupOperations.Ticket task) throws Exception {
        AppOperations.begin();
        HomeGuardState guard=new HomeGuardState(context);String previousMode=guard.preferences.getString("mode","AUTO"),previousStatus=guard.preferences.getString("status","");
        boolean configured=guard.preferences.getBoolean("configured",false),observe=guard.preferences.getBoolean("observeHome",true),paused=false;
        int generation=adb.generation();
        try{
            task.check();JSONObject fresh=inspect(adb,confirmed.optBoolean("keepAdb"));
            if(fresh==null || !fresh.toString().equals(confirmed.toString()))throw new IOException("接管方案或状态已变化，请重新检测");
            if(guard.helperActive())throw new IOException("请先暂停独立中转助手");
            store.baseline(home.inspect(adb).actual,confirmed.getString("original"));
            if(!new HomeProxy(context).pause())throw new IOException("代理暂停回执待确认");
            guard.pause("直接接管过程中，中转已暂停");paused=true;
            HomeTakeoverPlan.apply(confirmed,port(adb,task));
            new DesktopRoutes(context).directOnly();store.put("status","takeover-configured");
        }catch(Exception failure){
            if(paused && task.active() && adb.connected() && adb.generation()==generation
                    && ("proposed".equals(confirmed.optString("state")) || "rolled-back".equals(confirmed.optString("state"))))try{
                if(!guard.preferences.edit().putString("mode",previousMode).putBoolean("configured",configured)
                        .putBoolean("observeHome",observe).putString("status",previousStatus).commit())throw new IOException("中转原状态保存失败");
                if("AUTO".equals(previousMode) && configured){
                    HomeGuardSync.refresh(context);HomeGuardAdb.ensure(context);
                    if(new DesktopRoutes(context).proxySelected()){
                        String problem=new HomeProxy(context).resume();if(!problem.isEmpty())throw new IOException(problem);
                    }else if(observe)HomeGuardRecovery.request(context,"接管失败后恢复原中转");
                }
            }catch(Exception resume){failure.addSuppressed(resume);}
            throw failure;
        }finally{AppOperations.end();}
    }
    void restore(AdbController adb,SetupOperations.Ticket task) throws Exception {
        AppOperations.begin();try{
            JSONObject journal=store.get().optJSONObject("takeover");if(journal==null)throw new IOException("接管记录缺失");
            validate(journal,adb);new HomeGuardState(context).pause("恢复原桌面，中转已暂停");
            HomeTakeoverPlan.restore(journal,port(adb,task));store.put("status","restored");
        }finally{AppOperations.end();}
    }
    private void validate(JSONObject record,AdbController adb) throws Exception {
        if(record.getInt("user")!=adb.user || !Build.FINGERPRINT.equals(record.getString("fingerprint"))
                || !new ComponentName(context,HomeActivity.class).flattenToString().equals(record.getString("target")))throw new IOException("接管记录不属于当前用户或固件");
    }
    private HomeTakeoverPlan.Port port(AdbController adb,SetupOperations.Ticket task){
        int generation=adb.generation();
        return new HomeTakeoverPlan.Port(){
            public void check() throws Exception {task.check();if(adb.generation()!=generation)throw new IOException("连接已改变，操作停止");adb.validateUser();}
            public boolean rollbackAllowed(){return task.active() && adb.connected() && adb.generation()==generation;}
            public void save(JSONObject journal) throws Exception {store.put("takeover",journal);}
            public String read(JSONObject step) throws Exception {
                check();String key=step.getString("key"),kind=step.getString("kind");
                if("home".equals(kind))return home.inspect(adb).actual;
                boolean adbPlan=home.presets.homeAllows("keepAdb") && adb.user==0 && HomeGuardAdb.supported(context) && Build.VERSION.SDK_INT<=29;
                if("property".equals(kind) && (HomeTakeoverPlan.property(key) || adbPlan && HomeTakeoverPlan.ADB_PORT.equals(key)) && adb.user==0)return adb.shell("getprop "+key).trim();
                if(adbPlan && "permission".equals(kind) && HomeTakeoverPlan.WRITE_SECURE.equals(key))return HomeGuardRecovery.writeAllowed(context)?"granted":"denied";
                if(adbPlan && "preference".equals(kind) && "keepAdb".equals(key))return Boolean.toString(HomeGuardAdb.enabled(context));
                if("package".equals(kind) && HomeTakeoverPlan.packageName(key) && (HomeTakeoverPlan.GOOGLE.equals(key) || HomeTakeoverPlan.CLASSIC.equals(key) || HomeTakeoverPlan.SETUP.equals(key)
                        || home.presets.homeAllows("disableVerified") && home.presets.home().startsWith(key+"/"))){
                    AppRepository.App app=apps.find(adb,key);
                    if(app==null || !app.installed || !app.verified || !app.system || app.versionCode!=step.getLong("versionCode") || !app.version.equals(step.getString("version")))throw new IOException("原桌面版本或安装状态已变化");
                    return Integer.toString(app.enabledSetting);
                }
                throw new IOException("接管步骤无效");
            }
            public void write(JSONObject step,String value) throws Exception {
                check();String key=step.getString("key"),kind=step.getString("kind");read(step);
                if("home".equals(kind)){
                    if(!home.component(value,true))throw new IOException("恢复目标 HOME 不可用");home.setAndVerify(adb,value);
                }else if("property".equals(kind)){
                    if(!(HomeTakeoverPlan.ADB_PORT.equals(key)?HomeTakeoverPlan.adbPortState(value):validPropertyValue(value)))throw new IOException("系统属性值无效");adb.shell("setprop "+key+" '"+value+"'");
                }else if("permission".equals(kind)){
                    if(!"granted".equals(value) && !"denied".equals(value))throw new IOException("授权状态无效");
                    String operation="granted".equals(value)?"grant":"revoke";
                    String user=ShellCapabilities.userOption(ShellCapabilities.permissionHelp("pm",adb::shell),operation,Build.VERSION.SDK_INT,adb.user);
                    adb.shell("pm "+operation+user+" "+context.getPackageName()+" "+HomeTakeoverPlan.WRITE_SECURE);
                }else if("preference".equals(kind)){
                    if(!"true".equals(value) && !"false".equals(value) || !HomeGuardAdb.setEnabled(context,Boolean.parseBoolean(value)))throw new IOException("ADB 保持设置未保存");
                }else{
                    String command="3".equals(value)?adb.capabilities.disableCommand:"0".equals(value)?adb.capabilities.defaultStateCommand:"1".equals(value)?adb.capabilities.enableCommand:"";
                    if(command.isEmpty())throw new IOException("缺少可核对的恢复命令");adb.shell(command+" --user "+adb.user+" "+key);
                }
            }
        };
    }
}
