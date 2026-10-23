package home.quiet.tv;

import org.json.*;
import java.util.*;

/** Stable wallpaper identities and policy, independent of Android views and file I/O. */
final class WallpaperCatalog {
    static final String DEFAULT = "default";
    static final String DAILY = "daily", BING = "bing-cn", BT = "bt-landscape";
    static boolean dailySource(String source) { return BING.equals(source)||BT.equals(source); }
    static String sourceLabel(String source,boolean chinese) { return BT.equals(source)?chinese?"搏天风景":"Btstu Landscape":chinese?"必应国区":"Bing China"; }
    static final class DailyImage {
        String source=BING, key="", url="", title="", copyright="", link="", upstream="", day="";
        int slot,width,height;
        long checked;
        JSONObject json() throws JSONException {
            return new JSONObject().put("source",source).put("key",key).put("url",url).put("title",title).put("copyright",copyright).put("link",link).put("upstream",upstream).put("day",day).put("slot",slot).put("width",width).put("height",height).put("checked",checked);
        }
        static DailyImage read(JSONObject json) {
            if(json==null||!dailySource(json.optString("source")))return null;
            DailyImage image=new DailyImage();image.source=json.optString("source");image.key=json.optString("key");image.url=json.optString("url");image.title=json.optString("title");image.copyright=json.optString("copyright");image.link=json.optString("link");image.upstream=json.optString("upstream");image.day=json.optString("day");image.checked=json.optLong("checked");image.slot=json.optInt("slot");image.width=json.optInt("width");image.height=json.optInt("height");
            return image.key.matches("[a-f0-9]{64}")&&(image.slot==0||image.slot==1)&&image.width>0&&image.height>0?image:null;
        }
    }
    static final String[] BUILTINS = {"big-sur-coastline", "big-sur-mountains", "big-sur-road"};
    static final String[] ZH = {"大苏尔海岸", "大苏尔群山", "大苏尔山路"};
    static final String[] EN = {"Big Sur Coastline", "Big Sur Mountains", "Big Sur Road"};
    static final int MAX_CUSTOM = 20;
    static final long MAX_BYTES = 50L * 1024 * 1024;
    static final class Entry {
        final String id, name;
        final int width, height;
        final long bytes;
        Entry(String id, String name, int width, int height, long bytes) {
            this.id=id; this.name=name; this.width=width; this.height=height; this.bytes=bytes;
        }
        boolean custom() { return id.startsWith("user-"); }
        String label(boolean chinese) {
            if (DEFAULT.equals(id)) return chinese ? "默认颜色主题" : "Default color theme";
            if (DAILY.equals(id)) return chinese ? "每日壁纸" : "Daily wallpaper";
            for (int i=0;i<BUILTINS.length;i++) if (BUILTINS[i].equals(id)) return chinese ? ZH[i] : EN[i];
            return name;
        }
    }
    String current = DEFAULT;
    boolean rotating;
    String dailySource=BING,lastLocal=DEFAULT;
    final Map<String,DailyImage> dailyImages=new LinkedHashMap<>();
    final Set<String> removed = new LinkedHashSet<>();
    final List<Entry> custom = new ArrayList<>();

    static WallpaperCatalog migrate(boolean defaultTheme, int index, boolean rotating) {
        WallpaperCatalog result=new WallpaperCatalog();
        if (!defaultTheme && index>=0 && index<BUILTINS.length) {
            result.current=BUILTINS[index]; result.rotating=rotating;
        }
        return result;
    }
    List<Entry> entries() {
        List<Entry> result=new ArrayList<>();
        result.add(new Entry(DEFAULT,"",0,0,0));
        result.add(new Entry(DAILY,"",0,0,0));
        for (String id:BUILTINS) if (!removed.contains(id)) result.add(new Entry(id,"",1920,1080,0));
        result.addAll(custom); return result;
    }
    Entry find(String id) { for (Entry entry:entries()) if (entry.id.equals(id)) return entry; return null; }
    List<Entry> pictures() { List<Entry> result=entries(); result.remove(0);result.remove(0); return result; }
    void normalize() {
        if (find(current)==null) current=DEFAULT;
        if (DEFAULT.equals(current) || DAILY.equals(current) || pictures().size()<2) rotating=false;
        if(DAILY.equals(lastLocal)||find(lastLocal)==null)lastLocal=DEFAULT;
    }
    void select(String id) {
        if (find(id)==null) throw new IllegalArgumentException("Unknown wallpaper");
        if(DAILY.equals(id)&&!DAILY.equals(current))lastLocal=current;
        current=id; rotating=false;
    }
    void rotation(boolean enabled) {
        List<Entry> pool=pictures();
        rotating=enabled && !DAILY.equals(current) && pool.size()>=2;
        if (rotating && DEFAULT.equals(current)) current=pool.get(0).id;
    }
    void next() {
        List<Entry> pool=pictures();
        if (!rotating || pool.size()<2) { normalize(); return; }
        int n=0;
        for (int i=0;i<pool.size();i++) if (pool.get(i).id.equals(current)) n=i;
        current=pool.get((n+1)%pool.size()).id;
    }
    void remove(String id) {
        if (DEFAULT.equals(id)||DAILY.equals(id)) throw new IllegalArgumentException("Wallpaper entry cannot be removed");
        Entry entry=find(id); if (entry==null) return;
        List<Entry> pool=pictures(); int position=0;
        for (int i=0;i<pool.size();i++) if (pool.get(i).id.equals(id)) position=i;
        if (entry.custom()) custom.remove(entry); else removed.add(id);
        pool=pictures();
        if (current.equals(id)) current=rotating && !pool.isEmpty() ? pool.get(position%pool.size()).id : DEFAULT;
        normalize();
    }
    void restore() { removed.clear(); }
    long bytes() { long size=0; for (Entry e:custom) size+=e.bytes; return size; }
    void add(Entry entry) {
        if (custom.size()>=MAX_CUSTOM || bytes()+entry.bytes>MAX_BYTES) throw new IllegalStateException("Wallpaper capacity exceeded");
        custom.add(entry);
    }
    WallpaperCatalog copy() { try { return read(json()); } catch (JSONException e) { throw new IllegalStateException(e); } }
    JSONObject json() throws JSONException {
        JSONObject result=new JSONObject().put("version",1).put("current",current).put("rotating",rotating);
        result.put("removed",new JSONArray(removed));
        JSONArray user=new JSONArray();
        for (Entry e:custom) user.put(new JSONObject().put("id",e.id).put("name",e.name).put("width",e.width).put("height",e.height).put("bytes",e.bytes));
        JSONObject images=new JSONObject();for(DailyImage image:dailyImages.values())images.put(image.source,image.json());
        return result.put("custom",user).put("daily",new JSONObject().put("source",dailySource).put("lastLocal",lastLocal).put("images",images));
    }
    static WallpaperCatalog read(JSONObject json) throws JSONException {
        if (json.getInt("version")!=1) throw new JSONException("Unsupported wallpaper catalog");
        WallpaperCatalog result=new WallpaperCatalog();
        result.current=json.optString("current",DEFAULT); result.rotating=json.optBoolean("rotating",false);
        JSONArray hidden=json.optJSONArray("removed");
        if(hidden!=null) for(int i=0;i<hidden.length();i++) for(String id:BUILTINS) if(id.equals(hidden.optString(i))) result.removed.add(id);
        JSONArray users=json.optJSONArray("custom");
        if(users!=null) for(int i=0;i<users.length() && result.custom.size()<MAX_CUSTOM;i++) {
            JSONObject e=users.getJSONObject(i); String id=e.getString("id");
            if(!id.matches("user-[a-f0-9-]{36}") || result.find(id)!=null) continue;
            result.custom.add(new Entry(id,e.getString("name"),e.getInt("width"),e.getInt("height"),e.getLong("bytes")));
        }
        JSONObject daily=json.optJSONObject("daily");
        if(daily!=null){
            String source=daily.optString("source",BING);if(dailySource(source))result.dailySource=source;
            result.lastLocal=daily.optString("lastLocal",DEFAULT);JSONObject images=daily.optJSONObject("images");
            if(images!=null)for(String id:new String[]{BING,BT}){DailyImage image=DailyImage.read(images.optJSONObject(id));if(image!=null&&id.equals(image.source))result.dailyImages.put(id,image);}
        }
        result.normalize(); return result;
    }
}
