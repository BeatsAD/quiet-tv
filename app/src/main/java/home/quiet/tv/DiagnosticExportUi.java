package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

/** Export content can replace its caller's body without creating another dialog. */
final class DiagnosticExportUi {
    enum Page { LOGS, SPECIALTY, INPUTS }
    private static final int BLUETOOTH_PERMISSION=206;
    private final ManagementUi ui;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet sheet;
    private DiagnosticDownloadServer server;
    private volatile DiagnosticDownloadServer preparing;
    private volatile int generation;
    private Page page;
    private boolean busy,marking,destroyed,active;
    private String failure="",host="";
    private DesktopSettingsRow recordingRow;
    private final SharedPreferences.OnSharedPreferenceChangeListener recordingChanged=(prefs,key)->{
        if(recordingRow!=null)recordingRow.checked(DiagnosticLog.recording());
        if(sheet!=null&&page==Page.SPECIALTY)render();
    };
    private final Runnable recordingEnded=this::render;
    private Bitmap bitmap;
    private final Runnable check=new Runnable(){public void run(){if(sheet==null||server==null)return;if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui)))expire();else main.postDelayed(this,2000);}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(server!=null||busy&&!marking)expire();}};
    DiagnosticExportUi(ManagementUi ui){this.ui=ui;androidx.core.content.ContextCompat.registerReceiver(ui,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value,float size,float line,int color){TextView v=ui.label(value,12);QuickControlPreviewView.type(v,size,line,ui.unit,color);return v;}
    private void block(View view,float top,float bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);int previous=sheet.body.getChildCount()==0?0:((LinearLayout.LayoutParams)sheet.body.getChildAt(sheet.body.getChildCount()-1).getLayoutParams()).bottomMargin;lp.topMargin=Math.max(0,p(top)-previous);lp.bottomMargin=p(bottom);sheet.body.addView(view,lp);}
    private void copy(String value,boolean muted){block(text(value,muted?14.464f:17.28f,muted?23.8656f:29.376f,UiPalette.SECONDARY),muted?10.24f:17.28f,muted?19.2f:17.28f);}
    private Button button(String title,String tag,Runnable operation,boolean primary){Button v=ui.button(title,operation);v.setTag(tag);v.setId(androidx.core.view.ViewCompat.generateViewId());v.setFocusableInTouchMode(true);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);v.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));v.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE).outlineOnFocus());return v;}
    void show(ManagementUi.Sheet value){show(value,Page.LOGS);}
    void show(ManagementUi.Sheet value,Page destination){
        if(sheet!=value||page!=destination){
            hide();sheet=value;page=destination;active=true;
            DiagnosticLog.observeRecording(recordingChanged);
        }
        render();
    }
    private void render(){
        if(sheet==null||destroyed)return;
        View focused=sheet.box.findFocus();Object previous=focused==null?null:focused.getTag();
        sheet.heading.setText(page==Page.INPUTS?"信号源诊断包":page==Page.SPECIALTY?"专项排查":"诊断日志");
        sheet.body.removeAllViews();sheet.settingsActions(false);
        recordingRow=null;
        if(page==Page.LOGS){
            recordingRow=new DesktopSettingsRow(ui,"记录诊断日志","",false);
            recordingRow.hideIcon();recordingRow.setTag("diagnostic-recording");recordingRow.setFocusableInTouchMode(true);
            recordingRow.checked(DiagnosticLog.recording());
            recordingRow.setOnClickListener(v->DiagnosticLog.setRecording(ui,!DiagnosticLog.recording()));
            block(recordingRow,14.08f,0);recordingRow.getLayoutParams().height=ui.px(42);
        }else if(page==Page.SPECIALTY){
            specialty();
        }else if(!InputSourceObservation.hasEvidence(ui)){
            copy("暂无信号源配对或测试记录。",true);
        }
        if(busy){
            TextView loading=text(marking?"正在启动录制…":"正在生成…",17.28f,29.376f,UiPalette.TEXT);
            OperationFeedback.status(loading,OperationFeedback.Kind.PROGRESS);block(loading,17.28f,17.28f);
        }else if(server!=null&&bitmap!=null){
            transfer();
        }else if(page!=Page.SPECIALTY){
            boolean canDownload=page!=Page.INPUTS||InputSourceObservation.hasEvidence(ui);
            Button download=button(page==Page.INPUTS?"下载信号源诊断包":"下载日志","diagnostic-retry",()->start(false),true);
            download.setEnabled(canDownload);
            block(download,19.2f,0);
        }
        if(!failure.isEmpty()){
            TextView problem=text(failure,15.36f,23.04f,UiPalette.TEXT);OperationFeedback.status(problem,OperationFeedback.Kind.ERROR);
            block(problem,16,0);
        }
        if(previous!=null){
            View target=sheet.body.findViewWithTag(previous);
            if(target!=null&&target.isEnabled())target.requestFocusFromTouch();
        }
    }
    private void specialty(){
        boolean enabled=DiagnosticLog.recording();
        boolean recording=DiagnosticProbe.active();
        main.removeCallbacks(recordingEnded);
        if(recording){
            copy("正在录制，请复现问题。录制结束后从“诊断日志”下载。",true);
            main.postDelayed(recordingEnded,Math.max(1,DiagnosticProbe.remaining()));
        }else{
            copy(enabled?"开始录制后，在 30 秒内复现问题。完成后从“诊断日志”下载。":"请先在“诊断日志”中开启记录。",true);
        }
        Button mark=button("录制 30 秒","diagnostic-mark",this::mark,true);
        mark.setEnabled(enabled&&!recording&&!busy);block(mark,14.08f,10);
        if(Build.VERSION.SDK_INT>=31&&!BluetoothDiagnostics.permitted(ui)){
            Button permission=button("允许读取蓝牙状态","diagnostic-bluetooth",()->ui.requestPermissions(new String[]{BluetoothDiagnostics.CONNECT},BLUETOOTH_PERMISSION),false);
            permission.setEnabled(enabled&&!busy);block(permission,8,0);
        }
        if(server==null){
            Button bluetooth=button("下载蓝牙诊断","diagnostic-retry",()->start(true),false);
            bluetooth.setEnabled(enabled&&!busy);block(bluetooth,8,0);
        }
    }
    private void transfer(){
        LinearLayout transfer=ui.row();transfer.setGravity(Gravity.CENTER_VERTICAL);transfer.setPadding(p(17.92f),p(17.92f),p(17.92f),p(17.92f));
        transfer.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,9.6f));block(transfer,14.08f,19.2f);
        ImageView qr=new QrCodeView(ui,ui.unit);qr.setImageBitmap(bitmap);qr.setContentDescription("诊断日志下载二维码");qr.setPadding(p(5.12f),p(5.12f),p(5.12f),p(5.12f));
        transfer.addView(qr,new LinearLayout.LayoutParams(p(115.2f),p(115.2f)));
        LinearLayout details=ui.column();LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=p(25.6f);transfer.addView(details,dp);
        TextView title=text("手机扫码下载",19.2f,26.88f,UiPalette.TEXT);QuickControlPreviewView.weight(title,500);details.addView(title);
        TextView hint=text("手机与电视连接同一局域网",14.72f,25.024f,UiPalette.SECONDARY);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=hp.bottomMargin=p(14.72f);details.addView(hint,hp);
        details.addView(text("离开此页、息屏或网络变化后链接失效",12.16f,17.024f,UiPalette.SECONDARY));
    }
    private void mark(){
        if(busy||!DiagnosticLog.recording())return;
        int attempt=++generation;
        busy=marking=true;render();
        worker.execute(()->{
            boolean success;
            try{DiagnosticLog.markIncident();success=true;}
            catch(Exception e){DiagnosticLog.error("diagnostic.mark_failed",e);success=false;}
            boolean marked=success;
            main.post(()->{
                if(sheet==null||destroyed||attempt!=generation)return;
                busy=marking=false;render();
                if(!marked)ui.error(DiagnosticLog.recording()?"录制未开始，请重试":"诊断日志记录已关闭");
            });
        });
    }
    private void start(boolean bluetooth){
        stop();if(sheet==null||destroyed||!active)return;failure="";busy=true;render();String address=WallpaperUploadPanel.lanAddress(ui);if(address==null){fail("未连接局域网，请连接 Wi-Fi 或网线后重试");return;}host=address;
        boolean source=page==Page.INPUTS;
        String window=!source&&DiagnosticLog.recording()?DiagnosticDisplaySnapshot.window(ui):"未采集";
        int attempt=++generation;
        worker.execute(()->{DiagnosticDownloadServer candidate=null;Bitmap image=null;
            try{if(source&&InputSourceObservation.busy())throw new IOException("请在信号源记录结束后下载");if(attempt!=generation)return;File file=source?DiagnosticExports.inputs(ui):DiagnosticExports.create(ui,window,bluetooth);if(attempt!=generation)return;candidate=new DiagnosticDownloadServer(address,file,name->ui.getAssets().open("diagnostic-download/"+name),source);preparing=candidate;candidate.listen();if(attempt!=generation){candidate.stop();return;}
                BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,2));int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;image=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);DiagnosticDownloadServer ready=candidate;Bitmap qr=image;
                main.post(()->{if(destroyed||sheet==null||attempt!=generation){qr.recycle();return;}preparing=null;server=ready;bitmap=qr;busy=false;render();main.postDelayed(check,2000);});
            }catch(Exception unavailable){DiagnosticLog.error("diagnostic.export_failed",unavailable);if(image!=null)image.recycle();if(candidate!=null)candidate.stop();main.post(()->{if(sheet!=null&&attempt==generation)fail(source&&InputSourceObservation.busy()?"请在信号源记录结束后下载":"日志生成失败，请重试");});}
        });
    }
    private void fail(String value){busy=false;failure=value;render();}
    private void expire(){stop();fail("下载链接已失效，请重新生成");}
    private void stop(){++generation;busy=marking=false;main.removeCallbacks(check);DiagnosticDownloadServer pending=preparing;preparing=null;if(pending!=null){pending.invalidate();worker.execute(pending::stop);}DiagnosticDownloadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    void pause(){active=false;if(sheet!=null&&(server!=null||busy&&!marking))expire();}
    void resume(){active=true;if(sheet!=null)render();}
    void permissionResult(int request){if(request==BLUETOOTH_PERMISSION){BluetoothDiagnostics.init(ui);if(sheet!=null&&active)render();}}
    void hide(){
        if(sheet!=null)DiagnosticLog.forgetRecording(recordingChanged);
        main.removeCallbacks(recordingEnded);stop();sheet=null;recordingRow=null;failure="";
    }
    void destroy(){hide();destroyed=true;ui.unregisterReceiver(screen);worker.shutdown();}
}
