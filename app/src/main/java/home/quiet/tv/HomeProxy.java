package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import home.quiet.proxy.ProxyContract;
import java.io.IOException;

/** Controls our optional signed helper, never adopts an unrelated HOME app or installs a package. */
final class HomeProxy {
    private static final Object LOCK=new Object();
    private final Context context;
    private final HomeGuardState state;
    private final SharedPreferences prefs;
    HomeProxy(Context c){context=c.getApplicationContext();state=new HomeGuardState(context,false);prefs=state.preferences;}
    String packageName(){return ProxyContract.packageFor(HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND),
            selected() || prefs.getBoolean("proxyPausePending",false),prefs.getString("proxyPackage",""));}
    String component(){return new ComponentName(packageName(),ProxyContract.HOME).flattenToString();}
    boolean isComponent(String name){
        ComponentName target=ComponentName.unflattenFromString(name);
        return target!=null && ProxyContract.HOME.equals(target.getClassName()) && ProxyContract.isPackage(target.getPackageName()) && available(target.getPackageName());
    }
    boolean available(){
        return available(packageName());
    }
    private boolean available(String name){
        if(BuildConfig.LEGACY_19 || Build.VERSION.SDK_INT<21)return false;
        PackageManager pm=context.getPackageManager();
        try{
            PackageInfo pkg=pm.getPackageInfo(name,0);
            ProviderInfo provider=pm.resolveContentProvider(name+".control",0);
            return pkg.applicationInfo!=null && pkg.applicationInfo.enabled
                    && pm.checkSignatures(context.getPackageName(),name)==PackageManager.SIGNATURE_MATCH
                    && provider!=null && provider.exported && provider.enabled && name.equals(provider.packageName)
                    && (context.getPackageName()+".permission.HOME_PROXY").equals(provider.readPermission)
                    && new HomeController(context).component(new ComponentName(name,ProxyContract.HOME).flattenToString(),true);
        }catch(PackageManager.NameNotFoundException|SecurityException failure){return false;}
    }
    private Bundle call(String action,Bundle values) throws IOException {
        if(!available())throw new IOException("未安装匹配签名的 HOME 中转助手");
        try{
            Bundle result=context.getContentResolver().call(Uri.parse("content://"+packageName()+".control"),action,null,values);
            if(result==null || result.getInt("protocol")!=ProxyContract.VERSION || !context.getPackageName().equals(result.getString("target")))throw new IOException("HOME 代理协议或目标不匹配");
            return result;
        }catch(RuntimeException failure){throw new IOException("HOME 代理通信未完成",failure);}
    }
    Bundle status() throws IOException {return call(ProxyContract.STATUS,null);}
    private long nextEpoch() throws IOException {
        long epoch=Math.max(prefs.getLong("proxyEpoch",0),status().getLong(ProxyContract.EPOCH,0))+1;
        if(epoch<=0 || !prefs.edit().putLong("proxyEpoch",epoch).commit())throw new IOException("HOME 代理控制序号未保存");return epoch;
    }
    private Bundle values(long epoch){Bundle data=new Bundle();data.putLong(ProxyContract.EPOCH,epoch);return data;}
    private boolean selected(){return "PROXY".equals(prefs.getString("relaySource",""));}
    boolean active(){return selected() && state.automatic() && new DesktopRoutes(context,false).current() && localIdentity();}
    private boolean localIdentity(){
        return available() && state.validSelection() && state.selected().equals(prefs.getString("proxyOriginal",""))
                && Build.FINGERPRINT.equals(prefs.getString("proxyFirmware",""));
    }
    boolean ready(){
        if(!active())return false;
        try{Bundle result=status();return result.getBoolean("valid") && ProxyContract.TARGET.equals(result.getString("mode"))
                && result.getLong(ProxyContract.EPOCH)==prefs.getLong("proxyEpoch",-1)
                && component().equals(new HomeController(context).inspect(null).actual);}
        catch(Exception failure){return false;}
    }
    String problem(){
        if(!available())return "未安装匹配的 HOME 中转助手，将使用原主页识别中转";
        try{Bundle result=status();return ready()?"代理已就绪，实体 HOME 和开机场景待验证":result.getString("problem","代理尚未配置");}
        catch(IOException failure){return failure.getMessage();}
    }
    // The durable OFF gate is set by the caller first. Keep the helper enabled while it owns HOME.
    boolean pause(){synchronized(LOCK){
        if(!available()){prefs.edit().putBoolean("proxyPausePending",selected()).remove("proxyPending").commit();return !selected();}
        try{long epoch=nextEpoch();Bundle result=call(ProxyContract.PAUSE,values(epoch));
            boolean ok=epoch==result.getLong(ProxyContract.EPOCH) && ProxyContract.STOCK.equals(result.getString("mode"));
            prefs.edit().putBoolean("proxyPausePending",!ok).remove("proxyPending").commit();return ok;
        }catch(IOException failure){prefs.edit().putBoolean("proxyPausePending",true).remove("proxyPending").commit();return false;}
    }}
    /** Returns false only when no helper was eligible. A changed/unknown HOME never falls through. */
    boolean configure(AdbController adb,SetupOperations.Ticket task) throws Exception {
        boolean eligible=available();
        DiagnosticLog.info("home_proxy.configure","package="+packageName()+" available="+eligible+" xiaomi="+HomeGuardPolicy.xiaomi(Build.MANUFACTURER,Build.BRAND));
        if(!eligible)return false;
        try{status();}catch(IOException incompatible){return false;}
        HomeController home=new HomeController(context);task.check();state.configure();
        if(!state.validSelection() || state.helperActive())return false;
        HomeController.State before=home.inspect(adb);
        if(!before.known)throw new IOException("系统 HOME 未确认，不尝试代理切换");
        home.remember(before);
        if(!state.selected().equals(new SetupStore(context).originalHome()))throw new IOException("原桌面与恢复基线不一致，请重新核对原桌面");
        int generation=adb.generation();long epoch;
        synchronized(LOCK){
            task.check();if("OFF".equals(prefs.getString("mode","AUTO")))throw new IOException("中转已暂停");
            epoch=nextEpoch();Bundle data=values(epoch);data.putString(ProxyContract.ORIGINAL,state.selected());
            Bundle prepared=call(ProxyContract.PREPARE,data);
            if(!prepared.getBoolean("valid") || epoch!=prepared.getLong(ProxyContract.EPOCH) || !ProxyContract.STOCK.equals(prepared.getString("mode")))throw new IOException("代理原桌面回退未准备好");
            if(!prefs.edit().putString("proxyPackage",packageName()).putString("proxyOriginal",state.selected()).putString("proxyFirmware",Build.FINGERPRINT).commit())throw new IOException("代理回退记录未保存");
        }
        try{
            task.check();home.switchTo(adb,component());task.check();
            synchronized(LOCK){
                if("OFF".equals(prefs.getString("mode","AUTO")) || prefs.getLong("proxyEpoch",-1)!=epoch)throw new IOException("代理配置已暂停或被替代");
                new DesktopRoutes(context).proxy();
                Bundle enabled=call(ProxyContract.ENABLE,values(epoch));
                if(!ProxyContract.TARGET.equals(enabled.getString("mode")) || !ready())throw new IOException("代理启用未确认");
                prefs.edit().putBoolean("proxyPausePending",false).commit();state.status("HOME 代理已配置，等待实际 HOME 进入桌面");
            }
            task.check();new SetupStore(context).put("status","proxy-configured");DiagnosticLog.info("home_proxy.configure.result","package="+packageName()+" result=RESOLVED screenVerified=false");return true;
        }catch(Exception failure){
            DiagnosticLog.error("home_proxy.configure.failed","package="+packageName(),failure);
            pause();
            // Roll back only through the same still-authorized connection. Otherwise the helper stays ORIGINAL.
            if(task.active() && adb.connected() && adb.generation()==generation){
                try{if(!before.actual.equals(home.inspect(adb).actual))home.setAndVerify(adb,before.actual);
                    if(before.actual.equals(home.inspect(adb).actual) && failure instanceof HomeController.SwitchFailure
                            && (((HomeController.SwitchFailure)failure).result==DesktopRoutePolicy.Result.RETAINED || ((HomeController.SwitchFailure)failure).result==DesktopRoutePolicy.Result.UNSUPPORTED))return false;
                }catch(Exception restore){failure.addSuppressed(restore);}
            }
            throw failure;
        }
    }
    String resume(){synchronized(LOCK){
        try{
            if(!localIdentity() || !component().equals(new HomeController(context).inspect(null).actual))throw new IOException("代理或系统 HOME 已变化，请重新自动配置");
            long epoch=nextEpoch();Bundle data=values(epoch);data.putString(ProxyContract.ORIGINAL,state.selected());call(ProxyContract.PREPARE,data);
            prefs.edit().putString("mode","AUTO").putBoolean("configured",true).commit();new DesktopRoutes(context).proxy();
            call(ProxyContract.ENABLE,values(epoch));if(!ready())throw new IOException("HOME 代理未就绪");return "";
        }catch(Exception failure){prefs.edit().putString("mode","OFF").commit();pause();state.status(failure.getMessage());return failure.getMessage();}
    }}
    boolean accept(Intent intent){
        if(intent==null || !active() || !HomeGuardState.canShow(context))return false;
        String token=intent.getStringExtra(ProxyContract.REQUEST);long epoch=intent.getLongExtra(ProxyContract.EPOCH,-1);
        // The entry is signature protected. Runtime uses saved local state; no selection or IPC probe per HOME.
        if(token==null || token.isEmpty() || epoch!=prefs.getLong("proxyEpoch",-2))return false;
        return prefs.edit().putString("proxyPending",token).commit();
    }

    boolean allowed(Intent intent){return intent==null || !intent.hasExtra(ProxyContract.REQUEST)
            || active() && prefs.getLong("proxyEpoch",-1)==intent.getLongExtra(ProxyContract.EPOCH,-2);}
    void defer(Intent intent){
        if(intent==null || !intent.hasExtra(ProxyContract.REQUEST))return;
        synchronized(LOCK){try{Bundle data=values(intent.getLongExtra(ProxyContract.EPOCH,-1));data.putString(ProxyContract.TOKEN,intent.getStringExtra(ProxyContract.REQUEST));
            call(ProxyContract.DEFER,data);prefs.edit().remove("proxyPending").commit();
        }catch(IOException failure){state.status("暂缓回执未确认，下次 HOME 将核对回退");}}
    }
    void visible(Activity activity){
        Intent intent=activity.getIntent();if(intent==null || !intent.hasExtra(ProxyContract.REQUEST))return;
        if(!allowed(intent)){original(activity);activity.finish();return;}
        String token=intent.getStringExtra(ProxyContract.REQUEST);
        if(token==null || !token.equals(prefs.getString("proxyPending","")))return;
        synchronized(LOCK){
            if(!allowed(intent)){original(activity);activity.finish();return;}
            try{Bundle data=values(intent.getLongExtra(ProxyContract.EPOCH,-1));data.putString(ProxyContract.TOKEN,token);call(ProxyContract.ACK,data);
                prefs.edit().remove("proxyPending").putLong("proxyVisibleAt",System.currentTimeMillis()).putLong("proxyVisibleEpoch",intent.getLongExtra(ProxyContract.EPOCH,-1)).commit();
                intent.removeExtra(ProxyContract.REQUEST);intent.removeExtra(ProxyContract.EPOCH);
                state.status("已通过 HOME 代理进入 Quiet TV");DiagnosticLog.info("home_proxy.visibility","callback=true screenVerified=false");
            }catch(IOException failure){state.status("代理显示回执未确认，下次 HOME 将回原桌面");}
        }
    }
    void original(Context caller){
        HomeGuardRecovery.cancel();HomeGuardService.cancelPending();
        String original=prefs.getString("proxyOriginal",state.selected());
        if(state.validSelection() && original.equals(state.selected()))try{
            caller.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(original)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;
        }catch(ActivityNotFoundException|SecurityException ignored){}
        // The helper owns the independent recovery screen and retains its own original baseline.
        if(!available()){state.status("匹配的 HOME 助手不可用，请通过系统设置恢复");return;}
        try{caller.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(packageName(),ProxyContract.HOME)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(ActivityNotFoundException|SecurityException failure){state.status("原桌面不可用，请通过系统设置恢复");}
    }
}
