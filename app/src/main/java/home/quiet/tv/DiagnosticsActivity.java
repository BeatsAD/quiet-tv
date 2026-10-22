package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.concurrent.*;

/** Three remote-friendly steps, with optional on-device ADB and local ZIP export. */
public final class DiagnosticsActivity extends ManagementUi {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private DiagnosticsRecorder recorder;
    private AdbConnectionPanel connection;
    private DiagnosticsDownloadServer server;
    private Sheet downloadSheet;
    private File zip;
    private TextView status;
    private boolean busy,active;
    private final Runnable expiry=()->{closeDownload();if(active)render();};
    @Override public void onCreate(Bundle state){super.onCreate(state);recorder=DiagnosticsRecorder.get(this);connection=new AdbConnectionPanel(this,recorder.adb,worker);}
    @Override protected void onResume(){super.onResume();active=true;render();}
    @Override protected void onStop(){active=false;connection.close();closeDownload();if(!recorder.recording())recorder.adb.disconnect();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    private void render(){
        while(root.getChildCount()>1)root.removeViewAt(1);((TextView)header.getChildAt(1)).setText("诊断日志");
        gap(root,18);status=label(busy?"正在采集，请稍候…":recorder.summary(),18);root.addView(status);gap(root,10);
        TextView guide=muted("开始记录后，打开系统设置并按实体 HOME。观察返回哪个桌面，再回到这里导出。",13);root.addView(guide);gap(root,8);
        root.addView(muted("未连接本机 ADB 时系统日志受限。记录最多五分钟；进程结束或重启会中断。",12));gap(root,18);
        LinearLayout actions=row();root.addView(actions,new LinearLayout.LayoutParams(-1,px(42)));
        Button start=button("开始记录",()->work(()->{zip=null;recorder.start();},"已开始记录，请按实体 HOME 复现"));start.setTag("diagnostics-start");actions.addView(start,new LinearLayout.LayoutParams(-2,-1));start.setEnabled(!busy && !recorder.recording());
        Button settings=button("打开系统设置",()->{if(!new SystemSettingsRouter(this).open(Settings.ACTION_SETTINGS))message("未找到设置入口，请使用遥控器打开设置。");});settings.setTag("diagnostics-settings");marginAdd(actions,settings,-2,-1,10);settings.setEnabled(!busy);
        Button export=button("停止并导出",()->work(()->{zip=recorder.export();recorder.adb.disconnect();},"诊断包已生成"));export.setTag("diagnostics-export");marginAdd(actions,export,-2,-1,10);export.setEnabled(!busy && recorder.directory()!=null);
        gap(root,14);LinearLayout options=row();root.addView(options,new LinearLayout.LayoutParams(-1,px(38)));
        Button adb=button(recorder.adb.connected()?"本机 ADB 已连接":"连接本机 ADB（可选）",()->connection.show(()->{render();feedback("已连接，下次开始记录可采集系统 Logcat");}));options.addView(adb,new LinearLayout.LayoutParams(-2,-1));adb.setEnabled(!busy && !recorder.recording());
        Button observed=button("记录 HOME 结果",()->{Sheet sheet=new Sheet("实体遥控器 HOME 的结果");for(String result:new String[]{"回到 Quiet TV","回到原桌面","其他或无反应"})sheet.option(result,()->{sheet.dialog.dismiss();work(()->{recorder.observe(result);zip=null;},"已记录用户反馈，请导出诊断包");});sheet.show(null);});observed.setTag("diagnostics-observe");marginAdd(options,observed,-2,-1,10);observed.setEnabled(!busy && recorder.directory()!=null);
        gap(root,14);root.addView(muted("诊断包包含设备、应用及日志信息。常见标识已尽力脱敏，请检查后私下发给开发者。",12));
        if(zip!=null && zip.isFile()){
            gap(root,12);LinearLayout downloads=row();root.addView(downloads,new LinearLayout.LayoutParams(-1,px(38)));
            Button download=button("手机扫码下载",this::download);download.setTag("diagnostics-download");downloads.addView(download,new LinearLayout.LayoutParams(-2,-1));
            Button save=button("另存 ZIP",this::save);marginAdd(downloads,save,-2,-1,10);
            if(server!=null){gap(root,8);root.addView(muted("下载入口十分钟有效，离开本页即关闭。",12));}
        }
        (recorder.recording()?export:start).requestFocus();
    }
    interface Task {void run() throws Exception;}
    private void work(Task task,String success){
        if(busy)return;closeDownload();busy=true;render();
        worker.execute(()->{String result=success;try{task.run();}catch(Exception failure){result="未完成："+failure.getMessage();}String message=result;runOnUiThread(()->{busy=false;if(active && !isDestroyed()){render();feedback(message);}});});
    }
    private void save(){
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"QuietTV-diagnostics.zip"),41);}
        catch(ActivityNotFoundException failure){message("电视没有文件保存入口，请使用手机扫码下载。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=41 || result!=RESULT_OK || data==null || data.getData()==null || zip==null)return;
        android.net.Uri target=data.getData();File source=zip;work(()->{try(InputStream input=new FileInputStream(source);OutputStream output=getContentResolver().openOutputStream(target)){if(output==null)throw new IOException("无法写入所选位置");byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);}},"ZIP 已保存");
    }
    private void download(){
        closeDownload();
        try{
            String host=WallpaperUploadPanel.lanAddress(this);
            if(host==null)throw new IOException("未找到局域网地址，请先连接 Wi-Fi 或有线网络");
            server=new DiagnosticsDownloadServer(host,zip);server.start(5000,true);main.postDelayed(expiry,DiagnosticsDownloadServer.LIFETIME_MS);
            String address=server.address();BitMatrix code=new QRCodeWriter().encode(address,BarcodeFormat.QR_CODE,320,320);Bitmap bitmap=Bitmap.createBitmap(320,320,Bitmap.Config.RGB_565);
            for(int y=0;y<320;y++)for(int x=0;x<320;x++)bitmap.setPixel(x,y,code.get(x,y)?Color.BLACK:Color.WHITE);
            Sheet sheet=new Sheet("下载诊断包");downloadSheet=sheet;ImageView qr=new ImageView(this);qr.setImageBitmap(bitmap);qr.setContentDescription("诊断包下载二维码");sheet.body.addView(qr,new LinearLayout.LayoutParams(-1,px(180)));sheet.text("手机与电视连接同一局域网后扫码。十分钟有效，关闭即停止。");TextView url=muted(address,10);url.setSingleLine(false);sheet.body.addView(url);
            Button close=sheet.action("关闭",sheet.dialog::dismiss);sheet.dialog.setOnDismissListener(value->{closeDownload();bitmap.recycle();});sheet.show(close);
        }catch(Exception failure){closeDownload();message("下载入口未开启："+failure.getMessage());}
    }
    private void closeDownload(){main.removeCallbacks(expiry);DiagnosticsDownloadServer previous=server;server=null;if(previous!=null)previous.stop();Sheet sheet=downloadSheet;downloadSheet=null;if(sheet!=null && sheet.dialog.isShowing())sheet.dialog.dismiss();}
}
