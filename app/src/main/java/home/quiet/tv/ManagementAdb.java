package home.quiet.tv;

import android.content.Context;
import android.os.*;
import android.provider.Settings;
import java.util.concurrent.*;

/** One shared local TCP connection, opened explicitly and released when management leaves. */
final class ManagementAdb {
    interface Listener {void changed();}
    private static ManagementAdb instance;
    static synchronized ManagementAdb get(Context context){if(instance==null)instance=new ManagementAdb(context.getApplicationContext());return instance;}
    static synchronized ManagementAdb existing(){return instance;}
    final AdbController adb;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final Runnable dispatch=()->{for(Listener listener:listeners)listener.changed();};
    private final AdbConnectionKeeper keeper;
    private ManagementAdb(Context context){
        adb=new AdbController(context,value->{});
        keeper=new AdbConnectionKeeper(new AdbConnectionKeeper.Client(){
            public boolean available(){
                if(!HomeGuardState.unlocked(context))return false;
                try{return (Build.VERSION.SDK_INT>=17?Api17.enabled(context):Settings.Secure.getInt(context.getContentResolver(),"adb_enabled",-1))!=0;}
                catch(SecurityException unknown){return true;} // The verified protocol remains authoritative.
            }
            public boolean connected(){return adb.connected();}
            public void connect() throws Exception {adb.connect(context.getSharedPreferences("adb",0).getInt("port",5555));}
            public void check() throws Exception {adb.validateUser();}
            public void disconnect(){adb.disconnect();}
        },worker,()->{main.removeCallbacks(dispatch);main.post(dispatch);});
    }
    @android.annotation.TargetApi(17)
    private static final class Api17 {
        static int enabled(Context context){return Settings.Global.getInt(context.getContentResolver(),"adb_enabled",-1);}
    }
    void request(){keeper.request();}
    void disconnect(){keeper.disconnect();}
    AdbConnectionKeeper.State state(){return keeper.state();}
    String detail(){return keeper.detail();}
    void addListener(Listener listener){listeners.addIfAbsent(listener);}
    void removeListener(Listener listener){listeners.remove(listener);}
}
