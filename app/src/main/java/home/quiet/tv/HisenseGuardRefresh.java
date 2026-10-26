package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Aerial #285: explicit shell repair only; never hide an initially visible vendor package. */
final class HisenseGuardRefresh {
    static final String PACKAGE="com.hisense.vidaaassistant";
    static final String FINGERPRINT="Hisense/VIDAA_TV/MSD6A828:5.1.1/LVY48C/01.02.40.01:user/release-keys";
    interface Check { void run() throws Exception; }
    interface Journal { void save(String phase) throws Exception; }
    static boolean matches(String brand,String manufacturer,String model,String device,String fingerprint,int sdk,int user,boolean legacy){
        return !legacy && sdk==22 && user==0 && "Hisense".equalsIgnoreCase(brand) && "Hisense".equalsIgnoreCase(manufacturer)
                && "VIDAA_TV".equals(model) && "MSD6A828".equals(device) && FINGERPRINT.equals(fingerprint);
    }
    static boolean supported(){return matches(Build.BRAND,Build.MANUFACTURER,Build.MODEL,Build.DEVICE,Build.FINGERPRINT,
            Build.VERSION.SDK_INT,android.os.Process.myUid()/100000,BuildConfig.LEGACY_19);}
    static boolean needsShell(Context c){
        if(!supported())return false;
        if(!new HomeGuardState(c,false).serviceEnabled())return true;
        try{return pending(new SetupStore(c).get().optJSONObject("hisenseGuardRefresh"));}
        catch(Exception unreadable){return true;}
    }
    private static boolean pending(org.json.JSONObject previous){
        return previous!=null && "unhide_pending".equals(previous.optString("phase")) && PACKAGE.equals(previous.optString("package"))
                && previous.optBoolean("hiddenBefore") && previous.optInt("user",-1)==0;
    }
    static boolean hidden(String dump) throws IOException {
        Matcher rows=Pattern.compile("(?m)^\\s*User 0:([^\\r\\n]+)").matcher(dump);
        if(!rows.find())throw new IOException("上游刷新包的当前用户状态无法确认，未修改厂商包");
        String row=rows.group(1);
        if(rows.find() || !Pattern.compile("\\binstalled=true\\b").matcher(row).find()
                || !Pattern.compile("\\benabled=[01]\\b").matcher(row).find())throw new IOException("上游刷新包未安装或已禁用，未修改厂商包");
        Matcher state=Pattern.compile("\\bhidden=(true|false)\\b").matcher(row);
        if(!state.find())throw new IOException("上游刷新包的隐藏状态无法确认，未修改厂商包");
        return Boolean.parseBoolean(state.group(1));
    }
    private static String get(ShellCapabilities.Query shell,String key) throws Exception {return shell.run("settings --user 0 get secure "+key).trim();}
    private static boolean accepted(ShellCapabilities.Query shell,String own) throws Exception {
        String current=get(shell,"enabled_accessibility_services");
        return "1".equals(get(shell,"accessibility_enabled")) && HomeGuardPolicy.services(current,own,true).equals(current);
    }
    private static void write(ShellCapabilities.Query shell,Check check,String own) throws Exception {
        check.run();String current=get(shell,"enabled_accessibility_services");
        String desired=HomeGuardPolicy.services(current,own,true);
        shell.run("settings --user 0 put secure enabled_accessibility_services "+quote(desired));
        check.run();shell.run("settings --user 0 put secure accessibility_enabled 1");
    }
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    static void repair(ShellCapabilities.Query shell,Check check,Check restoreCheck,Journal journal,String own,boolean pending) throws Exception {
        // A persisted pending record can only originate from the verified hidden baseline below.
        String help="",hide="";
        if(pending){
            restoreCheck.run();
            if(!hidden(shell.run("dumpsys package "+PACKAGE))){
                help=ShellCapabilities.permissionHelp("pm",shell);
                hide="pm hide"+ShellCapabilities.userOption(help,"hide",22,0)+" "+PACKAGE;
                shell.run(hide);
                if(!hidden(shell.run("dumpsys package "+PACKAGE)))throw new IOException("上次刷新包尚未恢复隐藏，请保留恢复记录并重新连接");
            }
            journal.save("restored");
        }
        check.run();
        if(accepted(shell,own))return;
        write(shell,check,own);
        if(accepted(shell,own))return;
        if(!"0".equals(get(shell,"accessibility_enabled")))throw new IOException("无障碍配置读回不一致，未修改厂商包");
        check.run();
        if(!hidden(shell.run("dumpsys package "+PACKAGE)))throw new IOException("固件未接受无障碍设置；上游刷新包原本未隐藏，未修改厂商包");
        if(help.isEmpty())help=ShellCapabilities.permissionHelp("pm",shell);
        hide="pm hide"+ShellCapabilities.userOption(help,"hide",22,0)+" "+PACKAGE;
        String unhide="pm unhide"+ShellCapabilities.userOption(help,"unhide",22,0)+" "+PACKAGE;
        check.run();journal.save("unhide_pending");
        Exception failure=null;
        try{
            shell.run(unhide);
            if(hidden(shell.run("dumpsys package "+PACKAGE)))throw new IOException("刷新包未解除隐藏");
            write(shell,check,own);
            if(!accepted(shell,own))throw new IOException("包状态刷新后固件仍未接受无障碍设置");
        }catch(Exception problem){failure=problem;}
        try{
            // Cancellation stops setup writes, but restoration still uses the verified original user.
            restoreCheck.run();shell.run(hide);
            if(!hidden(shell.run("dumpsys package "+PACKAGE)))throw new IOException("刷新包恢复隐藏未读回");
            journal.save("restored");
        }catch(Exception restore){
            IOException incomplete=new IOException("刷新包恢复隐藏未确认，请保留恢复记录并重新连接",restore);
            if(failure!=null)incomplete.addSuppressed(failure);
            throw incomplete;
        }
        if(failure!=null)throw failure;
        if(!accepted(shell,own))throw new IOException("恢复包隐藏状态后无障碍设置未保持，守护未确认");
    }
    static void configure(Context c,AdbController adb,SetupOperations.Ticket task) throws Exception {
        if(!supported())return;
        SetupStore store=new SetupStore(c);
        org.json.JSONObject previous=store.get().optJSONObject("hisenseGuardRefresh");
        boolean pending=pending(previous);
        Check check=()->{task.check();adb.validateUser();if(!new HomeGuardState(c,false).recoveryReady())throw new IOException("守护已暂停或配置已改变");};
        repair(adb::shell,check,adb::validateUser,phase->{
            store.put("hisenseGuardRefresh",new org.json.JSONObject().put("package",PACKAGE).put("user",0).put("hiddenBefore",true)
                    .put("phase",phase).put("time",System.currentTimeMillis()));
            DiagnosticLog.info("home_guard.hisense_refresh","phase="+phase+" serviceBound="+HomeGuardService.connected()+" scenesVerified=false");
        },HomeGuardRecovery.guard(c),pending);
        DiagnosticLog.info("home_guard.hisense_refresh","result=settings_readback serviceBound="+HomeGuardService.connected()+" scenesVerified=false");
    }
}
