package home.quiet.tv;

import android.content.*;
import android.content.pm.PackageManager;
import android.media.tv.*;
import android.net.Uri;
import android.os.Build;
import java.util.*;

/** Resolve/query on a worker; only the resulting, still-current route may be dispatched. */
@android.annotation.TargetApi(21)
final class InputLaunch {
    enum Issue { NONE, PERMISSION, NO_CHANNELS }
    final TvInputInfo input;
    final VendorTvInputs.Source vendor;
    final ArrayList<Intent> intents=new ArrayList<>();
    final TvInputPanels.Panel panel;
    Issue issue=Issue.NONE;
    boolean channelsPending;
    private final String fingerprint=Build.FINGERPRINT,profile;
    private final Map<String,String> packages=new HashMap<>();
    private String vendorScope,foreground="";
    String foregroundComponent(){return foreground;}
    private InputLaunch(Context context,TvInputInfo input,VendorTvInputs.Source vendor,TvInputPanels.Panel panel) throws Exception {
        this.input=input;this.vendor=vendor;this.panel=panel;profile=profile(context);
        if(input!=null)bind(context,input.getServiceInfo().packageName);
        if(vendor!=null)vendorScope=VendorTvInputs.scope(context,vendor);
        if(panel!=null&&panel.component!=null)bind(context,panel.component.getPackageName());
    }
    private static String profile(Context context){
        return Build.FINGERPRINT+"\n"+context.getSharedPreferences("device-input-profile",Context.MODE_PRIVATE).getString("profile","")
            +"\n"+(new DeviceInputProfile(context).active().length()>0)+"\n"+android.provider.Settings.Secure.getString(context.getContentResolver(),"tv_input_hidden_inputs");
    }
    private void bind(Context context,String pkg) throws Exception {packages.put(pkg,VendorTvInputs.packageStamp(context,pkg));}
    private void add(Context context,Intent intent) throws Exception {
        if(intent==null)return;
        bind(context,intent.getComponent().getPackageName());
        for(Intent old:intents)if(old.toUri(0).equals(intent.toUri(0)))return;
        intents.add(new Intent(intent));
    }
    static InputLaunch saved(Context context,HdmiTarget saved) throws Exception {
        String before=profile(context);InputLaunch result=savedCurrent(context,saved);
        return before.equals(profile(context))?result:null;
    }
    private static InputLaunch savedCurrent(Context context,HdmiTarget saved) throws Exception {
        TvInputController controller=new TvInputController(context);TvInputController.Snapshot snapshot=controller.inspectSources();
        boolean matched=false;for(HdmiTarget current:HdmiTarget.available(context,snapshot))if(saved.matches(current)){matched=true;break;}
        if(!matched)return null;
        if("vendor".equals(saved.kind))for(VendorTvInputs.Source source:snapshot.vendor.sources)if(saved.id.equals(source.key))return new InputLaunch(context,null,source,null);
        if("standard".equals(saved.kind))for(TvInputInfo input:snapshot.inputs)if(saved.id.equals(input.getId())){
            TvInputInfo mapped=controller.mappedInput(input);if(mapped==null)return null;
            InputLaunch launch=new InputLaunch(context,mapped,null,null);launch.bind(context,input.getServiceInfo().packageName);launch.add(context,controller.hdmiIntent(input));return launch.intents.isEmpty()?null:launch;
        }
        return null;
    }
    static InputLaunch resolve(Context context,String id,boolean vendorOnly) throws Exception {
        String before=profile(context);InputLaunch result=resolveCurrent(context,id,vendorOnly);
        return before.equals(profile(context))?result:null;
    }
    private static InputLaunch resolveCurrent(Context context,String id,boolean vendorOnly) throws Exception {
        TvInputController controller=new TvInputController(context);TvInputController.Snapshot snapshot=controller.inspectSources();
        for(VendorTvInputs.Source source:snapshot.vendor.sources)if(id.equals(source.key))return new InputLaunch(context,null,source,null);
        if(vendorOnly)return null;
        TvInputInfo input=null,original=null;for(TvInputInfo candidate:snapshot.inputs)if(id.equals(candidate.getId())){original=candidate;input=controller.mappedInput(candidate);break;}
        if(input==null)return null;
        InputLaunch launch=new InputLaunch(context,input,null,snapshot.nativePanels.directPanel(true));
        launch.bind(context,original.getServiceInfo().packageName);
        launch.add(context,controller.knownInputIntent(input));
        Intent fallback=controller.fallbackInputIntent(input);
        if(input.getType()==TvInputInfo.TYPE_TUNER)launch.add(context,fallback);
        if(input.getType()==TvInputInfo.TYPE_TUNER&&!launch.intents.isEmpty()){launch.channelsPending=true;return launch;}
        if(input.isPassthroughInput()){
            launch.add(context,controller.standardIntent(input.getId(),TvContract.buildChannelUriForPassthroughInput(input.getId())));
            launch.add(context,fallback);
        }else launch.channels(context);
        return launch;
    }
    InputLaunch channels(Context context) throws Exception {
        if(!current(context))return null;channelsPending=false;intents.clear();
        if(context.checkCallingOrSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED){issue=Issue.PERMISSION;return this;}
        long channel=-1;
        try(android.database.Cursor cursor=context.getContentResolver().query(TvContract.buildChannelsUriForInput(input.getId()),new String[]{TvContract.Channels._ID},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())channel=cursor.getLong(0);
        }
        if(channel<0)issue=Issue.NO_CHANNELS;
        else add(context,new TvInputController(context).standardIntent(input.getId(),TvContract.buildChannelUri(channel)));
        return this;
    }
    boolean current(Context context){
        try{
            if(!fingerprint.equals(Build.FINGERPRINT)||!profile.equals(profile(context)))return false;
            for(Map.Entry<String,String> entry:packages.entrySet())if(!entry.getValue().equals(VendorTvInputs.packageStamp(context,entry.getKey())))return false;
            if(input!=null){
                android.content.pm.ServiceInfo service=input.getServiceInfo();
                service=context.getPackageManager().getServiceInfo(new ComponentName(service.packageName,service.name),0);
                if(!TvInputController.enabled(context,service))return false;
            }
            return vendor==null||vendorScope.equals(VendorTvInputs.scope(context,vendor));
        }catch(Exception changed){return false;}
    }
    boolean blocking(){return vendor!=null&&vendor.intent==null;}
    boolean dispatch(Context context){
        if(Thread.currentThread().isInterrupted()||!current(context))return false;
        if(vendor!=null)return new VendorTvInputs(context).openPrepared(vendor);
        for(Intent intent:intents){
            if(!TvInputController.canOpen(context,intent.getComponent()))continue;
            try{context.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));foreground=intent.getComponent().flattenToString();DiagnosticLog.info("input.dispatch","dispatched_unverified");return true;}
            catch(RuntimeException failure){DiagnosticLog.error("input.dispatch.failed",failure);}
        }
        // Permission and empty-channel issues are shown to the user, never hidden by a picker.
        return !channelsPending&&issue==Issue.NONE&&panel!=null&&new TvInputPanels(context).open(panel)==TvInputPanels.PanelState.READY;
    }
}
