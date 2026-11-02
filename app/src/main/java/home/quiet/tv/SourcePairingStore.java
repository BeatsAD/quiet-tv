package home.quiet.tv;

import android.content.*;
import android.os.Build;
import org.json.*;

/** Pairing overlays and the selected HDMI are committed together in the existing boot store. */
final class SourcePairingStore {
    private final Context context;
    private final SharedPreferences prefs;
    SourcePairingStore(Context context){this.context=context;this.prefs=context.getSharedPreferences("boot-app",0);}
    JSONObject value(){try{return new JSONObject(prefs.getString("source-pairing","{}"));}catch(JSONException invalid){return new JSONObject();}}
    String revision()throws Exception{
        return DeviceInputProfile.digest(prefs.getString("source-pairing","{}")+"\n"+prefs.getString("watch-hdmi","")+"\n"+prefs.getString("tested-hdmi","")
            +"\n"+prefs.getBoolean("hdmi-boot",false)+"\n"+context.getSharedPreferences("device-input-profile",0).getString("profile","")
            +"\n"+context.getSharedPreferences("system-entry-maintenance",0).getString("inputs","{}"));
    }
    JSONObject entry(){JSONObject entry=value().optJSONObject("entry");return entry!=null&&entry.has("id")?entry:null;}
    boolean overridesEntry(){return value().has("entry");}
    String label(String id){
        JSONObject labels=value().optJSONObject("labels"),item=labels==null?null:labels.optJSONObject(id);
        if(item==null||!Build.FINGERPRINT.equals(item.optString("fingerprint")))return "";
        try{return VendorTvInputs.packageStamp(context,item.getString("owner")).equals(item.getString("packageStamp"))?item.optString("label"):"";}
        catch(Exception changed){return "";}
    }
    private JSONObject backup()throws Exception{
        return new JSONObject().put("fingerprint",Build.FINGERPRINT).put("user",android.os.Process.myUid()/100000)
            .put("configuration",value()).put("watch",prefs.getString("watch-hdmi","")).put("label",prefs.getString("watch-label","HDMI"))
            .put("tested",prefs.getString("tested-hdmi","")).put("boot",prefs.getBoolean("hdmi-boot",false));
    }
    void saveEntry(JSONObject entry,String expected)throws Exception{commit(entry,null,null,"","",expected);}
    void commit(JSONObject entry,HdmiTarget target,String label,String sourceId,String owner,String expected)throws Exception{
        synchronized(BootAppStore.class){
            if(!revision().equals(expected))throw new IllegalStateException("配置已变化，请重新读取");
            JSONObject next=value();SharedPreferences.Editor edit=prefs.edit().putString("source-pairing-before",backup().toString());
            if(entry!=null)next.put("entry",entry);
            if(target!=null){
                String name=label==null||label.isEmpty()?target.label:label;
                if(name.length()>40||name.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("信号源名称无效");
                JSONObject labels=next.optJSONObject("labels");if(labels==null)labels=new JSONObject();
                if(!sourceId.isEmpty())labels.put(sourceId,new JSONObject().put("label",name).put("owner",owner)
                    .put("fingerprint",Build.FINGERPRINT).put("packageStamp",VendorTvInputs.packageStamp(context,owner)));
                next.put("labels",labels);
                String saved=target.withLabel(name).encode();
                edit.putString("watch-hdmi",saved).putString("watch-label",name).putString("tested-hdmi",saved)
                    .putBoolean("hdmi-boot",false).remove("test-hdmi").putBoolean("cover-stopped",true).putBoolean("cover-armed",false);
            }
            next.put("schemaVersion",1);
            String serialized=next.toString();edit.putString("source-pairing",serialized);
            BootAppStore.commit(prefs,edit);
            if(!serialized.equals(prefs.getString("source-pairing","")))throw new java.io.IOException("配置读回失败");
        }
    }
    JSONObject watchSnapshot()throws JSONException{
        String saved=prefs.getString("watch-hdmi","");JSONObject target=null;
        try{if(!saved.isEmpty())target=new JSONObject(saved);}catch(JSONException invalid){/* Keep an explicit unreadable state. */}
        return new JSONObject().put("target",target==null?JSONObject.NULL:target).put("targetPresent",!saved.isEmpty())
            .put("label",prefs.getString("watch-label","HDMI")).put("confirmed",!saved.isEmpty()&&saved.equals(prefs.getString("tested-hdmi","")))
            .put("boot",prefs.getBoolean("hdmi-boot",false)).put("availability","RECHECK_ON_USE");
    }
    JSONObject previousSummary()throws JSONException{
        JSONObject value=previous();String watch=value.optString("watch");value.remove("watch");value.remove("tested");
        if(!watch.isEmpty())try{value.put("watch",new JSONObject(watch));}catch(JSONException invalid){value.put("watchUnreadable",true);}
        return value;
    }
    JSONObject previous(){try{return new JSONObject(prefs.getString("source-pairing-before","{}"));}catch(JSONException invalid){return new JSONObject();}}
    void restore(String expected,boolean defaults)throws Exception{
        synchronized(BootAppStore.class){
            if(!revision().equals(expected))throw new IllegalStateException("配置已变化，请重新读取");
            JSONObject old=previous();
            if(!defaults&&(!Build.FINGERPRINT.equals(old.optString("fingerprint"))||old.optInt("user",-1)!=android.os.Process.myUid()/100000))
                throw new IllegalStateException("恢复记录不属于当前固件或用户");
            JSONObject next=defaults?new JSONObject().put("entry",new JSONObject()).put("labels",new JSONObject()):old.getJSONObject("configuration");
            String watch=defaults?"":old.optString("watch");
            if(!watch.isEmpty()){HdmiTarget target=HdmiTarget.decode(watch);if(target==null||!target.availableNow(context))throw new IllegalStateException("原端口已变化，请重新配对");}
            JSONObject panel=next.optJSONObject("entry");
            if(panel!=null&&panel.has("id")&&!SourcePairing.validEntry(context,panel))throw new IllegalStateException("原入口已变化，请重新配对");
            SharedPreferences.Editor edit=prefs.edit().putString("source-pairing-before",backup().toString()).putString("source-pairing",next.toString())
                .putString("watch-hdmi",watch).putString("watch-label",defaults?"HDMI":old.optString("label","HDMI"))
                .putString("tested-hdmi",defaults?"":old.optString("tested")).putBoolean("hdmi-boot",false).remove("test-hdmi")
                .putBoolean("cover-stopped",true).putBoolean("cover-armed",false);
            BootAppStore.commit(prefs,edit);
        }
    }
}
