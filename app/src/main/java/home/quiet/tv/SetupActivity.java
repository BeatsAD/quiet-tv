package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Two native screens. UI choices are snapshots; the worker rechecks every write. */
public final class SetupActivity extends ManagementUi {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<String,Boolean> choices=new LinkedHashMap<>();
    private final ArrayList<AppRepository.App> apps=new ArrayList<>();
    private AdbController adb;
    private AdbConnectionPanel connection;
    private AppRepository repository;
    private AppOperations operations;
    private HomeController home;
    private SetupStore store;
    private SetupOperations.Ticket ticket;
    private HomeController.State homeState;
    private AppRepository.App original;
    private String originalChoice="KEEP",notice="",progress="正在检查设备能力…";
    private boolean originalTouched,busy,active,recovery,roleCancelled,recordAvailable=true;
    private int screen;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);home=new HomeController(this);store=new SetupStore(this);repository=new AppRepository(this);operations=new AppOperations(this);
        adb=new AdbController(this,value->runOnUiThread(()->{if(active && !isDestroyed()){progress=value;if(busy)render();}}));connection=new AdbConnectionPanel(this,adb,worker);
        recovery=getIntent().getBooleanExtra("restore",false);
        header.getChildAt(0).setOnClickListener(v->exit(false));
        try{JSONObject record=store.get(),saved=record.optJSONObject("choices");if(saved!=null)for(Iterator<String> it=saved.keys();it.hasNext();){String pkg=it.next();choices.put(pkg,saved.getBoolean(pkg));}
            originalTouched=record.optBoolean("originalTouched");originalChoice=record.optString("originalChoice","KEEP");
            JSONObject run=record.optJSONObject("run");if(run!=null && !"complete".equals(run.optString("state")))notice="上次设置未完成；已重新查询状态，不会自动重放操作。";
        }catch(Exception e){recordAvailable=false;notice="设置记录无法读取；请先退出并检查记录，未执行任何操作。";}
    }
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onStop(){active=false;cancel();super.onStop();}
    @Override protected void onDestroy(){cancel();adb.close();worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){exit(false);}
    private void cancel(){if(ticket!=null)ticket.cancel();connection.close();adb.disconnect();busy=false;}
    private boolean valid(SetupOperations.Ticket task){return active && task==ticket && task.active() && !isFinishing() && !isDestroyed();}
    private void ui(SetupOperations.Ticket task,Runnable action){runOnUiThread(()->{if(valid(task))action.run();});}
    private SetupOperations.Ticket begin(String text){if(ticket!=null)ticket.cancel();ticket=new SetupOperations.Ticket();busy=true;progress=text;render();return ticket;}
    private void refresh(){
        SetupOperations.Ticket task=begin("正在检查设备能力与应用状态…");
        worker.execute(()->{try{
            task.check();HomeController.State state=home.inspect(adb);List<AppRepository.App> loaded=repository.load(adb);task.check();
            ui(task,()->{homeState=state;apps.clear();apps.addAll(loaded);original=null;
                for(AppRepository.App app:apps)if(!state.original.isEmpty() && state.original.startsWith(app.pkg+"/"))original=app;
                screen=state.isOwn(this) && !roleCancelled?1:0;busy=false;
                for(AppRepository.App app:apps)if(canRecommend(app) && !choices.containsKey(app.pkg))choices.put(app.pkg,true);
                if(!originalTouched)originalChoice=canOriginal("DISABLE")?"DISABLE":"KEEP";
                if(!"KEEP".equals(originalChoice) && !canOriginal(originalChoice))originalChoice="KEEP";
                render();
            });
        }catch(Exception e){ui(task,()->{busy=false;homeState=null;screen=0;notice="状态未确认："+e.getMessage();render();});}});
    }
    private boolean canRecommend(AppRepository.App app){return recordAvailable && app.installed && app.enabled && app.verified && home.presets.recommended(app.pkg) && operations.actions(app).contains(AppOperations.Action.DISABLE);}
    private boolean canOriginal(String action){
        if(!recordAvailable || homeState==null || !homeState.isOwn(this) || !homeState.ownUsable || !adb.connected() || original==null || !original.metadataKnown || !original.verified || !original.installed || !original.enabled)return false;
        return home.presets.homeAllows("DISABLE".equals(action)?"disableVerified":"uninstallVerified") && (!"UNINSTALL".equals(action) || home.presets.homeAllows("restoreInstallVerified"));
    }
    private void persist(){try{JSONObject saved=new JSONObject();for(Map.Entry<String,Boolean> value:choices.entrySet())saved.put(value.getKey(),value.getValue());store.put("choices",saved);store.put("originalChoice",originalChoice);store.put("originalTouched",originalTouched);}catch(Exception e){notice="选择未保存："+e.getMessage();}}
    private Button action(LinearLayout parent,String text,String tag,Runnable run,boolean enabled){Button button=button(text,run);button.setTag(tag);button.setEnabled(enabled);button.setAlpha(enabled?1f:.42f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(40));p.bottomMargin=px(10);parent.addView(button,p);return button;}
    private void render(){
        if(root==null)return;Object focus=getCurrentFocus()==null?null:getCurrentFocus().getTag();
        ((TextView)header.getChildAt(1)).setText(recovery?"恢复原桌面":screen==0?"设为默认桌面":"管理预装应用");
        if(root.getChildCount()>1)root.removeViews(1,root.getChildCount()-1);
        LinearLayout body=column();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,0,1);bp.topMargin=px(22);root.addView(body,bp);
        if(!notice.isEmpty()){TextView note=muted(notice,12);note.setMaxLines(3);body.addView(note,new LinearLayout.LayoutParams(-1,-2));gap(body,12);}
        if(busy){body.addView(center(progress,18),new LinearLayout.LayoutParams(-1,0,1));action(body,"停止后续操作并退出设置","stop",()->exit(false),true);footer("操作结果以实际状态为准");return;}
        if(recovery)renderRecovery(body);else if(screen==0)renderDefault(body);else renderApps(body);
        footer("方向键选择  ·  确定操作  ·  返回退出");
        View restored=focus==null?null:root.findViewWithTag(focus);if(restored!=null && restored.isEnabled())restored.requestFocus();else{View first=root.findViewWithTag("primary");if(first!=null && first.isEnabled())first.requestFocus();}
    }
    private void renderDefault(LinearLayout body){
        TextView state=label(homeState==null?"默认桌面状态未确认":homeState.reason,16);state.setMaxLines(2);body.addView(state,new LinearLayout.LayoutParams(-1,-2));gap(body,12);
        body.addView(muted("先核对默认桌面，再管理预装应用。临时使用时，HOME 键仍按系统实际设置返回。",13));gap(body,22);
        boolean usable=recordAvailable && homeState!=null && homeState.ownUsable;
        if(homeState!=null && homeState.roleAvailable)action(body,"设为默认桌面","primary",this::requestRole,usable);
        action(body,adb.connected()?"通过 ADB 设置默认桌面":"连接 ADB 并设置默认桌面",homeState!=null && homeState.roleAvailable?"adb":"primary",()->withConnection(this::setDefault),usable);
        action(body,"重新检测","refresh",()->{roleCancelled=false;refresh();},true);
        body.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        action(body,"暂不设置，临时使用","temporary",()->exit(true),true);action(body,"退出设置","exit",()->exit(false),true);
    }
    private void renderApps(LinearLayout body){
        LinearLayout panels=row();panels.setGravity(Gravity.TOP);body.addView(panels,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=column();LinearLayout.LayoutParams leftp=new LinearLayout.LayoutParams(0,-1,34);leftp.rightMargin=px(24);panels.addView(left,leftp);
        left.addView(label("原桌面",18));gap(left,10);TextView name=muted(original==null?"身份未确认，仅保留":original.label+" · "+original.state(),12);name.setMaxLines(2);left.addView(name);gap(left,12);
        for(String value:new String[]{"KEEP","DISABLE","UNINSTALL"}){String text="KEEP".equals(value)?"保留":"DISABLE".equals(value)?"禁用":"卸载";action(left,text+(value.equals(originalChoice)?"  ✓":""),"home-"+value,()->{originalTouched=true;originalChoice=value;persist();render();},"KEEP".equals(value)||canOriginal(value));}
        left.addView(muted("禁用保留安装与数据。卸载删除当前用户数据，需单独确认。未验收的操作不开放。",11));gap(left,12);
        action(left,adb.connected()?"已连接 · 重新查询":"连接后选择应用","connect",()->{if(adb.connected())refresh();else withConnection(this::refresh);},true);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);LinearLayout inventory=column();scroll.addView(inventory);panels.addView(scroll,new LinearLayout.LayoutParams(0,-1,66));
        ArrayList<AppRepository.App> shown=new ArrayList<>();for(AppRepository.App app:apps)if(app.system || home.presets.app(app.pkg)!=null)shown.add(app);
        shown.sort((a,b)->Boolean.compare(home.presets.recommended(b.pkg),home.presets.recommended(a.pkg)));
        inventory.addView(muted((adb.connected()?"已核对当前用户":"本地扫描，状态待连接核对")+" · "+shown.size()+" 项",12));gap(inventory,10);
        if(shown.isEmpty())inventory.addView(muted(adb.connected()?"当前未发现可识别的预装应用":"暂未读取到预装应用，连接后可进一步核对",13));
        for(AppRepository.App app:shown){
            boolean allowed=canRecommend(app),checked=allowed && Boolean.TRUE.equals(choices.get(app.pkg));
            LinearLayout item=row();item.setPadding(px(10),px(8),px(10),px(8));focusable(item,10);item.setClickable(allowed);item.setFocusable(allowed);item.setTag("app-"+app.pkg);item.setContentDescription(app.label+" "+app.pkg+" "+app.state()+(allowed?(checked?" 已选择":" 未选择"):" 只读"));
            item.addView(icon(app.icon,34));LinearLayout copy=column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=px(12);item.addView(copy,cp);copy.addView(label(app.label,14));
            String reason=!operations.protection(app).isEmpty()?operations.protection(app):!app.verified?"待连接核对":!app.enabled?"已禁用，不计入待执行":home.presets.purpose(app.pkg);
            TextView detail=muted(app.state()+" · "+(allowed?home.presets.purpose(app.pkg):reason),10);detail.setMaxLines(2);copy.addView(detail);item.addView(label(allowed?(checked?"☑":"□"):"只读",12));
            item.setOnClickListener(v->{choices.put(app.pkg,!Boolean.TRUE.equals(choices.get(app.pkg)));persist();render();});LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.bottomMargin=px(6);inventory.addView(item,ip);
        }
        gap(body,12);LinearLayout buttons=row();body.addView(buttons,new LinearLayout.LayoutParams(-1,px(42)));
        int count=selected().size()+("KEEP".equals(originalChoice)?0:1);Button apply=button(count==0?"完成设置":"执行所选操作（"+count+"）",this::confirm);apply.setTag("primary");buttons.addView(apply,new LinearLayout.LayoutParams(0,-1,1));marginAdd(buttons,button("不处理，进入桌面",()->finishSetup("skipped","已进入桌面")),-2,42,14);
    }
    private ArrayList<AppRepository.App> selected(){ArrayList<AppRepository.App> result=new ArrayList<>();for(AppRepository.App app:apps)if(Boolean.TRUE.equals(choices.get(app.pkg)) && canRecommend(app))result.add(app);return result;}
    private void requestRole(){if(busy)return;try{home.remember(homeState);roleCancelled=false;startActivityForResult(home.request(),40);}catch(Exception e){notice="系统入口不可用："+e.getMessage();render();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==40){roleCancelled=result!=RESULT_OK;if(roleCancelled)notice="已取消默认桌面设置，可重试、临时使用或退出。";}}
    private void withConnection(Runnable next){if(busy)return;if(adb.connected())next.run();else connection.show(()->{if(active)next.run();});}
    private void setDefault(){
        if(busy)return;
        SetupOperations.Ticket task=begin("正在设置并核对默认桌面…");worker.execute(()->{try{task.check();home.switchToOwn(adb);task.check();ui(task,()->{busy=false;roleCancelled=false;notice="";refresh();});}catch(Exception e){ui(task,()->{busy=false;notice="未完成默认桌面设置，请重新检测实际状态："+e.getMessage();render();});}});
    }
    private void confirm(){
        if(busy)return;final ArrayList<AppRepository.App> frozen=selected();final AppRepository.App old=original;final String choice=originalChoice;
        Runnable execute=()->runSelected(frozen,old,choice);
        if("UNINSTALL".equals(choice)){Sheet sheet=new Sheet("卸载原桌面");sheet.text("仅为当前用户卸载，数据将删除。恢复安装不恢复数据。\n"+(old==null?"":old.label));Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("确认卸载",()->{sheet.dialog.dismiss();execute.run();});sheet.show(cancel);}else execute.run();
    }
    private void runSelected(ArrayList<AppRepository.App> frozen,AppRepository.App old,String choice){
        if(busy)return;
        SetupOperations.Ticket task=begin("正在核对所选操作…");worker.execute(()->{try{new SetupOperations(this).execute(adb,frozen,old,choice,task,value->ui(task,()->{progress=value;render();}));ui(task,()->{busy=false;openHome("设置成功");});}catch(Exception e){ui(task,()->{busy=false;notice="部分设置未完成："+e.getMessage();roleCancelled=false;refresh();});}});
    }
    private void finishSetup(String status,String text){
        if(busy)return;SetupOperations.Ticket task=begin("正在核对默认桌面…");worker.execute(()->{try{task.check();if(!home.inspect(adb).isOwn(this))throw new java.io.IOException("默认桌面已改变或未确认");store.put("status",status);ui(task,()->{busy=false;openHome(text);});}catch(Exception e){ui(task,()->{busy=false;screen=0;notice=e.getMessage();render();});}});
    }
    private void renderRecovery(LinearLayout body){
        body.addView(label(original==null?"原桌面身份未确认":original.label+" · "+original.state(),18));gap(body,16);body.addView(muted("先恢复可用，再切回默认桌面；其他预装应用保持当前状态。恢复安装不会恢复已删除的数据。",13));gap(body,24);
        if(!adb.connected())action(body,"连接后核对恢复能力","primary",()->withConnection(this::refresh),true);
        else{
            boolean available=original!=null && original.metadataKnown && original.verified && (original.installed?(original.enabled||home.presets.homeAllows("disableVerified")):(original.recoverable&&home.presets.homeAllows("restoreInstallVerified")));
            String text=original==null?"无法确认原桌面":!original.installed?"恢复安装并切回":!original.enabled?"启用并切回":"切回原桌面";
            action(body,text,"primary",this::restore,available);if(!available)body.addView(muted("当前固件未验收恢复能力，或原桌面安装包／组件缺失。",12));
        }
        action(body,"重新检测","refresh",this::refresh,true);body.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));action(body,"退出设置","exit",()->exit(false),true);
    }
    private void restore(){if(busy)return;SetupOperations.Ticket task=begin("正在恢复原桌面…");worker.execute(()->{try{home.remember(home.inspect(adb));new SetupOperations(this).restore(adb,task,value->ui(task,()->{progress=value;render();}));ui(task,()->{busy=false;cancel();startActivity(HomeController.homeIntent());finish();});}catch(Exception e){ui(task,()->{busy=false;notice="恢复未完成："+e.getMessage();refresh();});}});}
    private void exit(boolean temporary){
        cancel();try{store.put("status",homeState==null || !homeState.known?"unknown":"deferred");}catch(Exception e){Toast.makeText(this,"设置记录未保存",Toast.LENGTH_LONG).show();}
        if(temporary)openHome("已进入 Quiet TV；默认桌面以系统实际状态为准");else{if(isTaskRoot())try{startActivity(HomeController.homeIntent());}catch(ActivityNotFoundException ignored){}finish();}
    }
    private void openHome(String text){cancel();Toast.makeText(this,text,Toast.LENGTH_LONG).show();startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
}
