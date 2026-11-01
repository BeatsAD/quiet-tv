package home.quiet.tv;

import android.content.*;
import android.content.pm.PackageInfo;
import android.os.*;
import org.json.*;

/** Remembers only explicitly confirmed, currently callable routes from the built-in router. */
final class SettingsRouteMemory {
    enum Result { DISPATCHED_UNVERIFIED, WINDOW_OBSERVED, CONFIRMED, REJECTED, EXPIRED }
    static final class Attempt {
        final String action,slot,identity,scope;
        final SystemSettingsRouter.Route route;
        final boolean trial;
        final long started=SystemClock.elapsedRealtime();
        private Result result=Result.DISPATCHED_UNVERIFIED;
        Attempt(String action,String slot,SystemSettingsRouter.Route route,String scope,boolean trial){
            this.action=action;this.slot=slot;this.route=route;identity=route.identity;this.scope=scope;this.trial=trial;
        }
        boolean fallback(){return SystemSettingsRouter.settingsFallback(action,route.page);}
        synchronized Result result(){
            if((result==Result.DISPATCHED_UNVERIFIED||result==Result.WINDOW_OBSERVED)&&SystemClock.elapsedRealtime()-started>600000)result=Result.EXPIRED;
            return result;
        }
        synchronized void observed(String component){
            if(result()!=Result.DISPATCHED_UNVERIFIED)return;
            ComponentName name=ComponentName.unflattenFromString(component),target=route.launch.getComponent();
            if(name!=null&&(route.type==SystemSettingsRouter.LaunchType.ACTIVITY?target.equals(name):target.getPackageName().equals(name.getPackageName()))){
                result=Result.WINDOW_OBSERVED;DiagnosticLog.info("settings.result","result=window_observed function_verified=false");
            }
        }
    }
    private final Context context;
    private final SharedPreferences prefs;
    SettingsRouteMemory(Context context){this.context=context;prefs=context.getSharedPreferences("settings-route-memory",Context.MODE_PRIVATE);}
    static String identity(String value){
        try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(Charsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static String scope(Context context,SystemSettingsRouter.Route route) throws Exception {
        PackageInfo info=context.getPackageManager().getPackageInfo(route.launch.getComponent().getPackageName(),0);
        String related="";
        String frontend=route.launch.getStringExtra("packageName");
        if(frontend!=null)try{PackageInfo extra=context.getPackageManager().getPackageInfo(frontend,0);related="\n"+frontend+":"+extra.versionCode+":"+extra.lastUpdateTime;}
        catch(android.content.pm.PackageManager.NameNotFoundException missing){related="\n"+frontend+":missing";}
        return identity(Build.FINGERPRINT+"\n"+Build.VERSION.SDK_INT+"\n"+route.identity+"\n"+info.packageName+":"+info.versionCode+":"+info.lastUpdateTime+related);
    }
    boolean preferred(String slot,SystemSettingsRouter.Route route){
        try{JSONObject saved=new JSONObject(prefs.getString(slot,"{}"));return saved.optBoolean("confirmed")&&route.identity.equals(saved.optString("identity"))&&scope(context,route).equals(saved.optString("scope"));}
        catch(Exception stale){return false;}
    }
    boolean confirm(Attempt attempt,boolean successful){
        return confirm(attempt,successful,"user");
    }
    boolean confirm(Attempt attempt,boolean successful,String source){
        if(!"user".equals(source)&&!"agent".equals(source))return false;
        if(attempt==null||!attempt.trial)return false;
        synchronized(attempt){
            Result state=attempt.result();if(state!=Result.DISPATCHED_UNVERIFIED&&state!=Result.WINDOW_OBSERVED)return false;
            if(!successful){attempt.result=Result.REJECTED;failed(attempt.slot,attempt.route);DiagnosticLog.info("settings.result","result="+source+"_rejected");return true;}
            if(attempt.fallback())return false;
            try{
                if(!attempt.scope.equals(scope(context,attempt.route))||!new SystemSettingsRouter(context).callable(attempt.action,attempt.route))return false;
                JSONObject record=new JSONObject().put("identity",attempt.identity).put("scope",attempt.scope).put("confirmed",true).put("source",source);
                if(!prefs.edit().putString(attempt.slot,record.toString()).commit())return false;
                attempt.result=Result.CONFIRMED;DiagnosticLog.info("settings.result","result="+source+"_confirmed");return true;
            }catch(Exception changed){return false;}
        }
    }
    void failed(String slot,SystemSettingsRouter.Route route){
        try{JSONObject saved=new JSONObject(prefs.getString(slot,"{}"));
            if(route.identity.equals(saved.optString("identity"))&&scope(context,route).equals(saved.optString("scope")))prefs.edit().putString(slot,saved.put("confirmed",false).toString()).apply();
        }catch(Exception invalid){/* Preserve unrelated or stale records. */}
    }
}
