package home.quiet.proxy;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.util.UUID;

final class ProxyState {
    // Provider and activities share this process and lock; a pause cannot race an enable/dispatch.
    static final Object LOCK=new Object();
    final Context context;
    final SharedPreferences prefs;
    ProxyState(Context context){this.context=context;Context storage=Build.VERSION.SDK_INT>=24?context.createDeviceProtectedStorageContext():context;prefs=storage.getSharedPreferences("route",Context.MODE_PRIVATE);}
    boolean usable(String value,boolean stock){
        ComponentName name=ComponentName.unflattenFromString(value);if(name==null || context.getPackageName().equals(name.getPackageName()))return false;
        try{
            PackageManager pm=context.getPackageManager();ActivityInfo info=pm.getActivityInfo(name,0);
            if(!info.exported || !info.enabled || !info.applicationInfo.enabled || info.permission!=null && pm.checkPermission(info.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED)return false;
            if(!stock)return BuildConfig.TARGET_PACKAGE.equals(name.getPackageName())
                    && pm.checkSignatures(context.getPackageName(),name.getPackageName())==PackageManager.SIGNATURE_MATCH;
            if(BuildConfig.TARGET_PACKAGE.equals(name.getPackageName()) || (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
            for(ResolveInfo home:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(name.getPackageName()),0))
                if(home.activityInfo!=null && name.equals(new ComponentName(home.activityInfo.packageName,home.activityInfo.name)))return true;
        }catch(PackageManager.NameNotFoundException|SecurityException ignored){}return false;
    }
    String entry(){return new ComponentName(BuildConfig.TARGET_PACKAGE,ProxyContract.ENTRY).flattenToString();}
    long targetVersion(){try{PackageInfo info=context.getPackageManager().getPackageInfo(BuildConfig.TARGET_PACKAGE,0);return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}catch(PackageManager.NameNotFoundException ignored){return -1;}}
    boolean valid(){return Build.FINGERPRINT.equals(prefs.getString("firmware","")) && usable(prefs.getString(ProxyContract.ORIGINAL,""),true)
            && usable(entry(),false) && targetVersion()==prefs.getLong("targetVersion",-2);}
    Bundle status(){Bundle out=new Bundle();out.putInt("protocol",ProxyContract.VERSION);out.putString("target",BuildConfig.TARGET_PACKAGE);
        out.putString("mode",prefs.getString("mode",ProxyContract.STOCK));out.putLong(ProxyContract.EPOCH,prefs.getLong(ProxyContract.EPOCH,0));
        out.putString(ProxyContract.ORIGINAL,prefs.getString(ProxyContract.ORIGINAL,""));out.putBoolean("valid",valid());
        out.putString(ProxyContract.TOKEN,prefs.getString("pending",""));out.putLong("visibleAt",prefs.getLong("visibleAt",0));out.putString("problem",prefs.getString("problem",""));return out;}
    void stock(String reason){if(!prefs.edit().putString("mode",ProxyContract.STOCK).remove("pending").putString("problem",reason).commit())throw new IllegalStateException("代理状态未保存");}
    void original(Context caller){
        String saved=prefs.getString(ProxyContract.ORIGINAL,"");
        if(usable(saved,true))try{caller.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(saved)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));return;}
        catch(ActivityNotFoundException|SecurityException ignored){}
        caller.startActivity(new Intent(caller,RecoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    void home(Context caller){synchronized(LOCK){
        android.app.KeyguardManager keyguard=(android.app.KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        UserManager user=(UserManager)context.getSystemService(Context.USER_SERVICE);
        // A temporary screen/lock condition must not permanently turn a healthy route off.
        if(power==null || !power.isInteractive() || keyguard==null || keyguard.isKeyguardLocked()
                || Build.VERSION.SDK_INT>=24 && (user==null || !user.isUserUnlocked()))return;
        int next=ProxyPolicy.next(ProxyContract.TARGET.equals(prefs.getString("mode","")),valid(),!prefs.getString("pending","").isEmpty(),SystemClock.elapsedRealtime(),prefs.getLong("started",0));
        if(next==ProxyPolicy.WAIT)return;
        if(next==ProxyPolicy.FALLBACK){stock("暂停、身份变化或上次启动未确认");original(caller);return;}
        String token=UUID.randomUUID().toString();
        if(!prefs.edit().putString("pending",token).putLong("started",SystemClock.elapsedRealtime()).commit()){original(caller);return;}
        try{caller.startActivity(new Intent().setComponent(ComponentName.unflattenFromString(entry())).putExtra(ProxyContract.REQUEST,token)
                .putExtra(ProxyContract.EPOCH,prefs.getLong(ProxyContract.EPOCH,0)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(ActivityNotFoundException|SecurityException failure){stock("主程序无法启动");original(caller);}
    }}
}
