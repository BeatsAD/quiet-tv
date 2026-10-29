package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Ordinary hiding is independent of parental rules; Dock membership changes atomically. */
final class HomeVisibilityStore {
    private final Context context;
    private final SharedPreferences preferences;
    HomeVisibilityStore(Context context){this.context=context;preferences=context.getSharedPreferences("dock",0);}
    String signature(){return preferences.getString("hidden-apps","{}");}
    private JSONObject hidden(){try{return new JSONObject(signature());}catch(JSONException e){throw new IllegalStateException("无法读取隐藏应用",e);}}
    boolean hidden(String pkg){return hidden().has(pkg);}
    boolean hide(String pkg){
        ArrayList<String> dock=new DockStore(context).load();JSONObject values=hidden();
        try{if(!values.has(pkg))values.put(pkg,dock.indexOf(pkg));}catch(JSONException e){throw new IllegalStateException(e);}
        dock.remove(pkg);return save(values,dock);
    }
    boolean restore(String pkg){
        JSONObject values=hidden();if(!values.has(pkg))return true;
        ArrayList<String> dock=new DockStore(context).load();int index=values.optInt(pkg,-1);values.remove(pkg);
        if(index>=0&&dock.size()<DockStore.CAPACITY&&!dock.contains(pkg))dock.add(Math.min(index,dock.size()),pkg);
        return save(values,dock);
    }
    boolean wasPinned(String pkg){return hidden().optInt(pkg,-1)>=0;}
    void remove(String pkg){JSONObject values=hidden();if(values.has(pkg)){values.remove(pkg);preferences.edit().putString("hidden-apps",values.toString()).apply();}}
    private boolean save(JSONObject hidden,List<String> dock){
        String oldHidden=signature(),oldDock=preferences.getString("packages","[]");
        if(preferences.edit().putString("hidden-apps",hidden.toString()).putString("packages",new JSONArray(dock).toString()).commit())return true;
        preferences.edit().putString("hidden-apps",oldHidden).putString("packages",oldDock).apply();return false;
    }
}
