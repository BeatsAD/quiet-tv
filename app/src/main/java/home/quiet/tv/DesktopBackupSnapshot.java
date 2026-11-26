package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Reads and applies the explicit desktop whitelist, leaving every other store untouched. */
final class DesktopBackupSnapshot {
    private final Context context;
    private final Map<String,Stamp> hashes=new HashMap<>();
    private final Set<String> readableMedia=new HashSet<>();
    private static final class Stamp {final long bytes,modified;final String hash;Stamp(File file,String hash){bytes=file.length();modified=file.lastModified();this.hash=hash;}}
    DesktopBackupSnapshot(Context context){this.context=context.getApplicationContext();}
    private JSONObject preferences(String name,Set<String> keys)throws JSONException,IOException{
        JSONObject values=new JSONObject();Map<String,?> current=context.getSharedPreferences(name,0).getAll();for(String key:keys)if(current.containsKey(key))values.put(key,current.get(key));
        File file=new File(context.getFilesDir().getParentFile(),"shared_prefs/"+name+".xml"),backup=new File(file+".bak");if(backup.isFile())file=backup;JSONObject saved=new JSONObject();
        if(file.isFile())try(InputStream input=new FileInputStream(file)){
            org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();parser.setInput(input,"UTF-8");int event;
            while((event=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){if(event!=org.xmlpull.v1.XmlPullParser.START_TAG||parser.getDepth()!=2)continue;String key=parser.getAttributeValue(null,"name");if(!keys.contains(key))continue;String tag=parser.getName(),raw=parser.getAttributeValue(null,"value");if(tag.equals("string"))saved.put(key,parser.nextText());else if(tag.equals("boolean"))saved.put(key,Boolean.parseBoolean(raw));else if(tag.equals("int"))saved.put(key,Integer.parseInt(raw));else if(tag.equals("long"))saved.put(key,Long.parseLong(raw));else throw new IOException("桌面配置字段类型无效");}
        }catch(org.xmlpull.v1.XmlPullParserException|NumberFormatException invalid){throw new IOException("桌面配置文件无法读取，备份已暂停",invalid);}
        DesktopBackupRecord.require(DesktopBackupRecord.canonical(values).equals(DesktopBackupRecord.canonical(saved)),"配置尚未保存到存储，请稍后重试备份");return values;
    }
    JSONObject read()throws IOException{
        try{
            WallpaperCatalog catalog=WallpaperStore.get(context).backupSnapshot();JSONObject walls=catalog.json();walls.getJSONObject("daily").put("images",new JSONObject());
            QuickControlStore quick=new QuickControlStore(new File(context.getFilesDir(),"quick-controls"));
            JSONObject saver=ScreensaverStore.get(context).backupSnapshot(catalog).json();
            JSONObject result=new JSONObject().put("desktop",preferences("desktop-settings",DesktopBackupRecord.DESKTOP_KEYS)).put("weather",preferences("weather",DesktopBackupRecord.WEATHER_KEYS)).put("dock",preferences("dock",DesktopBackupRecord.DOCK_KEYS)).put("order",preferences("home-order",DesktopBackupRecord.ORDER_KEYS)).put("wallpaper",walls).put("screensaver",saver).put("shortcuts",new JSONArray(quick.read().ids)).put("covers",AppCoverStore.get(context).backupSnapshot());
            DesktopBackupRecord.validateConfig(result);return result;
        }catch(JSONException|RuntimeException failure){throw new IOException("当前桌面配置无法读取，已有备份保留",failure);}
    }
    static String comparable(JSONObject config)throws JSONException{
        JSONObject copy=new JSONObject(config.toString()),walls=WallpaperCatalog.read(copy.getJSONObject("wallpaper")).json();
        // Compare the same defaults that the stores write when restoring an older backup.
        JSONObject savedSaver=copy.getJSONObject("screensaver"),saver=ScreensaverSettings.read(savedSaver).json();
        copy.put("wallpaper",walls).put("screensaver",saver);
        if(walls.optBoolean("rotating")){walls.remove("current");walls.remove("lastVideo");}JSONArray shortcuts=copy.getJSONArray("shortcuts");List<String> ids=new ArrayList<>();for(int i=0;i<shortcuts.length();i++)ids.add(shortcuts.getString(i));copy.put("shortcuts",new JSONArray(QuickControlStore.normalize(ids)));return DesktopBackupRecord.canonical(copy);
    }
    DesktopBackupRecord capture(String kind)throws IOException{
        try{
            JSONObject config=read();JSONArray assets=collect(config);if(!comparable(config).equals(comparable(read())))throw new IOException("桌面配置已变化，请重新保存备份");return DesktopBackupRecord.create(kind,config,assets);
        }catch(JSONException failure){throw new IOException("当前桌面无法备份",failure);}
    }
    JSONArray collect(JSONObject config)throws IOException,JSONException{
        TreeSet<String> paths=new TreeSet<>(DesktopBackupRecord.requiredAssets(config));WallpaperCatalog catalog=WallpaperCatalog.read(config.getJSONObject("wallpaper"));for(WallpaperCatalog.Entry entry:catalog.custom)if(entry.video()){String poster="wallpapers/"+entry.id+"-poster.jpg";if(file(context,poster).isFile())paths.add(poster);}
        JSONArray assets=new JSONArray();for(String path:paths){File file=file(context,path);DesktopBackupRecord.require(file.isFile()&&file.length()>0,"素材缺失，备份未保存："+file.getName());String hash=hash(file,false);media(file,path,hash);assets.put(new JSONObject().put("path",path).put("bytes",file.length()).put("sha256",hash));}return assets;
    }
    void media(File file,String path,String hash)throws IOException{
        String category=path.endsWith(".mp4")?"video":path.startsWith("greeting-avatars/")?"avatar":path.startsWith("app-covers/")?"cover":"image",key=category+":"+hash;if(readableMedia.contains(key))return;
        try{
            if(category.equals("video")){WallpaperVideo.Info info=WallpaperVideo.inspect(file,"source=desktop_backup");DesktopBackupRecord.require(info.width>0&&info.height>0,"备份视频信息无法读取");}
            else{
                android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),bounds);int w=bounds.outWidth,h=bounds.outHeight;
                DesktopBackupRecord.require(w>0&&h>0&&w<=16384&&h<=16384&&(long)w*h<=40000000L&&Arrays.asList("image/jpeg","image/png","image/webp").contains(bounds.outMimeType),"备份图片格式无法读取");
                if(category.equals("avatar"))DesktopBackupRecord.require("image/png".equals(bounds.outMimeType)&&w==512&&h==512,"备份头像尺寸无效");
                if(category.equals("cover"))DesktopBackupRecord.require("image/png".equals(bounds.outMimeType)&&(w==720&&(h==406||h==405)||w==512&&h==512),"备份封面尺寸无效");
                AppCoverImages.checkContainer(file,bounds.outMimeType);bounds.inJustDecodeBounds=false;bounds.inSampleSize=1;while(w/bounds.inSampleSize>256||h/bounds.inSampleSize>256)bounds.inSampleSize*=2;android.graphics.Bitmap decoded=android.graphics.BitmapFactory.decodeFile(file.getPath(),bounds);DesktopBackupRecord.require(decoded!=null,"备份图片无法解码");decoded.recycle();
            }
            readableMedia.add(key);
        }catch(RuntimeException|OutOfMemoryError unavailable){throw new IOException("备份素材无法读取，请检查格式或本机播放能力",unavailable);}
    }
    String hash(File file,boolean verify)throws IOException{
        String path=file.getCanonicalPath();Stamp old=hashes.get(path);if(!verify&&old!=null&&file.length()==old.bytes&&file.lastModified()==old.modified)return old.hash;
        long bytes=file.length(),modified=file.lastModified();MessageDigest digest=DesktopBackupRecord.digest();try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[64*1024];int read;while((read=input.read(buffer))!=-1)digest.update(buffer,0,read);}
        DesktopBackupRecord.require(file.length()==bytes&&file.lastModified()==modified,"素材已变化，请重新操作");String hash=DesktopBackupRecord.hex(digest.digest());hashes.put(path,new Stamp(file,hash));return hash;
    }
    static File file(Context context,String path)throws IOException{
        if(!DesktopBackupRecord.sourcePath(path))throw new IOException("备份素材路径无效");
        File root=context.getFilesDir().getCanonicalFile(),file=new File(root,path).getCanonicalFile();
        if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("备份素材路径不可用");
        return file;
    }
    private void applyPreferences(String name,Set<String> keys,JSONObject values,boolean buttons)throws IOException,JSONException{
        SharedPreferences prefs=context.getSharedPreferences(name,0);SharedPreferences.Editor edit=prefs.edit();for(String key:keys){if(!values.has(key)){edit.remove(key);continue;}Object value=values.get(key);if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Number)edit.putInt(key,((Number)value).intValue());else edit.putString(key,(String)value);}
        if(buttons){long revision=prefs.getLong("home-buttons-revision",0);DesktopBackupRecord.require(revision<Long.MAX_VALUE,"首页按钮版本无法更新");edit.putLong("home-buttons-revision",revision+1);}
        if(!edit.commit())throw new IOException("桌面配置写入失败，正在保留原配置");
    }
    void apply(JSONObject config)throws IOException{
        try{
            DesktopBackupRecord.validateConfig(config);
            synchronized(DesktopSettingsStore.MUTATIONS){applyPreferences("desktop-settings",DesktopBackupRecord.DESKTOP_KEYS,config.getJSONObject("desktop"),true);}
            applyPreferences("weather",DesktopBackupRecord.WEATHER_KEYS,config.getJSONObject("weather"),false);applyPreferences("dock",DesktopBackupRecord.DOCK_KEYS,config.getJSONObject("dock"),false);applyPreferences("home-order",DesktopBackupRecord.ORDER_KEYS,config.getJSONObject("order"),false);
            QuickControlStore quick=new QuickControlStore(new File(context.getFilesDir(),"quick-controls"));JSONArray ids=config.getJSONArray("shortcuts");List<String> values=new ArrayList<>();for(int i=0;i<ids.length();i++)values.add(ids.getString(i));quick.restore(quick.read().revision,values);
            WallpaperStore.get(context).restoreBackup(config.getJSONObject("wallpaper"));
            ScreensaverStore.get(context).save(ScreensaverSettings.read(config.getJSONObject("screensaver")));AppCoverStore.get(context).restoreBackup(config.getJSONObject("covers"));
            if(!context.getSharedPreferences("desktop-settings",0).getBoolean("key-sounds",false))KeySounds.stop();
            if(!comparable(config).equals(comparable(read())))throw new IOException("恢复写入核对未通过");
        }catch(JSONException|RuntimeException failure){throw new IOException("桌面恢复未完成",failure);}
    }
}
