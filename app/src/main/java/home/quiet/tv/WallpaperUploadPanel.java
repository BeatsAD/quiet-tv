package home.quiet.tv;

import android.app.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.json.*;

/** Owns the upload socket only while its TV dialog is visible. */
final class WallpaperUploadPanel {
    interface Closed { void closed(String importedId); }
    private final Activity activity;
    private final WallpaperStore store;
    private final boolean chinese;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private WallpaperUploadServer server;
    private ImageUploadDialog view;
    private TextView address,status;
    private ImageView qr;
    private TextView retry;
    private volatile int generation;
    private String host,lastImported;
    private Closed callback;
    private final Runnable check=new Runnable(){@Override public void run(){
        if(dialog==null || server==null)return;
        if(server.expired() || !host.equals(lanAddress(activity))){
            view.resetConnection();address.setVisibility(View.GONE);
            status.setText(tr("会话已结束，请重新连接","Session ended. Reconnect to upload."));status.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);stopServer(server.expired()?"idle_expired":"lan_address_changed");
        } else main.postDelayed(this,2000);
    }};
    WallpaperUploadPanel(Activity activity,WallpaperStore store,boolean chinese){this.activity=activity;this.store=store;this.chinese=chinese;}
    private String tr(String zh,String en){return chinese?zh:en;}
    void show(Bitmap backdrop,Closed callback){
        if(dialog!=null){backdrop.recycle();return;}this.callback=callback;lastImported=null;
        view=new ImageUploadDialog(activity,chinese,backdrop,
            tr("把喜欢的壁纸，留在电视上","Keep your favorite wallpaper on TV"),
            tr("图片  JPG / PNG / 静态 WebP  最大 60 MB\n视频  MP4 / MOV / MKV / WebM  最大 200 MB  60 FPS","Images  JPG / PNG / static WebP  Up to 60 MB\nVideo  MP4 / MOV / MKV / WebM  Up to 200 MB  60 FPS"),null,this::close,this::start,tr("选择图片或视频，上传后在电视上应用","Upload an image or video, then apply it on your TV"));
        dialog=view.dialog;address=view.address;status=view.status;retry=view.retry;qr=view.qr;
        ImageUploadDialog current=view;dialog.setOnDismissListener(d->{dialog=null;view=null;stopServer("dialog_dismissed");current.release();Closed finished=this.callback;this.callback=null;if(finished!=null)finished.closed(lastImported);});
        dialog.show();start();
    }
    private void start(){
        stopServer("restart");int attempt=++generation;status.setVisibility(View.GONE);retry.setVisibility(View.GONE);view.resetConnection();host=lanAddress(activity);
        if(host==null){address.setText(tr("未找到局域网地址，请先连接 Wi-Fi 或网线","No LAN address. Connect Wi-Fi or Ethernet first."));retry.setVisibility(View.VISIBLE);return;}
        String selectedHost=host;address.setText(tr("正在开启上传…","Starting upload…"));
        worker.execute(()->{
            WallpaperUploadServer candidate=server(selectedHost,8765,attempt);
            try{
                try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=server(selectedHost,0,attempt);candidate.listen();}
                String url=candidate.address();BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,384,384,java.util.Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);WallpaperUploadServer ready=candidate;
                main.post(()->{
                    if(dialog==null||attempt!=generation){ready.stop();bitmap.recycle();return;}server=ready;view.connected(ready.shortAddress(),ready.connectionCode());qr.setImageBitmap(bitmap);main.postDelayed(check,2000);
                });
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(dialog!=null&&attempt==generation){address.setText(tr("无法开启上传，请检查网络后重试","Unable to start upload. Check your network and retry."));retry.setVisibility(View.VISIBLE);}});}
        });
    }
    private WallpaperUploadServer server(String host,int port,int attempt){
        return new WallpaperUploadServer(host,port,name->activity.getAssets().open("wallpaper-upload/"+name),new WallpaperUploadServer.Importer(){
            @Override public String accept(InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{return importImage(attempt,input,length,name,active);}
            @Override public String acceptVideo(InputStream input,long length,String name,WallpaperUploadServer.Active active,long upload)throws IOException{return importVideo(attempt,upload,input,length,name,active);}
        },WallpaperUploadServer.MAX_UPLOAD,WallpaperVideo.capabilities(),true);
    }
    private String importVideo(int attempt,long upload,InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{
        return importMedia(attempt,upload,input,length,name,true,active);
    }
    private String importImage(int attempt,InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{
        return importMedia(attempt,0,input,length,name,false,active);
    }
    private String importMedia(int attempt,long upload,InputStream input,long length,String name,boolean video,WallpaperUploadServer.Active active)throws IOException{
        String context="attempt="+attempt+" upload="+upload;long started=SystemClock.elapsedRealtime();
        DiagnosticLog.info("wallpaper.upload.begin",context+" kind="+(video?"video":"image")+" bytes="+length);
        try{
            WallpaperCatalog.Entry entry=WallpaperImport.accept(activity,input,length,name,video,()->attempt==generation&&active.valid(),context);
            DiagnosticLog.info("wallpaper.upload.saved",context+" kind="+(video?"video":"image")+" elapsedMs="+(SystemClock.elapsedRealtime()-started)+" evidence=import_only");
            main.post(()->{if(dialog!=null&&attempt==generation){lastImported=entry.id;status.setText(tr("已收到文件，请返回后预览并应用","File received. Go back to preview and apply."));status.setVisibility(View.VISIBLE);}});
            return new JSONObject().put("id",entry.id).put("kind",entry.kind).put("width",entry.width).put("height",entry.height).put("durationMs",entry.durationMs).toString();
        }catch(JSONException e){throw new IOException(e);}
        catch(IOException|RuntimeException e){DiagnosticLog.error("wallpaper.upload.failed",context+" "+VideoDiagnostics.failureDetails(e),e);throw e;}
        catch(OutOfMemoryError e){throw new IOException("电视无法处理此文件，请降低规格 / File too large for this TV");}
    }
    private void stopServer(String reason){int attempt=generation;++generation;main.removeCallbacks(check);WallpaperUploadServer old=server;server=null;if(old!=null){DiagnosticLog.info("wallpaper.upload.closed","attempt="+attempt+" reason="+reason);old.invalidate();worker.execute(old::stop);}}
    void close(){if(dialog!=null)dialog.dismiss();else stopServer("close");}
    void destroy(){close();worker.shutdown();}
    static String lanAddress(Context context){
        if(Build.VERSION.SDK_INT>=21)return Api21.lanAddress(context);
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active=manager==null?null:manager.getActiveNetworkInfo();
        if(active==null||!active.isConnected())return null;
        if(active.getType()==ConnectivityManager.TYPE_WIFI){
            android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo info=wifi==null?null:wifi.getConnectionInfo();int ip=info==null?0:info.getIpAddress();
            return ip==0?null:(ip&255)+"."+((ip>>>8)&255)+"."+((ip>>>16)&255)+"."+((ip>>>24)&255);
        }
        if(active.getType()==ConnectivityManager.TYPE_ETHERNET)try{
            for(NetworkInterface nic:java.util.Collections.list(NetworkInterface.getNetworkInterfaces())){
                if(!nic.isUp()||!nic.getName().matches("(?:eth|en)[0-9]+"))continue;
                for(InetAddress address:java.util.Collections.list(nic.getInetAddresses()))if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&!address.isLinkLocalAddress()&&address.isSiteLocalAddress())return address.getHostAddress();
            }
        }catch(SocketException ignored){}
        return null;
    }
    @android.annotation.TargetApi(21)
    private static final class Api21 {
        static String lanAddress(Context context){
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(manager==null)return null;
            Network[] networks=manager.getAllNetworks();
            if(Build.VERSION.SDK_INT>=23){Network active=manager.getActiveNetwork();if(active!=null){String address=address(manager,active);if(address!=null)return address;}}
            for(Network network:networks){String address=address(manager,network);if(address!=null)return address;}return null;
        }
        private static String address(ConnectivityManager manager,Network network){
            NetworkCapabilities capabilities=manager.getNetworkCapabilities(network);LinkProperties links=manager.getLinkProperties(network);
            if(capabilities==null||links==null||(!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))return null;
            for(LinkAddress link:links.getLinkAddresses()){InetAddress address=link.getAddress();if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&!address.isLinkLocalAddress()&&address.isSiteLocalAddress())return address.getHostAddress();}return null;
        }
    }
}
