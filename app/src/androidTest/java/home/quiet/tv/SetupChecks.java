package home.quiet.tv;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Bitmap;
import org.json.*;
import java.io.*;
import java.util.*;

/** Mutates only a dedicated emulator's HOME fixture. Never run this scenario on a physical TV. */
final class SetupChecks {
    private final Instrumentation test;
    private final Context context;
    private static final String FIXTURE="home.quiet.tv.homefixture",COMPONENT=FIXTURE+"/"+FIXTURE+".HomeActivity";
    SetupChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private String shell(String command) throws Exception {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int size;while((size=in.read(buffer))!=-1)out.write(buffer,0,size);return out.toString("UTF-8").trim();}}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text){if(root==null)return null;if(text.equals(String.valueOf(root.getText())) || text.equals(String.valueOf(root.getContentDescription())))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo found=find(root.getChild(i),text);if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo await(String text) throws Exception {long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=find(test.getUiAutomation().getRootInActiveWindow(),text);if(found!=null)return found;Thread.sleep(100);}throw new AssertionError("Missing UI: "+text);}
    private void click(String text) throws Exception {AccessibilityNodeInfo node=await(text);require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cannot click "+text);test.waitForIdleSync();}
    private void capture(String name) throws Exception {test.waitForIdleSync();SystemClock.sleep(400);Bitmap bitmap=test.getUiAutomation().takeScreenshot();require(bitmap!=null,"No screenshot");try(FileOutputStream out=context.openFileOutput(name,0)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
    private void open() {test.startActivitySync(new Intent(context,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK));test.waitForIdleSync();}
    private String actualHome(String user) throws Exception {String value=HomeController.parseComponent(shell("cmd package resolve-activity --brief --user "+user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));ComponentName name=ComponentName.unflattenFromString(value);return name==null?"":name.flattenToString();}
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"This test is restricted to dedicated emulators");
        String user=shell("am get-current-user");
        String previous=HomeController.parseComponent(shell("cmd package resolve-activity --brief --user "+user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));
        require(!previous.isEmpty(),"Emulator HOME is unresolved");
        String disabledBefore=shell("pm list packages -d --user "+user);boolean stockSuppressed=false;
        ComponentName stock=ComponentName.unflattenFromString(previous);int stockSetting=context.getPackageManager().getApplicationEnabledSetting(stock.getPackageName());
        require(stockSetting==0 || stockSetting==1,"Unexpected stock HOME component baseline");
        JSONObject rule=new JSONObject().put("fingerprint",Build.FINGERPRINT).put("home",new JSONObject().put("component",COMPONENT).put("disableVerified",true).put("uninstallVerified",false).put("restoreInstallVerified",false)).put("apps",new JSONArray());
        SetupPresets presets=new SetupPresets(new JSONObject().put("schema",1).put("devices",new JSONArray().put(rule)).toString(),Build.BRAND,Build.MODEL,Build.FINGERPRINT);
        // Isolate test baselines from the production setup store while retaining the same PackageManager/user.
        File files=new File(context.getCacheDir(),"setup-check-"+UUID.randomUUID());require(files.mkdirs(),"Cannot create isolated state directory");
        Context isolated=new ContextWrapper(context){@Override public File getFilesDir(){return files;}};
        try{
            require(shell("pm path "+FIXTURE).startsWith("package:"),"Install the dedicated HOME fixture first");
            open();await("暂不设置，临时使用");capture("setup-default.png");click("暂不设置，临时使用");
            require(actualHome(user).equals(stock.flattenToString()),"Temporary use changed HOME");
            try(AdbController adb=new AdbController(context,v->{})){
                adb.connect(5555);HomeController home=new HomeController(isolated,presets);SetupOperations operations=new SetupOperations(isolated,presets);AppRepository repository=new AppRepository(context);
                AppRepository.App fixture=repository.find(adb,FIXTURE);require(fixture!=null && fixture.enabled,"Fixture is not enabled");
                require(new AppOperations(context).actions(fixture).isEmpty(),"Generic application manager may mutate HOME fixture");
                try{operations.execute(adb,Collections.emptyList(),fixture,"DISABLE",new SetupOperations.Ticket(),v->{});throw new AssertionError("Disabled current HOME");}catch(IOException expected){}
                // The stock Google TV HOME has priority 2: set-home can return Success without changing resolution.
                boolean switched=false;try{home.switchToOwn(adb);switched=true;}catch(IOException expected){}
                require(switched==home.inspect(adb).isOwn(context),"Switch result did not reflect actual HOME");
                require(repository.find(adb,FIXTURE).enabled,"Failed HOME switch changed fixture state");
                // Controlled-image arrangement only: temporarily suppress the higher-priority stock launcher.
                stockSuppressed=true;shell("pm disable-user --user "+user+" "+stock.getPackageName());
                shell("cmd package set-home-activity --user "+user+" "+COMPONENT);require(actualHome(user).equals(COMPONENT),"Fixture HOME setup failed");
                home.switchToOwn(adb);require(home.inspect(adb).isOwn(context),"HOME switch readback failed");
                SetupOperations.Ticket cancelled=new SetupOperations.Ticket();cancelled.cancel();
                try{operations.execute(adb,Collections.emptyList(),fixture,"DISABLE",cancelled,v->{});throw new AssertionError("Cancelled operation executed");}catch(IOException expected){}
                require(repository.find(adb,FIXTURE).enabled,"Cancelled selection disabled fixture");
                try{operations.execute(adb,Collections.emptyList(),fixture,"UNINSTALL",new SetupOperations.Ticket(),v->{});throw new AssertionError("Unverified uninstall was enabled");}catch(IOException expected){}
                require(repository.find(adb,FIXTURE).installed,"Unverified uninstall removed fixture");
                operations.execute(adb,Collections.emptyList(),fixture,"DISABLE",new SetupOperations.Ticket(),v->{});
                require(!repository.find(adb,FIXTURE).enabled && home.inspect(adb).isOwn(context),"Disable did not preserve Quiet TV HOME");
                operations.restore(adb,new SetupOperations.Ticket(),v->{});
                require(repository.find(adb,FIXTURE).enabled && home.inspect(adb).actual.equals(COMPONENT),"Restore did not return to fixture HOME");
                require(repository.find(adb,FIXTURE).enabledSetting==fixture.enabledSetting,"Restore changed original enabled setting");
                home.switchToOwn(adb);require(home.inspect(null).isOwn(context),"Offline HOME readback disagrees with shell");open();await("管理预装应用");await("完成设置");capture("setup-apps-unknown-firmware.png");click("不处理，进入桌面");
                require(home.inspect(adb).isOwn(context),"Skip changed default HOME");
                Set<String> disabled=AppRepository.parsePackages(shell("pm list packages -d --user "+user));disabled.remove(stock.getPackageName());
                require(AppRepository.parsePackages(disabledBefore).equals(disabled),"Other package states changed");
            }
        }finally{
            shell("pm default-state --user "+user+" "+FIXTURE);
            if(stockSuppressed)shell("pm "+(stockSetting==0?"default-state":"enable")+" --user "+user+" "+stock.getPackageName());
            shell("cmd package set-home-activity --user "+user+" "+previous);
            require(actualHome(user).equals(stock.flattenToString()),"Original HOME was not restored");
            require(AppRepository.parsePackages(disabledBefore).equals(AppRepository.parsePackages(shell("pm list packages -d --user "+user))),"Disabled package baseline was not restored");
        }
    }
}
