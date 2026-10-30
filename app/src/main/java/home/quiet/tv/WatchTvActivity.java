package home.quiet.tv;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Family setup for the saved HDMI shortcut; physical confirmation stays on this page. */
public final class WatchTvActivity extends ManagementUi {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private BootAppStore store;
    private boolean active,busy;
    private int generation;
    @Override boolean localManagement(){return false;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);header.getChildAt(0).setId(androidx.core.view.ViewCompat.generateViewId());store=new BootAppStore(this);((TextView)header.getChildAt(1)).setText("开机直达");
        ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=px(20);root.addView(scroll,p);
        content=column();scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
    }
    @Override protected void onResume(){super.onResume();active=true;render();}
    @Override protected void onPause(){active=false;++generation;busy=false;super.onPause();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private void action(String title,String tag,Runnable action){
        Button button=button(title,action);button.setTag(tag);button.setId(androidx.core.view.ViewCompat.generateViewId());button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        button.setSingleLine(true);button.setEllipsize(android.text.TextUtils.TruncateAt.END);button.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(46));p.topMargin=px(8);content.addView(button,p);
    }
    private void render(){
        View focused=getCurrentFocus();Object tag=focused==null?null:focused.getTag();content.removeAllViews();
        content.addView(label("开机进入："+(store.enabled()?store.label():"Quiet TV 桌面"),18));
        gap(content,6);content.addView(muted("先让电视开机进入 Quiet TV。按 HOME 返回后不会重复跳转。",13));
        action("选择 HDMI"+(store.watchTarget().isEmpty()?"":"："+store.watchLabel()),"choose",this::choose);
        if(!store.watchTarget().isEmpty()){
            action("立即测试 HDMI","test",this::test);
            if(!store.pendingTest().isEmpty())action("确认测试结果","confirm",this::confirm);
            action("开机进入此 HDMI："+(store.hdmiBoot()?"开启":"关闭"),"boot",this::toggleBoot);
        }
        action("首页“看电视”入口："+(shortcutOnHome()?"已添加":"未添加"),"shortcut",()->{if(shortcutOnHome())save(()->store.showWatch(false));else addShortcut();});
        action("选择应用自启","app",()->startActivity(new Intent(this,AppManagementActivity.class).putExtra("management-page",0)));
        if(store.enabled())action("关闭开机直达","off",()->save(()->store.select("","")));
        gap(content,12);content.addView(muted("HDMI 需先测试并确认画面、声音。待机唤醒保留系统恢复的画面；仅有信号源菜单的电视暂不支持直达。",13));
        ArrayList<View> buttons=new ArrayList<>();for(int i=0;i<content.getChildCount();i++)if(content.getChildAt(i).isFocusable())buttons.add(content.getChildAt(i));
        View restore=null;for(int i=0;i<buttons.size();i++){View v=buttons.get(i);v.setNextFocusUpId(i==0?header.getChildAt(0).getId():buttons.get(i-1).getId());v.setNextFocusDownId(buttons.get(Math.min(i+1,buttons.size()-1)).getId());v.setNextFocusLeftId(v.getId());v.setNextFocusRightId(v.getId());if(v.getTag().equals(tag))restore=v;}
        if(!buttons.isEmpty()){header.getChildAt(0).setNextFocusDownId(buttons.get(0).getId());(restore==null?buttons.get(0):restore).requestFocus();}
    }
    private interface Write {void run() throws Exception;}
    private void save(Write operation){try{operation.run();render();}catch(Exception failure){message(failure.getMessage());}}
    private boolean shortcutOnHome(){return store.showWatch()&&new DockStore(this).load().contains(HomeAppCatalog.WATCH_TV);}
    private void addShortcut(){
        ArrayList<String> pinned=new DockStore(this).load();
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
                if(!active||request!=generation||isFinishing())return;busy=false;
                if(choices.isEmpty()){message("未找到可直接进入的 HDMI。请使用首页信号源入口或电视遥控器；能够打开菜单不代表支持指定端口直达。");return;}
                Sheet sheet=new Sheet("选择 HDMI");Button first=null;
                for(HdmiTarget target:choices){Button b=sheet.option(target.label,()->{sheet.dialog.dismiss();save(()->store.saveWatch(target.encode(),target.label));if(store.watchTarget().equals(target.encode())&&!shortcutOnHome())addShortcut();});if(first==null)first=b;}
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
            if(!target.open(this)){message("HDMI 切换请求未发出，请使用信号源入口。");return;}
            store.testDispatched(target.encode());if(active)render();
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
        if(!store.tested()){message("请先点击“立即测试 HDMI”，返回此页确认画面和声音。");return;}
        if(Build.VERSION.SDK_INT<21)return;
        HdmiTarget target=HdmiTarget.decode(store.watchTarget());
        try{if(target==null||!target.availableNow(this)){message("HDMI 或固件已变化，请重新选择并测试。");return;}}
        catch(RuntimeException failure){message("HDMI 暂不可用，请稍后重试。");return;}
        Sheet sheet=new Sheet("开机进入 "+store.watchLabel()+"？");sheet.text("下次完整开机进入 Quiet TV 后切换一次。待机唤醒不主动切源。"+(store.target().isEmpty()?"":"将替换当前自启应用："+store.label()+"。"));
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("开启",()->{sheet.dialog.dismiss();save(()->store.hdmiBoot(true));});sheet.show(cancel);
    }
}
