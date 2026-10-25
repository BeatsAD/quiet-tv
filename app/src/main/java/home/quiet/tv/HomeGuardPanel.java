package home.quiet.tv;

import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.concurrent.*;

/** AUTO resumes after the initial configuration; system grants and connections remain separate. */
final class HomeGuardPanel implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final ManagementUi ui;
    final ScrollView view;
    private HomeGuardState state;
    private DesktopSettingsRow routeRow,homeRow,accessibilityRow,notificationRow,bridgeRow,guardRow;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AdbController adb;
    private AdbConnectionPanel connection;
    private SetupOperations.Ticket ticket;
    private boolean configuring;
    private final Runnable bindingRefresh=()->{if(this.resumed)refreshUi();};
    private Button more;
    private final View back;
    private final boolean headerActions;
    private ManagementUi.Sheet openSheet;
    private boolean resumed;
    private String resumeFocus;
    private final Runnable update=this::refreshUi;
    HomeGuardPanel(ManagementUi ui,View back,boolean headerActions){
        this.ui=ui;this.back=back;this.headerActions=headerActions;state=new HomeGuardState(ui);
        adb=new AdbController(ui,value->ui.runOnUiThread(()->{if(resumed)guardRow.description(value);}));
        connection=new AdbConnectionPanel(ui,adb,worker);
        view=new ScrollView(ui);view.setFocusable(false);view.setVerticalScrollBarEnabled(false);
        LinearLayout body=ui.column();view.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout heading=ui.row();heading.addView(ui.muted("优先系统直接启动，必要时保留原桌面中转。",14),new LinearLayout.LayoutParams(0,-2,1));
        body.addView(heading,new LinearLayout.LayoutParams(-1,headerActions?-2:ui.px(38)));ui.gap(body,22);
        LinearLayout group=ui.listGroup();body.addView(group);
        routeRow=setting("启动方式","desktop","desktop-routes");routeRow.tail("arrow");routeRow.setOnClickListener(v->showRoutes());addSetting(group,routeRow);
        homeRow=setting("原桌面","desktop","guard-home");homeRow.tail("arrow");homeRow.setOnClickListener(v->showHomes());addSetting(group,homeRow);
        accessibilityRow=setting("无障碍权限","tools","guard-accessibility");accessibilityRow.tail("arrow");accessibilityRow.setOnClickListener(v->openAccessibility());addSetting(group,accessibilityRow);
        notificationRow=setting("通知恢复","tools","guard-notification");notificationRow.tail("arrow");notificationRow.setOnClickListener(v->openNotification());addSetting(group,notificationRow);
        bridgeRow=setting("开机跳板","tools","vendor-boot");bridgeRow.checked(false);bridgeRow.setOnClickListener(v->toggleVendor());addSetting(group,bridgeRow);
        guardRow=setting("自动中转","shield","guard-enabled");guardRow.checked(false);guardRow.setOnClickListener(v->toggleGuard());addSetting(group,guardRow);
        LinearLayout actions=headerActions?ui.header:heading;
        more=ui.button("更多操作",this::showMore);more.setId(View.generateViewId());more.setTag("guard-more");actions.addView(more,new LinearLayout.LayoutParams(-2,-1));
    }
    private DesktopSettingsRow setting(String title,String symbol,String tag){DesktopSettingsRow row=new DesktopSettingsRow(ui,title,symbol,false);row.setTag(tag);return row;}
    private void addSetting(LinearLayout group,DesktopSettingsRow row){
        group.addView(row,new LinearLayout.LayoutParams(-1,ui.px(80)));
    }
    void resume(){
        if(resumed)return;resumed=true;state.preferences.registerOnSharedPreferenceChangeListener(this);new DesktopRoutes(ui).reconcileVendor();HomeGuardRecovery.request(ui,"设置返回");refreshUi();
        view.postDelayed(bindingRefresh,1800);
    }
    void pause(){
        if(!resumed)return;resumed=false;state.preferences.unregisterOnSharedPreferenceChangeListener(this);view.removeCallbacks(update);view.removeCallbacks(bindingRefresh);
        if(ticket!=null)ticket.cancel();connection.close();adb.disconnect();configuring=false;
        if(openSheet!=null)openSheet.dialog.dismiss();
    }
    void destroy(){pause();adb.close();worker.shutdownNow();}
    void saveState(Bundle saved){View focus=ui.getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String)saved.putString("guard-focus",(String)focus.getTag());}
    void restoreState(Bundle saved){if(saved!=null)resumeFocus=saved.getString("guard-focus");}
    View firstRow(){return preferredFocus();}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){view.removeCallbacks(update);view.post(update);}
    private void refreshUi(){
        View previous=ui.getCurrentFocus();
        DesktopRoutes routes=new DesktopRoutes(ui);routeRow.value(routes.homeLabel());routeRow.description((BuildConfig.DECLARES_HOME?"标准包":"无 HOME 兼容包")+"  开机："+routes.bootLabel()+"  唤醒："+routes.wakeLabel());
        boolean supported=state.supported(),valid=state.validSelection(),authorized=state.serviceEnabled();
        List<HomeGuardState.Candidate> candidates=state.candidates();
        HomeGuardState.Candidate selected=null;
        if(valid)for(HomeGuardState.Candidate candidate:candidates)if(candidate.component.flattenToString().equals(state.selected())){selected=candidate;break;}
        homeRow.value(selected!=null?selected.label:state.selected().isEmpty()?"未选择":"已失效");
        homeRow.description(!supported?"当前设备未开放此实验功能":candidates.isEmpty()?"未检测到可用的原桌面，请在更多操作中重新检测。":!state.selected().isEmpty()&&!valid?"原桌面或固件已变化，请重新选择。":selected==null?"先选择电视自带的桌面，作为识别目标。":"已选为识别目标，授权完成后自动守护。");
        homeRow.setEnabled(supported&&!candidates.isEmpty());
        accessibilityRow.value(authorized?"已开启":"未开启");accessibilityRow.description(authorized?"已获得识别原桌面所需的权限。":"在系统设置中开启后，请返回此页。");accessibilityRow.setEnabled(supported);
        boolean notification=HomeGuardRecovery.notificationAllowed(ui);
        notificationRow.value(!HomeGuardRecovery.notificationSupported(ui)?"此设备不可用":!notification?"未授权":HomeGuardRevivalListener.connected()?"已连接":"等待绑定");
        notificationRow.description(HomeGuardSync.ready(ui)?"系统同步恢复已配置，不传输数据；通知恢复可同时使用。":"用于系统清理后的恢复；通知不可用时可配置系统同步恢复。");notificationRow.setEnabled(supported);
        accessibilityRow.setVisibility(state.enabled()?View.VISIBLE:View.GONE);notificationRow.setVisibility(state.enabled()?View.VISIBLE:View.GONE);
        bridgeRow.checked(routes.vendorReady());bridgeRow.description(routes.vendorReady()?"已配置，重启和断电开机待验证":routes.vendorProblem());bridgeRow.setEnabled(supported);
        String problem=state.enableProblem(),summary=state.summary();
        guardRow.checked(state.automatic());guardRow.description("OFF".equals(state.preferences.getString("mode","AUTO"))?"已暂停，中转入口不会自动恢复":!state.automatic()?"尚未配置，请先选择启动方式":state.enabled()?summary:routes.proxySelected()?new HomeProxy(ui).problem():"系统直接启动；中转按场景配置");
        guardRow.setEnabled(supported);
        wireFocus();
        View focus=resumeFocus!=null?ui.root.findViewWithTag(resumeFocus):previous;resumeFocus=null;
        if(focus==null||!focus.isShown()||!focus.isEnabled())focus=preferredFocus();
        if(focus!=ui.getCurrentFocus())focus.requestFocus();
    }
    private View preferredFocus(){return routeRow;}
    private void wireFocus(){
        View previous=headerActions?more:back;
        View[] rows=headerActions?new View[]{routeRow,homeRow,accessibilityRow,notificationRow,bridgeRow,guardRow}:new View[]{more,routeRow,homeRow,accessibilityRow,notificationRow,bridgeRow,guardRow};
        for(View row:rows){
            if(!row.isEnabled() || row.getVisibility()!=View.VISIBLE)continue;
            previous.setNextFocusDownId(row.getId());row.setNextFocusUpId(previous.getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());previous=row;
        }
        previous.setNextFocusDownId(previous.getId());
        if(headerActions){
            back.setNextFocusLeftId(back.getId());back.setNextFocusRightId(more.getId());back.setNextFocusUpId(back.getId());back.setNextFocusDownId(more.getNextFocusDownId());
            more.setNextFocusLeftId(back.getId());more.setNextFocusRightId(more.getId());more.setNextFocusUpId(more.getId());
        }
    }
    private void openAccessibility(){
        resumeFocus="guard-accessibility";
        if(!ExternalActivityLauncher.open(ui,"guard.accessibility",new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))){resumeFocus=null;ui.message("无障碍设置暂时无法打开，暂不能启用桌面守护（实验）。");}
    }
    private void openNotification(){
        resumeFocus="guard-notification";
        if(!ExternalActivityLauncher.open(ui,"guard.notification",new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))){resumeFocus=null;ui.message("通知访问设置暂时无法打开，可通过更多操作配置自动恢复。");}
    }
    private void configureRecovery(){
        if(configuring)return;
        if(!state.enabled()){ui.message("请先启用桌面守护。");return;}
        if(HomeGuardRecovery.writeAllowed(ui)){
            try{
                HomeGuardRecovery.configureGranted(ui);
                if(HomeGuardRecovery.configurationProblem(ui).isEmpty()){
                    refreshUi();ui.message("权限已配置，等待系统绑定");view.postDelayed(bindingRefresh,1800);return;
                }
            }catch(java.io.IOException|SecurityException failure){ui.message("配置未全部完成："+failure.getMessage());return;}
        }
        connection.show(()->{
            configuring=true;ticket=new SetupOperations.Ticket();SetupOperations.Ticket task=ticket;
            worker.execute(()->{String result;
                try{HomeGuardRecovery.configureAdb(ui,adb,task);result="自动恢复权限已配置，等待系统绑定";}
                catch(Exception failure){result="配置未全部完成："+failure.getMessage();}
                String text=result;ui.runOnUiThread(()->{configuring=false;if(resumed && task.active()){refreshUi();ui.message(text);view.postDelayed(bindingRefresh,1800);}});
            });
        },"连接并配置自动恢复");
    }
    private void toggleGuard(){
        if(state.automatic())state.pause("已暂停");
        else{new DesktopRoutes(ui).resume();if(!state.automatic())ui.message(state.preferences.getString("status","重新启用未完成"));if(state.enabled()){String problem=state.enableProblem();if(!problem.isEmpty())ui.message(problem);}}
        refreshUi();
    }
    private void toggleVendor(){
        DesktopRoutes routes=new DesktopRoutes(ui);
        if(routes.vendorReady())DesktopRoutes.disableVendor(ui);
        else{String problem=routes.configureVendor();if(!problem.isEmpty())ui.message(problem);}
        refreshUi();
    }
    private void showRoutes(){
        ManagementUi.Sheet sheet=ui.new Sheet("桌面配置");DesktopRoutes routes=new DesktopRoutes(ui);
        sheet.text(routes.details());sheet.text("自动配置保留原桌面。暂停中转不改变系统默认 HOME；恢复原桌面需单独操作。");
        boolean paused="OFF".equals(state.preferences.getString("mode","AUTO"));
        Button auto=sheet.option(paused?"重新启用并自动配置":"自动配置桌面",()->{
            if(paused && !state.preferences.edit().putString("mode","AUTO").putBoolean("configured",false).commit()){ui.message("重新启用未保存");return;}
            sheet.dialog.dismiss();ui.startActivity(new Intent(ui,SetupActivity.class).putExtra("autoConfigure",true));
        });
        sheet.option("使用原主页中转",()->{sheet.dialog.dismiss();String problem=state.enable();refreshUi();if(!problem.isEmpty())ui.message(problem);});
        sheet.option("记录实际验证结果",()->{sheet.dialog.dismiss();showScenes();});
        sheet.option("恢复原桌面",()->{sheet.dialog.dismiss();if(!BuildConfig.DECLARES_HOME && !routes.proxySelected())openOriginal();else ui.startActivity(new Intent(ui,SetupActivity.class).putExtra("restore",true));});
        sheet.action("返回",sheet.dialog::dismiss);showSheet(sheet,auto,routeRow);
    }
    private void showScenes(){
        ManagementUi.Sheet sheet=ui.new Sheet("实际验证结果");sheet.text("请实际操作后记录。手动打开不能代替遥控器 HOME、重启、断电开机或待机唤醒。");
        String[] scenes={"home","reboot","coldBoot","wake"},labels={"实体 HOME","重启","断电开机","待机唤醒"};Button first=null;
        for(int i=0;i<scenes.length;i++){final String scene=scenes[i],label=labels[i];Button option=sheet.option(label+"  "+new DesktopRoutes(ui).test(scene),()->{sheet.dialog.dismiss();showScene(scene,label);});if(first==null)first=option;}
        sheet.action("返回",sheet.dialog::dismiss);showSheet(sheet,first,routeRow);
    }
    private void showScene(String scene,String label){
        ManagementUi.Sheet sheet=ui.new Sheet(label);Button first=null;
        for(String result:new String[]{"通过","失败","待验证"}){Button option=sheet.option(result,()->{new DesktopRoutes(ui).observation(scene,result);sheet.dialog.dismiss();showScenes();});if(first==null)first=option;}
        sheet.action("返回",sheet.dialog::dismiss);showSheet(sheet,first,routeRow);
    }
    private void showHomes(){
        List<HomeGuardState.Candidate> candidates=state.candidates();
        if(candidates.isEmpty()){refreshUi();ui.message("未检测到可用的原桌面，请重新检测。");return;}
        ManagementUi.Sheet sheet=ui.new Sheet("选择原桌面");sheet.text("选择电视自带的原厂桌面。更换后自动使用新的原主页。");Button focus=null;
        for(HomeGuardState.Candidate candidate:candidates){
            boolean selected=candidate.component.flattenToString().equals(state.selected());int same=0;
            for(HomeGuardState.Candidate other:candidates)if(candidate.label.equals(other.label))same++;
            String name=(selected?"已选择  ":"")+candidate.label+(same>1?"  "+candidate.component.getShortClassName():"");
            Button option=sheet.option(name,()->{state.select(candidate.component);sheet.dialog.dismiss();});
            option.setSelected(selected);
            option.setSingleLine(true);option.setEllipsize(android.text.TextUtils.TruncateAt.END);option.setContentDescription(name+"  "+candidate.component.flattenToString());
            if(focus==null||selected)focus=option;
        }
        sheet.action("组件信息",()->{sheet.dialog.dismiss();showComponents();});Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        showSheet(sheet,focus==null?cancel:focus,homeRow);
    }
    private void showComponents(){
        ManagementUi.Sheet sheet=ui.new Sheet("组件信息");
        for(HomeGuardState.Candidate candidate:state.candidates())sheet.text(candidate.label+"\n"+candidate.component.flattenToString());
        Button close=sheet.action("返回选择",()->{sheet.dialog.dismiss();showHomes();});showSheet(sheet,close,homeRow);
    }
    private void showMore(){
        ManagementUi.Sheet sheet=ui.new Sheet("更多操作");
        sheet.text("自动恢复会修复被系统移除的无障碍配置。停止自动接管请关闭守护开关；首次调试授权仍由系统确认。");
        sheet.option("配置自动恢复",this::configureRecovery);
        sheet.option("电脑配置说明",()->{
            sheet.dialog.dismiss();ManagementUi.Sheet guide=ui.new Sheet("通过电脑配置守护");
            guide.text("请让电脑上的安装助手配置桌面守护。电视内置 ADB 连接失败时，仍可使用已授权的电脑连接。");
            guide.text(HomeGuardRecovery.configurationProblem(ui).isEmpty()?"权限已配置，实际接管仍需按遥控器 HOME 验证。":HomeGuardRecovery.configurationProblem(ui));
            Button close=guide.action("返回",guide.dialog::dismiss);showSheet(guide,close,more);
        });
        boolean canOpen=state.validSelection();if(!canOpen)sheet.text("请先选择有效的原桌面，才能打开原桌面。");
        Button refresh=sheet.option("重新检测",R.drawable.ic_manage_refresh,()->{sheet.dialog.dismiss();ui.feedback("已重新检测原桌面与权限状态");});
        Button original=sheet.option("暂停并打开原桌面",R.drawable.ic_manage_back,()->{sheet.dialog.dismiss();openOriginal();});original.setEnabled(canOpen);original.setAlpha(canOpen?1f:.45f);
        sheet.action("返回",sheet.dialog::dismiss);showSheet(sheet,refresh,more);
    }
    private void openOriginal(){
        state.pause("已暂停");refreshUi();
        if(!state.validSelection()){ui.message("原主页不可用，请重新选择。");return;}
        if(!ExternalActivityLauncher.open(ui,"guard.original_home",new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(state.selected()))))ui.message("原桌面无法打开，守护保持暂停。");
    }
    private void showSheet(ManagementUi.Sheet sheet,View focus,View origin){
        if(openSheet!=null)openSheet.dialog.dismiss();openSheet=sheet;
        sheet.dialog.setOnDismissListener(d->{if(openSheet==sheet)openSheet=null;if(resumed){refreshUi();if(origin.isEnabled())origin.requestFocus();}});
        sheet.show(focus);
    }
}
