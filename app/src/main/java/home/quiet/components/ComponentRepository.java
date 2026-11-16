package home.quiet.components;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import androidx.core.util.AtomicFile;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Two opt-in components. Downloading never starts a node or changes wallpaper selection. */
public final class ComponentRepository {
    public static final String INDEX="https://github.com/BeatsAD/quiet-tv/releases/download/v2.0.0/QuietTV_components.json";
    public static final String WEATHER_INDEX="https://github.com/BeatsAD/quiet-tv-components/releases/download/weather-v1.0.0/QuietTV_components.json";
    public interface Listener {void changed();}
    private static ComponentRepository instance;
    public static synchronized ComponentRepository get(Context context){if(instance==null)instance=new ComponentRepository(context);return instance;}
    private final Context context;private final String target;private final File root;private final SharedPreferences preferences;
    private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new CopyOnWriteArraySet<>();private final Map<String,State> states=new HashMap<>();
    private final Map<String,File> ready=new HashMap<>();private final Map<String,ComponentManifest.Artifact> installed=new HashMap<>();
    private final Map<String,Long> records=new HashMap<>();private final Set<String> restoring=new HashSet<>();
    private final Map<String,ComponentManifest> catalogs=new HashMap<>();private String loadedHash="";
    private final Set<String> refreshing=new HashSet<>(),refreshed=new HashSet<>();
    private static final class State {volatile String state="missing",message="",task="";volatile long received,total;volatile boolean cancelled;volatile ComponentTransfer transfer;}
    private ComponentRepository(Context supplied){
        context=supplied.getApplicationContext();target=context.getPackageName().startsWith("home.quiet.mobile")?"phone":"tv";root=new File(context.getFilesDir(),"components");preferences=context.getSharedPreferences("optional-components-v1",Context.MODE_PRIVATE);
        for(String id:new String[]{"weather","tailscale"}){State s=new State();if(preferences.getBoolean(id+".working",false)){s.state="failed";s.message="上次下载已中断，请重新下载";}states.put(id,s);}
        worker.execute(()->{for(String id:new String[]{"weather","tailscale"})restore(id);notifyChanged();});
    }
    public void add(Listener listener){listeners.add(listener);listener.changed();refresh();}
    public void remove(Listener listener){listeners.remove(listener);}
    private String indexUrl(String id){return id.equals("weather")?WEATHER_INDEX:INDEX;}
    private long minimumRevision(String id){return preferences.getLong(id+".revision",preferences.getLong("revision",0));}
    public synchronized void refresh(){for(String id:new String[]{"weather","tailscale"}){if(refreshing.contains(id)||refreshed.contains(id)||!reason(id).isEmpty())continue;refreshing.add(id);worker.execute(()->{try{ComponentManifest found=ComponentManifest.verify(fetchIndex(id,transfer(id,null)),key(),minimumRevision(id));synchronized(this){catalogs.put(id,found);}}catch(IOException e){State s=states.get(id);if(s.state.equals("missing"))s.message=e.getMessage();}finally{synchronized(this){refreshing.remove(id);refreshed.add(id);}notifyChanged();}});}}
    private void notifyChanged(){main.post(()->{for(Listener listener:listeners)listener.changed();});}
    public String reason(String id){
        if(!id.equals("weather")&&!id.equals("tailscale"))return "不支持的组件";
        if(id.equals("tailscale")){if(Build.VERSION.SDK_INT<26)return "需要64位安卓8及以上系统";if(!android.os.Process.is64Bit()||!Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"))return "需要ARM64应用运行环境";return "";}
        if(!target.equals("tv"))return "天气资源保存在电视";
        if(Build.VERSION.SDK_INT<21)return "当前系统不支持动态天气";
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null||manager.isLowRamDevice()||manager.getDeviceConfigurationInfo().reqGlEsVersion<0x30000)return "当前设备不支持动态天气效果";return "";
    }
    public synchronized boolean isReady(String id){File record=new File(root,id+".json");if(record.isFile()&&!Long.valueOf(record.lastModified()).equals(records.get(id))&&restoring.add(id))worker.execute(()->{try{restore(id);}finally{synchronized(this){restoring.remove(id);}notifyChanged();}});return reason(id).isEmpty()&&ready.containsKey(id);}
    public static final class Resources {
        private final File directory;private final ComponentManifest.Artifact artifact;
        private Resources(File directory,ComponentManifest.Artifact artifact){this.directory=directory;this.artifact=artifact;}
        public File file(String name)throws IOException{ComponentManifest.Entry entry=artifact.files.get(name);if(entry==null)throw new IOException("组件不包含此资源");File f=new File(directory,name);if(!f.isFile()||f.length()!=entry.bytes)throw new IOException("组件资源不完整");return f;}
    }
    public synchronized Resources resources(String id)throws IOException{if(!isReady(id))throw new IOException("需要先下载资源才能使用");return new Resources(ready.get(id),installed.get(id));}
    public synchronized File file(String id,String name)throws IOException{
        ComponentManifest.Artifact a=installed.get(id);File directory=ready.get(id);
        if(directory==null||a==null||!a.files.containsKey(name))throw new IOException("需要先下载组件才能使用");File f=new File(directory,name);
        if(!f.isFile()||f.length()!=a.files.get(name).bytes)throw new IOException("组件文件不完整，请重新下载");return f;
    }
    public synchronized JSONObject state(String id){
        State s=states.get(id);if(s==null)throw new IllegalArgumentException("component");String reason=reason(id);ComponentManifest catalog=catalogs.get(id);ComponentManifest.Artifact a=catalog==null?installed.get(id):catalog.select(target,id);
        try{return new JSONObject().put("id",id).put("target",target).put("supported",reason.isEmpty()).put("reason",reason)
            .put("state",reason.isEmpty()?s.state:"unsupported").put("message",s.message).put("taskId",s.task).put("ready",isReady(id)).put("version",a==null?"":a.version)
            .put("downloadedBytes",s.received).put("totalBytes",s.total>0?s.total:a==null?0:a.bytes).put("expandedBytes",a==null?0:a.expandedBytes).put("updatePending",id.equals("tailscale")&&!loadedHash.isEmpty()&&installed.get(id)!=null&&!loadedHash.equals(installed.get(id).sha256));
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    public synchronized JSONObject snapshot(){try{return new JSONObject().put("hostApi",1).put("weather",state("weather")).put("tailscale",state("tailscale"));}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized String start(String id)throws IOException{
        if(!reason(id).isEmpty())throw new IOException(reason(id));State s=states.get(id);
        if(Arrays.asList("downloading","checking","verifying").contains(s.state))return s.task;
        s.task=UUID.randomUUID().toString();s.state="checking";s.message="正在读取组件信息";s.received=0;s.total=0;s.cancelled=false;preferences.edit().putBoolean(id+".working",true).apply();notifyChanged();worker.execute(()->download(id,s));return s.task;
    }
    public synchronized void cancel(String id){State s=states.get(id);if(s!=null&&Arrays.asList("downloading","checking","verifying").contains(s.state)){s.cancelled=true;if(s.transfer!=null)s.transfer.cancel();}}
    private void alive(State s)throws IOException{if(s.cancelled)throw new InterruptedIOException("下载已取消");}
    private void download(String id,State s){
        ComponentTransfer transfer=transfer(id,s);s.transfer=transfer;
        try{
            alive(s);byte[] envelope=fetchIndex(id,transfer);ComponentManifest index=ComponentManifest.verify(envelope,key(),minimumRevision(id));
            ComponentManifest.Artifact a=index.select(target,id);if(a==null)throw new IOException("当前组件尚未发布");if(Build.VERSION.SDK_INT<a.minSdk)throw new IOException("当前系统不支持此组件");
            synchronized(this){catalogs.put(id,index);s.total=a.bytes;if(installed.containsKey(id)&&ready.containsKey(id)&&installed.get(id).sha256.equals(a.sha256)){s.state="ready";s.message="组件已是当前版本";s.received=a.bytes;preferences.edit().putLong(id+".revision",index.revision).apply();return;}s.state="downloading";s.message="正在下载";}notifyChanged();
            if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建组件目录");
            // A signed artifact hash is stable across sources, manual retries and process restarts.
            File archive=new File(root,"download-"+id+"-"+a.sha256+".zip");s.received=archive.isFile()?Math.min(archive.length(),a.bytes):0;
            if(root.getUsableSpace()<a.bytes-s.received+a.expandedBytes+4L*1024*1024)throw new IOException("设备空间不足，请释放空间后重试");
            transfer.download(a.url,a.bytes,a.sha256,archive);alive(s);s.state="verifying";s.message="正在校验";notifyChanged();
            File directory=ComponentFiles.install(root,archive,a,()->alive(s));alive(s);
            JSONObject receipt=new JSONObject().put("directory",directory.getName()).put("envelope",new String(envelope,"UTF-8"));AtomicFile record=new AtomicFile(new File(root,id+".json"));FileOutputStream out=record.startWrite();
            try{out.write(receipt.toString().getBytes("UTF-8"));record.finishWrite(out);}catch(Exception e){record.failWrite(out);throw e;}
            synchronized(this){ready.put(id,directory);installed.put(id,a);records.put(id,new File(root,id+".json").lastModified());s.state="ready";s.message="";}
            preferences.edit().putLong(id+".revision",index.revision).apply();
        }catch(Exception e){s.state=s.cancelled?"cancelled":"failed";s.message=s.cancelled?"下载已取消":e instanceof IOException?e.getMessage():"组件未能完成校验，请重试";}
        finally{s.transfer=null;preferences.edit().putBoolean(id+".working",false).apply();notifyChanged();}
    }
    private void restore(String id){
        File record=new File(root,id+".json");if(!record.isFile())return;
        try{
            JSONObject j=new JSONObject(new String(ComponentFiles.read(new AtomicFile(record).openRead(),ComponentManifest.MAX_BYTES*2),"UTF-8"));ComponentManifest index=ComponentManifest.verify(j.getString("envelope").getBytes("UTF-8"),key(),0);ComponentManifest.Artifact a=index.select(target,id);
            String name=j.getString("directory");if(a==null||!name.matches(id+"-[a-zA-Z0-9.-]{1,100}"))throw new IOException("组件记录无效");if(!reason(id).isEmpty()){synchronized(this){records.put(id,record.lastModified());}return;}File directory=new File(root,name);ComponentFiles.verify(directory,a);
            synchronized(this){ready.put(id,directory);installed.put(id,a);records.put(id,record.lastModified());State s=states.get(id);s.state="ready";s.message="";s.total=a.bytes;s.received=a.bytes;}
        }catch(Exception e){synchronized(this){records.put(id,record.lastModified());ready.remove(id);installed.remove(id);}State s=states.get(id);s.state="failed";s.message="本地组件无法验证，请重新下载";}
    }
    public synchronized void loadCore()throws IOException{
        if(!loadedHash.isEmpty())return;if(!isReady("tailscale"))throw new IOException("需要先下载组网组件");File library=file("tailscale","libquietremote.so");
        try{System.load(library.getAbsolutePath());loadedHash=installed.get("tailscale").sha256;}catch(UnsatisfiedLinkError|SecurityException e){throw new IOException("组网组件无法在此设备加载，请重新下载",e);}
    }
    private byte[] key()throws IOException{return ComponentFiles.read(context.getAssets().open("components/public-key.der"),4096);}
    private byte[] fetchIndex(String id,ComponentTransfer transfer)throws IOException{
        byte[] publicKey=key();long revision=minimumRevision(id);
        return transfer.manifest(indexUrl(id),ComponentManifest.MAX_BYTES,bytes->ComponentManifest.verify(bytes,publicKey,revision).revision);
    }
    private ComponentTransfer transfer(String id,State s){
        return new ComponentTransfer(()->{if(s!=null)alive(s);},new ComponentTransfer.Listener(){
            private long last;
            @Override public void source(String stage,String name,int attempt,boolean switching){
                android.util.Log.i("QuietTVComponents","id="+id+" stage="+stage+" source="+name+" attempt="+(attempt+1));
                if(s==null)return;
                s.message=attempt>=DownloadSources.values().length?"正在重试下载源":switching?"正在切换下载源":stage.equals("manifest")?"正在读取组件信息":s.received>0?"正在继续下载":"正在下载";notifyChanged();
            }
            @Override public void progress(long bytes,long total){
                if(s==null)return;if(bytes>s.received)s.message="正在下载";s.received=bytes;s.total=total;long now=SystemClock.elapsedRealtime();
                if(now-last>150){last=now;notifyChanged();}
            }
            @Override public void failed(String stage,String name,IOException error){
                String detail=error instanceof ComponentTransfer.HttpFailure?error.getMessage():error.getClass().getSimpleName();
                android.util.Log.w("QuietTVComponents","id="+id+" stage="+stage+" source="+name+" failure="+detail);
            }
        });
    }
}
