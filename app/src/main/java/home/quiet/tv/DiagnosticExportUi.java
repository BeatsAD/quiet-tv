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
    enum Page { LOGS, INPUTS }
    private static final int BLUETOOTH_PERMISSION=206;
    private final ManagementUi ui;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet sheet;
    private DiagnosticDownloadServer server;
    private volatile DiagnosticDownloadServer preparing;
    private volatile int generation;
    private Page page;
    private boolean busy,destroyed,active;
    private String failure="",host="";
    private DesktopSettingsRow recordingRow;
    private Button bluetoothPermission;
    private final SharedPreferences.OnSharedPreferenceChangeListener recordingChanged=(prefs,key)->{
        boolean recording=DiagnosticLog.recording();
        if(recordingRow!=null)recordingRow.checked(recording);
        updateBluetoothPermission(recording);
    };
    private Bitmap bitmap;
    private final Runnable check=new Runnable(){public void run(){if(sheet==null||server==null)return;if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui)))expire();else main.postDelayed(this,2000);}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(server!=null||busy)expire();}};
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
        sheet.heading.setText(page==Page.INPUTS?"信号源诊断包":"诊断日志");
        sheet.body.removeAllViews();sheet.settingsActions(false);
        recordingRow=null;bluetoothPermission=null;
        if(page==Page.LOGS){
            recordingRow=new DesktopSettingsRow(ui,"记录诊断日志","",false);
            recordingRow.hideIcon();recordingRow.setTag("diagnostic-recording");recordingRow.setFocusableInTouchMode(true);
            recordingRow.checked(DiagnosticLog.recording());
            recordingRow.setOnClickListener(v->DiagnosticLog.setRecording(ui,!DiagnosticLog.recording()));
            block(recordingRow,14.08f,0);recordingRow.getLayoutParams().height=ui.px(42);
        }else if(!InputSourceObservation.hasEvidence(ui)){
            copy("暂无信号源配对或测试记录。",true);
        }
        Button download=null;
        if(busy){
            TextView loading=text("正在生成…",17.28f,29.376f,UiPalette.TEXT);
            OperationFeedback.status(loading,OperationFeedback.Kind.PROGRESS);block(loading,17.28f,17.28f);
        }else if(server!=null&&bitmap!=null){
            transfer();
        }else{
            boolean canDownload=page!=Page.INPUTS||InputSourceObservation.hasEvidence(ui);
            download=button(page==Page.INPUTS?"下载信号源诊断包":"下载日志","diagnostic-retry",this::start,true);
            download.setEnabled(canDownload);
            if(page==Page.INPUTS)block(download,19.2f,0);
        }
        if(!failure.isEmpty()){
            TextView problem=text(failure,15.36f,23.04f,UiPalette.TEXT);OperationFeedback.status(problem,OperationFeedback.Kind.ERROR);
            block(problem,16,0);
        }
        if(page==Page.LOGS)logActions(download);
        if(previous!=null){
            View target=sheet.body.findViewWithTag(previous);
            if((target==null||!target.isShown())&&"diagnostic-bluetooth".equals(previous))target=download;
            if(target!=null&&target.isEnabled()&&target.isShown())target.requestFocusFromTouch();
        }
    }
    private void logActions(Button download){
        LinearLayout actions=ui.row();
        actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        if(Build.VERSION.SDK_INT>=31&&!BluetoothDiagnostics.permitted(ui)){
            bluetoothPermission=button("允许读取蓝牙状态","diagnostic-bluetooth",
                ()->ui.requestPermissions(new String[]{BluetoothDiagnostics.CONNECT},BLUETOOTH_PERMISSION),false);
            bluetoothPermission.setEnabled(!busy);
            bluetoothPermission.setNextFocusUpId(recordingRow.getId());
            actions.addView(bluetoothPermission,new LinearLayout.LayoutParams(-2,-2));
        }
        if(download!=null){
            LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(-2,-2);
            if(bluetoothPermission!=null){
                margins.leftMargin=p(12);
                bluetoothPermission.setNextFocusRightId(download.getId());
            }
            download.setNextFocusUpId(recordingRow.getId());
            recordingRow.setNextFocusDownId(download.getId());
            actions.addView(download,margins);
        }
        if(actions.getChildCount()>0)block(actions,19.2f,0);
        updateBluetoothPermission(DiagnosticLog.recording());
    }
    private void updateBluetoothPermission(boolean recording){
        if(bluetoothPermission==null)return;
        LinearLayout actions=(LinearLayout)bluetoothPermission.getParent();
        View download=actions.findViewWithTag("diagnostic-retry");
        if(download!=null){
            download.setNextFocusLeftId(recording?bluetoothPermission.getId():View.NO_ID);
            if(!recording&&bluetoothPermission.isFocused())download.requestFocusFromTouch();
        }
        bluetoothPermission.setVisibility(recording?View.VISIBLE:View.GONE);
        actions.setVisibility(recording||download!=null?View.VISIBLE:View.GONE);
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
    private void start(){
        stop();if(sheet==null||destroyed||!active)return;failure="";busy=true;render();String address=WallpaperUploadPanel.lanAddress(ui);if(address==null){fail("未连接局域网，请连接 Wi-Fi 或网线后重试");return;}host=address;
        boolean source=page==Page.INPUTS;
        String window=!source&&DiagnosticLog.recording()?DiagnosticDisplaySnapshot.window(ui):"未采集";
        int attempt=++generation;
        worker.execute(()->{DiagnosticDownloadServer candidate=null;Bitmap image=null;
            try{if(source&&InputSourceObservation.busy())throw new IOException("请在信号源记录结束后下载");if(attempt!=generation)return;File file=source?DiagnosticExports.inputs(ui):DiagnosticExports.create(ui,window);if(attempt!=generation)return;candidate=new DiagnosticDownloadServer(address,file,name->ui.getAssets().open("diagnostic-download/"+name),source);preparing=candidate;candidate.listen();if(attempt!=generation){candidate.stop();return;}
                BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,2));int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;image=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);DiagnosticDownloadServer ready=candidate;Bitmap qr=image;
                main.post(()->{if(destroyed||sheet==null||attempt!=generation){qr.recycle();return;}preparing=null;server=ready;bitmap=qr;busy=false;render();main.postDelayed(check,2000);});
            }catch(Exception unavailable){DiagnosticLog.error("diagnostic.export_failed",unavailable);if(image!=null)image.recycle();if(candidate!=null)candidate.stop();main.post(()->{if(sheet!=null&&attempt==generation)fail(source&&InputSourceObservation.busy()?"请在信号源记录结束后下载":"日志生成失败，请重试");});}
        });
    }
    private void fail(String value){busy=false;failure=value;render();}
    private void expire(){stop();fail("下载链接已失效，请重新生成");}
    private void stop(){++generation;busy=false;main.removeCallbacks(check);DiagnosticDownloadServer pending=preparing;preparing=null;if(pending!=null){pending.invalidate();worker.execute(pending::stop);}DiagnosticDownloadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    void pause(){active=false;if(sheet!=null&&(server!=null||busy))expire();}
    void resume(){active=true;if(sheet!=null)render();}
    void permissionResult(int request){if(request==BLUETOOTH_PERMISSION){BluetoothDiagnostics.init(ui);if(sheet!=null&&active)render();}}
    void hide(){
        if(sheet!=null)DiagnosticLog.forgetRecording(recordingChanged);
        stop();sheet=null;recordingRow=null;bluetoothPermission=null;failure="";
    }
    void destroy(){hide();destroyed=true;ui.unregisterReceiver(screen);worker.shutdown();}
}
