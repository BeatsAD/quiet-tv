package home.quiet.tv;

import android.content.*;
import android.content.pm.PackageInfo;
import android.media.tv.TvInputInfo;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Saved identity, not an executable Intent. Re-resolve the current firmware on every use. */
final class HdmiTarget {
    final String kind,id,label,scope;
    private HdmiTarget(String kind,String id,String label,String scope){this.kind=kind;this.id=id;this.label=label;this.scope=scope;}
    String encode(){
        try{return new JSONObject().put("kind",kind).put("id",id).put("label",label).put("scope",scope).toString();}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    static HdmiTarget decode(String saved){
        try{JSONObject value=new JSONObject(saved);String kind=value.getString("kind"),id=value.getString("id"),label=value.getString("label"),scope=value.getString("scope");
            if((!"standard".equals(kind)&&!"vendor".equals(kind)&&!"adb".equals(kind))||id.isEmpty()||label.isEmpty()||scope.isEmpty())return null;
            return new HdmiTarget(kind,id,label,scope);
        }catch(JSONException invalid){return null;}
    }
    private static String stamp(Context context,String pkg) throws Exception {
        PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);
        return pkg+":"+info.versionCode+":"+info.lastUpdateTime;
    }
    static List<HdmiTarget> available(Context context){
        if(Build.VERSION.SDK_INT<21)return nativeTargets(context,legacySources(context));
        return Api21.available(context);
    }
    @androidx.annotation.RequiresApi(21)
    static final class Api21 {
        static List<HdmiTarget> available(Context context){return available(context,new TvInputController(context).inspectSources());}
        static SavedInputLaunch prepare(Context context,HdmiTarget target)throws Exception{return InputLaunch.saved(context,target);}
        static String foreground(Context context,HdmiTarget target)throws Exception{
            InputLaunch launch=InputLaunch.saved(context,target);
            return launch==null||!"standard".equals(target.kind)||launch.intents.isEmpty()?"":launch.intents.get(0).getComponent().flattenToString();
        }
        static List<HdmiTarget> available(Context context,TvInputController.Snapshot snapshot){
            ArrayList<HdmiTarget> result=new ArrayList<>();TvInputController controller=new TvInputController(context);
            for(TvInputInfo input:snapshot.inputs)try{
                if(input.getType()!=TvInputInfo.TYPE_HDMI)continue;
                Intent route=controller.hdmiIntent(input);if(route==null)continue;
                String scope=DeviceInputProfile.digest(Build.FINGERPRINT+"\n"+route.toUri(0)+"\n"+stamp(context,input.getServiceInfo().packageName)+"\n"+stamp(context,route.getComponent().getPackageName()));
                result.add(new HdmiTarget("standard",input.getId(),controller.inputLabel(input),scope));
            }catch(Exception failure){DiagnosticLog.info("input.saved.inspect","standard route unavailable");}
            result.addAll(nativeTargets(context,snapshot.vendor.sources));
            return unique(result);
        }
    }
    static List<VendorTvInputs.Source> legacySources(Context context){
        List<VendorTvInputs.Source> sources=VendorTvInputs.legacySources(context);
        for(Iterator<VendorTvInputs.Source> it=sources.iterator();it.hasNext();)if(!it.next().hdmi)it.remove();
        return sources;
    }
    static List<HdmiTarget> nativeTargets(Context context,List<VendorTvInputs.Source> sources){
        ArrayList<HdmiTarget> result=new ArrayList<>();
        for(VendorTvInputs.Source source:sources)try{
            if(!source.hdmi)continue;
            result.add(new HdmiTarget("vendor",source.key,source.label,VendorTvInputs.scope(context,source)));
        }catch(Exception failure){DiagnosticLog.info("input.saved.inspect","vendor route unavailable");}
        return unique(result);
    }
    private static List<HdmiTarget> unique(ArrayList<HdmiTarget> result){
        // Duplicate provider names cannot identify a port unambiguously.
        HashMap<String,Integer> counts=new HashMap<>();for(HdmiTarget target:result){String key=target.kind+":"+target.id;counts.put(key,counts.containsKey(key)?counts.get(key)+1:1);}
        for(Iterator<HdmiTarget> it=result.iterator();it.hasNext();){HdmiTarget target=it.next();if(counts.get(target.kind+":"+target.id)!=1)it.remove();}
        return result;
    }
    static HdmiTarget adb(String id,String label,String scope){return new HdmiTarget("adb",id,label,scope);}
    static HdmiTarget vendor(String id,String label,String scope){return new HdmiTarget("vendor",id,label,scope);}
    HdmiTarget withLabel(String name){return new HdmiTarget(kind,id,name,scope);}
    boolean matches(HdmiTarget current){return current!=null&&kind.equals(current.kind)&&id.equals(current.id)&&scope.equals(current.scope);}
    boolean availableNow(Context context){if("adb".equals(kind)){try{return AdbHdmi.prepare(context,this,null)!=null;}catch(Exception unavailable){return false;}}for(HdmiTarget current:available(context))if(matches(current))return true;return false;}
    /** Worker-only compatibility helper; UI callers keep the prepared dispatch receipt. */
    String foregroundComponent(Context context){
        try{return Build.VERSION.SDK_INT>=21?Api21.foreground(context,this):"";}
        catch(Exception unavailable){return "";}
    }
    SavedInputLaunch prepare(Context context) throws Exception {if("adb".equals(kind))return AdbHdmi.prepare(context,this,null);return Build.VERSION.SDK_INT>=21?Api21.prepare(context,this):SavedInputLaunch.legacy(context,this);}
    boolean open(Context context){return open(context,false);}
    boolean open(Context context,boolean automatic){
        try{SavedInputLaunch launch=prepare(context);if(launch==null)return false;
            if(!automatic)HomeGuardService.bootSourceUserAction(context,"saved_hdmi");
            boolean opened=launch.dispatch(context);if(opened)HomeGuardService.savedInputSelected(context,automatic);return opened;
        }catch(Exception failure){DiagnosticLog.error("input.saved.failed",failure);return false;}
    }
}
