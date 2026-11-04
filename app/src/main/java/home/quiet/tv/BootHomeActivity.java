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

    /** Run on the catalog worker, after the foreground desktop has submitted its app list. */
    static void reconcile(Context context){
        if(Build.VERSION.SDK_INT<24 || BuildConfig.LEGACY_19 || !BuildConfig.DECLARES_HOME || !HomeGuardState.unlocked(context))return;
        PackageManager pm=context.getPackageManager();ComponentName entry=new ComponentName(context,BootHomeActivity.class);
        try{
            int current=pm.getComponentEnabledSetting(entry);
            // Preserve an explicit external disable; our own inactive state is the manifest default.
            if(current!=PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && current!=PackageManager.COMPONENT_ENABLED_STATE_ENABLED)return;
            ResolveInfo resolved=pm.resolveActivity(HomeController.homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);
            boolean enable=resolved!=null && resolved.activityInfo!=null
                && new ComponentName(context,HomeActivity.class).equals(new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name));
            if(enable)for(ResolveInfo candidate:pm.queryIntentActivities(HomeController.homeIntent(),0)){
                if(candidate.activityInfo!=null && !context.getPackageName().equals(candidate.activityInfo.packageName) && candidate.priority>=-1){enable=false;break;}
            }
            int wanted=enable?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            if(current!=wanted){
                int flags=PackageManager.DONT_KILL_APP;
                if(Build.VERSION.SDK_INT>=30)flags|=PackageManager.SYNCHRONOUS;
                pm.setComponentEnabledSetting(entry,wanted,flags);
                DiagnosticLog.info("home.boot_entry.config","enabled="+enable);
            }
        }catch(RuntimeException unavailable){DiagnosticLog.error("home.boot_entry.config_failed",unavailable);}
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
        if(target.equals(getComponentName()))return;
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
