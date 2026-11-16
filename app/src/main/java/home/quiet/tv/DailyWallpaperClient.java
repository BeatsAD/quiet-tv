package home.quiet.tv;

import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import javax.net.ssl.*;
import org.bouncycastle.util.encoders.Base64;

/** Bing metadata and final image requests have independent bounds. */
final class DailyWallpaperClient {
    static final long MAX_IMAGE_BYTES=8L*1024*1024;
    static final String BING_URL="https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";
    static final String MICROSOFT_URL="https://arc.msn.com/v3/Delivery/Placement?pid=209567&fmt=json&ua=WindowsShellClient%2F0&cdm=1&lo=80217&pl=zh-CN&lc=zh-CN&ctry=CN";
    private final NetworkRequest requests=new NetworkRequest();
    private final android.content.Context context;
    private SSLSocketFactory legacyTls,cinemaTls;
    private volatile int generation;
    DailyWallpaperClient(android.content.Context context){this.context=context;}
    static final class Unavailable extends IOException {
        Unavailable(){super("No recent Bing image is available as wallpaper");}
    }
    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status){super("Wallpaper service unavailable");this.status=status;}
    }
    static final class Limited extends IOException {
        final long retryMs;
        Limited(long retryMs){super("Wallpaper service rate limited");this.retryMs=retryMs;}
    }
    static final class Candidate {
        String source,url,title,copyright,link,upstream;
        long expectedBytes;
        String expectedHash="";
        MovieWallpaperFeed movie;
        boolean reuses(WallpaperCatalog.DailyImage image){
            if(image==null||!source.equals(image.source))return false;
            if(WallpaperCatalog.MOVIE.equals(source))return !expectedHash.isEmpty()&&expectedHash.equals(image.key);
            if(WallpaperCatalog.MICROSOFT.equals(source))return !expectedHash.isEmpty()&&expectedHash.equals(image.key);
            if(url.equals(image.url))return true;
            return upstream.matches("[0-9]{8}([0-9]{4})?")&&image.upstream.matches("[0-9]{8}([0-9]{4})?")&&upstream.compareTo(image.upstream)<0;
        }
    }
    Candidate latest(String source) throws IOException {
        return latest(source,null);
    }
    Candidate latest(String source,WallpaperCatalog.DailyImage previous) throws IOException {
        if(!WallpaperCatalog.dailyProvider(source))throw new IOException("Unknown wallpaper source");
        if(WallpaperCatalog.MOVIE.equals(source))return latestMovie(previous);
        try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
            java.text.SimpleDateFormat time=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US);time.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String url=WallpaperCatalog.MOVIE.equals(source)?BuildConfig.CINEMA_FEED_URL:WallpaperCatalog.MICROSOFT.equals(source)?MICROSOFT_URL+"&time="+URLEncoder.encode(time.format(new java.util.Date()),"UTF-8"):BING_URL;
            transfer(url,output,256*1024,"application/json",source);
            return parse(source,new JSONObject(output.toString("UTF-8")),previous);
        }catch(JSONException e){throw new IOException("Invalid wallpaper response",e);}
    }
    byte[] movieKey()throws IOException{
        try(InputStream input=context.getAssets().open("cinema/public-key.der");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);return output.toByteArray();
        }
    }
    Candidate movieOn(String day,WallpaperCatalog.DailyImage previous)throws IOException{return movieManifest(MovieWallpaperSources.dated(BuildConfig.CINEMA_FEED_URL,day),day,previous);}
    private Candidate latestMovie(WallpaperCatalog.DailyImage previous)throws IOException{return movieManifest(BuildConfig.CINEMA_FEED_URL,null,previous);}
    private Candidate movieManifest(String manifest,String day,WallpaperCatalog.DailyImage previous)throws IOException{
        int attempt=generation,verified=0;long deadline=now()+40000;Candidate best=null;IOException last=null;byte[] key=movieKey();
        for(String address:MovieWallpaperSources.addresses(manifest)){
            alive(attempt);if(now()>=deadline)break;
            try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
                String fresh=address+(address.contains("?")?"&":"?")+"quietCheck="+System.currentTimeMillis();
                transfer(fresh,output,256*1024,"application/json",WallpaperCatalog.MOVIE,deadline);alive(attempt);
                Candidate candidate=movie(MovieWallpaperFeed.verify(output.toByteArray(),key),previous);
                if(day!=null&&!day.equals(candidate.movie.contentDate))throw new IOException("Cinema response has a different content date");
                if(best!=null&&best.movie.revision==candidate.movie.revision&&!best.movie.sameContent(candidate.movie))throw new IOException("Cinema revision conflicts");
                if(best==null||candidate.movie.revision>best.movie.revision)best=candidate;
                DiagnosticLog.info("wallpaper.cinema.source_verified","host="+new URL(address).getHost()+" revision="+candidate.movie.revision);
                if(++verified>=2)break;
            }catch(IOException error){alive(attempt);last=error;movieFailed(address,"manifest",error);}
        }
        alive(attempt);if(best!=null)return best;throw new IOException("All cinema manifest sources failed",last);
    }
    private static long now(){return System.nanoTime()/1000000;}
    private void alive(int attempt)throws InterruptedIOException{if(attempt!=generation||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Wallpaper cancelled");}
    private static void movieFailed(String address,String stage,IOException error){
        try{DiagnosticLog.info("wallpaper.cinema.source_failed","host="+new URL(address).getHost()+" stage="+stage+" error="+error.getClass().getSimpleName());}catch(MalformedURLException ignored){}
    }
    static Candidate parse(String source,JSONObject json) throws IOException,JSONException {
        return parse(source,json,null);
    }
    static Candidate parse(String source,JSONObject json,WallpaperCatalog.DailyImage previous) throws IOException,JSONException {
        if(WallpaperCatalog.MOVIE.equals(source))return movie(json,previous);
        if(WallpaperCatalog.MICROSOFT.equals(source))return microsoft(json,previous);
        Candidate result=new Candidate();result.source=source;
        if(WallpaperCatalog.BING.equals(source)){
            JSONArray images=json.getJSONArray("images");JSONObject image=null;
            // Bing returns newest first. Inspect metadata only; download just the selected image.
            for(int n=0;n<Math.min(8,images.length());n++){
                JSONObject item=images.getJSONObject(n);
                if(item.optBoolean("wp",false)){image=item;break;}
            }
            if(image==null)throw new Unavailable();
            result.url=new URL(new URL("https://cn.bing.com"),image.getString("url")).toString();
            result.title=image.optString("title");result.copyright=image.optString("copyright");result.link=image.optString("copyrightlink");result.upstream=image.optString("fullstartdate",image.optString("startdate"));
        }else throw new IOException("Unknown wallpaper source");
        https(result.url);
        if(!result.link.isEmpty())try{https(result.link);}catch(IOException e){result.link="";}
        return result;
    }
    private static Candidate movie(JSONObject json,WallpaperCatalog.DailyImage previous)throws IOException{
        return movie(MovieWallpaperFeed.read(json),previous);
    }
    static Candidate movie(MovieWallpaperFeed feed,WallpaperCatalog.DailyImage previous)throws IOException{
        if(previous!=null&&previous.movie!=null){
            if(feed.hash.equals(previous.movie.hash)&&feed.bytes!=previous.movie.bytes)throw new IOException("Cinema image size changed");
            if(feed.revision<previous.movie.revision)throw new IOException("Cinema revision moved backwards");
            if(feed.revision==previous.movie.revision&&!feed.sameContent(previous.movie))throw new IOException("Cinema revision was overwritten");
        }
        Candidate result=new Candidate();result.source=WallpaperCatalog.MOVIE;result.movie=feed;
        result.url=feed.imageUrl(BuildConfig.CINEMA_FEED_URL);result.title=feed.title;result.copyright="TMDB";result.link=feed.sourceUrl;result.upstream=feed.contentDate;
        result.expectedBytes=feed.bytes;result.expectedHash=feed.hash;return result;
    }
    private static Candidate microsoft(JSONObject json,WallpaperCatalog.DailyImage previous)throws IOException,JSONException{
        JSONArray items=json.getJSONObject("batchrsp").optJSONArray("items");Candidate repeated=null;
        if(items!=null)for(int n=0;n<Math.min(8,items.length());n++){
            try{
                JSONObject ad=new JSONObject(items.getJSONObject(n).getString("item")).getJSONObject("ad");
                JSONObject image=ad.getJSONObject("image_fullscreen_001_landscape");
                Candidate result=new Candidate();result.source=WallpaperCatalog.MICROSOFT;result.url=image.getString("u");https(result.url);
                byte[] hash=Base64.decode(image.getString("sha256"));if(hash.length!=32)continue;
                result.expectedHash=hex(hash);result.expectedBytes=image.getLong("fileSize");
                if(result.expectedBytes<1||result.expectedBytes>MAX_IMAGE_BYTES)continue;
                result.title=text(ad,"title_text");result.copyright=text(ad,"copyright_text");
                result.link=ad.optJSONObject("title_destination_url")==null?"":ad.getJSONObject("title_destination_url").optString("u");
                if(!result.link.isEmpty())try{https(result.link);}catch(IOException ignored){result.link="";}
                result.upstream=result.expectedHash;
                if(!result.reuses(previous))return result;repeated=result;
            }catch(JSONException|RuntimeException|IOException ignored){}
        }
        if(repeated!=null)return repeated;
        throw new IOException("微软壁纸暂无可用图片");
    }
    private static String text(JSONObject ad,String key){JSONObject value=ad.optJSONObject(key);return value==null?"":value.optString("tx");}
    private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(java.util.Locale.US,"%02x",value&255));return result.toString();}
    String download(Candidate candidate,File file) throws IOException {
        if(WallpaperCatalog.MOVIE.equals(candidate.source)){
            int attempt=generation;long deadline=now()+40000;IOException last=null;
            for(String address:MovieWallpaperSources.addresses(candidate.url)){
                alive(attempt);if(now()>=deadline)break;
                try{String hash=download(candidate,file,address,deadline);alive(attempt);return hash;}
                catch(IOException error){alive(attempt);last=error;movieFailed(address,"image",error);}
            }
            throw new IOException("All cinema image sources failed",last);
        }
        return download(candidate,file,candidate.url,0);
    }
    private String download(Candidate candidate,File file,String address,long deadline)throws IOException{
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(FileOutputStream output=new FileOutputStream(file);DigestOutputStream checked=new DigestOutputStream(output,digest)){
                transfer(address,checked,WallpaperCatalog.MOVIE.equals(candidate.source)?MovieWallpaperFeed.MAX_BYTES:MAX_IMAGE_BYTES,"image/*",candidate.source,deadline);output.getFD().sync();
            }
            String key=hex(digest.digest());verify(candidate,file.length(),key);return key;
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    static void verify(Candidate candidate,long bytes,String key)throws IOException{
        if(candidate.expectedBytes>0&&candidate.expectedBytes!=bytes||!candidate.expectedHash.isEmpty()&&!candidate.expectedHash.equals(key))throw new IOException("壁纸下载校验失败");
    }
    private static URL https(String value) throws IOException {
        URL url=new URL(value);if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty()||url.getUserInfo()!=null)throw new IOException("Wallpaper requires HTTPS");return url;
    }
    static boolean bundledTls(int sdk,String source){return sdk<21||WallpaperCatalog.MOVIE.equals(source)&&sdk<26;}
    private void transfer(String address,OutputStream output,long limit,String accept,String source) throws IOException {
        transfer(address,output,limit,accept,source,0);
    }
    private void transfer(String address,OutputStream output,long limit,String accept,String source,long deadline) throws IOException {
        URL url=https(address);boolean appTls=bundledTls(Build.VERSION.SDK_INT,source);
        boolean mirrored=WallpaperCatalog.MOVIE.equals(source)&&MovieWallpaperSources.mirrored(address);
        String diagnosticRequest=NetworkEvidence.request();
        for(int redirects=0;redirects<=3;redirects++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            if(deadline>0&&now()>=deadline)throw new SocketTimeoutException("Cinema request timed out");
            if(mirrored&&!MovieWallpaperSources.mirrored(url.toString()))throw new IOException("Cinema mirror redirected to origin");
            HttpsURLConnection request=(HttpsURLConnection)url.openConnection();NetworkRequest.Handle handle=requests.begin(request);
            NetworkEvidence evidence=new NetworkEvidence(diagnosticRequest,accept.startsWith("image")?"daily_image":"daily_manifest","daily_https");
            try{
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                if(appTls){
                    if(WallpaperCatalog.MOVIE.equals(source)){
                        if(cinemaTls==null)cinemaTls=UpdateTls.create(context);
                        request.setSSLSocketFactory(cinemaTls);
                    }else{
                        if(legacyTls==null){
                            try(InputStream root=context.getAssets().open("certificates/digicert-global-root-g2.pem")){legacyTls=WallpaperTls.socketFactory(root);}
                            try(SSLSocket socket=(SSLSocket)legacyTls.createSocket()){
                                DiagnosticLog.info("wallpaper.daily.tls","enabled="+java.util.Arrays.toString(socket.getEnabledProtocols())
                                    +" supported="+java.util.Arrays.toString(socket.getSupportedProtocols())+" ciphers="+socket.getEnabledCipherSuites().length);
                            }
                        }
                        request.setSSLSocketFactory(legacyTls);
                    }
                }
                int timeout=deadline>0?(int)Math.max(1,Math.min(8000,deadline-now())):8000;
                request.setConnectTimeout(timeout);request.setReadTimeout(deadline>0?timeout:10000);request.setInstanceFollowRedirects(false);
                if(WallpaperCatalog.MOVIE.equals(source)){request.setRequestProperty("Cache-Control","no-cache");request.setUseCaches(false);}
                request.setRequestProperty("Accept",accept);request.setRequestProperty("User-Agent","QuietTV/"+BuildConfig.VERSION_NAME);
                int status=request.getResponseCode();
                evidence.status(status);
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=request.getHeaderField("Location");if(location==null)throw new IOException("Missing wallpaper redirect");url=https(new URL(url,location).toString());evidence.redirects=1;evidence.finish("redirect",null);continue;
                }
                if(status==429){
                    long retry=0;try{retry=Math.min(86400,Math.max(0,Long.parseLong(request.getHeaderField("Retry-After"))))*1000L;}catch(NumberFormatException ignored){}
                    if(retry==0)retry=Math.max(0,request.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis());throw new Limited(retry);
                }
                if(status!=200)throw new HttpFailure(status);
                if(request.getContentLength()>limit)throw new IOException("Wallpaper response too large");
                try(InputStream input=request.getInputStream()){
                    evidence.phase("read");
                    byte[] buffer=new byte[16384];long total=0;int count;
                    while((count=input.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                        if(deadline>0&&now()>=deadline)throw new SocketTimeoutException("Cinema request timed out");
                        evidence.add(count);total+=count;if(total>limit)throw new IOException("Wallpaper response too large");output.write(buffer,0,count);
                    }
                    if(total==0)throw new IOException("Empty wallpaper response");
                }
                evidence.finish("body_received_unverified",null);return;
            }catch(IOException e){
                evidence.finish("failed",e);
                DiagnosticLog.info("wallpaper.daily.network_failed","host="+url.getHost()+" kind="+(e instanceof HttpFailure||e instanceof Limited?"http":WallpaperTls.failureCode(e))
                    +(e instanceof HttpFailure?" status="+((HttpFailure)e).status:e instanceof Limited?" status=429":"")
                    +" tls="+(appTls?"app":"system"));
                throw e;
            }finally{requests.finish(handle);}
        }
        throw new IOException("Too many wallpaper redirects");
    }
    void cancel(){++generation;requests.cancel();}
}
