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
        JSONObject config;
        try(InputStream input=context.getAssets().open("launcher.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
            config=new JSONObject(out.toString("UTF-8"));
        }catch(IOException|JSONException e){throw new IllegalStateException("Invalid bundled launcher configuration",e);}
        HashSet<String> hidden=new HashSet<>();JSONArray values=config.optJSONArray("hidden");
        if(values!=null)for(int i=0;i<values.length();i++)hidden.add(values.optString(i));
        PackageManager pm=context.getPackageManager();LinkedHashMap<String,ResolveInfo> choices=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                String id=info.activityInfo.packageName;
                if((!hidden.contains(id)||pinned.contains(id)) && !id.equals(context.getPackageName()) && info.activityInfo.applicationInfo.enabled && !choices.containsKey(id))choices.put(id,info);
            }
        }
        ArrayList<Entry> result=new ArrayList<>();
        for(Map.Entry<String,ResolveInfo> choice:choices.entrySet())result.add(new Entry(choice.getKey(),choice.getValue().loadLabel(pm).toString(),choice.getValue()));
        Collator collator=Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(result,(a,b)->{int compared=collator.compare(a.label,b.label);return compared!=0?compared:a.id.compareTo(b.id);});
        boolean chinese=!"en".equals(config.optString("language","zh"));
        result.add(0,new Entry(WALLPAPER,chinese?"壁纸设置":"Wallpaper Settings",null));
        result.add(1,new Entry(DESKTOP_SETTINGS,chinese?"桌面设置":"Desktop Settings",null));
        result.add(2,new Entry(MANAGEMENT,chinese?"应用管理":"App Management",null));
        return result;
    }
    static ArrayList<String> gridIds(List<Entry> entries,List<String> pinned){
        ArrayList<String> ids=new ArrayList<>();for(Entry entry:entries)if(!pinned.contains(entry.id))ids.add(entry.id);return ids;
    }
}
