package home.quiet.tv;

import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground-only advertising management. Effects are reported by the person watching the TV. */
public final class BootAdActivity extends ManagementUi {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<AppRepository.App> apps=new ArrayList<>();
    private final Set<String> selected=new HashSet<>(),restoreSelected=new HashSet<>();
    private final ArrayList<View> cards=new ArrayList<>();
    private List<BootAdOperations.RestoreItem> restores=Collections.emptyList();
    private JSONArray runs=new JSONArray();
    private AdbController adb;
    private AdbSession session;
    private AdbConnectionPanel connection;
    private BootAdOperations operations;
    private AppRepository repository;
    private SetupStore store;
    private SetupOperations.Ticket ticket;
    private LinearLayout content;
    private Button status,apply,restore,refresh,observe;
    private TextView notice;
    private boolean active,busy,refreshAfterConnection;
    private int generation;
    private String result="",focusTag="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);((TextView)header.getChildAt(1)).setText("开机广告");header.getChildAt(0).setOnClickListener(view->handleBack());
        operations=new BootAdOperations(this);repository=new AppRepository(this);store=new SetupStore(this);
        session=new AdbSession(this,new AdbSession.Listener(){
            public void changed(AdbState state){if(status!=null){status.setText(state.label());status.setContentDescription(state.description());}}
            public void connected(){if(busy)refreshAfterConnection=true;else refresh();}
        });adb=session.adb;
        connection=new AdbConnectionPanel(this,adb,worker,session);
        status=button("连接本机 ADB",()->{if(!busy)connection.show(this::refresh);});status.setTag("connect");header.addView(status,new LinearLayout.LayoutParams(px(155),px(36)));
        gap(root,18);notice=muted("正在读取适配状态…",13);notice.setTag("notice");notice.setLineSpacing(px(4),1);root.addView(notice,new LinearLayout.LayoutParams(-1,-2));gap(root,12);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);content=column();content.setPadding(px(2),px(2),px(2),px(2));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        gap(root,12);LinearLayout actions=row();root.addView(actions,new LinearLayout.LayoutParams(-1,px(40)));
        apply=button("停用所选服务",this::confirmDisable);apply.setTag("disable");actions.addView(apply,new LinearLayout.LayoutParams(-2,-1));
        restore=button("恢复所选修改",this::confirmRestore);restore.setTag("restore");marginAdd(actions,restore,-2,-1,10);
        refresh=button("重新检测",this::refresh);refresh.setTag("refresh");marginAdd(actions,refresh,-2,-1,10);
        observe=button("记录开机效果",this::observations);observe.setTag("observe");marginAdd(actions,observe,-2,-1,10);
        for(Button action:new Button[]{apply,restore,refresh,observe})action.setOnKeyListener((view,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_UP)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN)(cards.isEmpty()?status:cards.get(cards.size()-1)).requestFocus();return true;
        });
        status.setOnKeyListener((view,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN)(cards.isEmpty()?firstAction():cards.get(0)).requestFocus();return true;
        });
        footer("方向键选择  确定勾选  返回退出；不会自动重启电视");
    }
    @Override protected void onResume(){super.onResume();active=true;busy=false;refresh();session.resume();}
    @Override protected void onStop(){active=false;refreshAfterConnection=false;generation++;if(ticket!=null)ticket.cancel();connection.suspend();session.suspend();super.onStop();}
    @Override protected void onDestroy(){session.close();worker.shutdownNow();super.onDestroy();}
    private void ui(int epoch,Runnable action){runOnUiThread(()->{if(active && generation==epoch && !isFinishing() && !isDestroyed())action.run();});}
    private void rememberFocus(){View view=getCurrentFocus();if(view!=null && view.getTag()!=null)focusTag=view.getTag().toString();}
    private void updateActions(){
        apply.setEnabled(!busy && !selected.isEmpty());restore.setEnabled(!busy && !restoreSelected.isEmpty());refresh.setEnabled(!busy);observe.setEnabled(!busy && runs.length()>0);status.setEnabled(!busy);
    }
    private void refresh(){
        if(busy || !active)return;rememberFocus();busy=true;int epoch=++generation;notice.setText("正在重新检测实际状态…");updateActions();
        worker.execute(()->{try{
            List<AppRepository.App> actual=repository.load(adb);JSONArray saved=store.bootRuns();
            List<BootAdOperations.RestoreItem> items=BootAdOperations.restoreItems(saved,actual,android.os.Process.myUid()/100000,Build.FINGERPRINT);
            ui(epoch,()->{apps.clear();apps.addAll(actual);runs=saved;restores=items;selected.clear();restoreSelected.clear();busy=false;render();if(refreshAfterConnection){refreshAfterConnection=false;refresh();}});
        }catch(Exception e){ui(epoch,()->{selected.clear();restoreSelected.clear();busy=false;notice.setText("检测未完成："+e.getMessage());updateActions();});}});
    }
    private AppRepository.App find(String pkg){for(AppRepository.App app:apps)if(pkg.equals(app.pkg))return app;return null;}
    private void text(String value,int size){TextView view=muted(value,size);view.setLineSpacing(px(4),1);content.addView(view,new LinearLayout.LayoutParams(-1,-2));gap(content,10);}
    private View firstAction(){return apply.isEnabled()?apply:restore.isEnabled()?restore:refresh;}
    private void linkCard(View item){
        cards.add(item);item.setOnKeyListener((view,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_UP && key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN){int index=cards.indexOf(view),next=index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);(next<0?status:next>=cards.size()?firstAction():cards.get(next)).requestFocus();}return true;
        });
    }
    private void row(String tag,String value,boolean checked,String reason,Runnable toggle){
        Button item=button((checked?"☑  ":"□  ")+value+(reason.isEmpty()?"":"\n"+reason),()->{if(busy)return;if(reason.isEmpty()){rememberFocus();toggle.run();render();}else message(reason);});
        item.setTag(tag);item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setPadding(px(16),px(12),px(16),px(12));item.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(13));item.setSelected(checked);item.setAlpha(reason.isEmpty()?1f:.65f);
        linkCard(item);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=px(8);content.addView(item,p);
    }
    private void render(){
        content.removeAllViews();cards.clear();JSONArray rules=operations.presets.bootAds();
        notice.setText(result.isEmpty()?(rules.length()==0?"当前固件暂无经 Quiet TV 真机验收的开机广告规则。":adb.connected()?"已连接本机；请选择要处理的服务。":"可先选择服务，执行前按需连接本机 ADB。") :result);
        text("仅处理已适配的独立开机广告服务，保留应用和数据。无需先设为默认桌面。",12);
        if(rules.length()==0){
            text(operations.presets.matched()?"本机已有其他功能适配，尚未开放开机广告操作。":"本机固件未匹配可执行规则，系统包保持只读。",14);
            text("上游资料仅供调查，不代表本机已支持。可先向电视官方客服申请永久关闭开机广告。",13);
        }
        for(int i=0;i<rules.length();i++){
            JSONObject rule=rules.optJSONObject(i);if(rule==null)continue;String pkg=rule.optString("package");AppRepository.App app=find(pkg);
            String reason=operations.reason(app,false),name=app==null?pkg:app.label;
            row("app:"+pkg,name+"  "+(app==null?"未找到":app.state())+"\n"+rule.optString("purpose")+"\n影响："+rule.optString("impact")+"\n"+pkg+"  版本号 "+(app==null?"未知":app.versionCode),selected.contains(pkg),reason,()->{if(!selected.add(pkg))selected.remove(pkg);});
        }
        gap(content,12);text("本次修改的恢复记录",17);
        if(restores.isEmpty())text("暂无待恢复项目。已在其他地方停用的应用不会自动纳入恢复。",12);
        for(BootAdOperations.RestoreItem item:restores){String key=item.runId+":"+item.pkg(),reason=operations.restoreReason(item,false);String label=item.current==null?item.pkg():item.current.label;
            row("restore:"+key,label+"  "+(item.current==null?"未找到":item.current.state())+"\n"+item.baseline.optString("result")+"\n恢复目标："+(item.baseline.optInt("enabledSetting",-1)==0?"系统默认启用状态":"手动启用状态"),restoreSelected.contains(key),reason,()->{if(!restoreSelected.add(key))restoreSelected.remove(key);});
        }
        if(runs.length()>0){JSONObject last=runs.optJSONObject(runs.length()-1);if(last!=null){
            gap(content,12);StringBuilder history=new StringBuilder("最近一次停用任务  ").append(runState(last.optString("state")));JSONArray entries=last.optJSONArray("apps");
            if(entries!=null)for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry!=null)history.append("\n").append(entry.optString("package")).append("  ").append(entry.optString("result"));}
            history.append("\n\n").append(observationSummary(last));String description=history.toString();
            Button details=button("查看最近任务及开机效果记录",()->{Sheet sheet=new Sheet("最近任务记录");sheet.text(description);Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.show(close);});details.setTag("run-details");linkCard(details);content.addView(details,new LinearLayout.LayoutParams(-1,px(40)));
        }}
        updateActions();content.post(()->{if(!active)return;View target=stage.findViewWithTag(focusTag);if(target==null || !target.isEnabled() || !target.isFocusable())target=status;target.requestFocus();});
    }
    private static String runState(String state){return "complete".equals(state)?"服务停用已完成，效果待验证":"cancelled".equals(state)?"已中止后续操作":"未全部完成，请核对实际状态";}
    private void confirmDisable(){
        if(busy)return;ArrayList<AppRepository.App> frozen=new ArrayList<>();for(AppRepository.App app:apps)if(selected.contains(app.pkg))frozen.add(app);if(frozen.isEmpty())return;
        Sheet sheet=new Sheet("停用所选开机广告服务？");sheet.text("仅停用所选 "+frozen.size()+" 项，保留安装和数据；不改变默认桌面。执行后仍需实际开机验证效果。");Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("确认停用",()->{sheet.dialog.dismiss();withConnection(()->run(false,frozen,Collections.emptyList()));});sheet.show(cancel);
    }
    private void confirmRestore(){
        if(busy)return;ArrayList<BootAdOperations.RestoreItem> frozen=new ArrayList<>();for(BootAdOperations.RestoreItem item:restores)if(restoreSelected.contains(item.runId+":"+item.pkg()))frozen.add(item);if(frozen.isEmpty())return;
        Sheet sheet=new Sheet("恢复所选修改？");sheet.text("恢复所选 "+frozen.size()+" 项在本次停用前的启用状态。若之前结果不明，将重新查询；状态不匹配时不执行。");Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("确认恢复",()->{sheet.dialog.dismiss();withConnection(()->run(true,Collections.emptyList(),frozen));});sheet.show(cancel);
    }
    private void withConnection(Runnable action){if(adb.connected())action.run();else connection.show(()->{if(active && !busy)action.run();});}
    private void run(boolean restoring,List<AppRepository.App> frozen,List<BootAdOperations.RestoreItem> previous){
        if(!active || busy)return;rememberFocus();busy=true;int epoch=++generation;ticket=new SetupOperations.Ticket();SetupOperations.Ticket task=ticket;notice.setText("正在核对所选操作…");updateActions();
        worker.execute(()->{String outcome;try{outcome=restoring?operations.restore(adb,previous,task,value->ui(epoch,()->notice.setText(value))):operations.disable(adb,frozen,task,value->ui(epoch,()->notice.setText(value)));}
            catch(Exception e){outcome="未全部完成："+e.getMessage()+"。请重新检测实际状态；不会自动重试。";}
            final String message=outcome;ui(epoch,()->{result=message;busy=false;refresh();});
        });
    }
    @Override public void handleBack(){
        if(!busy){super.handleBack();return;}
        Sheet sheet=new Sheet("停止后续操作？");sheet.text("已发出的命令可能已执行。停止后将保留记录，下次进入重新查询，不自动继续。");Button stay=sheet.action("继续等待",sheet.dialog::dismiss);sheet.action("停止并退出",()->{if(ticket!=null)ticket.cancel();sheet.dialog.dismiss();finish();});sheet.show(stay);
    }
    private String observationSummary(JSONObject run){
        JSONObject values=run.optJSONObject("observations");String[] keys={"reboot","coldBoot","wake"},names={"整机重启","冷开机","待机唤醒"};StringBuilder text=new StringBuilder("用户反馈（不代表程序已验收）：");
        for(int i=0;i<keys.length;i++){JSONObject item=values==null?null:values.optJSONObject(keys[i]);String value=item==null?"":item.optString("result");text.append("\n").append(names[i]).append("：").append("absent".equals(value)?"未出现广告":"present".equals(value)?"仍有广告":"unknown".equals(value)?"不确定":"尚未记录");}return text.toString();
    }
    private void observations(){
        if(busy || runs.length()==0)return;JSONObject run=runs.optJSONObject(runs.length()-1);if(run==null)return;String id=run.optString("id");
        Sheet sheet=new Sheet("记录最近任务的实际开机效果");sheet.text("请自行操作电视后记录所见结果。本页面不会触发重启或断电。");String[] scenes={"reboot","coldBoot","wake"},names={"整机重启","冷开机","待机唤醒"};
        for(int i=0;i<scenes.length;i++){final String scene=scenes[i],name=names[i];sheet.option(name,()->{sheet.dialog.dismiss();Sheet answer=new Sheet(name+"后的广告表现");String[] values={"absent","present","unknown"},labels={"未出现广告","仍有广告","不确定"};for(int j=0;j<values.length;j++){final String value=values[j];answer.option(labels[j],()->{answer.dialog.dismiss();saveObservation(id,scene,value);});}Button cancel=answer.action("取消",answer.dialog::dismiss);answer.show(cancel);});}Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.show(close);
    }
    private void saveObservation(String id,String scene,String value){
        if(busy || !active)return;busy=true;int epoch=++generation;updateActions();worker.execute(()->{try{store.bootObservation(id,scene,value);ui(epoch,()->{result="已保存用户反馈；服务状态与实际广告效果分别记录。";busy=false;refresh();});}catch(Exception e){ui(epoch,()->{busy=false;message("记录未保存："+e.getMessage());updateActions();});}});
    }
}
