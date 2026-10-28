package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.provider.Settings;

/** Boot and package replacement resume existing recovery; they never grant permissions. */
public final class HomeGuardBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context supplied,Intent intent){
        String action=intent.getAction();
        boolean updated=Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if(!updated && !Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action))return;
        DiagnosticLog.info(updated?"home_guard.package_replaced":"home_guard.boot","action="+action+" elapsedRealtimeMs="+SystemClock.elapsedRealtime());
        Context c=supplied.getApplicationContext();HomeGuardAdb.restoreSetting(c);HomeGuardSync.refresh(c);HomeGuardAdb.ensure(c);HomeGuardRecovery.request(c,updated?"应用升级":"开机");
        if(updated)return;
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) || !new HomeGuardState(c).ready())return;
        if(HomeGuardService.connected()){HomeGuardService.lifecycle("开机完成");return;}
        // A receiver has no accessibility binding's activity-start privilege.
        if(Build.VERSION.SDK_INT>=29 && !Settings.canDrawOverlays(c))return;
        PendingResult pending=goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{
                if(HomeGuardService.connected())HomeGuardService.lifecycle("开机完成");
                else if(HomeGuardRecovery.unknownAllowed(c) && (!HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND)
                        || HomeGuardService.bootHomeObserved(c)))HomeGuardRecovery.launch(c,"开机兜底");
            }finally{pending.finish();}
        },HomeGuardPolicy.SETTLE_MS);
    }
}
