package home.quiet.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** About integration and cold-entry regression checks. Only the dedicated emulator is allowed. */
final class AboutChecks {
    private final Instrumentation test;
    AboutChecks(Instrumentation test){this.test=test;}
    private HomeActivity home;
    private AboutActivity about;
    private WebView web;
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private Object field(Object target,String name) throws Exception {Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private int value(String name) throws Exception{return (Integer)field(home,name);}
    private <T> T ui(Callable<T> action) throws Exception {
        Object[] result={null};Exception[] error={null};
        test.runOnMainSync(()->{try{result[0]=action.call();}catch(Exception e){error[0]=e;}});
        if(error[0]!=null)throw error[0];
        @SuppressWarnings("unchecked") T value=(T)result[0];return value;
    }
    private void await(Callable<Boolean> condition,String message) throws Exception {
        long end=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<end){if(ui(condition))return;Thread.sleep(50);}
        throw new AssertionError(message);
    }
    private View node(String tag){return about.getWindow().getDecorView().findViewWithTag(tag);}
    private String text(String tag){return ((TextView)node(tag)).getText().toString();}
    private void focus(String tag) throws Exception {await(()->about.getCurrentFocus()!=null && tag.equals(about.getCurrentFocus().getTag()),"Missing focus: "+tag);}
    private void key(int code){test.sendKeyDownUpSync(code);test.waitForIdleSync();}
    private void capture(String name) throws Exception {
        test.waitForIdleSync();SystemClock.sleep(200);Bitmap bitmap=test.getUiAutomation().takeScreenshot();require(bitmap!=null,"No screenshot");
        try(FileOutputStream out=test.getTargetContext().openFileOutput(name,0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void checkpoint(String text){Bundle result=new Bundle();result.putString("stream",text+"\n");test.sendStatus(0,result);}
    private void enter() throws Exception {
        await(()->home.hasWindowFocus(),"Home has not regained the input window");
        ActivityMonitor monitor=test.addMonitor(AboutActivity.class.getName(),null,false);
        long start=SystemClock.elapsedRealtime();key(KeyEvent.KEYCODE_DPAD_CENTER);
        about=(AboutActivity)monitor.waitForActivityWithTimeout(10000);test.removeMonitor(monitor);
        require(about!=null,"About entry did not launch");
        await(()->node("about-native")!=null && node("about-native").isShown() && node("about-native").getWidth()>0,"Native scene not laid out");
        focus("back-home");require(ui(()->field(about,"web")==null),"Opening About constructed WebView");
        checkpoint("PASS: native About entered without a WebView ("+(SystemClock.elapsedRealtime()-start)+" ms including test synchronization)");
    }
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Emulator only");
        home=(HomeActivity)test.startActivitySync(new Intent(test.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(()->(Boolean)field(home,"ready"),"Home did not load");
        for(int n=0;n<30 && value("row")>=0;n++)key(KeyEvent.KEYCODE_DPAD_UP);
        for(int n=0;n<3;n++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
        require(value("topColumn")==3,"Fourth entry not reachable");capture("about-home.png");enter();
        PackageInfo installed=test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),0);
        String channel=(installed.applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0?"测试版":"正式版";
        require(ui(()->("版本 "+installed.versionName).equals(text("version-name"))),"Version is hardcoded");
        require(ui(()->channel.equals(text("version-channel"))),"Build number still visible or channel mismatch");
        require(ui(()->(installed.applicationInfo.loadLabel(test.getTargetContext().getPackageManager())+".").equals(text("app-name"))),"App name mismatch");
        for(String tag:new String[]{"source-address","developer-email"}){
            require(ui(()->!node(tag).isFocusable()&&!node(tag).isClickable()&&!node(tag).performClick()),"Read-only text is interactive");
        }
        for(String tag:new String[]{"project-qr","wechat-qr","official-qr"}){
            require(ui(()->{View v=node(tag);Rect rect=new Rect();return ((ImageView)v).getDrawable()!=null && v.getGlobalVisibleRect(rect) && rect.width()>100 && rect.height()>100;}),"Missing or clipped QR: "+tag);
        }
        capture("about-native.png");checkpoint("PASS: native metadata / read-only text / three complete QR codes in first scene");
        key(KeyEvent.KEYCODE_DPAD_DOWN);focus("license-link");key(KeyEvent.KEYCODE_DPAD_RIGHT);focus("upstream-link");
        key(KeyEvent.KEYCODE_DPAD_LEFT);focus("license-link");
        View scene=ui(()->node("about-native"));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        await(()->(Boolean)field(about,"licenseReady"),"License did not reach visual-state callback");
        web=ui(()->(WebView)field(about,"web"));
        require(ui(()->web.isShown() && "开源许可".equals(web.getTitle()) && scene.getVisibility()==View.INVISIBLE),"License was not presented atomically");
        require(ui(()->web.getSettings().getBlockNetworkLoads()&&!web.getSettings().getJavaScriptEnabled()&&!web.getSettings().getAllowFileAccess()&&!web.getSettings().getAllowContentAccess()),"License access policy changed");
        for(int n=0;n<4;n++)key(KeyEvent.KEYCODE_DPAD_DOWN);
        await(()->web.getScrollY()>0,"Offline license did not scroll");capture("about-license.png");
        key(KeyEvent.KEYCODE_BACK);focus("license-link");
        require(ui(()->scene==node("about-native") && scene.isShown()&&!web.isShown()),"Return rebuilt native scene or left license visible");
        // A pending visual callback must never cover About after an immediate Back.
        key(KeyEvent.KEYCODE_DPAD_CENTER);key(KeyEvent.KEYCODE_BACK);focus("license-link");Thread.sleep(250);
        require(ui(()->scene.isShown()&&!web.isShown()),"Late license callback obscured About");
        key(KeyEvent.KEYCODE_DPAD_UP);focus("back-home");key(KeyEvent.KEYCODE_DPAD_RIGHT);focus("license-link");
        key(KeyEvent.KEYCODE_DPAD_LEFT);focus("back-home");key(KeyEvent.KEYCODE_DPAD_DOWN);focus("license-link");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);focus("upstream-link");
        checkExternal();if(Build.VERSION.SDK_INT>=26)checkUnavailable();
        checkpoint("PASS: remaining remote routes / license visual readiness / rapid cancel / exact focus restoration");
        key(KeyEvent.KEYCODE_BACK);require(about.isFinishing(),"Back did not exit About");
        require(value("row")==-1 && value("topColumn")==3,"Home entry focus lost");
        // Repeated native entry cannot retain an old WebView or construct a new one.
        for(int n=0;n<2;n++){enter();key(KeyEvent.KEYCODE_DPAD_CENTER);require(about.isFinishing(),"Back button did not exit");}
        capture("about-home-return.png");checkpoint("PASS: repeated cold-native entry and HOME return without WebView construction");
    }
    private void checkExternal() throws Exception {
        String uri="https://github.com/Gathin23/tv-vibehack";
        boolean handler=test.getTargetContext().getPackageManager().resolveActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(uri)),0)!=null;
        android.content.IntentFilter filter=new android.content.IntentFilter(Intent.ACTION_VIEW);filter.addDataScheme("https");
        ActivityMonitor monitor=handler?test.addMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true):null;
        try{key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(250);require(!about.isFinishing(),"External action closed About");if(handler)require(monitor.getHits()==1,"External intent not dispatched once");focus("upstream-link");}
        finally{if(monitor!=null)test.removeMonitor(monitor);}
    }
    @android.annotation.TargetApi(26)
    private void checkUnavailable() throws Exception {
        CountDownLatch attempted=new CountDownLatch(1);
        ActivityMonitor unavailable=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){
            if("https://github.com/Gathin23/tv-vibehack".equals(intent.getDataString())){attempted.countDown();throw new android.content.ActivityNotFoundException("About test: no handler");}return null;
        }};
        test.addMonitor(unavailable);
        try{key(KeyEvent.KEYCODE_DPAD_CENTER);require(attempted.await(10,TimeUnit.SECONDS),"Unavailable-app callback missing");require(!about.isFinishing(),"Unavailable app closed About");focus("upstream-link");}
        finally{test.removeMonitor(unavailable);}
    }
}
