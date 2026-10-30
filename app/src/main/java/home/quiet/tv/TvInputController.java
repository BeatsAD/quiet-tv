package home.quiet.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.os.Build;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.util.*;

/** Standard inputs first; explicit, installed system components provide firmware-specific fallbacks. */
@android.annotation.TargetApi(21)
final class TvInputController {
    private static final String TAG="QuietTV";
    enum InputState { READY, EMPTY, FILTERED, UNAVAILABLE, RESTRICTED, FAILED }
    static final class Snapshot {
        final ArrayList<TvInputInfo> inputs=new ArrayList<>();
        final TvInputPanels.Snapshot nativePanels=new TvInputPanels.Snapshot();
        InputState state=InputState.EMPTY;
        int reported,filtered,duplicates,itemFailures,metadataWarnings;
        final ArrayList<TvInputInfo> raw=new ArrayList<>();
        final Map<String,String> exclusions=new HashMap<>();
        List<TvInputPanels.Panel> availablePanels(){return nativePanels.availablePanels();}
        TvInputPanels.Panel directPanel(){return nativePanels.directPanel(inputs.isEmpty());}
    }

    private final Context context;
    private final PackageManager pm;
    TvInputController(Context context){this.context=context;pm=context.getPackageManager();}

    List<TvInputInfo> rawInputs(){
        TvInputManager manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);
        if(manager==null)throw new IllegalStateException("系统信号源服务不可用");
        return manager.getTvInputList();
    }
    TvInputInfo findInput(String id){for(TvInputInfo input:rawInputs())if(input.getId().equals(id))return input;return null;}
    TvInputInfo mappedInput(TvInputInfo input){
        String target=new DeviceInputProfile(context).target(input.getId());
        if(target.equals(input.getId()))return input;
        TvInputInfo mapped=findInput(target);
        return mapped!=null && mapped.isPassthroughInput() && enabledSystem(context,mapped.getServiceInfo())?mapped:null;
    }
    JSONObject maintenanceInputs() throws Exception {
        Snapshot snapshot=inspect();JSONArray values=new JSONArray();DeviceInputProfile profile=new DeviceInputProfile(context);
        Set<String> shown=new HashSet<>();for(TvInputInfo input:snapshot.inputs)shown.add(input.getId());
        for(TvInputInfo input:snapshot.raw)try{
            values.put(new JSONObject().put("id",input.getId()).put("label",inputLabel(input)).put("firmwareLabel",input.loadLabel(context).toString())
                .put("shown",shown.contains(input.getId())).put("filterReason",snapshot.exclusions.containsKey(input.getId())?snapshot.exclusions.get(input.getId()):"")
                .put("passthrough",input.isPassthroughInput()).put("type",input.getType()).put("target",profile.target(input.getId()))
                .put("system",enabledSystem(context,input.getServiceInfo())));
        }catch(RuntimeException failed){DiagnosticLog.error("input.maintenance.item_failed",failed);}
        return new JSONObject().put("supported",true).put("state",snapshot.state.name()).put("reported",snapshot.reported)
            .put("filtered",snapshot.filtered).put("duplicates",snapshot.duplicates).put("itemFailures",snapshot.itemFailures)
            .put("metadataWarnings",snapshot.metadataWarnings).put("sources",values).put("configuration",profile.snapshot());
    }

    Snapshot inspect(){
        Snapshot result=new Snapshot();
        try{
            TvInputManager manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);
            if(manager==null)result.state=InputState.UNAVAILABLE;
            else{List<TvInputInfo> raw=manager.getTvInputList();if(raw==null)throw new IllegalStateException("Null input list");result.raw.addAll(raw);filterInputs(result);}
        }catch(SecurityException failure){result.state=InputState.RESTRICTED;DiagnosticLog.error("input.enumeration.restricted",failure);}
        catch(RuntimeException failure){result.state=InputState.FAILED;DiagnosticLog.error("input.enumeration.failed",failure);}
        TvInputPanels.Snapshot nativePanels=new TvInputPanels(context).inspect();result.nativePanels.panels.addAll(nativePanels.panels);result.nativePanels.profilesUnavailable=nativePanels.profilesUnavailable;
        DiagnosticLog.info("input.enumeration","state="+result.state+" reported="+result.reported+" filtered="+result.filtered+" duplicates="+result.duplicates+" itemFailures="+result.itemFailures+" metadataWarnings="+result.metadataWarnings+" usable="+result.inputs.size()+" panels="+result.nativePanels.panels.size());
        return result;
    }

    private void filterInputs(Snapshot result){
        DeviceInputProfile profile=new DeviceInputProfile(context);LinkedHashMap<String,TvInputInfo> candidates=new LinkedHashMap<>();
        HashMap<String,String> parents=new HashMap<>();HashSet<String> hidden=new HashSet<>();boolean restricted=false;
        for(TvInputInfo input:result.raw){
            result.reported++;
            try{
                String id=input.getId();if(id==null||id.isEmpty())throw new IllegalStateException("Missing input ID");
                input.getType();input.isPassthroughInput();
                if(hiddenInput(Build.MODEL,input.getServiceInfo().packageName,id,Build.DISPLAY)||profile.hidden(id)){
                    hidden.add(id);result.exclusions.put(id,"profile_hidden");result.filtered++;continue;
                }
                boolean systemHidden=false;
                if(Build.VERSION.SDK_INT>=24)try{systemHidden=input.isHidden(context);}catch(RuntimeException failure){
                    // The hiding preference is optional metadata, not the input service itself.
                    result.metadataWarnings++;DiagnosticLog.error("input.metadata.hidden_failed",failure);
                }
                if(systemHidden){hidden.add(id);result.exclusions.put(id,"user_hidden");result.filtered++;continue;}
                if(candidates.containsKey(id)){result.duplicates++;continue;}
                parents.put(id,input.getParentId());candidates.put(id,input);
            }catch(SecurityException failure){restricted=true;result.itemFailures++;DiagnosticLog.error("input.item.restricted",failure);}
            catch(RuntimeException failure){result.itemFailures++;DiagnosticLog.error("input.item.failed",failure);}
        }
        for(Map.Entry<String,TvInputInfo> entry:candidates.entrySet()){
            String parent=parents.get(entry.getKey());
            if(parent!=null&&hidden.contains(parent)){result.filtered++;result.exclusions.put(entry.getKey(),"parent_hidden");continue;}
            if(parent!=null&&!parent.equals(entry.getKey())&&candidates.containsKey(parent)){result.duplicates++;result.exclusions.put(entry.getKey(),"parent_visible");continue;}
            result.inputs.add(entry.getValue());
        }
        result.state=!result.inputs.isEmpty()?InputState.READY:result.reported==0?InputState.EMPTY
            :result.itemFailures>0?(restricted?InputState.RESTRICTED:InputState.FAILED):InputState.FILTERED;
    }

    static final class Route {
        final String label,activity,action;final int source;
        Route(String label,int source){this.label=label;this.source=source;activity="ExternalSourceActivity";action="EXTSRC_PLAY";}
        Route(String activity,String action){label=null;source=-1;this.activity=activity;this.action=action;}
    }
    static Route correction(String model,String provider,int type,String id){
        if(("MiTV-ANTL0".equals(model)||"MiTV_ANTL0".equals(model)) && "com.droidlogic.tvinput".equals(provider)
                && type==TvInputInfo.TYPE_HDMI && id.endsWith("/HW6"))return new Route("HDMI 1",23);
        if(("MiTV-MFFU1".equals(model)||"MiTV_MFFU1".equals(model)) && "com.mediatek.tvinput".equals(provider)
                && type==TvInputInfo.TYPE_DISPLAY_PORT){
            if(id.endsWith("/HW9"))return new Route("DP",29);
            if(id.endsWith("/HW10"))return new Route("USB-C",30);
        }
        if(("MiTV-MFZP0".equals(model)||"MiTV_MFZP0".equals(model)) && "com.mediatek.tvinput".equals(provider)
                && type==TvInputInfo.TYPE_HDMI){
            for(int port=4;port<=6;port++)if(id.endsWith("/HW"+port))return new Route("HDMI "+(port-3),port+19);
        }
        return null;
    }
    static Route xiaomiRoute(String model,String provider,int type,String id){
        Route corrected=correction(model,provider,type,id);if(corrected!=null)return corrected;
        // CN MediaTek firmware registers these inputs but does not handle their standard VIEW URI.
        // Use only registered, known hardware IDs; never infer ports from the Xiaomi brand alone.
        if(!"com.mediatek.tvinput".equals(provider))return null;
        if(type==TvInputInfo.TYPE_HDMI){
            if("MiTV-MFZP0".equals(model)||"MiTV_MFZP0".equals(model))return null;
            for(int port=5;port<=7;port++)if(id.endsWith("/HW"+port))return new Route(null,port+18);
        }
        if(type==TvInputInfo.TYPE_COMPOSITE && id.endsWith("/HW2"))return new Route(null,2);
        if(type==TvInputInfo.TYPE_TUNER){
            if(id.endsWith("/HW0"))return new Route("dtmb.DTMBActivity","DTMB_PLAY");
            if(id.endsWith("/HW1"))return new Route("AtvActivity","ATV_PLAY");
        }
        return null;
    }
    static Route xiaomiRoute(String model,String provider,int type,String id,String firmware){
        Route corrected=correction(model,provider,type,id,firmware);if(corrected!=null)return corrected;
        if(("MiTV4-ANSM0".equals(model)||"MiTV4_ANSM0".equals(model)||"MiTV4A".equals(model))
                && "com.droidlogic.tvinput".equals(provider)){
            int major=0;try{major=Integer.parseInt(firmware.split("\\.")[0]);}catch(NumberFormatException ignored){}
            if(type==TvInputInfo.TYPE_HDMI){
                if(major<22 && id.endsWith("/HW7"))return new Route(null,23);
                if(major>=22)for(int port=5;port<=7;port++)if(id.endsWith("/HW"+port))return new Route(null,port+18);
            }
        }
        return xiaomiRoute(model,provider,type,id);
    }
    static Route xiaomiRoute(String model,String provider,int type,String id,String firmware,String label){
        Route corrected=correction(model,provider,type,id,firmware);if(corrected!=null)return corrected;
        if(type==TvInputInfo.TYPE_HDMI && id.matches(".*/HW[0-9]+")){
            Integer configured=xiaomiConfiguredSource(label);
            if(configured!=null)return new Route(null,configured);
        }
        return xiaomiRoute(model,provider,type,id,firmware);
    }
    // Aerial f1c8ea97: read the board's HDMI order before guessing from model/build numbers.
    static Integer xiaomiConfiguredSource(String label){
        if(label==null)return null;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("HDMI\\s*([0-9]+)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(label);
        if(!match.find())return null;
        try{
            int port=Integer.parseInt(match.group(1));if(port<=0)return null;
            Class<?> configuration=Class.forName("mitv.common.ConfigurationManager");
            Object manager=configuration.getMethod("getInstance").invoke(null);
            Object value=configuration.getMethod("queryTvConfigByKey",String.class).invoke(manager,"hdmi"+port+"_order");
            if(!(value instanceof String))return null;
            int source=Integer.parseInt(((String)value).trim());return source>0?source:null;
        }catch(Exception|LinkageError unavailable){return null;}
    }
    static boolean hiddenInput(String model,String provider,String id){
        return hiddenInput(model,provider,id,"");
    }
    static boolean hiddenInput(String model,String provider,String id,String firmware){
        if(ansm0Firmware(model,provider,firmware) && id.endsWith("/HDMI300004"))return true;
        return "com.mediatek.tvinput".equals(provider) && ((("MiTV-MFTR0".equals(model)||"MiTV_MFTR0".equals(model)) && id.endsWith("/HW8"))
            || (("MiTV-MFZP0".equals(model)||"MiTV_MFZP0".equals(model)) && id.endsWith("/HW7")));
    }
    private static boolean ansm0Firmware(String model,String provider,String firmware){
        return ("MiTV4-ANSM0".equals(model)||"MiTV4_ANSM0".equals(model)) && "com.droidlogic.tvinput".equals(provider)
            && "22.11.18.3386".equals(firmware);
    }
    static Route correction(String model,String provider,int type,String id,String firmware){
        // This firmware exposes physical HDMI1 as HAL HDMI3/HW7, but tvplayer expects source 23.
        if(ansm0Firmware(model,provider,firmware) && type==TvInputInfo.TYPE_HDMI && id.endsWith("/HW7"))return new Route("HDMI 1",23);
        return correction(model,provider,type,id);
    }
    private Route correction(TvInputInfo input){
        android.content.pm.ServiceInfo service=input.getServiceInfo();
        if((service.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return null;
        return correction(Build.MODEL,service.packageName,input.getType(),input.getId(),Build.DISPLAY);
    }
    String inputLabel(TvInputInfo input){
        String custom=new DeviceInputProfile(context).label(input.getId());if(!custom.isEmpty())return custom;
        Route route=correction(input);if(route!=null)return route.label;
        try{CharSequence label=input.loadLabel(context);if(label!=null&&label.length()>0)return label.toString();}
        catch(RuntimeException failure){DiagnosticLog.error("input.metadata.label_failed",failure);}
        return input.getType()==TvInputInfo.TYPE_HDMI?"HDMI":"TV input";
    }
    private Intent knownInputIntent(TvInputInfo input){
        android.content.pm.ServiceInfo service=input.getServiceInfo();
        if((service.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return null;
        String label=null;
        if(input.getType()==TvInputInfo.TYPE_HDMI)try{CharSequence value=input.loadLabel(context);if(value!=null)label=value.toString();}catch(RuntimeException unavailable){DiagnosticLog.error("input.xiaomi.label.failed",unavailable);}
        Integer custom=new DeviceInputProfile(context).xiaomi(input.getId());
        Route route=custom==null?xiaomiRoute(Build.MODEL,service.packageName,input.getType(),input.getId(),Build.DISPLAY,label):new Route(null,custom);if(route==null){DiagnosticLog.info("input.xiaomi.route","provider="+service.packageName+" type="+input.getType()+" result=no_known_route");return null;}
        ComponentName target=new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer."+route.activity);
        try{
            android.content.pm.ActivityInfo activity=pm.getActivityInfo(target,0);
            int appState=pm.getApplicationEnabledSetting(target.getPackageName()),componentState=pm.getComponentEnabledSetting(target);
            if((activity.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0 || !activity.exported
                    || !(appState==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?activity.applicationInfo.enabled:appState==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    || !(componentState==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?activity.enabled:componentState==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    || (activity.permission!=null && pm.checkPermission(activity.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED)){DiagnosticLog.info("input.xiaomi.route","component="+target.flattenToShortString()+" result=not_callable exported="+activity.exported+" appState="+appState+" componentState="+componentState+" permission="+activity.permission);return null;}
            Intent intent=new Intent("com.xiaomi.mitv.tvplayer."+route.action).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(route.source>=0)intent.putExtra("input",route.source);
            return intent;
        }catch(PackageManager.NameNotFoundException e){DiagnosticLog.info("input.xiaomi.route","component="+target.flattenToShortString()+" result=not_installed");return null;}
        catch(RuntimeException e){DiagnosticLog.error("input.xiaomi.failed","component="+target.flattenToShortString(),e);return null;}
    }

    boolean openKnownInput(TvInputInfo input){
        Intent intent=knownInputIntent(input);if(intent==null)return false;
        try{context.startActivity(intent);DiagnosticLog.info("input.xiaomi.dispatch",intent.getComponent().flattenToShortString());return true;}
        catch(RuntimeException failure){DiagnosticLog.error("input.xiaomi.failed",failure);return false;}
    }
    /** Resolve one direct HDMI route without dispatching or opening a source picker. */
    Intent hdmiIntent(TvInputInfo input){
        if(input.getType()!=TvInputInfo.TYPE_HDMI || !enabledSystem(context,input.getServiceInfo()))return null;
        input=mappedInput(input);if(input==null || input.getType()!=TvInputInfo.TYPE_HDMI)return null;
        Intent intent=knownInputIntent(input);
        if(intent==null)intent=standardIntent(input.getId(),android.media.tv.TvContract.buildChannelUriForPassthroughInput(input.getId()));
        return intent!=null?intent:fallbackInputIntent(input);
    }

    static boolean enabledSystem(Context context,ComponentInfo info){
        if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
        PackageManager pm=context.getPackageManager();
        int app=pm.getApplicationEnabledSetting(info.packageName),component=pm.getComponentEnabledSetting(new ComponentName(info.packageName,info.name));
        return (app==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.applicationInfo.enabled:app==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            && (component==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.enabled:component==PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }
    static boolean canOpen(Context context,ComponentName target){
        if(target==null)return false;
        try{
            android.content.pm.ActivityInfo info=context.getPackageManager().getActivityInfo(target,0);
            String reason=!enabledSystem(context,info)?"not_enabled_system":!info.exported?"not_exported":info.permission!=null && context.getPackageManager().checkPermission(info.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED?"permission_denied permission="+info.permission:"";
            if(!reason.isEmpty())DiagnosticLog.info("input.component","component="+target.flattenToShortString()+" result="+reason);
            return reason.isEmpty();
        }catch(PackageManager.NameNotFoundException unavailable){DiagnosticLog.info("input.component","component="+target.flattenToShortString()+" result=not_installed");return false;}
        catch(RuntimeException unavailable){DiagnosticLog.error("input.component.failed","component="+target.flattenToShortString(),unavailable);return false;}
    }
    Intent standardIntent(String inputId,android.net.Uri uri){
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,TvContract.Channels.CONTENT_ITEM_TYPE).putExtra(TvInputInfo.EXTRA_INPUT_ID,inputId);
        List<android.content.pm.ResolveInfo> handlers=pm.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY);
        android.content.pm.ResolveInfo preferred=pm.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
        ComponentName only=null;int count=0;
        DiagnosticLog.info("input.standard.resolve","handlers="+handlers.size()+" preferred="+(preferred==null || preferred.activityInfo==null?"none":new ComponentName(preferred.activityInfo.packageName,preferred.activityInfo.name).flattenToShortString()));
        for(android.content.pm.ResolveInfo handler:handlers){
            android.content.pm.ActivityInfo info=handler.activityInfo;
            if(info==null || context.getPackageName().equals(info.packageName))continue;
            ComponentName component=new ComponentName(info.packageName,info.name);if(!canOpen(context,component)){DiagnosticLog.info("input.standard.handler","component="+component.flattenToShortString()+" result=not_callable");continue;}
            if(preferred!=null && preferred.activityInfo!=null && info.packageName.equals(preferred.activityInfo.packageName) && info.name.equals(preferred.activityInfo.name)){DiagnosticLog.info("input.standard.resolve.result","result=preferred component="+component.flattenToShortString());return intent.setComponent(component);}
            only=component;count++;
        }
        DiagnosticLog.info("input.standard.resolve.result","callable="+count+" result="+(count==1?"single_handler":count==0?"no_handler":"ambiguous"));
        return count==1?intent.setComponent(only):null;
    }
    static Integer tclSource(String device,String hardware,String provider,int type,String label){
        if(!"com.tcl.tvinput".equals(provider) || !("T966D5".equalsIgnoreCase(device) || "mt5879".equalsIgnoreCase(device) || "mt5879".equalsIgnoreCase(hardware)))return null;
        if(type==TvInputInfo.TYPE_HDMI){
            java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)^HDMI\\s*([1-4])(?:\\s.*|\\(.*)?$").matcher(label.trim());
            return match.matches()?7+Integer.parseInt(match.group(1)):null;
        }
        if(type==TvInputInfo.TYPE_COMPOSITE)return 3;
        if(type==TvInputInfo.TYPE_TUNER && (label.toUpperCase(Locale.ROOT).contains("DTV") || label.toUpperCase(Locale.ROOT).contains("DTMB")))return 2;
        return null;
    }
    private Intent fallbackInputIntent(TvInputInfo input){
        if(!enabledSystem(context,input.getServiceInfo())){DiagnosticLog.info("input.fallback.route","result=not_enabled_system");return null;}
        Intent intent=null;
        ComponentName droidlogic=new ComponentName("com.droidlogic.tvsource","com.droidlogic.tvsource.DroidLogicTv");
        if(input.getType()==TvInputInfo.TYPE_HDMI && "com.droidlogic.tvinput".equals(input.getServiceInfo().packageName) && canOpen(context,droidlogic))intent=new Intent(Intent.ACTION_MAIN).setComponent(droidlogic).putExtra("id",input.getId());
        if(intent==null){
            // Prefer TCL's native picker if present; only documented middleware uses these ordinals.
            for(TvInputPanels.Panel panel:inspect().availablePanels())if(panel.profile.component.startsWith("com.tcl.settings/") || "com.android.systemui".equals(panel.profile.packageName)){DiagnosticLog.info("input.fallback.route","result=prefer_native_panel");return null;}
            Integer source=tclSource(Build.DEVICE,Build.HARDWARE,input.getServiceInfo().packageName,input.getType(),input.loadLabel(context).toString());
            ComponentName player=new ComponentName("com.tcl.tv","com.tcl.tv.TVActivity");
            if(source!=null && canOpen(context,player))intent=new Intent().setComponent(player).putExtra("targetSourceId",source).putExtra("referer","com.tcl.settings.module_source");
        }
        if(intent==null){DiagnosticLog.info("input.fallback.route","result=no_known_route provider="+input.getServiceInfo().packageName+" type="+input.getType());return null;}
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    boolean openFallbackInput(TvInputInfo input){
        Intent intent=fallbackInputIntent(input);if(intent==null)return false;
        try{context.startActivity(intent);DiagnosticLog.info("input.fallback.dispatch",intent.getComponent().flattenToShortString());return true;}
        catch(RuntimeException failure){DiagnosticLog.error("input.fallback.failed",failure);return false;}
    }
}
