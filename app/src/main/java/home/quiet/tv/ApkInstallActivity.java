package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.concurrent.*;

/** User-selected APK review and system confirmation, independent of signed Quiet TV updates. */
public final class ApkInstallActivity extends Activity {
    private static final int INSTALL=7410,PERMISSION=7411;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private boolean resumed,waiting,viewRoute,fallback;
    private String fileId;
    private ApkPackage apk;
    private File prepared;
    private long baseline=-1;
    private TextView details,status;
    private Button install;
    private float unit;
    private int px(int value){return Math.round(value*unit);}
    private boolean sessions(){return UpdateInstallPolicy.session(Build.VERSION.SDK_INT);}
    private void style(Button button){button.setTextColor(ManagementUi.TEXT);button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(14));button.setAllCaps(false);button.setBackgroundDrawable(ControlStyle.action(unit,24,0xff24262c,0x22ffffff));}
    @Override public boolean dispatchKeyEvent(KeyEvent key){return KeyFeedback.dispatch(getWindow().getDecorView(),key,()->super.dispatchKeyEvent(key));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);unit=getResources().getDisplayMetrics().widthPixels/960f;
        getWindow().getDecorView().setSystemUiVisibility(5894);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);getWindow().setDimAmount(.65f);
        FrameLayout root=new FrameLayout(this);setContentView(root);WallpaperUi ui=new WallpaperUi(this,unit);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(px(24),px(22),px(24),px(22));card.setBackgroundDrawable(ui.surface(0xff202126,22,0x38ffffff,1));root.addView(card,new FrameLayout.LayoutParams(px(560),-2,Gravity.CENTER));
        card.addView(ui.text("安装应用",22),new LinearLayout.LayoutParams(-1,px(36)));
        details=ui.text("正在读取安装包…",14);details.setMaxLines(5);details.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(details,new LinearLayout.LayoutParams(-1,-2));
        status=ui.text("",13);status.setTextColor(ManagementUi.MUTED);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=px(16);card.addView(status,sp);
        LinearLayout buttons=new LinearLayout(this);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,px(42));bp.topMargin=px(20);card.addView(buttons,bp);
        Button back=new KeyFeedback.Button(this);back.setText("返回文件传输");style(back);back.setOnClickListener(v->{finish();KeyFeedback.returned(this);});buttons.addView(back,new LinearLayout.LayoutParams(0,-1,1));
        install=new KeyFeedback.Button(this);style(install);install.setText("安装");install.setEnabled(false);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-1,1);ip.leftMargin=px(12);buttons.addView(install,ip);back.requestFocus();
        fileId=getIntent().getStringExtra("file");waiting=saved!=null&&saved.getBoolean("waiting");viewRoute=saved!=null&&saved.getBoolean("viewRoute");fallback=saved!=null&&saved.getBoolean("fallback");baseline=saved==null?-1:saved.getLong("baseline",-1);
        if(saved!=null&&saved.getString("prepared")!=null)prepared=new File(saved.getString("prepared"));
        worker.execute(()->{
            try{
                FileTransferStore store=store();FileTransferStore.Entry entry=store.entry(fileId);ApkPackage result=ApkPackage.inspect(this,store.file(fileId),entry.name);
                runOnUiThread(()->{if(closed)return;apk=result;if(!waiting)baseline=apk.previousUpdate;
                    details.setText(apk.label+"\n"+apk.pkg+"\n"+apk.version+"  "+FileTransferSource.size(apk.bytes)+"\n"+(apk.update?"覆盖安装，保留应用数据":"新安装"));
                    if(sessions()&&ApkInstallSession.owns(this,fileId)&&apk.hash.equals(ApkInstallSession.prefs(this).getString("hash",""))){
                        baseline=ApkInstallSession.prefs(this).getLong("baseline",baseline);refreshSession(false);
                    }else if(waiting)checkResult("安装结果待核对，请重新选择文件检查");else ready();
                });
            }catch(IOException|RuntimeException error){runOnUiThread(()->{if(!closed){details.setText("无法安装此文件");fail(errorMessage(error));}});}
        });
    }
    private FileTransferStore store()throws IOException{return new FileTransferStore(new File(getFilesDir(),"received-files"));}
    private void action(String label,Runnable run){install.setText(label);install.setEnabled(true);install.setOnClickListener(v->run.run());}
    private void ready(){waiting=false;status.setText("确认后由系统安装器完成安装");action(apk.update?"更新应用":"安装",this::begin);}
    private String errorMessage(Exception error){
        if(error instanceof ApkPackage.Invalid)return error.getMessage();
        if(error instanceof UpdateInstallPreparation.Failure){String reason=((UpdateInstallPreparation.Failure)error).reason;return "storage_full".equals(reason)?"电视存储空间不足，无法准备安装文件":"copy_mismatch".equals(reason)?"安装文件副本校验失败":"安装文件目录不可用或读写失败";}
        return UpdateInstallPolicy.message(UpdateInstallPolicy.ioReason(error));
    }
    private void fail(String message){waiting=false;status.setText(message);install.setEnabled(false);}
    @SuppressWarnings("deprecation")
    private boolean installationAllowed(){
        if(Build.VERSION.SDK_INT>=26)return UpdateInstaller.permitted(this);
        try{return android.provider.Settings.Secure.getInt(getContentResolver(),"install_non_market_apps",1)!=0;}
        catch(RuntimeException unavailable){return true;} // Unknown vendor policy remains the system installer's decision.
    }
    private void begin(){
        if(apk==null||waiting)return;
        if(!installationAllowed()){
            status.setText(Build.VERSION.SDK_INT>=26?"请先允许 Quiet TV 安装应用，返回后再次选择安装":"请先在系统设置中允许未知来源，返回后再次选择安装");action("允许安装",()->{
                try{startActivityForResult(Build.VERSION.SDK_INT>=26?new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",Uri.parse("package:"+getPackageName())):new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS),PERMISSION);}
                catch(ActivityNotFoundException|SecurityException error){fail("系统未提供安装来源授权入口，请在电视系统设置中允许安装应用");}
            });return;
        }
        waiting=true;install.setEnabled(false);status.setText("正在检查并准备安装…");
        worker.execute(()->{
            try{
                FileTransferStore receipts=store();File source=receipts.file(fileId);ApkPackage fresh=ApkPackage.inspect(this,source,receipts.entry(fileId).name);
                if(!apk.hash.equals(fresh.hash))throw new ApkPackage.Invalid("安装包已变化，请返回后重新选择");
                if(closed)return;
                if(sessions()&&!fallback){
                    if(ApkInstallSession.active(this))throw new ApkPackage.Invalid("已有安装任务正在进行，请稍后核对结果");
                    try{ApkInstallSession.start(this,fileId,fresh,source,()->!closed);}
                    catch(UnsupportedOperationException unsupported){runOnUiThread(()->{if(!closed)fallback();});return;}
                    runOnUiThread(()->{if(!closed){baseline=fresh.previousUpdate;refreshSession(resumed);}});
                }else{
                    File file=fresh.prepare(this,source);runOnUiThread(()->{if(closed)return;baseline=fresh.previousUpdate;prepared=file;if(resumed)launchFile();else{waiting=false;status.setText("安装文件已准备好");action("继续安装",this::launchFile);}});
                }
            }catch(IOException|RuntimeException error){runOnUiThread(()->{if(!closed)fail(errorMessage(error));});}
        });
    }
    @SuppressWarnings("deprecation")
    private void launchFile(){
        if(!resumed||prepared==null)return;
        try{
            Uri uri=Build.VERSION.SDK_INT>=24?FileProvider.getUriForFile(this,getPackageName()+".updates",prepared):Uri.fromFile(prepared);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").putExtra(Intent.EXTRA_RETURN_RESULT,true);
            if(Build.VERSION.SDK_INT>=24){intent.setClipData(ClipData.newRawUri("APK",uri));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            waiting=true;viewRoute=false;install.setEnabled(false);status.setText("请在系统安装器中确认");
            try{startActivityForResult(intent,INSTALL);}
            catch(ActivityNotFoundException absent){viewRoute=true;intent.setAction(Intent.ACTION_VIEW);startActivityForResult(intent,INSTALL);}
        }catch(ActivityNotFoundException error){fail("系统安装器不可用，无法安装应用");}
        catch(RuntimeException error){fail("系统限制了安装，请检查安装授权");}
    }
    private void refreshSession(boolean dispatch){
        if(apk==null||!ApkInstallSession.owns(this,fileId))return;
        int result=ApkInstallSession.status(this);
        if(result==-1){
            if(ApkInstallSession.confirmation==null){waiting=false;status.setText("上次安装确认已中断，可重新选择安装");action("重新安装",this::begin);return;}
            status.setText("请在系统安装器中确认");action("继续安装",this::confirmSession);if(dispatch&&resumed)confirmSession();
        }else if(result>=0){
            if(result==0)checkResult("系统报告安装成功，实际安装结果待核对");
            else {waiting=false;status.setText(UpdateInstallPolicy.message(ApkInstallSession.prefs(this).getString("reason","install_failed")));action("重试",this::begin);}
        }else if(result==-3){waiting=false;status.setText("上次安装准备已中断，可重新选择安装");action("重新安装",this::begin);}
        else {waiting=true;status.setText("等待系统安装结果");action("核对结果",this::checkSessionReturn);}
    }
    private void confirmSession(){
        Intent confirmation=ApkInstallSession.confirmation;if(!resumed||confirmation==null)return;
        ApkInstallSession.confirmation=null;ApkInstallSession.prefs(this).edit().putInt("status",-4).apply();waiting=true;install.setEnabled(false);
        try{startActivityForResult(confirmation,INSTALL);}
        catch(ActivityNotFoundException error){fallback();}
        catch(RuntimeException error){fail("系统限制了安装，请检查安装授权");}
    }
    private void fallback(){
        if(fallback){fail("系统安装器不可用，无法安装应用");return;}
        fallback=true;install.setEnabled(false);worker.execute(()->{
            try{ApkInstallSession.abandon(this);runOnUiThread(()->{if(!closed){waiting=false;begin();}});}
            catch(IOException error){runOnUiThread(()->{if(!closed)fail(error.getMessage());});}
        });
    }
    private void checkResult(String unresolved){
        waiting=false;install.setEnabled(false);
        worker.execute(()->{boolean installed=apk.installed(this,baseline);if(installed)removeCopy();runOnUiThread(()->{if(!closed){status.setText(installed?"应用已安装":unresolved);install.setEnabled(false);}});});
    }
    private void removeCopy(){if(prepared!=null&&Build.VERSION.SDK_INT<24&&prepared.isFile()&&!prepared.delete())DiagnosticLog.info("received_apk.copy_retained","reason=delete_failed");}
    private void checkSessionReturn(){
        install.setEnabled(false);worker.execute(()->{
            boolean installed=apk.installed(this,baseline),active=ApkInstallSession.active(this);
            runOnUiThread(()->{if(closed)return;
                if(ApkInstallSession.status(this)>=0){refreshSession(false);return;}
                if(installed){waiting=false;status.setText("应用已安装");install.setEnabled(false);}
                else if(active){waiting=true;status.setText("安装仍在进行，请稍后核对");action("核对结果",this::checkSessionReturn);}
                else {waiting=false;status.setText("安装尚未完成或已取消，可重新选择安装");action("重新安装",this::begin);}
            });
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(apk==null)return;
        if(request==PERMISSION){ready();if(!installationAllowed())status.setText("尚未获得安装授权");}
        else if(request==INSTALL){
            if(sessions()&&!fallback){checkSessionReturn();return;}
            if(!viewRoute)worker.execute(this::removeCopy);
            checkResult(viewRoute?"安装结果待核对，请检查应用列表":result==RESULT_CANCELED?"已取消安装":result==RESULT_OK?"系统报告安装成功，实际安装结果待核对":"系统安装失败，请检查安装包或系统限制");
        }
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(sessions()){ApkInstallSession.changed=()->refreshSession(true);if(apk!=null)refreshSession(false);}}
    @Override protected void onPause(){resumed=false;if(sessions())ApkInstallSession.changed=null;super.onPause();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putBoolean("waiting",waiting);saved.putLong("baseline",baseline);saved.putBoolean("viewRoute",viewRoute);saved.putBoolean("fallback",fallback);if(prepared!=null)saved.putString("prepared",prepared.getAbsolutePath());}
    @Override protected void onDestroy(){closed=true;super.onDestroy();}
}
