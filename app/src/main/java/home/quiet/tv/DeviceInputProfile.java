package home.quiet.tv;

import android.content.*;
import android.media.tv.TvInputInfo;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Per-firmware input corrections. Version 2 adds explicit native ports and cross-provider aliases. */
final class DeviceInputProfile {
    private final Context context;
    private final SharedPreferences prefs;
    private static final Object profileLock=new Object();
    private static String pending="",request="",pendingTarget="";
    private static long requestAt;
    private static JSONObject last=new JSONObject();
    DeviceInputProfile(Context c){context=c;prefs=c.getSharedPreferences("device-input-profile",Context.MODE_PRIVATE);}
    static String digest(String text) throws Exception {
        byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(home.quiet.tv.Charsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static JSONObject validate(String text,String fingerprint,Set<String> inputs,Set<String> xiaomi) throws Exception {
        return validate(text,fingerprint,inputs,xiaomi,Collections.emptyMap(),Collections.emptyMap());
    }
    static JSONObject validate(String text,String fingerprint,Set<String> inputs,Set<String> xiaomi,Map<String,Integer> vendor,Map<String,Integer> standard) throws Exception {
        if(text.length()>8192)throw new IllegalArgumentException("本机配置过长");
        JSONObject value=new JSONObject(text);
        Set<String> fields=new HashSet<>(Arrays.asList("schemaVersion","fingerprint","hidden","labels","routes","xiaomiSources"));
        int schema=value.getInt("schemaVersion");
        if(schema==2)fields.addAll(Arrays.asList("nativeSources","vendorAliases","packageVersions"));
        for(Iterator<String> keys=value.keys();keys.hasNext();)if(!fields.contains(keys.next()))throw new IllegalArgumentException("未知配置字段");
        if((schema!=1&&schema!=2) || !fingerprint.equals(value.getString("fingerprint")))throw new IllegalArgumentException("配置不属于当前固件");
        Set<String> configurable=new HashSet<>(inputs);if(schema==2)configurable.addAll(vendor.keySet());
        JSONArray hidden=value.has("hidden")?value.getJSONArray("hidden"):new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<hidden.length();i++){String id=hidden.getString(i);if(!configurable.contains(id) || !seen.add(id))throw new IllegalArgumentException("隐藏项不是本机唯一信号源");}
        JSONObject routes=value.has("routes")?value.getJSONObject("routes"):new JSONObject();
        for(String field:new String[]{"labels","routes","xiaomiSources"}){
            JSONObject map=value.has(field)?value.getJSONObject(field):new JSONObject();
            for(Iterator<String> keys=map.keys();keys.hasNext();){String id=keys.next();Object item=map.get(id);
                if(!("labels".equals(field)?configurable:inputs).contains(id))throw new IllegalArgumentException("配置引用未知信号源");
                if("labels".equals(field)){
                    if(!(item instanceof String) || ((String)item).trim().isEmpty() || ((String)item).length()>40 || ((String)item).matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("信号源名称无效");
                }else if("routes".equals(field)){
                    if(!(item instanceof String) || !inputs.contains(item) || id.equals(item) || routes.has((String)item))throw new IllegalArgumentException("映射目标无效，不允许循环或多级映射");
                }else if(!(item instanceof Integer) || (Integer)item<19 || (Integer)item>32 || !xiaomi.contains(id) || routes.has(id))throw new IllegalArgumentException("本机没有对应的小米 HDMI 播放入口");
            }
        }
        if(schema==2){
            for(VendorTvInputs.Source source:XiaomiInputs.records(value))if(!vendor.containsKey(source.key))throw new IllegalArgumentException("本机没有可用的小米原生端口协议");
            JSONObject aliases=value.has("vendorAliases")?value.getJSONObject("vendorAliases"):new JSONObject();Set<String> targets=new HashSet<>();
            for(Iterator<String> keys=aliases.keys();keys.hasNext();){String source=keys.next();Object target=aliases.get(source);
                if(!(target instanceof String)||!vendor.containsKey(source)||!standard.containsKey(target)||vendor.get(source)<0||!vendor.get(source).equals(standard.get(target))||!targets.add((String)target)||routes.has((String)target))throw new IllegalArgumentException("端口对应必须引用同类型的本机唯一输入，不能与入口重映射冲突");
                JSONObject overrides=value.optJSONObject("xiaomiSources");
                if(source.startsWith("xiaomi:")&&overrides!=null&&overrides.has((String)target)&&!source.equals("xiaomi:"+overrides.getInt((String)target)))throw new IllegalArgumentException("小米端口对应与播放器编号冲突");
            }
            if(value.has("packageVersions"))value.getJSONObject("packageVersions");
        }
        return value;
    }
    JSONObject active(){
        try{JSONObject value=new JSONObject(prefs.getString("profile","{}"));return Build.FINGERPRINT.equals(value.optString("fingerprint"))&&(value.optInt("schemaVersion")==1||value.optInt("schemaVersion")==2&&packagesMatch(value.optJSONObject("packageVersions")))?value:new JSONObject();}
        catch(JSONException invalid){return new JSONObject();}
    }
    private boolean packagesMatch(JSONObject versions){
        if(versions==null)return false;
        try{for(Iterator<String> keys=versions.keys();keys.hasNext();){String pkg=keys.next();if(!VendorTvInputs.packageStamp(context,pkg).equals(versions.getString(pkg)))return false;}return true;}
        catch(Exception unavailable){return false;}
    }
    String vendorTarget(String key){JSONObject aliases=active().optJSONObject("vendorAliases");return aliases==null?"":aliases.optString(key);}
    boolean aliasHidden(String standard){JSONObject aliases=active().optJSONObject("vendorAliases");if(aliases!=null)for(Iterator<String> keys=aliases.keys();keys.hasNext();){String key=keys.next();if(standard.equals(aliases.optString(key))&&hidden(key))return true;}return false;}
    boolean hidden(String id){JSONArray values=active().optJSONArray("hidden");if(values!=null)for(int i=0;i<values.length();i++)if(id.equals(values.optString(i)))return true;return false;}
    String label(String id){JSONObject values=active().optJSONObject("labels");return values==null?"":values.optString(id);}
    String target(String id){JSONObject values=active().optJSONObject("routes");return values==null?id:values.optString(id,id);}
    Integer xiaomi(String id){JSONObject values=active().optJSONObject("xiaomiSources");return values!=null && values.has(id)?values.optInt(id):null;}
    JSONObject snapshot() throws Exception {
        String raw=prefs.getString("profile","");JSONObject profile=raw.isEmpty()?new JSONObject():new JSONObject(raw);
        return new JSONObject().put("revision",digest(raw)).put("state",raw.isEmpty()?"default":active().length()>0?"active":"stale")
            .put("profile",profile).put("lastInput",lastSnapshot());
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    void save(String text,String expected,boolean reset) throws Exception {
        if(expected==null || !digest(prefs.getString("profile","")).equals(expected))throw new IllegalStateException("本机配置已变化，请重新读取");
        String desired="";
        if(!reset){
            JSONObject proposed=new JSONObject(text);
            Set<String> ids=new HashSet<>(),xiaomi=new HashSet<>();
            Map<String,Integer> standard=new HashMap<>(),vendor=new HashMap<>();JSONObject versions=new JSONObject();
            if(Build.VERSION.SDK_INT>=21)Api21.collect(context,ids,xiaomi,standard,versions);
            else if(proposed.optInt("schemaVersion")!=2)throw new IllegalArgumentException("旧系统仅支持原生端口配置版本 2");
            if(proposed.optInt("schemaVersion")==2){
                // Validate the restricted native record syntax before any adapter reads it.
                XiaomiInputs.records(proposed);
                for(VendorTvInputs.Source source:new VendorTvInputs(context).inspect(proposed).sources){
                    if(vendor.containsKey(source.key))vendor.put(source.key,-1);else vendor.put(source.key,source.type);
                    versions.put(source.owner,VendorTvInputs.packageStamp(context,source.owner));
                    if(source.key.startsWith("hisense:"))versions.put(HisenseInputs.SOURCE.getPackageName(),VendorTvInputs.packageStamp(context,HisenseInputs.SOURCE.getPackageName()));
                    if(source.key.startsWith("com.skyworth.tv.provider:")||source.key.startsWith("com.skyworth.tv.sdk.provider:")){
                        android.content.pm.ProviderInfo provider=context.getPackageManager().resolveContentProvider(source.key.substring(0,source.key.indexOf(':')),0);
                        if(provider==null)throw new IllegalStateException("厂商输入服务已变化");
                        versions.put(provider.packageName,VendorTvInputs.packageStamp(context,provider.packageName));
                    }
                }
                if(proposed.optJSONObject("xiaomiSources")!=null&&proposed.getJSONObject("xiaomiSources").length()>0)
                    versions.put(XiaomiInputs.PLAYER.getPackageName(),VendorTvInputs.packageStamp(context,XiaomiInputs.PLAYER.getPackageName()));
            }
            JSONObject valid=validate(text,Build.FINGERPRINT,ids,xiaomi,vendor,standard);
            if(valid.optInt("schemaVersion")==2){
                JSONObject before=valid.optJSONObject("packageVersions");
                if(before!=null){
                    if(before.length()!=versions.length())throw new IllegalArgumentException("系统组件已变化，请重新读取端口");
                    for(Iterator<String> keys=before.keys();keys.hasNext();){String pkg=keys.next();if(!versions.optString(pkg).equals(before.getString(pkg)))throw new IllegalArgumentException("系统组件已变化，请重新读取端口");}
                }
                valid.put("packageVersions",versions);
            }
            desired=valid.toString();
            if(desired.length()>8192)throw new IllegalArgumentException("含系统组件版本的本机配置过长");
        }
        synchronized(profileLock){
            // Native enumeration may wait for initialization. A second writer must not be
            // overwritten using the revision observed before that wait.
            if(!digest(prefs.getString("profile","")).equals(expected))throw new IllegalStateException("本机配置已变化，请重新读取");
            if(!prefs.edit().putString("profile",desired).commit())throw new IllegalStateException("本机配置未保存");
        }
    }
    @androidx.annotation.RequiresApi(21)
    private static final class Api21 {
        static JSONObject inputs(Context c)throws Exception{return new TvInputController(c).maintenanceInputs();}
        static boolean contains(Context c,String id){
            TvInputController.Snapshot snapshot=new TvInputController(c).inspectSources();boolean found=false;
            for(TvInputInfo input:snapshot.inputs)if(id.equals(input.getId())){found=true;break;}
            for(VendorTvInputs.Source source:snapshot.vendor.sources)if(id.equals(source.key)){found=true;break;}
            return found;
        }
        static void collect(Context context,Set<String> ids,Set<String> xiaomi,Map<String,Integer> standard,JSONObject versions)throws Exception{
            TvInputController controller=new TvInputController(context);
            TvInputController.Snapshot snapshot=controller.inspect();
            if(snapshot.state==TvInputController.InputState.RESTRICTED)throw new IllegalStateException("标准输入信息受限，不能更新端口映射");
            for(TvInputInfo input:snapshot.raw)if(input!=null&&input.isPassthroughInput() && TvInputController.enabledSystem(context,input.getServiceInfo())){
                ids.add(input.getId());if(input.getType()==TvInputInfo.TYPE_HDMI && TvInputController.canOpen(context,new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer.ExternalSourceActivity")))xiaomi.add(input.getId());
                if(input.getParentId()==null)standard.put(input.getId(),input.getType());
                String pkg=input.getServiceInfo().packageName;versions.put(pkg,VendorTvInputs.packageStamp(context,pkg));
            }
        }
    }
    static JSONObject maintenanceInputs(Context context)throws Exception{
        if(Build.VERSION.SDK_INT>=21)return Api21.inputs(context);
        JSONArray sources=new JSONArray();
        for(HdmiTarget target:HdmiTarget.available(context))sources.put(new JSONObject().put("id",target.id).put("label",target.label)
            .put("type",VendorTvInputs.TYPE_HDMI).put("hdmi",true));
        return new JSONObject().put("supported",true).put("standardSupported",false).put("scope","native_hdmi")
            .put("state",sources.length()==0?"EMPTY":"READY").put("sources",new JSONArray()).put("vendorSources",sources)
            .put("configuration",new DeviceInputProfile(context).snapshot());
    }
    static synchronized boolean hasLiveRequest(){return android.os.SystemClock.elapsedRealtime()-requestAt<30000&&"pending".equals(last.optString("result"));}
    static String request(Context c,String id) throws Exception {
        synchronized(DeviceInputProfile.class){if(!pending.isEmpty())throw new IllegalStateException("上一次信号源请求尚未处理");}
        if(id==null)throw new IllegalArgumentException("信号源不存在");
        String saved="";
        if(Build.VERSION.SDK_INT>=21){if(!Api21.contains(c,id))throw new IllegalArgumentException("信号源不存在或已隐藏");}
        else{
            for(HdmiTarget target:HdmiTarget.available(c))if(id.equals(target.id)){saved=target.encode();break;}
            if(saved.isEmpty())throw new IllegalArgumentException("信号源不存在或已隐藏");
        }
        final String token=UUID.randomUUID().toString();
        synchronized(DeviceInputProfile.class){
            if(!pending.isEmpty())throw new IllegalStateException("上一次信号源请求尚未处理");
            pending=id;request=token;pendingTarget=saved;requestAt=android.os.SystemClock.elapsedRealtime();last=new JSONObject().put("request",request).put("id",id).put("result","pending");
        }
        try{c.startActivity(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException failure){synchronized(DeviceInputProfile.class){if(token.equals(request)){pending="";pendingTarget="";last.put("result","failed");}}throw failure;}
        return token;
    }
    private static synchronized JSONObject lastSnapshot() throws JSONException {return new JSONObject(last.toString());}
    static final class Request {final String id,token,saved;Request(String id,String token,String saved){this.id=id;this.token=token;this.saved=saved;}}
    static synchronized Request consume(){Request value=new Request(pending,request,pendingTarget);pending="";pendingTarget="";return value;}
    static synchronized void cancelled(String token){result(token,"cancelled_unverified");}
    static synchronized void dispatched(String token,boolean opened){result(token,opened?"dispatched":"failed");}
    private static void result(String token,String result){if(!token.equals(request))return;try{last.put("result",result).put("physicalVerified",false);}catch(JSONException ignored){}}
}
