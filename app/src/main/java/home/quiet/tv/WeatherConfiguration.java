package home.quiet.tv;

import android.content.Context;
import java.io.*;
import org.json.*;

/** Optional private build asset; a clean public clone needs no developer credentials. */
final class WeatherConfiguration {
    final String host,key;
    WeatherConfiguration(String host,String key){
        if(!host.isEmpty()&&!host.matches("[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*\\.qweatherapi\\.com"))throw new IllegalArgumentException("Invalid QWeather host");
        this.host=host;this.key=key;
    }
    boolean configured(){return !host.isEmpty()&&!key.isEmpty();}
    static WeatherConfiguration load(Context context){
        try(InputStream input=context.getAssets().open("weather-config.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[1024];int count;while((count=input.read(bytes))!=-1){if(out.size()+count>8192)throw new IOException("Invalid weather configuration");out.write(bytes,0,count);}
            JSONObject data=new JSONObject(out.toString("UTF-8"));return new WeatherConfiguration(data.optString("qweatherHost",""),data.optString("qweatherApiKey",""));
        }catch(IOException|JSONException|IllegalArgumentException e){return new WeatherConfiguration("","");}
    }
}
