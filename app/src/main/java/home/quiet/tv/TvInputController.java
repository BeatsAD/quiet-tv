package home.quiet.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
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
    enum InputState { READY, EMPTY, UNAVAILABLE, RESTRICTED, FAILED }
    enum PanelState { READY, DISABLED, RESTRICTED, UNAVAILABLE, FAILED }

    static final class Profile {
        final String component,packageName,type,action,zh,en,feature;
        final android.os.Bundle extras=new android.os.Bundle();
        final boolean preferred;
        Profile(JSONObject value) throws JSONException {
            component=value.optString("component");packageName=value.optString("package");type=value.getString("type");action=value.optString("action");feature=value.optString("requiredFeature");
            zh=value.getString("labelZh");en=value.getString("labelEn");preferred=value.optBoolean("preferPanel");
            if((component.isEmpty()?(!packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") || action.isEmpty() || !"service".equals(type))
                    :(!packageName.isEmpty() || !component.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")))
                    || !("activity".equals(type)||"service".equals(type)) || zh.isEmpty() || en.isEmpty()
                    || ("service".equals(type) && action.isEmpty()))throw new JSONException("Invalid TV input profile");
            JSONObject parameters=value.optJSONObject("extras");
            if(value.has("extras") && parameters==null)throw new JSONException("Invalid TV input extras");
            if(parameters!=null)for(Iterator<String> keys=parameters.keys();keys.hasNext();){
                String key=keys.next();Object parameter=parameters.get(key);
                if(parameter instanceof String)extras.putString(key,(String)parameter);
                else if(parameter instanceof Boolean)extras.putBoolean(key,(Boolean)parameter);
                else if(parameter instanceof Integer)extras.putInt(key,(Integer)parameter);
                else throw new JSONException("Unsupported TV input extra");
            }
        }
        String label(boolean chinese){return chinese?zh:en;}
        Intent intent(){
            Intent intent=new Intent();if(!action.isEmpty())intent.setAction(action);
            if(component.isEmpty())intent.setPackage(packageName);else intent.setComponent(ComponentName.unflattenFromString(component));
            return intent.putExtras(extras);
        }
    }
    static List<Profile> parseProfiles(String source) throws JSONException {
        JSONObject root=new JSONObject(source);
        if(root.getInt("schema")!=1)throw new JSONException("Unsupported TV input profile schema");
        ArrayList<Profile> profiles=new ArrayList<>();HashSet<String> components=new HashSet<>();
        JSONArray values=root.getJSONArray("panels");
        for(int i=0;i<values.length();i++){
            Profile profile=new Profile(values.getJSONObject(i));
            String canonical;
            if(profile.component.isEmpty())canonical=profile.packageName+":"+profile.action;
            else{String[] name=profile.component.split("/",2);canonical=name[0]+"/"+(name[1].startsWith(".")?name[0]+name[1]:name[1]);}
            if(!components.add(canonical))throw new JSONException("Duplicate TV input panel");
            profiles.add(profile);
        }
        return profiles;
    }
    static final class Panel {
        final Profile profile;final PanelState state;final ComponentName component;
        Panel(Profile profile,PanelState state,ComponentName component){this.profile=profile;this.state=state;this.component=component;}
    }
    static final class Snapshot {
        final ArrayList<TvInputInfo> inputs=new ArrayList<>();
        final ArrayList<Panel> panels=new ArrayList<>();
        InputState state=InputState.EMPTY;
        boolean profilesUnavailable;
        List<Panel> availablePanels(){
            ArrayList<Panel> available=new ArrayList<>();
            for(Panel panel:panels)if(panel.state==PanelState.READY)available.add(panel);
            return available;
        }
        Panel directPanel(){
            List<Panel> available=availablePanels();
            // Multiple installed panels require an explicit choice rather than a trial launch.
            if(available.size()!=1)return null;
            Panel panel=available.get(0);
            return inputs.isEmpty()||panel.profile.preferred?panel:null;
        }
    }

    private final Context context;
    private final PackageManager pm;
    TvInputController(Context context){this.context=context;pm=context.getPackageManager();}

    Snapshot inspect(){
        Snapshot result=new Snapshot();
        try{
            TvInputManager manager=(TvInputManager)context.getSystemService(Context.TV_INPUT_SERVICE);
            if(manager==null)result.state=InputState.UNAVAILABLE;
            else{
                for(TvInputInfo input:manager.getTvInputList())if(input.getParentId()==null)result.inputs.add(input);
                result.state=result.inputs.isEmpty()?InputState.EMPTY:InputState.READY;
            }
        }catch(SecurityException e){result.state=InputState.RESTRICTED;}
        // Some old firmware exposes a manager without a working backing service.
        catch(RuntimeException e){result.state=InputState.FAILED;Log.w(TAG,"TV input enumeration failed: "+e.getClass().getSimpleName());}
        try(InputStream in=context.getAssets().open("tv-input-profiles.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int size;while((size=in.read(buffer))!=-1)out.write(buffer,0,size);
            for(Profile profile:parseProfiles(out.toString("UTF-8"))){Panel panel=inspectPanel(profile);if(panel!=null)result.panels.add(panel);}
        }catch(IOException|JSONException e){result.profilesUnavailable=true;Log.w(TAG,"TV input profiles unavailable: "+e.getClass().getSimpleName());}
        return result;
    }

    private ComponentName panelComponent(Profile profile){
        if(!profile.component.isEmpty())return ComponentName.unflattenFromString(profile.component);
        android.content.pm.ResolveInfo resolved=pm.resolveService(profile.intent(),0);
        if(resolved==null || resolved.serviceInfo==null || !profile.packageName.equals(resolved.serviceInfo.packageName))return null;
        return new ComponentName(resolved.serviceInfo.packageName,resolved.serviceInfo.name);
    }

    private Panel inspectPanel(Profile profile){
        if(!profile.feature.isEmpty() && !pm.hasSystemFeature(profile.feature))return null;
        ComponentName name=null;
        int flags=Build.VERSION.SDK_INT>=24?PackageManager.MATCH_DISABLED_COMPONENTS:PackageManager.GET_DISABLED_COMPONENTS;
        try{
            name=panelComponent(profile);if(name==null)return null;
            ComponentInfo info;String permission;
            if("activity".equals(profile.type)){
                android.content.pm.ActivityInfo activity=pm.getActivityInfo(name,flags);info=activity;permission=activity.permission;
            }else{
                android.content.pm.ServiceInfo service=pm.getServiceInfo(name,flags);info=service;permission=service.permission;
            }
            // A matching package name alone must not turn an ordinary app into a system-source handler.
            if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return null;
            int appState=pm.getApplicationEnabledSetting(name.getPackageName()),componentState=pm.getComponentEnabledSetting(name);
            boolean appEnabled=appState==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.applicationInfo.enabled:appState==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            boolean componentEnabled=componentState==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.enabled:componentState==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            if(!appEnabled||!componentEnabled)return new Panel(profile,PanelState.DISABLED,name);
            if(!info.exported || (permission!=null && context.checkCallingOrSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED))return new Panel(profile,PanelState.RESTRICTED,name);
            return new Panel(profile,PanelState.READY,name);
        }catch(PackageManager.NameNotFoundException e){return null;}
        catch(SecurityException e){return new Panel(profile,PanelState.RESTRICTED,name);}
        catch(RuntimeException e){return new Panel(profile,PanelState.FAILED,name);}
    }

    PanelState openPanel(Profile profile){
        // Packages may be disabled or replaced while a menu is open.
        Panel current=inspectPanel(profile);
        if(current==null)return PanelState.UNAVAILABLE;
        if(current.state!=PanelState.READY)return current.state;
        ComponentName target=current.component;if(target==null)return PanelState.UNAVAILABLE;
        Intent intent=profile.intent().setComponent(target);
        try{
            if("activity".equals(profile.type))context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if(context.startService(intent)==null)return PanelState.UNAVAILABLE;
            // This only confirms dispatch; physical switching requires hardware acceptance.
            return PanelState.READY;
        }catch(SecurityException e){return PanelState.RESTRICTED;}
        catch(android.content.ActivityNotFoundException e){return PanelState.UNAVAILABLE;}
        catch(RuntimeException e){return PanelState.FAILED;}
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
    private Route correction(TvInputInfo input){
        android.content.pm.ServiceInfo service=input.getServiceInfo();
        if((service.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return null;
        return correction(Build.MODEL,service.packageName,input.getType(),input.getId());
    }
    String inputLabel(TvInputInfo input){Route route=correction(input);return route==null?input.loadLabel(context).toString():route.label;}
    boolean openKnownInput(TvInputInfo input){
        android.content.pm.ServiceInfo service=input.getServiceInfo();
        if((service.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
        Route route=xiaomiRoute(Build.MODEL,service.packageName,input.getType(),input.getId());if(route==null)return false;
        ComponentName target=new ComponentName("com.xiaomi.mitv.tvplayer","com.xiaomi.mitv.tvplayer."+route.activity);
        try{
            android.content.pm.ActivityInfo activity=pm.getActivityInfo(target,0);
            int appState=pm.getApplicationEnabledSetting(target.getPackageName()),componentState=pm.getComponentEnabledSetting(target);
            if((activity.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0 || !activity.exported
                    || !(appState==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?activity.applicationInfo.enabled:appState==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    || !(componentState==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?activity.enabled:componentState==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    || (activity.permission!=null && pm.checkPermission(activity.permission,context.getPackageName())!=PackageManager.PERMISSION_GRANTED))return false;
            Intent intent=new Intent("com.xiaomi.mitv.tvplayer."+route.action).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(route.source>=0)intent.putExtra("input",route.source);
            context.startActivity(intent);
            return true;
        }catch(PackageManager.NameNotFoundException e){return false;}
        catch(SecurityException|android.content.ActivityNotFoundException e){Log.w(TAG,"Xiaomi TV input launch failed: "+e.getClass().getSimpleName());return false;}
    }

}
