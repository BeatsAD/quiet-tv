package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** In-desktop screensaver; the wallpaper controller remains the sole owner of playback. */
final class ScreensaverController {
    interface Host {
        boolean allowed();
        void captureFocus();
        void restoreFocus();
        void desktopVisible(boolean visible);
        void weatherDemand(boolean enabled);
    }
    private enum State { OFF,ENTERING,ACTIVE,EXITING }
    private final Activity activity;
    private final WallpaperController wallpapers;
    private final WallpaperStore media;
    private final ScreensaverStore store;
    private final Host host;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScreensaverInformationView information;
    private final boolean chinese;
    private final Map<Integer,Long> wakeKeys=new HashMap<>();
    private State state=State.OFF;
    private boolean eligible,screenOff,wakeTouch,destroyed;
    private int generation;
    private ScreensaverSettings settings;
    private String mediaKey="";
    private WeatherData weatherData;
    private FlipClockSound flipSound;
    private final Runnable idle=this::enter;
    private long renderedMinute=-1;
    private final Runnable tick=this::tick;
    private void tick(){
        if(state!=State.ACTIVE)return;
        long now=System.currentTimeMillis();
        if(now/60000!=renderedMinute)render(now);
        else information.updateTime(now);
        scheduleTick(now);
    }
    private void scheduleTick(long now){
        main.removeCallbacks(tick);
        long period=settings.tickMillis();
        main.postDelayed(tick,period-now%period);
    }
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());
        if(screenOff)pause();else{if(host.allowed())wallpapers.resume();availabilityChanged();}
    }};
    ScreensaverController(Activity activity,FrameLayout root,WallpaperController wallpapers,float unit,boolean chinese,Host host){
        this.activity=activity;this.wallpapers=wallpapers;this.host=host;this.chinese=chinese;media=WallpaperStore.get(activity);store=ScreensaverStore.get(activity);
        information=new ScreensaverInformationView(activity,96*unit,19.2f*unit);information.setVisibility(View.GONE);
        information.onLanded(()->{if(state==State.ACTIVE&&settings!=null&&settings.flipping()&&settings.flipSound&&allowed()&&flipSound!=null)flipSound.play();});
        root.addView(information,new FrameLayout.LayoutParams(-1,-1));
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);activity.registerReceiver(screen,filter);
    }
    private boolean allowed(){PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);return !destroyed&&!screenOff&&host.allowed()&&power!=null&&(Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn());}
    private ScreensaverSettings read(){
        try{return store.snapshot(media.snapshot());}
        catch(IOException e){DiagnosticLog.error("screensaver.load.failed",e);return null;}
    }
    void availabilityChanged(){
        boolean next=allowed();if(next==eligible)return;eligible=next;
        main.removeCallbacks(idle);if(!next){if(state!=State.OFF)exit();}else interaction();
    }
    void interaction(){
        main.removeCallbacks(idle);if(state!=State.OFF||!allowed())return;
        ScreensaverSettings current=store.current();if(current!=null&&current.idleMinutes>0)main.postDelayed(idle,current.idleMinutes*60000L);
    }
    void enter(){
        main.removeCallbacks(idle);if(state!=State.OFF||!allowed())return;
        settings=read();if(settings==null){OperationFeedback.get(activity).show(OperationFeedback.Kind.ERROR,chinese?"屏保配置无法读取":"Unable to read screensaver settings");return;}
        host.captureFocus();present();
    }
    private String mediaKey(ScreensaverSettings value,WallpaperCatalog catalog){return value.mode+":"+value.customBlank(catalog)+":"+media.imageKey(value.resolved(catalog))+":"+catalog.weather+":"+catalog.weatherDayNight;}
    private void updateFlipSound(){if(settings!=null&&settings.flipping()&&settings.flipSound&&(state==State.ENTERING||state==State.ACTIVE)){if(flipSound==null)try{flipSound=new FlipClockSound(activity);}catch(RuntimeException error){DiagnosticLog.error("screensaver.flip_sound.unavailable",error);}}else if(flipSound!=null)flipSound.stop();}
    private void present(){
        information.active(false);state=State.ENTERING;int version=++generation;main.removeCallbacks(tick);host.weatherDemand(settings.showWeather);if(flipSound!=null)flipSound.stop();updateFlipSound();
        WallpaperCatalog catalog=media.snapshot();mediaKey=mediaKey(settings,catalog);Runnable prepared=()->{
            if(version!=generation||state!=State.ENTERING)return;
            DiagnosticLog.info("screensaver.transition","state=active evidence=background_callback");state=State.ACTIVE;host.desktopVisible(false);information.setVisibility(View.VISIBLE);information.active(true);refreshClock();
        };
        if(settings.customBlank(catalog))wallpapers.showTemporaryBlank(prepared);
        else wallpapers.showTemporary(settings.resolved(catalog),ScreensaverSettings.FOLLOW.equals(settings.mode)&&catalog.weather&&WallpaperGpuSupport.available(activity),catalog.weatherDayNight,settings.sound&&settings.video(catalog),prepared);
    }
    void settingsChanged(){
        ScreensaverSettings next=read();if(next==null)return;boolean changed=settings!=next;settings=next;updateFlipSound();
        if(state==State.OFF){if(changed)interaction();return;}if(state==State.EXITING)return;
        if(!mediaKey.equals(mediaKey(settings,media.snapshot())))present();else{wallpapers.temporarySound(settings.sound&&settings.video(media.snapshot()));host.weatherDemand(settings.showWeather);if(state==State.ACTIVE){if(changed)refreshClock();else render(System.currentTimeMillis());}}
    }
    private void exit(){
        if(state==State.OFF||state==State.EXITING)return;
        DiagnosticLog.info("screensaver.transition","state=exiting");information.active(false);if(flipSound!=null)flipSound.stop();state=State.EXITING;int version=++generation;main.removeCallbacks(tick);host.weatherDemand(false);
        wallpapers.restoreDesktop(()->{
            if(version!=generation||state!=State.EXITING)return;
            information.setVisibility(View.GONE);host.desktopVisible(true);state=State.OFF;DiagnosticLog.info("screensaver.transition","state=off evidence=background_callback");host.restoreFocus();interaction();
        });
    }
    void pause(){
        information.active(false);if(flipSound!=null)flipSound.stop();eligible=false;main.removeCallbacks(idle);main.removeCallbacks(tick);++generation;
        if(state!=State.OFF){state=State.OFF;information.setVisibility(View.GONE);host.desktopVisible(true);host.weatherDemand(false);wallpapers.cancelTemporary();host.restoreFocus();}
        // Retain held wake gestures across focus/lifecycle transitions until their releases arrive.
    }
    static boolean systemKey(int key){return key==KeyEvent.KEYCODE_HOME||key==KeyEvent.KEYCODE_POWER||key==KeyEvent.KEYCODE_TV_POWER||key==KeyEvent.KEYCODE_STB_POWER||key==KeyEvent.KEYCODE_AVR_POWER||key==KeyEvent.KEYCODE_SLEEP||key==KeyEvent.KEYCODE_WAKEUP||key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE||key==KeyEvent.KEYCODE_MUTE;}
    boolean key(KeyEvent event){
        int key=event.getKeyCode();if(systemKey(key)){interaction();return false;}
        Long held=wakeKeys.get(key);
        if(held!=null&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&event.getDownTime()!=held){wakeKeys.remove(key);held=null;}
        if(state==State.OFF&&held==null)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){wakeKeys.put(key,event.getDownTime());exit();}
        else if(event.getAction()==KeyEvent.ACTION_UP)wakeKeys.remove(key);
        return true;
    }
    boolean touch(MotionEvent event){
        if(state==State.OFF&&!wakeTouch)return false;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){wakeTouch=true;exit();}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)wakeTouch=false;
        return true;
    }
    boolean pointer(MotionEvent event){
        if(state!=State.OFF&&event.getActionMasked()==MotionEvent.ACTION_SCROLL){exit();return true;}return false;
    }
    boolean showing(){return state!=State.OFF;}
    void weatherChanged(WeatherData value){weatherData=value;if(state==State.ACTIVE)render(System.currentTimeMillis());}
    void timeChanged(){
        information.resetTime();
        if(flipSound!=null)flipSound.stop();
        refreshClock();
    }
    private void refreshClock(){
        if(state!=State.ACTIVE)return;
        long now=System.currentTimeMillis();
        render(now);
        scheduleTick(now);
    }
    private void render(long now){
        if(settings==null)return;
        renderedMinute=now/60000;
        boolean clockOnly=settings.clockOnly();information.setBackgroundColor(clockOnly?0xff000000:Color.TRANSPARENT);
        boolean visible=weatherData!=null&&weatherData.usable(now);
        information.show(settings,now,visible?String.format(Locale.ROOT,"%s  %s  %s",weatherData.city,weatherData.temperatureText(),weatherData.description):"");
    }
    void destroy(){pause();destroyed=true;if(flipSound!=null)flipSound.destroy();activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);}
}
