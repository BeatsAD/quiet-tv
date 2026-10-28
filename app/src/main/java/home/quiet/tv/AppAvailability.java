package home.quiet.tv;

import android.content.pm.*;
import android.os.Build;

/** FLAG_INSTALLED did not exist before per-user packages were introduced in API17. */
final class AppAvailability {
    static boolean installed(PackageManager pm,ApplicationInfo app){
        if(Build.VERSION.SDK_INT>=17)return (app.flags&ApplicationInfo.FLAG_INSTALLED)!=0;
        try{pm.getApplicationInfo(app.packageName,0);return true;}
        catch(PackageManager.NameNotFoundException|SecurityException missing){return false;}
    }
    private AppAvailability(){}
}
