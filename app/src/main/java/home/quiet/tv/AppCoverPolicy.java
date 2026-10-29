package home.quiet.tv;

import android.content.Context;
import android.content.pm.*;

/** Cosmetic launcher overrides never grant shell or package-management permissions. */
final class AppCoverPolicy {
    private AppCoverPolicy(){}
    static boolean ownReadOnly(Context context,String pkg){return context.getPackageName().equals(pkg)||"home.quiet.tv.test".equals(pkg);}
    static boolean allowed(Context context,String pkg){
        if(!AppCoverStore.packageName(pkg)||ownReadOnly(context,pkg))return false;
        try{ApplicationInfo info=context.getPackageManager().getApplicationInfo(pkg,0);
            return AppAvailability.installed(context.getPackageManager(),info)&&info.enabled&&new HomeAppCatalog(context).launchable().containsKey(pkg);
        }catch(PackageManager.NameNotFoundException|SecurityException|IllegalArgumentException e){return false;}
    }
    static void require(Context context,String pkg) throws java.io.IOException {
        if(!allowed(context,pkg))throw new java.io.IOException("应用状态已改变，请返回应用管理重新选择 / App is no longer available");
    }
}
