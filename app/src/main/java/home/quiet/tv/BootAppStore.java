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
    static final String HDMI="quiet:hdmi";
    private final Context context;
    private final SharedPreferences preferences;
    BootAppStore(Context context){this.context=context;preferences=context.getSharedPreferences("boot-app",0);}
    String target(){return preferences.getString("package","");}
    String label(){return hdmiBoot()?watchLabel():preferences.getString("label",target());}
    boolean hdmiBoot(){return preferences.getBoolean("hdmi-boot",false);}
    boolean enabled(){return hdmiBoot()||!target().isEmpty();}
    String watchTarget(){return preferences.getString("watch-hdmi","");}
    String watchLabel(){return preferences.getString("watch-label","HDMI");}
    String watchRevision(){return watchTarget();}
    boolean tested(){return !watchTarget().isEmpty()&&watchTarget().equals(preferences.getString("tested-hdmi",""));}
    String pendingTest(){return preferences.getString("test-hdmi","");}
    void saveWatch(String target,String label) throws IOException {synchronized(BootAppStore.class){
        save(preferences.edit().putString("watch-hdmi",target).putString("watch-label",label)
            .putBoolean("hdmi-boot",false).remove("tested-hdmi").remove("test-hdmi"));
    }}
    void testDispatched(String target) throws IOException {if(!target.isEmpty()&&!target.equals(watchTarget()))throw new IOException("目标已变化，请重新测试");save(preferences.edit().putString("test-hdmi",target).remove("tested-hdmi").putBoolean("hdmi-boot",false));}
    void confirmTest(boolean passed) throws IOException {
        String pending=pendingTest();if(pending.isEmpty()||!pending.equals(watchTarget()))throw new IOException("请先测试当前 HDMI");
        SharedPreferences.Editor editor=preferences.edit().remove("test-hdmi");
        if(passed)editor.putString("tested-hdmi",pending);else editor.remove("tested-hdmi").putBoolean("hdmi-boot",false);
        save(editor);
    }
    void hdmiBoot(boolean enabled) throws IOException {synchronized(BootAppStore.class){
        String boot=bootId();if(enabled&&(!tested()||boot.isEmpty()))throw new IOException("请先测试 HDMI 并确认画面和声音");
        SharedPreferences.Editor editor=preferences.edit().putBoolean("hdmi-boot",enabled).putString("handled-boot",boot);
        if(enabled)editor.putString("package","").putBoolean("sleeping",false).putLong("wake-at",-1);
        save(editor);
    }}
    private void save(SharedPreferences.Editor editor) throws IOException {
        java.util.Map<String,?> before=preferences.getAll();
        if(editor.commit())return;
        SharedPreferences.Editor rollback=preferences.edit();
        // Restore only this store's snapshot after a failed commit changes its memory map.
        for(String key:preferences.getAll().keySet())if(!before.containsKey(key))rollback.remove(key);
        for(java.util.Map.Entry<String,?> item:before.entrySet()){
            Object value=item.getValue();if(value instanceof String)rollback.putString(item.getKey(),(String)value);
            else if(value instanceof Boolean)rollback.putBoolean(item.getKey(),(Boolean)value);
            else if(value instanceof Long)rollback.putLong(item.getKey(),(Long)value);
        }
        rollback.commit();throw new IOException("设置保存失败，请检查存储空间后重试");
    }
    void cancelHdmiBoot(){synchronized(BootAppStore.class){
        if(hdmiBoot()){String boot=bootId();if(!boot.isEmpty()&&!boot.equals(preferences.getString("handled-boot","")))preferences.edit().putString("handled-boot",boot).commit();}
    }}
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
        if(!preferences.edit().putBoolean("hdmi-boot",false).putString("package",pkg).putString("label",label).putString("handled-boot",boot)
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
            if(!enabled())return "";
            String boot=bootId();
            if(!syncBoot(boot))return "";
            boolean reboot=!boot.equals(preferences.getString("handled-boot",""));
            if(hdmiBoot()&&(!reboot||SystemClock.elapsedRealtime()>=HomeGuardPolicy.BOOT_WINDOW_MS)){
                if(reboot)cancelHdmiBoot();return "";
            }
            if(!reboot && !pendingWake())return "";
            // Consume before starting, including unavailable/blocked targets, so Home remains recoverable.
            if(!preferences.edit().putString("handled-boot",boot).putLong("wake-at",-1).putBoolean("sleeping",false)
                    .putLong("handled-wake",preferences.getLong("wake-generation",0)).commit())return "";
            if(!target().isEmpty())Log.i(TAG,reboot?"consumed boot":"consumed wake");
            return hdmiBoot()?HDMI:target();
        }
    }
    String bootId(){
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
