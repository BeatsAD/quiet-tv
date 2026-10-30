package home.quiet.tv;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Startup configuration shares the settings window and the existing boot/HDMI stores. */
final class StartupDestinationPanel extends ManagementPanel {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private BootAppStore store;
    private boolean busy;
    private int generation,editing;
    private String focusTag;
    private View first;
    StartupDestinationPanel(AppManagementActivity activity){super(activity);}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle saved){
        store=new BootAppStore(this);editing=store.hdmiBoot()?1:store.enabled()?2:store.watchTarget().isEmpty()?0:1;
        if(saved!=null){editing=saved.getInt("editing",editing);focusTag=saved.getString("focus");}
        pageRoot=column();activity.attachPanel(pageRoot);
        ScrollView scroll=new ScrollView(activity);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        pageRoot.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        content=column();content.setPadding(px(2),0,px(2),px(4));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));render();
    }
    @Override protected void onResume(){super.onResume();render();}
    @Override protected void onPause(){remember();super.onPause();++generation;busy=false;}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle saved){remember();saved.putInt("editing",editing);saved.putString("focus",focusTag);}
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();}
    @Override void focusManagementContent(){View focus=focusTag==null?null:content.findViewWithTag(focusTag);if(focus==null||!focus.isEnabled()||!focus.isShown())focus=first;if(focus!=null)focus.requestFocus();}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);}
    private DesktopSettingsRow action(String title,String tag,Runnable operation){
        DesktopSettingsRow view=activity.setting(title,"desktop",tag);view.hideIcon();
        view.setOnClickListener(v->operation.run());LinearLayout group=activity.group();activity.addSetting(group,view);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=px(8);content.addView(group,params);return view;
    }
    private Button choice(LinearLayout choices,String title,String tag,boolean selected,Runnable operation){
        Button button=button(title,operation);identify(button,tag);button.setSingleLine();button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));
        button.setPadding(px(7),0,px(7),0);button.setSelected(selected);ControlStyle.choice(button,unit,9,0x10ffffff,0x22ffffff);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(px(200),-1);if(choices.getChildCount()>0)params.leftMargin=px(8);choices.addView(button,params);return button;
    }
    private void render(){
        boolean restore=pageRoot.hasFocus();remember();content.removeAllViews();
        content.addView(activity.section(tr("开机进入","On startup")));gap(content,8);
        LinearLayout modes=row();content.addView(modes,new LinearLayout.LayoutParams(-1,px(38)));
        int selected=store.hdmiBoot()?1:store.enabled()?2:0;
        Button desktop=choice(modes,"Quiet TV "+tr("桌面","Home"),"mode-desktop",selected==0,()->{editMode(0);save(()->store.select("",""));});
        Button hdmi=choice(modes,tr("HDMI 信号源","HDMI input"),"mode-hdmi",selected==1,()->{editMode(1);render();if(store.hdmiBoot())return;if(store.watchTarget().isEmpty())choose();else toggleBoot();});
        Button app=choice(modes,tr("应用","App"),"mode-app",selected==2,()->{editMode(2);render();chooseApp();});
        first=selected==1?hdmi:selected==2?app:desktop;
        ArrayList<View[]> rows=new ArrayList<>();rows.add(new View[]{desktop,hdmi,app});
        gap(content,6);
        if(editing==1){
            DesktopSettingsRow target=action(tr("HDMI 信号源","HDMI input"),"choose",this::choose);target.value(store.watchTarget().isEmpty()?tr("请选择","Choose"):store.watchLabel());target.tail("arrow");rows.add(new View[]{target});
            if(!store.watchTarget().isEmpty()){
                DesktopSettingsRow test=action(tr("立即测试","Test now"),"test",this::test);test.tail("arrow");rows.add(new View[]{test});
                if(!store.pendingTest().isEmpty())rows.add(new View[]{action(tr("确认测试结果","Confirm test result"),"confirm",this::confirm)});
                if(!store.hdmiBoot()){gap(content,6);content.addView(muted(tr(store.tested()?"已确认画面和声音，选择 HDMI 信号源可开启直达。":"尚未开启直达，请先测试并确认画面和声音。","Test and confirm picture and sound, then select HDMI input to enable startup."),13));}
            }
        }else if(editing==2){
            DesktopSettingsRow target=action(tr("应用","App"),"choose-app",this::chooseApp);target.value(store.target().isEmpty()?tr("请选择","Choose"):store.label());target.tail("arrow");rows.add(new View[]{target});
            if(!store.target().isEmpty())rows.add(new View[]{action(tr("立即测试","Test now"),"test-app",this::testApp)});
        }
        DesktopSettingsRow shortcut=action(tr("首页“看电视”入口","Home Watch TV shortcut"),"shortcut",()->{if(shortcutOnHome())save(()->store.showWatch(false));else addShortcut();});shortcut.checked(shortcutOnHome());rows.add(new View[]{shortcut});
        gap(content,12);content.addView(muted(tr("按 HOME 返回桌面不会重复跳转。","Returning Home will not launch again."),12));
        for(int r=0;r<rows.size();r++)for(int c=0;c<rows.get(r).length;c++){
            View[] line=rows.get(r);View view=line[c];view.setNextFocusLeftId(line[Math.max(0,c-1)].getId());view.setNextFocusRightId(line[Math.min(line.length-1,c+1)].getId());
            view.setNextFocusUpId(r==0?managementTab().getId():rows.get(r-1)[Math.min(c,rows.get(r-1).length-1)].getId());
            view.setNextFocusDownId(r==rows.size()-1?view.getId():rows.get(r+1)[Math.min(c,rows.get(r+1).length-1)].getId());
        }
        if(restore)focusManagementContent();
    }
    private void editMode(int mode){editing=mode;++generation;busy=false;}
    void editApp(String pkg){editMode(2);render();if(!pkg.equals(store.target()))confirmApp(pkg);}
    private void chooseApp(){
        if(busy)return;busy=true;int request=++generation;
        worker.execute(()->{
            ArrayList<HomeAppCatalog.Entry> apps=new ArrayList<>();
            try{for(HomeAppCatalog.Entry app:new HomeAppCatalog(this).load(Collections.emptyList()))if(app.info!=null&&!"home.quiet.tv.test".equals(app.id))apps.add(app);}
            catch(RuntimeException failure){runOnUiThread(()->{if(showing&&request==generation){busy=false;message("应用列表读取失败，请稍后重试。");}});return;}
            runOnUiThread(()->{
                if(!showing||request!=generation||isFinishing())return;busy=false;
                if(apps.isEmpty()){message("未找到可自启的应用。");return;}
                Sheet sheet=new Sheet("选择自启应用");Button first=null;
                for(HomeAppCatalog.Entry app:apps){Button option=sheet.option(app.label,()->{sheet.dialog.dismiss();confirmApp(app.id);});if(first==null)first=option;}
                sheet.action("取消",sheet.dialog::dismiss);sheet.show(first);
            });
        });
    }
    private void confirmApp(String pkg){
        Intent intent=store.launchIntent(pkg);if(intent==null){message("此应用暂不可用，请重新选择。");return;}
        String label=pkg;try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        final String name=label;Sheet sheet=new Sheet("设为开机自启？");
        sheet.text("开机或待机唤醒进入 Quiet TV 后，将自动打开 "+name+"。"+(!store.enabled()?"":"将替换当前开机目标："+store.label()+"。")+"按 HOME 返回不重复，儿童模式限制仍然有效。");
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("开启",()->{sheet.dialog.dismiss();save(()->store.select(pkg,name));});sheet.show(cancel);
    }
    private void testApp(){
        String pkg=store.target();Intent intent=store.launchIntent(pkg);if(intent==null){message("此应用暂不可用，请重新选择。");return;}
        if(ChildBlockActivity.gate(activity,pkg))return;
        try{if(new HomeGuardState(this).protects(pkg))HomeGuardService.allowOriginal();activity.startActivity(intent);}catch(RuntimeException failure){message("应用暂时无法打开，请稍后重试。");}
    }
    private interface Write {void run() throws Exception;}
    private void save(Write operation){try{operation.run();render();}catch(Exception failure){message(failure.getMessage());}}
    private boolean shortcutOnHome(){return store.showWatch()&&new DockStore(this).load().contains(HomeAppCatalog.WATCH_TV);}
    private void addShortcut(){
        ArrayList<String> pinned=new DockStore(this).loadAvailable();
        if(pinned.contains(HomeAppCatalog.WATCH_TV)){save(()->store.showWatch(true));return;}
        if(pinned.size()<DockStore.CAPACITY){ArrayList<String> next=new ArrayList<>(pinned);next.add(0,HomeAppCatalog.WATCH_TV);saveShortcut(next);return;}
        Sheet sheet=new Sheet("选择首页位置");sheet.text("首页已有五项。选择要替换的位置，原应用仍保留在应用列表。");
        ArrayList<HomeAppCatalog.Entry> catalog=new HomeAppCatalog(this).load(pinned);
        for(int i=0;i<pinned.size();i++){
            final int index=i;String title=pinned.get(i);for(HomeAppCatalog.Entry entry:catalog)if(entry.id.equals(title)){title=entry.label;break;}
            sheet.option((i+1)+"  "+title,()->{sheet.dialog.dismiss();ArrayList<String> next=new ArrayList<>(pinned);next.set(index,HomeAppCatalog.WATCH_TV);saveShortcut(next);});
        }
        Button cancel=sheet.action("暂不添加",sheet.dialog::dismiss);sheet.show(cancel);
    }
    private void saveShortcut(ArrayList<String> next){
        save(()->{
            boolean wasShown=store.showWatch();store.showWatch(true);
            if(!new DockStore(this).saveOrder(next)){store.showWatch(wasShown);throw new java.io.IOException("首页位置保存失败，请重试");}
        });
    }
    private void choose(){
        if(busy)return;if(Build.VERSION.SDK_INT<21){message("此系统暂不支持保存 HDMI，请使用遥控器信号源键。应用自启仍可使用。");return;}
        busy=true;int request=++generation;
        worker.execute(()->{
            List<HdmiTarget> targets;try{targets=HdmiTarget.available(this);}catch(RuntimeException failure){targets=Collections.emptyList();}
            final List<HdmiTarget> choices=targets;runOnUiThread(()->{
                if(!showing||request!=generation||isFinishing())return;busy=false;
                if(choices.isEmpty()){message("未找到可直接进入的 HDMI。请使用首页信号源入口或电视遥控器；能够打开菜单不代表支持指定端口直达。");return;}
                Sheet sheet=new Sheet("选择 HDMI");Button first=null;
                for(HdmiTarget target:choices){Button b=sheet.option(target.label,()->{sheet.dialog.dismiss();save(()->store.saveWatch(target.encode(),target.label));});if(first==null)first=b;}
                sheet.action("返回",sheet.dialog::dismiss);sheet.show(first);
            });
        });
    }
    private void test(){
        if(Build.VERSION.SDK_INT<21)return;
        try{
            HdmiTarget target=HdmiTarget.decode(store.watchTarget());
            if(target==null||!target.availableNow(this)){message("HDMI 或固件已变化，请重新选择并测试。");return;}
            store.testDispatched("");
            if(!target.open(activity)){message("HDMI 切换请求未发出，请使用信号源入口。");return;}
            store.testDispatched(target.encode());if(showing)render();
        }catch(Exception failure){message("HDMI 暂不可用，请重新选择并测试。");}
    }
    private void confirm(){
        Sheet sheet=new Sheet("刚才的 HDMI 测试结果");sheet.text("请确认对应机顶盒的画面和声音。程序发出请求不代表切换成功。");
        Button cancel=sheet.action("稍后确认",sheet.dialog::dismiss);
        sheet.option("画面和声音正常",()->{sheet.dialog.dismiss();save(()->{
            if(Build.VERSION.SDK_INT<21)throw new IllegalStateException("此系统不支持 HDMI 直达");
            HdmiTarget target=HdmiTarget.decode(store.pendingTest());if(target==null||!target.availableNow(this))throw new IllegalStateException("HDMI 或固件已变化，请重新测试");
            store.confirmTest(true);
        });});
        sheet.option("切错或画面、声音异常",()->{sheet.dialog.dismiss();save(()->store.confirmTest(false));});sheet.show(cancel);
    }
    private void toggleBoot(){
        if(store.hdmiBoot()){save(()->store.hdmiBoot(false));return;}
        if(!store.tested()){message("请先点击“立即测试”，返回此页确认画面和声音后，再选择 HDMI 信号源。");return;}
        if(Build.VERSION.SDK_INT<21)return;
        HdmiTarget target=HdmiTarget.decode(store.watchTarget());
        try{if(target==null||!target.availableNow(this)){message("HDMI 或固件已变化，请重新选择并测试。");return;}}
        catch(RuntimeException failure){message("HDMI 暂不可用，请稍后重试。");return;}
        Sheet sheet=new Sheet("开机进入 "+store.watchLabel()+"？");sheet.text("下次完整开机进入 Quiet TV 后切换一次。待机唤醒不主动切源。"+(store.target().isEmpty()?"":"将替换当前自启应用："+store.label()+"。"));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("开启",()->{sheet.dialog.dismiss();save(()->store.hdmiBoot(true));});sheet.show(cancel);
    }
}
