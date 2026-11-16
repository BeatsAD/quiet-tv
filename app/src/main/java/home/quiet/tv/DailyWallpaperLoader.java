package home.quiet.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import java.io.*;

/** Shared TV/phone preparation. The caller alone decides whether the result may commit. */
final class DailyWallpaperLoader {
    interface Check {void run()throws IOException;}
    static final class Result implements Closeable {
        final WallpaperCatalog.DailyImage image;
        final File prepared;
        final boolean unchanged,offline;
        Result(WallpaperCatalog.DailyImage image,File prepared,boolean unchanged,boolean offline){this.image=image;this.prepared=prepared;this.unchanged=unchanged;this.offline=offline;}
        public void close(){if(prepared!=null&&prepared.isFile())prepared.delete();}
    }
    private final Context context;
    private final WallpaperStore store;
    private final DailyWallpaperClient client;
    DailyWallpaperLoader(Context context,WallpaperStore store,DailyWallpaperClient client){this.context=context;this.store=store;this.client=client;}
    static String source(WallpaperCatalog state){return WallpaperCatalog.dailyProvider(state.dailySource)?state.dailySource:WallpaperCatalog.BING;}
    static boolean reuse(boolean cached,boolean online,boolean force,String day,String cachedDay){return cached&&(!online||!force&&day.equals(cachedDay));}
    static boolean reuse(String source,boolean cached,boolean online,boolean force,String day,WallpaperCatalog.DailyImage old,long now){
        if(WallpaperCatalog.MOVIE.equals(source))return cached&&(!online||!force&&current(source,day,old));
        return reuse(cached,online,force,day,old==null?"":old.day);
    }
    static boolean current(String source,String day,WallpaperCatalog.DailyImage image){return image!=null&&(WallpaperCatalog.MOVIE.equals(source)?image.movie!=null&&day.equals(image.movie.contentDate):day.equals(image.day));}
    boolean online(){ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();return network!=null&&network.isConnected();}
    Result select(String source,Check check)throws IOException{return select(source,check,DailyWallpaperController.today(source));}
    Result select(String source,Check check,String day)throws IOException{return load(source,false,true,check,day);}
    Result load(String source,boolean force,Check check)throws IOException{return load(source,force,check,DailyWallpaperController.today(source));}
    Result load(String source,boolean force,Check check,String day)throws IOException{return load(source,force,false,check,day);}
    private Result load(String source,boolean force,boolean cacheFirst,Check check,String day)throws IOException{
        check.run();WallpaperCatalog.DailyImage old=store.snapshot().dailyImages.get(source);boolean cached=store.cached(old);
        if(cached)try{validate(store.dailyFile(source,old.slot));}catch(IOException invalid){cached=false;}
        boolean online=online();boolean movie=WallpaperCatalog.MOVIE.equals(source);
        MovieWallpaperCache.Entry saved=movie?store.movieCache(client.movieKey()).read(day):null;
        MovieWallpaperCache.Entry local=saved!=null&&(!force||!online||SystemClock.elapsedRealtime()<store.dailyRetry(source))?saved:null;
        if(local==null&&(cached&&cacheFirst||reuse(source,cached,online,force,day,old,System.currentTimeMillis())))return new Result(copy(old),null,true,!online);
        if(local==null&&!online)throw new IOException("网络不可用，此来源暂无缓存");
        if(local==null&&SystemClock.elapsedRealtime()<store.dailyRetry(source))throw new IOException("壁纸来源暂不可用，请稍后重试");
        File download=null,prepared=null;
        try{
            DailyWallpaperClient.Candidate candidate;
            if(local!=null)candidate=DailyWallpaperClient.movie(local.movie,old);
            else if(movie){
                try{candidate=client.movieOn(day,old);}catch(InterruptedIOException cancelled){throw cancelled;}
                catch(IOException datedFailure){if(saved!=null){local=saved;candidate=DailyWallpaperClient.movie(saved.movie,old);}else candidate=client.latest(source,old);}
                if(saved!=null&&saved.movie.revision>candidate.movie.revision){local=saved;candidate=DailyWallpaperClient.movie(saved.movie,old);}
                if(saved!=null&&saved.movie.revision==candidate.movie.revision&&!saved.movie.sameContent(candidate.movie))throw new IOException("Cinema revision conflicts with prepared content");
                if(candidate.movie.contentDate.compareTo(day)>0)throw new IOException("Future cinema cannot be displayed early");
            }else candidate=client.latest(source,cached?old:null);
            check.run();
            WallpaperCatalog.DailyImage image;String key;
            boolean duplicate=cached&&candidate.reuses(old);
            if(duplicate){image=copy(old);key=old.key;}
            else{
                File original;
                if(local!=null){original=local.image;key=candidate.expectedHash;}
                else{
                    download=File.createTempFile("daily-download-",".tmp",store.dailyDirectory());original=download;
                    key=client.download(candidate,download);check.run();
                    if(movie){movieDimensions(download);store.movieCache(client.movieKey()).save(candidate.movie,download,check);}
                }
                duplicate=cached&&old.key.equals(key);
                if(duplicate)image=copy(old);
                else{
                    if(store.dailyDirectory().getUsableSpace()<24L*1024*1024)throw new IOException("壁纸存储空间不足");
                    prepared=File.createTempFile("daily-ready-",".tmp",store.dailyDirectory());
                    if(movie)movieDimensions(original);
                    int[] size=WallpaperImages.prepare(original,prepared,Math.min(1920,WallpaperImages.importLimit(context)));check.run();
                    if(size[0]<320||size[1]<180||size[0]<size[1])throw new IOException("壁纸图片尺寸无效");
                    if(prepared.length()>DailyWallpaperClient.MAX_IMAGE_BYTES)throw new IOException("壁纸图片过大");
                    image=new WallpaperCatalog.DailyImage();image.width=size[0];image.height=size[1];
                }
            }
            // Bing may return older metadata. Preserve the newer cache and its attribution.
            if(!duplicate||!WallpaperCatalog.BING.equals(source)||candidate.url.equals(old.url)){
                image.source=source;image.key=key;image.url=candidate.url;image.title=candidate.title;
                image.copyright=candidate.copyright;image.link=candidate.link;image.upstream=candidate.upstream;image.movie=candidate.movie;
            }
            image.day=day;image.checked=System.currentTimeMillis();check.run();
            Result result=new Result(image,prepared,duplicate,!online);prepared=null;return result;
        }catch(DailyWallpaperClient.Limited error){
            store.dailyRetry(source,SystemClock.elapsedRealtime()+Math.max(DailyWallpaperController.COOLDOWN_MS,error.retryMs));throw error;
        }catch(InterruptedIOException cancelled){throw cancelled;}
        catch(IOException error){store.dailyRetry(source,SystemClock.elapsedRealtime()+DailyWallpaperController.COOLDOWN_MS);throw error;}
        finally{
            if(download!=null&&download.isFile())download.delete();
            if(prepared!=null&&prepared.isFile())prepared.delete();
        }
    }
    void prefetchMovie(String today,Check check)throws IOException{
        MovieWallpaperCache cache=store.movieCache(client.movieKey());
        try{for(int offset=1;offset<=MovieWallpaperCache.FUTURE_DAYS;offset++){
            check.run();String day=MovieWallpaperCache.date(today,offset);MovieWallpaperCache.Entry ready=cache.read(day);
            if(ready!=null){movieDimensions(ready.image);continue;}
            if(!online())throw new IOException("Network unavailable for cinema preparation");
            DailyWallpaperClient.Candidate candidate=client.movieOn(day,null);check.run();
            File download=File.createTempFile("movie-prefetch-",".tmp",store.dailyDirectory());
            try{
                client.download(candidate,download);check.run();movieDimensions(download);
                if(store.dailyDirectory().getUsableSpace()<24L*1024*1024)throw new IOException("Cinema cache storage unavailable");
                cache.save(candidate.movie,download,check);
            }finally{if(download.isFile())download.delete();}
        }
        check.run();
        }catch(DailyWallpaperClient.Limited limited){store.dailyRetry(WallpaperCatalog.MOVIE,SystemClock.elapsedRealtime()+Math.max(DailyWallpaperController.COOLDOWN_MS,limited.retryMs));throw limited;}
    }
    private static void movieDimensions(File file)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth!=1920||bounds.outHeight!=1080)throw new IOException("电影壁纸尺寸与清单不符");
    }
    private static WallpaperCatalog.DailyImage copy(WallpaperCatalog.DailyImage image)throws IOException{
        try{return WallpaperCatalog.DailyImage.read(image.json());}catch(org.json.JSONException error){throw new IOException(error);}
    }
    private static void validate(File file)throws IOException{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;Bitmap image=BitmapFactory.decodeFile(file.getPath(),options);
        if(image==null)throw new IOException("壁纸缓存无法读取");image.recycle();
    }
}
