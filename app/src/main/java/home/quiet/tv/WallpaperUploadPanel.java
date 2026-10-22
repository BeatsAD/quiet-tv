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
    private final WallpaperUi ui;
    private Bitmap backdrop;
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
    WallpaperUploadPanel(Activity activity,WallpaperStore store,float unit,boolean chinese){this.activity=activity;this.store=store;this.unit=unit;this.chinese=chinese;this.ui=new WallpaperUi(activity,unit);}
    private int px(int n){return Math.round(n*unit);}
    private String tr(String zh,String en){return chinese?zh:en;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout step(LinearLayout parent,String number,String title,String detail,int minimum){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.TOP);
        TextView badge=ui.text(number,11);badge.setGravity(Gravity.CENTER);badge.setBackground(ui.surface(0xff3d4857,100,Color.TRANSPARENT,0));
        row.addView(badge,new LinearLayout.LayoutParams(px(23),px(23)));
        LinearLayout body=column();body.setPadding(px(11),px(2),0,0);body.addView(ui.text(title,13));
        if(detail!=null){TextView hint=ui.text(detail,11);hint.setTextColor(WallpaperUi.MUTED);hint.setPadding(0,px(5),0,0);body.addView(hint);}
        row.addView(body,new LinearLayout.LayoutParams(0,-2,1));row.setMinimumHeight(px(minimum));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);if(parent.getChildCount()>0)rp.topMargin=px(12);parent.addView(row,rp);return body;
    }
    void show(Bitmap backdrop,Closed callback){
        if(dialog!=null){backdrop.recycle();return;}this.backdrop=backdrop;this.callback=callback;lastImported=null;
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(activity);ImageView background=new ImageView(activity);background.setScaleType(ImageView.ScaleType.FIT_XY);background.setImageBitmap(backdrop);stage.addView(background,new FrameLayout.LayoutParams(-1,-1));
        View dim=new View(activity);dim.setBackgroundColor(0xbf070b12);stage.addView(dim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout box=column();box.setPadding(px(34),px(30),px(34),px(30));box.setBackground(ui.surface(0xff222831,23,0x20ffffff,1));
        if(Build.VERSION.SDK_INT>=21)box.setElevation(px(18));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=ui.text(tr("把喜欢的风景，留在电视上","Keep your favorite view on TV"),23);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.button("",false,this::close);close.setContentDescription(tr("关闭上传","Close upload"));close.setPadding(px(8),px(8),px(8),px(8));close.setCompoundDrawables(ui.icon(R.drawable.wp_x,17,WallpaperUi.INK),null,null,null);close.setBackground(ui.focusSurface(0xff343c48,100));
        header.addView(close,new LinearLayout.LayoutParams(px(33),px(33)));box.addView(header,new LinearLayout.LayoutParams(-1,px(42)));
        LinearLayout connection=new LinearLayout(activity);connection.setGravity(Gravity.CENTER_VERTICAL);LinearLayout steps=column();
        step(steps,"1",tr("连接同一个局域网","Connect to the same local network"),tr("电视可使用 Wi-Fi 或网线连接","Your TV can use Wi-Fi or Ethernet"),46);
        LinearLayout second=step(steps,"2",tr("手机扫码，或在电脑浏览器打开","Scan with your phone, or open on your computer"),null,84);
        address=ui.text(tr("正在开启上传…","Starting upload…"),15);address.setPadding(0,px(8),0,0);second.addView(address);
        code=ui.text("",12);code.setTextColor(WallpaperUi.MUTED);code.setPadding(0,px(6),0,0);second.addView(code);
        step(steps,"3",tr("选择图片，上传后在电视上应用","Upload a photo, then apply it on your TV"),tr("JPG / PNG / 静态 WebP  单张不超过 20 MB","JPG / PNG / static WebP  Up to 20 MB"),46);
        connection.addView(steps,new LinearLayout.LayoutParams(0,-2,1));View gap=new View(activity);connection.addView(gap,new LinearLayout.LayoutParams(px(35),1));
        FrameLayout qrBox=new FrameLayout(activity);qrBox.setBackground(ui.surface(Color.WHITE,15,Color.TRANSPARENT,0));qrBox.setPadding(px(13),px(13),px(13),px(13));
        qr=new ImageView(activity);qr.setContentDescription(tr("上传地址二维码","Upload address QR code"));qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qrBox.addView(qr,new FrameLayout.LayoutParams(-1,-1));connection.addView(qrBox,new LinearLayout.LayoutParams(px(170),px(170)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=px(24);box.addView(connection,cp);
        status=ui.text("",12);status.setTextColor(WallpaperUi.MUTED);status.setVisibility(View.GONE);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=px(16);box.addView(status,st);
        retry=ui.button(tr("重新连接","Reconnect"),false,this::start);retry.setVisibility(View.GONE);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);rp.topMargin=px(12);box.addView(retry,rp);
        close.setNextFocusDownId(retry.getId());retry.setNextFocusUpId(close.getId());
        FrameLayout.LayoutParams sheet=new FrameLayout.LayoutParams(px(770),-2,Gravity.CENTER);stage.addView(box,sheet);
        dialog.setContentView(stage);dialog.setOnDismissListener(d->{dialog=null;stopServer();background.setImageDrawable(null);this.backdrop.recycle();this.backdrop=null;Closed finished=this.callback;this.callback=null;if(finished!=null)finished.closed(lastImported);});
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setDimAmount(0);window.getDecorView().setSystemUiVisibility(5894);}
        dialog.show();if(window!=null)window.setLayout(-1,-1);close.requestFocus();start();
    }
    private void start(){
        stopServer();int attempt=++generation;status.setVisibility(View.GONE);retry.setVisibility(View.GONE);code.setText("");qr.setImageDrawable(null);host=lanAddress(activity);
        if(host==null){address.setText(tr("未找到局域网地址，请先连接 Wi-Fi 或网线","No LAN address. Connect Wi-Fi or Ethernet first."));retry.setVisibility(View.VISIBLE);return;}
        String selectedHost=host;address.setText(tr("正在开启上传…","Starting upload…"));
        worker.execute(()->{
            WallpaperUploadServer candidate=new WallpaperUploadServer(selectedHost,8765,name->activity.getAssets().open("wallpaper-upload/"+name),(input,length,name,active)->importImage(attempt,input,length,name,active));
            try{
                try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=new WallpaperUploadServer(selectedHost,0,name->activity.getAssets().open("wallpaper-upload/"+name),(input,length,name,active)->importImage(attempt,input,length,name,active));candidate.listen();}
                String url=candidate.address();BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,384,384,java.util.Collections.singletonMap(EncodeHintType.MARGIN,1));
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);WallpaperUploadServer ready=candidate;
                main.post(()->{if(dialog==null||attempt!=generation){ready.stop();bitmap.recycle();return;}server=ready;address.setText(ready.shortAddress());code.setText(tr("连接码  ","Connection code  ")+ready.connectionCode());qr.setImageBitmap(bitmap);main.postDelayed(check,2000);});
            }catch(IOException|WriterException e){candidate.stop();main.post(()->{if(dialog!=null&&attempt==generation){address.setText(tr("无法开启上传，请检查网络后重试","Unable to start upload. Check your network and retry."));retry.setVisibility(View.VISIBLE);}});}
        });
    }
    private String importImage(int attempt,InputStream input,long length,String name,WallpaperUploadServer.Active active) throws IOException {
        store.checkCapacity();File incoming=store.incoming(),prepared=store.prepared();
        try{
            try(FileOutputStream output=new FileOutputStream(incoming)){
                byte[] buffer=new byte[16384];long remaining=length;
                while(remaining>0){if(attempt!=generation || !active.valid())throw new IOException("上传会话已结束 / Session ended");int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)throw new IOException("图片传输未完成 / Incomplete upload");output.write(buffer,0,n);remaining-=n;}
            }
            int[] size=WallpaperImages.prepare(incoming,prepared);
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
