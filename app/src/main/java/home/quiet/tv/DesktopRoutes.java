package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import org.json.*;

/** One plan in the existing device-protected preferences. No device mutation during detection. */
final class DesktopRoutes {
    static final String VENDOR_ACTION="com.skyworth.noriceguide.show";
    private final Context context;
    private final HomeGuardState state;
    private final SharedPreferences prefs;
    private final SetupPresets rules;
    DesktopRoutes(Context supplied){this(supplied,true);}
    DesktopRoutes(Context supplied,boolean migrate){
        context=supplied.getApplicationContext();state=new HomeGuardState(context,migrate);prefs=state.preferences;rules=SetupPresets.load(context);
    }
    private long version(String pkg){
        try{PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
        catch(PackageManager.NameNotFoundException|SecurityException failure){return -1;}
    }
    private String revision(){
        ComponentName home=ComponentName.unflattenFromString(state.selected());JSONObject boot=bridge();
        String proxy=new HomeProxy(context).packageName();
        return Build.FINGERPRINT+":"+BuildConfig.VERSION_CODE+":"+BuildConfig.DECLARES_HOME+":"+state.selected()+":"+(home==null?-1:version(home.getPackageName()))
                +":"+(boot==null?-1:version(boot.optString("systemPackage")))+":"+proxy+":"+version(proxy);
    }
    boolean current(){return revision().equals(prefs.getString("routeRevision",""));}
    private SharedPreferences.Editor edit(){
        SharedPreferences.Editor edit=prefs.edit();
        if(!current())for(String scene:new String[]{"home","reboot","coldBoot","wake"})edit.remove("routeTest_"+scene);
        return edit.putString("routeRevision",revision());
    }
    void direct(){
        if(!BuildConfig.DECLARES_HOME)throw new IllegalStateException("此兼容包只能使用原桌面中转");
        // Only an explicit configuration changes the chosen route; opening settings never does.
        new HomeProxy(context).pause();prefs.edit().putBoolean("observeHome",true).putString("relaySource","").apply();state.configure();
        SharedPreferences.Editor edit=edit();if(!"DIRECT".equals(home()))edit.remove("routeTest_home").remove("routeTest_wake");
        edit.putString("routeHome","DIRECT").putBoolean("observeHome",true).putBoolean("configured",true)
                .putBoolean("directHomeKeys",state.directHomeAvailable()).apply();
        HomeGuardRecovery.cancel();HomeGuardService.cancelPending();
        HomeGuardRecovery.request(context,"直接 HOME 开机补偿");
        state.status("系统 HOME 已设置，开机补偿待核对");
    }
    void directOnly(){
        if(!BuildConfig.DECLARES_HOME)throw new IllegalStateException("此兼容包只能使用原桌面中转");
        HomeGuardRecovery.cancel();HomeGuardService.cancelPending();HomeGuardSync.pause(context);HomeGuardUsageService.pause(context);
        edit().putString("routeHome","DIRECT").putString("relaySource","").putString("mode","AUTO")
                .putBoolean("configured",true).putBoolean("observeHome",false).putBoolean("directHomeKeys",false)
                .remove("routeTest_home").remove("routeTest_reboot").remove("routeTest_coldBoot").remove("routeTest_wake").apply();
        state.status("直接接管已配置，实体 HOME、开机与唤醒待验证");
        HomeGuardSync.refresh(context);HomeGuardAdb.ensure(context);
    }
    void observe(){SharedPreferences.Editor edit=edit();if(!"RELAY".equals(home()))edit.remove("routeTest_home").remove("routeTest_wake");edit.putString("routeHome","RELAY").putString("relaySource","OBSERVER").putBoolean("observeHome",true).putBoolean("directHomeKeys",false).apply();}
    void proxy(){
        edit().putString("routeHome","RELAY").putString("relaySource","PROXY").putBoolean("observeHome",false).putBoolean("configured",true).putBoolean("directHomeKeys",false)
                .remove("routeTest_home").remove("routeTest_wake").commit();
        HomeGuardRecovery.cancel();HomeGuardService.cancelPending();HomeGuardSync.pause(context);HomeGuardUsageService.pause(context);
    }
    boolean proxySelected(){return "PROXY".equals(prefs.getString("relaySource",""));}
    void resume(){
        if(proxySelected()){String problem=new HomeProxy(context).resume();if(!problem.isEmpty())state.status(problem);return;}

        prefs.edit().putString("mode","AUTO").putBoolean("configured",true).apply();
        if(!"DIRECT".equals(home())){state.enable();return;}
        prefs.edit().putBoolean("observeHome",true).putBoolean("directHomeKeys",state.directHomeAvailable()).apply();HomeGuardRecovery.request(context,"恢复开机补偿");
        state.status("系统直接启动，开机补偿待核对");
    }
    String home(){return current()?prefs.getString("routeHome",state.enabled()?"RELAY":"UNKNOWN"):"UNKNOWN";}
    String homeLabel(){if(state.directHomeConfigured())return "直接 HOME 按键补偿";if("RELAY".equals(home()) && proxySelected())return "HOME 代理中转";return "DIRECT".equals(home())?"系统直接启动":"RELAY".equals(home())?"原主页中转":"待检测";}
    String bootLabel(){return vendorReady()?"厂商开机中转":state.observationReady()?"守护开机补偿":state.enabled()?"守护待就绪":"待验证";}
    String wakeLabel(){return state.observationReady()?"原主页中转":state.enabled()?"守护待就绪":"待验证";}
    String test(String scene){return current()?prefs.getString("routeTest_"+scene,"待验证"):"待验证";}
    void observation(String scene,String result){
        if(!java.util.Arrays.asList("home","reboot","coldBoot","wake").contains(scene)
                || !java.util.Arrays.asList("通过","失败","待验证").contains(result))throw new IllegalArgumentException("验证记录无效");
        long at=System.currentTimeMillis();
        DiagnosticOperation trace=new DiagnosticOperation("desktop.scene",at,SystemClock.elapsedRealtime(),DiagnosticLog::info);
        String lastHandover=HomeGuardRecovery.lastDiagnosticAttempt();
        edit().putString("routeTest_"+scene,result).putLong("routeTestAt_"+scene,at).putString("routeTestEvidence_"+scene,trace.id).apply();
        DiagnosticLog.info("desktop.scene","attempt="+trace.id+" scene="+scene+" source=user result="+result+" lastHandoverAttempt="+lastHandover);
        trace.finish("USER_REPORTED","scene_"+scene,System.currentTimeMillis(),SystemClock.elapsedRealtime());
    }
    String details(){return "HOME："+homeLabel()+"  "+test("home")+"\n重启："+bootLabel()+"  "+test("reboot")
            +"\n断电开机："+bootLabel()+"  "+test("coldBoot")+"\n待机唤醒："+wakeLabel()+"  "+test("wake");}
    JSONObject snapshot() throws JSONException {
        JSONObject snapshot=new JSONObject().put("packageMode",BuildConfig.DECLARES_HOME?"standard":"relay")
                .put("homeRoute",home()).put("relaySource",prefs.getString("relaySource","UNKNOWN")).put("proxyPackage",new HomeProxy(context).packageName())
                .put("proxyAvailable",new HomeProxy(context).available()).put("proxyReady",new HomeProxy(context).ready())
                .put("proxyPausePending",prefs.getBoolean("proxyPausePending",false)).put("proxyVisibleAt",current() && prefs.getLong("proxyVisibleEpoch",-1)==prefs.getLong("proxyEpoch",-2)?prefs.getLong("proxyVisibleAt",0):0).put("bootRoute",vendorReady()?"VENDOR":state.enabled()?"GUARD":"UNKNOWN")
                .put("wakeRoute",state.enabled()?"GUARD":"UNKNOWN").put("paused","OFF".equals(prefs.getString("mode","AUTO"))).put("automatic",state.automatic())
                .put("vendorBootEnabled",vendorReady()).put("vendorBootBasis",bridge()!=null?"verified_profile":DesktopRoutePolicy.bridgeCandidate(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19)?"runtime_candidate_unverified":"unavailable").put("scenes",new JSONObject().put("home",test("home"))
                .put("reboot",test("reboot")).put("coldBoot",test("coldBoot")).put("wake",test("wake")));
        try{HomeController.State actual=new HomeController(context).inspect(null);snapshot.put("systemHome",actual.resolved)
                .put("systemHomeKnown",actual.known).put("entryUsable",actual.ownUsable);}
        catch(Exception failure){snapshot.put("systemHomeKnown",false);}
        JSONObject evidence=new JSONObject();
        for(String scene:new String[]{"home","reboot","coldBoot","wake"}){
            boolean recorded=current() && prefs.contains("routeTest_"+scene);
            evidence.put(scene,new JSONObject().put("result",test(scene)).put("source",recorded?"user":"unverified")
                    .put("recordedAt",recorded?prefs.getLong("routeTestAt_"+scene,0):0)
                    .put("evidenceId",recorded?prefs.getString("routeTestEvidence_"+scene,""):""));
        }
        try{JSONObject takeover=new SetupStore(context).get().optJSONObject("takeover");
            if(takeover!=null)snapshot.put("takeover",new JSONObject().put("recipe",takeover.optString("recipe")).put("state",takeover.optString("state"))
                    .put("steps",takeover.optJSONArray("steps")).put("deviceAcceptance","separate_scene_evidence"));
        }catch(Exception unavailable){snapshot.put("takeoverRecord","unreadable");}
        return snapshot.put("sceneEvidence",evidence).put("vendorBootProblem",vendorProblem())
                .put("usageObserverBound",HomeGuardUsageService.connected()).put("tclPolicyStatus",prefs.getString("tclPolicyStatus","未配置"))
                .put("accessibilityAllowed",state.serviceEnabled()).put("accessibilityBound",HomeGuardService.connected())
                .put("usageRequired",HomeGuardService.usageRequired(context)).put("usageAllowed",HomeGuardService.usageAllowed(context))
                .put("configurationProblem",state.enabled()?HomeGuardRecovery.configurationProblem(context):"")
                .put("syncRecovery",HomeGuardSync.summary(context)).put("syncRecoveryReady",HomeGuardSync.ready(context)).put("keepAdb",HomeGuardAdb.enabled(context))
                .put("keepAdbReady",HomeGuardAdb.ready(context)).put("keepAdbStatus",HomeGuardAdb.summary(context))
                .put("observationReady",state.observationReady()).put("directHomeConfigured",state.directHomeConfigured())
                .put("homeActionReady",state.homeActionReady()).put("homeActionChannelBound",HomeGuardService.connected())
                .put("homeKeyFilterCapable",HomeGuardService.connected()?(Object)HomeGuardService.keyFilterCapable():JSONObject.NULL);
    }
    private JSONObject bridge(){return rules.vendorBoot();}
    String vendorProblem(){
        if(!state.supported())return "此系统档位未开放开机跳板";
        if(!HomeGuardState.unlocked(context))return "等待系统解锁";
        JSONObject rule=bridge();
        if(rule==null && !DesktopRoutePolicy.bridgeCandidate(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19))return "此设备不在创维／酷开开机跳板适用范围";
        if(state.helperActive())return "请先暂停独立中转助手";
        if(!state.validSelection())return "请选择可用的原桌面";
        try{
            // An available runtime candidate is not firmware acceptance. Exact profiles retain their checks.
            if(rule!=null){
                if(!state.selected().equals(rules.home()))return "原桌面与开机适配不一致";
                PackageInfo info=context.getPackageManager().getPackageInfo(rule.getString("systemPackage"),0);
                long version=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
                if(info.applicationInfo==null || !info.applicationInfo.enabled || (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0
                        || version!=rule.getLong("versionCode"))return "厂商组件已变化，跳板保持关闭";
            }
            ComponentName own=new ComponentName(context,VendorBootActivity.class);
            for(ResolveInfo match:context.getPackageManager().queryIntentActivities(new Intent(VENDOR_ACTION),PackageManager.MATCH_DEFAULT_ONLY)){
                if(match.activityInfo==null || !own.equals(new ComponentName(match.activityInfo.packageName,match.activityInfo.name)))
                    return "开机动作已有其他处理应用，未启用跳板";
            }
        }catch(PackageManager.NameNotFoundException|JSONException|SecurityException failure){return "无法核对厂商开机组件";}
        return "";
    }
    boolean vendorReady(){return state.automatic() && current() && prefs.getBoolean("vendorBoot",false)
            && context.getPackageManager().getComponentEnabledSetting(new ComponentName(context,VendorBootActivity.class))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            && prefs.getString("vendorFirmware","").equals(Build.FINGERPRINT)
            && prefs.getString("vendorRevision","").equals(revision()) && vendorProblem().isEmpty();}
    void reconcileVendor(){if(prefs.getBoolean("vendorBoot",false) && !vendorReady())disableVendor(context);}
    String configureVendor(){
        if(!state.automatic()){disableVendor(context);return "中转已暂停，跳板保持关闭";}
        String problem=vendorProblem();if(!problem.isEmpty()){disableVendor(context);return problem;}
        SharedPreferences.Editor edit=edit();if(!vendorReady())edit.remove("routeTest_reboot").remove("routeTest_coldBoot");
        if(!edit.putBoolean("vendorBoot",true).putString("vendorFirmware",Build.FINGERPRINT).putString("vendorRevision",revision()).commit())return "开机配置未保存";
        try{setVendorComponent(context,true);}
        catch(RuntimeException failure){disableVendor(context);return "系统未允许启用开机跳板";}
        if(!vendorReady()){disableVendor(context);return "配置已暂停或身份已变化，跳板保持关闭";}
        return "";
    }
    void configureAvailableVendor(){if(vendorProblem().isEmpty()){String problem=configureVendor();if(!problem.isEmpty())state.status(problem);}}
    static void setVendorComponent(Context context,boolean enabled){
        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context,VendorBootActivity.class),
                enabled?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
    }
    static void disableVendor(Context supplied){
        Context context=supplied.getApplicationContext();
        HomeGuardState state=new HomeGuardState(context);
        SharedPreferences.Editor edit=state.preferences.edit();if(state.preferences.getBoolean("vendorBoot",false))edit.remove("routeTest_reboot").remove("routeTest_coldBoot");edit.putBoolean("vendorBoot",false).apply();
        try{setVendorComponent(context,false);}catch(RuntimeException failure){state.status("跳板已停用，组件关闭未确认");}
    }
    boolean acceptVendor(Intent intent){
        if(intent==null || !VENDOR_ACTION.equals(intent.getAction()) || intent.getData()!=null || intent.getType()!=null)return false;
        if(intent.getCategories()!=null)for(String category:intent.getCategories())if(!Intent.CATEGORY_DEFAULT.equals(category))return false;
        String boot=new BootAppStore(context).bootId();
        if(boot.isEmpty() || !DesktopRoutePolicy.bridgeAllowed(state.automatic(),vendorReady(),HomeGuardState.canShow(context),
                state.helperActive(),SystemClock.elapsedRealtime(),boot.equals(prefs.getString("vendorConsumedBoot",""))))return false;
        // Consume before launch, including a rejected launch. No repeated foreground stealing this boot.
        return prefs.edit().putString("vendorConsumedBoot",boot).commit();
    }
}
