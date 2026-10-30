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
    String installState="",installError="";
    private boolean installFallback;
    private String installAttempt="";
    private long lastWall,lastElapsed,lastProgress;
    UpdateManifest.Artifact available;
    boolean checking,downloading,verifying,ready,switching;
    long received,total;
    String checkError="",downloadError="",unavailableReason="";
    private UpdateRepository(Context context){
        this.context=context;prefs=context.getSharedPreferences("application-updates",0);
        directory=new File(context.getFilesDir(),"updates");cache=new File(context.getFilesDir(),"update-manifest.json");
        if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT)){
            installAttempt=prefs.getString("installAttempt","");installSession=prefs.getInt("installSession",-1);installFallback=prefs.getBoolean("installFallback",false);
            if(!prefs.getString("installPhase","").isEmpty()){
                installState="interrupted";installError=UpdateInstallPolicy.message("interrupted");installNotice=true;
            }
        }
        worker.execute(()->{try{
            key=read(context.getAssets().open("updates/public-key.der"),4096);
            if(cache.isFile()){manifest=UpdateManifest.verify(read(new FileInputStream(cache),UpdateManifest.MAX_BYTES),key,prefs.getLong("revision",0));prefs.edit().putLong("revision",manifest.revision).apply();}
        }catch(IOException e){DiagnosticLog.info("update.cache.unavailable",UpdateFailure.details("cache_read",e));}
            main.post(()->{reconcile();notifyChanged();});
        });
    }
    void add(Listener listener){listeners.add(listener);listener.changed();}
    void remove(Listener listener){listeners.remove(listener);}
    private void notifyChanged(){for(Listener listener:new ArrayList<>(listeners))listener.changed();}
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
                prefs.edit().remove("pendingVersion").remove("pendingCode").remove("installSession").remove("installPhase").remove("installHash").remove("installFallback").apply();
                installSession=-1;installConfirmation=null;installState="success";installNotice=true;
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
                if(failure.isEmpty()){manifest=found[0];prefs.edit().putLong("revision",manifest.revision).putLong("success",now()).remove("failure").putInt("manifestSource",source[0]).apply();reconcile();}
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
        if(!ready||available==null||verifying||downloading){installationFailed("更新状态已变化，请重新检查");return;}
        final UpdateManifest.Artifact target=available;installAttempt=Long.toHexString(System.nanoTime());installTarget=target;installFile=null;installConfirmation=null;
        int previous=installSession>=0?installSession:prefs.getInt("installCleanup",-1);installSession=-1;installFallback=false;installBusy=true;installNotice=false;installState="preparing";installError="";
        SharedPreferences.Editor record=prefs.edit().remove("installSession").putString("installPhase","preparing").putString("installHash",target.sha256).putString("installAttempt",installAttempt).putBoolean("installFallback",false)
                .putString("pendingVersion",target.version()).putLong("pendingCode",target.code);
        if(previous>=0)record.putInt("installCleanup",previous);record.apply();notifyChanged();
        worker.execute(()->{
            if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT)&&previous>=0&&!releaseInstallSession(previous)){
                main.post(()->{installBusy=false;installationFailed("上次安装任务无法结束，请重试或通过电脑更新");});return;
            }
            String stage="verify";File prepared=null;int session=-1;Exception error=null;
            DiagnosticLog.info("update.install.prepare","attempt="+installAttempt+" "+installDetail(target));
            try{
                UpdateInstaller.validate(context,target,file(target));stage="prepare";
                if(UpdateInstallPolicy.session(Build.VERSION.SDK_INT))session=UpdateSessionInstaller.prepare(context,target,file(target),id->{installSession=id;return prefs.edit().putInt("installSession",id).commit();});
                else prepared=UpdateInstaller.prepare(context,target,file(target));
            }catch(IOException|RuntimeException failure){error=failure;}
            final File result=prepared;final int id=session;final Exception failure=error;final String failedStage=stage;
            main.post(()->{
                installBusy=false;
                if(failure instanceof UpdateSessionInstaller.Failure){sessionFailed((UpdateSessionInstaller.Failure)failure);return;}
                if(failure!=null){
                    if("verify".equals(failedStage)){ready=false;prefs.edit().remove("readyHash").apply();}
                    installationFailed(preparationMessage(failedStage,failure));
                    DiagnosticLog.error("update.install.prepare_failed",installDetail(target)+" stage="+failedStage+" "+preparationDetails(failure),failure);return;
                }
                installFile=result;installSession=id;installState="prepared";installNotice=true;notifyChanged();
            });
        });
    }
    void dispatchInstall(android.app.Activity activity){
        if(!"prepared".equals(installState)||installBusy)return;installNotice=false;
        if(installSession<0){
            installState="file_dispatched";
            if(!UpdateInstaller.launch(activity,installFile))installationFailed(UpdateInstallPolicy.message("confirmation_missing"));
            else {prefs.edit().remove("installPhase").apply();notifyChanged();}return;
        }
        final int id=installSession;installBusy=true;installState="committing";prefs.edit().putString("installPhase","committing").apply();notifyChanged();
        worker.execute(()->{
            UpdateSessionInstaller.Failure error=null;
            try{UpdateSessionInstaller.commit(context,id);}catch(UpdateSessionInstaller.Failure failure){error=failure;}
            final UpdateSessionInstaller.Failure failure=error;
            main.post(()->{
                if(installSession!=id)return; // A result may arrive before commit returns.
                installBusy=false;
                if(failure!=null){sessionFailed(failure);return;}
                if("committing".equals(installState)){installState="submitted";prefs.edit().putString("installPhase","submitted").apply();}
                DiagnosticLog.info("update.install.session","step=commit session="+id);notifyChanged();
            });
        });
    }
    void installationResult(Intent intent){
        int id=prefs.getInt("installSession",-1);
        if(id<0||!UpdateSessionInstaller.action(context,id).equals(intent.getAction())||intent.getIntExtra("android.content.pm.extra.SESSION_ID",id)!=id){
            DiagnosticLog.info("update.install.result_ignored","reason=session_mismatch");return;
        }
        int status=intent.getIntExtra("android.content.pm.extra.STATUS",Integer.MIN_VALUE);
        if(status==Integer.MIN_VALUE)return;
        installSession=id;installBusy=false;
        String reason=status==-1?"confirmation":status==0?"success":UpdateInstallPolicy.result(status,intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE"));
        DiagnosticLog.info("update.install.result","attempt="+installAttempt+" route=session session="+id+" status="+status+" reason="+reason);
        if(status==-1){
            installConfirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(installConfirmation==null){fallbackInstall("confirmation_missing");return;}
            installState="confirmation";installNotice=true;prefs.edit().putString("installPhase","confirmation").apply();notifyChanged();return;
        }
        installSession=-1;installConfirmation=null;prefs.edit().remove("installSession").remove("installPhase").apply();
        if("invalid".equals(reason)){ready=false;prefs.edit().remove("readyHash").apply();}
        if(status==0){
            installState="awaiting_version";installError="安装结果待核对，请重新打开 Quiet TV";reconcile();
            if(!"success".equals(installState))installNotice=true;
            notifyChanged();
        }else installationFailed(UpdateInstallPolicy.message(reason),"cancelled".equals(reason)?"cancelled":"failed");
    }
    void confirmInstall(android.app.Activity activity){
        if(!"confirmation".equals(installState)||installConfirmation==null)return;
        Intent confirmation=installConfirmation;installConfirmation=null;installNotice=false;installState="confirming";
        try{
            activity.startActivity(confirmation);prefs.edit().putString("installPhase","confirming").apply();
            DiagnosticLog.info("update.install.session","step=confirm session="+installSession);notifyChanged();
        }catch(ActivityNotFoundException failure){fallbackInstall("confirmation_missing");}
        catch(RuntimeException failure){
            int id=installSession;clearInstallSession();worker.execute(()->releaseInstallSession(id));
            installationFailed(UpdateInstallPolicy.message(UpdateInstallPolicy.ioReason(failure)));
            DiagnosticLog.error("update.install.confirm_failed","reason="+UpdateInstallPolicy.ioReason(failure),failure);
        }
    }
    private void sessionFailed(UpdateSessionInstaller.Failure failure){
        DiagnosticLog.error("update.install.prepare_failed","attempt="+installAttempt+" requiredBytes="+(installTarget==null?-1:installTarget.bytes)+" "+failure.details(),failure);
        if("invalid".equals(failure.reason)){ready=false;prefs.edit().remove("readyHash").apply();}
        if(UpdateInstallPolicy.fallback(failure.reason)){fallbackInstall(failure.reason);return;}
        int id=installSession;clearInstallSession();worker.execute(()->releaseInstallSession(id));
        installationFailed(UpdateInstallPolicy.message(failure.reason));
    }
    private void fallbackInstall(String reason){
        final int id=installSession;clearInstallSession();
        final UpdateManifest.Artifact target=installTarget;
        if(!UpdateInstallPolicy.fallback(reason)||installFallback||target==null){
            worker.execute(()->releaseInstallSession(id));installationFailed(UpdateInstallPolicy.message(reason));return;
        }
        installFallback=true;installBusy=true;installNotice=false;installState="preparing";
        prefs.edit().putBoolean("installFallback",true).putString("installPhase","preparing").apply();
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
                installFile=result;installState="prepared";installNotice=true;notifyChanged();
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
    private void installationFailed(String message){installationFailed(message,"failed");}
    private void installationFailed(String message,String state){installState=state;installError=message;installNotice=true;prefs.edit().remove("installPhase").apply();notifyChanged();}
    private static String preparationMessage(String stage,Exception error){return "verify".equals(stage)?"安装包校验失败，请重新下载":error instanceof UpdateInstallPreparation.Failure?((UpdateInstallPreparation.Failure)error).userMessage():UpdateInstallPolicy.message("io_failed");}
    private static String preparationDetails(Exception error){return error instanceof UpdateInstallPreparation.Failure?((UpdateInstallPreparation.Failure)error).details():"reason="+(error instanceof UpdateTransfer.IntegrityException?"invalid":"io_failed");}
    private static String installDetail(UpdateManifest.Artifact target){return "version="+target.version()+" build="+target.code+" api="+Build.VERSION.SDK_INT+" route="+(UpdateInstallPolicy.session(Build.VERSION.SDK_INT)?"session":Build.VERSION.SDK_INT<24?"legacy_file":"content");}
    private UpdateTransfer newTransfer(){return new UpdateTransfer(url->{
        HttpsURLConnection request=(HttpsURLConnection)url.openConnection();
        if(Build.VERSION.SDK_INT<=25){if(legacyTls==null)legacyTls=UpdateTls.create(context);request.setSSLSocketFactory(legacyTls);}
        return request;
    },"legacy14".equals(BuildConfig.UPDATE_COMPATIBILITY));}
    private static byte[] read(InputStream input,int limit)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Update cache too large");out.write(buffer,0,n);}return out.toByteArray();}}
}
