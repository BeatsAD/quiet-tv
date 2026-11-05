package home.quiet.tv;

import android.content.*;
import android.os.*;

/** An observed sleep survives process death; rebinding alone never invents a wake. */
final class HomeGuardWake {
    private static final long WINDOW_MS=15000;
    private static boolean supported(){return !BuildConfig.LEGACY_19 && Build.VERSION.SDK_INT>=21;}
    private static SharedPreferences prefs(Context c){return new HomeGuardState(c,false).preferences;}
    @android.annotation.SuppressLint("ApplySharedPref")
    static void screen(Context c,String action){
        if(!supported() || !Intent.ACTION_SCREEN_OFF.equals(action) && !Intent.ACTION_SCREEN_ON.equals(action))return;
        SharedPreferences p=prefs(c);String boot=BootAppStore.bootId(c);
        if(!new HomeGuardState(c,false).enabled() || boot.isEmpty()){cancel(c);return;}
        boolean same=boot.equals(p.getString("guardWakeBoot",""));
        if(Intent.ACTION_SCREEN_OFF.equals(action)){
            if(!same || p.getLong("guardWakeUntil",0)!=-1)p.edit().putString("guardWakeBoot",boot)
                .putLong("guardWakeFloor",System.currentTimeMillis()).putLong("guardWakeUntil",-1).commit();
        }else if(Intent.ACTION_SCREEN_ON.equals(action) && same && p.getLong("guardWakeUntil",0)==-1){
            p.edit().putLong("guardWakeFloor",System.currentTimeMillis()).putLong("guardWakeUntil",SystemClock.elapsedRealtime()+WINDOW_MS).commit();
        }
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static long pending(Context c){
        if(!supported())return 0;
        SharedPreferences p=prefs(c);String boot=p.getString("guardWakeBoot","");
        if(boot.isEmpty())return 0;
        if(!new HomeGuardState(c,false).enabled() || !boot.equals(BootAppStore.bootId(c))){cancel(c);return 0;}
        if(!HomeGuardState.interactive(c) || !HomeGuardState.unlocked(c))return 0;
        long until=p.getLong("guardWakeUntil",0),now=SystemClock.elapsedRealtime();
        if(until==-1){
            until=now+WINDOW_MS;if(!p.edit().putLong("guardWakeUntil",until).commit())return 0;
        }
        long floor=p.getLong("guardWakeFloor",0);
        if(now>until || until-now>WINDOW_MS || floor<=0 || floor>System.currentTimeMillis()){cancel(c);return 0;}
        return floor;
    }
    static long remaining(Context c){return Math.max(0,prefs(c).getLong("guardWakeUntil",0)-SystemClock.elapsedRealtime());}
    @android.annotation.SuppressLint("ApplySharedPref")
    static void cancel(Context c){
        if(supported() && prefs(c).contains("guardWakeBoot"))prefs(c).edit().remove("guardWakeBoot").remove("guardWakeFloor").remove("guardWakeUntil").commit();
    }
    private HomeGuardWake(){}
}
