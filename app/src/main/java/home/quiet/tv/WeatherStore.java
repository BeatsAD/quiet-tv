package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

final class WeatherStore {
    private final SharedPreferences preferences;
    private final Context context;
    WeatherStore(Context context){this.context=context;preferences=context.getSharedPreferences("weather",0);}
    boolean enabled(){return preferences.getBoolean("enabled",true);}
    void enabled(boolean value){preferences.edit().putBoolean("enabled",value).apply();}
    String adcode(){return preferences.getString("adcode","");}
    String city(){return preferences.getString("city","");}
    boolean automatic(){return adcode().isEmpty();}
    void automaticMode(){preferences.edit().remove("adcode").remove("city").remove("place").apply();}
    void select(String adcode,String city){preferences.edit().putString("adcode",adcode).putString("city",city).remove("place").apply();}
    void select(WeatherRegions.Region place){
        try{preferences.edit().putString("adcode",place.code).putString("city",place.name).putString("place",WeatherRegions.placeJson(place).toString()).apply();}
        catch(JSONException e){throw new IllegalArgumentException("Invalid selected place",e);}
    }
    WeatherRegions.Region selected(){
        try{String saved=preferences.getString("place","");if(!saved.isEmpty())return WeatherRegions.readPlace(new JSONObject(saved));}catch(JSONException ignored){}
        return WeatherRegions.load(context).find(adcode());
    }
    String provider(){return preferences.getString("provider","auto");}
    void provider(String value){preferences.edit().putString("provider",value).apply();}
    boolean meteoFallback(){return preferences.getBoolean("meteo-fallback",false);}
    void meteoFallback(boolean value){preferences.edit().putBoolean("meteo-fallback",value).apply();}
    boolean fixAutomaticCity(boolean chinese){
        WeatherData data=cached(chinese);
        if(data==null)return false;
        if(WeatherRegions.coordinates(data.latitude,data.longitude))select(new WeatherRegions.Region(data.placeCode.isEmpty()?"point:"+data.latitude+","+data.longitude:data.placeCode,data.city,data.province,"","",2,data.latitude,data.longitude,data.adcode,""));
        else if(data.adcode.matches("[0-9]{6}"))select(data.adcode,data.city);else return false;
        save(chinese,data);return true;
    }
    String key(boolean chinese){return (automatic()?"auto":preferences.getString("place",adcode()))+(chinese?":zh":":en")+("auto".equals(provider())&&!meteoFallback()?"":":"+provider()+":"+meteoFallback());}
    WeatherData cached(boolean chinese){
        if(!key(chinese).equals(preferences.getString("cache-key","")))return null;
        try{return WeatherData.read(new JSONObject(preferences.getString("cache","")));}catch(JSONException e){return null;}
    }
    void save(boolean chinese,WeatherData data){
        try{preferences.edit().putString("cache-key",key(chinese)).putString("cache",data.json().toString()).apply();}
        catch(JSONException e){throw new IllegalStateException("Invalid weather snapshot",e);}
    }
}
