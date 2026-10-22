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
    private TextView address,status;
    private ImageView qr;
    private Button retry;
    private volatile int generation;
    private String host,lastImported;
    private Closed callback;
    private final Runnable check=new Runnable(){@Override public void run(){
        if(dialog==null || server==null)return;
        if(server.expired() || !host.equals(lanAddress(activity))){
            status.setText(tr("会话已结束，请重新连接","Session ended. Reconnect to upload."));retry.setVisibility(View.VISIBLE);stopServer();
        } else main.postDelayed(this,2000);
    }};
    WallpaperUploadPanel(Activity activity,WallpaperStore store,float unit,boolean chinese){this.activity=activity;this.store=store;this.unit=unit;this.chinese=chinese;}
    private int px(int n){return Math.round(n*unit);}
    private String tr(String zh,String en){return chinese?zh:en;}
    private TextView text(String value,int size){TextView view=new TextView(activity);view.setText(value);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));view.setTextColor(0xffeff1f5);return view;}
    private Button button(String value,Runnable action){Button b=new Button(activity);b.setText(value);b.setAllCaps(false);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(13));b.setOnClickListener(v->action.run());return b;}
    void show(Closed callback){
        if(dialog!=null)return;this.callback=callback;lastImported=null;
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(px(32),px(28),px(32),px(24));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff222831);bg.setCornerRadius(px(20));box.setBackground(bg);
        box.addView(text(tr("把喜欢的风景，留在电视上","Keep your favorite view on TV"),24));
        TextView steps=text(tr("1  手机或电脑与电视连接同一局域网\n2  扫描二维码，或打开下方完整地址\n3  选择图片上传，再在电视确认使用","1  Connect your phone or computer to the TV’s LAN\n2  Scan the QR code or open the full address below\n3  Upload a photo, then select it on your TV"),13);
        steps.setLineSpacing(px(6),1);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=px(22);box.addView(steps,sp);
        LinearLayout connection=new LinearLayout(activity);connection.setGravity(Gravity.CENTER_VERTICAL);
        address=text(tr("正在开启上传…","Starting upload…"),14);address.setTextIsSelectable(true);
        connection.addView(address,new LinearLayout.LayoutParams(0,-2,1));
        qr=new ImageView(activity);qr.setContentDescription(tr("上传地址二维码","Upload address QR code"));qr.setBackgroundColor(Color.WHITE);qr.setPadding(px(8),px(8),px(8),px(8));connection.addView(qr,new LinearLayout.LayoutParams(px(146),px(146)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=px(18);box.addView(connection,cp);
        status=text(tr("JPG / PNG / 静态 WebP  单张 ≤ 20 MB  退出后关闭上传","JPG / PNG / static WebP  Up to 20 MB  Closes when you leave"),12);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=px(17);box.addView(status,st);
        LinearLayout buttons=new LinearLayout(activity);buttons.setGravity(Gravity.END);
        retry=button(tr("重新连接","Reconnect"),this::start);retry.setVisibility(View.GONE);buttons.addView(retry);
        Button close=button(tr("关闭","Close"),this::close);buttons.addView(close);box.addView(buttons);
        dialog.setContentView(box);dialog.setOnDismissListener(d->{dialog=null;stopServer();Closed finished=this.callback;this.callback=null;if(finished!=null)finished.closed(lastImported);});
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.65f);window.getDecorView().setSystemUiVisibility(5894);}
        dialog.show();if(window!=null)window.setLayout(px(750),-2);close.requestFocus();start();
    }
    private void start(){
        stopServer();int attempt=++generation;retry.setVisibility(View.GONE);qr.setImageDrawable(null);host=lanAddress(activity);
        if(host==null){address.setText(tr("未找到局域网地址，请先连接 Wi-Fi 或网线","No LAN address. Connect Wi-Fi or Ethernet first."));retry.setVisibility(View.VISIBLE);return;}
        String selectedHost=host;address.setText(tr("正在开启上传…","Starting upload…"));
        worker.execute(()->{
            WallpaperUploadServer candidate=new WallpaperUploadServer(selectedHost,8765,name->activity.getAssets().open("wallpaper-upload/"+name),(input,length,name,active)->importImage(attempt,input,length,name,active));
            try{
                try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=new WallpaperUploadServer(selectedHost,0,name->activity.getAssets().open("wallpaper-upload/"+name),(input,length,name,active)->importImage(attempt,input,length,name,active));candidate.listen();}
                String url=candidate.address();BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,384,384);
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap bitmap=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);WallpaperUploadServer ready=candidate;
                main.post(()->{if(dialog==null||attempt!=generation){ready.stop();bitmap.recycle();return;}server=ready;address.setText(url);qr.setImageBitmap(bitmap);status.setText(tr("上传已开启  空闲 10 分钟后结束  单张 ≤ 20 MB","Upload ready  Expires after 10 idle minutes  Up to 20 MB"));main.postDelayed(check,2000);});
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
            int[] size=WallpaperImages.prepare(incoming,prepared,WallpaperImages.importLimit(activity));
            if(attempt!=generation || !active.valid())throw new IOException("上传会话已结束 / Session ended");
            WallpaperCatalog.Entry entry=store.add(prepared,name,size[0],size[1]);
            main.post(()->{if(dialog!=null&&attempt==generation){lastImported=entry.id;status.setText(tr("已收到图片，请按返回后选择使用","Photo received. Press Back, then select it to apply."));}});
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
