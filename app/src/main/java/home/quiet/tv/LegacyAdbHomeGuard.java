package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import org.json.JSONObject;

/** Opt-in API19 shell observer. It never changes HOME, stock packages or debugging settings. */
final class LegacyAdbHomeGuard {
    static boolean supported(){return BuildConfig.LEGACY_19 && Build.VERSION.SDK_INT==19 && BuildConfig.DECLARES_HOME;}
    static String original(Context c){return new HomeGuardState(c,false).preferences.getString("adbGuardHome","");}
    static boolean configured(Context c){return supported() && !original(c).isEmpty();}
    static boolean enabled(Context c){
        android.content.SharedPreferences p=new HomeGuardState(c,false).preferences;
        return configured(c) && p.getBoolean("adbGuardEnabled",false) && !"OFF".equals(p.getString("mode","AUTO"))
                && Build.FINGERPRINT.equals(p.getString("adbGuardFirmware",""));
    }
    static boolean usable(Context c,String home){
        ComponentName name=ComponentName.unflattenFromString(home);
        if(name==null || name.getPackageName().equals(c.getPackageName()) || HomeGuardPolicy.excludedHome(name.getPackageName()))return false;
        try{
            ActivityInfo a=c.getPackageManager().getActivityInfo(name,0);
            if(!a.enabled || !a.applicationInfo.enabled || !a.exported || (a.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
            if(new HomeController(c).component(home,true))return true;
            // The submitted script identifies this vendor launcher. Never observe its settings/player pages.
            Intent launch=c.getPackageManager().getLaunchIntentForPackage(name.getPackageName());
            return "com.tianci.movieplatform".equals(name.getPackageName()) && launch!=null && name.equals(launch.getComponent());
        }catch(PackageManager.NameNotFoundException|SecurityException unavailable){return false;}
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static void configure(Context c,String home,int port,boolean resume) throws Exception {
        if(!supported() || port<1 || port>65535 || !usable(c,home))throw new IllegalArgumentException("本机不支持此守护或原桌面组件／端口无效");
        home=ComponentName.unflattenFromString(home).flattenToString();
        HomeGuardState state=new HomeGuardState(c,false);
        if("OFF".equals(state.preferences.getString("mode","AUTO")) && !resume)throw new IllegalStateException("接管已暂停，需要明确恢复");
        String saved=DeviceMaintenanceReceiver.original(c);
        if(!saved.isEmpty() && !saved.equals(home))throw new IllegalStateException("原桌面与已有恢复记录不一致");
        if(!c.getSharedPreferences("adb",0).edit().putInt("port",port).commit())throw new IllegalStateException("本机端口未保存");
        if(!state.preferences.edit().putString("adbGuardHome",home).putString("adbGuardFirmware",Build.FINGERPRINT)
                .putBoolean("adbGuardEnabled",true).putString("mode","AUTO").commit())throw new IllegalStateException("守护配置未保存");
        updateBootReceiver(c);
        ManagementAdb adb=ManagementAdb.get(c);
        if(!adb.adb.connected() || adb.adb.connectedPort()!=port){adb.disconnect();adb.requestAuthorization();}
        refresh(c);
    }
    static void refresh(Context c){
        if(enabled(c))c.startService(new Intent(c,LegacyAdbHomeGuardService.class));
    }
    static void updateBootReceiver(Context c){
        if(!BuildConfig.LEGACY_19)return;
        c.getPackageManager().setComponentEnabledSetting(new ComponentName(c,LegacyAdbHomeGuardReceiver.class),
                enabled(c) || HomeGuardAdb.enabled(c)?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
    }
    static boolean bootEnabled(Context c){return c.getPackageManager().getComponentEnabledSetting(new ComponentName(c,LegacyAdbHomeGuardReceiver.class))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
    static void stop(Context c){
        updateBootReceiver(c);
        c.stopService(new Intent(c,LegacyAdbHomeGuardService.class));
    }
    static JSONObject snapshot(Context c) throws org.json.JSONException {
        ManagementAdb adb=ManagementAdb.existing();
        return new JSONObject().put("supported",supported()).put("configured",configured(c)).put("enabled",enabled(c))
                .put("home",original(c)).put("running",LegacyAdbHomeGuardService.running())
                .put("bootEnabled",bootEnabled(c))
                .put("adbConnected",adb!=null && adb.adb.connected()).put("lastLaunchVerifiedAt",LegacyAdbHomeGuardService.lastVerifiedAt())
                .put("port",c.getSharedPreferences("adb",0).getInt("port",5555))
                .put("status",LegacyAdbHomeGuardService.status()).put("physicalHomeVerified",false);
    }
    private LegacyAdbHomeGuard(){}
}
