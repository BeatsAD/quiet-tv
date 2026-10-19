package home.quiet.tv;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Black-box entry checks: no references to names or members rewritten by R8. */
final class OptimizedSmoke implements Application.ActivityLifecycleCallbacks {
    private final Instrumentation test;
    private volatile Activity current;
    OptimizedSmoke(Instrumentation test){this.test=test;}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text){
        if(node==null)return null;
        if(String.valueOf(node.getText()).contains(text) || String.valueOf(node.getContentDescription()).contains(text))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),text);if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo await(String text) throws Exception {
        long until=SystemClock.elapsedRealtime()+25000;
        while(SystemClock.elapsedRealtime()<until){
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
            if(find(root,"Viewing full screen")!=null){AccessibilityNodeInfo got=exact(root,"GOT IT");if(got!=null)got.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
            if(text.equals("关于")){AccessibilityNodeInfo skip=exact(root,"暂不设置，临时使用");if(skip!=null)skip.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
            AccessibilityNodeInfo node=find(root,text);if(node!=null)return node;Thread.sleep(150);
            final AccessibilityNodeInfo[] nativeNode={null};
            test.runOnMainSync(()->{View view=nativeView(text,false);if(view!=null)nativeNode[0]=view.createAccessibilityNodeInfo();});
            if(nativeNode[0]!=null)return nativeNode[0];
        }
        throw new AssertionError("Missing optimized UI: "+text+"; "+describe(test.getUiAutomation().getRootInActiveWindow()));
    }
    private String describe(AccessibilityNodeInfo node){if(node==null)return "no window";StringBuilder result=new StringBuilder(String.valueOf(node.getText())).append('/').append(node.getContentDescription()).append(';');for(int i=0;i<node.getChildCount();i++)result.append(describe(node.getChild(i)));return result.toString();}
    private AccessibilityNodeInfo exact(AccessibilityNodeInfo node,String text){
        if(node==null)return null;
        if(text.equals(String.valueOf(node.getText())) || text.equals(String.valueOf(node.getContentDescription())))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=exact(node.getChild(i),text);if(found!=null)return found;}return null;
    }
    private void click(String text) throws Exception {
        await(text);AccessibilityNodeInfo node=exact(test.getUiAutomation().getRootInActiveWindow(),text);
        boolean clicked=node!=null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if(!clicked){final boolean[] result={false};test.runOnMainSync(()->{View view=nativeView(text,true);if(view!=null)result[0]=view.performClick();});clicked=result[0];}
        if(!clicked)throw new AssertionError("Cannot click "+text);test.waitForIdleSync();
    }
    private View nativeView(String text,boolean exact){Activity activity=current;if(activity==null || activity.isDestroyed())return null;View root=activity.getWindow().getDecorView();return root.hasWindowFocus()?nativeView(root,text,exact):null;}
    private View nativeView(View view,String text,boolean exact){
        if(!view.isShown())return null;String label=view instanceof TextView?String.valueOf(((TextView)view).getText()):"";
        String desc=String.valueOf(view.getContentDescription());
        if(exact?(text.equals(label)||text.equals(desc)):(label.contains(text)||desc.contains(text)))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=nativeView(((ViewGroup)view).getChildAt(i),text,exact);if(found!=null)return found;}return null;
    }
    public void onActivityResumed(Activity activity){current=activity;}
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityPaused(Activity a){}
    public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
    private void open(String activity){Context context=test.getTargetContext();test.runOnMainSync(()->context.startActivity(new Intent().setClassName(context.getPackageName(),"home.quiet.tv."+activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));test.waitForIdleSync();}
    private AccessibilityNodeInfo input(AccessibilityNodeInfo node,String hint){
        if(node==null)return null;if("android.widget.EditText".contentEquals(node.getClassName()) && hint.equals(String.valueOf(node.getHintText())))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=input(node.getChild(i),hint);if(found!=null)return found;}return null;
    }
    private void setInput(String hint,String value) throws Exception {
        AccessibilityNodeInfo node=input(test.getUiAutomation().getRootInActiveWindow(),hint);
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        if(node==null || !node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("Cannot set test pairing field: "+hint);
    }
    private void pairingCancellationUi() throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            server.setSoTimeout(7000);click("ADB 未连接");click("无线配对（实验）");
            setInput("配对端口",String.valueOf(server.getLocalPort()));setInput("六位配对码（已配对可留空）","000000");setInput("连接端口","5555");click("连接");
            try(Socket reachability=server.accept()){}
            try(Socket handshake=server.accept()){
                handshake.setSoTimeout(3000);
                if(handshake.getInputStream().read()!=22)throw new AssertionError("Optimized TLS ClientHello missing");
                click("取消");byte[] data=new byte[2048];while(handshake.getInputStream().read(data)>=0){}
            }
        }
        await("ADB 未连接");
    }
    void run() throws Exception {
        if(!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))throw new AssertionError("Dedicated emulator only");
        Application application=(Application)test.getTargetContext().getApplicationContext();application.registerActivityLifecycleCallbacks(this);
        try{
        test.getUiAutomation().getServiceInfo();
        open("HomeActivity");await("关于");
        open("AboutActivity");await("1.1.0");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        open("DockEditorActivity");await("常用应用");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        ClassLoader loader=test.getTargetContext().getClassLoader();
        Class.forName("io.github.muntashirakon.crypto.spake2.Spake2Context",true,loader);
        if(!Boolean.TRUE.equals(Class.forName("org.conscrypt.Conscrypt",true,loader).getMethod("isAvailable").invoke(null)))throw new AssertionError("Conscrypt native unavailable");
        open("AppManagementActivity");await("个应用");
        if(Build.VERSION.SDK_INT>=33)pairingCancellationUi();
        click("ADB 未连接");click("连接");
        long until=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<until){
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
            if(find(root,"ADB 已连接")!=null)break;
            AccessibilityNodeInfo allow=exact(root,"Allow");if(allow==null)allow=exact(root,"允许");
            if(allow==null && find(root,"Allow USB debugging")!=null)allow=exact(root,"OK");
            if(allow!=null)allow.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Thread.sleep(150);
        }
        await("ADB 已连接");
        click("更多");click("首次设置");await("默认桌面");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        open("HomeActivity");await("关于");
        }finally{application.unregisterActivityLifecycleCallbacks(this);}
    }
}
