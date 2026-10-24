package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** Local display preferences shared by Home and its settings page. */
final class DesktopSettingsStore {
    private final SharedPreferences preferences;
    DesktopSettingsStore(Context context){
        preferences=context.getSharedPreferences("desktop-settings",0);
        // Capture the old combined setting once, before either independent switch changes.
        if(!preferences.contains("show-date"))preferences.edit().putBoolean("show-date",preferences.getBoolean("show-time",true)).apply();
    }
    String layout(){return "gallery".equals(preferences.getString("home-layout","classic"))?"gallery":"classic";}
    void layout(String layout){if(!"classic".equals(layout)&&!"gallery".equals(layout))throw new IllegalArgumentException("Unknown home layout");preferences.edit().putString("home-layout",layout).apply();DiagnosticLog.info("desktop.layout",layout);}
    boolean showDate(){return preferences.getBoolean("show-date",true);}
    void showDate(boolean show){preferences.edit().putBoolean("show-date",show).apply();DiagnosticLog.info("desktop.setting","show-date="+show);}
    void greetingEnabled(boolean enabled){preferences.edit().putBoolean("greeting-enabled",enabled).apply();DiagnosticLog.info("desktop.greeting.visibility",String.valueOf(enabled));}
    boolean greetingEnabled(){return preferences.getBoolean("greeting-enabled",true);}
    String greetingText(String fallback){return preferences.getString("greeting-text",fallback);}
    void saveGreeting(boolean enabled,String text){preferences.edit().putBoolean("greeting-enabled",enabled).putString("greeting-text",text.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim()).apply();DiagnosticLog.info("desktop.greeting.saved","enabled="+enabled);}
    boolean showTime(){return preferences.getBoolean("show-time",true);}
    boolean showAppTitle(){return preferences.getBoolean("show-app-title",true);}
    boolean showButtons(){return preferences.getBoolean("show-buttons",true);}
    boolean showInput(){return preferences.getBoolean("show-input",showButtons());}
    boolean showNetwork(){return preferences.getBoolean("show-network",showButtons());}
    boolean showSettings(){return preferences.getBoolean("show-settings",showButtons());}
    void showInput(boolean show){preferences.edit().putBoolean("show-input",show).apply();DiagnosticLog.info("desktop.setting","show-input="+show);}
    void showNetwork(boolean show){preferences.edit().putBoolean("show-network",show).apply();DiagnosticLog.info("desktop.setting","show-network="+show);}
    void showSettings(boolean show){preferences.edit().putBoolean("show-settings",show).apply();DiagnosticLog.info("desktop.setting","show-settings="+show);}
    void showTime(boolean show){preferences.edit().putBoolean("show-time",show).apply();DiagnosticLog.info("desktop.setting","show-time="+show);}
    void showAppTitle(boolean show){preferences.edit().putBoolean("show-app-title",show).apply();DiagnosticLog.info("desktop.setting","show-app-title="+show);}
    void showButtons(boolean show){preferences.edit().putBoolean("show-buttons",show).apply();DiagnosticLog.info("desktop.setting","show-buttons="+show);}
}
