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
final class HomePreviewView extends FrameLayout {
    final HomeHeaderView header;
    private final FrameLayout scene,band;
    private final ImageView wallpaper;
    private final View shade;
    private final WallpaperController.Ambient ambient;
    private final TextView title;
    private final boolean chinese,fixedGreeting;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean active;
    private volatile int generation;
    private final Runnable tick=new Runnable(){public void run(){if(active){refresh();main.postDelayed(this,60000-System.currentTimeMillis()%60000);}}};
    private final BroadcastReceiver changes=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refresh();}};
    HomePreviewView(Context context,boolean chinese,boolean fixedGreeting){
        super(context);this.chinese=chinese;this.fixedGreeting=fixedGreeting;setTag("home-preview");setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);setClipChildren(true);
        scene=new FrameLayout(context);scene.setPivotX(0);scene.setPivotY(0);scene.setBackgroundColor(Color.BLACK);addView(scene,new FrameLayout.LayoutParams(960,540));
        wallpaper=new ImageView(context);wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);scene.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        shade=new View(context);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));scene.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ambient=new WallpaperController.Ambient(context,1);scene.addView(ambient,new FrameLayout.LayoutParams(-1,-1));ambient.setColor(0xff666972);
        Typeface font=!chinese&&Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif",Typeface.NORMAL);
        header=new HomeHeaderView(context,1,chinese,font);scene.addView(header,new FrameLayout.LayoutParams(-1,-1));
        title=new TextView(context);title.setTextColor(0xfff1f2f6);title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,40);title.setIncludeFontPadding(false);title.setGravity(Gravity.CENTER_VERTICAL);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);place(title,820,56,50,314);
        band=new FrameLayout(context);band.setBackground(HomeHeaderView.pillSurface(1));place(band,864,114,48,386);refresh();
    }
    private void place(View v,int width,int height,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;scene.addView(v,p);}
    @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);setMeasuredDimension(w,Math.round(w*540f/960f));scene.measure(MeasureSpec.makeMeasureSpec(960,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(540,MeasureSpec.EXACTLY));}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){scene.setScaleX(w/960f);scene.setScaleY(w/960f);if(active&&w>0)loadLocalScene();}
    void refresh(){
        header.updateClock(fixedGreeting);WeatherStore weather=new WeatherStore(getContext());WeatherData data=weather.enabled()?weather.cached(chinese):null;header.weather(data!=null&&data.usable(System.currentTimeMillis())?data:null);
        android.net.ConnectivityManager manager=(android.net.ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);android.net.NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();
        TextView pill=header.pills.get(1);boolean connected=network!=null&&network.isConnected();int icon=R.drawable.ic_wifi_off;String label=chinese?"Wi-Fi 未连接":"Wi-Fi offline";
        if(connected){if(network.getType()==ConnectivityManager.TYPE_ETHERNET){icon=R.drawable.ic_ethernet;label=chinese?"有线网络":"Ethernet";}else{icon=R.drawable.ic_wifi;label=chinese?"Wi-Fi 已连接":"Wi-Fi connected";
            try{WifiManager wifi=(WifiManager)getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);String ssid=wifi==null?null:wifi.getConnectionInfo().getSSID();if(ssid!=null&&!"<unknown ssid>".equals(ssid))label=ssid.replace("\"","");}catch(SecurityException ignored){}
        }}pill.setText(label);header.setPillIcon(pill,icon);header.centerPill(pill);
    }
    void resume(){if(active)return;active=true;IntentFilter filter=new IntentFilter(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);getContext().registerReceiver(changes,filter);tick.run();if(getWidth()>0)loadLocalScene();}
    void pause(){if(!active)return;active=false;++generation;getContext().unregisterReceiver(changes);main.removeCallbacks(tick);}
    void destroy(){pause();++generation;worker.shutdownNow();wallpaper.setImageDrawable(null);band.removeAllViews();}
    private void loadLocalScene(){
        final int request=++generation;final Context context=getContext();
        android.app.ActivityManager memory=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        int limit=memory==null||memory.isLowRamDevice()||memory.getMemoryClass()<256?480:960;
        final int width=Math.min(limit,Math.max(360,getWidth()));
        worker.execute(()->{
            if(request!=generation)return;
            WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();WallpaperCatalog.Entry entry=catalog.find(catalog.current);Bitmap background=null;
            if(entry!=null&&!WallpaperCatalog.DEFAULT.equals(entry.id))try{background=WallpaperImages.decode(context,store,entry,width,width*9/16);}catch(java.io.IOException ignored){}
            if(request!=generation)return;
            ArrayList<String> packages=new DockStore(context).load();ArrayList<Bitmap> art=new ArrayList<>();ArrayList<String> names=new ArrayList<>();ArrayList<HomeAppCatalog.Entry> catalogApps=new HomeAppCatalog(context).load(packages);
            for(String pkg:packages)for(HomeAppCatalog.Entry app:catalogApps)if(pkg.equals(app.id)){names.add(app.label);art.add(app.info==null?HomeEntryArtwork.render(context,app.label,app.id):AppArtwork.load(context,app.info));break;}
            Bitmap image=background;main.post(()->{
                if(!active||request!=generation)return;
                wallpaper.setImageBitmap(image);shade.setVisibility(image==null?GONE:VISIBLE);ambient.setVisibility(image==null?VISIBLE:GONE);band.removeAllViews();
                title.setText(names.isEmpty()?chinese?"添加常用应用":"Add favorite apps":names.get(0));
                int color=art.isEmpty()?0xff666972:HomeActivity.sampleColor(art.get(0));ambient.setColor(color);
                if(art.isEmpty())art.add(emptyCard());
                float cardWidth=(864-36-4*18)/5f,cardHeight=cardWidth*203/360;
                FrameLayout.LayoutParams bandParams=(FrameLayout.LayoutParams)band.getLayoutParams();bandParams.height=Math.round(cardHeight+36);band.setLayoutParams(bandParams);
                for(int i=0;i<art.size();i++){ImageView card=new ImageView(context);card.setImageBitmap(art.get(i));card.setScaleType(ImageView.ScaleType.FIT_XY);GradientDrawable outline=new GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(11);card.setBackground(outline);UiCompat.clipOutline(card);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(cardWidth),Math.round(cardHeight));p.leftMargin=Math.round(18+i*(cardWidth+18));p.topMargin=18;band.addView(card,p);}
            });
        });
    }
    private Bitmap emptyCard(){Bitmap b=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);p.setShader(new LinearGradient(0,0,360,203,0xff2b2d33,0xff1b1d22,Shader.TileMode.CLAMP));c.drawRect(0,0,360,203,p);p.setShader(null);p.setColor(0xffe7e8ec);p.setTextSize(28);p.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics fm=p.getFontMetrics();float top=(203-76-12-(fm.descent-fm.ascent))/2;Drawable plus=UiCompat.drawable(getContext(),R.drawable.ic_manage_plus);plus.setBounds(142,Math.round(top),218,Math.round(top+76));plus.draw(c);c.drawText(chinese?"添加常用应用":"Add favorite apps",180,top+88-fm.ascent,p);return b;}
}
