package home.quiet.tv;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;

/** Section entry points use the existing production tools and their retained state. */
final class SettingsHubPanel extends ManagementPanel {
    private final int section;
    private final ArrayList<DesktopSettingsRow> rows=new ArrayList<>();
    private DesktopSettingsRow sounds;
    private MemoPanel memos;
    private DesktopSettingsRow memoRow;
    private String focusTag;
    SettingsHubPanel(AppManagementActivity activity,int section){super(activity);this.section=section;}
    @Override public void onCreate(Bundle state){
        pageRoot=column();ScrollView scroll=activity.settingsScroll();scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);activity.addSettingsScroll(pageRoot,scroll);
        LinearLayout body=column();scroll.addView(body);activity.attachPanel(pageRoot);
        if(section==SettingsPages.QUICK){
            LinearLayout controls=group(body,activity.tr("常用控制","Quick controls"));
            sounds=setting(controls,activity.tr("按键音","Key sounds"),"sound","quick-sounds",()->{DesktopSettingsStore store=new DesktopSettingsStore(this);store.keySounds(!store.keySounds());sounds.checked(store.keySounds());});sounds.checked(new DesktopSettingsStore(this).keySounds());
            setting(controls,activity.tr("更换背景","Change background"),"desktop","quick-wallpaper",()->activity.selectManagementPage(SettingsPages.DESKTOP));
            setting(controls,activity.tr("网络设置","Network settings"),"tools","quick-network",()->{if(!new SystemSettingsRouter(activity).open(android.provider.Settings.ACTION_WIFI_SETTINGS))message("当前电视没有可用的网络设置入口");});
            gap(body,12);LinearLayout tools=group(body,activity.tr("工具","Tools"));
            setting(tools,activity.tr("文件传输助手","File transfer"),"logs","quick-files",()->activity.selectManagementPage(SettingsPages.FILES));
            memos=new MemoPanel(activity,()->memoRow.value(memos.unfinished()<0?activity.tr("读取失败","Unavailable"):memos.unfinished()+activity.tr(" 条未完成"," unfinished")));
            memoRow=setting(tools,activity.tr("我的备忘录","My notes"),"logs","quick-memos",memos::show);
            setting(tools,activity.tr("信号源","Inputs"),"desktop","quick-source",()->activity.selectManagementPage(SettingsPages.STARTUP));
        }else{
            LinearLayout software=group(body,activity.tr("软件更新","Software updates"));
            DesktopSettingsRow update=setting(software,activity.tr("软件更新","Software updates"),"refresh","help-update",()->activity.helpTool("update"));update.value(BuildConfig.VERSION_NAME+" "+UpdatePanel.channel(BuildConfig.UPDATE_CHANNEL));
            gap(body,12);LinearLayout help=group(body,activity.tr("问题与反馈","Questions and feedback"));
            setting(help,activity.tr("常见问题","Common questions"),"info","help-topics",()->activity.helpTool("help")).value(activity.tr("HOME、开机、安装与恢复","HOME, startup, installation and recovery"));
            setting(help,activity.tr("导出诊断","Export diagnostics"),"logs","help-diagnostics",()->activity.helpTool("logs")).value(activity.tr("手机扫码下载","Scan to download"));
            setting(help,activity.tr("加入交流群","Join the community"),"info","help-community",()->activity.helpTool("community")).value(activity.tr("提反馈、提建议","Feedback and suggestions"));
            gap(body,12);LinearLayout about=group(body,activity.tr("关于","About"));
            DesktopSettingsRow aboutRow=setting(about,activity.tr("关于 Quiet TV","About Quiet TV"),"info","help-about",()->activity.helpTool("about"));
            aboutRow.value(activity.tr("版本与开源信息","Version and open-source information"));aboutRow.brandIcon(R.drawable.launcher_icon);
        }
        activity.wireRows(rows.toArray(new DesktopSettingsRow[0]),managementTab());if(state!=null)focusTag=state.getString("focus");if(memos!=null)memos.restoreState(state);
    }
    private LinearLayout group(LinearLayout body,String title){body.addView(activity.section(title));LinearLayout group=activity.group();body.addView(group);return group;}
    private DesktopSettingsRow setting(LinearLayout group,String title,String icon,String tag,Runnable action){DesktopSettingsRow row=activity.setting(title,icon,tag);row.tail("arrow");row.setOnClickListener(v->action.run());activity.addSetting(group,row);rows.add(row);return row;}
    void toolReturned(String kind){String tag=kind.equals("help")?"help-topics":kind.equals("logs")?"help-diagnostics":"help-"+kind;View row=pageRoot.findViewWithTag(tag);if(row!=null){focusTag=tag;row.requestFocusFromTouch();}}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isEnabled()||!target.isShown())target=rows.get(0);target.requestFocus();}
    @Override public void handleBack(){if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    @Override protected void onResume(){super.onResume();if(memos!=null)memos.refreshCount();if(sounds!=null)sounds.checked(new DesktopSettingsStore(this).keySounds());}
    @Override protected void onPause(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();super.onPause();}
    @Override protected void onDestroy(){if(memos!=null)memos.destroy();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();state.putString("focus",focusTag);if(memos!=null)memos.saveState(state);}
}
