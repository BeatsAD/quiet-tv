package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.json.JSONException;
import org.json.JSONObject;

final class WeatherStore {
    private static final String METEO_FALLBACK="meteo-fallback";
    private static final String FALLBACK_INITIALIZED="meteo-fallback-initialized";
    private final SharedPreferences preferences;
    private final Context context;
    WeatherStore(Context context){this.context=context;preferences=context.getSharedPreferences("weather",0);}
    static void initializeFallback(Context context){
        SharedPreferences preferences=context.getSharedPreferences("weather",0);
        synchronized(DesktopSettingsStore.MUTATIONS){
            if(preferences.getBoolean(FALLBACK_INITIALIZED,false))return;
            SharedPreferences.Editor editor=preferences.edit().putBoolean(FALLBACK_INITIALIZED,true);
            if(!preferences.contains(METEO_FALLBACK)){
                try{
                    PackageInfo installed=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
                    if(installed.firstInstallTime>0&&installed.firstInstallTime==installed.lastUpdateTime)
                        editor.putBoolean(METEO_FALLBACK,true);
                }catch(PackageManager.NameNotFoundException ignored){}
            }
            // Keep the initialized marker outside weather backup keys.
            editor.apply();
        }
    }
    boolean enabled(){return preferences.getBoolean("enabled",true);}
    void enabled(boolean value){synchronized(DesktopSettingsStore.MUTATIONS){preferences.edit().putBoolean("enabled",value).apply();}}
    void savePhoneEnabled(boolean value)throws java.io.IOException{synchronized(DesktopSettingsStore.MUTATIONS){boolean present=preferences.contains("enabled"),old=enabled();if(!preferences.edit().putBoolean("enabled",value).commit()){SharedPreferences.Editor rollback=preferences.edit();if(present)rollback.putBoolean("enabled",old);else rollback.remove("enabled");rollback.commit();throw new java.io.IOException("天气设置保存未确认");}}}
    void savePhoneSetting(String field,Object value)throws java.io.IOException{synchronized(DesktopSettingsStore.MUTATIONS){
        SharedPreferences.Editor editor=preferences.edit();
        try{switch(field){
            case "weatherEnabled":case "autoLocation":if(!(value instanceof Boolean))throw new IllegalArgumentException();break;
            default:break;
        }
        switch(field){
            case "weatherEnabled":editor.putBoolean("enabled",(Boolean)value);break;
            case "provider":if(!(value instanceof String)||!java.util.Arrays.asList("auto","qweather","uapi","openweather","openmeteo").contains(value)||!new WeatherClient(context).available((String)value))throw new IllegalArgumentException();editor.putString("provider",(String)value);break;
            case "cityCode":if(!(value instanceof String))throw new IllegalArgumentException();WeatherRegions.Region place=WeatherRegions.load(context).find((String)value);if(place==null||place.level==0)throw new IllegalArgumentException();editor.putString("adcode",place.code).putString("city",place.name).putString("place",WeatherRegions.placeJson(place).toString());break;
            case "autoLocation":if((Boolean)value)editor.remove("adcode").remove("city").remove("place");else{WeatherData current=cached(true);if(current==null)throw new PhoneSettingsServer.Rejected("请先更新电视定位或手动选择城市");WeatherRegions.Region selected=null;if(WeatherRegions.coordinates(current.latitude,current.longitude))selected=new WeatherRegions.Region(current.placeCode.isEmpty()?"point:"+current.latitude+","+current.longitude:current.placeCode,current.city,current.province,"","",2,current.latitude,current.longitude,current.adcode,"");else if(current.adcode.matches("[0-9]{6}"))selected=WeatherRegions.load(context).find(current.adcode);if(selected==null)throw new PhoneSettingsServer.Rejected("定位信息不足，请手动选择城市");editor.putString("adcode",selected.code).putString("city",selected.name).putString("place",WeatherRegions.placeJson(selected).toString());}break;
            default:throw new IllegalArgumentException();
        }BootAppStore.commit(preferences,editor);}catch(JSONException e){throw new IllegalArgumentException();}
    }}
    String adcode(){return preferences.getString("adcode","");}
    String city(){return preferences.getString("city","");}
    boolean automatic(){return adcode().isEmpty();}
    void automaticMode(){synchronized(DesktopSettingsStore.MUTATIONS){preferences.edit().remove("adcode").remove("city").remove("place").apply();}}
    void select(String adcode,String city){synchronized(DesktopSettingsStore.MUTATIONS){preferences.edit().putString("adcode",adcode).putString("city",city).remove("place").apply();}}
    void select(WeatherRegions.Region place){synchronized(DesktopSettingsStore.MUTATIONS){
        try{preferences.edit().putString("adcode",place.code).putString("city",place.name).putString("place",WeatherRegions.placeJson(place).toString()).apply();}
        catch(JSONException e){throw new IllegalArgumentException("Invalid selected place",e);}
    }}
    void selectConfirmed(WeatherRegions.Region place)throws java.io.IOException{synchronized(DesktopSettingsStore.MUTATIONS){
        try{BootAppStore.commit(preferences,preferences.edit().putString("adcode",place.code).putString("city",place.name).putString("place",WeatherRegions.placeJson(place).toString()));}
        catch(JSONException error){throw new java.io.IOException("地区信息无法保存",error);}
    }}
    WeatherRegions.Region selected(){
        try{String saved=preferences.getString("place","");if(!saved.isEmpty())return WeatherRegions.readPlace(new JSONObject(saved));}catch(JSONException ignored){}
        return WeatherRegions.load(context).find(adcode());
    }
    String provider(){return preferences.getString("provider","auto");}
    boolean meteoFallback(){return preferences.getBoolean(METEO_FALLBACK,false);}
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
