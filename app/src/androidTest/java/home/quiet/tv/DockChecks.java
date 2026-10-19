package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.accessibility.*;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;

final class DockChecks {
    private final Instrumentation test;
    private Activity editor;
    DockChecks(Instrumentation test){this.test=test;}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void click(String text){
        for(int attempt=0;attempt<12;attempt++){
            test.waitForIdleSync();AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
            if(root!=null){
                for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){
                    if((node.getText()!=null && node.getText().toString().contains(text)) || (node.getContentDescription()!=null && node.getContentDescription().toString().contains(text))){
                        while(node!=null && !node.isClickable())node=node.getParent();
                        if(node!=null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}
                    }
                }
                scroll(root);
            }
            android.os.SystemClock.sleep(180);
        }
        throw new AssertionError("UI text not reachable: "+text);
    }
    private boolean scroll(AccessibilityNodeInfo node){
        if(node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null && scroll(child))return true;}return false;
    }
    private void open(){
        if(editor!=null)test.runOnMainSync(()->editor.finish());
        editor=test.startActivitySync(new Intent(test.getTargetContext(),DockEditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.waitForIdleSync();
    }
    private void key(int key){test.sendKeyDownUpSync(key);test.waitForIdleSync();}
    private void capture(String name) throws Exception {
        android.os.SystemClock.sleep(250);
        Bitmap image=test.getUiAutomation().takeScreenshot();
        try(FileOutputStream output=test.getTargetContext().openFileOutput(name,0)){require(image!=null,"Screenshot missing");image.compress(Bitmap.CompressFormat.PNG,100,output);}
    }
    void run() throws Exception {
        DockStore store=new DockStore(test.getTargetContext());ArrayList<String> original=store.load();
        // Occupied-but-unavailable shortcuts are a supported state. Do not require the
        // emulator owner's saved Dock to happen to contain four entries.
        java.util.List<String> sample=Arrays.asList("quiet.missing.one","quiet.missing.two","quiet.missing.three","quiet.missing.four");
        try{
            store.save(Collections.emptyList());require(new DockStore(test.getTargetContext()).load().isEmpty(),"Empty Dock re-imported defaults");
            open();capture("dock-empty.png");key(KeyEvent.KEYCODE_DPAD_CENTER);click("home.quiet.tv.fixture");
            require(store.load().equals(Arrays.asList("home.quiet.tv.fixture")),"Add to empty Dock failed");capture("dock-one.png");
            click("位置 1 ·");click("移除快捷入口");require(store.load().isEmpty(),"Remove shortcut failed");
            for(int count=1;count<=4;count++){store.save(sample.subList(0,count));require(new DockStore(test.getTargetContext()).load().equals(sample.subList(0,count)),"Dock size "+count+" did not persist");}
            open();key(KeyEvent.KEYCODE_DPAD_CENTER);click("替换应用");click("home.quiet.tv.fixture");
            ArrayList<String> replaced=new ArrayList<>(sample);replaced.set(0,"home.quiet.tv.fixture");require(store.load().equals(replaced),"Four-slot replacement failed");
            click("位置 1 ·");click("左右排序");key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
            Collections.swap(replaced,0,1);require(store.load().equals(replaced),"Reorder save failed");capture("dock-four-sorted.png");
            click("位置 1 ·");click("左右排序");key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_BACK);
            require(store.load().equals(replaced),"Reorder cancel changed saved Dock");
            try{store.save(Arrays.asList("a","b","c","d","e"));throw new AssertionError("More than four allowed");}catch(IllegalArgumentException expected){}
        }finally{store.save(original);require(test.getTargetContext().getSharedPreferences("dock",0).edit().putString("packages",new org.json.JSONArray(original).toString()).commit(),"Restore did not reach disk");if(editor!=null)test.runOnMainSync(()->editor.finish());}
    }
}
