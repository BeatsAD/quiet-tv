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
    private static final String DANGBEI="com.dangbei.leard.settings";
    static final String XIAOMI_POPUP="com.xiaomi.intent.action.show.globalsetting";
    private static final String NETWORK="android.settings.NETWORK_SETTINGS";
    private static final ComponentName KONKA=new ComponentName("com.konka.tvsettings","com.konka.service.SettingService");
    private static final ComponentName XGIMI=new ComponentName("com.android.newsettings","com.xgimi.newsettings.SettingService");
    private static final ComponentName HAIER=new ComponentName("com.haier.tv.menusetting.ui","com.haier.tv.menusetting.ui.SettingsService");
    private static final ComponentName HISENSE=new ComponentName("com.android.tv.settings","com.hisense.systemsettings.MainActivity");
    enum LaunchType { ACTIVITY, SERVICE, BROADCAST }
    enum Page { NETWORK, WIFI, ETHERNET, SETTINGS, OTHER }
    private final Context context;
    private final PackageManager pm;
    SystemSettingsRouter(Context context){this.context=context;pm=context.getPackageManager();}

    static boolean xiaomi(String manufacturer,String brand,String model){
        return "xiaomi".equalsIgnoreCase(manufacturer) || "xiaomi".equalsIgnoreCase(brand)
            || model.toLowerCase(Locale.ROOT).startsWith("mitv");
    }
    private static boolean hisense(){return "Hisense".equalsIgnoreCase(Build.MANUFACTURER)||"Hisense".equalsIgnoreCase(Build.BRAND);}
    private static Intent activity(String pkg,String name){return new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg,name));}
    private static Intent xiaomiPopup(){return new Intent(XIAOMI_POPUP).setPackage(XIAOMI);}
    static boolean preferXiaomiPopup(int sdk,String fingerprint){
        // Upstream #834: this MiBOX3S firmware finishes MainActivity over another HOME.
        // Other firmware retains the existing order and can use the explicit alternate entry.
        return sdk==23 && "Xiaomi/queenchristina/queenchristina:6.0.1/MOB31S/838:user/release-keys".equals(fingerprint);
    }
    static LaunchType launchType(Intent intent){
        if(XIAOMI_POPUP.equals(intent.getAction()) && XIAOMI.equals(intent.getPackage()))return LaunchType.BROADCAST;
        ComponentName component=intent.getComponent();
        return XGIMI.equals(component)||HAIER.equals(component)||KONKA.equals(component)?LaunchType.SERVICE:LaunchType.ACTIVITY;
    }
    static Page page(Intent intent){
        String action=intent.getAction();ComponentName component=intent.getComponent();
        if(NETWORK.equals(action)||component!=null&&"com.android.tv.settings.connectivity.NetworkActivity".equals(component.getClassName()))return Page.NETWORK;
        if((XIAOMI+".ETHERNET_SETTINGS").equals(action))return Page.ETHERNET;
        if(Settings.ACTION_WIFI_SETTINGS.equals(action)||(XIAOMI+".WIFI_SETTINGS").equals(action)||"com.pptv.intent.action.WIFI_ACTIVITY".equals(action))return Page.WIFI;
        if(Settings.ACTION_WIRELESS_SETTINGS.equals(action))return Page.NETWORK;
        String link=intent.getDataString();
        if(XGIMI.equals(component))link=intent.getStringExtra("data");
        if(link!=null&&link.startsWith("Settings://"))return link.contains("/wifi_misckey")?Page.WIFI:Page.SETTINGS;
        if(Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(action)||Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS.equals(action))return Page.OTHER;
        return Page.SETTINGS;
    }
    static boolean settingsFallback(String action,Page page){
        return !Settings.ACTION_SETTINGS.equals(action)&&!XIAOMI_POPUP.equals(action)&&page==Page.SETTINGS;
    }
    private void dangbeiSettings(List<Intent> intents){
        intents.add(new Intent(Settings.ACTION_SETTINGS).setPackage(DANGBEI));
        try{
            // B1S/q200 reports SliceSettingsActivity without a full class name. Read it from the installed package.
            ActivityInfo[] activities=pm.getPackageInfo(DANGBEI,PackageManager.GET_ACTIVITIES).activities;
            if(activities!=null)for(ActivityInfo info:activities)
                if(info.name!=null && info.name.endsWith(".SliceSettingsActivity"))intents.add(activity(DANGBEI,info.name));
        }catch(PackageManager.NameNotFoundException missing){/* Other firmware keeps the existing routes. */}
        catch(RuntimeException unavailable){DiagnosticLog.error("settings.dangbei.discovery_failed",unavailable);}
    }

    List<Intent> candidates(String action){return candidates(action,false);}
    List<Intent> candidates(String action,boolean ethernet){
        ArrayList<Intent> intents=new ArrayList<>();
        if(XIAOMI_POPUP.equals(action)){intents.add(xiaomiPopup());return intents;}
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
            intents.add(new Intent(NETWORK).setComponent(HISENSE));
            intents.add(new Intent(action).setPackage(HISENSE.getPackageName()));
            intents.add(new Intent(Settings.ACTION_WIRELESS_SETTINGS).setPackage(HISENSE.getPackageName()));
            // Submitted VIDAA firmware exposes the OEM settings root in this same package.
            intents.add(new Intent(Settings.ACTION_SETTINGS).setComponent(HISENSE));
        }
        if(network)intents.add(new Intent(action).setPackage(DANGBEI));
        if(settings)dangbeiSettings(intents);
        if(network||settings){
            if(settings&&preferXiaomiPopup(Build.VERSION.SDK_INT,Build.FINGERPRINT))intents.add(xiaomiPopup());
            String page=network?"Settings://com.xgimi.settings.net/wifi_misckey":"Settings://com.xgimi.misckey.main/mainPage";
            intents.add(new Intent("com.xgimi.settings.SETTINGS").setComponent(XGIMI)
                .putExtra("data",page).putExtra("packageName","com.xgimi.misckey").putExtra("unlocked",false));
            if(network){
                if(ethernet)intents.add(new Intent(XIAOMI+".ETHERNET_SETTINGS").setPackage(XIAOMI));
                intents.add(new Intent(NETWORK));
                // MiTV-ANSP0/MFTP0 can resolve the Xiaomi Wi-Fi page then close it immediately (#331).
                // Prefer the TV network page instead of guessing whether a quick Back meant failure.
                intents.add(activity("com.android.tv.settings","com.android.tv.settings.connectivity.NetworkActivity"));
                intents.add(new Intent(XIAOMI+".WIFI_SETTINGS").setComponent(new ComponentName(XIAOMI,XIAOMI+".wifi.MitvWifiActivity")));
                intents.add(new Intent(action));
                intents.add(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                intents.add(new Intent("com.pptv.intent.action.WIFI_ACTIVITY").setComponent(new ComponentName("com.pptv.setting","com.pptv.setting.wifi.WifiWorkActivity")));
                intents.add(new Intent(Intent.ACTION_VIEW,Uri.parse(page)));
                // An unplugged Ethernet connection still needs an entry for configuration.
                if(!ethernet)intents.add(new Intent(XIAOMI+".ETHERNET_SETTINGS").setPackage(XIAOMI));
            }
            if(network)dangbeiSettings(intents);
            intents.add(new Intent("com.konka.settings.action.SETTING_MAIN_VIEW").setComponent(KONKA));
            intents.add(new Intent(XIAOMI+".SETTINGS").setComponent(new ComponentName(XIAOMI,XIAOMI+".entry.MainActivity")));
            intents.add(xiaomiPopup());
            // Haier's full settings is an overlay service; RootActivity opens the source menu.
            intents.add(new Intent("com.haier.settings.intent.action.SettingsService").setComponent(HAIER).putExtra("command",0));
            intents.add(activity("com.tcl.settings","com.tcl.settings.ui.MainActivity"));
            intents.add(activity("com.pptv.setting","com.pptv.setting.root.SettingsActivity"));
            intents.add(activity("com.helios.setting","com.helios.activity.StartActivity"));
        }else intents.add(new Intent(action));
        intents.add(new Intent(Settings.ACTION_SETTINGS));
        if(network||settings){
            intents.add(activity("com.konka.tvsettings","com.konka.settingcenter.settings.view.MainSettingsActivity"));
            intents.add(activity("com.konka.systemsetting","com.konka.systemsetting.MainActivity"));
        }
        if(!network)intents.add(new Intent(Intent.ACTION_VIEW,Uri.parse("Settings://com.xgimi.misckey.main/mainPage")));
        intents.add(activity("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity"));
        return intents;
    }

    private List<ComponentInfo> targets(Intent intent){
        ArrayList<ComponentInfo> values=new ArrayList<>();LaunchType type=launchType(intent);
        if(type==LaunchType.BROADCAST){
            List<ResolveInfo> receivers=pm.queryBroadcastReceivers(intent,0);
            if(receivers!=null)for(ResolveInfo receiver:receivers)if(receiver!=null&&receiver.activityInfo!=null)values.add(receiver.activityInfo);
        }else{
            ResolveInfo resolved=type==LaunchType.SERVICE?pm.resolveService(intent,0):pm.resolveActivity(intent,0);
            ComponentInfo info=resolved==null?null:type==LaunchType.SERVICE?resolved.serviceInfo:resolved.activityInfo;
            if(info!=null)values.add(info);
        }
        return values;
    }
    private String rejection(String action,Intent intent,ComponentInfo info){
        if(Settings.ACTION_WIFI_SETTINGS.equals(action)&&hisense()&&HISENSE.getPackageName().equals(intent.getPackage())
                && !info.name.startsWith("com.hisense."))return "not_hisense_network";
        String reason=rejection(info,info instanceof ServiceInfo?((ServiceInfo)info).permission:((ActivityInfo)info).permission);
        if(reason.isEmpty()&&launchType(intent)==LaunchType.ACTIVITY&&isHome((ActivityInfo)info))return "home_handler";
        return reason;
    }
    boolean available(String action){
        for(Intent intent:candidates(action))try{
            for(ComponentInfo info:targets(intent))if(rejection(action,intent,info).isEmpty())return true;
        }catch(RuntimeException unavailable){DiagnosticLog.error("settings.inspect.failed",unavailable);}
        return false;
    }

    static final class Route {
        final Intent template,launch;
        final LaunchType type;
        final Page page;
        final String identity;
        Route(Intent intent,ComponentInfo info){
            template=new Intent(intent);launch=new Intent(intent).setComponent(new ComponentName(info.packageName,info.name));
            type=launchType(intent);page=page(intent);identity=SettingsRouteMemory.identity(type+":"+launch.toUri(0));
        }
    }
    boolean callable(String action,Route route){
        try{for(ComponentInfo info:targets(route.template))
            if(route.launch.getComponent().equals(new ComponentName(info.packageName,info.name))&&rejection(action,route.template,info).isEmpty())return true;
        }catch(RuntimeException unavailable){DiagnosticLog.error("settings.inspect.failed",unavailable);}
        return false;
    }
    private List<Route> routes(String action,boolean ethernet){
        ArrayList<Route> result=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(Intent intent:candidates(action,ethernet))try{
            for(ComponentInfo info:targets(intent)){
                String rejection=rejection(action,intent,info);
                if(!rejection.isEmpty()){DiagnosticLog.info("settings.route",DiagnosticLog.intent(intent)+" result="+rejection);continue;}
                Route route=new Route(intent,info);if(seen.add(route.identity))result.add(route);
            }
        }catch(RuntimeException failure){DiagnosticLog.error("settings.route.failed",failure);}
        return result;
    }
    boolean open(String action){return open(action,false,java.util.Collections.emptySet())!=null;}
    @SuppressWarnings("deprecation")
    SettingsRouteMemory.Attempt open(String action,boolean trial,java.util.Set<String> excluded){
        DiagnosticOperation diagnostic=new DiagnosticOperation("settings",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),DiagnosticLog::info);
        DiagnosticLog.info("settings.requested","attempt="+diagnostic.id+" action="+action+" trial="+trial);
        boolean ethernet=false;
        if(Settings.ACTION_WIFI_SETTINGS.equals(action)){
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active=manager==null?null:manager.getActiveNetworkInfo();
            ethernet=active!=null&&active.isConnected()&&active.getType()==ConnectivityManager.TYPE_ETHERNET;
        }
        String slot=action+"|"+ethernet;SettingsRouteMemory memory=new SettingsRouteMemory(context);
        List<Route> routes=routes(action,ethernet);
        // A record can only reorder newly resolved built-in candidates, never supply an Intent.
        for(int i=0;i<routes.size();i++)if(!settingsFallback(action,routes.get(i).page)&&memory.preferred(slot,routes.get(i))){Route preferred=routes.remove(i);routes.add(0,preferred);break;}
        diagnostic.stage("ROUTING",android.os.SystemClock.elapsedRealtime());
        for(Route route:routes){
            if(excluded.contains(route.identity))continue;
            if(!callable(action,route))continue;
            try{
                String scope=SettingsRouteMemory.scope(context,route);
                Intent launch=new Intent(route.launch);ComponentName target=launch.getComponent();
                if(route.type==LaunchType.BROADCAST)context.sendBroadcast(launch);
                else if(route.type==LaunchType.SERVICE){if(context.startService(launch)==null){memory.failed(slot,route);continue;}}
                else{
                    boolean panel=context instanceof android.app.Activity&&"com.android.tv.settings".equals(target.getPackageName())&&"com.android.tv.settings.MainSettings".equals(target.getClassName());
                    if(!panel)launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                }
                SettingsRouteMemory.Attempt attempt=new SettingsRouteMemory.Attempt(action,slot,route,scope,trial);
                DiagnosticLog.info("settings.dispatch","attempt="+diagnostic.id+" resolved="+target.flattenToShortString()+" type="+route.type+" page="+route.page+" result=dispatched_unverified");
                try{SettingsPageCapture.observe(context,attempt);}catch(RuntimeException unavailable){DiagnosticLog.error("settings.capture.unavailable",unavailable);}
                boolean fallback=settingsFallback(action,route.page);
                diagnostic.finish("DISPATCHED_UNVERIFIED",fallback?"system_settings_fallback":"candidate_dispatched",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime());
                return attempt;
            }catch(Exception failure){memory.failed(slot,route);DiagnosticLog.error("settings.route.failed",failure);}
        }
        diagnostic.finish("UNAVAILABLE","no_usable_candidate",System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime());return null;
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
        List<ResolveInfo> homes=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
        if(homes==null)return false;
        for(ResolveInfo candidate:homes){
            if(candidate==null)continue;
            ActivityInfo home=candidate.activityInfo;
            if(home!=null && home.packageName.equals(activity.packageName)
                && (home.name.equals(activity.name) || (activity.targetActivity!=null && activity.targetActivity.equals(home.name))))return true;
        }
        return false;
    }
}
