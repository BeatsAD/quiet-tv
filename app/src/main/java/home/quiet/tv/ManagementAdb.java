package home.quiet.tv;

import android.content.Context;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import java.util.concurrent.*;

/** One local TCP management connection per process, independent of pages and TV brand. */
final class ManagementAdb {
    interface Listener {void changed();}
    private static ManagementAdb instance;
    static synchronized ManagementAdb get(Context context){if(instance==null)instance=new ManagementAdb(context.getApplicationContext());return instance;}
    static synchronized ManagementAdb existing(){return instance;}
    final AdbController adb;
    final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final Runnable dispatch=()->{for(Listener listener:listeners)listener.changed();};
    private final Runnable authorizationTick=new Runnable(){public void run(){if(!waitingAuthorization()||listeners.isEmpty())return;dispatch.run();main.postDelayed(this,1000);}};
    private final AdbConnectionKeeper keeper;
    private final Context context;
    private final android.content.SharedPreferences preferences;
    private ManagementAdb(Context context){
        this.context=context;preferences=context.getSharedPreferences("adb",0);
        adb=new AdbController(context,value->progressChanged());
        keeper=new AdbConnectionKeeper(new AdbConnectionKeeper.Client(){
            public boolean available(){
                if(!HomeGuardState.unlocked(context))return false;
                try{return (Build.VERSION.SDK_INT>=17?Api17.enabled(context):Settings.Secure.getInt(context.getContentResolver(),"adb_enabled",-1))!=0;}
                catch(SecurityException unknown){return true;} // The verified protocol remains authoritative.
            }
            public boolean connected(){return adb.connected();}
            public int prepare(){return adb.prepare();}
            public void connect(int attempt,boolean requestAuthorization) throws Exception {adb.connect(context.getSharedPreferences("adb",0).getInt("port",5555),attempt,requestAuthorization);}
            public void check() throws Exception {adb.validateUser();}
            public void disconnect(){adb.disconnect();}
            public boolean retryable(){return adb.failure()==AdbController.Failure.PORT || adb.failure()==AdbController.Failure.NONE;}
        },worker,()->{main.removeCallbacks(dispatch);main.post(dispatch);});
        if(preferences.getBoolean("managementPaused",false))keeper.pause();
        ContentObserver observer=new ContentObserver(main){@Override public void onChange(boolean selfChange){request();}};
        try{context.getContentResolver().registerContentObserver(Build.VERSION.SDK_INT>=17?Api17.uri():Settings.Secure.getUriFor("adb_enabled"),false,observer);}
        catch(SecurityException unavailable){/* Startup, resume and wake still verify the actual connection. */}
    }
    @android.annotation.TargetApi(17)
    private static final class Api17 {
        static int enabled(Context context){return Settings.Global.getInt(context.getContentResolver(),"adb_enabled",-1);}
        static int value(Context context){return Settings.Global.getInt(context.getContentResolver(),"adb_enabled",-1)==1||Settings.Global.getInt(context.getContentResolver(),"adb_wifi_enabled",-1)==1?1:-1;}
        static android.net.Uri uri(){return Settings.Global.getUriFor("adb_enabled");}
    }
    void request(){keeper.request();}
    void requestAuthorization(){preferences.edit().putBoolean("managementPaused",false).apply();keeper.requestAuthorization();}
    void pause(){preferences.edit().putBoolean("managementPaused",true).apply();keeper.pause();}
    void check(){keeper.checkConnected();main.removeCallbacks(dispatch);main.post(dispatch);}
    void disconnect(){keeper.disconnect();}
    AdbConnectionKeeper.State state(){return keeper.state();}
    String detail(){return keeper.detail();}
    boolean paused(){return keeper.paused();}
    private void progressChanged(){main.removeCallbacks(dispatch);main.post(dispatch);main.removeCallbacks(authorizationTick);if(waitingAuthorization()&&!listeners.isEmpty())main.postDelayed(authorizationTick,1000);}
    boolean waitingAuthorization(){return adb!=null&&state()==AdbConnectionKeeper.State.CONNECTING&&adb.waitingAuthorization();}
    int authorizationSeconds(){return waitingAuthorization()?adb.authorizationSeconds():0;}
    boolean timedOut(){return !paused()&&state()==AdbConnectionKeeper.State.DISCONNECTED&&adb.connectionTimedOut();}
    String tone(){
        if(state()==AdbConnectionKeeper.State.CONNECTED)return "success";
        if(waitingAuthorization())return "warning";
        if(state()==AdbConnectionKeeper.State.CONNECTING)return "progress";
        return !paused()&&(timedOut()||adb.failure()!=AdbController.Failure.NONE)?"error":"neutral";
    }
    String label(){
        if(state()==AdbConnectionKeeper.State.CONNECTED)return "已连接";
        if(state()==AdbConnectionKeeper.State.CONNECTING){if(waitingAuthorization())return "等待电视授权";switch(adb.stage()){case HANDSHAKE:return "正在核对授权";case LOCAL:case SHELL:case USER:case CAPABILITIES:return "正在验证连接";default:return "连接中…";}}
        return timedOut()?"连接超时":!paused()&&adb.failure()!=AdbController.Failure.NONE?"连接失败":"未连接";
    }
    String description(){
        if(state()==AdbConnectionKeeper.State.CONNECTED)return "Quiet TV 已连接当前电视。";
        if(waitingAuthorization())return "请在电视的系统调试授权窗口选择允许。电脑已获授权，不代表 Quiet TV 已获授权。";
        if(state()==AdbConnectionKeeper.State.CONNECTING)return adb.stage()==AdbController.Stage.HANDSHAKE?"正在核对 Quiet TV 已有的本机 ADB 授权。":"正在连接并核对当前电视与用户。";
        if(paused())return "Quiet TV 尚未连接当前电视，自动重连已暂停。";
        if(timedOut()&&adb.failure()==AdbController.Failure.HANDSHAKE)return "ADB 握手或授权超时（60 秒）。请在电视处理已有的系统授权窗口后重试；窗口未出现时，可通过已授权电脑检查挂起请求。";
        String value=detail();return value.isEmpty()||value.equals("已核对本机与当前用户")?"Quiet TV 尚未连接当前电视。":value;
    }
    int port(){int actual=adb.connectedPort();return actual>0?actual:preferences.getInt("port",5555);}
    String debugging(){
        if(state()==AdbConnectionKeeper.State.CONNECTED)return "已开启";
        try{
            int enabled=Build.VERSION.SDK_INT>=17?Api17.value(context):Settings.Secure.getInt(context.getContentResolver(),"adb_enabled",-1);
            return enabled==1?"已开启":"待确认";
        }catch(SecurityException unavailable){return "待确认";}
    }
    void addListener(Listener listener){listeners.addIfAbsent(listener);if(waitingAuthorization())progressChanged();}
    void removeListener(Listener listener){listeners.remove(listener);if(listeners.isEmpty())main.removeCallbacks(authorizationTick);}
}
