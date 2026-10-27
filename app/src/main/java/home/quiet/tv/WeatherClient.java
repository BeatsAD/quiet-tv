package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.*;

/** Coordinate weather requests, independent place search and bounded sequential failover. */
final class WeatherClient {
    static final String SOURCE_URL="https://dev.qweather.com/docs/";
    interface Transport { JSONObject get(String base,String path,String key)throws IOException,JSONException; }
    private static volatile long uapiUntil,qweatherUntil,openMeteoUntil,openWeatherUntil;
    private final NetworkRequest requests=new NetworkRequest();
    private android.content.res.AssetManager assets;
    private SSLSocketFactory legacyTls;
    private boolean tlsLogged;
    private final WeatherRegions regions;
    private final WeatherConfiguration configuration;
    private final Transport transport;
    static final class ApiException extends IOException {
        final int status;final long retryAfterMs;
        ApiException(int status,long retryAfterMs){super("Weather service unavailable ("+status+")");this.status=status;this.retryAfterMs=retryAfterMs;}
    }
    WeatherClient(Context context){
        this(WeatherRegions.load(context),WeatherConfiguration.load(context),null);assets=context.getAssets();
        try{
            android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES);
            if(info.signatures!=null&&info.signatures.length>0){
                byte[] digest=java.security.MessageDigest.getInstance("SHA-1").digest(info.signatures[0].toByteArray());
                StringBuilder text=new StringBuilder();for(byte value:digest){if(text.length()>0)text.append(':');text.append(String.format(Locale.ROOT,"%02X",value&255));}certificate=text.toString();
            }
        }catch(android.content.pm.PackageManager.NameNotFoundException|java.security.NoSuchAlgorithmException ignored){}
    }
    WeatherClient(WeatherRegions regions,WeatherConfiguration configuration,Transport transport){this.regions=regions;this.configuration=configuration;this.transport=transport==null?this::request:transport;}
    static String providerName(String provider){switch(provider){case "qweather":return "和风天气";case "openmeteo":return "Open-Meteo";case "openweather":return "OpenWeather";case "auto":return "自动";default:return "UAPI";}}
    static String website(String provider){switch(provider){case "qweather":return "https://www.qweather.com";case "openmeteo":return "https://open-meteo.com/";case "openweather":return "https://openweathermap.org/";default:return "https://uapis.cn";}}
    static String terms(String provider){switch(provider){case "qweather":return "https://dev.qweather.com/docs/terms/tos/";case "openmeteo":return "https://open-meteo.com/en/terms";case "openweather":return "https://openweather.co.uk/privacy-policy";default:return "https://uapis.cn/docs/getting-started/privacy-policy";}}
    boolean available(String provider){return "auto".equals(provider)||"uapi".equals(provider)||"openmeteo".equals(provider)||("qweather".equals(provider)&&configuration.configured())||("openweather".equals(provider)&&!configuration.openWeatherKey.isEmpty());}
    WeatherData weather(String adcode,String source,boolean chinese)throws IOException,JSONException{
        WeatherRegions.Region place=regions.find(adcode);
        if(place==null&&!adcode.isEmpty())place=new WeatherRegions.Region(adcode,adcode,"","","",2);
        return weather(place,"auto",false,source,chinese);
    }
    WeatherData weather(WeatherRegions.Region selected,String mode,boolean meteoFallback,String source,boolean chinese)throws IOException,JSONException{
        checkInterrupted();if(!available(mode))throw new ApiException(503,0);
        WeatherData ip=null;
        if(selected==null){
            ip=uapiWeather(null,"ip",chinese);selected=regions.find(ip.adcode);
            if(selected==null)selected=new WeatherRegions.Region(ip.adcode,ip.city,ip.province,"","",2);
        }
        String[] order="auto".equals(mode)?new String[]{"qweather","uapi","openweather","openmeteo"}:new String[]{mode};
        IOException io=null;JSONException json=null;long retry=Long.MAX_VALUE;boolean nonLimited=false;
        for(String provider:order){
            if(!available(provider)||("auto".equals(mode)&&"openmeteo".equals(provider)&&!meteoFallback))continue;
            // Only automatic system location may resolve coordinates to a UAPI district.
            if("uapi".equals(provider)&&selected.adcode.isEmpty()&&ip==null&&!"system".equals(source))continue;
            try{
                if("uapi".equals(provider)){
                    if(ip!=null)return ip;
                    if(selected.adcode.isEmpty())return uapiCoordinates(selected,source,chinese);
                    return uapiWeather(selected,source,chinese);
                }
                if("qweather".equals(provider)){
                    WeatherRegions.Region point=selected.located()?selected:qweatherPoint(selected);
                    return qweatherWeather(selected,point,source,chinese);
                }
                if(!selected.located())selected=resolve(selected);
                if("openweather".equals(provider))return openWeather(selected,source,chinese);
                return openMeteo(selected,source,chinese);
            }catch(IOException|JSONException e){
                checkInterrupted();DiagnosticLog.info("weather.source.failed","provider="+provider+" "+NetworkDiagnostics.failureDetails(e));
                if(e instanceof ApiException&&((ApiException)e).status==429)retry=Math.min(retry,((ApiException)e).retryAfterMs);else nonLimited=true;
                if(e instanceof IOException){io=(IOException)e;json=null;}else{json=(JSONException)e;io=null;}
            }
        }
        if(!nonLimited&&retry!=Long.MAX_VALUE)throw new ApiException(429,retry);
        if(json!=null)throw json;if(io!=null)throw io;throw new ApiException(404,0);
    }
    WeatherData locatedWeather(double latitude,double longitude,boolean chinese)throws IOException,JSONException{return locatedWeather(latitude,longitude,"auto",false,chinese);}
    WeatherData locatedWeather(double latitude,double longitude,String mode,boolean meteoFallback,boolean chinese)throws IOException,JSONException{
        if(!WeatherRegions.coordinates(latitude,longitude))throw new JSONException("Invalid coordinates");
        // System coordinates are sufficient: neither reverse-geocoding nor a district ID gates weather.
        WeatherRegions.Region point=new WeatherRegions.Region("point:"+latitude+","+longitude,chinese?"当前位置":"Current location","","","",2,latitude,longitude,"","");
        if("uapi".equals(mode))return uapiCoordinates(point,"system",chinese);
        return weather(point,mode,meteoFallback,"system",chinese);
    }
    List<WeatherRegions.Region> cities(String query)throws IOException,JSONException{
        if(query.trim().isEmpty()||query.length()>60)return Collections.emptyList();
        return districts("keywords="+encode(query)+"&country=CN&limit=20");
    }
    WeatherRegions.Region resolve(WeatherRegions.Region place)throws IOException,JSONException{
        if(place.located())return place;if(place.adcode.isEmpty())throw new ApiException(404,0);
        for(WeatherRegions.Region candidate:districts("adcode="+encode(place.adcode)))if(place.adcode.equals(candidate.adcode))return place.at(candidate.latitude,candidate.longitude);
        throw new ApiException(404,0);
    }
    private List<WeatherRegions.Region> districts(String query)throws IOException,JSONException{
        JSONArray items=get("uapi","/misc/district?"+query).optJSONArray("results");List<WeatherRegions.Region> result=new ArrayList<>();if(items==null)return result;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);String code=item.optString("adcode"),level=item.optString("level"),name=item.optString("name");JSONObject center=item.optJSONObject("center");
            if(!"CN".equals(item.optString("country_code"))||!code.matches("[0-9]{6}")||name.trim().isEmpty()||!("city".equals(level)||"district".equals(level)||"street".equals(level))||center==null)continue;
            double lat=center.optDouble("lat",Double.NaN),lon=center.optDouble("lng",Double.NaN);if(!WeatherRegions.coordinates(lat,lon))continue;
            WeatherRegions.Region local=regions.find(code);
            boolean street="street".equals(level);
            result.add(new WeatherRegions.Region(street?"point:"+lat+","+lon:code,name,item.optString("province",""),item.optString("city",""),local==null?"":local.parent,"city".equals(level)?1:2,lat,lon,street?"":code,""));
        }
        return result;
    }
    private WeatherRegions.Region qweatherPoint(WeatherRegions.Region region)throws IOException,JSONException{
        JSONObject response=get("qweather","/geo/v2/city/lookup?location="+encode(region.name)+"&range=cn&number=10&lang=zh&adm="+encode(region.city.isEmpty()?region.province:region.city));
        if(!"200".equals(response.optString("code")))throw new ApiException(404,0);
        JSONArray locations=response.getJSONArray("location");
        for(int i=0;i<locations.length();i++){
            JSONObject place=locations.getJSONObject(i);
            if(!same(place.optString("name"),region.name)||!same(place.optString("adm1"),region.province)||(!region.city.contains("直辖")&&!region.city.equals(region.province)&&!same(place.optString("adm2"),region.city)))continue;
            double lat=place.getDouble("lat"),lon=place.getDouble("lon");if(!WeatherRegions.coordinates(lat,lon))throw new JSONException("Invalid coordinates");return region.at(lat,lon);
        }
        throw new ApiException(404,0);
    }
    private static boolean same(String left,String right){return WeatherRegions.administrativeName(left).equals(WeatherRegions.administrativeName(right));}
    private JSONObject normalized(WeatherRegions.Region place,String provider)throws JSONException{
        JSONObject data=new JSONObject().put("city",place.name).put("province",place.province).put("adcode",place.adcode).put("placeCode",place.code).put("provider",provider);
        if(place.located())data.put("latitude",place.latitude).put("longitude",place.longitude);return data;
    }
    private WeatherData qweatherWeather(WeatherRegions.Region place,WeatherRegions.Region point,String source,boolean chinese)throws IOException,JSONException{
        JSONObject response=get("qweather",String.format(Locale.US,"/weather/v1/current/%.2f/%.2f?lang=%s",point.latitude,point.longitude,chinese?"zh":"en"));
        JSONObject temperature=response.getJSONObject("temperature");if(!"°C".equals(temperature.getString("unit")))throw new JSONException("Unexpected temperature unit");
        return new WeatherData(normalized(place,"qweather").put("weather",response.getJSONObject("condition").getString("text")).put("weather_icon",response.getJSONObject("condition").getString("code")).put("temperature",temperature.getDouble("value")),System.currentTimeMillis(),source);
    }
    private WeatherData uapiCoordinates(WeatherRegions.Region point,String source,boolean chinese)throws IOException,JSONException{
        for(WeatherRegions.Region candidate:districts("lat="+decimal(point.latitude)+"&lng="+decimal(point.longitude)+"&country=CN&limit=5"))if(!candidate.adcode.isEmpty())return uapiWeather(candidate,source,chinese);
        throw new ApiException(404,0);
    }
    private WeatherData uapiWeather(WeatherRegions.Region place,String source,boolean chinese)throws IOException,JSONException{
        String adcode=place==null?"":place.adcode;
        if(place!=null&&adcode.isEmpty())throw new ApiException(404,0);
        JSONObject response=get("uapi","/misc/weather?lang="+(chinese?"zh":"en")+(adcode.isEmpty()?"":"&adcode="+encode(adcode)));
        if(!adcode.isEmpty()&&!adcode.equals(response.optString("adcode")))throw new JSONException("Weather region mismatch");
        response.put("provider","uapi");String district=response.optString("district","");if(!district.isEmpty())response.put("city",district);
        if(place!=null){response.put("city",place.name).put("placeCode",place.code);if(place.located())response.put("latitude",place.latitude).put("longitude",place.longitude);}
        return new WeatherData(response,System.currentTimeMillis(),source);
    }
    private WeatherData openMeteo(WeatherRegions.Region place,String source,boolean chinese)throws IOException,JSONException{
        JSONObject response=get("openmeteo","/v1/forecast?latitude="+decimal(place.latitude)+"&longitude="+decimal(place.longitude)+"&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timeformat=unixtime&forecast_days=1&cell_selection=nearest");
        checkPosition(response,"latitude","longitude",place);
        JSONObject current=response.getJSONObject("current"),units=response.getJSONObject("current_units");if(!"°C".equals(units.getString("temperature_2m")))throw new JSONException("Unexpected temperature unit");
        String[] condition=meteoCondition(current.getInt("weather_code"),current.getInt("is_day")==1,chinese);
        return new WeatherData(normalized(place,"openmeteo").put("weather",condition[0]).put("weather_icon",condition[1]).put("temperature",current.getDouble("temperature_2m")).put("observedAt",current.getLong("time")*1000L),System.currentTimeMillis(),source);
    }
    private WeatherData openWeather(WeatherRegions.Region place,String source,boolean chinese)throws IOException,JSONException{
        JSONObject response=get("openweather","/data/2.5/weather?lat="+decimal(place.latitude)+"&lon="+decimal(place.longitude)+"&units=metric&lang="+(chinese?"zh_cn":"en"));
        checkPosition(response.getJSONObject("coord"),"lat","lon",place);
        JSONObject condition=response.getJSONArray("weather").getJSONObject(0);int id=condition.getInt("id");String icon=id>=200&&id<300?"302":id>=300&&id<600?"305":id>=600&&id<700?"400":id>=700&&id<800?"501":id==800?(condition.optString("icon").endsWith("n")?"150":"100"):id>800&&id<900?"101":"999";
        return new WeatherData(normalized(place,"openweather").put("weather",condition.getString("description")).put("weather_icon",icon).put("temperature",response.getJSONObject("main").getDouble("temp")).put("observedAt",response.getLong("dt")*1000L),System.currentTimeMillis(),source);
    }
    private static void checkPosition(JSONObject point,String latKey,String lonKey,WeatherRegions.Region selected)throws JSONException{
        double lat=point.getDouble(latKey),lon=point.getDouble(lonKey);
        if(!WeatherRegions.coordinates(lat,lon)||Math.abs(lat-selected.latitude)>.25||Math.abs(lon-selected.longitude)*Math.cos(Math.toRadians(selected.latitude))>.25)throw new JSONException("Weather coordinate mismatch");
    }
    static String[] meteoCondition(int code,boolean day,boolean chinese){
        String zh,en,icon;
        switch(code){
            case 0:zh="晴";en="Clear";icon=day?"100":"150";break;
            case 1:case 2:zh="多云";en="Partly cloudy";icon="101";break;
            case 3:zh="阴";en="Overcast";icon="104";break;
            case 45:case 48:zh="雾";en="Fog";icon="501";break;
            case 51:case 53:case 55:zh="毛毛雨";en="Drizzle";icon="309";break;
            case 56:case 57:case 66:case 67:zh="冻雨";en="Freezing rain";icon="313";break;
            case 61:case 63:case 65:zh="雨";en="Rain";icon="305";break;
            case 71:case 73:case 75:case 77:zh="雪";en="Snow";icon="400";break;
            case 80:case 81:case 82:zh="阵雨";en="Rain showers";icon="300";break;
            case 85:case 86:zh="阵雪";en="Snow showers";icon="407";break;
            case 95:case 96:case 99:zh="雷雨";en="Thunderstorm";icon="302";break;
            default:zh="天气状况未知";en="Unknown conditions";icon="999";
        }
        return new String[]{chinese?zh:en,icon};
    }
    private static String decimal(double value){return String.format(Locale.US,"%.2f",value);}
    private static void checkInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
    private static String encode(String value)throws UnsupportedEncodingException{return URLEncoder.encode(value,"UTF-8");}
    private static long until(String provider){switch(provider){case "qweather":return qweatherUntil;case "openmeteo":return openMeteoUntil;case "openweather":return openWeatherUntil;default:return uapiUntil;}}
    private static void limited(String provider,long until){switch(provider){case "qweather":qweatherUntil=until;break;case "openmeteo":openMeteoUntil=until;break;case "openweather":openWeatherUntil=until;break;default:uapiUntil=until;}}
    private JSONObject get(String provider,String path)throws IOException,JSONException{
        checkInterrupted();long remaining=until(provider)-System.nanoTime()/1000000L;if(remaining>0)throw new ApiException(429,remaining);
        String base="uapi".equals(provider)?"https://uapis.cn/api/v1":"qweather".equals(provider)?"https://"+configuration.host:"openmeteo".equals(provider)?"https://api.open-meteo.com":"https://api.openweathermap.org";
        try{
            JSONObject result=transport.get(base,path,"qweather".equals(provider)?configuration.key:"openweather".equals(provider)?configuration.openWeatherKey:"");
            String status="qweather".equals(provider)?result.optString("code",""):"openweather".equals(provider)?result.optString("cod",""):"";
            if(!status.isEmpty()&&!"200".equals(status))throw new ApiException(status.matches("[0-9]{3}")?Integer.parseInt(status):502,0);return result;
        }
        catch(IOException|JSONException e){
            // Never log URLs, query strings, authentication headers or provider error bodies.
            DiagnosticLog.info("weather.request.failed","provider="+provider+" "+NetworkDiagnostics.failureDetails(e)+(e instanceof ApiException?" http="+((ApiException)e).status:""));
            if(e instanceof ApiException&&((ApiException)e).status==429)limited(provider,System.nanoTime()/1000000L+Math.max(WeatherData.REFRESH_MS,((ApiException)e).retryAfterMs));throw e;
        }
    }
    private JSONObject request(String base,String path,String key)throws IOException,JSONException{
        boolean qweather=base.endsWith(".qweatherapi.com");
        if(base.equals("https://api.openweathermap.org"))path+="&appid="+encode(key);
        HttpsURLConnection request=(HttpsURLConnection)new URL(base+path).openConnection();NetworkRequest.Handle handle=requests.begin(request);
        String stage="tls_setup";int status=0;
        try{
            checkInterrupted();
            if(Build.VERSION.SDK_INT<=25){
                if(legacyTls==null)try(InputStream root=assets.open("certificates/isrg-root-x1.pem")){
                    SSLSocketFactory factory=WeatherTls.socketFactory(root);legacyTls=Build.VERSION.SDK_INT<21?new Tls12SocketFactory(factory):factory;
                }
                request.setSSLSocketFactory(legacyTls);
            }
            if(!tlsLogged){DiagnosticLog.networkTls("weather.tls",request.getSSLSocketFactory());tlsLogged=true;}
            request.setConnectTimeout(8000);request.setReadTimeout(10000);request.setInstanceFollowRedirects(false);
            request.setRequestProperty("Accept","application/json");request.setRequestProperty("Accept-Encoding","gzip");request.setRequestProperty("User-Agent","QuietTV/"+BuildConfig.VERSION_NAME);
            if(qweather){request.setRequestProperty("X-QW-Api-Key",key);request.setRequestProperty("X-Android-Package-Name",BuildConfig.APPLICATION_ID);if(!certificate.isEmpty())request.setRequestProperty("X-Android-Cert",certificate);}
            stage="connect";status=request.getResponseCode();stage="response";
            if(status!=200){
                long retry=0;try{retry=Math.min(86400,Math.max(0,Long.parseLong(request.getHeaderField("Retry-After"))))*1000L;}catch(NumberFormatException ignored){}
                if(retry==0)retry=Math.min(86400000,Math.max(0,request.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis()));throw new ApiException(status,retry);
            }
            stage="read";
            try(InputStream raw=request.getInputStream();InputStream input="gzip".equalsIgnoreCase(request.getContentEncoding())?new GZIPInputStream(raw):raw){return readJson(input);}
        }catch(IOException|JSONException e){DiagnosticLog.info("weather.transport.failed","stage="+(e instanceof JSONException?"parse":stage)+" "+NetworkDiagnostics.failureDetails(e)+(status>0?" http="+status:""));throw e;}
        finally{requests.finish(handle);}
    }
    static JSONObject readJson(InputStream input)throws IOException,JSONException{
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int count;while((count=input.read(bytes))!=-1){checkInterrupted();if(out.size()+count>256*1024)throw new IOException("Weather response too large");out.write(bytes,0,count);}return new JSONObject(out.toString("UTF-8"));
        }
    }
    private String certificate="";
    void cancel(){requests.cancel();}
}
