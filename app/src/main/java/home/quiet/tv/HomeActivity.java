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
    private static final String ADD_DOCK_ENTRY = "quiet:add-dock";
    static final int APP_COLUMNS = 5;
    static int gridColumns(String size,String shape){return "rectangle".equals(shape)?("small".equals(size)?6:"large".equals(size)?4:5):7;}
    private int appColumns=APP_COLUMNS;
    private PhoneSettingsServer.Active phoneActionAuthority;private String phoneWeatherKey="";
    private String appearanceKey="",pendingHomeAction="",quickReturn="",pendingButtonFocus="",pendingAppFocus="";
    private boolean pendingDockFocus;
    private int dockLimit=5;
    private Integer brightnessDraft,blurDraft,dockScaleDraft,dockMagnificationDraft;
    private String phoneWallpaperKey="";
    private final Runnable phoneConfiguration=this::phoneConfigurationChanged;
    private static java.lang.ref.WeakReference<HomeActivity> liveHome=new java.lang.ref.WeakReference<>(null);
    private static final float CONTENT_MARGIN = 48;
    // The complete first screen scrolls as one item; preserve its original composition.
    private static final float HOME_HEADER_HEIGHT = 540;
    private static final float HOME_DOCK_TOP = 386;
    private static final float APP_TOP = 24, APP_TITLE_HEIGHT = APP_TOP;
    private static final float APP_GAP = 16, APP_LABEL_GAP = 6, APP_LABEL_HEIGHT = 20;
    private static final float FOCUS_SCALE = 1.065f;
    // A critically damped spring settles without bouncing past the target.
    private static final android.animation.TimeInterpolator FOCUS_MOTION = t -> (float)((1-(1+8*t)*Math.exp(-8*t))/(1-9*Math.exp(-8)));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ArrayList<App> dock = new ArrayList<>(), apps = new ArrayList<>();
    private final ArrayList<String> fullAppOrder=new ArrayList<>();
    private final ArrayList<Tile> dockTiles = new ArrayList<>();
    private final AppCardShadow appCardShadow = new AppCardShadow();
    private final HashMap<String,Tile> visibleTiles = new HashMap<>();
    private final ArrayList<TextView> pills = new ArrayList<>();
    private final ArrayList<TextView> visiblePills = new ArrayList<>();
    private final HashMap<String,App> artworkCache = new HashMap<>();
    private LegacyIconRecovery legacyIcons;
    private boolean legacyArtworkStarted,legacyTrialRetiring,drawingLegacySnapshot,legacyIconsDestroyed;
    private Runnable legacyPendingResult;
    private final HashSet<String> legacyDeferredTrials=new HashSet<>();
    private int legacyTrialGeneration;
    private String legacyTrialPackage="",legacyTrialKey="";
    private final HomeCatalogRefresh catalogRefresh=new HomeCatalogRefresh();
    private volatile long packageRevision;
    private volatile long previewCatalogRevision=-1;
    private volatile Map<String,App> previewCatalog=Collections.emptyMap();
    private Map<String,?> appliedDesktopValues;
    private String dockGeometryKey="";private DockLayout[] dockGeometries;
    private DockLayout renderedDockGeometry;
    private int catalogSequence,focusGeneration,hdmiLaunchGeneration;
    private ViewTreeObserver.OnPreDrawListener focusRestore;
    private boolean waitingForUnlock,unlockRecreateRequested,pendingHomeFocusReset;
    private final BroadcastReceiver unlockReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){resumeAfterUnlock();}
    };
    private FrameLayout root;
    private OperationFeedback operationFeedback;
    private String pendingFeedback="";
    private boolean pendingFeedbackSuccess;
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
    private volatile HomeQuickControlOverlay quickControls;
    private boolean quickControlsOpening,quickDesktopSwitching,quickSettingsOpening;
    private Bundle restoredQuickControls;
    private TextView weatherText;
    private DesktopSettingsStore desktopSettings;
    private View timeRow, topButtons;
    private boolean rightButtonsVisible=true,homeAppsVisible=true;
    private TextView clock, date, greeting, heroTitle, network, inputButton;
    private int networkIcon;
    private KeyFeedback.Popup inputPopup;
    private String systemEntryPending="";
    private long systemEntryStarted;
    private boolean systemEntryDispatched,inputDiscoveryPending;
    private int inputDiscoveryGeneration,systemEntryGeneration;
    private final InputEntryTask inputWork=new InputEntryTask();
    private String maintenanceInputRequest="";
    private String maintenanceEntryRequest="";
    private boolean automaticInputPending;
    private SettingsRouteMemory.Attempt settingsTrial;
    private String settingsTrialAction="";
    private final HashSet<String> settingsTried=new HashSet<>();
    private final String homeInstance=Integer.toHexString(System.identityHashCode(this));
    private int homeResumeSequence;
    private long homeResumeStarted,homePausedAt;
    private boolean homeDrawPending;
    private AutoDiagnosticOperation homeResumeOperation;
    private Runnable homeFrameWindow;
    private boolean externalLaunchDispatched;

    private void clearHomeEvidence(){
        if(homeFrameWindow!=null){homeFrameWindow.run();homeFrameWindow=null;}
        if(homeResumeOperation!=null){homeResumeOperation.cancel();homeResumeOperation=null;}
    }
    private void finishHomeEvidence(){
        if(homeResumeOperation==null||!homeReadyDrawn||!hasWindowFocus())return;
        homeResumeOperation.stage("FOCUSED_READY_DRAW");
        homeResumeOperation.finish();homeResumeOperation=null;
    }
    private boolean homeReadyDrawn;
    private boolean homeFocusSeen;
    private long homeWatchScheduled;
    private final Runnable homeTransitionWatch=this::checkHomeTransition;
    private void checkHomeTransition(){
        if(!resumed||isFinishing()||UiCompat.destroyed(this))return;
        long now=SystemClock.uptimeMillis();
        if(homeDrawPending||!homeFocusSeen&&!hasWindowFocus()||now-homeWatchScheduled>950){
            homeResumeEvent("home.transition.slow.result","stage=foreground_watch drawPending="+homeDrawPending
                +" focusSeen="+homeFocusSeen+" watchQueueMs="+Math.max(0,now-homeWatchScheduled-700));
            DiagnosticProbe.slowResource("home_foreground_watch");
        }
    }
    private static int observedInputKinds;
    private Typeface outfit;
    private boolean chinese = true, fixedGreeting = false, ready = false, resumed = false, requestedLocation = false;
    private final Runnable checkHomeUpdates=()->{
        if(!resumed || !ready || !homeReadyDrawn)return;
        long started=SystemClock.uptimeMillis();
        UpdateRepository.get(this).foreground();
        DiagnosticLog.info("home.update.foreground","elapsedMs="+(SystemClock.uptimeMillis()-started));
    };
    private static final long UPDATE_IDLE_MS=7000L;
    private long lastUpdateIdleInput;
    private final UpdateRepository.Listener homeUpdateListener=this::scheduleUpdateReminder;
    private final Runnable showUpdateReminder=()->{
        if(!resumed||isFinishing()||UiCompat.destroyed(this))return;
        String candidate=UpdateRepository.get(this).reminderCandidate();
        if(candidate.isEmpty())return;
        if(!updateReminderIdle()||SystemClock.uptimeMillis()-lastUpdateIdleInput<UPDATE_IDLE_MS){
            scheduleUpdateReminder();return;
        }
        startActivity(addSettingsBackdrop(new Intent(this,UpdateReminderActivity.class).putExtra(UpdateReminderActivity.CANDIDATE,candidate)));
    };
    private void scheduleUpdateReminder(){
        main.removeCallbacks(showUpdateReminder);
        if(resumed&&!waitingForUnlock&&homeReadyDrawn&&!UpdateRepository.get(this).reminderCandidate().isEmpty())
            main.postDelayed(showUpdateReminder,UPDATE_IDLE_MS);
    }
    private void updateReminderInteraction(){
        lastUpdateIdleInput=SystemClock.uptimeMillis();
        scheduleUpdateReminder();
    }
    private boolean updateReminderIdle(){
        return homeReadyDrawn&&screensaverAllowed()&&screensaver!=null&&!screensaver.showing()
            && !quickControlsOpening&&!quickDesktopSwitching&&!quickSettingsOpening&&restoredQuickControls==null
            && pendingHomeAction.isEmpty()&&pendingConfirm==null&&heldPackage.isEmpty()&&!externalLaunchDispatched
            && !automaticInputPending&&!inputDiscoveryPending&&systemEntryPending.isEmpty()
            && maintenanceInputRequest.isEmpty()&&maintenanceEntryRequest.isEmpty()
            && list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE&&(scrollAnimation==null||!scrollAnimation.isRunning());
    }
    private float unit;
    private int row = 0, column = 0, topColumn = 0, restoreColumn = 0;
    private int scrollTarget = RecyclerView.NO_POSITION;
    private ValueAnimator scrollAnimation;
    private boolean animateHomeReturn;
    private String activePackage = "";
    private boolean keepAppActionPositionUntilRefresh;
    private TvInputs tvInputs;
    private Tile selected;
    private boolean moveDock,moveSaving,confirmLong;
    private String movingPackage="",pendingMove="",heldPackage="";
    private boolean pendingMoveDock;
    private ArrayList<App> beforeMove;
    private ArrayList<String> moveDefaults;
    private HomeCatalogRefresh.Input moveInput;
    private final Runnable confirmHold=()->{
        if(screensaverAllowed()&&row<0&&heldPackage.equals("button:"+topEntry())&&!confirmLong){confirmLong=true;String id=topEntry();cancelConfirm();visiblePills.get(topColumn).setPressed(false);if(!"settings".equals(id))openRemoteHomeTools(true,id);return;}
        App app=currentApp();
        if(screensaverAllowed()&&app!=null&&app.pkg.equals(heldPackage)&&!confirmLong){confirmLong=true;cancelConfirm();pressDock(false);openAppActions(app);}
    };
    private JSONObject config;
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){clearHomeEvidence();homeHeader.clockActive(false);cancelInputWork();return;}
            boolean wake=Intent.ACTION_SCREEN_ON.equals(i.getAction());
            AutoDiagnosticOperation op=wake?AutoDiagnosticOperation.begin("home_wake",500):null;
            DiagnosticProbe.StallWatch watch=wake?DiagnosticProbe.watchMain("home_wake",op):null;
            try{
                if(wake){
                    op.stage("SCREEN_ON_CALLBACK");
                    if(homeResumeOperation!=null)homeResumeOperation.stage("SCREEN_ON_CALLBACK");
                    homeHeader.clockActive(resumed);homeResumeEvent("home.wake","evidence=screen_on_delivery_not_panel");
                }
                updateClock();if(screensaver!=null&&!Intent.ACTION_TIME_TICK.equals(i.getAction()))screensaver.timeChanged();if(wallpapers!=null)wallpapers.environmentChanged();
            }finally{if(watch!=null)watch.end();if(op!=null)op.finish();}
        }
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
        super.onCreate(state);if(state!=null){quickReturn=state.getString("quick-return","");pendingHomeAction=state.getString("quick-pending","");pendingButtonFocus=state.getString("button-pending-focus","");pendingAppFocus=state.getString("app-pending-focus","");pendingDockFocus=state.getBoolean("quick-return-dock");restoredQuickControls=state.getBundle("inline-quick-controls");}InputDiagnostics.home(this,isHomeIntent(getIntent()),"create");
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
        BackNavigation.install(this,this::handleBack);
        ChildMobileServer.restore(this);
        LegacyHomeKeyService.prepare(this);
        try {
            config = new JSONObject(readAsset("launcher.json"));
            chinese = !"en".equals(config.optString("language", "zh"));
            fixedGreeting = "home".equals(config.optString("greeting"));
            outfit = !chinese && Build.VERSION.SDK_INT>=26 ? Typeface.createFromAsset(getAssets(), "fonts/outfit.ttf") : Typeface.create("sans-serif",Typeface.NORMAL);
        } catch (Exception e) { throw new IllegalStateException("Invalid bundled launcher configuration", e); }
        row = getPreferences(0).getInt("row", 0);
        desktopSettings=new DesktopSettingsStore(this);
        column = getPreferences(0).getInt("column", 0);
        activePackage = getPreferences(0).getString("package", "");
        if(state==null && isHomeIntent(getIntent()))resetHomeFocus(true);
        makeScene();
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        registerReceiver(packageReceiver,packages);
        if(LegacyIconRecovery.supported()){
            LegacyIconRecovery policy=LegacyIconRecovery.get(this);
            loader.execute(()->{policy.prepare();main.post(()->{if(!legacyIconsDestroyed&&!UiCompat.destroyed(this)){legacyIcons=policy;loadApps("create");}});});
        }else loadApps("create");
        loader.execute(()->{try{new QuickControlStore(new java.io.File(getFilesDir(),"quick-controls")).read();}catch(java.io.IOException error){DiagnosticLog.info("quick.controls.preload_failed","use checked read on open");}});
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
        GradientDrawable bg = new SurfaceDrawable(unit,GradientDrawable.Orientation.TL_BR,
                new int[]{color, Color.argb(Math.round(Color.alpha(color)*.9f),Color.red(color),Color.green(color),Color.blue(color))});
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
        root = new FrameLayout(this){
            private int displayResume=-1,displayWidth,displayHeight;
            @Override protected void dispatchDraw(Canvas canvas){
                super.dispatchDraw(canvas);
                if(resumed&&(displayResume!=homeResumeSequence||displayWidth!=getWidth()||displayHeight!=getHeight())){
                    displayResume=homeResumeSequence;displayWidth=getWidth();displayHeight=getHeight();
                    DiagnosticDisplaySnapshot.home(HomeActivity.this,this,canvas);
                }
            }
        }; root.setBackgroundColor(Color.BLACK); root.setClipChildren(false);
        UiCompat.onDraw(root,()->{
            if(!resumed)return;
            if(homeDrawPending){
                homeDrawPending=false;
                // This callback precedes drawing; it does not establish a frame presented by the TV.
                if(homeResumeOperation!=null)homeResumeOperation.stage("FIRST_DRAW_CALLBACK");
                homeResumeEvent("home.first_draw","evidence=on_draw_callback_only");
                if(SystemClock.uptimeMillis()-homeResumeStarted>=250){
                    homeResumeEvent("home.transition.slow.result","stage=draw_callback evidence=not_presented_frame");
                    DiagnosticProbe.slowResource("home_slow_draw");
                }
            }
            if(ready && !homeReadyDrawn){homeReadyDrawn=true;scheduleHomeUpdate();scheduleUpdateReminder();finishHomeEvidence();}
        });
        setContentView(root);
        wallpapers = new WallpaperController(this, root, unit, chinese);
        wallpapers.observeBackdrop(()->{if(homeHeader!=null)homeHeader.movie(wallpapers.displayedMovie());if(quickControls!=null)quickControls.refreshBackdrop();});
        operationFeedback=OperationFeedback.get(this);operationFeedback.home(wallpapers::drawDockBackground,()->{
            if(row>0||quickControls!=null||dockTiles.isEmpty()||!dockTiles.get(0).isShown())return 0;
            int[] point=new int[2];dockTiles.get(0).getLocationOnScreen(point);return point[1];
        });
        list = new RecyclerView(this){
            @Override public void draw(Canvas canvas){int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());super.draw(canvas);canvas.restoreToCount(save);}
        };
        list.setClipChildren(false); list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER); list.setItemAnimator(null);
        list.setVerticalScrollBarEnabled(false); list.setHorizontalScrollBarEnabled(false);
        list.setFocusable(false); list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        list.setPadding(px(CONTENT_MARGIN),0,px(CONTENT_MARGIN),px(8));
        // The inline panel shares HOME's window; its focus must not scroll the background.
        layout = new GridLayoutManager(this,appColumns){
            @Override public boolean onRequestChildFocus(RecyclerView parent,RecyclerView.State state,View child,View focused){return quickControlsOpening||quickControls!=null||super.onRequestChildFocus(parent,state,child,focused);}
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent,View child,Rect rect,boolean immediate,boolean focusedChildVisible){return !quickControlsOpening&&quickControls==null&&super.requestChildRectangleOnScreen(parent,child,rect,immediate,focusedChildVisible);}
        };
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position < 2 ? appColumns : 1; }
        });
        list.setLayoutManager(layout);
        root.addView(list,new FrameLayout.LayoutParams(-1,-1));
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                View header=layout.findViewByPosition(0);
                if(header!=null)header.setAlpha(Math.max(0,Math.min(1,1+header.getTop()/(float)px(180))));
                updateHeaderFocus();invalidateDockBackdrop();updateWallpaperBlur();
            }
        });
        list.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateWallpaperBlur());
        homeHeader=new HomeHeaderView(this,unit,chinese,outfit);liveHome=new java.lang.ref.WeakReference<>(this);PhoneSettingsSession.get(this).observeConfiguration(phoneConfiguration);
        greeting=homeHeader.greeting;weatherText=homeHeader.weather;clock=homeHeader.clock;date=homeHeader.date;timeRow=homeHeader.timeRow;topButtons=homeHeader.buttons;
        homeHeader.buttonBackdrop(wallpapers::drawDockBackground,root);pills.addAll(homeHeader.pills);for(int i=0;i<pills.size();i++){final int index=i;TextView pill=pills.get(i);pill.setOnClickListener(v->KeyFeedback.clicked(v,()->{if(index<homeHeader.buttonIds.size())executeHomeButton(homeHeader.buttonIds.get(index));}));pill.setOnLongClickListener(v->{if(index<homeHeader.buttonIds.size()&&!"settings".equals(homeHeader.buttonIds.get(index)))openHomeTools(true,homeHeader.buttonIds.get(index));return true;});}
        weather=new WeatherController(this,chinese,(data,message,loading)->{homeHeader.weather(data);wallpapers.weatherChanged();if(screensaver!=null)screensaver.weatherChanged(data);});
        appAdapter=new AppAdapter();list.setAdapter(appAdapter);
        applyDesktopSettings();updateClock(); updateNetwork();
        screensaver=new ScreensaverController(this,root,wallpapers,unit,chinese,new ScreensaverController.Host(){
            @Override public boolean allowed(){return screensaverAllowed();}
            @Override public void captureFocus(){cancelConfirm();cancelFocusRestore("screensaver");if(scrollAnimation!=null)scrollAnimation.cancel();list.stopScroll();saverRow=row;saverColumn=column;saverTop=topColumn;saverRestoreColumn=restoreColumn;saverPackage=activePackage;}
            @Override public void restoreFocus(){row=Math.min(saverRow,gridRows());if(row<0&&!rightButtonsVisible)row=0;column=Math.max(0,Math.min(saverColumn,maxColumn(row)));topColumn=Math.min(saverTop,Math.max(0,visiblePills.size()-1));restoreColumn=saverRestoreColumn;activePackage=saverPackage;focusCurrent(false);main.post(HomeActivity.this::returnToQuick);}
            @Override public void desktopVisible(boolean visible){list.setVisibility(visible?View.VISIBLE:View.INVISIBLE);}
            @Override public void weatherDemand(boolean enabled){weather.screensaverWeather(enabled);}
        });
    }

    private void applyDesktopSettings(){applyDesktopSettings(true);}
    private void applyDesktopSettings(boolean restoreFocus){
        appliedDesktopValues=new HashMap<>(getSharedPreferences("desktop-settings",0).getAll());
        updateWallpaperBlur();
        homeHeader.apply();
        boolean showApps=desktopSettings.showHomeApps();
        if(homeAppsVisible!=showApps){homeAppsVisible=showApps;row=0;column=0;activePackage="";}
        updateHeroTitle();
        applyDockAppearance();
        TextView previous=visiblePills.isEmpty()?null:visiblePills.get(Math.min(topColumn,visiblePills.size()-1));visiblePills.clear();network=null;inputButton=null;
        for(int i=0;i<homeHeader.buttonIds.size();i++){TextView pill=pills.get(i);visiblePills.add(pill);String id=homeHeader.buttonIds.get(i);if("network".equals(id))network=pill;if("source".equals(id))inputButton=pill;}
        int nextLimit=desktopSettings.appearance("dock-limit");if(nextLimit!=dockLimit&&row==0)activePackage="";dockLimit=nextLimit;
        String nextAppearance=desktopSettings.iconSize()+desktopSettings.iconShape()+":"+desktopSettings.appearance("dock-scale")+":"+desktopSettings.globalGlass()+desktopSettings.dockMagnify()+desktopSettings.appearance("dock-magnification")+desktopSettings.appTitlePosition();
        if(!appearanceKey.equals(nextAppearance)){appearanceKey=nextAppearance;appColumns=gridColumns(desktopSettings.iconSize(),desktopSettings.iconShape());layout.setSpanCount(appColumns);if(row>0&&!activePackage.isEmpty())for(int i=0;i<apps.size();i++)if(activePackage.equals(apps.get(i).pkg)){row=1+i/appColumns;column=i%appColumns;break;}if(appAdapter!=null)appAdapter.notifyDataSetChanged();}
        int restored=visiblePills.indexOf(previous);
        topColumn=restored>=0?restored:Math.max(0,Math.min(topColumn,visiblePills.size()-1));
        rightButtonsVisible=!visiblePills.isEmpty();
        topButtons.setVisibility(rightButtonsVisible?View.VISIBLE:View.GONE);
        if(!rightButtonsVisible && row<0){row=0;column=Math.min(restoreColumn,maxColumn(0));}
        for(Tile tile:visibleTiles.values())if(tile.showLabel)tile.invalidate();
        if(restoreFocus&&ready&&resumed)focusCurrent(false);
    }

    private void updateWallpaperBlur(){
        View header=layout.findViewByPosition(0);
        float progress=header==null?(layout.findFirstVisibleItemPosition()>0||row>0?1:0):Math.max(0,Math.min(1,-header.getTop()/(float)px(HOME_HEADER_HEIGHT)));
        int home=blurDraft==null?desktopSettings.homeWallpaperBlur():blurDraft,second=desktopSettings.secondWallpaperBlur();
        int homeBrightness=brightnessDraft==null?desktopSettings.homeWallpaperBrightness():brightnessDraft,secondBrightness=desktopSettings.secondWallpaperBrightness();
        int homeFade=desktopSettings.homeWallpaperFade(),secondFade=desktopSettings.secondWallpaperFade();
        wallpapers.setDesktopEffects(home,second,progress,Math.round(homeBrightness+(secondBrightness-homeBrightness)*progress),Math.round(homeFade+(secondFade-homeFade)*progress));if(weather!=null)weather.wallpaperWeather(WallpaperGpuSupport.available(this)&&WallpaperStore.get(this).weatherEnabled());
    }

    private void setPillIcon(TextView pill,int resource){homeHeader.setPillIcon(pill,resource);networkIcon=resource;}
    private void updateClock(){homeHeader.updateClock(fixedGreeting);homeHeader.movie(wallpapers.displayedMovie());}

    private void scheduleHomeUpdate(){
        if(!resumed || !ready || !homeReadyDrawn)return;
        root.removeCallbacks(checkHomeUpdates);root.post(checkHomeUpdates);
    }

    private void updateNetwork() {
        scheduleHomeUpdate();
        if(wallpapers!=null)wallpapers.environmentChanged();
        if(network==null||network.getVisibility()!=View.VISIBLE)return;
        ConnectivityManager cm=(ConnectivityManager)getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
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
        clearHomeEvidence();homeResumeOperation=AutoDiagnosticOperation.begin("home_resume",1000);
        if(externalLaunchDispatched){if(homeResumeOperation!=null)homeResumeOperation.stage("AFTER_APP_DISPATCH_UNVERIFIED");externalLaunchDispatched=false;}
        DiagnosticProbe.StallWatch watch=DiagnosticProbe.watchMain("home_resume",homeResumeOperation);
        try{resumeHome();}finally{if(watch!=null)watch.end();}
    }

    private void resumeHome(){
        homeResumeStarted=SystemClock.uptimeMillis();++homeResumeSequence;homeDrawPending=true;homeReadyDrawn=false;homeFocusSeen=false;
        homeResumeEvent("home.resume.begin","sincePauseMs="+(homePausedAt==0?-1:homeResumeStarted-homePausedAt));
        super.onResume(); resumed=true;legacyDeferredTrials.clear();lastUpdateIdleInput=SystemClock.uptimeMillis();UpdateRepository.get(this).add(homeUpdateListener);
        homeFrameWindow=DiagnosticFrameWindow.watch(getWindow(),homeResumeOperation);
        homeWatchScheduled=homeResumeStarted;main.removeCallbacks(homeTransitionWatch);main.postAtTime(homeTransitionWatch,homeWatchScheduled+700);
        long lifecycleReadyAt=SystemClock.uptimeMillis();
        new DesktopRoutes(this).reconcileVendor();
        if(!new HomeProxy(this).allowed(getIntent())){new HomeProxy(this).original(this);finish();}
        if(!systemEntryPending.isEmpty()){systemEntryLifecycle(systemEntryDispatched?"resume_return_unverified":"resume_before_dispatch");systemEntryPending="";}
        BootAppLifecycle.visible(this);
        if(!HomeGuardService.connected())HomeGuardRecovery.ensureBinding(this,"桌面恢复");
        getWindow().getDecorView().post(()->{if(resumed && hasWindowFocus()){new HomeProxy(this).visible(this);if(!isFinishing()){HomeGuardRecovery.visible(this);launchBootApp();}}});
        IntentFilter time=new IntentFilter(Intent.ACTION_TIME_TICK);time.addAction(Intent.ACTION_TIME_CHANGED);time.addAction(Intent.ACTION_TIMEZONE_CHANGED);time.addAction(Intent.ACTION_SCREEN_OFF);time.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(timeReceiver,time);
        if(Build.VERSION.SDK_INT>=24)stopNetworkCallback=Api24.observe(this);
        else registerReceiver(legacyNetworkReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        long routesReadyAt=SystemClock.uptimeMillis();
        if(!getSharedPreferences("desktop-settings",0).getAll().equals(appliedDesktopValues))applyDesktopSettings(false);
        if(pendingHomeFocusReset){
            pendingHomeFocusReset=false;
            closeQuickControls(false);
            resetHomeFocus(true);
        }else if(ready)focusCurrent(false);
        updateClock();homeHeader.clockActive(true);updateNetwork();
        long desktopReadyAt=SystemClock.uptimeMillis();
        wallpapers.resume();weather.resume();screensaver.availabilityChanged();
        long mediaReadyAt=SystemClock.uptimeMillis();
        if(quickSettingsOpening){quickSettingsOpening=false;closeQuickControls(false);}
        if(quickControls!=null)quickControls.resume();
        loadApps("resume");
        loader.execute(()->BootHomeActivity.reconcile(getApplicationContext()));
        beginPendingMove();
        if(tvInputs!=null)main.post(tvInputs::openPending);
        main.post(this::openMaintenanceInput);main.post(this::runPendingHomeAction);main.post(this::returnToQuick);
        long finishedAt=SystemClock.uptimeMillis();
        if(homeResumeOperation!=null){
            homeResumeOperation.stage("RESUME_CALLBACK_COMPLETE");
            homeResumeOperation.metric("resume_callback_ms",finishedAt-homeResumeStarted);
            homeResumeOperation.metric("desktop_ms",desktopReadyAt-routesReadyAt);
            homeResumeOperation.metric("media_ms",mediaReadyAt-desktopReadyAt);
        }
        homeResumeEvent("home.resume.end","");
        if(finishedAt-homeResumeStarted>=100){
            homeResumeEvent("home.transition.slow.result","stage=resume_work lifecycleCallbacksMs="+(lifecycleReadyAt-homeResumeStarted)
                +" routesAndReceiversMs="+(routesReadyAt-lifecycleReadyAt)+" desktopMs="+(desktopReadyAt-routesReadyAt)
                +" mediaMs="+(mediaReadyAt-desktopReadyAt)+" remainingMs="+(finishedAt-mediaReadyAt));
            DiagnosticProbe.slowResource("home_slow_resume");
        }
    }

    private void homeResumeEvent(String event,String detail){
        long now=SystemClock.uptimeMillis();
        DiagnosticLog.info(event,"instance="+homeInstance+" resume="+homeResumeSequence+" uptimeMs="+now
            +" elapsedMs="+(homeResumeSequence==0?-1:now-homeResumeStarted)+" resumed="+resumed+" windowFocus="+hasWindowFocus()
            +" ready="+ready+" loadingApps="+catalogRefresh.loading()+" drawPending="+homeDrawPending
            +" screenOnAgeMs="+DiagnosticProbe.wakeAgeMs()+" size="+(root==null?"0x0":root.getWidth()+"x"+root.getHeight())+(detail.isEmpty()?"":" "+detail));
    }

    private String pairingPreparation="";
    private void openMaintenanceInput(){
        if(!resumed||!hasWindowFocus())return;
        SourcePairing pairing=SourcePairing.existing();
        if(pairing!=null&&pairing.pending()){pairingPreparation=pairing.pendingRequest();pairing.preparePending((receipt,launch)->{
            if(!inputActive()||!hasWindowFocus()){pairing.cancel(receipt.owner,true);return;}
            leaveSystemEntryPrepared("input.pairing",()->{pairingPreparation="";pairing.dispatch(this,receipt,launch,()->{
                systemEntryResult("DISPATCHED_UNVERIFIED".equals(receipt.state));
                if(resumed&&"FAILED".equals(receipt.state)){wallpapers.resume();showInputIssue("信号源测试未完成","请返回“看电视”尝试其他入口。");}
            });});
        });return;}
        if(SystemEntryMaintenance.pending()){openMaintenanceEntry();return;}
        DeviceInputProfile.Request request=DeviceInputProfile.consume();if(request.id.isEmpty())return;
        if(Build.VERSION.SDK_INT<21){
            leaveSystemEntry("input.maintenance",()->{
                Context app=getApplicationContext();
                inputWork.submit(()->{HdmiTarget target=HdmiTarget.decode(request.saved);return target==null?null:target.prepare(app);},
                    (launch,failure)->dispatchSavedHdmi(launch,failure,false));
            });
            maintenanceInputRequest=request.token;return;
        }
        if(tvInputs==null)tvInputs=new TvInputs();
        tvInputs.launch(request.id,false,request.token);
    }

    private void openMaintenanceEntry(){
        if(!inputActive()||!hasWindowFocus())return;
        SystemEntryMaintenance.Test test=SystemEntryMaintenance.consume();if(test==null)return;
        cancelInputWork();maintenanceEntryRequest=test.token;
        new BootAppStore(this).userAction("system_entry_test");
        leaveSystemEntryPrepared(test.entry+".maintenance",()->{
            boolean opened=SystemEntryMaintenance.dispatch(this,test);maintenanceEntryRequest="";systemEntryResult(opened);
            if(!opened)wallpapers.resume();
        });
    }

    @Override public void onWindowFocusChanged(boolean focused){
        if(waitingForUnlock){super.onWindowFocusChanged(focused);return;}
        long started=SystemClock.uptimeMillis();
        if(focused&&resumed&&!homeFocusSeen){
            homeFocusSeen=true;
            if(homeResumeOperation!=null)homeResumeOperation.stage("WINDOW_FOCUS_CALLBACK");
            if(started-homeResumeStarted>=500){
                homeResumeEvent("home.transition.slow.result","stage=window_focus_callback evidence=window_callback_not_system_window_timing");
                DiagnosticProbe.slowResource("home_slow_focus");
            }
        }
        homeResumeEvent("home.window_focus","focused="+focused+" "+InputDiagnostics.focus(this));
        try{
            super.onWindowFocusChanged(focused);if(!focused)cancelInputWork(true);if(wallpapers!=null)wallpapers.windowFocusChanged(focused);if(screensaver!=null){if(!focused)cancelConfirm();screensaver.availabilityChanged();}systemEntryLifecycle("window_focus="+focused);if(focused && resumed && systemEntryDispatched)systemEntryPending="";if(focused && resumed){runPendingHomeAction();new HomeProxy(this).visible(this);if(!isFinishing()){HomeGuardRecovery.visible(this);launchBootApp();}}
        }finally{homeResumeEvent("home.window_focus.end","focused="+focused+" callbackMs="+(SystemClock.uptimeMillis()-started));}
        if(focused&&resumed){updateReminderInteraction();finishHomeEvidence();main.post(this::openMaintenanceInput);}main.post(this::runPendingHomeAction);main.post(this::returnToQuick);
    }

    void checkBootApp(){getWindow().getDecorView().post(this::launchBootApp);}

    private void launchBootApp(){
        if(SystemEntryMaintenance.pending()||SourcePairing.existing()!=null&&SourcePairing.existing().pending())return;
        BootAppStore store=new BootAppStore(this);if(!store.enabled())return;
        long started=SystemClock.uptimeMillis();
        if(isFinishing() || !resumed || !hasWindowFocus() || !HomeGuardState.canShow(this))return;
        String pkg=store.consume();
        DiagnosticLog.info("home.autostart.check","elapsedMs="+(SystemClock.uptimeMillis()-started)+" consumed="+!pkg.isEmpty());
        if(pkg.isEmpty())return;
        if(BootAppStore.HDMI.equals(pkg)){
            openSavedHdmi(true);
            return;
        }
        launchStartupApp(pkg);
    }
    private void launchStartupApp(String pkg){
        BootAppStore store=new BootAppStore(this);
        String attempt=DiagnosticOperation.nextId(SystemClock.elapsedRealtime());
        leaveHome(()->{try{
            Intent intent=store.launchIntent(pkg);
            if(intent==null){Log.i(BootAppStore.TAG,"target unavailable");wallpapers.resume();OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,tr("自启应用不可用，请在应用管理中重新设置","Startup app unavailable"));return;}
            if(ChildBlockActivity.gate(this,pkg)){Log.i(BootAppStore.TAG,"blocked by child mode");return;}
            if(new HomeGuardState(this).protects(pkg))HomeGuardService.allowOriginal();
            String evidence="attempt="+attempt+" source=boot_app target="+pkg+" "+DiagnosticLog.intent(intent);
            DiagnosticLog.info("app.autostart.request",evidence);
            startActivity(intent);store.dispatched(pkg,pkg+"/");
            Log.i(BootAppStore.TAG,"launch requested");DiagnosticLog.info("app.autostart.dispatched",evidence+" result=dispatched_unverified");
        }catch(RuntimeException e){Log.w(BootAppStore.TAG,"launch rejected");DiagnosticLog.error("app.autostart.failed","attempt="+attempt+" source=boot_app target="+pkg,e);wallpapers.resume();OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,tr("自启应用暂时无法打开","Unable to open startup app"));}});
    }

    private void watchTv(){
        openSource();
    }
    private void openSource(){
        BootAppStore store=new BootAppStore(this);
        if(!store.watchTarget().isEmpty()){
            HomeGuardService.bootSourceUserAction(this,"saved_hdmi");openSavedHdmi(false);return;
        }
        JSONObject entry=new SourcePairingStore(this).entry();
        if(entry==null){openHomeTools(false,"source");return;}
        String saved=entry.toString();Context app=getApplicationContext();
        HomeGuardService.bootSourceUserAction(this);
        leaveSystemEntry("input.paired",()->inputWork.submit(()->{
            SourceSearch.Candidate candidate=SourcePairing.preferred(app,entry);return candidate==null?null:candidate.prepare(app,null);
        },(launch,failure)->{
            JSONObject current=new SourcePairingStore(this).entry();
            if(current==null||!saved.equals(current.toString())||!new BootAppStore(this).watchTarget().isEmpty()){systemEntryResult(false);wallpapers.resume();return;}
            dispatchSavedHdmi(launch,failure,false);
        }));
    }
    private void openSavedHdmi(boolean automatic){
        BootAppStore store=new BootAppStore(HomeActivity.this);
        if(automatic&&!store.tested())return;
        String saved=store.watchTarget();int generation=hdmiLaunchGeneration;
        leaveSystemEntry("input.saved",()->{
            automaticInputPending=automatic;Context app=getApplicationContext();
            inputWork.submit(()->{HdmiTarget target=HdmiTarget.decode(saved);return target==null?null:target.prepare(app);},(launch,failure)->{
                if(!inputActive())return;
                BootAppStore current=new BootAppStore(HomeActivity.this);
                if(!saved.equals(current.watchTarget())||automatic&&(generation!=hdmiLaunchGeneration||!current.hdmiBoot()||!current.tested())){automaticInputPending=false;systemEntryResult(false,automatic);wallpapers.resume();return;}
                dispatchSavedHdmi(launch,failure,automatic);
            });
        });
    }
    private void dispatchSavedHdmi(SavedInputLaunch launch,Exception failure,boolean automatic){
        if(!inputActive())return;
        if(failure!=null)DiagnosticLog.error("input.prepare.failed",failure);
        if(launch==null||!launch.current(this)){finishSavedHdmi(false,null,automatic);return;}
        if(launch.blocking()){
            HomeGuardService.savedInputSelected(this,automatic);Context app=getApplicationContext();
            inputWork.submit(()->launch.dispatch(app),(opened,error)->{if(inputActive())finishSavedHdmi(Boolean.TRUE.equals(opened),launch,automatic);});
        }else finishSavedHdmi(launch.dispatch(this),launch,automatic);
    }
    private void finishSavedHdmi(boolean opened,SavedInputLaunch launch,boolean automatic){
        if(!maintenanceInputRequest.isEmpty()){DeviceInputProfile.dispatched(maintenanceInputRequest,opened);maintenanceInputRequest="";}
        automaticInputPending=false;systemEntryResult(opened,automatic);
        if(opened){if(automatic)new BootAppStore(this).dispatched(BootAppStore.HDMI,launch.foregroundComponent());return;}
        HomeGuardService.cancelInputTransition();wallpapers.resume();
        showInputIssue(tr("信号源切换未完成","Input switch incomplete"),tr("信号源暂不可用，请重新检测或使用电视遥控器信号源键。","Input unavailable; detect again or use the TV remote input key."));
    }

    @Override protected void onPause() {
        clearHomeEvidence();
        if(operationFeedback!=null)operationFeedback.hide();
        animateHomeReturn=false;
        if(quickControls!=null)quickControls.pause();
        cancelInputWork(true);
        if(waitingForUnlock){super.onPause();return;}
        cancelConfirm();screensaver.pause();homeHeader.clockActive(false);
        cancelFocusRestore("pause");
        root.removeCallbacks(checkHomeUpdates);main.removeCallbacks(homeTransitionWatch);main.removeCallbacks(showUpdateReminder);UpdateRepository.get(this).remove(homeUpdateListener);homeReadyDrawn=false;
        if(!HomeGuardState.canShow(this))new HomeProxy(this).defer(getIntent());
        homePausedAt=SystemClock.uptimeMillis();homeResumeEvent("home.pause","");homeDrawPending=false;
        systemEntryLifecycle("pause");
        ++hdmiLaunchGeneration;new BootAppStore(this).cancelHdmiBoot();BootAppLifecycle.left(this);
        if(dockPress!=null)dockPress.cancel();updateDock(false);
        HomeGuardRecovery.leftHome(this);
        resumed=false;cancelUnshownLegacyTrial();main.removeCallbacks(confirmHold);heldPackage="";cancelMove();if(scrollAnimation!=null)scrollAnimation.cancel();list.stopScroll();saveFocus();wallpapers.pause();weather.pause();
        unregisterReceiver(timeReceiver);
        if(Build.VERSION.SDK_INT>=24)stopNetworkCallback.run();
        else unregisterReceiver(legacyNetworkReceiver);
        if(inputPopup!=null) inputPopup.closeQuietly();
        super.onPause();
    }

    @Override protected void onDestroy(){
        legacyIconsDestroyed=true;
        clearHomeEvidence();
        OperationFeedback.clear(this);main.removeCallbacks(homeTransitionWatch);
        if(liveHome.get()==this)liveHome.clear();inputWork.close();
        if(waitingForUnlock){unregisterReceiver(unlockReceiver);loader.shutdownNow();super.onDestroy();return;}
        if(quickControls!=null){quickControls.destroy();quickControls=null;}
        PhoneSettingsSession.get(this).forgetConfiguration(phoneConfiguration);cancelFocusRestore("destroy");
        BootAppLifecycle.destroyed(this);unregisterReceiver(packageReceiver);
        if(legacyPendingResult!=null){main.removeCallbacks(legacyPendingResult);legacyPendingResult.run();}
        cancelUnshownLegacyTrial();if(legacyIcons==null)loader.shutdownNow();else loader.shutdown();screensaver.destroy();wallpapers.destroy();weather.destroy();super.onDestroy();
    }
    @Override protected void onStop(){
        if(quickSettingsOpening){quickSettingsOpening=false;closeQuickControls(false);}
        cancelInputWork();
        if(!waitingForUnlock){homeResumeEvent("home.stop","");wallpapers.hidden();}
        super.onStop();
    }
    @Override protected void onNewIntent(Intent intent){
        boolean home=isHomeIntent(intent);
        AutoDiagnosticOperation op=home&&!waitingForUnlock?AutoDiagnosticOperation.begin("home_intent",500):null;
        DiagnosticProbe.StallWatch watch=op==null?null:DiagnosticProbe.watchMain("home_intent",op);
        try{
            super.onNewIntent(intent);setIntent(intent);InputDiagnostics.home(this,home,"new_intent");
            handleNewIntent(intent,home);
        }finally{if(watch!=null)watch.end();if(op!=null)op.finish();}
    }

    private void handleNewIntent(Intent intent,boolean home){
        if(waitingForUnlock){resumeAfterUnlock();return;}
        cancelInputWork();if(home)HomeGuardService.cancelInputTransition();
        if(!new HomeProxy(this).allowed(intent)){new HomeProxy(this).original(this);finish();return;}
        getWindow().getDecorView().post(()->{if(resumed && hasWindowFocus())new HomeProxy(this).visible(this);});
        homeResumeEvent("home.new_intent","home="+home);
        cancelConfirm();
        // Activity resume already restores the ordinary HOME background. An active saver must still exit.
        if(!home||resumed&&screensaver.showing()){
            screensaver.pause();if(resumed)wallpapers.resume();screensaver.availabilityChanged();
        }
        if(home&&!resumed)pendingHomeFocusReset=true;
        else if(home){closeQuickControls(false);resetHomeFocus(true);}
        else if(ready&&resumed)focusCurrent(false);
        main.post(this::openMaintenanceInput);main.post(this::runPendingHomeAction);main.post(this::returnToQuick);
    }

    private boolean isHomeIntent(Intent intent){return (Intent.ACTION_MAIN.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) && intent.hasCategory(Intent.CATEGORY_HOME);}

    private void resetHomeFocus(boolean applyFocus){
        cancelMove();
        if(inputPopup!=null && inputPopup.isShowing())inputPopup.closeQuietly();
        animateHomeReturn=ready&&root.isShown()&&root.getWindowVisibility()==View.VISIBLE&&list.canScrollVertically(-1);
        row=0;column=Math.max(0,Math.min(restoreColumn,maxColumn(0)));
        // Do not let an in-flight catalog refresh restore the old grid application.
        pendingAppFocus="";activePackage="";saveFocus();if(applyFocus)focusCurrent(false);
    }

    private static class App {
        String pkg,label,artworkKey,coverKey; ComponentName component; Bitmap banner; int color; boolean libraryCover,trialArtwork,trialReported;
    }

    private HomeCatalogRefresh.Input catalogInput(){
        return new HomeCatalogRefresh.Input(new DockStore(this).load(),new HomeOrderStore(this).signature(),
            new ChildModeStore(this).revision(),AppCoverStore.get(this).revision(),packageRevision,homeAppsVisible,new HomeVisibilityStore(this).signature()+new AppNameStore(this).signature()+new BootAppStore(this).watchRevision()+":"+desktopSettings.appListSignature()
                +(legacyIcons==null?"":":legacy-art:"+(legacyArtworkStarted?legacyIcons.signature():"stock_start")));
    }

    private void loadApps(String reason) {
        final LegacyIconRecovery recovery=legacyIcons;
        if(LegacyIconRecovery.supported()&&recovery==null)return;
        final boolean allowLegacyArtwork=legacyArtworkStarted;
        final HomeCatalogRefresh.Input requested=catalogInput();
        if(!catalogRefresh.start(requested))return;
        final int sequence=++catalogSequence;
        final long started=SystemClock.uptimeMillis();
        DiagnosticLog.info("home.catalog.begin","request="+sequence+" reason="+reason);
        final List<String> savedPinned=requested.pinned;
        loader.execute(()->{
            try {
            final long workerStarted=SystemClock.uptimeMillis();
            PackageManager pm=getPackageManager();
            ArrayList<HomeAppCatalog.Entry> catalog=new HomeAppCatalog(this).load(savedPinned);
            final List<String> pinned=HomeAppCatalog.pinnedIds(catalog,savedPinned);
            final long catalogLoadedAt=SystemClock.uptimeMillis();
            ArrayList<App> found=new ArrayList<>();
            HashMap<String,App> freshCache=new HashMap<>();
            for(HomeAppCatalog.Entry entry:catalog) {
                ResolveInfo info=entry.info;if(info==null)continue;
                String key=new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString();
                try { key += ":" + pm.getPackageInfo(info.activityInfo.packageName,0).lastUpdateTime; }
                catch (PackageManager.NameNotFoundException e) { continue; }
                String pkg=info.activityInfo.packageName;
                AppCoverStore covers=AppCoverStore.get(this);
                String coverKey=covers.key(pkg);
                boolean custom=recovery!=null&&allowLegacyArtwork&&recovery.allowed(pkg,coverKey);
                boolean trial=custom&&recovery.trial(pkg,coverKey);
                key+=":cover:"+coverKey+(recovery==null?"":trial?":trial":custom?":confirmed":":stock");
                App cached=artworkCache.get(key);
                if(cached!=null){cached.label=entry.label;found.add(cached);freshCache.put(key,cached);continue;}
                App app=new App();app.pkg=pkg;app.label=entry.label;app.artworkKey=key;app.coverKey=coverKey;
                app.component=new ComponentName(pkg,info.activityInfo.name);
                if(recovery==null){
                    app.libraryCover=covers.contain(pkg);app.banner=AppArtwork.load(this,info);
                }else{
                    app.banner=custom?AppArtwork.custom(this,pkg,coverKey):null;
                    if(app.banner==null){
                        if(trial)recovery.completed(pkg,coverKey,false);
                        app.banner=AppArtwork.load(this,info,false);
                    }else{
                        app.libraryCover=covers.contain(pkg);app.trialArtwork=trial;
                    }
                }
                app.color=sampleColor(app.banner);found.add(app);freshCache.put(key,app);
            }
            artworkCache.clear();artworkCache.putAll(freshCache);
            if(desktopSettings.showWatchTv()){
                App watch=new App();watch.pkg=HomeAppCatalog.WATCH_TV;watch.label=tr("看电视","Watch TV");watch.banner=HomeEntryArtwork.render(this,watch.label,watch.pkg);watch.color=HomeEntryArtwork.WATCH_TV_COLOR;found.add(0,watch);
            }
            // Keep the complete saved order; visibility is projected only when drawing the grid.
            ArrayList<String> defaults=appIds(found);
            ArrayList<String> gridOrder=new HomeOrderStore(this).order(defaults);
            final long preparedAt=SystemClock.uptimeMillis();
            main.post(()->{
                if(UiCompat.destroyed(this))return;
                long applyStarted=SystemClock.uptimeMillis();
                if(!catalogRefresh.finish(catalogInput())){
                    DiagnosticLog.info("home.catalog.stale","request="+sequence+" elapsedMs="+(SystemClock.uptimeMillis()-started));
                    if(resumed||root.getWindowVisibility()==View.VISIBLE)loadApps("changed_during_load");return;
                }
                ArrayList<App> nextDock=new ArrayList<>(),nextApps=new ArrayList<>();
                fullAppOrder.clear();fullAppOrder.addAll(gridOrder);
                for(String pkg:pinned)for(App app:found)if(pkg.equals(app.pkg)&&nextDock.size()<desktopSettings.appearance("dock-limit"))nextDock.add(app);
                HashMap<String,App> byId=new HashMap<>();for(App app:found)byId.put(app.pkg,app);
                for(String id:HomeAppCatalog.gridIds(gridOrder,pinned,desktopSettings.appearance("dock-limit"),homeAppsVisible,desktopSettings.hideHomeAppsOnSecondScreen()))nextApps.add(byId.get(id));
                previewCatalog=Collections.unmodifiableMap(new HashMap<>(byId));previewCatalogRevision=packageRevision;
                // Keep an actionable empty state on HOME, without saving it as a pinned app.
                if(homeAppsVisible&&nextDock.isEmpty()){
                    App add=new App();add.pkg=ADD_DOCK_ENTRY;add.label=tr("添加常用应用","Add favorite apps");
                    add.banner=HomeEntryArtwork.addFavorite(add.label);add.color=UiPalette.FAINT;nextDock.add(add);
                }
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
                boolean appFocusPending=!pendingAppFocus.isEmpty();
                if(appFocusPending)activePackage=pendingAppFocus;
                if(row>=0){
                    // Removed favorites still exist in the grid; keep the original slot instead of following them.
                    if(!keepAppActionPositionUntilRefresh&&!activePackage.isEmpty()) {
                        boolean foundInDock=false;
                        if(row==0&&homeAppsVisible)for(int n=0;n<dock.size();n++)if(activePackage.equals(dock.get(n).pkg)){column=n;foundInDock=true;break;}
                        if(!foundInDock)for(int n=0;n<apps.size();n++)if(activePackage.equals(apps.get(n).pkg)){row=1+n/appColumns;column=n%appColumns;break;}
                    }
                    row=Math.max(0,Math.min(row,gridRows()));column=Math.min(column,maxColumn(row));
                }
                pendingAppFocus="";keepAppActionPositionUntilRefresh=false;
                boolean first=!ready;ready=true;
                appAdapter.notifyItemChanged(1);
                if(screensaver!=null)screensaver.availabilityChanged();
                if(resumed&&(first||dockChanged||gridChanged||appFocusPending))focusCurrent(false);
                if(first)main.post(this::returnToQuick);
                beginPendingMove();
                long appliedAt=SystemClock.uptimeMillis();
                DiagnosticLog.info("home.catalog.applied","request="+sequence+" elapsedMs="+(appliedAt-started)
                    +" workerQueueMs="+(workerStarted-started)+" catalogMs="+(catalogLoadedAt-workerStarted)+" prepareMs="+(preparedAt-catalogLoadedAt)
                    +" queueMs="+(applyStarted-preparedAt)+" applyMs="+(appliedAt-applyStarted)+" dockChanged="+dockChanged+" gridChanged="+gridChanged);
                Log.i(TAG,"Loaded dock="+dock.size()+" apps="+apps.size());
            });
            } catch (RuntimeException e) {
                main.post(()->{catalogRefresh.failed();pendingAppFocus="";if(resumed&&ready)focusCurrent(false);Log.w(TAG,"App refresh failed; retaining current tiles",e);DiagnosticLog.error("app.catalog.failed",e);});
            }
        });
    }

    private void offerLegacyArtwork(Tile tile,App app){
        if(legacyIcons==null||drawingLegacySnapshot||!ready||!resumed||!hasWindowFocus()||!tile.isShown()||tile.getWidth()==0||!movingPackage.isEmpty())return;
        if(!legacyArtworkStarted){legacyArtworkStarted=true;main.post(()->loadApps("legacy_stock_drawn"));return;}
        if(!legacyTrialPackage.isEmpty()||app.coverKey==null||!legacyIcons.eligible(app.pkg,app.coverKey)||legacyDeferredTrials.contains(app.coverKey))return;
        if(!tile.getGlobalVisibleRect(tile.legacyVisibleBounds)||tile.legacyVisibleBounds.isEmpty())return;
        legacyTrialPackage=app.pkg;legacyTrialKey=app.coverKey;int request=++legacyTrialGeneration;LegacyIconRecovery recovery=legacyIcons;
        loader.execute(()->{
            boolean offered=recovery.offer(app.pkg,app.coverKey);
            main.post(()->{
                if(legacyIconsDestroyed||UiCompat.destroyed(this)||request!=legacyTrialGeneration)return;
                if(!offered){clearLegacyTrial();return;}
                if(!resumed||!hasWindowFocus()||tile.app!=app||!tile.isShown()){
                    legacyDeferredTrials.add(app.coverKey);cancelUnshownLegacyTrial();return;
                }
                loadApps("legacy_icon_trial");
                main.postDelayed(()->{
                    if(!legacyIconsDestroyed&&!UiCompat.destroyed(this)&&request==legacyTrialGeneration&&!legacyTrialRetiring){
                        legacyDeferredTrials.add(app.coverKey);cancelUnshownLegacyTrial();
                    }
                },1800);
            });
        });
    }
    private void finishLegacyTrial(App app,boolean success){
        if(app.trialReported||!app.trialArtwork||legacyTrialRetiring)return;
        app.trialReported=true;legacyTrialRetiring=true;
        LegacyIconRecovery recovery=legacyIcons;
        legacyPendingResult=()->{
            legacyPendingResult=null;
            loader.execute(()->{boolean changed=recovery.completed(app.pkg,app.coverKey,success);main.post(()->{if(!legacyIconsDestroyed&&!UiCompat.destroyed(this)){clearLegacyTrial();if(changed&&resumed)loadApps("legacy_icon_result");}});});
        };
        // Preserve the pending marker through the return from this drawing turn.
        if(success)main.postDelayed(legacyPendingResult,16);else legacyPendingResult.run();
    }
    private void cancelUnshownLegacyTrial(){
        if(legacyTrialPackage.isEmpty()||legacyTrialRetiring)return;
        legacyTrialRetiring=true;
        LegacyIconRecovery recovery=legacyIcons;String pkg=legacyTrialPackage,key=legacyTrialKey;
        loader.execute(()->{boolean changed=recovery.cancelUnshown(pkg,key);main.post(()->{if(!legacyIconsDestroyed&&!UiCompat.destroyed(this)){clearLegacyTrial();if(changed&&resumed)loadApps("legacy_icon_unshown");}});});
    }
    private void clearLegacyTrial(){++legacyTrialGeneration;legacyTrialPackage="";legacyTrialKey="";legacyTrialRetiring=false;}

    private static boolean sameApp(App a,App b){return a.pkg.equals(b.pkg)&&a.label.equals(b.label)&&Objects.equals(a.component,b.component)&&Objects.equals(a.artworkKey,b.artworkKey);}
    private static boolean sameApps(List<App> a,List<App> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!sameApp(a.get(i),b.get(i)))return false;return true;}

    static int sampleColor(Bitmap bitmap) {
        double best=0;int chosen=UiPalette.FAINT;
        float[] hsv=new float[3];
        for(int y=10;y<bitmap.getHeight();y+=12)for(int x=10;x<bitmap.getWidth();x+=12){
            int color=bitmap.getPixel(x,y);Color.colorToHSV(color,hsv);
            double score=hsv[1]*hsv[2];
            if(hsv[2]>.15 && hsv[2]<.98 && score>best){best=score;chosen=color;}
        }
        return chosen;
    }

    private int gridRows(){return (apps.size()+appColumns-1)/appColumns;}
    private int maxColumn(int r){if(r<0)return visiblePills.size()-1;if(r==0)return homeAppsVisible?Math.max(0,dock.size()-1):0;return Math.min(appColumns-1,apps.size()-(r-1)*appColumns-1);}

    private void saveFocus(){if(row>=0)getPreferences(0).edit().putInt("row",row).putInt("column",column).putString("package",activePackage).apply();}

    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("quick-return",quickReturn);state.putString("quick-pending",phoneActionAuthority==null?pendingHomeAction:"");state.putString("button-pending-focus",pendingButtonFocus);state.putString("app-pending-focus",pendingAppFocus);state.putBoolean("quick-return-dock",pendingDockFocus);if(quickControls!=null&&!quickSettingsOpening){Bundle quick=new Bundle();quickControls.save(quick);state.putBundle("inline-quick-controls",quick);}else if(restoredQuickControls!=null)state.putBundle("inline-quick-controls",restoredQuickControls);}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==HomeToolsActivity.REQUEST&&result==RESULT_OK&&data!=null){pendingHomeAction=data.getStringExtra("home-action");pendingButtonFocus=data.getStringExtra("focus-button");if(pendingButtonFocus==null)pendingButtonFocus="";pendingDockFocus=data.getBooleanExtra("focus-dock",false);if(data.getBooleanExtra("quick-origin",false)&&pendingHomeAction!=null)quickReturn=pendingHomeAction;main.post(this::runPendingHomeAction);main.post(this::returnToQuick);}
        if(request==HomeAppActionsActivity.REQUEST&&result==RESULT_OK&&data!=null){
            pendingFeedback=data.getStringExtra("feedback");if(pendingFeedback==null)pendingFeedback="";pendingFeedbackSuccess=data.getBooleanExtra("feedback-success",true);
            if("move".equals(data.getStringExtra("action"))){pendingMove=data.getStringExtra("package");pendingMoveDock=data.getBooleanExtra("dock",false);}
            else{activePackage=data.getStringExtra("package");if(activePackage==null)activePackage="";pendingAppFocus=activePackage;keepAppActionPositionUntilRefresh=activePackage.isEmpty();++packageRevision;}
        }
        if(request==WallpaperFilesActivity.REQUEST&&result==Activity.RESULT_OK&&data!=null&&wallpapers!=null)getWindow().getDecorView().post(()->{if(!isFinishing()&&!UiCompat.destroyed(this))wallpapers.imported(data.getStringExtra("wallpaper"));});
    }
    public void handleBack(){
        cancelInputWork();
        cancelConfirm();
        if(quickControls!=null){quickControls.back();return;}
        if(screensaver!=null&&screensaver.showing()){screensaver.key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));return;}
        if(moveSaving)return;
        if(!movingPackage.isEmpty()){cancelMove();KeyFeedback.returned(this);return;}
        if(inputPopup!=null&&inputPopup.isShowing()){inputPopup.closeQuietly();KeyFeedback.returned(this);}
        else if(row>0){row=0;column=Math.min(restoreColumn,maxColumn(0));activePackage="";focusCurrent(true);KeyFeedback.returned(this);}
    }
    private void openAppActions(App app){
        if(app==null||moveSaving||!pendingAppFocus.isEmpty())return;saveFocus();KeySounds.play(this,KeySoundPolicy.CONFIRM);
        int x=getResources().getDisplayMetrics().widthPixels/2,y=px(280);
        int[] location=new int[2];
        Rect cardBounds=new Rect();
        if(row==0&&dockBand!=null&&column<dockTiles.size()){
            // Dock focus can update on the next frame. Use the target card's layout.
            DockLayout geometry=dockGeometry(column);
            dockBand.getLocationOnScreen(location);
            x=location[0]+px(geometry.x[column]-CONTENT_MARGIN+geometry.cardWidth[column]/2f);
            y=location[1]+px(geometry.y[column]);
        }else if(row>0){
            RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(2+(row-1)*appColumns+column);
            Tile tile=holder!=null&&holder.itemView instanceof Tile?(Tile)holder.itemView:selected;
            if(tile!=null&&tile.showLabel&&tile.app!=null&&tile.app.pkg.equals(app.pkg)
                    &&androidx.core.view.ViewCompat.isAttachedToWindow(tile)&&tile.getGlobalVisibleRect(cardBounds)){
                tile.getRootView().getLocationOnScreen(location);
                cardBounds.offset(location[0],location[1]);
            }else cardBounds.setEmpty();
        }
        Intent intent=new Intent(this,HomeAppActionsActivity.class).putExtra("package",app.pkg).putExtra("dock",row==0)
            .putExtra("add",ADD_DOCK_ENTRY.equals(app.pkg))
            .putExtra("anchor-x",x).putExtra("anchor-y",y);
        if(!cardBounds.isEmpty())intent.putExtra("anchor-bounds",cardBounds);
        addSettingsBackdrop(intent);
        leaveHome(()->startActivityForResult(intent,HomeAppActionsActivity.REQUEST));
    }
    static void coverToolHeader(boolean covered){HomeActivity home=liveHome.get();if(home!=null&&home.homeHeader!=null)home.homeHeader.buttons.setAlpha(covered?0:1);}
    private Intent settingsIntent(Class<?> page){return addSettingsBackdrop(new Intent(this,page).putExtra("home-network-icon",networkIcon));}
    /** Reuse the rendered catalog only while it still describes the current desktop. */
    static HomePreviewCache.Scene previewScene(HomePreviewCache.Key key,String pkg,String size,String shape){
        HomeActivity home=liveHome.get();
        if(home==null||home.waitingForUnlock||!home.ready||UiCompat.destroyed(home)||!home.catalogRefresh.current(home.catalogInput()))return null;
        HomePreviewCache.Key current=HomePreviewCache.Key.read(home,home.chinese,HomePreviewCache.get(home).packages());
        if(!key.apps.equals(current.apps)||!key.configuration.equals(current.configuration))return null;
        Map<String,App> available=home.previewCatalog;
        List<Bitmap> art=new ArrayList<>(),gridArt=new ArrayList<>();List<String> names=new ArrayList<>(),gridNames=new ArrayList<>(),ids=new ArrayList<>(),gridIds=new ArrayList<>();
        for(String id:key.apps.pinned){App app=available.get(id);if(app!=null){if(app.trialArtwork)return null;art.add(app.banner);names.add(app.label);ids.add(id);}}
        for(String id:HomePreviewView.previewGridIds(home.appIds(home.apps),pkg,size,shape)){App app=available.get(id);if(app.trialArtwork)return null;gridArt.add(app.banner);gridNames.add(app.label);gridIds.add(id);}
        return new HomePreviewCache.Scene(key,art,names,gridArt,gridNames,ids,gridIds);
    }
    static Bitmap previewSnapshot(){
        HomeActivity home=liveHome.get();
        if(home==null||!home.ready||home.waitingForUnlock||UiCompat.destroyed(home)||home.root.getWidth()==0||home.root.getHeight()==0)return null;
        Bitmap image=Bitmap.createBitmap(480,Math.max(1,Math.round(480f*home.root.getHeight()/home.root.getWidth())),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.scale(image.getWidth()/(float)home.root.getWidth(),image.getHeight()/(float)home.root.getHeight());
        home.wallpapers.drawDockBackground(canvas,new Paint(Paint.FILTER_BITMAP_FLAG));home.drawSnapshotView(home.list,canvas);return image;
    }
    static void seedPreviewBackground(WallpaperController target){HomeActivity home=liveHome.get();if(home!=null&&!home.waitingForUnlock&&!UiCompat.destroyed(home))target.seedPreview(home.wallpapers);}
    private Intent addSettingsBackdrop(Intent intent){
        if(!GlassStyle.get(this).rendering())return intent;
        // Reuse bounded prepared wallpaper pixels without copying the native 4K output.
        Bitmap backdrop=Bitmap.createBitmap(320,Math.max(1,Math.round(320f*root.getHeight()/root.getWidth())),Bitmap.Config.ARGB_8888);
        Canvas backdropCanvas=new Canvas(backdrop);backdropCanvas.scale(backdrop.getWidth()/(float)root.getWidth(),backdrop.getHeight()/(float)root.getHeight());
        wallpapers.drawDockBackground(backdropCanvas,new Paint(Paint.FILTER_BITMAP_FLAG));intent.putExtra("backdrop-key",HomePreviewCache.get(this).keepSettingsBackdrop(backdrop));
        return intent;
    }
    private ArrayList<String> appIds(List<App> values){ArrayList<String> ids=new ArrayList<>();for(App app:values)ids.add(app.pkg);return ids;}
    private void beginPendingMove(){
        if(pendingMove==null||pendingMove.isEmpty()||!resumed||!ready||catalogRefresh.loading())return;
        List<App> values=pendingMoveDock?dock:apps;int index=-1;for(int i=0;i<values.size();i++)if(pendingMove.equals(values.get(i).pkg))index=i;
        if(index>=0){
            moveDock=pendingMoveDock;movingPackage=pendingMove;beforeMove=new ArrayList<>(values);moveDefaults=appIds(values);moveInput=catalogInput();activePackage=movingPackage;
            cancelConfirm();screensaver.availabilityChanged();row=moveDock?0:1+index/appColumns;column=moveDock?index:index%appColumns;appAdapter.notifyItemChanged(moveDock?0:1);focusCurrent(false);
            OperationFeedback.get(this).show(OperationFeedback.Kind.INFO,tr("方向键移动  确定放下  返回取消","Move with arrows  OK to save  Back to cancel"));
        }pendingMove="";
    }
    private void moveSelected(int dx,int dy){
        ArrayList<App> values=moveDock?dock:apps;ArrayList<String> ids=appIds(values);int from=ids.indexOf(movingPackage);
        int target=moveDock?Math.max(0,Math.min(ids.size()-1,from+dx)):HomeOrderStore.moveTarget(from,ids.size(),appColumns,dx,dy);
        if(from<0||target==from||moveDock&&dy!=0)return;
        HomeOrderStore.move(values,from,target);activePackage=movingPackage;row=moveDock?0:1+target/appColumns;column=moveDock?target:target%appColumns;
        if(moveDock)appAdapter.notifyItemChanged(0);else appAdapter.notifyItemRangeChanged(Math.min(from,target)+2,Math.abs(from-target)+1);
        focusCurrent(false);KeyFeedback.moved(this);
    }
    private void cancelMove(){
        if(movingPackage.isEmpty()||moveSaving)return;
        String id=movingPackage;ArrayList<App> values=moveDock?dock:apps;values.clear();values.addAll(beforeMove);int index=appIds(values).indexOf(id);
        movingPackage="";beforeMove=null;screensaver.availabilityChanged();row=moveDock?0:1+Math.max(0,index)/appColumns;column=moveDock?Math.max(0,index):Math.max(0,index)%appColumns;activePackage=id;
        if(appAdapter!=null){appAdapter.notifyItemChanged(moveDock?0:1);if(!moveDock)appAdapter.notifyItemRangeChanged(2,apps.size());focusCurrent(false);}
    }
    private void saveMove(){
        if(movingPackage.isEmpty()||moveSaving)return;
        if(!moveInput.equals(catalogInput())){cancelMove();loadApps("move_stale");return;}
        final boolean inDock=moveDock;final ArrayList<String> ids=appIds(inDock?dock:apps),defaults=new ArrayList<>(inDock?moveDefaults:fullAppOrder);moveSaving=true;
        loader.execute(()->{
            boolean saved;
            try{saved=moveInput.equals(catalogInput())&&(inDock?new DockStore(this).saveOrder(HomeOrderStore.merge(new DockStore(this).load(),ids)):new HomeOrderStore(this).save(HomeOrderStore.merge(defaults,ids),defaults,false));}catch(RuntimeException e){saved=false;}
            final boolean success=saved;main.post(()->{moveSaving=false;if(UiCompat.destroyed(this))return;if(success){movingPackage="";beforeMove=null;}else cancelMove();screensaver.availabilityChanged();
                appAdapter.notifyItemChanged(inDock?0:1);if(!inDock)appAdapter.notifyItemRangeChanged(2,apps.size());focusCurrent(false);loadApps("move_saved");OperationFeedback.get(this).show(success?OperationFeedback.Kind.SUCCESS:OperationFeedback.Kind.ERROR,success?"顺序已自动保存":"保存失败，已恢复原顺序");});
        });
    }
    // Public Activity override; AndroidX core marks its inherited bridge class library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(waitingForUnlock)return event.getKeyCode()==KeyEvent.KEYCODE_BACK||super.dispatchKeyEvent(event);
        return InputDiagnostics.dispatch(this,event,"home_window",()->dispatchObservedKey(event));
    }
    private boolean dispatchObservedKey(KeyEvent event){
        if(!waitingForUnlock)updateReminderInteraction();
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
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN && HomeGuardService.navigationKey(key)){if(automaticInputPending)cancelInputWork();++hdmiLaunchGeneration;new BootAppStore(this).cancelHdmiBoot();new BootAppStore(this).userAction("desktop_input");HomeGuardService.bootSourceUserAction(this);}
        int kind=key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN?1
            :key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER?2
            :key==KeyEvent.KEYCODE_VOLUME_UP || key==KeyEvent.KEYCODE_VOLUME_DOWN || key==KeyEvent.KEYCODE_VOLUME_MUTE?4:0;
        if(event.getAction()==KeyEvent.ACTION_DOWN&&wallpapers!=null&&(kind==1||kind==2||key==KeyEvent.KEYCODE_BACK))wallpapers.interaction();
        if(kind!=0 && event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && (observedInputKinds&kind)==0){
            observedInputKinds|=kind;long received=SystemClock.uptimeMillis();
            DiagnosticLog.info("home.input.first","instance="+homeInstance+" resume="+homeResumeSequence+" kind="+kind+" key="+key+" eventUptimeMs="+event.getEventTime()+" receivedUptimeMs="+received
                +" ageMs="+Math.max(0,received-event.getEventTime())+" ready="+ready+" resumed="+resumed+" windowFocus="+hasWindowFocus());
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_HOME))cancelInputWork();
        if(event.getAction()==KeyEvent.ACTION_DOWN && hasWindowFocus() && systemEntryDispatched && !systemEntryPending.isEmpty()){systemEntryLifecycle("next_home_key");systemEntryPending="";}
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(confirm && (!ready || row<0) && event.getRepeatCount()==0)
            DiagnosticLog.info("home.action.key","entry="+topEntry()+" key="+key+" phase="+event.getAction()+" ready="+ready+" popup="+(inputPopup!=null && inputPopup.isShowing()));
        if(inputPopup!=null && inputPopup.isShowing()){cancelConfirm();return super.dispatchKeyEvent(event);}
        if(!ready)return super.dispatchKeyEvent(event);
        if(ScreensaverController.systemKey(key))return super.dispatchKeyEvent(event);
        if(quickControls!=null){cancelConfirm();return quickControls.key(event)||super.dispatchKeyEvent(event);}
        if(!confirm)cancelConfirm();
        if(moveSaving)return true;
        if(!movingPackage.isEmpty()){
            if(confirm){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){saveMove();KeyFeedback.confirm(this);}return true;}
            if(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)moveSelected(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);return true;}
        }
        String remoteAction=new RemoteKeyMap(this).action(event);
        if("system".equals(remoteAction))return super.dispatchKeyEvent(event);
        if(remoteAction.isEmpty()&&key==KeyEvent.KEYCODE_MENU){
            if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){
                if(row<0){
                    String id=topEntry();
                    if(!"settings".equals(id))openRemoteHomeTools(true,id);
                }else{
                    App app=homeHeader.buttonIds.isEmpty()||!homeAppsVisible?null:currentApp();
                    if(app!=null&&!ADD_DOCK_ENTRY.equals(app.pkg))openAppActions(app);
                    else openRemoteHomeTools(false,"");
                }
            }
            return true;
        }
        if(remoteAction.isEmpty())remoteAction=key==KeyEvent.KEYCODE_TV_INPUT?"inputs":"";
        if(!remoteAction.isEmpty()){
            if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled()){
                switch(remoteAction){
                    case "settings":
                        openSettings(Settings.ACTION_SETTINGS);
                        break;
                    case "inputs":
                        if(inputDiscoveryPending)return true;
                        showInputs();
                        break;
                    case "desktop_settings":
                        leaveHome(()->startActivity(settingsIntent(DesktopSettingsActivity.class)));
                        break;
                    case "home":
                        HomeGuardService.cancelInputTransition();
                        startActivity(HomeController.launchIntent(this));
                        return true;
                    default:
                        return true;
                }
                KeySounds.play(this,KeySoundPolicy.CONFIRM);
            }
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            pendingAppFocus="";
            main.removeCallbacks(confirmHold);heldPackage="";
            int previousRow=row,previousTop=topColumn,previousColumn=column;
            if(row<0){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)topColumn=Math.max(0,topColumn-1);
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)topColumn=Math.min(visiblePills.size()-1,topColumn+1);
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){row=0;column=Math.min(restoreColumn,maxColumn(0));}
            }else{
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)column=row==0&&column==0?maxColumn(row):Math.max(0,column-1);
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)column=row==0&&column==maxColumn(row)?0:Math.min(maxColumn(row),column+1);
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
                if(event.getRepeatCount()==0){pressDock(true);confirmLong=false;App app=currentApp();heldPackage=row>=0&&app!=null?app.pkg:row<0?"button:"+topEntry():"";if(!heldPackage.isEmpty())main.postDelayed(confirmHold,600);}
                confirmGesture.down(key,event.getDownTime(),event.getRepeatCount());
                if(event.getRepeatCount()>0){if(pendingConfirm!=null)main.removeCallbacks(pendingConfirm);pendingConfirm=null;}
            }else if(event.getAction()==KeyEvent.ACTION_UP){
                main.removeCallbacks(confirmHold);heldPackage="";
                pressDock(false);if(row<0)visiblePills.get(topColumn).setPressed(false);
                if(confirmLong){confirmLong=false;cancelConfirm();return true;}
                HomeConfirmGesture.Result result=confirmGesture.up(key,event.getDownTime(),event.getEventTime(),event.isCanceled(),ViewConfiguration.getLongPressTimeout());
                if(pendingConfirm!=null)main.removeCallbacks(pendingConfirm);pendingConfirm=null;
                if(result==HomeConfirmGesture.Result.WAIT&&row<0&&"control".equals(topEntry())){cancelConfirm();visiblePills.get(topColumn).performClick();}
                else if(result==HomeConfirmGesture.Result.DOUBLE){cancelConfirm();screensaver.enter();KeyFeedback.confirm(this);}
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

    private boolean screensaverAllowed(){return quickControls==null&&ready&&resumed&&!moveSaving&&movingPackage.isEmpty()&&hasWindowFocus()&&!isFinishing()&&wallpapers!=null&&!wallpapers.pickerShowing()&&(inputPopup==null||!inputPopup.isShowing())&&!(getCurrentFocus() instanceof EditText);}
    private String confirmFocus(){App app=currentApp();return row+":"+column+":"+topColumn+":"+(app==null?"":app.pkg);}
    private void cancelConfirm(){main.removeCallbacks(confirmHold);heldPackage="";if(pendingConfirm!=null)main.removeCallbacks(pendingConfirm);pendingConfirm=null;pendingConfirmFocus="";confirmGesture.cancel();}
    private void focusCurrent(boolean animate) {
        if(quickControls!=null){quickControls.syncHeader();return;}
        if(pendingConfirm!=null&&!pendingConfirmFocus.equals(confirmFocus()))cancelConfirm();
        if(!ready)return;
        // HOME arrives between pause and resume; wait to start the existing page motion.
        if(animateHomeReturn){if(!resumed)return;animate=true;animateHomeReturn=false;}
        AppAdapter adapter=(AppAdapter)list.getAdapter();
        int gridStride=px(adapter.gridHeight())+px(APP_GAP);
        int bottom=Math.max(px(20),list.getHeight()-px(APP_TITLE_HEIGHT)-gridRows()*gridStride);
        if(list.getPaddingBottom()!=bottom)list.setPadding(px(CONTENT_MARGIN),0,px(CONTENT_MARGIN),bottom);
        if(row<0||row==0&&!homeAppsVisible){
            if(scrollAnimation!=null)scrollAnimation.cancel();
            list.stopScroll();scrollTarget=0;layout.scrollToPositionWithOffset(0,0);
            if(row==0){activePackage="";saveFocus();}
            focusApp(animate);Log.i(TAG,"Focus top="+topColumn);return;
        }
        int position=row==0?0:2+(row-1)*appColumns;
        int offset=row==0?0:px(row==1?APP_TITLE_HEIGHT:APP_TOP);
        if(scrollAnimation!=null && scrollAnimation.isRunning() && scrollTarget!=position){scrollAnimation.cancel();list.stopScroll();}
        boolean needsScroll=row<=1;
        if(row>1){
            View first=layout.findViewByPosition(position);
            needsScroll=first==null || first.getTop()<px(APP_TOP) || first.getBottom()>list.getHeight()-px(20);
        }
        if(needsScroll && !(scrollTarget==position && scrollAnimation!=null && scrollAnimation.isRunning())){
            if(scrollAnimation!=null)scrollAnimation.cancel();
            list.stopScroll();scrollTarget=position;
            if(!animate)layout.scrollToPositionWithOffset(position,offset);
            else{
                int first=layout.findFirstVisibleItemPosition();View view=layout.findViewByPosition(first);
                if(view!=null){
                    int before=first==0?0:px(HOME_HEADER_HEIGHT)+(first==1?0:px(APP_TITLE_HEIGHT)+(first-2)/appColumns*gridStride);
                    int current=before-layout.getDecoratedTop(view);
                    int target=position==0?0:px(HOME_HEADER_HEIGHT)+px(APP_TITLE_HEIGHT)+(row-1)*gridStride-offset;
                    if(target!=current){
                        final int[] moved={0};scrollAnimation=ValueAnimator.ofInt(0,target-current);
                        scrollAnimation.setDuration(420);scrollAnimation.setInterpolator(FOCUS_MOTION);
                        wallpapers.interaction(420);
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
        Tile previous=selected;selected=null;previous.setSelected(false);previous.invalidate();
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
            if(row>0){RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(2+(row-1)*appColumns+column);tile=holder!=null?(Tile)holder.itemView:null;}
            if(tile==null||!androidx.core.view.ViewCompat.isAttachedToWindow(tile)){
                if(scrollAnimation==null||!scrollAnimation.isRunning())cancelFocusRestore("tile_unavailable");
                return true;
            }
            cancelFocusRestore("superseded");
            Tile previous=selected;if(previous!=tile)clearSelected(animate);selected=tile;tile.setSelected(true);
            updateDock(animate);if(previous!=tile)tile.invalidate();
            if(row>0){tile.setPivotX(tile.getWidth()/2f);tile.setPivotY(tile.getHeight()/2f);transformTile(tile,FOCUS_SCALE,0,-px(2),animate,320);}
            if(pendingAppFocus.isEmpty())activePackage=app.pkg;
            updateHeroTitle();
            wallpapers.setThemeColor(app.color);
            updateHeaderFocus();saveFocus();Log.i(TAG,"Focus row="+row+" column="+column+" package="+app.pkg);
            if(!animate)DiagnosticLog.info("home.focus.restored","request="+generation+" row="+row+" column="+column);
            return true;
        };
        list.getViewTreeObserver().addOnPreDrawListener(focusRestore);list.invalidate();
    }


    private App currentApp(){if(row==0)return !homeAppsVisible||dock.isEmpty()?null:dock.get(Math.min(column,dock.size()-1));int n=(row-1)*appColumns+column;return n>=0&&n<apps.size()?apps.get(n):null;}
    private void selectTile(Tile tile){
        List<App> values=tile.showLabel?apps:dock;int index=values.indexOf(tile.app);if(index<0)return;
        pendingAppFocus="";row=tile.showLabel?1+index/appColumns:0;column=tile.showLabel?index%appColumns:index;activePackage=tile.app.pkg;focusCurrent(false);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        InputDiagnostics.pointer(this,event);
        if(!waitingForUnlock)updateReminderInteraction();
        if(!waitingForUnlock&&screensaver!=null){cancelConfirm();if(screensaver.touch(event))return true;screensaver.interaction();}
        if(!waitingForUnlock && event.getActionMasked()==MotionEvent.ACTION_DOWN){if(automaticInputPending)cancelInputWork();++hdmiLaunchGeneration;new BootAppStore(this).cancelHdmiBoot();new BootAppStore(this).userAction("desktop_input");HomeGuardService.bootSourceUserAction(this);}
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){
        InputDiagnostics.motion(event);
        if(!waitingForUnlock)updateReminderInteraction();
        if(!waitingForUnlock&&screensaver!=null){cancelConfirm();if(screensaver.pointer(event))return true;screensaver.interaction();}return super.dispatchGenericMotionEvent(event);
    }
    private void leaveHome(Runnable launch){cancelInputWork();leaveHomePrepared(launch);}
    private void leaveHomePrepared(Runnable launch){cancelConfirm();screensaver.pause();HomeGuardService.bootSourceUserAction(this);wallpapers.beforeLeave(()->{if(resumed&&!isFinishing()&&!UiCompat.destroyed(this))launch.run();});}
    private void launchSelected(){
        if(!pendingAppFocus.isEmpty())return;
        App app=currentApp();if(app==null)return;saveFocus();
        if(!movingPackage.isEmpty()){saveMove();KeyFeedback.confirm(this);return;}
        if(ADD_DOCK_ENTRY.equals(app.pkg)){openAppActions(app);return;}
        if(new HomeVisibilityStore(this).hidden(app.pkg)){loadApps("hidden_launch");return;}
        KeyFeedback.confirm(this);
        if(WALLPAPER_ENTRY.equals(app.pkg)){wallpapers.showPicker();return;}
        if(HomeAppCatalog.WATCH_TV.equals(app.pkg)){watchTv();return;}
        leaveHome(()->{
            if(DESKTOP_SETTINGS_ENTRY.equals(app.pkg)){startActivity(settingsIntent(DesktopSettingsActivity.class));return;}
            if(MANAGEMENT_ENTRY.equals(app.pkg)){startActivity(settingsIntent(AppManagementActivity.class));return;}
            AutoDiagnosticOperation launch=AutoDiagnosticOperation.begin("app_launch",500);
            try{
                if(ChildBlockActivity.gate(this,app.pkg)){if(launch!=null)launch.stage("GATED");return;}
                if(new HomeGuardState(this).protects(app.pkg))HomeGuardService.allowOriginal();
                Intent intent=new Intent(Intent.ACTION_MAIN).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                String evidence="attempt="+DiagnosticOperation.nextId(SystemClock.elapsedRealtime())+" source=desktop_tile target="+app.pkg+" "+DiagnosticLog.intent(intent);
                DiagnosticLog.info("app.launch.request",evidence);
                startActivity(intent);
                externalLaunchDispatched=true;if(launch!=null)launch.stage("DISPATCHED_UNVERIFIED");
                DiagnosticLog.info("app.launch.dispatched",evidence+" result=dispatched_unverified");
            }catch(RuntimeException e){if(launch!=null)launch.stage("FAILED");DiagnosticLog.error("app.launch.failed",e);wallpapers.resume();OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,tr("应用暂不可用，请检查儿童模式设置","App unavailable"));}finally{if(launch!=null)launch.finish();}
        });
    }

    private void invalidateDockBackdrop() {
        if(homeHeader!=null&&wallpapers!=null)homeHeader.movie(wallpapers.displayedMovie());
        for(TextView pill:pills)pill.invalidate();
        if(dockBand!=null)dockBand.invalidate();
        for(Tile tile:dockTiles)if(tile.app!=null&&ADD_DOCK_ENTRY.equals(tile.app.pkg))tile.invalidate();
    }

    private DockLayout dockGeometry(int focus){
        boolean empty=dock.size()==1&&ADD_DOCK_ENTRY.equals(dock.get(0).pkg);String size=desktopSettings.iconSize(),shape=desktopSettings.iconShape(),title=desktopSettings.appTitlePosition();int scale=dockScaleDraft==null?desktopSettings.appearance("dock-scale"):dockScaleDraft,magnification=dockMagnificationDraft==null?desktopSettings.appearance("dock-magnification"):dockMagnificationDraft;boolean magnify=desktopSettings.dockMagnify();
        String key=dock.size()+":"+empty+":"+size+":"+shape+":"+title+":"+scale+":"+magnification+":"+magnify;
        if(!key.equals(dockGeometryKey)){dockGeometryKey=key;dockGeometries=new DockLayout[dock.size()+1];}
        int index=Math.max(0,Math.min(dock.size(),focus+1));DockLayout value=dockGeometries[index];
        if(value==null)dockGeometries[index]=value=empty?DockLayout.empty(focus,size,scale,magnify,magnification):DockLayout.create(dock.size(),focus,size,shape,scale,magnify,magnification,title);
        return value;
    }
    private void updateHeroTitle(){
        if(heroTitle==null)return;
        App app=homeAppsVisible&&!dock.isEmpty()?dock.get(Math.min(Math.max(column,0),dock.size()-1)):null;
        boolean show=app!=null&&!ADD_DOCK_ENTRY.equals(app.pkg)&&"top-left".equals(desktopSettings.appTitlePosition());
        heroTitle.setVisibility(show?View.VISIBLE:View.GONE);heroTitle.setText(show?app.label:"");
        DockLayout reserved=homeAppsVisible&&!dock.isEmpty()?dockGeometry(0):null;
        boolean panel=desktopSettings.showDockBackground();
        homeHeader.applicationAreaTop(reserved==null?540:show?reserved.featuredTitleTop(panel):reserved.top);
        if(show){
            int focus=row==0||dockMagnificationDraft!=null?column:-1;
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)heroTitle.getLayoutParams();int top=px(dockGeometry(focus).featuredTitleTop(panel));
            if(p.topMargin!=top){p.topMargin=top;heroTitle.setLayoutParams(p);}
        }
    }
    private void updateDock(boolean animate) {
        updateHeroTitle();
        if(dockTiles.isEmpty()||!(dockBand instanceof DockBand)||dockTiles.get(0).getWidth()==0)return;
        int focus=(row==0||dockMagnificationDraft!=null)&&homeAppsVisible?Math.min(column,dockTiles.size()-1):-1;if(animate&&focus==dockFocus)return;dockFocus=focus;if(dockPress!=null)dockPress.cancel();
        DockLayout geometry=dockGeometry(focus);renderedDockGeometry=geometry;boolean motion=animate&&!SettingsFocusMotion.reduced();((DockBand)dockBand).surface(geometry,motion);
        for(int i=0;i<dockTiles.size();i++){Tile tile=dockTiles.get(i);tile.animate().cancel();tile.setPivotX(0);tile.setPivotY(0);float scale=px(geometry.cardWidth[i])/(float)tile.getWidth(),dx=px(geometry.x[i]-CONTENT_MARGIN)-tile.getLeft(),dy=px(geometry.y[i])-tile.getTop();
            if("below".equals(desktopSettings.appTitlePosition())&&!ADD_DOCK_ENTRY.equals(tile.app.pkg)){
                // Text keeps its screen size even when a neighbouring card yields space.
                int height=(int)Math.ceil(tile.imageHeight()+px(geometry.labelGap+geometry.labelSize*1.4f)/(Math.min(tile.getScaleX(),scale)*.98f));
                ViewGroup.LayoutParams bounds=tile.getLayoutParams();if(bounds.height<height){bounds.height=height;tile.setLayoutParams(bounds);}
            }
            if(motion)tile.animate().scaleX(scale).scaleY(scale).translationX(dx).translationY(dy).setDuration(180).setInterpolator(DockCurve.value());else{tile.setScaleX(scale);tile.setScaleY(scale);tile.setTranslationX(dx);tile.setTranslationY(dy);}tile.invalidate();}
    }
    @android.annotation.TargetApi(21) private static final class DockCurve {static android.animation.TimeInterpolator value(){return new android.view.animation.PathInterpolator(.25f,.1f,.25f,1);}}
    private void pressDock(boolean pressed) {
        if(dockPress!=null)dockPress.cancel();
        if(row!=0||!homeAppsVisible||dockTiles.isEmpty())return;
        Tile item=dockTiles.get(Math.min(column,dockTiles.size()-1));item.animate().cancel();float from=item.getScaleX(),target=px(dockGeometry(column).cardWidth[column])/(float)item.getWidth()*(pressed?.98f:1);
        dockPress=ValueAnimator.ofFloat(from,target);dockPress.setDuration(pressed?80:120);dockPress.setInterpolator(FOCUS_MOTION);
        dockPress.addUpdateListener(a->{float value=(float)a.getAnimatedValue();item.setScaleX(value);item.setScaleY(value);});dockPress.start();
    }

    private void updateHeaderFocus() {
        if(!ready)return;
        if(quickControls!=null){quickControls.syncHeader();return;}
        View header=layout.findViewByPosition(0);boolean visible=row<0 && header!=null && header.getTop()==0;
        homeHeader.focusButton(visible&&!visiblePills.isEmpty()?pills.indexOf(visiblePills.get(topColumn)):-1);
        for(TextView pill:pills){boolean focused=visible && visiblePills.indexOf(pill)==topColumn;if(pill.isActivated()!=focused){pill.setActivated(focused);SettingsFocusMotion.update(pill,focused);}if(!focused)pill.setPressed(false);}
        homeHeader.controlVisibility();
    }

    private void applyDockAppearance(){
        if(dockBand==null)return;boolean show=homeAppsVisible&&!dock.isEmpty();dockBand.setVisibility(show?View.VISIBLE:View.GONE);dockBand.setBackgroundDrawable(null);dockBand.invalidate();
    }
    private final class DockBand extends FrameLayout {
        boolean restoreLayout;DockBackdropDrawable material;ValueAnimator geometryAnimation;final RectF surfaceBounds=new RectF();float surfaceRadius;
        DockBand(){super(HomeActivity.this);setVisibility(View.GONE);setWillNotDraw(false);}
        void surface(DockLayout target,boolean animate){if(geometryAnimation!=null)geometryAnimation.cancel();RectF end=new RectF(px(target.left-CONTENT_MARGIN),px(target.top),px(target.left-CONTENT_MARGIN+target.width),px(target.top+target.height));float radius=target.radius*unit;material=new DockBackdropDrawable(wallpapers::drawDockBackground,this,root,unit,false,true);
            if(!animate||surfaceBounds.isEmpty()){surfaceBounds.set(end);surfaceRadius=radius;invalidate();return;}RectF start=new RectF(surfaceBounds);float startRadius=surfaceRadius;geometryAnimation=ValueAnimator.ofFloat(0,1);geometryAnimation.setDuration(180);geometryAnimation.setInterpolator(DockCurve.value());geometryAnimation.addUpdateListener(a->{float f=(float)a.getAnimatedValue();surfaceBounds.set(start.left+(end.left-start.left)*f,start.top+(end.top-start.top)*f,start.right+(end.right-start.right)*f,start.bottom+(end.bottom-start.bottom)*f);surfaceRadius=startRadius+(radius-startRadius)*f;for(Tile tile:dockTiles)tile.invalidate();invalidate();});geometryAnimation.start();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);boolean empty=dock.size()==1&&ADD_DOCK_ENTRY.equals(dock.get(0).pkg);if(material==null||empty||!desktopSettings.showDockBackground())return;material.setBounds(Math.round(surfaceBounds.left),Math.round(surfaceBounds.top),Math.round(surfaceBounds.right),Math.round(surfaceBounds.bottom));material.radiusPixels(surfaceRadius);material.draw(c);}
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){super.onLayout(changed,left,top,right,bottom);if(restoreLayout&&dockBand==this){restoreLayout=false;updateDock(false);}}
        @Override protected void onDetachedFromWindow(){if(geometryAnimation!=null)geometryAnimation.cancel();super.onDetachedFromWindow();}
    }

    private final class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        private float gridHeight(){return ((960-2*CONTENT_MARGIN)/appColumns-APP_GAP)/("rectangle".equals(desktopSettings.iconShape())?16/9f:1)+APP_LABEL_GAP+APP_LABEL_HEIGHT;}
        @Override public int getItemViewType(int position){return position==0?0:position==1?1:2;}
        @Override public int getItemCount(){return 2+apps.size();}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int type){
            View view;
            if(type==0){
                FrameLayout header=new FrameLayout(HomeActivity.this);header.setClipChildren(false);RecyclerView.LayoutParams hp=new RecyclerView.LayoutParams(-1,px(HOME_HEADER_HEIGHT));hp.leftMargin=hp.rightMargin=-px(CONTENT_MARGIN);header.setLayoutParams(hp);
                heroTitle=text("",40,400,UiPalette.TEXT);heroTitle.setGravity(Gravity.BOTTOM|Gravity.START);heroTitle.setSingleLine(true);heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);heroTitle.setVisibility(View.GONE);place(header,heroTitle,820,DockLayout.FEATURED_TITLE_HEIGHT,CONTENT_MARGIN+2,0);
                heroTitle.setShadowLayer(3*unit,0,unit,UiPalette.INK);
                DockBand band=new DockBand();dockBand=band;
                wallpapers.observeDock(HomeActivity.this::invalidateDockBackdrop);
                band.setClipChildren(false);place(header,band,960-2*CONTENT_MARGIN,HOME_HEADER_HEIGHT,CONTENT_MARGIN,0);
                header.setTag(band);view=header;
            }else if(type==1){view=new View(HomeActivity.this);view.setLayoutParams(new RecyclerView.LayoutParams(-1,px(APP_TITLE_HEIGHT)));}
            else{Tile tile=new Tile(true);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(gridHeight()));p.leftMargin=px(APP_GAP/2);p.rightMargin=px(APP_GAP/2);p.bottomMargin=px(APP_GAP);tile.setLayoutParams(p);view=tile;}
            return new RecyclerView.ViewHolder(view){};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            if(position==0){
                FrameLayout header=(FrameLayout)holder.itemView;
                if(homeHeader.getParent()!=header){
                    if(homeHeader.getParent()!=null)((ViewGroup)homeHeader.getParent()).removeView(homeHeader);
                    place(header,homeHeader,960,HOME_HEADER_HEIGHT,0,0);
                }
                header.setAlpha(1);
                DockBand band=(DockBand)holder.itemView.getTag();dockBand=band;
                if(dockPress!=null)dockPress.cancel();
                for(Tile old:dockTiles){old.animate().cancel();if(old.app!=null&&visibleTiles.get(old.app.pkg)==old)visibleTiles.remove(old.app.pkg);if(selected==old)selected=null;}
                band.removeAllViews();dockTiles.clear();dockFocus=-2;band.restoreLayout=true;
                DockLayout base=dockGeometry(-1);
                for(int n=0;n<dock.size();n++){Tile tile=new Tile();tile.bind(dock.get(n));dockTiles.add(tile);place(band,tile,base.cardWidth[n],base.cardHeight[n]+base.labelGap+base.labelSize*1.4f,base.x[n]-CONTENT_MARGIN,base.y[n]);}
                applyDockAppearance();
                updateHeroTitle();
            }else if(position>=2){holder.itemView.getLayoutParams().height=px(gridHeight());((Tile)holder.itemView).bind(apps.get(position-2));}
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){if(holder.itemView instanceof Tile){Tile tile=(Tile)holder.itemView;if(tile.app!=null && visibleTiles.get(tile.app.pkg)==tile)visibleTiles.remove(tile.app.pkg);}}
    }

    private final class Tile extends View {
        App app;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final RectF imageRect=new RectF(),containedArtwork=new RectF();
        private BitmapShader artwork;private final Matrix artworkMatrix=new Matrix();
        private final Rect legacyVisibleBounds=new Rect();
        final boolean showLabel;
        private final android.text.TextPaint labelPaint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint.FontMetrics labelMetrics=new Paint.FontMetrics();
        private final RectF labelBounds=new RectF();
        private float labelWidth=-1,labelSize=-1,labelTextWidth;
        private String labelSource="",labelText="";
        private boolean labelFocused;
        Tile(){this(false);}
        Tile(boolean showLabel){super(HomeActivity.this);this.showLabel=showLabel;
            labelPaint.setTextAlign(Paint.Align.CENTER);
            setOnClickListener(v->{if(app!=null){if(!movingPackage.isEmpty()){saveMove();return;}selectTile(this);launchSelected();}});
            setOnLongClickListener(v->{if(app==null||!movingPackage.isEmpty())return true;selectTile(this);openAppActions(app);return true;});}
        void bind(App value){if(app!=null && visibleTiles.get(app.pkg)==this)visibleTiles.remove(app.pkg);transformTile(this,1,0,0,false,0);app=value;setBackgroundDrawable(ADD_DOCK_ENTRY.equals(app.pkg)?new DockBackdropDrawable(wallpapers::drawDockBackground,this,root,unit,true):null);artwork=app.libraryCover?null:new BitmapShader(app.banner,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);updateArtwork();visibleTiles.put(app.pkg,this);setContentDescription(app.label);invalidate();}
        float imageHeight(){return app!=null&&ADD_DOCK_ENTRY.equals(app.pkg)?getHeight():getWidth()/("rectangle".equals(desktopSettings.iconShape())?16/9f:1);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);updateArtwork();}
        private void updateArtwork(){
            if(app==null)return;
            if(app.libraryCover){float[] fit=LibraryCover.fit(app.banner.getWidth(),app.banner.getHeight(),getWidth(),imageHeight());containedArtwork.set(fit[0],fit[1],fit[2],fit[3]);return;}
            float scale=Math.max(getWidth()/(float)app.banner.getWidth(),imageHeight()/app.banner.getHeight());artworkMatrix.setScale(scale,scale);artworkMatrix.postTranslate((getWidth()-app.banner.getWidth()*scale)/2,(imageHeight()-app.banner.getHeight()*scale)/2);artwork.setLocalMatrix(artworkMatrix);
        }
        private float cardRadius(float height){
            return !ADD_DOCK_ENTRY.equals(app.pkg)&&"circle".equals(desktopSettings.iconShape())
                    ? Math.min(getWidth(),height)/2 : SurfaceCorners.artworkRadius(getWidth(),height);
        }
        @Override public void draw(Canvas canvas){
            if(app!=null&&selected==this&&row>=0){
                float height=imageHeight();
                imageRect.set(0,0,getWidth(),height);
                appCardShadow.draw(canvas,imageRect,cardRadius(height),app.libraryCover&&!app.trialArtwork?app.banner:null,
                        containedArtwork,unit);
            }
            super.draw(canvas);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(app==null)return;float h=imageHeight(),radius=cardRadius(h);RectF rect=imageRect;rect.set(0,0,getWidth(),h);
            boolean add=ADD_DOCK_ENTRY.equals(app.pkg);
            paint.setAlpha(255);
            if(!app.trialArtwork||legacyIcons.confirmed(app.pkg,app.coverKey)){
                drawArtwork(c,rect,radius,radius);offerLegacyArtwork(this,app);
            }else if(!drawingLegacySnapshot&&resumed&&hasWindowFocus()&&isShown()
                    &&getGlobalVisibleRect(legacyVisibleBounds)&&!legacyVisibleBounds.isEmpty()
                    &&!legacyTrialRetiring&&legacyIcons.trial(app.pkg,app.coverKey)){
                try{drawArtwork(c,rect,radius,radius);finishLegacyTrial(app,true);}
                catch(RuntimeException|OutOfMemoryError failure){drawTrialPlaceholder(c,rect,radius,radius);finishLegacyTrial(app,false);}
            }else drawTrialPlaceholder(c,rect,radius,radius);
            if(!add&&(showLabel&&desktopSettings.showAllAppsNames()||!showLabel&&"below".equals(desktopSettings.appTitlePosition()))){
                drawAppLabel(c,h);
            }
            if(app.pkg.equals(movingPackage)){
                paint.setColor(UiPalette.TEXT);float width=px(58),height=px(20);RectF badge=new RectF(getWidth()-width-px(5),px(5),getWidth()-px(5),px(5)+height);SurfaceCorners.draw(c,badge,px(SurfaceCorners.CONTROL),px(SurfaceCorners.CONTROL),paint);
                paint.setColor(0xff000000);paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(px(11));c.drawText(tr("移动中","Moving"),badge.centerX(),badge.centerY()+px(4),paint);
            }
        }
        private void drawAppLabel(Canvas canvas,float artworkHeight){
            DockLayout geometry=renderedDockGeometry;
            if(!showLabel&&geometry==null)return;
            boolean focused=selected==this&&row>=0;
            float scale=Math.max(.01f,getScaleX());
            float textSize=showLabel?px(14):geometry.labelSize*unit/scale;
            float padding=focused?px(8)/scale:0;
            float textWidth=Math.max(0,getWidth()-px(4)/scale-2*padding);
            if(textSize!=labelSize||textWidth!=labelWidth||focused!=labelFocused||!app.label.equals(labelSource)){
                labelSize=textSize;
                labelWidth=textWidth;
                labelFocused=focused;
                labelSource=app.label;
                labelPaint.setTextSize(textSize);
                labelPaint.setTypeface(focused?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
                labelPaint.setColor(focused?UiPalette.INK:UiPalette.TEXT);
                if(focused)labelPaint.clearShadowLayer();
                else labelPaint.setShadowLayer(2.25f*unit,0,unit,UiPalette.INK);
                labelText=android.text.TextUtils.ellipsize(labelSource,labelPaint,textWidth,android.text.TextUtils.TruncateAt.END).toString();
                labelTextWidth=labelPaint.measureText(labelText);
                labelPaint.getFontMetrics(labelMetrics);
            }
            if(labelText.isEmpty())return;

            float center=getWidth()/2f;
            float lineHeight=showLabel?px(APP_LABEL_HEIGHT):geometry.labelSize*1.4f*unit/scale;
            float top=artworkHeight+(showLabel?px(APP_LABEL_GAP):geometry.labelGap*unit/scale);
            if(focused){
                float halfWidth=labelTextWidth/2+padding;
                float border=unit/scale;
                float edgeInset=border/2;
                labelBounds.set(center-halfWidth,top+edgeInset,center+halfWidth,top+lineHeight-edgeInset);
                float radius=labelBounds.height()/2;
                paint.setColor(UiPalette.INK);
                canvas.drawRoundRect(labelBounds,radius,radius,paint);
                labelBounds.inset(border,border);
                paint.setColor(UiPalette.TEXT);
                canvas.drawRoundRect(labelBounds,radius-border,radius-border,paint);
            }
            float baseline=top+(lineHeight-labelMetrics.descent-labelMetrics.ascent)/2;
            canvas.drawText(labelText,center,baseline,labelPaint);
        }
        private void drawArtwork(Canvas c,RectF rect,float radius,float ry){
            if(app.libraryCover){paint.setShader(null);c.drawBitmap(app.banner,null,containedArtwork,paint);}
            else{paint.setShader(artwork);try{SurfaceCorners.draw(c,rect,radius,ry,paint);}finally{paint.setShader(null);}}
        }
        private void drawTrialPlaceholder(Canvas c,RectF rect,float radius,float ry){
            paint.setShader(null);paint.setColor(app.color);SurfaceCorners.draw(c,rect,radius,ry,paint);
        }

    }

    // A transparent tools Activity pauses HOME while its Dock remains visible underneath.
    private void phoneConfigurationChanged(){if(waitingForUnlock||wallpapers==null||UiCompat.destroyed(this))return;if(!getSharedPreferences("desktop-settings",0).getAll().equals(appliedDesktopValues)){applyDesktopSettings(false);updateClock();updateNetwork();}WeatherStore phoneWeather=new WeatherStore(this);WeatherData cachedWeather=phoneWeather.cached(chinese);String weatherKey=phoneWeather.key(chinese)+phoneWeather.enabled()+":"+(cachedWeather==null?0:cachedWeather.fetchedAt);if(!weatherKey.equals(phoneWeatherKey)){phoneWeatherKey=weatherKey;weather.settingsChanged();}if(ready&&(resumed||root.getWindowVisibility()==View.VISIBLE))loadApps("phone_settings");WallpaperStore media=WallpaperStore.get(this);WallpaperCatalog catalog=media.snapshot();String key=catalog.current+catalog.rotating+catalog.sound+catalog.weather+catalog.weatherDayNight+":"+catalog.rotationMinutes+":"+media.imageKey(catalog.current)+":"+media.manualVersion();if(!key.equals(phoneWallpaperKey)){phoneWallpaperKey=key;wallpapers.settingsChanged();}if(screensaver!=null)screensaver.settingsChanged();DiagnosticLog.info("home.configuration.applied","parent="+DiagnosticOperation.current()+" evidence=configuration_callback_not_presentation");}
    static boolean phoneAction(Context context,String id,PhoneSettingsServer.Active authority){HomeActivity home=liveHome.get();if(home==null||!home.ready||UiCompat.destroyed(home)||(!"screensaver".equals(id)&&!"source".equals(id)&&!(id.startsWith("app:")&&AppCoverStore.packageName(id.substring(4))))||!home.pendingHomeAction.isEmpty()||!authority.valid())return false;home.pendingHomeAction=id;home.phoneActionAuthority=authority;try{context.startActivity(new Intent(context,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));home.main.post(home::runPendingHomeAction);return true;}catch(RuntimeException unavailable){home.pendingHomeAction="";home.phoneActionAuthority=null;return false;}}
    static Bitmap previewArtwork(Context context,String pkg){HomeActivity home=liveHome.get();if(home==null||home.previewCatalogRevision!=home.packageRevision||UiCompat.destroyed(home))return null;App app=home.previewCatalog.get(pkg);return app!=null&&!app.trialArtwork&&AppCoverStore.get(context).key(pkg).equals(app.coverKey)&&!app.banner.isRecycled()?app.banner:null;}
    static byte[] phoneBackground()throws java.io.IOException {HomeActivity home=liveHome.get();if(home==null||home.root==null||home.root.getWidth()<1)throw new java.io.IOException("背景预览不可用");Bitmap image=Bitmap.createBitmap(320,Math.max(1,Math.round(320f*home.root.getHeight()/home.root.getWidth())),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.scale(image.getWidth()/(float)home.root.getWidth(),image.getHeight()/(float)home.root.getHeight());home.wallpapers.drawDockBackground(canvas,new Paint(Paint.FILTER_BITMAP_FLAG));java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.JPEG,85,output);image.recycle();return output.toByteArray();}
    static void capturePhoneBackground(WallpaperGpuView.Capture callback){HomeActivity home=liveHome.get();if(home==null||home.wallpapers==null||home.waitingForUnlock||UiCompat.destroyed(home)){callback.ready(null);return;}home.wallpapers.phoneBackground(callback);}
    static void previewHomeBrightness(Integer value){previewQuickAdjustment("brightness",value);}
    static void previewQuickAdjustment(String id,Integer value){HomeActivity home=liveHome.get();if(home==null)return;home.main.post(()->{if(UiCompat.destroyed(home)||home.waitingForUnlock)return;switch(id){case "brightness":home.brightnessDraft=value;home.updateWallpaperBlur();break;case "homeBlur":home.blurDraft=value;home.updateWallpaperBlur();break;case "dockScale":home.dockScaleDraft=value;home.updateDock(false);break;case "dockMagnification":home.dockMagnificationDraft=value;home.updateDock(false);break;}});}
    static int maximumQuickDockScale(Context context){HomeActivity home=liveHome.get();DesktopSettingsStore settings=new DesktopSettingsStore(context);int count=home!=null&&!UiCompat.destroyed(home)?home.dock.size():Math.min(new DockStore(context).load().size(),settings.appearance("dock-limit"));if(home!=null&&home.dock.size()==1&&ADD_DOCK_ENTRY.equals(home.dock.get(0).pkg))count=0;return DockLayout.maximumScale(count,settings.iconSize(),settings.iconShape(),settings.dockMagnify(),settings.appearance("dock-magnification"),settings.appTitlePosition());}
    private void openRemoteHomeTools(boolean buttons,String id){
        openHomeTools(buttons,id);
        KeySounds.play(this,KeySoundPolicy.CONFIRM);
    }
    private void openHomeTools(boolean buttons,String id){if(!buttons&&id.isEmpty()){showQuickControls(null,"");return;}saveFocus();leaveHome(()->startActivityForResult(settingsIntent(HomeToolsActivity.class).putExtra("mode",buttons?"buttons":"control").putExtra("home-button",id).putExtra("chinese",chinese),HomeToolsActivity.REQUEST));}
    private void showQuickControls(Bundle saved,String focus){
        if(quickControls!=null)return;ManagementUi.performanceAction("quick_controls");cancelInputWork();cancelConfirm();cancelFocusRestore("quick_controls");screensaver.pause();
        if(scrollAnimation!=null)scrollAnimation.cancel();list.stopScroll();int control=homeHeader.buttonIds.indexOf("control");row=control>=0?-1:0;if(control>=0)topColumn=control;focusCurrent(false);saveFocus();
        if(saved==null&&!focus.isEmpty()){saved=new Bundle();saved.putBoolean("quick-phone-expanded",true);saved.putString("quick-focus",focus);}
        quickControlsOpening=true;
        try{quickControls=new HomeQuickControlOverlay(this,root,list,homeHeader,unit,chinese,wallpapers::drawDockBackground,saved);quickControls.resume();}
        finally{quickControlsOpening=false;}
    }
    void closeQuickControls(boolean dock){
        quickReturn="";restoredQuickControls=null;
        HomeQuickControlOverlay quick=quickControls;if(quick==null)return;quickControls=null;quick.destroy();cancelConfirm();
        int index=homeHeader.buttonIds.indexOf("control");row=!dock&&index>=0?-1:0;if(row<0)topColumn=index;else column=Math.min(restoreColumn,maxColumn(0));focusCurrent(false);screensaver.availabilityChanged();
    }
    void activateQuickHeader(String id){closeQuickControls(false);if(!"control".equals(id))executeHomeButton(id);}
    void executeQuickControl(String id){
        if(quickControls==null||quickSettingsOpening)return;
        if("original".equals(id)){switchQuickDesktop();return;}
        if("keySounds".equals(id)||"mute".equals(id)){
            try{if("keySounds".equals(id))desktopSettings.savePhoneValue("key-sounds",!desktopSettings.keySounds());else{WallpaperStore store=WallpaperStore.get(this);WallpaperCatalog state=store.snapshot();if(state.weather||state.rotating){OperationFeedback.get(this).show(OperationFeedback.Kind.INFO,state.weather?"天气联动保持静音":"轮换背景保持静音");return;}store.sound(!state.sound);}PhoneSettingsSession.get(this).configurationChanged();KeyFeedback.confirm(this);}catch(IOException error){OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,error.getMessage());}return;
        }
        if(QuickControlStore.SETTINGS.equals(id)){quickSettingsOpening=true;leaveHome(()->startActivity(settingsIntent(DesktopSettingsActivity.class)));KeyFeedback.confirm(this);return;}
        closeQuickControls(false);
        KeyFeedback.confirm(this);
        if("source".equals(id)){openSource();return;}
        if("screensaver".equals(id)){screensaver.enter();return;}
        if("child".equals(id)||"apps".equals(id)||"clock".equals(id)||"weather".equals(id)||"wallpaper".equals(id)){
            int page="child".equals(id)?SettingsPages.CHILD:"apps".equals(id)?SettingsPages.APPS:"weather".equals(id)?SettingsPages.WEATHER:SettingsPages.DESKTOP;
            leaveHome(()->startActivity(settingsIntent(AppManagementActivity.class).putExtra("management-page",page).putExtra("quick-selected",id).putExtra("desktop-background-entry","wallpaper".equals(id)).putExtra("desktop-clock-entry","clock".equals(id))));return;
        }
        if("network".equals(id)||"bluetooth".equals(id)||"systemSettings".equals(id)){leaveHome(()->{if(!new SystemSettingsRouter(this).open("network".equals(id)?Settings.ACTION_WIFI_SETTINGS:"bluetooth".equals(id)?Settings.ACTION_BLUETOOTH_SETTINGS:Settings.ACTION_SETTINGS)){wallpapers.resume();OperationFeedback.error(this,"设置未能打开，请从原设置进入");}});return;}
        leaveHome(()->startActivity(settingsIntent(HomeToolsActivity.class).putExtra("mode","control").putExtra("home-button",id).putExtra("inline-quick-origin",true).putExtra("chinese",chinese)));
    }
    private void switchQuickDesktop(){
        if(quickDesktopSwitching){OperationFeedback.get(this).show(OperationFeedback.Kind.INFO,"正在切换原桌面，请稍候");return;}
        HomeQuickControlOverlay quick=quickControls;DesktopSwitch engine=DesktopSwitch.get(this);quickDesktopSwitching=true;
        KeyFeedback.confirm(this);
        engine.worker.execute(()->{
            String problem=null;
            try{
                JSONObject state=engine.state();String error=state.optString("problem");if(!error.isEmpty())throw new IOException(error);
                engine.execute("stock",state.optString("revision"),()->quickControls==quick&&!isFinishing()&&!UiCompat.destroyed(this));
            }catch(Exception failure){problem=DesktopSwitch.message(failure);}
            String error=problem;main.post(()->{
                quickDesktopSwitching=false;if(quickControls!=quick||isFinishing()||UiCompat.destroyed(this))return;
                if(error==null)closeQuickControls(false);else OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,error);
            });
        });
    }
    private void runPendingHomeAction(){
        if(!resumed||!ready||!hasWindowFocus())return;
        if(!pendingFeedback.isEmpty()){String text=pendingFeedback;pendingFeedback="";operationFeedback.show(pendingFeedbackSuccess?OperationFeedback.Kind.SUCCESS:OperationFeedback.Kind.INFO,text);}
        if(pendingDockFocus){pendingDockFocus=false;pendingButtonFocus="";quickReturn="";applyDesktopSettings();row=0;column=Math.min(restoreColumn,maxColumn(0));activePackage="";focusCurrent(false);}
        else if(!pendingButtonFocus.isEmpty()){applyDesktopSettings();int index=homeHeader.buttonIds.indexOf(pendingButtonFocus);pendingButtonFocus="";if(index>=0){row=-1;topColumn=index;focusCurrent(false);}}
        if(pendingHomeAction==null||pendingHomeAction.isEmpty())return;String action=pendingHomeAction;pendingHomeAction="";PhoneSettingsServer.Active authority=phoneActionAuthority;phoneActionAuthority=null;if(authority!=null&&!authority.valid())return;if(action.startsWith("app:")){String pkg=action.substring(4);Intent launch=new BootAppStore(this).launchIntent(pkg);if(launch==null||new HomeVisibilityStore(this).hidden(pkg))return;leaveHome(()->{if(authority==null||!authority.valid())return;if(ChildBlockActivity.gate(this,pkg))return;if(new HomeGuardState(this).protects(pkg))HomeGuardService.allowOriginal();ExternalActivityLauncher.open(this,"phone.app.open",launch);});return;}executeHomeButton(action);
    }
    private void returnToQuick(){
        if(quickReturn.isEmpty()&&restoredQuickControls==null||pendingHomeAction!=null&&!pendingHomeAction.isEmpty()||!resumed||!ready||!hasWindowFocus()||inputDiscoveryPending||screensaver.showing()||wallpapers.pickerShowing()||inputPopup!=null&&inputPopup.isShowing())return;
        String id=quickReturn;quickReturn="";Bundle saved=restoredQuickControls;restoredQuickControls=null;showQuickControls(saved,id);
    }
    private void executeHomeButton(String id){
        if(quickControls!=null){activateQuickHeader(id);return;}
        if("settings".equals(id))ManagementUi.performanceAction("desktop_settings");
        switch(id){case "settings":leaveHome(()->startActivity(settingsIntent(DesktopSettingsActivity.class)));break;case "systemSettings":openSettings(Settings.ACTION_SETTINGS);break;case "network":openSettings(Settings.ACTION_WIFI_SETTINGS);break;case "source":openSource();break;case "screensaver":screensaver.enter();break;case "wallpaper":wallpapers.showPicker();break;
            case "keySounds":desktopSettings.keySounds(!desktopSettings.keySounds());break;
            case "mute":try{WallpaperStore store=WallpaperStore.get(this);WallpaperCatalog state=store.snapshot();if(state.rotating){OperationFeedback.get(this).show(OperationFeedback.Kind.INFO,"轮换背景保持静音");return;}store.sound(!state.sound);}catch(java.io.IOException error){OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,error.getMessage());}break;
            default:openHomeTools(false,"control".equals(id)?"":id);break;
        }
    }
    private String topEntry(){
        if(row>=0 || topColumn<0 || topColumn>=visiblePills.size())return "none";
        int index=pills.indexOf(visiblePills.get(topColumn));return index>=0&&index<homeHeader.buttonIds.size()?homeHeader.buttonIds.get(index):"none";
    }
    private void systemEntryLifecycle(String state){
        if(!systemEntryPending.isEmpty())DiagnosticLog.info("home.action.lifecycle","entry="+systemEntryPending+" elapsedMs="+(android.os.SystemClock.elapsedRealtime()-systemEntryStarted)+" state="+state);
    }
    private void cancelInputWork(){cancelInputWork(false);}
    private void cancelInputWork(boolean keepPermission){
        if(!keepPermission&&tvInputs!=null)tvInputs.pendingTvInput=null;
        if(!pairingPreparation.isEmpty()){SourcePairing pairing=SourcePairing.existing();if(pairing!=null)pairing.cancelPreparation(pairingPreparation);pairingPreparation="";}
        inputWork.cancel();automaticInputPending=false;++inputDiscoveryGeneration;++systemEntryGeneration;inputDiscoveryPending=false;
        if(!maintenanceInputRequest.isEmpty()){DeviceInputProfile.cancelled(maintenanceInputRequest);maintenanceInputRequest="";}
        if(!maintenanceEntryRequest.isEmpty()){SystemEntryMaintenance.cancelled(maintenanceEntryRequest);maintenanceEntryRequest="";}
        if(!systemEntryDispatched&&!systemEntryPending.isEmpty()){
            systemEntryLifecycle("cancelled_unverified");systemEntryPending="";
            if(!keepPermission)resumeCancelledEntry();
        }
    }
    private void resumeCancelledEntry(){if(inputActive()&&hasWindowFocus()&&HomeGuardState.interactive(this)&&wallpapers!=null)wallpapers.resume();}
    private boolean inputActive(){return resumed&&!isFinishing()&&!UiCompat.destroyed(this);}
    private void leaveSystemEntry(String entry,Runnable action){
        cancelInputWork();leaveSystemEntryPrepared(entry,action);
    }
    private void leaveSystemEntryPrepared(String entry,Runnable action){
        if(entry.startsWith("input."))InputTransitionEvidence.begin("home_"+entry,"unknown");
        final int request=systemEntryGeneration;
        systemEntryPending=entry;systemEntryDispatched=false;systemEntryStarted=android.os.SystemClock.elapsedRealtime();
        DiagnosticLog.info("home.action.request","entry="+entry+" ready="+ready+" resumed="+resumed+" windowFocus="+hasWindowFocus()+" api="+Build.VERSION.SDK_INT);
        leaveHomePrepared(()->{if(request!=systemEntryGeneration){if(systemEntryPending.isEmpty())resumeCancelledEntry();return;}systemEntryLifecycle("wallpaper_exit_ready");action.run();});
    }
    private void systemEntryResult(boolean dispatched){systemEntryResult(dispatched,false);}
    private void systemEntryResult(boolean dispatched,boolean automatic){
        if(systemEntryPending.startsWith("input."))DiagnosticLog.info("input.transition.dispatch",InputTransitionEvidence.fields()+" entry="+systemEntryPending+" dispatched="+dispatched+" automatic="+automatic);
        systemEntryDispatched=dispatched;
        if(dispatched && systemEntryPending.startsWith("input."))HomeGuardService.savedInputSelected(this,automatic);
        systemEntryLifecycle(dispatched?"dispatched_unverified":"not_dispatched");
        if(!dispatched)systemEntryPending="";
    }
    private void openSettings(String action){
        settingsTrial=null;settingsTried.clear();
        leaveSystemEntry(Settings.ACTION_WIFI_SETTINGS.equals(action)?"network":"settings",()->{
            boolean opened=new SystemSettingsRouter(this).open(action);systemEntryResult(opened);
            if(!opened){wallpapers.resume();OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,tr("系统设置不可用，请使用电视遥控器","Settings unavailable; use the TV remote"));}
        });
    }
    private void showSettingsTest(String action){
        cancelInputWork();
        if(!action.equals(settingsTrialAction)){settingsTrial=null;settingsTried.clear();settingsTrialAction=action;}
        ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> operations=new ArrayList<>();
        labels.add(tr("测试当前入口","Test current entry"));operations.add(()->{settingsTried.clear();testSettings(action);});
        if(!settingsTried.isEmpty()){labels.add(tr("测试下一个入口","Test next entry"));operations.add(()->testSettings(action));}
        SettingsRouteMemory.Attempt attempt=settingsTrial;
        if(attempt!=null&&(attempt.result()==SettingsRouteMemory.Result.DISPATCHED_UNVERIFIED||attempt.result()==SettingsRouteMemory.Result.WINDOW_OBSERVED)){
            if(!attempt.fallback()){
                labels.add(Settings.ACTION_WIFI_SETTINGS.equals(action)?tr("已正常打开网络设置，记住入口","Network settings worked; remember entry"):tr("已正常打开系统设置，记住入口","System settings worked; remember entry"));
                operations.add(()->{
                    boolean saved=new SettingsRouteMemory(this).confirm(attempt,true);
                    OperationFeedback.get(this).show(saved?OperationFeedback.Kind.SUCCESS:OperationFeedback.Kind.ERROR,saved?tr("已记住当前固件的入口","Entry remembered for this firmware"):tr("入口已变化或测试已过期，请重新测试","Entry changed or test expired; test again"));settingsTrial=null;
                });
            }
            labels.add(tr("没有正常打开","Did not open correctly"));operations.add(()->{new SettingsRouteMemory(this).confirm(attempt,false);settingsTrial=null;showSettingsTest(action);});
        }
        String title=attempt!=null&&attempt.fallback()?tr("当前是系统设置首页入口","Current entry opens the settings home"):Settings.ACTION_WIFI_SETTINGS.equals(action)?tr("网络入口测试","Network entry test"):tr("系统设置入口测试","System settings entry test");
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setItems(labels.toArray(new String[0]),(d,which)->operations.get(which).run()).create();
        showSettingsDialog(dialog,title);
    }
    private void testSettings(String action){
        settingsTrial=null;
        leaveSystemEntry(Settings.ACTION_WIFI_SETTINGS.equals(action)?"network.test":"settings.test",()->{
            settingsTrial=new SystemSettingsRouter(this).open(action,true,settingsTried);
            boolean opened=settingsTrial!=null;systemEntryResult(opened);
            if(opened){settingsTried.add(settingsTrial.identity);OperationFeedback.get(this).show(OperationFeedback.Kind.INFO,tr("返回后长按同一入口，确认是否正常打开","After returning, long-press this entry to confirm the result"));}
            else{wallpapers.resume();OperationFeedback.get(this).show(OperationFeedback.Kind.ERROR,tr("没有其他可用入口，可重新测试或使用电视遥控器","No further entry; restart the test or use the TV remote"));}
        });
    }

    private void openInputPairingSettings(){leaveHome(()->startActivity(settingsIntent(DesktopSettingsActivity.class).putExtra("management-page",SettingsPages.STARTUP)));}
    private void showInputs(){
        if(inputDiscoveryPending)return;
        HomeGuardService.bootSourceUserAction(this);
        DiagnosticLog.info("input.request","api="+Build.VERSION.SDK_INT+" standardSupported="+(Build.VERSION.SDK_INT>=21));
        cancelInputWork();inputDiscoveryPending=true;int request=++inputDiscoveryGeneration;
        org.json.JSONObject paired=new SourcePairingStore(this).entry();
        if(paired!=null&&"adb".equals(paired.optString("transport"))){Context app=getApplicationContext();
            inputWork.submit(()->{SourceSearch.Candidate candidate=SourcePairing.preferred(app,paired);return candidate==null?null:candidate.prepare(app,null);},(launch,failure)->{
                if(!finishInputDiscovery(request))return;
                if(launch==null){showInputIssue("入口需要重新确认","请进入看电视重新配对。");return;}
                leaveSystemEntry("input.paired_adb",()->inputWork.submit(()->launch.dispatch(app),(opened,error)->{
                    systemEntryResult(Boolean.TRUE.equals(opened));if(!Boolean.TRUE.equals(opened)){wallpapers.resume();showInputIssue("信号源入口暂不可用","请检查本机调试连接，或进入看电视重新配对。");}
                }));
            });return;
        }
        if(Build.VERSION.SDK_INT>=21){if(tvInputs==null)tvInputs=new TvInputs();tvInputs.show(request);return;}
        Context app=getApplicationContext();
        inputWork.submit(()->new android.util.Pair<>(new TvInputPanels(app).inspect(),VendorTvInputs.legacySources(app)),(result,failure)->{
            if(!finishInputDiscovery(request))return;
            if(failure!=null){showInputIssue(tr("信号源读取失败","Input discovery failed"),tr("请重新检测","Detect again"));return;}
            TvInputPanels.Snapshot snapshot=result.first;List<VendorTvInputs.Source> sources=result.second;
            TvInputPanels.Panel direct=snapshot.directPanel(true);
            if(direct!=null&&!direct.confirmedPreference&&!sources.isEmpty())direct=null;
            if(direct!=null){openNativeInputs(direct);return;}
            List<TvInputPanels.Panel> panels=snapshot.availablePanels();
            if(!sources.isEmpty()||!panels.isEmpty()){
                ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
                for(VendorTvInputs.Source source:sources){labels.add(source.label);actions.add(()->openLegacyInput(source.key));}
                for(TvInputPanels.Panel panel:panels){labels.add(panel.profile.label(chinese));actions.add(()->openNativeInputs(panel));}
                android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
                    .setItems(labels.toArray(new String[0]),(d,index)->actions.get(index).run()).create();
                showSettingsDialog(dialog,tr("系统信号源","System inputs"));return;
            }
            showInputIssue(tr("信号源入口不可用","Input entry unavailable"),tr("当前安卓版本不提供标准输入列表。","This Android version has no standard TV input list.")+"\n\n"+snapshot.problem(chinese));
        });
    }
    private void openLegacyInput(String id){
        leaveSystemEntry("input.vendor",()->{
            Context app=getApplicationContext();
            inputWork.submit(()->{
                VendorTvInputs.Source selected=null;
                for(VendorTvInputs.Source source:VendorTvInputs.legacySources(app))if(id.equals(source.key)){if(selected!=null)return null;selected=source;}
                return selected==null?null:SavedInputLaunch.vendor(app,selected);
            },(launch,failure)->dispatchSavedHdmi(launch,failure,false));
        });
    }
    private boolean finishInputDiscovery(int request){
        if(request!=inputDiscoveryGeneration)return false;inputDiscoveryPending=false;
        return resumed&&!isFinishing()&&!UiCompat.destroyed(this)&&hasWindowFocus();
    }
    private String nativeInputProblem(TvInputPanels.PanelState state){
        switch(state){
            case DISABLED:return tr("原厂信号源组件已停用，请在电视系统中检查组件状态。","The native input component is disabled. Check its state in TV settings.");
            case RESTRICTED:return tr("系统不允许 Quiet TV 调用此信号源入口，请使用电视遥控器信号源键。","The system restricts this input entry. Use the remote input key.");
            case UNAVAILABLE:return tr("原厂信号源入口已变化或不再可用，请重新检测。","The native input entry changed or is unavailable. Detect again.");
            default:return tr("原厂信号源启动失败，请使用电视遥控器信号源键。","The native input panel failed to start. Use the remote input key.");
        }
    }
    private void openNativeInputs(TvInputPanels.Panel panel){
        leaveSystemEntry("input.panel",()->{
            TvInputPanels.PanelState state=new TvInputPanels(this).open(panel);systemEntryResult(state==TvInputPanels.PanelState.READY);
            if(state!=TvInputPanels.PanelState.READY){wallpapers.resume();showInputIssue(tr("信号源入口不可用","Input entry unavailable"),nativeInputProblem(state));}
        });
    }
    private void drawSnapshotView(View view,Canvas canvas){
        boolean wasDrawing=drawingLegacySnapshot;drawingLegacySnapshot=true;
        try{view.draw(canvas);}finally{drawingLegacySnapshot=wasDrawing;}
    }
    private void showSettingsDialog(android.app.AlertDialog dialog,String title){
        ControlStyle.dialogNavigation(dialog,title,tr("关闭","Close"),unit);
        dialog.show();
        ControlStyle.dialog(dialog,unit,(canvas,paint)->{wallpapers.drawDockBackground(canvas,paint);drawSnapshotView(root,canvas);},loader);
    }
    private void showInputIssue(String title,String message){
        DiagnosticLog.info("input.problem","dialog_shown");
        android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(this).setMessage(message)
            .setNeutralButton(tr("重新检测","Detect again"),(d,w)->main.post(this::showInputs));
        builder.setPositiveButton(tr("搜索与配对","Search and pair"),(d,w)->openInputPairingSettings());
        android.app.AlertDialog dialog=builder.create();
        dialog.setOnDismissListener(d->main.post(this::updateHeaderFocus));showSettingsDialog(dialog,title);
    }

    @android.annotation.TargetApi(24)
    private static final class Api24 {
        static Runnable observe(HomeActivity home){
            ConnectivityManager manager=(ConnectivityManager)home.getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
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
        void show(int request){
            Context app=getApplicationContext();
            inputWork.submit(()->new TvInputController(app).inspectSources(),(snapshot,failure)->{
                if(!finishInputDiscovery(request))return;
                if(failure!=null){showInputProblem(tr("信号源读取失败，请重新检测","Input discovery failed; detect again"));return;}
                present(snapshot,snapshot.vendor);
            });
        }
        void launch(String id,boolean vendor,String maintenance){
            leaveSystemEntry(!maintenance.isEmpty()?"input.maintenance":vendor?"input.vendor":"input.standard",()->{
                Context app=getApplicationContext();
                inputWork.submit(()->InputLaunch.resolve(app,id,vendor),this::dispatch);
            });
            maintenanceInputRequest=maintenance;
        }
        private void dispatch(InputLaunch launch,Exception failure){dispatch(launch,failure,false);}
        private void dispatch(InputLaunch launch,Exception failure,boolean automatic){
            if(!inputActive())return;
            if(failure!=null)DiagnosticLog.error("input.prepare.failed",failure);
            if(launch==null||!launch.current(HomeActivity.this)){finishLaunch(false,null,automatic);return;}
            if(launch.blocking()){
                // An issued provider transaction cannot be retracted. Never apply its late UI reply.
                HomeGuardService.savedInputSelected(HomeActivity.this,automatic);Context app=getApplicationContext();
                inputWork.submit(()->launch.dispatch(app),(opened,error)->{if(inputActive())finishLaunch(Boolean.TRUE.equals(opened),launch,automatic);});
            }else{
                boolean opened=launch.dispatch(HomeActivity.this);
                if(!opened&&launch.channelsPending){Context app=getApplicationContext();inputWork.submit(()->launch.channels(app),(next,error)->dispatch(next,error,automatic));}
                else finishLaunch(opened,launch,automatic);
            }
        }
        private void finishLaunch(boolean opened,InputLaunch launch,boolean automatic){
            automaticInputPending=false;
            if(!maintenanceInputRequest.isEmpty()){DeviceInputProfile.dispatched(maintenanceInputRequest,opened);maintenanceInputRequest="";}
            systemEntryResult(opened,automatic);
            if(opened){if(automatic)new BootAppStore(HomeActivity.this).dispatched(BootAppStore.HDMI,launch.foregroundComponent());return;}
            HomeGuardService.cancelInputTransition();wallpapers.resume();
            if(launch!=null&&launch.issue==InputLaunch.Issue.PERMISSION){
                if(Build.VERSION.SDK_INT>=23){pendingTvInput=launch.input;requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},102);}
                else showInputProblem(tr("系统未授予电视频道读取权限","TV channel permission was not granted by the system"));return;
            }
            if(launch!=null&&launch.issue==InputLaunch.Issue.NO_CHANNELS){
                android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(HomeActivity.this)
                    .setMessage(tr("连接天线或有线电视后，可在信号源设置中搜台。","Connect an antenna or cable, then configure channels."));
                Intent setup=launch.input.createSetupIntent();
                if(setup!=null)dialog.setPositiveButton(tr("信号源设置","Set up input"),(d,w)->leaveSystemEntry("input.setup",()->{
                    try{startActivity(setup);systemEntryResult(true);}catch(RuntimeException unavailable){systemEntryResult(false);wallpapers.resume();showInputProblem(tr("信号源设置不可用","Input setup unavailable"));}
                }));
                showSettingsDialog(dialog.create(),tr("尚未配置电视频道","No TV channels configured"));return;
            }
            showInputProblem(tr("信号源暂不可用，请重新检测或使用电视遥控器信号源键。","Input unavailable; detect again or use the TV remote input key."));
        }
        private void present(TvInputController.Snapshot snapshot,VendorTvInputs.Discovery discovery){
            List<VendorTvInputs.Source> vendor=discovery.sources;
            TvInputPanels.Panel direct=snapshot.directPanel();
            if(direct!=null&&!direct.confirmedPreference&&!vendor.isEmpty())direct=null;
            DiagnosticLog.info("input.menu.inspect","standard="+snapshot.state+" inputs="+snapshot.inputs.size()+" vendor="+vendor.size()+" panels="+snapshot.nativePanels.panels.size()+" profilesUnavailable="+snapshot.nativePanels.profilesUnavailable+" direct="+(direct!=null));
            if(direct!=null){openNativeInputs(direct);return;}
            ArrayList<TvInputInfo> inputs=snapshot.inputs;
            Collections.sort(inputs,(a,b)->{
                int ar=a.getType()==TvInputInfo.TYPE_HDMI?0:a.isPassthroughInput()?1:2;
                int br=b.getType()==TvInputInfo.TYPE_HDMI?0:b.isPassthroughInput()?1:2;
                return ar==br?inputName(a).compareTo(inputName(b)):Integer.compare(ar,br);
            });
            LinearLayout menu=new KeyFeedback.Menu(HomeActivity.this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(7),px(7),px(7),px(7));menu.setBackgroundDrawable(surface(0xfa000000,18));
            ArrayList<TextView> choices=new ArrayList<>();HashSet<String> tuners=new HashSet<>();
            for(TvInputInfo info:inputs){
                if(!info.isPassthroughInput() && !tuners.add("live"))continue;
                addInputChoice(menu,choices,inputName(info),()->openInput(info));
            }
            for(VendorTvInputs.Source source:vendor)addInputChoice(menu,choices,source.label,()->launch(source.key,true,""));
            for(TvInputPanels.Panel nativeChoice:snapshot.availablePanels())addInputChoice(menu,choices,nativeChoice.profile.label(chinese),()->openNativeInputs(nativeChoice));
            DiagnosticLog.info("input.menu","choices="+choices.size());
            if(choices.isEmpty()){showUnavailableInputs(snapshot,discovery);return;}
            if(snapshot.itemFailures>0){TextView note=text(tr("部分输入信息读取失败","Some inputs could not be read"),12,400,UiPalette.SECONDARY);note.setPadding(px(12),px(8),px(12),px(8));menu.addView(note);}
            inputPopup=new KeyFeedback.Popup(HomeActivity.this,menu,px(222),-2);inputPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));inputPopup.setElevation(px(14));inputPopup.setOutsideTouchable(true);
            inputPopup.setOnDismissListener(()->main.post(()->updateHeaderFocus()));
            for(int n=0;n<choices.size();n++){
                final int index=n;TextView choice=choices.get(n);
                choice.setOnKeyListener((v,key,event)->{
                    if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
                    if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)choices.get(Math.max(0,Math.min(choices.size()-1,index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)))).requestFocus();return true;}return false;
                });
            }
            if(inputButton!=null&&inputButton.getVisibility()==View.VISIBLE)inputPopup.showAsDropDown(inputButton,0,px(9));else inputPopup.showAtLocation(root,Gravity.TOP|Gravity.RIGHT,px(48),px(77));choices.get(0).requestFocus();
        }

        private void addInputChoice(LinearLayout menu,ArrayList<TextView> choices,String title,Runnable action){
            TextView choice=text(title,15,400,UiPalette.TEXT);choice.setPadding(px(16),0,px(12),0);choice.setGravity(Gravity.CENTER_VERTICAL);choice.setFocusable(true);choice.setFocusableInTouchMode(true);choice.setBackgroundDrawable(ControlStyle.listRow(unit,11));
            final int choiceIndex=choices.size();choice.setOnClickListener(v->{DiagnosticLog.info("input.menu.select","index="+choiceIndex);inputPopup.closeQuietly();action.run();KeyFeedback.confirm(HomeActivity.this);});
            choices.add(choice);menu.addView(choice,new LinearLayout.LayoutParams(-1,px(40)));
        }

        private void showUnavailableInputs(TvInputController.Snapshot snapshot,VendorTvInputs.Discovery vendor){
            String title,message;
            switch(snapshot.state){
                case UNAVAILABLE:title=tr("系统未提供输入服务","Input service unavailable");message=tr("当前系统没有可用的标准信号源服务。","The system has no available standard TV input service.");break;
                case RESTRICTED:title=tr("系统限制信号源访问","Input access restricted");message=tr("系统限制了信号源列表或输入信息的读取。","The system restricts access to the input list or its entries.");break;
                case FAILED:title=tr("信号源读取失败","Input discovery failed");message=tr("读取标准信号源时发生异常，请重新检测。","Reading the standard inputs failed. Detect again.");break;
                case FILTERED:title=tr("信号源已隐藏或被过滤","Inputs hidden or filtered");message=tr("系统返回了输入项，但均被隐藏设置或重复项规则排除。","The system returned inputs, but all were hidden or duplicate entries.");break;
                default:title=tr("暂未找到可用的信号源入口","No available input entry found");message=tr("Quiet TV 尚未识别到本机可用的输入入口。","Quiet TV has not found a usable input entry on this device.");
            }
            String vendorProblem=vendor.problem(chinese);if(!vendorProblem.isEmpty())message+="\n\n"+vendorProblem;
            message+="\n\n"+snapshot.nativePanels.problem(chinese);
            message+="\n\n"+tr("可重新检测，或使用电视遥控器信号源键。","Detect again or use the TV remote's Input button.");
            showInputIssue(title,message);
        }
        private void showInputProblem(String message){showInputIssue(tr("信号源切换未完成","Input switch incomplete"),message);}

        private void openInput(TvInputInfo info){launch(info.getId(),false,"");}


        private String inputName(TvInputInfo info){
            String paired=new SourcePairingStore(HomeActivity.this).label(info.getId());if(!paired.isEmpty())return paired;
            String custom=new DeviceInputProfile(HomeActivity.this).label(info.getId());if(!custom.isEmpty())return custom;
            if(!info.isPassthroughInput())return tr("电视直播","Live TV");
            if(info.getType()==TvInputInfo.TYPE_HDMI || info.getType()==TvInputInfo.TYPE_DISPLAY_PORT)return new TvInputController(HomeActivity.this).inputLabel(info);
            if(info.getType()==TvInputInfo.TYPE_COMPOSITE)return "AV";
            return new TvInputController(HomeActivity.this).inputLabel(info);
        }
    }
}
