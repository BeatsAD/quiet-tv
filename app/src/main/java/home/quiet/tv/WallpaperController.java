package home.quiet.tv;

import android.animation.ValueAnimator;
import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Native full-screen picker with separate preview and committed wallpaper identities. */
final class WallpaperController {
    private static final int CARD_RADIUS=16,CARD_INSET=5,CARD_STROKE=2;
    private final Activity activity;
    private final FrameLayout root;
    private final WallpaperStore store;
    private final ScreensaverStore screensavers;
    private String temporaryMedia;
    private final WallpaperBackdropCache backdropCache;
    private final WallpaperUploadPanel uploads;
    private TextView soundToggle,localFile,applySaver,saverTime,saverWeather;
    private final Map<Integer,TextView> saverWaits=new LinkedHashMap<>();
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
    private boolean commitPending,leaving,destroyed,restoreDesktopPending;
    private int rotationCommit;
    private PopupWindow rotationMenu;
    private Bitmap restoredCover;
    private boolean dynamicTab;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(6*1024){@Override protected int sizeOf(String id,Bitmap bitmap){return bitmap.getByteCount()/1024;}};
    private final Map<String,View> cards=new LinkedHashMap<>();
    private final Map<View,Integer> pickerVisibility=new LinkedHashMap<>();
    private Bitmap entry,currentBlur,incomingBlur;
    private Runnable dockInvalidation;
    private final RectF dockScreen=new RectF();
    private volatile int request;
    private int saverApplyVersion;
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
    private void rotateWallpaper(){
        WallpaperCatalog state=store.snapshot();if(!active||temporaryMedia!=null||picker!=null||!state.rotating)return;
        if(state.dynamic()){WallpaperCatalog next=state.copy();next.next();startVideo(next.current,true,state.rotationMinutes);return;}
        try{store.next();display(store.snapshot().current,true);}catch(IOException e){notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}schedule();
    }

    WallpaperController(Activity activity,FrameLayout root,float unit,boolean chinese){
        this.activity=activity;this.root=root;this.homeUnit=unit;this.unit=unit*960f/1040f;this.ui=new WallpaperUi(activity,this.unit);this.chinese=chinese;store=WallpaperStore.get(activity);screensavers=ScreensaverStore.get(activity);uploads=new WallpaperUploadPanel(activity,store,chinese);
        backdropCache=new WallpaperBackdropCache(activity);daily=new DailyWallpaperController(activity,store,chinese,this::dailyChanged);
        FrameLayout background=new FrameLayout(activity);root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        video=new VideoWallpaperPlayer(activity,background,true);
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
            if(selected!=null&&selected.video())shownKey=store.imageKey(id);
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
        if(id.equals(shown)&&!store.imageKey(id).equals(shownKey))return;
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
            current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;shade.setVisibility(View.INVISIBLE);ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shown=id;shownKey=id;invalidateDock();stopVideo(prepared);return;
        }
        WallpaperCatalog state=store.snapshot();
        if(selected!=null&&selected.video()){
            String key=store.imageKey(id);
            if(id.equals(shown)&&key.equals(shownKey)&&current.getDrawable() instanceof BitmapDrawable){showPoster(id,((BitmapDrawable)current.getDrawable()).getBitmap(),currentBlur,key,prepared);return;}
            WallpaperCatalog.Entry posterEntry=!selected.custom()||store.poster(id).isFile()?selected:state.find(state.staticBeforeVideo);
            // Keep the last valid background while the new cover is decoded.
            decoder.execute(()->{try{
                if(version!=request)return;
                Bitmap image=null,blurred=null;
                if(posterEntry!=null&&!WallpaperCatalog.DEFAULT.equals(posterEntry.id))try{image=WallpaperImages.decode(activity,store,posterEntry,960,540);}catch(IOException ignored){}
                if(version!=request)return;
                if(image!=null){android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();blurred=DockBackdropDrawable.prepare(image,screen.widthPixels,screen.heightPixels);}
                Bitmap ready=image,blur=blurred;
                main.post(()->{if(version!=request||destroyed||UiCompat.destroyed(activity))return;showPoster(id,ready,blur,key,prepared);});
            }catch(OutOfMemoryError e){main.post(()->{if(version==request&&!destroyed)showPoster(id,null,null,key,prepared);});}});return;
        }
        String key=store.imageKey(id);
        if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(state.dailyImages.get(state.dailySource))){
            if(prepared!=null)display(WallpaperCatalog.DEFAULT,false,prepared);else if(shown==null)display(state.lastLocal,false);return;
        }
        if(id.equals(shown)&&key.equals(shownKey)&&current.getDrawable()!=null){ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);invalidateDock();stopVideo(prepared);return;}
        WallpaperCatalog.Entry wallpaper=state.find(id);if(wallpaper==null){display(WallpaperCatalog.DEFAULT,false,prepared);return;}
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
                    cacheBackdrop(id,bitmap,blurred);stopVideo(prepared);
                });
            }catch(IOException|OutOfMemoryError e){DiagnosticLog.error("wallpaper.decode.failed",e);main.post(()->{if(version!=request||destroyed)return;if(temporaryMedia!=null){display(WallpaperCatalog.DEFAULT,false,prepared);return;}if(WallpaperCatalog.DAILY.equals(id)){daily.cacheUnreadable();if(prepared!=null)display(WallpaperCatalog.DEFAULT,false,prepared);else if(current.getDrawable()==null)display(store.snapshot().lastLocal,false);return;}notice(tr("无法读取这张壁纸","Unable to read this wallpaper"));if(id.equals(store.snapshot().current))try{store.select(WallpaperCatalog.DEFAULT);}catch(IOException ignored){}display(WallpaperCatalog.DEFAULT,false,prepared);});}
        });
    }
    private void showPoster(String id,Bitmap image,Bitmap blur,String key,Runnable prepared){
        if(image!=null){current.setImageBitmap(image);currentBlur=blur;ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);}
        else if(current.getDrawable()==null){ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shade.setVisibility(View.INVISIBLE);}
        incoming.setImageDrawable(null);incoming.setAlpha(0f);incomingBlur=null;shown=id;shownKey=key;invalidateDock();
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
        if(focused&&active&&restoreDesktopPending){restoreDesktopPending=false;restoreSelected();}
        if(focused&&pendingVideoStart!=null){Runnable start=pendingVideoStart;pendingVideoStart=null;start.run();}
        if(focused&&active&&video.ready()&&videoFade==null&&current.getAlpha()>0f)revealVideo(videoRequest);
    }
    private void cancelVideoFade(){if(videoFade!=null){videoFade.cancel();videoFade.removeAllUpdateListeners();videoFade=null;}}
    private void revealVideo(int version){
        if(!active||destroyed||!root.isShown()||(!root.hasWindowFocus()&&(picker==null||!picker.isShowing())))return;
        cancelVideoFade();videoFade=ValueAnimator.ofFloat(current.getAlpha(),0f);videoFade.setDuration(200);
        videoFade.addUpdateListener(a->{if(version==videoRequest&&active&&!destroyed)current.setAlpha((float)a.getAnimatedValue());});videoFade.start();
    }
    void beforeLeave(Runnable launch){
        if(destroyed||leaving)return;
        if(!video.visible()){launch.run();return;}
        leaving=true;++request;
        // The existing poster/cache is already drawable. Navigation never waits for PixelCopy.
        finishLeave(launch);
    }
    private void finishLeave(Runnable launch){stopVideo(()->{leaving=false;if(active&&!destroyed)launch.run();});}
    private void restoreSelected(){String id=store.snapshot().current;if(store.snapshot().dynamic()&&!id.equals(failedVideo))startVideo(id,false);else display(id,false);}
    private void schedule(){main.removeCallbacks(rotate);WallpaperCatalog state=store.snapshot();if(active&&temporaryMedia==null&&state.rotating&&picker==null)main.postDelayed(rotate,state.rotationMinutes*60*1000L);}
    void resume(){active=true;restoreDesktopPending=false;video.setSound(store.snapshot().sound);leaving=false;video.setPaused(false);restoreSelected();if(video.ready()&&videoFade==null&&current.getAlpha()>0f)revealVideo(videoRequest);daily.resume();schedule();}
    void environmentChanged(){if(temporaryMedia==null)daily.environmentChanged();}
    boolean pickerShowing(){return picker!=null;}
    void showTemporary(String id,Runnable prepared){
        temporaryMedia=id;main.removeCallbacks(rotate);daily.pause();video.setSound(false);
        shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x33000000,Color.TRANSPARENT}));present(id,prepared);
    }
    void restoreDesktop(Runnable prepared){
        cancelTemporary();restoreDesktopPending=false;
        present(store.snapshot().current,()->{prepared.run();daily.resume();schedule();});
    }
    void cancelTemporary(){restoreDesktopPending|=temporaryMedia!=null;temporaryMedia=null;video.setSound(store.snapshot().sound);shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));}
    private void present(String id,Runnable prepared){
        WallpaperCatalog.Entry entry=store.snapshot().find(id);
        if(entry!=null&&entry.video())startVideo(id,false,prepared);else display(id,false,prepared);
    }
    private void dailyChanged(){
        if(temporaryMedia!=null)return;
        if(picker!=null){
            if(daily.busy()){details();return;}
            boolean cardFocused=false;for(View card:cards.values())if(card.hasFocus())cardFocused=true;
            refreshCards(preview,cardFocused);
        }else if(active)display(store.snapshot().current,true);
    }
    void pause(){active=false;leaving=false;++request;cancelCover();pendingVideoStart=null;cancelVideoFade();current.setAlpha(1f);video.setPaused(true);daily.pause();cancelCardPress();main.removeCallbacks(rotate);uploads.close();if(confirmation!=null)confirmation.dismiss();if(picker!=null)picker.dismiss();if(transition!=null)transition.end();}
    void hidden(){stopVideo();}
    void destroy(){destroyed=true;finishStartupCover();++request;pendingVideoStart=null;cancelCover();cancelVideoFade();video.destroy();restoredCover=null;daily.destroy();uploads.destroy();decoder.shutdownNow();main.removeCallbacksAndMessages(null);if(transition!=null){transition.cancel();transition.removeAllUpdateListeners();}current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;dockInvalidation=null;thumbnails.evictAll();}

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
    private String rotationLabel(int minutes){return minutes==0?tr("关闭","Off"):minutes==60?tr("1 小时","1 hour"):tr(minutes+" 分钟",minutes+" min");}
    private void showRotationMenu(){
        if(picker==null||rotationMenu!=null)return;
        WallpaperCatalog state=store.snapshot();List<WallpaperCatalog.Entry> pool=dynamicTab?state.videos():state.pictures();
        if(pool.size()<2){notice(tr(dynamicTab?"至少需要两个视频才能轮播":"至少需要两张图片才能轮播",dynamicTab?"Add at least two videos for rotation":"Add at least two photos for rotation"));return;}
        int selected=state.rotating&&state.dynamic()==dynamicTab?state.rotationMinutes:0;
        LinearLayout menu=new LinearLayout(activity);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(6),px(6),px(6),px(6));menu.setBackgroundDrawable(ui.surface(0xff242a33,14,0x38ffffff,1));
        PopupWindow popup=new PopupWindow(menu,Math.max(auto.getWidth(),px(150)),-2,true);rotationMenu=popup;
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);if(Build.VERSION.SDK_INT>=21)popup.setElevation(px(10));
        TextView focused=null;List<TextView> choices=new ArrayList<>();
        for(int minutes:new int[]{0,1,5,15,30,60}){
            TextView option=button(rotationLabel(minutes),false,()->{popup.dismiss();setRotation(minutes);});option.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);option.setBackgroundDrawable(ControlStyle.listRow(unit,10));option.setSelected(minutes==selected);option.setCompoundDrawables(null,null,minutes==selected?ui.icon(R.drawable.ic_wallpaper_checked,13,WallpaperUi.INK):null,null);option.setContentDescription(rotationLabel(minutes)+(minutes==selected?tr("，已选",", selected"):""));menu.addView(option,new LinearLayout.LayoutParams(-1,px(38)));choices.add(option);if(minutes==selected)focused=option;
        }
        choices.get(0).setNextFocusUpId(choices.get(0).getId());choices.get(choices.size()-1).setNextFocusDownId(choices.get(choices.size()-1).getId());
        popup.setOnDismissListener(()->{if(rotationMenu==popup)rotationMenu=null;auto.post(()->{if(picker!=null&&auto.isShown())auto.requestFocusFromTouch();});});
        popup.showAsDropDown(auto,0,px(5));if(focused!=null)focused.requestFocusFromTouch();
    }
    private void setRotation(int minutes){
        WallpaperCatalog state=store.snapshot();
        if(minutes==0){rotationCommit=0;commitPending=false;if(state.rotating&&state.dynamic()==dynamicTab)runSaved(()->{store.rotation(dynamicTab,0,preview);details();});return;}
        List<WallpaperCatalog.Entry> pool=dynamicTab?state.videos():state.pictures();if(pool.size()<2)return;
        WallpaperCatalog.Entry entry=state.find(preview);String first=entry!=null&&entry.custom()&&entry.video()==dynamicTab?preview:pool.get(0).id;
        if(dynamicTab)startVideo(first,true,minutes);
        else runSaved(()->{daily.cancel();store.rotation(false,minutes,first);preview=first;display(first,true);refreshCards(first,false);});
    }

    private ScreensaverSettings saverSettings(){
        try{return screensavers.snapshot(store.snapshot());}
        catch(IOException e){DiagnosticLog.error("screensaver.load.failed",e);return null;}
    }
    private void saveSaver(ScreensaverSettings next,boolean marks){
        if(next==null)return;
        try{screensavers.save(next);if(marks)refreshCards(preview,false);else updateSaverToolbar();}
        catch(IOException e){updateSaverToolbar();notice(tr("屏保设置保存失败，请检查存储空间","Unable to save screensaver settings. Check storage."));}
    }
    private void applySaver(){
        ScreensaverSettings state=saverSettings();String id=preview;if(state==null||id==null)return;int version=++saverApplyVersion;
        if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(store.snapshot().dailyImages.get(store.snapshot().dailySource))){
            daily.prepare((success,message)->{if(version!=saverApplyVersion||picker==null)return;if(success){ScreensaverSettings latest=saverSettings();if(latest!=null)saveSaver(latest.media(id),true);}else notice(message);});
        }else saveSaver(state.media(id),true);
    }
    private List<TextView> saverControls(){
        List<TextView> result=new ArrayList<>();result.add(applySaver);result.addAll(saverWaits.values());result.add(saverTime);result.add(saverWeather);return result;
    }
    private void addScreensaverToolbar(LinearLayout parent){
        LinearLayout bar=row();bar.setPadding(px(14),px(4),px(14),px(4));bar.setBackgroundDrawable(ui.capsule());
        applySaver=ui.compactButton(tr("设为屏保","Set saver"),this::applySaver);bar.addView(applySaver,new LinearLayout.LayoutParams(px(chinese?88:95),px(27)));gap(bar,16);
        TextView wait=label(tr("自动进入","Idle"),11);wait.setTextColor(WallpaperUi.MUTED);wait.setSingleLine(true);bar.addView(wait);gap(bar,8);
        LinearLayout choices=row();choices.setPadding(px(2),px(2),px(2),px(2));choices.setBackgroundDrawable(ui.surface(0x66101217,100,Color.TRANSPARENT,0));saverWaits.clear();
        for(int minutes:ScreensaverSettings.WAITS){
            TextView option=ui.compactButton(minutes==0?tr("关闭","Off"):String.valueOf(minutes),()->{ScreensaverSettings state=saverSettings();if(state!=null)saveSaver(state.waitMinutes(minutes),false);});
            option.setContentDescription(minutes==0?tr("关闭自动进入屏保","Disable automatic screensaver"):tr(minutes+" 分钟后进入屏保","Screensaver after "+minutes+" minutes"));
            ControlStyle.choice(option,unit,100,Color.TRANSPARENT,0);saverWaits.put(minutes,option);choices.addView(option,new LinearLayout.LayoutParams(px(minutes==0?37:30),px(23)));
        }
        bar.addView(choices);gap(bar,8);TextView minutes=label(tr("分钟","min"),10);minutes.setTextColor(WallpaperUi.MUTED);minutes.setSingleLine(true);bar.addView(minutes);gap(bar,16);
        saverTime=ui.compactButton(tr("时间","Time"),()->{ScreensaverSettings state=saverSettings();if(state!=null)saveSaver(state.time(!state.showTime),false);});
        saverWeather=ui.compactButton(tr("天气","Weather"),()->{ScreensaverSettings state=saverSettings();if(state!=null)saveSaver(state.weather(!state.showWeather),false);});
        for(TextView toggle:new TextView[]{saverTime,saverWeather}){toggle.setPadding(px(6),0,px(6),0);toggle.setBackgroundDrawable(ControlStyle.action(unit,100,Color.TRANSPARENT,0));bar.addView(toggle,new LinearLayout.LayoutParams(-2,px(27)));if(toggle==saverTime)gap(bar,12);}
        List<TextView> controls=saverControls();for(int n=0;n<controls.size();n++){TextView control=controls.get(n);control.setNextFocusLeftId(controls.get(Math.max(0,n-1)).getId());control.setNextFocusRightId(controls.get(Math.min(controls.size()-1,n+1)).getId());control.setNextFocusDownId(control.getId());}
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.gravity=Gravity.CENTER_HORIZONTAL;bp.topMargin=px(16);parent.addView(bar,bp);
    }
    private void updateSaverToolbar(){
        if(applySaver==null)return;ScreensaverSettings state=saverSettings();boolean enabled=state!=null;
        for(TextView control:saverControls())control.setEnabled(enabled);
        boolean selected=enabled&&state.mediaId.equals(preview);applySaver.setText(selected?tr("当前屏保","Current saver"):tr("设为屏保","Set saver"));applySaver.setEnabled(enabled&&preview!=null&&!selected);
        for(Map.Entry<Integer,TextView> option:saverWaits.entrySet())option.getValue().setSelected(enabled&&state.idleMinutes==option.getKey());
        for(TextView toggle:new TextView[]{saverTime,saverWeather}){
            boolean on=enabled&&(toggle==saverTime?state.showTime:state.showWeather);Drawable art=toggleDrawable(on);art.setBounds(0,0,px(22),px(13));toggle.setCompoundDrawables(null,null,art,null);toggle.setCompoundDrawablePadding(px(6));
            toggle.setContentDescription((toggle==saverTime?tr("屏保时间","Screensaver time"):tr("屏保天气","Screensaver weather"))+(on?tr("，开",", on"):tr("，关",", off")));
        }
    }
    private void addUsageMark(LinearLayout caption,String value){
        TextView mark=label(value,9);mark.setTextColor(0xff171d25);mark.setPadding(px(4),px(1),px(4),px(1));mark.setBackgroundDrawable(ui.surface(0xfff0f1f4,100,Color.TRANSPARENT,0));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=px(3);caption.addView(mark,p);
    }

    void imported(String id){
        if(id==null||store.snapshot().find(id)==null)return;
        showPicker();preview=id;dynamicTab=store.snapshot().find(id).video();refreshCards(id,true);
    }
    void showPicker(){
        if(picker!=null||leaving)return;stopVideo();main.removeCallbacks(rotate);preview=store.snapshot().current;dynamicTab=store.snapshot().dynamic();
        pickerVisibility.clear();for(int n=1;n<root.getChildCount();n++){View view=root.getChildAt(n);pickerVisibility.put(view,view.getVisibility());view.setVisibility(View.INVISIBLE);}
        picker=new androidx.activity.ComponentDialog(activity){@Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)focusPendingCard();else cancelCardPress();}
            @Override public boolean dispatchKeyEvent(KeyEvent key){return KeyFeedback.dispatch(getWindow().getDecorView(),key,()->super.dispatchKeyEvent(key));}};
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
        auto=button("",false,this::showRotationMenu);header.addView(auto);
        dynamicToggle=button("",false,this::toggleVideo);header.addView(dynamicToggle);
        soundToggle=button("",false,()->runSaved(()->{store.sound(!store.snapshot().sound);video.setSound(store.snapshot().sound);details();}));header.addView(soundToggle);
        localFile=button(tr("本地 / U盘","Files / USB"),false,()->{picker.dismiss();activity.startActivityForResult(new android.content.Intent(activity,WallpaperFilesActivity.class),WallpaperFilesActivity.REQUEST);});header.addView(localFile);
        gap(header,8);upload=button(tr("上传壁纸","Upload"),false,()->{daily.cancel();stopVideo();uploads.show(WallpaperUi.backdrop(root,content),id->{if(picker==null)return;if(id!=null&&store.snapshot().find(id)!=null){preview=id;dynamicTab=store.snapshot().find(id).video();}refreshCards(preview,true);});});ui.icon(upload,R.drawable.wp_upload);header.addView(upload);
        heading.setSingleLine();heading.setEllipsize(android.text.TextUtils.TruncateAt.END);for(TextView control:new TextView[]{auto,dynamicToggle,soundToggle,localFile,upload}){control.setSingleLine();control.setPadding(px(8),px(9),px(8),px(9));}
        layout.addView(header);
        layout.addView(new View(activity),new LinearLayout.LayoutParams(1,0,1));
        actions=row();LinearLayout titles=new LinearLayout(activity);titles.setOrientation(LinearLayout.VERTICAL);title=label("",27);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(title,new LinearLayout.LayoutParams(-1,-2));
        subtitle=label("",12);subtitle.setTextColor(WallpaperUi.MUTED);subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);subtitle.setPadding(0,px(7),0,0);titles.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));actions.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        // Together with the rail's top padding and focused-card lift, leave about 36px at 1080p.
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.setMargins(px(4),0,px(4),px(16));layout.addView(actions,ap);
        scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);rail=row();rail.setClipToPadding(false);rail.setClipChildren(false);rail.setGravity(Gravity.BOTTOM);rail.setPadding(px(4),px(7),px(4),0);scroll.addView(rail);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,px(154));layout.addView(scroll,rp);
        addScreensaverToolbar(layout);
        picker.setContentView(content);picker.setOnDismissListener(d->{++saverApplyVersion;commitPending=false;cancelCardPress();pendingCardFocus=null;++pickerVersion;uploads.close();picker=null;if(rotationMenu!=null)rotationMenu.dismiss();daily.cancel();cards.clear();for(Map.Entry<View,Integer> item:pickerVisibility.entrySet())item.getKey().setVisibility(item.getValue());pickerVisibility.clear();shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));if(active)restoreSelected();schedule();});
        Window window=picker.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setSystemUiVisibility(5894);window.setDimAmount(0);}
        shade.setBackgroundDrawable(WallpaperUi.shade());picker.show();if(window!=null)window.setLayout(-1,-1);refreshCards(preview,true);
    }
    private void stopVideo(){stopVideo(null);}
    private void stopVideo(Runnable after){
        ++videoRequest;playingVideo="";commitPending=false;rotationCommit=0;pendingVideoStart=null;
        withCover(()->{video.stop();if(after!=null)after.run();},video.visible());
    }
    private void startVideo(String id,boolean commit){startVideo(id,commit,0,null);}
    private void startVideo(String id,boolean commit,int minutes){startVideo(id,commit,minutes,null);}
    private void startVideo(String id,boolean commit,Runnable covered){startVideo(id,commit,0,covered);}
    private void startVideo(String id,boolean commit,int minutes,Runnable covered){
        WallpaperCatalog.Entry entry=store.snapshot().find(id);if(entry==null||!entry.video()||!active||leaving)return;
        rotationCommit=minutes;
        if(id.equals(playingVideo)&&video.requested(id)){commitPending|=commit;if(commitPending&&video.ready()){commitPending=false;commitVideo(id);}if(covered!=null)covered.run();return;}
        cancelVideoFade();pendingVideoStart=null;failedVideo="";playingVideo=id;commitPending=commit;final int version=++videoRequest;
        display(id,false,()->{
            if(version!=videoRequest||!active||destroyed)return;
            if(covered!=null)covered.run();
            Runnable start=()->withCover(()->{
                if(version!=videoRequest||!active||destroyed)return;
                video.play(entry,new VideoWallpaperPlayer.Listener(){
                    @Override public void stopped(){if(version==videoRequest){cancelVideoFade();current.setAlpha(1f);}}
                    @Override public void ready(){
                        if(version!=videoRequest||!active||destroyed)return;
                        ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);revealVideo(version);
                        captureVideoPoster(id,version);
                        if(commitPending){commitPending=false;commitVideo(id);}else if(picker!=null)details();
                    }
                    @Override public void failed(VideoWallpaperPlayer.Failure reason){if(version!=videoRequest)return;boolean rotating=rotationCommit>0;failedVideo=id;videoFailure=reason;stopVideo();if(rotating){restoreSelected();failedVideo=id;schedule();}if(picker!=null)details();notice(reason.hint(chinese));}
                });
            });
            // Resume may run before the window is drawable. Keep the cover until focus returns.
            if(!root.hasWindowFocus()&&picker==null)pendingVideoStart=start;else start.run();
        });
    }
    private void captureVideoPoster(String id,int version){
        if(!id.startsWith("user-")||store.poster(id).isFile())return;
        video.snapshot(image->{
            if(image==null)return;
            if(version!=videoRequest||destroyed){image.recycle();return;}
            decoder.execute(()->{
                try{
                    if(!store.saveVideoPoster(id,image)){image.recycle();return;}
                    android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                    Bitmap blur=DockBackdropDrawable.prepare(image,screen.widthPixels,screen.heightPixels);
                    main.post(()->{
                        if(version!=videoRequest||destroyed){image.recycle();blur.recycle();return;}
                        current.setImageBitmap(image);currentBlur=blur;shownKey=store.imageKey(id);invalidateDock();cacheBackdrop(id,image,blur);
                        // The poster and thumbnail cache own the captured bitmap after this point.
                        thumbnails.put(store.imageKey(id),image);View card=cards.get(id);
                        if(card instanceof FrameLayout)((ImageView)((FrameLayout)card).getChildAt(0)).setImageBitmap(image);
                    });
                }catch(IOException|RuntimeException|OutOfMemoryError e){
                    image.recycle();DiagnosticLog.error("wallpaper.video.poster_failed","optional=true "+VideoDiagnostics.failureDetails(e),e);
                }
            });
        });
    }
    private void commitVideo(String id){
        try{daily.cancel();boolean rotating=rotationCommit>0;if(rotating)store.rotation(true,rotationCommit,id);else store.select(id);rotationCommit=0;if(id.equals(shown)&&current.getDrawable() instanceof BitmapDrawable)cacheBackdrop(id,((BitmapDrawable)current.getDrawable()).getBitmap(),currentBlur);if(picker!=null)refreshCards(id,cards.get(id)!=null&&cards.get(id).hasFocus());schedule();if(!rotating)notice(tr("已应用动态壁纸","Video wallpaper applied"));}
        catch(IOException e){stopVideo();restoreSelected();schedule();notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}
    }
    private void applySelected(){WallpaperCatalog.Entry entry=store.snapshot().find(preview);if(entry==null)return;
        if(entry.video())startVideo(preview,true);
        else if(WallpaperCatalog.DAILY.equals(preview))daily.apply((success,message)->{if(picker!=null){refreshCards(WallpaperCatalog.DAILY,true);notice(success?tr("已应用每日壁纸","Daily wallpaper applied"):message);}});
        else runSaved(()->{daily.cancel();store.select(preview);display(preview,true);refreshCards(preview,true);notice(tr("已应用壁纸","Wallpaper applied"));});
    }
    private void category(boolean videos){if(dynamicTab==videos){focusCard(preview);return;}stopVideo();dynamicTab=videos;refreshCards(store.snapshot().current,true);}
    private void toggleVideo(){
        WallpaperCatalog state=store.snapshot();if(state.dynamic()){stopVideo();runSaved(()->{store.stopVideo();dynamicTab=false;refreshCards(store.snapshot().current,true);daily.environmentChanged();schedule();});}
        else if(state.find(state.lastVideo)!=null){dynamicTab=true;refreshCards(state.lastVideo,false);startVideo(state.lastVideo,true);}
        else {dynamicTab=true;refreshCards(null,false);upload.requestFocus();}
    }
    private void handlePickerBack(){if(picker==null)return;commitPending=false;picker.dismiss();KeyFeedback.returned(activity);}
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
            if(id.equals(state.current))addUsageMark(caption,tr("壁纸","Wall"));
            ScreensaverSettings saver=saverSettings();
            if(saver!=null&&id.equals(saver.mediaId))addUsageMark(caption,tr("屏保","Saver"));
            card.setOnFocusChangeListener((v,focus)->{if(focus){if(!id.equals(preview))commitPending=false;preview=id;if(wallpaper.video())startVideo(id,false);else display(id,true);details();scroll.requestChildFocus(rail,card);}else if(id.equals(pressedWallpaper))cancelCardPress();});
            card.setOnClickListener(v->{commitPending=false;preview=id;applySelected();KeyFeedback.confirm(activity);});
            card.setOnLongClickListener(v->{confirmDelete(id,KeyEvent.KEYCODE_UNKNOWN,0);return true;});
            card.setOnKeyListener((v,key,event)->{if(confirmKey(key))return cardKey(id,key,event);if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){cancelCardPress();List<String> ids=focusableCards();int index=ids.indexOf(id)+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);if(index>=0&&index<ids.size())cards.get(ids.get(index)).requestFocus();}return true;});
        }
        if(entries.isEmpty()){TextView empty=label(tr("上传视频作为动态壁纸","Upload a video wallpaper"),16);empty.setTextColor(WallpaperUi.MUTED);rail.addView(empty);display(store.snapshot().staticBeforeVideo,false);}else if(state.find(preview).video())startVideo(preview,false);else display(preview,true);
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
        updateSaverToolbar();
        staticTab.setSelected(!dynamicTab);videoTab.setSelected(dynamicTab);
        videoTab.setNextFocusRightId(auto.getId());auto.setNextFocusLeftId(videoTab.getId());dynamicToggle.setNextFocusLeftId(auto.getId());
        soundToggle.setVisibility(dynamicTab?View.VISIBLE:View.GONE);soundToggle.setText(tr("声音","Sound"));soundToggle.setCompoundDrawables(toggleDrawable(state.sound),null,null,null);soundToggle.setCompoundDrawablePadding(px(7));soundToggle.setContentDescription(state.sound?tr("壁纸声音，开","Wallpaper sound, on"):tr("壁纸声音，关","Wallpaper sound, off"));
        dynamicToggle.setNextFocusRightId(soundToggle.getId());soundToggle.setNextFocusLeftId(dynamicToggle.getId());soundToggle.setNextFocusRightId(localFile.getId());auto.setNextFocusRightId(dynamicTab?dynamicToggle.getId():localFile.getId());localFile.setNextFocusLeftId(dynamicTab?soundToggle.getId():auto.getId());localFile.setNextFocusRightId(upload.getId());upload.setNextFocusLeftId(localFile.getId());
        dynamicToggle.setVisibility(dynamicTab?View.VISIBLE:View.GONE);dynamicToggle.setText(tr("启用动态","Enable video"));dynamicToggle.setCompoundDrawables(toggleDrawable(state.dynamic()),null,null,null);dynamicToggle.setCompoundDrawablePadding(px(7));dynamicToggle.setContentDescription(state.dynamic()?tr("启用动态，开","Enable video, on"):tr("启用动态，关","Enable video, off"));
        int count=dynamicTab?state.videos().size():state.pictures().size();
        boolean rotating=state.rotating&&state.dynamic()==dynamicTab;String unavailable=tr(dynamicTab?"至少需要两个视频才能轮播":"至少需要两张图片才能轮播",dynamicTab?"Add at least two videos for rotation":"Add at least two photos for rotation");
        auto.setText(tr("轮播：","Rotate: ")+rotationLabel(rotating?state.rotationMinutes:0));auto.setCompoundDrawables(null,null,ui.icon(R.drawable.ic_wallpaper_dropdown,11,WallpaperUi.INK),null);auto.setCompoundDrawablePadding(px(5));auto.setEnabled(count>=2);auto.setAlpha(count>=2?1:.4f);auto.setContentDescription(count<2?unavailable:auto.getText());
        if(entry==null){title.setText(tr("还没有动态壁纸","No video wallpapers"));subtitle.setText(unavailable);subtitle.setVisibility(View.VISIBLE);for(TextView control:new TextView[]{staticTab,videoTab,dynamicToggle,soundToggle,localFile,upload})control.setNextFocusDownId(upload.getId());return;}
        title.setText(entry.label(chinese));boolean isDaily=WallpaperCatalog.DAILY.equals(preview);
        subtitle.setVisibility(isDaily||entry.custom()?View.VISIBLE:View.GONE);
        if(entry.custom()){
            String description=entry.width+" × "+entry.height;
            if(entry.video())description+="  "+Math.round(entry.fps)+" FPS  "+Math.round(entry.durationMs/1000f)+tr(" 秒"," s");
            description+="  "+Formatter.formatShortFileSize(activity,entry.bytes);
            subtitle.setText(entry.video()&&preview.equals(failedVideo)?videoFailure.hint(chinese):description);
        }
        if(isDaily){
            WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);
            String description=WallpaperCatalog.sourceLabel(state.dailySource,chinese)+"  "+tr("每天自动更新","Updates daily");
            if(store.cached(image)){
                description=image.width+" × "+image.height+"  "+Formatter.formatShortFileSize(activity,store.dailyFile(image.source,image.slot).length())+"  "+description;
                if(!image.title.isEmpty())description+="  "+image.title;
            }
            subtitle.setText(daily.busy()?daily.status(WallpaperCatalog.BING):description);
        }
        if(count<2){subtitle.setText((subtitle.getVisibility()==View.VISIBLE?subtitle.getText()+"  ":"")+unavailable);subtitle.setVisibility(View.VISIBLE);}
        View card=cards.get(preview);if(card==null)return;
        for(Map.Entry<String,View> item:cards.entrySet()){boolean selected=item.getKey().equals(preview);item.getValue().setBackgroundDrawable(border(selected,false));item.getValue().setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));item.getValue().setTranslationY(selected?-px(3):0);item.getValue().setNextFocusUpId(dynamicTab?videoTab.getId():staticTab.getId());}
        int lower=card.getId();
        for(View item:cards.values())item.setNextFocusDownId(applySaver.getId());
        for(TextView control:saverControls())control.setNextFocusUpId(lower);
        for(TextView control:new TextView[]{auto,upload,staticTab,videoTab,dynamicToggle,soundToggle,localFile})control.setNextFocusDownId(lower);
    }
    private void confirmDelete(String id,int initiatingKey,long downTime){
        if(picker==null||confirmation!=null&&confirmation.isShowing())return;
        WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(id);if(entry==null)return;
        if(WallpaperCatalog.DAILY.equals(id))return;
        if(WallpaperCatalog.DEFAULT.equals(id)){notice(tr("默认颜色主题不能删除","The default color theme cannot be deleted"));return;}
        stopVideo();List<String> before=focusableCards();int index=before.indexOf(id);String[] returnTo={id};
        AlertDialog dialog=new KeyFeedback.Alert(activity);dialog.setTitle(tr("删除「","Delete “")+entry.label(chinese)+tr("」？","”?"));
        dialog.setMessage(tr("删除电视中保存的壁纸，手机或电脑中的原文件不受影响。","Delete this TV copy. The original on your phone or computer is unchanged.")+(entry.id.equals(state.current)?entry.video()?tr("\n删除后恢复之前的静态壁纸。","\nRestores your previous static wallpaper."):state.rotating?tr("\n将切换到下一张有效图片；不足两张时关闭轮播。","\nSwitch to the next available photo; stop slideshow below two images."):tr("\n这张壁纸正在使用，删除后切回默认颜色主题。","\nThis wallpaper is in use. Deleting it restores the default color theme."):""));
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE,tr("取消","Cancel"),(android.content.DialogInterface.OnClickListener)null);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,tr("删除","Delete"),(android.content.DialogInterface.OnClickListener)null);
        // The opening hold can continue in the new window. Consume its repeats and release.
        boolean[] releasing={initiatingKey!=KeyEvent.KEYCODE_UNKNOWN};
        dialog.setOnKeyListener((d,key,event)->{if(!releasing[0]||key!=initiatingKey)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&event.getDownTime()>downTime){releasing[0]=false;return false;}if(event.getAction()==KeyEvent.ACTION_UP)releasing[0]=false;return true;});
        dialog.setOnDismissListener(d->{if(confirmation==dialog)confirmation=null;if(active)focusCard(returnTo[0]);});
        confirmation=dialog;dialog.show();ControlStyle.dialog(dialog,unit);View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setOnClickListener(v->{dialog.dismiss();KeyFeedback.back(activity);});cancel.post(()->{if(dialog.isShowing())cancel.requestFocusFromTouch();});
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                String thumbnailKey=store.imageKey(entry.id);store.remove(entry.id);thumbnails.remove(thumbnailKey);before.remove(entry.id);returnTo[0]=before.isEmpty()?WallpaperCatalog.DEFAULT:before.get(Math.max(0,Math.min(index,before.size()-1)));
                refreshCards(returnTo[0],false);dialog.dismiss();KeyFeedback.confirm(activity);
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
