package home.quiet.tv;

import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import java.io.Closeable;
import java.util.*;

/** Service discovery supplies local ports only. It never pairs, connects or accepts remote hosts. */
final class LocalAdbDiscovery implements Closeable {
    interface Found {void endpoint(boolean pairing,int port);}
    private final NsdManager manager;
    private final ArrayList<NsdManager.DiscoveryListener> listeners=new ArrayList<>();
    private volatile boolean closed;
    private final Handler main=new Handler(Looper.getMainLooper());
    LocalAdbDiscovery(Context context,Found found){
        manager=(NsdManager)context.getSystemService(Context.NSD_SERVICE);
        if(manager!=null)for(boolean pairing:new boolean[]{true,false}){
            String type=pairing?"_adb-tls-pairing._tcp.":"_adb-tls-connect._tcp.";
            NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
                public void onDiscoveryStarted(String serviceType){}
                public void onDiscoveryStopped(String serviceType){}
                public void onStartDiscoveryFailed(String serviceType,int code){}
                public void onStopDiscoveryFailed(String serviceType,int code){}
                public void onServiceLost(NsdServiceInfo service){}
                public void onServiceFound(NsdServiceInfo service){if(closed)return;try{manager.resolveService(service,new NsdManager.ResolveListener(){
                    public void onResolveFailed(NsdServiceInfo value,int code){}
                    public void onServiceResolved(NsdServiceInfo value){if(!closed && AdbController.localAddress(value.getHost()) && value.getPort()>0 && value.getPort()<65536)main.post(()->{if(!closed)found.endpoint(pairing,value.getPort());});}
                });}catch(RuntimeException ignored){}}
            };
            try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);listeners.add(listener);}catch(RuntimeException ignored){}
        }
        main.postDelayed(this::close,10000);
    }
    @Override public void close(){closed=true;main.removeCallbacksAndMessages(null);if(manager!=null)for(NsdManager.DiscoveryListener listener:listeners)try{manager.stopServiceDiscovery(listener);}catch(RuntimeException ignored){}listeners.clear();}
}
