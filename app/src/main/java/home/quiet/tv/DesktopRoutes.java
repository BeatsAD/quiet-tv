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
    DesktopRoutes(Context supplied){
        context=supplied.getApplicationContext();state=new HomeGuardState(context);prefs=state.preferences;rules=SetupPresets.load(context);
    }
    private long version(String pkg){
        try{PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
        catch(PackageManager.NameNotFoundException|SecurityException failure){return -1;}
    }
    private String revision(){
        ComponentName home=ComponentName.unflattenFromString(state.selected());JSONObject boot=bridge();
        return Build.FINGERPRINT+":"+BuildConfig.VERSION_CODE+":"+BuildConfig.DECLARES_HOME+":"+state.selected()+":"+(home==null?-1:version(home.getPackageName()))
                +":"+(boot==null?-1:version(boot.optString("systemPackage")));
    }
    private boolean current(){return revision().equals(prefs.getString("routeRevision",""));}
    private SharedPreferences.Editor edit(){
        SharedPreferences.Editor edit=prefs.edit();
        if(!current())for(String scene:new String[]{"home","reboot","coldBoot","wake"})edit.remove("routeTest_"+scene);
        return edit.putString("routeRevision",revision());
    }
    void direct(){
        if(!BuildConfig.DECLARES_HOME)throw new IllegalStateException("此兼容包只能使用原桌面中转");
        // Only an explicit configuration changes the chosen route; opening settings never does.
        prefs.edit().putBoolean("observeHome",false).apply();state.configure();
        SharedPreferences.Editor edit=edit();if(!"DIRECT".equals(home()))edit.remove("routeTest_home").remove("routeTest_wake");
        edit.putString("routeHome","DIRECT").putBoolean("observeHome",false).putBoolean("configured",true).apply();
        HomeGuardRecovery.cancel();HomeGuardService.cancelPending();
        state.status("系统 HOME 已设置，实际场景待验证");
    }
    void observe(){SharedPreferences.Editor edit=edit();if(!"RELAY".equals(home()))edit.remove("routeTest_home").remove("routeTest_wake");edit.putString("routeHome","RELAY").putBoolean("observeHome",true).apply();}
    void resume(){
        prefs.edit().putString("mode","AUTO").putBoolean("configured",true).apply();
        if(!"DIRECT".equals(home())){state.enable();return;}
        state.status("系统直接启动，中转按场景配置");
    }
    String home(){return current()?prefs.getString("routeHome",state.enabled()?"RELAY":"UNKNOWN"):"UNKNOWN";}
    String homeLabel(){return "DIRECT".equals(home())?"系统直接启动":"RELAY".equals(home())?"原主页中转":"待检测";}
    String bootLabel(){return vendorReady()?"厂商开机中转":state.enabled()?"守护开机补偿":"待验证";}
    String wakeLabel(){return state.enabled()?"原主页中转":"待验证";}
    String test(String scene){return current()?prefs.getString("routeTest_"+scene,"待验证"):"待验证";}
    void observation(String scene,String result){
        if(!java.util.Arrays.asList("home","reboot","coldBoot","wake").contains(scene)
                || !java.util.Arrays.asList("通过","失败","待验证").contains(result))throw new IllegalArgumentException("验证记录无效");
        edit().putString("routeTest_"+scene,result).putLong("routeTestAt_"+scene,System.currentTimeMillis()).apply();
        DiagnosticLog.info("desktop.scene",scene+" user="+result);
    }
    String details(){return "HOME："+homeLabel()+"  "+test("home")+"\n重启："+bootLabel()+"  "+test("reboot")
            +"\n断电开机："+bootLabel()+"  "+test("coldBoot")+"\n待机唤醒："+wakeLabel()+"  "+test("wake");}
    JSONObject snapshot() throws JSONException {
        JSONObject snapshot=new JSONObject().put("packageMode",BuildConfig.DECLARES_HOME?"standard":"relay")
                .put("homeRoute",home()).put("bootRoute",vendorReady()?"VENDOR":state.enabled()?"GUARD":"UNKNOWN")
                .put("wakeRoute",state.enabled()?"GUARD":"UNKNOWN").put("paused","OFF".equals(prefs.getString("mode","AUTO"))).put("automatic",state.automatic())
                .put("vendorBootEnabled",vendorReady()).put("scenes",new JSONObject().put("home",test("home"))
                .put("reboot",test("reboot")).put("coldBoot",test("coldBoot")).put("wake",test("wake")));
        try{HomeController.State actual=new HomeController(context).inspect(null);snapshot.put("systemHome",actual.resolved)
                .put("systemHomeKnown",actual.known).put("entryUsable",actual.ownUsable);}
        catch(Exception failure){snapshot.put("systemHomeKnown",false);}
        return snapshot.put("observationReady",state.ready() && state.serviceEnabled() && HomeGuardService.connected());
    }
    private JSONObject bridge(){return rules.vendorBoot();}
    String vendorProblem(){
        if(!state.supported())return "此系统档位未开放开机跳板";
        if(!HomeGuardState.unlocked(context))return "等待系统解锁";
        JSONObject rule=bridge();if(rule==null)return "未匹配已验证的固件入口，跳板保持关闭";
        if(state.helperActive())return "请先暂停独立中转助手";
        if(!state.validSelection())return "请选择可用的原桌面";
        if(!state.selected().equals(rules.home()))return "原桌面与开机适配不一致";
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(rule.getString("systemPackage"),0);
            long version=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
            if(info.applicationInfo==null || !info.applicationInfo.enabled || (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0
                    || version!=rule.getLong("versionCode"))return "厂商组件已变化，跳板保持关闭";
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
            && prefs.getString("vendorFirmware","").equals(Build.FINGERPRINT) && vendorProblem().isEmpty();}
    void reconcileVendor(){if(prefs.getBoolean("vendorBoot",false) && !vendorReady())disableVendor(context);}
    String configureVendor(){
        if(!state.automatic()){disableVendor(context);return "中转已暂停，跳板保持关闭";}
        String problem=vendorProblem();if(!problem.isEmpty()){disableVendor(context);return problem;}
        SharedPreferences.Editor edit=edit();if(!vendorReady())edit.remove("routeTest_reboot").remove("routeTest_coldBoot");
        if(!edit.putBoolean("vendorBoot",true).putString("vendorFirmware",Build.FINGERPRINT).commit())return "开机配置未保存";
        try{setVendorComponent(context,true);}
        catch(RuntimeException failure){disableVendor(context);return "系统未允许启用开机跳板";}
        if(!vendorReady()){disableVendor(context);return "配置已暂停或身份已变化，跳板保持关闭";}
        return "";
    }
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
