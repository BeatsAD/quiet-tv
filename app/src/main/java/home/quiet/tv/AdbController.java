package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import android.util.AtomicFile;
import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.AdbStream;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** A foreground-only, single-worker ADB client restricted to this device's addresses. */
final class AdbController implements Closeable {
    interface Status { void changed(String value); }
    private final Context context;
    private final Status status;
    private volatile AdbConnection connection;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    int user = -1;
    String host = "";
    AdbController(Context context, Status status) { this.context=context.getApplicationContext(); this.status=status; }
    boolean connected() { AdbConnection c=connection;return c!=null && c.isConnectionEstablished() && user>=0; }
    void validateUser() throws Exception {
        if(!connected())throw new IOException("需要连接 ADB");
        if(!shell("am get-current-user").trim().equals(String.valueOf(user)) || user!=android.os.Process.myUid()/100000){disconnect();throw new IOException("当前用户已改变，请重新连接");}
    }

    void connect(int port) throws Exception {
        if(port<1 || port>65535)throw new IOException("端口必须在 1～65535 之间");
        disconnect();
        List<String> hosts=new ArrayList<>();hosts.add("127.0.0.1");
        for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))
            if(nic.isUp() && !nic.isLoopback())for(InetAddress address:Collections.list(nic.getInetAddresses()))
                if(address instanceof Inet4Address)hosts.add(address.getHostAddress());
        Exception failure=null;
        for(String candidate:hosts){
            if(closed)throw new IOException("连接已取消");
            status.changed("连接中 · "+candidate+":"+port);
            // Bound the TCP reachability check; the library's constructor uses a blocking Socket.
            try(Socket probe=new Socket()){probe.connect(new InetSocketAddress(candidate,port),2000);}
            catch(IOException e){failure=e;continue;}
            KeyStore keys=loadKeys();
            AdbConnection c=new AdbConnection.Builder(candidate,port).setApi(Build.VERSION.SDK_INT)
                    .setDeviceName("QuietTV").setPrivateKey((PrivateKey)keys.getKey("adb",new char[0]))
                    .setCertificate(keys.getCertificate("adb")).build();
            connection=c;
            if(closed){disconnect();throw new IOException("连接已取消");}
            status.changed("等待授权 · 请在电视弹窗中允许 QuietTV 的调试连接");
            try{
                if(!c.connect(60,TimeUnit.SECONDS,false))throw new IOException("授权超时，请确认电视调试已开启并允许连接");
                // The per-boot id proves this shell belongs to the running TV, even with multiple NICs.
                String localBoot=new String(java.nio.file.Files.readAllBytes(new File("/proc/sys/kernel/random/boot_id").toPath()),StandardCharsets.UTF_8).trim();
                String remoteBoot=shell("cat /proc/sys/kernel/random/boot_id").trim();
                if(!localBoot.equals(remoteBoot))throw new IOException("连接目标不是当前电视，已停止");
                if(!shell("id -u").trim().equals("2000"))throw new IOException("连接未获得 shell 身份");
                user=Integer.parseInt(shell("am get-current-user").trim());
                if(!android.os.UserHandle.getUserHandleForUid(user*100000).equals(android.os.Process.myUserHandle()))throw new IOException("前台用户已切换，请重新打开应用管理");
                host=candidate;
                status.changed("已连接 · 本机 · 用户 "+user);
                return;
            }catch(Exception e){failure=e;disconnect();break;}
        }
        throw new IOException("连接失败："+(failure==null?"本机未开放 TCP ADB":failure.getMessage())+"。请开启电视网络调试，核对端口后重试。",failure);
    }

    String shell(String command) throws Exception {
        AdbConnection c=connection;if(c==null)throw new IOException("需要连接 ADB");
        // A timeout closes the transport. Mutating commands are never automatically retried.
        ScheduledFuture<?> timeout=timer.schedule(()->{try{c.close();}catch(IOException ignored){}},20,TimeUnit.SECONDS);
        String marker="__QUIET_EXIT_"+UUID.randomUUID().toString().replace("-","")+"=";
        String wrapped="( "+command+" ); quiet_result=$?; printf '\\n"+marker+"%d\\n' \"$quiet_result\"";
        try(AdbStream stream=c.open("shell:"+wrapped);InputStream input=stream.openInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int size;
            while(true){
                try{size=input.read(buffer);}catch(IOException e){if(!output.toString("UTF-8").contains(marker))throw e;break;}
                if(size<0)break;output.write(buffer,0,size);
                String value=output.toString("UTF-8");int position=value.lastIndexOf(marker);
                if(position>=0 && value.substring(position).endsWith("\n"))break;
            }
            String value=output.toString("UTF-8");int position=value.lastIndexOf(marker);
            if(position<0)throw new IOException("ADB 结果不完整，请重新连接并查询状态");
            int exit=Integer.parseInt(value.substring(position+marker.length()).trim());
            String body=value.substring(0,position).trim();
            if(exit!=0)throw new IOException("命令失败（"+exit+"）："+body);
            return body;
        }finally{timeout.cancel(false);}
    }

    private KeyStore loadKeys() throws Exception {
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"local-adb.p12"));
        KeyStore store=KeyStore.getInstance("PKCS12");
        if(file.getBaseFile().exists()){
            try(InputStream input=file.openRead()){store.load(input,new char[0]);}
        }else{
            store.load(null,new char[0]);
            KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
            KeyPair pair=generator.generateKeyPair();long now=System.currentTimeMillis();
            X500Name name=new X500Name("CN=QuietTV");
            X509Certificate certificate=new JcaX509CertificateConverter().getCertificate(
                new JcaX509v3CertificateBuilder(name,new BigInteger(64,new SecureRandom()),new Date(now-60000),
                    new Date(now+10L*365*86400000),name,pair.getPublic())
                    .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
            store.setKeyEntry("adb",pair.getPrivate(),new char[0],new java.security.cert.Certificate[]{certificate});
            FileOutputStream output=file.startWrite();
            try{store.store(output,new char[0]);file.finishWrite(output);}catch(Exception e){file.failWrite(output);throw e;}
        }
        return store;
    }
    void disconnect(){user=-1;AdbConnection c=connection;connection=null;if(c!=null)try{c.close();}catch(IOException ignored){} }
    @Override public void close(){closed=true;disconnect();timer.shutdownNow();}
}
