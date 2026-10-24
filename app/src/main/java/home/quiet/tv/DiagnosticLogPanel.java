package home.quiet.tv;

import android.app.Dialog;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.Collections;
import java.util.concurrent.*;

/** The only user operation is exporting; recording belongs to the application process. */
final class DiagnosticLogPanel {
    private final ManagementUi owner;
    private final ExecutorService worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final Handler main=new Handler(Looper.getMainLooper());
    private Dialog dialog;
    private LinearLayout content;
    private TextView status;
    private Button retry,close;
    private ImageView qr;
    private Bitmap bitmap;
    private DiagnosticDownloadServer server;
    private volatile DiagnosticDownloadServer preparing;
    private String host;
    private volatile int generation;
    private boolean registered;
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){close();}};
    private final Runnable check=new Runnable(){@Override public void run(){
        if(dialog==null||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(owner))){stopServer();failure("下载链接已失效，请重试");}
        else main.postDelayed(this,2000);
    }};
    DiagnosticLogPanel(ManagementUi owner){this.owner=owner;}
    void show(){
        if(dialog!=null)return;
        dialog=new Dialog(owner);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=owner.column();box.setPadding(owner.px(26),owner.px(24),owner.px(26),owner.px(24));box.setBackground(owner.surface(0xff25292f,0xff25292f,0x28ffffff,16));
        box.addView(owner.center("诊断日志",21),new LinearLayout.LayoutParams(-1,owner.px(28)));
        content=owner.column();content.setGravity(Gravity.CENTER);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,owner.px(226));cp.topMargin=owner.px(18);box.addView(content,cp);
        LinearLayout actions=owner.row();actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,owner.px(36));ap.topMargin=owner.px(18);box.addView(actions,ap);
        retry=owner.button("重试",this::start);retry.setId(View.generateViewId());retry.setVisibility(View.GONE);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(owner.px(92),-1);rp.rightMargin=owner.px(14);actions.addView(retry,rp);
        close=owner.button("关闭",this::close);close.setId(View.generateViewId());close.setFocusableInTouchMode(true);actions.addView(close,new LinearLayout.LayoutParams(owner.px(92),-1));
        close.setNextFocusLeftId(retry.getId());retry.setNextFocusRightId(close.getId());
        dialog.setContentView(box);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.7f);
        dialog.setOnDismissListener(d->{dialog=null;stopServer();if(registered){owner.unregisterReceiver(screen);registered=false;}owner.diagnosticClosed();});
        androidx.core.content.ContextCompat.registerReceiver(owner,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;
        dialog.show();window.setLayout(owner.px(360),-2);window.getDecorView().setSystemUiVisibility(5894);close.requestFocus();start();
    }
    private void start(){
        stopServer();int attempt=++generation;retry.setVisibility(View.GONE);content.removeAllViews();
        status=owner.center("正在生成日志…",14);status.setTextColor(0xffbcc4cf);content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        String address=WallpaperUploadPanel.lanAddress(owner);
        if(address==null){failure("未连接局域网，请连接 Wi-Fi 或网线后重试");return;}
        host=address;DiagnosticLog.info("diagnostic.export","requested");
        String adb=DiagnosticDeviceSnapshot.adb(owner,owner.managementSession);
        worker.execute(()->{
            DiagnosticDownloadServer candidate=null;
            try{
                File file=DiagnosticLog.export(owner,DiagnosticDeviceSnapshot.collect(owner,adb));
                if(attempt!=generation)return;
                candidate=new DiagnosticDownloadServer(address,file,name->owner.getAssets().open("diagnostic-download/"+name));preparing=candidate;candidate.listen();
                if(attempt!=generation){candidate.stop();return;}
                BitMatrix matrix=new QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,2));
                int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                Bitmap image=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);DiagnosticDownloadServer ready=candidate;
                main.post(()->{
                    if(dialog==null||attempt!=generation){image.recycle();return;}
                    server=ready;if(preparing==ready)preparing=null;bitmap=image;content.removeAllViews();
                    qr=new ImageView(owner);qr.setContentDescription("日志下载二维码");qr.setImageBitmap(image);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(owner.px(6),owner.px(6),owner.px(6),owner.px(6));qr.setBackground(owner.surface(Color.WHITE,Color.WHITE,0,8));content.addView(qr,new LinearLayout.LayoutParams(owner.px(158),owner.px(158)));
                    TextView scan=owner.center("手机扫码下载日志",14);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=owner.px(15);content.addView(scan,sp);
                    TextView hint=owner.center("手机与电视需连接同一局域网",11);hint.setTextColor(0xffaab3c0);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=owner.px(6);content.addView(hint,hp);main.postDelayed(check,2000);
                });
            }catch(Exception e){if(candidate!=null)candidate.stop();DiagnosticLog.error("diagnostic.export_failed",e);main.post(()->{if(dialog!=null&&attempt==generation)failure("日志生成失败，请重试");});}
        });
    }
    private void failure(String message){
        releaseImage();content.removeAllViews();status=owner.center(message,14);status.setTextColor(0xffc6cbd4);content.addView(status,new LinearLayout.LayoutParams(-1,-2));retry.setVisibility(View.VISIBLE);close.requestFocus();
    }
    private void releaseImage(){if(qr!=null){qr.setImageDrawable(null);qr=null;}if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    private void stopServer(){++generation;DiagnosticDownloadServer pending=preparing;preparing=null;if(pending!=null){pending.invalidate();worker.execute(pending::stop);}main.removeCallbacks(check);DiagnosticDownloadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}releaseImage();}
    void close(){if(dialog!=null)dialog.dismiss();}
    void destroy(){close();main.removeCallbacks(check);worker.shutdown();}
}
