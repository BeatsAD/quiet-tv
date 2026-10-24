package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One foreground owner; automatic connections only refresh reads, never replay writes. */
final class AdbSession {
    interface Listener {void changed(AdbState state);void connected();}
    final AdbController adb;
    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService worker;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final AdbStateReader reader;
    private final ContentObserver observer;
    private LocalAdbDiscovery discovery;
    private volatile boolean active;
    private volatile boolean suspended;
    private boolean registered,attempted,working,manual,protocolEnabled,manualTls;
    private volatile int epoch;
    private AdbState.Debug debug=AdbState.Debug.CHECKING;
    private AdbState.Connection connection=AdbState.Connection.DISCONNECTED;
    private String detail="";
    private final Runnable suspensionTimeout=()->{if(suspended && !active)stop();};
    AdbSession(Context context,Listener listener){this(context,Executors.newSingleThreadExecutor(),listener);}
    ExecutorService worker(){return worker;}
    AdbSession(Context context,ExecutorService worker,Listener listener){
        this.context=context;this.worker=worker;this.listener=listener;
        preferences=context.getSharedPreferences("adb",0);reader=new AdbStateReader(context.getContentResolver());
        adb=new AdbController(context,this::controllerChanged);
        observer=new ContentObserver(main){@Override public void onChange(boolean selfChange){settingsChanged();}};
    }
    AdbState state(){return new AdbState(protocolEnabled?AdbState.Debug.ENABLED:debug,connection,detail,!preferences.getBoolean("autoConnect",true));}
    private void publish(){if(active)listener.changed(state());}
    private void controllerChanged(String value){
        AdbController.Stage stage=adb.stage();int generation=adb.generation();
        main.post(()->{if((!active && !suspended) || generation!=adb.generation())return;
            if(stage==AdbController.Stage.CONNECTED){protocolEnabled=true;working=false;if(manual){remember(manualTls);manual=false;}connection=AdbState.Connection.CONNECTED;}
            else if(stage==AdbController.Stage.IDLE){if(connection!=AdbState.Connection.FAILED)connection=AdbState.Connection.DISCONNECTED;}
            else {connection=AdbState.Connection.CONNECTING;if(stage==AdbController.Stage.SHELL || stage==AdbController.Stage.USER || stage==AdbController.Stage.CAPABILITIES)protocolEnabled=true;}
            detail=value;publish();
        });
    }
    void resume(){
        if(active){settingsChanged();publish();return;}
        if(suspended){suspended=false;active=true;main.removeCallbacks(suspensionTimeout);observeSettings();settingsChanged();publish();return;}
        active=true;attempted=false;manual=false;working=false;protocolEnabled=false;reader.reset();debug=reader.read();detail="";
        connection=adb.connected()?AdbState.Connection.CONNECTED:AdbState.Connection.DISCONNECTED;
        observeSettings();
        publish();autoConnect();
    }
    private void observeSettings(){
        if(!registered)try{context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),false,observer);context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(AdbStateReader.WIFI),false,observer);registered=true;}catch(SecurityException ignored){context.getContentResolver().unregisterContentObserver(observer);}
    }
    private void settingsChanged(){
        if(!active)return;AdbState.Debug next=reader.read();boolean changed=next!=debug;debug=next;
        if(next==AdbState.Debug.DISABLED){cancel();protocolEnabled=false;detail="系统调试已关闭";}
        else if(changed){attempted=false;}
        publish();if(changed)autoConnect();
    }
    private void autoConnect(){
        if(!active || manual || working || attempted || adb.connected() || debug==AdbState.Debug.DISABLED || !preferences.getBoolean("autoConnect",true))return;
        attempted=true;working=true;connection=AdbState.Connection.CONNECTING;detail="正在连接本机  如出现调试授权弹窗，请选择允许";publish();
        final int run=++epoch;
        boolean tls=AdbController.pairingAvailable() && ("tls".equals(preferences.getString("lastTransport","tcp")) || readerWifiEnabled());
        if(tls){
            discovery=new LocalAdbDiscovery(context,(pairing,port)->{if(!pairing && discovery!=null && valid(run)){closeDiscovery();connect(run,port,true);}});
            main.postDelayed(()->{if(valid(run) && discovery!=null){closeDiscovery();failed(run,"未发现本机无线连接端口，请打开连接设置检查无线调试或配对");}},10000);
        }else connect(run,preferences.getInt("port",5555),false);
    }
    private boolean readerWifiEnabled(){try{return Settings.Global.getInt(context.getContentResolver(),AdbStateReader.WIFI,-1)==1;}catch(SecurityException e){return false;}}
    private boolean valid(int run){return (active || suspended) && run==epoch;}
    private void connect(int run,int port,boolean tls){
        final int request=adb.prepare();
        worker.execute(()->{if(!valid(run))return;try{adb.connect(port,request);main.post(()->{if(!valid(run))return;working=false;manual=false;remember(tls);connection=AdbState.Connection.CONNECTED;protocolEnabled=true;detail="已核对本机与当前用户";publish();if(active)listener.connected();});}
            catch(Exception e){main.post(()->failed(run,e.getMessage()==null?"连接失败":e.getMessage()));}});
    }
    private void failed(int run,String reason){if(!valid(run))return;AdbController.Failure failure=adb.failure();if(failure==AdbController.Failure.SHELL || failure==AdbController.Failure.USER || failure==AdbController.Failure.CAPABILITIES)protocolEnabled=true;working=false;connection=AdbState.Connection.FAILED;detail=reason;publish();}
    private void remember(boolean tls){preferences.edit().putString("lastTransport",tls?"tls":"tcp").putBoolean("autoConnect",true).apply();}
    void openManual(){cancel();manual=true;attempted=true;publish();}
    int beginManual(boolean tls){cancel();manual=true;manualTls=tls;working=true;attempted=true;preferences.edit().putBoolean("autoConnect",true).apply();detail="";publish();return adb.generation();}
    void manualConnected(boolean tls){working=false;manual=false;remember(tls);connection=AdbState.Connection.CONNECTED;protocolEnabled=true;detail="已核对本机与当前用户";publish();}
    void manualFailed(String reason){failed(epoch,reason);}
    void cancelManual(){cancel();manual=true;detail="";publish();}
    void pause(){preferences.edit().putBoolean("autoConnect",false).apply();cancel();detail="";publish();}
    void reportFailure(String reason){connection=adb.connected()?AdbState.Connection.CONNECTED:AdbState.Connection.FAILED;detail=reason;publish();}
    private void closeDiscovery(){if(discovery!=null){discovery.close();discovery=null;}}
    private void cancel(){epoch++;working=false;closeDiscovery();adb.disconnect();connection=AdbState.Connection.DISCONNECTED;}
    void suspend(){
        AdbController.Stage stage=adb.stage();
        if(working || stage==AdbController.Stage.HANDSHAKE || stage==AdbController.Stage.PAIRING || stage==AdbController.Stage.LOCAL || stage==AdbController.Stage.SHELL || stage==AdbController.Stage.USER || stage==AdbController.Stage.CAPABILITIES){
            active=false;suspended=true;
            if(registered){context.getContentResolver().unregisterContentObserver(observer);registered=false;}
            main.postDelayed(suspensionTimeout,60000);
        }else stop();
    }
    void stop(){active=false;suspended=false;cancel();main.removeCallbacksAndMessages(null);if(registered){context.getContentResolver().unregisterContentObserver(observer);registered=false;}}
    void close(){stop();adb.close();worker.shutdownNow();}
}
