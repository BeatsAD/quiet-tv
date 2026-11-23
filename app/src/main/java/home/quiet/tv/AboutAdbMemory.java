package home.quiet.tv;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Joins system PSS and numeric process ownership without including other Android users. */
final class AboutAdbMemory {
    private static final int UID_RANGE=100000;
    private static final Pattern PID_UID=Pattern.compile("\\s*(\\d+)\\s+(\\d+)\\s*");
    private static final Pattern PSS=Pattern.compile("\\s*([\\d,]+)\\s*(?:K|kB):\\s+(\\S+)\\s+\\(pid\\s+(\\d+)\\b.*");

    static Map<String,Long> parse(String output,String ownPackage,int ownPid,int ownUid,
                                  IntFunction<String[]> packagesForUid) throws SocketTimeoutException{
        // dumpsys can return exit code 0 even after the meminfo service times out.
        if(output.contains("SERVICE 'meminfo' DUMP TIMEOUT"))throw new SocketTimeoutException("ADB 应用内存查询超时");
        Map<Integer,Integer> owners=new HashMap<>();
        Map<Integer,String[]> packages=new HashMap<>();
        Map<String,Long> totals=new HashMap<>();
        boolean inPss=false,ownSample=false;
        for(String line:output.split("\\r?\\n")){
            String heading=line.trim();
            if(!inPss){
                if(heading.equals("Total PSS by process:"))inPss=true;
                else{
                    Matcher pair=PID_UID.matcher(line);
                    if(pair.matches())owners.put(Integer.parseInt(pair.group(1)),Integer.parseInt(pair.group(2)));
                }
                continue;
            }
            if(heading.equals("Total PSS by OOM adjustment:")){
                if(ownSample)return totals;
                break;
            }
            Matcher process=PSS.matcher(line);
            if(!process.matches())continue;
            long kb=Long.parseLong(process.group(1).replace(",",""));
            int pid=Integer.parseInt(process.group(3));
            Integer uid=owners.remove(pid);
            if(kb<=0||uid==null||uid/UID_RANGE!=ownUid/UID_RANGE)continue;
            String name;
            if(pid==ownPid&&uid==ownUid){
                name=ownPackage;
                ownSample=true;
            }else{
                if(!packages.containsKey(uid))packages.put(uid,packagesForUid.apply(uid));
                name=ownerPackage(process.group(2),uid,packages.get(uid));
            }
            if(name!=null){
                Long prior=totals.get(name);
                totals.put(name,(prior==null?0:prior)+kb*1024L);
            }
        }
        // A partial or unsupported dump cannot establish a fresh app list.
        totals.clear();
        return totals;
    }

    private static String ownerPackage(String process,int uid,String[] packages){
        if(packages==null)return null;
        String base=process.split(":",2)[0];
        for(String candidate:packages)if(candidate.equals(base))return candidate;
        // A normal app with its own UID can name processes independently of the package.
        return packages.length==1&&uid%UID_RANGE>=10000?packages[0]:null;
    }
}
