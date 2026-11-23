package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;

/** Reads observed configuration only. It never applies HOME, package or accessibility changes. */
final class SystemDiagnosticTimeline {
    private static SystemDiagnosticTimeline instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private String previous="unknown";
    private final SharedPreferences.OnSharedPreferenceChangeListener changed=(prefs,key)->{
        if(key!=null&&java.util.Arrays.asList("mode","configured","observeHome","directHomeKeys","home","relaySource","adbGuardEnabled","keepAdb").contains(key))record("preference_"+key);
    };
    private SystemDiagnosticTimeline(Context context){this.context=context.getApplicationContext();preferences=new HomeGuardState(context,false).preferences;}
    static synchronized void init(Context context){
        if(instance!=null)return;
        try{instance=new SystemDiagnosticTimeline(context);instance.start();}catch(RuntimeException failure){DiagnosticLog.info("system.configuration.result","status=observer_unavailable type="+failure.getClass().getSimpleName());}
    }
    private void start(){
        preferences.registerOnSharedPreferenceChangeListener(changed);record("process_start");
        ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean self){record("accessibility_settings");}};
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),false,observer);
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),false,observer);
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_CHANGED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");
        androidx.core.content.ContextCompat.registerReceiver(context,new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){record("package_broadcast");}},filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    private synchronized void record(String reason){
        if(!DiagnosticLog.recording())return;
        String current=read(context);if(current.equals(previous)&&!reason.equals("process_start"))return;
        String id=DiagnosticOperation.nextId(System.currentTimeMillis());
        DiagnosticLog.info("system.configuration.begin","operation="+id+" source=observer reason="+reason+" before=last_observed "+previous);
        DiagnosticLog.info("system.configuration.result","operation="+id+" source=observer reason="+reason+" readback=observed "+current);previous=current;
    }
    static String begin(Context context,String source,String operation){
        if(!DiagnosticLog.recording())return "";
        String id=DiagnosticOperation.nextId(System.currentTimeMillis());
        DiagnosticLog.info("system.operation.begin","operation="+id+" source="+source+" action="+operation+" "+read(context));return id;
    }
    static void end(Context context,String id,String source,String operation,int result){
        if(!DiagnosticLog.recording()||id.isEmpty())return;
        DiagnosticLog.info("system.operation.result","operation="+id+" source="+source+" action="+operation+" callbackResult="+result+" readback=observed "+read(context));
    }
    static boolean maintenance(String operation){return java.util.Arrays.asList("pause","finish_restore","proxy_prepare","proxy_finish","proxy_repair","takeover_preference","takeover_finish",
        "adb_guard_configure","window_guard_configure","boot_home_reconcile","rules_set","takeover_record","management_config").contains(operation);}
    static String read(Context context){
        try{
            HomeGuardState guard=new HomeGuardState(context,false);SharedPreferences p=guard.preferences;
            HomeController.State home=new HomeController(context).inspect();String original=home.original;
            ComponentName component=ComponentName.unflattenFromString(original);String enabled="unknown",available="unknown";
            if(component!=null)try{enabled=Integer.toString(context.getPackageManager().getApplicationEnabledSetting(component.getPackageName()));available=Boolean.toString(context.getPackageManager().getApplicationInfo(component.getPackageName(),HomeController.METADATA).enabled);}catch(PackageManager.NameNotFoundException|IllegalArgumentException missing){enabled="not_installed";}
            return "readbackSource=package_manager_and_preferences homeKnown="+home.known+" home="+safe(home.resolved)+" original="+safe(original)+" originalEnabledSetting="+enabled+" originalEnabled="+available
                +" guardMode="+("OFF".equals(p.getString("mode","AUTO"))?"OFF":"AUTO")+" configured="+p.getBoolean("configured",false)+" observeHome="+p.getBoolean("observeHome",true)
                +" directHomeKeys="+p.getBoolean("directHomeKeys",false)+" accessibilityEnabled="+Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,-1)
                +" ownServiceEnabled="+guard.serviceEnabled()+" user="+android.os.Process.myUid()/100000;
        }catch(Exception failure){return "readback=unknown type="+failure.getClass().getSimpleName();}
    }
    private static String safe(String component){return component!=null&&component.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")?component:"unknown";}
}
