package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import org.json.*;

/** Shell-authorized data only. No downloaded code or commands execute in the launcher. */
final class DeviceAdaptation {
    private final Context context;
    DeviceAdaptation(Context context){this.context=context;}
    JSONObject snapshot() throws Exception {
        String raw=context.getSharedPreferences("device-adaptation",Context.MODE_PRIVATE).getString("rules","");
        boolean active=!raw.isEmpty() && new SetupPresets(raw,Build.BRAND,Build.MODEL,Build.FINGERPRINT).matched();
        return new JSONObject().put("revision",DeviceInputProfile.digest(raw)).put("rules",raw.isEmpty()?JSONObject.NULL:new JSONObject(raw))
            .put("source",active?"computer":raw.isEmpty()?"legacy-default":"stale");
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    void save(String raw,String revision) throws Exception {
        if(!snapshot().getString("revision").equals(revision))throw new IllegalStateException("适配规则已变化，请重新读取");
        if(raw==null || raw.length()>32768)throw new IllegalArgumentException("适配规则过长或缺失");
        JSONObject root=new JSONObject(raw);JSONArray devices=root.getJSONArray("devices");
        if(root.has("references") || devices.length()!=1)throw new IllegalArgumentException("只接收当前设备的一条执行规则");
        if(!new SetupPresets(raw,Build.BRAND,Build.MODEL,Build.FINGERPRINT).matched())throw new IllegalArgumentException("规则与当前机型或固件不符");
        JSONObject pending=new SetupStore(context).get().optJSONObject("takeover");
        if(pending!=null && java.util.Arrays.asList("running","incomplete").contains(pending.optString("state")))throw new IllegalStateException("请先恢复未完成的接管，再更新规则");
        if(!context.getSharedPreferences("device-adaptation",Context.MODE_PRIVATE).edit().putString("rules",root.toString()).commit())throw new IllegalStateException("适配规则未保存");
    }
}
