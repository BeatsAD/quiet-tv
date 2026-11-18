package home.quiet.tv;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.core.util.AtomicFile;
import android.util.Log;
import org.json.*;
import java.io.*;
import home.quiet.tv.Charsets;
import java.util.*;

/** One process-wide, atomically saved wallpaper catalog for every native surface. */
final class WallpaperStore {
    private static WallpaperStore instance;
    private final File directory;
    private final Context context;
    private final AtomicFile catalogFile;
    private final ScreensaverStore screensavers;
    private WallpaperCatalog catalog;
    private long manualVersion;
    private long dailyOperation;
    private String dailyPending="";
    private final Set<Runnable> dailyListeners=new HashSet<>();
    private MovieWallpaperCache movieCache;
    private final Map<String,Long> dailyRetry=new HashMap<>();
    static synchronized WallpaperStore get(Context context) {
        if(instance==null) instance=new WallpaperStore(context.getApplicationContext());
        return instance;
    }
    private WallpaperStore(Context context) {
        this.context=context.getApplicationContext();
        screensavers=ScreensaverStore.get(context);
        directory=new File(context.getFilesDir(),"wallpapers");
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Wallpaper storage unavailable");
        catalogFile=new AtomicFile(new File(directory,"catalog.json"));
        if(catalogFile.getBaseFile().exists() || new File(directory,"catalog.json.bak").exists()) {
            try { catalog=WallpaperCatalog.read(new JSONObject(new String(catalogFile.readFully(),Charsets.UTF_8))); }
            catch(IOException|JSONException e) { Log.w("QuietTV","Wallpaper catalog could not be read");DiagnosticLog.error("wallpaper.catalog.failed",e); catalog=new WallpaperCatalog(); }
        } else {
            catalog=WallpaperCatalog.freshInstall();
            try { persist(catalog); } catch(IOException e) { Log.w("QuietTV","Wallpaper migration will retry on next launch"); }
        }
        // A missing upload must never leave the home screen pointing at an absent file.
        for(Iterator<WallpaperCatalog.Entry> it=catalog.custom.iterator();it.hasNext();) if(!file(it.next().id).isFile()) it.remove();
        catalog.normalize();
        try{screensavers.initialize(catalog);}catch(IOException e){DiagnosticLog.error("screensaver.load.failed",e);}
    }
    synchronized WallpaperCatalog snapshot() { return catalog.copy(); }
    // Rendering reads scalar state without serializing the complete media library.
    synchronized String currentId() { return catalog.current; }
    synchronized boolean weatherEnabled() { return catalog.weather; }
    synchronized boolean weatherDayNight() { return catalog.weatherDayNight; }
    synchronized WallpaperCatalog.Entry entry(String id) { return catalog.find(id); }
    synchronized WallpaperCatalog backupSnapshot()throws IOException{
        try{return WallpaperCatalog.read(new JSONObject(new String(catalogFile.readFully(),Charsets.UTF_8)));}catch(JSONException failure){throw new IOException("壁纸清单无法读取，自动备份已暂停",failure);}
    }
    synchronized void restoreBackup(JSONObject values)throws IOException{
        try{WallpaperCatalog next=WallpaperCatalog.read(values);next.dailyImages.putAll(catalog.dailyImages);persist(next);manualVersion++;DesktopBackups.changed();}catch(JSONException failure){throw new IOException("壁纸清单无法恢复",failure);}
    }
    synchronized long manualVersion(){return manualVersion;}
    synchronized String dailyPending(){return dailyPending;}
    synchronized void observeDaily(Runnable listener){dailyListeners.add(listener);}
    synchronized void forgetDaily(Runnable listener){dailyListeners.remove(listener);}
    private void dailyChanged(){for(Runnable listener:dailyListeners)listener.run();}
    synchronized long beginDaily(String source)throws IOException{
        if(!WallpaperCatalog.dailyProvider(source))throw new IOException("壁纸来源无效");
        if(!dailyPending.isEmpty())throw new IOException("正在获取壁纸，请稍候");
        dailyPending=source;++dailyOperation;dailyChanged();return dailyOperation;
    }
    synchronized boolean dailyActive(long operation){return operation==dailyOperation&&!dailyPending.isEmpty();}
    synchronized void finishDaily(long operation){if(operation==dailyOperation&&!dailyPending.isEmpty()){dailyPending="";dailyChanged();}}
    synchronized long dailyRetry(String source){Long value=dailyRetry.get(source);return value==null?0:value;}
    synchronized void dailyRetry(String source,long value){if(value==0)dailyRetry.remove(source);else dailyRetry.put(source,value);}
    synchronized long movieNextCheck(String day){return movieNextCheck(day,context.getSharedPreferences("daily-wallpaper-updates",0));}
    static long movieNextCheck(String day,android.content.SharedPreferences checks){
        if(!day.equals(checks.getString("movie-day","")))return 0;
        return checks.getLong("movie-checked",0)+DailyWallpaperController.AUTO_RETRY_MS;
    }
    synchronized void movieChecked(String day,long now){
        context.getSharedPreferences("daily-wallpaper-updates",0).edit().putString("movie-day",day).putLong("movie-checked",now).apply();
    }
    synchronized MovieWallpaperCache movieCache(byte[] key){if(movieCache==null)movieCache=new MovieWallpaperCache(new File(directory,"movie-cache"),key);return movieCache;}
    synchronized boolean moviePrefetched(String day){return day.equals(context.getSharedPreferences("daily-wallpaper-updates",0).getString("movie-prefetched-day",""));}
    synchronized void moviePrefetchedDay(String day){context.getSharedPreferences("daily-wallpaper-updates",0).edit().putString("movie-prefetched-day",day).apply();}
    synchronized boolean commitDailyIfCurrent(WallpaperCatalog.DailyImage image,File prepared,boolean apply,long expected,long operation)throws IOException{
        if(manualVersion!=expected||!dailyActive(operation))return false;
        commitDaily(image,prepared,apply);return true;
    }
    File dailyDirectory() throws IOException {
        File folder=new File(directory,"daily");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Daily wallpaper storage unavailable");return folder;
    }
    File dailyFile(String source,int slot) {
        if(!WallpaperCatalog.dailySource(source)||(slot!=0&&slot!=1))throw new IllegalArgumentException("Invalid daily wallpaper slot");
        return new File(new File(directory,"daily"),source+"-"+slot+".jpg");
    }
    boolean cached(WallpaperCatalog.DailyImage image) { return image!=null&&dailyFile(image.source,image.slot).isFile()&&dailyFile(image.source,image.slot).length()>0; }
    synchronized String imageKey(String id) {
        if(!WallpaperCatalog.DAILY.equals(id)){
            WallpaperCatalog.Entry entry=catalog.find(id);
            if(entry!=null&&entry.video()&&entry.custom()){File poster=poster(id);return id+":"+poster.length()+":"+poster.lastModified();}
            return id;
        }
        WallpaperCatalog.DailyImage image=catalog.dailyImages.get(catalog.dailySource);
        return cached(image)?id+":"+image.source+":"+image.key+":"+image.slot:id+":missing";
    }
    synchronized MovieWallpaperFeed displayedMovie(String shownKey){
        // The controller checks its selected mode; a preview may differ from the saved desktop.
        if(!WallpaperCatalog.MOVIE.equals(catalog.dailySource))return null;
        WallpaperCatalog.DailyImage image=catalog.dailyImages.get(catalog.dailySource);
        return image!=null&&(WallpaperCatalog.DAILY+":"+image.source+":"+image.key+":"+image.slot).equals(shownKey)?image.movie:null;
    }
    synchronized void commitDaily(WallpaperCatalog.DailyImage image,File prepared,boolean apply) throws IOException {
        if(!WallpaperCatalog.dailyProvider(image.source))throw new IOException("Unknown wallpaper source");
        WallpaperCatalog next=catalog.copy();
        WallpaperCatalog.DailyImage previous=catalog.dailyImages.get(image.source);
        if(WallpaperCatalog.MOVIE.equals(image.source)&&(image.movie==null||!image.key.equals(image.movie.hash)||previous!=null&&previous.movie!=null&&(image.movie.revision<previous.movie.revision||image.movie.revision==previous.movie.revision&&!image.movie.sameContent(previous.movie))))throw new IOException("Cinema cache revision changed");
        if(prepared!=null){
            if(prepared.length()==0||prepared.length()>DailyWallpaperClient.MAX_IMAGE_BYTES)throw new IOException("Invalid daily wallpaper size");
            WallpaperCatalog.DailyImage old=catalog.dailyImages.get(image.source);image.slot=old==null?0:1-old.slot;
            if(!prepared.renameTo(dailyFile(image.source,image.slot)))throw new IOException("Unable to save daily wallpaper");
        }else{
            WallpaperCatalog.DailyImage old=catalog.dailyImages.get(image.source);
            if(!cached(image)||old==null||!old.key.equals(image.key)||old.slot!=image.slot)throw new IOException("Daily wallpaper cache changed");
        }
        next.updateDaily(image,apply);
        persist(next);if(apply){manualVersion++;DesktopBackups.changed();}
    }
    synchronized File file(String id) {
        WallpaperCatalog.Entry entry=catalog.find(id);return mediaFile(id,entry!=null&&entry.video());
    }
    File poster(String id){mediaFile(id,true);return new File(directory,id+"-poster.jpg");}
    private File mediaFile(String id,boolean video){
        if(!id.matches("user-[a-f0-9-]{36}")) throw new IllegalArgumentException("Invalid wallpaper ID");
        // Keep historical private paths; image decoding detects the original JPG/PNG/WebP content.
        return new File(directory,id+(video?".mp4":".jpg"));
    }
    File preparedDestination(String id,boolean video){return mediaFile(id,video);}
    private void persist(WallpaperCatalog next) throws IOException {
        FileOutputStream stream=null;
        try { byte[] bytes=next.json().toString().getBytes(Charsets.UTF_8);stream=catalogFile.startWrite();stream.write(bytes);stream.getFD().sync();catalogFile.finishWrite(stream);stream=null;if(!java.util.Arrays.equals(bytes,catalogFile.readFully()))throw new IOException("Wallpaper write was not confirmed");catalog=next;DiagnosticLog.info("wallpaper.saved","current="+next.current+" rotating="+next.rotating); }
        catch(JSONException|IOException e) { if(stream!=null) catalogFile.failWrite(stream); DiagnosticLog.error("wallpaper.save.failed",e);throw new IOException("Unable to save wallpaper settings",e); }
    }
    synchronized boolean rename(String id,String originalName,String value,PhoneSettingsServer.Active active)throws IOException {
        String name=PhoneSettingsMediaName.read(value);WallpaperCatalog.Entry entry=catalog.find(id);
        if(entry==null||!entry.custom())throw new PhoneSettingsServer.Rejected("素材已移除或不支持改名，请重新选择");
        if(!entry.name.equals(originalName))throw new PhoneSettingsServer.Conflict();
        if(!active.valid())throw new PhoneSettingsServer.Ended();if(name.equals(entry.name))return false;
        WallpaperCatalog next=catalog.copy();next.rename(id,name);if(!active.valid())throw new PhoneSettingsServer.Ended();
        persist(next);DesktopBackups.changed();return true;
    }
    synchronized void select(String id) throws IOException { WallpaperCatalog next=catalog.copy();next.selectFixed(id);persist(next);manualVersion++;DesktopBackups.changed(); }
    synchronized void removeWeather()throws IOException{
        if(!catalog.weather)return;String fallback=catalog.current;WallpaperCatalog.Entry entry=catalog.find(fallback);
        if(entry==null||entry.video()||WallpaperCatalog.DAILY.equals(fallback)||entry.custom()&&(!file(fallback).isFile()||file(fallback).length()==0||!file(fallback).canRead()))fallback=WallpaperCatalog.DEFAULT;
        WallpaperCatalog next=catalog.copy();next.selectFixed(fallback);persist(next);manualVersion++;DesktopBackups.changed();
    }
    synchronized void background(String mode,String id,int minutes,boolean dayNight)throws IOException{
        if("weather".equals(mode)&&!home.quiet.components.ComponentRepository.get(context).isReady("weather"))throw new IOException("需要先下载天气资源才能使用");
        WallpaperCatalog next=catalog.copy();WallpaperCatalog.Entry item=next.find(id);if(item==null)throw new IOException("背景素材已不可用，请重新选择");
        if(item.custom()){File media=file(id);if(!media.isFile()||media.length()==0||!media.canRead())throw new IOException("背景素材无法读取，请重新选择");}
        try{if("rotation".equals(mode)){next.rotation(item.video(),minutes,id);}
            else if("daily".equals(mode))next.select(WallpaperCatalog.DAILY);
            else if("weather".equals(mode)){String fallback=item.video()?next.staticBeforeVideo:id;if(WallpaperCatalog.DAILY.equals(fallback))fallback=next.dailyFallback();next.select(fallback);next.weather=true;next.weatherDayNight=dayNight;next.sound=false;}
            else if("fixed".equals(mode)){if(WallpaperCatalog.DAILY.equals(id))throw new IOException("请选择固定壁纸");next.selectFixed(id);}else throw new IOException("背景方式无效");
        }catch(IllegalArgumentException error){throw new IOException(error.getMessage(),error);}persist(next);manualVersion++;DesktopBackups.changed();
    }
    synchronized boolean failedVideo(String id,Set<String> failed)throws IOException{
        WallpaperCatalog next=catalog.copy();String replacement=next.rotating?next.nextPlayableVideo(id,failed):null;
        if(replacement==null)next.stopVideo();else{next.current=replacement;next.lastVideo=replacement;}persist(next);return replacement!=null;
    }
    synchronized void stopVideo() throws IOException {WallpaperCatalog next=catalog.copy();next.stopVideo();persist(next);}
    synchronized void rotation(boolean video,int minutes,String start) throws IOException { WallpaperCatalog next=catalog.copy();next.rotation(video,minutes,start);persist(next);DesktopBackups.changed(); }
    synchronized void next() throws IOException { WallpaperCatalog next=catalog.copy();next.next();persist(next); }
    synchronized void remove(String id) throws IOException {
        WallpaperCatalog.Entry entry=catalog.find(id); if(entry==null||!entry.custom())return;
        WallpaperCatalog old=catalog.copy(),next=catalog.copy();next.remove(id);
        ScreensaverSettings oldSaver=screensavers.snapshot(old);
        // Move the reference before removing the media. Even an interrupted deletion stays valid.
        screensavers.reconcile(next);
        try{
            persist(next);
            if(entry.custom()){File deleted=mediaFile(id,entry.video());if(deleted.exists()&&!DesktopBackups.retains(deleted)&&!deleted.delete())throw new IOException("Unable to delete wallpaper file");}
        }catch(IOException failure){
            try{persist(old);screensavers.save(oldSaver);}catch(IOException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
        if(!entry.custom())return;
        if(entry.video()&&poster(id).exists()&&!DesktopBackups.retains(poster(id))&&!poster(id).delete())Log.w("QuietTV","Removed video poster retained");DesktopBackups.changed();
    }
    synchronized void checkCapacity() throws IOException {
        long available=directory.getUsableSpace();StorageEvidence.record("internal_wallpaper","image_admission",-1,available,30L*1024*1024,-1,-1,-1);
        if(available<30L*1024*1024) throw new IOException("电视存储空间不足 / Not enough TV storage");
    }
    synchronized WallpaperCatalog.Entry add(File ready,String filename,int width,int height) throws IOException {
        return add(ready,filename,width,height,"unknown","unknown");
    }
    synchronized WallpaperCatalog.Entry add(File ready,String filename,int width,int height,String fingerprint,String transfer) throws IOException {
        checkCapacity();
        String id="user-"+UUID.randomUUID();
        String name=filename.replaceAll("[\\p{Cntrl}/\\\\]"," ").replaceFirst("(?i)\\.(jpg|jpeg|png|webp)$","").trim();
        if(name.isEmpty()) name="我的壁纸 / My wallpaper";
        if(name.length()>48) name=name.substring(0,48);
        WallpaperCatalog.Entry entry=new WallpaperCatalog.Entry(id,name,width,height,ready.length());
        entry.diagnosticFingerprint=fingerprint;entry.diagnosticTransfer=transfer;
        File destination=file(id);
        if(!ready.renameTo(destination)) throw new IOException("Unable to save wallpaper image");
        WallpaperCatalog next=catalog.copy();next.add(entry);
        try { persist(next); } catch(IOException e) { if(!destination.delete()) Log.w("QuietTV","Unindexed wallpaper retained after failed save"); throw e; }
        DesktopBackups.changed();return entry;
    }
    long videoCapacity(){long available=directory.getUsableSpace(),maximum=Math.max(0,available-32L*1024*1024);StorageEvidence.record("internal_wallpaper","video_admission",-1,available,32L*1024*1024,-1,-1,maximum);return maximum;}
    synchronized void checkVideoCapacity(long incoming) throws IOException {
        long capacity=videoCapacity();DiagnosticLog.info("storage.decision","storage=internal_wallpaper step=video_admission incomingBytes="+incoming+" maximumBytes="+capacity+" accepted="+(incoming<=capacity));
        if(incoming>capacity)throw VideoDiagnostics.failure("capacity","storage_space_low","电视存储空间不足 / Not enough TV storage");
    }
    boolean saveVideoPoster(String id,Bitmap image)throws IOException{
        File destination=poster(id);
        synchronized(this){WallpaperCatalog.Entry entry=catalog.find(id);if(entry==null||!entry.video()||destination.isFile())return false;}
        File pending=videoIncoming();
        try{
            try(FileOutputStream stream=new FileOutputStream(pending)){
                if(!image.compress(Bitmap.CompressFormat.JPEG,85,stream))throw new IOException("Poster encoding failed");stream.getFD().sync();
            }
            synchronized(this){
                // Deletion and final publication share this lock; encoding never holds it.
                WallpaperCatalog.Entry entry=catalog.find(id);if(entry==null||!entry.video()||destination.isFile())return false;
                if(!pending.renameTo(destination))throw new IOException("Unable to save video poster");return true;
            }
        }finally{if(pending.isFile()&&!pending.delete())Log.w("QuietTV","Video poster temporary file retained");}
    }
    synchronized WallpaperCatalog.Entry addVideo(File ready,String filename,WallpaperVideo.Info info,WallpaperUploadServer.Active active) throws IOException {
        return addVideo(ready,filename,info,active,"unknown","unknown");
    }
    synchronized WallpaperCatalog.Entry addVideo(File ready,String filename,WallpaperVideo.Info info,WallpaperUploadServer.Active active,String fingerprint,String transfer) throws IOException {
        long bytes=ready.length();
        if(!active.valid())throw VideoDiagnostics.failure("save","session_ended","上传会话已结束 / Session ended");
        String id="user-"+UUID.randomUUID(),name=filename.replaceAll("[\\p{Cntrl}/\\\\]"," ").replaceFirst("(?i)\\.(mp4|mov|mkv|webm|m4v|3gp|3g2|ts|m2ts|mts|mpg|mpeg|vob|flv)$", "").trim();
        if(name.isEmpty())name="动态壁纸 / Video wallpaper";if(name.length()>48)name=name.substring(0,48);
        WallpaperCatalog.Entry entry=new WallpaperCatalog.Entry(id,name,info.width,info.height,bytes,"video",info.mime,info.durationMs,info.fps,info.rotation,info.pixelRatio);
        entry.diagnosticFingerprint=fingerprint;entry.diagnosticTransfer=transfer;
        File destination=mediaFile(id,true);if(!ready.renameTo(destination))throw VideoDiagnostics.failure("save","video_rename_failed","无法保存视频 / Unable to save video");
        WallpaperCatalog next=catalog.copy();next.add(entry);
        // A failed commit leaves this task's unindexed files, never removes an existing wallpaper.
        if(!active.valid())throw VideoDiagnostics.failure("save","session_ended","上传会话已结束 / Session ended");
        persist(next);DesktopBackups.changed();return entry;
    }
    synchronized WallpaperCatalog.Entry commitPrepared(String id,File ready,String filename,int width,int height,WallpaperVideo.Info video,WallpaperUploadServer.Active active)throws IOException {
        return commitPrepared(id,ready,filename,width,height,video,active,"unknown","unknown");
    }
    synchronized WallpaperCatalog.Entry commitPrepared(String id,File ready,String filename,int width,int height,WallpaperVideo.Info video,WallpaperUploadServer.Active active,String fingerprint,String transfer)throws IOException {
        if(!id.matches("user-[a-f0-9-]{36}"))throw new IOException("Invalid import identity");
        WallpaperCatalog.Entry saved=catalog.find(id);if(saved!=null)return saved;
        if(!active.valid())throw new IOException("导入已取消 / Import cancelled");
        boolean dynamic=video!=null;File destination=mediaFile(id,dynamic);if(!ready.isFile()||ready.length()==0)throw new IOException("素材草稿无法读取 / Media draft unavailable");
        if(destination.exists()&&!ready.equals(destination))throw new IOException("导入文件已存在，原文件已保留 / Existing file retained");
        String name=filename.replaceAll("[\\p{Cntrl}/\\\\]"," ").replaceFirst(dynamic?"(?i)\\.(mp4|mov|mkv|webm|m4v|3gp|3g2|ts|m2ts|mts|mpg|mpeg|vob|flv)$":"(?i)\\.(jpg|jpeg|png|webp)$","").trim();if(name.isEmpty())name=dynamic?"动态壁纸 / Video wallpaper":"我的壁纸 / My wallpaper";if(name.length()>48)name=name.substring(0,48);
        WallpaperCatalog.Entry entry=dynamic?new WallpaperCatalog.Entry(id,name,width,height,ready.length(),"video",video.mime,video.durationMs,video.fps,video.rotation,video.pixelRatio):new WallpaperCatalog.Entry(id,name,width,height,ready.length());
        entry.diagnosticFingerprint=fingerprint;entry.diagnosticTransfer=transfer;
        boolean moved=!ready.equals(destination);if(moved&&!ready.renameTo(destination))throw new IOException("素材保存失败 / Unable to save media");
        try{if(!active.valid())throw new IOException("导入已取消 / Import cancelled");WallpaperCatalog next=catalog.copy();next.add(entry);persist(next);DesktopBackups.changed();return entry;}
        catch(IOException failure){if(moved&&destination.isFile()&&!destination.renameTo(ready))DiagnosticLog.info("wallpaper.import.draft_retained",id);throw failure;}
    }
    File videoIncoming(){return new File(directory,"upload-"+UUID.randomUUID()+".tmp");}
    synchronized void sound(boolean enabled)throws IOException{WallpaperCatalog next=catalog.copy();next.sound=enabled;persist(next);DesktopBackups.changed();}
    File incoming() { return new File(directory,"incoming.tmp"); }
    File prepared() { return new File(directory,"prepared.tmp"); }
}
