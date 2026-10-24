package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.*;

/** QWeather first, UAPI fallback. Region selection itself is entirely offline. */
final class WeatherClient {
    static final String SOURCE_URL="https://dev.qweather.com/docs/";
    interface Transport { JSONObject get(String base,String path,String key)throws IOException,JSONException; }
    private static volatile long uapiUntil,qweatherUntil;
    private volatile HttpsURLConnection connection;
    private final WeatherRegions regions;
    private final WeatherConfiguration configuration;
    private final Transport transport;
    static final class ApiException extends IOException {
        final int status;final long retryAfterMs;
        ApiException(int status,long retryAfterMs){super("Weather service unavailable ("+status+")");this.status=status;this.retryAfterMs=retryAfterMs;}
    }
    WeatherClient(Context context){
        this(WeatherRegions.load(context),WeatherConfiguration.load(context),null);
        try{
            android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES);
            if(info.signatures!=null&&info.signatures.length>0){
                byte[] digest=java.security.MessageDigest.getInstance("SHA-1").digest(info.signatures[0].toByteArray());
                StringBuilder text=new StringBuilder();for(byte value:digest){if(text.length()>0)text.append(':');text.append(String.format(Locale.ROOT,"%02X",value&255));}certificate=text.toString();
            }
        }catch(android.content.pm.PackageManager.NameNotFoundException|java.security.NoSuchAlgorithmException ignored){}
    }
    WeatherClient(WeatherRegions regions,WeatherConfiguration configuration,Transport transport){this.regions=regions;this.configuration=configuration;this.transport=transport==null?this::request:transport;}
    WeatherData weather(String adcode,String source,boolean chinese)throws IOException,JSONException{
        WeatherData ip=null;String selected=adcode;
        // IP estimation is kept in the fallback service; manually selected regions never depend on it.
        if(selected.isEmpty()){ip=uapiWeather("",source,chinese);selected=ip.adcode;}
        WeatherRegions.Region region=regions.find(selected);
        if(configuration.configured()&&region!=null)try{
            JSONObject location=qweatherLocation(region.name,region.city.isEmpty()?region.province:region.city);
            String name=WeatherRegions.administrativeName(location.getString("name"));
            if(!name.equals(WeatherRegions.administrativeName(region.name))
                ||!WeatherRegions.administrativeName(location.getString("adm1")).equals(WeatherRegions.administrativeName(region.province))
                ||(!region.city.contains("直辖")&&!region.city.equals(region.province)&&!WeatherRegions.administrativeName(location.getString("adm2")).equals(WeatherRegions.administrativeName(region.city))))throw new ApiException(404,0);
            return qweatherWeather(region.code,region.name,region.province,location,source,chinese);
        }catch(IOException|JSONException e){checkInterrupted();DiagnosticLog.error("weather.primary.failed",e);}
        return ip!=null?ip:uapiWeather(adcode,source,chinese);
    }
    WeatherData locatedWeather(double latitude,double longitude,boolean chinese)throws IOException,JSONException{
        if(configuration.configured())try{
            JSONObject location=qweatherLocation(String.format(Locale.US,"%.2f,%.2f",longitude,latitude),"");
            String name=location.getString("name"),province=location.getString("adm1"),city=location.getString("adm2"),code="";
            for(WeatherRegions.Region candidate:regions.search(name,40))if(WeatherRegions.administrativeName(candidate.name).equals(WeatherRegions.administrativeName(name))
                &&WeatherRegions.administrativeName(candidate.province).equals(WeatherRegions.administrativeName(province))
                &&(candidate.city.equals(candidate.province)||WeatherRegions.administrativeName(candidate.city).equals(WeatherRegions.administrativeName(city)))){code=candidate.code;name=candidate.name;break;}
            return qweatherWeather(code,name,province,location,"system",chinese);
        }catch(IOException|JSONException e){checkInterrupted();DiagnosticLog.error("weather.primary.failed",e);}
        try{
            JSONArray result=get(false,"/misc/district?lat="+String.format(Locale.US,"%.2f",latitude)+"&lng="+String.format(Locale.US,"%.2f",longitude)+"&country=CN&limit=5").getJSONArray("results");
            for(int i=0;i<result.length();i++){String code=result.getJSONObject(i).optString("adcode");if(code.matches("[0-9]{6}"))return uapiWeather(code,"system",chinese);}
        }catch(IOException|JSONException e){checkInterrupted();}
        return uapiWeather("","ip",chinese);
    }
    private JSONObject qweatherLocation(String name,String parent)throws IOException,JSONException{
        JSONObject response=get(true,"/geo/v2/city/lookup?location="+encode(name)+"&range=cn&number=10&lang=zh"+(parent.isEmpty()?"":"&adm="+encode(parent)));
        String code=response.optString("code");if(!code.equals("200"))throw new ApiException(code.matches("[0-9]{3}")?Integer.parseInt(code):502,0);
        JSONArray locations=response.getJSONArray("location");if(locations.length()==0)throw new ApiException(404,0);return locations.getJSONObject(0);
    }
    private WeatherData qweatherWeather(String adcode,String name,String province,JSONObject location,String source,boolean chinese)throws IOException,JSONException{
        double lat=location.getDouble("lat"),lon=location.getDouble("lon");
        if(Double.isNaN(lat)||Double.isInfinite(lat)||Double.isNaN(lon)||Double.isInfinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new JSONException("Invalid coordinates");
        JSONObject response=get(true,String.format(Locale.US,"/weather/v1/current/%.2f/%.2f?lang=%s",lat,lon,chinese?"zh":"en"));
        JSONObject temperature=response.getJSONObject("temperature");if(!"°C".equals(temperature.getString("unit")))throw new JSONException("Unexpected temperature unit");
        JSONObject normalized=new JSONObject().put("city",name).put("province",province).put("adcode",adcode)
            .put("weather",response.getJSONObject("condition").getString("text")).put("weather_icon",response.getJSONObject("condition").getString("code"))
            .put("temperature",temperature.getDouble("value")).put("provider","qweather");
        // The current v1 response has no observation timestamp. Do not invent one.
        return new WeatherData(normalized,System.currentTimeMillis(),source);
    }
    private WeatherData uapiWeather(String adcode,String source,boolean chinese)throws IOException,JSONException{
        JSONObject response=get(false,"/misc/weather?lang="+(chinese?"zh":"en")+(adcode.isEmpty()?"":"&adcode="+encode(adcode)));
        if(!adcode.isEmpty()&&!adcode.equals(response.optString("adcode")))throw new JSONException("Weather region mismatch");
        response.put("provider","uapi");String district=response.optString("district","");if(!district.isEmpty())response.put("city",district);
        return new WeatherData(response,System.currentTimeMillis(),source);
    }
    private static void checkInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    private static String encode(String value)throws UnsupportedEncodingException{return URLEncoder.encode(value,"UTF-8");}
    private JSONObject get(boolean qweather,String path)throws IOException,JSONException{
        checkInterrupted();long remaining=(qweather?qweatherUntil:uapiUntil)-System.nanoTime()/1000000L;
        if(remaining>0)throw new ApiException(429,remaining);
        try{return transport.get(qweather?"https://"+configuration.host:"https://uapis.cn/api/v1",path,qweather?configuration.key:"");}
        catch(ApiException e){if(e.status==429){long until=System.nanoTime()/1000000L+Math.max(WeatherData.REFRESH_MS,e.retryAfterMs);if(qweather)qweatherUntil=until;else uapiUntil=until;}throw e;}
    }
    private JSONObject request(String base,String path,String key)throws IOException,JSONException{
        HttpsURLConnection request=(HttpsURLConnection)new URL(base+path).openConnection();connection=request;
        try{
            if(Build.VERSION.SDK_INT<21)request.setSSLSocketFactory(new Tls12SocketFactory());
            request.setConnectTimeout(8000);request.setReadTimeout(10000);request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Accept","application/json");request.setRequestProperty("Accept-Encoding","gzip");request.setRequestProperty("User-Agent","QuietTV/"+BuildConfig.VERSION_NAME);
            if(!key.isEmpty()){
                request.setRequestProperty("X-QW-Api-Key",key);
                request.setRequestProperty("X-Android-Package-Name",BuildConfig.APPLICATION_ID);
                if(!certificate.isEmpty())request.setRequestProperty("X-Android-Cert",certificate);
            }
            int status=request.getResponseCode();
            if(status!=200){
                long retry=0;try{retry=Math.min(86400,Math.max(0,Long.parseLong(request.getHeaderField("Retry-After"))))*1000L;}catch(NumberFormatException ignored){}
                if(retry==0)retry=Math.min(86400000,Math.max(0,request.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis()));
                throw new ApiException(status,retry);
            }
            try(InputStream raw=request.getInputStream();InputStream input="gzip".equalsIgnoreCase(request.getContentEncoding())?new GZIPInputStream(raw):raw){return readJson(input);}
        }finally{request.disconnect();if(connection==request)connection=null;}
    }
    static JSONObject readJson(InputStream input)throws IOException,JSONException{
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1){checkInterrupted();if(out.size()+count>256*1024)throw new IOException("Weather response too large");out.write(bytes,0,count);}
            return new JSONObject(out.toString("UTF-8"));
        }
    }
    private String certificate="";
    void cancel(){HttpsURLConnection active=connection;if(active!=null)active.disconnect();}
}
