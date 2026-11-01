package home.quiet.tv;

import android.content.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.*;
import org.json.*;

/** Computer tests select freshly resolved built-in entries, never supplied Intents. */
final class SystemEntryMaintenance {
    private static Test current;
    static final class Candidate {
        final String id,scope,page,type,component;
        final boolean fallback,preferred;
        final SystemSettingsRouter.Route route;
        final TvInputPanels.Panel panel;
        Candidate(Context context,SystemSettingsRouter.Route value,String action,boolean ethernet)throws Exception{
            route=value;panel=null;id=value.identity;scope=SettingsRouteMemory.scope(context,value);
            page=value.page.name();type=value.type.name();component=value.launch.getComponent().flattenToShortString();
            fallback=SystemSettingsRouter.settingsFallback(action,value.page);
            preferred=!fallback&&new SettingsRouteMemory(context).preferred(action+"|"+ethernet,value);
        }
        Candidate(Context context,TvInputPanels.Panel value)throws Exception{
            route=null;panel=value;id=panelId(value);scope=panelScope(context,value);
            page="INPUTS";type=value.profile.type;component=value.component.flattenToShortString();fallback=false;
            preferred=preferredPanel(context,value);
        }
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("component",component).put("type",type)
            .put("page",page).put("fallback",fallback).put("preferred",preferred);}
    }
    static final class Catalog {
        final String entry,scope;
        final List<Candidate> candidates=new ArrayList<>();
        final JSONArray unavailable=new JSONArray();
        Catalog(Context context,String entry)throws Exception{
            this.entry=entry;StringBuilder signature=new StringBuilder(Build.FINGERPRINT+"\n"+Build.VERSION.SDK_INT+"\n"+entry);
            if("inputs".equals(entry)){
                TvInputPanels.Snapshot panels=new TvInputPanels(context).inspect();
                for(TvInputPanels.Panel panel:panels.panels){
                    if(panel.state==TvInputPanels.PanelState.READY)candidates.add(new Candidate(context,panel));
                    else unavailable.put(new JSONObject().put("component",panel.component==null?panel.profile.component:panel.component.flattenToShortString()).put("reason",panel.state.name()));
                }
                if(panels.profilesUnavailable)unavailable.put(new JSONObject().put("reason","PROFILES_UNAVAILABLE"));
            }else{
                String action=action(entry);boolean ethernet=ethernet(context,entry);signature.append('\n').append(ethernet);
                for(SystemSettingsRouter.Route route:new SystemSettingsRouter(context).routes(action,ethernet))candidates.add(new Candidate(context,route,action,ethernet));
            }
            // Match ordinary navigation's confirmed preference, without changing fallback order.
            for(int i=0;i<candidates.size();i++)if(candidates.get(i).preferred){candidates.add(0,candidates.remove(i));break;}
            for(Candidate candidate:candidates)signature.append('\n').append(candidate.id).append(':').append(candidate.scope).append(':').append(candidate.preferred);
            scope=DeviceInputProfile.digest(signature.toString());
        }
        Candidate find(String id){for(Candidate value:candidates)if(value.id.equals(id))return value;return null;}
        JSONObject json()throws JSONException{
            JSONArray routes=new JSONArray();for(Candidate value:candidates)routes.put(value.json());
            return new JSONObject().put("entry",entry).put("scope",scope).put("candidates",routes).put("unavailable",unavailable)
                .put("state",candidates.isEmpty()?"NO_CALLABLE_CANDIDATE":"AVAILABLE");
        }
    }
    static final class Test {
        final String entry,id,scope,token=UUID.randomUUID().toString();
        final long started=SystemClock.elapsedRealtime();
        String state="PENDING",reason="",source="";
        SettingsRouteMemory.Attempt attempt;
        Test(String entry,String id,String scope){this.entry=entry;this.id=id;this.scope=scope;}
    }
    static String action(String entry){
        if("settings".equals(entry))return Settings.ACTION_SETTINGS;
        if("network".equals(entry))return Settings.ACTION_WIFI_SETTINGS;
        if("inputs".equals(entry))return "";
        throw new IllegalArgumentException("入口必须为 settings、network 或 inputs");
    }
    @SuppressWarnings("deprecation")
    private static boolean ethernet(Context context,String entry){
        if(!"network".equals(entry))return false;
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active=manager==null?null:manager.getActiveNetworkInfo();
        return active!=null&&active.isConnected()&&active.getType()==ConnectivityManager.TYPE_ETHERNET;
    }
    static JSONObject snapshot(Context context,String entry)throws Exception{
        action(entry);JSONObject value=new Catalog(context,entry).json();
        synchronized(SystemEntryMaintenance.class){
            expire();
            if(current!=null&&entry.equals(current.entry)){
                JSONObject test=new JSONObject().put("request",current.token).put("candidate",current.id).put("scope",current.scope)
                    .put("state",current.state).put("reason",current.reason).put("source",current.source).put("functionVerified",false);
                if(current.attempt!=null)test.put("page",current.attempt.route.page.name()).put("fallback",current.attempt.fallback());
                if("CONFIRMED".equals(current.state))test.put("functionVerified",true);
                value.put("test",test);
            }
        }
        return value;
    }
    static void request(Context context,String entry,String id,String scope)throws Exception{
        action(entry);Catalog catalog=new Catalog(context,entry);
        if(!catalog.scope.equals(scope)||catalog.find(id)==null)throw new IllegalStateException("入口已变化，请重新读取候选");
        Test test=new Test(entry,id,scope);
        synchronized(SystemEntryMaintenance.class){
            expire();if(current!=null&&("PENDING".equals(current.state)||"PREPARING".equals(current.state)))throw new IllegalStateException("上一次入口请求尚未处理");
            current=test;
        }
        try{context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException failure){finish(test,"FAILED","home_unavailable");throw failure;}
    }
    private static void expire(){
        if(current==null)return;long age=SystemClock.elapsedRealtime()-current.started;
        if(("PENDING".equals(current.state)||"PREPARING".equals(current.state))&&age>30000
                ||"DISPATCHED_UNVERIFIED".equals(current.state)&&age>600000){current.state="EXPIRED";current.reason="test_expired";}
    }
    static synchronized boolean pending(){expire();return current!=null&&("PENDING".equals(current.state)||"PREPARING".equals(current.state));}
    static synchronized Test consume(){
        expire();if(current==null||!"PENDING".equals(current.state))return null;
        current.state="PREPARING";return current;
    }
    private static synchronized void finish(Test test,String state,String reason){if(current==test){test.state=state;test.reason=reason;}}
    static synchronized void cancelled(String token){
        if(current!=null&&current.token.equals(token)&&"PREPARING".equals(current.state))finish(current,"CANCELLED_UNVERIFIED","home_interrupted");
    }
    static boolean dispatch(Context context,Test test){
        synchronized(SystemEntryMaintenance.class){expire();if(current!=test||!"PREPARING".equals(test.state))return false;}
        try{
            Catalog catalog=new Catalog(context,test.entry);Candidate candidate=catalog.find(test.id);
            if(!catalog.scope.equals(test.scope)||candidate==null){finish(test,"FAILED","entry_changed");return false;}
            boolean opened;
            if(candidate.panel!=null)opened=new TvInputPanels(context).open(candidate.panel)==TvInputPanels.PanelState.READY;
            else{test.attempt=new SystemSettingsRouter(context).openCandidate(action(test.entry),test.id);opened=test.attempt!=null;}
            finish(test,opened?"DISPATCHED_UNVERIFIED":"FAILED",opened?"candidate_dispatched":"candidate_failed");return opened;
        }catch(Exception failure){finish(test,"FAILED","inspection_failed");DiagnosticLog.error("entry.maintenance.failed",failure);return false;}
    }
    static synchronized void confirm(Context context,String entry,String token,boolean worked,String source)throws Exception{
        if(!"user".equals(source)&&!"agent".equals(source))throw new IllegalArgumentException("需要明确的观察来源");
        expire();Test test=current;
        if(test==null||!test.token.equals(token)||!test.entry.equals(entry)||!"DISPATCHED_UNVERIFIED".equals(test.state))throw new IllegalStateException("入口测试已变化或过期，请重新测试");
        Catalog catalog=new Catalog(context,entry);Candidate candidate=catalog.find(test.id);
        if(!catalog.scope.equals(test.scope)||candidate==null)throw new IllegalStateException("入口或成功记录已变化，请重新测试");
        if(candidate.route!=null){
            if(!new SettingsRouteMemory(context).confirm(test.attempt,worked,source))throw new IllegalStateException("此入口不能确认当前功能，或测试已失效");
        }else{
            if(worked){
                JSONObject saved=new JSONObject().put("id",candidate.id).put("scope",candidate.scope).put("source",source);
                if(!context.getSharedPreferences("system-entry-maintenance",0).edit().putString("inputs",saved.toString()).commit())throw new IllegalStateException("信号源入口未保存");
            }else failedPanel(context,candidate.panel);
        }
        test.state=worked?"CONFIRMED":"REJECTED";test.source=source;
    }
    private static String panelId(TvInputPanels.Panel panel){return SettingsRouteMemory.identity(panel.profile.type+":"+panel.profile.intent().setComponent(panel.component).toUri(0));}
    private static String panelScope(Context context,TvInputPanels.Panel panel)throws Exception{
        return DeviceInputProfile.digest(Build.FINGERPRINT+"\n"+Build.VERSION.SDK_INT+"\n"+panelId(panel)+"\n"+VendorTvInputs.packageStamp(context,panel.component.getPackageName()));
    }
    static boolean preferredPanel(Context context,TvInputPanels.Panel panel){
        if(panel.state!=TvInputPanels.PanelState.READY||panel.component==null)return false;
        try{JSONObject saved=new JSONObject(context.getSharedPreferences("system-entry-maintenance",0).getString("inputs","{}"));
            return panelId(panel).equals(saved.optString("id"))&&panelScope(context,panel).equals(saved.optString("scope"));
        }catch(Exception stale){return false;}
    }
    static void failedPanel(Context context,TvInputPanels.Panel panel){
        if(preferredPanel(context,panel))context.getSharedPreferences("system-entry-maintenance",0).edit().remove("inputs").commit();
    }
}
