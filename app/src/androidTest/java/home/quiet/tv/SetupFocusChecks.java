package home.quiet.tv;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;

/** Only UI fixture metadata is changed; no device HOME or package state is written. */
final class SetupFocusChecks {
    private final Instrumentation test;
    private final Context context;
    private SetupActivity activity;
    private HomeController home;
    private HomeController.State state;
    private AppRepository.App original;
    private final ArrayList<AppRepository.App> apps=new ArrayList<>();
    private final ArrayList<String> trace=new ArrayList<>();
    interface Ui {void run() throws Exception;}
    SetupFocusChecks(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void ui(Ui action) throws Exception {Throwable[] failed=new Throwable[1];test.runOnMainSync(()->{try{action.run();}catch(Throwable e){failed[0]=e;}});if(failed[0]!=null)throw new AssertionError(failed[0]);}
    private Object get(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void set(String name,Object value) throws Exception {Field f=SetupActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void render() throws Exception {Method m=SetupActivity.class.getDeclaredMethod("render");m.setAccessible(true);m.invoke(activity);}
    private View control(String tag){return activity.root.findViewWithTag(tag);}
    private View focused() throws Exception {View[] value=new View[1];ui(()->{
        value[0]=activity.getCurrentFocus();Object panel=get(get(activity,"connection"),"panel");
        if(panel!=null){ManagementUi.Sheet sheet=(ManagementUi.Sheet)get(panel,"sheet"),guide=(ManagementUi.Sheet)get(panel,"guide");if(guide!=null && guide.dialog.isShowing())value[0]=guide.dialog.getCurrentFocus();else if(sheet.dialog.isShowing())value[0]=sheet.dialog.getCurrentFocus();}
    });return value[0];}
    private void expect(String tag) throws Exception {View focus=focused();require(focus!=null && tag.equals(focus.getTag()),"Expected "+tag+", got "+(focus==null?"null":focus.getTag()+" / "+focus.getClass()));}
    private void settle(){test.waitForIdleSync();SystemClock.sleep(200);}
    private void key(int code) throws Exception {test.sendKeyDownUpSync(code);settle();View focus=focused();trace.add(code+" -> "+(focus==null?"null":focus.getTag()));require(!(focus instanceof ScrollView),"Container captured focus");}
    private Bitmap capture(String name) throws Exception {settle();Bitmap image=test.getUiAutomation().takeScreenshot();require(image!=null,"Screenshot unavailable");try(FileOutputStream out=context.openFileOutput(name,0)){image.compress(Bitmap.CompressFormat.PNG,100,out);}return image;}
    private void ring(View control,boolean visible) throws Exception {
        int[] location=new int[2];int[] size=new int[2];ui(()->{control.getLocationOnScreen(location);size[0]=control.getWidth();size[1]=control.getHeight();});
        Bitmap image=test.getUiAutomation().takeScreenshot();require(image!=null,"No pixels");int gap=activity.px(3);
        int[][] probes={{location[0]+size[0]/2,location[1]-gap},{location[0]+size[0]/2,location[1]+size[1]+gap},{location[0]-gap,location[1]+size[1]/2},{location[0]+size[0]+gap,location[1]+size[1]/2}};
        for(int[] point:probes){int pixel=image.getPixel(point[0],point[1]);boolean white=Color.red(pixel)>235 && Color.green(pixel)>235 && Color.blue(pixel)>235;if(white!=visible){try(FileOutputStream out=context.openFileOutput("focus-pixel-failure.png",0)){image.compress(Bitmap.CompressFormat.PNG,100,out);}}require(white==visible,"Focus outline pixels missing/unexpected at "+Arrays.toString(point)+" for "+control.getTag()+" pixel="+Integer.toHexString(pixel)+" windowFocus="+control.hasWindowFocus());}image.recycle();
    }
    private void prepare(int screen,int enabled,int count) throws Exception {
        ui(()->{
            for(int i=0;i<apps.size();i++){apps.get(i).enabled=i<enabled;apps.get(i).installed=i!=5 || enabled==6;}
            set("home",home);set("homeState",state);set("operations",new AppOperations(context,home.presets));set("apps",new ArrayList<>(apps.subList(0,count)));set("original",original);set("originalChoice","KEEP");set("originalTouched",false);set("busy",false);set("recovery",false);set("screen",screen);set("notice","");set("renderedScreen",-1);set("lastHome","");set("lastCard","");set("lastBottom","");set("lastMain","");
            Map<String,Boolean> choices=(Map<String,Boolean>)get(activity,"choices");choices.clear();for(AppRepository.App app:apps)choices.put(app.pkg,true);render();
        });settle();
    }
    private String record() throws Exception {return new SetupStore(context).get().toString();}
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Dedicated emulator only");
        File recordFile=new File(context.getFilesDir(),"setup-state.json");byte[] saved=recordFile.exists()?Files.readAllBytes(recordFile.toPath()):null;
        String homeBefore=new HomeController(context).inspect(null).actual;
        try{
            JSONArray rules=new JSONArray();
            for(int i=0;i<6;i++){AppRepository.App app=new AppRepository.App();app.pkg="home.quiet.tv.focusfixture"+i;app.label="示例应用 "+(i+1);app.metadataKnown=true;app.system=true;app.recoverable=true;app.icon=context.getDrawable(R.drawable.ic_manage_apps);apps.add(app);rules.put(new JSONObject().put("package",app.pkg).put("recommendDisable",true).put("actions",new JSONArray().put("DISABLE")));}
            String homeComponent="home.quiet.tv.homefixture/home.quiet.tv.homefixture.HomeActivity";
            JSONObject rule=new JSONObject().put("fingerprint",Build.FINGERPRINT).put("apps",rules).put("home",new JSONObject().put("component",homeComponent).put("disableVerified",true));
            SetupPresets presets=new SetupPresets(new JSONObject().put("schema",1).put("devices",new JSONArray().put(rule)).toString(),Build.BRAND,Build.MODEL,Build.FINGERPRINT);home=new HomeController(context,presets);
            state=new HomeController.State();state.known=true;state.ownUsable=true;state.actual=new ComponentName(context,HomeActivity.class).flattenToString();
            original=new AppRepository.App();original.pkg="home.quiet.tv.homefixture";original.label="原桌面测试";original.installed=true;original.metadataKnown=true;
            activity=(SetupActivity)test.startActivitySync(new Intent(context,SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long end=SystemClock.elapsedRealtime()+15000;boolean[] busy={true};while(SystemClock.elapsedRealtime()<end){ui(()->busy[0]=(boolean)get(activity,"busy"));if(!busy[0])break;SystemClock.sleep(100);}require(!busy[0],"Initial scan did not settle");
            prepare(0,0,6);expect("primary");ring(control("primary"),true);capture("focus-main-focused.png").recycle();
            key(KeyEvent.KEYCODE_DPAD_DOWN);expect("back");ring(control("primary"),false);capture("focus-main-unfocused.png").recycle();key(KeyEvent.KEYCODE_DPAD_UP);expect("primary");key(KeyEvent.KEYCODE_DPAD_CENTER);expect("home-KEEP");
            String readonlyBaseline=record();ring(control("home-KEEP"),true);capture("focus-home-keep.png").recycle();
            key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("home-DISABLE");ring(control("home-DISABLE"),true);capture("focus-home-disable.png").recycle();key(KeyEvent.KEYCODE_DPAD_CENTER);expect("home-DISABLE");require(record().equals(readonlyBaseline),"Readonly disable persisted a choice");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("home-UNINSTALL");ring(control("home-UNINSTALL"),true);key(KeyEvent.KEYCODE_DPAD_CENTER);expect("home-UNINSTALL");require(record().equals(readonlyBaseline),"Readonly uninstall persisted a choice");capture("focus-home-uninstall.png").recycle();
            key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("home-UNINSTALL");key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_LEFT);expect("home-KEEP");key(KeyEvent.KEYCODE_DPAD_DOWN);expect("app-"+apps.get(0).pkg);
            ring(control("app-"+apps.get(0).pkg),true);key(KeyEvent.KEYCODE_DPAD_CENTER);require(record().equals(readonlyBaseline),"Readonly card changed choices");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("app-"+apps.get(1).pkg);key(KeyEvent.KEYCODE_DPAD_DOWN);expect("app-"+apps.get(3).pkg);key(KeyEvent.KEYCODE_DPAD_LEFT);expect("app-"+apps.get(2).pkg);key(KeyEvent.KEYCODE_DPAD_DOWN);expect("app-"+apps.get(4).pkg);key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("app-"+apps.get(5).pkg);ring(control("app-"+apps.get(5).pkg),true);key(KeyEvent.KEYCODE_DPAD_DOWN);expect("primary");ring(control("primary"),true);capture("focus-complete-focused.png").recycle();
            key(KeyEvent.KEYCODE_DPAD_LEFT);expect("skip");ring(control("primary"),false);capture("focus-complete-unfocused.png").recycle();key(KeyEvent.KEYCODE_DPAD_LEFT);expect("inventory");key(KeyEvent.KEYCODE_DPAD_DOWN);expect("back");key(KeyEvent.KEYCODE_DPAD_UP);expect("inventory");key(KeyEvent.KEYCODE_DPAD_UP);expect("app-"+apps.get(5).pkg);require(record().equals(readonlyBaseline),"Readonly navigation wrote configuration");
            prepare(1,0,0);expect("home-KEEP");key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("home-DISABLE");key(KeyEvent.KEYCODE_DPAD_DOWN);expect("primary");key(KeyEvent.KEYCODE_DPAD_UP);expect("home-DISABLE");capture("focus-empty-grid.png").recycle();
            prepare(1,3,6);expect("home-KEEP");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_RIGHT);expect("app-"+apps.get(1).pkg);key(KeyEvent.KEYCODE_DPAD_CENTER);expect("app-"+apps.get(1).pkg);ring(control("app-"+apps.get(1).pkg),true);
            prepare(1,6,6);expect("home-KEEP");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);expect("app-"+apps.get(0).pkg);ring(control("app-"+apps.get(0).pkg),true);capture("focus-enabled-grid.png").recycle();
            // Readonly state and enabled state share routes, without granting execution capability.
            original.enabled=true;prepare(1,6,6);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);expect("home-DISABLE");require("DISABLE".equals(new SetupStore(context).get().optString("originalChoice")),"Allowed original choice did not persist");original.enabled=false;
            // First screen with system-role and ADB fallback; modal navigation uses actual keys.
            state.actual="home.quiet.tv.homefixture/home.quiet.tv.homefixture.HomeActivity";state.roleAvailable=true;prepare(0,0,6);expect("primary");key(KeyEvent.KEYCODE_DPAD_DOWN);expect("adb");key(KeyEvent.KEYCODE_DPAD_CENTER);
            Object panel=get(get(activity,"connection"),"panel");ManagementUi.Sheet sheet=(ManagementUi.Sheet)get(panel,"sheet");
            View[] modal=new View[1];ui(()->modal[0]=sheet.dialog.getCurrentFocus());require("connect".equals(modal[0].getTag()),"Dialog initial focus missing");ring(control("adb"),false);ring(modal[0],true);capture("focus-connection.png").recycle();
            key(KeyEvent.KEYCODE_DPAD_UP);ui(()->modal[0]=sheet.dialog.getCurrentFocus());require("connection-settings".equals(modal[0].getTag()),"Folded settings unreachable");key(KeyEvent.KEYCODE_DPAD_CENTER);ui(()->modal[0]=sheet.dialog.getCurrentFocus());require("connection-settings".equals(modal[0].getTag()),"Expansion lost focus");
            key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);ui(()->modal[0]=sheet.dialog.getCurrentFocus());require("connection-port".equals(modal[0].getTag()),"Port unreachable");ring(modal[0],true);
            key(KeyEvent.KEYCODE_BACK);expect("adb");ring(control("adb"),true);
            AppRepository.App knownOriginal=original;original=null;prepare(1,0,0);expect("home-KEEP");String unknownBaseline=record();key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);expect("home-DISABLE");require(unknownBaseline.equals(record()),"Unknown original changed a choice");original=knownOriginal;
            // An unusable HOME still leaves a visible refresh / exit path.
            state.ownUsable=false;prepare(0,0,0);expect("refresh");key(KeyEvent.KEYCODE_DPAD_DOWN);expect("back");key(KeyEvent.KEYCODE_DPAD_UP);expect("refresh");
            require(homeBefore.equals(new HomeController(context).inspect(null).actual),"UI test changed actual HOME");
            try(FileOutputStream out=context.openFileOutput("setup-focus-trace.txt",0)){out.write(String.join("\n",trace).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        }finally{
            if(activity!=null)ui(()->activity.finish());
            if(saved!=null)Files.write(recordFile.toPath(),saved);else if(recordFile.exists())require(recordFile.delete(),"Cannot remove single test record");
        }
    }
}
