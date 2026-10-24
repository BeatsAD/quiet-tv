package home.quiet.tv;

import org.json.JSONException;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A weather response and its timestamps, kept with the location that produced it. */
final class WeatherData {
    static final long REFRESH_MS=30*60*1000L, STALE_MS=60*60*1000L, EXPIRE_MS=6*60*60*1000L;
    final String city, province, adcode, description, icon, locationSource,provider;
    final double temperature;
    final long fetchedAt, observedAt;
    WeatherData(JSONObject json,long fetchedAt,String source)throws JSONException {
        city=required(json,"city");province=json.optString("province","");adcode=json.optString("adcode","");
        description=required(json,"weather");icon=json.optString("weather_icon","999");temperature=json.getDouble("temperature");
        if(Double.isNaN(temperature)||Double.isInfinite(temperature))throw new JSONException("Invalid temperature");
        this.fetchedAt=fetchedAt;locationSource=source;provider=json.optString("provider","uapi");
        observedAt=reportTime(json.optString("report_time",""),fetchedAt);
    }
    private WeatherData(JSONObject json)throws JSONException {
        city=required(json,"city");province=json.optString("province","");adcode=json.optString("adcode","");
        description=required(json,"weather");icon=json.optString("weather_icon","999");temperature=json.getDouble("temperature");
        if(Double.isNaN(temperature)||Double.isInfinite(temperature))throw new JSONException("Invalid temperature");
        fetchedAt=json.getLong("fetchedAt");observedAt=json.optLong("observedAt",0);locationSource=required(json,"source");provider=json.optString("provider","uapi");
    }
    static WeatherData read(JSONObject json)throws JSONException{return new WeatherData(json);}
    private static String required(JSONObject json,String key)throws JSONException{
        String value=json.getString(key).trim();if(value.isEmpty()||"null".equals(value)||value.length()>120)throw new JSONException("Invalid weather field");return value;
    }
    long age(long now){
        if(fetchedAt<=0||now<fetchedAt-5*60*1000L||observedAt>now+5*60*1000L)return Long.MAX_VALUE;
        return Math.max(0,now-(observedAt>0?Math.min(observedAt,fetchedAt):fetchedAt));
    }
    boolean usable(long now){return age(now)<EXPIRE_MS;}
    boolean stale(long now){return age(now)>=STALE_MS;}
    String temperatureText(){return Math.round(temperature)+"°C";}
    String providerName(){return "qweather".equals(provider)?"和风天气":"UAPI";}
    JSONObject json()throws JSONException{
        return new JSONObject().put("city",city).put("province",province).put("adcode",adcode).put("weather",description)
            .put("weather_icon",icon).put("temperature",temperature).put("fetchedAt",fetchedAt).put("observedAt",observedAt).put("source",locationSource).put("provider",provider);
    }
    static long reportTime(String text,long received){
        Matcher relative=Pattern.compile("(\\d+)\\s*(分钟|小时|minutes?|hours?)",Pattern.CASE_INSENSITIVE).matcher(text);
        if(relative.find())try{
            long count=Long.parseLong(relative.group(1));if(count>100000)return 0;
            long unit=relative.group(2).startsWith("小")||relative.group(2).toLowerCase(Locale.US).startsWith("hour")?3600000L:60000L;
            return Math.max(0,received-count*unit);
        }catch(NumberFormatException e){return 0;}
        if(text.contains("刚刚")||text.equalsIgnoreCase("just now"))return received;
        for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm"}){
            SimpleDateFormat format=new SimpleDateFormat(pattern,Locale.US);format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));format.setLenient(false);
            ParsePosition position=new ParsePosition(0);java.util.Date date=format.parse(text,position);
            if(date!=null&&position.getIndex()==text.length())return date.getTime();
        }
        return 0; // A fetch time is never presented as an upstream observation time.
    }
}
