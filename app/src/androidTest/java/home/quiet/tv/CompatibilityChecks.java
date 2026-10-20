package home.quiet.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.lang.reflect.Field;

/** Runtime checks for framework branches; restricted to disposable emulators. */
final class CompatibilityChecks {
    private final Instrumentation test;
    CompatibilityChecks(Instrumentation test){this.test=test;}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private Object field(Object owner,Class<?> type,String name) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void await(Object owner,Class<?> type,String name,boolean value) throws Exception {
        long end=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<end){if(Boolean.valueOf(value).equals(field(owner,type,name)))return;Thread.sleep(80);}
        throw new AssertionError("Timed out: "+name);
    }
    private boolean finishing(Activity activity,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;while(!activity.isFinishing() && SystemClock.elapsedRealtime()<end)Thread.sleep(80);return activity.isFinishing();
    }
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Disposable emulator only");
        Context context=test.getTargetContext();
        // Consume first-launch classification before testing HOME navigation in isolation.
        new SetupStore(context).shouldOffer(context);
        require(!new AppRepository(context).load(null).isEmpty(),"Offline package enumeration failed");
        HomeController home=new HomeController(context);HomeController.State state=home.inspect(null);
        require(state.ownUsable,"Own HOME component unavailable");
        if(state.roleAvailable)require(home.request().resolveActivity(context.getPackageManager())!=null,"Invalid system HOME entry");
        test.runOnMainSync(()->{
            Drawable icon=context.getDrawable(R.drawable.ic_ethernet);Bitmap bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);
            icon.setBounds(0,0,96,96);icon.draw(new Canvas(bitmap));
            require((bitmap.getPixel(48,20)>>>24)==0,"Ethernet icon hole filled on legacy vector renderer");
            require((bitmap.getPixel(34,12)>>>24)>0,"Ethernet icon outline missing");
        });
        HomeActivity launcher=(HomeActivity)test.startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(launcher,HomeActivity.class,"ready",true);
        if(Build.VERSION.SDK_INT==23){
            android.graphics.Typeface typeface=(android.graphics.Typeface)field(launcher,HomeActivity.class,"outfit");
            android.graphics.Paint paint=new android.graphics.Paint();paint.setTypeface(typeface);paint.setTextSize(40);
            require(paint.hasGlyph("Q") && paint.hasGlyph("T"),"Legacy English typeface has missing glyphs");
            try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand("dumpsys activity activities"));java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int length;while((length=input.read(bytes))!=-1)output.write(bytes,0,length);
                require(AdbController.parseForegroundUser(output.toString("UTF-8"))==android.os.Process.myUid()/100000,"Legacy foreground user differs from app user");
            }
        }
        for(int i=0;i<3;i++){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);test.waitForIdleSync();}
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        require(((Integer)field(launcher,HomeActivity.class,"row"))<=0 && !launcher.isFinishing(),"HOME Back failed");
        SetupActivity setup=(SetupActivity)test.startActivitySync(new Intent(context,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(setup,SetupActivity.class,"busy",false);
        require(field(setup,SetupActivity.class,"homeState")!=null,"Setup inspection failed");
        // Finish through the same registered action; screen 1 first returns to screen 0.
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        if(!setup.isFinishing()){test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();}
        require(setup.isFinishing(),"Setup Back failed");
        Activity management=test.startActivitySync(new Intent(context,AppManagementActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(management,AppManagementActivity.class,"busy",false);
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(AdbController adb=new AdbController(context,value->{})){
            AdbConnectionPanel panel=new AdbConnectionPanel((ManagementUi)management,adb,worker);
            test.runOnMainSync(()->{
                panel.show(()->{throw new AssertionError("Cancelled panel connected");});
            });
            if(Build.VERSION.SDK_INT==23){
                java.lang.reflect.Method keys=AdbController.class.getDeclaredMethod("loadKeys");keys.setAccessible(true);
                java.security.KeyStore first=(java.security.KeyStore)keys.invoke(adb),second=(java.security.KeyStore)keys.invoke(adb);
                require(first.getKey("adb",new char[0])!=null && java.util.Arrays.equals(first.getCertificate("adb").getEncoded(),second.getCertificate("adb").getEncoded()),"Legacy ADB key creation or persistence failed");
            }
            test.waitForIdleSync();
            // The active sheet's tagged pairing control must agree with actual native capability.
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> modes=java.util.Collections.emptyList();long end=SystemClock.elapsedRealtime()+10000;
            while(modes.isEmpty() && SystemClock.elapsedRealtime()<end){android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)modes=root.findAccessibilityNodeInfosByText("无线配对（实验）");if(modes.isEmpty())Thread.sleep(80);}
            require(!modes.isEmpty(),"Connection panel pairing control missing");
            for(android.view.accessibility.AccessibilityNodeInfo node:modes)require(node.isEnabled()==AdbController.pairingAvailable(),"Pairing enabled on unsupported platform");
            test.runOnMainSync(panel::close);require(!adb.connected(),"Cancelled panel retained ADB");
        }finally{worker.shutdownNow();}
        long focusEnd=SystemClock.elapsedRealtime()+10000;
        while(!management.hasWindowFocus() && SystemClock.elapsedRealtime()<focusEnd)Thread.sleep(80);
        require(management.hasWindowFocus(),"Management focus did not return after closing connection panel");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        if(!finishing(management,1500)){
            android.view.View list=(android.view.View)field(management,AppManagementActivity.class,"list");long end=SystemClock.elapsedRealtime()+5000;
            while(!list.hasFocus() && SystemClock.elapsedRealtime()<end)Thread.sleep(80);
            require(list.hasFocus(),"Application details Back did not restore list focus");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        }
        require(finishing(management,5000),"Application list Back failed");
    }
}
