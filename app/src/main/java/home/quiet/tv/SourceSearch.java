package home.quiet.tv;

import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.*;

/** Read-only discovery. An observed unknown component never becomes an executable candidate. */
final class SourceSearch {
    interface Progress {boolean publish(String stage,List<Candidate> values,JSONObject channels);}
    static final class Candidate {
        final String id,key,scope,label,owner,sourceId,transport;
        final HdmiTarget target;
        final TvInputPanels.Panel panel;
        final VendorTvInputs.Source vendor;
        final boolean standard;
        final int connection;
        Candidate(String key,String scope,String label,String owner,String sourceId,HdmiTarget target,
                  TvInputPanels.Panel panel,VendorTvInputs.Source vendor,boolean standard,String transport,int connection){
            this.key=key;this.scope=scope;this.label=label;this.owner=owner;this.sourceId=sourceId;this.target=target;
            this.panel=panel;this.vendor=vendor;this.standard=standard;this.transport=transport;this.connection=connection;
            id=SettingsRouteMemory.identity(key+"\n"+transport);
        }
        JSONObject json()throws JSONException{
            return new JSONObject().put("id",id).put("group",key).put("scope",scope).put("label",label).put("kind",panel==null?"input":"panel")
                .put("sourceId",sourceId).put("transport",transport).put("directHdmi",target!=null).put("connection",connection);
        }
        SavedInputLaunch prepare(Context context,InputPairingState receipt)throws Exception{
            if(panel!=null)return new PanelLaunch(context,this,receipt);
            if(target!=null)return "adb".equals(target.kind)?AdbHdmi.prepare(context,target,receipt):target.prepare(context);
            if(vendor!=null)return SavedInputLaunch.vendor(context,vendor);
            return Build.VERSION.SDK_INT>=21?Api21.prepare(context,sourceId):null;
        }
    }
    static List<Candidate> collect(Context context,Progress progress)throws Exception{
        List<Candidate> result=new ArrayList<>();JSONObject channels=new JSONObject();
        if(Build.VERSION.SDK_INT>=21){if(!Api21.collect(context,result,channels,progress))return result;}
        else{
            channels.put("standard",new JSONObject().put("state","UNSUPPORTED"));
            try{
                List<VendorTvInputs.Source> sources=VendorTvInputs.legacySources(context);
                vendors(context,sources,HdmiTarget.nativeTargets(context,sources),result);
                channels.put("vendor",new JSONObject().put("state","READ").put("count",sources.size()));
            }catch(Exception failure){channels.put("vendor",quality(failure));}
        }
        if(!progress.publish("正在检查系统入口",result,channels))return result;
        try{
            TvInputPanels.Snapshot panels=new TvInputPanels(context).inspect();JSONArray unavailable=new JSONArray();
            boolean adb=InputAdb.connected();
            for(TvInputPanels.Panel panel:panels.panels){
                if(panel.component==null)continue;
                String scope=SystemEntryMaintenance.panelScope(context,panel),key="panel:"+SystemEntryMaintenance.panelId(panel);
                if(panel.state==TvInputPanels.PanelState.READY)result.add(panel(context,panel,"native"));
                if(adb&&(panel.state==TvInputPanels.PanelState.READY||panel.state==TvInputPanels.PanelState.RESTRICTED))result.add(panel(context,panel,"adb"));
                if(panel.state!=TvInputPanels.PanelState.READY)unavailable.put(new JSONObject().put("component",panel.component.flattenToShortString()).put("state",panel.state));
            }
            channels.put("panels",new JSONObject().put("state",panels.profilesUnavailable?"FAILED":"READ").put("unavailable",unavailable));
        }catch(Exception failure){channels.put("panels",quality(failure));}
        if(!progress.publish("正在读取调试信息",result,channels))return result;
        try{
            String services=InputAdb.query(context,"dumpsys -l");
            channels.put("adb",new JSONObject().put("state","READ").put("tvInputService",service(services,"tv_input"))
                .put("hdmiService",service(services,"hdmi_control")));
        }catch(Exception failure){channels.put("adb",quality(failure));}
        Set<String> preferredIds=new HashSet<>();for(Candidate candidate:result)if(confirmed(context,candidate))preferredIds.add(candidate.id);
        Collections.sort(result,(a,b)->{
            int group=(a.panel==null?0:1)-(b.panel==null?0:1);if(group!=0)return group;
            return (preferredIds.contains(a.id)?0:1)-(preferredIds.contains(b.id)?0:1);
        });
        progress.publish("搜索完成",result,channels);return result;
    }
    static boolean confirmed(Context context,Candidate candidate){
        if(candidate.panel!=null){
            JSONObject saved=new SourcePairingStore(context).entry();
            return candidate.panel.confirmedPreference&&"native".equals(candidate.transport)||saved!=null
                &&saved.optString("id").equals(SystemEntryMaintenance.panelId(candidate.panel))&&saved.optString("scope").equals(candidate.scope)
                &&saved.optString("transport","native").equals(candidate.transport);
        }
        BootAppStore store=new BootAppStore(context);HdmiTarget target=HdmiTarget.decode(store.watchTarget());
        return store.tested()&&target!=null&&target.matches(candidate.target);
    }
    static boolean service(String services,String name){return java.util.regex.Pattern.compile("(?m)^\\s*"+name+"\\s*$").matcher(services).find();}
    static JSONObject quality(Exception failure)throws JSONException{return new JSONObject().put("state",InputAdb.quality(failure)).put("errorType",failure.getClass().getSimpleName());}
    static Candidate panel(Context context,TvInputPanels.Panel panel,String transport)throws Exception{
        return new Candidate("panel:"+SystemEntryMaintenance.panelId(panel),SystemEntryMaintenance.panelScope(context,panel),panel.profile.label(true),
            panel.component.getPackageName(),"",null,panel,null,false,transport,-1);
    }
    private static void vendors(Context context,List<VendorTvInputs.Source> sources,List<HdmiTarget> targets,List<Candidate> result)throws Exception{
        Map<String,HdmiTarget> direct=new HashMap<>();for(HdmiTarget target:targets)direct.put(target.id,target);
        SourcePairingStore names=new SourcePairingStore(context);
        for(VendorTvInputs.Source source:sources){
            String custom=names.label(source.key),label=custom.isEmpty()?source.label:custom;
            HdmiTarget target=direct.get(source.key);
            result.add(new Candidate("vendor:"+source.key,VendorTvInputs.scope(context,source),label,source.owner,source.key,target,null,source,false,"native",-1));
            if(target!=null&&source.intent!=null&&InputAdb.connected()){
                HdmiTarget adb=AdbHdmi.target("vendor",target,source.intent);
                result.add(new Candidate("vendor:"+source.key,adb.scope,label,source.owner,source.key,adb,null,source,false,"adb",-1));
            }
        }
    }
    @android.annotation.TargetApi(21)
    private static final class Api21 {
        static boolean collect(Context context,List<Candidate> result,JSONObject channels,Progress progress)throws Exception{
            TvInputController controller=new TvInputController(context);TvInputController.Snapshot snapshot=controller.inspect();
            channels.put("standard",new JSONObject().put("state",snapshot.state).put("reported",snapshot.reported).put("filtered",snapshot.filtered)
                .put("duplicates",snapshot.duplicates).put("itemFailures",snapshot.itemFailures).put("metadataWarnings",snapshot.metadataWarnings).put("excluded",new JSONObject(snapshot.exclusions)));
            Map<String,HdmiTarget> direct=new HashMap<>();List<HdmiTarget> targets=HdmiTarget.Api21.available(context,snapshot);
            for(HdmiTarget target:targets)if("standard".equals(target.kind))direct.put(target.id,target);
            android.media.tv.TvInputManager manager=(android.media.tv.TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);
            JSONArray raw=new JSONArray();
            for(android.media.tv.TvInputInfo input:snapshot.raw)try{raw.put(new JSONObject().put("id",input.getId()).put("type",input.getType()).put("parent",input.getParentId()));}catch(RuntimeException ignored){}
            channels.getJSONObject("standard").put("raw",raw);
            for(android.media.tv.TvInputInfo input:snapshot.inputs)try{
                String owner=input.getServiceInfo().packageName,id=input.getId();int connection=-1;
                try{if(manager!=null)connection=manager.getInputState(id);}catch(RuntimeException unavailable){/* Unknown is not disconnected. */}
                String scope=DeviceInputProfile.digest(Build.FINGERPRINT+"\n"+id+"\n"+VendorTvInputs.packageStamp(context,owner));
                HdmiTarget target=direct.get(id);if(target!=null)scope=target.scope;
                result.add(new Candidate("standard:"+id,scope,controller.inputLabel(input),owner,id,target,null,null,true,"native",connection));
                if(target!=null&&InputAdb.connected()){
                    android.content.Intent intent=controller.hdmiIntent(input);
                    if(intent!=null){HdmiTarget adb=AdbHdmi.target("standard",target,intent);
                        result.add(new Candidate("standard:"+id,adb.scope,controller.inputLabel(input),owner,id,adb,null,null,true,"adb",connection));}
                }
            }catch(RuntimeException unavailable){DiagnosticLog.error("input.search.item_failed",unavailable);}
            if(!progress.publish("正在读取厂商输入",result,channels))return false;
            snapshot.vendor=new VendorTvInputs(context).inspect();snapshot.vendorReported.addAll(snapshot.vendor.sources);controller.mergeVendorSources(snapshot);
            vendors(context,snapshot.vendor.sources,HdmiTarget.nativeTargets(context,snapshot.vendor.sources),result);
            channels.put("vendor",new JSONObject().put("state",snapshot.vendor.restricted?"RESTRICTED":snapshot.vendor.failed?"FAILED":snapshot.vendor.disabled?"DISABLED":"READ")
                .put("count",snapshot.vendor.sources.size()).put("reported",snapshot.vendorReported.size()).put("sdkUnavailable",snapshot.vendor.sdkUnavailable));
            return true;
        }
        static SavedInputLaunch prepare(Context context,String id)throws Exception{return InputLaunch.resolve(context,id,false);}
    }
    private static final class PanelLaunch extends SavedInputLaunch {
        private final Candidate candidate;
        private final InputPairingState receipt;
        PanelLaunch(Context context,Candidate candidate,InputPairingState receipt){this.candidate=candidate;this.receipt=receipt;}
        boolean current(Context context){
            try{return candidate.scope.equals(SystemEntryMaintenance.panelScope(context,candidate.panel));}catch(Exception changed){return false;}
        }
        boolean blocking(){return "adb".equals(candidate.transport);}
        String foregroundComponent(){return "activity".equals(candidate.panel.profile.type)?candidate.panel.component.flattenToString():"";}
        boolean dispatch(Context context){
            if(!current(context)||Thread.currentThread().isInterrupted())return false;
            try{
                if("adb".equals(candidate.transport))return InputAdb.open(context,candidate.panel,receipt);
                return (receipt==null?new TvInputPanels(context).open(candidate.panel):new TvInputPanels(context).testPanel(candidate.panel))==TvInputPanels.PanelState.READY;
            }catch(Exception failure){if(receipt!=null)receipt.failure=InputAdb.quality(failure);DiagnosticLog.error("input.pairing.dispatch_failed",failure);return false;}
        }
    }
}
