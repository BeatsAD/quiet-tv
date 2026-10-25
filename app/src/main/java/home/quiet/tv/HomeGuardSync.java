package home.quiet.tv;

import android.accounts.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Local account + settings sync, including low-RAM devices without notification access. */
final class HomeGuardSync {
    static final String AUTHORITY="settings";
    private static final ExecutorService worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    static boolean supported(Context c){return HomeGuardPolicy.supported(Build.MANUFACTURER,Build.BRAND,Build.VERSION.SDK_INT,BuildConfig.LEGACY_19);}
    static Account account(Context c){
        String name=c.getString(R.string.home_guard_account_name),type=c.getString(R.string.home_guard_account_type);
        for(Account existing:AccountManager.get(c).getAccountsByType(type))
            if(name.equals(existing.name) || "Quiet TV 桌面恢复".equals(existing.name))return existing;
        return new Account(name,type);
    }
    static void pause(Context c){
        if(!supported(c) || !HomeGuardState.unlocked(c))return;
        try{Account own=account(c);if(ContentResolver.getSyncAutomatically(own,AUTHORITY))ContentResolver.setSyncAutomatically(own,AUTHORITY,false);ContentResolver.cancelSync(own,AUTHORITY);}
        catch(SecurityException|IllegalArgumentException failure){DiagnosticLog.error("home_guard.sync.pause_failed",failure);}
    }
    static void refresh(Context c){if(!supported(c) || !HomeGuardState.unlocked(c))return;Context app=c.getApplicationContext();worker.execute(()->ensure(app));}
    static synchronized void ensure(Context c){
        if(!supported(c) || !HomeGuardState.unlocked(c))return;
        HomeGuardState state=new HomeGuardState(c);Account own=account(c);
        try{
            boolean exists=false;for(Account value:AccountManager.get(c).getAccountsByType(own.type))if(own.equals(value)){exists=true;break;}
            if(!state.enabled()){
                if(exists)pause(c);
                status(state,"已暂停");return;
            }
            if(!state.validSelection() || state.helperActive()){status(state,"原主页或中转条件不可用");return;}
            if(!HomeGuardRecovery.writeAllowed(c)){status(state,"写安全设置权限未授予");return;}
            if(!exists && !AccountManager.get(c).addAccountExplicitly(own,null,null)){status(state,"同步恢复账号未能创建");return;}
            if(ContentResolver.getIsSyncable(own,AUTHORITY)<=0)ContentResolver.setIsSyncable(own,AUTHORITY,1);
            if(!ContentResolver.getSyncAutomatically(own,AUTHORITY))ContentResolver.setSyncAutomatically(own,AUTHORITY,true);
            status(state,ContentResolver.getMasterSyncAutomatically()?"已配置，等待系统调度":"系统自动同步已关闭");
        }catch(SecurityException|IllegalArgumentException failure){status(state,"系统不允许配置同步恢复");DiagnosticLog.error("home_guard.sync.setup_failed",failure);}
    }
    private static void status(HomeGuardState state,String text){if(!text.equals(state.preferences.getString("syncRecoveryStatus","")))state.preferences.edit().putString("syncRecoveryStatus",text).apply();}
    static String summary(Context c){return new HomeGuardState(c).preferences.getString("syncRecoveryStatus","尚未配置");}
    static boolean ready(Context c){
        if(!supported(c) || !HomeGuardState.unlocked(c) || !new HomeGuardState(c).ready() || !HomeGuardRecovery.writeAllowed(c))return false;
        try{Account own=account(c);return ContentResolver.getMasterSyncAutomatically() && ContentResolver.getIsSyncable(own,AUTHORITY)>0 && ContentResolver.getSyncAutomatically(own,AUTHORITY);}
        catch(SecurityException|IllegalArgumentException failure){return false;}
    }
    static void perform(Context c,Account account,String authority){
        if(!supported(c) || !HomeGuardState.unlocked(c) || !AUTHORITY.equals(authority) || !account(c).equals(account))return;
        HomeGuardState state=new HomeGuardState(c);if(!state.ready()){if(!state.enabled())refresh(c);return;}
        DiagnosticLog.info("home_guard.sync.callback","local_only=true");HomeGuardAdb.ensure(c);
        if(!HomeGuardService.connected() || !HomeGuardState.interactive(c))HomeGuardRecovery.recovered(c);
        if(HomeGuardService.connected())return;
        boolean missing=!state.serviceEnabled();
        if(missing)HomeGuardRecovery.noteRevival(c);
        HomeGuardRecovery.request(c,"系统同步恢复");
    }
}
