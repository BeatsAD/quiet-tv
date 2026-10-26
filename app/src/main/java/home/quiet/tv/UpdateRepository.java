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
    private long lastWall,lastElapsed,lastProgress;
    UpdateManifest.Artifact available;
    boolean checking,downloading,verifying,ready,switching;
    long received,total;
    String checkError="",downloadError="",unavailableReason="";
    private UpdateRepository(Context context){
        this.context=context;prefs=context.getSharedPreferences("application-updates",0);
        directory=new File(context.getFilesDir(),"updates");cache=new File(context.getFilesDir(),"update-manifest.json");
        worker.execute(()->{try{
            key=read(context.getAssets().open("updates/public-key.der"),4096);
            if(cache.isFile()){manifest=UpdateManifest.verify(read(new FileInputStream(cache),UpdateManifest.MAX_BYTES),key,prefs.getLong("revision",0));prefs.edit().putLong("revision",manifest.revision).apply();}
        }catch(IOException e){DiagnosticLog.info("update.cache.unavailable","no_verified_cache");}
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
            if(!installedTarget.isEmpty()&&device.code>=installedCode&&UpdateManifest.compare(device.version,installedTarget)>=0)prefs.edit().remove("pendingVersion").remove("pendingCode").apply();
            // A partial file of the right length still needs full validation on the worker.
            if(!downloading&&!verifying&&available!=null&&!ready&&available.sha256.equals(prefs.getString("readyHash",""))&&file().isFile()){
                final UpdateManifest.Artifact candidate=available;verifying=true;
                worker.execute(()->{boolean valid=false;try{UpdateInstaller.validate(context,candidate,file(candidate));valid=true;}catch(IOException|RuntimeException failure){DiagnosticLog.info("update.cached_apk.failed",failure.getClass().getSimpleName()+" "+failure.getMessage());}
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
            final UpdateManifest[] found={null};final byte[][] envelope={null};final int[] source={-1};String error="";
            try{
                if(key==null)throw new IOException("Update key unavailable");
                transfer.manifest(prefs.getInt("manifestSource",-1),(bytes,index)->{found[0]=UpdateManifest.verify(bytes,key,prefs.getLong("revision",0));envelope[0]=bytes;source[0]=index;return found[0].revision;});
                if(generation!=checkGeneration)return;
                // Cache and its preference revision are written only after verification and cancellation checks.
                synchronized(this){if(generation!=checkGeneration)return;try(FileOutputStream out=new FileOutputStream(cache)){out.write(envelope[0]);out.getFD().sync();}}
            }catch(IOException|RuntimeException e){error="暂时无法检查更新";DiagnosticLog.info("update.check.failed",e.getClass().getSimpleName());}
            final String failure=error;
            main.post(()->{if(generation!=checkGeneration)return;checking=false;checkTransfer=null;checkError=failure;
                if(failure.isEmpty()){manifest=found[0];prefs.edit().putLong("revision",manifest.revision).putLong("success",now()).remove("failure").putInt("manifestSource",source[0]).apply();reconcile();}
                else prefs.edit().putLong("failure",now()).apply();notifyChanged();});
        });
    }
    synchronized void cancelCheck(){++checkGeneration;if(checkTransfer!=null)checkTransfer.cancel();checkTransfer=null;checking=false;notifyChanged();}
    void download(){
        if(available==null||downloading)return;final UpdateManifest.Artifact target=available;
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
                DiagnosticLog.info("update.download.failed",e.getClass().getSimpleName()+" "+e.getMessage());}
            final String failure=error;final int successfulSource=source;
            main.post(()->{if(generation!=downloadGeneration)return;downloading=false;verifying=false;switching=false;downloadTransfer=null;downloadError=failure;ready=failure.isEmpty();
                if(ready)prefs.edit().putInt("apkSource",successfulSource).putString("readyHash",target.sha256).apply();else prefs.edit().remove("readyHash").apply();notifyChanged();});
        });
    }
    void cancelDownload(){++downloadGeneration;if(downloadTransfer!=null)downloadTransfer.cancel();downloadTransfer=null;downloading=false;verifying=false;switching=false;notifyChanged();}
    File file(){return available==null?null:file(available);}
    private File file(UpdateManifest.Artifact target){return new File(directory,target.sha256+".apk");}
    void install(ActivityResult callback){
        if(!ready||available==null||verifying){callback.checked(null,"更新状态已变化，请重新检查");return;}UpdateManifest.Artifact target=available;verifying=true;notifyChanged();
        worker.execute(()->{String error="";try{UpdateInstaller.validate(context,target,file(target));}catch(IOException|RuntimeException e){error="安装包校验失败，请重新下载";}
            final String failure=error;main.post(()->{verifying=false;if(!failure.isEmpty()){ready=false;prefs.edit().remove("readyHash").apply();}notifyChanged();callback.checked(failure.isEmpty()?file(target):null,failure);});});
    }
    interface ActivityResult {void checked(File file,String error);}
    void dispatched(){if(available!=null)prefs.edit().putString("pendingVersion",available.version()).putLong("pendingCode",available.code).apply();}
    private UpdateTransfer newTransfer(){return new UpdateTransfer(url->{
        HttpsURLConnection request=(HttpsURLConnection)url.openConnection();
        if(Build.VERSION.SDK_INT<21){if(legacyTls==null)legacyTls=UpdateTls.create(context);request.setSSLSocketFactory(legacyTls);}
        return request;
    });}
    private static byte[] read(InputStream input,int limit)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Update cache too large");out.write(buffer,0,n);}return out.toByteArray();}}
}
