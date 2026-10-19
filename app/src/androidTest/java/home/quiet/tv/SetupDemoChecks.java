package home.quiet.tv;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.KeyEvent;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;

/** Runs the packaged prototype with actual remote keys; never opens a real ADB session. */
final class SetupDemoChecks {
    private final Instrumentation test;
    private final Context context;
    private SetupDemoActivity activity;
    private WebView web;
    private String auditSource;
    SetupDemoChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private String shell(String command) throws Exception {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8").trim();}}
    private Object js(String expression) throws Exception {CountDownLatch done=new CountDownLatch(1);String[] result={null};test.runOnMainSync(()->web.evaluateJavascript(expression,value->{result[0]=value;done.countDown();}));require(done.await(10,TimeUnit.SECONDS),"WebView script timeout");return new JSONTokener(result[0]==null?"null":result[0]).nextValue();}
    private void await(String expression) throws Exception {long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){if(Boolean.TRUE.equals(js(expression)))return;Thread.sleep(80);}throw new AssertionError("Missing demo state: "+expression+"; body="+js("document.body.innerText.slice(-1500)"));}
    private void key(int code){test.sendKeyDownUpSync(code);test.waitForIdleSync();}
    private JSONObject audit() throws Exception {JSONObject result=(JSONObject)js(auditSource);require(result!=null,"No demo focus scope");require(result.getJSONArray("missing").length()==0,"Unreachable controls: "+result.getJSONArray("missing"));require(!result.getBoolean("escapes"),"Focus escaped the current dialog");return result;}
    private void select(String text) throws Exception {
        JSONObject tree=audit();JSONArray names=tree.getJSONArray("items"),paths=tree.getJSONArray("paths");int target=-1;
        for(int i=0;i<names.length();i++)if(text.equals(names.getString(i))){target=i;break;}
        if(target<0)for(int i=0;i<names.length();i++)if(names.getString(i).contains(text)){target=i;break;}
        require(target>=0,"Missing control "+text+" in "+names);JSONArray path=paths.getJSONArray(target);
        for(int i=0;i<path.length();i++){String direction=path.getString(i);key("ArrowUp".equals(direction)?19:"ArrowDown".equals(direction)?20:"ArrowLeft".equals(direction)?21:22);}
        String focused=(String)js("(()=>{const n=document.activeElement;return (n.getAttribute('aria-label')||n.textContent||n.placeholder||n.tagName).replace(/\\s+/g,' ').trim()})()");require(focused.contains(text),"Remote path ended at "+focused+" instead of "+text);
    }
    private void click(String text) throws Exception {select(text);key(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(100);}
    private void capture(String name) throws Exception {test.waitForIdleSync();SystemClock.sleep(200);Bitmap b=test.getUiAutomation().takeScreenshot();require(b!=null,"No screenshot");try(FileOutputStream out=context.openFileOutput(name,0)){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    private void checkpoint(String text){Bundle out=new Bundle();out.putString("stream",text+"\n");test.sendStatus(0,out);}
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Dedicated emulator only");
        require(SetupDemoActivity.class.getName().equals(context.getPackageManager().getActivityInfo(new ComponentName(context,SetupActivity.class),0).targetActivity),"Legacy setup component does not resolve to the simulation");
        String home=shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"),disabled=shell("pm list packages -d"),nativeSetup=new SetupStore(context).get().toString();
        try(InputStream in=test.getContext().getAssets().open("demo-focus-audit.js")){auditSource=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        activity=(SetupDemoActivity)test.startActivitySync(new Intent(context,SetupDemoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.runOnMainSync(()->web=activity.getWindow().getDecorView().findViewWithTag("setup-demo"));
        try{
            await("!!window.quietTVRemote");key(KeyEvent.KEYCODE_MENU);await("!!document.querySelector('[role=dialog]')");click("重新体验首次设置");await("!!document.querySelector('.q-screen-default')");audit();capture("setup-demo-default.png");checkpoint("PASS: offline prototype loaded; default-page controls reachable by remote");
            click("设为默认桌面");await("document.body.innerText.includes('始终使用 Quiet TV')");audit();click("始终使用 Quiet TV");await("!!document.querySelector('.q-screen-curate')");audit();capture("setup-demo-presets.png");
            click("BRAVIA 演示");click("保留");click("禁用 5 项并完成");await("document.body.innerText.includes('连接电视')");audit();capture("setup-demo-connection.png");click("连接设置");await("!!document.querySelector('details[open]')");audit();select("调试端口");key(KeyEvent.KEYCODE_DPAD_DOWN);require(!Boolean.TRUE.equals(js("document.activeElement.tagName==='INPUT'")),"Port input trapped directional focus");key(KeyEvent.KEYCODE_BACK);await("!document.querySelector('[role=dialog]')");
            require(Boolean.TRUE.equals(js("JSON.parse(localStorage.getItem('quiet-tv-setup-prototype-v1')).apps.every(a=>a.enabled&&a.installed)")),"Cancel changed demo apps");checkpoint("PASS: three factory options / all six cards / folded connection settings / input escape / modal focus containment / cancellation");
            click("禁用 5 项并完成");click("打开系统设置");await("document.body.innerText.includes('返回连接')");audit();click("返回连接");click("连接并应用设置");await("document.body.innerText.includes('允许连接并继续')");audit();click("允许连接并继续");await("!!document.querySelector('.q-screen-home')");require(Boolean.TRUE.equals(js("(()=>{const s=JSON.parse(localStorage.getItem('quiet-tv-setup-prototype-v1'));return s.defaultHome&&s.apps.filter(a=>!a.enabled).length===5&&s.apps.find(a=>a.id==='showroom').enabled})()")),"Demo did not apply exactly the frozen selection");audit();checkpoint("PASS: simulated authorization automatically applied five choices; unselected app preserved");
            key(KeyEvent.KEYCODE_MENU);click("恢复中心");await("!!document.querySelector('.q-screen-recovery')");audit();click("查看 5 个可恢复项");audit();click("确认恢复");await("!document.querySelector('[role=dialog]')");require(Boolean.TRUE.equals(js("JSON.parse(localStorage.getItem('quiet-tv-setup-prototype-v1')).apps.every(a=>a.enabled)")),"Mock restore failed");
            key(KeyEvent.KEYCODE_MENU);click("无线配对");await("!!document.querySelector('.q-screen-default')");click("设为默认桌面");await("document.body.innerText.includes('配对码')");audit();select("6 位配对码");key(KeyEvent.KEYCODE_DPAD_DOWN);require(!Boolean.TRUE.equals(js("document.activeElement.tagName==='INPUT'")),"Pair code trapped directional focus");click("6 位配对码");test.sendStringSync("123456");await("document.querySelector('input[aria-label=\"6 位配对码\"]').value==='123456'");key(KeyEvent.KEYCODE_BACK);test.getUiAutomation().waitForIdle(700,5000);
            capture("setup-demo-input.png");click("配对并继续");capture("setup-demo-after-input-continue.png");await("!!document.querySelector('.q-screen-curate')");
            key(KeyEvent.KEYCODE_MENU);click("标准切换");click("设为默认桌面");click("始终使用 Quiet TV");click("卸载");click("卸载原桌面并完成");await("document.body.innerText.includes('确认卸载并完成')");audit();key(KeyEvent.KEYCODE_BACK);await("!document.querySelector('[role=dialog]')");require(Boolean.TRUE.equals(js("JSON.parse(localStorage.getItem('quiet-tv-setup-prototype-v1')).apps.find(a=>a.id==='factory').installed")),"Cancel uninstalled the mock launcher");click("卸载原桌面并完成");click("确认卸载并完成");await("document.body.innerText.includes('连接电视')");click("连接并卸载原桌面");await("document.body.innerText.includes('允许连接并继续')");click("允许连接并继续");await("!!document.querySelector('.q-screen-home')");require(Boolean.TRUE.equals(js("!JSON.parse(localStorage.getItem('quiet-tv-setup-prototype-v1')).apps.find(a=>a.id==='factory').installed")),"Mock uninstall did not complete");
            key(KeyEvent.KEYCODE_MENU);click("恢复中心");click("恢复安装并切回");click("恢复安装并切回");await("!document.querySelector('[role=dialog]')");require(Boolean.TRUE.equals(js("(()=>{const s=JSON.parse(localStorage.getItem('quiet-tv-setup-prototype-v1'));return !s.defaultHome&&s.apps.find(a=>a.id==='factory').installed&&s.apps.find(a=>a.id==='factory').enabled})()")),"Mock launcher restore failed");
            checkpoint("PASS: mock recovery / actual remote text input / wireless field navigation / simulated uninstall and reinstall");
            click("退出演示");test.waitForIdleSync();require(activity.isFinishing(),"Exit demo did not finish the activity");
            require(home.equals(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")),"Real HOME changed");require(disabled.equals(shell("pm list packages -d")),"Real package state changed");require(nativeSetup.equals(new SetupStore(context).get().toString()),"Native setup baseline changed");
        }finally{test.runOnMainSync(()->{if(activity!=null)activity.finish();});}
    }
}
