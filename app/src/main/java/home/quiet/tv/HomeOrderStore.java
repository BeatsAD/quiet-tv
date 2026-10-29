package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.*;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.*;

/** Only explicit saves change the user's order; unavailable entries retain their slots. */
final class HomeOrderStore {
    private static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences preferences;
    HomeOrderStore(Context context){this.context=context;preferences=context.getSharedPreferences("home-order",0);}
    String signature(){return preferences.getString("packages","");}
    boolean custom(){return preferences.contains("packages");}
    ArrayList<String> load(){
        synchronized(LOCK){
            ArrayList<String> result=new ArrayList<>();
            if(!custom())return result;
            try{JSONArray values=new JSONArray(signature());for(int i=0;i<values.length();i++){String id=values.getString(i);if(!result.contains(id))result.add(id);}}
            catch(JSONException e){throw new IllegalStateException("Invalid saved home order",e);}
            ArrayList<String> unified=HomeAppCatalog.unifiedSettingsEntries(result);if(!unified.equals(result))preferences.edit().putString("packages",new JSONArray(unified).toString()).apply();return unified;
        }
    }
    ArrayList<String> order(List<String> defaults){
        synchronized(LOCK){
            if(!custom())return new ArrayList<>(defaults);
            ArrayList<String> saved=load(),known=new ArrayList<>(saved);
            for(Iterator<String> items=known.iterator();items.hasNext();){String id=items.next();if(id.startsWith("quiet:"))continue;
                try{if(!AppAvailability.installed(context.getPackageManager(),context.getPackageManager().getApplicationInfo(id,0)))items.remove();}
                catch(PackageManager.NameNotFoundException e){items.remove();}
            }
            addNewEntries(known,defaults);
            // Remember newly visible apps so a later installation cannot sort ahead of them.
            if(!known.equals(saved))preferences.edit().putString("packages",new JSONArray(known).toString()).apply();
            return project(known,defaults);
        }
    }
    boolean save(List<String> visible,List<String> defaults,boolean reset){
        if(visible.size()!=new HashSet<>(visible).size() || !new HashSet<>(visible).equals(new HashSet<>(defaults)))throw new IllegalArgumentException("Home order must contain every visible entry once");
        synchronized(LOCK){
            SharedPreferences.Editor editor=preferences.edit();
            String previous=signature();boolean wasCustom=custom();
            if(reset)editor.remove("packages");
            else editor.putString("packages",new JSONArray(merge(load(),visible)).toString());
            if(editor.commit()){DiagnosticLog.info("home.order.saved","reset="+reset+" count="+visible.size());return true;}
            DiagnosticLog.info("home.order.failed","preferences write failed");
            // A failed disk write still changes the SharedPreferences memory snapshot.
            SharedPreferences.Editor rollback=preferences.edit();if(wasCustom)rollback.putString("packages",previous);else rollback.remove("packages");rollback.apply();return false;
        }
    }
    void remove(String id){
        synchronized(LOCK){ArrayList<String> saved=load();if(saved.remove(id))preferences.edit().putString("packages",new JSONArray(saved).toString()).apply();}
    }
    static ArrayList<String> project(List<String> order,List<String> visible){
        Set<String> available=new HashSet<>(visible);ArrayList<String> result=new ArrayList<>();
        for(String id:order)if(available.contains(id) && !result.contains(id))result.add(id);
        for(String id:visible)if(!result.contains(id))result.add(id);
        return result;
    }
    static void addNewEntries(List<String> known,List<String> defaults){
        for(String id:defaults)if(!known.contains(id)){
            int wallpaper=known.indexOf(HomeAppCatalog.WALLPAPER);
            if(HomeAppCatalog.DESKTOP_SETTINGS.equals(id) && wallpaper>=0)known.add(wallpaper+1,id);
            else known.add(id);
        }
    }
    static ArrayList<String> merge(List<String> saved,List<String> visible){
        Set<String> active=new HashSet<>(visible);ArrayList<String> result=new ArrayList<>();int next=0;
        for(String id:saved){if(active.contains(id)){if(next<visible.size())result.add(visible.get(next++));}else if(!result.contains(id))result.add(id);}
        while(next<visible.size())result.add(visible.get(next++));
        return result;
    }
    static int target(int index,int count,int columns,int dx,int dy){
        int row=index/columns+dy,column=index%columns+dx;
        if(row<0 || row>(count-1)/columns || column<0 || column>=columns)return index;
        return Math.min(row*columns+column,count-1);
    }
    static <T> void move(List<T> order,int from,int to){if(from!=to)Collections.swap(order,from,to);}
}
