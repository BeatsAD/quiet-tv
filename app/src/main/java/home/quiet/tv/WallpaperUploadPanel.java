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
    private final float unit;
    private final boolean chinese;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private WallpaperUploadServer server;
    private TextView address,code,status;
    private ImageView qr;
    private TextView retry;
    private volatile int generation;
    private String host,lastImported;
    private Closed callback;
    private final Runnable check=new Runnable(){@Override public void run(){
        if(dialog==null || server==null)return;
        if(server.expired() || !host.equals(lanAddress(activity))){
            status.setText(tr("会话已结束，请重新连接","Session ended. Reconnect to upload."));status.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);stopServer();
        } else main.postDelayed(this,2000);
    }};
    WallpaperUploadPanel(Activity activity,WallpaperStore store,float unit,boolean chinese){this.activity=activity;this.store=store;this.unit=unit;this.chinese=chinese;}
    private String tr(String zh,String en){return chinese?zh:en;}
    void show(Bitmap backdrop,Closed callback){
        if(dialog!=null){backdrop.recycle();return;}this.callback=callback;lastImported=null;
        ImageUploadDialog view=new ImageUploadDialog(activity,unit,chinese,backdrop,
            tr("把喜欢的壁纸，留在电视上","Keep your favorite wallpaper on TV"),
            tr("图片  JPG / PNG / 静态 WebP  最大 20 MB\n视频  MP4  最大 30 MB  30 FPS","Images  JPG / PNG / static WebP  Up to 20 MB\nVideo  MP4  Up to 30 MB  30 FPS"),null,this::close,this::start,tr("选择图片或视频，上传后在电视上应用","Upload an image or video, then apply it on your TV"));
        dialog=view.dialog;address=view.address;code=view.code;status=view.status;retry=view.retry;qr=view.qr;
        dialog.setOnDismissListener(d->{dialog=null;stopServer();view.release();Closed finished=this.callback;this.callback=null;if(finished!=null)finished.closed(lastImported);});
        dialog.show();start();
    }
    private void start(){
        stopServer();int attempt=++generation;status.setVisibility(View.GONE);retry.setVisibility(View.GONE);code.setText("");qr.setImageDrawable(null);host=lanAddress(activity);
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
                    if(dialog==null||attempt!=generation){ready.stop();bitmap.recycle();return;}server=ready;address.setText(ready.shortAddress());
                    String prefix=tr("连接码  ","Code  ");android.text.SpannableString value=new android.text.SpannableString(prefix+ready.connectionCode());
                    value.setSpan(new android.text.style.AbsoluteSizeSpan(Math.round(11*unit)),0,prefix.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    value.setSpan(new android.text.style.ForegroundColorSpan(WallpaperUi.MUTED),0,prefix.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    code.setText(value);code.setContentDescription(tr("连接码  ","Connection code  ")+ready.connectionCode());qr.setImageBitmap(bitmap);main.postDelayed(check,2000);
                });
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(dialog!=null&&attempt==generation){address.setText(tr("无法开启上传，请检查网络后重试","Unable to start upload. Check your network and retry."));retry.setVisibility(View.VISIBLE);}});}
        });
    }
    private WallpaperUploadServer server(String host,int port,int attempt){
        return new WallpaperUploadServer(host,port,name->activity.getAssets().open("wallpaper-upload/"+name),new WallpaperUploadServer.Importer(){
            @Override public String accept(InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{return importImage(attempt,input,length,name,active);}
            @Override public String acceptVideo(InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{return importVideo(attempt,input,length,name,active);}
        },WallpaperUploadServer.MAX_UPLOAD,WallpaperVideo.capabilities(),true);
    }
    private String importVideo(int attempt,InputStream input,long length,String name,WallpaperUploadServer.Active active)throws IOException{
        store.checkVideoCapacity(length);File incoming=store.videoIncoming(),poster=new File(incoming.getPath()+".jpg");
        WallpaperUploadServer.Active valid=()->attempt==generation&&active.valid();
        try{
            try(FileOutputStream output=new FileOutputStream(incoming)){
                byte[] buffer=new byte[16384];long remaining=length;
                while(remaining>0){if(!valid.valid())throw new IOException("上传会话已结束 / Session ended");int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("视频传输未完成 / Incomplete video upload");output.write(buffer,0,n);remaining-=n;}output.getFD().sync();
            }
            if(!valid.valid())throw new IOException("上传会话已结束 / Session ended");
            WallpaperVideo.Info info=WallpaperVideo.inspect(incoming);WallpaperVideo.poster(incoming,poster,info);
            WallpaperCatalog.Entry entry=store.addVideo(incoming,poster,name,info,valid);
            main.post(()->{if(dialog!=null&&attempt==generation){lastImported=entry.id;status.setText(tr("已收到视频，请返回后预览并应用","Video received. Go back to preview and apply."));status.setVisibility(View.VISIBLE);}});
            return new JSONObject().put("id",entry.id).put("kind","video").put("width",info.width).put("height",info.height).put("durationMs",info.durationMs).toString();
        }catch(FileNotFoundException e){throw new IOException("无法读取或保存视频，请检查存储 / Unable to read or save video; check storage");}
        catch(JSONException e){throw new IOException(e);}catch(OutOfMemoryError e){throw new IOException("电视无法处理此视频，请降低规格 / Video too large for this TV");}
        finally{if(incoming.isFile()&&!incoming.delete())android.util.Log.w("QuietTV","Video upload temporary file retained");if(poster.isFile()&&!poster.delete())android.util.Log.w("QuietTV","Video poster temporary file retained");}
    }
    private String importImage(int attempt,InputStream input,long length,String name,WallpaperUploadServer.Active active) throws IOException {
        store.checkCapacity();File incoming=store.incoming(),prepared=store.prepared();
        try{
            try(FileOutputStream output=new FileOutputStream(incoming)){
                byte[] buffer=new byte[16384];long remaining=length;
                while(remaining>0){if(attempt!=generation || !active.valid())throw new IOException("上传会话已结束 / Session ended");int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("图片传输未完成 / Incomplete upload");output.write(buffer,0,n);remaining-=n;}
            }
            int[] size=WallpaperImages.prepare(incoming,prepared,WallpaperImages.importLimit(activity));
            if(attempt!=generation || !active.valid())throw new IOException("上传会话已结束 / Session ended");
            WallpaperCatalog.Entry entry=store.add(prepared,name,size[0],size[1]);
            main.post(()->{if(dialog!=null&&attempt==generation){lastImported=entry.id;status.setText(tr("已收到图片，请按返回后选择使用","Photo received. Press Back, then select it to apply."));status.setVisibility(View.VISIBLE);}});
            return new JSONObject().put("id",entry.id).toString();
        }catch(JSONException e){throw new IOException(e);}
        catch(OutOfMemoryError e){throw new IOException("图片过大，电视无法处理，请选择较小图片 / Image too large for this TV");}
        finally{if(incoming.exists()&&!incoming.delete())android.util.Log.w("QuietTV","Upload temporary file retained");if(prepared.exists()&&!prepared.delete())android.util.Log.w("QuietTV","Prepared temporary file retained");}
    }
    private void stopServer(){++generation;main.removeCallbacks(check);WallpaperUploadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}}
    void close(){if(dialog!=null)dialog.dismiss();else stopServer();}
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
