package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

final class WeatherStore {
    private final SharedPreferences preferences;
    WeatherStore(Context context){preferences=context.getSharedPreferences("weather",0);}
    boolean enabled(){return preferences.getBoolean("enabled",true);}
    void enabled(boolean value){preferences.edit().putBoolean("enabled",value).apply();}
    String adcode(){return preferences.getString("adcode","");}
    String city(){return preferences.getString("city","");}
    boolean automatic(){return adcode().isEmpty();}
    void automaticMode(){preferences.edit().remove("adcode").remove("city").apply();}
    void select(String adcode,String city){preferences.edit().putString("adcode",adcode).putString("city",city).apply();}
    boolean fixAutomaticCity(boolean chinese){
        WeatherData data=cached(chinese);
        if(data==null||!data.adcode.matches("[0-9]{6}"))return false;
        select(data.adcode,data.city);save(chinese,data);return true;
    }
    String key(boolean chinese){return (automatic()?"auto":adcode())+(chinese?":zh":":en");}
    WeatherData cached(boolean chinese){
        if(!key(chinese).equals(preferences.getString("cache-key","")))return null;
        try{return WeatherData.read(new JSONObject(preferences.getString("cache","")));}catch(JSONException e){return null;}
    }
    void save(boolean chinese,WeatherData data){
        try{preferences.edit().putString("cache-key",key(chinese)).putString("cache",data.json().toString()).apply();}
        catch(JSONException e){throw new IllegalStateException("Invalid weather snapshot",e);}
    }
}
