package home.quiet.tv;

import java.net.*;
import java.util.*;

/** Only the public Quiet TV cinema repository is sent through GitHub mirrors. */
final class MovieWallpaperSources {
    static final String PAGES="https://beatsad.github.io/quiet-tv-cinema-feed/";
    static final String RAW="https://raw.githubusercontent.com/BeatsAD/quiet-tv-cinema-feed/feed-data/";
    static List<String> addresses(String address){
        String path=address.startsWith(PAGES)?address.substring(PAGES.length()):address.startsWith(RAW)?address.substring(RAW.length()):"";
        if(!path.equals("latest.signed.json")&&!path.matches("calendar/[0-9]{4}-[0-9]{2}-[0-9]{2}\\.signed\\.json")&&!path.matches("images/[1-9][0-9]*/[a-f0-9]{64}\\.jpg"))return Collections.singletonList(address);
        List<String> result=new ArrayList<>();
        for(String prefix:UpdateTransfer.SOURCES)if(!prefix.isEmpty())result.add(prefix+RAW+path);
        result.add(PAGES+path);result.add(RAW+path);return result;
    }
    static String dated(String base,String day)throws java.io.IOException{return new URL(new URL(base),"calendar/"+MovieWallpaperCache.date(day,0)+".signed.json").toString();}
    static boolean mirrored(String address){
        try{
            String host=new URL(address).getHost();
            for(String prefix:UpdateTransfer.SOURCES)if(!prefix.isEmpty()&&new URL(prefix).getHost().equalsIgnoreCase(host))return true;
        }catch(MalformedURLException ignored){}
        return false;
    }
}
