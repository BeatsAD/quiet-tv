package home.quiet.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import org.json.*;
import java.io.*;
import java.text.Collator;
import java.util.*;

/** The home and its editor must enumerate the same launchable packages and built-in entries. */
final class HomeAppCatalog {
    static final String WALLPAPER="quiet:wallpapers",DESKTOP_SETTINGS="quiet:desktop-settings",MANAGEMENT="quiet:management";
    static final class Entry {
        final String id,label;final ResolveInfo info;
        Entry(String id,String label,ResolveInfo info){this.id=id;this.label=label;this.info=info;}
    }
    private final Context context;
    HomeAppCatalog(Context context){this.context=context;}
    ArrayList<Entry> load(List<String> pinned){
        return load(pinned,false);
    }
    ArrayList<Entry> load(List<String> pinned,boolean hiddenApps){
        JSONObject config;
        try(InputStream input=context.getAssets().open("launcher.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
            config=new JSONObject(out.toString("UTF-8"));
        }catch(IOException|JSONException e){throw new IllegalStateException("Invalid bundled launcher configuration",e);}
        HashSet<String> hidden=new HashSet<>();JSONArray values=config.optJSONArray("hidden");
        if(values!=null)for(int i=0;i<values.length();i++)hidden.add(values.optString(i));
        PackageManager pm=context.getPackageManager();LinkedHashMap<String,ResolveInfo> choices=launchable();
        ChildModeStore children=new ChildModeStore(context);HomeVisibilityStore visibility=new HomeVisibilityStore(context);
        for(Iterator<String> ids=choices.keySet().iterator();ids.hasNext();){String id=ids.next();if(children.hidden(id) || visibility.hidden(id)!=hiddenApps || !hiddenApps&&hidden.contains(id)&&!pinned.contains(id))ids.remove();}
        ArrayList<Entry> result=new ArrayList<>();
        for(Map.Entry<String,ResolveInfo> choice:choices.entrySet())result.add(new Entry(choice.getKey(),choice.getValue().loadLabel(pm).toString(),choice.getValue()));
        Collator collator=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(result,(a,b)->{int compared=collator.compare(a.label,b.label);return compared!=0?compared:a.id.compareTo(b.id);});
        boolean chinese=!"en".equals(config.optString("language","zh"));
        if(!hiddenApps){
            result.add(0,new Entry(WALLPAPER,chinese?"壁纸设置":"Wallpaper Settings",null));
            result.add(1,new Entry(DESKTOP_SETTINGS,chinese?"桌面设置":"Desktop Settings",null));
        }
        return result;
    }
    /** Replace the retired management entry once and retain the first saved position. */
    static ArrayList<String> unifiedSettingsEntries(List<String> entries){
        ArrayList<String> result=new ArrayList<>();
        for(String entry:entries){String id=MANAGEMENT.equals(entry)?DESKTOP_SETTINGS:entry;if(!DESKTOP_SETTINGS.equals(id)||!result.contains(id))result.add(id);}

        return result;
    }
    /** Same launchable apps for Home, its editor and the Dock; TV entries win. */
    LinkedHashMap<String,ResolveInfo> launchable(){
        PackageManager pm=context.getPackageManager();LinkedHashMap<String,ResolveInfo> result=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER,"android.intent.category.APPSTORE_LAUNCHER"}){
            for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                boolean vendor="android.intent.category.APPSTORE_LAUNCHER".equals(category);
                if(usable(pm,info.activityInfo,vendor) && !result.containsKey(info.activityInfo.packageName))result.put(info.activityInfo.packageName,info);
            }
        }
        // Firmware entries reported by Aerial; each must be an installed, callable system activity.
        String[] components={"com.xiaomi.mitv.mediaexplorer/com.xiaomi.mitv.mediaexplorer.NewScraperMainEntryActivity",
            "com.xiaomi.smarthome.tv/com.xiaomi.smarthome.tv.ui.MainActivity"};
        for(String value:components){
            android.content.ComponentName component=android.content.ComponentName.unflattenFromString(value);
            try{ActivityInfo activity=pm.getActivityInfo(component,0);
                if(usable(pm,activity,true) && !result.containsKey(activity.packageName)){ResolveInfo info=new ResolveInfo();info.activityInfo=activity;result.put(activity.packageName,info);}
            }catch(PackageManager.NameNotFoundException|SecurityException ignored){}
        }
        return result;
    }
    private boolean usable(PackageManager pm,ActivityInfo activity,boolean systemOnly){
        if(activity==null || !activity.exported || activity.packageName.equals(context.getPackageName())
                || systemOnly && (activity.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
        int app=pm.getApplicationEnabledSetting(activity.packageName);
        int component=pm.getComponentEnabledSetting(new android.content.ComponentName(activity.packageName,activity.name));
        return (app==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?activity.applicationInfo.enabled:app==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            && (component==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?activity.enabled:component==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            && (activity.permission==null || pm.checkPermission(activity.permission,context.getPackageName())==PackageManager.PERMISSION_GRANTED);
    }
    static ArrayList<String> gridIds(List<Entry> entries,List<String> pinned){
        ArrayList<String> ids=new ArrayList<>();for(Entry entry:entries)if(!pinned.contains(entry.id))ids.add(entry.id);return ids;
    }
}
