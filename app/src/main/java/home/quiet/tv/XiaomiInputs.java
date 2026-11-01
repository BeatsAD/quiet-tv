package home.quiet.tv;

import android.content.*;
import android.media.tv.TvInputInfo;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Explicit per-firmware port records only. hdmiN_order is not a physical-port enumeration. */
@android.annotation.TargetApi(21)
final class XiaomiInputs {
    static final ComponentName PLAYER=new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer.ExternalSourceActivity");
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
            Intent intent=new Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY").setComponent(PLAYER).putExtra("input",source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            VendorTvInputs.Source item=new VendorTvInputs.Source("HDMI "+port,intent,null,"xiaomi:"+source,PLAYER.getPackageName(),TvInputInfo.TYPE_HDMI);
            item.mapping="port="+port;result.add(item);
        }
        return result;
    }
    static List<VendorTvInputs.Source> load(Context context,JSONObject profile) throws JSONException {
        List<VendorTvInputs.Source> sources=records(profile);if(sources.isEmpty())return sources;
        // A known TV model name plus its accessible system player is required. A Mi Box may ship
        // the same package but has HDMI output only; no default ports are manufactured for it.
        String model=Build.MODEL.toLowerCase(Locale.ROOT);
        if(!model.startsWith("mitv")||model.contains("box")||boxFirmware()||!SystemSettingsRouter.xiaomi(Build.MANUFACTURER,Build.BRAND,Build.MODEL)
                ||!Build.FINGERPRINT.equals(profile.optString("fingerprint"))||!TvInputController.canOpen(context,PLAYER))return Collections.emptyList();
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
