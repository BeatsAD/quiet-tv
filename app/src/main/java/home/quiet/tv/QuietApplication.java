package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.os.Bundle;

/** Process startup, rather than opening the export dialog, starts recording. */
public class QuietApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();DiagnosticLog.init(this);
        if(HomeGuardState.unlocked(this))new DesktopRoutes(this).restoreAfterUpgrade();
        HomeGuardRecovery.request(this,"进程启动");
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity activity,Bundle state){KeyFeedback.attach(activity.getWindow().getDecorView());DiagnosticLog.info("page.create",activity.getClass().getSimpleName());}
            public void onActivityResumed(Activity activity){KeySounds.prepare(activity);DiagnosticLog.info("page.resume",activity.getClass().getSimpleName());if(activity instanceof HomeActivity)HomeGuardService.homeResumed(((HomeActivity)activity).desktopInitialized());}
            public void onActivityPaused(Activity activity){DiagnosticLog.info("page.pause",activity.getClass().getSimpleName());if(activity instanceof HomeActivity)HomeGuardService.homeResumed(false);}
            public void onActivityStarted(Activity activity){}
            public void onActivityStopped(Activity activity){}
            public void onActivitySaveInstanceState(Activity activity,Bundle state){}
            public void onActivityDestroyed(Activity activity){}
        });
        HomeGuardSync.refresh(this);HomeGuardAdb.watch(this);
        if(HomeGuardState.unlocked(this))ManagementAdb.get(this).request();
        BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
            DiagnosticLog.info("screen",intent.getAction());
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){KeySounds.stop();SettingsPageCapture.stopCurrent("screen_off");}
            if(Intent.ACTION_USER_UNLOCKED.equals(intent.getAction()))new DesktopRoutes(context).restoreAfterUpgrade();
            if(!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){HomeGuardSync.refresh(context);HomeGuardAdb.ensure(context);HomeGuardRecovery.request(context,"亮屏或解锁恢复");if(HomeGuardState.unlocked(context))ManagementAdb.get(context).request();}
        }};
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        if(android.os.Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);
        androidx.core.content.ContextCompat.registerReceiver(this,screen,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
