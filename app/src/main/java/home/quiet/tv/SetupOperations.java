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
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private final Context context;
    private final HomeController home;
    private final SetupStore store;
    private final AppRepository repository;
    private final AppOperations operations;
    SetupOperations(Context context){this(context,SetupPresets.load(context));}
    SetupOperations(Context context,SetupPresets presets){this.context=context;home=new HomeController(context,presets);store=new SetupStore(context);repository=new AppRepository(context);operations=new AppOperations(context,presets);}
    void execute(AdbController adb,List<AppRepository.App> selected,AppRepository.App original,String choice,Ticket ticket,Progress progress) throws Exception {
        if(!RUNNING.compareAndSet(false,true))throw new IOException("已有设置操作正在执行");
        String id=UUID.randomUUID().toString();boolean begun=false;
        try{
            if(!Arrays.asList("KEEP","DISABLE","UNINSTALL").contains(choice))throw new IOException("原桌面操作选择无效");
            ticket.check();HomeController.State current=home.inspect(adb);
            if(!current.isOwn(context) || !current.ownUsable)throw new IOException("默认桌面已改变或未确认，请返回第一屏");
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
                try{String result=operations.perform(adb,original,action,id,ticket,true);store.result(id,original.pkg,action.name(),result);}
                catch(Exception e){store.result(id,original.pkg,action.name(),"未完成或结果未确认");throw e;}
            }
            ticket.check();if(!home.inspect(adb).isOwn(context))throw new IOException("默认桌面状态未确认");
            store.end(id,"complete");store.put("status","complete");
        }catch(Exception e){if(begun)store.end(id,ticket.active()?"incomplete":"cancelled");throw e;}
        finally{RUNNING.set(false);}
    }
    void restore(AdbController adb,Ticket ticket,Progress progress) throws Exception {
        if(!RUNNING.compareAndSet(false,true))throw new IOException("已有设置操作正在执行");
        String id=UUID.randomUUID().toString();boolean begun=false;
        try{
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
        finally{RUNNING.set(false);}
    }
    private JSONObject state(AppRepository.App app) throws JSONException {return new JSONObject().put("package",app.pkg).put("version",app.version).put("installed",app.installed).put("enabled",app.enabled).put("enabledSetting",app.enabledSetting);}
}
