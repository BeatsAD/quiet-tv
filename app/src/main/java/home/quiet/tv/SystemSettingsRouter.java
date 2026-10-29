package home.quiet.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Installed system entries only. A successful dispatch does not prove that an OEM page stayed open. */
final class SystemSettingsRouter {
    private static final String XIAOMI="com.xiaomi.mitv.settings";
    private static final ComponentName XGIMI=new ComponentName("com.android.newsettings","com.xgimi.newsettings.SettingService");
    private static final ComponentName HAIER=new ComponentName("com.haier.tv.menusetting.ui","com.haier.tv.menusetting.ui.SettingsService");
    private static final ComponentName HISENSE=new ComponentName("com.android.tv.settings","com.hisense.systemsettings.MainActivity");
    private final Context context;
    private final PackageManager pm;
    SystemSettingsRouter(Context context){this.context=context;pm=context.getPackageManager();}

    static boolean xiaomi(String manufacturer,String brand,String model){
        return "xiaomi".equalsIgnoreCase(manufacturer) || "xiaomi".equalsIgnoreCase(brand)
            || model.toLowerCase(Locale.ROOT).startsWith("mitv");
    }
    private static boolean hisense(){return "Hisense".equalsIgnoreCase(Build.MANUFACTURER)||"Hisense".equalsIgnoreCase(Build.BRAND);}
    private static Intent activity(String pkg,String name){return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg,name));}

    List<Intent> candidates(String action){return candidates(action,false);}
    List<Intent> candidates(String action,boolean ethernet){
        ArrayList<Intent> intents=new ArrayList<>();
        if(Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(action)){
            intents.add(new Intent(action));intents.addAll(candidates(Settings.ACTION_SETTINGS,ethernet));return intents;
        }
        if(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS.equals(action)){
            intents.add(new Intent(action));
            // Some MiTV firmware has no exported developer page or standard SETTINGS handler.
            intents.addAll(candidates(Settings.ACTION_SETTINGS,ethernet));
            return intents;
        }
        boolean network=Settings.ACTION_WIFI_SETTINGS.equals(action),settings=Settings.ACTION_SETTINGS.equals(action);
        if(network && hisense()){
            intents.add(new Intent(action).setPackage(HISENSE.getPackageName()));
            intents.add(new Intent(Settings.ACTION_WIRELESS_SETTINGS).setPackage(HISENSE.getPackageName()));
            // Submitted VIDAA firmware exposes the OEM settings root in this same package.
            intents.add(new Intent(Settings.ACTION_SETTINGS).setComponent(HISENSE));
        }
        if(network||settings){
            String page=network?"Settings://com.xgimi.settings.net/wifi_misckey":"Settings://com.xgimi.misckey.main/mainPage";
            intents.add(new Intent("com.xgimi.settings.SETTINGS").setComponent(XGIMI)
                .putExtra("data",page).putExtra("packageName","com.xgimi.misckey").putExtra("unlocked",false));
            if(network){
                if(ethernet)intents.add(new Intent(XIAOMI+".ETHERNET_SETTINGS").setPackage(XIAOMI));
                // MiTV-ANSP0/MFTP0 can resolve the Xiaomi Wi-Fi page then close it immediately (#331).
                // Prefer the TV network page instead of guessing whether a quick Back meant failure.
                intents.add(activity("com.android.tv.settings","com.android.tv.settings.connectivity.NetworkActivity"));
                intents.add(new Intent(XIAOMI+".WIFI_SETTINGS").setComponent(new ComponentName(XIAOMI,XIAOMI+".wifi.MitvWifiActivity")));
                intents.add(new Intent(action));
                intents.add(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                intents.add(new Intent("com.pptv.intent.action.WIFI_ACTIVITY").setComponent(new ComponentName("com.pptv.setting","com.pptv.setting.wifi.WifiWorkActivity")));
                intents.add(new Intent(Intent.ACTION_VIEW,Uri.parse(page)));
            }
            intents.add(new Intent(XIAOMI+".SETTINGS").setComponent(new ComponentName(XIAOMI,XIAOMI+".entry.MainActivity")));
            // Haier's full settings is an overlay service; RootActivity opens the source menu.
            intents.add(new Intent("com.haier.settings.intent.action.SettingsService").setComponent(HAIER).putExtra("command",0));
            intents.add(activity("com.tcl.settings","com.tcl.settings.ui.MainActivity"));
            intents.add(activity("com.pptv.setting","com.pptv.setting.root.SettingsActivity"));
            intents.add(activity("com.helios.setting","com.helios.activity.StartActivity"));
        }else intents.add(new Intent(action));
        intents.add(new Intent(Settings.ACTION_SETTINGS));
        if(!network)intents.add(new Intent(Intent.ACTION_VIEW,Uri.parse("Settings://com.xgimi.misckey.main/mainPage")));
        intents.add(activity("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity"));
        return intents;
    }

    @SuppressWarnings("deprecation")
    boolean open(String action){
        DiagnosticOperation diagnostic=new DiagnosticOperation("settings",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),DiagnosticLog::info);
        DiagnosticLog.info("settings.requested","attempt="+diagnostic.id+" action="+action);
        boolean ethernet=false;
        if(Settings.ACTION_WIFI_SETTINGS.equals(action)){
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active=manager==null?null:manager.getActiveNetworkInfo();
            ethernet=active!=null && active.isConnected() && active.getType()==ConnectivityManager.TYPE_ETHERNET;
            DiagnosticLog.info("settings.network","service="+(manager!=null)+" active="+(active!=null)+" connected="+(active!=null && active.isConnected())+" type="+(active==null?-1:active.getType()));
        }
        DiagnosticLog.info("settings.request","action="+action+" ethernet="+ethernet);
        diagnostic.stage("ROUTING",android.os.SystemClock.elapsedRealtime());
        HashSet<String> attempted=new HashSet<>();int candidate=0;
        for(Intent intent:candidates(action,ethernet)){
            String route="attempt="+diagnostic.id+" request="+action+" candidate="+(++candidate)+" "+DiagnosticLog.intent(intent);
            try{
                boolean service=XGIMI.equals(intent.getComponent()) || HAIER.equals(intent.getComponent());
                ResolveInfo resolved=service?pm.resolveService(intent,0):pm.resolveActivity(intent,0);
                ComponentInfo info=resolved==null?null:service?resolved.serviceInfo:resolved.activityInfo;
                if(info==null){DiagnosticLog.info("settings.route",route+" result=unresolved");continue;}
                if(Settings.ACTION_WIFI_SETTINGS.equals(action) && hisense() && HISENSE.getPackageName().equals(intent.getPackage())
                    && !info.name.startsWith("com.hisense.")){DiagnosticLog.info("settings.route",route+" result=not_hisense_network");continue;}
                String rejection=rejection(info,service?((ServiceInfo)info).permission:((ActivityInfo)info).permission);
                route+=" resolved="+new ComponentName(info.packageName,info.name).flattenToShortString();
                if(!rejection.isEmpty()){DiagnosticLog.info("settings.route",route+" result="+rejection);continue;}
                ComponentName target=new ComponentName(info.packageName,info.name);
                if(!attempted.add(target.flattenToString())){DiagnosticLog.info("settings.route",route+" result=duplicate");continue;}
                if(!service && isHome((ActivityInfo)info)){DiagnosticLog.info("settings.route",route+" result=home_handler");continue;}
                Intent launch=new Intent(intent).setComponent(target);
                if(service){if(context.startService(launch)==null){DiagnosticLog.info("settings.route",route+" result=null_service");continue;}}
                else{
                    // AOSP's translucent panel belongs above Home, without a separate black task.
                    boolean panel=context instanceof android.app.Activity && "com.android.tv.settings".equals(info.packageName)
                        && "com.android.tv.settings.MainSettings".equals(info.name);
                    if(!panel)launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                }
                DiagnosticLog.info("settings.dispatch",route+" type="+(service?"service":"activity")+" result=dispatched_unverified");
                try{SettingsPageCapture.observe(context);}catch(RuntimeException unavailable){DiagnosticLog.error("settings.capture.unavailable",unavailable);}
                boolean systemFallback=!Settings.ACTION_SETTINGS.equals(action) && (Settings.ACTION_SETTINGS.equals(intent.getAction())
                        || (XIAOMI+".entry.MainActivity").equals(info.name));
                diagnostic.finish("DISPATCHED_UNVERIFIED",systemFallback?"system_settings_fallback":"candidate_dispatched",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime());
                return true;
            }catch(RuntimeException failure){
                DiagnosticLog.error("settings.route.failed",route,failure);
            }
        }
        DiagnosticLog.info("settings.unavailable",action);
        diagnostic.finish("UNAVAILABLE","no_usable_candidate",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime());
        return false;
    }
    private String rejection(ComponentInfo info,String permission){
        // Also reject these when an OEM registers them for the standard SETTINGS action.
        if(("com.haier.tv.menusetting".equals(info.packageName) && "com.haier.tv.menusetting.RootActivity".equals(info.name))
            || ("com.haier.bluetooth.settings".equals(info.packageName) && "com.haier.bluetooth.settings.SettingsActivity".equals(info.name)))return "not_full_settings";
        if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return "not_system";
        if(!info.exported)return "not_exported";
        if(info.name.contains("CTSDummy") || "com.android.internal.app.ResolverActivity".equals(info.name) || "com.android.internal.app.ChooserActivity".equals(info.name))return "resolver_or_dummy";
        int app=pm.getApplicationEnabledSetting(info.packageName),component=pm.getComponentEnabledSetting(new ComponentName(info.packageName,info.name));
        if(!(app==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.applicationInfo.enabled:app==PackageManager.COMPONENT_ENABLED_STATE_ENABLED))return "app_disabled state="+app;
        if(!(component==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.enabled:component==PackageManager.COMPONENT_ENABLED_STATE_ENABLED))return "component_disabled state="+component;
        return permission!=null && pm.checkPermission(permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED?"permission_denied permission="+permission:"";
    }
    private boolean isHome(ActivityInfo activity){
        for(ResolveInfo candidate:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)){
            ActivityInfo home=candidate.activityInfo;
            if(home!=null && home.packageName.equals(activity.packageName)
                && (home.name.equals(activity.name) || (activity.targetActivity!=null && activity.targetActivity.equals(home.name))))return true;
        }
        return false;
    }
}
