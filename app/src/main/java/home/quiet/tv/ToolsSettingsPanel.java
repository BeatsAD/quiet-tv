package home.quiet.tv;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Retained tools moved out of General without changing their stores or dialogs. */
final class ToolsSettingsPanel extends ManagementPanel {
    private ScrollView scroll;
    private DesktopSettingsRow filesRow,memoRow,remoteRow;
    private MemoPanel memos;
    private boolean destroyed;
    private final ExecutorService toolsWorker=Executors.newSingleThreadExecutor();
    private int scrollY;
    private String focusTag;
    private final Runnable phoneChanged=()->{if(showing)refreshFiles();};
    private final home.quiet.components.ComponentRepository.Listener componentsChanged=()->remoteRow.value(OptionalComponentsUi.label(home.quiet.components.ComponentRepository.get(activity).state("tailscale")));
    ToolsSettingsPanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        pageRoot=column();scroll=activity.settingsScroll();scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);activity.addSettingsScroll(pageRoot,scroll);LinearLayout body=column();scroll.addView(body);activity.attachPanel(pageRoot);
        LinearLayout tools=activity.group();body.addView(tools);ArrayList<DesktopSettingsRow> rows=new ArrayList<>();
        filesRow=activity.setting(activity.tr("文件传输","File transfer"),"logs","general-tool-transfer");filesRow.tail("arrow");filesRow.value(activity.tr("读取中","Loading"));filesRow.setOnClickListener(v->activity.selectManagementPage(SettingsPages.FILES));activity.addSetting(tools,filesRow);
        memos=new MemoPanel(activity,()->memoRow.value(memos.unfinished()<0?activity.tr("读取失败","Unavailable"):memos.unfinished()+activity.tr(" 条未完成"," unfinished")),()->toolReturned("general-tool-memos"));
        memoRow=activity.setting(activity.tr("备忘录","My notes"),"logs","general-tool-memos");memoRow.tail("arrow");memoRow.value(activity.tr("读取中","Loading"));memoRow.setOnClickListener(v->memos.show());activity.addSetting(tools,memoRow);
        remoteRow=activity.setting("Tailscale 组网","tools","component-tailscale");remoteRow.tail("arrow");remoteRow.value(OptionalComponentsUi.label(home.quiet.components.ComponentRepository.get(activity).state("tailscale")));remoteRow.setOnClickListener(v->OptionalComponentsUi.show(activity,"tailscale"));activity.addSetting(tools,remoteRow);
        Collections.addAll(rows,filesRow,memoRow,remoteRow);activity.wireRows(rows.toArray(new DesktopSettingsRow[0]),managementTab());
        if(state!=null){scrollY=state.getInt("scroll");focusTag=state.getString("focus");}memos.restoreState(state);
    }
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scrollY=scroll.getScrollY();}
    @Override protected void onResume(){super.onResume();home.quiet.components.ComponentRepository.get(activity).add(componentsChanged);PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);memos.refreshCount();refreshFiles();scroll.post(()->{if(showing)scroll.scrollTo(0,scrollY);});}
    @Override protected void onPause(){home.quiet.components.ComponentRepository.get(activity).remove(componentsChanged);PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);remember();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;memos.destroy();toolsWorker.shutdown();super.onDestroy();}
    @Override void focusManagementContent(){View target=focusTag==null?null:pageRoot.findViewWithTag(focusTag);if(target==null||!target.isShown()||!target.isEnabled())target=filesRow;target.requestFocus();}
    @Override public void handleBack(){if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    @Override protected void onSaveInstanceState(Bundle state){remember();state.putString("focus",focusTag);state.putInt("scroll",scrollY);memos.saveState(state);}
    private void refreshFiles(){toolsWorker.execute(()->{int count=0;File directory=new File(activity.getFilesDir(),"received-files");if(directory.exists())try{count=new FileTransferStore(directory).list().size();}catch(IOException failure){count=-1;}final int result=count;activity.runOnUiThread(()->{if(!destroyed)filesRow.value(result<0?activity.tr("读取失败","Unavailable"):result+activity.tr(" 个文件"," files"));});});}
    private void toolReturned(String tag){if(destroyed)return;focusTag=tag;if(showing)pageRoot.findViewWithTag(tag).requestFocusFromTouch();}
    void filesToolReturned(){refreshFiles();toolReturned("general-tool-transfer");}
}
