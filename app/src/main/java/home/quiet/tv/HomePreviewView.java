package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** A local, 16:9 scene. It never launches apps, asks permissions or fetches weather. */
@android.annotation.SuppressLint("ViewConstructor") // Created only by the native desktop settings panel.
final class HomePreviewView extends FrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    final HomeHeaderView header;
    private final FrameLayout scene,band,grid;
    private final WallpaperController backgrounds;
    private final View startupCover;
    private String mediaPreview;
    private boolean previewWeather,previewDayNight=true,previewMuted=true;
    private DockBackdropDrawable dockSurface;
    private final Map<String,Object> appearancePreview=new HashMap<>();
    private String appearanceSignature="";
    private boolean dockDemo;
    private String coverPreviewPackage="";private Bitmap coverPreview;
    private Runnable dockLayoutChanged;
    private final Typeface typeface;
    private final TextView title;
    private final boolean chinese,fixedGreeting;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final HomePreviewCache cache;
    final PreviewSyncState sync=new PreviewSyncState();
    private HomePreviewCache.Scene applied;
    private HomePreviewCache.Key pendingKey,failedKey;
    private boolean active,hasDockApps,secondScreen,destroyed;
    private String draftProperty;
    private boolean draftSecond;
    private int draftValue;
    private volatile int generation;
    private final Runnable tick=new Runnable(){public void run(){if(active){refresh();main.postDelayed(this,60000-System.currentTimeMillis()%60000);}}};
    private final BroadcastReceiver changes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))header.clockActive(false);else{header.clockActive(active&&hasWindowFocus());refresh();}}};
    private final Runnable contentChanged=this::refresh;
    private static final String[] CONTENT_PREFERENCES={"desktop-settings","dock","home-order","child-mode","boot-app"};
    HomePreviewView(ManagementUi owner,boolean chinese,boolean fixedGreeting){
        super(owner);Context context=owner;this.chinese=chinese;this.fixedGreeting=fixedGreeting;cache=HomePreviewCache.get(context);setTag("home-preview");setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);setClipChildren(true);
        scene=new FrameLayout(context);scene.setPivotX(0);scene.setPivotY(0);scene.setBackgroundColor(Color.BLACK);addView(scene,new FrameLayout.LayoutParams(960,540));
        startupCover=new View(context);startupCover.setBackgroundColor(Color.BLACK);addView(startupCover,new FrameLayout.LayoutParams(-1,-1));
        backgrounds=new WallpaperController(owner,scene,1,chinese,true);backgrounds.observeDock(this::invalidateDock);
        typeface=!chinese&&Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif",Typeface.NORMAL);
        header=new HomeHeaderView(context,1,chinese,typeface);scene.addView(header,new FrameLayout.LayoutParams(-1,-1));header.buttonBackdrop(this::drawWallpaper,scene);
        backgrounds.observeBackdrop(()->header.movie(backgrounds.displayedMovie()));
        title=new TextView(context);title.setTextColor(UiPalette.TEXT);title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,40);title.setIncludeFontPadding(false);title.setGravity(Gravity.CENTER_VERTICAL);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);place(title,820,56,50,314);
        band=new FrameLayout(context);place(band,864,114,48,386);
        grid=new FrameLayout(context);place(grid,864,540,48,0);refresh();HomeActivity.seedPreviewBackground(backgrounds);
        loadLocalScene();getViewTreeObserver().addOnPreDrawListener(firstFrame);
    }
    // Reveal the scene only after its local content and background effects have a complete first frame.
    private final ViewTreeObserver.OnPreDrawListener firstFrame=this::prepareFirstFrame;
    private boolean prepareFirstFrame(){
        if(destroyed||!isShown()){revealPreview();return true;}
        loadLocalScene();if(applied!=null)resume();
        boolean ready=((applied!=null&&applied.key.equals(sceneKey()))||failedKey!=null)&&backgrounds.previewCoverReady();
        if(!ready){
            // Keep warm-up draws behind a local cover; a transparent window can
            // lose focus and prevent the preview's GPU surface from becoming ready.
            startupCover.setVisibility(VISIBLE);
            return true;
        }
        revealPreview();getViewTreeObserver().removeOnPreDrawListener(firstFrame);return true;
    }
    private void revealPreview(){startupCover.setVisibility(GONE);}
    private void place(View v,int width,int height,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;scene.addView(v,p);}
    private void invalidateDock(){if(header!=null)for(TextView pill:header.pills)pill.invalidate();if(band!=null){band.invalidate();for(int i=0;i<band.getChildCount();i++)band.getChildAt(i).invalidate();}}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(header!=null)header.clockActive(active&&focused);if(backgrounds!=null&&active){if(focused){backgrounds.resume();refresh();}else{sync.invalidate();backgrounds.pause();backgrounds.hidden();}}}
    void observeMessages(WallpaperController.Message listener){backgrounds.observeMessages(listener);}
    void observeDockLayout(Runnable listener){dockLayoutChanged=listener;listener.run();}
    int availablePinnedCount(){return applied==null?-1:applied.ids.size();}
    int maximumDockScale(){
        DesktopSettingsStore settings=new DesktopSettingsStore(getContext());int count=applied==null?0:Math.min(applied.art.size(),number(settings,"dock-limit"));
        return DockLayout.maximumScale(count,string(settings,"icon-size"),string(settings,"icon-shape"),settings.dockMagnify(),number(settings,"dock-magnification"),string(settings,"app-title-position"));
    }
    @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);setMeasuredDimension(w,Math.round(w*540f/960f));scene.measure(MeasureSpec.makeMeasureSpec(960,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(540,MeasureSpec.EXACTLY));startupCover.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(getMeasuredHeight(),MeasureSpec.EXACTLY));}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){scene.setScaleX(w/960f);scene.setScaleY(w/960f);}
    void refresh(){
        DesktopSettingsStore settings=new DesktopSettingsStore(getContext());
        if(!coverPreviewPackage.isEmpty()&&applied!=null){boolean second=!containsHomeApp(coverPreviewPackage);if(secondScreen!=second){secondScreen=second;backgrounds.previewFinish();}}
        WallpaperCatalog media=WallpaperStore.get(getContext()).snapshot();backgrounds.previewWallpaper(mediaPreview==null?media.current:mediaPreview,mediaPreview==null?media.weather:previewWeather,mediaPreview==null?media.weatherDayNight:previewDayNight,mediaPreview==null?!media.sound||media.rotating||media.weather:previewMuted);
        Object mode=appearancePreview.get("home-blur-mode");backgrounds.previewProgressive(!secondScreen&&"progressive".equals(mode==null?settings.homeBlurMode():mode));
        backgrounds.setDesktopEffects(effect("blur",secondScreen?settings.secondWallpaperBlur():settings.homeWallpaperBlur()),effect("brightness",secondScreen?settings.secondWallpaperBrightness():settings.homeWallpaperBrightness()),effect("fade",secondScreen?settings.secondWallpaperFade():settings.homeWallpaperFade()));
        header.setVisibility(secondScreen?GONE:VISIBLE);grid.setVisibility(secondScreen?VISIBLE:GONE);
        for(int i=0;i<grid.getChildCount();i++)((ViewGroup)grid.getChildAt(i)).getChildAt(1).setVisibility(settings.showAllAppsNames()?VISIBLE:GONE);
        band.setVisibility(!secondScreen&&settings.showHomeApps()?VISIBLE:GONE);
        if(applied!=null)renderAppearance(applied);
        header.previewClockStyle((String)appearancePreview.get("clock-style"));header.updateClock(fixedGreeting);WeatherStore weather=new WeatherStore(getContext());WeatherData data=weather.enabled()?weather.cached(chinese):null;header.weather(data!=null&&data.usable(System.currentTimeMillis())?data:null);header.movie(backgrounds.displayedMovie());
        android.net.ConnectivityManager manager=(android.net.ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);android.net.NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();
        TextView pill=null;for(int i=0;i<header.buttonIds.size();i++)if("network".equals(header.buttonIds.get(i)))pill=header.pills.get(i);if(pill!=null){boolean connected=network!=null&&network.isConnected();int icon=R.drawable.ic_wifi_off;String label=chinese?"Wi-Fi 未连接":"Wi-Fi offline";
        if(connected){if(network.getType()==ConnectivityManager.TYPE_ETHERNET){icon=R.drawable.ic_ethernet;label=chinese?"有线网络":"Ethernet";}else{icon=R.drawable.ic_wifi;label=chinese?"Wi-Fi 已连接":"Wi-Fi connected";
            try{WifiManager wifi=(WifiManager)getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);String ssid=wifi==null?null:wifi.getConnectionInfo().getSSID();if(ssid!=null&&!"<unknown ssid>".equals(ssid))label=ssid.replace("\"","");}catch(SecurityException ignored){}
        }}pill.setContentDescription(label);header.setPillIcon(pill,icon);}
        if(active&&getWidth()>0){loadLocalScene();updateSync(settings,media);}
    }
    private void updateSync(DesktopSettingsStore settings,WallpaperCatalog media){
        Map<String,Object> values=new HashMap<>(getContext().getSharedPreferences("desktop-settings",0).getAll());
        for(String key:new String[]{"icon-size","icon-shape","dock-scale","dock-limit","dock-magnification","home-blur-mode","home-wallpaper-blur","home-wallpaper-brightness","home-wallpaper-fade","second-wallpaper-blur","second-wallpaper-brightness","second-wallpaper-fade","key-sounds","key-sound-theme"})values.remove(key);
        Object key=Arrays.asList(sceneKey(),getWidth(),secondScreen,values,appearancePreview.get("clock-style"),string(settings,"icon-size"),string(settings,"icon-shape"),number(settings,"dock-limit"),number(settings,"dock-scale"),number(settings,"dock-magnification"),
            appearancePreview.containsKey("home-blur-mode")?appearancePreview.get("home-blur-mode"):settings.homeBlurMode(),effect("blur",secondScreen?settings.secondWallpaperBlur():settings.homeWallpaperBlur()),effect("brightness",secondScreen?settings.secondWallpaperBrightness():settings.homeWallpaperBrightness()),
            effect("fade",secondScreen?settings.secondWallpaperFade():settings.homeWallpaperFade()),mediaPreview==null?media.current:mediaPreview,mediaPreview==null?media.weather:previewWeather,mediaPreview==null?media.weatherDayNight:previewDayNight,new WeatherStore(getContext()).enabled());
        long before=sync.request(),request=sync.begin(key,PreviewSyncState.LOCAL|PreviewSyncState.BACKGROUND);
        if(before!=request)backgrounds.awaitPreview(request,(operation,error)->{if(error.isEmpty())sync.ready(operation,PreviewSyncState.BACKGROUND);else sync.failed(operation,error);});
        if(failedKey!=null&&failedKey.equals(sceneKey()))sync.failed(request,chinese?"桌面预览无法读取":"Unable to load desktop preview");
        else if(applied!=null&&applied.key.equals(sceneKey())){sync.ready(request,PreviewSyncState.LOCAL);}
    }
    void showSecondScreen(boolean second){
        if(secondScreen==second)return;
        secondScreen=second;backgrounds.previewFinish();refresh();
        backgrounds.interaction();
    }
    private int effect(String property,int committed){return secondScreen==draftSecond&&property.equals(draftProperty)?draftValue:committed;}
    void previewEffect(boolean second,String property,int value){draftSecond=second;draftProperty=property;draftValue=value;refresh();if("fade".equals(property))fadeIn(value);}
    void clearEffectPreview(){draftProperty=null;backgrounds.previewFinish();refresh();}
    void reloadScene(){if(active&&getWidth()>0)loadLocalScene();}
    void resume(){PhoneSettingsSession.get(getContext()).observeConfiguration(contentChanged);if(active)return;active=true;header.clockActive(hasWindowFocus());backgrounds.resume();IntentFilter filter=new IntentFilter(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);getContext().registerReceiver(changes,filter);cache.listen(contentChanged);for(String name:CONTENT_PREFERENCES)getContext().getSharedPreferences(name,0).registerOnSharedPreferenceChangeListener(this);tick.run();}
    void pause(){revealPreview();PhoneSettingsSession.get(getContext()).forgetConfiguration(contentChanged);if(!active)return;active=false;header.clockActive(false);sync.invalidate();backgrounds.pause();backgrounds.hidden();backgrounds.previewFinish();++generation;pendingKey=null;failedKey=null;getContext().unregisterReceiver(changes);cache.unlisten(contentChanged);for(String name:CONTENT_PREFERENCES)getContext().getSharedPreferences(name,0).unregisterOnSharedPreferenceChangeListener(this);main.removeCallbacks(tick);main.removeCallbacks(contentChanged);}
    @Override public void onSharedPreferenceChanged(SharedPreferences preferences,String key){main.removeCallbacks(contentChanged);main.post(contentChanged);}
    void fadeIn(int millis){if(active)backgrounds.previewFade(millis);}
    void destroy(){destroyed=true;revealPreview();getViewTreeObserver().removeOnPreDrawListener(firstFrame);pause();backgrounds.destroy();++generation;worker.shutdownNow();coverPreview=null;applied=null;band.removeAllViews();grid.removeAllViews();}
    private HomePreviewCache.Key sceneKey(){
        HomePreviewCache.Key key=HomePreviewCache.Key.read(getContext(),chinese,cache.packages());if(coverPreviewPackage.isEmpty())return key;
        DesktopSettingsStore settings=new DesktopSettingsStore(getContext());return new HomePreviewCache.Key(key.apps,key.wallpaper,key.configuration,coverPreviewPackage+":"+string(settings,"icon-size")+":"+string(settings,"icon-shape"));
    }
    private void loadLocalScene(){
        final HomePreviewCache.Key key=sceneKey();final Context context=getContext().getApplicationContext();
        DesktopSettingsStore settings=new DesktopSettingsStore(context);final String previewPackage=coverPreviewPackage,size=string(settings,"icon-size"),shape=string(settings,"icon-shape");
        if(applied!=null&&applied.key.equals(key)){if(pendingKey!=null){++generation;pendingKey=null;}return;}
        HomePreviewCache.Scene saved=cache.scene(),previous=applied!=null?applied:saved;
        HomePreviewCache.Scene local=previous!=null&&previous.key.sameApps(key)?new HomePreviewCache.Scene(key,previous.art,previous.names,previous.gridArt,previous.gridNames,previous.ids,previous.gridIds):HomeActivity.previewScene(key,previewPackage,size,shape);
        if(local!=null){if(DiagnosticProbe.allow("preview.cache",2000))DiagnosticLog.info("preview.cache","hit=true");++generation;pendingKey=null;failedKey=null;cache.save(local,cache.generation());applyLocalScene(local);return;}
        if(key.equals(failedKey)||key.equals(pendingKey))return;
        final int request=++generation;final long cacheGeneration=cache.generation();pendingKey=key;failedKey=null;
        worker.execute(()->{
            try{
                if(request!=generation)return;
                ArrayList<String> packages=new DockStore(context).load();
                List<Bitmap> art=new ArrayList<>(),gridArt=new ArrayList<>();List<String> names=new ArrayList<>(),gridNames=new ArrayList<>(),ids=new ArrayList<>(),gridIds=new ArrayList<>();
                ArrayList<HomeAppCatalog.Entry> catalogApps=new HomeAppCatalog(context).load(packages);Map<String,Bitmap> artwork=new HashMap<>();
                for(String pkg:packages)for(HomeAppCatalog.Entry app:catalogApps)if(pkg.equals(app.id)){ids.add(pkg);names.add(app.label);Bitmap image=app.info==null?HomeEntryArtwork.render(context,app.label,app.id):previewArtwork(context,app);art.add(image);artwork.put(pkg,image);break;}
                ArrayList<String> orderedIds=HomeOrderStore.project(new HomeOrderStore(context).load(),HomeAppCatalog.gridIds(catalogApps));
                for(String id:previewGridIds(orderedIds,previewPackage,size,shape))for(HomeAppCatalog.Entry app:catalogApps)if(id.equals(app.id)){
                    gridIds.add(id);gridNames.add(app.label);Bitmap image=artwork.get(id);gridArt.add(image!=null?image:app.info==null?HomeEntryArtwork.render(context,app.label,app.id):previewArtwork(context,app));break;
                }
                HomePreviewCache.Scene ready=new HomePreviewCache.Scene(key,art,names,gridArt,gridNames,ids,gridIds);main.post(()->{
                    if(destroyed||request!=generation){if(DiagnosticProbe.allow("preview.stale",2000))DiagnosticLog.info("preview.stale","reason=superseded");return;}
                    pendingKey=null;if(!key.equals(sceneKey())){loadLocalScene();return;}
                    cache.save(ready,cacheGeneration);applyLocalScene(ready);
                });
            }catch(RuntimeException|OutOfMemoryError failure){if(failure instanceof OutOfMemoryError)cache.clear();DiagnosticLog.error("home.preview.load_failed",failure);main.post(()->{if(request==generation&&!destroyed){pendingKey=null;failedKey=key;sync.failed(sync.request(),chinese?"桌面预览无法读取":"Unable to load desktop preview");invalidate();}});}
        });
    }
    private static Bitmap previewArtwork(Context context,HomeAppCatalog.Entry app){Bitmap shared=HomeActivity.previewArtwork(context,app.id);return shared==null?AppArtwork.load(context,app.info):shared;}
    static List<String> previewGridIds(List<String> ordered,String pkg,String size,String shape){
        int columns=HomeActivity.gridColumns(size,shape);
        float cardHeight=(864f/columns-16)/("rectangle".equals(shape)?16/9f:1);
        int rows=Math.max(1,(int)((540-24+16)/(cardHeight+42))),capacity=pkg.isEmpty()?20:Math.min(20,columns*rows);
        int target=ordered.indexOf(pkg),start=target<capacity?0:Math.max(0,target/columns-rows/2)*columns;
        return ordered.subList(start,Math.min(ordered.size(),start+capacity));
    }
    private void applyLocalScene(HomePreviewCache.Scene ready){
        applied=ready;band.removeAllViews();
        title.setText(ready.names.isEmpty()?chinese?"添加常用应用":"Add favorite apps":ready.names.get(0));
        backgrounds.setThemeColor(ready.art.isEmpty()?UiPalette.FAINT:HomeActivity.sampleColor(ready.art.get(0)));
        appearanceSignature="";refresh();backgrounds.interaction();
    }
    void previewAppearance(String key,Object value){appearancePreview.put(key,value);refresh();}
    void clearAppearancePreview(String key){if(appearancePreview.remove(key)!=null)refresh();}
    void demoDock(boolean enabled){if(dockDemo!=enabled){dockDemo=enabled;refresh();}}
    private int number(DesktopSettingsStore settings,String key){Object value=appearancePreview.get(key);return value instanceof Integer?(Integer)value:settings.appearance(key);}
    private String string(DesktopSettingsStore settings,String key){Object value=appearancePreview.get(key);return value instanceof String?(String)value:"app-title-position".equals(key)?settings.appTitlePosition():"icon-size".equals(key)?settings.iconSize():settings.iconShape();}
    private void renderAppearance(HomePreviewCache.Scene ready){
        DesktopSettingsStore settings=new DesktopSettingsStore(getContext());String size=string(settings,"icon-size"),shape=string(settings,"icon-shape");int limit=number(settings,"dock-limit");
        String titlePosition=string(settings,"app-title-position");boolean above="top-left".equals(titlePosition)&&!ready.names.isEmpty(),below="below".equals(titlePosition);
        title.setVisibility(!secondScreen&&settings.showHomeApps()&&above?VISIBLE:GONE);
        String key=size+shape+limit+titlePosition+number(settings,"dock-scale")+":"+number(settings,"dock-magnification")+":"+settings.dockMagnify()+settings.dockGlass()+settings.showDockBackground()+dockDemo+settings.showAllAppsNames()+settings.showHomeApps();if(key.equals(appearanceSignature))return;appearanceSignature=key;
        band.removeAllViews();boolean empty=ready.art.isEmpty();hasDockApps=!empty;int count=empty?1:Math.min(ready.art.size(),limit);int active=dockDemo&&!empty?(count-1)/2:-1;
        DockLayout geometry=empty?DockLayout.empty(-1,size,number(settings,"dock-scale"),settings.dockMagnify(),number(settings,"dock-magnification")):DockLayout.create(count,active,size,shape,number(settings,"dock-scale"),settings.dockMagnify(),number(settings,"dock-magnification"),titlePosition);
        DockLayout reserved=empty?DockLayout.empty(0,size,number(settings,"dock-scale"),settings.dockMagnify(),number(settings,"dock-magnification")):DockLayout.create(count,0,size,shape,number(settings,"dock-scale"),settings.dockMagnify(),number(settings,"dock-magnification"),titlePosition);
        header.applicationAreaTop(!settings.showHomeApps()?540:above?Math.min(314,reserved.top-64):reserved.top);
        if(above){title.setText(ready.names.get(active>=0?active:0));FrameLayout.LayoutParams tp=(FrameLayout.LayoutParams)title.getLayoutParams();tp.topMargin=Math.round(Math.min(314,geometry.top-64));title.setLayoutParams(tp);}
        FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)band.getLayoutParams();bp.leftMargin=Math.round(geometry.left);bp.topMargin=Math.round(geometry.top);bp.width=Math.round(geometry.width);bp.height=Math.round(geometry.height);band.setLayoutParams(bp);
        dockSurface=new DockBackdropDrawable(this::drawWallpaper,band,scene,1,false,settings.dockGlass());dockSurface.radiusPixels(geometry.radius);band.setBackgroundDrawable(!empty&&settings.showDockBackground()?dockSurface:null);
        for(int i=0;i<count;i++){View card=empty?emptyCard():artCard(ready.art.get(i),shape,ready.ids.get(i));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(Math.round(geometry.cardWidth[i]),Math.round(geometry.cardHeight[i]));cp.leftMargin=Math.round(geometry.x[i]-geometry.left);cp.topMargin=Math.round(geometry.y[i]-geometry.top);band.addView(card,cp);if(!empty&&below){TextView name=appName(ready.names.get(i),geometry.labelSize);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(cp.width,Math.round(geometry.labelSize*1.4f));lp.leftMargin=cp.leftMargin;lp.topMargin=Math.round(cp.topMargin+geometry.cardHeight[i]+geometry.labelGap);band.addView(name,lp);}}
        grid.removeAllViews();int columns=HomeActivity.gridColumns(size,shape);float cellWidth=864f/columns,cardWidth=cellWidth-16,cardHeight=cardWidth/("rectangle".equals(shape)?16/9f:1);
        for(int i=0;i<ready.gridArt.size();i++){FrameLayout tile=new FrameLayout(getContext());ImageView card=artCard(ready.gridArt.get(i),shape,ready.gridIds.get(i));tile.addView(card,new FrameLayout.LayoutParams(Math.round(cardWidth),Math.round(cardHeight)));TextView name=appName(ready.gridNames.get(i),14);name.setVisibility(settings.showAllAppsNames()?VISIBLE:GONE);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,20);lp.topMargin=Math.round(cardHeight+6);tile.addView(name,lp);FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(Math.round(cardWidth),Math.round(cardHeight+26));position.leftMargin=Math.round(i%columns*cellWidth+8);position.topMargin=Math.round(24+i/columns*(cardHeight+26+16));grid.addView(tile,position);}
        if(dockLayoutChanged!=null)dockLayoutChanged.run();
    }
    private TextView appName(String value,float size){TextView label=new TextView(getContext());label.setText(value);label.setTextColor(UiPalette.TEXT);label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setIncludeFontPadding(false);return label;}
    private ImageView artCard(Bitmap art,String shape,String pkg){boolean draft=coverPreview!=null&&pkg.equals(coverPreviewPackage);ImageView image=new ImageView(getContext());image.setImageDrawable(new AppShapeDrawable(draft?coverPreview:art,"circle".equals(shape),draft||AppCoverStore.get(getContext()).contain(pkg)));image.setScaleType(ImageView.ScaleType.FIT_XY);return image;}
    void previewCover(String pkg,Bitmap image){coverPreviewPackage=pkg==null?"":pkg;coverPreview=image;appearanceSignature="";refresh();}
    boolean containsHomeApp(String pkg){DesktopSettingsStore settings=new DesktopSettingsStore(getContext());int index=applied==null?-1:applied.ids.indexOf(pkg);return settings.showHomeApps()&&index>=0&&index<number(settings,"dock-limit");}
    boolean showingSecondScreen(){return secondScreen;}
    boolean previewAppUnavailable(String pkg){return !pkg.isEmpty()&&applied!=null&&!containsHomeApp(pkg)&&!applied.gridIds.contains(pkg);}
    void clearCoverPreview(){if(!coverPreviewPackage.isEmpty()||coverPreview!=null){coverPreview=null;coverPreviewPackage="";appearanceSignature="";refresh();}}
    private void drawWallpaper(Canvas canvas,Paint paint){backgrounds.drawDockBackground(canvas,paint);}
    void previewBackground(String id,boolean weather,boolean dayNight,boolean muted){mediaPreview=id;previewWeather=weather;previewDayNight=dayNight;previewMuted=muted;refresh();}
    void clearBackgroundPreview(){mediaPreview=null;refresh();}
    private View emptyCard(){ImageView card=new ImageView(getContext());card.setBackgroundDrawable(new DockBackdropDrawable(this::drawWallpaper,card,scene,1,true));card.setImageDrawable(new AppShapeDrawable(HomeEntryArtwork.addFavorite(chinese?"添加常用应用":"Add favorite apps"),false));card.setScaleType(ImageView.ScaleType.FIT_XY);return card;}
}
