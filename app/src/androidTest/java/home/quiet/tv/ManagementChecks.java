package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;

final class ManagementChecks {
    private final Instrumentation test;
    private Activity activity;
    ManagementChecks(Instrumentation test){this.test=test;}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private AccessibilityNodeInfo find(String text,boolean exact){
        AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return null;
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
            String value=node.getText()!=null?node.getText().toString():String.valueOf(node.getContentDescription());
            if(exact?value.equals(text):value.contains(text))return node;
        }return null;
    }
    private void await(String text) {
        long end=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<end){test.waitForIdleSync();if(find(text,false)!=null)return;SystemClock.sleep(150);}
        try{capture("management-failure.png");}catch(Exception ignored){}
        StringBuilder visible=new StringBuilder();dump(test.getUiAutomation().getRootInActiveWindow(),visible);
        throw new AssertionError("UI not ready: "+text+"; visible="+visible);
    }
    private void dump(AccessibilityNodeInfo node,StringBuilder output){
        if(node==null)return;if(node.getText()!=null)output.append(node.getText()).append(" | ");
        for(int i=0;i<node.getChildCount();i++)dump(node.getChild(i),output);
    }
    private void click(String text,boolean exact){
        await(text);AccessibilityNodeInfo node=find(text,exact);
        while(node!=null && !node.isClickable())node=node.getParent();
        require(node!=null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cannot click "+text);test.waitForIdleSync();
    }
    private void key(int key){test.sendKeyDownUpSync(key);test.waitForIdleSync();}
    private void checkDetailNavigation(){
        key(KeyEvent.KEYCODE_DPAD_RIGHT);require(find("禁用应用",true).isFocused(),"Right did not enter detail actions");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);require(find("卸载应用",true).isFocused(),"Right did not reach uninstall");
        key(KeyEvent.KEYCODE_DPAD_LEFT);require(find("禁用应用",true).isFocused(),"Left skipped first action");
        key(KeyEvent.KEYCODE_DPAD_LEFT);AccessibilityNodeInfo focused=test.getUiAutomation().getRootInActiveWindow().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);require(focused!=null && String.valueOf(focused.getContentDescription()).contains("home.quiet.tv.fixture"),"Left did not return to selected app");
    }
    private void search(String text){
        click("搜索",false);
        AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
        AccessibilityNodeInfo edit=findEdit(root);require(edit!=null,"Search field missing");
        Bundle value=new Bundle();value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        require(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value),"Search failed");test.waitForIdleSync();click("搜索",true);
    }
    private AccessibilityNodeInfo findEdit(AccessibilityNodeInfo node){
        if(node==null)return null;if("android.widget.EditText".contentEquals(node.getClassName()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findEdit(node.getChild(i));if(found!=null)return found;}return null;
    }
    private void openManagement(){
        // Navigate through the real HOME entry: startActivitySync can wait forever for a reused task.
        test.runOnMainSync(()->test.getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(test.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        activity=null;SystemClock.sleep(1200);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        for(int i=0;i<3;i++)test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        await("个应用");click("●  ADB 未连接",true);click("连接",true);await("●  ADB 已连接");SystemClock.sleep(1800);
    }
    private void capture(String name) throws Exception {
        SystemClock.sleep(1000);Bitmap image=test.getUiAutomation().takeScreenshot();require(image!=null,"Screenshot missing");
        try(FileOutputStream out=test.getTargetContext().openFileOutput(name,0)){image.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void home(String name) throws Exception {
        test.runOnMainSync(()->test.getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClass(test.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        activity=null;
        SystemClock.sleep(1200);test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();capture(name);
    }
    void visual() throws Exception {
        ArrayList<String> original=new DockStore(test.getTargetContext()).load();
        openManagement();search("quiet-no-match-acceptance");await("没有符合条件的应用");capture("redesign-empty-search.png");
        click("quiet-no-match-acceptance  ×",true);await("个应用");
        search("com.trim.tv");await("飞牛TV");capture("redesign-apps.png");
        click("更多",true);click("刷新应用列表",true);await("飞牛TV");
        click("更多",true);click("操作记录",true);await("配置记录");capture("redesign-history.png");click("关闭",true);
        click("常用应用",true);await("常用应用，随手可达");capture("redesign-dock.png");
        require(new DockStore(test.getTargetContext()).load().equals(original),"Read-only visual check changed Dock");
        key(KeyEvent.KEYCODE_BACK);await("个应用");
    }
    void run(boolean uninstall) throws Exception {
        Context context=test.getTargetContext();DockStore store=new DockStore(context);ArrayList<String> original=store.load();
        String fixture="home.quiet.tv.fixture";AppRepository repository=new AppRepository(context);AppOperations operations=new AppOperations(context);
        try(AdbController adb=new AdbController(context,v->{})){
            adb.connect(5555);
            List<AppRepository.App> loaded=repository.load(adb);
            require(loaded.size()==AppRepository.parsePackages(adb.shell("pm list packages -u --user "+adb.user)).size(),"Package count differs from shell");
            AppRepository.App own=repository.find(adb,"home.quiet.tv"),android=repository.find(adb,"android");
            require(operations.actions(own).isEmpty(),"Launcher protection missing");require(operations.actions(android).isEmpty(),"Android protection missing");
            require(operations.actions(repository.find(adb,"com.sony.dtv.systemupdate")).isEmpty(),"Unknown/critical system package protection missing");
            ArrayList<String> configured=new ArrayList<>(original);configured.set(3,fixture);store.save(configured);
            openManagement();search(fixture);await("Quiet TV 测试应用");capture("management-fixture.png");
            if(uninstall){
                checkDetailNavigation();
                click("卸载应用",true);capture("management-uninstall-confirm.png");click("取消",true);require(repository.find(adb,fixture).installed,"Cancel uninstall changed state");
                click("卸载应用",true);click("确认卸载",true);await("卸载成功");
                AppRepository.App after=repository.find(adb,fixture);require(after==null || !after.installed,"Uninstall failed");
                require(!store.load().contains(fixture),"Uninstalled app retained in Dock");
                require(operations.history().contains("UNINSTALL"),"Uninstall journal missing");capture("management-after-uninstall.png");
            }else{
                AppRepository.App fixtureBefore=repository.find(adb,fixture);require(fixtureBefore!=null && fixtureBefore.enabled,"Fixture should start enabled");
                click("禁用应用",true);capture("management-disable-confirm.png");click("取消",true);require(repository.find(adb,fixture).enabled,"Cancel disable changed state");
                click("禁用应用",true);click("确认禁用",true);await("启用应用");
                require(!repository.find(adb,fixture).enabled,"UI disable failed");require(store.load().equals(configured),"Disable erased Dock configuration");
                home("home-fixture-disabled.png");require(find("Quiet TV 测试应用",false)==null,"Disabled app visible on home");
                openManagement();search(fixture);await("已禁用");click("启用应用",true);click("确认启用",true);await("禁用应用");
                require(repository.find(adb,fixture).enabled,"UI enable failed");require(store.load().equals(configured),"Enable changed Dock position");
                home("home-fixture-enabled.png");require(find("Quiet TV 测试应用",false)!=null,"Enabled app missing from home Dock");
                openManagement();search("");click("筛选",true);click("已禁用",true);await("已禁用");capture("management-disabled.png");
                click("已禁用",true);click("全部",true);search("com.sony.dtv.systemupdate");await("关键组件或用途未确认");capture("management-readonly.png");
                click("●  ADB 已连接",true);click("断开连接",true);await("●  ADB 未连接");capture("management-offline.png");
                require(operations.history().contains("DISABLE") && operations.history().contains("ENABLE"),"Operation journal missing");
                adb.disconnect();
                try{operations.perform(adb,fixtureBefore,AppOperations.Action.UNINSTALL);throw new AssertionError("Disconnected operation allowed");}catch(IOException expected){}
            }
        }finally{
            store.save(original);require(context.getSharedPreferences("dock",0).edit().putString("packages",new org.json.JSONArray(original).toString()).commit(),"Restore did not reach disk");
            if(!uninstall){try(AdbController recovery=new AdbController(context,v->{})){recovery.connect(5555);AppRepository.App fixtureState=repository.find(recovery,fixture);if(fixtureState!=null && fixtureState.installed && !fixtureState.enabled)recovery.shell("pm enable --user "+recovery.user+" "+fixture);}}
            if(activity!=null)test.runOnMainSync(()->activity.finish());
        }
    }
}
