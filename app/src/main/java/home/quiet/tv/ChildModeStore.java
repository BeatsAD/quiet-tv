package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

final class ChildModeStore {
    private static final Object LOCK=new Object();
    final SharedPreferences preferences;
    private final Context context;
    ChildModeStore(Context context){this.context=context.getApplicationContext();preferences=this.context.getSharedPreferences("child-mode",Context.MODE_PRIVATE);}
    boolean configured(){return !preferences.getString("credential","").isEmpty();}
    String credential(){return preferences.getString("credential","");}
    String recovery(){return preferences.getString("recovery","");}
    boolean verifyRecovery(String value){return ChildModeCredentials.numeric(value) && !recovery().isEmpty() && java.security.MessageDigest.isEqual(value.getBytes(home.quiet.tv.Charsets.US_ASCII),recovery().getBytes(home.quiet.tv.Charsets.US_ASCII));}
    boolean saveCredential(String prepared,String pin){
        synchronized(LOCK){SharedPreferences.Editor editor=preferences.edit().putString("credential",prepared).remove("failures").remove("retryAfter");
            if(recovery().isEmpty())editor.putString("recovery",ChildModeCredentials.recovery(pin));
            return commit(editor);
        }
    }
    long retryAfter(){long deadline=preferences.getLong("retryAfter",0),now=android.os.SystemClock.elapsedRealtime();return deadline-now>30_000?0:deadline;}
    void failed(){synchronized(LOCK){int failures=preferences.getInt("failures",0)+1;SharedPreferences.Editor e=preferences.edit();if(failures>=5)e.putInt("failures",0).putLong("retryAfter",android.os.SystemClock.elapsedRealtime()+30_000);else e.putInt("failures",failures);commit(e);}}
    void verified(){commit(preferences.edit().remove("failures").remove("retryAfter"));}
    long revision(){return preferences.getLong("revision",0);}
    private JSONObject rules(){try{return new JSONObject(preferences.getString("rules","{}"));}catch(JSONException e){throw new IllegalStateException("儿童模式设置无法读取",e);}}
    ChildModePolicy.Rule rule(String pkg){synchronized(LOCK){
        JSONObject data=rules().optJSONObject(pkg);ChildModePolicy.Rule r=read(data);String previous=r.day;r.newDay(today());
        // Persist the date as well as the reset, including after an overnight restart without use.
        if(data!=null && !previous.equals(r.day) && !save(pkg,r))throw new IllegalStateException("每日额度保存失败");return r;
    }}
    boolean hidden(String pkg){return rule(pkg).hidden;}
    static ChildModePolicy.Rule read(JSONObject data){
        ChildModePolicy.Rule r=new ChildModePolicy.Rule();if(data!=null){r.hidden=data.optBoolean("hidden");r.locked=data.optBoolean("locked");r.mode=data.optInt("mode");r.limit=data.optLong("limit",r.limit);r.singleUsed=data.optLong("singleUsed");r.dailyUsed=data.optLong("dailyUsed");r.singleExtra=data.optLong("singleExtra");r.dailyExtra=data.optLong("dailyExtra");r.day=data.optString("day");}
        if(data!=null)r.repeatDays=data.optInt("repeatDays",ChildModePolicy.EVERY_DAY);
        // Older daily rules spent their base quota before any extra. Retain their remaining balance.
        if(data!=null)r.dailyExtraUsed=data.optLong("dailyExtraUsed",Math.min(r.dailyExtra,Math.max(0,r.dailyUsed-r.limit)));
        r.validate();return r;
    }
    boolean saveSettings(String pkg,ChildModePolicy.Rule edited){synchronized(LOCK){
        ChildModePolicy.Rule current=rule(pkg);current.hidden=edited.hidden;current.locked=edited.locked;current.mode=edited.mode;current.limit=edited.limit;current.repeatDays=edited.repeatDays;
        boolean saved=save(pkg,current);if(saved)ChildUsageService.changed();return saved;
    }}
    boolean save(String pkg,ChildModePolicy.Rule rule){
        rule.validate();synchronized(LOCK){try{
            JSONObject values=rules(),r=new JSONObject();r.put("hidden",rule.hidden).put("locked",rule.locked).put("mode",rule.mode).put("repeatDays",rule.repeatDays).put("limit",rule.limit).put("singleUsed",rule.singleUsed).put("dailyUsed",rule.dailyUsed).put("singleExtra",rule.singleExtra).put("dailyExtra",rule.dailyExtra).put("dailyExtraUsed",rule.dailyExtraUsed).put("day",rule.day);values.put(pkg,r);
            return commit(preferences.edit().putString("rules",values.toString()).putLong("revision",revision()+1));
        }catch(JSONException e){throw new IllegalStateException("无法保存儿童模式设置",e);}}
    }
    boolean consume(String pkg,long elapsed){
        synchronized(LOCK){ChildModePolicy.Rule r=read(rules().optJSONObject(pkg));
            r.consume(elapsed,today(),sinceMidnight());return save(pkg,r);
        }
    }
    boolean grant(String pkg,long amount){synchronized(LOCK){ChildModePolicy.Rule r=rule(pkg);r.grant(amount);return save(pkg,r);}}
    boolean eligible(String pkg){
        if(pkg.equals(context.getPackageName()) || pkg.startsWith("home.quiet.tv."))return false;
        PackageManager pm=context.getPackageManager();
        for(ResolveInfo home:pm.queryIntentActivities(HomeController.homeIntent(),0))if(home.activityInfo!=null && pkg.equals(home.activityInfo.packageName))return false;
        try{ApplicationInfo info=pm.getApplicationInfo(pkg,0);if(!info.enabled || !AppAvailability.installed(pm,info))return false;
            for(String action:new String[]{android.provider.Settings.ACTION_SETTINGS,android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS})for(ResolveInfo setting:pm.queryIntentActivities(new Intent(action),0))if(setting.activityInfo!=null && pkg.equals(setting.activityInfo.packageName))return false;
            return !new HomeGuardState(context).protects(pkg);
        }catch(PackageManager.NameNotFoundException e){return false;}
    }
    void fault(String reason){commit(preferences.edit().putString("timerFault",reason));ChildUsageService.changed();}
    String fault(){return preferences.getString("timerFault","");}
    static String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date());}
    static long sinceMidnight(){Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);return Math.max(0,System.currentTimeMillis()-start.getTimeInMillis());}
    private boolean commit(SharedPreferences.Editor editor){
        synchronized(LOCK){Map<String,?> previous=preferences.getAll();if(editor.commit())return true;
            // Android changes its in-memory snapshot even when commit() fails.
            SharedPreferences.Editor rollback=preferences.edit().clear();for(Map.Entry<String,?> value:previous.entrySet()){Object item=value.getValue();if(item instanceof String)rollback.putString(value.getKey(),(String)item);else if(item instanceof Integer)rollback.putInt(value.getKey(),(Integer)item);else if(item instanceof Long)rollback.putLong(value.getKey(),(Long)item);else if(item instanceof Boolean)rollback.putBoolean(value.getKey(),(Boolean)item);}rollback.apply();return false;
        }
    }
}
