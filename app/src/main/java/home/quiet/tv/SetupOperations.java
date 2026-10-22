package home.quiet.tv;

import android.content.Context;
import org.json.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One confirmed selection, sequential writes, and readback; never replayed after interruption. */
final class SetupOperations {
    static final class Ticket {
        private final AtomicBoolean cancelled=new AtomicBoolean();
        void cancel(){cancelled.set(true);}
        boolean active(){return !cancelled.get();}
        void check() throws IOException {if(!active())throw new IOException("已停止后续操作，请重新查询状态");}
    }
    interface Progress {void changed(String value);}
    private final Context context;
    private final HomeController home;
    private final SetupStore store;
    private final AppRepository repository;
    private final AppOperations operations;
    SetupOperations(Context context){this(context,SetupPresets.load(context));}
    SetupOperations(Context context,SetupPresets presets){this.context=context;home=new HomeController(context,presets);store=new SetupStore(context);repository=new AppRepository(context);operations=new AppOperations(context,presets);}
    void execute(AdbController adb,List<AppRepository.App> selected,AppRepository.App original,String choice,Ticket ticket,Progress progress) throws Exception {
        AppOperations.begin();
        String id=UUID.randomUUID().toString();boolean begun=false,originalAttempted=false;
        try{
            if(!Arrays.asList("KEEP","DISABLE","UNINSTALL").contains(choice))throw new IOException("原桌面操作选择无效");
            ticket.check();HomeController.State current=home.inspect(adb);
            if(!"KEEP".equals(choice) && original!=null && new HomeGuardState(context).protects(original.pkg))throw new IOException("主页守护依赖此原桌面，请保留");
            if(!current.isOwn(context) || !current.ownUsable)throw new IOException("默认桌面已改变或未确认，请返回第一屏");
            // Choices may be made before authorization. Validate the entire frozen selection before the first write.
            if(!selected.isEmpty() || !"KEEP".equals(choice)){
                adb.validateUser();Map<String,AppRepository.App> actual=new HashMap<>();for(AppRepository.App app:repository.load(adb))actual.put(app.pkg,app);
                for(AppRepository.App app:selected){AppRepository.App now=actual.get(app.pkg);if(!unchanged(app,now) || !home.presets.recommended(app.pkg) || !operations.actions(now).contains(AppOperations.Action.DISABLE))throw new IOException("应用状态或推荐资格已变化，请重新选择");}
                if(!"KEEP".equals(choice)){
                    AppRepository.App now=original==null?null:actual.get(original.pkg);
                    if(!unchanged(original,now) || !now.installed || !now.enabled || !current.original.startsWith(now.pkg+"/") || !home.presets.homeAllows("DISABLE".equals(choice)?"disableVerified":"uninstallVerified") || ("UNINSTALL".equals(choice) && !home.presets.homeAllows("restoreInstallVerified")))throw new IOException("原桌面状态或操作资格已变化，请重新选择");
                }
            }
            home.remember(current);
            JSONArray baseline=new JSONArray();for(AppRepository.App app:selected)baseline.put(state(app));
            store.begin(id,new JSONObject().put("user",adb.connected()?adb.user:android.os.Process.myUid()/100000).put("home",current.actual).put("apps",baseline).put("original",original==null?JSONObject.NULL:state(original)).put("choice",choice));begun=true;
            if(original!=null && !"KEEP".equals(choice) && !store.get().has("originalState"))store.put("originalState",state(original));
            for(AppRepository.App app:selected){
                ticket.check();if(!home.inspect(adb).isOwn(context))throw new IOException("默认桌面已改变，后续操作已停止");
                if(!home.presets.recommended(app.pkg))throw new IOException("推荐规则已改变，请刷新");
                progress.changed("正在禁用 · "+app.label);
                try{String result=operations.perform(adb,app,AppOperations.Action.DISABLE,id,ticket,false);store.result(id,app.pkg,"DISABLE",result);}
                catch(Exception e){store.result(id,app.pkg,"DISABLE","未完成或结果未确认");throw e;}
            }
            ticket.check();
            if(!"KEEP".equals(choice)){
                if(original==null)throw new IOException("原桌面身份未确认");
                AppOperations.Action action="DISABLE".equals(choice)?AppOperations.Action.DISABLE:AppOperations.Action.UNINSTALL;
                progress.changed("正在"+action.label+"原桌面");
                originalAttempted=true;
                try{String result=operations.perform(adb,original,action,id,ticket,true);store.result(id,original.pkg,action.name(),result);}
                catch(Exception e){store.result(id,original.pkg,action.name(),"未完成或结果未确认");throw e;}
                progress.changed("正在重新设置系统 HOME…");
                ticket.check();home.setOwnAndVerify(adb);
                store.result(id,original.pkg,"HOME","已重新设置并核对系统 HOME；遥控器及重启需另行确认");
            }
            progress.changed("正在核对默认桌面…");
            ticket.check();if(!home.inspect(adb).isOwn(context))throw new IOException("默认桌面状态未确认");
            store.end(id,"complete");store.put("status","complete");
        }catch(Exception e){
            if(originalAttempted && "DISABLE".equals(choice) && ticket.active() && adb.connected()){
                try{rollbackOriginal(adb,original,id);}
                catch(Exception rollback){e.addSuppressed(rollback);store.result(id,original.pkg,"ROLLBACK","恢复未确认，请重新连接查询");}
            }
            if(begun)store.end(id,ticket.active()?"incomplete":"cancelled");throw e;
        }
        finally{AppOperations.end();}
    }
    private void rollbackOriginal(AdbController adb,AppRepository.App before,String id) throws Exception {
        adb.validateUser();
        if(before==null || !before.installed || !before.enabled || !home.presets.home().startsWith(before.pkg+"/")
                || !home.presets.homeAllows("disableVerified"))throw new IOException("缺少可核验的原桌面恢复基线");
        AppRepository.App now=repository.find(adb,before.pkg);
        if(now==null || !now.installed || !now.version.equals(before.version) || now.enabledSetting!=3)return;
        if(before.enabledSetting!=0 && before.enabledSetting!=1)throw new IOException("原桌面启用基线不明确");
        adb.shell("pm "+(before.enabledSetting==0?"default-state":"enable")+" --user "+adb.user+" "+before.pkg);
        AppRepository.App restored=repository.find(adb,before.pkg);
        if(restored==null || !restored.enabled || restored.enabledSetting!=before.enabledSetting)throw new IOException("原桌面启用状态未恢复");
        if(!home.inspect(adb).isOwn(context))home.switchToOriginal(adb,home.presets.home());
        store.result(id,before.pkg,"ROLLBACK","已恢复本次操作前的原桌面启用状态并核对 HOME");
    }
    void restore(AdbController adb,Ticket ticket,Progress progress) throws Exception {
        AppOperations.begin();
        String id=UUID.randomUUID().toString();boolean begun=false;
        try{
            new HomeGuardState(context).pause("恢复原桌面，守护已暂停");
            ticket.check();adb.validateUser();String original=store.originalHome();
            if(original.isEmpty() || !original.equals(home.presets.home()) || !home.component(original,false))throw new IOException("原桌面记录缺失或当前固件不匹配");
            String pkg=android.content.ComponentName.unflattenFromString(original).getPackageName();AppRepository.App app=repository.find(adb,pkg);
            if(app==null || !app.metadataKnown)throw new IOException("系统安装包或原桌面元数据缺失，无法恢复");
            store.begin(id,new JSONObject().put("user",adb.user).put("original",state(app)).put("choice","RESTORE_HOME"));begun=true;
            if(!app.installed){ticket.check();progress.changed("正在恢复原桌面安装");operations.perform(adb,app,AppOperations.Action.RESTORE,id,ticket,true);store.result(id,pkg,"RESTORE","已核对安装");app=repository.find(adb,pkg);}
            if(app==null || !app.installed)throw new IOException("原桌面安装未恢复");
            if(!app.enabled){ticket.check();progress.changed("正在启用原桌面");operations.perform(adb,app,AppOperations.Action.ENABLE,id,ticket,true);store.result(id,pkg,"ENABLE","已核对启用");}
            ticket.check();progress.changed("正在切回原桌面");home.switchToOriginal(adb,original);store.result(id,pkg,"HOME","已核对默认桌面");store.end(id,"complete");
        }catch(Exception e){if(begun)store.end(id,"incomplete");throw e;}
        finally{AppOperations.end();}
    }
    private boolean unchanged(AppRepository.App selected,AppRepository.App actual){return selected!=null && actual!=null && actual.metadataKnown && actual.verified && selected.pkg.equals(actual.pkg) && selected.version.equals(actual.version) && selected.system==actual.system && selected.installed==actual.installed && selected.enabled==actual.enabled && selected.enabledSetting==actual.enabledSetting;}
    private JSONObject state(AppRepository.App app) throws JSONException {return new JSONObject().put("package",app.pkg).put("version",app.version).put("installed",app.installed).put("enabled",app.enabled).put("enabledSetting",app.enabledSetting);}
}
