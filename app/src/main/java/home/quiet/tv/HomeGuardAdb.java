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
    static boolean supported(Context c){return HomeGuardPolicy.xiaomiRecovery(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    static boolean enabled(Context c){return new HomeGuardState(c,false).preferences.getBoolean("keepAdb",false);}
    static boolean portSupported(Context c){return HomeGuardPolicy.keepAdbPort(supported(c),Build.VERSION.SDK_INT,1);}
    static int port(Context c){return c.getSharedPreferences("adb",0).getInt("port",0);}
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean setPort(Context c,int port){return HomeGuardPolicy.keepAdbPort(supported(c),Build.VERSION.SDK_INT,port)
        && c.getSharedPreferences("adb",0).edit().putInt("port",port).commit();}
    // The opt-in/out must survive a firmware kill immediately after the click.
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean setEnabled(Context c,boolean enabled){boolean saved=new HomeGuardState(c).preferences.edit().putBoolean("keepAdb",enabled).commit();if(saved){HomeGuardSync.refresh(c);if(enabled){watch(c);ensure(c);}}return saved;}
    static void watch(Context c){
        if(!supported(c) || watching)return;
        Context app=c.getApplicationContext();watching=true;
        app.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),false,new ContentObserver(new Handler(Looper.getMainLooper())){
            @Override public void onChange(boolean self){ensure(app);}
        });ensure(app);
    }
    static boolean ready(Context c){return HomeGuardPolicy.keepAdbReady(supported(c),HomeGuardState.unlocked(c),enabled(c),new HomeGuardState(c,false).automatic(),HomeGuardRecovery.writeAllowed(c));}
    static String summary(Context c){return !enabled(c)?"未开启":!new HomeGuardState(c,false).automatic()?"已暂停":!HomeGuardRecovery.writeAllowed(c)?"缺少写安全设置权限":ready(c)?"调试设置恢复已启用，端口持续可用性待验证":"当前不可用";}
    static void ensure(Context c){
        if(!ready(c))return;
        again.set(true);if(!pending.compareAndSet(false,true))return;
        Context app=c.getApplicationContext();worker.execute(()->{again.set(false);try{restore(app);}finally{pending.set(false);if(again.getAndSet(false))ensure(app);}});
    }
    private static void restore(Context c){
        if(!ready(c))return;
        try{
            if(Settings.Global.getInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,0)==0){
                boolean restored=Settings.Global.putInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,1);
                DiagnosticLog.info("home_guard.adb.restore","restored="+restored);return;
            }
            // An observed/configured TCP port is required; USB-only and modern wireless ADB stay alone.
            int port=port(c);
            if(probed || Build.VERSION.SDK_INT>=30 || SystemClock.elapsedRealtime()>=HomeGuardPolicy.BOOT_WINDOW_MS || port<=0 || port>65535)return;
            probed=true;
            try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",port),400);return;}
            catch(java.io.IOException closed){/* Configured port did not answer. */}
            if(!ready(c))return;
            if(!Settings.Global.putInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,0))return;
            try{Thread.sleep(1000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            finally{Settings.Global.putInt(c.getContentResolver(),Settings.Global.ADB_ENABLED,1);}
            DiagnosticLog.info("home_guard.adb.restart","attempted=true once_per_process=true");
        }catch(SecurityException failure){DiagnosticLog.error("home_guard.adb.restore_failed",failure);}
    }
}
