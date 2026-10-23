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
    private final WallpaperUploadPanel uploads;
    private final DailyWallpaperController daily;
    private final DailyWallpaperPanel dailyPanel;
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
    private int videoRequest;
    private boolean dynamicTab;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(6*1024){@Override protected int sizeOf(String id,Bitmap bitmap){return bitmap.getByteCount()/1024;}};
    private final Map<String,View> cards=new LinkedHashMap<>();
    private Bitmap entry,currentBlur,incomingBlur;
    private Runnable dockInvalidation;
    private final RectF dockScreen=new RectF();
    private volatile int request;
    private int pickerVersion,themeColor=0xff557eac;
    private String preview,shown,shownKey;
    private boolean active,managing;
    private Dialog picker,confirmation;
    private FrameLayout content;
    private LinearLayout rail,actions;
    private HorizontalScrollView scroll;
    private View pendingCardFocus;
    private TextView title,subtitle,apply,dailySettings,restore,auto,manage,upload,staticCategory,videoCategory,dynamicToggle,playPreview;
    private String pressedWallpaper;
    private int pressedKey=KeyEvent.KEYCODE_UNKNOWN;
    private long pressedAt;
    private boolean longPressed;
    private final Runnable holdCard=()->{
        View card=cards.get(pressedWallpaper);
        if(card==null||!card.hasFocus()||picker==null||!picker.isShowing())return;
        longPressed=true;confirmDelete(pressedWallpaper,pressedKey,pressedAt);
    };
    private ValueAnimator transition;
    private final Runnable rotate=this::rotateWallpaper;
    private void rotateWallpaper(){try{store.next();display(store.snapshot().current,true);}catch(IOException e){notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}schedule();}

    WallpaperController(Activity activity,FrameLayout root,float unit,boolean chinese){
        this.activity=activity;this.root=root;this.homeUnit=unit;this.unit=unit*960f/1040f;this.ui=new WallpaperUi(activity,this.unit);this.chinese=chinese;store=WallpaperStore.get(activity);uploads=new WallpaperUploadPanel(activity,store,this.unit,chinese);
        daily=new DailyWallpaperController(activity,store,chinese,this::dailyChanged);dailyPanel=new DailyWallpaperPanel(activity,store,daily,this.unit,chinese);
        FrameLayout background=new FrameLayout(activity);root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        video=new VideoWallpaperPlayer(activity,background);
        current=image();incoming=image();incoming.setAlpha(0);background.addView(current,new FrameLayout.LayoutParams(-1,-1));background.addView(incoming,new FrameLayout.LayoutParams(-1,-1));
        shade=new View(activity);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));background.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ambient=new Ambient(activity,homeUnit);ambient.colorChanged=this::invalidateDock;background.addView(ambient,new FrameLayout.LayoutParams(-1,-1));ambient.setVisibility(View.INVISIBLE);
        display(store.snapshot().current,false);
    }
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
    private ImageView image(){ImageView view=new ImageView(activity);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;}
    private void display(String id,boolean animate){
        WallpaperCatalog.Entry selected=store.snapshot().find(id);
        if(selected!=null&&selected.video()&&id.equals(playingVideo)&&video.ready())return;
        stopVideo();final int version=++request;
        if(transition!=null){transition.end();transition=null;}
        if(WallpaperCatalog.DEFAULT.equals(id)){
            current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;shade.setVisibility(View.INVISIBLE);ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shown=id;shownKey=id;invalidateDock();return;
        }
        WallpaperCatalog state=store.snapshot();
        if(selected!=null&&selected.video()){
            Bitmap fallback=defaultThumbnail();
            ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);current.setImageBitmap(fallback);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;shown=id;shownKey=id;invalidateDock();
            WallpaperCatalog.Entry posterEntry=store.poster(id).isFile()?selected:state.find(state.staticBeforeVideo);
            // SurfaceView video frames are not captured: the Dock uses the matching poster.
            decoder.execute(()->{try{
                if(version!=request)return;
                Bitmap poster=fallback;
                if(posterEntry!=null&&!WallpaperCatalog.DEFAULT.equals(posterEntry.id)&&(!posterEntry.video()||posterEntry==selected))try{poster=WallpaperImages.decode(activity,store,posterEntry,960,540);}catch(IOException ignored){}
                if(version!=request)return;
                android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                Bitmap image=poster,blurred=DockBackdropDrawable.prepare(poster,screen.widthPixels,screen.heightPixels);
                main.post(()->{if(version==request&&!activity.isDestroyed()){current.setImageBitmap(image);currentBlur=blurred;invalidateDock();}});
            }catch(OutOfMemoryError ignored){}});return;
        }
        String key=store.imageKey(id);
        if(WallpaperCatalog.DAILY.equals(id)&&!store.cached(state.dailyImages.get(state.dailySource))){
            if(shown==null)display(state.lastLocal,false);return;
        }
        if(id.equals(shown)&&key.equals(shownKey)&&current.getDrawable()!=null){ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);invalidateDock();return;}
        WallpaperCatalog.Entry wallpaper=store.snapshot().find(id);if(wallpaper==null){display(WallpaperCatalog.DEFAULT,false);return;}
        decoder.execute(()->{
            if(version!=request)return;
            try{
                android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,screen.widthPixels,screen.heightPixels);
                if(version!=request)return;
                Bitmap blurred=DockBackdropDrawable.prepare(bitmap,screen.widthPixels,screen.heightPixels);
                main.post(()->{
                    if(version!=request||activity.isDestroyed())return;
                    ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
                    if(!animate||shown==null||WallpaperCatalog.DEFAULT.equals(shown)){current.setImageBitmap(bitmap);currentBlur=blurred;incomingBlur=null;shown=id;shownKey=key;invalidateDock();}
                    else{
                        incoming.setImageBitmap(bitmap);incomingBlur=blurred;incoming.setAlpha(0);
                        transition=ValueAnimator.ofFloat(0,1);transition.setDuration(450);
                        transition.addUpdateListener(a->{float fraction=(float)a.getAnimatedValue();incoming.setAlpha(fraction);if(fraction==1f){current.setImageBitmap(bitmap);currentBlur=incomingBlur;incomingBlur=null;incoming.setImageDrawable(null);incoming.setAlpha(0);shown=id;shownKey=key;}invalidateDock();});transition.start();
                    }
                });
            }catch(IOException|OutOfMemoryError e){main.post(()->{if(version!=request)return;if(WallpaperCatalog.DAILY.equals(id)){daily.cacheUnreadable();if(current.getDrawable()==null)display(store.snapshot().lastLocal,false);return;}notice(tr("无法读取这张壁纸","Unable to read this wallpaper"));if(id.equals(store.snapshot().current))try{store.select(WallpaperCatalog.DEFAULT);}catch(IOException ignored){}display(WallpaperCatalog.DEFAULT,false);});}
        });
    }
    private void schedule(){main.removeCallbacks(rotate);WallpaperCatalog state=store.snapshot();if(active&&state.rotating&&picker==null)main.postDelayed(rotate,ROTATION_MS);}
    void resume(){active=true;String id=store.snapshot().current;display(id,false);if(store.snapshot().dynamic()&&!id.equals(failedVideo))startVideo(id,false);daily.resume();schedule();}
    void environmentChanged(){daily.environmentChanged();}
    private void dailyChanged(){
        dailyPanel.refresh();
        if(picker!=null){
            if(daily.busy()){details();return;}
            boolean cardFocused=false;for(View card:cards.values())if(card.hasFocus())cardFocused=true;
            refreshCards(preview,cardFocused);
        }else if(active)display(store.snapshot().current,true);
    }
    void pause(){active=false;stopVideo();daily.pause();dailyPanel.close();cancelCardPress();main.removeCallbacks(rotate);uploads.close();if(confirmation!=null)confirmation.dismiss();if(picker!=null)picker.dismiss();if(transition!=null)transition.end();}
    void destroy(){++request;video.destroy();daily.destroy();uploads.destroy();decoder.shutdownNow();main.removeCallbacksAndMessages(null);if(transition!=null){transition.cancel();transition.removeAllUpdateListeners();}current.setImageDrawable(null);incoming.setImageDrawable(null);currentBlur=null;incomingBlur=null;dockInvalidation=null;thumbnails.evictAll();}

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
    private void runSaved(IoAction action){try{action.run();}catch(IOException e){notice(tr("操作失败，请检查电视存储后重试","Operation failed. Check TV storage and retry."));}}
    private interface IoAction {void run()throws IOException;}

    void showPicker(){
        if(picker!=null)return;stopVideo();main.removeCallbacks(rotate);preview=store.snapshot().current;dynamicTab=store.snapshot().dynamic();managing=false;
        for(int n=1;n<root.getChildCount();n++)root.getChildAt(n).setVisibility(View.INVISIBLE);
        picker=new androidx.activity.ComponentDialog(activity){@Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)focusPendingCard();else cancelCardPress();}};
        ((androidx.activity.ComponentDialog)picker).getOnBackPressedDispatcher().addCallback((androidx.activity.ComponentDialog)picker,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){handlePickerBack();}});
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);content=new FrameLayout(activity);
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(px(36),px(32),px(36),px(32));content.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();TextView back=button("",false,this::handlePickerBack);back.setPadding(px(10),px(10),px(10),px(10));back.setCompoundDrawables(ui.icon(R.drawable.wp_chevron_left,17,WallpaperUi.INK),null,null,null);back.setBackground(ui.focusSurface(0xa6111924,100));back.setContentDescription(tr("返回桌面","Back to home"));header.addView(back,new LinearLayout.LayoutParams(px(37),px(37)));gap(header,13);
        TextView heading=label(tr("壁纸设置","Wallpaper Settings"),28);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        auto=button("",false,()->runSaved(()->{WallpaperCatalog s=store.snapshot();daily.cancel();store.rotation(!s.rotating);preview=store.snapshot().current;display(preview,true);refreshCards(preview,false);}));header.addView(auto);
        gap(header,8);upload=button(tr("上传壁纸","Upload"),false,()->{daily.cancel();stopVideo();uploads.show(WallpaperUi.backdrop(root,content),id->{if(picker!=null){if(id!=null){preview=id;WallpaperCatalog.Entry added=store.snapshot().find(id);dynamicTab=added!=null&&added.video();}refreshCards(preview,id!=null);}});});ui.icon(upload,R.drawable.wp_upload);header.addView(upload);
        gap(header,8);manage=button(tr("管理","Manage"),false,()->{daily.cancel();managing=!managing;refreshCards(preview,true);});ui.icon(manage,R.drawable.wp_sliders_horizontal);header.addView(manage);layout.addView(header);
        LinearLayout categories=row();categories.setPadding(0,px(18),0,0);
        staticCategory=button(tr("静态壁纸","Static"),false,()->category(false));categories.addView(staticCategory);gap(categories,8);
        videoCategory=button(tr("动态壁纸","Video"),false,()->category(true));categories.addView(videoCategory);
        categories.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
        dynamicToggle=button("",false,this::toggleVideo);categories.addView(dynamicToggle);layout.addView(categories);
        layout.addView(new View(activity),new LinearLayout.LayoutParams(1,0,1));
        actions=row();LinearLayout titles=new LinearLayout(activity);titles.setOrientation(LinearLayout.VERTICAL);title=label("",27);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);titles.addView(title,new LinearLayout.LayoutParams(-1,-2));
        subtitle=label("",12);subtitle.setTextColor(WallpaperUi.MUTED);subtitle.setSingleLine(true);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);subtitle.setPadding(0,px(8),0,0);titles.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));actions.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        dailySettings=button(tr("每日设置","Daily settings"),false,()->dailyPanel.show(WallpaperUi.backdrop(root,content),()->{if(picker!=null&&active){details();dailySettings.post(()->{if(picker!=null)dailySettings.requestFocusFromTouch();});}}));ui.icon(dailySettings,R.drawable.wp_sliders_horizontal);actions.addView(dailySettings);
        playPreview=button(tr("预览","Preview"),false,()->startVideo(preview,false));actions.addView(playPreview);gap(actions,8);
        apply=button("",true,this::applySelected);ui.icon(apply,R.drawable.wp_check);actions.addView(apply);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,px(60));ap.setMargins(px(4),0,px(4),px(24));layout.addView(actions,ap);
        LinearLayout restores=row();restores.setGravity(Gravity.END);restore=button(tr("恢复内置壁纸","Restore built-in wallpapers"),false,this::confirmRestore);restore.setMinHeight(px(30));restore.setBackground(ui.focusSurface(Color.TRANSPARENT,100));ui.icon(restore,R.drawable.wp_rotate_ccw);restores.addView(restore);layout.addView(restores);
        scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);rail=row();rail.setClipToPadding(false);rail.setClipChildren(false);rail.setGravity(Gravity.BOTTOM);rail.setPadding(px(4),px(7),px(4),0);scroll.addView(rail);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,px(154));layout.addView(scroll,rp);
        picker.setContentView(content);picker.setOnDismissListener(d->{cancelCardPress();pendingCardFocus=null;++pickerVersion;uploads.close();picker=null;dailyPanel.close();daily.cancel();cards.clear();for(int n=1;n<root.getChildCount();n++)root.getChildAt(n).setVisibility(View.VISIBLE);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));String selected=store.snapshot().current;display(selected,true);if(active&&store.snapshot().dynamic()&&!selected.equals(failedVideo))startVideo(selected,false);schedule();});
        Window window=picker.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setSystemUiVisibility(5894);window.setDimAmount(0);}
        shade.setBackground(WallpaperUi.shade());picker.show();if(window!=null)window.setLayout(-1,-1);refreshCards(preview,true);
    }
    private void stopVideo(){++videoRequest;video.stop();playingVideo="";current.setAlpha(1);}
    private void startVideo(String id,boolean commit){
        WallpaperCatalog.Entry entry=store.snapshot().find(id);if(entry==null||!entry.video()||!active)return;
        failedVideo="";display(id,false);stopVideo();playingVideo=id;final int version=videoRequest;
        video.play(store.file(id),entry,new VideoWallpaperPlayer.Listener(){
            boolean applying=commit;
            @Override public void stopped(){if(version==videoRequest)current.setAlpha(1);}
            @Override public void ready(){if(version!=videoRequest||!active)return;current.setAlpha(0);if(applying){applying=false;commitVideo(id);}else if(picker!=null)details();}
            @Override public void failed(){if(version!=videoRequest)return;failedVideo=id;playingVideo="";current.setAlpha(1);if(picker!=null)details();notice(tr("视频播放失败，请使用较低规格的 MP4","Video playback failed. Try a lower-resolution MP4."));}
        });
    }
    private void commitVideo(String id){
        try{daily.cancel();store.select(id);if(picker!=null)refreshCards(id,false);notice(tr("已应用动态壁纸","Video wallpaper applied"));}
        catch(IOException e){stopVideo();display(store.snapshot().current,false);notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}
    }
    private void applySelected(){WallpaperCatalog.Entry entry=store.snapshot().find(preview);if(entry==null)return;
        if(entry.video()){if(preview.equals(playingVideo)&&video.ready())commitVideo(preview);else startVideo(preview,true);}
        else runSaved(()->{daily.cancel();store.select(preview);display(preview,true);refreshCards(preview,true);notice(tr("已应用壁纸","Wallpaper applied"));});
    }
    private void category(boolean videos){stopVideo();dynamicTab=videos;managing=false;refreshCards(store.snapshot().current,true);}
    private void toggleVideo(){
        WallpaperCatalog state=store.snapshot();if(state.dynamic()){stopVideo();runSaved(()->{store.stopVideo();dynamicTab=false;refreshCards(store.snapshot().current,true);daily.environmentChanged();schedule();});}
        else if(state.find(state.lastVideo)!=null){dynamicTab=true;refreshCards(state.lastVideo,false);startVideo(state.lastVideo,true);}
        else {dynamicTab=true;refreshCards(null,false);upload.requestFocus();}
    }
    private void handlePickerBack(){if(picker==null)return;if(!playingVideo.isEmpty()&&!playingVideo.equals(store.snapshot().current)){stopVideo();display(store.snapshot().current,false);details();return;}if(managing){managing=false;refreshCards(preview,true);}else picker.dismiss();}
    private void refreshCards(String preferred,boolean focusCard){
        if(picker==null)return;cancelCardPress();pendingCardFocus=null;int version=++pickerVersion;WallpaperCatalog state=store.snapshot();
        List<WallpaperCatalog.Entry> entries=new ArrayList<>();for(WallpaperCatalog.Entry e:state.entries())if(e.video()==dynamicTab)entries.add(e);
        boolean found=false;for(WallpaperCatalog.Entry e:entries)if(e.id.equals(preferred))found=true;
        if(!found)preferred=entries.isEmpty()?null:entries.get(0).id;
        if(!dynamicTab&&managing&&(WallpaperCatalog.DEFAULT.equals(preferred)||WallpaperCatalog.DAILY.equals(preferred))&&!state.pictures().isEmpty())preferred=state.pictures().get(0).id;preview=preferred;
        rail.removeAllViews();cards.clear();
        for(WallpaperCatalog.Entry wallpaper:entries){
            String id=wallpaper.id;FrameLayout card=new FrameLayout(activity);card.setId(View.generateViewId());card.setFocusable(true);card.setContentDescription(wallpaper.label(chinese));card.setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));card.setBackground(border(false,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(184),px(147));cp.rightMargin=px(14);rail.addView(card,cp);cards.put(id,card);
            ImageView image=ui.image(CARD_RADIUS,CARD_INSET);card.addView(image,new FrameLayout.LayoutParams(-1,px(95)));
            if(wallpaper.video()){image.setImageBitmap(defaultThumbnail());TextView play=label("▶",22);play.setGravity(Gravity.CENTER);play.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(play,new FrameLayout.LayoutParams(-1,px(95)));}
            if(WallpaperCatalog.DEFAULT.equals(id))image.setImageBitmap(defaultThumbnail());else{
                String thumbnailKey=store.imageKey(id);Bitmap cached=thumbnails.get(thumbnailKey);if(cached!=null)image.setImageBitmap(cached);
                else if(!WallpaperCatalog.DAILY.equals(id)||store.cached(state.dailyImages.get(state.dailySource)))decoder.execute(()->{try{Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,360,203);thumbnails.put(thumbnailKey,bitmap);main.post(()->{if(picker!=null&&version==pickerVersion)image.setImageBitmap(bitmap);});}catch(IOException|OutOfMemoryError ignored){}});
            }
            if(WallpaperCatalog.DAILY.equals(id)){
                if(!store.cached(state.dailyImages.get(state.dailySource)))image.setImageBitmap(defaultThumbnail());
                ImageView calendar=new ImageView(activity);calendar.setImageDrawable(ui.calendar(18));calendar.setPadding(px(7),px(7),px(7),px(7));calendar.setBackground(ui.surface(0xcc20252d,100,Color.TRANSPARENT,0));calendar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(px(32),px(32),Gravity.TOP|Gravity.RIGHT);dp.setMargins(0,px(7),px(7),0);card.addView(calendar,dp);
            }
            LinearLayout caption=row();FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,px(34),Gravity.BOTTOM);np.leftMargin=px(4);np.rightMargin=px(4);card.addView(caption,np);
            TextView name=label(wallpaper.label(chinese),12);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            if(id.equals(state.current)){
                TextView mark=label(tr("当前使用","In use"),10);mark.setTextColor(0xff171d25);mark.setPadding(px(6),px(1),px(6),px(1));mark.setBackground(ui.surface(0xfff0f1f4,100,Color.TRANSPARENT,0));
                LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-2);mp.leftMargin=px(4);caption.addView(mark,mp);
            }
            if(managing&&!WallpaperCatalog.DEFAULT.equals(id)&&!WallpaperCatalog.DAILY.equals(id)){
                ImageView trash=new ImageView(activity);trash.setImageDrawable(ui.icon(R.drawable.wp_trash_2,14,WallpaperUi.INK));trash.setPadding(px(4),px(4),px(4),px(4));trash.setBackground(ui.surface(0xcc20252d,100,Color.TRANSPARENT,0));trash.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(px(24),px(24),Gravity.TOP|Gravity.RIGHT);tp.setMargins(0,px(5),px(5),0);card.addView(trash,tp);
            }
            card.setOnFocusChangeListener((v,focus)->{if(focus){preview=id;display(id,true);details();scroll.requestChildFocus(rail,card);}else if(id.equals(pressedWallpaper))cancelCardPress();});
            card.setOnClickListener(v->{if(managing)confirmDelete(id,KeyEvent.KEYCODE_UNKNOWN,0);else if(wallpaper.video()){preview=id;startVideo(id,false);}else if(WallpaperCatalog.DAILY.equals(id)){preview=id;daily.apply(store.snapshot().dailySource,(success,message)->{if(picker!=null){refreshCards(id,true);notice(success?tr("已应用每日壁纸","Daily wallpaper applied"):message);}});}else runSaved(()->{preview=id;daily.cancel();store.select(id);refreshCards(id,true);notice(tr("已应用壁纸","Wallpaper applied"));});});
            card.setOnLongClickListener(v->{confirmDelete(id,KeyEvent.KEYCODE_UNKNOWN,0);return true;});
            card.setFocusable(!managing||!WallpaperCatalog.DEFAULT.equals(id)&&!WallpaperCatalog.DAILY.equals(id));card.setAlpha(card.isFocusable()?1:.45f);
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
        staticCategory.setAlpha(dynamicTab?.5f:1);videoCategory.setAlpha(dynamicTab?1:.5f);
        dynamicToggle.setVisibility(dynamicTab?View.VISIBLE:View.GONE);dynamicToggle.setText(tr("动态壁纸","Video wallpaper"));dynamicToggle.setCompoundDrawables(toggleDrawable(state.dynamic()),null,null,null);dynamicToggle.setCompoundDrawablePadding(px(7));dynamicToggle.setContentDescription(state.dynamic()?tr("动态壁纸，开","Video wallpaper, on"):tr("动态壁纸，关","Video wallpaper, off"));
        auto.setVisibility(dynamicTab?View.GONE:View.VISIBLE);
        if(entry==null){title.setText(tr("还没有动态壁纸","No video wallpapers"));subtitle.setVisibility(View.GONE);apply.setVisibility(View.GONE);dailySettings.setVisibility(View.GONE);playPreview.setVisibility(View.GONE);((View)restore.getParent()).setVisibility(View.GONE);manage.setEnabled(false);return;}
        manage.setEnabled(true);playPreview.setVisibility(entry.video()&&!managing?View.VISIBLE:View.GONE);
        title.setText(entry.label(chinese));boolean isDaily=WallpaperCatalog.DAILY.equals(preview);
        ViewGroup.LayoutParams actionSize=actions.getLayoutParams();int actionHeight=px((isDaily||entry.video())&&!managing?60:43);if(actionSize.height!=actionHeight){actionSize.height=actionHeight;actions.setLayoutParams(actionSize);}
        dailySettings.setVisibility(isDaily&&!managing?View.VISIBLE:View.GONE);subtitle.setVisibility((isDaily||entry.video())&&!managing?View.VISIBLE:View.GONE);
        if(entry.video())subtitle.setText(preview.equals(failedVideo)?tr("播放已暂停，请预览重试或关闭动态壁纸","Playback paused. Preview to retry or turn off video wallpaper."):entry.width+" × "+entry.height+"  "+Math.round(entry.fps)+" FPS  "+Math.round(entry.durationMs/1000f)+tr(" 秒"," s"));
        if(isDaily){WallpaperCatalog.DailyImage image=state.dailyImages.get(state.dailySource);String source=WallpaperCatalog.sourceLabel(state.dailySource,chinese);subtitle.setText(daily.busy()?daily.status(state.dailySource):source+"  "+(image!=null&&!image.title.isEmpty()?image.title:tr("每天自动更新","Updates daily")));}
        boolean applied=preview.equals(state.current)&&!state.rotating;apply.setEnabled(!applied&&!managing);apply.setVisibility(applied||managing||isDaily?View.GONE:View.VISIBLE);apply.setText(entry.video()?tr("使用动态壁纸","Use video wallpaper"):WallpaperCatalog.DEFAULT.equals(preview)?tr("使用颜色主题","Use color theme"):tr("使用这张壁纸","Use wallpaper"));
        auto.setText(tr("壁纸轮播","Slideshow"));auto.setCompoundDrawables(toggleDrawable(state.rotating),null,null,null);auto.setCompoundDrawablePadding(px(7));auto.setContentDescription(state.rotating?tr("壁纸轮播，开","Slideshow, on"):tr("壁纸轮播，关","Slideshow, off"));auto.setEnabled(!WallpaperCatalog.DAILY.equals(state.current)&&state.pictures().size()>=2);auto.setAlpha(auto.isEnabled()?1:.4f);
        manage.setText(managing?tr("完成","Done"):tr("管理","Manage"));View restoreRow=(View)restore.getParent();restoreRow.setVisibility(managing&&!state.removed.isEmpty()?View.VISIBLE:View.GONE);
        View card=cards.get(preview);if(card==null)return;View action=managing?restoreRow.getVisibility()==View.VISIBLE?restore:manage:dailySettings.getVisibility()==View.VISIBLE?dailySettings:playPreview.getVisibility()==View.VISIBLE?playPreview:apply.getVisibility()==View.VISIBLE?apply:upload;
        for(Map.Entry<String,View> item:cards.entrySet()){boolean selected=item.getKey().equals(preview);item.getValue().setBackground(border(selected,false));item.getValue().setPadding(px(CARD_INSET),px(CARD_INSET),px(CARD_INSET),px(CARD_INSET));item.getValue().setTranslationY(selected?-px(3):0);item.getValue().setNextFocusUpId(action.getId());}
        int lower=card.isFocusable()?card.getId():restoreRow.getVisibility()==View.VISIBLE?restore.getId():manage.getId();
        for(TextView control:new TextView[]{auto,upload,manage,staticCategory,videoCategory,dynamicToggle})control.setNextFocusDownId(managing||action==upload?lower:action.getId());
        dailySettings.setNextFocusDownId(lower);dailySettings.setNextFocusUpId(upload.getId());
        playPreview.setNextFocusDownId(lower);playPreview.setNextFocusUpId(videoCategory.getId());
        apply.setNextFocusDownId(lower);apply.setNextFocusUpId(upload.getId());restore.setNextFocusDownId(lower);restore.setNextFocusUpId(manage.getId());
    }
    private void confirmDelete(String id,int initiatingKey,long downTime){
        if(picker==null||confirmation!=null&&confirmation.isShowing())return;
        WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(id);if(entry==null)return;
        if(WallpaperCatalog.DAILY.equals(id))return;
        if(WallpaperCatalog.DEFAULT.equals(id)){notice(tr("默认颜色主题不能删除","The default color theme cannot be deleted"));return;}
        stopVideo();List<String> before=focusableCards();int index=before.indexOf(id);String[] returnTo={id};
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(tr("删除「","Delete “")+entry.label(chinese)+tr("」？","”?"))
            .setMessage((entry.custom()?tr("删除电视中保存的壁纸，手机或电脑中的原文件不受影响。","Delete this TV copy. The original on your phone or computer is unchanged."):tr("将从列表和轮播中移除，以后可恢复内置壁纸。","Remove from the list and slideshow. You can restore built-in wallpapers later."))+(entry.id.equals(state.current)?entry.video()?tr("\n删除后恢复之前的静态壁纸。","\nRestores your previous static wallpaper."):state.rotating?tr("\n将切换到下一张有效图片；不足两张时关闭轮播。","\nSwitch to the next available photo; stop slideshow below two images."):tr("\n这张壁纸正在使用，删除后切回默认颜色主题。","\nThis wallpaper is in use. Deleting it restores the default color theme."):""))
            .setNegativeButton(tr("取消","Cancel"),null).setPositiveButton(tr("删除","Delete"),null).create();
        // The opening hold can continue in the new window. Consume its repeats and release.
        boolean[] releasing={initiatingKey!=KeyEvent.KEYCODE_UNKNOWN};
        dialog.setOnKeyListener((d,key,event)->{if(!releasing[0]||key!=initiatingKey)return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0&&event.getDownTime()>downTime){releasing[0]=false;return false;}if(event.getAction()==KeyEvent.ACTION_UP)releasing[0]=false;return true;});
        dialog.setOnDismissListener(d->{if(confirmation==dialog)confirmation=null;if(active)focusCard(returnTo[0]);});
        confirmation=dialog;dialog.show();View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.post(()->{if(dialog.isShowing())cancel.requestFocusFromTouch();});
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                store.remove(entry.id);thumbnails.remove(entry.id);before.remove(entry.id);returnTo[0]=before.isEmpty()?WallpaperCatalog.DEFAULT:before.get(Math.max(0,Math.min(index,before.size()-1)));
                refreshCards(returnTo[0],false);dialog.dismiss();
            }catch(IOException e){notice(tr("删除失败，请检查电视存储后重试","Unable to delete. Check TV storage and retry."));}
        });
    }
    private void confirmRestore(){confirmation=new AlertDialog.Builder(activity).setTitle(tr("恢复内置壁纸？","Restore built-in wallpapers?"))
        .setMessage(tr("当前壁纸和你上传的图片保持不变。","Your current wallpaper and uploaded photos stay unchanged."))
        .setNegativeButton(tr("取消","Cancel"),null).setPositiveButton(tr("恢复","Restore"),(d,w)->runSaved(()->{store.restore();refreshCards(preview,true);})).show();}
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
