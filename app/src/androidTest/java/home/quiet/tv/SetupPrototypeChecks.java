package home.quiet.tv;

import android.app.Instrumentation;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real native layout and authorization continuation checks, with rules injected only in this emulator process. */
final class SetupPrototypeChecks {
    private final Instrumentation test;
    private final Context context;
    private static final String FIXTURE="home.quiet.tv.fixture";
    SetupPrototypeChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private String shell(String command) throws Exception {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8").trim();}}
    private Object get(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void set(Object object,String name,Object value) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    private void render(SetupActivity activity) throws Exception {Method m=SetupActivity.class.getDeclaredMethod("render");m.setAccessible(true);m.invoke(activity);}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text){if(node==null)return null;if(text.equals(String.valueOf(node.getText())) || text.equals(String.valueOf(node.getContentDescription())))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=find(node.getChild(i),text);if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo await(String text) throws Exception {long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo node=find(test.getUiAutomation().getRootInActiveWindow(),text);if(node!=null)return node;Thread.sleep(100);}throw new AssertionError("Missing UI: "+text);}
    private void click(String text) throws Exception {require(await(text).performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cannot click "+text);test.waitForIdleSync();}
    private void capture(String name) throws Exception {test.waitForIdleSync();SystemClock.sleep(300);Bitmap b=test.getUiAutomation().takeScreenshot();require(b!=null,"No screenshot");try(FileOutputStream out=context.openFileOutput(name,0)){b.compress(Bitmap.CompressFormat.PNG,100,out);}}
    private void restorePreferences(android.content.SharedPreferences prefs,Map<String,?> values){android.content.SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){Object v=entry.getValue();if(v instanceof Boolean)e.putBoolean(entry.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(entry.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(entry.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(entry.getKey(),(Float)v);else if(v instanceof String)e.putString(entry.getKey(),(String)v);}e.commit();}
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Dedicated emulator only");
        String user=shell("am get-current-user");
        String previous=HomeController.parseComponent(shell("cmd package resolve-activity --brief --user "+user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));
        require(!previous.isEmpty(),"No HOME baseline");ComponentName stock=ComponentName.unflattenFromString(previous);
        int stockSetting=context.getPackageManager().getApplicationEnabledSetting(stock.getPackageName());
        int fixtureSetting=context.getPackageManager().getApplicationEnabledSetting(FIXTURE);
        String disabled=shell("pm list packages -d --user "+user);boolean suppressed=false;
        android.content.SharedPreferences wallpapers=context.getSharedPreferences("wallpapers",0),ports=context.getSharedPreferences("adb",0);Map<String,?> wallpaperBefore=wallpapers.getAll(),portsBefore=ports.getAll();
        SetupActivity activity=null;
        try{
            wallpapers.edit().putBoolean("default",false).putInt("index",0).commit();
            activity=(SetupActivity)test.startActivitySync(new Intent(context,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final SetupActivity ui=activity;await("设为默认桌面");test.waitForIdleSync();
            test.runOnMainSync(()->{View preview=ui.root.findViewWithTag("home-preview"),primary=ui.root.findViewWithTag("primary"),steps=ui.root.findViewWithTag("steps");require(preview!=null && steps!=null,"Default preview or step indicator missing");int[] a=new int[2],b=new int[2];primary.getLocationOnScreen(a);preview.getLocationOnScreen(b);require(a[0]+primary.getWidth()<b[0],"Default page is not split into copy and preview");require(primary.getWidth()<ui.root.getWidth()/2,"Default action is still full width");});
            capture("setup-prototype-default.png");
            suppressed=true;shell("pm disable-user --user "+user+" "+stock.getPackageName());shell("cmd package set-home-activity --user "+user+" home.quiet.tv/.HomeActivity");
            AppRepository repository=new AppRepository(context);List<AppRepository.App> actual=repository.load(null);ArrayList<AppRepository.App> shown=new ArrayList<>();
            for(AppRepository.App app:actual)if(FIXTURE.equals(app.pkg)){require(app.installed && app.enabled,"Fixture must be enabled");shown.add(app);}
            require(shown.size()==1,"Install the application fixture first");
            for(AppRepository.App app:actual)if(shown.size()<6 && app.system && app.installed && app.enabled && !app.pkg.equals("android") && !app.pkg.equals(stock.getPackageName()) && !app.pkg.contains("launcher"))shown.add(app);
            require(shown.size()==6,"Need six actual package records for the two-column layout check");
            JSONArray rules=new JSONArray();for(AppRepository.App app:shown)rules.put(new JSONObject().put("package",app.pkg).put("recommendDisable",true).put("actions",new JSONArray().put("DISABLE").put("ENABLE")));
            JSONObject device=new JSONObject().put("fingerprint",Build.FINGERPRINT).put("apps",rules);
            SetupPresets presets=new SetupPresets(new JSONObject().put("schema",1).put("devices",new JSONArray().put(device)).toString(),Build.BRAND,Build.MODEL,Build.FINGERPRINT);
            HomeController home=new HomeController(context,presets);HomeController.State state=home.inspect(null);require(state.isOwn(context),"HOME change was not read back");
            test.runOnMainSync(()->{try{set(ui,"home",home);set(ui,"operations",new AppOperations(context,presets));set(ui,"homeState",state);set(ui,"apps",new ArrayList<>(shown));Map<String,Boolean> choices=(Map<String,Boolean>)get(ui,"choices");choices.clear();for(AppRepository.App app:shown)choices.put(app.pkg,true);set(ui,"screen",1);set(ui,"busy",false);set(ui,"originalChoice","KEEP");set(ui,"notice","");render(ui);}catch(Exception e){throw new RuntimeException(e);}});
            await("禁用 6 项并完成");
            test.runOnMainSync(()->{LinearLayout factory=ui.root.findViewWithTag("factory-options"),grid=ui.root.findViewWithTag("preset-grid");require(factory.getOrientation()==LinearLayout.HORIZONTAL && factory.getChildCount()==3,"Factory choices are not a horizontal segment");require(grid.getChildCount()==3,"Six presets are not arranged in three rows");LinearLayout row=(LinearLayout)grid.getChildAt(0);require(row.getChildCount()==2,"Preset grid does not have two columns");int[] a=new int[2],b=new int[2],end=new int[2],button=new int[2];row.getChildAt(0).getLocationOnScreen(a);row.getChildAt(1).getLocationOnScreen(b);grid.getChildAt(2).getLocationOnScreen(end);ui.root.findViewWithTag("primary").getLocationOnScreen(button);require(a[1]==b[1] && a[0]+row.getChildAt(0).getWidth()<b[0],"Cards overlap or are not aligned");require(end[1]+grid.getChildAt(2).getHeight()<=button[1],"Preset cards overlap footer action");for(AppRepository.App app:shown)require(ui.root.findViewWithTag("app-"+app.pkg).isFocusable(),"Disconnected candidate cannot be selected");});
            capture("setup-prototype-presets.png");
            // Change selections with actual D-pad focus before asking for authorization.
            test.runOnMainSync(()->ui.root.findViewWithTag("app-"+shown.get(0).pkg).requestFocus());test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);test.waitForIdleSync();
            test.runOnMainSync(()->require(ui.getCurrentFocus()==ui.root.findViewWithTag("app-"+shown.get(1).pkg),"Right did not move to the adjacent preset"));
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);await("禁用 5 项并完成");
            test.runOnMainSync(()->require(ui.getCurrentFocus()==ui.root.findViewWithTag("app-"+shown.get(1).pkg),"Toggle lost D-pad focus"));
            for(int i=2;i<shown.size();i++){final String tag="app-"+shown.get(i).pkg;test.runOnMainSync(()->ui.root.findViewWithTag(tag).performClick());}
            await("禁用 1 项并完成");click("禁用 1 项并完成");await("连接电视");capture("setup-prototype-connection.png");click("打开系统设置");await("在电视系统设置中操作");click("返回连接");await("连接电视");click("取消");await("禁用 1 项并完成");
            require(repository.find(null,FIXTURE).enabled,"Cancel disabled the fixture");Set<String> afterCancel=AppRepository.parsePackages(shell("pm list packages -d --user "+user));afterCancel.remove(stock.getPackageName());require(AppRepository.parsePackages(disabled).equals(afterCancel),"Cancel changed other packages");
            // An offline snapshot that changed during authorization must fail before any package write.
            try(AdbController check=new AdbController(context,value->{})){
                check.connect(5555);AppRepository.App stale=repository.find(check,FIXTURE);stale.version+="-changed";
                try{new SetupOperations(context,presets).execute(check,Collections.singletonList(stale),null,"KEEP",new SetupOperations.Ticket(),value->{});throw new AssertionError("Stale selection was executed");}catch(IOException expected){}
                require(repository.find(check,FIXTURE).enabled,"Stale selection changed the fixture");
            }
            // The same frozen selection must proceed automatically after a real local TCP connection.
            String beforeRun=new SetupStore(context).get().optJSONObject("run")==null?"":new SetupStore(context).get().getJSONObject("run").optString("id");
            click("禁用 1 项并完成");await("连接电视");click("连接并应用设置");long end=SystemClock.elapsedRealtime()+20000;boolean complete=false;while(SystemClock.elapsedRealtime()<end){JSONObject run=new SetupStore(context).get().optJSONObject("run");if(run!=null && !beforeRun.equals(run.optString("id")) && "complete".equals(run.optString("state"))){complete=true;break;}Thread.sleep(150);}require(complete,"Authorized selection did not finish automatically");test.waitForIdleSync();
            require(!repository.find(null,FIXTURE).enabled,"Authorization did not continue the frozen selection");
            require(HomeController.parseComponent(shell("cmd package resolve-activity --brief --user "+user+" -a android.intent.action.MAIN -c android.intent.category.HOME")).contains("home.quiet.tv/"),"Apply changed default HOME");
        }finally{
            if(activity!=null){SetupActivity last=activity;test.runOnMainSync(last::finish);}
            shell("pm "+(fixtureSetting==0?"default-state":"enable")+" --user "+user+" "+FIXTURE);
            if(suppressed)shell("pm "+(stockSetting==0?"default-state":"enable")+" --user "+user+" "+stock.getPackageName());
            shell("cmd package set-home-activity --user "+user+" "+previous);
            restorePreferences(wallpapers,wallpaperBefore);restorePreferences(ports,portsBefore);
            require(AppRepository.parsePackages(disabled).equals(AppRepository.parsePackages(shell("pm list packages -d --user "+user))),"Package baseline not restored");
        }
    }
}
