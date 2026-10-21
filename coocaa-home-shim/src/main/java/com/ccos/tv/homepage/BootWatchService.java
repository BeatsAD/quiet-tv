package com.ccos.tv.homepage;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Coocaa's boot flow brings its own home up through the TV app and the boot ad, well after
 * BOOT_COMPLETED and without asking com.tianci.system. For a while after boot, whenever that home
 * is in front, hand over to the launcher. Stops as soon as anything else is opened, so it never
 * takes the screen from an app the user chose. Each run is written to
 * /sdcard/Android/data/com.ccos.tv.homepage/files/watch.log, since logd restarts late in boot here.
 */
public class BootWatchService extends Service {

    static final String STOCK_HOME = "com.tianci.movieplatform";
    /** Same applicationId as the paired Quiet TV build; com.coocaa.* is an opt-in device workaround. */
    static final String LAUNCHER = BuildConfig.LAUNCHER_PACKAGE;
    /** The boot flow passes through the TV app on its way to the stock home. */
    private static final String BOOT_TV_APP = "com.skyworth.tv";
    private static final Uri SYSTEM_PROVIDER = Uri.parse("content://com.tianci.provider");
    private static final long POLL_MS = 2_000L;
    private static final long WATCH_MS = 120_000L;
    /** Lets a finishing LaunchActivity or a stopping service wind down before the process goes. */
    private static final long EXIT_DELAY_MS = 500L;

    static boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long until;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            String front = foregroundPackage();
            if (front != null && !front.equals(STOCK_HOME) && !front.equals(LAUNCHER)
                    && !front.equals(getPackageName()) && !front.equals(BOOT_TV_APP)) {
                log("front=" + front + ", user opened an app, stop");
                stopSelf();
                return;
            }
            if (LAUNCHER.equals(front)) {
                log("front=" + front + ", launcher is up, stop");
                stopSelf();
                return;
            }
            if (STOCK_HOME.equals(front)) {
                // Coocaa aborts activity starts from background apps outside its whitelist, so ask
                // com.tianci.system (uid 1000) to go home; Home is this package's LaunchActivity.
                try {
                    getContentResolver().call(SYSTEM_PROVIDER, "COMMON", "gotoHomePage", null);
                    log("front=" + front + ", asked system to go home");
                } catch (RuntimeException e) {
                    log("front=" + front + ", gotoHomePage failed: " + e);
                }
            } else {
                log("front=" + front);
            }
            if (SystemClock.uptimeMillis() < until) {
                handler.postDelayed(this, POLL_MS);
            } else {
                log("watch over");
                stopSelf();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        BootReceiver.keepEnabled(this);
        log("start, uptime=" + SystemClock.uptimeMillis());
        handler.removeCallbacks(poll);
        until = SystemClock.uptimeMillis() + WATCH_MS;
        handler.post(poll);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        running = false;
        super.onDestroy();
        exitProcess();
    }

    /**
     * Coocaa's memory manager force-stops this package whenever its process is still alive behind the
     * launcher, and a force-stopped package misses the next boot broadcast. So never linger.
     */
    static void exitProcess() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!running) Process.killProcess(Process.myPid());
        }, EXIT_DELAY_MS);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Coocaa's ActivityManager publishes the resumed package in sky.current.apk. */
    private static String foregroundPackage() {
        try {
            String pkg = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, "sky.current.apk");
            return pkg == null || pkg.isEmpty() ? null : pkg;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private void log(String line) {
        File dir = getExternalFilesDir(null);
        if (dir == null) return;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        try (FileWriter w = new FileWriter(new File(dir, "watch.log"), true)) {
            w.write(time + " " + line + "\n");
        } catch (IOException ignored) {
        }
    }
}
