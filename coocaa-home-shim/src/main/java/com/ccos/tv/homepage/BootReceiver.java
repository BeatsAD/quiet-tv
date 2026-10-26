package com.ccos.tv.homepage;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BootReceiver extends BroadcastReceiver {

    /**
     * Something on Coocaa TVs (most likely the TV manager's autostart control) disables the boot
     * receivers of every third-party app, which silently drops even explicit broadcasts to them.
     * An app may re-enable its own components, so do that whenever this package runs.
     */
    static void keepEnabled(Context context) {
        ComponentName self = new ComponentName(context, BootReceiver.class);
        PackageManager pm = context.getPackageManager();
        try {
            if (pm.getComponentEnabledSetting(self) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(self, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean boot = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action);
        if (!boot) return;
        // Upstream 2.1: some boots leave home[0] / home[1] instead of a component.
        setProp("persist.service.homepage.pkg", "com.ccos.tv.homepage");
        setProp("persist.service.homepage.cls", "com.ccos.tv.homepage.LaunchActivity");
        context.startService(new Intent(context, BootWatchService.class));
    }
    private static void setProp(String key,String value) {
        try {
            Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,key,value);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            android.util.Log.w("QuietTVShim", "Homepage property restore failed: " + key);
        }
    }
}
