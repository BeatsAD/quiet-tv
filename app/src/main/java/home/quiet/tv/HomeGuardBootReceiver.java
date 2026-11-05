package home.quiet.tv;

import android.content.*;
import android.os.*;

/** Boot and package replacement resume existing recovery; they never grant permissions. */
public final class HomeGuardBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context supplied,Intent intent){
        String action=intent.getAction();
        boolean updated=Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if(!updated && !Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action))return;
        DiagnosticLog.info(updated?"home_guard.package_replaced":"home_guard.boot","action="+action+" elapsedRealtimeMs="+SystemClock.elapsedRealtime());
        Context c=supplied.getApplicationContext();BootHomeActivity.reconcile(c);HomeGuardAdb.restoreSetting(c);HomeGuardSync.refresh(c);HomeGuardAdb.ensure(c);HomeGuardRecovery.ensureBinding(c,updated?"应用升级":"开机");
        if(updated)return;
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) || !new HomeGuardState(c).ready())return;
        PendingResult pending=goAsync();
        HomeGuardRecovery.bootCompleted(c,pending::finish);
    }
}
