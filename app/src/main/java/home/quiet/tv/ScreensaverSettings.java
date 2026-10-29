package home.quiet.tv;

import org.json.JSONException;
import org.json.JSONObject;

/** Independent settings referencing the shared catalog, never another copy of its media. */
final class ScreensaverSettings {
    static final int[] WAITS={0,1,5,10,15};
    final String mediaId;
    final int idleMinutes;
    final boolean showTime,showWeather;
    ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather){
        boolean valid=false;for(int value:WAITS)if(value==idleMinutes)valid=true;
        if(mediaId==null||!valid)throw new IllegalArgumentException("Invalid screensaver settings");
        this.mediaId=mediaId;this.idleMinutes=idleMinutes;this.showTime=showTime;this.showWeather=showWeather;
    }
    ScreensaverSettings media(String id){return new ScreensaverSettings(id,idleMinutes,showTime,showWeather);}
    ScreensaverSettings waitMinutes(int minutes){return new ScreensaverSettings(mediaId,minutes,showTime,showWeather);}
    ScreensaverSettings time(boolean show){return new ScreensaverSettings(mediaId,idleMinutes,show,showWeather);}
    ScreensaverSettings weather(boolean show){return new ScreensaverSettings(mediaId,idleMinutes,showTime,show);}
    ScreensaverSettings validated(WallpaperCatalog catalog){return catalog.find(mediaId)==null?media(WallpaperCatalog.DEFAULT):this;}
    JSONObject json()throws JSONException{return new JSONObject().put("mediaId",mediaId).put("idleMinutes",idleMinutes).put("showTime",showTime).put("showWeather",showWeather);}
    static ScreensaverSettings read(JSONObject json)throws JSONException{
        try{return new ScreensaverSettings(json.getString("mediaId"),json.getInt("idleMinutes"),json.getBoolean("showTime"),json.getBoolean("showWeather"));}
        catch(IllegalArgumentException e){throw new JSONException("Invalid screensaver settings");}
    }
}
