package home.quiet.tv;

import android.content.*;
import android.os.Build;
import java.util.*;
import java.util.concurrent.*;

/** Known native-panel protocols only. No component, intent or shell text comes from maintenance input. */
final class InputAdb {
    static final class UnsupportedQuery extends java.io.IOException {UnsupportedQuery(String message){super(message);}}
    static boolean connected(){ManagementAdb session=ManagementAdb.existing();return session!=null&&session.adb.connected();}
    static String query(Context context,String command)throws Exception{return run(context,command,false,null);}
    private static String run(Context context,String command,boolean mutation,InputPairingState receipt)throws Exception{
        ManagementAdb session=ManagementAdb.existing();
        if(session==null||!session.adb.connected())throw new IllegalStateException("本机调试连接不可用");
        Future<String> future=session.worker.submit(()->{
            session.adb.validateUser();
            if(Thread.currentThread().isInterrupted()||receipt!=null&&!"DISPATCHING".equals(receipt.state))throw new CancellationException();
            if(session.adb.user!=android.os.Process.myUid()/100000)throw new IllegalStateException("当前用户已变化");
            return mutation?session.adb.shell(command):session.adb.inputDiagnosticShell(command);
        });
        try{return future.get(22,TimeUnit.SECONDS);}catch(InterruptedException|TimeoutException failure){future.cancel(true);throw failure;}
        catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new java.io.IOException("系统查询失败",cause);}
    }
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    static String panelCommand(TvInputPanels.Panel panel,int user,int sdk,String help){
        if(panel==null||panel.component==null)throw new IllegalArgumentException("入口不可用");
        String type=panel.profile.type;
        String verb="activity".equals(type)?"start":"receiver".equals(type)?"broadcast":panel.profile.foregroundService&&sdk>=26?"start-foreground-service":"startservice";
        if(!java.util.regex.Pattern.compile("(?m)(?:^|\\s)"+java.util.regex.Pattern.quote(verb)+"(?:\\s|$)").matcher(help).find())
            throw new IllegalStateException("系统未确认此信号源调用命令");
        if(user!=0&&!help.contains("--user"))throw new IllegalStateException("系统无法核对目标用户");
        StringBuilder command=new StringBuilder("am ").append(verb);
        if(help.contains("--user"))command.append(" --user ").append(user);
        command.append(" -n ").append(quote(panel.component.flattenToString()));
        if(!panel.profile.action.isEmpty())command.append(" -a ").append(quote(panel.profile.action));
        for(String key:new TreeSet<>(panel.profile.extras.keySet())){
            Object value=panel.profile.extras.get(key);
            command.append(value instanceof Boolean?" --ez ":value instanceof Integer?" --ei ":" --es ")
                .append(quote(key)).append(' ').append(quote(String.valueOf(value)));
        }
        return command.toString();
    }
    static boolean open(Context context,TvInputPanels.Panel panel,InputPairingState receipt)throws Exception{
        if(panel.state!=TvInputPanels.PanelState.READY&&panel.state!=TvInputPanels.PanelState.RESTRICTED)return false;
        android.content.pm.PackageManager pm=context.getPackageManager();
        android.content.pm.ComponentInfo info="service".equals(panel.profile.type)?pm.getServiceInfo(panel.component,0):"receiver".equals(panel.profile.type)?pm.getReceiverInfo(panel.component,0):pm.getActivityInfo(panel.component,0);
        if(!InputComponents.enabledSystem(context,info))return false;
        String help=help(context);
        String output=run(context,panelCommand(panel,android.os.Process.myUid()/100000,Build.VERSION.SDK_INT,help),true,receipt);
        if(output.contains("Permission Denial")||output.contains("SecurityException"))throw new SecurityException("系统拒绝信号源调用");
        if(output.contains("Error:")||output.contains("Exception:"))throw new IllegalStateException("信号源调用未完成");
        return true;
    }
    private static String help(Context context)throws Exception{
        try{return query(context,"am help");}catch(AdbController.CommandFailure failure){if(failure.output.contains("start")&&failure.output.contains("broadcast"))return failure.output;throw failure;}
    }
    static String intentCommand(Intent intent,int user,String help){
        if(intent.getComponent()==null||!help.contains("start")||user!=0&&!help.contains("--user"))throw new IllegalArgumentException("系统未确认此调用方式");
        StringBuilder command=new StringBuilder("am start");if(help.contains("--user"))command.append(" --user ").append(user);
        command.append(" -n ").append(quote(intent.getComponent().flattenToString()));
        if(intent.getAction()!=null)command.append(" -a ").append(quote(intent.getAction()));
        if(intent.getData()!=null)command.append(" -d ").append(quote(intent.getData().toString()));
        if(intent.getType()!=null)command.append(" -t ").append(quote(intent.getType()));
        if(intent.getFlags()!=0)command.append(" -f ").append(intent.getFlags());
        android.os.Bundle extras=intent.getExtras();if(extras!=null)for(String key:new TreeSet<>(extras.keySet())){
            Object value=extras.get(key);if(!(value instanceof String)&&!(value instanceof Integer)&&!(value instanceof Boolean))throw new IllegalArgumentException("此协议参数暂不支持 ADB");
            command.append(value instanceof Boolean?" --ez ":value instanceof Integer?" --ei ":" --es ").append(quote(key)).append(' ').append(quote(String.valueOf(value)));
        }
        return command.toString();
    }
    static boolean openIntent(Context context,Intent intent,InputPairingState receipt)throws Exception{
        android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(intent.getComponent(),0);
        if(!InputComponents.enabledSystem(context,info))return false;
        String output=run(context,intentCommand(intent,android.os.Process.myUid()/100000,help(context)),true,receipt);
        if(output.contains("Permission Denial")||output.contains("SecurityException"))throw new SecurityException("系统拒绝信号源调用");
        if(output.contains("Error:")||output.contains("Exception:"))throw new IllegalStateException("信号源调用未完成");
        return true;
    }
    static String quality(Exception failure){
        if(failure instanceof SecurityException)return "RESTRICTED";
        if(failure instanceof UnsupportedQuery)return "UNSUPPORTED";
        if(failure instanceof TimeoutException||failure instanceof java.net.SocketTimeoutException)return "TIMEOUT";
        if(failure instanceof CancellationException||failure instanceof InterruptedException)return "CANCELLED";
        if(failure instanceof AdbController.CommandFailure){String body=((AdbController.CommandFailure)failure).output;
            return body.contains("Permission")||body.contains("denied")?"RESTRICTED":"UNSUPPORTED";}
        return connected()?"FAILED":"ADB_UNAVAILABLE";
    }
}
