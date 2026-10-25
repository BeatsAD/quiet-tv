package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.os.Bundle;

/** Process startup, rather than opening the export dialog, starts recording. */
public class QuietApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();DiagnosticLog.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity activity,Bundle state){DiagnosticLog.info("page.create",activity.getClass().getSimpleName());}
            public void onActivityResumed(Activity activity){DiagnosticLog.info("page.resume",activity.getClass().getSimpleName());}
            public void onActivityPaused(Activity activity){DiagnosticLog.info("page.pause",activity.getClass().getSimpleName());}
            public void onActivityStarted(Activity activity){}
            public void onActivityStopped(Activity activity){}
            public void onActivitySaveInstanceState(Activity activity,Bundle state){}
            public void onActivityDestroyed(Activity activity){}
        });
        HomeGuardSync.refresh(this);HomeGuardAdb.watch(this);
        BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
            DiagnosticLog.info("screen",intent.getAction());
            if(!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){HomeGuardSync.refresh(context);HomeGuardAdb.ensure(context);HomeGuardRecovery.request(context,"亮屏或解锁恢复");}
        }};
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);
        if(android.os.Build.VERSION.SDK_INT>=24)filter.addAction(Intent.ACTION_USER_UNLOCKED);
        androidx.core.content.ContextCompat.registerReceiver(this,screen,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}
