package home.quiet.tv;

import android.content.*;
import android.os.*;
import android.view.*;
import org.json.*;
import java.util.*;

/** Three-key computer calibration. Drafts expire; only a fully confirmed profile becomes active. */
final class RemoteKeyMap {
    static final String[] STEPS={"home","inputs","menu"};
    static final String[] SCOPE={"manufacturer","brand","model","device","fingerprint","sdk","user"};
    private final Context context;
    private final SharedPreferences prefs;
    RemoteKeyMap(Context context){this.context=context.getApplicationContext();Context storage=Build.VERSION.SDK_INT>=24?this.context.createDeviceProtectedStorageContext():this.context;prefs=storage.getSharedPreferences("remote-keys",0);}
    private static String digest(String value){try{return DeviceInputProfile.digest(value);}catch(Exception error){throw new IllegalStateException("校准标识生成失败",error);}}
    static JSONObject scope() throws JSONException {
        return new JSONObject().put("manufacturer",Build.MANUFACTURER).put("brand",Build.BRAND).put("model",Build.MODEL)
            .put("device",Build.DEVICE).put("fingerprint",Build.FINGERPRINT).put("sdk",Build.VERSION.SDK_INT).put("user",android.os.Process.myUid()/100000);
    }
    static String scopeId(JSONObject scope) throws JSONException {JSONArray values=new JSONArray();for(String key:SCOPE)values.put(scope.get(key));return digest(values.toString());}
    static boolean sameScope(JSONObject a,JSONObject b) throws JSONException {return scopeId(a).equals(scopeId(b));}
    private JSONObject profiles() throws JSONException {return new JSONObject(prefs.getString("profiles","{}"));}
    private String revision(){return digest(prefs.getString("profiles","{}"));}
    private JSONObject session() throws JSONException {
        if(!HomeGuardState.unlocked(context))return new JSONObject();
        JSONObject s=new JSONObject(prefs.getString("session","{}"));
        if(s.length()==0 || !sameScope(s.getJSONObject("scope"),scope()) || !new BootAppStore(context).bootId().equals(s.optString("boot")))return new JSONObject();
        return s;
    }
    private boolean live(JSONObject s){long now=SystemClock.elapsedRealtime();return now>=s.optLong("started",Long.MAX_VALUE) && now<s.optLong("until",-1);}
    @android.annotation.SuppressLint("ApplySharedPref")
    private void session(JSONObject s) throws JSONException {if(!prefs.edit().putString("session",s.toString()).commit())throw new IllegalStateException("按键校准状态未保存");}
    private JSONObject profile() throws JSONException {JSONObject p=profiles().optJSONObject(scopeId(scope()));return p==null?new JSONObject():p;}
    JSONObject snapshot() throws JSONException {
        JSONObject s=session();
        if(("capture".equals(s.optString("phase")) || "preview".equals(s.optString("phase"))) && !live(s)){s.put("phase","expired");session(s);}
        return new JSONObject().put("scope",scope()).put("revision",revision()).put("profile",profile()).put("session",s)
            .put("globalHomeAvailable",Build.VERSION.SDK_INT>=21 && HomeGuardService.connected() && HomeGuardService.keyFilterCapable() && new HomeGuardState(context,false).homeActionReady());
    }
    static boolean allowed(String step,String action){return "system".equals(action) || "home".equals(step) && "home".equals(action)
        || "inputs".equals(step) && "inputs".equals(action) || "menu".equals(step) && ("settings".equals(action) || "desktop_settings".equals(action));}
    static boolean eligible(int code,int scan){
        return code>=0 && (code!=KeyEvent.KEYCODE_UNKNOWN || scan>0) && code!=KeyEvent.KEYCODE_BACK
            && !(code>=KeyEvent.KEYCODE_DPAD_UP && code<=KeyEvent.KEYCODE_DPAD_CENTER) && code!=KeyEvent.KEYCODE_ENTER && code!=KeyEvent.KEYCODE_NUMPAD_ENTER
            && code!=KeyEvent.KEYCODE_POWER && code!=KeyEvent.KEYCODE_SLEEP && code!=KeyEvent.KEYCODE_WAKEUP
            && code!=KeyEvent.KEYCODE_VOLUME_UP && code!=KeyEvent.KEYCODE_VOLUME_DOWN && code!=KeyEvent.KEYCODE_VOLUME_MUTE && code!=KeyEvent.KEYCODE_MUTE;
    }
    static boolean sameKey(JSONObject a,JSONObject b){if(a==null || b==null)return false;for(String key:new String[]{"code","scan","vendor","product"})if(a.optInt(key,-1)!=b.optInt(key,-2))return false;return true;}
    static void validateBinding(JSONObject b) throws JSONException {
        String step=b.getString("step"),action=b.getString("action");
        if(!Arrays.asList(STEPS).contains(step) || !allowed(step,action))throw new IllegalArgumentException("按键动作不适用于当前步骤");
        JSONObject key=b.optJSONObject("key");
        if(key==null){if(!"system".equals(action))throw new IllegalArgumentException("未采到键码，只能保留系统处理");return;}
        for(String name:new String[]{"code","scan","vendor","product"})if(!(key.get(name) instanceof Integer) || key.getInt(name)<0)throw new IllegalArgumentException("键码信息无效");
        if(!eligible(key.getInt("code"),key.getInt("scan")) || key.getInt("code")==KeyEvent.KEYCODE_HOME && !"home".equals(step))throw new IllegalArgumentException("此按键不能重新映射");
    }
    static void validateBindings(JSONArray bindings) throws JSONException {
        if(bindings.length()!=3)throw new IllegalArgumentException("需要依次确认三个按键");
        for(int i=0;i<3;i++){JSONObject b=bindings.getJSONObject(i);validateBinding(b);if(!STEPS[i].equals(b.getString("step")))throw new IllegalArgumentException("按键步骤顺序不符");
            for(int j=0;j<i;j++)if(sameKey(b.optJSONObject("key"),bindings.getJSONObject(j).optJSONObject("key")))throw new IllegalArgumentException("不同按键采到了相同键码，请重新核对");}
    }
    private void current(JSONObject s,Intent intent) throws JSONException {
        if(s.length()==0 || !s.getString("id").equals(intent.getStringExtra("calibration_id")) || !s.optString("step").equals(intent.getStringExtra("key")))throw new IllegalStateException("校准任务或按键步骤已变化");
        if(!s.getString("revision").equals(revision()))throw new IllegalStateException("按键配置已变化，请结束本次校准后重新开始");
    }
    private void showHome(){context.startActivity(HomeController.launchIntent(context));}
    void operation(String operation,Intent intent) throws Exception {
        JSONObject s=session();String savedProfiles=null;
        if("remote_begin".equals(operation)){
            if(!revision().equals(intent.getStringExtra("revision")))throw new IllegalStateException("按键配置已变化");
            if(s.length()>0 && !Arrays.asList("saved","cancelled","expired").contains(s.optString("phase")))throw new IllegalStateException("已有校准任务，请先检查或取消");
            if(new BootAppStore(context).bootId().isEmpty())throw new IllegalStateException("无法核对本次启动，未开始校准");
            s=new JSONObject().put("id",UUID.randomUUID().toString()).put("scope",scope()).put("revision",revision()).put("boot",new BootAppStore(context).bootId())
                .put("step",STEPS[0]).put("index",0).put("phase","ready").put("bindings",new JSONArray());
        }else if("remote_status".equals(operation))return;
        else {
            current(s,intent);String phase=s.getString("phase");
            if("remote_cancel".equals(operation))s.put("phase","cancelled");
            else if("remote_capture".equals(operation)){
                if(!Arrays.asList("ready","captured","expired","ambiguous","cancelled_key").contains(phase))throw new IllegalStateException("当前不能采集按键");
                int seconds=intent.getIntExtra("seconds",20);if(seconds<5 || seconds>30)throw new IllegalArgumentException("采集时长须为5至30秒");
                s.remove("key");s.remove("down");s.remove("candidate");s.remove("pressed");s.remove("trialDown");s.remove("source");s.remove("previewId");s.put("complete",false);s.put("phase","capture").put("started",SystemClock.elapsedRealtime()).put("until",SystemClock.elapsedRealtime()+seconds*1000L);
                session(s);HomeGuardService.remoteKeysChanged();showHome();return;
            }else if("remote_preview".equals(operation)){
                if(!Arrays.asList("captured","expired","preview").contains(phase))throw new IllegalStateException("请先采集当前按键");
                JSONObject candidate=new JSONObject().put("step",s.getString("step")).put("action",intent.getStringExtra("action"));
                if(s.optBoolean("complete"))candidate.put("key",s.getJSONObject("key"));
                candidate.put("captureSource",candidate.has("key")?s.optString("source","received_key"):"system_unobserved");
                validateBinding(candidate);JSONArray trial=new JSONArray(s.getJSONArray("bindings").toString());trial.put(candidate);
                for(int i=0;i<trial.length()-1;i++)if(sameKey(candidate.optJSONObject("key"),trial.getJSONObject(i).optJSONObject("key")))throw new IllegalArgumentException("不同按键采到了相同键码");
                if("home".equals(candidate.getString("action")) && candidate.getJSONObject("key").getInt("code")!=KeyEvent.KEYCODE_HOME
                        && !(Build.VERSION.SDK_INT>=21 && HomeGuardService.connected() && HomeGuardService.keyFilterCapable() && new HomeGuardState(context,false).homeActionReady()))
                    throw new IllegalStateException("自定义HOME需要现有桌面守护就绪和按键观察能力；未修改授权");
                s.remove("trialDown");s.put("previewId",UUID.randomUUID().toString()).put("candidate",candidate).put("phase","preview").put("pressed",false).put("started",SystemClock.elapsedRealtime()).put("until",SystemClock.elapsedRealtime()+120000);
                session(s);HomeGuardService.remoteKeysChanged();if(!"home".equals(s.getString("step")))showHome();return;
            }else if("remote_confirm".equals(operation)){
                String note=intent.getStringExtra("observed");
                if(!"preview".equals(phase) || !live(s) || !s.optString("previewId").equals(intent.getStringExtra("preview_id")) || !intent.getBooleanExtra("confirmed",false) || note==null || note.trim().isEmpty() || note.length()>300)throw new IllegalStateException("需要仍有效的试用和用户实际画面确认");
                JSONObject candidate=s.getJSONObject("candidate");
                if(!"system".equals(candidate.getString("action")) && !s.optBoolean("pressed"))throw new IllegalStateException("尚未观察到试用按键，不能确认映射");
                candidate.put("confirmation","user").put("observed",note.trim()).put("confirmedAt",System.currentTimeMillis());
                JSONArray bindings=s.getJSONArray("bindings");bindings.put(candidate);int index=s.getInt("index")+1;
                s.remove("key");s.remove("down");s.remove("candidate");s.remove("pressed");s.remove("trialDown");s.remove("source");s.remove("complete");s.remove("previewId");
                if(index==3){validateBindings(bindings);JSONObject profiles=profiles();profiles.put(scopeId(scope()),new JSONObject().put("scope",scope()).put("bindings",bindings));
                    savedProfiles=profiles.toString();s.put("phase","saved");
                }else s.put("index",index).put("step",STEPS[index]).put("phase","ready");
            }else throw new IllegalArgumentException("不支持的按键校准操作");
        }
        if(savedProfiles==null)session(s);else if(!prefs.edit().putString("profiles",savedProfiles).putString("session",s.toString()).commit())throw new IllegalStateException("按键映射未保存");
        HomeGuardService.remoteKeysChanged();
    }
    private static JSONObject key(KeyEvent event) throws JSONException {
        InputDevice device=event.getDevice();int vendor=0,product=0;
        if(Build.VERSION.SDK_INT>=19 && device!=null){vendor=device.getVendorId();product=device.getProductId();}
        return new JSONObject().put("code",event.getKeyCode()).put("scan",event.getScanCode()).put("vendor",vendor).put("product",product);
    }
    static void observe(Context c,KeyEvent event,String source){
        if(event.getRepeatCount()!=0 || event.getAction()!=KeyEvent.ACTION_DOWN && event.getAction()!=KeyEvent.ACTION_UP)return;
        try{
            RemoteKeyMap map=new RemoteKeyMap(c);JSONObject s=map.session();if(!map.live(s))return;
            JSONObject key=key(event);
            if("capture".equals(s.optString("phase")) && eligible(event.getKeyCode(),event.getScanCode())){
                if(event.getAction()==KeyEvent.ACTION_DOWN){
                    if(s.has("key") && (!sameKey(s.getJSONObject("key"),key) || s.optLong("down")!=event.getDownTime()))s.put("phase","ambiguous");
                    else s.put("key",key).put("down",event.getDownTime()).put("source",source);
                }else if(sameKey(s.optJSONObject("key"),key) && s.optLong("down",-1)==event.getDownTime())s.put("phase",event.isCanceled()?"cancelled_key":"captured").put("complete",!event.isCanceled());
                map.session(s);
            }else if("preview".equals(s.optString("phase")) && sameKey(s.getJSONObject("candidate").optJSONObject("key"),key)){
                if(event.getAction()==KeyEvent.ACTION_DOWN)s.put("trialDown",event.getDownTime());
                else if(s.optLong("trialDown",-1)==event.getDownTime() && !event.isCanceled())s.put("pressed",true).put("evidence","received_key_only");
                map.session(s);
            }
        }catch(Exception failure){DiagnosticLog.info("remote_keys.observe","unavailable="+failure.getClass().getSimpleName());}
    }
    boolean capturing(KeyEvent event){try{JSONObject s=session();return eligible(event.getKeyCode(),event.getScanCode()) && ("capture".equals(s.optString("phase")) && live(s) || "captured".equals(s.optString("phase")) && sameKey(s.optJSONObject("key"),key(event)) && s.optLong("down",-1)==event.getDownTime());}catch(JSONException ignored){}return false;}
    String action(KeyEvent event){try{
        JSONObject k=key(event),s=session();
        if("capture".equals(s.optString("phase")) && live(s) && eligible(event.getKeyCode(),event.getScanCode()))return "system";
        if("preview".equals(s.optString("phase")) && live(s) && sameKey(s.getJSONObject("candidate").optJSONObject("key"),k))return s.getJSONObject("candidate").getString("action");
        JSONArray bindings=profile().optJSONArray("bindings");if(bindings!=null)for(int i=0;i<bindings.length();i++){JSONObject b=bindings.getJSONObject(i);if(sameKey(b.optJSONObject("key"),k))return b.getString("action");}
    }catch(JSONException ignored){}return "";}
    boolean customHome(){try{
        JSONObject s=session();if("preview".equals(s.optString("phase")) && live(s) && custom(s.getJSONObject("candidate")))return true;
        JSONArray bindings=profile().optJSONArray("bindings");if(bindings!=null)for(int i=0;i<bindings.length();i++)if(custom(bindings.getJSONObject(i)))return true;
    }catch(JSONException ignored){}return false;}
    private boolean custom(JSONObject b){JSONObject key=b.optJSONObject("key");return "home".equals(b.optString("action")) && key!=null && key.optInt("code")!=KeyEvent.KEYCODE_HOME;}
    long refreshFor(){try{JSONObject s=session();if(("capture".equals(s.optString("phase")) || "preview".equals(s.optString("phase"))) && live(s))return s.getLong("until")-SystemClock.elapsedRealtime();}catch(JSONException ignored){}return 0;}
    long observingFor(){try{JSONObject s=session();if("capture".equals(s.optString("phase")) && live(s))return s.getLong("until")-SystemClock.elapsedRealtime();}catch(JSONException ignored){}return 0;}
}
