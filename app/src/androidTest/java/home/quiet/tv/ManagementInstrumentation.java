package home.quiet.tv;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;

/** Runs inside the launcher process, with its own app key and app permissions. */
public final class ManagementInstrumentation extends Instrumentation {
    private String scenario="adb";
    @Override public void onCreate(Bundle args){super.onCreate(args);scenario=args.getString("scenario","adb");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        if("about".equals(scenario)){
            try{new AboutChecks(this).run();result.putString("stream","PASS: About installed metadata / remote entry / offline QR and licenses / external-link fallback / HOME focus restoration\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVAboutChecks","About failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("setup-prototype".equals(scenario)){
            try{new SetupPrototypeChecks(this).run();result.putString("stream","PASS: native prototype split preview / horizontal factory options / two-column presets / disconnected selection / D-pad / cancellation / actual TCP authorization continuation; emulator HOME and package baseline restored\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Setup prototype failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("connection-ui".equals(scenario)){
            try{new SetupChecks(this).connectionUi();result.putString("stream","PASS: native TCP/wireless mode switching and cancel; HOME and packages unchanged\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Connection UI failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("setup-fresh".equals(scenario) || "setup-upgrade".equals(scenario)){
            try{new SetupChecks(this).onboarding("setup-upgrade".equals(scenario));result.putString("stream","PASS: "+scenario+" onboarding classification and state\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Onboarding check failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("pairing-cancel".equals(scenario)){
            try{new SetupChecks(this).pairingCancellation();result.putString("stream","PASS: native TLS/SPAKE2 capability; actual upstream TLS handshake interrupted by cancellation; no ADB continuation\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Pairing cancellation failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("setup".equals(scenario)){
            try{new SetupChecks(this).run();result.putString("stream","PASS: emulator native setup / temporary use / HOME readback / cancellation / unverified uninstall denied / fixture disable and exact enable-state restore / skip; original HOME restored\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Setup check failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("visual".equals(scenario)){
            try{new ManagementChecks(this).visual();result.putString("stream","PASS: native search empty/clear, refresh, history, real app metadata, Dock tab; configuration unchanged\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Visual check failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("apps".equals(scenario) || "uninstall".equals(scenario)){
            try{new ManagementChecks(this).run("uninstall".equals(scenario));result.putString("stream","PASS: "+scenario+" native UI / package readback / Dock linkage / confirmations / journal; original Dock restored\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Management failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("dock".equals(scenario)){
            try{new DockChecks(this).run();result.putString("stream","PASS: Dock empty/add/remove/1-4 slots/replace/reorder save/cancel; original configuration restored\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Dock failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        try(AdbController adb=new AdbController(getTargetContext(),value->{Bundle status=new Bundle();status.putString("stream",value+"\n");sendStatus(0,status);})){
            adb.connect(5555);
            String packages=adb.shell("pm list packages --user "+adb.user);
            if(!packages.contains("package:home.quiet.tv"))throw new AssertionError("Own package missing");
            if("connect".equals(scenario)){
                result.putString("stream","PASS: post-reboot in-app ADB; host="+adb.host+"; user="+adb.user+"; packages="+packages.split("\\n").length+"\n");finish(-1,result);return;
            }
            String fixture="home.quiet.tv.fixture";
            try{
                adb.shell("pm disable-user --user "+adb.user+" "+fixture);
                if(!adb.shell("pm list packages -d --user "+adb.user).contains("package:"+fixture))throw new AssertionError("Disable did not take effect");
            }finally{adb.shell("pm enable --user "+adb.user+" "+fixture);}
            if(!adb.shell("pm list packages -e --user "+adb.user).contains("package:"+fixture))throw new AssertionError("Enable did not take effect");
            result.putString("stream","PASS: fixture disable/enable; in-app ADB; host="+adb.host+"; user="+adb.user+"; packages="+packages.split("\\n").length+"\n");
            finish(-1,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(0,result);}
    }
}
