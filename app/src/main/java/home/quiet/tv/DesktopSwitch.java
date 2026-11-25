package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Shared TV/phone switching engine. Only the recorded stock launcher and current user may change. */
final class DesktopSwitch {
    private static DesktopSwitch instance;
    static synchronized DesktopSwitch get(Context context){if(instance==null)instance=new DesktopSwitch(context.getApplicationContext());return instance;}
    private static final String[] GUARD_KEYS={"mode","enabled","adbGuardEnabled","status","vendorBoot"};
    private static final String[] IDENTITY_KEYS={"home","firmware","configured","routeHome","relaySource","observeHome","directHomeKeys","proxyPackage","proxyOriginal","proxyFirmware","windowGuardHome","windowGuardEnabled","windowGuardFirmware","windowGuardVersion","adbGuardHome","adbGuardFirmware"};
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Context context;
    private final AtomicBoolean running=new AtomicBoolean();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private DesktopSwitch(Context context){this.context=context;}
    void listen(Runnable listener){listeners.addIfAbsent(listener);}
    void forget(Runnable listener){listeners.remove(listener);}
    private void changed(){main.post(()->{PhoneSettingsSession.get(context).configurationChanged();for(Runnable listener:listeners)listener.run();});}
    boolean busy(){return running.get();}
    JSONObject state(){
        DevicePort port=new DevicePort(()->true);
        try{return presentation(new DesktopSwitchPlan(port).state());}
        catch(Exception failure){try{
            JSONObject legacy=new JSONObject().put("phase","quiet").put("problem",message(failure)).put("actions",new JSONArray());
            return presentation(DesktopManagementState.present(legacy,new JSONObject(),port.runtime(),port.load(),port.management()));
        }catch(Exception unavailable){try{return presentation(DesktopManagementState.present(new JSONObject().put("problem","配置记录无法读取，请通过电脑核对恢复记录。").put("actions",new JSONArray()),new JSONObject(),new JSONObject().put("needsReview",true).put("problem","配置记录无法读取，请通过电脑核对恢复记录。"),null,new JSONObject()));}catch(JSONException impossible){return new JSONObject();}}}
    }
    private JSONObject presentation(JSONObject snapshot)throws JSONException {
        snapshot.put("busy",busy());if(busy()){JSONObject guard=snapshot.optJSONObject("guard");if(guard!=null)guard.put("state","working").put("label","正在处理");JSONObject desktop=snapshot.optJSONObject("mainDesktop");snapshot.put("summary",(desktop==null?"待核对":desktop.optString("label"))+"  正在处理");}return snapshot;
    }
    JSONObject execute(String id,String revision,Callable<Boolean> authority)throws Exception {return execute(id,revision,authority,false);}
    JSONObject manage(String id,String revision,Callable<Boolean> authority)throws Exception {return execute(id,revision,authority,true);}
    JSONObject configureGuard(String original)throws Exception {
        if(!running.compareAndSet(false,true))throw new IOException("已有桌面操作正在执行，请等待完成。");
        changed();
        try {
            AppOperations.begin();
            try {
                DevicePort port=new DevicePort(()->true);
                JSONObject setup=state().getJSONObject("settings").optJSONObject("guardSetup");
                if(setup==null || !original.equals(setup.optString("original")))throw new IOException("桌面状态已变化，请重新核对。");
                port.ui(()->{new DesktopGuardSetup(context).enable(original);return null;});
            } finally {AppOperations.end();}
        } finally {running.set(false);changed();}
        return state();
    }
    String repairGuard(Context caller,boolean openSettings)throws Exception {
        if(!running.compareAndSet(false,true))throw new IOException("已有桌面操作正在执行，请等待完成。");
        try{
            AppOperations.begin();
            try{
                JSONObject settings=state().optJSONObject("settings");
                JSONObject repair=settings==null?null:settings.optJSONObject("guardRepair");
                if(repair==null)throw new IOException("守护状态已变化，请重新核对。");
                if(!openSettings && repair.optBoolean("canRetry"))
                    return HomeGuardRecovery.retryExistingBinding(context)
                            ?"守护已连接，请按遥控器 HOME 键验证。"
                            :"已请求重新连接，稍后查看守护状态。";
                Intent intent=new DesktopGuardSetup(caller).settings("accessibility");
                if(intent!=null && !(caller instanceof android.app.Activity))intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if(intent==null || !ExternalActivityLauncher.open(caller,"guard.repair.accessibility",intent))
                    throw new IOException("系统无障碍入口不可用，请通过电脑修复。");
                return DesktopGuardSetup.permissionPrompt(caller,"accessibility");
            }finally{AppOperations.end();}
        }finally{running.set(false);changed();}
    }
    private JSONObject execute(String id,String revision,Callable<Boolean> authority,boolean management)throws Exception {
        if(!running.compareAndSet(false,true))throw new IOException("已有桌面操作正在执行，请等待完成。");
        boolean locked=false;changed();try{AppOperations.begin();locked=true;DesktopSwitchPlan plan=new DesktopSwitchPlan(new DevicePort(authority));return management?plan.manage(id,revision):plan.run(id,revision);}
        finally{if(locked)AppOperations.end();running.set(false);changed();}
    }
    JSONObject verify(String scene,String result,String revision,Callable<Boolean> authority)throws Exception {
        if(!running.compareAndSet(false,true))throw new IOException("已有桌面操作正在执行，请等待完成。");
        boolean locked=false;try{AppOperations.begin();locked=true;return new DesktopSwitchPlan(new DevicePort(authority)).verify(scene,result,revision);}
        finally{if(locked)AppOperations.end();running.set(false);changed();}
    }
    String legacy(boolean restore,Callable<Boolean> authority)throws Exception {
        if(!running.compareAndSet(false,true))throw new IOException("已有桌面操作正在执行，请等待完成。");
        boolean locked=false;try{AppOperations.begin();locked=true;DevicePort port=new DevicePort(authority);port.check();return port.ui(()->MaintenanceHelpActivity.perform(context,restore));}
        finally{if(locked)AppOperations.end();running.set(false);changed();}
    }
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    static String message(Throwable failure){return failure.getMessage()==null?"桌面切换未完成，请核对实际状态。":failure.getMessage();}
    private final class DevicePort implements DesktopSwitchPlan.Port {
        private final Callable<Boolean> authority;
        private final ManagementAdb management=ManagementAdb.existing();
        private final AdbController adb=management==null?null:management.adb;
        private final int connection=adb==null?-1:adb.generation();
        private final SetupStore store=new SetupStore(context);
        private final HomeGuardState guard=new HomeGuardState(context,false);
        DevicePort(Callable<Boolean> authority){this.authority=authority;}
        private <T>T ui(Callable<T> action)throws Exception {
            if(Looper.myLooper()==Looper.getMainLooper())return action.call();FutureTask<T> task=new FutureTask<>(action);main.post(task);
            try{return task.get(5,TimeUnit.SECONDS);}catch(TimeoutException failure){task.cancel(false);throw new IOException("电视操作响应未确认，请重新核对。",failure);}catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("电视操作失败",cause);}
        }
        public void check()throws Exception {if(!authority.call())throw new IOException("操作已取消或设置连接已结束。");if(adb!=null&&adb.connected()){if(connection!=adb.generation())throw new IOException("管理连接已变化，请重新核对。");try{adb.validateUser();}catch(Exception failure){invalidate();throw failure;}}}
        private AdbController shell()throws Exception{check();if(adb==null||!adb.connected()||connection!=adb.generation())throw new IOException("需要已授权的本机管理连接。");return adb;}
        private void invalidate(){if(adb!=null)adb.disconnect();if(management!=null)management.disconnect();}
        private String command(AdbController client,String value)throws Exception{try{return client.shell(value);}catch(IOException failure){if(!(failure instanceof AdbController.CommandFailure))invalidate();throw failure;}}
        public JSONObject load()throws Exception{return store.get().optJSONObject("desktopSwitch");}
        public void save(JSONObject record)throws Exception{store.put("desktopSwitch",record);}
        public JSONObject management()throws Exception {JSONObject value=store.get().optJSONObject("desktopManagement");return value==null?new JSONObject():value;}
        public void management(JSONObject record)throws Exception {store.put("desktopManagement",record);}
        public void verify(String scene,String result)throws Exception {new DesktopRoutes(context,false).observation(scene,result);}
        public JSONObject runtime()throws Exception {
            JSONObject r=new JSONObject(),verification=new JSONObject();HomeController.State actual=new HomeController(context).inspect();DesktopRoutes routes=new DesktopRoutes(context,false);
            boolean window=LegacyWindowHomeGuard.configured(context),localAdb=LegacyAdbHomeGuard.configured(context),proxy=routes.proxySelected();
            boolean paused="OFF".equals(guard.preferences.getString("mode","AUTO")),configured=guard.preferences.getBoolean("configured",false)||window||localAdb||proxy;
            boolean needed=configured&&(window||localAdb||proxy||guard.preferences.getBoolean("observeHome",true));
            boolean authorized=proxy||localAdb?true:window?LegacyWindowHomeGuard.authorized(context):guard.serviceEnabled()||HomeGuardUsageService.connected();
            boolean ready=!paused&&(proxy?new HomeProxy(context).ready():window?LegacyWindowHomeGuard.enabled(context)&&LegacyWindowHomeGuardService.connected():localAdb?LegacyAdbHomeGuard.enabled(context)&&LegacyAdbHomeGuardService.running()&&management!=null&&management.adb.connected():guard.observationReady());
            String problem=paused?"":window||localAdb?"":configured?routes.configurationProblem():"";
            JSONObject takeover=store.get().optJSONObject("takeover");
            boolean review=takeover!=null&&!Arrays.asList("applied","restored","rolled-back").contains(takeover.optString("state"));
            String homeRoute=routes.home();
            if(!paused&&needed)review|=window?!LegacyWindowHomeGuard.enabled(context):localAdb?!LegacyAdbHomeGuard.enabled(context):
                !routes.current()||routes.proxyUpgradePending()||!guard.validSelection()
                ||"DIRECT".equals(homeRoute)&&!actual.isOwn(context)||proxy&&!actual.isProxy(context)
                ||!Arrays.asList("DIRECT","RELAY").contains(homeRoute);
            boolean builtInGuard=!paused&&needed&&!window&&!localAdb&&!proxy&&guard.enabled();
            boolean bindingChanged=builtInGuard&&HomeGuardRecovery.bindingBlocked(context);
            boolean repairable=builtInGuard&&(!ready||bindingChanged)&&!review
                    &&actual.known&&actual.ownUsable&&guard.recoveryReady()&&!guard.helperActive()
                    &&("DIRECT".equals(homeRoute)||actual.actual.equals(guard.selected()));
            review|=bindingChanged;
            if(review&&problem.isEmpty())problem="原有桌面配置或恢复记录需要核对，请通过电脑继续原任务。";
            ready&=!review;
            for(String scene:new String[]{"home","reboot","coldBoot","wake"})verification.put(scene,routes.test(scene));
            String homeLabel="其他桌面";if(actual.known&&!actual.actual.isEmpty())try{homeLabel=context.getPackageManager().getActivityInfo(ComponentName.unflattenFromString(actual.actual),0).loadLabel(context.getPackageManager()).toString();}catch(PackageManager.NameNotFoundException|SecurityException ignored){}
            return r.put("homeOwn",actual.isOwn(context)).put("homeKnown",actual.known).put("systemHomeLabel",homeLabel).put("systemHome",actual.actual).put("quietUsable",actual.ownUsable)
                .put("paused",paused).put("configured",configured).put("guardNeeded",needed).put("guardReady",ready)
                .put("authorized",authorized).put("routeValid",window||localAdb||routes.current()).put("needsReview",review).put("problem",problem)
                .put("externalHelper",guard.helperActive()).put("proxyPausePending",guard.preferences.getBoolean("proxyPausePending",false))
                .put("verification",verification).put("canVerify",routes.current()&&!window&&!localAdb)
                .put("systemSelection",MaintenanceHelpActivity.homeSettingsIntent(context)!=null)
                .put("guardSetup",configured?null:new DesktopGuardSetup(context).status(guard,actual))
                .put("guardRepair",repairable?new DesktopGuardSetup(context).repairStatus(guard):null)
                .put("managementConnected",management!=null&&management.adb.connected());
        }
        private JSONObject preferences(String[] keys)throws JSONException{JSONObject values=new JSONObject();Map<String,?> all=guard.preferences.getAll();for(String key:keys)values.put(key,all.containsKey(key)?all.get(key):JSONObject.NULL);return values;}
        private String original()throws Exception {
            String found="";for(String value:new String[]{store.originalHome(),guard.selected(),guard.preferences.getString("proxyOriginal",""),LegacyWindowHomeGuard.original(context),LegacyAdbHomeGuard.original(context)}){
                if(value.isEmpty())continue;ComponentName name=ComponentName.unflattenFromString(value);if(name==null)throw new IOException("原桌面组件记录无效。");value=name.flattenToString();if(!found.isEmpty()&&!found.equals(value))throw new IOException("原桌面记录不一致，请通过电脑核对恢复记录。");found=value;
            }
            return found;
        }
        public JSONObject read()throws Exception {
            if(!HomeGuardState.unlocked(context))throw new IOException("请解锁电视后再切换桌面。");
            String stock=original();String firmware=guard.preferences.getString("firmware",Build.FINGERPRINT);if(!firmware.isEmpty()&&!Build.FINGERPRINT.equals(firmware))throw new IOException("固件已变化，请先核对原桌面接管配置。 ");ComponentName component=ComponentName.unflattenFromString(stock);
            if(component==null||component.getPackageName().equals(context.getPackageName())||home.quiet.proxy.ProxyContract.isPackage(component.getPackageName())||HomeGuardPolicy.excludedHome(component.getPackageName()))throw new IOException("没有可核对的原桌面记录。");
            PackageManager pm=context.getPackageManager();ActivityInfo info=pm.getActivityInfo(component,HomeController.METADATA);ApplicationInfo app=info.applicationInfo;
            if(!info.exported||app==null||!AppAvailability.installed(pm,app)||(app.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)throw new IOException("原桌面不是当前用户已安装的系统桌面，请核对恢复记录。");
            int packageSetting=pm.getApplicationEnabledSetting(app.packageName),componentSetting=pm.getComponentEnabledSetting(component);
            HomeController.State home=new HomeController(context).inspect();HomeProxy proxy=new HomeProxy(context);boolean throughProxy=new DesktopRoutes(context,false).proxySelected()&&home.isProxy(context);if(home.isProxy(context)&&!throughProxy)throw new IOException("HOME 代理与接管记录不一致，请先核对。 ");
            boolean retained=!home.known&&((LegacyAdbHomeGuard.configured(context)&&LegacyAdbHomeGuard.usable(context,stock))
                ||(LegacyWindowHomeGuard.configured(context)&&LegacyWindowHomeGuard.usable(context,stock)&&Build.FINGERPRINT.equals(guard.preferences.getString("windowGuardFirmware",""))&&pm.getPackageInfo(app.packageName,0).versionCode==guard.preferences.getInt("windowGuardVersion",-1))
                ||("RELAY".equals(guard.preferences.getString("routeHome",""))&&guard.preferences.getBoolean("observeHome",true)&&guard.validSelection()&&new DesktopRoutes(context,false).current()));
            if((!home.known&&!retained)||(home.known&&!home.isOwn(context)&&!home.isProxy(context)&&!home.actual.equals(stock)))throw new IOException("当前 HOME 不属于已记录的新旧桌面，请先核对系统默认桌面。 ");
            ShellCapabilities commands=adb!=null&&adb.connected()?adb.capabilities:ShellCapabilities.NONE;
            PackageInfo version=pm.getPackageInfo(app.packageName,HomeController.METADATA);
            boolean componentEnabled=info.enabled&&(componentSetting==0||componentSetting==1),packageEnabled=app.enabled&&(packageSetting==0||packageSetting==1);
            boolean depends=retained||throughProxy||"RELAY".equals(guard.preferences.getString("routeHome",""))||!BuildConfig.DECLARES_HOME||LegacyWindowHomeGuard.configured(context)||LegacyAdbHomeGuard.configured(context)||home.actual.equals(stock);
            JSONObject baseline=load();
            int stockPriority=Integer.MIN_VALUE,quietPriority=Integer.MIN_VALUE;ComponentName quiet=new ComponentName(context,HomeActivity.class);
            for(ResolveInfo candidate:pm.queryIntentActivities(HomeController.homeIntent(),HomeController.METADATA))if(candidate.activityInfo!=null){ComponentName name=new ComponentName(candidate.activityInfo.packageName,candidate.activityInfo.name);if(name.equals(component))stockPriority=candidate.priority;if(name.equals(quiet))quietPriority=candidate.priority;}
            boolean requiresDisabled=!depends&&stockPriority>quietPriority;
            int restorePackage=baseline!=null&&baseline.optJSONObject("baseline")!=null&&!"quiet".equals(baseline.optString("phase"))?baseline.getJSONObject("baseline").getInt("packageSetting"):packageSetting;
            int restoreComponent=baseline!=null&&baseline.optJSONObject("baseline")!=null&&!"quiet".equals(baseline.optString("phase"))?baseline.getJSONObject("baseline").getInt("componentSetting"):componentSetting;
            return new JSONObject().put("original",stock).put("label",info.loadLabel(pm).toString()).put("version",Build.VERSION.SDK_INT>=28?version.getLongVersionCode():version.versionCode)
                .put("user",android.os.Process.myUid()/100000).put("fingerprint",Build.FINGERPRINT).put("guardKey",PhoneSettingsState.revision(preferences(IDENTITY_KEYS))).put("guardIdentity",preferences(IDENTITY_KEYS)).put("guard",preferences(GUARD_KEYS))
                .put("home",home.actual).put("homeKnown",home.known).put("retainedHome",retained).put("stockHome",throughProxy||retained?home.actual:stock).put("packageSetting",packageSetting).put("componentSetting",componentSetting)
                .put("packageEnabled",packageEnabled).put("componentEnabled",componentEnabled).put("stockEnabled",packageEnabled&&componentEnabled).put("dependsOnStock",depends).put("proxy",throughProxy).put("requiresStockDisabled",baseline!=null&&!"quiet".equals(baseline.optString("phase"))?baseline.getJSONObject("baseline").optBoolean("requiresStockDisabled"):requiresDisabled).put("externalHelper",guard.helperActive())
                .put("routeLabel",baseline!=null&&!"quiet".equals(baseline.optString("phase"))?baseline.getJSONObject("baseline").optString("routeLabel"):throughProxy?"HOME 代理中转":depends?"原桌面中转":"直接 HOME").put("canHome",!commands.home.isEmpty()&&!commands.resolve.isEmpty()).put("canEnable",commands.enable)
                .put("canRestorePackage",!commands.enabledCommand(restorePackage).isEmpty()).put("canRestoreComponent",!commands.enabledCommand(restoreComponent).isEmpty())
                .put("canDisable",commands.disable).put("disableAllowed",SetupPresets.load(context).home().equals(stock)
                    && SetupPresets.load(context).homeAllows("disableVerified")&&SetupPresets.load(context).homeIdentityMatches(Build.VERSION.SDK_INT>=28?version.getLongVersionCode():version.versionCode,android.os.Process.myUid()/100000)
                    && !guard.protects(app.packageName));
        }
        private String resolve(AdbController client)throws Exception {
            if(client.capabilities.resolve.isEmpty())throw new IOException("系统没有可核对的 HOME 查询命令。");String result=HomeController.parseComponent(command(client,client.capabilities.resolve+" --brief --user "+client.user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));if(result.isEmpty())throw new IOException("系统 HOME 查询结果不明确。");return ComponentName.unflattenFromString(result).flattenToString();
        }
        public void enabled(boolean component,int value)throws Exception {
            AdbController client=shell();JSONObject before=read();String stock=before.getString("original"),pkg=ComponentName.unflattenFromString(stock).getPackageName();
            AppPackageState actual=AppPackageState.parse(command(client,"dumpsys package "+quote(pkg)),client.user);if(!actual.installed||actual.version!=before.getLong("version")||actual.enabled!=before.getInt("packageSetting"))throw new IOException("原桌面包状态已变化，请重新核对。");
            String command=client.capabilities.enabledCommand(value);if(command.isEmpty())throw new IOException("系统不支持恢复此启用状态。");command(client,command+" --user "+client.user+" "+quote(component?stock:pkg));
            client.validateUser();AppPackageState after=AppPackageState.parse(command(client,"dumpsys package "+quote(pkg)),client.user);if(after.version!=actual.version||!after.installed||(!component&&after.enabled!=value))throw new IOException("原桌面包状态写入后未确认。");
            if(component&&context.getPackageManager().getComponentEnabledSetting(ComponentName.unflattenFromString(stock))!=value)throw new IOException("原桌面组件状态写入后未确认。");
        }
        public void home(String target,boolean deferred)throws Exception {
            AdbController client=shell();JSONObject r=load(),b=r.getJSONObject("baseline");if(!(target.equals(b.getString("home"))||target.equals(b.getString("stockHome"))))throw new IOException("HOME 目标不属于本次切换记录。");
            if(!new HomeController(context).component(target,true))throw new IOException("目标 HOME 组件不可用。");String before=resolve(client);if(!before.equals(b.getString("home"))&&!before.equals(b.getString("stockHome")))throw new IOException("系统 HOME 已被其他操作改变。");
            if(client.capabilities.home.isEmpty())throw new IOException("系统没有 HOME 切换命令。");command(client,client.capabilities.home+" --user "+client.user+" "+quote(target));client.validateUser();String after=resolve(client);if(!after.equals(target)&&!(deferred&&b.optBoolean("requiresStockDisabled")&&!b.optBoolean("dependsOnStock")&&target.equals(new ComponentName(context,HomeActivity.class).flattenToString())&&after.equals(b.getString("stockHome"))))throw new IOException("HOME 切换结果未确认。");
        }
        public void pause()throws Exception {ui(()->{if(!guard.pause("用户暂停桌面接管"))throw new IOException("暂停接管未保存。");if(guard.preferences.getBoolean("proxyPausePending",false))throw new IOException("中转暂停尚未确认，请核对后再切换。");return null;});}
        public void resume(JSONObject baseline)throws Exception {
            ui(()->{if(guard.helperActive())throw new IOException("独立中转助手状态已变化，请先核对原方案。");JSONObject values=baseline.getJSONObject("guard");SharedPreferences.Editor editor=guard.preferences.edit();for(String key:GUARD_KEYS){Object value=values.opt(key);if(value==null||value==JSONObject.NULL)editor.remove(key);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else editor.putString(key,(String)value);}BootAppStore.commit(guard.preferences,editor);
                if(baseline.optBoolean("proxy")&&!"OFF".equals(guard.preferences.getString("mode","AUTO"))){String problem=new HomeProxy(context).resume();if(!problem.isEmpty())throw new IOException(problem);}
                if(values.optBoolean("vendorBoot")){String problem=new DesktopRoutes(context,false).configureVendor();if(!problem.isEmpty())throw new IOException(problem);}
                if(!guard.preferences.getString("mode","AUTO").equals(values.optString("mode","AUTO"))||guard.preferences.getBoolean("adbGuardEnabled",false)!=values.optBoolean("adbGuardEnabled"))throw new IOException("接管开关恢复后未确认。");
                HomeGuardSync.refresh(context);HomeGuardUsageService.ensure(context);LegacyAdbHomeGuard.updateBootReceiver(context);LegacyAdbHomeGuard.refresh(context);HomeGuardAdb.ensure(context);if(guard.automatic())HomeGuardRecovery.ensureBinding(context,"用户切回 Quiet TV");return null;
            });
        }
        public void launch(boolean stock)throws Exception {ui(()->{Intent intent=stock?new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(read().getString("original"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK):HomeController.launchIntent(context);if(!ExternalActivityLauncher.open(context,"desktop.switch",intent))throw new IOException("桌面显示请求失败，请核对实际画面。");return null;});}
    }
}
