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
    private DesktopSettingsRow homeRow,accessibilityRow,notificationRow,guardRow;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private AdbController adb;
    private AdbConnectionPanel connection;
    private SetupOperations.Ticket ticket;
    private boolean configuring;
    private final Runnable bindingRefresh=()->{if(this.resumed)refreshUi();};
    private Button more;
    private final View back;
    private ManagementUi.Sheet openSheet;
    private boolean resumed;
    private String resumeFocus;
    private final Runnable update=this::refreshUi;
    HomeGuardPanel(ManagementUi ui,View back){
        this.ui=ui;this.back=back;state=new HomeGuardState(ui);state.configure();
        adb=new AdbController(ui,value->ui.runOnUiThread(()->{if(resumed)guardRow.description(value);}));
        connection=new AdbConnectionPanel(ui,adb,worker);
        view=new ScrollView(ui);view.setFocusable(false);view.setVerticalScrollBarEnabled(false);
        LinearLayout body=ui.column();view.addView(body,new ScrollView.LayoutParams(-1,-2));
        body.addView(ui.muted("当电视回到原厂桌面时，尝试返回 Quiet TV。",14));ui.gap(body,22);
        LinearLayout group=ui.column();group.setPadding(ui.px(4),ui.px(4),ui.px(4),ui.px(4));group.setBackground(ui.surface(0x12ffffff,0x06ffffff,0x1cffffff,16));UiCompat.clipOutline(group);body.addView(group);
        homeRow=setting("原桌面","desktop","guard-home");homeRow.tail("arrow");homeRow.setOnClickListener(v->showHomes());addSetting(group,homeRow);
        accessibilityRow=setting("无障碍权限","tools","guard-accessibility");accessibilityRow.tail("arrow");accessibilityRow.setOnClickListener(v->openAccessibility());addSetting(group,accessibilityRow);
        notificationRow=setting("通知恢复","tools","guard-notification");notificationRow.tail("arrow");notificationRow.setOnClickListener(v->openNotification());addSetting(group,notificationRow);
        guardRow=setting("桌面守护","shield","guard-enabled");guardRow.checked(false);guardRow.setOnClickListener(v->toggleGuard());addSetting(group,guardRow);
        ui.gap(body,18);body.addView(ui.muted("首次授权后自动运行，不读取屏幕或通知内容。可在更多操作中配置自动恢复。",12));
        ui.gap(body,7);body.addView(ui.muted("启用后，请分别验证遥控器 HOME、重启和待机唤醒，并确认 HDMI 与播放不受影响。",12));
        ui.gap(body,14);LinearLayout actions=ui.row();actions.setGravity(Gravity.END);body.addView(actions,new LinearLayout.LayoutParams(-1,ui.px(38)));
        more=ui.button("更多操作",this::showMore);more.setId(View.generateViewId());more.setTag("guard-more");actions.addView(more,new LinearLayout.LayoutParams(-2,-1));
    }
    private DesktopSettingsRow setting(String title,String symbol,String tag){DesktopSettingsRow row=new DesktopSettingsRow(ui,title,symbol,false);row.setTag(tag);return row;}
    private void addSetting(LinearLayout group,DesktopSettingsRow row){
        if(group.getChildCount()>0){View line=new View(ui);line.setBackgroundColor(0x16ffffff);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,ui.px(1));p.setMargins(ui.px(18),0,ui.px(18),0);group.addView(line,p);}
        group.addView(row,new LinearLayout.LayoutParams(-1,ui.px(80)));
    }
    void resume(){
        if(resumed)return;resumed=true;state.configure();state.preferences.registerOnSharedPreferenceChangeListener(this);HomeGuardRecovery.request(ui,"设置返回");refreshUi();
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
        notificationRow.description("用于系统清理后的恢复，不读取通知内容。");notificationRow.setEnabled(supported);
        String problem=state.enableProblem(),summary=state.summary();
        guardRow.checked(state.enabled());guardRow.description(state.enabled()?summary:!problem.isEmpty()?problem:"未启用".equals(summary)||"已暂停".equals(summary)?"配置已就绪，打开右侧开关即可启用。":summary);
        guardRow.setEnabled(supported);
        wireFocus();
        View focus=resumeFocus!=null?view.findViewWithTag(resumeFocus):previous;resumeFocus=null;
        if(focus==null||!focus.isShown()||!focus.isEnabled())focus=preferredFocus();
        if(focus!=ui.getCurrentFocus())focus.requestFocus();
    }
    private View preferredFocus(){if(state.enabled()&&guardRow.isEnabled())return guardRow;if(!state.validSelection()&&homeRow.isEnabled())return homeRow;if(!state.serviceEnabled()&&accessibilityRow.isEnabled())return accessibilityRow;return guardRow.isEnabled()?guardRow:more;}
    private void wireFocus(){
        View previous=back;
        for(View row:new View[]{homeRow,accessibilityRow,notificationRow,guardRow,more}){
            if(!row.isEnabled())continue;
            previous.setNextFocusDownId(row.getId());row.setNextFocusUpId(previous.getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());previous=row;
        }
        previous.setNextFocusDownId(previous.getId());
    }
    private void openAccessibility(){
        resumeFocus="guard-accessibility";
        try{ui.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
        catch(ActivityNotFoundException|SecurityException failure){resumeFocus=null;ui.message("电视未开放无障碍设置入口，暂不能启用桌面守护（实验）。");}
    }
    private void openNotification(){
        resumeFocus="guard-notification";
        try{ui.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}
        catch(ActivityNotFoundException|SecurityException failure){resumeFocus=null;ui.message("电视未开放通知访问入口，可通过更多操作配置自动恢复。");}
    }
    private void configureRecovery(){
        if(configuring)return;
        if(!state.enabled()){ui.message("请先启用桌面守护。");return;}
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
        if(state.enabled())state.pause("已暂停");
        else{String problem=state.enable();if(!problem.isEmpty())ui.message(problem);}
        refreshUi();
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
        boolean canOpen=state.validSelection();if(!canOpen)sheet.text("请先选择有效的原桌面，才能打开原桌面。");
        Button refresh=sheet.option("重新检测",R.drawable.ic_manage_refresh,()->{sheet.dialog.dismiss();ui.feedback("已重新检测原桌面与权限状态");});
        Button original=sheet.option("暂停并打开原桌面",R.drawable.ic_manage_back,()->{sheet.dialog.dismiss();openOriginal();});original.setEnabled(canOpen);original.setAlpha(canOpen?1f:.45f);
        sheet.action("返回",sheet.dialog::dismiss);showSheet(sheet,refresh,more);
    }
    private void openOriginal(){
        state.pause("已暂停");refreshUi();
        if(!state.validSelection()){ui.message("原主页不可用，请重新选择。");return;}
        try{ui.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(state.selected())));}
        catch(ActivityNotFoundException|SecurityException failure){ui.message("原桌面无法打开，守护保持暂停。");}
    }
    private void showSheet(ManagementUi.Sheet sheet,View focus,View origin){
        if(openSheet!=null)openSheet.dialog.dismiss();openSheet=sheet;
        sheet.dialog.setOnDismissListener(d->{if(openSheet==sheet)openSheet=null;if(resumed){refreshUi();if(origin.isEnabled())origin.requestFocus();}});
        sheet.show(focus);
    }
}
