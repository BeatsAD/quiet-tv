package home.quiet.tv;

import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Reported model protocols or explicit per-firmware records; never infer ports from the brand. */
final class XiaomiInputs {
    static final ComponentName PLAYER=new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer.ExternalSourceActivity");
    private static final ComponentName MITV2_DTMB=new ComponentName(PLAYER.getPackageName(),"dtmb.DTMBActivity");
    // https://github.com/BeatsAD/quiet-tv/issues/132: MiTV2-49 / Android 4.3 only.
    // These are test candidates, not firmware acceptance. Pairing scopes include the fingerprint.
    static List<VendorTvInputs.Source> reported(String model,int api){
        ArrayList<VendorTvInputs.Source> sources=new ArrayList<>();
        if(!"MiTV2-49".equals(model)||api!=18)return sources;
        for(int port=1;port<=3;port++)sources.add(external("HDMI "+port,22+port,VendorTvInputs.TYPE_HDMI,"port="+port));
        sources.add(external("AV",2,VendorTvInputs.TYPE_COMPOSITE,""));
        sources.add(external("VGA",0,VendorTvInputs.TYPE_VGA,""));
        sources.add(external("TV",1,VendorTvInputs.TYPE_TUNER,""));
        sources.add(new VendorTvInputs.Source("DTMB",new Intent("com.xiaomi.mitv.tvplayer.DTMB_PLAY").setComponent(MITV2_DTMB)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),null,"xiaomi:dtmb",PLAYER.getPackageName(),VendorTvInputs.TYPE_TUNER));
        return sources;
    }
    private static VendorTvInputs.Source external(String label,int source,int type,String mapping){
        Intent intent=new Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY").setComponent(PLAYER).putExtra("input",source)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        VendorTvInputs.Source item=new VendorTvInputs.Source(label,intent,null,"xiaomi:"+source,PLAYER.getPackageName(),type);
        item.mapping=mapping;return item;
    }
    static List<VendorTvInputs.Source> records(JSONObject profile) throws JSONException {
        ArrayList<VendorTvInputs.Source> result=new ArrayList<>();
        if(!profile.has("nativeSources"))return result;
        if(profile.optInt("schemaVersion")!=2)throw new JSONException("原生端口需要配置版本 2");
        JSONArray records=profile.getJSONArray("nativeSources");Set<Integer> ports=new HashSet<>(),sources=new HashSet<>();
        for(int i=0;i<records.length();i++){
            JSONObject record=records.getJSONObject(i);
            for(Iterator<String> keys=record.keys();keys.hasNext();)if(!Arrays.asList("protocol","port","source").contains(keys.next()))throw new JSONException("未知原生端口字段");
            if(!"xiaomi".equals(record.getString("protocol"))||!(record.get("port") instanceof Integer)||!(record.get("source") instanceof Integer))throw new JSONException("原生端口协议或参数无效");
            int port=record.getInt("port"),source=record.getInt("source");
            if(port<1||port>4||source<19||source>32||!ports.add(port)||!sources.add(source))throw new JSONException("原生 HDMI 端口必须唯一且在支持范围内");
            result.add(external("HDMI "+port,source,VendorTvInputs.TYPE_HDMI,"port="+port));
        }
        return result;
    }
    static List<VendorTvInputs.Source> load(Context context,JSONObject profile) throws JSONException {
        List<VendorTvInputs.Source> sources=profile.has("nativeSources")?records(profile):reported(Build.MODEL,Build.VERSION.SDK_INT);
        if(sources.isEmpty())return sources;
        // A known TV model name plus its accessible system player is required. A Mi Box may ship
        // the same package but has HDMI output only; no default ports are manufactured for it.
        String model=Build.MODEL.toLowerCase(Locale.ROOT);
        if(!model.startsWith("mitv")||model.contains("box")||boxFirmware()||!SystemSettingsRouter.xiaomi(Build.MANUFACTURER,Build.BRAND,Build.MODEL)
                ||profile.has("nativeSources")&&!Build.FINGERPRINT.equals(profile.optString("fingerprint")))return Collections.emptyList();
        for(Iterator<VendorTvInputs.Source> it=sources.iterator();it.hasNext();)if(!InputComponents.canOpen(context,it.next().intent.getComponent()))it.remove();
        return sources;
    }
    private static boolean boxFirmware(){
        try{
            Object value=Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.build.characteristics");
            String flags=value instanceof String?((String)value).toLowerCase(Locale.ROOT):"";
            return flags.contains("stb")||flags.contains("mbx");
        }catch(Exception|LinkageError unavailable){return false;}
    }
}
