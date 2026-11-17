package home.quiet.tv;

import android.content.Context;
import home.quiet.components.ComponentRepository;
import java.io.IOException;

/** All local and authenticated remote entries use the same wallpaper transition. */
final class WeatherComponentActions {
    static void uninstall(Context context)throws IOException{
        Context app=context.getApplicationContext();
        ComponentRepository.get(app).uninstallWeather(()->{WallpaperStore.get(app).removeWeather();PhoneSettingsSession.get(app).configurationChanged();});
    }
}
