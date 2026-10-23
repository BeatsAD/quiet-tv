package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** Local display preferences shared by Home and its settings page. */
final class DesktopSettingsStore {
    private final SharedPreferences preferences;
    DesktopSettingsStore(Context context){preferences=context.getSharedPreferences("desktop-settings",0);}
    boolean greetingEnabled(){return preferences.getBoolean("greeting-enabled",true);}
    String greetingText(String fallback){return preferences.getString("greeting-text",fallback);}
    void saveGreeting(boolean enabled,String text){preferences.edit().putBoolean("greeting-enabled",enabled).putString("greeting-text",text.replace('\r',' ').replace('\n',' ').replace('\t',' ').trim()).apply();}
    boolean showTime(){return preferences.getBoolean("show-time",true);}
    boolean showAppTitle(){return preferences.getBoolean("show-app-title",true);}
    boolean showButtons(){return preferences.getBoolean("show-buttons",true);}
    boolean showInput(){return preferences.getBoolean("show-input",showButtons());}
    boolean showNetwork(){return preferences.getBoolean("show-network",showButtons());}
    boolean showSettings(){return preferences.getBoolean("show-settings",showButtons());}
    void showInput(boolean show){preferences.edit().putBoolean("show-input",show).apply();}
    void showNetwork(boolean show){preferences.edit().putBoolean("show-network",show).apply();}
    void showSettings(boolean show){preferences.edit().putBoolean("show-settings",show).apply();}
    void showTime(boolean show){preferences.edit().putBoolean("show-time",show).apply();}
    void showAppTitle(boolean show){preferences.edit().putBoolean("show-app-title",show).apply();}
    void showButtons(boolean show){preferences.edit().putBoolean("show-buttons",show).apply();}
}
