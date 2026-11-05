package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.View;

/** Draw a HOME before user storage unlocks, without initializing the desktop or changing its owner. */
public final class BootHomeActivity extends Activity {
    private boolean listening,forwarded;
    private final BroadcastReceiver unlock=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){forward();}
    };

    @androidx.annotation.ChecksSdkIntAtLeast(api=24)
    private static boolean supported(){return Build.VERSION.SDK_INT>=24 && !BuildConfig.LEGACY_19 && BuildConfig.DECLARES_HOME;}
    private static boolean required(Context context){
        PackageManager pm=context.getPackageManager();
        int flags=PackageManager.MATCH_DIRECT_BOOT_AWARE|PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        ResolveInfo resolved=pm.resolveActivity(HomeController.homeIntent(),flags|PackageManager.MATCH_DEFAULT_ONLY);
        if(resolved==null || resolved.activityInfo==null || !context.getPackageName().equals(resolved.activityInfo.packageName))return false;
        for(ResolveInfo candidate:pm.queryIntentActivities(HomeController.homeIntent(),flags))
            if(candidate.activityInfo!=null && !context.getPackageName().equals(candidate.activityInfo.packageName)
                    && candidate.priority>=-1 && !"android".equals(candidate.activityInfo.packageName)
                    && !candidate.activityInfo.name.endsWith("FallbackHome"))return false;
        return true;
    }
    /** Package replacement and locked boot must prepare the entry without opening credential storage. */
    static void reconcile(Context context){
        if(!supported())return;
        PackageManager pm=context.getPackageManager();ComponentName entry=new ComponentName(context,BootHomeActivity.class);
        try{
            int current=pm.getComponentEnabledSetting(entry);
            // Preserve an explicit external disable; our own inactive state is the manifest default.
            if(current!=PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && current!=PackageManager.COMPONENT_ENABLED_STATE_ENABLED)return;
            boolean enable=required(context);
            int wanted=enable?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            if(current!=wanted){
                int flags=PackageManager.DONT_KILL_APP;
                if(Build.VERSION.SDK_INT>=30)flags|=PackageManager.SYNCHRONOUS;
                pm.setComponentEnabledSetting(entry,wanted,flags);
                DiagnosticLog.info("home.boot_entry.config","enabled="+enable);
            }
        }catch(RuntimeException unavailable){DiagnosticLog.error("home.boot_entry.config_failed",unavailable);}
    }

    static org.json.JSONObject snapshot(Context context) throws org.json.JSONException {
        org.json.JSONObject value=new org.json.JSONObject().put("supported",supported()).put("required",false).put("enabled",false).put("ready",!supported());
        if(!supported())return value;
        boolean needed=required(context);value.put("required",needed);
        try{
            PackageManager pm=context.getPackageManager();ComponentName entry=new ComponentName(context,BootHomeActivity.class);
            ActivityInfo info=pm.getActivityInfo(entry,HomeController.METADATA);
            int setting=pm.getComponentEnabledSetting(entry);
            boolean enabled=setting==PackageManager.COMPONENT_ENABLED_STATE_ENABLED || setting==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && info.enabled;
            return value.put("setting",setting).put("enabled",enabled).put("ready",!needed || enabled && info.directBootAware);
        }catch(PackageManager.NameNotFoundException|SecurityException unavailable){return value.put("ready",!needed);}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        View waiting=new View(this);waiting.setBackgroundResource(R.drawable.home_startup_background);setContentView(waiting);
        DiagnosticLog.info("home.boot_entry.created","uptimeMs="+SystemClock.uptimeMillis()+" unlocked="+HomeGuardState.unlocked(this));
        if(!HomeGuardState.unlocked(this)){
            androidx.core.content.ContextCompat.registerReceiver(this,unlock,new IntentFilter(Intent.ACTION_USER_UNLOCKED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            listening=true;
        }
        forward();
    }
    @Override protected void onResume(){super.onResume();forward();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);forward();}
    private void forward(){
        if(forwarded || !HomeGuardState.unlocked(this))return;
        Intent home=HomeController.homeIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        ResolveInfo resolved=getPackageManager().resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
        if(resolved==null || resolved.activityInfo==null)return;
        ComponentName target=new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name);
        if(target.equals(getComponentName()))target=new ComponentName(this,HomeActivity.class);
        // Resolve again after unlock; a user-selected original desktop or proxy remains authoritative.
        if(!"android".equals(target.getPackageName()))home.setComponent(target);
        try{
            forwarded=true;startActivity(home);
            DiagnosticLog.info("home.boot_entry.dispatched","uptimeMs="+SystemClock.uptimeMillis()+" target="+target.flattenToShortString());
            finish();
        }catch(ActivityNotFoundException|SecurityException unavailable){forwarded=false;DiagnosticLog.error("home.boot_entry.dispatch_failed",unavailable);}
    }
    @Override protected void onDestroy(){if(listening)unregisterReceiver(unlock);super.onDestroy();}
}
