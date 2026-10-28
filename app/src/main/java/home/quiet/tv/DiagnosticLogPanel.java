package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.Collections;
import java.util.concurrent.*;

/** Export app events, with an explicit bounded capture for external settings pages. */
final class DiagnosticLogPanel extends ManagementPanel {
    private final AppManagementActivity owner;
    private final ExecutorService worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView status;
    private Button retry;
    private Button capture;
    private TextView captureStatus;
    private ImageView qr;
    private Bitmap bitmap;
    private DiagnosticDownloadServer server;
    private volatile DiagnosticDownloadServer preparing;
    private String host;
    private volatile int generation;
    private boolean registered;
    private boolean captureWasRunning;
    private final Runnable captureCheck=new Runnable(){@Override public void run(){
        if(!showing)return;boolean finished=captureWasRunning&&!SettingsPageCapture.running();updateCapture();if(finished)start();main.postDelayed(this,1000);
    }};
    private final BroadcastReceiver screen=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){stopServer();failure("下载链接已失效，请重新生成");}};
    private final Runnable check=new Runnable(){@Override public void run(){
        if(!showing||server==null)return;
        if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(owner))){stopServer();failure("下载链接已失效，请重试");}
        else main.postDelayed(this,2000);
    }};
    DiagnosticLogPanel(AppManagementActivity owner){super(owner);this.owner=owner;}
    @Override public void onCreate(Bundle state){
        pageRoot=owner.column();pageRoot.setGravity(Gravity.CENTER);owner.attachPanel(pageRoot);
        LinearLayout box=owner.column();box.setGravity(Gravity.CENTER);pageRoot.addView(box,new LinearLayout.LayoutParams(-1,-2));
        content=owner.column();content.setGravity(Gravity.CENTER);box.addView(content,new LinearLayout.LayoutParams(-1,owner.px(226)));
        captureStatus=owner.center("",11);captureStatus.setTextColor(0xffaab3c0);box.addView(captureStatus,new LinearLayout.LayoutParams(-1,owner.px(25)));
        LinearLayout actions=owner.row();actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,owner.px(36));ap.topMargin=owner.px(18);box.addView(actions,ap);
        retry=owner.button("重新生成",this::start);retry.setId(androidx.core.view.ViewCompat.generateViewId());retry.setFocusableInTouchMode(true);actions.addView(retry,new LinearLayout.LayoutParams(owner.px(110),-1));
        retry.setNextFocusUpId(managementTab().getId());
        retry.setOnKeyListener((view,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_UP)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)managementTab().requestFocus();return true;});
        capture=owner.button("采集设置页面",this::captureSettings);capture.setId(androidx.core.view.ViewCompat.generateViewId());capture.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(owner.px(175),-1);cp.leftMargin=owner.px(12);actions.addView(capture,cp);
        capture.setNextFocusUpId(managementTab().getId());capture.setNextFocusLeftId(retry.getId());retry.setNextFocusRightId(capture.getId());
        capture.setOnKeyListener((view,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_UP)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)managementTab().requestFocus();return true;});
    }
    @Override protected void onResume(){super.onResume();androidx.core.content.ContextCompat.registerReceiver(owner,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;main.post(captureCheck);start();}
    @Override protected void onPause(){super.onPause();main.removeCallbacks(captureCheck);stopServer();if(registered){owner.unregisterReceiver(screen);registered=false;}}
    @Override void focusManagementContent(){if(retry.isEnabled())retry.requestFocus();}
    @Override public void handleBack(){if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    private void updateCapture(){
        boolean active=SettingsPageCapture.running();captureWasRunning=active;capture.setText(active?"停止采集并生成":"采集设置页面");
        String summary=SettingsPageCapture.summary();int end=summary.indexOf('\n');
        captureStatus.setText(active?"正在采集，最多3分钟；复现后返回此处停止并生成日志":summary.startsWith("采集状态：")?summary.substring(5,end<0?summary.length():end):"需要排查系统网络页面时，选择“采集设置页面”");
    }
    private void captureSettings(){
        if(SettingsPageCapture.running()){SettingsPageCapture.stopCurrent("user_stop");updateCapture();start();return;}
        SettingsPageCapture.start(owner,()->{
            if(!showing || isDestroyed() || isFinishing()){SettingsPageCapture.stopCurrent("left_before_ready");return;}
            if(!new SystemSettingsRouter(owner).open(android.provider.Settings.ACTION_SETTINGS)){
                SettingsPageCapture.stopCurrent("settings_unavailable");message("系统设置入口不可用，请使用电视遥控器进入设置");
            }
        });updateCapture();
    }
    private void start(){
        stopServer();int attempt=++generation;retry.setText("重新生成");retry.setEnabled(false);content.removeAllViews();
        status=owner.center("正在生成日志…",14);status.setTextColor(0xffbcc4cf);content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        String address=WallpaperUploadPanel.lanAddress(owner);
        if(address==null){failure("未连接局域网，请连接 Wi-Fi 或网线后重试");return;}
        host=address;DiagnosticLog.info("diagnostic.export","requested");
        String adb=DiagnosticDeviceSnapshot.adb(owner);
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
                    if(!showing||attempt!=generation){image.recycle();return;}
                    server=ready;if(preparing==ready)preparing=null;bitmap=image;content.removeAllViews();
                    qr=new ImageView(owner);qr.setContentDescription("日志下载二维码");qr.setImageBitmap(image);qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(owner.px(6),owner.px(6),owner.px(6),owner.px(6));qr.setBackgroundDrawable(owner.surface(Color.WHITE,Color.WHITE,0,8));content.addView(qr,new LinearLayout.LayoutParams(owner.px(158),owner.px(158)));
                    TextView scan=owner.center("手机扫码下载日志",14);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=owner.px(15);content.addView(scan,sp);
                    TextView hint=owner.center("手机与电视需连接同一局域网",11);hint.setTextColor(0xffaab3c0);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=owner.px(6);content.addView(hint,hp);retry.setEnabled(true);main.postDelayed(check,2000);
                });
            }catch(Exception e){if(candidate!=null)candidate.stop();DiagnosticLog.error("diagnostic.export_failed",e);main.post(()->{if(showing&&attempt==generation)failure("日志生成失败，请重试");});}
        });
    }
    private void failure(String message){
        releaseImage();content.removeAllViews();status=owner.center(message,14);status.setTextColor(0xffc6cbd4);content.addView(status,new LinearLayout.LayoutParams(-1,-2));retry.setText("重试");retry.setEnabled(true);
    }
    private void releaseImage(){if(qr!=null){qr.setImageDrawable(null);qr=null;}if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    private void stopServer(){++generation;DiagnosticDownloadServer pending=preparing;preparing=null;if(pending!=null){pending.invalidate();worker.execute(pending::stop);}main.removeCallbacks(check);DiagnosticDownloadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}releaseImage();}
    @Override protected void onDestroy(){stopServer();main.removeCallbacks(captureCheck);main.removeCallbacks(check);worker.shutdown();super.onDestroy();}
}
