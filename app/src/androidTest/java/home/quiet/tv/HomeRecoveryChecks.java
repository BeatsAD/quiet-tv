package home.quiet.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Exercises a real priority conflict, without widening production firmware rules. */
final class HomeRecoveryChecks {
    private final Instrumentation test;
    HomeRecoveryChecks(Instrumentation test){this.test=test;}
    void run() throws Exception {
        if(!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE))throw new AssertionError("Dedicated emulator only");
        Context context=test.getTargetContext();String stock="com.google.android.tvlauncher";
        int setting=context.getPackageManager().getApplicationEnabledSetting(stock);
        File state=new File(context.getCacheDir(),"home-recovery-"+UUID.randomUUID());if(!state.mkdirs())throw new IOException("Cannot isolate journal");
        Context isolated=new ContextWrapper(context){@Override public File getFilesDir(){return state;}};
        HomeController controller=new HomeController(isolated);
        try(AdbController adb=new AdbController(context,value->{})){
            adb.connect(5555);String user=" --user "+adb.user;
            String previous=HomeController.parseComponent(adb.shell("cmd package resolve-activity --brief"+user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));
            String disabled=adb.shell("pm list packages -d"+user);
            try{
                adb.shell("pm enable"+user+" "+stock);
                HomeController.State before=controller.inspect(adb);
                if(!before.known || !before.actual.startsWith(stock+"/"))throw new AssertionError("Expected system-priority HOME conflict");
                boolean refused=false;
                try{controller.switchToOwn(adb);}catch(IOException expected){refused=true;}
                if(!refused)throw new AssertionError("False-success HOME switch accepted");
                JSONObject journal=new SetupStore(isolated).get().getJSONObject("homeSwitch");
                if(!"rolled-back".equals(journal.getString("state")))throw new AssertionError("Failed HOME preference was not read back after rollback");
                if(!before.actual.equals(controller.inspect(adb).actual))throw new AssertionError("Original HOME changed");
                new SetupStore(isolated).homeSwitch(previous,"home.quiet.tv/.HomeActivity",adb.user,"running",previous);
                if(!"running".equals(new SetupStore(isolated).get().getJSONObject("homeSwitch").getString("state")))throw new AssertionError("Interrupted HOME record lost");
                // A second conflict occurs after a real fixture disable, while checking final HOME.
                adb.shell("pm disable-user"+user+" "+stock);
                adb.shell("cmd package set-home-activity"+user+" home.quiet.tv/.HomeActivity");
                String fixture="home.quiet.tv.homefixture";
                int fixtureSetting=context.getPackageManager().getApplicationEnabledSetting(fixture);
                try{
                    adb.shell("pm default-state"+user+" "+fixture);
                    File failureState=new File(context.getCacheDir(),"home-operation-recovery-"+UUID.randomUUID());
                    if(!failureState.mkdirs())throw new IOException("Cannot isolate operation journal");
                    Context operationContext=new ContextWrapper(context){@Override public File getFilesDir(){return failureState;}};
                    JSONObject rule=new JSONObject().put("fingerprint",Build.FINGERPRINT).put("apps",new JSONArray())
                            .put("home",new JSONObject().put("component",fixture+"/"+fixture+".HomeActivity").put("disableVerified",true));
                    SetupPresets rules=new SetupPresets(new JSONObject().put("schema",1).put("devices",new JSONArray().put(rule)).toString(),Build.BRAND,Build.MODEL,Build.FINGERPRINT);
                    AppRepository.App original=new AppRepository(context).find(adb,fixture);
                    boolean failed=false;
                    try{new SetupOperations(operationContext,rules).execute(adb,java.util.Collections.emptyList(),original,"DISABLE",new SetupOperations.Ticket(),value->{
                        if(value.equals("正在核对默认桌面…"))try{adb.shell("pm enable"+user+" "+stock);}catch(Exception e){throw new RuntimeException(e);}
                    });}catch(Exception expected){failed=true;}
                    AppRepository.App restored=new AppRepository(context).find(adb,fixture);
                    if(!failed || !restored.enabled || restored.enabledSetting!=0)throw new AssertionError("Original fixture's exact enabled state was not restored");
                    JSONObject run=new SetupStore(operationContext).get().getJSONObject("run");
                    if(!"incomplete".equals(run.getString("state")) || !run.getJSONArray("results").toString().contains("ROLLBACK"))throw new AssertionError("Failed fallback was incorrectly marked complete");
                }finally{adb.shell("pm "+(fixtureSetting==0?"default-state":fixtureSetting==1?"enable":"disable-user")+user+" "+fixture);}
            }finally{
                String operation=setting==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?"default-state":setting==PackageManager.COMPONENT_ENABLED_STATE_ENABLED?"enable":"disable-user";
                adb.shell("pm "+operation+user+" "+stock);
                adb.shell("cmd package set-home-activity"+user+" "+previous);
                String after=HomeController.parseComponent(adb.shell("cmd package resolve-activity --brief"+user+" -a android.intent.action.MAIN -c android.intent.category.HOME"));
                if(!previous.equals(after) || !disabled.equals(adb.shell("pm list packages -d"+user)))throw new AssertionError("Emulator HOME/package baseline not restored");
            }
        }
    }
}
