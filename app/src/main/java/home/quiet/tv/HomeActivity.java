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
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Local-only TV home. Directional movement is owned here, never by focus search. */
public final class HomeActivity extends androidx.activity.ComponentActivity {
    private static final String TAG = "QuietTV";
    private static final String WALLPAPER_ENTRY = HomeAppCatalog.WALLPAPER;
    private static final String MANAGEMENT_ENTRY = HomeAppCatalog.MANAGEMENT;
    private static final float CONTENT_MARGIN = 48;
    private static final float FOCUS_SCALE = 1.035f, FOCUS_OUTSET = 5, FOCUS_STROKE = 3;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ArrayList<App> dock = new ArrayList<>(), apps = new ArrayList<>();
    private final ArrayList<Tile> dockTiles = new ArrayList<>();
    private final HashMap<String,Tile> visibleTiles = new HashMap<>();
    private final ArrayList<TextView> pills = new ArrayList<>();
    private final ArrayList<String> dockPackages = new ArrayList<>();
    private String orderSignature="";
    private final HashMap<String,App> artworkCache = new HashMap<>();
    private boolean appsDirty = true, loadingApps;
    private FrameLayout root;
    private RecyclerView list;
    private GridLayoutManager layout;
    private Glass glass;
    private WallpaperController wallpapers;
    private TextView clock, date, greeting, heroTitle, network;
    private PopupWindow inputPopup;
    private Typeface outfit;
    private boolean chinese = true, fixedGreeting = false, ready = false, resumed = false, requestedLocation = false;
    private float unit;
    private int row = 0, column = 0, topColumn = 0, restoreColumn = 0;
    private String activePackage = "";
    private TvInputInfo pendingTvInput;
    private Tile selected;
    private JSONObject config;
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if(Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && i.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            if(Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction()) && !i.getBooleanExtra(Intent.EXTRA_REPLACING,false) && i.getData()!=null){String pkg=i.getData().getSchemeSpecificPart();new DockStore(c).remove(pkg);new HomeOrderStore(c).remove(pkg);}
            appsDirty = true;
            if (resumed) loadApps();
        }
    };
    private ConnectivityManager.NetworkCallback networkCallback;
    private final BroadcastReceiver legacyNetworkReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){updateNetwork();}};

    private int px(float value) { return Math.round(value * unit); }
    private String tr(String zh, String en) { return chinese ? zh : en; }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);BackNavigation.install(this,this::handleBack);
        unit = getResources().getDisplayMetrics().widthPixels / 960f;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setNavigationBarColor(Color.BLACK);
        try {
            config = new JSONObject(readAsset("launcher.json"));
            chinese = !"en".equals(config.optString("language", "zh"));
            fixedGreeting = "home".equals(config.optString("greeting"));
            dockPackages.addAll(new DockStore(this).load());
            outfit = Build.VERSION.SDK_INT>=26 ? Typeface.createFromAsset(getAssets(), "fonts/outfit.ttf") : Typeface.create("sans-serif",Typeface.NORMAL);
        } catch (Exception e) { throw new IllegalStateException("Invalid bundled launcher configuration", e); }
        row = getPreferences(0).getInt("row", 0);
        column = getPreferences(0).getInt("column", 0);
        activePackage = getPreferences(0).getString("package", "");
        makeScene();
        IntentFilter packages=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
        registerReceiver(packageReceiver,packages);
        loadApps();
        loader.execute(()->{try{
            if(new SetupStore(this).shouldOffer(this))main.post(()->{if(!isFinishing() && !isDestroyed())startActivity(new Intent(this,SetupActivity.class));});
        }catch(Exception e){Log.w(TAG,"Setup state unavailable; retaining home",e);}});
    }

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
        setContentView(root);
        wallpapers = new WallpaperController(this, root, unit, chinese);
        list = new RecyclerView(this);
        list.setClipChildren(true); list.setClipToPadding(true);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER); list.setItemAnimator(null);
        list.setFocusable(false); list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        // Keep the home hint (ending at y=524) inside the padded viewport (ending at y=532).
        list.setPadding(px(CONTENT_MARGIN),0,px(CONTENT_MARGIN),px(8));
        layout = new GridLayoutManager(this,4);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return position < 2 ? 4 : 1; }
        });
        list.setLayoutManager(layout); list.setAdapter(new AppAdapter());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1,-1); lp.topMargin=px(192); root.addView(list,lp);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) { positionGlass(false); }
        });
        glass = new Glass(); root.addView(glass,new FrameLayout.LayoutParams(-1,-1));
        greeting=text("",14,600,0xffe6e7ed);greeting.setLetterSpacing(.14f);
        greeting.setGravity(Gravity.CENTER_VERTICAL);place(root,greeting,430,22,50,32);
        LinearLayout timeRow=new LinearLayout(this);timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);place(root,timeRow,430,64,50,57);
        clock=text("",50,400,0xfff1f2f6);clock.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        clock.setFontFeatureSettings("tnum");
        clock.setSingleLine(true);clock.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.addView(clock,new LinearLayout.LayoutParams(-2,-1));
        View divider=new View(this);divider.setBackgroundColor(0x70e6e7ed);
        LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(px(1),px(28));
        dividerParams.leftMargin=px(16);dividerParams.rightMargin=px(16);timeRow.addView(divider,dividerParams);
        date=text("",14,500,0xffe6e7ed);date.setLetterSpacing(.08f);
        date.setSingleLine(true);date.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.addView(date,new LinearLayout.LayoutParams(-2,-1));
        FrameLayout top = new FrameLayout(this);
        FrameLayout.LayoutParams topParams=new FrameLayout.LayoutParams(px(444),px(50),Gravity.TOP|Gravity.RIGHT);
        topParams.topMargin=px(67);topParams.rightMargin=px(CONTENT_MARGIN); root.addView(top,topParams);
        addPill(top, tr("信号源","Inputs"), R.drawable.ic_input, 102, 0, ()->showInputs());
        network=addPill(top,tr("网络","Network"), R.drawable.ic_ethernet, 102, 114, ()->openSettings(Settings.ACTION_WIFI_SETTINGS));
        addPill(top,tr("设置","Settings"), R.drawable.ic_settings, 102, 228, ()->openSettings(Settings.ACTION_SETTINGS));
        addPill(top,tr("关于","About"), R.drawable.ic_about, 102, 342, ()->{
            row=-1;topColumn=pills.size()-1;
            startActivity(new Intent(this,AboutActivity.class));
        });
        updateClock(); updateNetwork();
    }

    private TextView addPill(FrameLayout parent, String title, int icon, float width, float x, Runnable action) {
        TextView pill = text(title,14,400,0xffededf1); pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true); pill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.setPadding(px(12),0,px(12),0); pill.setBackground(pillSurface());
        pill.setCompoundDrawablePadding(px(6));setPillIcon(pill,icon);
        pill.setOnClickListener(v->action.run()); pills.add(pill); place(parent,pill,width,44,x,0);
        centerPillContents(pill);
        return pill;
    }

    private void setPillIcon(TextView pill,int resource) {
        Drawable icon=getDrawable(resource);icon.setBounds(0,0,px(16),px(16));
        pill.setCompoundDrawablesRelative(icon,null,null,null);
    }

    private void centerPillContents(TextView pill) {
        float content=px(22)+pill.getPaint().measureText(pill.getText().toString());
        int padding=Math.max(px(12),Math.round((pill.getLayoutParams().width-content)/2));
        pill.setPadding(padding,0,padding,0);
    }

    private void updateClock() {
        Calendar c=Calendar.getInstance(); int hour=c.get(Calendar.HOUR_OF_DAY);
        String message=hour<11?tr("早上好","Good morning"):hour<18?tr("下午好","Good afternoon"):tr("晚上好","Good evening");
        greeting.setText(fixedGreeting?tr("欢迎回家","Welcome home"):message);
        clock.setText(new SimpleDateFormat("HH:mm",Locale.US).format(c.getTime()));
        date.setText(new SimpleDateFormat(chinese?"M月d日  EEEE":"EEEE, MMMM d",chinese?Locale.SIMPLIFIED_CHINESE:Locale.US).format(c.getTime()));
    }

    private void updateNetwork() {
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
            network.setText(tr("有线网络","Ethernet"));setPillIcon(network,R.drawable.ic_ethernet);
        }
        else if(wifi) {
            setPillIcon(network,R.drawable.ic_wifi);
            if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && resumed && !requestedLocation){
                requestedLocation=true;
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},101);
            }
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            String ssid=wm.getConnectionInfo().getSSID();
            network.setText(ssid!=null && !ssid.equals("<unknown ssid>")?ssid.replace("\"", ""):tr("Wi-Fi 已连接","Wi-Fi connected"));
        } else {network.setText(tr("Wi-Fi 未连接","Wi-Fi offline"));setPillIcon(network,R.drawable.ic_wifi_off);}
        centerPillContents(network);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==101)updateNetwork();
        if(requestCode==102 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED && pendingTvInput!=null)openInput(pendingTvInput);
    }

    @Override protected void onResume() {
        super.onResume(); resumed=true;
        IntentFilter time=new IntentFilter(Intent.ACTION_TIME_TICK);time.addAction(Intent.ACTION_TIME_CHANGED);time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver,time);
        networkCallback=new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){main.post(()->updateNetwork());}
            @Override public void onLost(Network n){main.post(()->updateNetwork());}
        };
        if(Build.VERSION.SDK_INT>=24)((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(networkCallback);
        else registerReceiver(legacyNetworkReceiver,new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        updateClock();updateNetwork();
        wallpapers.resume();
        loadApps();
    }

    @Override protected void onPause() {
        resumed=false;saveFocus();wallpapers.pause();
        unregisterReceiver(timeReceiver);
        if(Build.VERSION.SDK_INT>=24)((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);
        else unregisterReceiver(legacyNetworkReceiver);
        if(inputPopup!=null) inputPopup.dismiss();
        super.onPause();
    }

    @Override protected void onDestroy(){unregisterReceiver(packageReceiver);loader.shutdownNow();wallpapers.destroy();super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(ready)focusCurrent(false);}

    private static class App {
        String pkg,label; ComponentName component; Bitmap banner; int color;
    }

    private void loadApps() {
        ArrayList<String> currentDock = new DockStore(this).load();
        final String requestedOrder=new HomeOrderStore(this).signature();
        if (!currentDock.equals(dockPackages) || !requestedOrder.equals(orderSignature)) appsDirty = true;
        if (loadingApps || (ready && !appsDirty)) return;
        loadingApps = true; appsDirty = false;
        dockPackages.clear();dockPackages.addAll(new DockStore(this).load());
        final ArrayList<String> pinned=new ArrayList<>(dockPackages);
        loader.execute(()->{
            try {
            PackageManager pm=getPackageManager();
            ArrayList<HomeAppCatalog.Entry> catalog=new HomeAppCatalog(this).load(pinned);
            ArrayList<App> found=new ArrayList<>();
            HashMap<String,App> freshCache=new HashMap<>();
            for(HomeAppCatalog.Entry entry:catalog) {
                ResolveInfo info=entry.info;if(info==null)continue;
                String key=new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString();
                try { key += ":" + pm.getPackageInfo(info.activityInfo.packageName,0).lastUpdateTime; }
                catch (PackageManager.NameNotFoundException e) { continue; }
                App cached=artworkCache.get(key);
                if(cached!=null){found.add(cached);freshCache.put(key,cached);continue;}
                App app=new App();app.pkg=info.activityInfo.packageName;app.label=info.loadLabel(pm).toString();
                app.component=new ComponentName(app.pkg,info.activityInfo.name);
                Drawable banner=info.activityInfo.loadBanner(pm);
                if(banner==null)banner=info.activityInfo.applicationInfo.loadBanner(pm);
                boolean hasBanner=banner!=null;
                if(banner==null)banner=info.loadIcon(pm);
                app.banner=renderBanner(banner,hasBanner);app.color=sampleColor(app.banner);found.add(app);freshCache.put(key,app);
            }
            artworkCache.clear();artworkCache.putAll(freshCache);
            App wallpaperApp=new App();wallpaperApp.pkg=WALLPAPER_ENTRY;wallpaperApp.label=tr("壁纸设置","Wallpaper Settings");
            wallpaperApp.banner=wallpapers.entryBanner();wallpaperApp.color=0xff557eac;found.add(0,wallpaperApp);
            App managementApp=new App();managementApp.pkg=MANAGEMENT_ENTRY;managementApp.label=tr("应用管理","App Management");
            managementApp.banner=managementBanner(tr("应用管理","App Management"));managementApp.color=0xff666972;found.add(1,managementApp);
            ArrayList<String> defaults=new ArrayList<>();for(App app:found)if(!pinned.contains(app.pkg))defaults.add(app.pkg);
            ArrayList<String> gridOrder=new HomeOrderStore(this).order(defaults);
            main.post(()->{
                if(isDestroyed())return;
                orderSignature=requestedOrder;
                if(!requestedOrder.equals(new HomeOrderStore(this).signature()))appsDirty=true;
                dock.clear();apps.clear();visibleTiles.clear();dockTiles.clear();
                for(String pkg:pinned)for(App app:found)if(pkg.equals(app.pkg))dock.add(app);
                HashMap<String,App> byId=new HashMap<>();for(App app:found)byId.put(app.pkg,app);
                for(String id:gridOrder)apps.add(byId.get(id));
                if(dock.isEmpty()){App empty=new App();empty.pkg="quiet:add-dock";empty.label=tr("添加常用应用","Add favorite apps");empty.banner=managementBanner(tr("添加常用应用","Add favorite apps"));empty.color=0xff666972;dock.add(empty);}
                if(row>=0){
                    if(!activePackage.isEmpty()) {
                        for(int n=0;n<dock.size();n++)if(activePackage.equals(dock.get(n).pkg)){row=0;column=n;}
                        for(int n=0;n<apps.size();n++)if(activePackage.equals(apps.get(n).pkg)){row=1+n/4;column=n%4;}
                    }
                    row=Math.max(0,Math.min(row,gridRows()));column=Math.min(column,maxColumn(row));
                }
                list.setAdapter(new AppAdapter());ready=true;
                loadingApps=false;
                main.post(()->focusCurrent(false));
                if(appsDirty && resumed)loadApps();
                Log.i(TAG,"Loaded dock="+dock.size()+" apps="+apps.size());
            });
            } catch (RuntimeException e) {
                main.post(()->{loadingApps=false;appsDirty=true;Log.w(TAG,"App refresh failed; retaining current tiles",e);});
            }
        });
    }

    private Bitmap renderBanner(Drawable drawable,boolean full) {
        Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);c.drawColor(0xff181a21);
        if(drawable!=null) {
            if(full)drawable.setBounds(0,0,360,203);else drawable.setBounds(126,47,234,155);
            drawable.draw(c);
        }
        return bitmap;
    }

    private Bitmap managementBanner(String title) {
        Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(3);
        paint.setShader(new LinearGradient(0,0,360,203,new int[]{0xff2b2d33,0xff1b1d22},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,360,203,paint);paint.setShader(null);
        // Center the icon + gap + actual font metrics as one group inside the banner.
        paint.setColor(0xffe7e8ec);paint.setTextSize(28);paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics=paint.getFontMetrics();float textHeight=metrics.descent-metrics.ascent;
        float top=(203-76-12-textHeight)/2f;
        Drawable icon=getDrawable(title.equals(tr("应用管理","App Management"))?R.drawable.ic_manage_apps:R.drawable.ic_manage_plus);
        icon.setBounds(142,Math.round(top),218,Math.round(top+76));icon.draw(canvas);
        canvas.drawText(title,180,top+76+12-metrics.ascent,paint);return bitmap;
    }

    private int sampleColor(Bitmap bitmap) {
        double best=0;int chosen=0xff5a6680;
        float[] hsv=new float[3];
        for(int y=10;y<bitmap.getHeight();y+=12)for(int x=10;x<bitmap.getWidth();x+=12){
            int color=bitmap.getPixel(x,y);Color.colorToHSV(color,hsv);
            double score=hsv[1]*hsv[2];
            if(hsv[2]>.15 && hsv[2]<.98 && score>best){best=score;chosen=color;}
        }
        return chosen;
    }

    private int gridRows(){return (apps.size()+3)/4;}
    private int maxColumn(int r){if(r<0)return pills.size()-1;if(r==0)return Math.max(0,dock.size()-1);return Math.min(3,apps.size()-(r-1)*4-1);}

    private void saveFocus(){if(row>=0)getPreferences(0).edit().putInt("row",row).putInt("column",column).putString("package",activePackage).apply();}

    public void handleBack(){if(inputPopup!=null && inputPopup.isShowing())inputPopup.dismiss();else if(row>0){row=0;column=Math.min(restoreColumn,maxColumn(0));focusCurrent(true);}}

    // Public Activity override; AndroidX core marks its inherited bridge class library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(inputPopup!=null && inputPopup.isShowing())return super.dispatchKeyEvent(event);
        if(!ready)return super.dispatchKeyEvent(event);
        if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            if(row<0){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)topColumn=Math.max(0,topColumn-1);
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)topColumn=Math.min(pills.size()-1,topColumn+1);
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){row=0;column=Math.min(restoreColumn,maxColumn(0));}
            }else{
                if(key==KeyEvent.KEYCODE_DPAD_LEFT)column=Math.max(0,column-1);
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT)column=Math.min(maxColumn(row),column+1);
                if(key==KeyEvent.KEYCODE_DPAD_UP){if(row==0){restoreColumn=column;row=-1;}else{row--;column=Math.min(column,maxColumn(row));}}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN && row<gridRows()){if(row==0)restoreColumn=column;row++;column=Math.min(column,maxColumn(row));}
            }
            focusCurrent(true);return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER){
            if(event.getAction()==KeyEvent.ACTION_UP){if(row<0)pills.get(topColumn).performClick();else launchSelected();}return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void focusCurrent(boolean animate) {
        if(!ready)return;
        if(row<0){
            if(selected!=null){selected.animate().scaleX(1).scaleY(1).translationX(0).setDuration(160);selected=null;}
            positionGlass(animate);Log.i(TAG,"Focus top="+topColumn);return;
        }
        if(row==0)layout.scrollToPositionWithOffset(0,0);
        else{
            int position=2+(row-1)*4;
            View first=layout.findViewByPosition(position);
            if(row==1 || first==null || first.getTop()<px(38) || first.getBottom()>list.getHeight()-px(20)) layout.scrollToPositionWithOffset(position,px(42));
        }
        list.post(()->{
            if(row<0)return;
            App app=currentApp();if(app==null)return;
            Tile tile=visibleTiles.get(app.pkg);
            if(tile==null){list.postDelayed(()->focusCurrent(animate),40);return;}
            if(selected!=null && selected!=tile){selected.animate().scaleX(1).scaleY(1).translationX(0).setDuration(180);}
            selected=tile;tile.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).translationX(focusTranslationX(tile)).setDuration(240).setInterpolator(new OvershootInterpolator(.7f));
            activePackage=app.pkg;
            if(row==0 && heroTitle!=null)heroTitle.setText(app.label);
            wallpapers.setThemeColor(app.color);
            positionGlass(animate);saveFocus();Log.i(TAG,"Focus row="+row+" column="+column+" package="+app.pkg);
        });
    }


    private App currentApp(){if(row==0)return dock.isEmpty()?null:dock.get(Math.min(column,dock.size()-1));int n=(row-1)*4+column;return n>=0&&n<apps.size()?apps.get(n):null;}

    private void launchSelected(){App app=currentApp();if(app==null)return;saveFocus();if("quiet:add-dock".equals(app.pkg)){startActivity(new Intent(this,DockEditorActivity.class));return;}if(MANAGEMENT_ENTRY.equals(app.pkg)){startActivity(new Intent(this,AppManagementActivity.class));return;}if(WALLPAPER_ENTRY.equals(app.pkg)){wallpapers.showPicker();return;}try{startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(ActivityNotFoundException e){Toast.makeText(this,tr("应用暂不可用","App unavailable"),Toast.LENGTH_SHORT).show();}}

    private int focusOutset(View view) {
        return px(view instanceof Tile?2:FOCUS_OUTSET);
    }

    private float focusTranslationX(Tile tile) {
        // Keep the outer white border aligned with the unfocused card edge at either end.
        float inset=tile.getWidth()*(FOCUS_SCALE-1)/2f+focusOutset(tile)+px(FOCUS_STROKE)/2f;
        int index=dockTiles.indexOf(tile);
        return index==0?inset:index>=0 && index==dockTiles.size()-1?-inset:0;
    }

    private void positionGlass(boolean animate) {
        if(glass==null || !ready)return;
        View view=row<0?pills.get(topColumn):selected;
        if(view==null || !view.isAttachedToWindow())return;
        int[] point=new int[2];int[] origin=new int[2];root.getLocationOnScreen(origin);
        float scale=view instanceof Tile?FOCUS_SCALE:1;
        // Animate towards the final transformed bounds, independent of the current animation frame.
        ((View)view.getParent()).getLocationOnScreen(point);
        float left=point[0]-origin[0]+view.getLeft()+view.getPivotX()*(1-scale);
        float top=point[1]-origin[1]+view.getTop()+view.getPivotY()*(1-scale);
        if(view instanceof Tile)left+=focusTranslationX((Tile)view);
        float height=view instanceof Tile?((Tile)view).imageHeight():view.getHeight();
        int outset=focusOutset(view);
        RectF target=new RectF(left-outset,top-outset,left+view.getWidth()*scale+outset,top+height*scale+outset);
        glass.move(target,row<0?px(25):px(15),animate);
    }

    private final class AppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        private final float dockPadding=18, dockGap=18, dockWidth=(864-2*dockPadding-3*dockGap)/4f, dockHeight=dockWidth*203f/360f;
        @Override public int getItemViewType(int position){return position==0?0:position==1?1:2;}
        @Override public int getItemCount(){return 2+apps.size();}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent,int type){
            View view;
            if(type==0){
                FrameLayout header=new FrameLayout(HomeActivity.this);header.setClipChildren(false);header.setLayoutParams(new RecyclerView.LayoutParams(-1,px(348)));
                heroTitle=text("",40,400,0xfff1f2f6);place(header,heroTitle,820,56,2,90);
                TextView hint=text(tr("按下 ↓ 浏览全部应用","Press ↓ for all apps"),12,300,0xff64656e);hint.setGravity(Gravity.CENTER);place(header,hint,-1,20,0,312);
                FrameLayout band=new FrameLayout(HomeActivity.this);band.setBackground(pillSurface());band.setClipChildren(false);place(header,band,-1,dockHeight+2*dockPadding,0,162);
                header.setTag(band);view=header;
            }else if(type==1){TextView title=text(tr("全部应用","All apps"),18,400,0xffbbbbc3);title.setPadding(px(3),px(4),0,0);title.setLayoutParams(new RecyclerView.LayoutParams(-1,px(38)));view=title;}
            else{Tile tile=new Tile();RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,px(145));p.leftMargin=px(8);p.rightMargin=px(8);p.bottomMargin=px(12);tile.setLayoutParams(p);view=tile;}
            return new RecyclerView.ViewHolder(view){};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            if(position==0){
                FrameLayout band=(FrameLayout)holder.itemView.getTag();band.removeAllViews();dockTiles.clear();
                for(int n=0;n<dock.size();n++){Tile tile=new Tile();tile.bind(dock.get(n));dockTiles.add(tile);place(band,tile,dockWidth,dockHeight,dockPadding+n*(dockWidth+dockGap),dockPadding);}
                if(!dock.isEmpty()){App app=dock.get(Math.min(Math.max(column,0),dock.size()-1));heroTitle.setText(app.label);}
            }else if(position>=2)((Tile)holder.itemView).bind(apps.get(position-2));
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){if(holder.itemView instanceof Tile){Tile tile=(Tile)holder.itemView;if(tile.app!=null && visibleTiles.get(tile.app.pkg)==tile)visibleTiles.remove(tile.app.pkg);}}
    }

    private final class Tile extends View {
        App app;final Paint paint=new Paint(3);final Path clip=new Path();
        Tile(){super(HomeActivity.this);setOnClickListener(v->{if(app!=null){for(int n=0;n<dock.size();n++)if(dock.get(n)==app){row=0;column=n;}for(int n=0;n<apps.size();n++)if(apps.get(n)==app){row=1+n/4;column=n%4;}focusCurrent(true);launchSelected();}});}
        void bind(App value){if(app!=null && visibleTiles.get(app.pkg)==this)visibleTiles.remove(app.pkg);app=value;visibleTiles.put(app.pkg,this);setContentDescription(app.label);invalidate();}
        float imageHeight(){return getWidth()*203f/360f;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(app==null)return;float h=imageHeight();RectF rect=new RectF(0,0,getWidth(),h);clip.reset();clip.addRoundRect(rect,px(11),px(11),Path.Direction.CW);c.save();c.clipPath(clip);paint.setColor(0xff191a20);c.drawRect(rect,paint);c.drawBitmap(app.banner,null,rect,paint);c.restore();
        }
    }

    private final class Glass extends View {
        final RectF current=new RectF();final Paint paint=new Paint(3);float radius;ValueAnimator animation;
        Glass(){super(HomeActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void move(RectF target,float corner,boolean spring){
            if(animation!=null)animation.cancel();radius=corner;
            if(!spring || current.isEmpty()){current.set(target);invalidate();return;}
            RectF from=new RectF(current);animation=ValueAnimator.ofFloat(0,1);animation.setDuration(340);animation.setInterpolator(new OvershootInterpolator(.8f));
            animation.addUpdateListener(a->{float f=(float)a.getAnimatedValue();current.set(from.left+(target.left-from.left)*f,from.top+(target.top-from.top)*f,from.right+(target.right-from.right)*f,from.bottom+(target.bottom-from.bottom)*f);invalidate();});animation.start();
        }
        @Override protected void onDraw(Canvas c){
            if(current.isEmpty())return;
            paint.setShader(new LinearGradient(current.left,current.top,current.right,current.bottom,new int[]{0x30ffffff,0x09ffffff},null,Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);c.drawRoundRect(current,radius,radius,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);paint.setStrokeWidth(px(FOCUS_STROKE));c.drawRoundRect(current,radius,radius,paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void openSettings(String action){try{startActivity(new Intent(action));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}

    private void showInputs(){
        TvInputController.Snapshot snapshot=new TvInputController(this).inspect();
        TvInputController.Panel direct=snapshot.directPanel();
        if(direct!=null){openSystemInputs(direct);return;}
        ArrayList<TvInputInfo> inputs=snapshot.inputs;
        Collections.sort(inputs,(a,b)->{
            int ar=a.getType()==TvInputInfo.TYPE_HDMI?0:a.isPassthroughInput()?1:2;
            int br=b.getType()==TvInputInfo.TYPE_HDMI?0:b.isPassthroughInput()?1:2;
            return ar==br?inputName(a).compareTo(inputName(b)):Integer.compare(ar,br);
        });
        LinearLayout menu=new LinearLayout(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(7),px(7),px(7),px(7));menu.setBackground(surface(0xfa25262e,18));
        ArrayList<TextView> choices=new ArrayList<>();HashSet<String> tuners=new HashSet<>();
        for(TvInputInfo info:inputs){
            if(!info.isPassthroughInput() && !tuners.add("live"))continue;
            addInputChoice(menu,choices,inputName(info),()->openInput(info));
        }
        for(TvInputController.Panel panel:snapshot.availablePanels())addInputChoice(menu,choices,panel.profile.label(chinese),()->openSystemInputs(panel));
        if(choices.isEmpty()){showUnavailableInputs(snapshot);return;}
        inputPopup=new PopupWindow(menu,px(222),-2,true);inputPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));inputPopup.setElevation(px(14));inputPopup.setOutsideTouchable(true);
        inputPopup.setOnDismissListener(()->main.post(()->positionGlass(false)));
        for(int n=0;n<choices.size();n++){
            final int index=n;TextView choice=choices.get(n);
            choice.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
                if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)choices.get(Math.max(0,Math.min(choices.size()-1,index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)))).requestFocus();return true;}return false;
            });
        }
        inputPopup.showAsDropDown(pills.get(0),0,px(9));choices.get(0).requestFocus();
    }

    private void addInputChoice(LinearLayout menu,ArrayList<TextView> choices,String title,Runnable action){
        TextView choice=text(title,15,400,0xffeeeeef);choice.setPadding(px(16),0,px(12),0);choice.setGravity(Gravity.CENTER_VERTICAL);choice.setFocusable(true);choice.setFocusableInTouchMode(true);choice.setBackground(surface(0x0025262e,11));
        choice.setOnFocusChangeListener((v,has)->v.setBackground(surface(has?0xff51525d:0x0025262e,11)));
        choice.setOnClickListener(v->{inputPopup.dismiss();action.run();});
        choices.add(choice);menu.addView(choice,new LinearLayout.LayoutParams(-1,px(40)));
    }

    private String panelProblem(TvInputController.PanelState state){
        switch(state){
            case DISABLED:return tr("系统信号源组件已禁用。请先在电视系统中检查该组件状态。","The system input component is disabled. Check its state in the TV settings.");
            case RESTRICTED:return tr("电视系统不允许 Quiet TV 打开此信号源面板。请使用电视遥控器的信号源键。","The TV restricts access to this input panel. Use the TV remote's Input button.");
            case UNAVAILABLE:return tr("系统信号源入口已不可用。请使用电视遥控器的信号源键。","The system input panel is no longer available. Use the TV remote's Input button.");
            default:return tr("系统信号源面板未能打开。请使用电视遥控器的信号源键。","The system input panel could not be opened. Use the TV remote's Input button.");
        }
    }

    private void openSystemInputs(TvInputController.Panel panel){
        TvInputController.PanelState state=new TvInputController(this).openPanel(panel.profile);
        if(state!=TvInputController.PanelState.READY)showInputProblem(panelProblem(state));
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
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle(tr("信号源暂不可用","Inputs unavailable")).setMessage(message)
            .setNegativeButton(tr("返回","Back"),(d,w)->{})
            .setPositiveButton(tr("系统设置","System settings"),(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_SETTINGS));}
                catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,tr("系统设置不可用，请使用电视遥控器","Settings unavailable; use the TV remote"),Toast.LENGTH_SHORT).show();}
            }).create();
        dialog.setOnDismissListener(d->main.post(()->positionGlass(false)));dialog.show();
    }

    private void offerSystemInputs(){
        TvInputController.Snapshot snapshot=new TvInputController(this).inspect();
        List<TvInputController.Panel> panels=snapshot.availablePanels();
        if(panels.isEmpty()){
            String message=tr("此输入无法打开，且未找到可用的系统信号源面板。请使用电视遥控器的信号源键或进入系统设置。","This input could not be opened and no usable system input panel was found. Use the TV remote's Input button or system settings.");
            for(TvInputController.Panel panel:snapshot.panels)message+="\n\n"+panel.profile.label(chinese)+"\n"+panelProblem(panel.state);
            showInputProblem(message);return;
        }
        String[] labels=new String[panels.size()];for(int i=0;i<labels.length;i++)labels[i]=panels.get(i).profile.label(chinese);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle(tr("此输入无法打开，请选择系统信号源","Input unavailable; choose system inputs"))
            .setItems(labels,(d,which)->openSystemInputs(panels.get(which)))
            .setNegativeButton(tr("返回","Back"),(d,w)->{}).create();
        dialog.setOnDismissListener(d->main.post(()->positionGlass(false)));dialog.show();
    }

    private void openInput(TvInputInfo info){
        Uri uri;
        if(info.isPassthroughInput())uri=TvContract.buildChannelUriForPassthroughInput(info.getId());
        else{
            if(checkCallingOrSelfPermission("android.permission.READ_TV_LISTINGS")!=PackageManager.PERMISSION_GRANTED){
                if(Build.VERSION.SDK_INT>=23){pendingTvInput=info;requestPermissions(new String[]{"android.permission.READ_TV_LISTINGS"},102);}
                else Toast.makeText(this,tr("系统未授予电视频道读取权限","TV channel permission was not granted by the system"),Toast.LENGTH_SHORT).show();
                return;
            }
            long channel=-1;
            try(android.database.Cursor cursor=getContentResolver().query(TvContract.buildChannelsUriForInput(info.getId()),new String[]{TvContract.Channels._ID},null,null,null)){
                if(cursor!=null && cursor.moveToFirst())channel=cursor.getLong(0);
            }catch(SecurityException e){Toast.makeText(this,tr("系统未允许读取电视频道","TV channels are restricted by the system"),Toast.LENGTH_SHORT).show();return;}
            if(channel<0){
                android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(this)
                    .setTitle(tr("尚未配置电视频道","No TV channels configured"))
                    .setMessage(tr("电视系统的频道库为空。连接天线或有线电视后，可在信号源设置中搜台。网络直播请打开天光云影。","Connect an antenna or cable source, then configure channels. For IPTV, open your streaming app."))
                    .setNegativeButton(tr("返回","Back"),(d,w)->{});
                Intent setup=info.createSetupIntent();
                if(setup!=null)dialog.setPositiveButton(tr("信号源设置","Set up input"),(d,w)->{try{startActivity(setup);}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,tr("信号源设置不可用","Input setup unavailable"),Toast.LENGTH_SHORT).show();}});
                dialog.show();return;
            }
            uri=TvContract.buildChannelUri(channel);
        }
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri).putExtra(TvInputInfo.EXTRA_INPUT_ID,info.getId()));Log.i(TAG,"Switch input "+info.getId());}
        catch(ActivityNotFoundException|SecurityException e){offerSystemInputs();}
    }

    private String inputName(TvInputInfo info){
        if(!info.isPassthroughInput())return tr("电视直播","Live TV");
        if(info.getType()==TvInputInfo.TYPE_HDMI)return info.loadLabel(this).toString();
        if(info.getType()==TvInputInfo.TYPE_COMPOSITE)return "AV";
        return info.loadLabel(this).toString();
    }
}
