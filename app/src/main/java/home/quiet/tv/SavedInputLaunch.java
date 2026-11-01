package home.quiet.tv;

import android.content.Context;
import android.os.Build;
import java.util.List;

/** Prepared saved HDMI dispatch without loading the API21 TV framework on Android 4.x. */
abstract class SavedInputLaunch {
    abstract boolean current(Context context);
    abstract boolean blocking();
    abstract boolean dispatch(Context context);
    abstract String foregroundComponent();

    private static String profile(Context context){
        return Build.FINGERPRINT+"\n"+context.getSharedPreferences("device-input-profile",0).getString("profile","")
            +"\n"+(new DeviceInputProfile(context).active().length()>0);
    }
    static SavedInputLaunch legacy(Context context,HdmiTarget target)throws Exception{
        if(!"vendor".equals(target.kind))return null;
        String before=profile(context);
        List<VendorTvInputs.Source> sources=HdmiTarget.legacySources(context);
        VendorTvInputs.Source selected=null;int matches=0;
        for(VendorTvInputs.Source source:sources)if(target.id.equals(source.key)){selected=source;matches++;}
        if(matches!=1||!target.scope.equals(VendorTvInputs.scope(context,selected))||!before.equals(profile(context)))return null;
        return new Native(selected,target.scope,before);
    }
    private static final class Native extends SavedInputLaunch {
        private final VendorTvInputs.Source source;
        private final String scope,profile;
        Native(VendorTvInputs.Source source,String scope,String profile){this.source=source;this.scope=scope;this.profile=profile;}
        boolean current(Context context){
            try{return profile.equals(profile(context))&&scope.equals(VendorTvInputs.scope(context,source));}
            catch(Exception unavailable){return false;}
        }
        boolean blocking(){return source.intent==null;}
        String foregroundComponent(){return "";}
        boolean dispatch(Context context){
            return !Thread.currentThread().isInterrupted()&&current(context)&&new VendorTvInputs(context).openPrepared(source);
        }
    }
}
