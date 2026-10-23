package home.quiet.tv;

import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;

/** Direct, key-free UAPI access. Only weather and region fields are retained. */
final class WeatherClient {
    static final String SOURCE_URL="https://uapis.cn/docs/api-reference/get-misc-weather";
    private static volatile long limitedUntil;
    private volatile HttpsURLConnection connection;
    static final class ApiException extends IOException{
        final int status;final long retryAfterMs;
        ApiException(int status,long retryAfterMs){super("Weather service unavailable ("+status+")");this.status=status;this.retryAfterMs=retryAfterMs;}
    }
    static final class City{
        final String adcode,name,area;
        City(JSONObject item)throws JSONException{
            adcode=item.getString("adcode");name=item.getString("name");
            if(!adcode.matches("[0-9]{6}")||name.trim().isEmpty())throw new JSONException("Invalid region");
            String province=item.optString("province","");String city=item.optString("city","");
            if(province.equals(name))province="";if(city.equals(name)||city.equals(province))city="";
            area=province+(province.isEmpty()||city.isEmpty()?"":"  ")+city;
        }
    }
    WeatherData weather(String adcode,String source,boolean chinese)throws IOException,JSONException{
        JSONObject response=get("/misc/weather?lang="+(chinese?"zh":"en")+(adcode.isEmpty()?"":"&adcode="+encode(adcode)));
        return new WeatherData(response,System.currentTimeMillis(),source);
    }
    List<City> cities(String keywords,boolean chinese)throws IOException,JSONException{
        return regions("keywords="+encode(keywords)+"&country=CN&limit=10");
    }
    City coordinates(double latitude,double longitude)throws IOException,JSONException{
        List<City> result=regions("lat="+String.format(Locale.US,"%.2f",latitude)+"&lng="+String.format(Locale.US,"%.2f",longitude)+"&country=CN&limit=5");
        if(result.isEmpty())throw new ApiException(404,0);return result.get(0);
    }
    private List<City> regions(String query)throws IOException,JSONException{
        JSONArray items=get("/misc/district?"+query).getJSONArray("results");List<City> result=new ArrayList<>();
        for(int n=0;n<items.length();n++){
            JSONObject item=items.getJSONObject(n);String level=item.optString("level");
            if("CN".equals(item.optString("country_code"))&&("city".equals(level)||"district".equals(level)||"province".equals(level)))result.add(new City(item));
        }
        return result;
    }
    private static String encode(String value)throws UnsupportedEncodingException{return URLEncoder.encode(value,"UTF-8");}
    private JSONObject get(String path)throws IOException,JSONException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        long remaining=limitedUntil-android.os.SystemClock.elapsedRealtime();
        if(remaining>0)throw new ApiException(429,remaining);
        HttpsURLConnection request=(HttpsURLConnection)new URL("https://uapis.cn/api/v1"+path).openConnection();connection=request;
        try{
            if(Build.VERSION.SDK_INT<21)request.setSSLSocketFactory(new Tls12());
            request.setConnectTimeout(8000);request.setReadTimeout(10000);request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Accept","application/json");request.setRequestProperty("User-Agent","QuietTV/"+BuildConfig.VERSION_NAME);
            int status=request.getResponseCode();
            if(status!=200){
                long retry=0;try{retry=Math.min(86400,Math.max(0,Long.parseLong(request.getHeaderField("Retry-After"))))*1000L;}catch(NumberFormatException ignored){}
                if(retry==0)retry=Math.max(0,request.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis());
                if(status==429){retry=Math.max(WeatherData.REFRESH_MS,retry);limitedUntil=android.os.SystemClock.elapsedRealtime()+retry;}
                throw new ApiException(status,retry);
            }
            try(InputStream in=request.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+count>256*1024)throw new IOException("Weather response too large");out.write(buffer,0,count);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{request.disconnect();if(connection==request)connection=null;}
    }
    void cancel(){HttpsURLConnection active=connection;if(active!=null)active.disconnect();}
    /** Enable TLS 1.2 on Android 4.4 without changing trust or hostname verification. */
    private static final class Tls12 extends SSLSocketFactory{
        private final SSLSocketFactory delegate;
        Tls12()throws IOException{try{SSLContext context=SSLContext.getInstance("TLS");context.init(null,null,null);delegate=context.getSocketFactory();}catch(java.security.GeneralSecurityException e){throw new IOException(e);}}
        private java.net.Socket configure(java.net.Socket socket){if(socket instanceof SSLSocket)((SSLSocket)socket).setEnabledProtocols(new String[]{"TLSv1.2"});return socket;}
        @Override public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}
        @Override public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
        @Override public java.net.Socket createSocket()throws IOException{return configure(delegate.createSocket());}
        @Override public java.net.Socket createSocket(java.net.Socket s,String h,int p,boolean close)throws IOException{return configure(delegate.createSocket(s,h,p,close));}
        @Override public java.net.Socket createSocket(String h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
        @Override public java.net.Socket createSocket(String h,int p,InetAddress local,int port)throws IOException{return configure(delegate.createSocket(h,p,local,port));}
        @Override public java.net.Socket createSocket(InetAddress h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
        @Override public java.net.Socket createSocket(InetAddress h,int p,InetAddress local,int port)throws IOException{return configure(delegate.createSocket(h,p,local,port));}
    }
}
