package home.quiet.tv;

import android.os.Build;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Two fixed providers; metadata and final image requests have independent bounds. */
final class DailyWallpaperClient {
    static final long MAX_IMAGE_BYTES=8L*1024*1024;
    static final String BING_URL="https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=zh-CN";
    static final String BT_URL="https://api.btstu.cn/sjbz/api.php?lx=fengjing&method=pc&format=json";
    private volatile HttpsURLConnection connection;
    private final Context context;
    private SSLSocketFactory btTls;
    DailyWallpaperClient(Context context){this.context=context.getApplicationContext();}
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
        boolean reuses(WallpaperCatalog.DailyImage image){
            if(image==null||!WallpaperCatalog.BING.equals(source)||!source.equals(image.source))return false;
            if(url.equals(image.url))return true;
            return upstream.matches("[0-9]{8}([0-9]{4})?")&&image.upstream.matches("[0-9]{8}([0-9]{4})?")&&upstream.compareTo(image.upstream)<0;
        }
    }
    Candidate latest(String source) throws IOException {
        if(!WallpaperCatalog.dailySource(source))throw new IOException("Unknown wallpaper source");
        try(ByteArrayOutputStream output=new ByteArrayOutputStream()){
            transfer(WallpaperCatalog.BT.equals(source)?BT_URL:BING_URL,output,256*1024,"application/json");
            return parse(source,new JSONObject(output.toString("UTF-8")));
        }catch(JSONException e){throw new IOException("Invalid wallpaper response",e);}
    }
    static Candidate parse(String source,JSONObject json) throws IOException,JSONException {
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
        }else if(WallpaperCatalog.BT.equals(source)){
            if(!"200".equals(json.optString("code")))throw new IOException("Wallpaper source unavailable");
            result.url=json.getString("imgurl");result.title="";result.copyright="";result.link="https://api.btstu.cn/doc/sjbz.php";result.upstream="";
        }else throw new IOException("Unknown wallpaper source");
        https(result.url);
        if(!result.link.isEmpty())try{https(result.link);}catch(IOException e){result.link="";}
        return result;
    }
    String download(Candidate candidate,File file) throws IOException {
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(FileOutputStream output=new FileOutputStream(file);DigestOutputStream checked=new DigestOutputStream(output,digest)){
                transfer(candidate.url,checked,MAX_IMAGE_BYTES,"image/*");output.getFD().sync();
            }
            StringBuilder key=new StringBuilder();for(byte value:digest.digest())key.append(String.format(java.util.Locale.US,"%02x",value&255));return key.toString();
        }catch(NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private static URL https(String value) throws IOException {
        URL url=new URL(value);if(!"https".equalsIgnoreCase(url.getProtocol())||url.getHost().isEmpty()||url.getUserInfo()!=null)throw new IOException("Wallpaper requires HTTPS");return url;
    }
    private void transfer(String address,OutputStream output,long limit,String accept) throws IOException {
        URL url=https(address);
        for(int redirects=0;redirects<=3;redirects++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            HttpsURLConnection request=(HttpsURLConnection)url.openConnection();connection=request;
            try{
                if(Build.VERSION.SDK_INT<=25&&("api.btstu.cn".equals(url.getHost())||"img.btstu.cn".equals(url.getHost())))request.setSSLSocketFactory(btTls());
                else if(Build.VERSION.SDK_INT<21)request.setSSLSocketFactory(new Tls12SocketFactory());
                request.setConnectTimeout(8000);request.setReadTimeout(10000);request.setInstanceFollowRedirects(false);
                request.setRequestProperty("Accept",accept);request.setRequestProperty("User-Agent","QuietTV/"+BuildConfig.VERSION_NAME);
                int status=request.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=request.getHeaderField("Location");if(location==null)throw new IOException("Missing wallpaper redirect");url=https(new URL(url,location).toString());continue;
                }
                if(status==429){
                    long retry=0;try{retry=Math.min(86400,Math.max(0,Long.parseLong(request.getHeaderField("Retry-After"))))*1000L;}catch(NumberFormatException ignored){}
                    if(retry==0)retry=Math.max(0,request.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis());throw new Limited(retry);
                }
                if(status!=200)throw new HttpFailure(status);
                if(request.getContentLength()>limit)throw new IOException("Wallpaper response too large");
                try(InputStream input=request.getInputStream()){
                    byte[] buffer=new byte[16384];long total=0;int count;
                    while((count=input.read(buffer))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                        total+=count;if(total>limit)throw new IOException("Wallpaper response too large");output.write(buffer,0,count);
                    }
                    if(total==0)throw new IOException("Empty wallpaper response");
                }
                return;
            }finally{request.disconnect();if(connection==request)connection=null;}
        }
        throw new IOException("Too many wallpaper redirects");
    }
    void cancel(){HttpsURLConnection active=connection;if(active!=null)active.disconnect();}
    /** Add the official ISRG root only to Btstu on old Android; retain platform trust and hostname checks. */
    private SSLSocketFactory btTls() throws IOException {
        if(btTls!=null)return btTls;
        try{
            TrustManagerFactory system=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());system.init((KeyStore)null);
            X509TrustManager platform=(X509TrustManager)system.getTrustManagers()[0];
            KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);
            int index=0;for(X509Certificate root:platform.getAcceptedIssuers())roots.setCertificateEntry("system-"+index++,root);
            try(InputStream input=context.getAssets().open("certificates/isrg-root-x1.pem")){roots.setCertificateEntry("isrg-root-x1",CertificateFactory.getInstance("X.509").generateCertificate(input));}
            TrustManagerFactory added=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());added.init(roots);
            SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,added.getTrustManagers(),null);btTls=Build.VERSION.SDK_INT<21?new Tls12SocketFactory(tls.getSocketFactory()):tls.getSocketFactory();return btTls;
        }catch(GeneralSecurityException e){throw new IOException("Wallpaper TLS unavailable",e);}
    }
}
