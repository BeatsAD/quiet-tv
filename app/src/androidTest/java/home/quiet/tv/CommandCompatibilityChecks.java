package home.quiet.tv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Exercise production operations on one disposable fixture, then restore its exact state. */
final class CommandCompatibilityChecks {
    private final Instrumentation test;
    CommandCompatibilityChecks(Instrumentation test){this.test=test;}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    void run() throws Exception {
        require("ranchu".equals(Build.HARDWARE)||"goldfish".equals(Build.HARDWARE),"Disposable emulator only");
        Context context=test.getTargetContext();new SetupStore(context).shouldOffer(context);
        test.startActivitySync(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        AppRepository repository=new AppRepository(context);AppOperations operations=new AppOperations(context);
        try(AdbController adb=new AdbController(context,value->{})){
            adb.connect(5555);adb.validateUser();
            AppRepository.App before=repository.find(adb,"home.quiet.tv.fixture");
            require(before!=null && before.installed && before.enabled && !before.system,"Enabled fixture missing");
            require(before.enabledSetting!=0 || adb.capabilities.defaultState,"Prepare the disposable fixture with pm enable before testing on systems without default-state");
            require(operations.actions(before).contains(AppOperations.Action.DISABLE),"Disable command was not detected");
            try{
                operations.perform(adb,before,AppOperations.Action.DISABLE);
                AppRepository.App disabled=repository.find(adb,before.pkg);require(!disabled.enabled,"Disable readback failed");
                require(operations.actions(disabled).contains(AppOperations.Action.ENABLE),"Enable command was not detected");
                operations.perform(adb,disabled,AppOperations.Action.ENABLE);
                require(repository.find(adb,before.pkg).enabled,"Enable readback failed");
                require(operations.history().contains("DISABLE") && operations.history().contains("ENABLE"),"Operation journal missing");
            }finally{
                adb.validateUser();adb.shell((before.enabledSetting==0?adb.capabilities.defaultStateCommand:adb.capabilities.enableCommand)+" --user "+adb.user+" "+before.pkg);
                AppRepository.App after=repository.find(adb,before.pkg);
                require(after.enabled && after.enabledSetting==before.enabledSetting,"Fixture state was not restored");
            }
            require(new HomeController(context).inspect(adb).ownUsable,"Connected HOME inspection failed");
        }
    }
}
