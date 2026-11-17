package home.quiet.tv;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.io.*;
import java.net.InetAddress;
import javax.jmdns.*;

/** Own the hostname A record as well as the optional HTTP service advertisement. */
final class PhoneSettingsDiscovery implements Closeable {
    private JmDNS dns;
    private WifiManager.MulticastLock multicast;
    PhoneSettingsDiscovery(Context context,String address,String hostname,int port)throws IOException {
        try{
            WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(wifi!=null){multicast=wifi.createMulticastLock("quiettv-phone-settings");multicast.setReferenceCounted(false);multicast.acquire();}
            dns=JmDNS.create(InetAddress.getByName(address),hostname.substring(0,hostname.length()-6));
            dns.registerService(ServiceInfo.create("_http._tcp.local.",hostname.substring(0,hostname.length()-6),port,"path=/"));
            if(!(hostname+".").equalsIgnoreCase(dns.getHostName()))throw new IOException("固定网址名称冲突");
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    @Override public void close(){
        try{if(dns!=null){dns.close();dns=null;}}catch(IOException ignored){}finally{if(multicast!=null&&multicast.isHeld())multicast.release();multicast=null;}
    }
}
