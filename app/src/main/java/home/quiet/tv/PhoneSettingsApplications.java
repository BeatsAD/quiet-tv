package home.quiet.tv;

import android.content.*;
import android.net.Uri;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** The same current-user repository, protection rules and management connection used by the TV. */
final class PhoneSettingsApplications {
    private final Context context;private final PhoneSettingsTasks tasks;private final String scope;private final Runnable changed;private final PhoneSettingsParent parent;
    private PhoneSettingsServer.Active lastActive;private JSONArray description=new JSONArray();
    private List<AppRepository.App> apps=Collections.emptyList();private long revision;private boolean loaded;private String problem="",attemptedState="";
    PhoneSettingsApplications(Context context,PhoneSettingsTasks tasks,String scope,Runnable changed,PhoneSettingsParent parent){this.context=context;this.tasks=tasks;this.scope=scope;this.changed=changed;this.parent=parent;}
    private AdbController adb(){ManagementAdb management=ManagementAdb.existing();return management==null?null:management.adb;}
    private String presentation()throws IOException{return PhoneSettingsPreview.get(context).signature().toString()+new DockStore(context).load().toString()+new BootAppStore(context).target();}
    private String readKey()throws IOException{return presentation()+":"+(adb()==null?-1:adb().generation())+":"+(adb()!=null&&adb().connected());}
    synchronized JSONObject state()throws JSONException,IOException{long packages=PhoneSettingsPreview.get(context).signature().optLong("packages");String current=presentation();if(loaded&&lastActive!=null&&lastActive.valid()&&!readKey().equals(attemptedState))refresh(lastActive);List<String> ids=new ArrayList<>();for(AppRepository.App app:apps)ids.add(app.pkg);Set<String> protectedApps=new ChildModeStore(context).uninstallParentPackages(ids);JSONArray parentApps=new JSONArray();for(String id:ids)if(protectedApps.contains(id))parentApps.put(id);return new JSONObject().put("packageRevision",packages).put("presentation",current).put("appsRevision",revision).put("namesRevision",new AppNameStore(context).signature()).put("appsLoaded",loaded).put("appsProblem",problem).put("managementConnected",adb()!=null&&adb().connected()).put("uninstallParentApps",parentApps);}
    synchronized JSONArray entries()throws IOException{try{JSONArray result=new JSONArray(description.toString());AppNameStore names=new AppNameStore(context);for(int i=0;i<result.length();i++){JSONObject item=result.getJSONObject(i);String pkg=item.getString("id"),alias=names.alias(pkg),original=item.getString("originalName");item.put("name",names.display(pkg,original)).put("customName",alias).put("renamed",!alias.isEmpty());}return result;}catch(JSONException e){throw new IOException(e);}}
    private JSONArray describe(List<AppRepository.App> apps,long version)throws IOException{
        JSONArray values=new JSONArray();AppOperations operations=new AppOperations(context);HomeVisibilityStore visibility=new HomeVisibilityStore(context);DockStore dock=new DockStore(context);List<String> pinned=dock.load();int dockCount=dock.loadAvailable().size();ChildModeStore children=new ChildModeStore(context);Set<String> launchable=new HomeAppCatalog(context).launchable().keySet();
        try{for(AppRepository.App app:apps){
            boolean own=AppCoverPolicy.ownReadOnly(context,app.pkg),hidden=visibility.hidden(app.pkg),childHidden=children.hidden(app.pkg);String protection=operations.protection(app);JSONArray actions=new JSONArray();
            if(AppNameStore.allowed(context,app))add(actions,"rename","重命名",false,"");
            if(app.installed&&app.enabled&&!own){add(actions,"open","打开应用",false,childHidden||hidden?"请先恢复应用显示":"");if(launchable.contains(app.pkg)){add(actions,"cover","更换封面",false,"");add(actions,"restoreCover","恢复默认封面",false,AppCoverStore.get(context).custom(app.pkg)?"":"当前使用默认封面");}
                if(launchable.contains(app.pkg))add(actions,"boot","设为开机应用",false,"");
                String hideReason=operations.hidingProtection(app);if(hideReason.isEmpty())add(actions,hidden?"show":"hide",hidden?"恢复显示":"隐藏应用",false,childHidden?"请在儿童模式中恢复此应用":"");
                boolean pinnedNow=pinned.contains(app.pkg);add(actions,pinnedNow?"dockRemove":"dockAdd",pinnedNow?"移除首页位置":"添加到首页",false,!pinnedNow&&(childHidden||hidden)?"请先恢复应用显示":!pinnedNow&&dockCount>=dock.capacity()?"Dock 已满":"");
            }
            for(AppOperations.Action action:operations.actions(app)){add(actions,action.name(),action.label,true,"");actions.getJSONObject(actions.length()-1).put("explanation",operations.explanation(app,action));}
            if((adb()==null||!adb().connected())&&!app.system&&app.installed&&protection.isEmpty())add(actions,"systemUninstall","卸载应用",true,"");
            String encoded=java.net.URLEncoder.encode(app.pkg,"UTF-8");JSONObject item=new JSONObject().put("id",app.pkg).put("name",app.label).put("originalName",app.originalName).put("canRename",AppNameStore.allowed(context,app)).put("version",app.version).put("apkBytes",app.apkSizeBytes).put("system",app.system).put("installed",app.installed).put("recoverable",app.recoverable).put("verified",app.verified).put("hidden",hidden).put("childHidden",childHidden).put("disabled",app.installed&&!app.enabled).put("protected",!protection.isEmpty()).put("protection",protection).put("icon","api/resources/icon/"+encoded+"?v="+version).put("actions",actions);
            values.put(item);
        }return values;}catch(JSONException e){throw new IOException(e);}
    }
    private static void add(JSONArray actions,String id,String title,boolean danger,String reason)throws JSONException{actions.put(new JSONObject().put("id",id).put("title",title).put("danger",danger).put("reason",reason));}
    private void load()throws Exception{
        String state=readKey();
        try{List<AppRepository.App> next=new AppRepository(context).load(adb());long nextRevision;synchronized(this){nextRevision=revision+1;}JSONArray details=describe(next,nextRevision);synchronized(this){apps=next;description=details;attemptedState=state;revision=nextRevision;loaded=true;problem="";}changed.run();}
        catch(Exception failure){synchronized(this){problem=loaded?"应用目录刷新未完成，请重试":"应用列表读取未完成，请重试";revision++;}changed.run();throw failure;}
    }
    synchronized JSONObject refresh(PhoneSettingsServer.Active active)throws IOException{lastActive=active;
        PhoneSettingsTasks.Job job=tasks.begin(scope+"/apps/refresh","读取应用列表","refresh",active);if(!job.scheduled)attemptedState=readKey();tasks.start(job,work->{work.check();work.stage("正在读取电视应用",-1);ManagementAdb management=ManagementAdb.existing();if(management!=null)management.worker.submit(()->{load();return null;}).get(40,TimeUnit.SECONDS);else load();work.stage("应用列表已更新",100);return new JSONObject();});return tasks.receipt(job);
    }
    private synchronized AppRepository.App find(String pkg){for(AppRepository.App app:apps)if(app.pkg.equals(pkg))return app;return null;}
    private void requireUninstallParent(String action,String pkg,PhoneSettingsServer.Active active)throws IOException{
        if((action.equals("UNINSTALL")||action.equals("systemUninstall"))&&new ChildModeStore(context).uninstallNeedsParent(pkg))parent.require(active);
    }
    JSONObject rename(JSONObject value,PhoneSettingsServer.Active active)throws IOException{
        if(value.length()!=3||!(value.opt("package") instanceof String)||!(value.opt("expectedAlias") instanceof String))throw new PhoneSettingsServer.BadInput();
        String pkg=value.optString("package"),original=AppNameStore.original(context,pkg);Object raw=value.opt("name");String name=original.equals(raw)?original:AppNameStore.normalize(raw);
        AppNameStore names=new AppNameStore(context);boolean renamed=names.save(pkg,value.optString("expectedAlias"),name,original,active);if(renamed)changed.run();
        try{return new JSONObject().put("package",pkg).put("name",names.display(pkg,original)).put("renamed",!names.alias(pkg).isEmpty()).put("persisted",true);}catch(JSONException e){throw new IOException(e);}
    }
    JSONObject perform(String action,JSONObject value,PhoneSettingsServer.Active active)throws IOException{
        if(value.length()!=1||!(value.opt("package") instanceof String))throw new PhoneSettingsServer.BadInput();String pkg=value.optString("package");AppRepository.App selected=find(pkg);if(selected==null)throw new PhoneSettingsServer.Rejected("应用列表已变化，请刷新后重试");
        try{
            JSONArray list=entries();boolean allowed=false;for(int i=0;i<list.length();i++){JSONObject app=list.getJSONObject(i);if(!pkg.equals(app.getString("id")))continue;JSONArray options=app.getJSONArray("actions");for(int k=0;k<options.length();k++){JSONObject option=options.getJSONObject(k);if(action.equals(option.getString("id"))&&option.optString("reason").isEmpty())allowed=true;}}
            if(!allowed)throw new PhoneSettingsServer.Rejected("当前应用状态不允许此操作，请刷新核对");
        }catch(JSONException e){throw new IOException(e);}
        requireUninstallParent(action,pkg,active);PhoneSettingsTasks.Job job=tasks.begin(scope+"/apps/"+pkg,"应用操作",action,active);
        tasks.start(job,work->{
            work.check();work.stage("正在核对应用状态",-1);
            try{
                requireUninstallParent(action,pkg,work::active);
                AppRepository.App current=new AppRepository(context).find(pkg);if(current==null||current.versionCode!=selected.versionCode||current.installed!=selected.installed||current.enabled!=selected.enabled)throw new PhoneSettingsTasks.Problem("应用状态已变化，请刷新后重新确认",false);
                if(Arrays.asList("ENABLE","DISABLE","UNINSTALL","RESTORE").contains(action)){
                    ManagementAdb management=ManagementAdb.existing();if(management==null||!management.adb.connected())throw new PhoneSettingsTasks.Problem("请先在电视连接应用管理",false);
                    String message;try{message=management.worker.submit(()->new AppOperations(context).perform(management.adb,selected,AppOperations.Action.valueOf(action),()->{requireUninstallParent(action,pkg,work::active);return work.active();})).get(60,TimeUnit.SECONDS);}catch(ExecutionException e){if(e.getCause() instanceof PhoneSettingsServer.Rejected||e.getCause() instanceof PhoneSettingsServer.Ended)throw new PhoneSettingsTasks.Problem(e.getCause().getMessage(),false);throw new PhoneSettingsTasks.Problem("应用操作结果待核对，请刷新列表核对，不要重复操作",true);}catch(Exception e){throw new PhoneSettingsTasks.Problem("应用操作结果待核对，请刷新列表核对，不要重复操作",true);}
                    load();work.stage(message,100);return new JSONObject().put("verified",true);
                }
                work.check();String message;
                if(action.equals("systemUninstall")){new PhoneSettingsActions(context).onMain(()->{requireUninstallParent(action,pkg,work::active);Intent intent=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);if(!ExternalActivityLauncher.open(context,"phone.app.uninstall",intent))throw new PhoneSettingsTasks.Problem("电视系统卸载入口不可用",false);return true;},work::active);message="已打开电视卸载确认，请完成后刷新应用列表";}
                else if(action.equals("open")){
                    work.check();new PhoneSettingsActions(context).openApp(pkg,work::active);message="已请求打开应用，家长限制和答题按电视规则执行";
                }else if(action.equals("restoreCover")){AppCoverPolicy.require(context,pkg);work.check();AppCoverStore.get(context).restore(pkg);message="已恢复默认封面";}
                else if(action.equals("boot")){work.check();new BootAppStore(context).select(pkg,current.label);message="已设置开机应用，未立即启动";}
                else synchronized(DesktopSettingsStore.MUTATIONS){work.check();HomeVisibilityStore visibility=new HomeVisibilityStore(context);
                    if(action.equals("hide")||action.equals("show")){String reason=new AppOperations(context).hidingProtection(current);if(!reason.isEmpty())throw new PhoneSettingsTasks.Problem(reason,false);if(new ChildModeStore(context).hidden(pkg))throw new PhoneSettingsTasks.Problem("请先在儿童模式恢复显示",false);if(!(action.equals("hide")?visibility.hide(pkg):visibility.restore(pkg)))throw new PhoneSettingsTasks.Problem("显示设置保存未确认",true);message=action.equals("hide")?"应用已隐藏":"已恢复显示";}
                    else{if(!action.equals("dockRemove")&&(visibility.hidden(pkg)||new ChildModeStore(context).hidden(pkg)))throw new PhoneSettingsTasks.Problem("请先恢复显示",false);DockStore dock=new DockStore(context);List<String> pinned=dock.load();if(action.equals("dockRemove"))pinned.remove(pkg);else if(action.equals("dockAdd")){if(dock.loadAvailable().size()>=dock.capacity()||pinned.size()>=DockStore.CAPACITY)throw new PhoneSettingsTasks.Problem("Dock 已满",false);if(!pinned.contains(pkg))pinned.add(pkg);}else throw new PhoneSettingsTasks.Problem("应用操作无效",false);if(!dock.saveOrder(pinned))throw new PhoneSettingsTasks.Problem("Dock 保存未确认",true);message=action.equals("dockRemove")?"已移除首页位置":"已添加到首页";}
                }
                if(!action.equals("open")&&!action.equals("systemUninstall"))load();changed.run();work.stage(message,100);return new JSONObject().put("package",pkg).put("waitingTelevision",action.equals("systemUninstall"));
            }catch(PhoneSettingsTasks.Problem e){throw e;}catch(PhoneSettingsServer.Rejected e){throw new PhoneSettingsTasks.Problem(e.getMessage(),false);}catch(InterruptedIOException e){throw e;}catch(Exception e){throw new PhoneSettingsTasks.Problem("应用操作结果待核对，请刷新电视状态",true);}
        });return tasks.receipt(job);
    }
}
