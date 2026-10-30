package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;

/** Native source panels remain usable without the API21 TV Input Framework. */
final class TvInputPanels {
    enum PanelState { READY, DISABLED, RESTRICTED, UNAVAILABLE, FAILED }

    static final class Profile {
        final String component,packageName,type,action,zh,en,feature;
        final android.os.Bundle extras=new android.os.Bundle();
        final boolean preferred,foregroundService;
        Profile(JSONObject value) throws JSONException {
            component=value.optString("component");packageName=value.optString("package");type=value.getString("type");action=value.optString("action");feature=value.optString("requiredFeature");
            zh=value.getString("labelZh");en=value.getString("labelEn");preferred=value.optBoolean("preferPanel");foregroundService=value.optBoolean("foregroundService");
            if(foregroundService && !"service".equals(type))throw new JSONException("Foreground launch requires a service");
            if(!("activity".equals(type)||"service".equals(type)||"receiver".equals(type))
                    || zh.isEmpty()||en.isEmpty()||(!component.isEmpty()&&(!packageName.isEmpty()||!component.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")))
                    || (!packageName.isEmpty()&&!packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))
                    || (component.isEmpty()&&action.isEmpty())||(!"activity".equals(type)&&action.isEmpty()))throw new JSONException("Invalid TV input profile");
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
            if(component.isEmpty()){if(!packageName.isEmpty())intent.setPackage(packageName);}else intent.setComponent(ComponentName.unflattenFromString(component));
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
            if(!components.add(profile.type+":"+canonical))throw new JSONException("Duplicate TV input panel");
            profiles.add(profile);
        }
        return profiles;
    }
    static final class Panel {
        final Profile profile;final PanelState state;final ComponentName component;
        Panel(Profile profile,PanelState state,ComponentName component){this.profile=profile;this.state=state;this.component=component;}
    }

    static final class Snapshot {
        final ArrayList<Panel> panels=new ArrayList<>();
        boolean profilesUnavailable;
        List<Panel> availablePanels(){ArrayList<Panel> ready=new ArrayList<>();for(Panel p:panels)if(p.state==PanelState.READY)ready.add(p);return ready;}
        Panel directPanel(boolean noInputs){
            List<Panel> ready=availablePanels();
            Panel preferred=null;int count=0;for(Panel p:ready)if(p.profile.preferred){preferred=p;count++;}
            if(count==1)return preferred;
            return noInputs&&!ready.isEmpty()?ready.get(0):null;
        }
        String problem(boolean chinese){
            if(profilesUnavailable)return chinese?"原厂信号源适配信息读取失败。":"Unable to read native input compatibility information.";
            for(Panel p:panels)if(p.state==PanelState.RESTRICTED)return chinese?"已识别原厂信号源入口，但系统未向 Quiet TV 开放调用权限。":"The native input panel was found, but the system restricts access.";
            for(Panel p:panels)if(p.state==PanelState.DISABLED)return chinese?"已识别原厂信号源组件，但组件已停用。":"The native input component is disabled.";
            for(Panel p:panels)if(p.state==PanelState.FAILED)return chinese?"读取原厂信号源入口时发生异常。":"Inspecting the native input panel failed.";
            return chinese?"尚未识别到本机可调用的原厂信号源入口。":"No callable native input panel was found on this firmware.";
        }
    }
    private final Context context;
    private final PackageManager pm;
    TvInputPanels(Context context){this.context=context;pm=context.getPackageManager();}
    Snapshot inspect(){
        Snapshot result=new Snapshot();HashSet<String> seen=new HashSet<>();
        try(InputStream in=context.getAssets().open("tv-input-profiles.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            for(Profile profile:parseProfiles(out.toString("UTF-8"))){
                try{if(!profile.feature.isEmpty()&&!pm.hasSystemFeature(profile.feature))continue;
                List<ComponentName> names=components(profile);
                if(names.isEmpty())DiagnosticLog.info("input.panel.inspect","type="+profile.type+" action="+profile.action+" result=unresolved");
                for(ComponentName name:names){
                    Panel panel=inspect(profile,name);if(panel==null)continue;
                    String key=profile.type+":"+profile.intent().setComponent(name).toUri(0);
                    if(seen.add(key))result.panels.add(panel);
                }}catch(SecurityException failure){result.panels.add(new Panel(profile,PanelState.RESTRICTED,null));}
                catch(RuntimeException failure){result.panels.add(new Panel(profile,PanelState.FAILED,null));DiagnosticLog.error("input.panel.inspect.failed",failure);}
            }
        }catch(IOException|JSONException failure){result.profilesUnavailable=true;DiagnosticLog.error("input.profiles.failed",failure);}
        return result;
    }
    private List<ComponentName> components(Profile profile){
        if(!profile.component.isEmpty())return Collections.singletonList(ComponentName.unflattenFromString(profile.component));
        ArrayList<ComponentName> names=new ArrayList<>();
        int flags=PackageManager.GET_DISABLED_COMPONENTS;
        List<ResolveInfo> matches="service".equals(profile.type)?pm.queryIntentServices(profile.intent(),flags)
            :"receiver".equals(profile.type)?pm.queryBroadcastReceivers(profile.intent(),flags):pm.queryIntentActivities(profile.intent(),flags);
        for(ResolveInfo match:matches){ComponentInfo info="service".equals(profile.type)?match.serviceInfo:match.activityInfo;
            if(info!=null)names.add(new ComponentName(info.packageName,info.name));}
        Collections.sort(names,(a,b)->a.flattenToString().compareTo(b.flattenToString()));return names;
    }
    private boolean home(ComponentName name){
        for(String category:new String[]{Intent.CATEGORY_HOME,"android.intent.category.HOME.CC","android.intent.category.HOME.CC_MAIN","smartdevice.intent.category.LITE_HOME"})
            for(ResolveInfo info:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(name.getPackageName()),PackageManager.GET_DISABLED_COMPONENTS))
                if(info.activityInfo!=null&&name.equals(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)))return true;
        return false;
    }
    private Panel inspect(Profile profile,ComponentName name){
        try{
            ComponentInfo info;String permission;
            if("service".equals(profile.type)){ServiceInfo value=pm.getServiceInfo(name,PackageManager.GET_DISABLED_COMPONENTS);info=value;permission=value.permission;}
            else{ActivityInfo value="receiver".equals(profile.type)?pm.getReceiverInfo(name,PackageManager.GET_DISABLED_COMPONENTS):pm.getActivityInfo(name,PackageManager.GET_DISABLED_COMPONENTS);info=value;permission=value.permission;}
            if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0||name.getPackageName().equals(context.getPackageName())||"activity".equals(profile.type)&&home(name))return null;
            int app=pm.getApplicationEnabledSetting(info.packageName),component=pm.getComponentEnabledSetting(name);
            PanelState state=!(app==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.applicationInfo.enabled:app==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    ||!(component==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?info.enabled:component==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)?PanelState.DISABLED
                :!info.exported||permission!=null&&context.checkCallingOrSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED?PanelState.RESTRICTED:PanelState.READY;
            DiagnosticLog.info("input.panel.inspect","component="+name.flattenToShortString()+" type="+profile.type+" state="+state);
            return new Panel(profile,state,name);
        }catch(PackageManager.NameNotFoundException missing){DiagnosticLog.info("input.panel.inspect","component="+name.flattenToShortString()+" result=not_installed");return null;}
        catch(SecurityException restricted){return new Panel(profile,PanelState.RESTRICTED,name);}
    }
    PanelState open(Panel requested){
        try{
            if(!requested.profile.feature.isEmpty()&&!pm.hasSystemFeature(requested.profile.feature))return PanelState.UNAVAILABLE;
            if(requested.component==null||!components(requested.profile).contains(requested.component))return PanelState.UNAVAILABLE;
            Panel current=inspect(requested.profile,requested.component);if(current==null)return PanelState.UNAVAILABLE;if(current.state!=PanelState.READY)return current.state;
            Intent intent=requested.profile.intent().setComponent(current.component);
            HomeGuardService.bootSourceUserAction(context,"native_input_panel");
            if("activity".equals(requested.profile.type))context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            else if("receiver".equals(requested.profile.type))context.sendBroadcast(intent);
            else if((requested.profile.foregroundService&&Build.VERSION.SDK_INT>=26?context.startForegroundService(intent):context.startService(intent))==null)return PanelState.UNAVAILABLE;
            HomeGuardService.savedInputSelected(context);DiagnosticLog.info("input.panel.dispatch","component="+current.component.flattenToShortString()+" type="+requested.profile.type+" dispatched_unverified");return PanelState.READY;
        }catch(SecurityException restricted){return PanelState.RESTRICTED;}
        catch(android.content.ActivityNotFoundException missing){return PanelState.UNAVAILABLE;}
        catch(RuntimeException failure){DiagnosticLog.error("input.panel.dispatch.failed",failure);return PanelState.FAILED;}
    }
}
