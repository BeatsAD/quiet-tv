package home.quiet.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.*;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Local-only TV home. Directional movement is owned here, never by focus search. */
public final class HomeActivity extends androidx.activity.ComponentActivity {
    private static final String TAG = "QuietTV";
    private static final String WALLPAPER_ENTRY = HomeAppCatalog.WALLPAPER;
    private static final String DESKTOP_SETTINGS_ENTRY = HomeAppCatalog.DESKTOP_SETTINGS;
    private static final String MANAGEMENT_ENTRY = HomeAppCatalog.MANAGEMENT;
    static final int APP_COLUMNS = 5;
    private static final float CONTENT_MARGIN = 48;
    // The complete first screen scrolls as one item; preserve its original composition.
    private static final float HOME_HEADER_HEIGHT = 540;
    private static final float HOME_TITLE_TOP = 314, HOME_DOCK_TOP = 386;
    private static final float APP_TOP = 24, APP_TITLE_HEIGHT = APP_TOP;
    private static final float APP_GAP = 16, APP_LABEL_GAP = 6, APP_LABEL_HEIGHT = 20;
    private static final float FOCUS_SCALE = 1.065f, FOCUS_STROKE = 3;
    // A critically damped spring settles without bouncing past the target.
    private static final android.animation.TimeInterpolator FOCUS_MOTION = t -> (float)((1-(1+8*t)*Math.exp(-8*t))/(1-9*Math.exp(-8)));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ArrayList<App> dock = new ArrayList<>(), apps = new ArrayList<>();
    private final ArrayList<Tile> dockTiles = new ArrayList<>();
    private final HashMap<String,Tile> visibleTiles = new HashMap<>();
    private final ArrayList<TextView> pills = new ArrayList<>();
    private final ArrayList<TextView> visiblePills = new ArrayList<>();
    private final HashMap<String,App> artworkCache = new HashMap<>();
    private final HomeCatalogRefresh catalogRefresh=new HomeCatalogRefresh();
    private long packageRevision;
    private int catalogSequence,focusGeneration;
    private ViewTreeObserver.OnPreDrawListener focusRestore;
    private boolean waitingForUnlock,unlockRecreateRequested;
    private final BroadcastReceiver unlockReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){resumeAfterUnlock();}
    };
    private FrameLayout root;
    private RecyclerView list;
    private AppAdapter appAdapter;
    private GridLayoutManager layout;
    private FrameLayout dockBand;
    private int dockFocus=-2;
    private ValueAnimator dockPress;
    private WallpaperController wallpapers;
    private WeatherController weather;
    private ScreensaverController screensaver;
    private final HomeConfirmGesture confirmGesture=new HomeConfirmGesture();
    private Runnable pendingConfirm;
    private String pendingConfirmFocus="";
    private int saverRow,saverColumn,saverTop,saverRestoreColumn;
    private String saverPackage="";
    private HomeHeaderView homeHeader;
    private TextView weatherText;
    private DesktopSettingsStore desktopSettings;
    private View timeRow, topButtons;
    private boolean rightButtonsVisible=true,homeAppsVisible=true;
    private TextView clock, date, greeting, heroTitle, network, inputButton;
    private KeyFeedback.Popup inputPopup;
    private String systemEntryPending="";
    private long systemEntryStarted;
    private boolean systemEntryDispatched;
    private final String homeInstance=Integer.toHexString(System.identityHashCode(this));
    private int homeResumeSequence;
    private long homeResumeStarted,homePausedAt;
    private boolean homeDrawPending;
    private boolean homeReadyDrawn;
    private static int observedInputKinds;
    private Typeface outfit;
    private boolean chinese = true, fixedGreeting = false, ready = false, resumed = false, requestedLocation = false;
    private final Runnable checkHomeUpdates=()->{
        if(!resumed || !ready || !homeReadyDrawn)return;
        long started=SystemClock.uptimeMillis();
        UpdateRepository.get(this).foreground();
        DiagnosticLog.info("home.update.foreground","elapsedMs="+(SystemClock.uptimeMillis()-started));
    };
    private float unit;
    private int row = 0, column = 0, topColumn = 0, restoreColumn = 0;
    private int scrollTarget = RecyclerView.NO_POSITION;
    private ValueAnimator scrollAnimation;
    private String activePackage = "";
    private TvInputs tvInputs;
    private Tile selected;
    private boolean moveDock,moveSaving,confirmLong;
    private String movingPackage="",pendingMove="",heldPackage="";
    private boolean pendingMoveDock;
    private ArrayList<App> beforeMove;
    private ArrayList<String> moveDefaults;
    private HomeCatalogRefresh.Input moveInput;
    private final Runnable confirmHold=()->{
        App app=currentApp();
        if(screensaverAllowed()&&app!=null&&app.pkg.equals(heldPackage)&&!confirmLong){confirmLong=true;cancelConfirm();pressDock(false);openAppActions(app);}
    };
    private JSONObject config;
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock();if(screensaver!=null)screensaver.timeChanged();if(wallpapers!=null)wallpapers.environmentChanged(); }
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if(Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && i.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            if(Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !i.getBooleanExtra(Intent.EXTRA_REPLACING,false) && i.getData()!=null){String pkg=i.getData().getSchemeSpecificPart();new DockStore(c).remove(pkg);new HomeOrderStore(c).remove(pkg);new HomeVisibilityStore(c).remove(pkg);try{AppCoverStore.get(c).restore(pkg);}catch(java.io.IOException e){Log.w(TAG,"Cover cleanup deferred");}}
            cancelMove();++packageRevision;
            if (resumed) loadApps("package");
        }
    };
    private Runnable stopNetworkCallback;
    private final BroadcastReceiver legacyNetworkReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){updateNetwork();}};

    private int px(float value) { return Math.round(value * unit); }
    private String tr(String zh, String en) { return chinese ? zh : en; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);InputDiagnostics.home(this,isHomeIntent(getIntent()),"create");BackNavigation.install(this,this::handleBack);
        unit = getResources().getDisplayMetrics().widthPixels / 960f;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if(Build.VERSION.SDK_INT>=21)getWindow().setNavigationBarColor(Color.BLACK);
        // A Direct Boot HOME must not open credential-protected preferences, media or databases.
        // Recreate after unlock so the normal lifecycle initializes every desktop dependency once.
        if(Build.VERSION.SDK_INT>=24 && !HomeGuardState.unlocked(this)){
            waitingForUnlock=true;
            DiagnosticLog.info("home.locked_wait","credential_storage_not_opened");
            // Match the starting window while user storage is unavailable; no settings or media reads.
            View waiting=new View(this);waiting.setBackgroundResource(R.drawable.home_startup_background);setContentView(waiting);
            androidx.core.content.ContextCompat.registerReceiver(this,unlockReceiver,new IntentFilter(Intent.ACTION_USER_UNLOCKED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
            resumeAfterUnlock();return;
        }
        LegacyHomeKeyService.prepare(this);
        try {
            config = new JSONObject(readAsset("launcher.json"));
            chinese = !"en".equals(config.optString("language", "zh"));
            fixedGreeting = "home".equals(config.optString("greeting"));
            outfit = Build.VERSION.SDK_INT>=26 ? Typeface.createFromAsset(getAssets(), "fonts/outfit.ttf") : Typeface.create("sans-serif",Typeface.NORMAL);
        } catch (Exception e) { throw new IllegalStateException("Invalid bundled launcher configuration", e); }
        row = getPreferences(0).getInt("row", 0);
        desktopSettings=new DesktopSettingsStore(this);
        column = getPreferences(0).getInt("column", 0);
        activePackage = getPreferences(0).getString("package", "");
        if(state==null && isHomeIntent(getIntent()))resetHomeFocus();
        makeScene();
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        registerReceiver(packageReceiver,packages);
        loadApps("create");
    }

    private void resumeAfterUnlock(){
        if(waitingForUnlock && !unlockRecreateRequested && HomeGuardState.unlocked(this) && !isFinishing()){
            unlockRecreateRequested=true;recreate();
        }
    }
    boolean desktopInitialized(){return !waitingForUnlock;}

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int size;
            while ((size = input.read(buffer)) != -1) out.write(buffer, 0, size);
            return out.toString("UTF-8");
        }
    }

    private TextView text(String value, float size, int weight, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(color);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
        Typeface base=chinese ? Typeface.create("sans-serif", Typeface.NORMAL) : outfit;
        view.setTypeface(Build.VERSION.SDK_INT>=28?Typeface.create(base,weight,false):Typeface.create(base,weight>=600?Typeface.BOLD:Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    private void place(FrameLayout parent, View child, float width, float height, float left, float top) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(width < 0 ? (int)width : px(width), height < 0 ? (int)height : px(height));
        p.leftMargin = px(left); p.topMargin = px(top); parent.addView(child, p);
    }

    private GradientDrawable surface(int color, float radius) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, Color.argb(Color.alpha(color), Math.max(0,Color.red(color)-5), Math.max(0,Color.green(color)-5), Math.max(0,Color.blue(color)-5))});
        bg.setCornerRadius(px(radius)); return bg;
    }

    private GradientDrawable pillSurface() {
        // Neutral translucent highlights let the ambient color show through.
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x38ffffff, 0x1cffffff, 0x12ffffff});
        bg.setCornerRadius(px(22));
        bg.setStroke(px(.5f), 0x78ffffff);
        return bg;
    }

    private void makeScene() {
        root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); root.setClipChildren(false);
        UiCompat.onDraw(root,()->{
            if(!resumed)return;
            if(homeDrawPending){
                homeDrawPending=false;
                // This callback precedes drawing; it does not establish a frame presented by the TV.
                homeResumeEvent("home.first_draw","evidence=on_draw_callback_only");
            }
            if(ready && !homeReadyDrawn){homeReadyDrawn=true;scheduleHomeUpdate();}
        });
        setContentView(root);
        wallpapers = new WallpaperController(this, root, unit, chinese);
        list = new RecyclerView(this){
            @Override public void draw(Canvas canvas){int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());super.draw(canvas);canvas.restoreToCount(save);}
        };
        list.setClipChildren(true); list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER); list.setItemAnimator(null);
        list.setVerticalScrollBarEnabled(false); list.setHorizontalScrollBarEnabled(false);
        list.setFocusable(false); list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        list.setPadding(px(CONTENT_MARGIN),0,px(CONTENT_MARGIN),px(8));
        layout = new GridLayoutManager(this,APP_COLUMNS);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position < 2 ? APP_COLUMNS : 1; }
        });
        list.setLayoutManager(layout);
        root.addView(list,new FrameLayout.LayoutParams(-1,-1));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                View header=layout.findViewByPosition(0);
                if(header!=null)header.setAlpha(Math.max(0,Math.min(1,1+header.getTop()/(float)px(180))));
                updateHeaderFocus();invalidateDockBackdrop();
            }
        });
        homeHeader=new HomeHeaderView(this,unit,chinese,outfit);
        greeting=homeHeader.greeting;weatherText=homeHeader.weather;clock=homeHeader.clock;date=homeHeader.date;timeRow=homeHeader.timeRow;topButtons=homeHeader.buttons;
        pills.addAll(homeHeader.pills);inputButton=pills.get(0);network=pills.get(1);
        inputButton.setOnClickListener(v->KeyFeedback.clicked(v,this::showInputs));network.setOnClickListener(v->KeyFeedback.clicked(v,()->openSettings(Settings.ACTION_WIFI_SETTINGS)));pills.get(2).setOnClickListener(v->KeyFeedback.clicked(v,()->openSettings(Settings.ACTION_SETTINGS)));
        weather=new WeatherController(this,chinese,(data,message,loading)->{homeHeader.weather(data);if(screensaver!=null)screensaver.weatherChanged(data);});
        appAdapter=new AppAdapter();list.setAdapter(appAdapter);
        applyDesktopSettings();updateClock(); updateNetwork();
        screensaver=new ScreensaverController(this,root,wallpapers,unit,chinese,new ScreensaverController.Host(){
            @Override public boolean allowed(){return screensaverAllowed();}
            @Override public void captureFocus(){cancelConfirm();cancelFocusRestore("screensaver");if(scrollAnimation!=null)scrollAnimation.cancel();list.stopScroll();saverRow=row;saverColumn=column;saverTop=topColumn;saverRestoreColumn=restoreColumn;saverPackage=activePackage;}
            @Override public void restoreFocus(){row=Math.min(saverRow,gridRows());if(row<0&&!rightButtonsVisible)row=0;column=Math.max(0,Math.min(saverColumn,maxColumn(row)));topColumn=Math.min(saverTop,Math.max(0,visiblePills.size()-1));restoreColumn=saverRestoreColumn;activePackage=saverPackage;focusCurrent(false);}
            @Override public void desktopVisible(boolean visible){list.setVisibility(visible?View.VISIBLE:View.INVISIBLE);}
            @Override public void weatherDemand(boolean enabled){weather.screensaverWeather(enabled);}
        });
    }

    private void applyDesktopSettings(){
        homeHeader.apply();
        boolean showApps=desktopSettings.showHomeApps();
        if(homeAppsVisible!=showApps){homeAppsVisible=showApps;row=0;column=0;activePackage="";}
        if(heroTitle!=null)heroTitle.setVisibility(homeAppsVisible&&desktopSettings.showAppTitle()?View.VISIBLE:View.GONE);
        applyDockAppearance();
        TextView previous=visiblePills.isEmpty()?null:visiblePills.get(topColumn);
        boolean[] shown={desktopSettings.showInput(),desktopSettings.showNetwork(),desktopSettings.showSettings()};
        visiblePills.clear();
        for(int i=0;i<pills.size();i++){
            TextView pill=pills.get(i);pill.setVisibility(shown[i]?View.VISIBLE:View.GONE);
            if(shown[i])visiblePills.add(pill);
        }
        int restored=visiblePills.indexOf(previous);
        topColumn=restored>=0?restored:Math.max(0,Math.min(topColumn,visiblePills.size()-1));
        rightButtonsVisible=!visiblePills.isEmpty();
        topButtons.setVisibility(rightButtonsVisible?View.VISIBLE:View.GONE);
        if(!rightButtonsVisible && row<0){row=0;column=Math.min(restoreColumn,maxColumn(0));}
        if(ready)focusCurrent(false);
    }

    private void setPillIcon(TextView pill,int resource){homeHeader.setPillIcon(pill,resource);}
    private void updateClock(){homeHeader.updateClock(fixedGreeting);}

    private void scheduleHomeUpdate(){
        if(!resumed || !ready || !homeReadyDrawn)return;
        root.removeCallbacks(checkHomeUpdates);root.post(checkHomeUpdates);
    }

    private void updateNetwork() {
        scheduleHomeUpdate();
        if(wallpapers!=null)wallpapers.environmentChanged();
        if(network.getVisibility()!=View.VISIBLE)return;
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        boolean ethernet,wifi;
        if(Build.VERSION.SDK_INT>=23){
            NetworkCapabilities nc=cm.getNetworkCapabilities(cm.getActiveNetwork());
            ethernet=nc!=null && nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            wifi=nc!=null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }else{
            NetworkInfo active=cm.getActiveNetworkInfo();
            boolean connected=active!=null && active.isConnected();
            ethernet=connected && active.getType()==ConnectivityManager.TYPE_ETHERNET;
            wifi=connected && active.getType()==ConnectivityManager.TYPE_WIFI;
        }
        if(ethernet) {
            network.setContentDescription(tr("有线网络","Ethernet"));setPillIcon(network,R.drawable.ic_ethernet);
        }
        else if(wifi) {
            setPillIcon(network,R.drawable.ic_wifi);
            if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && resumed && !requestedLocation){
                requestedLocation=true;
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},101);
            }
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            String ssid=wm.getConnectionInfo().getSSID();
            network.setContentDescription(ssid!=null && !ssid.equals("<unknown ssid>")?ssid.replace("\"", ""):tr("Wi-Fi 已连接","Wi-Fi connected"));
        } else {network.setContentDescription(tr("Wi-Fi 未连接","Wi-Fi offline"));setPillIcon(network,R.drawable.ic_wifi_off);}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==101)updateNetwork();
        if(requestCode==102)DiagnosticLog.info("input.permission.result","granted="+(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED));
        if(requestCode==102 && tvInputs!=null){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)tvInputs.openPending();else tvInputs.pendingTvInput=null;}
    }

    @Override protected void onRestart() {
        super.onRestart();
        // API28 releases the window Surface when stopped, but an unchanged visible window
        // can resume without relayout. Re-submit its attributes to restore that Surface.
        if(Build.VERSION.SDK_INT==28)getWindow().setAttributes(getWindow().getAttributes());
    }

    @Override protected void onResume() {
        if(waitingForUnlock){super.onResume();resumeAfterUnlock();return;}
        homeResumeStarted=SystemClock.uptimeMillis();++homeResumeSequence;homeDrawPending=true;homeReadyDrawn=false;
        homeResumeEvent("home.resume.begin","sincePauseMs="+(homePausedAt==0?-1:homeResumeStarted-homePausedAt));
        super.onResume(); resumed=true;
        new DesktopRoutes(this).reconcileVendor();
        if(!new HomeProxy(this).allowed(getIntent())){new HomeProxy(this).original(this);finish();}
        if(!systemEntryPending.isEmpty()){systemEntryLifecycle(systemEntryDispatched?"resume_return_unverified":"resume_before_dispatch");systemEntryPending="";}
        BootAppLifecycle.visible(this);
        if(!HomeGuardService.connected())HomeGuardRecovery.request(this,"桌面恢复");
        getWindow().getDecorView().post(()->{if(resumed && hasWindowFocus()){new HomeProxy(this).visible(this);if(!isFinishing()){HomeGuardRecovery.visible(this);launchBootApp();}}});
        IntentFilter time=new IntentFilter(Intent.ACTION_TIME_TICK);time.addAction(Intent.ACTION_TIME_CHANGED);time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver,time);
        if(Build.VERSION.SDK_INT>=24)stopNetworkCallback=Api24.observe(this);
        else registerReceiver(legacyNetworkReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        applyDesktopSettings();updateClock();updateNetwork();
        wallpapers.resume();weather.resume();screensaver.availabilityChanged();
        loadApps("resume");if(ready)focusCurrent(false);
        beginPendingMove();
        if(tvInputs!=null)main.post(tvInputs::openPending);
        main.post(this::openMaintenanceInput);
        homeResumeEvent("home.resume.end","");
    }

    private void homeResumeEvent(String event,String detail){
        long now=SystemClock.uptimeMillis();
        DiagnosticLog.info(event,"instance="+homeInstance+" resume="+homeResumeSequence+" uptimeMs="+now
            +" elapsedMs="+(homeResumeSequence==0?-1:now-homeResumeStarted)+" resumed="+resumed+" windowFocus="+hasWindowFocus()
            +" ready="+ready+" loadingApps="+catalogRefresh.loading()+" drawPending="+homeDrawPending
            +" size="+(root==null?"0x0":root.getWidth()+"x"+root.getHeight())+(detail.isEmpty()?"":" "+detail));
    }

    private void openMaintenanceInput(){
        if(!resumed || Build.VERSION.SDK_INT<21)return;
        String id=DeviceInputProfile.consume();if(id.isEmpty())return;
        try{
            TvInputInfo input=new TvInputController(this).findInput(id);
            if(input==null){DeviceInputProfile.dispatched(false);return;}
            if(tvInputs==null)tvInputs=new TvInputs();
            leaveSystemEntry("input.maintenance",()->{boolean opened=tvInputs.launchInput(input);DeviceInputProfile.dispatched(opened);systemEntryResult(opened);});
        }catch(RuntimeException failure){DeviceInputProfile.dispatched(false);DiagnosticLog.error("input.maintenance.failed",failure);}
    }

    @Override public void onWindowFocusChanged(boolean focused){
        if(waitingForUnlock){super.onWindowFocusChanged(focused);return;}
        long started=SystemClock.uptimeMillis();
        homeResumeEvent("home.window_focus","focused="+focused+" "+InputDiagnostics.focus(this));
        try{
            super.onWindowFocusChanged(focused);if(wallpapers!=null)wallpapers.windowFocusChanged(focused);if(screensaver!=null){if(!focused)cancelConfirm();screensaver.availabilityChanged();}systemEntryLifecycle("window_focus="+focused);if(focused && resumed && systemEntryDispatched)systemEntryPending="";if(focused && resumed){new HomeProxy(this).visible(this);if(!isFinishing()){HomeGuardRecovery.visible(this);launchBootApp();}}
        }finally{homeResumeEvent("home.window_focus.end","focused="+focused+" callbackMs="+(SystemClock.uptimeMillis()-started));}
    }

    void checkBootApp(){getWindow().getDecorView().post(this::launchBootApp);}

    private void launchBootApp(){
        BootAppStore store=new BootAppStore(this);if(store.target().isEmpty())return;
        long started=SystemClock.uptimeMillis();
        if(isFinishing() || !resumed || !hasWindowFocus() || !HomeGuardState.canShow(this))return;
        String pkg=store.consume();
        DiagnosticLog.info("home.autostart.check","elapsedMs="+(SystemClock.uptimeMillis()-started)+" consumed="+!pkg.isEmpty());
        if(pkg.isEmpty())return;
        leaveHome(()->{try{
            Intent intent=store.launchIntent(pkg);
            if(intent==null){Log.i(BootAppStore.TAG,"target unavailable");wallpapers.resume();Toast.makeText(this,tr("自启应用不可用，请在应用管理中重新设置","Startup app unavailable"),Toast.LENGTH_LONG).show();return;}
            if(ChildBlockActivity.gate(this,pkg)){Log.i(BootAppStore.TAG,"blocked by child mode");return;}
            if(new HomeGuardState(this).protects(pkg))HomeGuardService.allowOriginal();
            startActivity(intent);
            Log.i(BootAppStore.TAG,"launch requested");DiagnosticLog.info("app.autostart.dispatched",pkg);
        }catch(RuntimeException e){Log.w(BootAppStore.TAG,"launch rejected");DiagnosticLog.error("app.autostart.failed",e);wallpapers.resume();Toast.makeText(this,tr("自启应用暂时无法打开","Unable to open startup app"),Toast.LENGTH_SHORT).show();}});
    }

    @Override protected void onPause() {
        if(waitingForUnlock){super.onPause();return;}
        cancelConfirm();screensaver.pause();
        cancelFocusRestore("pause");
        root.removeCallbacks(checkHomeUpdates);homeReadyDrawn=false;
        if(!HomeGuardState.canShow(this))new HomeProxy(this).defer(getIntent());
        homePausedAt=SystemClock.uptimeMillis();homeResumeEvent("home.pause","");homeDrawPending=false;
        systemEntryLifecycle("pause");
        BootAppLifecycle.left(this);
        if(dockPress!=null)dockPress.cancel();updateDock(false);
        HomeGuardRecovery.leftHome(this);
        resumed=false;main.removeCallbacks(confirmHold);heldPackage="";cancelMove();if(scrollAnimation!=null)scrollAnimation.cancel();list.stopScroll();saveFocus();wallpapers.pause();weather.pause();
        unregisterReceiver(timeReceiver);
        if(Build.VERSION.SDK_INT>=24)stopNetworkCallback.run();
        else unregisterReceiver(legacyNetworkReceiver);
        if(inputPopup!=null) inputPopup.closeQuietly();
        super.onPause();
    }

    @Override protected void onDestroy(){
        if(waitingForUnlock){unregisterReceiver(unlockReceiver);loader.shutdownNow();super.onDestroy();return;}
        cancelFocusRestore("destroy");
        BootAppLifecycle.destroyed(this);unregisterReceiver(packageReceiver);loader.shutdownNow();screensaver.destroy();wallpapers.destroy();weather.destroy();super.onDestroy();
    }
    @Override protected void onStop(){
        if(!waitingForUnlock){homeResumeEvent("home.stop","");wallpapers.hidden();}
        super.onStop();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);InputDiagnostics.home(this,isHomeIntent(intent),"new_intent");
        if(waitingForUnlock){resumeAfterUnlock();return;}
        if(!new HomeProxy(this).allowed(intent)){new HomeProxy(this).original(this);finish();return;}
        getWindow().getDecorView().post(()->{if(resumed && hasWindowFocus())new HomeProxy(this).visible(this);});
        homeResumeEvent("home.new_intent","home="+isHomeIntent(intent));
        cancelConfirm();screensaver.pause();if(resumed)wallpapers.resume();screensaver.availabilityChanged();
        if(isHomeIntent(intent))resetHomeFocus();else if(ready)focusCurrent(false);
        main.post(this::openMaintenanceInput);
    }

    private boolean isHomeIntent(Intent intent){return Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME);}

    private void resetHomeFocus(){
        cancelMove();
        if(inputPopup!=null && inputPopup.isShowing())inputPopup.closeQuietly();
        row=0;column=Math.max(0,Math.min(restoreColumn,maxColumn(0)));
        // Do not let an in-flight catalog refresh restore the old grid application.
        activePackage="";saveFocus();focusCurrent(false);
    }

    private static class App {
        String pkg,label,artworkKey; ComponentName component; Bitmap banner; int color;
    }

    private HomeCatalogRefresh.Input catalogInput(){
        return new HomeCatalogRefresh.Input(new DockStore(this).load(),new HomeOrderStore(this).signature(),
            new ChildModeStore(this).revision(),AppCoverStore.get(this).revision(),packageRevision,homeAppsVisible,new HomeVisibilityStore(this).signature());
    }

    private void loadApps(String reason) {
        final HomeCatalogRefresh.Input requested=catalogInput();
        if(!catalogRefresh.start(requested))return;
        final int sequence=++catalogSequence;
        final long started=SystemClock.uptimeMillis();
        DiagnosticLog.info("home.catalog.begin","request="+sequence+" reason="+reason);
        final List<String> pinned=requested.pinned;
        loader.execute(()->{
            try {
            final long workerStarted=SystemClock.uptimeMillis();
            PackageManager pm=getPackageManager();
            ArrayList<HomeAppCatalog.Entry> catalog=new HomeAppCatalog(this).load(pinned);
            final long catalogLoadedAt=SystemClock.uptimeMillis();
            ArrayList<App> found=new ArrayList<>();
            HashMap<String,App> freshCache=new HashMap<>();
            for(HomeAppCatalog.Entry entry:catalog) {
                ResolveInfo info=entry.info;if(info==null)continue;
                String key=new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString();
                try { key += ":" + pm.getPackageInfo(info.activityInfo.packageName,0).lastUpdateTime; }
                catch (PackageManager.NameNotFoundException e) { continue; }
                key += ":cover:"+AppCoverStore.get(this).key(info.activityInfo.packageName);
                App cached=artworkCache.get(key);
                if(cached!=null){found.add(cached);freshCache.put(key,cached);continue;}
                App app=new App();app.pkg=info.activityInfo.packageName;app.label=entry.label;app.artworkKey=key;
                app.component=new ComponentName(app.pkg,info.activityInfo.name);
                app.banner=AppArtwork.load(this,info);app.color=sampleColor(app.banner);found.add(app);freshCache.put(key,app);
            }
            artworkCache.clear();artworkCache.putAll(freshCache);
            App wallpaperApp=new App();wallpaperApp.pkg=WALLPAPER_ENTRY;wallpaperApp.label=tr("壁纸设置","Wallpaper Settings");
            wallpaperApp.banner=wallpapers.entryBanner();wallpaperApp.color=HomeEntryArtwork.WALLPAPER_COLOR;found.add(0,wallpaperApp);
            App desktopApp=new App();desktopApp.pkg=DESKTOP_SETTINGS_ENTRY;desktopApp.label=tr("桌面设置","Desktop Settings");
            desktopApp.banner=HomeEntryArtwork.render(this,desktopApp.label,true);desktopApp.color=HomeEntryArtwork.DESKTOP_COLOR;found.add(1,desktopApp);
            ArrayList<String> defaults=new ArrayList<>();for(App app:found)if(!pinned.contains(app.pkg))defaults.add(app.pkg);
            ArrayList<String> gridOrder=new HomeOrderStore(this).order(defaults);
            final long preparedAt=SystemClock.uptimeMillis();
            main.post(()->{
                if(UiCompat.destroyed(this))return;
                long applyStarted=SystemClock.uptimeMillis();
                if(!catalogRefresh.finish(catalogInput())){
                    DiagnosticLog.info("home.catalog.stale","request="+sequence+" elapsedMs="+(SystemClock.uptimeMillis()-started));
                    if(resumed)loadApps("changed_during_load");return;
                }
                ArrayList<App> nextDock=new ArrayList<>(),nextApps=new ArrayList<>();
                for(String pkg:pinned)for(App app:found)if(pkg.equals(app.pkg))nextDock.add(app);
                HashMap<String,App> byId=new HashMap<>();for(App app:found)byId.put(app.pkg,app);
                for(String id:gridOrder)nextApps.add(byId.get(id));
                // A display-only projection: never save these temporary entries into the grid order.
                if(!homeAppsVisible)for(App app:nextDock)if(!nextApps.contains(app))nextApps.add(app);
                boolean dockChanged=!sameApps(dock,nextDock),gridChanged=!sameApps(apps,nextApps);
                if(dockChanged||gridChanged){
                    cancelFocusRestore("catalog_changed");
                    ArrayList<App> previousApps=new ArrayList<>(apps);
                    DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
                        @Override public int getOldListSize(){return previousApps.size();}
                        @Override public int getNewListSize(){return nextApps.size();}
                        @Override public boolean areItemsTheSame(int oldPosition,int newPosition){return previousApps.get(oldPosition).pkg.equals(nextApps.get(newPosition).pkg);}
                        @Override public boolean areContentsTheSame(int oldPosition,int newPosition){return sameApp(previousApps.get(oldPosition),nextApps.get(newPosition));}
                    });
                    if(dockChanged){dock.clear();dock.addAll(nextDock);}
                    if(gridChanged){apps.clear();apps.addAll(nextApps);}
                    if(dockChanged)appAdapter.notifyItemChanged(0);
                    diff.dispatchUpdatesTo(new ListUpdateCallback(){
                        @Override public void onInserted(int position,int count){appAdapter.notifyItemRangeInserted(position+2,count);}
                        @Override public void onRemoved(int position,int count){appAdapter.notifyItemRangeRemoved(position+2,count);}
                        @Override public void onMoved(int from,int to){appAdapter.notifyItemMoved(from+2,to+2);}
                        @Override public void onChanged(int position,int count,Object payload){appAdapter.notifyItemRangeChanged(position+2,count,payload);}
                    });
                }
                if(row>=0){
                    if(!activePackage.isEmpty()) {
                        for(int n=0;n<dock.size();n++)if(activePackage.equals(dock.get(n).pkg)){row=0;column=n;}
                        for(int n=0;n<apps.size();n++)if(activePackage.equals(apps.get(n).pkg)){row=1+n/APP_COLUMNS;column=n%APP_COLUMNS;}
                    }
                    row=Math.max(0,Math.min(row,gridRows()));column=Math.min(column,maxColumn(row));
                }
                boolean first=!ready;ready=true;
                appAdapter.notifyItemChanged(1);
                if(screensaver!=null)screensaver.availabilityChanged();
                if(resumed&&(first||dockChanged||gridChanged))focusCurrent(false);
                beginPendingMove();
                long appliedAt=SystemClock.uptimeMillis();
                DiagnosticLog.info("home.catalog.applied","request="+sequence+" elapsedMs="+(appliedAt-started)
                    +" workerQueueMs="+(workerStarted-started)+" catalogMs="+(catalogLoadedAt-workerStarted)+" prepareMs="+(preparedAt-catalogLoadedAt)
                    +" queueMs="+(applyStarted-preparedAt)+" applyMs="+(appliedAt-applyStarted)+" dockChanged="+dockChanged+" gridChanged="+gridChanged);
                Log.i(TAG,"Loaded dock="+dock.size()+" apps="+apps.size());
            });
            } catch (RuntimeException e) {
                main.post(()->{catalogRefresh.failed();Log.w(TAG,"App refresh failed; retaining current tiles",e);DiagnosticLog.error("app.catalog.failed",e);});
            }
        });
    }

    private static boolean sameApp(App a,App b){return a.pkg.equals(b.pkg)&&a.label.equals(b.label)&&Objects.equals(a.component,b.component)&&Objects.equals(a.artworkKey,b.artworkKey);}
    private static boolean sameApps(List<App> a,List<App> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!sameApp(a.get(i),b.get(i)))return false;return true;}

    static int sampleColor(Bitmap bitmap) {
        double best=0;int chosen=0xff5a6680;
        float[] hsv=new float[3];
        for(int y=10;y<bitmap.getHeight();y+=12)for(int x=10;x<bitmap.getWidth();x+=12){
            int color=bitmap.getPixel(x,y);Color.colorToHSV(color,hsv);
            double score=hsv[1]*hsv[2];
            if(hsv[2]>.15 && hsv[2]<.98 && score>best){best=score;chosen=color;}
        }
        return chosen;
    }

    private int gridRows(){return (apps.size()+APP_COLUMNS-1)/APP_COLUMNS;}
    private int maxColumn(int r){if(r<0)return visiblePills.size()-1;if(r==0)return homeAppsVisible?Math.max(0,dock.size()-1):0;return Math.min(APP_COLUMNS-1,apps.size()-(r-1)*APP_COLUMNS-1);}

    private void saveFocus(){if(row>=0)getPreferences(0).edit().putInt("row",row).putInt("column",column).putString("package",activePackage).apply();}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==HomeAppActionsActivity.REQUEST&&result==RESULT_OK&&data!=null){
            if("move".equals(data.getStringExtra("action"))){pendingMove=data.getStringExtra("package");pendingMoveDock=data.getBooleanExtra("dock",false);}
            else{activePackage=data.getStringExtra("package");if(activePackage==null)activePackage="";++packageRevision;}
        }
        if(request==WallpaperFilesActivity.REQUEST&&result==Activity.RESULT_OK&&data!=null&&wallpapers!=null)getWindow().getDecorView().post(()->{if(!isFinishing()&&!UiCompat.destroyed(this))wallpapers.imported(data.getStringExtra("wallpaper"));});
    }
    public void handleBack(){
        cancelConfirm();
        if(screensaver!=null&&screensaver.showing()){screensaver.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));return;}
        if(moveSaving)return;
        if(!movingPackage.isEmpty()){cancelMove();KeyFeedback.returned(this);return;}
        if(inputPopup!=null&&inputPopup.isShowing()){inputPopup.closeQuietly();KeyFeedback.returned(this);}
        else if(row>0){row=0;column=Math.min(restoreColumn,maxColumn(0));activePackage="";focusCurrent(true);KeyFeedback.returned(this);}
    }
    private void openAppActions(App app){
        if(app==null||moveSaving)return;saveFocus();KeySounds.play(this,KeySoundPolicy.CONFIRM);
        int[] location=new int[2];int x=getResources().getDisplayMetrics().widthPixels/2,y=px(280),bottom=y;
        if(selected!=null){selected.getLocationOnScreen(location);x=location[0]+selected.getWidth()/2;y=location[1];bottom=y+selected.getHeight();}
        Intent intent=new Intent(this,HomeAppActionsActivity.class).putExtra("package",app.pkg).putExtra("dock",row==0)
            .putExtra("anchor-x",x).putExtra("anchor-y",y).putExtra("anchor-bottom",bottom);
        // Reuse the prepared Dock pixels, including its wallpaper shade, without capturing app cards.
        Bitmap backdrop=Bitmap.createBitmap(320,Math.max(1,Math.round(320f*root.getHeight()/root.getWidth())),Bitmap.Config.ARGB_8888);
        Canvas backdropCanvas=new Canvas(backdrop);backdropCanvas.scale(backdrop.getWidth()/(float)root.getWidth(),backdrop.getHeight()/(float)root.getHeight());
        wallpapers.drawDockBackground(backdropCanvas,new Paint(Paint.FILTER_BITMAP_FLAG));intent.putExtra("backdrop",backdrop);
        leaveHome(()->startActivityForResult(intent,HomeAppActionsActivity.REQUEST));
    }
    private ArrayList<String> appIds(List<App> values){ArrayList<String> ids=new ArrayList<>();for(App app:values)ids.add(app.pkg);return ids;}
    private void beginPendingMove(){
        if(pendingMove==null||pendingMove.isEmpty()||!resumed||!ready||catalogRefresh.loading())return;
        List<App> values=pendingMoveDock?dock:apps;int index=-1;for(int i=0;i<values.size();i++)if(pendingMove.equals(values.get(i).pkg))index=i;
        if(index>=0){
            moveDock=pendingMoveDock;movingPackage=pendingMove;beforeMove=new ArrayList<>(values);moveDefaults=appIds(values);moveInput=catalogInput();activePackage=movingPackage;
            cancelConfirm();screensaver.availabilityChanged();row=moveDock?0:1+index/APP_COLUMNS;column=moveDock?index:index%APP_COLUMNS;appAdapter.notifyItemChanged(moveDock?0:1);focusCurrent(false);
            Toast.makeText(this,tr("方向键移动  确定放下  返回取消","Move with arrows  OK to save  Back to cancel"),Toast.LENGTH_SHORT).show();
        }pendingMove="";
    }
    private void moveSelected(int dx,int dy){
        ArrayList<App> values=moveDock?dock:apps;ArrayList<String> ids=appIds(values);int from=ids.indexOf(movingPackage);
        int target=moveDock?Math.max(0,Math.min(ids.size()-1,from+dx)):HomeOrderStore.target(from,ids.size(),APP_COLUMNS,dx,dy);
        if(from<0||target==from||moveDock&&dy!=0)return;
        HomeOrderStore.move(values,from,target);activePackage=movingPackage;row=moveDock?0:1+target/APP_COLUMNS;column=moveDock?target:target%APP_COLUMNS;
        if(moveDock)appAdapter.notifyItemChanged(0);else{appAdapter.notifyItemChanged(from+2);appAdapter.notifyItemChanged(target+2);}
        focusCurrent(false);KeyFeedback.moved(this);
    }
    private void cancelMove(){
        if(movingPackage.isEmpty()||moveSaving)return;
        String id=movingPackage;ArrayList<App> values=moveDock?dock:apps;values.clear();values.addAll(beforeMove);int index=appIds(values).indexOf(id);
        movingPackage="";beforeMove=null;screensaver.availabilityChanged();row=moveDock?0:1+Math.max(0,index)/APP_COLUMNS;column=moveDock?Math.max(0,index):Math.max(0,index)%APP_COLUMNS;activePackage=id;
        if(appAdapter!=null){appAdapter.notifyItemChanged(moveDock?0:1);if(!moveDock)appAdapter.notifyItemRangeChanged(2,apps.size());focusCurrent(false);}
    }
    private void saveMove(){
        if(movingPackage.isEmpty()||moveSaving)return;
        if(!moveInput.equals(catalogInput())){cancelMove();loadApps("move_stale");return;}
        final boolean inDock=moveDock;final ArrayList<String> ids=appIds(inDock?dock:apps),defaults=new ArrayList<>(moveDefaults);moveSaving=true;
        loader.execute(()->{
            boolean saved;
            try{saved=moveInput.equals(catalogInput())&&(inDock?new DockStore(this).saveOrder(HomeOrderStore.merge(new DockStore(this).load(),ids)):new HomeOrderStore(this).save(ids,defaults,false));}catch(RuntimeException e){saved=false;}
            final boolean success=saved;main.post(()->{moveSaving=false;if(UiCompat.destroyed(this))return;if(success){movingPackage="";beforeMove=null;}else cancelMove();screensaver.availabilityChanged();
                appAdapter.notifyItemChanged(inDock?0:1);if(!inDock)appAdapter.notifyItemRangeChanged(2,apps.size());focusCurrent(false);loadApps("move_saved");Toast.makeText(this,success?"顺序已自动保存":"保存失败，已恢复原顺序",Toast.LENGTH_SHORT).show();});
        });
    }
    // Public Activity override; AndroidX core marks its inherited bridge class library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(!waitingForUnlock&&screensaver!=null){
            if(screensaver.key(event)){cancelConfirm();pressDock(false);return true;}
            screensaver.interaction();
        }
        return KeyFeedback.dispatch(getWindow().getDecorView(),event,()->dispatchHomeKey(event));
    }
    @android.annotation.SuppressLint("RestrictedApi")
    private boolean dispatchHomeKey(KeyEvent event){
        boolean calibrating=new RemoteKeyMap(this).capturing(event);
        RemoteKeyMap.observe(this,event,"home");
        if(calibrating)return true;
        DiagnosticLog.navigationKey("quiet_tv_home",event);
        if(waitingForUnlock)return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN && HomeGuardService.navigationKey(key))HomeGuardService.bootSourceUserAction(this);
        int kind=key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN?1
            :key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER?2
            :key==KeyEvent.KEYCODE_VOLUME_UP || key==KeyEvent.KEYCODE_VOLUME_DOWN || key==KeyEvent.KEYCODE_VOLUME_MUTE?4:0;
        if(kind!=0 && event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && (observedInputKinds&kind)==0){
            observedInputKinds|=kind;long received=SystemClock.uptimeMillis();
            DiagnosticLog.info("home.input.first","instance="+homeInstance+" resume="+homeResumeSequence+" kind="+kind+" key="+key+" eventUptimeMs="+event.getEventTime()+" receivedUptimeMs="+received
                +" ageMs="+Math.max(0,received-event.getEventTime())+" ready="+ready+" resumed="+resumed+" windowFocus="+hasWindowFocus());
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN && hasWindowFocus() && systemEntryDispatched && !systemEntryPending.isEmpty()){systemEntryLifecycle("next_home_key");systemEntryPending="";}
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(confirm && (!ready || row<0) && event.getRepeatCount()==0)
            DiagnosticLog.info("home.action.key","entry="+topEntry()+" key="+key+" phase="+event.getAction()+" ready="+ready+" popup="+(inputPopup!=null && inputPopup.isShowing()));
        if(inputPopup!=null && inputPopup.isShowing()){cancelConfirm();return super.dispatchKeyEvent(event);}
        if(!ready)return super.dispatchKeyEvent(event);
        if(ScreensaverController.systemKey(key))return super.dispatchKeyEvent(event);
        if(!confirm)cancelConfirm();
        if(moveSaving)return true;
        if(!movingPackage.isEmpty()){
            if(confirm){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){saveMove();KeyFeedback.confirm(this);}return true;}
            if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)moveSelected(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);return true;}
        }
        String remoteAction=new RemoteKeyMap(this).action(event);
        if("system".equals(remoteAction))return super.dispatchKeyEvent(event);
        if(remoteAction.isEmpty())remoteAction=key==KeyEvent.KEYCODE_MENU?"settings":key==KeyEvent.KEYCODE_TV_INPUT?"inputs":"";
        if(!remoteAction.isEmpty()){
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()){
                if("settings".equals(remoteAction))openSettings(Settings.ACTION_SETTINGS);
                else if("inputs".equals(remoteAction))showInputs();
                else if("desktop_settings".equals(remoteAction))leaveHome(()->startActivity(new Intent(this,DesktopSettingsActivity.class)));
                else if("home".equals(remoteAction)){HomeGuardService.cancelInputTransition();startActivity(HomeController.launchIntent(this));}
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            main.removeCallbacks(confirmHold);heldPackage="";
            int previousRow=row,previousTop=topColumn,previousColumn=column;
            if(row<0){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)topColumn=Math.max(0,topColumn-1);
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)topColumn=Math.min(visiblePills.size()-1,topColumn+1);
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){row=0;column=Math.min(restoreColumn,maxColumn(0));}
            }else{
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)column=Math.max(0,column-1);
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)column=Math.min(maxColumn(row),column+1);
                if(key==KeyEvent.KEYCODE_DPAD_UP){if(row==0){if(rightButtonsVisible){restoreColumn=column;row=-1;}}else{row--;column=Math.min(row==0?restoreColumn:column,maxColumn(row));}}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN && row<gridRows()){if(row==0){restoreColumn=column;column=0;}row++;column=Math.min(column,maxColumn(row));}
            }
            if(previousRow==row && previousTop==topColumn && previousColumn==column)return true;
            if((previousRow<0 || row<0) && (previousRow!=row || previousTop!=topColumn))
                DiagnosticLog.info("home.action.focus","entry="+topEntry()+" row="+row+" key="+key);
            focusCurrent(true);KeyFeedback.moved(this);return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(row<0)visiblePills.get(topColumn).setPressed(event.getAction()==KeyEvent.ACTION_DOWN);
            if(!screensaverAllowed()){cancelConfirm();return super.dispatchKeyEvent(event);}
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(!pendingConfirmFocus.isEmpty()&&!pendingConfirmFocus.equals(confirmFocus()))cancelConfirm();
                if(pendingConfirm!=null){main.removeCallbacks(pendingConfirm);pendingConfirm=null;}
                if(event.getRepeatCount()==0){pressDock(true);confirmLong=false;App app=currentApp();heldPackage=row>=0&&app!=null?app.pkg:"";if(!heldPackage.isEmpty())main.postDelayed(confirmHold,600);}
                confirmGesture.down(key,event.getDownTime(),event.getRepeatCount());
                if(event.getRepeatCount()>0){if(pendingConfirm!=null)main.removeCallbacks(pendingConfirm);pendingConfirm=null;}
            }else if(event.getAction()==KeyEvent.ACTION_UP){
                main.removeCallbacks(confirmHold);heldPackage="";
                pressDock(false);if(row<0)visiblePills.get(topColumn).setPressed(false);
                if(confirmLong){confirmLong=false;cancelConfirm();return true;}
                HomeConfirmGesture.Result result=confirmGesture.up(key,event.getDownTime(),event.getEventTime(),event.isCanceled(),ViewConfiguration.getLongPressTimeout());
                if(pendingConfirm!=null)main.removeCallbacks(pendingConfirm);pendingConfirm=null;
                if(result==HomeConfirmGesture.Result.DOUBLE){cancelConfirm();screensaver.enter();KeyFeedback.confirm(this);}
                else if(result==HomeConfirmGesture.Result.WAIT){
                    KeyEvent confirmation=new KeyEvent(event);pendingConfirmFocus=confirmFocus();pendingConfirm=()->{
                        boolean valid=confirmGesture.pending()&&screensaverAllowed()&&pendingConfirmFocus.equals(confirmFocus());cancelConfirm();
                        if(valid)KeyFeedback.dispatch(getWindow().getDecorView(),confirmation,()->{if(row<0)visiblePills.get(topColumn).performClick();else launchSelected();return true;});
                    };main.postDelayed(pendingConfirm,HomeConfirmGesture.WINDOW_MS);
                }else cancelConfirm();
            }else cancelConfirm();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean screensaverAllowed(){return ready&&resumed&&!moveSaving&&movingPackage.isEmpty()&&hasWindowFocus()&&!isFinishing()&&wallpapers!=null&&!wallpapers.pickerShowing()&&(inputPopup==null||!inputPopup.isShowing())&&!(getCurrentFocus() instanceof EditText);}
    private String confirmFocus(){App app=currentApp();return row+":"+column+":"+topColumn+":"+(app==null?"":app.pkg);}
    private void cancelConfirm(){main.removeCallbacks(confirmHold);heldPackage="";if(pendingConfirm!=null)main.removeCallbacks(pendingConfirm);pendingConfirm=null;pendingConfirmFocus="";confirmGesture.cancel();}
    private void focusCurrent(boolean animate) {
        if(pendingConfirm!=null&&!pendingConfirmFocus.equals(confirmFocus()))cancelConfirm();
        if(!ready)return;
        AppAdapter adapter=(AppAdapter)list.getAdapter();
        int gridStride=px(adapter.gridHeight)+px(APP_GAP);
        int bottom=Math.max(px(20),list.getHeight()-px(APP_TITLE_HEIGHT)-gridRows()*gridStride);
        if(list.getPaddingBottom()!=bottom)list.setPadding(px(CONTENT_MARGIN),0,px(CONTENT_MARGIN),bottom);
        if(row<0||row==0&&!homeAppsVisible){
            if(scrollAnimation!=null)scrollAnimation.cancel();
            list.stopScroll();scrollTarget=0;layout.scrollToPositionWithOffset(0,0);
            if(row==0){activePackage="";saveFocus();}
            focusApp(animate);Log.i(TAG,"Focus top="+topColumn);return;
        }
        int position=row==0?0:2+(row-1)*APP_COLUMNS;
        int offset=row==0?0:px(row==1?APP_TITLE_HEIGHT:APP_TOP);
        if(scrollAnimation!=null && scrollAnimation.isRunning() && scrollTarget!=position){scrollAnimation.cancel();list.stopScroll();}
        boolean needsScroll=row<=1;
        if(row>1){
            View first=layout.findViewByPosition(position);
            needsScroll=first==null || first.getTop()<px(APP_TOP) || first.getBottom()>list.getHeight()-px(20);
        }
        if(needsScroll && !(animate && scrollTarget==position && scrollAnimation!=null && scrollAnimation.isRunning())){
            if(scrollAnimation!=null)scrollAnimation.cancel();
            list.stopScroll();scrollTarget=position;
            if(!animate)layout.scrollToPositionWithOffset(position,offset);
            else{
                int first=layout.findFirstVisibleItemPosition();View view=layout.findViewByPosition(first);
                if(view!=null){
                    int before=first==0?0:px(HOME_HEADER_HEIGHT)+(first==1?0:px(APP_TITLE_HEIGHT)+(first-2)/APP_COLUMNS*gridStride);
                    int current=before-layout.getDecoratedTop(view);
                    int target=position==0?0:px(HOME_HEADER_HEIGHT)+px(APP_TITLE_HEIGHT)+(row-1)*gridStride-offset;
                    if(target!=current){
                        final int[] moved={0};scrollAnimation=ValueAnimator.ofInt(0,target-current);
                        scrollAnimation.setDuration(420);scrollAnimation.setInterpolator(FOCUS_MOTION);
                        scrollAnimation.addUpdateListener(a->{int distance=(int)a.getAnimatedValue();list.scrollBy(0,distance-moved[0]);moved[0]=distance;});scrollAnimation.start();
                    }
                }else layout.scrollToPositionWithOffset(position,offset);
            }
        }
        focusApp(animate);
    }

    private void cancelFocusRestore(String reason){
        ++focusGeneration;
        if(focusRestore==null)return;
        if(list.getViewTreeObserver().isAlive())list.getViewTreeObserver().removeOnPreDrawListener(focusRestore);
        focusRestore=null;
        if(!"superseded".equals(reason))DiagnosticLog.info("home.focus.cancel","reason="+reason);
    }

    private void clearSelected(boolean animate){
        if(selected==null)return;
        Tile previous=selected;selected=null;previous.invalidate();
        if(!dockTiles.contains(previous))transformTile(previous,1,0,0,animate,260);
    }

    private void transformTile(Tile tile,float scale,float x,float y,boolean animate,int duration){
        tile.animate().cancel();
        if(animate)tile.animate().scaleX(scale).scaleY(scale).translationX(x).translationY(y).setDuration(duration).setInterpolator(FOCUS_MOTION);
        else{tile.setScaleX(scale);tile.setScaleY(scale);tile.setTranslationX(x);tile.setTranslationY(y);}
    }

    private void focusApp(boolean animate) {
        cancelFocusRestore("superseded");
        if(!resumed)return;
        final int generation=focusGeneration;
        focusRestore=()->{
            if(generation!=focusGeneration)return true;
            if(!resumed||UiCompat.destroyed(this)){cancelFocusRestore("inactive");return true;}
            if(list.hasPendingAdapterUpdates()||list.isLayoutRequested())return true;
            if(row<0||row==0&&!homeAppsVisible){
                cancelFocusRestore("superseded");clearSelected(animate);updateDock(animate);updateHeaderFocus();return true;
            }
            App app=currentApp();if(app==null){cancelFocusRestore("no_app");return true;}
            Tile tile=row==0&&column<dockTiles.size()?dockTiles.get(column):null;
            if(row>0){RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(2+(row-1)*APP_COLUMNS+column);tile=holder!=null?(Tile)holder.itemView:null;}
            if(tile==null||!androidx.core.view.ViewCompat.isAttachedToWindow(tile)){
                if(scrollAnimation==null||!scrollAnimation.isRunning())cancelFocusRestore("tile_unavailable");
                return true;
            }
            cancelFocusRestore("superseded");
            Tile previous=selected;if(previous!=tile)clearSelected(animate);selected=tile;
            updateDock(animate);if(previous!=tile)tile.invalidate();
            if(row>0){tile.setPivotX(tile.getWidth()/2f);tile.setPivotY(tile.getHeight()/2f);transformTile(tile,FOCUS_SCALE,0,-px(2),animate,320);}
            activePackage=app.pkg;
            if(row==0 && heroTitle!=null)heroTitle.setText(app.label);
            wallpapers.setThemeColor(app.color);
            updateHeaderFocus();saveFocus();Log.i(TAG,"Focus row="+row+" column="+column+" package="+app.pkg);
            if(!animate)DiagnosticLog.info("home.focus.restored","request="+generation+" row="+row+" column="+column);
            return true;
        };
        list.getViewTreeObserver().addOnPreDrawListener(focusRestore);list.invalidate();
    }


    private App currentApp(){if(row==0)return !homeAppsVisible||dock.isEmpty()?null:dock.get(Math.min(column,dock.size()-1));int n=(row-1)*APP_COLUMNS+column;return n>=0&&n<apps.size()?apps.get(n):null;}
    private void selectTile(Tile tile){
        List<App> values=tile.showLabel?apps:dock;int index=values.indexOf(tile.app);if(index<0)return;
        row=tile.showLabel?1+index/APP_COLUMNS:0;column=tile.showLabel?index%APP_COLUMNS:index;activePackage=tile.app.pkg;focusCurrent(false);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        InputDiagnostics.pointer(this,event);
        if(!waitingForUnlock&&screensaver!=null){cancelConfirm();if(screensaver.touch(event))return true;screensaver.interaction();}
        if(!waitingForUnlock && event.getActionMasked()==MotionEvent.ACTION_DOWN)HomeGuardService.bootSourceUserAction(this);
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
        if(!waitingForUnlock&&screensaver!=null){cancelConfirm();if(screensaver.pointer(event))return true;screensaver.interaction();}return super.dispatchGenericMotionEvent(event);
    }
    private void leaveHome(Runnable launch){cancelConfirm();screensaver.pause();HomeGuardService.bootSourceUserAction(this);wallpapers.beforeLeave(()->{if(resumed&&!isFinishing()&&!UiCompat.destroyed(this))launch.run();});}
    private void launchSelected(){
        App app=currentApp();if(app==null)return;saveFocus();
        if(!movingPackage.isEmpty()){saveMove();KeyFeedback.confirm(this);return;}
        if(new HomeVisibilityStore(this).hidden(app.pkg)){loadApps("hidden_launch");return;}
        KeyFeedback.confirm(this);
        if(WALLPAPER_ENTRY.equals(app.pkg)){wallpapers.showPicker();return;}
        leaveHome(()->{
            if(DESKTOP_SETTINGS_ENTRY.equals(app.pkg)){startActivity(new Intent(this,DesktopSettingsActivity.class));return;}
            if(MANAGEMENT_ENTRY.equals(app.pkg)){startActivity(new Intent(this,AppManagementActivity.class));return;}
            try{
                if(ChildBlockActivity.gate(this,app.pkg))return;
                if(new HomeGuardState(this).protects(app.pkg))HomeGuardService.allowOriginal();
                DiagnosticLog.info("app.launch.request",app.pkg);
                startActivity(new Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                DiagnosticLog.info("app.launch.dispatched",app.pkg);
            }catch(RuntimeException e){DiagnosticLog.error("app.launch.failed",e);wallpapers.resume();Toast.makeText(this,tr("应用暂不可用，请检查儿童模式设置","App unavailable"),Toast.LENGTH_SHORT).show();}
        });
    }

    private void invalidateDockBackdrop() {
        if(dockBand!=null)dockBand.invalidate();
    }

    private void updateDock(boolean animate) {
        if(dockTiles.isEmpty()||dockBand==null||dockBand.getWidth()==0)return;
        int focus=row==0&&homeAppsVisible?Math.min(column,dockTiles.size()-1):-1;
        if(animate&&focus==dockFocus)return;
        Tile first=dockTiles.get(0);if(first.getWidth()==0)return;
        if(dockPress!=null)dockPress.cancel();dockFocus=focus;
        float gap=dockTiles.size()>1?dockTiles.get(1).getLeft()-first.getRight():px(18);
        float[] shifts=DockMotion.translations(dockTiles.size(),focus,first.getWidth(),gap,first.getLeft(),dockBand.getWidth(),px(8));
        for(int i=0;i<dockTiles.size();i++){
            Tile item=dockTiles.get(i);item.animate().cancel();item.setPivotX(item.getWidth()/2f);item.setPivotY(item.getHeight());
            float scale=DockMotion.scale(i,focus),rise=focus<0?0:i==focus?-px(2):Math.abs(i-focus)==1?-px(1):0;
            if(animate){
                if(item.getScaleX()!=scale||item.getScaleY()!=scale||item.getTranslationX()!=shifts[i]||item.getTranslationY()!=rise)
                    item.animate().scaleX(scale).scaleY(scale).translationX(shifts[i]).translationY(rise).setDuration(focus<0?220:260).setInterpolator(FOCUS_MOTION);
            }
            else{item.setScaleX(scale);item.setScaleY(scale);item.setTranslationX(shifts[i]);item.setTranslationY(rise);}
        }
    }

    private void pressDock(boolean pressed) {
        if(dockPress!=null)dockPress.cancel();
        if(row!=0||!homeAppsVisible||dockTiles.isEmpty())return;
        Tile item=dockTiles.get(Math.min(column,dockTiles.size()-1));item.animate().cancel();float from=item.getScaleX(),target=(DockMotion.scale(column,column))*(pressed?.98f:1);
        dockPress=ValueAnimator.ofFloat(from,target);dockPress.setDuration(pressed?80:120);dockPress.setInterpolator(FOCUS_MOTION);
        dockPress.addUpdateListener(a->{float value=(float)a.getAnimatedValue();item.setScaleX(value);item.setScaleY(value);});dockPress.start();
    }

    private void updateHeaderFocus() {
        if(!ready)return;
        View header=layout.findViewByPosition(0);boolean visible=row<0 && header!=null && header.getTop()==0;
        homeHeader.focusButton(visible&&!visiblePills.isEmpty()?pills.indexOf(visiblePills.get(topColumn)):-1);
        for(TextView pill:pills){boolean focused=visible && visiblePills.indexOf(pill)==topColumn;pill.setActivated(focused);if(!focused)pill.setPressed(false);}
    }

    private void applyDockAppearance(){
        if(dockBand==null)return;
        boolean show=homeAppsVisible && !dock.isEmpty() && dockBand.getChildCount()>0;
        dockBand.setVisibility(show?View.VISIBLE:View.GONE);
        dockBand.setBackgroundDrawable(show && desktopSettings.showDockBackground()?new DockBackdropDrawable(wallpapers,dockBand,root,unit):null);
    }

    private final class DockBand extends FrameLayout {
        boolean restoreLayout;
        DockBand(){super(HomeActivity.this);setVisibility(View.GONE);}
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            super.onLayout(changed,left,top,right,bottom);
            if(restoreLayout&&dockBand==this){restoreLayout=false;updateDock(false);}
        }
        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);restoreLayout=true;}
    }

    private final class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        private final float gridHeight=((960-2*CONTENT_MARGIN)/APP_COLUMNS-APP_GAP)*203f/360f+APP_LABEL_GAP+APP_LABEL_HEIGHT;
        private final float dockPadding=18, dockGap=18, dockWidth=(864-2*dockPadding-(DockStore.CAPACITY-1)*dockGap)/DockStore.CAPACITY, dockHeight=dockWidth*203f/360f;
        @Override public int getItemViewType(int position){return position==0?0:position==1?1:2;}
        @Override public int getItemCount(){return 2+apps.size();}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int type){
            View view;
            if(type==0){
                FrameLayout header=new FrameLayout(HomeActivity.this);header.setClipChildren(false);header.setLayoutParams(new RecyclerView.LayoutParams(-1,px(HOME_HEADER_HEIGHT)));
                heroTitle=text("",40,400,0xfff1f2f6);heroTitle.setVisibility(homeAppsVisible&&desktopSettings.showAppTitle()?View.VISIBLE:View.GONE);place(header,heroTitle,820,56,2,HOME_TITLE_TOP);
                DockBand band=new DockBand();dockBand=band;
                wallpapers.observeDock(HomeActivity.this::invalidateDockBackdrop);
                band.setClipChildren(false);place(header,band,-1,dockHeight+2*dockPadding,0,HOME_DOCK_TOP);
                header.setTag(band);view=header;
            }else if(type==1){view=new View(HomeActivity.this);view.setLayoutParams(new RecyclerView.LayoutParams(-1,px(APP_TITLE_HEIGHT)));}
            else{Tile tile=new Tile(true);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(gridHeight));p.leftMargin=px(APP_GAP/2);p.rightMargin=px(APP_GAP/2);p.bottomMargin=px(APP_GAP);tile.setLayoutParams(p);view=tile;}
            return new RecyclerView.ViewHolder(view){};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            if(position==0){
                FrameLayout header=(FrameLayout)holder.itemView;
                if(homeHeader.getParent()!=header){
                    if(homeHeader.getParent()!=null)((ViewGroup)homeHeader.getParent()).removeView(homeHeader);
                    place(header,homeHeader,960,HOME_HEADER_HEIGHT,-CONTENT_MARGIN,0);
                }
                header.setAlpha(1);
                DockBand band=(DockBand)holder.itemView.getTag();dockBand=band;
                if(dockPress!=null)dockPress.cancel();
                for(Tile old:dockTiles){old.animate().cancel();if(old.app!=null&&visibleTiles.get(old.app.pkg)==old)visibleTiles.remove(old.app.pkg);if(selected==old)selected=null;}
                band.removeAllViews();dockTiles.clear();dockFocus=-2;band.restoreLayout=true;
                float width=dockWidth,height=dockHeight;
                FrameLayout.LayoutParams bandParams=(FrameLayout.LayoutParams)band.getLayoutParams();
                bandParams.width=px(2*dockPadding+dock.size()*width+Math.max(0,dock.size()-1)*dockGap);bandParams.height=px(height+2*dockPadding);
                bandParams.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;bandParams.leftMargin=0;band.setLayoutParams(bandParams);
                for(int n=0;n<dock.size();n++){Tile tile=new Tile();tile.bind(dock.get(n));dockTiles.add(tile);place(band,tile,width,height,dockPadding+n*(width+dockGap),dockPadding);}
                applyDockAppearance();
                heroTitle.setText(dock.isEmpty()?"":dock.get(Math.min(Math.max(column,0),dock.size()-1)).label);
            }else if(position>=2)((Tile)holder.itemView).bind(apps.get(position-2));
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){if(holder.itemView instanceof Tile){Tile tile=(Tile)holder.itemView;if(tile.app!=null && visibleTiles.get(tile.app.pkg)==tile)visibleTiles.remove(tile.app.pkg);}}
    }

    private final class Tile extends View {
        App app;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF imageRect=new RectF();Shader highlight;
        private BitmapShader artwork;private final Matrix artworkMatrix=new Matrix();
        final boolean showLabel;
        final android.text.TextPaint labelPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String label="";
        private float labelBaseline;
        Tile(){this(false);}
        Tile(boolean showLabel){super(HomeActivity.this);this.showLabel=showLabel;
            labelPaint.setTextSize(px(14));labelPaint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            labelPaint.setColor(0xfff1f2f6);labelPaint.setTextAlign(Paint.Align.CENTER);labelPaint.setShadowLayer(px(1),0,px(1),0x99000000);
            setOnClickListener(v->{if(app!=null){if(!movingPackage.isEmpty()){saveMove();return;}selectTile(this);launchSelected();}});
            setOnLongClickListener(v->{if(app==null||!movingPackage.isEmpty())return true;selectTile(this);openAppActions(app);return true;});}
        void bind(App value){if(app!=null && visibleTiles.get(app.pkg)==this)visibleTiles.remove(app.pkg);transformTile(this,1,0,0,false,0);app=value;artwork=new BitmapShader(app.banner,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);updateArtwork();visibleTiles.put(app.pkg,this);setContentDescription(app.label);updateLabel();invalidate();}
        float imageHeight(){return getWidth()*203f/360f;}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);highlight=new LinearGradient(0,0,w,imageHeight(),new int[]{0x30ffffff,0x09ffffff},null,Shader.TileMode.CLAMP);updateArtwork();updateLabel();}
        private void updateArtwork(){
            if(artwork==null)return;
            artworkMatrix.setScale(getWidth()/(float)app.banner.getWidth(),imageHeight()/app.banner.getHeight());artwork.setLocalMatrix(artworkMatrix);
        }
        private void updateLabel(){
            if(!showLabel || app==null)return;
            label=android.text.TextUtils.ellipsize(app.label,labelPaint,Math.max(0,getWidth()-px(8)),android.text.TextUtils.TruncateAt.END).toString();
            Paint.FontMetrics metrics=labelPaint.getFontMetrics();
            labelBaseline=imageHeight()+px(APP_LABEL_GAP)+(px(APP_LABEL_HEIGHT)-metrics.bottom-metrics.top)/2;
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(app==null)return;float h=imageHeight(),radius=px(11);RectF rect=imageRect;rect.set(0,0,getWidth(),h);
            {
                // Paint anti-aliasing does not control clipPath edges on older TV renderers.
                paint.setColor(0xff191a20);c.drawRoundRect(rect,radius,radius,paint);
                paint.setShader(artwork);c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);
            }
            if(selected==this && row>=0){
                paint.setShader(highlight);c.drawRoundRect(rect,radius,radius,paint);paint.setShader(null);
                float stroke=px(FOCUS_STROKE),inset=stroke/2f;rect.inset(inset,inset);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(Color.WHITE);c.drawRoundRect(rect,Math.max(0,radius-inset),Math.max(0,radius-inset),paint);paint.setStyle(Paint.Style.FILL);
            }
            if(showLabel&&desktopSettings.showAllAppsNames())c.drawText(label,getWidth()/2f,labelBaseline,labelPaint);
            if(app.pkg.equals(movingPackage)){
                paint.setColor(0xffeff0f4);float width=px(58),height=px(20);RectF badge=new RectF(getWidth()-width-px(5),px(5),getWidth()-px(5),px(5)+height);c.drawRoundRect(badge,px(5),px(5),paint);
                paint.setColor(0xff191d24);paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(px(11));c.drawText(tr("移动中","Moving"),badge.centerX(),badge.centerY()+px(4),paint);
            }
        }

    }

    private String topEntry(){
        if(row>=0 || topColumn<0 || topColumn>=visiblePills.size())return "none";
        int index=pills.indexOf(visiblePills.get(topColumn));return index==0?"input":index==1?"network":index==2?"settings":"none";
    }
    private void systemEntryLifecycle(String state){
        if(!systemEntryPending.isEmpty())DiagnosticLog.info("home.action.lifecycle","entry="+systemEntryPending+" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-systemEntryStarted)+" state="+state);
    }
    private void leaveSystemEntry(String entry,Runnable action){
        systemEntryPending=entry;systemEntryDispatched=false;systemEntryStarted=android.os.SystemClock.elapsedRealtime();
        DiagnosticLog.info("home.action.request","entry="+entry+" ready="+ready+" resumed="+resumed+" windowFocus="+hasWindowFocus()+" api="+Build.VERSION.SDK_INT);
        leaveHome(()->{systemEntryLifecycle("wallpaper_exit_ready");action.run();});
    }
    private void systemEntryResult(boolean dispatched){
        systemEntryDispatched=dispatched;
        if(dispatched && systemEntryPending.startsWith("input."))HomeGuardService.inputSelected(this);
        systemEntryLifecycle(dispatched?"dispatched_unverified":"not_dispatched");
        if(!dispatched)systemEntryPending="";
    }
    private void openSettings(String action){
        leaveSystemEntry(Settings.ACTION_WIFI_SETTINGS.equals(action)?"network":"settings",()->{
            boolean opened=new SystemSettingsRouter(this).open(action);systemEntryResult(opened);
            if(!opened){wallpapers.resume();Toast.makeText(this,tr("系统设置不可用，请使用电视遥控器","Settings unavailable; use the TV remote"),Toast.LENGTH_LONG).show();}
        });
    }

    private void showInputs(){
        HomeGuardService.bootSourceUserAction(this);
        DiagnosticLog.info("input.request","api="+Build.VERSION.SDK_INT+" standardSupported="+(Build.VERSION.SDK_INT>=21));
        if(Build.VERSION.SDK_INT>=21){if(tvInputs==null)tvInputs=new TvInputs();tvInputs.show();return;}
        Toast.makeText(this,tr("请在电视系统设置中选择信号源，或使用遥控器信号源键","Select an input in TV settings or use the remote input key"),Toast.LENGTH_LONG).show();
        openSettings(Settings.ACTION_SETTINGS);
    }

    @android.annotation.TargetApi(24)
    private static final class Api24 {
        static Runnable observe(HomeActivity home){
            ConnectivityManager manager=(ConnectivityManager)home.getSystemService(CONNECTIVITY_SERVICE);
            ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
                @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){home.main.post(home::updateNetwork);}
                @Override public void onLost(Network n){home.main.post(home::updateNetwork);}
            };
            manager.registerDefaultNetworkCallback(callback);
            return ()->manager.unregisterNetworkCallback(callback);
        }
    }

    @android.annotation.TargetApi(21)
    private final class TvInputs {
        private TvInputInfo pendingTvInput;
        void openPending(){if(pendingTvInput!=null && resumed && checkCallingOrSelfPermission("android.permission.READ_TV_LISTINGS")==PackageManager.PERMISSION_GRANTED){TvInputInfo input=pendingTvInput;pendingTvInput=null;openInput(input);}}
        void show(){
            TvInputController.Snapshot snapshot=new TvInputController(HomeActivity.this).inspect();
            List<VendorTvInputs.Source> vendor=snapshot.inputs.isEmpty()?new VendorTvInputs(HomeActivity.this).load():Collections.emptyList();
            TvInputController.Panel direct=vendor.isEmpty()?snapshot.directPanel():null;
            DiagnosticLog.info("input.menu.inspect","standard="+snapshot.state+" inputs="+snapshot.inputs.size()+" vendor="+vendor.size()+" panels="+snapshot.panels.size()+" profilesUnavailable="+snapshot.profilesUnavailable+" direct="+(direct!=null));
            if(direct!=null){openSystemInputs(direct);return;}
            ArrayList<TvInputInfo> inputs=snapshot.inputs;
            Collections.sort(inputs,(a,b)->{
                int ar=a.getType()==TvInputInfo.TYPE_HDMI?0:a.isPassthroughInput()?1:2;
                int br=b.getType()==TvInputInfo.TYPE_HDMI?0:b.isPassthroughInput()?1:2;
                return ar==br?inputName(a).compareTo(inputName(b)):Integer.compare(ar,br);
            });
            LinearLayout menu=new KeyFeedback.Menu(HomeActivity.this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(7),px(7),px(7),px(7));menu.setBackgroundDrawable(surface(0xfa25262e,18));
            ArrayList<TextView> choices=new ArrayList<>();HashSet<String> tuners=new HashSet<>();
            for(TvInputInfo info:inputs){
                if(!info.isPassthroughInput() && !tuners.add("live"))continue;
                addInputChoice(menu,choices,inputName(info),()->openInput(info));
            }
            for(VendorTvInputs.Source source:vendor)addInputChoice(menu,choices,source.label,()->leaveSystemEntry("input.vendor",()->{
                boolean opened=new VendorTvInputs(HomeActivity.this).open(source);systemEntryResult(opened);
                if(!opened){wallpapers.resume();showInputProblem(tr("原厂信号源未接受切换请求，请使用电视遥控器。","The TV did not accept the input request. Use the TV remote."));}
            }));
            for(TvInputController.Panel panel:snapshot.availablePanels())addInputChoice(menu,choices,panel.profile.label(chinese),()->openSystemInputs(panel));
            DiagnosticLog.info("input.menu","choices="+choices.size());
            if(choices.isEmpty()){showUnavailableInputs(snapshot);return;}
            inputPopup=new KeyFeedback.Popup(HomeActivity.this,menu,px(222),-2);inputPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));inputPopup.setElevation(px(14));inputPopup.setOutsideTouchable(true);
            inputPopup.setOnDismissListener(()->main.post(()->updateHeaderFocus()));
            for(int n=0;n<choices.size();n++){
                final int index=n;TextView choice=choices.get(n);
                choice.setOnKeyListener((v,key,event)->{
                    if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
                    if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)choices.get(Math.max(0,Math.min(choices.size()-1,index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)))).requestFocus();return true;}return false;
                });
            }
            inputPopup.showAsDropDown(inputButton,0,px(9));choices.get(0).requestFocus();
        }

        private void addInputChoice(LinearLayout menu,ArrayList<TextView> choices,String title,Runnable action){
            TextView choice=text(title,15,400,0xffeeeeef);choice.setPadding(px(16),0,px(12),0);choice.setGravity(Gravity.CENTER_VERTICAL);choice.setFocusable(true);choice.setFocusableInTouchMode(true);choice.setBackgroundDrawable(ControlStyle.listRow(unit,11));
            final int choiceIndex=choices.size();choice.setOnClickListener(v->{DiagnosticLog.info("input.menu.select","index="+choiceIndex);inputPopup.closeQuietly();action.run();KeyFeedback.confirm(HomeActivity.this);});
            choices.add(choice);menu.addView(choice,new LinearLayout.LayoutParams(-1,px(40)));
        }

        private String panelProblem(TvInputController.PanelState state){
            switch(state){
                case DISABLED:return tr("系统信号源组件已禁用。请先在电视系统中检查该组件状态。","The system input component is disabled. Check its state in the TV settings.");
                case RESTRICTED:return tr("电视系统不允许 Quiet TV 打开此信号源面板。请使用电视遥控器的信号源键。","The TV restricts access to HomeActivity.this input panel. Use the TV remote's Input button.");
                case UNAVAILABLE:return tr("系统信号源入口已不可用。请使用电视遥控器的信号源键。","The system input panel is no longer available. Use the TV remote's Input button.");
                default:return tr("系统信号源面板未能打开。请使用电视遥控器的信号源键。","The system input panel could not be opened. Use the TV remote's Input button.");
            }
        }

        private void openSystemInputs(TvInputController.Panel panel){
            leaveSystemEntry("input.panel",()->{
                TvInputController.PanelState state=new TvInputController(HomeActivity.this).openPanel(panel.profile);
                systemEntryResult(state==TvInputController.PanelState.READY);
                if(state!=TvInputController.PanelState.READY){wallpapers.resume();showInputProblem(panelProblem(state));}
            });
        }

        private void showUnavailableInputs(TvInputController.Snapshot snapshot){
            String message;
            switch(snapshot.state){
                case UNAVAILABLE:message=tr("电视未提供可用的标准输入服务。","The standard TV input service is unavailable.");break;
                case RESTRICTED:message=tr("电视系统限制了标准输入列表的读取。","The TV restricts access to its standard input list.");break;
                case FAILED:message=tr("电视标准输入服务未能返回输入列表。","The TV input service could not return its input list.");break;
                default:message=tr("电视未通过标准接口提供输入列表。","The TV returned no inputs through the standard interface.");
            }
            for(TvInputController.Panel panel:snapshot.panels)message+="\n\n"+panel.profile.label(chinese)+"\n"+panelProblem(panel.state);
            if(snapshot.panels.isEmpty())message+="\n\n"+tr("尚未找到可用的系统信号源入口。请使用电视遥控器的信号源键，或进入系统设置。这不表示电视没有 HDMI／AV 接口。","No usable system input panel was found. Use the TV remote's Input button or open system settings. This does not mean the TV has no HDMI/AV ports.");
            if(snapshot.profilesUnavailable)message+="\n\n"+tr("系统信号源兼容信息暂不可用。","System input compatibility information is unavailable.");
            showInputProblem(message);
        }

        private void showInputProblem(String message){
            DiagnosticLog.info("input.problem","dialog_shown");
            android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(HomeActivity.this)
                .setTitle(tr("信号源暂不可用","Inputs unavailable")).setMessage(message)
                .setNegativeButton(tr("返回","Back"),(d,w)->KeySounds.play(HomeActivity.this,KeySoundPolicy.BACK))
                .setPositiveButton(tr("系统设置","System settings"),(d,w)->{
                    openSettings(Settings.ACTION_SETTINGS);KeySounds.play(HomeActivity.this,KeySoundPolicy.CONFIRM);
                }).create();
            dialog.setOnDismissListener(d->main.post(()->updateHeaderFocus()));dialog.show();ControlStyle.dialog(dialog,unit);
        }

        private void offerSystemInputs(){
            TvInputController.Snapshot snapshot=new TvInputController(HomeActivity.this).inspect();
            List<TvInputController.Panel> panels=snapshot.availablePanels();
            if(panels.isEmpty()){
                String message=tr("此输入无法打开，且未找到可用的系统信号源面板。请使用电视遥控器的信号源键或进入系统设置。","This input could not be opened and no usable system input panel was found. Use the TV remote's Input button or system settings.");
                for(TvInputController.Panel panel:snapshot.panels)message+="\n\n"+panel.profile.label(chinese)+"\n"+panelProblem(panel.state);
                showInputProblem(message);return;
            }
            String[] labels=new String[panels.size()];for(int i=0;i<labels.length;i++)labels[i]=panels.get(i).profile.label(chinese);
            android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(HomeActivity.this)
                .setTitle(tr("此输入无法打开，请选择系统信号源","Input unavailable; choose system inputs"))
                .setItems(labels,(d,which)->{openSystemInputs(panels.get(which));KeySounds.play(HomeActivity.this,KeySoundPolicy.CONFIRM);})
                .setNegativeButton(tr("返回","Back"),(d,w)->KeySounds.play(HomeActivity.this,KeySoundPolicy.BACK)).create();
            dialog.setOnDismissListener(d->main.post(()->updateHeaderFocus()));dialog.show();ControlStyle.dialog(dialog,unit);
        }

        private void openInput(TvInputInfo info){leaveSystemEntry("input.standard",()->{DiagnosticLog.info("input.select","provider="+info.getServiceInfo().packageName+" type="+info.getType()+" passthrough="+info.isPassthroughInput());boolean opened=launchInput(info);systemEntryResult(opened);if(!opened)wallpapers.resume();});}
        private boolean launchInput(TvInputInfo info){
            TvInputController controller=new TvInputController(HomeActivity.this);
            info=controller.mappedInput(info);
            if(info==null){offerSystemInputs();return false;}
            if(controller.openKnownInput(info))return true;
            // Native DTV players do not need an Android channel database or READ_TV_LISTINGS.
            if(info.getType()==TvInputInfo.TYPE_TUNER && controller.openFallbackInput(info))return true;
            Uri uri;
            if(info.isPassthroughInput())uri=TvContract.buildChannelUriForPassthroughInput(info.getId());
            else{
                if(checkCallingOrSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED){
                    DiagnosticLog.info("input.permission","permission=android.permission.READ_TV_LISTINGS result=missing requestable="+(Build.VERSION.SDK_INT>=23));
                    if(Build.VERSION.SDK_INT>=23){pendingTvInput=info;requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},102);}
                    else Toast.makeText(HomeActivity.this,tr("系统未授予电视频道读取权限","TV channel permission was not granted by the system"),Toast.LENGTH_SHORT).show();
                    return false;
                }
                long channel=-1;
                try(android.database.Cursor cursor=getContentResolver().query(TvContract.buildChannelsUriForInput(info.getId()),new String[]{TvContract.Channels._ID},null,null,null)){
                    if(cursor!=null && cursor.moveToFirst())channel=cursor.getLong(0);
                }catch(SecurityException e){DiagnosticLog.error("input.channels.failed",e);Toast.makeText(HomeActivity.this,tr("系统未允许读取电视频道","TV channels are restricted by the system"),Toast.LENGTH_SHORT).show();return false;}
                DiagnosticLog.info("input.channels","hasChannel="+(channel>=0));
                if(channel<0){
                    android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(HomeActivity.this)
                        .setTitle(tr("尚未配置电视频道","No TV channels configured"))
                        .setMessage(tr("电视系统的频道库为空。连接天线或有线电视后，可在信号源设置中搜台。网络直播请打开天光云影。","Connect an antenna or cable source, then configure channels. For IPTV, open your streaming app."))
                        .setNegativeButton(tr("返回","Back"),(d,w)->KeySounds.play(HomeActivity.this,KeySoundPolicy.BACK));
                    Intent setup=info.createSetupIntent();
                    if(setup!=null)dialog.setPositiveButton(tr("信号源设置","Set up input"),(d,w)->{try{DiagnosticLog.info("input.setup.request",DiagnosticLog.intent(setup));startActivity(setup);KeySounds.play(HomeActivity.this,KeySoundPolicy.CONFIRM);DiagnosticLog.info("input.setup.dispatch","dispatched_unverified");}catch(ActivityNotFoundException|SecurityException e){DiagnosticLog.error("input.setup.failed",e);Toast.makeText(HomeActivity.this,tr("信号源设置不可用","Input setup unavailable"),Toast.LENGTH_SHORT).show();}});
                    ControlStyle.dialog(dialog.show(),unit);return false;
                }
                uri=TvContract.buildChannelUri(channel);
            }
            try{
                Intent intent=controller.standardIntent(info.getId(),uri);
                if(intent!=null){startActivity(intent);DiagnosticLog.info("input.standard.dispatch",intent.getComponent().flattenToShortString());return true;}
            }catch(RuntimeException e){DiagnosticLog.error("input.standard.failed",e);}
            if(controller.openFallbackInput(info))return true;
            offerSystemInputs();return false;
        }

        private String inputName(TvInputInfo info){
            String custom=new DeviceInputProfile(HomeActivity.this).label(info.getId());if(!custom.isEmpty())return custom;
            if(!info.isPassthroughInput())return tr("电视直播","Live TV");
            if(info.getType()==TvInputInfo.TYPE_HDMI || info.getType()==TvInputInfo.TYPE_DISPLAY_PORT)return new TvInputController(HomeActivity.this).inputLabel(info);
            if(info.getType()==TvInputInfo.TYPE_COMPOSITE)return "AV";
            return info.loadLabel(HomeActivity.this).toString();
        }
    }
}
