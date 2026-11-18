package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Native management surfaces keep settings focus separate from the existing HOME style. */
abstract class ManagementUi extends androidx.activity.ComponentActivity implements GlassStyle.Listener {
    static final int TEXT=UiPalette.TEXT,MUTED=UiPalette.SECONDARY;
    LinearLayout root,header,managementInfo;
    FrameLayout stage;
    private OperationFeedback operationFeedback;
    float unit;
    private static final int[] MANAGEMENT_ORDER=SettingsPages.ORDER;
    private final Button[] managementTabs=new Button[SettingsPages.COUNT];
    private int managementPage=-1;
    private boolean managementEntry=true;
    private Button managementStatus;
    private Button managementMoreButton;
    private KeyFeedback.Popup managementMorePopup;
    private UpdateBadgeView updateBadge;
    private ExecutorService backdropLoader;
    private boolean backdropClosed;
    private ImageView backdropImage;
    private Bitmap ownedBackdrop;
    private GlassStyle glassStyle;
    private volatile int backdropGeneration;
    private volatile String backdropIdentity;
    private final long createdAt=SystemClock.uptimeMillis();
    private Runnable contentDraw;
    private Runnable afterContent;
    private String contentScope="";
    private boolean contentDrawn,contentReported;
    private final java.util.ArrayList<SettingsSheetBackdrop> sheetBackdrops=new java.util.ArrayList<>();
    private final UpdateRepository.Listener updateListener=()->{if(updateBadge!=null)updateBadge.available(UpdateRepository.get(this).available!=null);};
    int px(float value){return Math.round(value*unit);}
    static void performanceAction(String action){if(BuildConfig.INDEPENDENT_BUILD)android.util.Log.i("QuietTVPerf","action="+action+" uptimeMs="+SystemClock.uptimeMillis());}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);glassStyle=GlassStyle.get(this);glassStyle.observe(this);BackNavigation.install(this,this::handleBack);unit=getResources().getDisplayMetrics().widthPixels/960f;
        getWindow().getDecorView().setSystemUiVisibility(5894);
        stage=new FrameLayout(this);stage.setBackgroundColor(0xff000000);setContentView(stage);
        operationFeedback=OperationFeedback.get(this);
        if(unifiedSettings()){stage.setClipChildren(false);SettingsFocusMotion.attach(stage);}
        if(!managementChrome()){root=column();return;}
        if(loadWallpaper())prepareBackdrop();
        View shade=new View(this);shade.setBackgroundDrawable(unifiedSettings()?new ColorDrawable(0xb8000000):new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xf0000000,0xf5000000}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        root=column();root.setPadding(px(48),px(unifiedSettings()?30:28),px(48),px(16));stage.addView(root,new FrameLayout.LayoutParams(-1,-1));
        if(unifiedSettings()){root.setPadding(px(48),px(30),px(48),px(24));root.setClipChildren(false);root.setClipToPadding(false);}
        header=row();root.addView(header,new LinearLayout.LayoutParams(-1,px(38)));
        Button back=iconButton("返回","arrow-left",18,()->{handleBack();KeyFeedback.back(this);});header.addView(back,new LinearLayout.LayoutParams(px(34),px(34)));
        TextView title=label("应用管理",27);title.setPadding(px(14),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
    }
    private void prepareBackdrop(){
        ImageView image=new ImageView(this);backdropImage=image;image.setScaleType(ImageView.ScaleType.CENTER_CROP);stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Bitmap supplied=entryBackdrop();
        if(supplied!=null&&glassStyle.rendering()){image.setImageBitmap(supplied);if(!unifiedSettings())return;}
        // A direct settings entry still draws immediately; file IO and decoding are not on its first-frame path.
        refreshBackdrop();
    }
    Bitmap entryBackdrop(){Bitmap image=HomePreviewCache.get(this).settingsBackdrop(getIntent().getLongExtra("backdrop-key",-1));return image!=null?image:getIntent().getParcelableExtra("backdrop");}
    private void clearBackdrop(){if(glassStyle!=null&&!glassStyle.rendering()){getIntent().removeExtra("backdrop");getIntent().removeExtra("backdrop-key");}if(backdropImage!=null){backdropImage.animate().cancel();backdropImage.setImageDrawable(null);backdropImage.setAlpha(1f);}if(ownedBackdrop!=null){ownedBackdrop.recycle();ownedBackdrop=null;}backdropIdentity=null;}
    @Override public void glassChanged(){if(!glassStyle.rendering()||hasWindowFocus())refreshBackdrop();}
    void refreshBackdrop(){
        if(backdropImage==null||backdropClosed)return;int request=++backdropGeneration,styleRevision=glassStyle.revision();
        if(!glassStyle.rendering()){clearBackdrop();return;}
        if(backdropLoader==null)backdropLoader=Executors.newSingleThreadExecutor();
        backdropLoader.execute(()->{
            if(request!=backdropGeneration||!glassStyle.valid(styleRevision))return;Bitmap decoded=null;String identity=null;boolean empty=false;
            try{
                WallpaperStore store=WallpaperStore.get(this);WallpaperCatalog state=store.snapshot();WallpaperCatalog.Entry entry=state.find(state.current);
                identity=store.imageKey(state.current)+(WallpaperCatalog.DAILY.equals(state.current)?":"+state.lastLocal:"");
                if(identity.equals(backdropIdentity))return;empty=entry==null||WallpaperCatalog.DEFAULT.equals(entry.id);
                if(entry!=null&&!WallpaperCatalog.DEFAULT.equals(entry.id)){
                    Bitmap source=WallpaperImages.decode(this,store,entry,480,270);
                    try{decoded=unifiedSettings()?settingsBackdrop(source,480,Math.max(1,Math.round(480f*getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().widthPixels)),BuildConfig.LEGACY_19,glassStyle,styleRevision):source;}
                    finally{if(unifiedSettings())source.recycle();}
                }
            }catch(java.io.IOException|RuntimeException|OutOfMemoryError error){DiagnosticLog.info("management.backdrop.unavailable","keep themed background");}
            Bitmap ready=decoded;String key=identity;boolean themed=empty;
            runOnUiThread(()->{
                if(backdropClosed||isFinishing()||UiCompat.destroyed(this)||request!=backdropGeneration||!glassStyle.valid(styleRevision)){if(ready!=null)ready.recycle();return;}
                if(ready!=null){Bitmap previous=ownedBackdrop;ownedBackdrop=ready;backdropIdentity=key;backdropImage.animate().cancel();backdropImage.setAlpha(0f);backdropImage.setImageBitmap(ready);if(previous!=null)previous.recycle();backdropImage.animate().alpha(1f).setDuration(getResources().getInteger(R.integer.quiet_motion_panel_ms)).setListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){if(!backdropClosed&&key.equals(backdropIdentity)&&backdropImage.getAlpha()>.999f)refreshSheetBackdrops();}}).start();}
                else if(themed){if(backdropIdentity!=null){backdropImage.animate().cancel();clearBackdrop();refreshSheetBackdrops();}backdropIdentity=key;}
            });
        });
    }
    void watchBackdrop(SettingsSheetBackdrop surface,boolean watching){if(watching)sheetBackdrops.add(surface);else sheetBackdrops.remove(surface);}
    void contentReady(String scope,View content){
        if(contentReported||contentDraw!=null)return;contentScope=scope;
        contentDraw=UiCompat.onDraw(content,()->content.post(()->{if(contentDraw==null)return;contentDraw.run();contentDraw=null;contentDrawn=true;reportContentReady();}));
    }
    void afterContentReady(Runnable action){if(contentReported)stage.post(action);else afterContent=action;}
    private void reportContentReady(){
        if(backdropClosed||contentReported||!contentDrawn||!hasWindowFocus())return;contentReported=true;
        String detail="page="+contentScope+" uptimeMs="+SystemClock.uptimeMillis()+" createMs="+(SystemClock.uptimeMillis()-createdAt)+" evidence=controls_drawn_and_window_focused";
        DiagnosticLog.info("page.content_ready",detail);if(BuildConfig.INDEPENDENT_BUILD)android.util.Log.i("QuietTVPerf",detail);
        if(Build.VERSION.SDK_INT>=19)reportFullyDrawn();
        Runnable action=afterContent;afterContent=null;if(action!=null)stage.post(action);
    }
    interface SettingsWrite {void run()throws java.io.IOException;}
    interface SettingsSaved {void complete(String failure);}
    private final ExecutorService settingsWriter=Executors.newSingleThreadExecutor();
    void saveSetting(SettingsWrite write,SettingsSaved complete){
        settingsWriter.execute(()->{String failure="";try{write.run();}catch(java.io.IOException error){failure=error.getMessage();}String result=failure;runOnUiThread(()->{if(!backdropClosed&&!isFinishing()&&!UiCompat.destroyed(this))complete.complete(result);});});
    }
    void filterSheetBackdrop(Runnable work){if(backdropClosed)return;if(backdropLoader==null)backdropLoader=Executors.newSingleThreadExecutor();backdropLoader.execute(work);}
    private void refreshSheetBackdrops(){for(SettingsSheetBackdrop surface:sheetBackdrops)surface.refresh();}
    // The settings backdrop follows the prototype's 2cqw overscan and 20px blur on a 1280px canvas.
    // Decode the selected image/poster rather than reusing HOME's blur, brightness and Dock tint.
    static Bitmap settingsBackdrop(Bitmap source,int width,int height,boolean reduced){return settingsBackdrop(source,width,height,reduced,null,0);}
    static Bitmap settingsBackdrop(Bitmap source,int width,int height,boolean reduced,GlassStyle glass,int revision){
        if(glass!=null&&!glass.valid(revision))return null;
        int inset=Math.max(1,Math.round(width*.02f)),w=width+inset*2,h=height+inset*2;
        Bitmap expanded=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(expanded);canvas.drawColor(Color.BLACK);
        float scale=Math.max(w/(float)source.getWidth(),h/(float)source.getHeight()),sw=source.getWidth()*scale,sh=source.getHeight()*scale;
        canvas.drawBitmap(source,null,new RectF((w-sw)/2,(h-sh)/2,(w+sw)/2,(h+sh)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
        if(!reduced&&!SettingsSheetBackdrop.gaussian(expanded,20f*width/1280f,glass,revision)){expanded.recycle();return null;}
        Bitmap result=Bitmap.createBitmap(expanded,inset,inset,width,height);if(result!=expanded)expanded.recycle();return result;
    }
    void drawSheetBackdrop(Canvas canvas){stage.draw(canvas);}
    void emphasize(Button button,boolean primary){StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_enabled,android.R.attr.state_focused,android.R.attr.state_window_focused},new SettingsFocusDrawable(unit,12,Color.TRANSPARENT));int fill=primary?0xe6ffffff:Color.TRANSPARENT;background.addState(new int[0],surface(fill,fill,0,12));button.setBackgroundDrawable(background);button.setTextColor(SettingsFocusDrawable.textColors(primary?UiPalette.INK:UiPalette.TEXT));}
    boolean loadWallpaper(){return true;}
    boolean managementChrome(){return true;}
    boolean localManagement(){return true;}
    boolean unifiedSettings(){return false;}
    boolean chinese(){return true;}
    String tr(String chinese,String english){return chinese()?chinese:english;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);managementEntry=true;refreshBackdrop();}
    @Override protected void onResume(){super.onResume();if(backdropImage!=null)refreshBackdrop();UpdateRepository.get(this).add(updateListener);UpdateRepository.get(this).foreground();if(managementEntry && managementPage>=0){managementEntry=false;managementTab().requestFocus();}}
    @Override protected void onStop(){if(operationFeedback!=null)operationFeedback.hide();UpdateRepository.get(this).remove(updateListener);if(managementMorePopup!=null)managementMorePopup.closeQuietly();super.onStop();}
    @Override protected void onDestroy(){OperationFeedback.clear(this);backdropClosed=true;if(glassStyle!=null)glassStyle.forget(this);clearBackdrop();settingsWriter.shutdown();afterContent=null;if(contentDraw!=null){contentDraw.run();contentDraw=null;}if(backdropLoader!=null)backdropLoader.shutdownNow();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(unifiedSettings())SettingsFocusMotion.update(getCurrentFocus(),focused);DiagnosticLog.info("input.window","page="+getClass().getSimpleName()+" "+InputDiagnostics.focus(this));if(focused)reportContentReady();}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){InputDiagnostics.pointer(this,event);return super.dispatchTouchEvent(event);}
    void managementConnected(){}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){return InputDiagnostics.dispatch(this,event,"management_window",()->KeyFeedback.dispatch(getWindow().getDecorView(),event,()->{DiagnosticLog.navigationKey("quiet_tv_management",event);return super.dispatchKeyEvent(event);}));}
    void connectionPanel(){startActivity(new Intent(this,MaintenanceHelpActivity.class));}
    void managementMore(){
        LinearLayout menu=new KeyFeedback.Menu(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(7),px(7),px(7),px(7));menu.setBackgroundDrawable(surface(0xfa000000,0xfa000000,0,18));
        managementMoreOptions(menu);
        int width=px(180);managementMorePopup=new KeyFeedback.Popup(this,menu,width,-2);
        managementMorePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if(Build.VERSION.SDK_INT>=21)managementMorePopup.setElevation(px(14));
        managementMorePopup.setOutsideTouchable(true);
        View anchor=unifiedSettings()?managementTab():managementMoreButton;
        managementMorePopup.setOnDismissListener(()->{managementMorePopup=null;anchor.requestFocus();});
        for(int i=0;i<menu.getChildCount();i++){
            final int index=i;
            menu.getChildAt(i).setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
                if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)menu.getChildAt(Math.max(0,Math.min(menu.getChildCount()-1,index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)))).requestFocus();return true;}return false;
            });
        }
        managementMorePopup.showAsDropDown(anchor,anchor.getWidth()-width,px(9));menu.getChildAt(0).requestFocus();
    }
    void managementMoreOptions(LinearLayout menu){
        managementMoreOption(menu,"帮助与恢复",()->startActivity(new Intent(this,MaintenanceHelpActivity.class)));
    }
    void managementMoreOption(LinearLayout menu,String title,Runnable action){
        Button choice=button(title,()->{managementMorePopup.closeQuietly();action.run();});choice.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(15));choice.setTextColor(UiPalette.TEXT);
        choice.setPadding(px(16),0,px(12),0);choice.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);choice.setFocusableInTouchMode(true);
        choice.setBackgroundDrawable(useSettingListStyle()?ControlStyle.listRow(unit,11):ControlStyle.action(unit,11,Color.TRANSPARENT,0));
        menu.addView(choice,new LinearLayout.LayoutParams(-1,px(40)));
    }
    private void managementHeader(){
        managementStatus=button("电脑维护",this::connectionPanel);managementStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(12));managementStatus.setPadding(px(12),0,px(12),0);header.addView(managementStatus,new LinearLayout.LayoutParams(-2,px(30)));
        Button more=iconText("更多",R.drawable.ic_manage_more,this::managementMore);
        managementMoreButton=more;
        marginAdd(header,more,-2,30,8);
        updateBadge=new UpdateBadgeView(this,managementTabs[4],unit);stage.addView(updateBadge,new FrameLayout.LayoutParams(-1,-1));
        managementTabs[4].addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateBadge.invalidate());
        if(!localManagement())managementStatus.setVisibility(View.GONE);
        View[] actions=localManagement()?new View[]{header.getChildAt(0),managementStatus,more}:new View[]{header.getChildAt(0),more};
        for(View action:actions)if(action.getId()==View.NO_ID)action.setId(androidx.core.view.ViewCompat.generateViewId());
        for(int i=0;i<actions.length;i++){View action=actions[i];action.setNextFocusLeftId(actions[Math.max(0,i-1)].getId());action.setNextFocusRightId(actions[Math.min(actions.length-1,i+1)].getId());action.setNextFocusDownId(managementTab().getId());}
    }
    Button managementTab(){return managementTabs[SettingsPages.section(managementPage)];}
    boolean managementTabFocused(){for(Button tab:managementTabs)if(tab!=null && tab.isFocused())return true;return false;}
    void focusManagementContent(){}
    public void handleBack(){finish();}
    // Unified settings cards keep their focus shadows outside the child rectangle.
    LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);if(unifiedSettings())view.setClipChildren(false);return view;}
    LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);if(unifiedSettings())view.setClipChildren(false);return view;}
    ScrollView settingsScroll(){return new ScrollView(this){@Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};}
    TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));view.setTextColor(unifiedSettings()?UiPalette.TEXT:TEXT);view.setGravity(Gravity.CENTER_VERTICAL);view.setIncludeFontPadding(false);return view;}
    TextView muted(String text,int size){TextView view=label(text,size);view.setTextColor(unifiedSettings()?UiPalette.SECONDARY:MUTED);return view;}
    TextView center(String text,int size){TextView view=label(text,size);view.setGravity(Gravity.CENTER);return view;}
    GradientDrawable surface(int top,int bottom,int stroke,float radius){GradientDrawable shape=new SurfaceDrawable(unit,unifiedSettings()?GradientDrawable.Orientation.TOP_BOTTOM:GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});shape.setCornerRadius(px(radius));shape.setStroke(px(1),stroke);return shape;}
    Drawable focusSurface(float radius){return unifiedSettings()?new SettingsFocusDrawable(unit,radius,0x10ffffff):ControlStyle.action(unit,radius,0x10ffffff,0x22ffffff);}
    void focusable(View view,float radius){view.setSoundEffectsEnabled(false);view.setFocusable(true);view.setBackgroundDrawable(focusSurface(radius));}
    Button button(String text,Runnable action){Button b=new KeyFeedback.Button(this){@Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(unifiedSettings())setAlpha(1f);}};b.setText(text);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(13));b.setTextColor(unifiedSettings()?SettingsFocusDrawable.textColors(UiPalette.TEXT):ColorStateList.valueOf(TEXT));b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);b.setPadding(px(16),0,px(16),0);b.setBackgroundDrawable(focusSurface(24));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);b.setOnClickListener(v->action.run());if(unifiedSettings()){b.setFocusableInTouchMode(true);b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));}if(unifiedSettings())SettingsTouchClick.attach(b);return b;}
    void styleTab(Button b,boolean compact){
        if(unifiedSettings()){b.setBackgroundDrawable(new SettingsFocusDrawable(unit,compact?12:999,Color.TRANSPARENT));b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.SECONDARY,UiPalette.INK));}
        else ControlStyle.choice(b,unit,compact?15:18,Color.TRANSPARENT,0);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(compact?12:unifiedSettings()?14:16));
        b.setSingleLine(true);b.setPadding(px(compact?10:18),0,px(compact?10:18),0);b.setMinWidth(px(compact?48:100));
    }
    void styleManagementTab(Button b,boolean compact){styleTab(b,compact);}
    Typeface navigationTypeface(boolean selected){return selected?(Build.VERSION.SDK_INT>=28?Api28.navigationTypeface():Typeface.create("sans-serif-medium",Typeface.NORMAL)):Typeface.create("sans-serif",Typeface.NORMAL);}
    @android.annotation.TargetApi(28) private static final class Api28 {static Typeface navigationTypeface(){return Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),600,false);}}
    boolean useSettingListStyle(){return true;}
    Drawable listSurface(){
        if(unifiedSettings())return new SettingsFocusDrawable(unit,12,Color.TRANSPARENT,true);
        if(useSettingListStyle())return ControlStyle.listRow(unit,10);
        StateListDrawable state=new StateListDrawable();state.addState(new int[]{android.R.attr.state_focused},surface(0x24ffffff,0x16ffffff,-1,12));state.addState(new int[]{android.R.attr.state_selected},surface(0x14ffffff,0x10ffffff,0x1effffff,12));state.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));return state;
    }
    LinearLayout listGroup(){LinearLayout box=unifiedSettings()?column():new SurfaceCorners.Column(this);box.setOrientation(LinearLayout.VERTICAL);if(unifiedSettings()){box.setPadding(px(.75f),px(.75f),px(.75f),px(.75f));box.setBackgroundDrawable(surface(0x0fffffff,0x0fffffff,UiPalette.EDGE,15));box.setClipChildren(false);box.setClipToPadding(false);}else{box.setPadding(px(4),px(4),px(4),px(4));box.setBackgroundDrawable(surface(0x12ffffff,0x06ffffff,0x1cffffff,16));UiCompat.clipOutline(box);}return box;}
    private Drawable uiGlyph(int resource){
        return uiGlyph(UiCompat.drawable(this,resource));
    }
    private Drawable uiGlyph(Drawable source){
        Drawable glyph=androidx.core.graphics.drawable.DrawableCompat.wrap(source).mutate();
        androidx.core.graphics.drawable.DrawableCompat.setTintList(glyph,unifiedSettings()?SettingsFocusDrawable.textColors(UiPalette.TEXT):new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{UiPalette.FAINT,UiPalette.TEXT}));
        return glyph;
    }
    Button iconButton(String description,int resource,Runnable action){
        return iconButton(description,uiGlyph(resource),16,action);
    }
    Button iconButton(String description,String glyph,int size,Runnable action){
        return iconButton(description,uiGlyph(new QuickControlIcon(glyph)),size,action);
    }
    private Button iconButton(String description,Drawable icon,int size,Runnable action){
        Button b=button("",action);b.setContentDescription(description);
        icon.setBounds(0,0,px(size),px(size));b.setCompoundDrawables(icon,null,null,null);
        b.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int horizontal=(right-left-icon.getBounds().width())/2,vertical=(bottom-top-icon.getBounds().height())/2;
            view.setPadding(horizontal,vertical,horizontal,vertical);
        });return b;
    }
    Button iconText(String text,int resource,Runnable action){Button b=button(text,action);Drawable icon=uiGlyph(resource);icon.setBounds(0,0,px(15),px(15));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(px(7));return b;}
    void gap(LinearLayout parent,float height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,px(height)));}
    void marginAdd(LinearLayout parent,View view,int width,int height,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:px(width),height<0?height:px(height));p.leftMargin=px(left);parent.addView(view,p);}
    LinearLayout toolbar(boolean dock){
        return toolbar(dock?1:0);
    }
    void leaveManagement(Runnable action){action.run();}
    LinearLayout managementContent(){
        managementInfo=row();LinearLayout.LayoutParams info=new LinearLayout.LayoutParams(-1,px(26));info.topMargin=px(8);root.addView(managementInfo,info);
        LinearLayout body=column();LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,0,1);content.topMargin=px(6);root.addView(body,content);return body;
    }
    TextView managementHint(String text){TextView hint=muted(text,11);hint.setSingleLine();hint.setEllipsize(android.text.TextUtils.TruncateAt.END);return hint;}
    LinearLayout toolbar(int selected){
        if(unifiedSettings())root.removeView(header);
        HorizontalScrollView navigation=new HorizontalScrollView(this);navigation.setFocusable(false);navigation.setHorizontalScrollBarEnabled(false);navigation.setFillViewport(true);
        if(unifiedSettings()){navigation.setClipChildren(false);navigation.setClipToPadding(false);navigation.setPadding(px(18),px(18),px(18),px(18));}
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(unifiedSettings()?36:54));p.topMargin=px(unifiedSettings()?0:22);root.addView(navigation,p);
        if(unifiedSettings()){p.height=px(72);p.setMargins(px(-18),px(-18),px(-18),px(-18));navigation.setLayoutParams(p);}
        boolean fit=!unifiedSettings()&&((AppManagementActivity)this).chinese();
        LinearLayout tools=row();tools.setClipChildren(false);tools.setClipToPadding(false);navigation.addView(tools,new HorizontalScrollView.LayoutParams(fit?-1:-2,-1));
        for(int i:MANAGEMENT_ORDER){
            Button tab=button(SettingsPages.title(i,((AppManagementActivity)this).chinese()),()->{});managementTab(tab,i,selected,header.getChildAt(0));
            if(fit){tab.setMinWidth(0);tab.setMinimumWidth(0);tab.setPadding(px(8),0,px(8),0);}
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(fit?0:-2,px(36),fit?1:0);tp.rightMargin=i==MANAGEMENT_ORDER[MANAGEMENT_ORDER.length-1]?0:px(fit?6:12);if(unifiedSettings()){tp.height=px(36);tp.rightMargin=i==MANAGEMENT_ORDER[MANAGEMENT_ORDER.length-1]?0:px(9);if(((AppManagementActivity)this).chinese())tp.width=px(SettingsPages.title(i,true).length()*13.5f+24);tab.setTextSize(TypedValue.COMPLEX_UNIT_PX,13.5f*unit);tab.setTypeface(navigationTypeface(tab.isSelected()));tab.setMinWidth(0);tab.setMinimumWidth(0);tab.setPadding(px(12),0,px(12),0);}
            tools.addView(tab,tp);
            tab.setOnFocusChangeListener((v,focused)->{if(focused)navigation.post(()->{Rect bounds=new Rect();tab.getDrawingRect(bounds);tab.requestRectangleOnScreen(bounds,false);});});
        }
        managementHeader();
        if(!fit)tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(!unifiedSettings()){View line=new View(this);line.setBackgroundColor(0x18ffffff);root.addView(line,new LinearLayout.LayoutParams(-1,px(1)));}return tools;
    }
    void managementTab(Button tab,int page,int selected,View back){
        managementPage=selected;managementTabs[page]=tab;styleManagementTab(tab,false);tab.setSelected(page==SettingsPages.section(selected));tab.setFocusableInTouchMode(true);tab.setId(androidx.core.view.ViewCompat.generateViewId());tab.setTag("management-tab-"+page);
        if(back.getId()==View.NO_ID)back.setId(androidx.core.view.ViewCompat.generateViewId());tab.setNextFocusUpId(unifiedSettings()?tab.getId():back.getId());
        if(page==selected)back.setNextFocusDownId(tab.getId());
        tab.setOnClickListener(v->selectManagementPage(page));
        if(unifiedSettings())SettingsTouchClick.attach(tab);
        tab.setOnKeyListener((v,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT && key!=KeyEvent.KEYCODE_DPAD_RIGHT && key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusManagementContent();
            else{int[] order=MANAGEMENT_ORDER;int position=0;for(int i=0;i<order.length;i++)if(order[i]==page)position=i;int next=order[(position+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)+order.length)%order.length];if(next!=page)managementTabs[next].performClick();}
            return true;
        });
    }
    void selectManagementPage(int page){
        Class<?>[] pages={AppManagementActivity.class,DockEditorActivity.class,HomeSortActivity.class,ChildModeActivity.class};
        startActivity(new Intent(this,pages[page]));
    }
    void managementPageSelected(int page){
        managementPage=page;
        for(int i:MANAGEMENT_ORDER){managementTabs[i].setSelected(i==SettingsPages.section(page));if(unifiedSettings())managementTabs[i].setTypeface(navigationTypeface(managementTabs[i].isSelected()));}
        for(int i=0;i<header.getChildCount();i++)header.getChildAt(i).setNextFocusDownId(managementTab().getId());
    }
    ImageView icon(android.graphics.drawable.Drawable art,int size){ImageView image=new SurfaceCorners.Image(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundDrawable(surface(UiPalette.CONTROL,UiPalette.CONTROL,0,size*.24f));UiCompat.clipOutline(image);image.setImageDrawable(art);image.setLayoutParams(new LinearLayout.LayoutParams(px(size),px(size)));return image;}
    EditText input(String text,String hint){EditText edit=new EditText(this);edit.setText(text);edit.setHint(hint);edit.setSingleLine(true);edit.setTextColor(unifiedSettings()?SettingsFocusDrawable.textColors(UiPalette.TEXT):ColorStateList.valueOf(TEXT));edit.setHintTextColor(unifiedSettings()?SettingsFocusDrawable.textColors(UiPalette.SECONDARY):ColorStateList.valueOf(MUTED));edit.setTextSize(TypedValue.COMPLEX_UNIT_PX,unifiedSettings()?15.36f*unit:px(14));edit.setPadding(px(unifiedSettings()?11.52f:14),px(unifiedSettings()?9.6f:10),px(unifiedSettings()?11.52f:14),px(unifiedSettings()?9.6f:10));edit.setBackgroundDrawable(focusSurface(unifiedSettings()?12:10));return edit;}
    final class Sheet {
        final Dialog dialog=new KeyFeedback.Dialog(ManagementUi.this){@Override int motionStyle(){return backdrop==null?super.motionStyle():R.style.QuietSheetAnimation;}@Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(unifiedSettings())SettingsFocusMotion.update(getCurrentFocus(),focused);if(focused&&backdrop!=null){backdrop.prepareForFocusedWindow(()->{if(dialog.isShowing()&&dialog.getWindow().getDecorView().hasWindowFocus())UiMotion.sheet(box);});}}};
        final LinearLayout body=column(),actions=row(),box=new LinearLayout(ManagementUi.this){
            @Override protected void onMeasure(int width,int height){
                if(heightMaximum>0){
                    int maximum=Math.min(px(heightMaximum),getResources().getDisplayMetrics().heightPixels-px(48));
                    if(View.MeasureSpec.getMode(height)!=View.MeasureSpec.UNSPECIFIED)maximum=Math.min(maximum,View.MeasureSpec.getSize(height));
                    height=View.MeasureSpec.makeMeasureSpec(Math.max(0,maximum),View.MeasureSpec.AT_MOST);
                }
                if(contentSized||fullWindow&&getLayoutParams().width==ViewGroup.LayoutParams.WRAP_CONTENT){
                    int limit=px(contentSized?contentMaximum:448);if(contentSized)limit=Math.min(limit,getResources().getDisplayMetrics().widthPixels-px(48));
                    int maximum=View.MeasureSpec.getMode(width)==View.MeasureSpec.UNSPECIFIED?limit:Math.min(limit,View.MeasureSpec.getSize(width));
                    super.onMeasure(View.MeasureSpec.makeMeasureSpec(maximum,View.MeasureSpec.AT_MOST),height);
                    int content=!contentSized&&scroll!=null&&scroll.getMeasuredHeight()>=px(bodyMaximum)?maximum:getMeasuredWidth();
                    width=View.MeasureSpec.makeMeasureSpec(Math.max(Math.min(px(contentSized?contentMinimum:320),maximum),content),View.MeasureSpec.EXACTLY);
                }
                super.onMeasure(width,height);
            }
        };
        final TextView heading;
        final ScrollView scroll;
        float bodyMaximum=240;
        private float feedbackReserved;
        private boolean contentSized,fixedSize;
        private float contentMinimum,contentMaximum,heightMaximum;
        private boolean fullWindow;
        private SettingsSheetBackdrop backdrop;
        void contentWidth(float minimum,float maximum){if(fixedSize)return;contentSized=true;contentMinimum=minimum;contentMaximum=maximum;box.requestLayout();}
        void lightSurface(){backdrop.light();}
        // Keep the panel and its chrome anchored; only the content viewport scrolls.
        void fixedSize(float width,float height){
            fixedSize=true;contentSized=false;heightMaximum=0;
            android.util.DisplayMetrics screen=getResources().getDisplayMetrics();
            FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)box.getLayoutParams();
            frame.width=Math.min(px(width),screen.widthPixels-px(48));frame.height=Math.min(px(height),screen.heightPixels-px(48));box.setLayoutParams(frame);
            LinearLayout.LayoutParams content=(LinearLayout.LayoutParams)scroll.getLayoutParams();content.height=0;content.weight=1;scroll.setLayoutParams(content);
        }
        // Wrap short content; the weighted viewport gives long content room to scroll.
        void contentSize(float width,float maximumHeight){
            fixedSize=false;contentSized=false;heightMaximum=maximumHeight;
            FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)box.getLayoutParams();
            frame.width=Math.min(px(width),getResources().getDisplayMetrics().widthPixels-px(48));frame.height=-2;box.setLayoutParams(frame);
            LinearLayout.LayoutParams content=(LinearLayout.LayoutParams)scroll.getLayoutParams();content.height=-2;content.weight=1;scroll.setLayoutParams(content);
        }
        void fitContent(float minimum,float maximum){
            fixedSize=false;contentSized=true;contentMinimum=minimum;contentMaximum=maximum;
            FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)box.getLayoutParams();frame.width=frame.height=ViewGroup.LayoutParams.WRAP_CONTENT;box.setLayoutParams(frame);
            LinearLayout.LayoutParams content=(LinearLayout.LayoutParams)scroll.getLayoutParams();content.height=ViewGroup.LayoutParams.WRAP_CONTENT;content.weight=0;scroll.setLayoutParams(content);scroll.setFillViewport(false);
            settingsActions(actions.getVisibility()==View.VISIBLE);box.requestLayout();
        }
        void fullScreen(){
            fixedSize=true;contentSized=false;heightMaximum=0;FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)box.getLayoutParams();frame.width=frame.height=-1;box.setLayoutParams(frame);
            box.setPadding(px(36),px(28),px(36),px(24));LinearLayout.LayoutParams content=(LinearLayout.LayoutParams)scroll.getLayoutParams();content.height=0;content.weight=1;content.setMargins(0,0,0,0);scroll.setLayoutParams(content);scroll.setPadding(0,0,0,0);scroll.setFillViewport(true);
        }
        Sheet(String title){this(title,null);}
        Sheet(String title,Sheet underlay){
            OperationFeedback.get(ManagementUi.this).watch(dialog,box,actions,height->{feedbackReserved=height;box.requestLayout();});
            box.setOrientation(LinearLayout.VERTICAL);if(unifiedSettings())box.setClipChildren(false);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);box.setPadding(px(30),px(27),px(30),px(25));
            if(unifiedSettings()){box.setPadding(px(18),px(18),px(18),px(18));box.setClipChildren(false);box.setClipToPadding(false);}
            backdrop=underlay==null?new SettingsSheetBackdrop(ManagementUi.this,box):
                    new SettingsSheetBackdrop(box,unit,(canvas,paint)->{
                        drawSheetBackdrop(canvas);
                        int[] position=new int[2];
                        underlay.box.getLocationOnScreen(position);
                        int save=canvas.save();
                        canvas.translate(position[0],position[1]);
                        underlay.box.draw(canvas);
                        canvas.restoreToCount(save);
                    },ManagementUi.this::filterSheetBackdrop);
            box.setBackgroundDrawable(backdrop);
            heading=center(title,23);if(unifiedSettings()){heading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);heading.setTextSize(TypedValue.COMPLEX_UNIT_PX,18*unit);}box.addView(heading,new LinearLayout.LayoutParams(-1,-2));gap(box,unifiedSettings()?12:18);
            scroll=new ScrollView(ManagementUi.this){@Override protected void onMeasure(int w,int h){if(fixedSize)super.onMeasure(w,h);else super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getMode(h)==View.MeasureSpec.UNSPECIFIED?px(Math.max(48,bodyMaximum-feedbackReserved)):Math.min(px(Math.max(48,bodyMaximum-feedbackReserved)),View.MeasureSpec.getSize(h)),View.MeasureSpec.AT_MOST));}@Override protected void dispatchDraw(Canvas canvas){if(!unifiedSettings()){super.dispatchDraw(canvas);return;}int save=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};scroll.setFillViewport(false);scroll.addView(body);box.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
            actions.setGravity(unifiedSettings()?Gravity.END|Gravity.CENTER_VERTICAL:Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,px(unifiedSettings()?33:42));ap.topMargin=px(unifiedSettings()?18:24);box.addView(actions,ap);
            dialog.setContentView(box);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(SettingsSheetBackdrop.DIM_AMOUNT);
            // Bound long package histories and app pickers without clipping the dialog actions.
        }
        void text(String text){TextView description=center(text,13);description.setTextColor(fullWindow?UiPalette.SECONDARY:UiPalette.SECONDARY);description.setLineSpacing(px(5),1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);if(fullWindow){description.setGravity(Gravity.START);description.setTextSize(TypedValue.COMPLEX_UNIT_PX,12.96f*unit);Paint.FontMetrics fm=description.getPaint().getFontMetrics();float leading=Math.max(0,22.032f*unit-(fm.descent-fm.ascent));description.setLineSpacing(leading,1);description.setPadding(0,Math.round(leading/2),0,Math.round(leading/2));description.setMinimumHeight(px(22.032f));p.topMargin=px(12.96f);p.bottomMargin=px(12.96f);}body.addView(description,p);}
        Button action(String text,Runnable action){Button b=button(text,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);if(fullWindow){b.setTextSize(TypedValue.COMPLEX_UNIT_PX,11.712f*unit);b.setPadding(px(13.71f),0,px(13.71f),0);b.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));if(actions.getChildCount()>0)p.leftMargin=px(6);}else p.setMargins(px(5),0,px(5),0);actions.addView(b,p);return b;}
        Button option(String text,Runnable action){Button b=button(text,action);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setBackgroundDrawable(useSettingListStyle()?listSurface():focusSurface(9));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(43));p.bottomMargin=px(6);body.addView(b,p);return b;}
        Button option(String text,int resource,Runnable action){Button b=option(text,action);Drawable icon=uiGlyph(resource);icon.setBounds(0,0,px(17),px(17));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(px(10));return b;}
        Button settingsLayout(String closeLabel,Runnable close){
            fullWindow=true;box.setClickable(true);box.setPadding(px(18.75f),px(18.75f),px(18.75f),px(18.75f));box.setBackgroundDrawable(backdrop);
            box.removeView(heading);LinearLayout title=row();heading.setTextSize(TypedValue.COMPLEX_UNIT_PX,19.2f*unit);heading.setTextColor(UiPalette.TEXT);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);title.addView(heading,new LinearLayout.LayoutParams(0,px(26.88f),1));
            Button dismiss=button(closeLabel,close);dismiss.setContentDescription(closeLabel);dismiss.setTag("sheet-close");dismiss.setId(androidx.core.view.ViewCompat.generateViewId());dismiss.setTextSize(TypedValue.COMPLEX_UNIT_PX,10.08f*unit);dismiss.setPadding(0,0,0,0);dismiss.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));title.addView(dismiss,new LinearLayout.LayoutParams(px(41.2f),px(25.2f)));box.addView(title,0,new LinearLayout.LayoutParams(-1,px(26.88f)));
            actions.getLayoutParams().height=px(34.56f);settingsActions(true);scroll.setVerticalScrollBarEnabled(false);scroll.setClipChildren(false);scroll.setClipToPadding(false);body.setClipChildren(false);body.setClipToPadding(false);
            FrameLayout overlay=new FrameLayout(ManagementUi.this);overlay.setClipChildren(false);View dim=new View(ManagementUi.this);dim.setBackgroundColor(0x35000000);dim.setOnClickListener(v->{if(box.isEnabled())close.run();});overlay.addView(dim,new FrameLayout.LayoutParams(-1,-1));((ViewGroup)box.getParent()).removeView(box);overlay.addView(box,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));dialog.setContentView(overlay);dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);return dismiss;
        }
        void settingsActions(boolean visible){
            actions.setVisibility(visible?View.VISIBLE:View.GONE);
            if(fullWindow){int side=px(18.75f),top=px(12),bottom=visible?px(18):side;LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)scroll.getLayoutParams();size.setMargins(-side,-top,-side,0);scroll.setLayoutParams(size);scroll.setPadding(side,top,side,bottom);box.setPadding(side,side,side,visible?side:0);((LinearLayout.LayoutParams)actions.getLayoutParams()).topMargin=0;}
            bodyMaximum=Math.max(120,getResources().getDisplayMetrics().heightPixels/unit-46.08f-128.94f+(visible?0:52.56f)+(fullWindow?(visible?30:30.75f):0));
        }
        // A first show waits for clean glass after focus transfer. Content changes
        // in an already focused dialog must reveal without another focus callback.
        void show(View focus){Window window=dialog.getWindow();int width=px(unifiedSettings()?448:444);if(contentSized&&!fullWindow){box.measure(View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().heightPixels,View.MeasureSpec.AT_MOST));width=box.getMeasuredWidth();}window.setLayout(fullWindow?-1:width,fullWindow?-1:-2);window.getDecorView().setSystemUiVisibility(5894);
            if(backdrop!=null){if(dialog.isShowing()&&window.getDecorView().hasWindowFocus()){backdrop.prepareForFocusedWindow(()->{if(dialog.isShowing()&&dialog.getWindow().getDecorView().hasWindowFocus())UiMotion.sheet(box);});}else{UiMotion.settle(box);box.setAlpha(0f);}}
            dialog.show();if(unifiedSettings())SettingsFocusMotion.attach(window.getDecorView());if(focus!=null)focus.requestFocus();}
    }
    void message(String text){error(text);}
    void feedback(String text){OperationFeedback.info(this,text);}
    void success(String text){OperationFeedback.success(this,text);}
    void error(String text){OperationFeedback.error(this,text);}
    void progress(String text){OperationFeedback.get(this).show(OperationFeedback.Kind.PROGRESS,text);}
}
