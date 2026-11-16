package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.security.keystore.*;
import android.util.AtomicFile;
import android.widget.*;
import home.quiet.components.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Node, short-lived QR and explicit TV approval are separate from component download. */
@androidx.annotation.RequiresApi(26)
final class TvRemoteController {
    private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static volatile JSONObject state=new JSONObject();private static volatile boolean enabled;
    private static Context context;private static volatile boolean prepared;private static ScheduledFuture<?> polling;
    private static final Set<Runnable> listeners=new CopyOnWriteArraySet<>();
    private static void notifyChanged(){main.post(()->{for(Runnable r:listeners)r.run();});}
    private static JSONObject request(JSONObject input)throws IOException{
        try{JSONObject output=new JSONObject(TvRemoteNative.call(input.toString()));if(output.has("error"))throw new IOException("组网操作尚未完成，请核对节点、网络与本机ADB状态");return output;}catch(JSONException|UnsatisfiedLinkError e){throw new IOException("组网组件无法使用，请重新核对组件",e);}
    }
    private static JSONObject op(String name)throws IOException{try{return request(new JSONObject().put("op",name));}catch(JSONException e){throw new IOException(e);}}
    private static JSONObject peer(Context c)throws Exception{
        PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
        android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?info.signingInfo.getApkContentsSigners():info.signatures;JSONArray signers=new JSONArray();for(android.content.pm.Signature s:signatures)signers.put(ComponentFiles.hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
        return new JSONObject().put("packageName",c.getPackageName()).put("uid",android.os.Process.myUid()).put("versionCode",info.versionCode).put("fingerprintSha256",ComponentFiles.hex(MessageDigest.getInstance("SHA-256").digest(Build.FINGERPRINT.getBytes("UTF-8")))).put("signerSha256",signers);
    }
    private static void prepare(Context supplied)throws Exception{
        context=supplied.getApplicationContext();if(prepared)return;ComponentRepository.get(context).loadCore();if(op("version").getInt("hostApi")!=1)throw new IOException("组网接口版本不兼容");
        byte[] key=stateKey(context);try{request(new JSONObject().put("op","create").put("dir",new File(context.getFilesDir(),"remote-node").getAbsolutePath()).put("key",android.util.Base64.encodeToString(key,android.util.Base64.NO_WRAP)).put("peer",peer(context)));prepared=true;}finally{Arrays.fill(key,(byte)0);}
    }
    static void start(Context supplied,Runnable failed){
        if(enabled)return;enabled=true;worker.execute(()->{try{prepare(supplied);op("start");if(polling!=null)polling.cancel(false);polling=worker.scheduleWithFixedDelay(()->{if(!enabled)return;try{JSONObject next=op("status");String url=next.optString("authUrl");if(!url.isEmpty()&&!loginUrl(url))throw new IOException("节点登录地址无效");if("Running".equals(next.optString("state")))try{request(new JSONObject().put("op","serve").put("port",adbPort()));next.put("serviceReady",true);}catch(IOException unavailable){next.put("serviceReady",false).put("message","节点在线  本机ADB服务待核对");}next.put("requests",op("pending").optJSONArray("requests"));state=next;notifyChanged();}catch(Exception e){setError(e);}},0,2,TimeUnit.SECONDS);}catch(Exception e){enabled=false;setError(e);main.post(failed);}});
    }
    static void stop(){enabled=false;worker.execute(()->{if(polling!=null){polling.cancel(false);polling=null;}if(prepared)try{op("close");}catch(IOException ignored){}prepared=false;if(!state.optString("state").equals("ERROR"))state=new JSONObject();notifyChanged();});}
    private static void setError(Exception e){try{state=new JSONObject().put("state","ERROR").put("message",e instanceof IOException?e.getMessage():"组网操作未完成");}catch(JSONException ignored){}notifyChanged();}
    static JSONObject command(Context c,String action,String arg)throws IOException{
        if(action.equals("state"))try{return new JSONObject(state.toString());}catch(JSONException e){throw new IOException(e);}
        if(action.equals("start")){if(!ComponentRepository.get(c).isReady("tailscale"))throw new IOException("需要先下载组网组件");c.startForegroundService(new Intent(c,TvRemoteService.class).setAction("START"));return state;}
        if(action.equals("pause")){c.stopService(new Intent(c,TvRemoteService.class));stop();return state;}
        if(!enabled||!prepared)throw new IOException("请先开启电视远程访问");
        Future<JSONObject> result=worker.submit(()->{if(action.equals("login"))return op("login");if(action.equals("offer")){JSONObject q=new JSONObject().put("op","offer").put("ip",localAddress()).put("port",adbPort());return request(q);}throw new IOException("不支持的组网操作");});
        try{return result.get(20,TimeUnit.SECONDS);}catch(Exception e){throw new IOException("组网操作未确认，请重新读取状态",e);}
    }
    private static boolean loginUrl(String value){try{URI u=new URI(value);return value.length()<=2048&&"https".equals(u.getScheme())&&"login.tailscale.com".equals(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null&&u.getFragment()==null;}catch(Exception e){return false;}}
    private static int adbPort(){for(String key:new String[]{"service.adb.tls.port","service.adb.tcp.port","persist.adb.tcp.port"})try{java.lang.Process p=new ProcessBuilder("/system/bin/getprop",key).start();String value=new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();p.waitFor();int port=Integer.parseInt(value==null?"":value.trim());if(port>0&&port<65536)return port;}catch(Exception ignored){}return 5555;}
    private static String localAddress()throws Exception{for(NetworkInterface network:Collections.list(NetworkInterface.getNetworkInterfaces()))if(network.isUp()&&!network.isLoopback())for(InetAddress address:Collections.list(network.getInetAddresses()))if(address instanceof Inet4Address&&address.isSiteLocalAddress())return address.getHostAddress();throw new IOException("电视没有可用的局域网地址");}
    private static byte[] stateKey(Context c)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);String alias="quiet_tv_optional_remote_v1";
        File file=new File(c.getFilesDir(),"remote-node-key.sealed");boolean saved=file.exists()||new File(file+".bak").exists();
        javax.crypto.SecretKey key=(javax.crypto.SecretKey)store.getKey(alias,null);if(key==null&&saved)throw new IOException("组网身份密钥不可用，原资料已保留");if(key==null){KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());key=generator.generateKey();}
        AtomicFile atomic=new AtomicFile(file);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");byte[] aad=(c.getPackageName()+":remote-node-v1").getBytes("UTF-8");
        if(saved){byte[] sealed=ComponentFiles.read(atomic.openRead(),256);if(sealed.length<40)throw new IOException("组网身份无法解密，原资料已保留");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(sealed,12)));cipher.updateAAD(aad);byte[] plain=cipher.doFinal(Arrays.copyOfRange(sealed,12,sealed.length));if(plain.length!=32)throw new IOException("组网身份无效");return plain;}
        byte[] plain=new byte[32];new SecureRandom().nextBytes(plain);cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad);byte[] encrypted=cipher.doFinal(plain);FileOutputStream out=atomic.startWrite();try{out.write(cipher.getIV());out.write(encrypted);atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}return plain;
    }
    static void show(AppManagementActivity ui){
        ManagementUi.Sheet sheet=ui.new Sheet("Tailscale 组网");sheet.settingsLayout("关闭",sheet.dialog::dismiss);sheet.settingsActions(false);
        TextView status=ui.label("",14);sheet.body.addView(status);ImageView qr=new QrCodeView(ui,ui.unit);sheet.body.addView(qr,new LinearLayout.LayoutParams(ui.px(210),ui.px(210)));qr.setVisibility(android.view.View.GONE);
        TextView hint=ui.muted("首次设置请在电视旁完成，手机仍需独立ADB授权。",12);sheet.body.addView(hint);
        Button start=ui.button("开启远程访问",()->{try{command(ui,enabled?"pause":"start",null);}catch(IOException e){ui.message(e.getMessage());}});sheet.body.addView(start);
        Button login=ui.button("显示登录二维码",()->worker.execute(()->{try{op("login");}catch(IOException e){setError(e);}}));sheet.body.addView(login);
        Button bind=ui.button("显示绑定二维码",()->new Thread(()->{try{JSONObject offer=command(ui,"offer",null);main.post(()->drawQr(qr,offer.toString(),hint,"请使用手机助手扫码绑定，并在电视上批准请求。"));}catch(IOException e){main.post(()->ui.message(e.getMessage()));}},"remote-offer").start());sheet.body.addView(bind);
        String[] seen={""};String[] shownUrl={""};android.app.Dialog[] pendingDialog={null};Runnable changed=()->{
            String phase=state.optString("state","OFF");status.setText(state.optString("message",phase.equals("Running")?"节点在线  管理授权待核对":phase.equals("NeedsLogin")?"等待登录":phase.equals("NeedsMachineAuth")?"等待网络审批":enabled?"正在连接":"已暂停"));start.setText(enabled?"暂停远程访问":"开启远程访问");login.setEnabled(enabled);bind.setEnabled(phase.equals("Running"));
            String url=state.optString("authUrl");if(!url.isEmpty()&&loginUrl(url)&&!url.equals(shownUrl[0])){shownUrl[0]=url;drawQr(qr,url,hint,"扫码登录电视节点，登录后再显示绑定二维码。");}
            JSONArray requests=state.optJSONArray("requests");if(requests!=null&&requests.length()>0){JSONObject pending=requests.optJSONObject(0);String ticket=pending.optString("ticket");if(!ticket.equals(seen[0])){seen[0]=ticket;ManagementUi.Sheet approval=ui.new Sheet("允许此手机远程访问？");pendingDialog[0]=approval.dialog;approval.text(pending.optString("name")+"\n身份 "+pending.optString("fingerprint").substring(0,Math.min(12,pending.optString("fingerprint").length()))+"\n仅允许绑定后的管理通道，ADB仍需单独授权。");Button allow=approval.action("允许",()->{approval.dialog.dismiss();approve(ticket,true);});approval.action("拒绝",()->{approval.dialog.dismiss();approve(ticket,false);});approval.show(allow);}}
        };
        listeners.add(changed);sheet.dialog.setOnDismissListener(d->{listeners.remove(changed);if(pendingDialog[0]!=null)pendingDialog[0].dismiss();});changed.run();sheet.show(start);
    }
    private static void approve(String ticket,boolean allow){worker.execute(()->{try{request(new JSONObject().put("op",allow?"approve":"deny").put("node",ticket));}catch(Exception e){setError(e);}});}
    private static void drawQr(ImageView image,String text,TextView hint,String message){try{com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(text,com.google.zxing.BarcodeFormat.QR_CODE,480,480,Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,2));int[] pixels=new int[480*480];for(int y=0;y<480;y++)for(int x=0;x<480;x++)pixels[y*480+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;image.setImageBitmap(Bitmap.createBitmap(pixels,480,480,Bitmap.Config.ARGB_8888));image.setVisibility(android.view.View.VISIBLE);hint.setText(message);}catch(Exception e){hint.setText("二维码生成失败，请重新操作");}}
}
