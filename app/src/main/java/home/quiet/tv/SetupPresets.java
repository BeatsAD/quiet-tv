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
            validateBootAds(candidate);
            validateHome(candidate);
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
    boolean recommended(String pkg){JSONObject value=app(pkg);return value!=null && !bootAd(pkg) && value.optBoolean("recommendDisable") && allows(pkg,"DISABLE");}
    boolean bootAd(String pkg){JSONObject value=app(pkg);return value!=null && "bootAd".equals(value.optString("category"));}
    boolean bootAdMatches(String pkg,long version){JSONObject value=app(pkg);return bootAd(pkg) && version>=0 && value.optLong("versionCode",-1)==version;}
    JSONArray bootAds(){JSONArray result=new JSONArray(),apps=device==null?null:device.optJSONArray("apps");if(apps!=null)for(int i=0;i<apps.length();i++){JSONObject value=apps.optJSONObject(i);if(value!=null && bootAd(value.optString("package")))result.put(value);}return result;}
    boolean matched(){return device!=null;}
    static void validateBootAds(JSONObject device) throws JSONException {
        JSONArray apps=device.optJSONArray("apps");if(apps==null)return;
        for(int i=0;i<apps.length();i++){
            JSONObject app=apps.getJSONObject(i);if(!"bootAd".equals(app.optString("category")))continue;
            if(device.optString("brand").isEmpty() || device.optString("model").isEmpty() || device.optString("fingerprint").isEmpty())throw new JSONException("开机广告规则必须精确限定品牌、型号和固件");
            Object version=app.opt("versionCode");
            if(!app.optString("package").matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || !(version instanceof Integer || version instanceof Long) || app.optLong("versionCode",-1)<0 || app.optString("purpose").isEmpty() || app.optString("impact").isEmpty() || !app.optString("source").startsWith("https://"))throw new JSONException("开机广告规则缺少包版本、用途、影响或来源");
            JSONArray actions=app.getJSONArray("actions");boolean enable=false,disable=false;
            for(int j=0;j<actions.length();j++){String action=actions.getString(j);if("ENABLE".equals(action))enable=true;else if("DISABLE".equals(action))disable=true;else throw new JSONException("开机广告规则仅允许停用和恢复启用状态");}
            if(!enable || !disable || app.optBoolean("recommendDisable"))throw new JSONException("开机广告必须可恢复且不能作为首次设置的默认推荐");
        }
    }
    String purpose(String pkg){JSONObject value=app(pkg);return value==null?"用途待确认":value.optString("purpose","用途待确认");}
    String home(){JSONObject value=device==null?null:device.optJSONObject("home");return value==null?"":value.optString("component");}
    boolean homeAllows(String capability){JSONObject value=device==null?null:device.optJSONObject("home");return value!=null && value.optBoolean(capability)
            && !(value.optBoolean("preserveOriginal") && ("disableVerified".equals(capability) || "uninstallVerified".equals(capability)));}
    boolean protectedHomePackage(String pkg){
        JSONObject home=device==null?null:device.optJSONObject("home");if(home==null)return false;
        if(this.home().startsWith(pkg+"/") && (home.optBoolean("preserveOriginal") || !home.optBoolean("disableVerified")))return true;
        JSONObject boot=home.optJSONObject("vendorBoot");if(boot!=null && pkg.equals(boot.optString("systemPackage")))return true;
        JSONArray required=home.optJSONArray("requiredPackages");
        if(required!=null)for(int i=0;i<required.length();i++)if(pkg.equals(required.optString(i)))return true;
        return false;
    }
    JSONObject vendorBoot(){JSONObject home=device==null?null:device.optJSONObject("home");return home==null?null:home.optJSONObject("vendorBoot");}
    static void validateHome(JSONObject device) throws JSONException {
        JSONObject home=device.optJSONObject("home");if(home==null)return;
        if(home.optBoolean("preserveOriginal") && (home.optBoolean("disableVerified") || home.optBoolean("uninstallVerified")))throw new JSONException("必须保留的原桌面不能开放禁用或卸载");
        JSONArray required=home.optJSONArray("requiredPackages");
        if(required!=null)for(int i=0;i<required.length();i++)if(!required.getString(i).matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))throw new JSONException("原桌面依赖包名无效");
        JSONObject boot=home.optJSONObject("vendorBoot");if(boot==null)return;
        Object version=boot.opt("versionCode");
        if(device.optString("brand").isEmpty() || device.optString("model").isEmpty() || device.optString("fingerprint").isEmpty()
                || home.optString("component").isEmpty() || !home.optBoolean("preserveOriginal") || !boot.optBoolean("verified")
                || !"com.skyworth.noriceguide.show".equals(boot.optString("action"))
                || !boot.optString("systemPackage").matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
                || !(version instanceof Integer || version instanceof Long) || boot.optLong("versionCode",-1)<0
                || !boot.optBoolean("afterUnlock") || !boot.optString("source").startsWith("https://"))throw new JSONException("开机跳板缺少精确固件、组件版本或解锁后验证证据");
    }
    // References are documentation only. They can never participate in permission matching.
    static void validateReference(JSONObject reference) throws JSONException {
        if(!reference.has("model") || !reference.has("source") || !reference.has("limitations"))throw new JSONException("机型参考缺少来源或限制");
        if(reference.has("fingerprint") || reference.has("apps") || reference.has("home"))throw new JSONException("未验收参考不能携带执行规则");
    }
}
