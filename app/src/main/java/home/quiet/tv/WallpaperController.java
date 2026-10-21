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
    private final Activity activity;
    private final FrameLayout root;
    private final WallpaperStore store;
    private final WallpaperUploadPanel uploads;
    private final float unit;
    private final boolean chinese;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final ImageView current,incoming;
    private final View shade;
    private final Ambient ambient;
    private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(6*1024){@Override protected int sizeOf(String id,Bitmap bitmap){return bitmap.getByteCount()/1024;}};
    private final Map<String,View> cards=new LinkedHashMap<>();
    private Bitmap entry;
    private volatile int request;
    private int pickerVersion,themeColor=0xff557eac;
    private String preview,shown;
    private boolean active,managing;
    private Dialog picker,confirmation;
    private FrameLayout content;
    private LinearLayout rail;
    private HorizontalScrollView scroll;
    private TextView title,apply,remove,restore,auto,manage,upload;
    private ValueAnimator transition;
    private final Runnable rotate=this::rotateWallpaper;
    private void rotateWallpaper(){try{store.next();display(store.snapshot().current,true);}catch(IOException e){notice(tr("壁纸设置保存失败","Unable to save wallpaper settings"));}schedule();}

    WallpaperController(Activity activity,FrameLayout root,float unit,boolean chinese){
        this.activity=activity;this.root=root;this.unit=unit;this.chinese=chinese;store=WallpaperStore.get(activity);uploads=new WallpaperUploadPanel(activity,store,unit,chinese);
        FrameLayout background=new FrameLayout(activity);root.addView(background,new FrameLayout.LayoutParams(-1,-1));
        current=image();incoming=image();incoming.setAlpha(0);background.addView(current,new FrameLayout.LayoutParams(-1,-1));background.addView(incoming,new FrameLayout.LayoutParams(-1,-1));
        shade=new View(activity);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x70000000,0x10000000,0xf00a0e15}));background.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        ambient=new Ambient();background.addView(ambient,new FrameLayout.LayoutParams(-1,-1));ambient.setVisibility(View.INVISIBLE);
        display(store.snapshot().current,false);
    }
    private int px(float value){return Math.round(value*unit);}
    private String tr(String zh,String en){return chinese?zh:en;}
    private void notice(String message){Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();}
    void setThemeColor(int color){themeColor=color;if(ambient.getVisibility()==View.VISIBLE)ambient.setColor(color);}
    private ImageView image(){ImageView view=new ImageView(activity);view.setScaleType(ImageView.ScaleType.CENTER_CROP);view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return view;}
    private void display(String id,boolean animate){
        final int version=++request;
        if(transition!=null){transition.end();transition=null;}
        if(WallpaperCatalog.DEFAULT.equals(id)){
            current.setImageDrawable(null);incoming.setImageDrawable(null);shade.setVisibility(View.INVISIBLE);ambient.setVisibility(View.VISIBLE);ambient.setColor(themeColor);shown=id;return;
        }
        if(id.equals(shown))return;
        WallpaperCatalog.Entry wallpaper=store.snapshot().find(id);if(wallpaper==null){display(WallpaperCatalog.DEFAULT,false);return;}
        decoder.execute(()->{
            if(version!=request)return;
            try{
                android.util.DisplayMetrics screen=activity.getResources().getDisplayMetrics();
                Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,screen.widthPixels,screen.heightPixels);
                main.post(()->{
                    if(version!=request||activity.isDestroyed())return;
                    ambient.setVisibility(View.INVISIBLE);shade.setVisibility(View.VISIBLE);
                    if(!animate||shown==null||WallpaperCatalog.DEFAULT.equals(shown)){current.setImageBitmap(bitmap);shown=id;}
                    else{
                        incoming.setImageBitmap(bitmap);incoming.setAlpha(0);
                        transition=ValueAnimator.ofFloat(0,1);transition.setDuration(450);
                        transition.addUpdateListener(a->{float fraction=(float)a.getAnimatedValue();incoming.setAlpha(fraction);if(fraction==1f){current.setImageBitmap(bitmap);incoming.setImageDrawable(null);incoming.setAlpha(0);shown=id;}});transition.start();
                    }
                });
            }catch(IOException|OutOfMemoryError e){main.post(()->{if(version!=request)return;notice(tr("无法读取这张壁纸","Unable to read this wallpaper"));if(id.equals(store.snapshot().current))try{store.select(WallpaperCatalog.DEFAULT);}catch(IOException ignored){}display(WallpaperCatalog.DEFAULT,false);});}
        });
    }
    private void schedule(){main.removeCallbacks(rotate);WallpaperCatalog state=store.snapshot();if(active&&state.rotating&&picker==null)main.postDelayed(rotate,ROTATION_MS);}
    void resume(){active=true;display(store.snapshot().current,false);schedule();}
    void pause(){active=false;main.removeCallbacks(rotate);uploads.close();if(confirmation!=null)confirmation.dismiss();if(picker!=null)picker.dismiss();if(transition!=null)transition.end();}
    void destroy(){++request;uploads.destroy();decoder.shutdownNow();main.removeCallbacksAndMessages(null);if(transition!=null){transition.cancel();transition.removeAllUpdateListeners();}current.setImageDrawable(null);incoming.setImageDrawable(null);thumbnails.evictAll();}

    synchronized Bitmap entryBanner(){
        if(entry!=null)return entry;
        entry=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(entry);canvas.drawColor(0xff162333);Paint paint=new Paint(3);
        paint.setShader(new RadialGradient(100,50,260,new int[]{0xff426b8e,0xff101822},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,360,203,paint);paint.setShader(null);paint.setColor(Color.WHITE);paint.setTextSize(chinese?34:27);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));canvas.drawText(tr("壁纸设置","Wallpaper Settings"),180,120,paint);return entry;
    }
    private TextView label(String value,int size){TextView view=new TextView(activity);view.setText(value);view.setTextColor(Color.WHITE);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));view.setIncludeFontPadding(false);return view;}
    private GradientDrawable border(boolean focus,boolean primary){GradientDrawable drawable=new GradientDrawable();drawable.setColor(primary?0xfff1f2f4:0xff242a33);drawable.setCornerRadius(px(14));drawable.setStroke(px(focus?3:1),focus?Color.WHITE:0x60ffffff);return drawable;}
    private TextView button(String value,boolean primary,Runnable action){
        TextView view=label(value,12);view.setId(View.generateViewId());view.setGravity(Gravity.CENTER);view.setFocusable(true);view.setPadding(px(15),px(10),px(15),px(10));view.setTextColor(primary?0xff1e242c:Color.WHITE);view.setBackground(border(false,primary));view.setOnFocusChangeListener((v,focus)->view.setBackground(border(focus,primary)));view.setOnClickListener(v->action.run());return view;
    }
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
        if(picker!=null)return;main.removeCallbacks(rotate);preview=store.snapshot().current;managing=false;
        for(int n=1;n<root.getChildCount();n++)root.getChildAt(n).setVisibility(View.INVISIBLE);
        picker=new androidx.activity.ComponentDialog(activity);
        ((androidx.activity.ComponentDialog)picker).getOnBackPressedDispatcher().addCallback((androidx.activity.ComponentDialog)picker,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){handlePickerBack();}});
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);content=new FrameLayout(activity);
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(px(40),px(28),px(40),px(28));content.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();TextView back=button("‹",false,this::handlePickerBack);back.setContentDescription(tr("返回桌面","Back to home"));header.addView(back,new LinearLayout.LayoutParams(px(38),px(38)));gap(header,14);
        LinearLayout branding=new LinearLayout(activity);branding.setOrientation(LinearLayout.VERTICAL);branding.addView(label("QUIET TV",10));TextView heading=label(tr("壁纸设置","Wallpaper Settings"),28);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2);hp.topMargin=px(5);branding.addView(heading,hp);header.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        auto=button("",false,()->runSaved(()->{WallpaperCatalog s=store.snapshot();store.rotation(!s.rotating);preview=store.snapshot().current;display(preview,true);refreshCards(preview,false);}));header.addView(auto);
        gap(header,8);upload=button(tr("↑  上传壁纸","↑  Upload"),false,()->uploads.show(id->{if(picker!=null){if(id!=null)preview=id;refreshCards(preview,id!=null);}}));header.addView(upload);
        gap(header,8);manage=button(tr("管理","Manage"),false,()->{managing=!managing;refreshCards(preview,false);});header.addView(manage);layout.addView(header);
        layout.addView(new View(activity),new LinearLayout.LayoutParams(1,0,1));
        LinearLayout actions=row();title=label("",26);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);actions.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        remove=button(tr("删除","Delete"),false,this::confirmDelete);actions.addView(remove);gap(actions,10);
        apply=button("",true,()->runSaved(()->{store.select(preview);refreshCards(preview,false);notice(tr("已应用壁纸","Wallpaper applied"));}));actions.addView(apply);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,px(58));ap.bottomMargin=px(16);layout.addView(actions,ap);
        LinearLayout restores=row();restores.setGravity(Gravity.END);restore=button(tr("↶  恢复内置壁纸","↶  Restore built-in wallpapers"),false,this::confirmRestore);restores.addView(restore);layout.addView(restores);
        scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setFillViewport(false);rail=row();rail.setGravity(Gravity.BOTTOM);rail.setPadding(px(4),px(6),px(4),px(4));scroll.addView(rail);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,px(166));rp.topMargin=px(10);layout.addView(scroll,rp);
        picker.setContentView(content);picker.setOnDismissListener(d->{++pickerVersion;uploads.close();picker=null;cards.clear();for(int n=1;n<root.getChildCount();n++)root.getChildAt(n).setVisibility(View.VISIBLE);display(store.snapshot().current,true);schedule();});
        Window window=picker.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.getDecorView().setSystemUiVisibility(5894);window.setDimAmount(0);}
        picker.show();if(window!=null)window.setLayout(-1,-1);refreshCards(preview,true);
    }
    private void handlePickerBack(){if(picker==null)return;if(managing){managing=false;refreshCards(preview,true);}else picker.dismiss();}
    private void refreshCards(String preferred,boolean focusCard){
        if(picker==null)return;int version=++pickerVersion;WallpaperCatalog state=store.snapshot();if(state.find(preferred)==null)preferred=state.current;preview=preferred;
        rail.removeAllViews();cards.clear();
        for(WallpaperCatalog.Entry wallpaper:state.entries()){
            String id=wallpaper.id;FrameLayout card=new FrameLayout(activity);card.setId(View.generateViewId());card.setFocusable(true);card.setContentDescription(wallpaper.label(chinese));card.setPadding(px(5),px(5),px(5),px(5));card.setBackground(border(false,false));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(184),px(148));cp.rightMargin=px(14);rail.addView(card,cp);cards.put(id,card);
            ImageView image=image();card.addView(image,new FrameLayout.LayoutParams(-1,px(100)));
            if(WallpaperCatalog.DEFAULT.equals(id))image.setImageBitmap(defaultThumbnail());else{
                Bitmap cached=thumbnails.get(id);if(cached!=null)image.setImageBitmap(cached);
                else decoder.execute(()->{try{Bitmap bitmap=WallpaperImages.decode(activity,store,wallpaper,360,203);thumbnails.put(id,bitmap);main.post(()->{if(picker!=null&&version==pickerVersion)image.setImageBitmap(bitmap);});}catch(IOException|OutOfMemoryError ignored){}});
            }
            TextView name=label(wallpaper.label(chinese)+(id.equals(state.current)?tr("  · 当前","  · Current"):""),12);name.setGravity(Gravity.CENTER_VERTICAL);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,px(34),Gravity.BOTTOM);np.leftMargin=px(4);card.addView(name,np);
            card.setOnFocusChangeListener((v,focus)->{card.setBackground(border(focus,false));card.animate().scaleX(focus?1.035f:1).scaleY(focus?1.035f:1).setDuration(120).start();if(focus){preview=id;display(id,true);details();scroll.requestChildFocus(rail,card);}});
            card.setOnClickListener(v->runSaved(()->{preview=id;store.select(id);refreshCards(id,true);notice(tr("已应用壁纸","Wallpaper applied"));}));
            card.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN){List<String> ids=new ArrayList<>(cards.keySet());int index=ids.indexOf(id)+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);if(index>=0&&index<ids.size())cards.get(ids.get(index)).requestFocus();}return true;});
        }
        details();if(focusCard)cards.get(preview).requestFocus();
    }
    private void details(){
        if(picker==null)return;WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(preview);if(entry==null)return;
        title.setText(entry.label(chinese));remove.setVisibility(managing&&!WallpaperCatalog.DEFAULT.equals(preview)?View.VISIBLE:View.GONE);
        boolean applied=preview.equals(state.current)&&!state.rotating;apply.setEnabled(!applied);apply.setAlpha(applied?.5f:1f);apply.setText(applied?tr("正在使用","In use"):WallpaperCatalog.DEFAULT.equals(preview)?tr("使用颜色主题","Use color theme"):tr("使用这张壁纸","Use wallpaper"));
        auto.setText(tr("壁纸轮播","Slideshow"));auto.setCompoundDrawables(toggleDrawable(state.rotating),null,null,null);auto.setCompoundDrawablePadding(px(7));auto.setContentDescription(state.rotating?tr("壁纸轮播，开","Slideshow, on"):tr("壁纸轮播，关","Slideshow, off"));auto.setEnabled(state.pictures().size()>=2);auto.setAlpha(auto.isEnabled()?1:.4f);
        manage.setText(managing?tr("完成","Done"):tr("管理","Manage"));View restoreRow=(View)restore.getParent();restoreRow.setVisibility(managing&&!state.removed.isEmpty()?View.VISIBLE:View.GONE);
        View card=cards.get(preview);if(card==null)return;View action=remove.getVisibility()==View.VISIBLE?remove:apply.isEnabled()?apply:upload;
        for(View item:cards.values())item.setNextFocusUpId(action.getId());
        for(TextView control:new TextView[]{auto,upload,manage})control.setNextFocusDownId(action==upload?card.getId():action.getId());
        int lower=restoreRow.getVisibility()==View.VISIBLE?restore.getId():card.getId();apply.setNextFocusDownId(lower);remove.setNextFocusDownId(lower);apply.setNextFocusUpId(upload.getId());remove.setNextFocusUpId(upload.getId());restore.setNextFocusDownId(card.getId());restore.setNextFocusUpId(action.getId());
    }
    private void confirmDelete(){
        WallpaperCatalog.Entry entry=store.snapshot().find(preview);if(entry==null||WallpaperCatalog.DEFAULT.equals(preview))return;
        confirmation=new AlertDialog.Builder(activity).setTitle(tr("删除「","Delete “")+entry.label(chinese)+tr("」？","”?"))
            .setMessage((entry.custom()?tr("删除电视中保存的图片，手机或电脑中的原图不受影响。","Delete this TV copy. The original on your phone or computer is unchanged."):tr("将从列表和轮播中移除，以后可恢复内置壁纸。","Remove from the list and slideshow. You can restore built-in wallpapers later."))+(entry.id.equals(store.snapshot().current)?store.snapshot().rotating?tr("\n将切换到下一张有效图片；不足两张时关闭轮播。","\nSwitch to the next available photo; stop slideshow below two images."):tr("\n这张壁纸正在使用，删除后切回默认颜色主题。","\nThis wallpaper is in use. Deleting it restores the default color theme."):""))
            .setNegativeButton(tr("取消","Cancel"),null).setPositiveButton(tr("删除","Delete"),(d,w)->runSaved(()->{store.remove(entry.id);thumbnails.remove(entry.id);preview=store.snapshot().current;display(preview,true);refreshCards(preview,true);})).show();
    }
    private void confirmRestore(){confirmation=new AlertDialog.Builder(activity).setTitle(tr("恢复内置壁纸？","Restore built-in wallpapers?"))
        .setMessage(tr("当前壁纸和你上传的图片保持不变。","Your current wallpaper and uploaded photos stay unchanged."))
        .setNegativeButton(tr("取消","Cancel"),null).setPositiveButton(tr("恢复","Restore"),(d,w)->runSaved(()->{store.restore();refreshCards(preview,true);})).show();}
    private Bitmap defaultThumbnail(){Bitmap bitmap=Bitmap.createBitmap(360,203,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.BLACK);Paint paint=new Paint(3);paint.setShader(new RadialGradient(116,59,232,new int[]{0xac557eac,0x40557eac,Color.TRANSPARENT},new float[]{0,.5f,1},Shader.TileMode.CLAMP));canvas.drawRect(0,0,360,203,paint);return bitmap;}
    private final class Ambient extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
        Shader bloom,spill;int color=Color.TRANSPARENT,targetColor=Color.TRANSPARENT;ValueAnimator animation;
        final float[] stops={0,.12f,.26f,.42f,.58f,.74f,.88f,1};
        final float[] falloff={1,.96f,.80f,.54f,.28f,.10f,.015f,0};
        Ambient(){super(activity);}
        void setColor(int value){
            float[] hsv=new float[3];Color.colorToHSV(value,hsv);
            hsv[1]=Math.min(.88f,hsv[1]);hsv[2]=.95f;value=Color.HSVToColor(hsv);
            if(value==targetColor)return;targetColor=value;
            if(animation!=null)animation.cancel();
            if(color==Color.TRANSPARENT){color=value;updateGlow();return;}
            animation=ValueAnimator.ofArgb(color,value);animation.setDuration(650);
            animation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animation.addUpdateListener(a->{color=(int)a.getAnimatedValue();updateGlow();});animation.start();
        }
        private Shader glow(float x,float y,float radius,float flatten,int alpha){
            int[] colors=new int[stops.length];
            for(int n=0;n<colors.length;n++)colors[n]=(color&0xffffff)|(Math.round(alpha*falloff[n])<<24);
            RadialGradient gradient=new RadialGradient(px(x),px(y),px(radius),colors,stops,Shader.TileMode.CLAMP);
            Matrix ellipse=new Matrix();ellipse.setScale(1,flatten,px(x),px(y));gradient.setLocalMatrix(ellipse);
            return gradient;
        }
        private void updateGlow(){
            bloom=glow(310,158,620,.68f,172);
            spill=glow(100,310,480,.72f,48);
            invalidate();
        }
        @Override protected void onDraw(Canvas c){
            if(bloom==null)return;
            paint.setShader(bloom);c.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(spill);c.drawRect(0,0,getWidth(),getHeight(),paint);
        }
        @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
    }

}
