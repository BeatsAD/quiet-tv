package home.quiet.tv;

import org.json.JSONException;
import org.json.JSONObject;

/** Optional observations, in explicit SI units. NaN means unknown; zero is real data. */
final class WeatherAtmosphere {
    static final WeatherAtmosphere EMPTY=new WeatherAtmosphere(new JSONObject());
    final double cloudCover,windSpeed,windFrom,gust,precipitation,windHeightMetres;
    final int precipitationSeconds;
    WeatherAtmosphere(JSONObject json){
        windHeightMetres=number(json,"windHeightMetres",0,500);cloudCover=number(json,"cloudCover",0,1);windSpeed=number(json,"windSpeedMs",0,100);
        double direction=number(json,"windFromDegrees",0,360);windFrom=direction==360?0:direction;
        gust=number(json,"windGustMs",0,150);precipitation=number(json,"precipitationMm",0,500);
        double interval=number(json,"precipitationSeconds",1,86400);precipitationSeconds=Double.isNaN(interval)?0:(int)interval;
    }
    static double number(JSONObject json,String key,double min,double max){double n=json.optDouble(key,Double.NaN);return Double.isNaN(n)||Double.isInfinite(n)||n<min||n>max?Double.NaN:n;}
    double rainPerHour(){return precipitationSeconds==0?Double.NaN:precipitation*3600/precipitationSeconds;}
    void write(JSONObject json)throws JSONException{
        put(json,"windHeightMetres",windHeightMetres);put(json,"cloudCover",cloudCover);put(json,"windSpeedMs",windSpeed);put(json,"windFromDegrees",windFrom);put(json,"windGustMs",gust);
        if(precipitationSeconds>0&&!Double.isNaN(precipitation)){json.put("precipitationMm",precipitation).put("precipitationSeconds",precipitationSeconds);}
    }
    private static void put(JSONObject json,String key,double n)throws JSONException{if(!Double.isNaN(n))json.put(key,n);}
    static WeatherAtmosphere openMeteo(JSONObject current,JSONObject units)throws JSONException{
        JSONObject data=new JSONObject().put("windHeightMetres",10);
        if("%".equals(units.optString("cloud_cover")))put(data,"cloudCover",number(current,"cloud_cover",0,100)/100);
        if("m/s".equals(units.optString("wind_speed_10m")))put(data,"windSpeedMs",number(current,"wind_speed_10m",0,100));
        if("°".equals(units.optString("wind_direction_10m")))put(data,"windFromDegrees",number(current,"wind_direction_10m",0,360));
        if("m/s".equals(units.optString("wind_gusts_10m")))put(data,"windGustMs",number(current,"wind_gusts_10m",0,150));
        if("mm".equals(units.optString("precipitation"))&&"seconds".equals(units.optString("interval"))){put(data,"precipitationMm",number(current,"precipitation",0,500));put(data,"precipitationSeconds",number(current,"interval",1,86400));}
        return new WeatherAtmosphere(data);
    }
    static WeatherAtmosphere openWeather(JSONObject response)throws JSONException{
        JSONObject data=new JSONObject(),wind=response.optJSONObject("wind"),clouds=response.optJSONObject("clouds");
        if(wind!=null){put(data,"windSpeedMs",number(wind,"speed",0,100));put(data,"windFromDegrees",number(wind,"deg",0,360));put(data,"windGustMs",number(wind,"gust",0,150));}
        if(clouds!=null)put(data,"cloudCover",number(clouds,"all",0,100)/100);
        // This provider documents 1h amounts in mm. Missing precipitation stays unknown.
        JSONObject rain=response.optJSONObject("rain"),snow=response.optJSONObject("snow");
        double r=rain==null?Double.NaN:number(rain,"1h",0,500),s=snow==null?Double.NaN:number(snow,"1h",0,500);
        if(!Double.isNaN(r)||!Double.isNaN(s))data.put("precipitationMm",(Double.isNaN(r)?0:r)+(Double.isNaN(s)?0:s)).put("precipitationSeconds",3600);
        return new WeatherAtmosphere(data);
    }
    static WeatherAtmosphere uapi(JSONObject response)throws JSONException{
        // UAPI currently exposes compass text and Beaufort class, not measured m/s.
        String[] names={"北风","东北风","东风","东南风","南风","西南风","西风","西北风"};
        JSONObject data=new JSONObject();for(int i=0;i<names.length;i++)if(names[i].equals(response.optString("wind_direction")))data.put("windFromDegrees",i*45);
        return new WeatherAtmosphere(data);
    }
}
