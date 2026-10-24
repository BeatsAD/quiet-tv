package home.quiet.tv;

import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.io.*;

/** One foreground attempt per boot or observed wake, durable across process recreation. */
final class BootAppStore {
    static final String TAG="QuietTVBootApp";
    static final long WAKE_WINDOW_MS=10000;
    private final Context context;
    private final SharedPreferences preferences;
    BootAppStore(Context context){this.context=context;preferences=context.getSharedPreferences("boot-app",0);}
    String target(){return preferences.getString("package","");}
    String label(){return preferences.getString("label",target());}
    Intent launchIntent(String pkg){
        if(pkg.isEmpty() || pkg.equals(context.getPackageName()) || "home.quiet.tv.test".equals(pkg))return null;
        ResolveInfo info=new HomeAppCatalog(context).launchable().get(pkg);
        return info==null?null:new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(new ComponentName(pkg,info.activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
    // Saving consumes the current cycle; only a later boot or sleep/wake may launch it.
    void select(String pkg,String label) throws IOException {synchronized(BootAppStore.class){
        String boot=bootId();
        if(!pkg.isEmpty() && (boot.isEmpty() || launchIntent(pkg)==null))throw new IOException("此应用或当前系统暂不支持开机自启");
        if(!preferences.edit().putString("package",pkg).putString("label",label).putString("handled-boot",boot)
                .putString("wake-boot",boot).putBoolean("sleeping",false).putLong("wake-at",-1)
                .putLong("handled-wake",preferences.getLong("wake-generation",0)).commit())throw new IOException("自启设置保存失败，请重试");
    }}
    private boolean syncBoot(String boot){
        if(boot.isEmpty())return false;
        if(boot.equals(preferences.getString("wake-boot","")))return true;
        return preferences.edit().putString("wake-boot",boot).putBoolean("sleeping",false).putLong("wake-at",-1)
            .putLong("handled-wake",preferences.getLong("wake-generation",0)).commit();
    }
    void screenOff(boolean homeEligible){synchronized(BootAppStore.class){
        if(!syncBoot(bootId()) || target().isEmpty())return;
        if(preferences.getBoolean("sleeping",false)==homeEligible && preferences.getLong("wake-at",-1)<0)return;
        if(preferences.edit().putBoolean("sleeping",homeEligible).putLong("wake-at",-1)
                .putLong("handled-wake",preferences.getLong("wake-generation",0)).commit())Log.i(TAG,homeEligible?"sleep recorded":"sleep outside home");
    }}
    void screenOn(){synchronized(BootAppStore.class){
        if(!syncBoot(bootId()) || !preferences.getBoolean("sleeping",false))return;
        long generation=preferences.getLong("wake-generation",0)+1;
        if(preferences.edit().putBoolean("sleeping",false).putLong("wake-generation",generation)
                .putLong("wake-at",SystemClock.elapsedRealtime()).commit())Log.i(TAG,"wake recorded cycle="+generation);
    }}
    void cancelWake(String reason){synchronized(BootAppStore.class){
        if(!preferences.getBoolean("sleeping",false) && preferences.getLong("wake-at",-1)<0)return;
        if(preferences.edit().putBoolean("sleeping",false).putLong("wake-at",-1)
                .putLong("handled-wake",preferences.getLong("wake-generation",0)).commit())Log.i(TAG,"wake cancelled: "+reason);
    }}
    private boolean pendingWake(){
        long at=preferences.getLong("wake-at",-1),now=SystemClock.elapsedRealtime();
        if(at<0 || preferences.getLong("wake-generation",0)==preferences.getLong("handled-wake",0))return false;
        if(now<at || now-at>WAKE_WINDOW_MS){cancelWake("expired");return false;}
        return true;
    }
    String consume(){
        synchronized(BootAppStore.class){
            String boot=bootId();
            if(!syncBoot(boot))return "";
            boolean reboot=!boot.equals(preferences.getString("handled-boot",""));
            if(!reboot && !pendingWake())return "";
            // Consume before starting, including unavailable/blocked targets, so Home remains recoverable.
            if(!preferences.edit().putString("handled-boot",boot).putLong("wake-at",-1).putBoolean("sleeping",false)
                    .putLong("handled-wake",preferences.getLong("wake-generation",0)).commit())return "";
            if(!target().isEmpty())Log.i(TAG,reboot?"consumed boot":"consumed wake");
            return target();
        }
    }
    private String bootId(){
        if(Build.VERSION.SDK_INT>=24){
            try{int count=Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);if(count>=0)return "count:"+count;}
            catch(SecurityException ignored){}
        }
        // Android 4.4–6 has no public boot counter. The kernel UUID survives process death and sleep.
        try(BufferedReader input=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))){
            String id=input.readLine();if(id!=null && id.matches("[0-9a-fA-F-]{36}"))return "id:"+id;
        }catch(IOException|SecurityException ignored){}
        return "";
    }
}
