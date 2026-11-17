package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.concurrent.*;

/** User-selected APK review and system confirmation, independent of signed Quiet TV updates. */
public final class ApkInstallActivity extends ManagementUi {
    private static final int INSTALL=7410,PERMISSION=7411;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private boolean resumed,waiting,viewRoute,fallback,installerOpened,autoBeginPending,installationVerified;
    private String fileId;
    private ApkPackage apk;
    private File prepared;
    private long baseline=-1;
    private String diagnosticAttempt="unknown";
    private long diagnosticStarted=-1;
    private TextView details,status;
    private Button install;
    private ManagementUi.Sheet sheet;
    private Bitmap backdrop;
    @Override void drawSheetBackdrop(Canvas canvas){if(backdrop!=null)canvas.drawBitmap(backdrop,null,new RectF(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels),new Paint(3));else super.drawSheetBackdrop(canvas);}
    @Override boolean loadWallpaper(){return false;}
    @Override boolean unifiedSettings(){return true;}
    @Override public void handleBack(){finish();}
    private boolean sessions(){return UpdateInstallPolicy.session(Build.VERSION.SDK_INT);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);root.setVisibility(View.GONE);backdrop=getIntent().getParcelableExtra("backdrop");if(backdrop!=null){stage.removeAllViews();stage.setBackgroundColor(Color.TRANSPARENT);}
        sheet=new Sheet("安装应用");Button back=sheet.settingsLayout("关闭",this::finish);sheet.settingsActions(true);
        sheet.dialog.setOnDismissListener(d->finish());
        details=label("正在读取安装包…",14);QuickControlPreviewView.type(details,19.2f,28.8f,unit,UiPalette.TEXT);details.setMaxLines(5);details.setEllipsize(android.text.TextUtils.TruncateAt.END);sheet.body.addView(details,new LinearLayout.LayoutParams(-1,-2));
        status=label("",13);QuickControlPreviewView.type(status,17.28f,28.288f,unit,UiPalette.SECONDARY);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=px(12.96f);sheet.body.addView(status,sp);
        sheet.action("返回文件",()->{finish();KeyFeedback.returned(this);});install=sheet.action("安装",this::begin);install.setEnabled(false);sheet.show(back);
        fileId=getIntent().getStringExtra("file");waiting=saved!=null&&saved.getBoolean("waiting");viewRoute=saved!=null&&saved.getBoolean("viewRoute");fallback=saved!=null&&saved.getBoolean("fallback");baseline=saved==null?-1:saved.getLong("baseline",-1);installerOpened=saved!=null&&saved.getBoolean("installer-opened");autoBeginPending=saved!=null&&saved.getBoolean("auto-begin-pending");
        if(saved!=null){diagnosticAttempt=saved.getString("diagnosticAttempt","unknown");diagnosticStarted=saved.getLong("diagnosticStarted",-1);installationVerified=saved.getBoolean("installation-verified");}
        if(saved!=null&&saved.getString("prepared")!=null)prepared=new File(saved.getString("prepared"));
        worker.execute(()->{
            try{
                ApkPackage result=ApkPackage.inspect(this,sourceFile(),sourceName());
                runOnUiThread(()->{if(closed)return;apk=result;if(!waiting)baseline=apk.previousUpdate;
                    details.setText(apk.label+"\n"+apk.pkg+"\n"+apk.version+"  "+FileTransferSource.size(apk.bytes)+"\n"+(apk.update?"覆盖安装，保留应用数据":"新安装"));
                    if(sessions()&&ApkInstallSession.owns(this,fileId)&&apk.hash.equals(ApkInstallSession.prefs(this).getString("hash",""))){
                        diagnosticAttempt=ApkInstallSession.prefs(this).getString("diagnosticAttempt","unknown");diagnosticStarted=ApkInstallSession.prefs(this).getLong("diagnosticStarted",-1);
                        baseline=ApkInstallSession.prefs(this).getLong("baseline",baseline);refreshSession(false);
                    }else if(waiting)checkResult("安装结果待核对，请重新选择文件检查");else{ready();if(saved==null&&getIntent().getBooleanExtra("begin-install",false))autoBeginPending=true;beginWhenReady();}
                });
            }catch(IOException|RuntimeException error){DiagnosticLog.info("install.evidence.inspect_failed","source=received_apk attempt=unknown stage=inspect type="+error.getClass().getSimpleName());runOnUiThread(()->{if(!closed){details.setText("无法安装此文件");fail(errorMessage(error));}});}
        });
    }
    private FileTransferStore store()throws IOException{return new FileTransferStore(new File(getFilesDir(),"received-files"));}
    private File sourceFile()throws IOException{
        String path=getIntent().getStringExtra("source-file");if(path==null)return store().file(fileId);
        File file=new File(path).getCanonicalFile();if(!new File(getCacheDir(),"selected-apks").getCanonicalFile().equals(file.getParentFile())||!file.getName().matches("selected-apk-.*\\.apk")||!file.isFile())throw new IOException("安装文件无法读取");return file;
    }
    private String sourceName()throws IOException{String name=getIntent().getStringExtra("source-name");return name==null?store().entry(fileId).name:name;}
    private void action(String label,Runnable run){install.setText(label);install.setEnabled(true);install.setOnClickListener(v->run.run());}
    private void ready(){waiting=false;if(installationVerified){status.setText("应用已安装");OperationFeedback.status(status,OperationFeedback.Kind.SUCCESS);install.setEnabled(false);return;}status.setText("确认后由系统安装器完成安装");status.setCompoundDrawables(null,null,null,null);action(apk.update?"更新应用":"安装",this::begin);}
    private String errorMessage(Exception error){
        if(error instanceof ApkPackage.Invalid)return error.getMessage();
        if(error instanceof UpdateInstallPreparation.Failure){String reason=((UpdateInstallPreparation.Failure)error).reason;return "storage_full".equals(reason)?"电视存储空间不足，无法准备安装文件":"copy_mismatch".equals(reason)?"安装文件副本校验失败":"安装文件目录不可用或读写失败";}
        return UpdateInstallPolicy.message(UpdateInstallPolicy.ioReason(error));
    }
    private void fail(String message){waiting=false;status.setText(message);OperationFeedback.status(status,OperationFeedback.Kind.ERROR);install.setEnabled(false);}
    @SuppressWarnings("deprecation")
    private boolean installationAllowed(){
        if(Build.VERSION.SDK_INT>=26)return UpdateInstaller.permitted(this);
        try{return android.provider.Settings.Secure.getInt(getContentResolver(),"install_non_market_apps",1)!=0;}
        catch(RuntimeException unavailable){return true;} // Unknown vendor policy remains the system installer's decision.
    }
    private void permissionRequired(){waiting=false;status.setText("尚未获得安装授权");action("去系统设置授权",this::showPermissionDialog);}
    private void showPermissionDialog(){
        if(closed||isFinishing())return;sheet.heading.setText("允许安装应用");
        status.setText(Build.VERSION.SDK_INT>=26?"安装应用需要授权，请在系统设置中允许 Quiet TV 安装应用。":"安装应用需要授权，请在系统安全设置中允许未知来源。");
        action("去系统设置授权",this::openPermissionSettings);install.requestFocusFromTouch();
    }
    private void openPermissionSettings(){
        Intent intent=Build.VERSION.SDK_INT>=26?new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES",Uri.parse("package:"+getPackageName())):new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS);
        DiagnosticLog.info("received_apk.permission","phase=request api="+Build.VERSION.SDK_INT+" action="+intent.getAction());
        try{startActivityForResult(intent,PERMISSION);}
        catch(ActivityNotFoundException|SecurityException error){DiagnosticLog.error("received_apk.permission_failed",error);fail("系统未提供安装来源授权入口，请在电视系统设置中允许安装应用");}
    }
    private void beginWhenReady(){if(resumed&&autoBeginPending&&apk!=null&&!waiting){autoBeginPending=false;begin();}}
    private void begin(){
        if(apk==null||waiting)return;autoBeginPending=false;installationVerified=false;
        diagnosticAttempt=java.util.UUID.randomUUID().toString();diagnosticStarted=android.os.SystemClock.elapsedRealtime();
        InstallDiagnostics.stage("received_apk",diagnosticAttempt,sessions()&&!fallback?"session":"file","begin",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"receivedTransfer="+fileId);
        if(!installationAllowed()){
            InstallDiagnostics.stage("received_apk",diagnosticAttempt,"unknown","permission_required",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"");
            permissionRequired();showPermissionDialog();return;
        }
        waiting=true;install.setEnabled(false);status.setText("正在检查并准备安装…");OperationFeedback.status(status,OperationFeedback.Kind.PROGRESS);
        worker.execute(()->{
            try{
                File source=sourceFile();ApkPackage fresh=ApkPackage.inspect(this,source,sourceName());
                if(!apk.hash.equals(fresh.hash))throw new ApkPackage.Invalid("安装包已变化，请返回后重新选择");
                InstallDiagnostics.stage("received_apk",diagnosticAttempt,sessions()&&!fallback?"session":"file","verified",-1,diagnosticStarted,InstallDiagnostics.identity(fresh),"");
                InstallDiagnostics.baseline(this,"received_apk",diagnosticAttempt,sessions()&&!fallback?"session":"file",fresh.pkg,fresh.version,fresh.code,fresh.signers,fresh.previousUpdate);
                if(closed)return;
                if(sessions()&&!fallback){
                    if(ApkInstallSession.active(this))throw new ApkPackage.Invalid("已有安装任务正在进行，请稍后核对结果");
                    try{ApkInstallSession.start(this,fileId,fresh,source,()->!closed,diagnosticAttempt,diagnosticStarted);}
                    catch(UnsupportedOperationException unsupported){runOnUiThread(()->{if(!closed)fallback();});return;}
                    runOnUiThread(()->{if(!closed){baseline=fresh.previousUpdate;refreshSession(resumed);}});
                }else{
                    File file=fresh.prepare(this,source);InstallDiagnostics.stage("received_apk",diagnosticAttempt,"file","prepared",-1,diagnosticStarted,InstallDiagnostics.identity(fresh),"");runOnUiThread(()->{if(closed)return;baseline=fresh.previousUpdate;prepared=file;if(resumed)launchFile();else{waiting=false;status.setText("安装文件已准备好");action("继续安装",this::launchFile);}});
                }
            }catch(IOException|RuntimeException error){InstallDiagnostics.stage("received_apk",diagnosticAttempt,sessions()&&!fallback?"session":"file","failed",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"type="+error.getClass().getSimpleName());runOnUiThread(()->{if(!closed)fail(errorMessage(error));});}
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
            InstallDiagnostics.stage("received_apk",diagnosticAttempt,"file","dispatch",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"action="+intent.getAction());
            try{startActivityForResult(intent,INSTALL);installerOpened=true;}
            catch(ActivityNotFoundException absent){viewRoute=true;intent.setAction(Intent.ACTION_VIEW);InstallDiagnostics.stage("received_apk",diagnosticAttempt,"view","dispatch",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"action="+intent.getAction());startActivityForResult(intent,INSTALL);installerOpened=true;}
        }catch(ActivityNotFoundException error){InstallDiagnostics.stage("received_apk",diagnosticAttempt,"file","failed",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"reason=installer_missing");fail("系统安装器不可用，无法安装应用");}
        catch(RuntimeException error){InstallDiagnostics.stage("received_apk",diagnosticAttempt,"file","failed",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"type="+error.getClass().getSimpleName());fail("系统限制了安装，请检查安装授权");}
    }
    private void refreshSession(boolean dispatch){
        if(apk==null||!ApkInstallSession.owns(this,fileId))return;
        ApkInstallPhase phase=ApkInstallSession.phase(this);
        if(phase==ApkInstallPhase.CONFIRMATION){
            if(ApkInstallSession.confirmation==null){waiting=false;status.setText("上次安装确认已中断，可重新选择安装");action("重新安装",this::begin);return;}
            status.setText("请在系统安装器中确认");action("继续安装",this::confirmSession);if(dispatch&&resumed)confirmSession();
        }else if(phase==ApkInstallPhase.REPORTED_SUCCESS||phase==ApkInstallPhase.REPORTED_FAILURE){
            if(phase==ApkInstallPhase.REPORTED_SUCCESS)checkResult("系统报告安装成功，实际安装结果待核对");
            else {waiting=false;status.setText(UpdateInstallPolicy.message(ApkInstallSession.prefs(this).getString("reason","install_failed")));action("重试",this::begin);}
        }else if(phase==ApkInstallPhase.PREPARING){waiting=false;status.setText("上次安装准备已中断，可重新选择安装");action("重新安装",this::begin);}
        else {waiting=true;status.setText("等待系统安装结果");action("核对结果",this::checkSessionReturn);}
    }
    private void confirmSession(){
        Intent confirmation=ApkInstallSession.confirmation;if(!resumed||confirmation==null)return;
        ApkInstallSession.confirmation=null;ApkInstallSession.prefs(this).edit().putInt("status",ApkInstallPhase.CONFIRMING.status).apply();waiting=true;install.setEnabled(false);
        InstallDiagnostics.stage("received_apk",ApkInstallSession.prefs(this).getString("diagnosticAttempt",diagnosticAttempt),"session","confirm",ApkInstallSession.id(this),diagnosticStarted,InstallDiagnostics.identity(apk),"");
        try{startActivityForResult(confirmation,INSTALL);installerOpened=true;}
        catch(ActivityNotFoundException error){fallback();}
        catch(RuntimeException error){fail("系统限制了安装，请检查安装授权");}
    }
    private void fallback(){
        if(fallback){fail("系统安装器不可用，无法安装应用");return;}
        InstallDiagnostics.stage("received_apk",diagnosticAttempt,"session","fallback",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"to=file");
        fallback=true;install.setEnabled(false);worker.execute(()->{
            try{ApkInstallSession.abandon(this);runOnUiThread(()->{if(!closed){waiting=false;begin();}});}
            catch(IOException error){runOnUiThread(()->{if(!closed)fail(error.getMessage());});}
        });
    }
    private void checkResult(String unresolved){
        waiting=false;install.setEnabled(false);
        worker.execute(()->{boolean installed=apk.installed(this,baseline);diagnosticReadback(installed);if(installed)removeCopy();runOnUiThread(()->{if(!closed){installationVerified=installed;status.setText(installed?"应用已安装":unresolved);OperationFeedback.status(status,installed?OperationFeedback.Kind.SUCCESS:OperationFeedback.Kind.INFO);install.setEnabled(false);}});});
    }
    private void diagnosticReadback(boolean installed){
        String attempt=diagnosticAttempt;if(sessions()&&!fallback)attempt=ApkInstallSession.prefs(this).getString("diagnosticAttempt",attempt);
        InstallDiagnostics.readback(this,"received_apk",attempt,sessions()&&!fallback?"session":viewRoute?"view":"file",apk.pkg,apk.version,apk.code,apk.signers,baseline,installed);
    }
    private void removeCopy(){if(prepared!=null&&Build.VERSION.SDK_INT<24&&prepared.isFile()&&!prepared.delete())DiagnosticLog.info("received_apk.copy_retained","reason=delete_failed");}
    private void checkSessionReturn(){
        install.setEnabled(false);worker.execute(()->{
            boolean installed=apk.installed(this,baseline),active=ApkInstallSession.active(this);
            diagnosticReadback(installed);
            runOnUiThread(()->{if(closed)return;
                if(ApkInstallSession.status(this)>=0){refreshSession(false);return;}
                if(installed){installationVerified=true;waiting=false;status.setText("应用已安装");OperationFeedback.status(status,OperationFeedback.Kind.SUCCESS);install.setEnabled(false);}
                else if(active){waiting=true;status.setText("安装仍在进行，请稍后核对");action("核对结果",this::checkSessionReturn);}
                else {waiting=false;status.setText("安装尚未完成或已取消，可重新选择安装");action("重新安装",this::begin);}
            });
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(apk==null)return;
        if(request==PERMISSION){boolean allowed=installationAllowed();DiagnosticLog.info("received_apk.permission","phase=return allowed="+allowed+" result="+result);if(allowed){ready();status.setText("已获得安装授权，请再次选择安装");}else{permissionRequired();status.setText("尚未获得安装授权。系统没有 Quiet TV 入口时，请通过电脑安装。");}}
        else if(request==INSTALL){
            int legacy=data==null?Integer.MIN_VALUE:data.getIntExtra("android.intent.extra.INSTALL_RESULT",Integer.MIN_VALUE);
            InstallDiagnostics.stage("received_apk",diagnosticAttempt,viewRoute?"view":sessions()&&!fallback?"session":"file","file_return",-1,diagnosticStarted,InstallDiagnostics.identity(apk),"statusNamespace=activity_result result="+result+" legacyStatus="+InstallDiagnostics.number(legacy)+" platformReason="+UpdateInstallPolicy.platformReason(legacy,null));
            if(sessions()&&!fallback){checkSessionReturn();return;}
            if(!viewRoute)worker.execute(this::removeCopy);
            checkResult(viewRoute?"安装结果待核对，请检查应用列表":result==RESULT_CANCELED?"已取消安装":result==RESULT_OK?"系统报告安装成功，实际安装结果待核对":"系统安装失败，请检查安装包或系统限制");
        }
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(sessions()){ApkInstallSession.changed=()->refreshSession(true);if(apk!=null)refreshSession(false);}beginWhenReady();}
    @Override protected void onPause(){resumed=false;if(sessions())ApkInstallSession.changed=null;super.onPause();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putBoolean("waiting",waiting);saved.putBoolean("installation-verified",installationVerified);saved.putString("diagnosticAttempt",diagnosticAttempt);saved.putLong("diagnosticStarted",diagnosticStarted);saved.putBoolean("installer-opened",installerOpened);saved.putBoolean("auto-begin-pending",autoBeginPending);saved.putLong("baseline",baseline);saved.putBoolean("viewRoute",viewRoute);saved.putBoolean("fallback",fallback);if(prepared!=null)saved.putString("prepared",prepared.getAbsolutePath());}
    @Override public void finish(){setResult(RESULT_OK,new Intent().putExtra("installer-opened",installerOpened).putExtra("installation-verified",installationVerified));super.finish();}
    @Override protected void onDestroy(){closed=true;if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();}super.onDestroy();}
}
