package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.util.*;

/** An explicit empty array is a user choice and must never re-import bundled defaults. */
final class DockStore {
    static final int CAPACITY = 5;
    private final SharedPreferences preferences;
    DockStore(Context context){
        preferences=context.getSharedPreferences("dock",0);
        if(!preferences.contains("packages")){
            try(InputStream input=context.getAssets().open("launcher.json");ByteArrayOutputStream output=new ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);
                JSONArray defaults=new JSONObject(output.toString("UTF-8")).getJSONArray("dock");
                ArrayList<String> values=new ArrayList<>();for(int i=0;i<defaults.length();i++)values.add(defaults.getString(i));save(values);
            }catch(IOException|JSONException e){throw new IllegalStateException("Invalid bundled Dock",e);}
        }
    }
    ArrayList<String> load(){
        try{JSONArray saved=new JSONArray(preferences.getString("packages","[]"));ArrayList<String> result=new ArrayList<>();
            for(int i=0;i<saved.length();i++)result.add(saved.getString(i));
            ArrayList<String> unified=HomeAppCatalog.unifiedSettingsEntries(result);if(!unified.equals(result))preferences.edit().putString("packages",new JSONArray(unified).toString()).apply();return unified;
        }catch(JSONException e){throw new IllegalStateException("Invalid saved Dock",e);}
    }
    void save(List<String> packages){
        if(packages.size()>CAPACITY || new HashSet<>(packages).size()!=packages.size())throw new IllegalArgumentException("Dock 最多 "+CAPACITY+" 个且不能重复");
        preferences.edit().putString("packages",new JSONArray(packages).toString()).apply();DiagnosticLog.info("dock.saved","count="+packages.size());
    }
    boolean saveOrder(List<String> packages){
        if(packages.size()>CAPACITY || new HashSet<>(packages).size()!=packages.size())throw new IllegalArgumentException("Dock 最多 "+CAPACITY+" 个且不能重复");
        String previous=preferences.getString("packages","[]");
        if(preferences.edit().putString("packages",new JSONArray(packages).toString()).commit()){DiagnosticLog.info("dock.order.saved","count="+packages.size());return true;}
        DiagnosticLog.info("dock.order.failed","preferences write failed");
        preferences.edit().putString("packages",previous).apply();return false;
    }
    void remove(String pkg){ArrayList<String> packages=load();if(packages.remove(pkg))save(packages);}
}
