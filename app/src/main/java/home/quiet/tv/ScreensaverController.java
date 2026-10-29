package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
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
    private final LinearLayout information;
    private final TextView clock,date,weather;
    private final boolean chinese;
    private final Map<Integer,Long> wakeKeys=new HashMap<>();
    private State state=State.OFF;
    private boolean eligible,screenOff,wakeTouch,destroyed;
    private int generation;
    private ScreensaverSettings settings;
    private WeatherData weatherData;
    private final Runnable idle=this::enter;
    private final Runnable tick=new Runnable(){public void run(){if(state==State.ACTIVE){render();main.postDelayed(this,60000-System.currentTimeMillis()%60000);}}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        screenOff=Intent.ACTION_SCREEN_OFF.equals(i.getAction());
        if(screenOff)pause();else{if(host.allowed())wallpapers.resume();availabilityChanged();}
    }};
    ScreensaverController(Activity activity,FrameLayout root,WallpaperController wallpapers,float unit,boolean chinese,Host host){
        this.activity=activity;this.wallpapers=wallpapers;this.host=host;this.chinese=chinese;media=WallpaperStore.get(activity);store=ScreensaverStore.get(activity);
        WallpaperUi ui=new WallpaperUi(activity,unit);
        information=new LinearLayout(activity);information.setOrientation(LinearLayout.VERTICAL);information.setVisibility(View.GONE);information.setFocusable(false);
        clock=ui.text("",80);clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        date=ui.text("",14);weather=ui.text("",18);
        for(TextView view:new TextView[]{clock,date,weather}){view.setSingleLine(true);view.setTextColor(Color.WHITE);view.setShadowLayer(ui.px(12),0,ui.px(1),0x66000000);information.addView(view,new LinearLayout.LayoutParams(-2,-2));}
        ((LinearLayout.LayoutParams)date.getLayoutParams()).topMargin=ui.px(8);((LinearLayout.LayoutParams)weather.getLayoutParams()).topMargin=ui.px(20);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);p.leftMargin=ui.px(67);p.topMargin=ui.px(65);root.addView(information,p);
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
        settings=read();if(settings==null){Toast.makeText(activity,chinese?"屏保配置无法读取":"Unable to read screensaver settings",Toast.LENGTH_SHORT).show();return;}
        host.captureFocus();state=State.ENTERING;int version=++generation;host.weatherDemand(settings.showWeather);
        wallpapers.showTemporary(settings.mediaId,()->{
            if(version!=generation||state!=State.ENTERING)return;
            state=State.ACTIVE;host.desktopVisible(false);information.setVisibility(View.VISIBLE);render();main.postDelayed(tick,60000-System.currentTimeMillis()%60000);
        });
    }
    private void exit(){
        if(state==State.OFF||state==State.EXITING)return;
        state=State.EXITING;int version=++generation;main.removeCallbacks(tick);host.weatherDemand(false);
        wallpapers.restoreDesktop(()->{
            if(version!=generation||state!=State.EXITING)return;
            information.setVisibility(View.GONE);host.desktopVisible(true);state=State.OFF;host.restoreFocus();interaction();
        });
    }
    void pause(){
        eligible=false;main.removeCallbacks(idle);main.removeCallbacks(tick);++generation;
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
    void weatherChanged(WeatherData value){weatherData=value;if(state==State.ACTIVE)render();}
    void timeChanged(){if(state==State.ACTIVE)render();}
    private void render(){
        if(settings==null)return;
        Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",Locale.US).format(now));date.setText(new SimpleDateFormat(chinese?"M月d日  EEEE":"EEEE, MMMM d",chinese?Locale.SIMPLIFIED_CHINESE:Locale.US).format(now));
        clock.setVisibility(settings.showTime?View.VISIBLE:View.GONE);date.setVisibility(settings.showTime?View.VISIBLE:View.GONE);
        boolean visible=settings.showWeather&&weatherData!=null&&weatherData.usable(System.currentTimeMillis());weather.setVisibility(visible?View.VISIBLE:View.GONE);
        if(visible)weather.setText(String.format(Locale.ROOT,"%s  %s  %s",weatherData.temperatureText(),weatherData.description,weatherData.city));
    }
    void destroy(){pause();destroyed=true;activity.unregisterReceiver(screen);main.removeCallbacksAndMessages(null);}
}
