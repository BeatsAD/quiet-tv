package home.quiet.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;

/** Legacy and devices without GLES3 retain their existing static/uniform path. */
final class WallpaperGpuSupport {
    private static Boolean supported;
    private WallpaperGpuSupport(){}
    @androidx.annotation.ChecksSdkIntAtLeast(api=21)
    static synchronized boolean available(Context context){
        if(BuildConfig.LEGACY_19||android.os.Build.VERSION.SDK_INT<21)return false;
        if(supported!=null)return supported;
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)return false;
        if(UiCompat.lowRam(manager))return supported=false;
        ConfigurationInfo configuration=manager.getDeviceConfigurationInfo();
        return supported=configuration!=null&&configuration.reqGlEsVersion>=0x30000;
    }
}
