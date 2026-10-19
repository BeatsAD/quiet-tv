package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Rules annotate discovered packages; they never manufacture a device's app inventory. */
final class SetupPresets {
    private final JSONObject device;
    SetupPresets(String source,String brand,String model,String firmware) throws JSONException {
        JSONObject root=new JSONObject(source);JSONObject match=null;
        if(root.getInt("schema")!=1)throw new JSONException("不支持的设备规则版本");
        JSONArray references=root.optJSONArray("references");
        if(references!=null)for(int i=0;i<references.length();i++)validateReference(references.getJSONObject(i));
        JSONArray devices=root.getJSONArray("devices");
        for(int i=0;i<devices.length();i++){
            JSONObject candidate=devices.getJSONObject(i);
            // A brand match alone never grants permission to alter system packages.
            if(!candidate.optString("fingerprint").equals(firmware) || firmware.isEmpty())continue;
            if(candidate.has("brand") && !candidate.getString("brand").equalsIgnoreCase(brand))continue;
            if(candidate.has("model") && !candidate.getString("model").equals(model))continue;
            if(match!=null)throw new JSONException("设备规则存在冲突");match=candidate;
        }
        device=match;
    }
    static SetupPresets load(Context context) {
        try(InputStream in=context.getAssets().open("setup-presets.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int size;while((size=in.read(buffer))!=-1)out.write(buffer,0,size);
            return new SetupPresets(out.toString("UTF-8"),Build.BRAND,Build.MODEL,Build.FINGERPRINT);
        }catch(Exception e){throw new IllegalStateException("设备规则无法读取",e);}
    }
    JSONObject app(String pkg){
        JSONArray apps=device==null?null:device.optJSONArray("apps");
        if(apps!=null)for(int i=0;i<apps.length();i++){JSONObject value=apps.optJSONObject(i);if(value!=null && pkg.equals(value.optString("package")))return value;}
        return null;
    }
    boolean allows(String pkg,String action){JSONObject value=app(pkg);JSONArray actions=value==null?null:value.optJSONArray("actions");if(actions!=null)for(int i=0;i<actions.length();i++)if(action.equals(actions.optString(i)))return true;return false;}
    boolean recommended(String pkg){JSONObject value=app(pkg);return value!=null && value.optBoolean("recommendDisable") && allows(pkg,"DISABLE");}
    String purpose(String pkg){JSONObject value=app(pkg);return value==null?"用途待确认":value.optString("purpose","用途待确认");}
    String home(){JSONObject value=device==null?null:device.optJSONObject("home");return value==null?"":value.optString("component");}
    boolean homeAllows(String capability){JSONObject value=device==null?null:device.optJSONObject("home");return value!=null && value.optBoolean(capability);}
    // References are documentation only. They can never participate in permission matching.
    static void validateReference(JSONObject reference) throws JSONException {
        if(!reference.has("model") || !reference.has("source") || !reference.has("limitations"))throw new JSONException("机型参考缺少来源或限制");
        if(reference.has("fingerprint") || reference.has("apps") || reference.has("home"))throw new JSONException("未验收参考不能携带执行规则");
    }
}
