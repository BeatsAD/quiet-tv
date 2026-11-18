package home.quiet.tv;

import org.json.JSONException;
import org.json.JSONObject;

/** Independent settings referencing the shared catalog, never another copy of its media. */
final class ScreensaverSettings {
    static final int[] WAITS={0,1,2,5,10,15,30};
    static final String FOLLOW="follow",CLOCK="clock",FLIP="flip-clock",CUSTOM="custom";
    static final String TOP_LEFT="top-left",TOP_RIGHT="top-right",CENTER="center",BOTTOM_LEFT="bottom-left",BOTTOM_RIGHT="bottom-right";
    static final String[] POSITIONS={TOP_LEFT,TOP_RIGHT,CENTER,BOTTOM_LEFT,BOTTOM_RIGHT};
    final String mediaId,mode,timePosition,weatherPosition;
    final int idleMinutes;
    final ClockOptions clock;
    final boolean showTime,showWeather,sound,flipShowSeconds,flipSound;
    ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather){
        this(mediaId,idleMinutes,showTime,showWeather,CUSTOM);
    }
    ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather,String mode){
        this(mediaId,idleMinutes,showTime,showWeather,mode,CENTER,BOTTOM_LEFT,false,false,false,ClockOptions.DEFAULT);
    }
    private ScreensaverSettings(String mediaId,int idleMinutes,boolean showTime,boolean showWeather,String mode,String timePosition,String weatherPosition,boolean sound,boolean flipShowSeconds,boolean flipSound,ClockOptions clock){
        boolean valid=false;for(int value:WAITS)if(value==idleMinutes)valid=true;
        if(mediaId==null||!valid||!FOLLOW.equals(mode)&&!CLOCK.equals(mode)&&!FLIP.equals(mode)&&!CUSTOM.equals(mode)||!position(timePosition)||!position(weatherPosition))throw new IllegalArgumentException("Invalid screensaver settings");
        this.clock=clock;this.sound=sound;this.flipShowSeconds=flipShowSeconds;this.flipSound=flipSound;this.mediaId=mediaId;this.idleMinutes=idleMinutes;this.showTime=showTime;this.showWeather=showWeather;this.mode=mode;this.timePosition=timePosition;this.weatherPosition=weatherPosition;
    }
    private static boolean position(String value){for(String position:POSITIONS)if(position.equals(value))return true;return false;}
    ScreensaverSettings media(String id){return new ScreensaverSettings(id,idleMinutes,showTime,showWeather,CUSTOM,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings content(String mode){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings waitMinutes(int minutes){return new ScreensaverSettings(mediaId,minutes,showTime,showWeather,mode,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings time(boolean show){return new ScreensaverSettings(mediaId,idleMinutes,show,showWeather,mode,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings weather(boolean show){return new ScreensaverSettings(mediaId,idleMinutes,showTime,show,mode,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings timePosition(String position){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,position,weatherPosition,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings weatherPosition(String position){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,position,sound,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings sound(boolean enabled){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition,enabled,flipShowSeconds,flipSound,clock);}
    ScreensaverSettings clock(ClockOptions options){
        return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,options);
    }
    ScreensaverSettings flipSeconds(boolean enabled){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition,sound,enabled,flipSound,clock);}
    ScreensaverSettings flipSound(boolean enabled){return new ScreensaverSettings(mediaId,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition,sound,flipShowSeconds,enabled,clock);}
    String clockPosition(){return flipping()?CENTER:timePosition;}
    String timePattern(){return tickMillis()==1000?"HH:mm:ss":"HH:mm";}
    boolean digitalMode(){return CLOCK.equals(mode);}
    boolean flipping(){return FLIP.equals(mode);}
    boolean clockOnly(){return digitalMode()||flipping();}
    boolean analogVisible(){return digitalMode()&&clock.analog;}
    boolean largeDateVisible(){return digitalMode()&&!clock.analog&&ClockOptions.LARGE_DATE.equals(clock.layout);}
    boolean composedClock(){return digitalMode()&&(clock.calendar||analogVisible()||largeDateVisible());}
    boolean secondsVisible(){return digitalMode()&&clock.seconds&&!largeDateVisible();}
    boolean lunarAvailable(){return digitalMode()&&(clock.calendar||largeDateVisible());}
    long tickMillis(){return (flipping()?flipShowSeconds:secondsVisible())?1000:60000;}
    boolean video(WallpaperCatalog catalog){WallpaperCatalog.Entry entry=catalog.find(resolved(catalog));return !clockOnly()&&!(FOLLOW.equals(mode)&&catalog.weather)&&entry!=null&&entry.video();}
    String resolved(WallpaperCatalog catalog){return clockOnly()?WallpaperCatalog.DEFAULT:FOLLOW.equals(mode)?catalog.current:catalog.find(mediaId)==null?WallpaperCatalog.DEFAULT:mediaId;}
    ScreensaverSettings validated(WallpaperCatalog catalog){return catalog.find(mediaId)==null?new ScreensaverSettings(WallpaperCatalog.DEFAULT,idleMinutes,showTime,showWeather,mode,timePosition,weatherPosition,sound,flipShowSeconds,flipSound,clock):this;}
    JSONObject json()throws JSONException{return clock.write(new JSONObject().put("version",2).put("mode",mode).put("mediaId",mediaId).put("idleMinutes",idleMinutes).put("showTime",showTime).put("showWeather",showWeather).put("sound",sound).put("flipShowSeconds",flipShowSeconds).put("flipSound",flipSound).put("timePosition",timePosition).put("weatherPosition",weatherPosition));}
    static ScreensaverSettings read(JSONObject json)throws JSONException{
        if(json.optInt("version",1)!=1&&json.optInt("version",1)!=2)throw new JSONException("Unsupported screensaver settings");
        try{return new ScreensaverSettings(json.getString("mediaId"),json.getInt("idleMinutes"),json.getBoolean("showTime"),json.getBoolean("showWeather"),json.optString("mode",CUSTOM),json.has("timePosition")?json.getString("timePosition"):CENTER,json.has("weatherPosition")?json.getString("weatherPosition"):BOTTOM_LEFT,json.optBoolean("sound",false),json.has("flipShowSeconds")&&json.getBoolean("flipShowSeconds"),json.has("flipSound")&&json.getBoolean("flipSound"),ClockOptions.read(json));}
        catch(IllegalArgumentException e){throw new JSONException("Invalid screensaver settings");}
    }

    static final class ClockOptions {
        static final String ROUND="round", SQUARE="square", TIME="time", LARGE_DATE="large-date";
        static final String[] STYLES={ROUND,SQUARE}, LAYOUTS={TIME,LARGE_DATE};
        static final String[] KEYS={"showAnalogClock","showCalendar","showSeconds","showLunarDate","analogStyle","digitalLayout"};
        static final ClockOptions DEFAULT=new ClockOptions(false,false,false,false,ROUND,TIME);
        final boolean analog, calendar, seconds, lunar;
        final String style, layout;

        private ClockOptions(boolean analog,boolean calendar,boolean seconds,boolean lunar,String style,String layout){
            if((!ROUND.equals(style)&&!SQUARE.equals(style))||(!TIME.equals(layout)&&!LARGE_DATE.equals(layout))){
                throw new IllegalArgumentException("Invalid screensaver clock style");
            }
            this.analog=analog;
            this.calendar=calendar;
            this.seconds=seconds;
            this.lunar=lunar;
            this.style=style;
            this.layout=layout;
        }
        ClockOptions analog(boolean show){return new ClockOptions(show,calendar,seconds,lunar,style,layout);}
        ClockOptions calendar(boolean show){return new ClockOptions(analog,show,seconds,lunar,style,layout);}
        ClockOptions seconds(boolean show){return new ClockOptions(analog,calendar,show,lunar,style,layout);}
        ClockOptions lunar(boolean show){return new ClockOptions(analog,calendar,seconds,show,style,layout);}
        ClockOptions style(String value){return new ClockOptions(analog,calendar,seconds,lunar,value,layout);}
        ClockOptions layout(String value){return new ClockOptions(analog,calendar,seconds,lunar,style,value);}
        JSONObject write(JSONObject json)throws JSONException{
            return json.put("showAnalogClock",analog).put("showCalendar",calendar).put("showSeconds",seconds)
                .put("showLunarDate",lunar).put("analogStyle",style).put("digitalLayout",layout);
        }
        static ClockOptions read(JSONObject json)throws JSONException{
            return new ClockOptions(enabled(json,"showAnalogClock"),enabled(json,"showCalendar"),
                enabled(json,"showSeconds"),enabled(json,"showLunarDate"),
                json.optString("analogStyle",ROUND),json.optString("digitalLayout",TIME));
        }
        private static boolean enabled(JSONObject json,String key)throws JSONException{
            if(!json.has(key))return false;
            Object value=json.get(key);
            if(!(value instanceof Boolean))throw new JSONException("Invalid screensaver clock setting: "+key);
            return (Boolean)value;
        }
    }
}
