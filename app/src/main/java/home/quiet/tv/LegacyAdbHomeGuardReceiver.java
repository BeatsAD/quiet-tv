package home.quiet.tv;

import android.content.*;

/** Restore only a previously enabled API19 guard; an update never grants authorization. */
public final class LegacyAdbHomeGuardReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent!=null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))LegacyAdbHomeGuard.refresh(c);
    }
}
