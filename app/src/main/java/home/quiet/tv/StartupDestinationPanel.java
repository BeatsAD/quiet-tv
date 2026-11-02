package home.quiet.tv;

import android.os.*;
import android.view.*;
import android.widget.*;

/** One TV source for manual watching and optional boot entry. */
final class StartupDestinationPanel extends ManagementPanel {
    private SourcePairingUi pairingUi;
    private final InputEntryTask inputWork=new InputEntryTask();
    private LinearLayout content;
    private BootAppStore store;
    private DesktopSettingsStore desktop;
    private boolean busy;
    private boolean enableAfterTest;
    private String focusTag;
    private View first;
    private android.app.Dialog confirmation;
    StartupDestinationPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle saved){
        store=new BootAppStore(this);pairingUi=new SourcePairingUi(this);
        desktop=new DesktopSettingsStore(this);
        if(saved!=null){enableAfterTest=saved.getBoolean("enable-after-test",false);focusTag=saved.getString("focus");}
        pageRoot=column();activity.attachPanel(pageRoot);
        ScrollView scroll=new ScrollView(activity);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        pageRoot.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        content=column();content.setPadding(px(2),0,px(2),px(4));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));render();
    }
    @Override protected void onResume(){super.onResume();render();pairingUi.resume();if(!store.pendingTest().isEmpty())confirm();}
    @Override protected void onPause(){remember();if(pairingUi!=null)pairingUi.pause();super.onPause();inputWork.cancel();busy=false;if(confirmation!=null)confirmation.dismiss();}
    @Override protected void onDestroy(){if(pairingUi!=null)pairingUi.close();inputWork.close();super.onDestroy();}
    @Override protected void onStop(){inputWork.cancel();busy=false;super.onStop();}
    @Override public void handleBack(){inputWork.cancel();busy=false;super.handleBack();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()==KeyEvent.KEYCODE_BACK||event.getKeyCode()==KeyEvent.KEYCODE_HOME)){inputWork.cancel();busy=false;}return false;}
    @Override protected void onSaveInstanceState(Bundle saved){remember();saved.putBoolean("enable-after-test",enableAfterTest);saved.putString("focus",focusTag);}
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();}
    @Override void focusManagementContent(){View focus=focusTag==null?null:content.findViewWithTag(focusTag);if(focus==null||!focus.isEnabled()||!focus.isShown())focus=first;if(focus!=null)focus.requestFocus();}
    private DesktopSettingsRow action(String title,String tag,Runnable operation){
        DesktopSettingsRow view=activity.setting(title,"desktop",tag);view.hideIcon();
        view.setOnClickListener(v->operation.run());LinearLayout group=activity.group();activity.addSetting(group,view);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=px(8);content.addView(group,params);return view;
    }
    void render(){
        boolean restore=pageRoot.hasFocus();remember();content.removeAllViews();
        DesktopSettingsRow visible=action(tr("显示看电视","Show Watch TV"),"show-watch-tv",()->save(()->desktop.showWatchTv(!desktop.showWatchTv())));
        visible.checked(desktop.showWatchTv());first=visible;
        DesktopSettingsRow target=action(tr("电视信号源","TV source"),"choose",pairingUi::details);
        target.value(store.watchTarget().isEmpty()?(new SourcePairingStore(this).entry()==null?tr("请选择","Choose"):tr("系统信号源  手动选择","System inputs  Select manually")):store.watchLabel());target.tail("arrow");
        DesktopSettingsRow pairing=action(tr("搜索与配对","Search and pair"),"pair-inputs",pairingUi::open);pairing.tail("arrow");
        DesktopSettingsRow boot=action(tr("开机自动看电视","Watch TV on startup"),"boot",this::toggleBoot);
        boot.checked(store.hdmiBoot());boot.available(!store.watchTarget().isEmpty());
        DesktopSettingsRow watch=action(store.watchTarget().isEmpty()?tr("打开信号源","Open inputs"):tr("立即观看","Watch now"),"test",()->{if(store.watchTarget().isEmpty())openPairedPanel();else test(false);});watch.tail("arrow");
        watch.available(!store.watchTarget().isEmpty()||new SourcePairingStore(this).entry()!=null);
        activity.wireRows(new DesktopSettingsRow[]{visible,target,pairing,boot,watch},managementTab());
        if(restore)focusManagementContent();
    }
    private void openPairedPanel(){
        if(busy)return;busy=true;android.content.Context app=getApplicationContext();
        inputWork.submit(()->{SourceSearch.Candidate candidate=SourcePairing.preferred(app,new SourcePairingStore(app).entry());return candidate==null?null:candidate.prepare(app,null);},(launch,failure)->{
            busy=false;if(!showing)return;if(launch==null||!launch.current(this)){message("入口已变化，请重新配对");return;}
            if(launch.blocking())inputWork.submit(()->launch.dispatch(app),(opened,error)->{if(showing&&!Boolean.TRUE.equals(opened))message("入口暂不可用，请检查连接或重新配对");});
            else if(!launch.dispatch(activity))message("入口暂不可用，请重新配对");
        });
    }
    private interface Write {void run() throws Exception;}
    private void save(Write operation){try{operation.run();render();}catch(Exception failure){message(failure.getMessage());}}
    private interface Prepared {void accept(SavedInputLaunch launch) throws Exception;}
    private void prepare(String saved,Prepared action){
        if(busy)return;busy=true;
        android.content.Context app=getApplicationContext();
        inputWork.submit(()->{HdmiTarget target=HdmiTarget.decode(saved);return target==null?null:target.prepare(app);},(launch,failure)->{
            if(!showing||isFinishing()||isDestroyed())return;busy=false;
            if(failure!=null||launch==null||!saved.equals(store.watchTarget())||!launch.current(this)){message("信号源或固件已变化，请重新选择。");return;}
            try{action.accept(launch);}catch(Exception unavailable){enableAfterTest=false;message("信号源暂不可用，请重新选择。");}
        });
    }
    private void test(boolean enable){
        String saved=store.watchTarget();
        prepare(saved,launch->{
            enableAfterTest=enable;
            // Pending means a requested test, never verified picture/audio. Only confirmTest can promote it.
            if(!store.tested())store.testDispatched(saved);
            HomeGuardService.bootSourceUserAction(activity,"saved_hdmi");
            if(launch.blocking()){
                busy=true;HomeGuardService.savedInputSelected(activity);android.content.Context app=getApplicationContext();
                inputWork.submit(()->launch.dispatch(app),(opened,failure)->{busy=false;if(showing)finishTest(Boolean.TRUE.equals(opened));});
            }else finishTest(launch.dispatch(activity));
        });
    }
    private void finishTest(boolean opened){
        if(!opened){HomeGuardService.cancelInputTransition();enableAfterTest=false;save(()->store.testDispatched(""));message("信号源暂时无法打开，请使用遥控器信号源键。");return;}
        HomeGuardService.savedInputSelected(activity);
        if(!store.tested())Toast.makeText(activity,tr("观看后按 HOME 返回，再进入“看电视”确认画面和声音","After watching, return with HOME and reopen Watch TV to confirm picture and sound"),Toast.LENGTH_LONG).show();
    }

    private String bootMessage(){
        return "下次完整开机进入 Quiet TV 后，自动进入 "+store.watchLabel()+"。待机唤醒不主动切源。"
            +(store.target().isEmpty()?"":"将替换应用自启："+store.label()+"。");
    }
    private void confirm(){
        final boolean enable=enableAfterTest;
        final String pending=store.pendingTest();
        if(pending.isEmpty())return;
        Sheet sheet=new Sheet("电视画面和声音正常吗？");confirmation=sheet.dialog;
        sheet.text(enable?bootMessage():"请确认已进入 "+store.watchLabel()+"，且画面和声音正常。");
        Button cancel=sheet.action("稍后确认",sheet.dialog::dismiss);
        sheet.option(enable?"正常，开启自动进入":"画面和声音正常",()->{sheet.dialog.dismiss();if(!currentTest(pending))return;prepare(pending,launch->{
            if(!currentTest(pending))return;
            save(()->{store.confirmTest(true);if(enable)store.hdmiBoot(true);enableAfterTest=false;});
        });});
        sheet.option("画面或声音异常",()->{sheet.dialog.dismiss();if(!currentTest(pending))return;enableAfterTest=false;save(()->store.confirmTest(false));});sheet.show(cancel);
    }
    private boolean currentTest(String pending){
        if(pending.equals(store.pendingTest())&&pending.equals(store.watchTarget()))return true;
        message("测试目标已变化，请重新测试。");return false;
    }
    private void toggleBoot(){
        if(store.hdmiBoot()){enableAfterTest=false;save(()->store.hdmiBoot(false));return;}
        if(!store.tested()){test(true);return;}
        String saved=store.watchTarget();prepare(saved,launch->{
            Sheet sheet=new Sheet("开启开机自动看电视？");sheet.text(bootMessage());
            Button cancel=sheet.action("取消",sheet.dialog::dismiss);
            sheet.action("开启",()->{sheet.dialog.dismiss();prepare(saved,current->save(()->store.hdmiBoot(true)));});sheet.show(cancel);
        });
    }
}
