package home.quiet.tv;

import android.content.*;
import android.os.Build;

/** Only the system holding our explicit PendingIntent can deliver an installation result. */
public final class ApkInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT))ApkInstallSession.result(context,intent);
    }
}
