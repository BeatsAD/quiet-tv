package home.quiet.tv;

import org.json.JSONException;
import org.json.JSONObject;

/** Independent settings referencing the shared catalog, never another copy of its media. */
final class ScreensaverSettings {
    static final int[] WAITS={0,1,2,5,10,15,30};
    static final String FOLLOW="follow",CLOCK="clock",CUSTOM="custom";
    static final String TOP_LEFT="top-left",TOP_RIGHT="top-right",CENTER="center",BOTTOM_LEFT="bottom-left",BOTTOM_RIGHT="bottom-right";
    static final String[] POSITIONS={TOP_LEFT,TOP_RIGHT,CENTER,BOTTOM_LEFT,BOTTOM_RIGHT};
    final String mediaId,mode,timePosition,weatherPosition;
    final int idleMinutes;
    final boolean showTime,showWeather;
    ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather){
        this(mediaId,idleMinutes,showTime,showWeather,CUSTOM);
    }
    ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather,String mode){
        this(mediaId,idleMinutes,showTime,showWeather,mode,CENTER,BOTTOM_LEFT);
    }
    private ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather,String mode,String timePosition,String weatherPosition){
        boolean valid=false;for(int value:WAITS)if(value==idleMinutes)valid=true;
        if(mediaId==null||!valid||!FOLLOW.equals(mode)&&!CLOCK.equals(mode)&&!CUSTOM.equals(mode)||!position(timePosition)||!position(weatherPosition))throw new IllegalArgumentException("Invalid screensaver settings");
        this.mediaId=mediaId;this.idleMinutes=idleMinutes;this.showTime=showTime;this.showWeather=showWeather;this.mode=mode;this.timePosition=timePosition;this.weatherPosition=weatherPosition;
    }
    private static boolean position(String value){for(String position:POSITIONS)if(position.equals(value))return true;return false;}
    ScreensaverSettings media(String id){return new ScreensaverSettings(id,idleMinutes,showTime,showWeather,CUSTOM,timePosition,weatherPosition);}
    ScreensaverSettings content(String mode){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition);}
    ScreensaverSettings waitMinutes(int minutes){return new ScreensaverSettings(mediaId,minutes,showTime,showWeather,mode,timePosition,weatherPosition);}
    ScreensaverSettings time(boolean show){return new ScreensaverSettings(mediaId,idleMinutes,show,showWeather,mode,timePosition,weatherPosition);}
    ScreensaverSettings weather(boolean show){return new ScreensaverSettings(mediaId,idleMinutes,showTime,show,mode,timePosition,weatherPosition);}
    ScreensaverSettings timePosition(String position){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,position,weatherPosition);}
    ScreensaverSettings weatherPosition(String position){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,position);}
    String resolved(WallpaperCatalog catalog){return CLOCK.equals(mode)?WallpaperCatalog.DEFAULT:FOLLOW.equals(mode)?catalog.current:catalog.find(mediaId)==null?WallpaperCatalog.DEFAULT:mediaId;}
    ScreensaverSettings validated(WallpaperCatalog catalog){return catalog.find(mediaId)==null?new ScreensaverSettings(WallpaperCatalog.DEFAULT,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition):this;}
    JSONObject json()throws JSONException{return new JSONObject().put("version",2).put("mode",mode).put("mediaId",mediaId).put("idleMinutes",idleMinutes).put("showTime",showTime).put("showWeather",showWeather).put("timePosition",timePosition).put("weatherPosition",weatherPosition);}
    static ScreensaverSettings read(JSONObject json)throws JSONException{
        if(json.optInt("version",1)!=1&&json.optInt("version",1)!=2)throw new JSONException("Unsupported screensaver settings");
        try{return new ScreensaverSettings(json.getString("mediaId"),json.getInt("idleMinutes"),json.getBoolean("showTime"),json.getBoolean("showWeather"),json.optString("mode",CUSTOM),json.has("timePosition")?json.getString("timePosition"):CENTER,json.has("weatherPosition")?json.getString("weatherPosition"):BOTTOM_LEFT);}
        catch(IllegalArgumentException e){throw new JSONException("Invalid screensaver settings");}
    }
}
