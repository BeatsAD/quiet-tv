package home.quiet.tv;

import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Portable desktop settings only. Credentials, parental data and system state have no fields here. */
final class DesktopBackupRecord {
    private static final java.util.regex.Pattern IDENTITY=java.util.regex.Pattern.compile("[a-f0-9-]{36}");
    private static final java.util.regex.Pattern SOFTWARE=java.util.regex.Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.1)?");
    private static final java.util.regex.Pattern HASH=java.util.regex.Pattern.compile("[a-f0-9]{64}");
    private static final java.util.regex.Pattern WALLPAPER_ASSET=java.util.regex.Pattern.compile("wallpapers/user-[a-f0-9-]{36}(?:\\.jpg|\\.mp4|-poster\\.jpg)");
    private static final java.util.regex.Pattern AVATAR_ASSET=java.util.regex.Pattern.compile("greeting-avatars/avatar-[a-f0-9-]{36}\\.png");
    private static final java.util.regex.Pattern COVER_ASSET=java.util.regex.Pattern.compile("app-covers/cover-[a-f0-9-]{36}\\.png");
    private static final java.util.regex.Pattern STORED_ASSET=java.util.regex.Pattern.compile("desktop-backups/assets/[a-f0-9]{64}");
    private static final java.util.regex.Pattern SHORTCUT=java.util.regex.Pattern.compile("quiet:[a-z-]{1,48}");
    static final int VERSION=6,MAX_CONFIG_BYTES=2*1024*1024;
    static final String FORMAT="quiet-tv-desktop";
    static final Set<String> DESKTOP_BOOLEAN=names("show-home-quote show-screensaver-quote show-time show-date show-seconds show-lunar-date show-movie-info greeting-enabled greeting-avatar-enabled key-sounds show-app-title show-home-apps top-shelf hide-home-apps-on-second-screen show-watch-tv show-dock-background show-all-apps-names dock-magnify dock-glass global-glass show-buttons show-input show-network show-settings");
    static final Set<String> DESKTOP_STRING=names("home-layout greeting-text greeting-avatar-file icon-size icon-shape home-blur-mode home-buttons app-title-position key-sound-theme clock-style quick-control-visibility");
    static final Set<String> DESKTOP_INTEGER=names("icon-radius dock-radius dock-scale dock-limit dock-magnification home-wallpaper-blur second-wallpaper-blur home-wallpaper-brightness second-wallpaper-brightness home-wallpaper-fade second-wallpaper-fade");
    static final Set<String> DESKTOP_KEYS=union(DESKTOP_BOOLEAN,DESKTOP_STRING,DESKTOP_INTEGER);
    static final Set<String> WEATHER_KEYS=names("enabled adcode city place provider meteo-fallback");
    static final Set<String> DOCK_KEYS=names("packages hidden-apps app-names"),ORDER_KEYS=names("packages");
    final JSONObject json,config;
    final JSONArray assets;
    final String id,kind,fingerprint;
    final long created;

    DesktopBackupRecord(JSONObject input)throws IOException{
        try{
            json=new JSONObject(input.toString());only(json,names("format formatVersion id kind created software build config assets includesAssets checksum"));
            if(json.has("includesAssets"))require(json.get("includesAssets") instanceof Boolean,"备份素材包含方式无效");
            require(FORMAT.equals(json.getString("format"))&&json.getInt("formatVersion")>=1&&json.getInt("formatVersion")<=VERSION,"不支持的桌面备份格式");
            id=json.getString("id");kind=json.getString("kind");created=json.getLong("created");
            require(IDENTITY.matcher(id).matches()&&Arrays.asList("manual","auto","before","upgrade","imported").contains(kind)&&created>0,"备份信息无效");
            require(SOFTWARE.matcher(json.getString("software")).matches()&&json.getInt("build")>0,"备份软件版本无效");
            config=json.getJSONObject("config");validateConfig(config,json.getInt("formatVersion"));assets=json.getJSONArray("assets");validateAssets(config,assets);
            String expected=json.getString("checksum");JSONObject payload=new JSONObject(json.toString());payload.remove("checksum");
            require(HASH.matcher(expected).matches()&&expected.equals(hash(canonical(payload).getBytes(Charsets.UTF_8))),"备份校验失败，文件可能已损坏");
            fingerprint=hash(canonical(new JSONObject().put("config",config).put("assets",assets)).getBytes(Charsets.UTF_8));
        }catch(JSONException|IllegalArgumentException failure){throw new IOException("备份内容无效，当前桌面未改变",failure);}
    }
    static DesktopBackupRecord create(String kind,JSONObject config,JSONArray assets)throws IOException{
        try{
            JSONObject value=new JSONObject().put("format",FORMAT).put("formatVersion",VERSION).put("id",UUID.randomUUID().toString()).put("kind",kind).put("created",System.currentTimeMillis()).put("software",BuildConfig.VERSION_NAME).put("build",BuildConfig.VERSION_CODE).put("config",new JSONObject(config.toString())).put("assets",new JSONArray(assets.toString())).put("includesAssets",false);
            value.put("checksum",hash(canonical(value).getBytes(Charsets.UTF_8)));return new DesktopBackupRecord(value);
        }catch(JSONException failure){throw new IOException("备份无法编码",failure);}
    }
    static DesktopBackupRecord imported(DesktopBackupRecord original,JSONObject config,JSONArray assets)throws IOException{
        try{JSONObject value=new JSONObject(original.json.toString());value.remove("checksum");value.put("id",UUID.randomUUID().toString()).put("kind","imported").put("created",System.currentTimeMillis()).put("config",config).put("assets",assets);value.put("checksum",hash(canonical(value).getBytes(Charsets.UTF_8)));return new DesktopBackupRecord(value);}catch(JSONException failure){throw new IOException("导入备份无法编码",failure);}
    }
    static DesktopBackupRecord media(DesktopBackupRecord record,boolean included)throws IOException{
        try{JSONObject value=new JSONObject(record.json.toString());value.remove("checksum");value.put("includesAssets",included);value.put("checksum",hash(canonical(value).getBytes(Charsets.UTF_8)));return new DesktopBackupRecord(value);}catch(JSONException failure){throw new IOException("备份素材包含方式无法编码",failure);}
    }
    static Set<String> names(String value){return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(value.split(" "))));}
    @SafeVarargs private static Set<String> union(Set<String>... groups){Set<String> result=new LinkedHashSet<>();for(Set<String> group:groups)result.addAll(group);return Collections.unmodifiableSet(result);}
    static void require(boolean valid,String message)throws IOException{if(!valid)throw new IOException(message);}
    static void only(JSONObject value,Set<String> keys)throws IOException{for(Iterator<String> names=value.keys();names.hasNext();)require(keys.contains(names.next()),"备份含不支持的字段");}
    static boolean assetPath(String path){return path!=null&&(WALLPAPER_ASSET.matcher(path).matches()||AVATAR_ASSET.matcher(path).matches()||COVER_ASSET.matcher(path).matches());}
    static boolean sourcePath(String path){return assetPath(path)||path!=null&&STORED_ASSET.matcher(path).matches();}
    private static void bool(JSONObject value,String name)throws IOException,JSONException{if(value.has(name))require(value.get(name) instanceof Boolean,"备份开关类型无效");}
    private static void integer(JSONObject value,String name,int low,int high)throws IOException,JSONException{
        if(!value.has(name))return;Object raw=value.get(name);require(raw instanceof Integer||raw instanceof Long,"备份数值类型无效");long number=((Number)raw).longValue();require(number>=low&&number<=high,"备份数值超出范围");
    }
    private static void choice(JSONObject value,String name,String... options)throws IOException,JSONException{if(value.has(name))require(value.get(name) instanceof String&&Arrays.asList(options).contains(value.getString(name)),"备份选项无效");}
    private static void string(JSONObject value,String name,int maximum)throws IOException,JSONException{if(value.has(name))require(value.get(name) instanceof String&&value.getString(name).length()<=maximum,"备份文字无效");}
    private static List<String> strings(JSONArray values,int maximum,Collection<String> allowed,boolean packages)throws IOException,JSONException{
        List<String> result=new ArrayList<>();Set<String> seen=new HashSet<>();require(values.length()<=maximum,"备份列表过长");for(int i=0;i<values.length();i++){Object raw=values.get(i);require(raw instanceof String,"备份列表类型无效");String id=(String)raw;require(seen.add(id)&&(allowed==null||allowed.contains(id))&&(!packages||AppCoverStore.packageName(id)||SHORTCUT.matcher(id).matches()),"备份列表内容无效");result.add(id);}return result;
    }
    static void validateConfig(JSONObject config)throws IOException,JSONException{validateConfig(config,VERSION);}
    private static void validateConfig(JSONObject config,int version)throws IOException,JSONException{
        only(config,names("desktop weather dock order wallpaper screensaver shortcuts covers"));
        // Restore existing long greetings without truncation; both editors still enforce 80 for new edits.
        JSONObject desktop=config.getJSONObject("desktop");only(desktop,DESKTOP_KEYS);for(String name:DESKTOP_BOOLEAN)bool(desktop,name);for(String name:DESKTOP_STRING)string(desktop,name,4096);
        choice(desktop,"home-layout","classic","gallery");choice(desktop,"icon-size","small","medium","large");choice(desktop,"icon-shape","rectangle","square","circle");choice(desktop,"home-blur-mode","uniform","progressive");
        choice(desktop,"app-title-position","hidden","below","top-left");
        choice(desktop,"clock-style",DesktopSettingsStore.CLOCK_STYLES);choice(desktop,"quick-control-visibility",DesktopSettingsStore.CONTROL_VISIBILITY);
        choice(desktop,"key-sound-theme",KeySoundTheme.ids());
        for(String name:DESKTOP_INTEGER){int low=name.endsWith("brightness")?20:name.equals("dock-scale")?70:name.equals("dock-magnification")?100:0,high=name.endsWith("fade")?1200:name.endsWith("radius")?50:name.equals("dock-scale")?130:name.equals("dock-magnification")?160:name.equals("dock-limit")?12:100;integer(desktop,name,low,high);if(desktop.has(name)){int n=desktop.getInt(name);if(name.endsWith("fade"))require(n%100==0,"备份淡入时间无效");if(name.equals("dock-scale")||name.equals("dock-magnification"))require(n%5==0,"备份缩放数值无效");if(name.equals("dock-limit"))require(n==5||n==7||n==10||n==12,"备份 Dock 上限无效");}}
        String avatar=desktop.optString("greeting-avatar-file","");require(avatar.isEmpty()||GreetingAvatarStore.validName(avatar),"备份头像名称无效");if(desktop.has("home-buttons"))strings(new JSONArray(desktop.getString("home-buttons")),3,HomeButtonStore.ALL,false);
        JSONObject weather=config.getJSONObject("weather");only(weather,WEATHER_KEYS);bool(weather,"enabled");bool(weather,"meteo-fallback");string(weather,"adcode",200);string(weather,"city",120);string(weather,"place",4096);choice(weather,"provider","auto","uapi","qweather","openweather","openmeteo");if(weather.has("place")&&!weather.getString("place").isEmpty())WeatherRegions.readPlace(new JSONObject(weather.getString("place")));
        JSONObject dock=config.getJSONObject("dock");only(dock,DOCK_KEYS);if(dock.has("packages"))strings(new JSONArray(dock.getString("packages")),DockStore.CAPACITY,null,true);if(dock.has("hidden-apps")){JSONObject hidden=new JSONObject(dock.getString("hidden-apps"));for(Iterator<String> keys=hidden.keys();keys.hasNext();){String pkg=keys.next();require(AppCoverStore.packageName(pkg),"备份隐藏应用无效");integer(hidden,pkg,-1,DockStore.CAPACITY);}}
        if(dock.has(AppNameStore.KEY)){require(dock.get(AppNameStore.KEY) instanceof String,"备份应用名称类型无效");AppNameStore.validate(dock.getString(AppNameStore.KEY));}
        JSONObject order=config.getJSONObject("order");only(order,ORDER_KEYS);if(order.has("packages"))strings(new JSONArray(order.getString("packages")),MAX_CONFIG_BYTES,null,true);
        strings(config.getJSONArray("shortcuts"),QuickControlStore.ALL.size(),QuickControlStore.ALL,false);
        JSONObject walls=config.getJSONObject("wallpaper");WallpaperCatalog catalog=WallpaperCatalog.read(walls);JSONObject normalizedWalls=catalog.json();
        if(!walls.has("rotationSeconds")&&!walls.has("imageRotationSeconds")&&!walls.has("videoRotationSeconds")){
            for(String key:new String[]{"rotationSeconds","imageRotationSeconds","videoRotationSeconds"})normalizedWalls.remove(key);
        }
        // Diagnostics were added without changing wallpaper identities in older backups.
        JSONArray savedMedia=walls.getJSONArray("custom"),normalizedMedia=normalizedWalls.getJSONArray("custom");for(int i=0;i<savedMedia.length()&&i<normalizedMedia.length();i++)for(String key:new String[]{"diagnosticFingerprint","diagnosticTransfer"})if(!savedMedia.getJSONObject(i).has(key))normalizedMedia.getJSONObject(i).remove(key);
        require(canonical(normalizedWalls).equals(canonical(walls)),"备份壁纸清单无效");require(catalog.dailyImages.isEmpty(),"备份不接受动态缓存");for(WallpaperCatalog.Entry entry:catalog.custom)require(entry.width>0&&entry.height>0&&entry.bytes>0&&entry.name.length()<=256,"备份素材信息无效");
        JSONObject savedSaver=new JSONObject(config.getJSONObject("screensaver").toString());
        // Older backups stored these wallpaper wall options.
        savedSaver.remove("wallInterval");
        savedSaver.remove("wallDensity");
        if(savedSaver.has("aerialExcluded")){
            JSONArray excluded=savedSaver.getJSONArray("aerialExcluded");
            require(excluded.length()<=128,"备份航拍清单无效");
            Set<String> ids=new HashSet<>();
            for(int i=0;i<excluded.length();i++){
                String id=excluded.getString(i);
                require(id.length()<=80&&id.matches("aerial-[a-z0-9-]+")&&ids.add(id),"备份航拍清单无效");
            }
            savedSaver.remove("aerialExcluded");
        }
        ScreensaverSettings saver=ScreensaverSettings.read(savedSaver);
        JSONObject normalizedSaver=saver.json();
        // Validate the stored form without changing the original backup checksum.
        if(ScreensaverSettings.RETIRED_AERIAL.equals(savedSaver.opt("mode")))
            normalizedSaver.put("mode",ScreensaverSettings.RETIRED_AERIAL);
        if(savedSaver.has("version"))normalizedSaver.put("version",savedSaver.getInt("version"));
        for(String key:new String[]{"version","mode","timePosition","weatherPosition","sound","flipShowSeconds","flipSound"})
            if(!savedSaver.has(key))normalizedSaver.remove(key);
        for(String key:ScreensaverSettings.ClockOptions.KEYS)
            if(!savedSaver.has(key))normalizedSaver.remove(key);
        require(catalog.find(saver.mediaId)!=null&&canonical(normalizedSaver).equals(canonical(savedSaver)),"备份屏保信息无效");
        JSONObject covers=config.getJSONObject("covers");for(Iterator<String> keys=covers.keys();keys.hasNext();){String pkg=keys.next();require(AppCoverStore.packageName(pkg),"备份封面应用无效");LibraryCover.validate(covers.getJSONObject(pkg),version);}
    }
    static Set<String> requiredAssets(JSONObject config)throws IOException,JSONException{
        Set<String> paths=new LinkedHashSet<>();WallpaperCatalog catalog=WallpaperCatalog.read(config.getJSONObject("wallpaper"));for(WallpaperCatalog.Entry entry:catalog.custom)paths.add("wallpapers/"+entry.id+(entry.video()?".mp4":".jpg"));String avatar=config.getJSONObject("desktop").optString("greeting-avatar-file","");if(!avatar.isEmpty())paths.add("greeting-avatars/"+avatar);JSONObject covers=config.getJSONObject("covers");for(Iterator<String> keys=covers.keys();keys.hasNext();)paths.add("app-covers/"+covers.getJSONObject(keys.next()).getString("file"));return paths;
    }
    private static void validateAssets(JSONObject config,JSONArray assets)throws IOException,JSONException{
        Set<String> required=requiredAssets(config),actual=new HashSet<>();WallpaperCatalog catalog=WallpaperCatalog.read(config.getJSONObject("wallpaper"));String previous="";
        for(int i=0;i<assets.length();i++){JSONObject item=assets.getJSONObject(i);only(item,names("path bytes sha256"));String path=item.getString("path");require(assetPath(path)&&actual.add(path)&&path.compareTo(previous)>0,"备份素材路径无效或重复");previous=path;require(item.getLong("bytes")>0&&HASH.matcher(item.getString("sha256")).matches(),"备份素材校验信息无效");if(!required.contains(path)){require(path.startsWith("wallpapers/")&&path.endsWith("-poster.jpg"),"备份含未引用素材");String id=path.substring(11,path.length()-11);WallpaperCatalog.Entry entry=catalog.find(id);require(entry!=null&&entry.video(),"备份视频预览无效");}}
        require(actual.containsAll(required),"备份缺少素材清单");for(WallpaperCatalog.Entry entry:catalog.custom){String path="wallpapers/"+entry.id+(entry.video()?".mp4":".jpg");for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);if(path.equals(asset.getString("path")))require(entry.bytes==asset.getLong("bytes"),"备份素材大小与清单不一致");}}
    }
    static String canonical(Object value)throws JSONException{
        if(value==null||value==JSONObject.NULL)return "null";if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();for(Iterator<String> names=object.keys();names.hasNext();)keys.add(names.next());Collections.sort(keys);StringBuilder result=new StringBuilder("{");for(String key:keys){if(result.length()>1)result.append(',');result.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));}return result.append('}').toString();}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder result=new StringBuilder("[");for(int i=0;i<array.length();i++){if(i>0)result.append(',');result.append(canonical(array.get(i)));}return result.append(']').toString();}return value instanceof String?JSONObject.quote((String)value):value instanceof Number?JSONObject.numberToString((Number)value):value.toString();
    }
    static String hash(byte[] bytes){return hex(digest().digest(bytes));}
    static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    static String hex(byte[] bytes){StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
}
