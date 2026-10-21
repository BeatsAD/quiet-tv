package home.quiet.tv;

import android.os.Build;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Modern pairing is absent from the API 19 build, including its native dependencies. */
final class WirelessPairing {
    private volatile PairingTransport transport;
    static boolean available(){
        if(Build.VERSION.SDK_INT<33)return false;
        try{Class.forName("io.github.muntashirakon.crypto.spake2.Spake2Context");return org.conscrypt.Conscrypt.isAvailable();}
        catch(LinkageError|ReflectiveOperationException e){return false;}
    }
    void start(int port,String code,KeyStore keys,AtomicInteger generation,int attempt,AdbController.Status status) throws Exception {
        byte[] password=code.getBytes(StandardCharsets.UTF_8);
        io.github.muntashirakon.adb.PairingConnectionCtx protocol=null;
        try{
            String target=null;ArrayList<InetAddress> addresses=new ArrayList<>();addresses.add(InetAddress.getByName("127.0.0.1"));
            for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))if(nic.isUp())for(InetAddress value:Collections.list(nic.getInetAddresses()))if(value instanceof Inet4Address && !value.isLoopbackAddress())addresses.add(value);
            for(InetAddress address:addresses){if(generation.get()!=attempt)throw new IOException("配对已取消");try(Socket probe=new Socket()){probe.connect(new InetSocketAddress(address,port),1500);target=address.getHostAddress();break;}catch(IOException ignored){}}
            if(target==null)throw new IOException("本机配对端口不可达");
            PairingTransport active=new PairingTransport(target,port,30000);transport=active;
            if(generation.get()!=attempt)throw new IOException("配对已取消");
            status.changed("正在配对  请保持系统配对页面有效");
            protocol=new io.github.muntashirakon.adb.PairingConnectionCtx("127.0.0.1",active.port(),password,(PrivateKey)keys.getKey("adb",new char[0]),keys.getCertificate("adb"),"QuietTV");
            protocol.start();if(generation.get()!=attempt)throw new IOException("配对已取消");status.changed("已配对  尚需连接并核对本机身份");
        }catch(Exception|LinkageError e){throw new IOException("配对未完成，请重新打开系统配对页面并核对端口和配对码；取消或超时后不会自动重试");}
        finally{Arrays.fill(password,(byte)0);close();if(protocol!=null)try{protocol.close();}catch(RuntimeException ignored){}}
    }
    void close(){PairingTransport active=transport;transport=null;if(active!=null)active.close();}
}
