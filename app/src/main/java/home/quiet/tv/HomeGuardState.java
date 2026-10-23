package home.quiet.tv;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import java.util.*;

/** Compatibility preferences never stand in for Android's actual HOME identity. */
final class HomeGuardState {
    static final String PREFS="home-guard";
    final SharedPreferences preferences;
    private final Context context;
    private final PackageManager pm;
    static final class Candidate {
        final ComponentName component;
        final String label;
        Candidate(ComponentName component,String label){this.component=component;this.label=label;}
    }
    HomeGuardState(Context context){this.context=context;pm=context.getPackageManager();preferences=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    boolean supported(){return HomeGuardPolicy.supported(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    boolean enabled(){return !BuildConfig.LEGACY_19 && preferences.getBoolean("enabled",false);}
    String selected(){return preferences.getString("home","");}
    boolean protects(String pkg){ComponentName name=ComponentName.unflattenFromString(selected());return enabled() && name!=null && name.getPackageName().equals(pkg);}
    void pause(String reason){preferences.edit().putBoolean("enabled",false).putString("status",reason).apply();HomeGuardService.cancelPending();}
    void select(ComponentName name){pause("已暂停");preferences.edit().putString("home",name.flattenToString()).putString("firmware",Build.FINGERPRINT).apply();}
    void status(String text){if(!text.equals(preferences.getString("status","")))preferences.edit().putString("status",text).apply();}
    boolean serviceEnabled(){
        AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(manager==null)return false;
        for(AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
            ResolveInfo resolved=info.getResolveInfo();ServiceInfo service=resolved==null?null:resolved.serviceInfo;
            if(service!=null && context.getPackageName().equals(service.packageName) && HomeGuardService.class.getName().equals(service.name))return true;
        }
        return false;
    }
    List<Candidate> candidates(){
        Map<String,Candidate> homes=new LinkedHashMap<>();
        collect(homes,pm.queryIntentActivities(HomeController.homeIntent(),0));
        for(String category:new String[]{"android.intent.category.HOME.CC","android.intent.category.HOME.CC_MAIN","smartdevice.intent.category.LITE_HOME"}){
            if(HomeGuardPolicy.vendorHome(category))collect(homes,pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0));
        }
        return new ArrayList<>(homes.values());
    }
    private void collect(Map<String,Candidate> homes,List<ResolveInfo> values){
        for(ResolveInfo value:values){
            ActivityInfo info=value.activityInfo;
            if(info==null || context.getPackageName().equals(info.packageName) || !info.enabled || !info.applicationInfo.enabled
                    || !info.exported || (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0
                    || "android".equals(info.packageName) || info.name.endsWith("FallbackHome")
                    || (info.permission!=null && pm.checkPermission(info.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED))continue;
            ComponentName name=new ComponentName(info.packageName,info.name);
            homes.put(name.flattenToString(),new Candidate(name,info.loadLabel(pm).toString()));
        }
    }
    boolean validSelection(){
        if(!Build.FINGERPRINT.equals(preferences.getString("firmware","")))return false;
        for(Candidate home:candidates())if(home.component.flattenToString().equals(selected()))return true;
        return false;
    }
    String enableProblem(){
        if(!supported())return "当前设备未开放此实验功能";
        if(!validSelection())return "请先选择本机检测到的原主页";
        if(helperActive())return "请先暂停独立中转助手，避免同时接管主页";
        if(!serviceEnabled())return "请先开启“桌面守护（实验）”的无障碍权限";
        return "";
    }
    String enable(){
        String problem=enableProblem();if(!problem.isEmpty())return problem;
        preferences.edit().putBoolean("enabled",true).putString("status","已启用，等待主页事件").apply();return "";
    }
    String summary(){
        if(!supported())return "当前设备未开放此实验功能";
        if(!enabled())return preferences.getString("status","未启用");
        if(!validSelection())return "原主页已变化，请暂停后重新选择";
        if(!serviceEnabled())return "需要开启无障碍";
        if(!HomeGuardService.connected())return "已启用，等待系统绑定服务";
        return preferences.getString("status","已启用，等待主页事件");
    }
    boolean helperActive(){
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled!=null)for(String entry:enabled.split(":")){
            ComponentName name=ComponentName.unflattenFromString(entry);
            if(name!=null && "home.quiet.tv.xiaomihelper".equals(name.getPackageName()))return true;
        }
        // Coocaa's shim is active by package presence on its supported firmware; it has no pause switch.
        try{PackageInfo helper=pm.getPackageInfo("com.ccos.tv.homepage",0);return helper.applicationInfo!=null && helper.applicationInfo.enabled;}
        catch(PackageManager.NameNotFoundException ignored){return false;}
    }
}
