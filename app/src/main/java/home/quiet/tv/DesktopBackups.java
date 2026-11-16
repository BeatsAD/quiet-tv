package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Committed desktop fields schedule one restore point; slider previews never reach this recorder. */
final class DesktopBackups {
    interface Job<T> {T run(DesktopBackupStore store)throws IOException;}
    interface Result<T> {void complete(T value,String error);}
    private static DesktopBackups instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<SharedPreferences> preferences=new ArrayList<>();
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners=new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicLong changes=new AtomicLong();
    private volatile DesktopBackupStore store;
    private volatile Set<String> retained=Collections.emptySet();
    private volatile String error="",recoveryError="";
    private volatile boolean unknown,phoneRestoring;
    private java.lang.ref.WeakReference<Activity> foreground=new java.lang.ref.WeakReference<>(null);
    private final Runnable automatic=()->{long expected=changes.get();run(value->changes.get()==expected&&value.automatic()?value.capture("auto"):null,(value,failure)->{});};
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged=(prefs,key)->{
        if(key==null){changed();return;}int index=preferences.indexOf(prefs);Set<String> keys=index==0?DesktopBackupRecord.DESKTOP_KEYS:index==1?DesktopBackupRecord.WEATHER_KEYS:index==2?DesktopBackupRecord.DOCK_KEYS:DesktopBackupRecord.ORDER_KEYS;if(keys.contains(key))changed();
    };
    static synchronized DesktopBackups get(Context context){if(instance==null)instance=new DesktopBackups(context.getApplicationContext());return instance;}
    static void initialize(Context context){get(context);}
    private DesktopBackups(Context context){
        this.context=context;load();for(String name:new String[]{"desktop-settings","weather","dock","home-order"}){SharedPreferences value=context.getSharedPreferences(name,0);preferences.add(value);value.registerOnSharedPreferenceChangeListener(preferenceChanged);}
        if(store!=null)run(value->{String version=BuildConfig.VERSION_NAME+"/"+BuildConfig.VERSION_CODE;if(!version.equals(value.software())){File prefs=new File(context.getFilesDir().getParentFile(),"shared_prefs/desktop-settings.xml");if(prefs.isFile()||new File(context.getFilesDir(),"wallpapers/catalog.json").isFile())value.capture("upgrade");value.software(version);}return null;},(value,failure)->{});
    }
    private void load(){
        try{store=new DesktopBackupStore(context);retained=Collections.unmodifiableSet(store.retainedPaths());unknown=false;error=recoveryError="";}
        catch(IOException failure){store=null;unknown=true;error=failure.getMessage();try{File task=new File(context.getFilesDir(),"desktop-backups/restore.json");if(task.exists()||new File(task+".bak").exists()){org.json.JSONObject value=new org.json.JSONObject(new String(new androidx.core.util.AtomicFile(task).readFully(),Charsets.UTF_8));String phase=value.optString("phase");if(value.optInt("version")!=1||!phase.equals("complete")&&!phase.equals("rolled-back"))recoveryError=error;}}catch(IOException|org.json.JSONException invalid){recoveryError="桌面恢复任务无法读取，请重试或通过电脑处理";}}
    }
    static void changed(){DesktopBackups value=instance;if(value==null)return;value.changes.incrementAndGet();value.main.removeCallbacks(value.automatic);value.main.postDelayed(value.automatic,1200);}
    void beforeIconBatch()throws IOException{
        try{worker.submit(()->{if(!recoveryError.isEmpty())throw new IOException(recoveryError);DesktopBackupStore value=store();if(value.automatic()){value.capture("before");retained=Collections.unmodifiableSet(value.retainedPaths());}return null;}).get();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new InterruptedIOException("图标替换已取消");}
        catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof IOException)throw (IOException)cause;throw new IOException("替换前备份未完成",cause);}
    }
    static boolean retains(File file){
        DesktopBackups value=instance;if(value==null||file==null)return false;try{String path=file.getCanonicalPath();if(value.retained.contains(path))return true;String root=value.context.getFilesDir().getCanonicalPath()+File.separator;return value.unknown&&path.startsWith(root)&&DesktopBackupRecord.assetPath(path.substring(root.length()));}catch(IOException invalid){return true;}
    }
    DesktopBackupStore store()throws IOException{if(store==null)throw new IOException(error.isEmpty()?"备份记录暂不可用":error);return store;}
    <T> void run(Job<T> job,Result<T> result){
        worker.execute(()->{T value=null;String failure="";unknown=true;try{if(!recoveryError.isEmpty())throw new IOException(recoveryError);value=job.run(store());error="";}catch(IOException unavailable){failure=unavailable.getMessage();error=failure;try{if(store!=null&&store.recoveryPending())recoveryError=failure;}catch(IOException invalid){recoveryError=invalid.getMessage();}}finally{if(store!=null)try{retained=Collections.unmodifiableSet(store.retainedPaths());unknown=false;}catch(IOException invalid){unknown=true;}}T ready=value;String problem=failure;main.post(()->{Activity activity=foreground.get();if(activity!=null&&!recoveryError.isEmpty())redirect(activity);for(Runnable listener:listeners)listener.run();result.complete(ready,problem);});});
    }
    <T> void read(Job<T> job,Result<T> result){worker.execute(()->{T value=null;String failure="";try{value=job.run(store());}catch(IOException unavailable){failure=unavailable.getMessage();}T ready=value;String problem=failure;main.post(()->result.complete(ready,problem));});}
    void current(Result<DesktopBackupStore.Entry> result){
        worker.execute(()->{DesktopBackupStore.Entry entry=null;String failure="";try{if(!recoveryError.isEmpty())throw new IOException(recoveryError);DesktopBackupRecord record=(store==null?new DesktopBackupSnapshot(context):store.snapshots).capture("manual");org.json.JSONObject sources=new org.json.JSONObject();for(int i=0;i<record.assets.length();i++){String path=record.assets.getJSONObject(i).getString("path");sources.put(path,path);}entry=new DesktopBackupStore.Entry(record,sources);}catch(IOException|org.json.JSONException unavailable){failure=unavailable.getMessage();}DesktopBackupStore.Entry ready=entry;String problem=failure;main.post(()->result.complete(ready,problem));});
    }
    void export(DesktopBackupStore.Entry entry,boolean media,File target,DesktopBackupArchive.Active active,Result<File> result){
        worker.execute(()->{String failure="";try{if(!recoveryError.isEmpty())throw new IOException(recoveryError);DesktopBackupArchive archive=new DesktopBackupArchive(context,store);DesktopBackupRecord.require(target.getParentFile().isDirectory()||target.getParentFile().mkdirs(),"备份导出目录不可用");DesktopBackupRecord.require(target.getParentFile().getUsableSpace()>archive.estimate(entry.record,media)+DesktopBackupStore.RESERVE,"导出备份所需空间不足");try(FileOutputStream output=new FileOutputStream(target)){archive.export(entry,media,output,active);output.getFD().sync();}DesktopBackupRecord.require(active.valid(),"备份操作已取消");}catch(IOException unavailable){failure=unavailable.getMessage();}String problem=failure;main.post(()->result.complete(problem.isEmpty()?target:null,problem));});
    }
    void hold(DesktopBackupStore.Entry entry,Result<String> result){worker.execute(()->{String failure="";try{DesktopBackupStore.write(new androidx.core.util.AtomicFile(new File(context.getCacheDir(),"desktop-backup-"+entry.record.id+".json")),new org.json.JSONObject().put("record",entry.record.json).put("sources",entry.sources));}catch(IOException|org.json.JSONException unavailable){failure=unavailable.getMessage();}String problem=failure;main.post(()->result.complete(problem.isEmpty()?entry.record.id:null,problem));});}
    void held(String id,Result<DesktopBackupStore.Entry> result){worker.execute(()->{DesktopBackupStore.Entry entry=null;String failure="";try{DesktopBackupRecord.require(FileTransferPolicy.id(id),"导出草稿标识无效");org.json.JSONObject value=new org.json.JSONObject(new String(new androidx.core.util.AtomicFile(new File(context.getCacheDir(),"desktop-backup-"+id+".json")).readFully(),Charsets.UTF_8));entry=new DesktopBackupStore.Entry(new DesktopBackupRecord(value.getJSONObject("record")),value.getJSONObject("sources"));DesktopBackupRecord.require(entry.record.id.equals(id),"导出草稿不匹配");}catch(IOException|org.json.JSONException unavailable){failure=unavailable.getMessage();}DesktopBackupStore.Entry ready=entry;String problem=failure;main.post(()->result.complete(ready,problem));});}
    String receive(InputStream input,long length,DesktopBackupArchive.Active active)throws IOException{
        try{return worker.submit(()->{if(!recoveryError.isEmpty())throw new IOException(recoveryError);DesktopBackupStore value=store();DesktopBackupStore.Entry entry=new DesktopBackupArchive(context,value).read(input,length,active);DesktopBackupRecord.require(active.valid(),"备份操作已取消");return value.draft(entry);}).get();}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("备份导入已中断",interrupted);}
        catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof IOException)throw (IOException)cause;throw new IOException("备份导入未完成",cause);}
    }
    void retryRecovery(Runnable done){worker.execute(()->{load();main.post(done);});}
    boolean phoneRestoring(){return phoneRestoring;}
    void phoneRestore(Runnable ready){phoneRestoring=true;main.post(()->{Activity activity=foreground.get();if(activity!=null)redirect(activity);ready.run();});}
    void phoneRestoreFinished(){phoneRestoring=false;}
    String error(){return error;}
    String recoveryError(){return recoveryError;}
    void listen(Runnable listener){listeners.addIfAbsent(listener);}
    void unlisten(Runnable listener){listeners.remove(listener);}
    static boolean resumed(Activity activity){DesktopBackups value=instance;if(value!=null)value.foreground=new java.lang.ref.WeakReference<>(activity);return redirect(activity);}
    static void paused(Activity activity){DesktopBackups value=instance;if(value!=null&&value.foreground.get()==activity)value.foreground.clear();}
    static boolean redirect(Activity activity){
        DesktopBackups value=instance;if(value==null||!value.phoneRestoring&&value.recoveryError.isEmpty()||activity.isFinishing()||UiCompat.destroyed(activity)||activity instanceof DesktopRecoveryActivity||activity instanceof MaintenanceHelpActivity)return false;
        activity.getWindow().getDecorView().setVisibility(android.view.View.INVISIBLE);activity.startActivity(new Intent(activity,DesktopRecoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));activity.finish();return true;
    }
}
