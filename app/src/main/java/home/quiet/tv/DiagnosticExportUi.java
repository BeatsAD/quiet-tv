package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;

/** Export content can replace its caller's body without creating another dialog. */
final class DiagnosticExportUi {
    private static final int BLUETOOTH_PERMISSION=206;
    private final ManagementUi ui;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet sheet;
    private DiagnosticDownloadServer server;
    private volatile DiagnosticDownloadServer preparing;
    private volatile int generation;
    private boolean inputs,busy,destroyed,active;
    private String failure="",host="",notice="";
    private Bitmap bitmap;
    private final Runnable check=new Runnable(){public void run(){if(sheet==null||server==null)return;if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui)))expire();else main.postDelayed(this,2000);}};
    private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){if(sheet!=null)expire();}};
    DiagnosticExportUi(ManagementUi ui){this.ui=ui;androidx.core.content.ContextCompat.registerReceiver(ui,screen,new IntentFilter(Intent.ACTION_SCREEN_OFF),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value,float size,float line,int color){TextView v=ui.label(value,12);QuickControlPreviewView.type(v,size,line,ui.unit,color);return v;}
    private void block(View view,float top,float bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);int previous=sheet.body.getChildCount()==0?0:((LinearLayout.LayoutParams)sheet.body.getChildAt(sheet.body.getChildCount()-1).getLayoutParams()).bottomMargin;lp.topMargin=Math.max(0,p(top)-previous);lp.bottomMargin=p(bottom);sheet.body.addView(view,lp);}
    private void copy(String value,boolean muted){block(text(value,muted?14.464f:17.28f,muted?23.8656f:29.376f,UiPalette.SECONDARY),muted?10.24f:17.28f,muted?19.2f:17.28f);}
    private Button button(String title,String tag,Runnable operation,boolean primary){Button v=ui.button(title,operation);v.setTag(tag);v.setId(androidx.core.view.ViewCompat.generateViewId());v.setFocusableInTouchMode(true);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);v.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));v.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE,false,12));return v;}
    void show(ManagementUi.Sheet value){show(value,false);}
    void show(ManagementUi.Sheet value,boolean source){if(sheet==value)return;hide();sheet=value;inputs=source;active=true;start();}
    private void render(){
        if(sheet==null||destroyed)return;sheet.heading.setText("导出诊断");sheet.body.removeAllViews();sheet.settingsActions(false);
        if(busy){TextView loading=text("正在生成诊断包…",17.28f,29.376f,UiPalette.TEXT);OperationFeedback.status(loading,OperationFeedback.Kind.PROGRESS);block(loading,17.28f,17.28f);return;}
        copy(inputs?"导出本次信号源配对与测试记录。":"导出应用日志与设备信息，后台记录无需手动开启。",true);
        if(server!=null&&bitmap!=null){LinearLayout transfer=ui.row();transfer.setGravity(Gravity.CENTER_VERTICAL);transfer.setPadding(p(17.92f),p(17.92f),p(17.92f),p(17.92f));transfer.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,9.6f));block(transfer,14.08f,19.2f);
            ImageView qr=new QrCodeView(ui,ui.unit);qr.setImageBitmap(bitmap);qr.setContentDescription("诊断包下载二维码");qr.setPadding(p(5.12f),p(5.12f),p(5.12f),p(5.12f));transfer.addView(qr,new LinearLayout.LayoutParams(p(115.2f),p(115.2f)));
            LinearLayout details=ui.column();LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=p(25.6f);transfer.addView(details,dp);TextView title=text("手机扫码下载诊断包",19.2f,26.88f,UiPalette.TEXT);QuickControlPreviewView.weight(title,500);details.addView(title);TextView hint=text("手机与电视连接同一局域网",14.72f,25.024f,UiPalette.SECONDARY);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=hp.bottomMargin=p(14.72f);details.addView(hint,hp);details.addView(text("离开此页、息屏或网络变化后链接失效",12.16f,17.024f,UiPalette.SECONDARY));
        }else copy("诊断包尚未生成。",false);
        copy("下载后可选择附在反馈中，不会自动发送。备忘录正文和传输文件不包含在内。",true);
        LinearLayout actions=ui.row();actions.setGravity(Gravity.END);actions.setClipChildren(false);actions.setClipToPadding(false);block(actions,24,0);actions.getLayoutParams().height=p(46.08f);
        actions.addView(button(server==null?"重试":"重新生成","diagnostic-retry",this::start,true),new LinearLayout.LayoutParams(-2,-1));
        if(!inputs){Button mark=button("标记刚才的问题","diagnostic-mark",this::mark,false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-1);mp.leftMargin=p(8);actions.addView(mark,mp);}
        if(!inputs&&Build.VERSION.SDK_INT>=31&&!BluetoothDiagnostics.permitted(ui)){Button permission=button("允许读取蓝牙状态","diagnostic-bluetooth",()->ui.requestPermissions(new String[]{BluetoothDiagnostics.CONNECT},BLUETOOTH_PERMISSION),false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-1);bp.leftMargin=p(8);actions.addView(permission,bp);}
        if(!notice.isEmpty())copy(notice,true);
        if(inputs||InputSourceObservation.hasEvidence(ui)){Button other=button(inputs?"应用诊断包":"信号源诊断包","diagnostic-kind",()->{inputs=!inputs;start();},false);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-2,-1);op.leftMargin=p(8);actions.addView(other,op);}
        if(!failure.isEmpty()){TextView problem=text(failure,15.36f,23.04f,UiPalette.TEXT);OperationFeedback.status(problem,OperationFeedback.Kind.ERROR);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=p(16);sheet.body.addView(problem,fp);}
    }
    private void mark(){
        if(busy)return;busy=true;render();worker.execute(()->{String result;boolean success;try{DiagnosticLog.markIncident();success=true;result="已标记，继续记录30秒；随后重新生成诊断包";}catch(Exception e){DiagnosticLog.error("diagnostic.mark_failed",e);success=false;result="标记失败，请重试";}String message=result;boolean marked=success;main.post(()->{if(sheet==null||destroyed)return;busy=false;notice="";render();if(marked)ui.success(message);else ui.error(message);});});
    }
    private void start(){
        stop();if(sheet==null||destroyed||!active)return;failure="";busy=true;render();String address=WallpaperUploadPanel.lanAddress(ui);if(address==null){fail("未连接局域网，请连接 Wi-Fi 或网线后重试");return;}host=address;
        SettingsPageCapture.stopCurrent("export");InputSourceObservation.stop("export");String adb=DiagnosticDeviceSnapshot.adb(ui),window=DiagnosticDisplaySnapshot.window(ui);boolean source=inputs;int attempt=++generation;
        worker.execute(()->{DiagnosticDownloadServer candidate=null;Bitmap image=null;
            try{while(source&&InputSourceObservation.busy()){if(attempt!=generation)return;Thread.sleep(50);}if(attempt!=generation)return;File file=source?InputSourceObservation.export(ui):DiagnosticExports.create(ui,adb,window);if(attempt!=generation)return;candidate=new DiagnosticDownloadServer(address,file,name->ui.getAssets().open("diagnostic-download/"+name),source);preparing=candidate;candidate.listen();if(attempt!=generation){candidate.stop();return;}
                BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(candidate.address(),BarcodeFormat.QR_CODE,384,384,Collections.singletonMap(EncodeHintType.MARGIN,2));int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;image=Bitmap.createBitmap(pixels,384,384,Bitmap.Config.ARGB_8888);DiagnosticDownloadServer ready=candidate;Bitmap qr=image;
                main.post(()->{if(destroyed||sheet==null||attempt!=generation){qr.recycle();return;}preparing=null;server=ready;bitmap=qr;busy=false;render();main.postDelayed(check,2000);});
            }catch(Exception unavailable){DiagnosticLog.error("diagnostic.export_failed",unavailable);if(image!=null)image.recycle();if(candidate!=null)candidate.stop();main.post(()->{if(sheet!=null&&attempt==generation)fail("诊断包生成失败，请重试");});}
        });
    }
    private void fail(String value){busy=false;failure=value;render();}
    private void expire(){stop();fail("下载链接已失效，请重新生成");}
    private void stop(){++generation;busy=false;main.removeCallbacks(check);DiagnosticDownloadServer pending=preparing;preparing=null;if(pending!=null){pending.invalidate();worker.execute(pending::stop);}DiagnosticDownloadServer old=server;server=null;if(old!=null){old.invalidate();worker.execute(old::stop);}if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    void pause(){active=false;if(sheet!=null)expire();}
    void resume(){active=true;if(sheet!=null)render();}
    void permissionResult(int request){if(request==BLUETOOTH_PERMISSION){BluetoothDiagnostics.init(ui);if(sheet!=null&&active)start();}}
    void hide(){stop();sheet=null;failure="";notice="";inputs=false;}
    void destroy(){hide();destroyed=true;ui.unregisterReceiver(screen);worker.shutdown();}
}
