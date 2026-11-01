package home.quiet.tv;

import android.content.*;
import android.content.pm.*;

/** Component checks shared by native inputs and the API21 framework. */
final class InputComponents {
    static boolean enabledSystem(Context context,ComponentInfo info){
        if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
        return enabled(context,info);
    }
    static boolean enabled(Context context,ComponentInfo info){
        PackageManager pm=context.getPackageManager();
        int app=pm.getApplicationEnabledSetting(info.packageName),component=pm.getComponentEnabledSetting(new ComponentName(info.packageName,info.name));
        return (app==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.applicationInfo.enabled:app==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            && (component==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.enabled:component==PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }
    static boolean canOpen(Context context,ComponentName target){
        if(target==null)return false;
        try{
            android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(target,0);
            String reason=!enabledSystem(context,info)?"not_enabled_system":!info.exported?"not_exported":info.permission!=null && context.getPackageManager().checkPermission(info.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED?"permission_denied permission="+info.permission:"";
            if(!reason.isEmpty())DiagnosticLog.info("input.component","component="+target.flattenToShortString()+" result="+reason);
            return reason.isEmpty();
        }catch(PackageManager.NameNotFoundException unavailable){DiagnosticLog.info("input.component","component="+target.flattenToShortString()+" result=not_installed");return false;}
        catch(RuntimeException unavailable){DiagnosticLog.error("input.component.failed","component="+target.flattenToShortString(),unavailable);return false;}
    }
}
