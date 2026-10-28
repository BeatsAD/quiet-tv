package home.quiet.tv;

import android.content.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in setting recovery, with one bounded boot probe. Never pairs or opens an ADB session. */
final class HomeGuardAdb {
    private static final ExecutorService worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private static final AtomicBoolean pending=new AtomicBoolean(),again=new AtomicBoolean();
    private static boolean watching,probed;
    static boolean supported(Context c){return HomeGuardPolicy.supported(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    static boolean enabled(Context c){return new HomeGuardState(c,false).preferences.getBoolean("keepAdb",false);}
    static boolean portSupported(Context c){return HomeGuardPolicy.keepAdbPort(HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19),Build.VERSION.SDK_INT,1);}
    static int port(Context c){return c.getSharedPreferences("adb",0).getInt("port",0);}
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean setPort(Context c,int port){return HomeGuardPolicy.keepAdbPort(portSupported(c),Build.VERSION.SDK_INT,port)
        && c.getSharedPreferences("adb",0).edit().putInt("port",port).commit();}
    // The opt-in/out must survive a firmware kill immediately after the click.
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean setEnabled(Context c,boolean enabled){boolean saved=new HomeGuardState(c).preferences.edit().putBoolean("keepAdb",enabled).commit();if(saved){HomeGuardSync.refresh(c);if(enabled){watch(c);ensure(c);}}return saved;}
    static void watch(Context c){
        if(Build.VERSION.SDK_INT<21 || !supported(c) || watching)return;
        Context app=c.getApplicationContext();watching=true;
        app.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),false,new ContentObserver(new Handler(Looper.getMainLooper())){
            @Override public void onChange(boolean self){ensure(app);}
        });ensure(app);
    }
    static boolean ready(Context c){return HomeGuardPolicy.keepAdbReady(supported(c),enabled(c),HomeGuardRecovery.writeAllowed(c));}
    static Boolean settingEnabled(Context c){if(Build.VERSION.SDK_INT<21)return null;try{return Settings.Global.getInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,0)==1;}catch(SecurityException unavailable){return null;}}
    static org.json.JSONObject snapshot(Context c) throws org.json.JSONException {
        Boolean actual=settingEnabled(c);
        return new org.json.JSONObject().put("supported",supported(c)).put("selected",enabled(c)).put("ready",ready(c))
            .put("permissionGranted",HomeGuardRecovery.writeAllowed(c))
            .put("settingEnabled",actual==null?org.json.JSONObject.NULL:actual).put("rebootVerified",false);
    }
    static String summary(Context c){return !enabled(c)?"未开启":!supported(c)?"当前档位不支持":!HomeGuardRecovery.writeAllowed(c)?"缺少写安全设置权限":Boolean.TRUE.equals(settingEnabled(c))?"调试开关已开启，重启后连接待验证":"调试开关恢复未确认";}
    // Run the bounded settings write before a boot broadcast or setup reply completes.
    // The preference is device-protected; HOME pause and user unlock do not gate ADB recovery.
    static boolean restoreSetting(Context c){
        if(Build.VERSION.SDK_INT<21 || !ready(c))return false;
        try{
            if(Boolean.TRUE.equals(settingEnabled(c)))return true;
            boolean written=Settings.Global.putInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,1);
            boolean confirmed=Boolean.TRUE.equals(settingEnabled(c));
            DiagnosticLog.info("home_guard.adb.restore","written="+written+" readback="+confirmed);
            return written && confirmed;
        }catch(SecurityException|IllegalStateException failure){DiagnosticLog.error("home_guard.adb.restore_failed",failure);return false;}
    }
    static void ensure(Context c){
        if(Build.VERSION.SDK_INT<21 || !ready(c))return;
        again.set(true);if(!pending.compareAndSet(false,true))return;
        Context app=c.getApplicationContext();worker.execute(()->{again.set(false);try{restore(app);}finally{pending.set(false);if(again.getAndSet(false))ensure(app);}});
    }
    private static void restore(Context c){
        if(Build.VERSION.SDK_INT<21 || !ready(c))return;
        try{
            if(!restoreSetting(c))return;
            // An observed/configured TCP port is required; USB-only and modern wireless ADB stay alone.
            if(!portSupported(c) || !HomeGuardState.unlocked(c))return;
            int port=port(c);
            if(probed || Build.VERSION.SDK_INT>=30 || SystemClock.elapsedRealtime()>=HomeGuardPolicy.BOOT_WINDOW_MS || port<=0 || port>65535)return;
            probed=true;
            try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),400);return;}
            catch(java.io.IOException closed){/* Configured port did not answer. */}
            if(Build.VERSION.SDK_INT<21 || !ready(c))return;
            if(!Settings.Global.putInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,0))return;
            try{Thread.sleep(1000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            finally{Settings.Global.putInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,1);}
            DiagnosticLog.info("home_guard.adb.restart","attempted=true once_per_process=true");
        }catch(SecurityException failure){DiagnosticLog.error("home_guard.adb.restore_failed",failure);}
    }
}
