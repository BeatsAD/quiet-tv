package home.quiet.tv;

import org.json.*;
import java.text.*;
import java.util.*;

/** Stable wallpaper identities and policy, independent of Android views and file I/O. */
final class WallpaperCatalog {
    static final String DEFAULT = "default";
    static final String DAILY = "daily", BING = "bing-cn", BT = "bt-landscape";
    // BT is accepted only for reading legacy caches until a Bing image is committed.
    static boolean dailySource(String source) { return BING.equals(source)||BT.equals(source); }
    static String sourceLabel(String source,boolean chinese) { return chinese?"必应中国":"Bing China"; }
    static final class DailyImage {
        String source=BING, key="", url="", title="", copyright="", link="", upstream="", day="";
        int slot,width,height;
        long checked;
        String dateLabel(){
            if(!BING.equals(source)||!upstream.matches("[0-9]{8}([0-9]{4})?"))return "";
            SimpleDateFormat input=new SimpleDateFormat(upstream.length()==12?"yyyyMMddHHmm":"yyyyMMdd",Locale.US);input.setLenient(false);
            if(upstream.length()==12)input.setTimeZone(TimeZone.getTimeZone("UTC"));
            try{return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(input.parse(upstream));}catch(ParseException e){return "";}
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("source",source).put("key",key).put("url",url).put("title",title).put("copyright",copyright).put("link",link).put("upstream",upstream).put("day",day).put("slot",slot).put("width",width).put("height",height).put("checked",checked);
        }
        static DailyImage read(JSONObject json) {
            if(json==null||!dailySource(json.optString("source")))return null;
            DailyImage image=new DailyImage();image.source=json.optString("source");image.key=json.optString("key");image.url=json.optString("url");image.title=json.optString("title");image.copyright=json.optString("copyright");image.link=json.optString("link");image.upstream=json.optString("upstream");image.day=json.optString("day");image.checked=json.optLong("checked");image.slot=json.optInt("slot");image.width=json.optInt("width");image.height=json.optInt("height");
            return image.key.matches("[a-f0-9]{64}")&&(image.slot==0||image.slot==1)&&image.width>0&&image.height>0?image:null;
        }
    }
    static final int MAX_CUSTOM = 20;
    static final long MAX_BYTES = 50L * 1024 * 1024;
    static final int MAX_VIDEOS = 10;
    static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    static final class Entry {
        final String id, name;
        final int width, height;
        final long bytes;
        final String kind,videoMime;
        final long durationMs;
        final float fps,pixelRatio;
        final int rotation;
        Entry(String id, String name, int width, int height, long bytes) {
            this(id,name,width,height,bytes,"image","",0,0,0);
        }
        Entry(String id,String name,int width,int height,long bytes,String kind,String mime,long duration,float fps,int rotation){
            this(id,name,width,height,bytes,kind,mime,duration,fps,rotation,1f);
        }
        Entry(String id,String name,int width,int height,long bytes,String kind,String mime,long duration,float fps,int rotation,float pixelRatio){
            this.id=id;this.name=name;this.width=width;this.height=height;this.bytes=bytes;this.kind=kind;videoMime=mime;durationMs=duration;this.fps=fps;this.rotation=rotation;this.pixelRatio=pixelRatio;
        }
        boolean video(){return "video".equals(kind);}
        boolean custom() { return id.startsWith("user-"); }
        String label(boolean chinese) {
            if (DEFAULT.equals(id)) return chinese ? "默认颜色主题" : "Default color theme";
            if (DAILY.equals(id)) return chinese ? "每日壁纸" : "Daily wallpaper";
            return name;
        }
    }
    String current = DEFAULT;
    boolean rotating,sound;
    int rotationMinutes=5;
    static boolean rotationMinutes(int minutes){return minutes==1||minutes==5||minutes==15||minutes==30||minutes==60;}
    String lastVideo="",staticBeforeVideo=DEFAULT;
    boolean rotationBeforeVideo;
    // Source of the displayed cache, not a configurable download provider.
    String dailySource=BING,lastLocal=DEFAULT;
    final Map<String,DailyImage> dailyImages=new LinkedHashMap<>();
    final List<Entry> custom = new ArrayList<>();

    List<Entry> entries() {
        List<Entry> result=new ArrayList<>();
        result.add(new Entry(DEFAULT,"",0,0,0));
        result.add(new Entry(DAILY,"",0,0,0));
        result.addAll(custom); return result;
    }
    Entry find(String id) { for (Entry entry:entries()) if (entry.id.equals(id)) return entry; return null; }
    List<Entry> pictures() { List<Entry> result=new ArrayList<>();for(Entry e:entries())if(!e.video()&&!DEFAULT.equals(e.id)&&!DAILY.equals(e.id))result.add(e);return result; }
    List<Entry> videos(){List<Entry> result=new ArrayList<>();for(Entry e:entries())if(e.video())result.add(e);return result;}
    boolean dynamic(){Entry e=find(current);return e!=null&&e.video();}
    int count(boolean video){int n=0;for(Entry e:custom)if(e.video()==video)n++;return n;}
    void normalize() {
        if (find(current)==null) {current=staticBeforeVideo;rotating=rotationBeforeVideo;}
        if(find(current)==null)current=DEFAULT;
        Entry previous=find(staticBeforeVideo);if(previous==null||previous.video()){staticBeforeVideo=DEFAULT;rotationBeforeVideo=false;}
        Entry video=find(lastVideo);if(video==null||!video.video()){List<Entry> pool=videos();lastVideo=pool.isEmpty()?"":pool.get(0).id;}
        if (DEFAULT.equals(current) || DAILY.equals(current) || (dynamic()?videos():pictures()).size()<2) rotating=false;
        if(!rotationMinutes(rotationMinutes))rotationMinutes=5;
        if(DAILY.equals(lastLocal)||find(lastLocal)==null||find(lastLocal).video())lastLocal=DEFAULT;
    }
    void select(String id) {
        if (find(id)==null) throw new IllegalArgumentException("Unknown wallpaper");
        if(find(id).video()){if(!dynamic()){staticBeforeVideo=current;rotationBeforeVideo=rotating;}lastVideo=id;}
        if(DAILY.equals(id)&&!DAILY.equals(current)){String local=dynamic()?staticBeforeVideo:current;if(!DAILY.equals(local))lastLocal=local;}
        current=id; rotating=false;
    }
    void stopVideo(){if(!dynamic())return;current=staticBeforeVideo;rotating=rotationBeforeVideo;normalize();}
    void updateDaily(DailyImage image,boolean apply){
        dailyImages.put(BING,image);
        if(apply)select(DAILY);
        if(DAILY.equals(current))dailySource=BING;
    }
    void rotation(boolean video,int minutes,String start) {
        if(minutes==0){rotating=false;return;}
        if(!rotationMinutes(minutes))throw new IllegalArgumentException("Unsupported rotation interval");
        List<Entry> pool=video?videos():pictures();
        if(pool.size()<2)throw new IllegalArgumentException("Rotation needs at least two wallpapers");
        Entry entry=find(start);if(entry==null||entry.video()!=video||DEFAULT.equals(start)||DAILY.equals(start))start=pool.get(0).id;
        select(start);rotationMinutes=minutes;rotating=true;
    }
    void next() {
        List<Entry> pool=dynamic()?videos():pictures();
        if (!rotating || pool.size()<2) { normalize(); return; }
        int n=0;
        for (int i=0;i<pool.size();i++) if (pool.get(i).id.equals(current)) n=i;
        current=pool.get((n+1)%pool.size()).id;
        if(dynamic())lastVideo=current;
    }
    void remove(String id) {
        if (DEFAULT.equals(id)||DAILY.equals(id)) throw new IllegalArgumentException("Wallpaper entry cannot be removed");
        Entry entry=find(id); if (entry==null) return;
        List<Entry> pool=entry.video()?videos():pictures(); int position=0;
        for (int i=0;i<pool.size();i++) if (pool.get(i).id.equals(id)) position=i;
        custom.remove(entry);
        pool=entry.video()?videos():pictures();
        if (current.equals(id)){
            if(rotating&&!pool.isEmpty()){current=pool.get(position%pool.size()).id;if(entry.video())lastVideo=current;}
            else if(entry.video()){current=staticBeforeVideo;rotating=rotationBeforeVideo;}
            else current=DEFAULT;
        }
        normalize();
    }
    long bytes() {return bytes(false);}
    long bytes(boolean video){long size=0;for(Entry e:custom)if(e.video()==video)size+=e.bytes;return size;}
    void add(Entry entry) {
        // Video byte limits include asynchronously captured posters and belong to WallpaperStore.
        if (count(entry.video())>=(entry.video()?MAX_VIDEOS:MAX_CUSTOM) || !entry.video()&&bytes()+entry.bytes>MAX_BYTES) throw new IllegalStateException("Wallpaper capacity exceeded");
        custom.add(entry);
    }
    WallpaperCatalog copy() { try { return read(json()); } catch (JSONException e) { throw new IllegalStateException(e); } }
    JSONObject json() throws JSONException {
        JSONObject result=new JSONObject().put("version",2).put("current",current).put("rotating",rotating).put("sound",sound);
        result.put("lastVideo",lastVideo).put("staticBeforeVideo",staticBeforeVideo).put("rotationBeforeVideo",rotationBeforeVideo);
        result.put("rotationMinutes",rotationMinutes);
        JSONArray user=new JSONArray();
        for (Entry e:custom) user.put(new JSONObject().put("id",e.id).put("name",e.name).put("width",e.width).put("height",e.height).put("bytes",e.bytes).put("kind",e.kind).put("mime",e.videoMime).put("durationMs",e.durationMs).put("fps",e.fps).put("rotation",e.rotation).put("pixelRatio",e.pixelRatio));
        JSONObject images=new JSONObject();for(DailyImage image:dailyImages.values())images.put(image.source,image.json());
        return result.put("custom",user).put("daily",new JSONObject().put("source",dailySource).put("lastLocal",lastLocal).put("images",images));
    }
    static WallpaperCatalog read(JSONObject json) throws JSONException {
        if (json.getInt("version")!=1&&json.getInt("version")!=2) throw new JSONException("Unsupported wallpaper catalog");
        WallpaperCatalog result=new WallpaperCatalog();
        result.current=json.optString("current",DEFAULT); result.rotating=json.optBoolean("rotating",false);result.sound=json.optBoolean("sound",false);
        result.lastVideo=json.optString("lastVideo","");result.staticBeforeVideo=json.optString("staticBeforeVideo",DEFAULT);result.rotationBeforeVideo=json.optBoolean("rotationBeforeVideo",false);
        result.rotationMinutes=json.optInt("rotationMinutes",5);
        JSONArray users=json.optJSONArray("custom");
        if(users!=null) for(int i=0;i<users.length() && result.custom.size()<MAX_CUSTOM+MAX_VIDEOS;i++) {
            JSONObject e=users.getJSONObject(i); String id=e.getString("id");
            if(!id.matches("user-[a-f0-9-]{36}") || result.find(id)!=null) continue;
            String kind=e.optString("kind","image");if(!"image".equals(kind)&&!"video".equals(kind))continue;
            Entry entry=new Entry(id,e.getString("name"),e.getInt("width"),e.getInt("height"),e.getLong("bytes"),kind,e.optString("mime"),e.optLong("durationMs"),(float)e.optDouble("fps",0),e.optInt("rotation"),(float)e.optDouble("pixelRatio",1));
            if(result.count(entry.video())<(entry.video()?MAX_VIDEOS:MAX_CUSTOM))result.custom.add(entry);
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
