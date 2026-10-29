package home.quiet.tv;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityEvent;

/** Observes package/window identity only, and times an app explicitly launched from Quiet TV. */
public final class ChildUsageService extends AccessibilityService {
    private static volatile ChildUsageService instance;
    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners=new java.util.concurrent.CopyOnWriteArrayList<>();
    static void listen(Runnable listener){listeners.addIfAbsent(listener);}
    static void unlisten(Runnable listener){listeners.remove(listener);}
    static void changed(){for(Runnable listener:listeners)listener.run();}
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ChildModeStore store;
    private String target="",foreground="",blocking="";
    private long last,pending,lastBlock;
    private boolean registered;
    private final Runnable checkBlock=()->{
        if(blocking.isEmpty())return;
        store.fault("系统未确认到时限制页，请检查儿童模式服务");blocking="";
    };
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(target.isEmpty() || !target.equals(foreground))return;
        if(!interactive()){last=0;flush();return;}
        long now=SystemClock.elapsedRealtime();pending+=Math.max(0,now-last);last=now;
        try{
            ChildModePolicy.Rule r=store.rule(target);
            if(r.locked || !r.limited()){stopSession();return;}
            if(pending>=r.remaining()){flush();block();return;}
            if(pending>=5000 && !flush())return;
            handler.postDelayed(this,1000);
        }catch(RuntimeException e){store.fault("计时数据无法读取，请检查儿童模式设置");stopSession();}
    }};
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))last=0;
        pause();
        if(!Intent.ACTION_SCREEN_OFF.equals(i.getAction()) && !target.isEmpty() && target.equals(foreground) && interactive()){
            last=SystemClock.elapsedRealtime();handler.postDelayed(tick,1000);
        }
    }};
    @Override protected void onServiceConnected(){
        super.onServiceConnected();store=new ChildModeStore(this);instance=this;DiagnosticLog.info("child.timer.service","state=connected");
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(!registered){if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);registered=true;}
        // Clear stale failure only after a fresh explicit service connection.
        store.fault("");
    }
    static Boolean allowed(Context context){
        try{
            String enabled=android.provider.Settings.Secure.getString(context.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if(enabled==null)return null;
            ComponentName own=new ComponentName(context,ChildUsageService.class);
            for(String item:enabled.split(":"))if(own.equals(ComponentName.unflattenFromString(item)))return true;
            return false;
        }catch(SecurityException unavailable){return null;}
    }
    static ChildTimerState status(Context context){return ChildTimerState.read(allowed(context),connected(),new ChildModeStore(context).fault());}
    static boolean connected(){return instance!=null;}
    static boolean ready(Context context){return connected() && new ChildModeStore(context).fault().isEmpty();}
    static boolean watch(String pkg){
        ChildUsageService service=instance;if(service==null)return false;
        service.stopSession();service.target=pkg;service.foreground="";return true;
    }
    static void cancel(){ChildUsageService service=instance;if(service!=null)service.stopSession();}
    static void blockVisible(String pkg){ChildUsageService service=instance;if(service!=null && service.blocking.equals(pkg)){service.handler.removeCallbacks(service.checkBlock);service.blocking="";}}
    private boolean interactive(){
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        return power!=null && (Build.VERSION.SDK_INT>=20?power.isInteractive():power.isScreenOn()) && (keyguard==null || !UiCompat.keyguardLocked(keyguard));
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(store==null || event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null || target.isEmpty())return;
        long age=SystemClock.uptimeMillis()-event.getEventTime();if(age<0 || age>3000)return;
        String pkg=event.getPackageName().toString();
        if(pkg.equals(foreground))return;
        pause();foreground=pkg;
        if(!pkg.equals(target) || !interactive())return;
        try{if(store.rule(target).remaining()<=0){block();return;}}
        catch(RuntimeException e){store.fault("计时数据无法读取");return;}
        last=SystemClock.elapsedRealtime();handler.postDelayed(tick,1000);
    }
    private boolean flush(){
        if(pending==0 || target.isEmpty())return true;
        long delta=pending;pending=0;
        if(store.consume(target,delta))return true;
        store.fault("使用时长保存失败，请检查存储空间");handler.removeCallbacks(tick);return false;
    }
    private void pause(){
        handler.removeCallbacks(tick);
        if(!target.isEmpty() && target.equals(foreground) && last>0 && interactive())pending+=Math.max(0,SystemClock.elapsedRealtime()-last);
        last=0;if(store!=null)flush();
    }
    private void stopSession(){pause();target="";foreground="";pending=0;}
    private void block(){
        handler.removeCallbacks(tick);long now=SystemClock.elapsedRealtime();if(now-lastBlock<1500)return;lastBlock=now;
        blocking=target;
        try{startActivity(ChildBlockActivity.intent(this,target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));handler.removeCallbacks(checkBlock);handler.postDelayed(checkBlock,2000);}
        catch(SecurityException|android.content.ActivityNotFoundException e){store.fault("系统不允许显示到时限制页");blocking="";}
    }
    @Override public void onInterrupt(){DiagnosticLog.info("child.timer.service","state=interrupted");pause();foreground="";if(store!=null)store.fault("计时服务已中断，请重新启用");}
    @Override public void onDestroy(){DiagnosticLog.info("child.timer.service","state=destroyed");stopSession();handler.removeCallbacksAndMessages(null);if(registered)unregisterReceiver(screen);if(instance==this)instance=null;changed();super.onDestroy();}
    @Override public boolean onUnbind(Intent intent){DiagnosticLog.info("child.timer.service","state=unbound");stopSession();if(instance==this)instance=null;changed();return super.onUnbind(intent);}
}
