package home.quiet.tv;

import java.util.*;

/** Read-only vendor routes. Installed system activities are verified by HomeGuardState. */
final class CoocaaHomeRoutes {
    static final String STOCK="com.tianci.movieplatform/com.coocaa.homepage.vast.HomePageActivity";
    static final String[] KEYS={"persist.service.model.pkg","persist.service.model.cls",
            "persist.service.homepage.pkg","persist.service.homepage.cls"};
    interface Properties {String get(String key);}
    // Null means unreadable, distinct from an empty property. Never writes a vendor route.
    static String read(String key){
        try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key);}
        catch(ReflectiveOperationException|RuntimeException unavailable){return null;}
    }
    static List<String> targets(Properties properties){
        Set<String> targets=new LinkedHashSet<>();
        for(int i=0;i<KEYS.length;i+=2){String target=component(properties.get(KEYS[i]),properties.get(KEYS[i+1]));if(!target.isEmpty())targets.add(target);}
        return new ArrayList<>(targets);
    }
    static String component(String pkg,String cls){
        if(pkg==null || cls==null || !pkg.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
                || !cls.matches("(?:\\.[A-Za-z_$][A-Za-z0-9_$]*|[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+)"))return "";
        // Coocaa's activity class can belong to a different Java namespace than its package.
        return pkg+"/"+(cls.startsWith(".")?pkg+cls:cls);
    }
    static String unique(List<String> targets,Collection<String> available){
        String selected="";
        for(String target:targets)if(available.contains(target)){
            if(!selected.isEmpty() && !selected.equals(target))return "";
            selected=target;
        }
        return selected;
    }
}
