package home.quiet.tv;

import java.net.*;
import java.util.*;

/** Public icon resources follow the application's agreed mirror priority. */
final class IconLibrarySources {
    static final String PAGES="https://beatsad.github.io/quiet-tv-icons/";
    static final String RAW="https://raw.githubusercontent.com/BeatsAD/quiet-tv-icons/main/";
    static boolean path(String path){return "latest.signed.json".equals(path)||path!=null&&path.matches("images/[a-z0-9-]+/v[1-9][0-9]*/(?:icons|thumbnails)/[a-z0-9-]+\\.png");}
    static List<String> addresses(String path){
        if(!path(path))throw new IllegalArgumentException("Invalid public icon path");
        ArrayList<String> result=new ArrayList<>();
        for(String prefix:UpdateTransfer.SOURCES)if(!prefix.isEmpty())result.add(prefix+RAW+path);
        result.add(PAGES+path);result.add(RAW+path);return result;
    }
    static boolean mirrored(URL url){
        for(String prefix:UpdateTransfer.SOURCES)if(!prefix.isEmpty())try{if(new URL(prefix).getHost().equalsIgnoreCase(url.getHost()))return true;}catch(MalformedURLException ignored){}
        return false;
    }
}
