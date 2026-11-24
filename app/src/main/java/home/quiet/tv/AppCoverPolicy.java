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
            return AppAvailability.installed(context.getPackageManager(),info)&&info.enabled&&new HomeAppCatalog(context).find(pkg)!=null;
        }catch(PackageManager.NameNotFoundException|SecurityException|IllegalArgumentException e){return false;}
    }
    static final class ShapeChanged extends java.io.IOException {
        ShapeChanged(){super("桌面图标形状已变化或与所选图标不同，请按当前桌面形状重新预览 / Preview again using the current desktop icon shape");}
    }
    static void requireDesktopShape(Context context,String shape)throws ShapeChanged{
        if(!new DesktopSettingsStore(context).iconShape().equals(shape))throw new ShapeChanged();
    }
    static void require(Context context,String pkg) throws java.io.IOException {
        if(!allowed(context,pkg))throw new java.io.IOException("应用状态已改变，请返回应用管理重新选择 / App is no longer available");
    }
}
