package home.quiet.tv;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import java.util.*;

/** One process-local scene. Cached data never owns a settings Activity or View. */
final class HomePreviewCache implements ComponentCallbacks2 {
    private static HomePreviewCache instance;
    private final Set<Runnable> listeners=new HashSet<>();
    private long packages,generation;
    private Scene scene;
    static synchronized HomePreviewCache get(Context context){
        if(instance==null)instance=new HomePreviewCache(context.getApplicationContext());return instance;
    }
    private HomePreviewCache(Context app){
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");
        androidx.core.content.ContextCompat.registerReceiver(app,new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){
            ArrayList<Runnable> notify;
            synchronized(HomePreviewCache.this){++packages;clear();notify=new ArrayList<>(listeners);}
            for(Runnable listener:notify)listener.run();
        }},filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
        app.registerComponentCallbacks(this);
    }
    synchronized long packages(){return packages;}
    synchronized long generation(){return generation;}
    synchronized Scene scene(){return scene;}
    synchronized void save(Scene value,long expectedGeneration){if(generation==expectedGeneration&&value.key.apps.packages==packages&&value.complete)scene=value;}
    synchronized void listen(Runnable listener){listeners.add(listener);}
    synchronized void unlisten(Runnable listener){listeners.remove(listener);}
    synchronized void clear(){scene=null;++generation;}
    @Override public void onConfigurationChanged(Configuration configuration){clear();}
    @Override public void onLowMemory(){clear();}
    @Override public void onTrimMemory(int level){if(level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)clear();}

    static final class Key {
        final HomeCatalogRefresh.Input apps;
        final String wallpaper,configuration;
        Key(HomeCatalogRefresh.Input apps,String wallpaper,String configuration){this.apps=apps;this.wallpaper=wallpaper;this.configuration=configuration;}
        static Key read(Context context,boolean chinese,long packages){
            DesktopSettingsStore settings=new DesktopSettingsStore(context);WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();
            String wallpaper=store.imageKey(catalog.current)+":"+store.imageKey(catalog.staticBeforeVideo)+":"+store.imageKey(catalog.lastLocal);
            HomeCatalogRefresh.Input apps=new HomeCatalogRefresh.Input(new DockStore(context).load(),new HomeOrderStore(context).signature(),
                new ChildModeStore(context).revision(),AppCoverStore.get(context).revision(),packages,settings.showHomeApps(),
                new HomeVisibilityStore(context).signature()+new BootAppStore(context).watchRevision()+":"+settings.showWatchTv());
            android.util.DisplayMetrics screen=context.getResources().getDisplayMetrics();
            return new Key(apps,wallpaper,screen.widthPixels+":"+screen.heightPixels+":"+screen.densityDpi+":"+chinese);
        }
        boolean sameApps(Key other){return apps.equals(other.apps)&&configuration.equals(other.configuration);}
        boolean sameWallpaper(Key other){return wallpaper.equals(other.wallpaper)&&configuration.equals(other.configuration);}
        @Override public boolean equals(Object other){return other instanceof Key&&sameApps((Key)other)&&sameWallpaper((Key)other);}
        @Override public int hashCode(){return Objects.hash(apps,wallpaper,configuration);}
    }
    static final class Scene {
        final Key key;
        final int width;
        final Bitmap wallpaper,dockBackground;
        final List<Bitmap> art,gridArt;
        final List<String> names,gridNames;
        final boolean complete;
        Scene(Key key,int width,Bitmap wallpaper,Bitmap dockBackground,List<Bitmap> art,List<String> names,List<Bitmap> gridArt,List<String> gridNames,boolean complete){
            this.key=key;this.width=width;this.wallpaper=wallpaper;this.dockBackground=dockBackground;
            this.art=Collections.unmodifiableList(new ArrayList<>(art));this.names=Collections.unmodifiableList(new ArrayList<>(names));
            this.gridArt=Collections.unmodifiableList(new ArrayList<>(gridArt));this.gridNames=Collections.unmodifiableList(new ArrayList<>(gridNames));this.complete=complete;
        }
    }
}
