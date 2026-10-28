package home.quiet.tv;

import android.animation.ValueAnimator;
import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Native full-screen picker with separate preview and committed wallpaper identities. */
final class WallpaperController {
    private static final long ROTATION_MS=5*60*1000L;
    private static final int CARD_RADIUS=16,CARD_INSET=5,CARD_STROKE=2;
    private final Activity activity;
    private final FrameLayout root;
    private final WallpaperStore store;
    private final WallpaperBackdropCache backdropCache;
    private final WallpaperUploadPanel uploads;
    private final DailyWallpaperController daily;
    private final float unit,homeUnit;
    private final WallpaperUi ui;
    private final boolean chinese;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final ImageView current,incoming;
    private final View shade;
    private final Ambient ambient;
    private final VideoWallpaperPlayer video;
    private String playingVideo="",failedVideo="";
    private VideoWallpaperPlayer.Failure videoFailure=VideoWallpaperPlayer.Failure.UNKNOWN;
    private int videoRequest;
    private boolean commitPending,leaving,destroyed;
    private String posterId;
    private Bitmap poster,posterBlur,restoredCover;
    private boolean dynamicTab;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(6*1024){@Override protected int sizeOf(String id,Bitmap bitmap){return bitmap.getByteCount()/1024;}};
    private final Map<String,View> cards=new LinkedHashMap<>();
    private Bitmap entry,currentBlur,incomingBlur;
    private Runnable dockInvalidation;
    private final RectF dockScreen=new RectF();
    private volatile int request;
    private int pickerVersion,themeColor=0xff557eac;
    private String preview,shown,shownKey;
    private boolean active;
    private Dialog picker,confirmation;
    private FrameLayout content;
    private LinearLayout rail,actions;
    private HorizontalScrollView scroll;
    private View pendingCardFocus;
    private TextView title,subtitle,auto,upload,staticTab,videoTab,dynamicToggle;
    private String pressedWallpaper;
    private int pressedKey=KeyEvent.KEYCODE_UNKNOWN;
    private long pressedAt;
    private boolean longPressed;
    private final Runnable holdCard=()->{
        View card=cards.get(pressedWallpaper);
        if(card==null||!card.hasFocus()||picker==null||!picker.isShowing())return;
        longPressed=true;confirmDelete(pressedWallpaper,pressedKey,pressedAt);
    };
    private ValueAnimator transition,videoFade;
    private Runnable pendingCover,coverCommit,pendingVideoStart;
    private ViewTreeObserver coverTree;
    private Runnable coverDraw,startupDraw;
    private final Runnable rotate=this::rotateWallpaper;
    private void rotateWallpaper(){try{store.next();display(store.snapshot().current,true);}catch(IOException e){notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}schedule();}

    WallpaperController(Activity activity,FrameLayout root,float unit,boolean chinese){
        this.activity=activity;this.root=root;this.homeUnit=unit;this.unit=unit*960f/1040f;this.ui=new WallpaperUi(activity,this.unit);this.chinese=chinese;store=WallpaperStore.get(activity);uploads=new WallpaperUploadPanel(activity,store,chinese);
        backdropCache=new WallpaperBackdropCache(activity);daily=new DailyWallpaperController(activity,store,chinese,this::dailyChanged);
        FrameLayout background=new FrameLayout(activity);root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        video=new VideoWallpaperPlayer(activity,background);
        current=image();current.setBackgroundColor(Color.BLACK);incoming=image();incoming.setAlpha(0f);background.addView(current,new FrameLayout.LayoutParams(-1,-1));background.addView(incoming,new FrameLayout.LayoutParams(-1,-1));
        shade=new View(activity);shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));background.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ambient=new Ambient(activity,homeUnit);ambient.colorChanged=this::invalidateDock;background.addView(ambient,new FrameLayout.LayoutParams(-1,-1));ambient.setColor(themeColor);ambient.setVisibility(View.VISIBLE);
        String id=store.snapshot().current;
        android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
        WallpaperBackdropCache.Picture cached=WallpaperCatalog.DEFAULT.equals(id)?null:backdropCache.read(WallpaperBackdropCache.key(store,id,screen.widthPixels,screen.heightPixels));
        if(cached!=null){
            restoredCover=cached.image;
            // The cached cover is a startup placeholder, not the full-resolution static image.
            current.setImageBitmap(cached.image);currentBlur=cached.blur;shown=id;ambient.setVisibility(View.INVISIBLE);
            WallpaperCatalog.Entry selected=store.snapshot().find(id);
            if(selected!=null&&selected.video()){posterId=id;poster=cached.image;posterBlur=cached.blur;}
            // Install before the window's first draw; the regular cover takes over immediately.
            BitmapDrawable backgroundImage=new BitmapDrawable(activity.getResources(),cached.image);backgroundImage.setGravity(Gravity.FILL);
            activity.getWindow().setBackgroundDrawable(new LayerDrawable(new Drawable[]{backgroundImage,new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15})}));
            startupDraw=UiCompat.onDraw(root,()->root.post(this::finishStartupCover));
            DiagnosticLog.info("wallpaper.backdrop.restored","prepared cover");
        }
        display(id,false);
    }
    private void finishStartupCover(){if(startupDraw!=null){startupDraw.run();startupDraw=null;activity.getWindow().setBackgroundDrawable(null);}}
    private int px(float value){return Math.round(value*unit);}
    private String tr(String zh,String en){return chinese?zh:en;}
    private void notice(String message){Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();}
    void setThemeColor(int color){themeColor=color;if(ambient.getVisibility()==View.VISIBLE)ambient.setColor(color);}
    void observeDock(Runnable invalidation){dockInvalidation=invalidation;}
    private void invalidateDock(){if(dockInvalidation!=null)dockInvalidation.run();}
    void drawDockBackground(Canvas canvas,Paint paint){
        canvas.drawColor(Color.BLACK);
        if(ambient.getVisibility()==View.VISIBLE){ambient.draw(canvas);return;}
        dockScreen.set(0,0,root.getWidth(),root.getHeight());paint.setAlpha(255);
        if(currentBlur!=null)canvas.drawBitmap(currentBlur,null,dockScreen,paint);
        if(incomingBlur!=null){paint.setAlpha(Math.round(incoming.getAlpha()*255));canvas.drawBitmap(incomingBlur,null,dockScreen,paint);paint.setAlpha(255);}
        if(shade.getVisibility()==View.VISIBLE)shade.draw(canvas);
    }
    private ImageView image(){ImageView view=new ImageView(activity);view.setScaleType(ImageView.ScaleType.CENTER_CROP);androidx.core.view.ViewCompat.setImportantForAccessibility(view,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;}
    private void cacheBackdrop(String id,Bitmap image,Bitmap blur){
        if(image==null||blur==null||image==restoredCover||!id.equals(store.snapshot().current))return;
        restoredCover=null;
        android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();int width=screen.widthPixels,height=screen.heightPixels;
        String key=WallpaperBackdropCache.key(store,id,width,height);
        decoder.execute(()->{
            if(!id.equals(store.snapshot().current)||!key.equals(WallpaperBackdropCache.key(store,id,width,height)))return;
            try{Bitmap cover=WallpaperBackdropCache.cover(image,width,height);backdropCache.write(key,cover,blur);cover.recycle();}
            catch(RuntimeException|OutOfMemoryError e){DiagnosticLog.info("wallpaper.backdrop.save_failed","cover preparation");}
        });
    }
    private void display(String id,boolean animate){display(id,animate,null);}
    private void display(String id,boolean animate,Runnable prepared){
        if(destroyed||leaving)return;
        WallpaperCatalog.Entry selected=store.snapshot().find(id);
        if(selected!=null&&selected.video()&&id.equals(playingVideo)&&(prepared==null||video.requested(id))){if(prepared!=null)prepared.run();return;}
        final int version=++request;
        if(transition!=null){transition.end();transition=null;}
        if(WallpaperCatalog.DEFAULT.equals(id)){
            current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;shade.setVisibility(View.INVISIBLE);ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shown=id;shownKey=id;invalidateDock();stopVideo();return;
        }
        WallpaperCatalog state=store.snapshot();
        if(selected!=null&&selected.video()){
            if(id.equals(posterId)&&poster!=null){showPoster(id,poster,posterBlur,prepared);return;}
            WallpaperCatalog.Entry posterEntry=!selected.custom()||store.poster(id).isFile()?selected:state.find(state.staticBeforeVideo);
            // Keep the last valid background while the new cover is decoded.
            decoder.execute(()->{try{
                if(version!=request)return;
                Bitmap image=null,blurred=null;
                if(posterEntry!=null&&!WallpaperCatalog.DEFAULT.equals(posterEntry.id))try{image=WallpaperImages.decode(activity,store,posterEntry,960,540);}catch(IOException ignored){}
                if(version!=request)return;
                if(image!=null){android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();blurred=DockBackdropDrawable.prepare(image,screen.widthPixels,screen.heightPixels);}
                Bitmap ready=image,blur=blurred;
                main.post(()->{if(version!=request||destroyed||UiCompat.destroyed(activity))return;if(ready!=null){posterId=id;poster=ready;posterBlur=blur;}showPoster(id,ready,blur,prepared);});
            }catch(OutOfMemoryError e){main.post(()->{if(version==request&&!destroyed)showPoster(id,null,null,prepared);});}});return;
        }
        String key=store.imageKey(id);
        if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(state.dailyImages.get(state.dailySource))){
            if(shown==null)display(state.lastLocal,false);return;
        }
        if(id.equals(shown)&&key.equals(shownKey)&&current.getDrawable()!=null){ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);invalidateDock();stopVideo();return;}
        WallpaperCatalog.Entry wallpaper=state.find(id);if(wallpaper==null){display(WallpaperCatalog.DEFAULT,false);return;}
        decoder.execute(()->{
            if(version!=request)return;
            try{
                android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,screen.widthPixels,screen.heightPixels);
                if(version!=request)return;
                Bitmap blurred=DockBackdropDrawable.prepare(bitmap,screen.widthPixels,screen.heightPixels);
                main.post(()->{
                    if(version!=request||destroyed||UiCompat.destroyed(activity))return;
                    ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
                    if(!animate||shown==null||WallpaperCatalog.DEFAULT.equals(shown)){current.setImageBitmap(bitmap);currentBlur=blurred;incomingBlur=null;shown=id;shownKey=key;invalidateDock();}
                    else{
                        incoming.setImageBitmap(bitmap);incomingBlur=blurred;incoming.setAlpha(0f);
                        transition=ValueAnimator.ofFloat(0,1);transition.setDuration(450);
                        transition.addUpdateListener(a->{float fraction=(float)a.getAnimatedValue();incoming.setAlpha(fraction);if(fraction==1f){current.setImageBitmap(bitmap);currentBlur=incomingBlur;incomingBlur=null;incoming.setImageDrawable(null);incoming.setAlpha(0f);shown=id;shownKey=key;}invalidateDock();});transition.start();
                    }
                    cacheBackdrop(id,bitmap,blurred);stopVideo();
                });
            }catch(IOException|OutOfMemoryError e){DiagnosticLog.error("wallpaper.decode.failed",e);main.post(()->{if(version!=request||destroyed)return;if(WallpaperCatalog.DAILY.equals(id)){daily.cacheUnreadable();if(current.getDrawable()==null)display(store.snapshot().lastLocal,false);return;}notice(tr("无法读取这张壁纸","Unable to read this wallpaper"));if(id.equals(store.snapshot().current))try{store.select(WallpaperCatalog.DEFAULT);}catch(IOException ignored){}display(WallpaperCatalog.DEFAULT,false);});}
        });
    }
    private void showPoster(String id,Bitmap image,Bitmap blur,Runnable prepared){
        if(image!=null){current.setImageBitmap(image);currentBlur=blur;ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);}
        else if(current.getDrawable()==null){ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shade.setVisibility(View.INVISIBLE);}
        incoming.setImageDrawable(null);incoming.setAlpha(0f);incomingBlur=null;shown=id;shownKey=id;invalidateDock();
        cacheBackdrop(id,image,blur);if(prepared!=null)prepared.run();else stopVideo();
    }
    private void withCover(Runnable action){withCover(action,true);}
    private void withCover(Runnable action,boolean awaitFrame){
        cancelCover();cancelVideoFade();current.setAlpha(1f);
        if(current.getDrawable()==null&&ambient.getVisibility()!=View.VISIBLE){ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shade.setVisibility(View.INVISIBLE);}
        // A native Surface is independent of the cover. Submit the opaque cover before changing it.
        // Covered/paused windows may not draw; they must never hold up navigation or decoder release.
        if(!awaitFrame||!active||!root.isShown()||!root.hasWindowFocus()||root.getWindowVisibility()!=View.VISIBLE){action.run();return;}
        final int version=videoRequest;
        pendingCover=()->{cancelCover();if(version==videoRequest&&!destroyed)action.run();};
        Runnable pending=pendingCover;coverTree=root.getViewTreeObserver();
        if(Build.VERSION.SDK_INT>=29&&root.isHardwareAccelerated()){
            coverCommit=()->main.post(()->{if(pendingCover==pending)pending.run();});
            coverTree.registerFrameCommitCallback(coverCommit);
        }else{
            coverDraw=UiCompat.onDraw(root,()->androidx.core.view.ViewCompat.postOnAnimation(root,()->{if(pendingCover==pending)pending.run();}));
        }
        root.invalidate();
    }
    private void cancelCover(){
        pendingCover=null;
        if(coverTree!=null&&coverTree.isAlive()){
            if(coverDraw!=null)coverDraw.run();
            if(Build.VERSION.SDK_INT>=29&&coverCommit!=null)coverTree.unregisterFrameCommitCallback(coverCommit);
        }
        coverTree=null;coverDraw=null;coverCommit=null;
    }
    void windowFocusChanged(boolean focused){
        if(!focused&&pendingCover!=null)pendingCover.run();
        if(focused&&pendingVideoStart!=null){Runnable start=pendingVideoStart;pendingVideoStart=null;start.run();}
    }
    private void cancelVideoFade(){if(videoFade!=null){videoFade.cancel();videoFade.removeAllUpdateListeners();videoFade=null;}}
    private void revealVideo(int version){
        cancelVideoFade();videoFade=ValueAnimator.ofFloat(current.getAlpha(),0f);videoFade.setDuration(200);
        videoFade.addUpdateListener(a->{if(version==videoRequest&&active&&!destroyed)current.setAlpha((float)a.getAnimatedValue());});videoFade.start();
    }
    void beforeLeave(Runnable launch){
        if(destroyed||leaving)return;
        if(!video.visible()){launch.run();return;}
        leaving=true;++request;final int version=videoRequest;String id=playingVideo;
        video.snapshot(image->{
            if(destroyed||!active||version!=videoRequest){if(image!=null)image.recycle();leaving=false;return;}
            if(image==null){finishLeave(launch);return;}
            posterId=id;poster=image;posterBlur=currentBlur;current.setImageBitmap(image);invalidateDock();
            // Navigation waits for the cover, never for blur work or disk compression.
            decoder.execute(()->{
                try{
                    android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                    Bitmap blur=DockBackdropDrawable.prepare(image,screen.widthPixels,screen.heightPixels);
                    main.post(()->{
                        if(destroyed||poster!=image){blur.recycle();return;}
                        posterBlur=blur;
                        if(id.equals(shown)&&current.getDrawable() instanceof BitmapDrawable&&((BitmapDrawable)current.getDrawable()).getBitmap()==image){currentBlur=blur;invalidateDock();}
                        cacheBackdrop(id,image,blur);
                    });
                }catch(RuntimeException|OutOfMemoryError e){DiagnosticLog.info("wallpaper.backdrop.save_failed","snapshot blur");}
            });
            finishLeave(launch);
        });
    }
    private void finishLeave(Runnable launch){stopVideo(()->{leaving=false;if(active&&!destroyed)launch.run();});}
    private void restoreSelected(){String id=store.snapshot().current;if(store.snapshot().dynamic()&&!id.equals(failedVideo))startVideo(id,false);else display(id,false);}
    private void schedule(){main.removeCallbacks(rotate);WallpaperCatalog state=store.snapshot();if(active&&state.rotating&&picker==null)main.postDelayed(rotate,ROTATION_MS);}
    void resume(){active=true;leaving=false;video.setPaused(false);restoreSelected();if(video.ready()&&videoFade==null&&current.getAlpha()>0f)revealVideo(videoRequest);daily.resume();schedule();}
    void environmentChanged(){daily.environmentChanged();}
    private void dailyChanged(){
        if(picker!=null){
            if(daily.busy()){details();return;}
            boolean cardFocused=false;for(View card:cards.values())if(card.hasFocus())cardFocused=true;
            refreshCards(preview,cardFocused);
        }else if(active)display(store.snapshot().current,true);
    }
    void pause(){active=false;leaving=false;++request;cancelCover();pendingVideoStart=null;cancelVideoFade();video.setPaused(true);daily.pause();cancelCardPress();main.removeCallbacks(rotate);uploads.close();if(confirmation!=null)confirmation.dismiss();if(picker!=null)picker.dismiss();if(transition!=null)transition.end();}
    void hidden(){stopVideo();}
    void destroy(){destroyed=true;finishStartupCover();++request;pendingVideoStart=null;cancelCover();cancelVideoFade();video.destroy();poster=null;posterBlur=null;restoredCover=null;daily.destroy();uploads.destroy();decoder.shutdownNow();main.removeCallbacksAndMessages(null);if(transition!=null){transition.cancel();transition.removeAllUpdateListeners();}current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;dockInvalidation=null;thumbnails.evictAll();}

    synchronized Bitmap entryBanner(){
        if(entry==null)entry=HomeEntryArtwork.render(activity,tr("壁纸设置","Wallpaper Settings"),HomeAppCatalog.WALLPAPER);
        return entry;
    }
    private TextView label(String value,int size){return ui.text(value,size);}
    private GradientDrawable border(boolean focus,boolean primary){
        GradientDrawable border=ui.surface(primary?0xfff1f2f4:0xff20252d,0,focus?Color.WHITE:0x30ffffff,CARD_STROKE);
        // GradientDrawable insets its stroke path by half the stroke width.
        border.setCornerRadius(px(CARD_RADIUS)-px(CARD_STROKE)/2f);return border;
    }
    private TextView button(String value,boolean primary,Runnable action){return ui.button(value,primary,action);}
    private void gap(LinearLayout parent,int n){parent.addView(new View(activity),new LinearLayout.LayoutParams(px(n),1));}
    private Drawable toggleDrawable(boolean checked){
        Drawable drawable=new Drawable(){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){Rect b=getBounds();float radius=b.height()/2f;paint.setColor(checked?0xff608975:0xff626a75);canvas.drawRoundRect(new RectF(b),radius,radius,paint);paint.setColor(Color.WHITE);float inset=px(2);canvas.drawCircle(checked?b.right-radius:b.left+radius,b.centerY(),radius-inset,paint);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };drawable.setBounds(0,0,px(28),px(16));return drawable;
    }
    private LinearLayout row(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private void runSaved(IoAction action){try{action.run();}catch(IOException e){DiagnosticLog.error("wallpaper.operation.failed",e);notice(tr("操作失败，请检查电视存储后重试","Operation failed. Check TV storage and retry."));}}
    private interface IoAction {void run()throws IOException;}

    void showPicker(){
        if(picker!=null||leaving)return;stopVideo();main.removeCallbacks(rotate);preview=store.snapshot().current;dynamicTab=store.snapshot().dynamic();
        for(int n=1;n<root.getChildCount();n++)root.getChildAt(n).setVisibility(View.INVISIBLE);
        picker=new androidx.activity.ComponentDialog(activity){@Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)focusPendingCard();else cancelCardPress();}};
        ((androidx.activity.ComponentDialog)picker).getOnBackPressedDispatcher().addCallback((androidx.activity.ComponentDialog)picker,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){handlePickerBack();}});
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);content=new FrameLayout(activity);
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(px(36),px(32),px(36),px(32));content.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();TextView back=button("",false,this::handlePickerBack);back.setPadding(px(10),px(10),px(10),px(10));back.setCompoundDrawables(ui.icon(R.drawable.wp_chevron_left,17,WallpaperUi.INK),null,null,null);back.setBackgroundDrawable(ui.focusSurface(0xa6111924,100));back.setContentDescription(tr("返回桌面","Back to home"));header.addView(back,new LinearLayout.LayoutParams(px(37),px(37)));gap(header,13);
        TextView heading=label(tr("壁纸设置","Wallpaper Settings"),28);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout categories=row();categories.setPadding(px(4),px(4),px(4),px(4));categories.setBackgroundDrawable(ui.surface(0xff242a33,100,0x38ffffff,1));
        staticTab=ui.categoryTab(tr("静态壁纸","Static"),()->category(false));videoTab=ui.categoryTab(tr("动态壁纸","Video"),()->category(true));
        categories.addView(staticTab,new LinearLayout.LayoutParams(px(78),px(30)));gap(categories,4);categories.addView(videoTab,new LinearLayout.LayoutParams(px(78),px(30)));
        staticTab.setNextFocusLeftId(back.getId());staticTab.setNextFocusRightId(videoTab.getId());videoTab.setNextFocusLeftId(staticTab.getId());back.setNextFocusRightId(staticTab.getId());
        header.addView(categories);gap(header,8);
        auto=button("",false,()->runSaved(()->{WallpaperCatalog s=store.snapshot();daily.cancel();store.rotation(!s.rotating);preview=store.snapshot().current;display(preview,true);refreshCards(preview,false);}));header.addView(auto);
        dynamicToggle=button("",false,this::toggleVideo);header.addView(dynamicToggle);
        gap(header,8);upload=button(tr("上传壁纸","Upload"),false,()->{daily.cancel();stopVideo();uploads.show(WallpaperUi.backdrop(root,content),id->{if(picker!=null){if(id!=null){preview=id;WallpaperCatalog.Entry added=store.snapshot().find(id);dynamicTab=added!=null&&added.video();}refreshCards(preview,id!=null);}});});ui.icon(upload,R.drawable.wp_upload);header.addView(upload);
        layout.addView(header);
        layout.addView(new View(activity),new LinearLayout.LayoutParams(1,0,1));
        actions=row();LinearLayout titles=new LinearLayout(activity);titles.setOrientation(LinearLayout.VERTICAL);title=label("",27);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(title,new LinearLayout.LayoutParams(-1,-2));
        subtitle=label("",12);subtitle.setTextColor(WallpaperUi.MUTED);subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);subtitle.setPadding(0,px(7),0,0);titles.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));actions.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        // Together with the rail's top padding and focused-card lift, leave about 36px at 1080p.
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.setMargins(px(4),0,px(4),px(16));layout.addView(actions,ap);
        scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);rail=row();rail.setClipToPadding(false);rail.setClipChildren(false);rail.setGravity(Gravity.BOTTOM);rail.setPadding(px(4),px(7),px(4),0);scroll.addView(rail);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,px(154));layout.addView(scroll,rp);
        picker.setContentView(content);picker.setOnDismissListener(d->{commitPending=false;cancelCardPress();pendingCardFocus=null;++pickerVersion;uploads.close();picker=null;daily.cancel();cards.clear();for(int n=1;n<root.getChildCount();n++)root.getChildAt(n).setVisibility(View.VISIBLE);shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));if(active)restoreSelected();schedule();});
        Window window=picker.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setSystemUiVisibility(5894);window.setDimAmount(0);}
        shade.setBackgroundDrawable(WallpaperUi.shade());picker.show();if(window!=null)window.setLayout(-1,-1);refreshCards(preview,true);
    }
    private void stopVideo(){stopVideo(null);}
    private void stopVideo(Runnable after){
        ++videoRequest;playingVideo="";commitPending=false;pendingVideoStart=null;
        withCover(()->{video.stop();if(after!=null)after.run();},video.visible());
    }
    private void startVideo(String id,boolean commit){
        WallpaperCatalog.Entry entry=store.snapshot().find(id);if(entry==null||!entry.video()||!active||leaving)return;
        if(id.equals(playingVideo)&&video.requested(id)){commitPending|=commit;if(commitPending&&video.ready()){commitPending=false;commitVideo(id);}return;}
        cancelVideoFade();pendingVideoStart=null;failedVideo="";playingVideo=id;commitPending=commit;final int version=++videoRequest;
        display(id,false,()->{
            if(version!=videoRequest||!active||destroyed)return;
            Runnable start=()->withCover(()->{
                if(version!=videoRequest||!active||destroyed)return;
                video.play(entry,new VideoWallpaperPlayer.Listener(){
                    @Override public void stopped(){if(version==videoRequest){cancelVideoFade();current.setAlpha(1f);}}
                    @Override public void ready(){
                        if(version!=videoRequest||!active||destroyed)return;
                        ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);revealVideo(version);
                        if(commitPending){commitPending=false;commitVideo(id);}else if(picker!=null)details();
                    }
                    @Override public void failed(VideoWallpaperPlayer.Failure reason){if(version!=videoRequest)return;failedVideo=id;videoFailure=reason;stopVideo();if(picker!=null)details();notice(reason.hint(chinese));}
                });
            });
            // Resume may run before the window is drawable. Keep the cover until focus returns.
            if(!root.hasWindowFocus()&&picker==null)pendingVideoStart=start;else start.run();
        });
    }
    private void commitVideo(String id){
        try{daily.cancel();store.select(id);if(id.equals(posterId))cacheBackdrop(id,poster,posterBlur);if(picker!=null)refreshCards(id,cards.get(id)!=null&&cards.get(id).hasFocus());notice(tr("已应用动态壁纸","Video wallpaper applied"));}
        catch(IOException e){stopVideo();display(store.snapshot().current,false);notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}
    }
    private void applySelected(){WallpaperCatalog.Entry entry=store.snapshot().find(preview);if(entry==null)return;
        if(entry.video())startVideo(preview,true);
        else if(WallpaperCatalog.DAILY.equals(preview))daily.apply((success,message)->{if(picker!=null){refreshCards(WallpaperCatalog.DAILY,true);notice(success?tr("已应用每日壁纸","Daily wallpaper applied"):message);}});
        else runSaved(()->{daily.cancel();store.select(preview);display(preview,true);refreshCards(preview,true);notice(tr("已应用壁纸","Wallpaper applied"));});
    }
    private void category(boolean videos){if(dynamicTab==videos)return;stopVideo();dynamicTab=videos;refreshCards(store.snapshot().current,false);(videos?videoTab:staticTab).requestFocus();}
    private void toggleVideo(){
        WallpaperCatalog state=store.snapshot();if(state.dynamic()){stopVideo();runSaved(()->{store.stopVideo();dynamicTab=false;refreshCards(store.snapshot().current,true);daily.environmentChanged();schedule();});}
        else if(state.find(state.lastVideo)!=null){dynamicTab=true;refreshCards(state.lastVideo,false);startVideo(state.lastVideo,true);}
        else {dynamicTab=true;refreshCards(null,false);upload.requestFocus();}
    }
    private void handlePickerBack(){if(picker==null)return;commitPending=false;picker.dismiss();}
    private void refreshCards(String preferred,boolean focusCard){
        if(picker==null)return;cancelCardPress();pendingCardFocus=null;int version=++pickerVersion;WallpaperCatalog state=store.snapshot();
        List<WallpaperCatalog.Entry> entries=new ArrayList<>();for(WallpaperCatalog.Entry e:state.entries())if(e.video()==dynamicTab)entries.add(e);
        boolean found=false;for(WallpaperCatalog.Entry e:entries)if(e.id.equals(preferred))found=true;
        if(!found)preferred=entries.isEmpty()?null:entries.get(0).id;
        preview=preferred;
        rail.removeAllViews();cards.clear();
        for(WallpaperCatalog.Entry wallpaper:entries){
            String id=wallpaper.id;FrameLayout card=new FrameLayout(activity);card.setId(androidx.core.view.ViewCompat.generateViewId());card.setFocusable(true);card.setContentDescription(wallpaper.label(chinese));card.setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));card.setBackgroundDrawable(border(false,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(184),px(147));cp.rightMargin=px(14);rail.addView(card,cp);cards.put(id,card);
            ImageView image=ui.image(CARD_RADIUS,CARD_INSET);card.addView(image,new FrameLayout.LayoutParams(-1,px(95)));
            if(wallpaper.video()){image.setImageBitmap(defaultThumbnail());TextView play=label("▶",22);play.setGravity(Gravity.CENTER);androidx.core.view.ViewCompat.setImportantForAccessibility(play,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(play,new FrameLayout.LayoutParams(-1,px(95)));}
            if(WallpaperCatalog.DEFAULT.equals(id))image.setImageBitmap(defaultThumbnail());else{
                String thumbnailKey=store.imageKey(id);Bitmap cached=thumbnails.get(thumbnailKey);if(cached!=null)image.setImageBitmap(cached);
                else if(!WallpaperCatalog.DAILY.equals(id)||store.cached(state.dailyImages.get(state.dailySource)))decoder.execute(()->{try{Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,360,203);thumbnails.put(thumbnailKey,bitmap);main.post(()->{if(picker!=null&&version==pickerVersion)image.setImageBitmap(bitmap);});}catch(IOException|OutOfMemoryError ignored){}});
            }
            if(WallpaperCatalog.DAILY.equals(id)){
                if(!store.cached(state.dailyImages.get(state.dailySource)))image.setImageBitmap(defaultThumbnail());
                ImageView calendar=new ImageView(activity);calendar.setImageDrawable(ui.calendar(18));calendar.setPadding(px(7),px(7),px(7),px(7));calendar.setBackgroundDrawable(ui.surface(0xcc20252d,100,Color.TRANSPARENT,0));androidx.core.view.ViewCompat.setImportantForAccessibility(calendar,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(px(32),px(32),Gravity.TOP|Gravity.RIGHT);dp.setMargins(0,px(7),px(7),0);card.addView(calendar,dp);
            }
            LinearLayout caption=row();FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,px(34),Gravity.BOTTOM);np.leftMargin=px(4);np.rightMargin=px(4);card.addView(caption,np);
            TextView name=label(wallpaper.label(chinese),12);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            if(id.equals(state.current)){
                TextView mark=label(tr("当前使用","In use"),10);mark.setTextColor(0xff171d25);mark.setPadding(px(6),px(1),px(6),px(1));mark.setBackgroundDrawable(ui.surface(0xfff0f1f4,100,Color.TRANSPARENT,0));
                LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-2);mp.leftMargin=px(4);caption.addView(mark,mp);
            }
            card.setOnFocusChangeListener((v,focus)->{if(focus){if(!id.equals(preview))commitPending=false;preview=id;if(wallpaper.video())startVideo(id,false);else display(id,true);details();scroll.requestChildFocus(rail,card);}else if(id.equals(pressedWallpaper))cancelCardPress();});
            card.setOnClickListener(v->{commitPending=false;preview=id;applySelected();});
            card.setOnLongClickListener(v->{confirmDelete(id,KeyEvent.KEYCODE_UNKNOWN,0);return true;});
            card.setOnKeyListener((v,key,event)->{if(confirmKey(key))return cardKey(id,key,event);if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){cancelCardPress();List<String> ids=focusableCards();int index=ids.indexOf(id)+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);if(index>=0&&index<ids.size())cards.get(ids.get(index)).requestFocus();}return true;});
        }
        if(entries.isEmpty()){TextView empty=label(tr("上传视频作为动态壁纸","Upload a video wallpaper"),16);empty.setTextColor(WallpaperUi.MUTED);rail.addView(empty);display(store.snapshot().staticBeforeVideo,false);}else display(preview,true);
        details();if(focusCard)focusCard(preview);
    }
    private List<String> focusableCards(){List<String> ids=new ArrayList<>();for(Map.Entry<String,View> item:cards.entrySet())if(item.getValue().isFocusable())ids.add(item.getKey());return ids;}
    private void focusCard(String id){
        if(picker==null)return;View card=cards.get(id);
        pendingCardFocus=card!=null&&card.isFocusable()?card:upload;
        pendingCardFocus.post(this::focusPendingCard);
    }
    private void focusPendingCard(){
        // Dialog dismissal can regain window focus after its callback has returned.
        if(picker==null||pendingCardFocus==null||!pendingCardFocus.hasWindowFocus()||!pendingCardFocus.isShown())return;
        View target=pendingCardFocus;pendingCardFocus=null;target.requestFocusFromTouch();
    }
    private static boolean confirmKey(int key){return key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER||key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_BUTTON_SELECT;}
    private void cancelCardPress(){main.removeCallbacks(holdCard);View card=cards.get(pressedWallpaper);if(card!=null)card.setPressed(false);pressedWallpaper=null;pressedKey=KeyEvent.KEYCODE_UNKNOWN;longPressed=false;}
    private boolean cardKey(String id,int key,KeyEvent event){
        // Own the complete gesture so a long press cannot also apply on release.
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            if(event.getRepeatCount()==0){cancelCardPress();pressedWallpaper=id;pressedKey=key;pressedAt=event.getDownTime();cards.get(id).setPressed(true);main.postDelayed(holdCard,ViewConfiguration.getLongPressTimeout());}
        }else if(event.getAction()==KeyEvent.ACTION_UP){
            boolean click=id.equals(pressedWallpaper)&&key==pressedKey&&!longPressed&&!event.isCanceled();cancelCardPress();if(click)cards.get(id).performClick();
        }
        return true;
    }
    private void details(){
        if(picker==null)return;WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(preview);
        staticTab.setSelected(!dynamicTab);videoTab.setSelected(dynamicTab);
        videoTab.setNextFocusRightId(dynamicTab?dynamicToggle.getId():auto.getId());auto.setNextFocusLeftId(videoTab.getId());dynamicToggle.setNextFocusLeftId(videoTab.getId());
        dynamicToggle.setVisibility(dynamicTab?View.VISIBLE:View.GONE);dynamicToggle.setText(tr("启用动态","Enable video"));dynamicToggle.setCompoundDrawables(toggleDrawable(state.dynamic()),null,null,null);dynamicToggle.setCompoundDrawablePadding(px(7));dynamicToggle.setContentDescription(state.dynamic()?tr("启用动态，开","Enable video, on"):tr("启用动态，关","Enable video, off"));
        auto.setVisibility(dynamicTab?View.GONE:View.VISIBLE);
        if(entry==null){title.setText(tr("还没有动态壁纸","No video wallpapers"));subtitle.setVisibility(View.GONE);for(TextView control:new TextView[]{staticTab,videoTab,dynamicToggle,upload})control.setNextFocusDownId(upload.getId());return;}
        title.setText(entry.label(chinese));boolean isDaily=WallpaperCatalog.DAILY.equals(preview);
        subtitle.setVisibility(isDaily||entry.video()?View.VISIBLE:View.GONE);
        if(entry.video())subtitle.setText(preview.equals(failedVideo)?videoFailure.hint(chinese):entry.width+" × "+entry.height+"  "+Math.round(entry.fps)+" FPS  "+Math.round(entry.durationMs/1000f)+tr(" 秒"," s"));
        if(isDaily){
            WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);
            String description=WallpaperCatalog.sourceLabel(state.dailySource,chinese)+"  "+tr("每天自动更新","Updates daily");
            if(store.cached(image)&&!image.title.isEmpty())description+="  "+image.title;
            subtitle.setText(daily.busy()?daily.status(WallpaperCatalog.BING):description);
        }
        auto.setText(tr("壁纸轮播","Slideshow"));auto.setCompoundDrawables(toggleDrawable(state.rotating),null,null,null);auto.setCompoundDrawablePadding(px(7));auto.setContentDescription(state.rotating?tr("壁纸轮播，开","Slideshow, on"):tr("壁纸轮播，关","Slideshow, off"));auto.setEnabled(!WallpaperCatalog.DAILY.equals(state.current)&&state.pictures().size()>=2);auto.setAlpha(auto.isEnabled()?1:.4f);
        View card=cards.get(preview);if(card==null)return;
        for(Map.Entry<String,View> item:cards.entrySet()){boolean selected=item.getKey().equals(preview);item.getValue().setBackgroundDrawable(border(selected,false));item.getValue().setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));item.getValue().setTranslationY(selected?-px(3):0);item.getValue().setNextFocusUpId(upload.getId());}
        int lower=card.getId();
        for(TextView control:new TextView[]{auto,upload,staticTab,videoTab,dynamicToggle})control.setNextFocusDownId(lower);
    }
    private void confirmDelete(String id,int initiatingKey,long downTime){
        if(picker==null||confirmation!=null&&confirmation.isShowing())return;
        WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(id);if(entry==null)return;
        if(WallpaperCatalog.DAILY.equals(id))return;
        if(WallpaperCatalog.DEFAULT.equals(id)){notice(tr("默认颜色主题不能删除","The default color theme cannot be deleted"));return;}
        stopVideo();List<String> before=focusableCards();int index=before.indexOf(id);String[] returnTo={id};
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(tr("删除「","Delete “")+entry.label(chinese)+tr("」？","”?"))
            .setMessage(tr("删除电视中保存的壁纸，手机或电脑中的原文件不受影响。","Delete this TV copy. The original on your phone or computer is unchanged.")+(entry.id.equals(state.current)?entry.video()?tr("\n删除后恢复之前的静态壁纸。","\nRestores your previous static wallpaper."):state.rotating?tr("\n将切换到下一张有效图片；不足两张时关闭轮播。","\nSwitch to the next available photo; stop slideshow below two images."):tr("\n这张壁纸正在使用，删除后切回默认颜色主题。","\nThis wallpaper is in use. Deleting it restores the default color theme."):""))
            .setNegativeButton(tr("取消","Cancel"),null).setPositiveButton(tr("删除","Delete"),null).create();
        // The opening hold can continue in the new window. Consume its repeats and release.
        boolean[] releasing={initiatingKey!=KeyEvent.KEYCODE_UNKNOWN};
        dialog.setOnKeyListener((d,key,event)->{if(!releasing[0]||key!=initiatingKey)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&event.getDownTime()>downTime){releasing[0]=false;return false;}if(event.getAction()==KeyEvent.ACTION_UP)releasing[0]=false;return true;});
        dialog.setOnDismissListener(d->{if(confirmation==dialog)confirmation=null;if(active)focusCard(returnTo[0]);});
        confirmation=dialog;dialog.show();ControlStyle.dialog(dialog,unit);View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.post(()->{if(dialog.isShowing())cancel.requestFocusFromTouch();});
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                store.remove(entry.id);thumbnails.remove(entry.id);before.remove(entry.id);returnTo[0]=before.isEmpty()?WallpaperCatalog.DEFAULT:before.get(Math.max(0,Math.min(index,before.size()-1)));
                refreshCards(returnTo[0],false);dialog.dismiss();
            }catch(IOException e){notice(tr("删除失败，请检查电视存储后重试","Unable to delete. Check TV storage and retry."));}
        });
    }
    private Bitmap defaultThumbnail(){Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);Paint paint=new Paint(3);paint.setShader(new RadialGradient(116,59,232,new int[]{0xac557eac,0x40557eac,Color.TRANSPARENT},new float[]{0,.5f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,360,203,paint);return bitmap;}
    static final class Ambient extends View {
        private final float homeUnit;
        Runnable colorChanged;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
        Shader bloom,spill;int color=Color.TRANSPARENT,targetColor=Color.TRANSPARENT;ValueAnimator animation;
        final float[] stops={0,.12f,.26f,.42f,.58f,.74f,.88f,1};
        final float[] falloff={1,.96f,.80f,.54f,.28f,.10f,.015f,0};
        Ambient(android.content.Context context,float homeUnit){super(context);this.homeUnit=homeUnit;}
        void setColor(int value){
            float[] hsv=new float[3];Color.colorToHSV(value,hsv);
            hsv[1]=Math.min(.88f,hsv[1]);hsv[2]=.95f;value=Color.HSVToColor(hsv);
            if(value==targetColor)return;targetColor=value;
            if(animation!=null)animation.cancel();
            if(color==Color.TRANSPARENT){color=value;updateGlow();return;}
            animation=ValueAnimator.ofObject(new android.animation.ArgbEvaluator(),color,value);animation.setDuration(650);
            animation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animation.addUpdateListener(a->{color=(int)a.getAnimatedValue();updateGlow();});animation.start();
        }
        private Shader glow(float x,float y,float radius,float flatten,int alpha){
            int[] colors=new int[stops.length];
            for(int n=0;n<colors.length;n++)colors[n]=(color&0xffffff)|(Math.round(alpha*falloff[n])<<24);
            RadialGradient gradient=new RadialGradient(x*homeUnit,y*homeUnit,radius*homeUnit,colors,stops,Shader.TileMode.CLAMP);
            Matrix ellipse=new Matrix();ellipse.setScale(1,flatten,x*homeUnit,y*homeUnit);gradient.setLocalMatrix(ellipse);
            return gradient;
        }
        private void updateGlow(){
            bloom=glow(310,158,620,.68f,172);
            spill=glow(100,310,480,.72f,48);
            invalidate();if(colorChanged!=null)colorChanged.run();
        }
        @Override protected void onDraw(Canvas c){
            if(bloom==null)return;
            paint.setShader(bloom);c.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(spill);c.drawRect(0,0,getWidth(),getHeight(),paint);
        }
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }

}
