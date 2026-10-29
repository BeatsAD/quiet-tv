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

/** Native management surfaces share the launcher's quiet glass and white focus outline. */
abstract class ManagementUi extends androidx.activity.ComponentActivity {
    static final int TEXT=0xffeff0f4,MUTED=0xff9298a4;
    LinearLayout root,header,managementInfo;
    FrameLayout stage;
    float unit;
    private static final int[] MANAGEMENT_ORDER=SettingsPages.ORDER;
    private final Button[] managementTabs=new Button[SettingsPages.COUNT];
    private int managementPage=-1;
    private boolean managementEntry=true;
    private Button managementStatus;
    private Button managementMoreButton;
    private KeyFeedback.Popup managementMorePopup;
    private UpdateBadgeView updateBadge;
    private final UpdateRepository.Listener updateListener=()->{if(updateBadge!=null)updateBadge.available(UpdateRepository.get(this).available!=null);};
    int px(float value){return Math.round(value*unit);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);BackNavigation.install(this,this::handleBack);unit=getResources().getDisplayMetrics().widthPixels/960f;
        getWindow().getDecorView().setSystemUiVisibility(5894);
        stage=new FrameLayout(this);stage.setBackgroundColor(0xff0e0f13);setContentView(stage);
        if(loadWallpaper()){
        WallpaperStore wallpaperStore=WallpaperStore.get(this);
        WallpaperCatalog wallpaperState=wallpaperStore.snapshot();
        WallpaperCatalog.Entry wallpaperEntry=wallpaperState.find(wallpaperState.current);
        if(wallpaperEntry!=null && !WallpaperCatalog.DEFAULT.equals(wallpaperEntry.id))try{
            Bitmap bitmap=WallpaperImages.decode(this,wallpaperStore,wallpaperEntry,480,270);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(bitmap);stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
        }catch(java.io.IOException ignored){}
        }
        View shade=new View(this);shade.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xf00c0d11,0xf50b0c10}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        root=column();root.setPadding(px(48),px(28),px(48),px(16));stage.addView(root,new FrameLayout.LayoutParams(-1,-1));
        header=row();root.addView(header,new LinearLayout.LayoutParams(-1,px(38)));
        Button back=iconButton("返回",R.drawable.ic_manage_back,()->{handleBack();KeyFeedback.back(this);});header.addView(back,new LinearLayout.LayoutParams(px(34),px(34)));
        TextView title=label("应用管理",27);title.setPadding(px(14),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
    }
    boolean loadWallpaper(){return true;}
    boolean localManagement(){return true;}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);managementEntry=true;}
    @Override protected void onResume(){super.onResume();UpdateRepository.get(this).add(updateListener);UpdateRepository.get(this).foreground();if(managementEntry && managementPage>=0){managementEntry=false;managementTab().requestFocus();}}
    @Override protected void onStop(){UpdateRepository.get(this).remove(updateListener);if(managementMorePopup!=null)managementMorePopup.closeQuietly();super.onStop();}
    @Override protected void onDestroy(){super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);DiagnosticLog.info("input.window","page="+getClass().getSimpleName()+" "+InputDiagnostics.focus(this));}
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){InputDiagnostics.pointer(this,event);return super.dispatchTouchEvent(event);}
    void managementConnected(){}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){return KeyFeedback.dispatch(getWindow().getDecorView(),event,()->{DiagnosticLog.navigationKey("quiet_tv_management",event);return super.dispatchKeyEvent(event);});}
    void connectionPanel(){startActivity(new Intent(this,MaintenanceHelpActivity.class));}
    void managementMore(){
        LinearLayout menu=new KeyFeedback.Menu(this);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(px(7),px(7),px(7),px(7));menu.setBackgroundDrawable(surface(0xfa25262e,0xfa25262e,0,18));
        managementMoreOptions(menu);
        int width=px(180);managementMorePopup=new KeyFeedback.Popup(this,menu,width,-2);
        managementMorePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if(Build.VERSION.SDK_INT>=21)managementMorePopup.setElevation(px(14));
        managementMorePopup.setOutsideTouchable(true);
        managementMorePopup.setOnDismissListener(()->{managementMorePopup=null;managementMoreButton.requestFocus();});
        for(int i=0;i<menu.getChildCount();i++){
            final int index=i;
            menu.getChildAt(i).setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
                if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)menu.getChildAt(Math.max(0,Math.min(menu.getChildCount()-1,index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)))).requestFocus();return true;}return false;
            });
        }
        managementMorePopup.showAsDropDown(managementMoreButton,managementMoreButton.getWidth()-width,px(9));menu.getChildAt(0).requestFocus();
    }
    void managementMoreOptions(LinearLayout menu){
        managementMoreOption(menu,"帮助与恢复",()->startActivity(new Intent(this,MaintenanceHelpActivity.class)));
    }
    void managementMoreOption(LinearLayout menu,String title,Runnable action){
        Button choice=button(title,()->{managementMorePopup.closeQuietly();action.run();});choice.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(15));choice.setTextColor(0xffeeeeef);
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
    Button managementTab(){return managementTabs[managementPage];}
    boolean managementTabFocused(){for(Button tab:managementTabs)if(tab!=null && tab.isFocused())return true;return false;}
    void focusManagementContent(){}
    public void handleBack(){finish();}
    LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));view.setTextColor(TEXT);view.setGravity(Gravity.CENTER_VERTICAL);view.setIncludeFontPadding(false);return view;}
    TextView muted(String text,int size){TextView view=label(text,size);view.setTextColor(MUTED);return view;}
    TextView center(String text,int size){TextView view=label(text,size);view.setGravity(Gravity.CENTER);return view;}
    GradientDrawable surface(int top,int bottom,int stroke,float radius){GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});shape.setCornerRadius(px(radius));shape.setStroke(px(1),stroke);return shape;}
    Drawable focusSurface(float radius){return ControlStyle.action(unit,radius,0x10ffffff,0x22ffffff);}
    void focusable(View view,float radius){view.setSoundEffectsEnabled(false);view.setFocusable(true);view.setBackgroundDrawable(focusSurface(radius));}
    Button button(String text,Runnable action){Button b=new KeyFeedback.Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(13));b.setTextColor(TEXT);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);b.setPadding(px(16),0,px(16),0);b.setBackgroundDrawable(focusSurface(24));if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
    void styleTab(Button b,boolean compact){
        ControlStyle.choice(b,unit,compact?15:18,Color.TRANSPARENT,0);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(compact?12:16));
        b.setSingleLine(true);b.setPadding(px(compact?10:18),0,px(compact?10:18),0);b.setMinWidth(px(compact?48:100));
    }
    void styleManagementTab(Button b,boolean compact){styleTab(b,compact);}
    boolean useSettingListStyle(){return true;}
    Drawable listSurface(){
        if(useSettingListStyle())return ControlStyle.listRow(unit,10);
        StateListDrawable state=new StateListDrawable();state.addState(new int[]{android.R.attr.state_focused},surface(0x24ffffff,0x16ffffff,-1,12));state.addState(new int[]{android.R.attr.state_selected},surface(0x14ffffff,0x10ffffff,0x1effffff,12));state.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));return state;
    }
    LinearLayout listGroup(){LinearLayout box=column();box.setPadding(px(4),px(4),px(4),px(4));box.setBackgroundDrawable(surface(0x12ffffff,0x06ffffff,0x1cffffff,16));UiCompat.clipOutline(box);return box;}
    Button iconButton(String description,int resource,Runnable action){
        Button b=button("",action);b.setContentDescription(description);
        Drawable icon=UiCompat.drawable(ManagementUi.this,resource);icon.setBounds(0,0,px(16),px(16));b.setCompoundDrawables(icon,null,null,null);
        b.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int horizontal=(right-left-icon.getBounds().width())/2,vertical=(bottom-top-icon.getBounds().height())/2;
            view.setPadding(horizontal,vertical,horizontal,vertical);
        });return b;
    }
    Button iconText(String text,int resource,Runnable action){Button b=button(text,action);Drawable icon=UiCompat.drawable(ManagementUi.this,resource);icon.setBounds(0,0,px(15),px(15));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(px(7));return b;}
    void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,px(height)));}
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
        HorizontalScrollView navigation=new HorizontalScrollView(this);navigation.setFocusable(false);navigation.setHorizontalScrollBarEnabled(false);navigation.setFillViewport(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(54));p.topMargin=px(22);root.addView(navigation,p);
        LinearLayout tools=row();navigation.addView(tools,new HorizontalScrollView.LayoutParams(-2,-1));
        for(int i:MANAGEMENT_ORDER){
            Button tab=button(SettingsPages.title(i,((AppManagementActivity)this).chinese()),()->{});managementTab(tab,i,selected,header.getChildAt(0));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,px(36));tp.rightMargin=px(12);tools.addView(tab,tp);
            tab.setOnFocusChangeListener((v,focused)->{if(focused)navigation.post(()->{Rect bounds=new Rect();tab.getDrawingRect(bounds);tab.requestRectangleOnScreen(bounds,false);});});
        }
        managementHeader();
        tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        View line=new View(this);line.setBackgroundColor(0x18ffffff);root.addView(line,new LinearLayout.LayoutParams(-1,px(1)));return tools;
    }
    void managementTab(Button tab,int page,int selected,View back){
        managementPage=selected;managementTabs[page]=tab;styleManagementTab(tab,false);tab.setSelected(page==selected);tab.setFocusableInTouchMode(true);tab.setId(androidx.core.view.ViewCompat.generateViewId());tab.setTag("management-tab-"+page);
        if(back.getId()==View.NO_ID)back.setId(androidx.core.view.ViewCompat.generateViewId());tab.setNextFocusUpId(back.getId());
        if(page==selected)back.setNextFocusDownId(tab.getId());
        tab.setOnClickListener(v->selectManagementPage(page));
        tab.setOnKeyListener((v,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_LEFT && key!=KeyEvent.KEYCODE_DPAD_RIGHT && key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
            if(event.getAction()==KeyEvent.ACTION_UP)return true;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN)focusManagementContent();
            else{int[] order=MANAGEMENT_ORDER;int position=0;for(int i=0;i<order.length;i++)if(order[i]==page)position=i;int next=order[Math.max(0,Math.min(order.length-1,position+(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)))];if(next!=page)managementTabs[next].performClick();}
            return true;
        });
    }
    void selectManagementPage(int page){
        Class<?>[] pages={AppManagementActivity.class,DockEditorActivity.class,HomeSortActivity.class,ChildModeActivity.class};
        startActivity(new Intent(this,pages[page]));
    }
    void managementPageSelected(int page){
        managementPage=page;
        for(int i:MANAGEMENT_ORDER)managementTabs[i].setSelected(i==page);
        for(int i=0;i<header.getChildCount();i++)header.getChildAt(i).setNextFocusDownId(managementTab().getId());
    }
    ImageView icon(android.graphics.drawable.Drawable art,int size){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundDrawable(surface(0x08ffffff,0x08ffffff,0,size*.24f));UiCompat.clipOutline(image);image.setImageDrawable(art);image.setLayoutParams(new LinearLayout.LayoutParams(px(size),px(size)));return image;}
    EditText input(String text,String hint){EditText edit=new EditText(this);edit.setText(text);edit.setHint(hint);edit.setSingleLine(true);edit.setTextColor(TEXT);edit.setHintTextColor(MUTED);edit.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(14));edit.setPadding(px(14),px(10),px(14),px(10));edit.setBackgroundDrawable(focusSurface(10));return edit;}
    final class Sheet {
        final Dialog dialog=new KeyFeedback.Dialog(ManagementUi.this);
        final LinearLayout body=column(),actions=row(),box=column();
        final TextView heading;
        Sheet(String title){
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);box.setPadding(px(30),px(27),px(30),px(25));box.setBackgroundDrawable(surface(0xff292c33,0xff1b1e24,0x38ffffff,21));
            heading=center(title,23);box.addView(heading,new LinearLayout.LayoutParams(-1,-2));gap(box,18);
            ScrollView scroll=new ScrollView(ManagementUi.this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getMode(h)==View.MeasureSpec.UNSPECIFIED?px(240):Math.min(px(240),View.MeasureSpec.getSize(h)),View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(body);box.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
            actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,px(42));ap.topMargin=px(24);box.addView(actions,ap);
            dialog.setContentView(box);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.65f);
            // Bound long package histories and app pickers without clipping the dialog actions.
        }
        void text(String text){TextView description=center(text,13);description.setTextColor(0xffb0b4be);description.setLineSpacing(px(5),1);body.addView(description,new LinearLayout.LayoutParams(-1,-2));}
        Button action(String text,Runnable action){Button b=button(text,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.setMargins(px(5),0,px(5),0);actions.addView(b,p);return b;}
        Button option(String text,Runnable action){Button b=button(text,action);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setBackgroundDrawable(useSettingListStyle()?listSurface():focusSurface(9));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(43));p.bottomMargin=px(6);body.addView(b,p);return b;}
        Button option(String text,int resource,Runnable action){Button b=option(text,action);Drawable icon=UiCompat.drawable(ManagementUi.this,resource);icon.setBounds(0,0,px(17),px(17));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(px(10));return b;}
        void show(View focus){dialog.show();Window window=dialog.getWindow();window.setLayout(px(444),-2);window.getDecorView().setSystemUiVisibility(5894);if(focus!=null)focus.requestFocus();}
    }
    void message(String text){Sheet sheet=new Sheet("提示");sheet.text(text);Button close=sheet.action("确定",sheet.dialog::dismiss);sheet.show(close);}
    private TextView feedback;
    void feedback(String text){if(feedback!=null)stage.removeView(feedback);TextView toast=center(text,13);toast.setPadding(px(20),px(12),px(20),px(12));toast.setBackgroundDrawable(surface(0xff292e34,0xff23272d,0x30ffffff,28));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=px(32);stage.addView(toast,p);feedback=toast;UiCompat.announce(toast,text);toast.postDelayed(()->{stage.removeView(toast);if(feedback==toast)feedback=null;},3000);}
}
