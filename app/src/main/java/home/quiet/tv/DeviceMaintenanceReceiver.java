package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import org.json.JSONObject;

/** Computer maintenance reads the installed app's rules; it never executes arbitrary shell input. */
public final class DeviceMaintenanceReceiver extends BroadcastReceiver {
    static String original(Context context) throws Exception {
        String saved=new SetupStore(context).originalHome();
        return saved.isEmpty()?new HomeGuardState(context,false).selected():saved;
    }
    private static JSONObject app(Context context,String pkg) throws Exception {
        if(!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new IllegalArgumentException("包名无效");
        PackageManager pm=context.getPackageManager();PackageInfo info=pm.getPackageInfo(pkg,HomeController.METADATA);
        ApplicationInfo ai=info.applicationInfo;
        if(ai==null)throw new IllegalStateException("应用信息不可用");
        AppRepository.App app=new AppRepository.App();app.pkg=pkg;app.metadataKnown=true;
        app.system=(ai.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
        app.installed=AppAvailability.installed(pm,ai);app.enabled=ai.enabled;
        app.versionCode=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        app.enabledSetting=pm.getApplicationEnabledSetting(pkg);
        SetupPresets rules=SetupPresets.load(context);String reason=new AppOperations(context,rules).protection(app);
        boolean allowed=reason.isEmpty() && (!app.system || rules.allows(pkg,"DISABLE") && rules.allows(pkg,"ENABLE"))
                && (!rules.bootAd(pkg) || rules.bootAdMatches(pkg,app.versionCode));
        return new JSONObject().put("package",pkg).put("label",ai.loadLabel(pm).toString()).put("versionCode",app.versionCode).put("version",info.versionName==null?String.valueOf(app.versionCode):info.versionName)
                .put("system",app.system).put("installed",app.installed).put("enabled",app.enabled).put("enabledSetting",app.enabledSetting)
                .put("canManage",allowed).put("reason",reason).put("purpose",rules.purpose(pkg))
                .put("impact",rules.app(pkg)==null?"停用后无法使用此应用，保留安装和数据":rules.app(pkg).optString("impact","停用后无法使用此应用"));
    }
    static JSONObject snapshot(Context context) throws Exception {
        HomeController.State home=new HomeController(context).inspect();HomeGuardState guard=new HomeGuardState(context,false);
        String original=original(context);
        JSONObject value=new JSONObject().put("schemaVersion",1).put("package",context.getPackageName())
                .put("user",android.os.Process.myUid()/100000).put("fingerprint",Build.FINGERPRINT)
                .put("appVersion",BuildConfig.VERSION_CODE).put("systemHome",home.known?home.actual:"")
                .put("originalHome",original).put("originalUsable",new HomeController(context).component(original,true))
                .put("paused","OFF".equals(guard.preferences.getString("mode","AUTO"))).put("guardSupported",guard.supported())
                .put("guardConfigured",guard.preferences.getBoolean("configured",false))
                .put("directHomeConfigured",guard.directHomeConfigured()).put("keepAdb",HomeGuardAdb.enabled(context)).put("adb",HomeGuardAdb.snapshot(context));
        JSONObject originalState=new SetupStore(context).get().optJSONObject("originalState");
        if(originalState!=null)value.put("originalState",originalState);
        JSONObject takeover=new SetupStore(context).get().optJSONObject("takeover");
        value.put("takeoverRevision",DeviceInputProfile.digest(takeover==null?"":takeover.toString()));
        if(takeover!=null && !"restored".equals(takeover.optString("state")) && !"rolled-back".equals(takeover.optString("state")))
            value.put("takeoverRecord",takeover);
        if(!BuildConfig.LEGACY_19)value.put("routes",new DesktopRoutes(context,false).snapshot());
        value.put("capabilities",new org.json.JSONArray().put("input-profile-v1").put("takeover-restore-v1").put("proxy-repair-v1").put("adaptation-rules-v1").put("takeover-record-v1"));
        value.put("adaptation",new DeviceAdaptation(context).snapshot());
        value.getJSONArray("capabilities").put("daily-app-management-v1");
        android.content.SharedPreferences daily=context.getSharedPreferences("adb",0);
        value.put("dailyManagement",new JSONObject().put("transport","tcp").put("port",daily.getInt("port",5555)).put("configured",daily.getBoolean("dailyManagement",false)));
        value.getJSONArray("capabilities").put("legacy-records-v1").put("proxy-setup-v1").put("remote-keymap-v1");
        HomeProxy proxy=new HomeProxy(context);
        value.put("proxy",new JSONObject().put("selected","PROXY".equals(guard.preferences.getString("relaySource","")))
            .put("available",proxy.available()).put("component",proxy.component()).put("ready",proxy.ready())
            .put("pending",proxy.setupPending()).put("pausePending",guard.preferences.getBoolean("proxyPausePending",false)));
        return value;
    }
    @android.annotation.SuppressLint("ApplySharedPref") // A successful reply requires durable restoration state.
    @Override public void onReceive(Context context,Intent intent){
        setResultCode(Activity.RESULT_CANCELED);
        try{
            if(intent==null || !(context.getPackageName()+".DEVICE_MAINTENANCE").equals(intent.getAction()))return;
            if(intent.getIntExtra("user",-1)!=android.os.Process.myUid()/100000)throw new IllegalArgumentException("目标用户不一致");
            String operation=intent.getStringExtra("operation");
            if(operation!=null && operation.startsWith("remote_")){
                new RemoteKeyMap(context).operation(operation,intent);
            }else if("management_config".equals(operation)){
                int port=intent.getIntExtra("port",-1);
                if(port<1 || port>65535)throw new IllegalArgumentException("本机 TCP ADB 端口无效");
                if(!context.getSharedPreferences("adb",0).edit().putInt("port",port).putBoolean("dailyManagement",true).commit())throw new IllegalStateException("管理配置未保存");
            }else if("rules_set".equals(operation)){
                new DeviceAdaptation(context).save(intent.getStringExtra("rules_json"),intent.getStringExtra("revision"));
            }else if("takeover_record".equals(operation)){
                new ComputerRestorePlan(context).save(intent.getStringExtra("record_json"),intent.getStringExtra("revision"));
            }else if("proxy_prepare".equals(operation)){
                new HomeProxy(context).prepareComputer(original(context));
            }else if("proxy_finish".equals(operation)){
                new HomeProxy(context).finishComputer(Long.parseLong(intent.getStringExtra("epoch")));
            }else if("remember_takeover_home".equals(operation)){
                HomeController controller=new HomeController(context);HomeController.State home=controller.inspect();
                String expected=intent.getStringExtra("home");SetupStore store=new SetupStore(context);
                if(expected==null || expected.startsWith(context.getPackageName()+"/") || !controller.component(expected,true))throw new IllegalStateException("专项原桌面不可用");
                boolean wizard=home.known && home.actual.startsWith(HomeTakeoverPlan.SETUP+"/") && expected.startsWith(HomeTakeoverPlan.GOOGLE+"/")
                    && store.originalHome().startsWith(HomeTakeoverPlan.SETUP+"/");
                if(!store.originalHome().isEmpty() && !store.originalHome().equals(expected) && !wizard)throw new IllegalStateException("不能覆盖历史原桌面");
                if(!home.known || !(expected.equals(home.actual) || expected.equals(store.originalHome())
                        || home.actual.startsWith(HomeTakeoverPlan.SETUP+"/") && expected.startsWith(HomeTakeoverPlan.GOOGLE+"/")))throw new IllegalStateException("专项原桌面与当前 HOME 不符");
                if(wizard)store.put("baseline",store.get().getJSONObject("baseline").put("originalHome",expected).put("resolvedSetupWizard",true));
                else store.baseline(home.actual,expected);
            }else if("remember_home".equals(operation)){
                HomeController controller=new HomeController(context);HomeController.State home=controller.inspect();
                String expected=intent.getStringExtra("home");
                if(expected==null || !home.known || home.isOwn(context) || home.isProxy(context) || !home.actual.equals(expected))
                    throw new IllegalStateException("原桌面与当前 HOME 不一致，未保存恢复基线");
                SetupStore store=new SetupStore(context);
                if(!store.originalHome().isEmpty() && !store.originalHome().equals(expected))throw new IllegalStateException("已有不同原桌面记录，不能覆盖");
                store.baseline(home.actual,home.actual);
            }else if("pause".equals(operation)){
                HomeGuardState state=new HomeGuardState(context);state.pause("用户暂停桌面接管");
                if(state.preferences.getBoolean("proxyPausePending",false))throw new IllegalStateException("代理暂停尚未确认");
            }else if("finish_restore".equals(operation)){
                HomeController.State home=new HomeController(context).inspect();
                if(!home.known || !home.actual.equals(original(context)))throw new IllegalStateException("原桌面尚未恢复");
                if(!new HomeGuardState(context).preferences.edit().putString("relaySource","").remove("proxyPackage").putBoolean("proxyPausePending",false).commit())
                    throw new IllegalStateException("恢复状态未保存");
            }else if("profile_set".equals(operation) || "profile_reset".equals(operation)){
                new DeviceInputProfile(context).save(intent.getStringExtra("profile_json"),intent.getStringExtra("revision"),"profile_reset".equals(operation));
            }else if("input_open".equals(operation)){
                DeviceInputProfile.request(context,intent.getStringExtra("source"));
            }else if("proxy_repair".equals(operation)){
                HomeGuardState state=new HomeGuardState(context,false);
                if(!"PROXY".equals(state.preferences.getString("relaySource","")))throw new IllegalStateException("当前未选择 HOME 代理");
                if("OFF".equals(state.preferences.getString("mode","AUTO")) && !intent.getBooleanExtra("resume",false))throw new IllegalStateException("接管已暂停，需要明确恢复");
                String problem=new HomeProxy(context).resume();if(!problem.isEmpty())throw new IllegalStateException(problem);
            }else if("takeover_preference".equals(operation)){
                new ComputerRestorePlan(context).preference(intent.getStringExtra("revision"),intent.getIntExtra("index",-1));
            }else if("takeover_finish".equals(operation)){
                if(!"OFF".equals(new HomeGuardState(context,false).preferences.getString("mode","AUTO")))throw new IllegalStateException("恢复前必须暂停接管");
                new ComputerRestorePlan(context).finish(intent.getStringExtra("revision"),intent.getStringExtra("proof"));
            }else if(!java.util.Arrays.asList("inspect","input_list","profile_get","takeover_plan","legacy_records").contains(operation))throw new IllegalArgumentException("不支持的维护操作");
            JSONObject result=snapshot(context);String pkg=intent.getStringExtra("target_package");
            if(operation.startsWith("remote_"))result.put("remoteKeys",new RemoteKeyMap(context).snapshot());
            if(operation.startsWith("proxy_"))result.put("proxyEpoch",Long.toString(new HomeGuardState(context,false).preferences.getLong("proxyEpoch",-1)));
            if("legacy_records".equals(operation)){
                result.put("legacyRecords",new SetupStore(context).get());
                org.json.JSONArray providers=new org.json.JSONArray();
                for(String authority:new String[]{"com.tcl.config.ConfigProvider","com.tcl.providers.config"}){
                    ProviderInfo info=context.getPackageManager().resolveContentProvider(authority,0);
                    if(info!=null && "com.tcl.providers.config".equals(info.packageName) && (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0)providers.put(authority);
                }
                result.put("tclProviders",providers);
            }
            if(pkg!=null)result.put("app",app(context,pkg));
            if(operation.startsWith("profile_") || "input_open".equals(operation))result.put("inputProfile",new DeviceInputProfile(context).snapshot());
            if("input_list".equals(operation))result.put("inputs",Build.VERSION.SDK_INT>=21?new TvInputController(context).maintenanceInputs():new JSONObject().put("supported",false));
            if("takeover_plan".equals(operation))result.put("restorePlan",new ComputerRestorePlan(context).plan());
            setResultData(result.toString());setResultCode(Activity.RESULT_OK);
        }catch(Exception failure){setResultData("维护未完成："+failure.getMessage());}
    }
}
