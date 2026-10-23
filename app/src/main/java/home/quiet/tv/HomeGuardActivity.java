package home.quiet.tv;

import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Explicit remote-controlled opt-in; opening system settings never enables handover by itself. */
public final class HomeGuardActivity extends ManagementUi implements SharedPreferences.OnSharedPreferenceChangeListener {
    private HomeGuardState state;
    private DesktopSettingsRow homeRow,accessibilityRow,guardRow;
    private Button more,back;
    private Sheet openSheet;
    private boolean resumed,initialFocus=true;
    private String resumeFocus;
    private final Runnable update=this::refreshUi;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);state=new HomeGuardState(this);
        resumeFocus=saved==null?null:saved.getString("guard-focus");
        back=(Button)header.getChildAt(0);back.setId(View.generateViewId());back.setTag("guard-back");
        ((TextView)header.getChildAt(1)).setText("桌面守护（实验）");
        ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=column();scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=px(18);root.addView(scroll,sp);
        body.addView(muted("当电视回到原厂桌面时，尝试返回 Quiet TV。",14));gap(body,22);
        LinearLayout group=column();group.setPadding(px(4),px(4),px(4),px(4));group.setBackground(surface(0x12ffffff,0x06ffffff,0x1cffffff,16));UiCompat.clipOutline(group);body.addView(group);
        homeRow=setting("原桌面","desktop","guard-home");homeRow.tail("arrow");homeRow.setOnClickListener(v->showHomes());addSetting(group,homeRow);
        accessibilityRow=setting("无障碍权限","tools","guard-accessibility");accessibilityRow.tail("arrow");accessibilityRow.setOnClickListener(v->openAccessibility());addSetting(group,accessibilityRow);
        guardRow=setting("桌面守护","shield","guard-enabled");guardRow.checked(false);guardRow.setOnClickListener(v->toggleGuard());addSetting(group,guardRow);
        gap(body,18);body.addView(muted("守护不改变系统默认桌面，也不读取屏幕内容。启用前请先停用独立中转助手。",12));
        gap(body,7);body.addView(muted("启用后，请分别验证遥控器 HOME、重启和待机唤醒，并确认 HDMI 与播放不受影响。",12));
        gap(body,14);LinearLayout actions=row();actions.setGravity(Gravity.END);body.addView(actions,new LinearLayout.LayoutParams(-1,px(38)));
        more=button("更多操作",this::showMore);more.setId(View.generateViewId());more.setTag("guard-more");actions.addView(more,new LinearLayout.LayoutParams(-2,-1));
    }
    private DesktopSettingsRow setting(String title,String symbol,String tag){DesktopSettingsRow row=new DesktopSettingsRow(this,title,symbol,false);row.setTag(tag);return row;}
    private void addSetting(LinearLayout group,DesktopSettingsRow row){
        if(group.getChildCount()>0){View line=new View(this);line.setBackgroundColor(0x16ffffff);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(1));p.setMargins(px(18),0,px(18),0);group.addView(line,p);}
        group.addView(row,new LinearLayout.LayoutParams(-1,px(80)));
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;state.preferences.registerOnSharedPreferenceChangeListener(this);refreshUi();
    }
    @Override protected void onPause(){
        resumed=false;state.preferences.unregisterOnSharedPreferenceChangeListener(this);root.removeCallbacks(update);
        if(openSheet!=null)openSheet.dialog.dismiss();super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);View focus=getCurrentFocus();if(focus!=null&&focus.getTag() instanceof String)saved.putString("guard-focus",(String)focus.getTag());}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){root.removeCallbacks(update);root.post(update);}
    private void refreshUi(){
        View previous=getCurrentFocus();
        boolean supported=state.supported(),valid=state.validSelection(),authorized=state.serviceEnabled();
        if(state.enabled()){
            if(!supported||!valid)state.pause("原主页或适用条件已变化，守护已暂停");
            else if(!authorized)state.pause("无障碍权限已关闭，请检查后重新启用");
            else if(state.helperActive())state.pause("检测到独立中转助手，守护已暂停");
        }
        List<HomeGuardState.Candidate> candidates=state.candidates();
        HomeGuardState.Candidate selected=null;
        if(valid)for(HomeGuardState.Candidate candidate:candidates)if(candidate.component.flattenToString().equals(state.selected())){selected=candidate;break;}
        homeRow.value(selected!=null?selected.label:state.selected().isEmpty()?"未选择":"已失效");
        homeRow.description(!supported?"当前设备未开放此实验功能":candidates.isEmpty()?"未检测到可用的原桌面，请在更多操作中重新检测。":!state.selected().isEmpty()&&!valid?"原桌面或固件已变化，请重新选择。":selected==null?"先选择电视自带的桌面，作为识别目标。":"已选为识别目标，更换前会先暂停守护。");
        homeRow.setEnabled(supported&&!candidates.isEmpty());
        accessibilityRow.value(authorized?"已开启":"未开启");accessibilityRow.description(authorized?"已获得识别原桌面所需的权限。":"在系统设置中开启后，请返回此页。");accessibilityRow.setEnabled(supported);
        String problem=state.enableProblem(),summary=state.summary();
        guardRow.checked(state.enabled());guardRow.description(state.enabled()?summary:!problem.isEmpty()?problem:"未启用".equals(summary)||"已暂停".equals(summary)?"配置已就绪，打开右侧开关即可启用。":summary);
        guardRow.setEnabled(state.enabled()||problem.isEmpty());
        wireFocus();
        View focus=resumeFocus!=null?root.findViewWithTag(resumeFocus):initialFocus?preferredFocus():previous;resumeFocus=null;initialFocus=false;
        if(focus==null||!focus.isShown()||!focus.isEnabled())focus=preferredFocus();
        if(focus!=getCurrentFocus())focus.requestFocus();
    }
    private View preferredFocus(){if(state.enabled()&&guardRow.isEnabled())return guardRow;if(!state.validSelection()&&homeRow.isEnabled())return homeRow;if(!state.serviceEnabled()&&accessibilityRow.isEnabled())return accessibilityRow;return guardRow.isEnabled()?guardRow:more;}
    private void wireFocus(){
        View previous=back;
        for(View row:new View[]{homeRow,accessibilityRow,guardRow,more}){
            if(!row.isEnabled())continue;
            previous.setNextFocusDownId(row.getId());row.setNextFocusUpId(previous.getId());row.setNextFocusLeftId(row.getId());row.setNextFocusRightId(row.getId());previous=row;
        }
        back.setNextFocusUpId(back.getId());previous.setNextFocusDownId(previous.getId());
    }
    private void openAccessibility(){
        resumeFocus="guard-accessibility";
        try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
        catch(ActivityNotFoundException|SecurityException failure){resumeFocus=null;message("电视未开放无障碍设置入口，暂不能启用桌面守护（实验）。");}
    }
    private void toggleGuard(){
        if(state.enabled())state.pause("已暂停");
        else{String problem=state.enable();if(!problem.isEmpty())message(problem);}
        refreshUi();
    }
    private void showHomes(){
        List<HomeGuardState.Candidate> candidates=state.candidates();
        if(candidates.isEmpty()){refreshUi();message("未检测到可用的原桌面，请重新检测。");return;}
        Sheet sheet=new Sheet("选择原桌面");sheet.text("选择电视自带的原厂桌面。更换选择会先暂停守护。");Button focus=null;
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
        Sheet sheet=new Sheet("组件信息");
        for(HomeGuardState.Candidate candidate:state.candidates())sheet.text(candidate.label+"\n"+candidate.component.flattenToString());
        Button close=sheet.action("返回选择",()->{sheet.dialog.dismiss();showHomes();});showSheet(sheet,close,homeRow);
    }
    private void showMore(){
        Sheet sheet=new Sheet("更多操作");
        boolean canOpen=state.validSelection();if(!canOpen)sheet.text("请先选择有效的原桌面，才能打开原桌面。");
        Button refresh=sheet.option("重新检测",R.drawable.ic_manage_refresh,()->{sheet.dialog.dismiss();feedback("已重新检测原桌面与权限状态");});
        Button original=sheet.option("暂停并打开原桌面",R.drawable.ic_manage_back,()->{sheet.dialog.dismiss();openOriginal();});original.setEnabled(canOpen);original.setAlpha(canOpen?1f:.45f);
        sheet.action("返回",sheet.dialog::dismiss);showSheet(sheet,refresh,more);
    }
    private void openOriginal(){
        state.pause("已暂停");refreshUi();
        if(!state.validSelection()){message("原主页不可用，请重新选择。");return;}
        try{startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(state.selected())));}
        catch(ActivityNotFoundException|SecurityException failure){message("原桌面无法打开，守护保持暂停。");}
    }
    private void showSheet(Sheet sheet,View focus,View origin){
        if(openSheet!=null)openSheet.dialog.dismiss();openSheet=sheet;
        sheet.dialog.setOnDismissListener(d->{if(openSheet==sheet)openSheet=null;if(resumed){refreshUi();if(origin.isEnabled())origin.requestFocus();}});
        sheet.show(focus);
    }
}
