package home.quiet.tv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Xiaomi's own settings first on Xiaomi firmware; never deliberately open a HOME handler. */
final class SystemSettingsRouter {
    private static final String XIAOMI="com.xiaomi.mitv.settings";
    private final Context context;
    private final PackageManager pm;
    SystemSettingsRouter(Context context){this.context=context;pm=context.getPackageManager();}

    static boolean xiaomi(String manufacturer,String brand,String model){
        return "xiaomi".equalsIgnoreCase(manufacturer) || "xiaomi".equalsIgnoreCase(brand)
            || model.toLowerCase(Locale.ROOT).startsWith("mitv");
    }

    List<Intent> candidates(String action){
        ArrayList<Intent> intents=new ArrayList<>();
        if(xiaomi(Build.MANUFACTURER,Build.BRAND,Build.MODEL)){
            if(Settings.ACTION_WIFI_SETTINGS.equals(action))
                intents.add(new Intent(XIAOMI+".WIFI_SETTINGS").setComponent(new ComponentName(XIAOMI,XIAOMI+".wifi.MitvWifiActivity")));
            intents.add(new Intent(XIAOMI+".SETTINGS").setComponent(new ComponentName(XIAOMI,XIAOMI+".entry.MainActivity")));
        }
        intents.add(new Intent(action));
        if(Settings.ACTION_WIFI_SETTINGS.equals(action))intents.add(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        if(!Settings.ACTION_SETTINGS.equals(action))intents.add(new Intent(Settings.ACTION_SETTINGS));
        return intents;
    }

    boolean open(String action){
        for(Intent intent:candidates(action)){
            ResolveInfo resolved=pm.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
            if(resolved==null || resolved.activityInfo==null)continue;
            ActivityInfo activity=resolved.activityInfo;
            if(!activity.enabled || !activity.applicationInfo.enabled || !activity.exported
                    || (activity.permission!=null && pm.checkPermission(activity.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED))continue;
            if(XIAOMI.equals(activity.packageName) && (activity.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)continue;
            ComponentName target=new ComponentName(activity.packageName,activity.name);
            boolean home=false;
            for(ResolveInfo candidate:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0))
                if(candidate.activityInfo!=null && target.equals(new ComponentName(candidate.activityInfo.packageName,candidate.activityInfo.name))){home=true;break;}
            if(home)continue;
            try{
                context.startActivity(new Intent(intent).setComponent(target));
                return true;
            }catch(ActivityNotFoundException|SecurityException ignored){/* Try the next usable settings entry. */}
        }
        return false;
    }
}
