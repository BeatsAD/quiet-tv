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

/** A foreground-owned ADB client with serialized connection attempts restricted to this device's addresses. */
final class AdbController implements Closeable {
    interface Status { void changed(String value); }
    enum Stage { IDLE, KEYS, PAIRING, PORT, HANDSHAKE, LOCAL, SHELL, USER, CAPABILITIES, CONNECTED }
    enum Failure { NONE, KEYS, PORT, HANDSHAKE, LOCAL, SHELL, USER, CAPABILITIES }
    private static final Object KEY_LOCK=new Object();
    private static KeyStore cachedKeys;
    private volatile Stage stage=Stage.IDLE;
    private volatile Failure failure=Failure.NONE;
    Stage stage(){return stage;}
    Failure failure(){return failure;}
    int generation(){return generation.get();}
    private void publish(Stage value,String detail){if(stage!=value)DiagnosticLog.info("adb.stage",value.name());stage=value;status.changed(detail);}
    private synchronized void publish(Stage value,String detail,int attempt){if(!closed && generation.get()==attempt)publish(value,detail);}
    private synchronized void attach(Closeable value,int attempt) throws IOException {
        if(closed || generation.get()!=attempt){value.close();throw new IOException("连接已取消");}transport=value;
    }
    synchronized int prepare(){disconnect();failure=Failure.NONE;return generation.get();}
    private final Context context;
    private final Status status;
    private volatile AdbConnection connection;
    private volatile Closeable transport;
    private final WirelessPairing pairing=new WirelessPairing();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicInteger generation=new java.util.concurrent.atomic.AtomicInteger();
    int user = -1;
    String host = "";
    private volatile int connectedPort;
    int connectedPort(){return connected()?connectedPort:0;}
    ShellCapabilities capabilities=ShellCapabilities.NONE;
    AdbController(Context context, Status status) { this.context=context.getApplicationContext(); this.status=status; }
    boolean connected() { AdbConnection c=connection;return stage==Stage.CONNECTED && c!=null && c.isConnectionEstablished() && user>=0; }
    void validateUser() throws Exception {
        final int request=generation.get(),expectedUser=user;
        if(!connected())throw new IOException("需要连接 ADB");
        int actual=currentUser();
        synchronized(this){
            if(generation.get()!=request)throw new IOException("查询已取消，请重新连接");
            if(actual!=expectedUser || expectedUser!=android.os.Process.myUid()/100000){disconnect();throw new IOException("当前用户已改变，请重新连接");}
        }
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
    static boolean pairingAvailable(){return WirelessPairing.available();}
    static boolean localAddress(InetAddress address){
        if(address==null)return false;if(address.isLoopbackAddress())return true;
        try{for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))if(nic.isUp())for(InetAddress local:Collections.list(nic.getInetAddresses()))if(local.equals(address))return true;}catch(SocketException ignored){}return false;
    }
    void pair(int port,String code) throws Exception {pair(port,code,prepare());}
    void pair(int port,String code,int attempt) throws Exception {
        if(!pairingAvailable())throw new IOException("当前版本或系统不支持无线配对，请使用传统 TCP");
        if(port<1 || port>65535 || !code.matches("[0-9]{6}"))throw new IOException("请输入有效配对端口及六位配对码");
        if(closed || generation.get()!=attempt)throw new IOException("配对已取消");
        publish(Stage.PAIRING,"正在配对",attempt);
        pairing.start(port,code,loadKeys(),generation,attempt,value->publish(Stage.PAIRING,value,attempt));
    }

    void connect(int port) throws Exception {connect(port,prepare());}
    void connect(int port,int attempt) throws Exception {
        if(port<1 || port>65535)throw new IOException("端口必须在 1～65535 之间");
        if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
        List<String> hosts=new ArrayList<>();hosts.add("127.0.0.1");
        for(NetworkInterface nic:Collections.list(NetworkInterface.getNetworkInterfaces()))
            if(nic.isUp() && !nic.isLoopback())for(InetAddress address:Collections.list(nic.getInetAddresses()))
                if(address instanceof Inet4Address)hosts.add(address.getHostAddress());
        Exception failure=null;
        String stage="连接端口";
        publish(Stage.KEYS,"正在读取本机连接密钥",attempt);
        KeyStore keys;
        try{keys=loadKeys();}catch(Exception e){this.failure=Failure.KEYS;throw new IOException("读取本机连接密钥失败",e);}
        for(String candidate:hosts){
            if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
            publish(Stage.PORT,"连接中  "+candidate+":"+port,attempt);
            // One actual daemon connection, with a bounded connect and cancellation support.
            Socket socket=new Socket();attach(socket,attempt);
            if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
            try{socket.connect(new InetSocketAddress(candidate,port),2000);}
            catch(IOException e){socket.close();transport=null;if(closed || generation.get()!=attempt)throw new IOException("连接已取消",e);failure=e;continue;}
            stage="ADB 握手／授权";
            try{
                PairingTransport relay=new PairingTransport(socket);attach(relay,attempt);
                if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
                AdbConnection c=new AdbConnection.Builder("127.0.0.1",relay.port()).setApi(Build.VERSION.SDK_INT)
                        .setDeviceName("QuietTV").setPrivateKey(privateKey(keys))
                        .setCertificate(keys.getCertificate("adb")).build();
                synchronized(this){if(closed || generation.get()!=attempt){c.close();throw new IOException("连接已取消");}connection=c;}
                if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
                publish(Stage.HANDSHAKE,"正在进行 ADB 握手  如电视出现调试授权弹窗，请选择允许",attempt);
                if(!c.connect(60,TimeUnit.SECONDS,false))throw new IOException("端口已连接，但 ADB 握手或授权超时；没有弹窗不代表已授权");
                stage="本机身份核对";publish(Stage.LOCAL,"正在核对本机身份",attempt);
                verifyLocalDevice();
                stage="ADB 身份核对";publish(Stage.SHELL,"正在核对 ADB shell 身份",attempt);
                if(!shell("id -u").trim().equals("2000"))throw new IOException("adbd 未提供 shell 身份（uid 2000）；工厂／root 调试模式不能用于当前应用管理");
                stage="前台用户核对";publish(Stage.USER,"正在核对当前用户",attempt);
                user=currentUser();
                if(user!=android.os.Process.myUid()/100000)throw new IOException("前台用户已切换，请重新打开应用管理");
                publish(Stage.CAPABILITIES,"正在核对管理能力",attempt);
                String pmHelp="",cmdHelp="";
                try{pmHelp=shell("pm help 2>&1 || true");}catch(IOException ignored){}
                try{cmdHelp=shell("cmd package help 2>&1 || true");}catch(IOException ignored){}
                capabilities=new ShellCapabilities(pmHelp,cmdHelp);
                host=candidate;connectedPort=port;
                if(closed || generation.get()!=attempt)throw new IOException("连接已取消");
                if(!c.isConnectionEstablished())throw new IOException("ADB 连接已断开，请重新连接");
                publish(Stage.CONNECTED,"已连接  本机  用户 "+user,attempt);
                return;
            }catch(Exception e){boolean cancelled=closed || generation.get()!=attempt;failure=e;if(!cancelled){this.failure=failureFor(this.stage);disconnect();}if(cancelled)throw new IOException("连接已取消",e);break;}
        }
        if(this.failure==Failure.NONE)this.failure=Failure.PORT;
        DiagnosticLog.error("adb.connection_failed."+this.failure.name(),failure);
        throw new IOException("连接失败（"+stage+"）："+(failure==null?"本机未开放 TCP ADB":(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage())),failure);
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
        int request=generation.get();AdbConnection c=connection;if(c==null)throw new IOException("需要连接 ADB");
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
            if(generation.get()!=request)throw new IOException("查询已取消，请重新连接");
            int exit=Integer.parseInt(value.substring(position+marker.length()).trim());
            String body=value.substring(0,position).trim();
            if(exit!=0)throw new IOException("命令失败（"+exit+"）："+body);
            return body;
        }finally{timeout.cancel(false);}
    }

    static PrivateKey privateKey(KeyStore keys) throws Exception {
        // libadb destroys its private-key instance on close. Keep cached credentials
        // intact and give each connection/pairing an independent RSA key object.
        return KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(keys.getKey("adb",new char[0]).getEncoded()));
    }
    private KeyStore loadKeys() throws Exception {
        synchronized(KEY_LOCK){if(cachedKeys!=null)return cachedKeys;
            KeyStore loaded=readKeys();cachedKeys=loaded;return loaded;
        }
    }
    private KeyStore readKeys() throws Exception {
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
    private static Failure failureFor(Stage stage){
        switch(stage){case KEYS:return Failure.KEYS;case HANDSHAKE:return Failure.HANDSHAKE;case LOCAL:return Failure.LOCAL;case SHELL:return Failure.SHELL;case USER:return Failure.USER;case CAPABILITIES:return Failure.CAPABILITIES;default:return Failure.PORT;}
    }
    synchronized void disconnect(){generation.incrementAndGet();user=-1;capabilities=ShellCapabilities.NONE;pairing.close();Closeable active=transport;transport=null;if(active!=null)try{active.close();}catch(IOException ignored){}AdbConnection c=connection;connection=null;if(c!=null)try{c.close();}catch(IOException ignored){}publish(Stage.IDLE,"未连接");}
    @Override public void close(){closed=true;disconnect();timer.shutdownNow();}
}
