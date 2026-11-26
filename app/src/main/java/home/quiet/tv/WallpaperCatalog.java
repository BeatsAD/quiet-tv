package home.quiet.tv;

import org.json.*;
import java.text.*;
import java.util.*;

/** Stable wallpaper identities and policy, independent of Android views and file I/O. */
final class WallpaperCatalog {
    private static final java.util.regex.Pattern USER_ID=java.util.regex.Pattern.compile("user-[a-f0-9-]{36}"),HASH=java.util.regex.Pattern.compile("[a-f0-9]{64}"),UPSTREAM=java.util.regex.Pattern.compile("[0-9]{8}([0-9]{4})?");
    static final String DEFAULT = "default", BUILTIN = "yosemite-valley";
    static final String DAILY = "daily", BING = "bing-cn", MICROSOFT = "spotlight-cn", MOVIE = "movie-daily", BT = "bt-landscape";
    // BT is accepted only for reading legacy caches until a Bing image is committed.
    static boolean dailyProvider(String source) { return BING.equals(source)||MICROSOFT.equals(source)||MOVIE.equals(source); }
    static boolean dailySource(String source) { return dailyProvider(source)||BT.equals(source); }
    static String sourceLabel(String source,boolean chinese) { return MOVIE.equals(source)?chinese?"每日电影":"Daily movie":MICROSOFT.equals(source)?chinese?"微软壁纸":"Microsoft wallpapers":chinese?"必应":"Bing"; }
    static final class DailyImage {
        String source=BING, key="", url="", title="", copyright="", link="", upstream="", day="";
        int slot,width,height;
        long checked;
        MovieWallpaperFeed movie;
        String dateLabel(){
            if(MOVIE.equals(source)&&movie!=null)return movie.contentDate;
            if(!BING.equals(source)||!UPSTREAM.matcher(upstream).matches())return "";
            SimpleDateFormat input=new SimpleDateFormat(upstream.length()==12?"yyyyMMddHHmm":"yyyyMMdd",Locale.US);input.setLenient(false);
            if(upstream.length()==12)input.setTimeZone(TimeZone.getTimeZone("UTC"));
            try{return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(input.parse(upstream));}catch(ParseException e){return "";}
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("source",source).put("key",key).put("url",url).put("title",title).put("copyright",copyright).put("link",link).put("upstream",upstream).put("day",day).put("slot",slot).put("width",width).put("height",height).put("checked",checked).put("movie",movie==null?JSONObject.NULL:movie.json());
        }
        static DailyImage read(JSONObject json) {
            if(json==null||!dailySource(json.optString("source")))return null;
            DailyImage image=new DailyImage();image.source=json.optString("source");image.key=json.optString("key");image.url=json.optString("url");image.title=json.optString("title");image.copyright=json.optString("copyright");image.link=json.optString("link");image.upstream=json.optString("upstream");image.day=json.optString("day");image.checked=json.optLong("checked");image.slot=json.optInt("slot");image.width=json.optInt("width");image.height=json.optInt("height");
            if(MOVIE.equals(image.source)){try{image.movie=MovieWallpaperFeed.read(json.getJSONObject("movie"));if(!image.key.equals(image.movie.hash))return null;}catch(java.io.IOException|JSONException error){return null;}}
            return HASH.matcher(image.key).matches()&&(image.slot==0||image.slot==1)&&image.width>0&&image.height>0?image:null;
        }
    }
    static final class Entry {
        final String id, name;
        final int width, height;
        final long bytes;
        final String kind,videoMime;
        final long durationMs;
        final float fps,pixelRatio;
        final int rotation;
        String diagnosticFingerprint="unknown",diagnosticTransfer="unknown";
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
            if (BUILTIN.equals(id)) return chinese ? "优胜美地山谷" : "Yosemite Valley";
            if (DAILY.equals(id)) return chinese ? "每日壁纸" : "Daily wallpaper";
            return name;
        }
    }
    String current = DEFAULT;
    boolean rotating,sound,weather,weatherDayNight=true;
    int rotationSeconds=WallpaperRotation.DEFAULT_SECONDS,imageRotationSeconds=WallpaperRotation.DEFAULT_SECONDS,videoRotationSeconds=WallpaperRotation.DEFAULT_SECONDS;
    String lastVideo="",staticBeforeVideo=DEFAULT,lastFixed=DEFAULT;
    boolean rotationBeforeVideo, wallpaperBuiltinHidden, saverBuiltinHidden;
    // Last successfully selected source. Pending requests never change this value.
    String dailySource=BING,lastLocal=DEFAULT;

    boolean dailyMovieSelected(){return !weather&&DAILY.equals(current)&&MOVIE.equals(dailySource);}
    final Map<String,DailyImage> dailyImages=new LinkedHashMap<>();
    final List<Entry> custom = new ArrayList<>();

    static WallpaperCatalog freshInstall(){
        WallpaperCatalog catalog=new WallpaperCatalog();
        catalog.selectFixed(BUILTIN);
        catalog.select(DAILY);
        return catalog;
    }

    List<Entry> entries() {
        List<Entry> result=new ArrayList<>();
        result.add(new Entry(DEFAULT,"",0,0,0));
        result.add(new Entry(BUILTIN,"",1280,720,187415));
        result.add(new Entry(DAILY,"",0,0,0));
        result.addAll(custom); return result;
    }
    Entry find(String id) { for (Entry entry:entries()) if (entry.id.equals(id)) return entry; return null; }
    List<Entry> pictures() { List<Entry> result=new ArrayList<>();for(Entry e:entries())if(!e.video()&&!DEFAULT.equals(e.id)&&!DAILY.equals(e.id)&&!builtinHidden(e.id,false))result.add(e);return result; }
    List<Entry> videos(){List<Entry> result=new ArrayList<>();for(Entry e:entries())if(e.video())result.add(e);return result;}
    boolean audible(){return sound&&!rotating&&!weather;}
    boolean dynamic(){Entry e=find(current);return e!=null&&e.video();}
    int count(boolean video){return video?videos().size():pictures().size();}
    boolean builtinHidden(String id,boolean saver){return BUILTIN.equals(id)&&(saver?saverBuiltinHidden:wallpaperBuiltinHidden);}
    private String firstCustomPicture(){for(Entry entry:custom)if(!entry.video())return entry.id;return DEFAULT;}
    String saverBuiltinFallback(){String id=firstCustomPicture();return DEFAULT.equals(id)?ScreensaverSettings.NO_MEDIA:id;}
    String dailyFallback(){return DEFAULT.equals(lastLocal)?wallpaperBuiltinHidden?firstCustomPicture():BUILTIN:lastLocal;}
    void builtinHidden(boolean saver,boolean hidden){
        if(saver)saverBuiltinHidden=hidden;
        else {wallpaperBuiltinHidden=hidden;normalize();}
    }
    String fixedCandidate(String id){
        if(DAILY.equals(id))id=lastFixed;
        return DEFAULT.equals(id)?wallpaperBuiltinHidden?firstCustomPicture():BUILTIN:id;
    }
    void normalize() {
        if(wallpaperBuiltinHidden){
            if(BUILTIN.equals(current))current=firstCustomPicture();
            if(BUILTIN.equals(staticBeforeVideo))staticBeforeVideo=firstCustomPicture();
            if(BUILTIN.equals(lastLocal))lastLocal=firstCustomPicture();
            if(BUILTIN.equals(lastFixed))lastFixed=firstCustomPicture();
        }
        if (find(current)==null) {current=staticBeforeVideo;rotating=rotationBeforeVideo;}
        if(find(current)==null)current=DEFAULT;
        Entry previous=find(staticBeforeVideo);if(previous==null||previous.video()){staticBeforeVideo=DEFAULT;rotationBeforeVideo=false;}
        Entry video=find(lastVideo);if(video==null||!video.video()){List<Entry> pool=videos();lastVideo=pool.isEmpty()?"":pool.get(0).id;}
        if (DEFAULT.equals(current) || DAILY.equals(current) || (dynamic()?videos():pictures()).size()<2) rotating=false;
        if(!WallpaperRotation.valid(imageRotationSeconds))imageRotationSeconds=WallpaperRotation.DEFAULT_SECONDS;
        if(!WallpaperRotation.valid(videoRotationSeconds))videoRotationSeconds=WallpaperRotation.DEFAULT_SECONDS;
        rotationSeconds=dynamic()?videoRotationSeconds:imageRotationSeconds;
        if(DAILY.equals(lastLocal)||find(lastLocal)==null||find(lastLocal).video())lastLocal=DEFAULT;
        if(DAILY.equals(lastFixed)||find(lastFixed)==null)lastFixed=DEFAULT;
    }
    void selectFixed(String id){select(id);if(!DAILY.equals(id))lastFixed=id;}
    void select(String id) {
        if (find(id)==null) throw new IllegalArgumentException("Unknown wallpaper");
        if(find(id).video()){if(!dynamic()){staticBeforeVideo=current;rotationBeforeVideo=rotating;}lastVideo=id;}
        if(DAILY.equals(id)&&!DAILY.equals(current)){String local=dynamic()?staticBeforeVideo:current;if(!DAILY.equals(local))lastLocal=local;}
        current=id;rotating=false;weather=false;rotationSeconds=dynamic()?videoRotationSeconds:imageRotationSeconds;
    }
    void stopVideo(){if(!dynamic())return;current=staticBeforeVideo;rotating=rotationBeforeVideo;normalize();}
    void updateDaily(DailyImage image,boolean apply){
        dailyImages.put(image.source,image);
        if(apply){select(DAILY);dailySource=image.source;}
        else if(BT.equals(dailySource)&&BING.equals(image.source))dailySource=BING;
    }
    void rotation(boolean video,int seconds,String start) {
        if(seconds==0){rotating=false;return;}
        if(!WallpaperRotation.valid(seconds))throw new IllegalArgumentException("Unsupported rotation interval");
        List<Entry> pool=video?videos():pictures();
        if(pool.size()<2)throw new IllegalArgumentException("Rotation needs at least two wallpapers");
        Entry entry=find(start);if(entry==null||entry.video()!=video||DEFAULT.equals(start)||DAILY.equals(start)||builtinHidden(start,false))start=pool.get(0).id;
        select(start);rotationSeconds=seconds;if(video)videoRotationSeconds=seconds;else imageRotationSeconds=seconds;rotating=true;
    }
    void next() {
        List<Entry> pool=dynamic()?videos():pictures();
        if (!rotating || pool.size()<2) { normalize(); return; }
        int n=0;
        for (int i=0;i<pool.size();i++) if (pool.get(i).id.equals(current)) n=i;
        current=pool.get((n+1)%pool.size()).id;
        if(dynamic())lastVideo=current;
    }
    String nextPlayableVideo(String after,Set<String> failed){
        List<Entry> pool=videos();int index=-1;for(int i=0;i<pool.size();i++)if(pool.get(i).id.equals(after))index=i;
        for(int i=1;i<=pool.size();i++){String id=pool.get((index+i)%pool.size()).id;if(!failed.contains(id))return id;}return null;
    }
    void remove(String id) {
        Entry entry=find(id); if (entry==null) return;
        if(!entry.custom())throw new IllegalArgumentException("Wallpaper entry cannot be removed");
        List<Entry> pool=entry.video()?videos():pictures(); int position=0;
        for (int i=0;i<pool.size();i++) if (pool.get(i).id.equals(id)) position=i;
        custom.remove(entry);
        pool=entry.video()?videos():pictures();
        if (current.equals(id)){
            if(rotating&&!pool.isEmpty()){current=pool.get(position%pool.size()).id;if(entry.video())lastVideo=current;}
            else if(entry.video()){current=staticBeforeVideo;rotating=rotationBeforeVideo;}
            else current=wallpaperBuiltinHidden?firstCustomPicture():BUILTIN;
        }
        normalize();
    }
    long bytes() {return bytes(false);}
    long bytes(boolean video){long size=0;for(Entry e:custom)if(e.video()==video)size+=e.bytes;return size;}
    void rename(String id,String name) {
        for(int index=0;index<custom.size();index++){
            Entry entry=custom.get(index);if(!entry.id.equals(id))continue;
            custom.set(index,new Entry(entry.id,name,entry.width,entry.height,entry.bytes,entry.kind,entry.videoMime,entry.durationMs,entry.fps,entry.rotation,entry.pixelRatio));return;
        }
        throw new IllegalArgumentException("素材已移除，请重新选择");
    }
    void add(Entry entry) {
        custom.add(entry);
    }
    WallpaperCatalog copy() {
        WallpaperCatalog result=new WallpaperCatalog();
        result.current=current;result.rotating=rotating;result.sound=sound;result.weather=weather;result.weatherDayNight=weatherDayNight;
        result.rotationSeconds=rotationSeconds;result.imageRotationSeconds=imageRotationSeconds;result.videoRotationSeconds=videoRotationSeconds;
        result.lastVideo=lastVideo;result.staticBeforeVideo=staticBeforeVideo;result.lastFixed=lastFixed;result.rotationBeforeVideo=rotationBeforeVideo;result.dailySource=dailySource;result.lastLocal=lastLocal;
        result.wallpaperBuiltinHidden=wallpaperBuiltinHidden;result.saverBuiltinHidden=saverBuiltinHidden;
        result.custom.addAll(custom); // Entries contain only immutable scalar values.
        for(Map.Entry<String,DailyImage> entry:dailyImages.entrySet()){
            DailyImage source=entry.getValue(),image=new DailyImage();image.source=source.source;image.key=source.key;image.url=source.url;image.title=source.title;image.copyright=source.copyright;image.link=source.link;image.upstream=source.upstream;image.day=source.day;image.slot=source.slot;image.width=source.width;image.height=source.height;image.checked=source.checked;image.movie=source.movie==null?null:source.movie.snapshot();result.dailyImages.put(entry.getKey(),image);
        }
        result.normalize();return result;
    }
    JSONObject json() throws JSONException {
        JSONObject result=new JSONObject().put("version",2).put("current",current).put("rotating",rotating).put("sound",sound).put("weather",weather).put("weatherDayNight",weatherDayNight);
        result.put("lastVideo",lastVideo).put("staticBeforeVideo",staticBeforeVideo).put("rotationBeforeVideo",rotationBeforeVideo).put("lastFixed",lastFixed);
        result.put("rotationSeconds",rotationSeconds).put("imageRotationSeconds",imageRotationSeconds).put("videoRotationSeconds",videoRotationSeconds);
        result.put("rotationMinutes",WallpaperRotation.legacyMinutes(rotationSeconds))
                .put("imageRotationMinutes",WallpaperRotation.legacyMinutes(imageRotationSeconds))
                .put("videoRotationMinutes",WallpaperRotation.legacyMinutes(videoRotationSeconds));
        if(wallpaperBuiltinHidden)result.put("wallpaperBuiltinHidden",true);
        if(saverBuiltinHidden)result.put("saverBuiltinHidden",true);
        JSONArray user=new JSONArray();
        for (Entry e:custom) user.put(new JSONObject().put("id",e.id).put("name",e.name).put("width",e.width).put("height",e.height).put("bytes",e.bytes).put("kind",e.kind).put("mime",e.videoMime).put("durationMs",e.durationMs).put("fps",e.fps).put("rotation",e.rotation).put("pixelRatio",e.pixelRatio).put("diagnosticFingerprint",e.diagnosticFingerprint).put("diagnosticTransfer",e.diagnosticTransfer));
        JSONObject images=new JSONObject();for(DailyImage image:dailyImages.values())images.put(image.source,image.json());
        return result.put("custom",user).put("daily",new JSONObject().put("source",dailySource).put("lastLocal",lastLocal).put("images",images));
    }
    private static int readRotation(JSONObject json,String secondsKey,String minutesKey,int fallback){
        return json.optInt(secondsKey,json.has(minutesKey)?WallpaperRotation.fromMinutes(json.optInt(minutesKey,5)):fallback);
    }
    static WallpaperCatalog read(JSONObject json) throws JSONException {
        if (json.getInt("version")!=1&&json.getInt("version")!=2) throw new JSONException("Unsupported wallpaper catalog");
        WallpaperCatalog result=new WallpaperCatalog();
        result.current=json.optString("current",DEFAULT); result.rotating=json.optBoolean("rotating",false);result.sound=json.optBoolean("sound",false);result.weather=json.optBoolean("weather",false);result.weatherDayNight=json.optBoolean("weatherDayNight",true);
        result.lastVideo=json.optString("lastVideo","");result.staticBeforeVideo=json.optString("staticBeforeVideo",DEFAULT);result.rotationBeforeVideo=json.optBoolean("rotationBeforeVideo",false);
        int minutes=json.optInt("rotationMinutes",5);
        result.rotationSeconds=json.optInt("rotationSeconds",WallpaperRotation.fromMinutes(minutes));
        result.imageRotationSeconds=readRotation(json,"imageRotationSeconds","imageRotationMinutes",result.rotationSeconds);
        result.videoRotationSeconds=readRotation(json,"videoRotationSeconds","videoRotationMinutes",result.rotationSeconds);
        JSONArray users=json.optJSONArray("custom");
        if(users!=null) for(int i=0;i<users.length();i++) {
            JSONObject e=users.getJSONObject(i); String id=e.getString("id");
            if(!USER_ID.matcher(id).matches() || result.find(id)!=null) continue;
            String kind=e.optString("kind","image");if(!"image".equals(kind)&&!"video".equals(kind))continue;
            Entry entry=new Entry(id,e.getString("name"),e.getInt("width"),e.getInt("height"),e.getLong("bytes"),kind,e.optString("mime"),e.optLong("durationMs"),(float)e.optDouble("fps",0),e.optInt("rotation"),(float)e.optDouble("pixelRatio",1));
            String fingerprint=e.optString("diagnosticFingerprint"),transfer=e.optString("diagnosticTransfer");
            if(fingerprint.matches("media-[a-f0-9]{20}"))entry.diagnosticFingerprint=fingerprint;if(transfer.matches("[a-f0-9-]{36}"))entry.diagnosticTransfer=transfer;
            result.custom.add(entry);
        }
        JSONObject daily=json.optJSONObject("daily");
        if(daily!=null){
            String source=daily.optString("source",BING);if(dailySource(source))result.dailySource=source;
            result.lastLocal=daily.optString("lastLocal",DEFAULT);JSONObject images=daily.optJSONObject("images");
            if(images!=null)for(String id:new String[]{BING,MICROSOFT,MOVIE,BT}){DailyImage image=DailyImage.read(images.optJSONObject(id));if(image!=null&&id.equals(image.source))result.dailyImages.put(id,image);}
        }
        result.lastFixed=json.optString("lastFixed",!result.rotating&&!result.weather&&!DAILY.equals(result.current)?result.current:result.lastLocal);
        result.wallpaperBuiltinHidden=json.optBoolean("wallpaperBuiltinHidden");result.saverBuiltinHidden=json.optBoolean("saverBuiltinHidden");
        result.normalize(); return result;
    }
}
