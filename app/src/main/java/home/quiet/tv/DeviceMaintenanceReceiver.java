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
        app.installed=(ai.flags&ApplicationInfo.FLAG_INSTALLED)!=0;app.enabled=ai.enabled;
        app.versionCode=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        app.enabledSetting=pm.getApplicationEnabledSetting(pkg);
        SetupPresets rules=SetupPresets.load(context);String reason=new AppOperations(context,rules).protection(app);
        boolean allowed=reason.isEmpty() && (!app.system || rules.allows(pkg,"DISABLE") && rules.allows(pkg,"ENABLE"))
                && (!rules.bootAd(pkg) || rules.bootAdMatches(pkg,app.versionCode));
        return new JSONObject().put("package",pkg).put("label",ai.loadLabel(pm).toString()).put("versionCode",app.versionCode)
                .put("system",app.system).put("installed",app.installed).put("enabled",app.enabled).put("enabledSetting",app.enabledSetting)
                .put("canManage",allowed).put("reason",reason).put("purpose",rules.purpose(pkg))
                .put("impact",rules.app(pkg)==null?"停用后无法使用此应用，保留安装和数据":rules.app(pkg).optString("impact","停用后无法使用此应用"));
    }
    static JSONObject snapshot(Context context) throws Exception {
        HomeController.State home=new HomeController(context).inspect(null);HomeGuardState guard=new HomeGuardState(context,false);
        String original=original(context);
        JSONObject value=new JSONObject().put("schemaVersion",1).put("package",context.getPackageName())
                .put("user",android.os.Process.myUid()/100000).put("fingerprint",Build.FINGERPRINT)
                .put("appVersion",BuildConfig.VERSION_CODE).put("systemHome",home.known?home.actual:"")
                .put("originalHome",original).put("originalUsable",new HomeController(context).component(original,true))
                .put("paused","OFF".equals(guard.preferences.getString("mode","AUTO"))).put("guardSupported",guard.supported())
                .put("guardConfigured",guard.preferences.getBoolean("configured",false))
                .put("directHomeConfigured",guard.directHomeConfigured()).put("keepAdb",HomeGuardAdb.enabled(context));
        JSONObject originalState=new SetupStore(context).get().optJSONObject("originalState");
        if(originalState!=null)value.put("originalState",originalState);
        JSONObject takeover=new SetupStore(context).get().optJSONObject("takeover");
        if(takeover!=null && !"restored".equals(takeover.optString("state")) && !"rolled-back".equals(takeover.optString("state")))
            value.put("takeoverRecord",takeover);
        if(!BuildConfig.LEGACY_19)value.put("routes",new DesktopRoutes(context,false).snapshot());
        return value;
    }
    @android.annotation.SuppressLint("ApplySharedPref") // A successful reply requires durable restoration state.
    @Override public void onReceive(Context context,Intent intent){
        setResultCode(Activity.RESULT_CANCELED);
        try{
            if(intent==null || !(context.getPackageName()+".DEVICE_MAINTENANCE").equals(intent.getAction()))return;
            if(intent.getIntExtra("user",-1)!=android.os.Process.myUid()/100000)throw new IllegalArgumentException("目标用户不一致");
            String operation=intent.getStringExtra("operation");
            if("remember_home".equals(operation)){
                HomeController controller=new HomeController(context);HomeController.State home=controller.inspect(null);
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
                HomeController.State home=new HomeController(context).inspect(null);
                if(!home.known || !home.actual.equals(original(context)))throw new IllegalStateException("原桌面尚未恢复");
                if(!new HomeGuardState(context).preferences.edit().putString("relaySource","").remove("proxyPackage").putBoolean("proxyPausePending",false).commit())
                    throw new IllegalStateException("恢复状态未保存");
            }else if(!"inspect".equals(operation))throw new IllegalArgumentException("不支持的维护操作");
            JSONObject result=snapshot(context);String pkg=intent.getStringExtra("target_package");
            if(pkg!=null)result.put("app",app(context,pkg));
            setResultData(result.toString());setResultCode(Activity.RESULT_OK);
        }catch(Exception failure){setResultData("维护未完成："+failure.getMessage());}
    }
}
