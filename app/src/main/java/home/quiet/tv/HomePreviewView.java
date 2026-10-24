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
    private Bitmap dockBackground;
    private final DockBackdropDrawable dockSurface;
    private final Typeface typeface;
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
        typeface=!chinese&&Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif",Typeface.NORMAL);
        header=new HomeHeaderView(context,1,chinese,typeface);scene.addView(header,new FrameLayout.LayoutParams(-1,-1));
        title=new TextView(context);title.setTextColor(0xfff1f2f6);title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,40);title.setIncludeFontPadding(false);title.setGravity(Gravity.CENTER_VERTICAL);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);place(title,820,56,50,314);
        band=new FrameLayout(context);dockSurface=new DockBackdropDrawable(this::drawWallpaper,band,scene,1);band.setBackground(dockSurface);ambient.colorChanged=()->{band.invalidate();for(int i=0;i<band.getChildCount();i++)band.getChildAt(i).invalidate();};place(band,864,114,48,386);refresh();
    }
    private void place(View v,int width,int height,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,height);p.leftMargin=x;p.topMargin=y;scene.addView(v,p);}
    @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width);setMeasuredDimension(w,Math.round(w*540f/960f));scene.measure(MeasureSpec.makeMeasureSpec(960,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(540,MeasureSpec.EXACTLY));}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){scene.setScaleX(w/960f);scene.setScaleY(w/960f);if(active&&w>0)loadLocalScene();}
    void refresh(){
        title.setVisibility(new DesktopSettingsStore(getContext()).showAppTitle()?VISIBLE:GONE);header.updateClock(fixedGreeting);WeatherStore weather=new WeatherStore(getContext());WeatherData data=weather.enabled()?weather.cached(chinese):null;header.weather(data!=null&&data.usable(System.currentTimeMillis())?data:null);
        android.net.ConnectivityManager manager=(android.net.ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);android.net.NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();
        TextView pill=header.pills.get(1);boolean connected=network!=null&&network.isConnected();int icon=R.drawable.ic_wifi_off;String label=chinese?"Wi-Fi 未连接":"Wi-Fi offline";
        if(connected){if(network.getType()==ConnectivityManager.TYPE_ETHERNET){icon=R.drawable.ic_ethernet;label=chinese?"有线网络":"Ethernet";}else{icon=R.drawable.ic_wifi;label=chinese?"Wi-Fi 已连接":"Wi-Fi connected";
            try{WifiManager wifi=(WifiManager)getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);String ssid=wifi==null?null:wifi.getConnectionInfo().getSSID();if(ssid!=null&&!"<unknown ssid>".equals(ssid))label=ssid.replace("\"","");}catch(SecurityException ignored){}
        }}pill.setContentDescription(label);header.setPillIcon(pill,icon);
    }
    void resume(){if(active)return;active=true;IntentFilter filter=new IntentFilter(Intent.ACTION_TIME_CHANGED);filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);getContext().registerReceiver(changes,filter);tick.run();if(getWidth()>0)loadLocalScene();}
    void pause(){if(!active)return;active=false;++generation;getContext().unregisterReceiver(changes);main.removeCallbacks(tick);}
    void destroy(){pause();++generation;worker.shutdownNow();wallpaper.setImageDrawable(null);dockBackground=null;band.removeAllViews();}
    private void loadLocalScene(){
        final int request=++generation;final Context context=getContext();
        android.app.ActivityManager memory=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        int limit=memory!=null&&memory.isLowRamDevice()?720:1280;
        final int width=Math.min(limit,Math.max(360,getWidth()));
        worker.execute(()->{
            if(request!=generation)return;
            WallpaperStore store=WallpaperStore.get(context);WallpaperCatalog catalog=store.snapshot();WallpaperCatalog.Entry entry=catalog.find(catalog.current);Bitmap background=null;
            if(entry!=null&&entry.video()&&entry.custom()&&!store.poster(entry.id).isFile())entry=catalog.find(catalog.staticBeforeVideo);
            if(entry!=null&&!WallpaperCatalog.DEFAULT.equals(entry.id))try{background=WallpaperImages.decode(context,store,entry,width,width*9/16);}catch(java.io.IOException ignored){}
            if(request!=generation)return;
            ArrayList<String> packages=new DockStore(context).load();ArrayList<Bitmap> art=new ArrayList<>();ArrayList<String> names=new ArrayList<>();ArrayList<HomeAppCatalog.Entry> catalogApps=new HomeAppCatalog(context).load(packages);
            for(String pkg:packages)for(HomeAppCatalog.Entry app:catalogApps)if(pkg.equals(app.id)){names.add(app.label);art.add(app.info==null?HomeEntryArtwork.render(context,app.label,app.id):AppArtwork.load(context,app.info));break;}
            Bitmap image=background,blurred=background==null?null:DockBackdropDrawable.prepare(background,960,540);main.post(()->{
                if(!active||request!=generation)return;
                dockBackground=blurred;wallpaper.setImageBitmap(image);shade.setVisibility(image==null?GONE:VISIBLE);ambient.setVisibility(image==null?VISIBLE:GONE);band.removeAllViews();
                title.setText(names.isEmpty()?chinese?"添加常用应用":"Add favorite apps":names.get(0));
                int color=art.isEmpty()?0xff666972:HomeActivity.sampleColor(art.get(0));ambient.setColor(color);
                boolean empty=art.isEmpty();int count=empty?1:art.size();band.setBackground(empty?null:dockSurface);
                float cardWidth=(864-36-4*18)/5f,cardHeight=cardWidth*203/360;
                FrameLayout.LayoutParams bandParams=(FrameLayout.LayoutParams)band.getLayoutParams();bandParams.height=Math.round(cardHeight+36);bandParams.width=Math.round(36+count*cardWidth+Math.max(0,count-1)*18);bandParams.leftMargin=Math.round((960-bandParams.width)/2f);band.setLayoutParams(bandParams);
                for(int i=0;i<count;i++){View card;if(empty)card=emptyCard();else{ImageView imageCard=new ImageView(context);imageCard.setImageBitmap(art.get(i));imageCard.setScaleType(ImageView.ScaleType.FIT_XY);GradientDrawable outline=new GradientDrawable();outline.setColor(Color.TRANSPARENT);outline.setCornerRadius(11);imageCard.setBackground(outline);UiCompat.clipOutline(imageCard);card=imageCard;}FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(cardWidth),Math.round(cardHeight));p.leftMargin=Math.round(18+i*(cardWidth+18));p.topMargin=18;band.addView(card,p);}
            });
        });
    }
    private void drawWallpaper(Canvas canvas,Paint paint){
        canvas.drawColor(Color.BLACK);paint.setAlpha(255);paint.setShader(null);
        if(dockBackground==null)ambient.draw(canvas);else{canvas.drawBitmap(dockBackground,null,new RectF(0,0,960,540),paint);shade.draw(canvas);}
    }
    private View emptyCard(){return new View(getContext()){
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final Path clip=new Path();final RectF rect=new RectF();final int[] location=new int[2],origin=new int[2];
        @Override protected void onDraw(Canvas canvas){
            rect.set(0,0,getWidth(),getHeight());clip.reset();clip.addRoundRect(rect,16,16,Path.Direction.CW);int saved=canvas.save();canvas.clipPath(clip);
            getLocationInWindow(location);scene.getLocationInWindow(origin);int background=canvas.save();canvas.translate((origin[0]-location[0])/scene.getScaleX(),(origin[1]-location[1])/scene.getScaleY());drawWallpaper(canvas,paint);canvas.restoreToCount(background);
            paint.setColor(0x1fffffff);canvas.drawRect(rect,paint);float center=getWidth()/2f,circle=16,gap=10;
            paint.setTypeface(typeface);paint.setTextSize(14);paint.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics metrics=paint.getFontMetrics();float top=(rect.height()-2*circle-gap-(metrics.descent-metrics.ascent))/2f,cy=top+circle;
            paint.setColor(0x0fffffff);canvas.drawCircle(center,cy,circle,paint);paint.setColor(Color.WHITE);paint.setStrokeWidth(1.5f);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawLine(center-6,cy,center+6,cy,paint);canvas.drawLine(center,cy-6,center,cy+6,paint);paint.setStrokeCap(Paint.Cap.BUTT);
            String label=chinese?"添加常用应用":"Add favorite apps";paint.setTextSize(Math.min(14,14*(getWidth()-20)/paint.measureText(label)));metrics=paint.getFontMetrics();canvas.drawText(label,center,top+2*circle+gap-metrics.ascent,paint);
            float stroke=1,inset=stroke/2;rect.inset(inset,inset);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(0x38ffffff);canvas.drawRoundRect(rect,16-inset,16-inset,paint);paint.setStyle(Paint.Style.FILL);canvas.restoreToCount(saved);
        }
    };}
}
