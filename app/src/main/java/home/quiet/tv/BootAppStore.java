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
        editor.putBoolean("cover-pending",false).putBoolean("cover-armed",false).putBoolean("cover-stopped",true);
        commit(preferences,editor);
    }
    static void commit(SharedPreferences preferences,SharedPreferences.Editor editor) throws IOException {
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
        ResolveInfo info=new HomeAppCatalog(context).find(pkg);
        return info==null?null:new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(new ComponentName(pkg,info.activityInfo.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
    // Saving consumes the current cycle; only a later boot or sleep/wake may launch it.
    void select(String pkg,String label) throws IOException {synchronized(BootAppStore.class){
        String boot=bootId();
        if(!pkg.isEmpty() && (boot.isEmpty() || launchIntent(pkg)==null))throw new IOException("此应用或当前系统暂不支持开机自启");
        if(!preferences.edit().putBoolean("cover-pending",false).putBoolean("cover-armed",false).putBoolean("cover-stopped",true)
                .putBoolean("hdmi-boot",false).putString("package",pkg).putString("label",label).putString("handled-boot",boot)
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
    private String configuredTarget(){return hdmiBoot()?HDMI:target();}
    private boolean coverCurrent(){
        long at=preferences.getLong("cover-at",-1),now=SystemClock.elapsedRealtime();
        return at>=0 && now>=at && now-at<=60000 && HomeGuardPolicy.bootRecheck(now)
            && bootId().equals(preferences.getString("cover-boot","")) && configuredTarget().equals(preferences.getString("cover-target",""))
            && (!hdmiBoot() || tested() && watchRevision().equals(preferences.getString("cover-input","")));
    }
    void dispatched(String target,String foreground){synchronized(BootAppStore.class){
        if(foreground.isEmpty() || !target.equals(configuredTarget()) || !bootId().equals(preferences.getString("cover-boot",""))
                || preferences.getBoolean("cover-stopped",true) || preferences.getBoolean("cover-used",false))return;
        preferences.edit().putString("cover-foreground",foreground).putLong("cover-at",SystemClock.elapsedRealtime())
            .putLong("cover-wall",System.currentTimeMillis()).putBoolean("cover-armed",true).putBoolean("cover-seen",false).commit();
    }}
    void foreground(String component,boolean stock,boolean own,long at){synchronized(BootAppStore.class){
        if(!preferences.getBoolean("cover-armed",false) || !coverCurrent() || at<preferences.getLong("cover-wall",Long.MAX_VALUE))return;
        String expected=preferences.getString("cover-foreground","");
        if(component.equals(expected) || expected.endsWith("/") && component.startsWith(expected)){
            if(!preferences.getBoolean("cover-seen",false))preferences.edit().putBoolean("cover-seen",true).putLong("cover-seen-at",at).commit();
        }else if(!stock && !own)userAction("other_foreground");
    }}
    boolean covered(long at){synchronized(BootAppStore.class){
        if(!preferences.getBoolean("cover-armed",false) || !preferences.getBoolean("cover-seen",false)
                || preferences.getBoolean("cover-used",false) || !coverCurrent() || at<preferences.getLong("cover-seen-at",Long.MAX_VALUE))return false;
        return preferences.edit().putBoolean("cover-used",true).putBoolean("cover-pending",true).putBoolean("cover-armed",false).commit();
    }}
    void userAction(String reason){synchronized(BootAppStore.class){
        String boot=bootId();if(boot.isEmpty() || !HomeGuardPolicy.bootRecheck(SystemClock.elapsedRealtime()))return;
        if(boot.equals(preferences.getString("boot-user-cycle","")) && preferences.getBoolean("cover-stopped",true))return;
        preferences.edit().putString("boot-user-cycle",boot).putBoolean("cover-stopped",true).putBoolean("cover-armed",false).putBoolean("cover-pending",false).commit();
        DiagnosticLog.info("home.autostart.cover","event=user_cancel reason="+reason);
    }}
    boolean userNavigated(){String boot=bootId();return !boot.isEmpty() && boot.equals(preferences.getString("boot-user-cycle",""));}
    long stockHoldRemaining(long duration){synchronized(BootAppStore.class){
        long now=SystemClock.elapsedRealtime();String boot=bootId();
        if(duration<=0 || boot.isEmpty() || !HomeGuardPolicy.bootRecheck(now) || boot.equals(preferences.getString("boot-user-cycle","")))return 0;
        long at=preferences.getLong("stock-hold-at",now);
        if(!boot.equals(preferences.getString("stock-hold-boot",""))){at=now;if(!preferences.edit().putString("stock-hold-boot",boot).putLong("stock-hold-at",at).commit())return 0;}
        return now>=at?Math.max(0,Math.min(duration-(now-at),HomeGuardPolicy.BOOT_WINDOW_MS-now)):0;
    }}
    String consume(){
        synchronized(BootAppStore.class){
            if(!enabled())return "";
            if(preferences.getBoolean("cover-pending",false)){
                boolean valid=coverCurrent() && !preferences.getBoolean("cover-stopped",true);
                if(!preferences.edit().putBoolean("cover-pending",false).commit())return "";
                if(valid)return configuredTarget();
            }
            String boot=bootId();
            if(!syncBoot(boot))return "";
            boolean reboot=!boot.equals(preferences.getString("handled-boot",""));
            if(hdmiBoot()&&(!reboot||SystemClock.elapsedRealtime()>=HomeGuardPolicy.BOOT_WINDOW_MS)){
                if(reboot)cancelHdmiBoot();return "";
            }
            if(!reboot && !pendingWake())return "";
            // Consume before starting, including unavailable/blocked targets, so Home remains recoverable.
            if(!preferences.edit().putString("cover-boot",reboot?boot:"").putString("cover-target",configuredTarget()).putString("cover-input",watchRevision())
                    .putBoolean("cover-stopped",boot.equals(preferences.getString("boot-user-cycle",""))).putBoolean("cover-used",false).putBoolean("cover-pending",false).putBoolean("cover-armed",false)
                    .putString("handled-boot",boot).putLong("wake-at",-1).putBoolean("sleeping",false)
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
