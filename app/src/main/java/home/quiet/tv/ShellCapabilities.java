package home.quiet.tv;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only help discovery. A command is usable only with an explicit user option. */
final class ShellCapabilities {
    static final ShellCapabilities NONE=new ShellCapabilities("","");
    final boolean enable,disable,uninstall,defaultState;
    final String resolve,home,restore,enableCommand,disableCommand,uninstallCommand,defaultStateCommand;
    ShellCapabilities(String pm,String cmd){
        enableCommand=command(pm,cmd,"enable");disableCommand=command(pm,cmd,"disable-user");
        uninstallCommand=command(pm,cmd,"uninstall");defaultStateCommand=command(pm,cmd,"default-state");
        enable=!enableCommand.isEmpty();disable=!disableCommand.isEmpty();
        uninstall=!uninstallCommand.isEmpty();defaultState=!defaultStateCommand.isEmpty();
        resolve=command(pm,cmd,"resolve-activity");home=command(pm,cmd,"set-home-activity");
        restore=command(pm,cmd,"install-existing");
    }
    boolean supports(AppOperations.Action action){
        switch(action){case ENABLE:return enable;case DISABLE:return disable && enable;case UNINSTALL:return uninstall;case RESTORE:return !restore.isEmpty();default:return false;}
    }
    private static String command(String pm,String cmd,String name){
        return scoped(cmd,name)?"cmd package "+name:scoped(pm,name)?"pm "+name:"";
    }
    private static boolean scoped(String help,String name){
        Matcher match=Pattern.compile("(?m)^([ \\t]*)(?:pm[ \\t]+)?"+Pattern.quote(name)+"(?=[ \\t]|$)([^\\r\\n]*)").matcher(help);
        if(!match.find())return false;
        StringBuilder signature=new StringBuilder(match.group(2));int indent=match.group(1).length();
        for(String line:help.substring(match.end()).split("\\r?\\n")){
            if(line.trim().isEmpty())continue;
            int width=0;while(width<line.length() && Character.isWhitespace(line.charAt(width)))width++;
            if(width<=indent)break;
            signature.append(' ').append(line.trim());
        }
        return signature.toString().contains("--user");
    }
}
