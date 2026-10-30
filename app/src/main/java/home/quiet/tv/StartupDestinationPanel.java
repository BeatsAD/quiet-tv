package home.quiet.tv;

import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** One TV source for manual watching and optional boot entry. */
final class StartupDestinationPanel extends ManagementPanel {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private BootAppStore store;
    private boolean busy;
    private int generation;
    private boolean enableAfterTest;
    private String focusTag;
    private View first;
    private android.app.Dialog confirmation;
    StartupDestinationPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle saved){
        store=new BootAppStore(this);
        if(saved!=null){enableAfterTest=saved.getBoolean("enable-after-test",false);focusTag=saved.getString("focus");}
        pageRoot=column();activity.attachPanel(pageRoot);
        ScrollView scroll=new ScrollView(activity);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        pageRoot.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        content=column();content.setPadding(px(2),0,px(2),px(4));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));render();
    }
    @Override protected void onResume(){super.onResume();render();if(!store.pendingTest().isEmpty())confirm();}
    @Override protected void onPause(){remember();super.onPause();++generation;busy=false;if(confirmation!=null)confirmation.dismiss();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle saved){remember();saved.putBoolean("enable-after-test",enableAfterTest);saved.putString("focus",focusTag);}
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();}
    @Override void focusManagementContent(){View focus=focusTag==null?null:content.findViewWithTag(focusTag);if(focus==null||!focus.isEnabled()||!focus.isShown())focus=first;if(focus!=null)focus.requestFocus();}
    private DesktopSettingsRow action(String title,String tag,Runnable operation){
        DesktopSettingsRow view=activity.setting(title,"desktop",tag);view.hideIcon();
        view.setOnClickListener(v->operation.run());LinearLayout group=activity.group();activity.addSetting(group,view);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=px(8);content.addView(group,params);return view;
    }
    private void render(){
        boolean restore=pageRoot.hasFocus();remember();content.removeAllViews();
        DesktopSettingsRow target=action(tr("电视信号源","TV source"),"choose",this::choose);
        target.value(store.watchTarget().isEmpty()?tr("请选择","Choose"):store.watchLabel());target.tail("arrow");first=target;
        DesktopSettingsRow boot=action(tr("开机自动看电视","Watch TV on startup"),"boot",this::toggleBoot);
        boot.checked(store.hdmiBoot());boot.available(!store.watchTarget().isEmpty()&&Build.VERSION.SDK_INT>=21);
        DesktopSettingsRow watch=action(tr("立即观看","Watch now"),"test",()->test(false));watch.tail("arrow");
        watch.available(!store.watchTarget().isEmpty()&&Build.VERSION.SDK_INT>=21);
        activity.wireRows(new DesktopSettingsRow[]{target,boot,watch},managementTab());
        if(restore)focusManagementContent();
    }
    private interface Write {void run() throws Exception;}
    private void save(Write operation){try{operation.run();render();}catch(Exception failure){message(failure.getMessage());}}
    private void choose(){
        if(busy)return;if(Build.VERSION.SDK_INT<21){message("此系统暂不支持保存电视信号源，请使用遥控器信号源键。");return;}
        busy=true;int request=++generation;
        worker.execute(()->{
            List<HdmiTarget> targets;try{targets=HdmiTarget.available(this);}catch(RuntimeException failure){targets=Collections.emptyList();}
            final List<HdmiTarget> choices=targets;runOnUiThread(()->{
                if(!showing||request!=generation||isFinishing())return;busy=false;
                if(choices.isEmpty()){message("未找到可直接进入的电视信号源，请使用首页信号源入口或遥控器信号源键。");return;}
                Sheet sheet=new Sheet(tr("选择电视信号源","Choose TV source"));Button first=null;
                for(HdmiTarget target:choices){Button b=sheet.option(target.label,()->{sheet.dialog.dismiss();enableAfterTest=false;save(()->store.saveWatch(target.encode(),target.label));});if(first==null)first=b;}
                sheet.action("返回",sheet.dialog::dismiss);sheet.show(first);
            });
        });
    }
    private void test(boolean enable){
        if(Build.VERSION.SDK_INT<21)return;
        try{
            HdmiTarget target=HdmiTarget.decode(store.watchTarget());
            if(target==null||!target.availableNow(this)){message("信号源或固件已变化，请重新选择。");return;}
            enableAfterTest=enable;
            if(!store.tested())store.testDispatched("");
            if(!target.open(activity)){enableAfterTest=false;message("信号源暂时无法打开，请使用遥控器信号源键。");return;}
            if(!store.tested()){
                store.testDispatched(target.encode());
                Toast.makeText(activity,tr("观看后按 HOME 返回，再进入“看电视”确认画面和声音","After watching, return with HOME and reopen Watch TV to confirm picture and sound"),Toast.LENGTH_LONG).show();
            }
        }catch(Exception failure){enableAfterTest=false;message("信号源暂不可用，请重新选择。");}
    }
    private String bootMessage(){
        return "下次完整开机进入 Quiet TV 后，自动进入 "+store.watchLabel()+"。待机唤醒不主动切源。"
            +(store.target().isEmpty()?"":"将替换应用自启："+store.label()+"。");
    }
    private void confirm(){
        final boolean enable=enableAfterTest;
        Sheet sheet=new Sheet("电视画面和声音正常吗？");confirmation=sheet.dialog;
        sheet.text(enable?bootMessage():"请确认已进入 "+store.watchLabel()+"，且画面和声音正常。");
        Button cancel=sheet.action("稍后确认",sheet.dialog::dismiss);
        sheet.option(enable?"正常，开启自动进入":"画面和声音正常",()->{sheet.dialog.dismiss();save(()->{
            if(Build.VERSION.SDK_INT<21)throw new IllegalStateException("此系统不支持保存电视信号源");
            HdmiTarget target=HdmiTarget.decode(store.pendingTest());
            if(target==null||!target.availableNow(this))throw new IllegalStateException("信号源或固件已变化，请重新选择并观看");
            store.confirmTest(true);if(enable)store.hdmiBoot(true);enableAfterTest=false;
        });});
        sheet.option("画面或声音异常",()->{sheet.dialog.dismiss();enableAfterTest=false;save(()->store.confirmTest(false));});sheet.show(cancel);
    }
    private void toggleBoot(){
        if(store.hdmiBoot()){enableAfterTest=false;save(()->store.hdmiBoot(false));return;}
        if(!store.tested()){test(true);return;}
        if(Build.VERSION.SDK_INT<21)return;
        HdmiTarget target=HdmiTarget.decode(store.watchTarget());
        try{if(target==null||!target.availableNow(this)){message("信号源或固件已变化，请重新选择并观看。");return;}}
        catch(RuntimeException failure){message("信号源暂不可用，请稍后重试。");return;}
        Sheet sheet=new Sheet("开启开机自动看电视？");sheet.text(bootMessage());
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("开启",()->{sheet.dialog.dismiss();save(()->store.hdmiBoot(true));});sheet.show(cancel);
    }
}
