package home.quiet.tv;

import java.util.regex.*;

/** Only a uniquely resumed activity is foreground evidence; history is never a trigger. */
final class AdbHomeGuardPolicy {
    private String attempted="";
    static String foreground(String dump,int user){
        return component(dump,user,"(?m)^\\s*mResumedActivity:\\s*ActivityRecord\\{[^\\r\\n}]*\\bu(\\d+)\\s+([\\w.]+)/([\\w.$]+)");
    }
    static String focused(String dump,int user){
        return component(dump,user,"(?m)^\\s*mCurrentFocus\\s*=\\s*Window\\{[^\\r\\n}]*\\bu(\\d+)\\s+([\\w.]+)/([\\w.$]+)");
    }
    private static String component(String dump,int user,String pattern){
        Matcher match=Pattern.compile(pattern).matcher(dump);
        String result="";int count=0;
        while(match.find()){
            if(Integer.parseInt(match.group(1))!=user)return "";
            String pkg=match.group(2),cls=match.group(3);result=pkg+"/"+(cls.startsWith(".")?pkg+cls:cls);count++;
        }
        return count==1?result:"";
    }
    boolean take(String original,String foreground){
        if(foreground.isEmpty())return false;
        if(!original.equals(foreground)){attempted="";return false;}
        if(original.equals(attempted))return false;
        attempted=original;return true;
    }
}
