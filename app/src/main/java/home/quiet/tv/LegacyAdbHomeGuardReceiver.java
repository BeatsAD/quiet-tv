package home.quiet.tv;

import android.content.*;

/** Restore only explicitly enabled legacy recovery; an update never grants authorization. */
public final class LegacyAdbHomeGuardReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent!=null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))){
            HomeGuardAdb.restoreSetting(c);HomeGuardAdb.watch(c);LegacyAdbHomeGuard.refresh(c);
        }
    }
}
