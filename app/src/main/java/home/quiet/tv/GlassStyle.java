package home.quiet.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

/** One application-wide glass preference, including the former Dock switch. */
final class GlassStyle implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String KEY="global-glass";
    interface Listener {void glassChanged();}
    private static GlassStyle instance;
    private final SharedPreferences preferences;
    private final ArrayList<WeakReference<Listener>> listeners=new ArrayList<>();
    private volatile boolean enabled;
    private volatile int revision;
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
        if(key!=null&&!KEY.equals(key)&&!"dock-glass".equals(key))return;
        boolean next=read(prefs);if(next==enabled)return;enabled=next;++revision;
        if(!next)WallpaperBackdropCache.clearGlass();
        ArrayList<Listener> targets=new ArrayList<>();
        synchronized(listeners){for(Iterator<WeakReference<Listener>> it=listeners.iterator();it.hasNext();){Listener target=it.next().get();if(target==null)it.remove();else targets.add(target);}}
        for(Listener target:targets)target.glassChanged();
    }
    // SharedPreferences holds listeners weakly; keep the listener alive through this singleton.
    private GlassStyle(Context context){preferences=context.getSharedPreferences("desktop-settings",0);enabled=read(preferences);preferences.registerOnSharedPreferenceChangeListener(this);}
    static synchronized GlassStyle get(Context context){if(instance==null)instance=new GlassStyle(context.getApplicationContext());return instance;}
    static boolean read(SharedPreferences preferences){return preferences.getBoolean(KEY,preferences.getBoolean("dock-glass",true));}
    boolean enabled(){return enabled;}
    boolean rendering(){return enabled&&!SettingsFocusMotion.reduced();}
    int revision(){return revision;}
    boolean valid(int value){return value==revision&&rendering();}
    void observe(Listener listener){synchronized(listeners){for(Iterator<WeakReference<Listener>> it=listeners.iterator();it.hasNext();){Listener current=it.next().get();if(current==null)it.remove();else if(current==listener)return;}listeners.add(new WeakReference<>(listener));}}
    void forget(Listener listener){synchronized(listeners){for(Iterator<WeakReference<Listener>> it=listeners.iterator();it.hasNext();){Listener current=it.next().get();if(current==null||current==listener)it.remove();}}}
}
