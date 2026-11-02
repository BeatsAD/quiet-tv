package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
import java.util.*;
import org.json.*;

/** Explicit API18 window observation; it never changes the system HOME or grants permissions. */
final class LegacyWindowHomeGuard {
    static boolean supported(){return BuildConfig.LEGACY_19 && Build.VERSION.SDK_INT==18 && BuildConfig.DECLARES_HOME;}
    private static SharedPreferences preferences(Context c){return c.getSharedPreferences(HomeGuardState.PREFS,Context.MODE_PRIVATE);}
    static String original(Context c){return preferences(c).getString("windowGuardHome","");}
    static boolean configured(Context c){return supported() && !original(c).isEmpty();}
    static List<String> candidates(Context c){
        List<String> homes=new ArrayList<>();
        for(ResolveInfo value:c.getPackageManager().queryIntentActivities(HomeController.homeIntent(),0)){
            if(value.activityInfo==null)continue;
            String home=new ComponentName(value.activityInfo.packageName,value.activityInfo.name).flattenToString();
            if(usable(c,home))homes.add(home);
        }
        return homes;
    }
    static boolean usable(Context c,String home){
        if(home==null)return false;
        ComponentName name=ComponentName.unflattenFromString(home);
        if(name==null || name.getPackageName().equals(c.getPackageName()) || HomeGuardPolicy.excludedHome(name.getPackageName()))return false;
        try{
            ActivityInfo a=c.getPackageManager().getActivityInfo(name,0);
            return a.exported && a.enabled && a.applicationInfo.enabled && (a.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0
                    && AppAvailability.installed(c.getPackageManager(),a.applicationInfo) && new HomeController(c).component(home,true);
        }catch(PackageManager.NameNotFoundException|SecurityException unavailable){return false;}
    }
    static boolean enabled(Context c){
        SharedPreferences p=preferences(c);
        if(!configured(c) || !p.getBoolean("windowGuardEnabled",false) || "OFF".equals(p.getString("mode","AUTO"))
                || !Build.FINGERPRINT.equals(p.getString("windowGuardFirmware","")) || !usable(c,original(c)))return false;
        try{return c.getPackageManager().getPackageInfo(ComponentName.unflattenFromString(original(c)).getPackageName(),0).versionCode==p.getInt("windowGuardVersion",-1);}
        catch(PackageManager.NameNotFoundException|SecurityException unavailable){return false;}
    }
    static Set<String> targets(Context c){
        Set<String> result=new LinkedHashSet<>();String home=original(c);
        if(!enabled(c))return result;
        result.add(home);
        try{
            ActivityInfo a=c.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(home),0);
            if(a.targetActivity!=null){
                ComponentName target=new ComponentName(a.packageName,a.targetActivity.startsWith(".")?a.packageName+a.targetActivity:a.targetActivity);
                ActivityInfo actual=c.getPackageManager().getActivityInfo(target,0);
                if(actual.enabled && actual.applicationInfo.enabled)result.add(target.flattenToString());
            }
            // LeTV's HOME trampoline opens a separate activity, not an activity-alias (#121 upstream).
            if(a.packageName.equals("com.stv.deskplatform") && a.name.equals("com.stv.deskplatform.ui.activity.ExternalJumpActivity")){
                ComponentName target=new ComponentName(a.packageName,"com.stv.deskplatform.ui.activity.Launcher");
                try{
                    ActivityInfo actual=c.getPackageManager().getActivityInfo(target,0);
                    if(actual.enabled && actual.applicationInfo.enabled)result.add(target.flattenToString());
                }catch(PackageManager.NameNotFoundException|SecurityException unavailable){/* The registered HOME still applies. */}
            }
        }catch(PackageManager.NameNotFoundException|SecurityException unavailable){result.clear();}
        return result;
    }
    static boolean authorized(Context c){
        if(Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0)!=1)return false;
        String entries=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName own=new ComponentName(c,LegacyWindowHomeGuardService.class);
        if(entries!=null)for(String entry:entries.split(":"))if(own.equals(ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static void configure(Context c,String home,boolean resume)throws Exception {
        if(!supported() || !usable(c,home))throw new IllegalArgumentException("需要 API18 及本机启用的系统 HOME 组件");
        SharedPreferences p=preferences(c);
        if("OFF".equals(p.getString("mode","AUTO")) && !resume)throw new IllegalStateException("接管已暂停，需要明确恢复");
        home=ComponentName.unflattenFromString(home).flattenToString();
        String saved=DeviceMaintenanceReceiver.original(c);
        if(!saved.isEmpty() && !saved.equals(home))throw new IllegalStateException("原桌面与已有恢复记录不一致");
        if(!authorized(c))throw new IllegalStateException("请先通过电脑或系统设置启用 API18 窗口守护无障碍服务");
        int version=c.getPackageManager().getPackageInfo(ComponentName.unflattenFromString(home).getPackageName(),0).versionCode;
        if(!p.edit().putString("windowGuardHome",home).putInt("windowGuardVersion",version).putString("windowGuardFirmware",Build.FINGERPRINT)
                .putBoolean("windowGuardEnabled",true).putString("mode","AUTO").commit())throw new IllegalStateException("窗口守护配置未保存");
    }
    static JSONObject snapshot(Context c)throws JSONException {
        return new JSONObject().put("supported",supported()).put("configured",configured(c)).put("enabled",enabled(c))
                .put("home",original(c)).put("candidates",new JSONArray(supported()?candidates(c):Collections.emptyList()))
                .put("targets",new JSONArray(targets(c))).put("accessibilityAllowed",supported() && authorized(c))
                .put("accessibilityBound",LegacyWindowHomeGuardService.connected())
                .put("lastDispatchedAt",LegacyWindowHomeGuardService.dispatchedAt())
                .put("lastHomeObservedAt",LegacyWindowHomeGuardService.observedAt()).put("physicalHomeVerified",false);
    }
    private LegacyWindowHomeGuard(){}
}
