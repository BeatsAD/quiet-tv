package home.quiet.tv.xiaomihelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

final class HelperState {
    static final ComponentName QUIET=new ComponentName("home.quiet.tv","home.quiet.tv.HomeActivity");
    private final Context context;
    final SharedPreferences preferences;
    HelperState(Context context){this.context=context;preferences=context.getSharedPreferences("redirect",Context.MODE_PRIVATE);}
    boolean supported(){return RedirectPolicy.supported(Build.MODEL,Build.VERSION.SDK_INT);}
    boolean enabled(){return preferences.getBoolean("enabled",false);}
    String selected(){return preferences.getString("home","");}
    void pause(){preferences.edit().putBoolean("enabled",false).apply();}
    boolean serviceEnabled(){
        AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if(manager==null)return false;
        for(AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){
            android.content.pm.ServiceInfo value=info.getResolveInfo().serviceInfo;
            if(context.getPackageName().equals(value.packageName) && HomeRedirectService.class.getName().equals(value.name))return true;
        }
        return false;
    }
    List<ComponentName> stockHomes(){
        ArrayList<ComponentName> result=new ArrayList<>();
        PackageManager pm=context.getPackageManager();
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(RedirectPolicy.STOCK_PACKAGE);
        for(ResolveInfo info:pm.queryIntentActivities(intent,0)){
            ActivityInfo activity=info.activityInfo;
            if(activity!=null && activity.exported && activity.enabled && activity.applicationInfo.enabled
                    && (activity.applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0
                    && (activity.permission==null || pm.checkPermission(activity.permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED))
                result.add(new ComponentName(activity.packageName,activity.name));
        }
        return result;
    }
    boolean validSelection(){for(ComponentName name:stockHomes())if(name.flattenToString().equals(selected()))return true;return false;}
    boolean quietAvailable(){
        try{
            ActivityInfo info=context.getPackageManager().getActivityInfo(QUIET,0);
            return info.enabled && info.applicationInfo.enabled && info.exported
                && new Intent().setComponent(QUIET).resolveActivity(context.getPackageManager())!=null;
        }catch(PackageManager.NameNotFoundException e){return false;}
    }
    boolean openQuiet(){
        if(!quietAvailable())return false;
        try{context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(QUIET).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
        catch(ActivityNotFoundException|SecurityException e){return false;}
    }
    void redirect(){
        if(!supported() || !enabled() || !validSelection() || !serviceEnabled())return;
        if(!openQuiet()){
            pause();
            Toast.makeText(context,"Quiet TV 无法打开，中转已暂停。请打开小米中转助手检查。",Toast.LENGTH_LONG).show();
        }
    }
}
