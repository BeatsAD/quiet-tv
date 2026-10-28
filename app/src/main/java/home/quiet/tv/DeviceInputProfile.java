package home.quiet.tv;

import android.content.*;
import android.media.tv.TvInputInfo;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Per-firmware corrections to enumerated inputs, never arbitrary intents or shell commands. */
final class DeviceInputProfile {
    private final Context context;
    private final SharedPreferences prefs;
    private static String pending="",request="";
    private static JSONObject last=new JSONObject();
    DeviceInputProfile(Context c){context=c;prefs=c.getSharedPreferences("device-input-profile",Context.MODE_PRIVATE);}
    static String digest(String text) throws Exception {
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static JSONObject validate(String text,String fingerprint,Set<String> inputs,Set<String> xiaomi) throws Exception {
        if(text.length()>8192)throw new IllegalArgumentException("本机配置过长");
        JSONObject value=new JSONObject(text);
        Set<String> fields=new HashSet<>(Arrays.asList("schemaVersion","fingerprint","hidden","labels","routes","xiaomiSources"));
        for(Iterator<String> keys=value.keys();keys.hasNext();)if(!fields.contains(keys.next()))throw new IllegalArgumentException("未知配置字段");
        if(value.getInt("schemaVersion")!=1 || !fingerprint.equals(value.getString("fingerprint")))throw new IllegalArgumentException("配置不属于当前固件");
        JSONArray hidden=value.has("hidden")?value.getJSONArray("hidden"):new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<hidden.length();i++){String id=hidden.getString(i);if(!inputs.contains(id) || !seen.add(id))throw new IllegalArgumentException("隐藏项不是本机唯一信号源");}
        JSONObject routes=value.has("routes")?value.getJSONObject("routes"):new JSONObject();
        for(String field:new String[]{"labels","routes","xiaomiSources"}){
            JSONObject map=value.has(field)?value.getJSONObject(field):new JSONObject();
            for(Iterator<String> keys=map.keys();keys.hasNext();){String id=keys.next();Object item=map.get(id);
                if(!inputs.contains(id))throw new IllegalArgumentException("配置引用未知信号源");
                if("labels".equals(field)){
                    if(!(item instanceof String) || ((String)item).trim().isEmpty() || ((String)item).length()>40 || ((String)item).matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("信号源名称无效");
                }else if("routes".equals(field)){
                    if(!(item instanceof String) || !inputs.contains(item) || id.equals(item) || routes.has((String)item))throw new IllegalArgumentException("映射目标无效，不允许循环或多级映射");
                }else if(!(item instanceof Integer) || (Integer)item<19 || (Integer)item>32 || !xiaomi.contains(id) || routes.has(id))throw new IllegalArgumentException("本机没有对应的小米 HDMI 播放入口");
            }
        }
        return value;
    }
    JSONObject active(){
        try{JSONObject value=new JSONObject(prefs.getString("profile","{}"));return Build.FINGERPRINT.equals(value.optString("fingerprint"))?value:new JSONObject();}
        catch(JSONException invalid){return new JSONObject();}
    }
    boolean hidden(String id){JSONArray values=active().optJSONArray("hidden");if(values!=null)for(int i=0;i<values.length();i++)if(id.equals(values.optString(i)))return true;return false;}
    String label(String id){JSONObject values=active().optJSONObject("labels");return values==null?"":values.optString(id);}
    String target(String id){JSONObject values=active().optJSONObject("routes");return values==null?id:values.optString(id,id);}
    Integer xiaomi(String id){JSONObject values=active().optJSONObject("xiaomiSources");return values!=null && values.has(id)?values.optInt(id):null;}
    JSONObject snapshot() throws Exception {
        String raw=prefs.getString("profile","");JSONObject profile=raw.isEmpty()?new JSONObject():new JSONObject(raw);
        return new JSONObject().put("revision",digest(raw)).put("state",raw.isEmpty()?"default":Build.FINGERPRINT.equals(profile.optString("fingerprint"))?"active":"stale")
            .put("profile",profile).put("lastInput",last);
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    void save(String text,String expected,boolean reset) throws Exception {
        if(expected==null || !digest(prefs.getString("profile","")).equals(expected))throw new IllegalStateException("本机配置已变化，请重新读取");
        String desired="";
        if(!reset){
            if(Build.VERSION.SDK_INT<21)throw new IllegalStateException("本档位没有标准信号源配置接口");
            TvInputController controller=new TvInputController(context);Set<String> ids=new HashSet<>(),xiaomi=new HashSet<>();
            for(TvInputInfo input:controller.rawInputs())if(input.isPassthroughInput() && TvInputController.enabledSystem(context,input.getServiceInfo())){
                ids.add(input.getId());if(input.getType()==TvInputInfo.TYPE_HDMI && TvInputController.canOpen(context,new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer.ExternalSourceActivity")))xiaomi.add(input.getId());
            }
            desired=validate(text,Build.FINGERPRINT,ids,xiaomi).toString();
        }
        if(!prefs.edit().putString("profile",desired).commit())throw new IllegalStateException("本机配置未保存");
    }
    static synchronized String request(Context c,String id) throws Exception {
        if(Build.VERSION.SDK_INT<21 || new TvInputController(c).findInput(id)==null)throw new IllegalArgumentException("信号源不存在");
        if(!pending.isEmpty())throw new IllegalStateException("上一次信号源请求尚未处理");
        pending=id;request=UUID.randomUUID().toString();last=new JSONObject().put("request",request).put("id",id).put("result","pending");
        try{c.startActivity(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException failure){pending="";last.put("result","failed");throw failure;}
        return request;
    }
    static synchronized String consume(){String id=pending;pending="";return id;}
    static synchronized void dispatched(boolean opened){try{last.put("result",opened?"dispatched":"failed").put("physicalVerified",false);}catch(JSONException ignored){}}
}
