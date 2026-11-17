package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** User shortcuts retain their order; quick controls are always rendered separately. */
final class HomeButtonStore {
    static final List<String> DEFAULT=Collections.unmodifiableList(Collections.<String>emptyList());
    static final List<String> ALL;
    static {ArrayList<String> ids=new ArrayList<>(Arrays.asList("settings","control","systemSettings"));for(String id:QuickControlStore.ALL)if(!ids.contains(id))ids.add(id);ALL=Collections.unmodifiableList(ids);}
    static final List<String> EDITABLE;
    static {ArrayList<String> ids=new ArrayList<>(ALL);ids.remove("settings");ids.remove("control");EDITABLE=Collections.unmodifiableList(ids);}
    static List<String> visibleIds(List<String> shortcuts){ArrayList<String> ids=new ArrayList<>(shortcuts);ids.remove("settings");ids.remove("control");ids.add("control");return ids;}
    static final Object LOCK=new Object();
    private final SharedPreferences preferences;
    HomeButtonStore(Context context){preferences=context.getSharedPreferences("desktop-settings",0);}
    static final class Snapshot {
        final List<String> ids;final long revision;
        Snapshot(List<String> ids,long revision){this.ids=Collections.unmodifiableList(new ArrayList<>(ids));this.revision=revision;}
    }
    Snapshot read()throws IOException {synchronized(LOCK){return load();}}
    private Snapshot load()throws IOException {
        return decode(preferences.getAll());
    }
    static Snapshot decode(Map<String,?> values)throws IOException {
        List<String> ids=new ArrayList<>();Object rawRevision=values.get("home-buttons-revision");
        if(rawRevision!=null&&!(rawRevision instanceof Long))throw new IOException("首页按钮版本无效");
        long revision=rawRevision==null?0:(Long)rawRevision;
        if(revision<0)throw new IOException("首页按钮版本无效");
        if(values.containsKey("home-buttons")){
            Object raw=values.get("home-buttons");if(!(raw instanceof String))throw new IOException("无法读取首页按钮，原配置已保留");
            try{JSONArray array=new JSONArray((String)raw);for(int i=0;i<array.length();i++){Object id=array.get(i);if(!(id instanceof String))throw new JSONException("Invalid button");ids.add((String)id);}}
            catch(JSONException error){throw new IOException("无法读取首页按钮，原配置已保留",error);}
        }else{
            boolean legacy=false;for(String key:new String[]{"show-buttons","show-input","show-network","show-settings"})legacy|=values.containsKey(key);
            if(!legacy)ids.addAll(DEFAULT);
            else{boolean master=flag(values,"show-buttons",true);if(flag(values,"show-input",master))ids.add("source");if(flag(values,"show-network",master))ids.add("network");if(flag(values,"show-settings",master))ids.add("systemSettings");}
        }
        validate(ids);ids.remove("settings");ids.remove("control");return new Snapshot(ids,revision);
    }
    private static boolean flag(Map<String,?> values,String key,boolean fallback)throws IOException {Object value=values.get(key);if(value==null)return fallback;if(!(value instanceof Boolean))throw new IOException("首页按钮配置无效，原配置已保留");return (Boolean)value;}
    Snapshot save(long expected,List<String> ids)throws IOException {synchronized(LOCK){
        validate(ids);if(ids.contains("settings")||ids.contains("control"))throw new IOException("快捷控制固定保留，桌面设置位于面板第九格");Snapshot current=load();if(current.ids.equals(ids))return current;
        if(current.revision!=expected)throw new IOException("首页按钮已在其他位置修改，请重新读取");
        if(current.revision==Long.MAX_VALUE)throw new IOException("首页按钮版本无法继续保存");
        Map<String,?> before=preferences.getAll();long revision=current.revision+1;
        if(!preferences.edit().putString("home-buttons",new JSONArray(ids).toString()).putLong("home-buttons-revision",revision).commit()){
            SharedPreferences.Editor rollback=preferences.edit();
            if(before.containsKey("home-buttons"))rollback.putString("home-buttons",(String)before.get("home-buttons"));else rollback.remove("home-buttons");
            if(before.containsKey("home-buttons-revision"))rollback.putLong("home-buttons-revision",(Long)before.get("home-buttons-revision"));else rollback.remove("home-buttons-revision");
            rollback.commit();throw new IOException("保存失败，首页按钮未改变，请重试");
        }
        return new Snapshot(ids,revision);
    }}
    private static void validate(List<String> ids)throws IOException {if(ids==null||ids.size()>3||new HashSet<>(ids).size()!=ids.size()||!ALL.containsAll(ids))throw new IOException("首页按钮最多三个且不能重复");}
    static String title(String id,boolean chinese){if("settings".equals(id))return chinese?"桌面设置":"Desktop settings";if("control".equals(id))return chinese?"快捷控制":"Quick controls";if("systemSettings".equals(id))return chinese?"系统设置":"System settings";return QuickControlStore.title(id,chinese);}
    static String glyph(String id){switch(id){case "settings":return "desktop-layout";case "control":return "header-control";default:return QuickControlPreviewView.glyph(id);}}
}
