package home.quiet.tv;

import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** The last successful list and navigation survive settings windows, without retaining a View. */
final class AppListCache {
    private static AppListCache instance;
    static synchronized AppListCache get(Context context){if(instance==null)instance=new AppListCache(context.getApplicationContext());return instance;}
    private final AppRepository repository;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> listeners=new CopyOnWriteArrayList<>();
    private List<AppRepository.App> values;
    private long revision,loadedRevision=-1,version;
    private boolean online;
    private int user=-1;
    private Bundle navigation;
    private AppListCache(Context context){
        repository=new AppRepository(context);
        BroadcastReceiver packages=new BroadcastReceiver(){@Override public void onReceive(Context ignored,Intent event){invalidate();}};
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)Api33.register(context,packages,filter);else context.registerReceiver(packages,filter);
    }
    synchronized List<AppRepository.App> snapshot(){return copy(values);}
    private static List<AppRepository.App> copy(List<AppRepository.App> source){ArrayList<AppRepository.App> result=new ArrayList<>();if(source!=null)for(AppRepository.App app:source)result.add(app.copy());return result;}
    synchronized boolean available(){return values!=null;}
    synchronized long version(){return version;}
    synchronized boolean stale(AdbController adb){return values==null||loadedRevision!=revision||online!=adb.connected()||online&&user!=adb.user;}
    void invalidate(){synchronized(this){revision++;}main.post(()->{for(Runnable listener:listeners)listener.run();});}
    void addListener(Runnable listener){listeners.addIfAbsent(listener);}
    void removeListener(Runnable listener){listeners.remove(listener);}
    synchronized Bundle navigation(){return navigation==null?null:new Bundle(navigation);}
    synchronized void navigation(Bundle state){navigation=new Bundle(state);}
    // All reads share ManagementAdb's existing worker, so queued callers reuse a completed read.
    void load(AdbController adb)throws Exception{
        long requested;List<AppRepository.App> previous;
        synchronized(this){if(!stale(adb))return;requested=revision;previous=snapshot();}
        List<AppRepository.App> ready=repository.load(adb,previous);
        synchronized(this){values=copy(ready);loadedRevision=requested;online=adb.connected();user=online?adb.user:-1;version++;}
    }
    @android.annotation.TargetApi(33) private static final class Api33 {static void register(Context context,BroadcastReceiver receiver,IntentFilter filter){context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);}}
}
