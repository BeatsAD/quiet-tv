package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.util.Map;

/** Local display preferences shared by Home and its settings page. */
final class DesktopSettingsStore {
    static final Object MUTATIONS=new Object();
    static final String MOVIE_INFO="show-movie-info";
    private final Context app;
    private final SharedPreferences preferences;
    private final SharedPreferences dock;
    DesktopSettingsStore(Context context){
        app=context.getApplicationContext();
        preferences=context.getSharedPreferences("desktop-settings",0);
        dock=context.getSharedPreferences("dock",0);
        // Capture the old combined setting once, before either independent switch changes.
        if(!preferences.contains("show-date"))preferences.edit().putBoolean("show-date",preferences.getBoolean("show-time",true)).apply();
    }
    String layout(){return "gallery".equals(preferences.getString("home-layout","classic"))?"gallery":"classic";}
    boolean galleryLayout(){return "gallery".equals(layout());}
    void layout(String layout){synchronized(MUTATIONS){if(!"classic".equals(layout)&&!"gallery".equals(layout))throw new IllegalArgumentException("Unknown home layout");preferences.edit().putString("home-layout",layout).apply();DiagnosticLog.info("desktop.layout",layout);}}
    boolean showDate(){return preferences.getBoolean("show-date",true);}
    void showDate(boolean show){synchronized(MUTATIONS){preferences.edit().putBoolean("show-date",show).apply();DiagnosticLog.info("desktop.setting","show-date="+show);}}
    boolean showSeconds(){return preferences.getBoolean("show-seconds",false);}
    void showSeconds(boolean show)throws IOException{savePhoneValue("show-seconds",show);}
    boolean showLunarDate(){return preferences.getBoolean("show-lunar-date",false);}
    void showLunarDate(boolean show)throws IOException{savePhoneValue("show-lunar-date",show);}
    boolean showHomeQuote(){return showHomeQuote(WallpaperStore.get(app).dailyMovieSelected());}
    boolean showHomeQuote(boolean movieWallpaper){return preferences.getBoolean(DailyQuote.HOME,false)&&!showMovieInfo(movieWallpaper);}
    boolean showScreensaverQuote(){return preferences.getBoolean(DailyQuote.SAVER,false);}
    boolean showMovieInfo(boolean movieWallpaper){return movieWallpaper&&preferences.getBoolean(MOVIE_INFO,true);}
    void greetingEnabled(boolean enabled){synchronized(MUTATIONS){preferences.edit().putBoolean("greeting-enabled",enabled).apply();DiagnosticLog.info("desktop.greeting.visibility",String.valueOf(enabled));}}
    boolean greetingEnabled(){return preferences.getBoolean("greeting-enabled",true);}
    String greetingText(String fallback){return preferences.getString("greeting-text",fallback);}
    static String automaticGreeting(boolean chinese,int hour){return hour<11?(chinese?"早上好":"Good morning"):hour<18?(chinese?"下午好":"Good afternoon"):(chinese?"晚上好":"Good evening");}
    void saveGreeting(boolean enabled,String text){synchronized(MUTATIONS){preferences.edit().putBoolean("greeting-enabled",enabled).putString("greeting-text",text.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim()).apply();DiagnosticLog.info("desktop.greeting.saved","enabled="+enabled);}}
    boolean avatarEnabled(){return preferences.getBoolean("greeting-avatar-enabled",false);}
    String avatarFile(){return preferences.getString("greeting-avatar-file","");}
    /** Called on the editor worker only, after its candidate file has been flushed. */
    void saveGreeting(boolean enabled,String text,boolean avatarEnabled,String file) throws IOException {saveGreeting(enabled,text,avatarEnabled,file,null);}
    void saveGreeting(boolean enabled,String text,boolean avatarEnabled,String file,String expectedText) throws IOException {synchronized(MUTATIONS){
        if(expectedText!=null&&!greetingText("").equals(expectedText))throw new IOException("问候语已在其他位置修改，请返回后重新编辑");
        if(!file.isEmpty()&&!GreetingAvatarStore.validName(file))throw new IOException("无效的头像 / Invalid avatar");
        Map<String,?> before=preferences.getAll();
        boolean written=preferences.edit().putBoolean("greeting-enabled",enabled).putString("greeting-text",text.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim())
            .putBoolean("greeting-avatar-enabled",avatarEnabled&&!file.isEmpty()).putString("greeting-avatar-file",file).commit();
        if(!written){
            // commit can fail after updating the in-memory map. Restore these four keys too.
            SharedPreferences.Editor rollback=preferences.edit();
            for(String key:new String[]{"greeting-enabled","greeting-text","greeting-avatar-enabled","greeting-avatar-file"}){
                Object value=before.get(key);if(value instanceof Boolean)rollback.putBoolean(key,(Boolean)value);else if(value instanceof String)rollback.putString(key,(String)value);else rollback.remove(key);
            }
            rollback.commit();throw new IOException("保存失败，请检查电视存储空间后重试 / Unable to save settings");
        }
        DiagnosticLog.info("desktop.greeting.saved","enabled="+enabled+" avatar="+(avatarEnabled&&!file.isEmpty()));
    }}
    boolean keySounds(){return preferences.getBoolean("key-sounds",false);}
    KeySoundTheme keySoundTheme(){return KeySoundTheme.from(preferences.getString("key-sound-theme",KeySoundTheme.WOOD.id));}
    void keySoundTheme(String theme)throws IOException{savePhoneValue("key-sound-theme",theme);}
    void keySounds(boolean enabled){synchronized(MUTATIONS){preferences.edit().putBoolean("key-sounds",enabled).apply();}if(enabled)KeySounds.prepare(app);else KeySounds.stop();}
    boolean showTime(){return preferences.getBoolean("show-time",true);}
    static final String[] CLOCK_STYLES={"digital","analog-light","analog-dark"};
    static boolean validClockStyle(Object value){return java.util.Arrays.asList(CLOCK_STYLES).contains(value);}
    String clockStyle(){String value=preferences.getString("clock-style","digital");return validClockStyle(value)?value:"digital";}
    // Older configurations and backups have only the visibility switch.
    String appTitlePosition(){return preferences.getString("app-title-position",preferences.getBoolean("show-app-title",true)?"below":"hidden");}
    static final String TOP_SHELF = "top-shelf";
    boolean topShelf(){return preferences.getBoolean(TOP_SHELF,false);}
    boolean showHomeApps(){return preferences.getBoolean("show-home-apps",true);}
    boolean hideHomeAppsOnSecondScreen(){return preferences.getBoolean("hide-home-apps-on-second-screen",false);}
    String appListSignature(){return showWatchTv()+":"+appearance("dock-limit")+":"+hideHomeAppsOnSecondScreen();}
    boolean showWatchTv(){return preferences.getBoolean("show-watch-tv",false);}
    static final String[] CONTROL_VISIBILITY={"always","focus"};
    String quickControlVisibility(){return "focus".equals(preferences.getString("quick-control-visibility","always"))?"focus":"always";}
    boolean controlFocusOnly(){return "focus".equals(quickControlVisibility());}
    boolean showDockBackground(){return preferences.getBoolean("show-dock-background",true);}
    boolean showAllAppsNames(){return preferences.getBoolean("show-all-apps-names",true);}
    String iconSize(){String value=preferences.getString("icon-size","medium");return "small".equals(value)||"large".equals(value)?value:"medium";}
    String iconShape(){String value=preferences.getString("icon-shape","rectangle");return "square".equals(value)||"circle".equals(value)?value:"rectangle";}
    int appearance(String key){
        int fallback="dock-scale".equals(key)?100:"dock-limit".equals(key)?5:"dock-magnification".equals(key)?120:16;
        // Keep previously pinned apps visible when the former seven-app default was never saved.
        if("dock-limit".equals(key)&&!preferences.contains(key))fallback=defaultDockLimit(dock.getString("packages","[]"));
        return preferences.getInt(key,fallback);
    }
    static int defaultDockLimit(String saved){
        try{int count=new org.json.JSONArray(saved).length();return count<=5?5:count<=7?7:count<=10?10:12;}
        catch(org.json.JSONException error){return 5;} // DockStore reports malformed saved content when loading it.
    }
    boolean dockMagnify(){return preferences.getBoolean("dock-magnify",true);}
    boolean globalGlass(){return GlassStyle.read(preferences);}
    boolean dockGlass(){return globalGlass();}
    void saveAppearance(String key,Object value)throws IOException {if("dock-glass".equals(key))key=GlassStyle.KEY;synchronized(MUTATIONS){
        boolean valid=false;
        if("quick-control-visibility".equals(key))valid=java.util.Arrays.asList(CONTROL_VISIBILITY).contains(value);
        else if("icon-size".equals(key))valid=java.util.Arrays.asList("small","medium","large").contains(value);
        else if("clock-style".equals(key)){if(!galleryLayout())throw new IOException("时钟样式仅在艺术画廊布局中可设置");valid=validClockStyle(value);}
        else if("app-title-position".equals(key))valid=java.util.Arrays.asList("hidden","below","top-left").contains(value);
        else if("icon-shape".equals(key))valid=java.util.Arrays.asList("rectangle","square","circle").contains(value);
        else if("dock-magnify".equals(key)||GlassStyle.KEY.equals(key))valid=value instanceof Boolean;
        else if(value instanceof Integer){int n=(Integer)value;switch(key){case "dock-scale":valid=n>=70&&n<=130&&n%5==0;break;case "dock-magnification":valid=n>=100&&n<=160&&n%5==0;break;case "dock-limit":valid=n==5||n==7||n==10||n==12;break;}}
        if(!valid)throw new IOException("外观参数无效");
        saveValue(key,value);
    }}
    void savePhoneValue(String key,Object value)throws IOException {
        boolean valid=java.util.Arrays.asList(DailyQuote.HOME,DailyQuote.SAVER,TOP_SHELF,"key-sounds","show-time","show-date","show-seconds","show-lunar-date",MOVIE_INFO,"show-app-title","show-home-apps","show-watch-tv","hide-home-apps-on-second-screen","show-all-apps-names","show-dock-background","greeting-enabled","greeting-avatar-enabled").contains(key)&&value instanceof Boolean
            ||"home-wallpaper-brightness".equals(key)&&value instanceof Integer&&(Integer)value>=20&&(Integer)value<=100
            ||"greeting-text".equals(key)&&value instanceof String&&((String)value).length()<=GreetingInputServer.MAX_TEXT
            ||"home-layout".equals(key)&&java.util.Arrays.asList("classic","gallery").contains(value)
            ||"key-sound-theme".equals(key)&&KeySoundTheme.valid(value);
        if(!valid)throw new IOException("设置内容无效");
        synchronized(MUTATIONS){
            if(DailyQuote.HOME.equals(key)||MOVIE_INFO.equals(key))saveHomeContent(key,(Boolean)value);
            else if(DailyQuote.SAVER.equals(key)){
                WallpaperStore.get(app);
                ScreensaverStore saver=ScreensaverStore.get(app);
                synchronized(saver){
                    ScreensaverSettings current=saver.current();
                    if(current==null||current.digitalMode())throw new PhoneSettingsServer.Rejected("当前屏保内容无法设置每日格言");
                    saveValue(key,value);
                }
            }else saveValue(key,value);
        }
        if("key-sound-theme".equals(key)||"key-sounds".equals(key)&&Boolean.FALSE.equals(value))KeySounds.stop();
        else if("key-sounds".equals(key))KeySounds.prepare(app);
    }
    private void saveHomeContent(String key,boolean enabled)throws IOException{
        WallpaperStore wallpaper=WallpaperStore.get(app);
        synchronized(wallpaper){
            boolean movieWallpaper=wallpaper.dailyMovieSelected();
            boolean movie=MOVIE_INFO.equals(key);
            if(movie&&!movieWallpaper)throw new PhoneSettingsServer.Rejected("选择每日电影后可设置影片信息");
            if(movie&&showHomeQuote(movieWallpaper))throw new PhoneSettingsServer.Rejected("请先关闭每日格言");
            if(!movie&&showMovieInfo(movieWallpaper))throw new PhoneSettingsServer.Rejected("请先关闭影片信息");
            // An explicit choice clears the other flag, including older configurations with both flags on.
            String clear=movie?DailyQuote.HOME:enabled?MOVIE_INFO:null;
            saveValue(key,enabled,clear);
        }
    }
    private void saveValue(String key,Object value)throws IOException{saveValue(key,value,null);}
    private void saveValue(String key,Object value,String clear)throws IOException {
        Map<String,?> before=preferences.getAll();
        SharedPreferences.Editor edit=preferences.edit();
        put(edit,key,value);
        if(clear!=null)edit.putBoolean(clear,false);
        if(!edit.commit()){
            SharedPreferences.Editor rollback=preferences.edit();
            restore(rollback,before,key);
            if(clear!=null)restore(rollback,before,clear);
            rollback.commit();
            DiagnosticLog.info("desktop.configuration.result","result=FAILED reason=commit_failed");
            throw new IOException("保存失败，已保留当前外观");
        }
        DiagnosticLog.info("desktop.configuration.result","result=SAVED field="+key+" visualVerified=false");
    }
    private static void restore(SharedPreferences.Editor editor,Map<String,?> before,String key){
        Object old=before.get(key);
        if(old==null)editor.remove(key);else put(editor,key,old);
    }
    private static void put(SharedPreferences.Editor edit,String key,Object value){if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else edit.putInt(key,(Integer)value);}
    String homeBlurMode(){return "progressive".equals(preferences.getString("home-blur-mode","uniform"))?"progressive":"uniform";}
    void homeBlurMode(String mode)throws IOException{if(!"uniform".equals(mode)&&!"progressive".equals(mode))throw new IOException("模糊方式无效");synchronized(MUTATIONS){saveValue("home-blur-mode",mode);}}
    int homeWallpaperBlur(){return Math.max(0,Math.min(100,preferences.getInt("home-wallpaper-blur",0)));}
    int secondWallpaperBlur(){return Math.max(0,Math.min(100,preferences.getInt("second-wallpaper-blur",0)));}
    void homeWallpaperBlur(int value){synchronized(MUTATIONS){preferences.edit().putInt("home-wallpaper-blur",Math.max(0,Math.min(100,value))).apply();DiagnosticLog.info("wallpaper.blur.saved","page=home requested="+value+" readback="+homeWallpaperBlur()+" persistence=shared_preferences_memory_disk_async");}}
    void secondWallpaperBlur(int value){synchronized(MUTATIONS){preferences.edit().putInt("second-wallpaper-blur",Math.max(0,Math.min(100,value))).apply();DiagnosticLog.info("wallpaper.blur.saved","page=second requested="+value+" readback="+secondWallpaperBlur()+" persistence=shared_preferences_memory_disk_async");}}
    int homeWallpaperBrightness(){return Math.max(20,Math.min(100,preferences.getInt("home-wallpaper-brightness",100)));}
    int secondWallpaperBrightness(){return Math.max(20,Math.min(100,preferences.getInt("second-wallpaper-brightness",100)));}
    void homeWallpaperBrightness(int value){synchronized(MUTATIONS){preferences.edit().putInt("home-wallpaper-brightness",Math.max(20,Math.min(100,value))).apply();}}
    void secondWallpaperBrightness(int value){synchronized(MUTATIONS){preferences.edit().putInt("second-wallpaper-brightness",Math.max(20,Math.min(100,value))).apply();}}
    int homeWallpaperFade(){return Math.max(0,Math.min(1200,preferences.getInt("home-wallpaper-fade",600)))/100*100;}
    int secondWallpaperFade(){return Math.max(0,Math.min(1200,preferences.getInt("second-wallpaper-fade",600)))/100*100;}
    void homeWallpaperFade(int millis){synchronized(MUTATIONS){preferences.edit().putInt("home-wallpaper-fade",Math.max(0,Math.min(1200,millis))/100*100).apply();}}
    void secondWallpaperFade(int millis){synchronized(MUTATIONS){preferences.edit().putInt("second-wallpaper-fade",Math.max(0,Math.min(1200,millis))/100*100).apply();}}
    void saveWallpaperEffect(boolean second,String property,int value) throws IOException {saveWallpaperEffect(second,property,value,null);}
    void saveWallpaperEffect(boolean second,String property,int value,Integer expected) throws IOException {synchronized(MUTATIONS){
        if(!"blur".equals(property)&&!"brightness".equals(property)&&!"fade".equals(property))throw new IllegalArgumentException("Unknown wallpaper effect");
        int minimum="brightness".equals(property)?20:0,maximum="fade".equals(property)?1200:100;
        if(value<minimum||value>maximum||"fade".equals(property)&&value%100!=0)throw new IllegalArgumentException("Wallpaper effect outside range");
        String key=(second?"second":"home")+"-wallpaper-"+property;
        boolean existed=preferences.contains(key);int previous=preferences.getInt(key,"brightness".equals(property)?100:"fade".equals(property)?600:0);if(expected!=null&&expected!=previous)throw new IOException("背景参数已变化，请返回后重新调整");
        if(!preferences.edit().putInt(key,value).commit()){
            SharedPreferences.Editor rollback=preferences.edit();if(existed)rollback.putInt(key,previous);else rollback.remove(key);rollback.commit();
            throw new IOException("保存失败，请检查电视存储空间后重试 / Unable to save settings");
        }
    }}
    boolean showButtons(){return preferences.getBoolean("show-buttons",true);}
    boolean showInput(){return preferences.getBoolean("show-input",showButtons());}
    boolean showNetwork(){return preferences.getBoolean("show-network",showButtons());}
    boolean showSettings(){return preferences.getBoolean("show-settings",showButtons());}
    void showInput(boolean show){preferences.edit().putBoolean("show-input",show).apply();DiagnosticLog.info("desktop.setting","show-input="+show);}
    void showNetwork(boolean show){preferences.edit().putBoolean("show-network",show).apply();DiagnosticLog.info("desktop.setting","show-network="+show);}
    void showSettings(boolean show){preferences.edit().putBoolean("show-settings",show).apply();DiagnosticLog.info("desktop.setting","show-settings="+show);}
    void showTime(boolean show){synchronized(MUTATIONS){preferences.edit().putBoolean("show-time",show).apply();DiagnosticLog.info("desktop.setting","show-time="+show);}}
    void showHomeApps(boolean show){synchronized(MUTATIONS){preferences.edit().putBoolean("show-home-apps",show).apply();DiagnosticLog.info("desktop.setting","show-home-apps="+show);}}
    void showWatchTv(boolean show)throws IOException{savePhoneValue("show-watch-tv",show);}
    void showDockBackground(boolean show){synchronized(MUTATIONS){preferences.edit().putBoolean("show-dock-background",show).apply();DiagnosticLog.info("desktop.setting","show-dock-background="+show);}}
    void showAllAppsNames(boolean show){synchronized(MUTATIONS){preferences.edit().putBoolean("show-all-apps-names",show).apply();DiagnosticLog.info("desktop.setting","show-all-apps-names="+show);}}
    void showButtons(boolean show){preferences.edit().putBoolean("show-buttons",show).apply();DiagnosticLog.info("desktop.setting","show-buttons="+show);}
}
