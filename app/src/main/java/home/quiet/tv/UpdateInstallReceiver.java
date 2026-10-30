package home.quiet.tv;

import android.content.*;
import android.os.Build;

/** Explicit, non-exported result target of our own installation PendingIntent. */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT)&&intent!=null)UpdateRepository.get(context).installationResult(intent);
    }
}
