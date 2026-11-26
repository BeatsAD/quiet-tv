package home.quiet.tv;

import android.content.Context;
import android.provider.Settings;
import java.util.HashSet;

/** The current attempts to open the TV's own settings, scoped to one UI or phone session. */
final class SystemSettingsTrial {
    private final Context context;
    private final HashSet<String> tried=new HashSet<>();
    private SettingsRouteMemory.Attempt attempt;

    SystemSettingsTrial(Context context){this.context=context;}

    boolean open(boolean next){
        if(next&&attempt!=null)new SettingsRouteMemory(context).confirm(attempt,false);
        if(!next)tried.clear();
        attempt=new SystemSettingsRouter(context).open(Settings.ACTION_SETTINGS,true,tried);
        if(attempt==null)return false;
        tried.add(attempt.identity);
        return true;
    }

    boolean hasTried(){return !tried.isEmpty();}

    String confirmation(){
        if(attempt==null||attempt.fallback())return "";
        SettingsRouteMemory.Result result=attempt.result();
        return result==SettingsRouteMemory.Result.DISPATCHED_UNVERIFIED||result==SettingsRouteMemory.Result.WINDOW_OBSERVED?attempt.diagnosticId:"";
    }

    boolean remember(String confirmation){
        if(attempt==null||confirmation.isEmpty()||!confirmation.equals(attempt.diagnosticId))return false;
        boolean saved=new SettingsRouteMemory(context).confirm(attempt,true);
        attempt=null;
        return saved;
    }
}
