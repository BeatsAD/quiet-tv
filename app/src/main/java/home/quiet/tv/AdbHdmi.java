package home.quiet.tv;

import android.content.*;
import android.os.Build;
import org.json.*;
import java.util.*;

/** ADB reuses a route produced by an existing HDMI protocol, never a supplied component or source number. */
final class AdbHdmi {
    static HdmiTarget target(String kind,HdmiTarget original,Intent intent)throws Exception{
        return HdmiTarget.adb(kind+":"+original.id,original.label,DeviceInputProfile.digest(original.scope+"\n"+intent.toUri(0)));
    }
    static SavedInputLaunch prepare(Context context,HdmiTarget saved,InputPairingState receipt)throws Exception{
        List<VendorTvInputs.Source> vendors;
        if(Build.VERSION.SDK_INT>=21){
            Resolution standard=Api21.resolve(context,saved);
            if(standard!=null)return prepared(context,saved,standard.target,standard.intent,receipt);
            vendors=Api21.vendors(context);
        }else vendors=HdmiTarget.legacySources(context);
        if(!saved.id.startsWith("vendor:"))return null;
        for(VendorTvInputs.Source source:vendors)if(source.hdmi&&source.intent!=null&&saved.id.equals("vendor:"+source.key)){
            HdmiTarget original=HdmiTarget.vendor(source.key,source.label,VendorTvInputs.scope(context,source));
            return prepared(context,saved,original,source.intent,receipt);
        }
        return null;
    }
    private static SavedInputLaunch prepared(Context context,HdmiTarget saved,HdmiTarget original,Intent intent,InputPairingState receipt)throws Exception{
        String kind=saved.id.startsWith("standard:")?"standard":"vendor";
        if(!saved.matches(target(kind,original,intent)))return null;
        return new Launch(context,intent,receipt);
    }
    private static final class Resolution {
        final HdmiTarget target;final Intent intent;
        Resolution(HdmiTarget target,Intent intent){this.target=target;this.intent=intent;}
    }
    @android.annotation.TargetApi(21)
    private static final class Api21 {
        static Resolution resolve(Context context,HdmiTarget saved)throws Exception{
            if(!saved.id.startsWith("standard:"))return null;
            TvInputController controller=new TvInputController(context);TvInputController.Snapshot snapshot=controller.inspectSources();
            for(HdmiTarget original:HdmiTarget.Api21.available(context,snapshot))if("standard".equals(original.kind)&&saved.id.equals("standard:"+original.id))
                for(android.media.tv.TvInputInfo input:snapshot.inputs)if(input.getId().equals(original.id)){
                    Intent intent=controller.hdmiIntent(input);return intent==null?null:new Resolution(original,intent);
                }
            return null;
        }
        static List<VendorTvInputs.Source> vendors(Context context){return new TvInputController(context).inspectSources().vendor.sources;}
    }
    private static String profile(Context context){return Build.FINGERPRINT+"\n"+context.getSharedPreferences("device-input-profile",0).getString("profile","")
        +"\n"+android.provider.Settings.Secure.getString(context.getContentResolver(),"tv_input_hidden_inputs");}
    private static final class Launch extends SavedInputLaunch {
        final Intent intent;final InputPairingState receipt;final String profile,stamp;
        Launch(Context context,Intent intent,InputPairingState receipt)throws Exception{
            this.intent=new Intent(intent);this.receipt=receipt;profile=profile(context);stamp=VendorTvInputs.packageStamp(context,intent.getComponent().getPackageName());
        }
        boolean current(Context context){try{return profile.equals(profile(context))&&stamp.equals(VendorTvInputs.packageStamp(context,intent.getComponent().getPackageName()));}catch(Exception changed){return false;}}
        boolean blocking(){return true;}
        String foregroundComponent(){return intent.getComponent().flattenToString();}
        boolean dispatch(Context context){try{return current(context)&&InputAdb.openIntent(context,intent,receipt);}catch(Exception failure){if(receipt!=null)receipt.failure=InputAdb.quality(failure);DiagnosticLog.error("input.adb.hdmi_failed",failure);return false;}}
    }
}
