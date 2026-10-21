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
final class TvInputController {
    private static final String TAG="QuietTV";
    enum InputState { READY, EMPTY, UNAVAILABLE, RESTRICTED, FAILED }
    enum PanelState { READY, DISABLED, RESTRICTED, UNAVAILABLE, FAILED }

    static final class Profile {
        final String component,type,action,zh,en;
        final boolean preferred;
        Profile(JSONObject value) throws JSONException {
            component=value.getString("component");type=value.getString("type");action=value.optString("action");
            zh=value.getString("labelZh");en=value.getString("labelEn");preferred=value.optBoolean("preferPanel");
            if(!component.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")
                    || !("activity".equals(type)||"service".equals(type)) || zh.isEmpty() || en.isEmpty()
                    || ("service".equals(type) && action.isEmpty()))throw new JSONException("Invalid TV input profile");
        }
        String label(boolean chinese){return chinese?zh:en;}
    }
    static List<Profile> parseProfiles(String source) throws JSONException {
        JSONObject root=new JSONObject(source);
        if(root.getInt("schema")!=1)throw new JSONException("Unsupported TV input profile schema");
        ArrayList<Profile> profiles=new ArrayList<>();HashSet<String> components=new HashSet<>();
        JSONArray values=root.getJSONArray("panels");
        for(int i=0;i<values.length();i++){
            Profile profile=new Profile(values.getJSONObject(i));
            String[] name=profile.component.split("/",2);
            String canonical=name[0]+"/"+(name[1].startsWith(".")?name[0]+name[1]:name[1]);
            if(!components.add(canonical))throw new JSONException("Duplicate TV input panel");
            profiles.add(profile);
        }
        return profiles;
    }
    static final class Panel {
        final Profile profile;final PanelState state;
        Panel(Profile profile,PanelState state){this.profile=profile;this.state=state;}
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
                result.inputs.addAll(manager.getTvInputList());
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

    private Panel inspectPanel(Profile profile){
        ComponentName name=ComponentName.unflattenFromString(profile.component);
        int flags=Build.VERSION.SDK_INT>=24?PackageManager.MATCH_DISABLED_COMPONENTS:PackageManager.GET_DISABLED_COMPONENTS;
        try{
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
            if(!appEnabled||!componentEnabled)return new Panel(profile,PanelState.DISABLED);
            if(!info.exported || (permission!=null && context.checkCallingOrSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED))return new Panel(profile,PanelState.RESTRICTED);
            return new Panel(profile,PanelState.READY);
        }catch(PackageManager.NameNotFoundException e){return null;}
        catch(SecurityException e){return new Panel(profile,PanelState.RESTRICTED);}
        catch(RuntimeException e){return new Panel(profile,PanelState.FAILED);}
    }

    PanelState openPanel(Profile profile){
        // Packages may be disabled or replaced while a menu is open.
        Panel current=inspectPanel(profile);
        if(current==null)return PanelState.UNAVAILABLE;
        if(current.state!=PanelState.READY)return current.state;
        Intent intent=new Intent().setComponent(ComponentName.unflattenFromString(profile.component));
        if(!profile.action.isEmpty())intent.setAction(profile.action);
        try{
            if("activity".equals(profile.type))context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if(context.startService(intent)==null)return PanelState.UNAVAILABLE;
            // This only confirms dispatch; physical switching requires hardware acceptance.
            return PanelState.READY;
        }catch(SecurityException e){return PanelState.RESTRICTED;}
        catch(android.content.ActivityNotFoundException e){return PanelState.UNAVAILABLE;}
        catch(RuntimeException e){return PanelState.FAILED;}
    }
}
