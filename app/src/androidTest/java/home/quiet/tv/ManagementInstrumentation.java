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
        if("state-digest".equals(scenario)){
            try{
                if(!"ranchu".equals(android.os.Build.HARDWARE) && !"goldfish".equals(android.os.Build.HARDWARE))throw new AssertionError("Dedicated emulator only");
                Context context=getTargetContext();org.json.JSONObject snapshot=new org.json.JSONObject();
                snapshot.put("firstInstallTime",context.getPackageManager().getPackageInfo(context.getPackageName(),0).firstInstallTime);
                for(String name:new String[]{"files/local-adb.p12","files/setup-state.json","shared_prefs/dock.xml","shared_prefs/wallpapers.xml"}){
                    java.io.File file=new java.io.File(context.getDataDir(),name);String digest="absent";
                    if(file.isFile()){
                        java.security.MessageDigest sha=java.security.MessageDigest.getInstance("SHA-256");
                        try(java.io.InputStream input=new java.io.FileInputStream(file)){byte[] bytes=new byte[8192];int size;while((size=input.read(bytes))!=-1)sha.update(bytes,0,size);}
                        StringBuilder hex=new StringBuilder();for(byte value:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));digest=hex.toString();
                    }
                    snapshot.put(name,digest);
                }
                result.putString("stream","STATE_DIGEST: "+snapshot+"\n");finish(-1,result);
            }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("rotation".equals(scenario)){
            try{new HomeMemoryChecks(this).run(true);result.putString("stream","PASS: actual five-minute wallpaper rotation; original wallpaper preferences restored\n");finish(-1,result);}
            catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("identity-reject".equals(scenario)){
            try{IdentityChecks.run(this);result.putString("stream","PASS: boot_id unavailable / shell cannot read private challenge / connection rejected / no package writes\n");finish(-1,result);}
            catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("home-recovery".equals(scenario)){
            try{new HomeRecoveryChecks(this).run();result.putString("stream","PASS: actual priority conflict / false-success rejected / HOME rollback readback / interrupted journal retained / emulator baseline restored\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","HOME recovery failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("memory".equals(scenario)){
            try{new HomeMemoryChecks(this).run();result.putString("stream","PASS: default/fixed/rotating steady-state samples; original wallpaper preferences restored; full five-minute rotation not exercised\n");finish(-1,result);}
            catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("optimized".equals(scenario)){
            try{new OptimizedSmoke(this).run();result.putString("stream","PASS: optimized HOME / native About / Dock / JNI providers / actual local ADB / setup entry\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Optimized smoke failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("commands-compatibility".equals(scenario)){
            try{new CommandCompatibilityChecks(this).run();result.putString("stream","PASS: production ADB capability detection / fixture disable-enable / journal / exact state restore / connected HOME inspection\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVCompatChecks","Commands failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("compatibility".equals(scenario)){
            try{new CompatibilityChecks(this).run();result.putString("stream","PASS: offline package metadata / system HOME entry / vector holes / HOME, setup and management Back on API "+android.os.Build.VERSION.SDK_INT+"\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVCompatChecks","Compatibility failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("about".equals(scenario)){
            try{new AboutChecks(this).run();result.putString("stream","PASS: About installed metadata / remote entry / offline QR and licenses / external-link fallback / HOME focus restoration\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVAboutChecks","About failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("setup-focus".equals(scenario)){
            try{new SetupFocusChecks(this).run();result.putString("stream","PASS: continuous remote paths / visible outer focus rings / readonly original options and cards / empty and mixed grids / modal focus and return / actual HOME unchanged\n");finish(-1,result);}
            catch(Throwable e){android.util.Log.e("QuietTVChecks","Setup focus failed",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}return;
        }
        if("setup-prototype".equals(scenario)){
            try{new SetupPrototypeChecks(this).run();result.putString("stream","PASS: real native setup entry / anonymous fixed preview independent of Dock and wallpaper / all enabled controls D-pad reachable / remote numeric input and focus restore / selection before authorization / cancellation / actual TCP continuation; emulator HOME and package baseline restored\n");finish(-1,result);}
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
        if("connect".equals(scenario)){
            try{new SetupStore(getTargetContext()).shouldOffer(getTargetContext());startActivitySync(new android.content.Intent(getTargetContext(),HomeActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));}
            catch(Exception e){result.putString("stream","FAIL: foreground setup "+e+"\n");finish(0,result);return;}
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
