package home.quiet.tv;

import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Explicit system dispatch; opening an Android page never claims permission was granted. */
final class PhoneSettingsActions {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    PhoneSettingsActions(Context context){this.context=context;}
    JSONObject audio()throws JSONException{
        AudioManager audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int max=audio==null?0:audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        boolean available=audio!=null&&max>0&&(Build.VERSION.SDK_INT<21||!Api21.fixed(audio));
        return new JSONObject().put("volume",audio==null?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC)).put("maximum",max).put("available",available);
    }
    JSONObject run(String id,PhoneSettingsServer.Active active)throws IOException{
        if(!QuickControlStore.ALL.contains(id))throw new PhoneSettingsServer.BadInput();
        return onMain(()->{
            String action="network".equals(id)?Settings.ACTION_WIFI_SETTINGS:"bluetooth".equals(id)?Settings.ACTION_BLUETOOTH_SETTINGS:"systemSettings".equals(id)?Settings.ACTION_SETTINGS:null;
            if(action!=null){if(!new SystemSettingsRouter(context).open(action))throw new PhoneSettingsServer.Rejected("此电视没有可调用的系统入口");return new JSONObject().put("message","已请求打开电视设置，请在电视操作后返回核对").put("state","waitingTelevision");}
            if("original".equals(id)){new HomeProxy(context).original(context);return new JSONObject().put("message","已请求进入原桌面，请在电视核对").put("state","waitingTelevision");}
            if("source".equals(id)){if(new BootAppStore(context).watchTarget().isEmpty()&&new SourcePairingStore(context).entry()==null)throw new PhoneSettingsServer.Rejected("请先配置信号源");if(!HomeActivity.phoneAction(context,id,active))throw new PhoneSettingsServer.Rejected("桌面尚未就绪，请先在电视打开 QuietTV 首页");return new JSONObject().put("message","已请求打开已保存的信号源，请核对电视画面和声音").put("state","waitingTelevision");}
            if("screensaver".equals(id)){if(!HomeActivity.phoneAction(context,id,active))throw new PhoneSettingsServer.Rejected("桌面尚未就绪，请先在电视打开 QuietTV 首页");return new JSONObject().put("message","已请求电视预览屏保").put("state","waitingTelevision");}
            throw new PhoneSettingsServer.Rejected("请使用对应的手机设置入口");
        },active);
    }
    JSONObject openApp(String pkg,PhoneSettingsServer.Active active)throws IOException{return onMain(()->{if(!HomeActivity.phoneAction(context,"app:"+pkg,active))throw new PhoneSettingsServer.Rejected("桌面尚未就绪，请先在电视打开 QuietTV 首页");return new JSONObject().put("message","已请求打开应用；家长限制和答题仍按电视规则执行").put("state","waitingTelevision");},active);}
    void volume(Object value,PhoneSettingsServer.Active active)throws IOException{
        try{JSONObject current=audio();if(!current.getBoolean("available"))throw new PhoneSettingsServer.Rejected("此电视音量由外部设备或系统控制");int level=PhoneSettingsConfiguration.integer(value,0,current.getInt("maximum"));if(!active.valid())throw new PhoneSettingsServer.Ended();((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_MUSIC,level,0);}catch(JSONException e){throw new IOException(e);}catch(SecurityException e){throw new PhoneSettingsServer.Rejected("电视系统不允许调节此音量");}
    }
    <T>T onMain(Callable<T> operation,PhoneSettingsServer.Active active)throws IOException{
        FutureTask<T> task=new FutureTask<>(()->{if(!active.valid())throw new PhoneSettingsServer.Ended();return operation.call();});main.post(task);
        try{return task.get(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();task.cancel(false);throw new IOException("操作结果待核对",e);}catch(TimeoutException e){task.cancel(false);throw new IOException("操作结果待核对",e);}catch(ExecutionException e){if(e.getCause() instanceof IOException)throw (IOException)e.getCause();throw new IOException("操作未确认",e.getCause());}
    }
    @androidx.annotation.RequiresApi(21) private static final class Api21{static boolean fixed(AudioManager audio){return audio.isVolumeFixed();}}
}
