package home.quiet.tv;

import java.util.*;
import java.util.regex.*;

/** Extract structural evidence only. Never return raw dumps, window titles, extras or view text. */
final class SettingsPageSnapshot {
    private static final String COMPONENT="([A-Za-z_][\\w.]*)/([A-Za-z_.$][\\w.$]*)";
    private static final Pattern RESUMED=Pattern.compile("(?:topResumedActivity|mResumedActivity)\\s*[:=]\\s*ActivityRecord\\{[^\\r\\n}]*?\\bu(\\d+)\\s+"+COMPONENT);
    private static final Pattern WINDOW=Pattern.compile("mCurrentFocus=Window\\{[^\\r\\n}]*?(?:\\bu(\\d+)\\s+)?"+COMPONENT+"[\\s}]");
    private static final Pattern ACTIVITY=Pattern.compile("^\\s*ACTIVITY\\s+"+COMPONENT+"(?:\\s|$)");
    private static final Pattern HISTORY=Pattern.compile("Hist #\\d+: ActivityRecord\\{[^\\r\\n}]*?\\bu(\\d+)\\s+"+COMPONENT);
    private static final Pattern FRAGMENT=Pattern.compile("^\\s*#\\d+: ([A-Za-z_.$][\\w.$]*)\\{[0-9a-fA-F]+(?:[ #}])");
    private static final Pattern FRAGMENT_STATE=Pattern.compile("\\b(mState=-?\\d{1,2}|mHidden=(?:true|false)|mAdded=(?:true|false)|mDetached=(?:true|false)|mUserVisibleHint=(?:true|false))\\b");
    private static final Pattern ACTION=Pattern.compile("(?:^|[ {])act=([A-Za-z_][\\w.]*)");
    final String resumed,focused,action;
    final List<String> fragments;
    final boolean intentData,intentExtras;
    private SettingsPageSnapshot(String resumed,String focused,String action,List<String> fragments,boolean data,boolean extras){
        this.resumed=resumed;this.focused=focused;this.action=action;this.fragments=fragments;intentData=data;intentExtras=extras;
    }
    static String component(String pkg,String name){return pkg+"/"+(name.startsWith(".")?pkg+name:name);}
    static SettingsPageSnapshot parse(String activities,String windows,String top,int user){
        String resumed="",focused="",action="";boolean data=false,extras=false;
        Matcher match=RESUMED.matcher(activities);
        Set<String> found=new LinkedHashSet<>();
        while(match.find())if(Integer.toString(user).equals(match.group(1)))found.add(component(match.group(2),match.group(3)));
        // Multiple resumed activities on multi-window firmware do not identify one foreground page.
        if(found.size()==1)resumed=found.iterator().next();
        boolean recordSelected=false;
        for(String line:activities.split("\\r?\\n")){
            match=HISTORY.matcher(line);
            if(match.find()){recordSelected=Integer.toString(user).equals(match.group(1))&&resumed.equals(component(match.group(2),match.group(3)));continue;}
            String trimmed=line.trim();
            if(recordSelected && (trimmed.startsWith("Intent {")||trimmed.startsWith("intent={")||trimmed.startsWith("mIntent="))){
                match=ACTION.matcher(trimmed);if(match.find())action=match.group(1);
                data=trimmed.contains("dat=");extras=trimmed.contains("(has extras)")||trimmed.contains("extras=");
                recordSelected=false;
            }
        }
        match=WINDOW.matcher(windows);
        while(match.find())if(match.group(1)==null || Integer.toString(user).equals(match.group(1)))focused=component(match.group(2),match.group(3));
        LinkedHashMap<String,Map<String,String>> fragments=new LinkedHashMap<>();String fragment="";boolean selected=false,inFragments=false;
        for(String line:top.split("\\r?\\n")){
            match=ACTIVITY.matcher(line);
            if(match.find()){selected=!resumed.isEmpty()&&resumed.equals(component(match.group(1),match.group(2)));inFragments=false;fragment="";continue;}
            if(!selected)continue;
            String trimmed=line.trim();
            if(trimmed.startsWith("mIntent=")){
                match=ACTION.matcher(trimmed);if(match.find())action=match.group(1);
                data=trimmed.contains("dat=");extras=trimmed.contains("(has extras)")||trimmed.contains("extras=");
            }
            if(trimmed.startsWith("Active Fragments")||trimmed.startsWith("Added Fragments")){inFragments=true;fragment="";continue;}
            if(trimmed.startsWith("Back Stack")||trimmed.startsWith("FragmentManager misc state")||trimmed.startsWith("View Hierarchy"))inFragments=false;
            if(inFragments){
                match=FRAGMENT.matcher(line);
                if(match.find()){
                    fragment=match.group(1);
                    if(!fragments.containsKey(fragment) && fragments.size()<8)fragments.put(fragment,new LinkedHashMap<>());
                }else if(fragments.containsKey(fragment)){
                    match=FRAGMENT_STATE.matcher(line);
                    while(match.find()){String value=match.group(1);fragments.get(fragment).put(value.substring(0,value.indexOf('=')),value);}
                }
            }
        }
        List<String> candidates=new ArrayList<>();
        for(Map.Entry<String,Map<String,String>> entry:fragments.entrySet())candidates.add(entry.getKey()+(entry.getValue().isEmpty()?"":entry.getValue().values().toString()));
        return new SettingsPageSnapshot(resumed,focused,action,candidates,data,extras);
    }
    String page(){return "resumed="+unknown(resumed)+" focused="+unknown(focused)+" action="+unknown(action)+" dataPresent="+intentData+" extrasPresent="+intentExtras;}
    String fragments(){return "candidates="+fragments+" scope=active_or_added_not_verified_visible limit=8";}
    String key(){return page()+fragments();}
    private static String unknown(String value){return value.isEmpty()?"unknown":value;}
}
