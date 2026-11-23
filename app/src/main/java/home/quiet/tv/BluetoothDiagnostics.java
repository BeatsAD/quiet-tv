package home.quiet.tv;

import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.InputDevice;
import java.util.*;

/** Passive process-wide Bluetooth observations. No scanning, pairing, reconnecting or key filtering. */
final class BluetoothDiagnostics {
    static final String CONNECT="android.permission.BLUETOOTH_CONNECT";
    private static final String HID="android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED";
    private static BluetoothDiagnostics instance;
    private final Context context;
    private final String salt;
    private boolean registered;
    private final Map<String,String> connections=new LinkedHashMap<>();
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){observe(intent);}};
    private BluetoothDiagnostics(Context supplied){
        context=supplied.getApplicationContext();
        Context storage=Build.VERSION.SDK_INT>=24?Api24.storage(context):context;
        android.content.SharedPreferences prefs=storage.getSharedPreferences("bluetooth-diagnostics",0);
        String saved=prefs.getString("salt","");if(saved.isEmpty()){saved=UUID.randomUUID().toString();prefs.edit().putString("salt",saved).apply();}salt=saved;
    }
    static synchronized void init(Context context){
        try{if(instance==null)instance=new BluetoothDiagnostics(context);instance.start();}
        catch(RuntimeException unavailable){DiagnosticLog.info("bluetooth.observer","status=unavailable type="+unavailable.getClass().getSimpleName());}
    }
    static boolean permitted(Context context){return Build.VERSION.SDK_INT<31?context.checkCallingOrSelfPermission("android.permission.BLUETOOTH")==PackageManager.PERMISSION_GRANTED:Api23.granted(context);}
    static synchronized String salt(Context context){if(instance==null)instance=new BluetoothDiagnostics(context);return instance.salt;}
    private void start(){
        if(registered||!DiagnosticLog.recording()||!permitted(context))return;
        IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);filter.addAction(HID);
        // Bluetooth broadcasts can originate in the privileged Bluetooth process, not system UID.
        androidx.core.content.ContextCompat.registerReceiver(context,receiver,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED);registered=true;
        if(Build.VERSION.SDK_INT>=16)Api16.observe(context);
        DiagnosticLog.info("bluetooth.observer","status=recording scope=process_lifetime pairing_logic=unchanged");
    }
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("MissingPermission") // Reads are gated by permitted(); revocation is caught below.
    private void observe(Intent intent){
        if(!DiagnosticLog.recording())return;
        try{
            if(!permitted(context)){DiagnosticLog.info("bluetooth.observer","status=permission_missing");return;}
            String action=intent.getAction();String kind;
            String time=" uptimeMs="+SystemClock.uptimeMillis();
            int state=-1,previous=-1;
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                DiagnosticLog.info("bluetooth.adapter","previous="+BluetoothDiagnosticEvidence.adapter(intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,-1))+" state="+BluetoothDiagnosticEvidence.adapter(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1))+time);
                BluetoothSystemCapture.request(context,"adapter",false);return;
            }
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String id=BluetoothDiagnosticEvidence.device(salt,device==null?null:device.getAddress());
            if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                state=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,-1);previous=intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,-1);
                DiagnosticLog.info("bluetooth.bond","device="+id+" previous="+BluetoothDiagnosticEvidence.bond(previous)+" state="+BluetoothDiagnosticEvidence.bond(state)
                    +" reason="+(intent.hasExtra("android.bluetooth.device.extra.REASON")?Integer.toString(intent.getIntExtra("android.bluetooth.device.extra.REASON",-1)):"unavailable")+time);
                BluetoothSystemCapture.request(context,"bond",state==BluetoothDevice.BOND_NONE);return;
            }
            if(HID.equals(action)){kind="hid_host";state=intent.getIntExtra(BluetoothProfile.EXTRA_STATE,-1);previous=intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,-1);}
            else if(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)){kind="adapter_profile";state=intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,-1);previous=intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE,-1);}
            else if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){kind="acl";state=2;}
            else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){kind="acl";state=0;}
            else if(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action))kind="acl_disconnect_requested";
            else return;
            String detail="device="+id+" channel="+kind+" previous="+BluetoothDiagnosticEvidence.connection(previous)+" state="+BluetoothDiagnosticEvidence.connection(state);
            synchronized(connections){if(connections.size()>=32&&!connections.containsKey(id+":"+kind))connections.remove(connections.keySet().iterator().next());connections.put(id+":"+kind,detail+" observedAt="+System.currentTimeMillis());}
            DiagnosticLog.info("bluetooth.connection",detail+time);BluetoothSystemCapture.request(context,"connection",false);
        }catch(RuntimeException unavailable){DiagnosticLog.info("bluetooth.observer","status=read_failed type="+unavailable.getClass().getSimpleName());}
    }
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("MissingPermission") // Reads are gated by permitted(); revocation remains an unknown result.
    static String snapshot(Context context){
        init(context);StringBuilder text=new StringBuilder("状态读取授权：").append(permitted(context)?"已获得":"未获得，当前状态未知").append('\n');
        BluetoothDiagnostics observer=instance;
        text.append("被动观察：").append(observer!=null&&observer.registered?"已注册":"未注册").append('\n');
        try{
            if(permitted(context)&&observer!=null){
                BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
                if(adapter==null)text.append("蓝牙适配器：系统未提供\n");
                else{
                    int state=adapter.getState();text.append("适配器状态：").append(BluetoothDiagnosticEvidence.adapter(state)).append("  原值 ").append(state).append('\n');
                    Set<BluetoothDevice> bonded=adapter.getBondedDevices();text.append("已配对设备数：").append(bonded==null?"未知":bonded.size()).append('\n');int count=0;
                    if(bonded!=null)for(BluetoothDevice device:bonded){if(count++>=32){text.append("其余设备未展开\n");break;}text.append("已配对：").append(BluetoothDiagnosticEvidence.device(observer.salt,device.getAddress())).append('\n');}
                }
                synchronized(observer.connections){for(String value:observer.connections.values())text.append("本进程最近连接事件：").append(value).append('\n');}
            }
        }catch(RuntimeException unavailable){text.append("当前状态读取失败：").append(unavailable.getClass().getSimpleName()).append('\n');}
        text.append("系统日志采集：").append(BluetoothSystemCapture.status()).append('\n');
        text.append("当前按键连接：需结合HID事件与系统采样判断；没有连接事件时记为未知。\n");
        text.append("范围：仅被动记录本进程存活且权限可用期间的配对、ACL、配置文件连接及输入设备变化。已配对不代表已连接；ACL连接不代表遥控按键通道可用。连接事件带观察时间，不冒充实时查询。\n");
        return text.append("隐私：设备使用本机加盐匿名编号，不记录MAC、名称、配对码或密钥。未请求扫描、配对、解绑、重连或修改蓝牙开关。\n").append(InputDiagnostics.devicesSnapshot()).toString();
    }
    @android.annotation.TargetApi(16) private static final class Api16 {
        static void observe(Context context){
            android.hardware.input.InputManager manager=(android.hardware.input.InputManager)context.getSystemService(Context.INPUT_SERVICE);if(manager==null)return;
            manager.registerInputDeviceListener(new android.hardware.input.InputManager.InputDeviceListener(){
                public void onInputDeviceAdded(int id){record("added",id);}
                public void onInputDeviceChanged(int id){record("changed",id);}
                public void onInputDeviceRemoved(int id){record("removed",id);}
                private void record(String change,int id){if(!DiagnosticLog.recording())return;InputDiagnostics.deviceChanged(id);InputDevice device=InputDevice.getDevice(id);DiagnosticLog.info("bluetooth.input_device","change="+change+" deviceId="+id+" sources="+(device==null?"unknown":device.getSources())+" transport=unverified");}
            },new Handler(Looper.getMainLooper()));
        }
    }
    @android.annotation.TargetApi(23) private static final class Api23 {static boolean granted(Context context){return context.checkSelfPermission(CONNECT)==PackageManager.PERMISSION_GRANTED;}}
    @android.annotation.TargetApi(24) private static final class Api24 {static Context storage(Context context){return context.createDeviceProtectedStorageContext();}}
}
