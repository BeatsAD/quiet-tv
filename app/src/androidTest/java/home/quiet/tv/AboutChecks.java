package home.quiet.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.webkit.WebView;
import org.json.JSONTokener;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** About integration checks, restricted to the dedicated TV emulator. No real-device operations. */
final class AboutChecks {
    private final Instrumentation test;
    AboutChecks(Instrumentation test){this.test=test;}
    private HomeActivity home;
    private AboutActivity about;
    private WebView web;
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private int value(HomeActivity activity,String field) throws Exception {
        Field property=HomeActivity.class.getDeclaredField(field);property.setAccessible(true);return property.getInt(activity);
    }
    private Object js(String expression) throws Exception {
        CountDownLatch ready=new CountDownLatch(1);String[] result={null};
        test.runOnMainSync(()->web.evaluateJavascript(expression,value->{result[0]=value;ready.countDown();}));
        require(ready.await(10,TimeUnit.SECONDS),"WebView script timeout");
        return new JSONTokener(result[0]==null?"null":result[0]).nextValue();
    }
    private void await(String expression) throws Exception {
        long end=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<end){if(Boolean.TRUE.equals(js(expression)))return;Thread.sleep(80);}
        throw new AssertionError("Missing state: "+expression+"; focused="+js("document.activeElement.id")+"; url="+js("location.href"));
    }
    private void key(int code){test.sendKeyDownUpSync(code);test.waitForIdleSync();}
    private void focus(String id) throws Exception {await("document.activeElement.id==='"+id+"'");}
    private void capture(String name) throws Exception {
        test.waitForIdleSync();SystemClock.sleep(200);Bitmap bitmap=test.getUiAutomation().takeScreenshot();require(bitmap!=null,"No screenshot");
        try(FileOutputStream out=test.getTargetContext().openFileOutput(name,0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
    private void checkpoint(String text){Bundle result=new Bundle();result.putString("stream",text+"\n");test.sendStatus(0,result);}

    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Emulator only");
        home=(HomeActivity)test.startActivitySync(new Intent(test.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long ready=SystemClock.elapsedRealtime()+15000;
        Field loaded=HomeActivity.class.getDeclaredField("ready");loaded.setAccessible(true);
        while(!loaded.getBoolean(home) && SystemClock.elapsedRealtime()<ready)Thread.sleep(80);
        require(loaded.getBoolean(home),"Home did not load");
        for(int n=0;n<30 && value(home,"row")>=0;n++)key(KeyEvent.KEYCODE_DPAD_UP);
        for(int n=0;n<3;n++)key(KeyEvent.KEYCODE_DPAD_RIGHT);
        require(value(home,"topColumn")==3,"Fourth entry not reachable");
        capture("about-home.png");
        ActivityMonitor monitor=test.addMonitor(AboutActivity.class.getName(),null,false);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        about=(AboutActivity)monitor.waitForActivityWithTimeout(10000);test.removeMonitor(monitor);
        require(about!=null,"About entry did not launch");
        test.runOnMainSync(()->web=about.getWindow().getDecorView().findViewWithTag("about"));
        await("window.quietTVAbout?.ready===true && document.fonts.status==='loaded' && document.querySelector('.source-qr img').complete");
        focus("source-link");
        PackageInfo installed=test.getTargetContext().getPackageManager().getPackageInfo(test.getTargetContext().getPackageName(),0);
        require(("版本 "+installed.versionName).equals(js("document.querySelector('#version-name').textContent")),"Version is hardcoded");
        require(((String)js("document.querySelector('#version-build').textContent")).startsWith("构建 "+installed.getLongVersionCode()+" · "),"Build is hardcoded");
        require(test.getTargetContext().getApplicationInfo().loadLabel(test.getTargetContext().getPackageManager()).toString().equals(js("document.querySelector('#app-name').textContent")),"App name mismatch");
        require(Boolean.TRUE.equals(js("!document.querySelector('.workbench,#home,.prototype-controls,#website-link,#overlay') && document.querySelectorAll('.source-qr img,.code-window').length===3")),"Prototype tools or removed content shipped");
        require(Boolean.TRUE.equals(js("[...document.querySelectorAll('#about .brand,#about .information')].every(e=>e.scrollHeight<=e.clientHeight+1&&e.scrollWidth<=e.clientWidth+1)")),"Single screen overflow");
        test.runOnMainSync(()->{require(web.getSettings().getBlockNetworkLoads(),"Network loads enabled");require(!web.getSettings().getAllowFileAccess()&&!web.getSettings().getAllowContentAccess(),"File/content access enabled");});
        capture("about-native.png");checkpoint("PASS: installed app name/version/build, no preview tools, three offline QR codes, single-screen geometry");
        key(KeyEvent.KEYCODE_DPAD_DOWN);focus("email-link");key(KeyEvent.KEYCODE_DPAD_DOWN);focus("license-link");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);focus("upstream-link");key(KeyEvent.KEYCODE_DPAD_LEFT);focus("license-link");
        key(KeyEvent.KEYCODE_DPAD_CENTER);await("document.title==='开源许可' && document.body.innerText.includes('Copyright (c) 2026 Gathin') && document.body.innerText.includes('SIL OPEN FONT LICENSE')");
        key(KeyEvent.KEYCODE_DPAD_DOWN);await("scrollY>0 && document.documentElement.scrollWidth<=innerWidth");capture("about-license.png");
        key(KeyEvent.KEYCODE_BACK);await("window.quietTVAbout?.ready===true");focus("license-link");
        key(KeyEvent.KEYCODE_DPAD_UP);focus("email-link");key(KeyEvent.KEYCODE_DPAD_UP);focus("source-link");
        key(KeyEvent.KEYCODE_DPAD_LEFT);focus("back-home");key(KeyEvent.KEYCODE_DPAD_RIGHT);focus("source-link");
        checkpoint("PASS: remote routes / complete offline licenses / return to exact license focus");
        checkExternal("https://github.com/BeatsAD/quiet-tv",Intent.ACTION_VIEW);
        key(KeyEvent.KEYCODE_DPAD_DOWN);focus("email-link");checkExternal("mailto:wenbinailab@gmail.com",Intent.ACTION_SENDTO);
        checkUnavailable("mailto:wenbinailab@gmail.com");
        key(KeyEvent.KEYCODE_DPAD_UP);focus("source-link");checkUnavailable("https://github.com/BeatsAD/quiet-tv");
        key(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();Thread.sleep(400);
        require(about.isFinishing(),"Back did not exit About");
        require(value(home,"row")==-1 && value(home,"topColumn")==3,"About entry focus lost on return");
        capture("about-home-return.png");
        checkpoint("PASS: HOME returned to About entry without changing launcher configuration");
    }
    private void checkExternal(String uri,String action) throws Exception {
        boolean handler=test.getTargetContext().getPackageManager().resolveActivity(new Intent(action,android.net.Uri.parse(uri)),0)!=null;
        android.content.IntentFilter filter=new android.content.IntentFilter(action);filter.addDataScheme(android.net.Uri.parse(uri).getScheme());
        ActivityMonitor monitor=handler?test.addMonitor(filter,new ActivityResult(Activity.RESULT_CANCELED,null),true):null;
        try {
            key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(250);
            require(!about.isFinishing(),"External action closed About");
            require(((String)js("location.href")).startsWith("https://quiet-tv.invalid/about/index.html"),"External page entered local WebView");
            if(handler)require(monitor.getHits()==1,"External intent was not dispatched exactly once");
            checkpoint("PASS: external "+action+(handler?" dispatched outside WebView (intercepted by test)":" unavailable-app fallback"));
        } finally {if(monitor!=null)test.removeMonitor(monitor);}
    }
    private void checkUnavailable(String uri) throws Exception {
        int[] attempts={0};
        ActivityMonitor unavailable=new ActivityMonitor(){
            @Override public ActivityResult onStartActivity(Intent intent){
                if(uri.equals(intent.getDataString())){attempts[0]++;throw new android.content.ActivityNotFoundException("About test: no handler");}
                return null;
            }
        };
        test.addMonitor(unavailable);
        try {
            String focused=(String)js("document.activeElement.id");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            require(attempts[0]==1,"Unavailable-app fixture did not run");
            require(!about.isFinishing(),"Unavailable handler closed About");focus(focused);
            require(((String)js("location.href")).startsWith("https://quiet-tv.invalid/about/index.html"),"Unavailable handler changed page");
            checkpoint("PASS: unavailable-app error injection retained About and exact link focus");
        } finally {test.removeMonitor(unavailable);}
    }
}
