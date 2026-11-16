package home.quiet.tv;

import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Process-wide checked release state. Listeners are owned by visible screens only. */
final class UpdateRepository {
    interface Listener {void changed();}
    private static UpdateRepository instance;
    static synchronized UpdateRepository get(Context context){if(instance==null)instance=new UpdateRepository(context.getApplicationContext());return instance;}
    private final Context context;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    private final File cache,directory;
    private UpdateTransfer checkTransfer,downloadTransfer;
    private UpdateManifest manifest;
    private byte[] key;
    private javax.net.ssl.SSLSocketFactory legacyTls;
    private volatile int checkGeneration,downloadGeneration;
    private volatile int installSession=-1;
    private UpdateManifest.Artifact installTarget;
    private File installFile;
    private Intent installConfirmation;
    boolean installBusy,installNotice;
    private UpdateState.Install installState=UpdateState.Install.IDLE;
    String installError="";
    private boolean installFallback;
    private String installAttempt="";
    private String installRoute="";
    private long diagnosticInstallStarted=-1;
    private long lastWall,lastElapsed,lastProgress;
    UpdateManifest.Artifact available;
    boolean checking,downloading,verifying,ready,switching,checked;
    long received,total;
    String checkError="",downloadError="",unavailableReason="";
    private String lastState="";
    private UpdateRepository(Context context){
        this.context=context;prefs=context.getSharedPreferences("application-updates",0);
        directory=new File(context.getFilesDir(),"updates");cache=new File(context.getFilesDir(),"update-manifest.json");
        installAttempt=prefs.getString("installAttempt","");
        diagnosticInstallStarted=prefs.getLong("diagnosticInstallStarted",-1);
        installRoute=prefs.getString("installRoute",Build.VERSION.SDK_INT<24?"legacy_file":"content");
        if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT)){
            installSession=prefs.getInt("installSession",-1);installFallback=prefs.getBoolean("installFallback",false);
            if(installSession>=0)installRoute="session";
            if(!prefs.getString("installPhase","").isEmpty()){
                installState=UpdateState.Install.INTERRUPTED;installError=UpdateInstallPolicy.message("interrupted");installNotice=true;
            }
        }
        if(snapshot().savedInstall==UpdateState.Install.FILE_DISPATCHED){installState=UpdateState.Install.FILE_DISPATCHED;installNotice=true;}
        worker.execute(()->{try{
            key=read(context.getAssets().open("updates/public-key.der"),4096);
            if(cache.isFile()){manifest=UpdateManifest.verify(read(new FileInputStream(cache),UpdateManifest.MAX_BYTES),key,prefs.getLong("revision",0));prefs.edit().putLong("revision",manifest.revision).apply();}
        }catch(IOException e){DiagnosticLog.info("update.cache.unavailable",UpdateFailure.details("cache_read",e));}
            main.post(()->{reconcile();notifyChanged();});
        });
    }
    UpdateState snapshot(){return new UpdateState(checking,checkError,downloading,verifying,switching,ready,downloadError,installState,
        prefs.getString("installPhase",""),installAttempt,installSession,installBusy,installConfirmation!=null);}
    void add(Listener listener){listeners.add(listener);listener.changed();}
    void remove(Listener listener){listeners.remove(listener);}
    private void notifyChanged(){
        String observation=snapshot().diagnostics();
        if(!observation.equals(lastState)){lastState=observation;DiagnosticLog.info("update.state",observation);}
        for(Listener listener:new ArrayList<>(listeners))listener.changed();
    }
    boolean online(){try{ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();return network!=null&&network.isConnected();}catch(RuntimeException e){return false;}}
    void foreground(){reconcile();notifyChanged();if(!checking&&!downloading&&online()&&UpdatePolicy.due(now(),prefs.getLong("success",0),prefs.getLong("failure",0)))check(false);}
    private long now(){long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
        // Within a process, elapsed time prevents timezone/manual-clock changes from advancing intervals.
        if(lastWall==0){lastWall=wall;lastElapsed=elapsed;}return lastWall+elapsed-lastElapsed;
    }
    void reconcile(){
        try{
            UpdateManifest.Device device=UpdateInstaller.device(context);UpdateManifest.Artifact selected=manifest==null?null:manifest.select(device);
            boolean changed=available==null?selected!=null:selected==null||!available.sha256.equals(selected.sha256);
            available=selected;unavailableReason=manifest==null||selected!=null?"":manifest.unavailableReason(device);if(changed){ready=false;received=0;downloadError="";}
            String installedTarget=prefs.getString("pendingVersion","");long installedCode=prefs.getLong("pendingCode",0);
            if(!installedTarget.isEmpty()&&device.code>=installedCode&&UpdateManifest.compare(device.version,installedTarget)>=0){
                diagnosticReadback(true);
                prefs.edit().remove("pendingVersion").remove("pendingCode").remove("installSession").remove("installPhase").remove("installHash").remove("installFallback").remove("installRoute").apply();
                installSession=-1;installConfirmation=null;installState=UpdateState.Install.SUCCESS;installNotice=true;
                DiagnosticLog.info("update.install.reconciled","result=installed version="+device.version+" build="+device.code);
            }
            if(installTarget==null&&available!=null&&available.sha256.equals(prefs.getString("installHash","")))installTarget=available;
            // A partial file of the right length still needs full validation on the worker.
            if(!downloading&&!verifying&&available!=null&&!ready&&available.sha256.equals(prefs.getString("readyHash",""))&&file().isFile()){
                final UpdateManifest.Artifact candidate=available;verifying=true;
                worker.execute(()->{boolean valid=false;try{UpdateInstaller.validate(context,candidate,file(candidate));valid=true;}catch(IOException|RuntimeException failure){DiagnosticLog.info("update.cached_apk.failed",UpdateFailure.details("cached_apk",failure));}
                    final boolean checked=valid;main.post(()->{verifying=false;if(available!=null&&available.sha256.equals(candidate.sha256)){ready=checked;if(!checked)prefs.edit().remove("readyHash").apply();}notifyChanged();});});
            }
        }catch(IOException e){available=null;ready=false;checkError="无法读取本机安装信息，请重试";}
    }
    void check(boolean manual){
        if(checking||downloading)return;
        if(!online()){if(manual){checkError="网络未连接";notifyChanged();}return;}
        checking=true;checkError="";int generation=++checkGeneration;
        UpdateTransfer transfer=newTransfer();checkTransfer=transfer;notifyChanged();
        worker.execute(()->{
            final UpdateManifest[] found={null};final byte[][] envelope={null};final int[] source={-1};String error="",stage="key";
            try{
                if(key==null)throw new IOException("Update key unavailable");
                stage="manifest";
                transfer.manifest(prefs.getInt("manifestSource",-1),(bytes,index)->{found[0]=UpdateManifest.verify(bytes,key,prefs.getLong("revision",0));envelope[0]=bytes;source[0]=index;return found[0].revision;});
                if(generation!=checkGeneration)return;
                // Cache and its preference revision are written only after verification and cancellation checks.
                stage="cache";
                synchronized(this){if(generation!=checkGeneration)return;try(FileOutputStream out=new FileOutputStream(cache)){out.write(envelope[0]);out.getFD().sync();}}
            }catch(IOException|RuntimeException e){error=UpdateFailure.message(stage,e);DiagnosticLog.info("update.check.failed",UpdateFailure.details(stage,e));}
            final String failure=error;
            main.post(()->{if(generation!=checkGeneration)return;checking=false;checkTransfer=null;checkError=failure;
                if(failure.isEmpty()){manifest=found[0];checked=true;prefs.edit().putLong("revision",manifest.revision).putLong("success",now()).remove("failure").putInt("manifestSource",source[0]).apply();reconcile();}
                else prefs.edit().putLong("failure",now()).apply();notifyChanged();});
        });
    }
    synchronized void cancelCheck(){++checkGeneration;if(checkTransfer!=null)checkTransfer.cancel();checkTransfer=null;checking=false;notifyChanged();}
    void download(){
        if(available==null||downloading||installBusy)return;final UpdateManifest.Artifact target=available;
        if(!directory.isDirectory()&&!directory.mkdirs()){downloadError="无法创建更新目录";notifyChanged();return;}
        ready=false;downloading=true;verifying=false;downloadError="";received=0;total=target.bytes;lastProgress=0;
        int generation=++downloadGeneration;UpdateTransfer transfer=newTransfer();downloadTransfer=transfer;notifyChanged();
        worker.execute(()->{String error="";int source=-1;boolean validating=false;
            try{
                source=transfer.download(target.url,target.bytes,target.sha256,file(target),prefs.getInt("apkSource",-1),(bytes,size,changed)->{
                    long tick=SystemClock.elapsedRealtime();if(!changed&&bytes!=size&&tick-lastProgress<200)return;lastProgress=tick;
                    main.post(()->{if(generation!=downloadGeneration)return;received=bytes;total=size;switching=changed;notifyChanged();});
                });
                main.post(()->{if(generation==downloadGeneration){verifying=true;notifyChanged();}});
                if(generation!=downloadGeneration)return;validating=true;UpdateInstaller.validate(context,target,file(target));
            }catch(IOException|RuntimeException e){error=(validating||e instanceof UpdateTransfer.IntegrityException)?"安装包校验失败":"更新下载失败";
                if(file(target).isFile()&&file(target).length()==target.bytes)try(RandomAccessFile invalid=new RandomAccessFile(file(target),"rw")){invalid.setLength(0);}catch(IOException ignored){}
                DiagnosticLog.info("update.download.failed",UpdateFailure.details(validating?"apk_verify":"download",e));}
            final String failure=error;final int successfulSource=source;
            main.post(()->{if(generation!=downloadGeneration)return;downloading=false;verifying=false;switching=false;downloadTransfer=null;downloadError=failure;ready=failure.isEmpty();
                if(ready)prefs.edit().putInt("apkSource",successfulSource).putString("readyHash",target.sha256).apply();else prefs.edit().remove("readyHash").apply();notifyChanged();});
        });
    }
    void cancelDownload(){++downloadGeneration;if(downloadTransfer!=null)downloadTransfer.cancel();downloadTransfer=null;downloading=false;verifying=false;switching=false;notifyChanged();}
    File file(){return available==null?null:file(available);}
    private File file(UpdateManifest.Artifact target){return new File(directory,target.sha256+".apk");}
    /** Single installation entry; UI only dispatches a prepared route while foreground. */
    void install(){
        if(installBusy)return;
        if(snapshot().savedInstall==UpdateState.Install.FILE_DISPATCHED){fileInstallReturned(Integer.MIN_VALUE,null);return;}
        if(!ready||available==null||verifying||downloading){installationFailed("更新状态已变化，请重新检查");return;}
        final int previous=installSession>=0?installSession:prefs.getInt("installCleanup",-1);
        if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT)&&previous>=0){
            final String previousAttempt=installAttempt;
            installBusy=true;installNotice=false;notifyChanged();
            worker.execute(()->{
                boolean active=UpdateSessionInstaller.active(context,previous);
                boolean released=!active&&releaseInstallSession(previous);
                main.post(()->{
                    if(!previousAttempt.equals(installAttempt))return;
                    installBusy=false;
                    // A terminal callback may have arrived while the session was queried.
                    if(installState==UpdateState.Install.SUCCESS){notifyChanged();return;}
                    if(active&&prefs.getInt("installSession",-1)!=previous&&prefs.getInt("installCleanup",-1)!=previous){notifyChanged();return;}
                    if(active){
                        if(installState!=UpdateState.Install.CONFIRMATION&&installState!=UpdateState.Install.CONFIRMING)installState=UpdateState.Install.SUBMITTED;
                        installNotice=true;notifyChanged();return;
                    }
                    if(!released){installationFailed("上次安装任务无法结束，请重试或通过电脑更新");return;}
                    if(installSession==previous)installSession=-1;
                    if(prefs.getInt("installSession",-1)==previous)prefs.edit().remove("installSession").remove("installPhase").apply();
                    install();
                });
            });return;
        }
        final UpdateManifest.Artifact target=available;installAttempt=Long.toHexString(System.nanoTime());installTarget=target;installFile=null;installConfirmation=null;diagnosticInstallStarted=SystemClock.elapsedRealtime();
        installSession=-1;installFallback=false;installBusy=true;installNotice=false;installState=UpdateState.Install.PREPARING;installError="";
        installRoute=Build.VERSION.SDK_INT<24?"legacy_file":"content";
        SharedPreferences.Editor record=prefs.edit().remove("installSession").putString("installPhase",UpdateState.Install.PREPARING.wire).putString("installHash",target.sha256).putString("installAttempt",installAttempt).putBoolean("installFallback",false)
                .putString("installRoute",installRoute).putString("pendingVersion",target.version()).putLong("pendingCode",target.code).putLong("diagnosticInstallStarted",diagnosticInstallStarted)
                .putString("diagnosticIdentity",InstallDiagnostics.identity(target)).putString("diagnosticPackage",target.pkg).putString("diagnosticVersion",target.version()).putLong("diagnosticCode",target.code)
                .putStringSet("diagnosticSigners",new HashSet<>(target.signers));
        record.apply();notifyChanged();
        worker.execute(()->{
            String stage="verify";File prepared=null;int session=-1;Exception error=null;
            DiagnosticLog.info("update.install.prepare","attempt="+installAttempt+" "+installDetail(target));
            InstallDiagnostics.stage("online_update",installAttempt,installRoute,"begin",-1,diagnosticInstallStarted,InstallDiagnostics.identity(target),"");
            InstallDiagnostics.baseline(context,"online_update",installAttempt,installRoute,target.pkg,target.version(),target.code,target.signers,-1);
            try{
                UpdateInstaller.validate(context,target,file(target));stage="prepare";
                diagnosticStage("verified",-1,"");
                try{prepared=UpdateInstaller.prepare(context,target,file(target));}
                catch(UpdateInstallPreparation.Failure failure){
                    if(!UpdateInstallPolicy.sessionFallback(Build.VERSION.SDK_INT,failure.reason))throw failure;
                    // prepare() has already exhausted both external directories. Do not cycle back to them.
                    installFallback=true;installRoute="session";
                    prefs.edit().putBoolean("installFallback",true).putString("installRoute",installRoute).apply();
                    DiagnosticLog.info("update.install.fallback","attempt="+installAttempt+" from=legacy_file to=session "+failure.details());
                    session=UpdateSessionInstaller.prepare(context,target,file(target),id->{installSession=id;boolean saved=prefs.edit().putInt("installSession",id).commit();diagnosticStage("created",id,"recorded="+saved);return saved;});
                    diagnosticStage("written",session,"fsync=true");
                }
            }catch(IOException|RuntimeException failure){error=failure;}
            final File result=prepared;final int id=session;final Exception failure=error;final String failedStage=stage;
            main.post(()->{
                installBusy=false;
                if(failure instanceof UpdateSessionInstaller.Failure){sessionFailed((UpdateSessionInstaller.Failure)failure);return;}
                if(failure!=null){
                    if("verify".equals(failedStage)){ready=false;prefs.edit().remove("readyHash").apply();}
                    diagnosticStage("failed",installSession,"failedStage="+failedStage+" type="+failure.getClass().getSimpleName());installationFailed(preparationMessage(failedStage,failure));
                    DiagnosticLog.error("update.install.prepare_failed",installDetail(target)+" stage="+failedStage+" "+preparationDetails(failure),failure);return;
                }
                diagnosticStage("prepared",id,"");installFile=result;installSession=id;installState=UpdateState.Install.PREPARED;installNotice=true;notifyChanged();
            });
        });
    }
    void dispatchInstall(android.app.Activity activity){
        if(!snapshot().canDispatch)return;installNotice=false;
        if(installSession<0){
            installState=UpdateState.Install.FILE_DISPATCHED;
            if(!prefs.edit().putString("installPhase",UpdateState.Install.FILE_DISPATCHED.wire).commit()){installationFailed("无法记录安装任务，请重试");return;}
            UpdateInstaller.LaunchResult result=UpdateInstaller.launch(activity,installFile);
            diagnosticStage("dispatch",-1,"launchResult="+result.name());
            if(result!=UpdateInstaller.LaunchResult.DISPATCHED)installationFailed(UpdateInstallPolicy.message(result.reason));
            else notifyChanged();return;
        }
        final int id=installSession;installBusy=true;installState=UpdateState.Install.COMMITTING;prefs.edit().putString("installPhase",UpdateState.Install.COMMITTING.wire).apply();notifyChanged();
        worker.execute(()->{
            UpdateSessionInstaller.Failure error=null;
            try{UpdateSessionInstaller.commit(context,id);diagnosticStage("submitted",id,"");}catch(UpdateSessionInstaller.Failure failure){error=failure;}
            final UpdateSessionInstaller.Failure failure=error;
            main.post(()->{
                if(installSession!=id)return; // A result may arrive before commit returns.
                installBusy=false;
                if(failure!=null){sessionFailed(failure);return;}
                if(installState==UpdateState.Install.COMMITTING){installState=UpdateState.Install.SUBMITTED;prefs.edit().putString("installPhase",UpdateState.Install.SUBMITTED.wire).apply();}
                DiagnosticLog.info("update.install.session","step=commit session="+id);notifyChanged();
            });
        });
    }
    /** A file installer may omit its result or finish immediately. The installed version is authoritative. */
    void fileInstallReturned(int resultCode,Intent result){
        if(snapshot().savedInstall!=UpdateState.Install.FILE_DISPATCHED)return;
        int status=result==null?Integer.MIN_VALUE:result.getIntExtra("android.intent.extra.INSTALL_RESULT",Integer.MIN_VALUE);
        reconcile();boolean installed=(installState==UpdateState.Install.SUCCESS);
        String reason=status<0&&status!=Integer.MIN_VALUE?UpdateInstallPolicy.fileResult(status):"pending";
        DiagnosticLog.info("update.install.file_result","attempt="+installAttempt+" result="+(resultCode==Integer.MIN_VALUE?"unavailable":resultCode)+" status="+(status==Integer.MIN_VALUE?"unavailable":status)+" outcome="+(installed?"installed":reason));
        diagnosticStage("file_return",-1,"statusNamespace=activity_result result="+InstallDiagnostics.number(resultCode)+" legacyStatus="+InstallDiagnostics.number(status)+" platformReason="+UpdateInstallPolicy.platformReason(status,null));
        diagnosticReadback(installed);
        if(installed){notifyChanged();return;}
        // ACTION_VIEW installers can return RESULT_CANCELED immediately without an install result.
        // Only an explicit platform failure ends this attempt; onResume alone is not a failure.
        if(!"pending".equals(reason)){
            installationFailed(UpdateInstallPolicy.message(reason),"cancelled".equals(reason)?UpdateState.Install.CANCELLED:UpdateState.Install.FAILED);return;
        }
        installState=UpdateState.Install.FILE_DISPATCHED;installNotice=true;notifyChanged();
    }
    /** Called only after the user confirms the previous file installer has closed. */
    boolean retryFileInstall(){
        reconcile();
        if(!snapshot().canRetryFile){notifyChanged();return false;}
        prefs.edit().remove("installPhase").apply();installState=UpdateState.Install.IDLE;installNotice=false;return true;
    }
    void installationResult(Intent intent){
        int id=prefs.getInt("installSession",-1);
        if(id<0||!UpdateSessionInstaller.action(context,id).equals(intent.getAction())||intent.getIntExtra("android.content.pm.extra.SESSION_ID",id)!=id){
            DiagnosticLog.info("update.install.result_ignored","reason=session_mismatch");return;
        }
        int status=intent.getIntExtra("android.content.pm.extra.STATUS",Integer.MIN_VALUE);
        if(status==Integer.MIN_VALUE)return;
        installSession=id;installBusy=false;
        int legacy=intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS",Integer.MIN_VALUE);
        String message=intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
        String reason=status==-1?"confirmation":status==0?"success":UpdateInstallPolicy.result(status,legacy,message);
        DiagnosticLog.info("update.install.result","attempt="+installAttempt+" route=session session="+id+" status="+status+" legacyStatus="+(legacy==Integer.MIN_VALUE?"unavailable":legacy)+" platformReason="+UpdateInstallPolicy.platformReason(legacy,message)+" reason="+reason);
        diagnosticStage("result",id,InstallDiagnostics.result(status,legacy,message));
        if(status!=-1)diagnosticReadback(null);
        if(status==-1){
            installConfirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(installConfirmation==null){fallbackInstall("confirmation_missing");return;}
            installState=UpdateState.Install.CONFIRMATION;installNotice=true;prefs.edit().putString("installPhase",UpdateState.Install.CONFIRMATION.wire).apply();notifyChanged();return;
        }
        installSession=-1;installConfirmation=null;prefs.edit().remove("installSession").remove("installPhase").apply();
        if("invalid".equals(reason)){ready=false;prefs.edit().remove("readyHash").apply();}
        if(status==0){
            installState=UpdateState.Install.AWAITING_VERSION;installError="安装结果待核对，请重新打开 Quiet TV";reconcile();
            if(installState!=UpdateState.Install.SUCCESS)installNotice=true;
            notifyChanged();
        }else installationFailed(UpdateInstallPolicy.message(reason),"cancelled".equals(reason)?UpdateState.Install.CANCELLED:UpdateState.Install.FAILED);
    }
    void confirmInstall(android.app.Activity activity){
        if(!snapshot().canConfirm)return;
        Intent confirmation=installConfirmation;installConfirmation=null;installNotice=false;installState=UpdateState.Install.CONFIRMING;
        diagnosticStage("confirm",installSession,"");
        try{
            activity.startActivity(confirmation);prefs.edit().putString("installPhase",UpdateState.Install.CONFIRMING.wire).apply();
            DiagnosticLog.info("update.install.session","step=confirm session="+installSession);notifyChanged();
        }catch(ActivityNotFoundException failure){fallbackInstall("confirmation_missing");}
        catch(RuntimeException failure){
            int id=installSession;clearInstallSession();worker.execute(()->releaseInstallSession(id));
            installationFailed(UpdateInstallPolicy.message(UpdateInstallPolicy.ioReason(failure)));
            DiagnosticLog.error("update.install.confirm_failed","reason="+UpdateInstallPolicy.ioReason(failure),failure);
        }
    }
    private void sessionFailed(UpdateSessionInstaller.Failure failure){
        diagnosticStage("failed",failure.session,"failedStage="+failure.step+" reason="+failure.reason);
        DiagnosticLog.error("update.install.prepare_failed","attempt="+installAttempt+" requiredBytes="+(installTarget==null?-1:installTarget.bytes)+" "+failure.details(),failure);
        if("invalid".equals(failure.reason)){ready=false;prefs.edit().remove("readyHash").apply();}
        if(UpdateInstallPolicy.fallback(failure.reason)){fallbackInstall(failure.reason);return;}
        int id=installSession;clearInstallSession();worker.execute(()->releaseInstallSession(id));
        installationFailed(UpdateInstallPolicy.message(failure.reason));
    }
    private void fallbackInstall(String reason){
        diagnosticStage("fallback",installSession,"reason="+reason);
        final int id=installSession;clearInstallSession();
        final UpdateManifest.Artifact target=installTarget;
        if(!UpdateInstallPolicy.fallback(reason)||installFallback||target==null){
            worker.execute(()->releaseInstallSession(id));installationFailed(UpdateInstallPolicy.message(reason));return;
        }
        installFallback=true;installBusy=true;installNotice=false;installState=UpdateState.Install.PREPARING;installRoute="legacy_file";
        prefs.edit().putBoolean("installFallback",true).putString("installPhase",UpdateState.Install.PREPARING.wire).putString("installRoute",installRoute).apply();
        DiagnosticLog.info("update.install.fallback","attempt="+installAttempt+" from=session to=legacy_file reason="+reason+" session="+id);notifyChanged();
        worker.execute(()->{
            if(!releaseInstallSession(id)){
                main.post(()->{installBusy=false;installationFailed("安装任务无法结束，未切换安装方式，请重试");});return;
            }
            File prepared=null;Exception error=null;String stage="verify";
            try{UpdateInstaller.validate(context,target,file(target));stage="prepare";prepared=UpdateInstaller.prepare(context,target,file(target));}
            catch(IOException|RuntimeException failure){error=failure;}
            final File result=prepared;final Exception failure=error;final String failedStage=stage;
            main.post(()->{
                installBusy=false;
                if(failure!=null){
                    if("verify".equals(failedStage)){ready=false;prefs.edit().remove("readyHash").apply();}
                    DiagnosticLog.error("update.install.prepare_failed","route=legacy_file stage="+failedStage+" "+preparationDetails(failure),failure);
                    installationFailed(preparationMessage(failedStage,failure));return;
                }
                installFile=result;installState=UpdateState.Install.PREPARED;installNotice=true;notifyChanged();
            });
        });
    }
    private boolean releaseInstallSession(int id){
        if(id<0)return true;
        boolean released=UpdateSessionInstaller.abandon(context,id);
        if(!released)prefs.edit().putInt("installCleanup",id).commit();
        else if(prefs.getInt("installCleanup",-1)==id)prefs.edit().remove("installCleanup").commit();
        return released;
    }
    private void clearInstallSession(){
        int id=installSession;installSession=-1;installConfirmation=null;
        SharedPreferences.Editor record=prefs.edit().remove("installSession").remove("installPhase");
        if(id>=0)record.putInt("installCleanup",id);record.apply();
    }
    private void diagnosticStage(String stage,int id,String detail){
        InstallDiagnostics.stage("online_update",installAttempt,installRoute,stage,id,diagnosticInstallStarted,prefs.getString("diagnosticIdentity","identity=unknown"),detail);
    }
    private void diagnosticReadback(Boolean existingCheck){
        final String attempt=installAttempt,route=installRoute,pkg=prefs.getString("diagnosticPackage",""),version=prefs.getString("diagnosticVersion","");
        final long code=prefs.getLong("diagnosticCode",-1);final Set<String> signers=new HashSet<>(prefs.getStringSet("diagnosticSigners",Collections.emptySet()));
        if(!pkg.isEmpty())worker.execute(()->InstallDiagnostics.readback(context,"online_update",attempt,route,pkg,version,code,signers,-1,existingCheck));
    }
    private void installationFailed(String message){installationFailed(message,UpdateState.Install.FAILED);}
    private void installationFailed(String message,UpdateState.Install state){installState=state;installError=message;installNotice=true;prefs.edit().remove("installPhase").apply();notifyChanged();}
    private static String preparationMessage(String stage,Exception error){return "verify".equals(stage)?"安装包校验失败，请重新下载":error instanceof UpdateInstallPreparation.Failure?((UpdateInstallPreparation.Failure)error).userMessage():UpdateInstallPolicy.message("io_failed");}
    private static String preparationDetails(Exception error){return error instanceof UpdateInstallPreparation.Failure?((UpdateInstallPreparation.Failure)error).details():"reason="+(error instanceof UpdateTransfer.IntegrityException?"invalid":"io_failed");}
    private String installDetail(UpdateManifest.Artifact target){return "version="+target.version()+" build="+target.code+" api="+Build.VERSION.SDK_INT+" route="+installRoute;}
    private UpdateTransfer newTransfer(){return new UpdateTransfer(url->{
        HttpsURLConnection request=(HttpsURLConnection)url.openConnection();
        if(Build.VERSION.SDK_INT<=25){if(legacyTls==null)legacyTls=UpdateTls.create(context);request.setSSLSocketFactory(legacyTls);}
        return request;
    },"legacy14".equals(BuildConfig.UPDATE_COMPATIBILITY),BuildConfig.INDEPENDENT_BUILD);}
    private static byte[] read(InputStream input,int limit)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Update cache too large");out.write(buffer,0,n);}return out.toByteArray();}}
}
