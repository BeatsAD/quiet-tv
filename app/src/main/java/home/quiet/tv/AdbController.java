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
    private volatile PairingTransport pairing;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicInteger generation=new java.util.concurrent.atomic.AtomicInteger();
    int user = -1;
    String host = "";
    ShellCapabilities capabilities=ShellCapabilities.NONE;
    AdbController(Context context, Status status) { this.context=context.getApplicationContext(); this.status=status; }
    boolean connected() { AdbConnection c=connection;return c!=null && c.isConnectionEstablished() && user>=0; }
    void validateUser() throws Exception {
        if(!connected())throw new IOException("需要连接 ADB");
        if(currentUser()!=user || user!=android.os.Process.myUid()/100000){disconnect();throw new IOException("当前用户已改变，请重新连接");}
    }
    private int currentUser() throws Exception {
        if(Build.VERSION.SDK_INT>=24)return Integer.parseInt(shell("am get-current-user").trim());
        // Android 5/6 have no am get-current-user. Only accept a uniquely resumed foreground user.
        return parseForegroundUser(shell("dumpsys activity activities"));
    }
    static int parseForegroundUser(String output) throws IOException {
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("mResumedActivity:[ \\t]*ActivityRecord\\{[^\\r\\n}]*\\bu([0-9]+)\\b").matcher(output);
        int found=-1;
        while(match.find()){int value=Integer.parseInt(match.group(1));if(found>=0 && found!=value)throw new IOException("前台用户状态不唯一");found=value;}
        if(found<0)throw new IOException("无法核对前台用户，请返回 Quiet TV 后重试");return found;
    }
    static boolean pairingAvailable(){
        if(Build.VERSION.SDK_INT<33)return false;
        try{Class.forName("io.github.muntashirakon.crypto.spake2.Spake2Context");return org.conscrypt.Conscrypt.isAvailable();}catch(LinkageError|ReflectiveOperationException e){return false;}
    }
    static boolean localAddress(InetAddress address){
        if(address==null)return false;if(address.isLoopbackAddress())return true;
        try{for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))if(nic.isUp())for(InetAddress local:Collections.list(nic.getInetAddresses()))if(local.equals(address))return true;}catch(SocketException ignored){}return false;
    }
    void pair(int port,String code) throws Exception {
        if(!pairingAvailable())throw new IOException("当前系统或 ABI 无法使用无线配对");
        if(port<1 || port>65535 || !code.matches("[0-9]{6}"))throw new IOException("请输入有效配对端口及六位配对码");
        disconnect();int attempt=generation.get();byte[] password=code.getBytes(StandardCharsets.UTF_8);
        io.github.muntashirakon.adb.PairingConnectionCtx protocol=null;
        try{
            String target=null;ArrayList<InetAddress> addresses=new ArrayList<>();addresses.add(InetAddress.getByName("127.0.0.1"));
            for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))if(nic.isUp())for(InetAddress value:Collections.list(nic.getInetAddresses()))if(value instanceof Inet4Address && !value.isLoopbackAddress())addresses.add(value);
            for(InetAddress address:addresses){if(closed || generation.get()!=attempt)throw new IOException("配对已取消");try(Socket probe=new Socket()){probe.connect(new InetSocketAddress(address,port),1500);target=address.getHostAddress();break;}catch(IOException ignored){}}
            if(target==null)throw new IOException("本机配对端口不可达");
            KeyStore keys=loadKeys();PairingTransport transport=new PairingTransport(target,port,30000);pairing=transport;
            if(closed || generation.get()!=attempt)throw new IOException("配对已取消");
            status.changed("正在配对 · 请保持系统配对页面有效");
            protocol=new io.github.muntashirakon.adb.PairingConnectionCtx("127.0.0.1",transport.port(),password,(PrivateKey)keys.getKey("adb",new char[0]),keys.getCertificate("adb"),"QuietTV");
            protocol.start();if(closed || generation.get()!=attempt)throw new IOException("配对已取消");status.changed("已配对 · 尚需连接并核对本机身份");
        }catch(Exception|LinkageError e){throw new IOException("配对未完成，请重新打开系统配对页面并核对端口和配对码；取消或超时后不会自动重试");}
        finally{Arrays.fill(password,(byte)0);PairingTransport transport=pairing;pairing=null;if(transport!=null)transport.close();if(protocol!=null)try{protocol.close();}catch(RuntimeException ignored){}}
    }

    void connect(int port) throws Exception {
        if(port<1 || port>65535)throw new IOException("端口必须在 1～65535 之间");
        disconnect();
        int attempt=generation.get();
        List<String> hosts=new ArrayList<>();hosts.add("127.0.0.1");
        for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))
            if(nic.isUp() && !nic.isLoopback())for(InetAddress address:Collections.list(nic.getInetAddresses()))
                if(address instanceof Inet4Address)hosts.add(address.getHostAddress());
        Exception failure=null;
        for(String candidate:hosts){
            if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
            status.changed("连接中 · "+candidate+":"+port);
            // Bound the TCP reachability check; the library's constructor uses a blocking Socket.
            try(Socket probe=new Socket()){probe.connect(new InetSocketAddress(candidate,port),2000);}
            catch(IOException e){failure=e;continue;}
            KeyStore keys=loadKeys();
            AdbConnection c=new AdbConnection.Builder(candidate,port).setApi(Build.VERSION.SDK_INT)
                    .setDeviceName("QuietTV").setPrivateKey((PrivateKey)keys.getKey("adb",new char[0]))
                    .setCertificate(keys.getCertificate("adb")).build();
            connection=c;
            if(closed || generation.get()!=attempt){disconnect();throw new IOException("连接已取消");}
            status.changed("等待授权 · 请在电视弹窗中允许 QuietTV 的调试连接");
            try{
                if(!c.connect(60,TimeUnit.SECONDS,false))throw new IOException("授权超时，请确认电视调试已开启并允许连接");
                verifyLocalDevice();
                if(!shell("id -u").trim().equals("2000"))throw new IOException("连接未获得 shell 身份");
                user=currentUser();
                if(user!=android.os.Process.myUid()/100000)throw new IOException("前台用户已切换，请重新打开应用管理");
                String pmHelp="",cmdHelp="";
                try{pmHelp=shell("pm help 2>&1 || true");}catch(IOException ignored){}
                try{cmdHelp=shell("cmd package help 2>&1 || true");}catch(IOException ignored){}
                capabilities=new ShellCapabilities(pmHelp,cmdHelp);
                host=candidate;
                if(closed || generation.get()!=attempt){disconnect();throw new IOException("连接已取消");}
                status.changed("已连接 · 本机 · 用户 "+user);
                return;
            }catch(Exception e){failure=e;disconnect();break;}
        }
        throw new IOException("连接失败："+(failure==null?"本机未开放 TCP ADB":failure.getMessage())+"。请开启电视网络调试，核对端口后重试。",failure);
    }

    private void verifyLocalDevice() throws Exception {
        String localBoot;
        try(BufferedReader input=new BufferedReader(new InputStreamReader(new FileInputStream("/proc/sys/kernel/random/boot_id"),StandardCharsets.UTF_8))){String value=input.readLine();localBoot=value==null?"":value.trim();}
        catch(IOException unavailable){
            // Older SELinux policies hide boot_id from apps. A fresh local-file challenge still
            // rejects accidentally forwarded connections to another device; no model-name fallback.
            File directory=context.getExternalFilesDir(null);
            if(directory==null)throw new IOException("无法读取本机标识，且本机校验目录不可用");
            File marker=File.createTempFile("quiet-adb-probe-",".txt",directory);
            try{
                String challenge=UUID.randomUUID().toString()+UUID.randomUUID().toString();
                try(FileOutputStream out=new FileOutputStream(marker)){out.write(challenge.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
                String path="'"+marker.getAbsolutePath().replace("'","'\\''")+"'";
                if(!challenge.equals(shell("cat "+path).trim()))throw new IOException("连接目标不是当前电视，已停止");
            }finally{
                // Exactly the one ephemeral marker created by this attempt; never scan or bulk-clean.
                if(!marker.delete())android.util.Log.w("QuietTVAdb","Temporary local identity marker retained");
            }
            return;
        }
        String remoteBoot=shell("cat /proc/sys/kernel/random/boot_id").trim();
        if(localBoot.isEmpty() || !localBoot.equals(remoteBoot))throw new IOException("连接目标不是当前电视，已停止");
    }

    String shell(String command) throws Exception {
        AdbConnection c=connection;if(c==null)throw new IOException("需要连接 ADB");
        // A timeout closes the transport. Mutating commands are never automatically retried.
        ScheduledFuture<?> timeout=timer.schedule(()->{try{c.close();}catch(IOException ignored){}},20,TimeUnit.SECONDS);
        String marker="__QUIET_EXIT_"+UUID.randomUUID().toString().replace("-","")+"=";
        // Android 5 toolbox does not provide printf; echo is a shell builtin there.
        String wrapped="( "+command+" ); quiet_result=$?; echo; echo \""+marker+"$quiet_result\"";
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
    void disconnect(){generation.incrementAndGet();user=-1;capabilities=ShellCapabilities.NONE;PairingTransport transport=pairing;if(transport!=null)transport.close();AdbConnection c=connection;connection=null;if(c!=null)try{c.close();}catch(IOException ignored){}status.changed("未连接");}
    @Override public void close(){closed=true;disconnect();timer.shutdownNow();}
}
