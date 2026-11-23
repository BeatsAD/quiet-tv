package home.quiet.tv;

import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Application-owned authorization survives closing or recreating the Quick controls window. */
final class PhoneSettingsSession {
    private static final int PORT=8766;
    private static PhoneSettingsSession instance;
    static synchronized PhoneSettingsSession get(Context context){if(instance==null)instance=new PhoneSettingsSession(context.getApplicationContext());return instance;}
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final PhonePairing pairing;
    private final Set<BrowserBackend> browserBackends=new CopyOnWriteArraySet<>();private volatile String offeredFile="";
    private final PhoneSettingsTasks tasks=new PhoneSettingsTasks();
    private final Set<Runnable> observers=new HashSet<>(),configurationObservers=new HashSet<>(),mediaNameObservers=new HashSet<>();private volatile PhoneSettingsServer server;private boolean starting;private volatile int generation;private String failure="",host="",fallback="";
    private PhoneSettingsSession(Context context){
        this.context=context;SharedPreferences preferences=context.getSharedPreferences("phone-pairing",0);
        pairing=new PhonePairing(new PhonePairing.Storage(){public String get(String key){return preferences.getString(key,"");}public boolean put(String key,String value){return preferences.edit().putString(key,value).commit();}});
        androidx.core.content.ContextCompat.registerReceiver(context,new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(pairing.paired()||server!=null)start();}},new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    void restore(){if(pairing.paired())start();}
    boolean paired(){return pairing.paired();}
    void revoke(){try{if(server!=null)server.revokePairing();else pairing.revoke();failure="";fire();}catch(IOException error){failure=error.getMessage();fire();}}
    void observe(Runnable observer){observers.add(observer);}
    void forget(Runnable observer){observers.remove(observer);}
    void observeConfiguration(Runnable observer){configurationObservers.add(observer);}
    void forgetConfiguration(Runnable observer){configurationObservers.remove(observer);}
    void observeMediaNames(Runnable observer){mediaNameObservers.add(observer);}
    void forgetMediaNames(Runnable observer){mediaNameObservers.remove(observer);}
    private void mediaNamesChanged(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::mediaNamesChanged);return;}for(Runnable observer:new ArrayList<>(mediaNameObservers))observer.run();}
    private void fire(){main.post(()->{for(Runnable observer:new ArrayList<>(observers))observer.run();});}
    void configurationChanged(){if(Looper.myLooper()!=Looper.getMainLooper()){String operation=DiagnosticOperation.current();main.post(()->{String previous=DiagnosticOperation.enter(operation);try{configurationChanged();}finally{DiagnosticOperation.restore(previous);}});return;}for(Runnable observer:new ArrayList<>(configurationObservers))observer.run();DiagnosticLog.info("phone.configuration.notified","parent="+DiagnosticOperation.current()+" observers="+configurationObservers.size()+" evidence=callbacks_not_presentation");fire();}
    String state(){if(starting)return "starting";if(!failure.isEmpty())return "error";if(server==null)return "closed";String state=server.state();return paired()&&!"connected".equals(state)?"waiting":state;}
    String failure(){return failure;}
    String address(){PhoneSettingsServer current=server;return current==null?"":current.address();}
    String pairingCode(){PhoneSettingsServer current=server;return current==null?"":current.pairingCode();}
    void offerFile(String id)throws IOException{if(server==null||!Arrays.asList("waiting","connected").contains(state()))throw new IOException("请先开启手机连接");new FileTransferStore(new File(context.getFilesDir(),"received-files")).entry(id);offeredFile=id;for(BrowserBackend backend:browserBackends)backend.syncOffer();}
    void start(){
        if(starting)return;
        if(server!=null){
            String state=server.state();
            if(host.equals(WallpaperUploadPanel.lanAddress(context))){
                if(!"waiting".equals(state)&&!"connected".equals(state))server.reopen();
                return;
            }
            server.stop();server=null;
        }
        starting=true;failure="";int job=++generation;fire();
        worker.execute(()->{
            PhoneSettingsServer candidate=null;String error="",network=WallpaperUploadPanel.lanAddress(context);
            if(network==null||network.isEmpty())error="请先连接局域网";
            else try{
                boolean chinese;try(InputStream input=context.getAssets().open("launcher.json");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);JSONObject launcher=new JSONObject(output.toString("UTF-8"));chinese=!"en".equals(launcher.optString("language"));fallback="home".equals(launcher.optString("greeting"))?(chinese?"欢迎回家":"Welcome home"):"";}catch(JSONException problem){throw new IOException("桌面配置不可用",problem);}
                boolean language=chinese;
                candidate=new PhoneSettingsServer(network,PORT,pairing,name->context.getAssets().open("phone-settings/"+name),()->new BrowserBackend(job,language),this::fire);
                candidate.listen();
            }catch(IOException|RuntimeException problem){
                DiagnosticLog.error("phone.session.start_failed",problem);
                if(candidate!=null)candidate.stop();
                candidate=null;error="手机设置服务启动失败，请检查局域网和端口后重试";
            }
            PhoneSettingsServer ready=candidate;String message=error;
            main.post(()->{
                starting=false;
                if(!android.text.TextUtils.equals(network,WallpaperUploadPanel.lanAddress(context))){
                    if(ready!=null)ready.stop();
                    start();return;
                }
                server=ready;host=network;failure=message;fire();
                main.removeCallbacks(check);if(ready!=null)main.postDelayed(check,2000);
            });
        });
    }
    void end(){if(server!=null)server.end();fire();}
    void restart(){failure="";if(server!=null&&host.equals(WallpaperUploadPanel.lanAddress(context))){server.reopen();fire();}else start();}
    private void revokeForRestore(int expected){if(generation!=expected)return;PhoneSettingsServer current=server;if(current==null)return;current.end();main.post(()->{if(generation==expected&&server==current)end();});}
    private final Runnable check=new Runnable(){public void run(){if(server==null)return;if(!host.equals(WallpaperUploadPanel.lanAddress(context))){start();return;}server.state();main.postDelayed(this,5000);}};
    private <T>T onMain(Callable<T> operation)throws IOException {
        if(Looper.myLooper()==Looper.getMainLooper())try{return operation.call();}catch(IOException error){throw error;}catch(Exception error){throw new IOException(error);}
        FutureTask<T> task=new FutureTask<>(operation);main.post(task);
        try{return task.get(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();task.cancel(false);throw new IOException("设置未确认",error);}catch(ExecutionException error){Throwable cause=error.getCause();if(cause instanceof IOException)throw (IOException)cause;if(cause instanceof IllegalArgumentException)throw (IllegalArgumentException)cause;throw new IOException("设置未确认",cause);}catch(TimeoutException error){task.cancel(false);throw new IOException("设置未确认",error);}
    }
    private JSONObject snapshot()throws IOException {synchronized(DesktopSettingsStore.MUTATIONS){WallpaperStore wallpapers=WallpaperStore.get(context);synchronized(wallpapers){
        DesktopSettingsStore desktop=new DesktopSettingsStore(context);WallpaperCatalog catalog=wallpapers.snapshot();
        try{
            JSONArray revisionValues=new JSONArray().put(desktop.keySounds()).put(desktop.homeWallpaperBrightness()).put(desktop.greetingText(fallback)).put(catalog.current).put(catalog.rotating);
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(revisionValues.toString().getBytes(Charsets.UTF_8));StringBuilder revision=new StringBuilder();for(byte b:digest)revision.append(String.format(Locale.ROOT,"%02x",b&255));
            JSONArray library=new JSONArray();for(WallpaperCatalog.Entry entry:catalog.entries())library.put(new JSONObject().put("id",entry.id).put("name",entry.label(true)).put("video",entry.video()));
            return new JSONObject().put("revision",revision.toString()).put("keySounds",desktop.keySounds()).put("brightness",desktop.homeWallpaperBrightness()).put("greeting",desktop.greetingText(fallback)).put("wallpaper",catalog.current).put("wallpapers",library);
        }catch(JSONException|java.security.NoSuchAlgorithmException error){throw new IOException("设置读取失败",error);}
    }}}
    private byte[] currentBackground()throws IOException{
        CountDownLatch done=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<Bitmap> captured=new java.util.concurrent.atomic.AtomicReference<>();java.util.concurrent.atomic.AtomicBoolean waiting=new java.util.concurrent.atomic.AtomicBoolean(true);
        main.post(()->{try{HomeActivity.capturePhoneBackground(image->{synchronized(waiting){if(waiting.get())captured.set(image);else if(image!=null)image.recycle();done.countDown();}});}catch(RuntimeException|OutOfMemoryError unavailable){done.countDown();}});
        try{done.await(3,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}finally{synchronized(waiting){waiting.set(false);}}
        Bitmap image=captured.get();if(Thread.currentThread().isInterrupted()){if(image!=null)image.recycle();throw new IOException("背景预览已中断");}if(image==null)throw new IOException("电视当前背景暂不可用，请返回 QuietTV 桌面后重试");
        try{ByteArrayOutputStream output=new ByteArrayOutputStream();if(!image.compress(Bitmap.CompressFormat.JPEG,85,output))throw new IOException("背景预览不可用");return output.toByteArray();}catch(RuntimeException|OutOfMemoryError unavailable){throw new IOException("背景预览不可用",unavailable);}finally{image.recycle();}
    }
    private final class BrowserBackend implements PhoneSettingsServer.Backend {
        private final PhoneSettingsConfiguration configuration;private final PhoneSettingsState protocol;
        BrowserBackend(int job,boolean chinese){configuration=new PhoneSettingsConfiguration(context,chinese,fallback,tasks,PhoneSettingsSession.this::configurationChanged,PhoneSettingsSession.this::mediaNamesChanged,()->revokeForRestore(job));protocol=new PhoneSettingsState(configuration);browserBackends.add(this);try{syncOffer();}catch(IOException missing){DiagnosticLog.info("phone.file.offer_unavailable","source=browser_connect");}}
        synchronized void syncOffer()throws IOException{String id=offeredFile;if(!id.isEmpty())configuration.offerFile(id);}
        public PhoneSettingsState protocol(){return protocol;}
        public void connectionEnded(){configuration.closed();browserBackends.remove(this);}
        public PhoneSettingsServer.Download previewVideo(String id)throws IOException{return configuration.previewVideo(id);}
        public byte[] resource(String route)throws IOException{return configuration.resource(route);}
        public void delivered(String id){configuration.delivered(id);}
        public PhoneSettingsServer.Download download(String route)throws IOException{return configuration.download(route);}
        public JSONObject about(boolean runtime)throws IOException{return configuration.about(runtime);}
        public JSONObject places(String query)throws IOException{return configuration.places(query);}
        public JSONObject parent(JSONObject value,PhoneSettingsServer.Active active)throws IOException{return configuration.parent(value,active);}
        public JSONObject task(String id)throws IOException{return configuration.task(id);}
        public JSONObject upload(String id,InputStream input,long length,PhoneSettingsServer.Active active)throws IOException{return configuration.upload(id,input,length,active);}
        public JSONObject snapshot()throws IOException{return PhoneSettingsSession.this.snapshot();}
        public JSONObject change(String field,Object value,String revision,PhoneSettingsServer.Active active)throws IOException{
            synchronized(DesktopSettingsStore.MUTATIONS){WallpaperStore wallpapers=WallpaperStore.get(context);synchronized(wallpapers){
                if(!active.valid())throw new PhoneSettingsServer.Ended();JSONObject before=PhoneSettingsSession.this.snapshot();if(!before.optString("revision").equals(revision))throw new PhoneSettingsServer.Conflict();
                DesktopSettingsStore desktop=new DesktopSettingsStore(context);
                switch(field){
                    case "keySounds":if(!(value instanceof Boolean))throw new IllegalArgumentException();desktop.savePhoneValue("key-sounds",value);break;
                    case "brightness":if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue()||((Number)value).intValue()<20||((Number)value).intValue()>100)throw new IllegalArgumentException();desktop.savePhoneValue("home-wallpaper-brightness",((Number)value).intValue());break;
                    case "greeting":if(!(value instanceof String))throw new IllegalArgumentException();String text=((String)value).replace('\r',' ').replace('\n',' ').replace('\t',' ').trim();if(text.length()>GreetingInputServer.MAX_TEXT)throw new IllegalArgumentException();for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<32||c==127)throw new IllegalArgumentException();if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException();}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException();}desktop.savePhoneValue("greeting-text",text);break;
                    case "wallpaper":if(!(value instanceof String)||wallpapers.snapshot().find((String)value)==null)throw new IllegalArgumentException();if(WallpaperCatalog.DAILY.equals(value)&&!wallpapers.cached(wallpapers.snapshot().dailyImages.get(wallpapers.snapshot().dailySource)))throw new PhoneSettingsServer.Rejected("每日壁纸尚未准备，请使用每日更新入口");wallpapers.select((String)value);break;
                    default:throw new IllegalArgumentException();
                }
                configurationChanged();return PhoneSettingsSession.this.snapshot();
            }}
        }
        public byte[] previewImage(String id)throws IOException {
            if(WallpaperCatalog.DEFAULT.equals(id))return currentBackground();
            WallpaperStore wallpapers=WallpaperStore.get(context);WallpaperCatalog.Entry entry=wallpapers.snapshot().find(id);if(entry==null)throw new IOException("背景不可用");
            Bitmap bitmap=null;try{bitmap=WallpaperImages.decode(context,wallpapers,entry,1280,720);ByteArrayOutputStream output=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,output))throw new IOException("背景预览不可用");return output.toByteArray();}catch(OutOfMemoryError error){throw new IOException("背景预览不可用",error);}finally{if(bitmap!=null)bitmap.recycle();}
        }
        public byte[] thumbnail(String id)throws IOException {
            if("__current-background".equals(id))return currentBackground();
            WallpaperStore wallpapers=WallpaperStore.get(context);WallpaperCatalog.Entry entry=wallpapers.snapshot().find(id);if(entry==null)throw new IOException("背景不可用");
            if(WallpaperCatalog.DEFAULT.equals(id))return onMain(HomeActivity::phoneBackground);
            Bitmap bitmap=null;try{bitmap=WallpaperImages.decode(context,wallpapers,entry,480,270);ByteArrayOutputStream output=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,85,output))throw new IOException("背景预览不可用");return output.toByteArray();}catch(OutOfMemoryError error){throw new IOException("背景预览不可用",error);}finally{if(bitmap!=null)bitmap.recycle();}
        }
    }
}
