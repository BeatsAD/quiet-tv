package home.quiet.tv;

import android.app.Dialog;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** A single transient sheet over HOME. Only an explicit choice changes local preferences. */
public final class HomeAppActionsActivity extends ManagementUi {
    static final int REQUEST=7201;
    private static final int DELETE=7202;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Dialog dialog;
    private ManagementUi.Sheet sheet;
    private boolean closing;
    private String retainedFocus="";
    private float retainedScroll;
    private LinearLayout box;
    private FrameLayout sheetContent;
    private LinearLayout menuBox;
    private ScrollView menuScroll;
    // Retain the entry pixels while glass is off so the same menu can restore them.
    private Bitmap backdrop;
    private String pkg,mode="menu",pickerMode="",candidate="";
    private boolean fromDock,pickerFromMenu,waitingForDelete;
    private int generation;
    private final HashMap<String,Bitmap> pictures=new HashMap<>();
    @Override boolean unifiedSettings(){return true;}
    @Override boolean loadWallpaper(){return false;}
    @Override void drawSheetBackdrop(Canvas canvas){if(backdrop==null||!GlassStyle.get(this).rendering())canvas.drawColor(0xe6000000);else canvas.drawBitmap(backdrop,null,new RectF(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels),new Paint(3));}
    @Override public void glassChanged(){super.glassChanged();if(menuBox!=null)menuBox.invalidate();if(stage!=null)stage.invalidate();}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);stage.removeAllViews();stage.setBackgroundColor(Color.TRANSPARENT);
        pkg=getIntent().getStringExtra("package");if(pkg==null)pkg="";fromDock=getIntent().getBooleanExtra("dock",false);backdrop=entryBackdrop();
        if(state!=null){pkg=state.getString("app-menu-package",pkg);fromDock=state.getBoolean("app-menu-dock",fromDock);mode=state.getString("app-menu-mode","menu");pickerMode=state.getString("app-menu-picker","");pickerFromMenu=state.getBoolean("app-menu-from-menu");candidate=state.getString("app-menu-candidate","");retainedFocus=state.getString("app-menu-focus","");retainedScroll=state.getFloat("app-menu-scroll");waitingForDelete=state.getBoolean("app-menu-waiting-delete");}
        sheet=new Sheet("");sheet.settingsLayout("关闭",this::finish);sheet.settingsActions(false);dialog=sheet.dialog;box=sheet.body;sheetContent=(FrameLayout)sheet.box.getParent();dialog.setCanceledOnTouchOutside(true);
        // Keep one full-screen surface and dim layer while switching menu and sheet content.
        dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);
        dialog.setOnCancelListener(d->finish());dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP){finish();KeyFeedback.back(this);}return true;});
        if(state!=null&&mode.equals("picker"))picker(pickerMode,pickerFromMenu);
        else if(state!=null&&mode.equals("cover"))coverMenu();
        else if(state!=null&&mode.equals("unpin"))confirmUnpin();
        else if(state!=null&&mode.equals("uninstall"))confirmUninstall();
        else if(state!=null&&mode.equals("auth"))authorizeUninstall();
        else if(getIntent().getBooleanExtra("add",false))picker("add",false);else menu();
        if(waitingForDelete)dialog.hide();
    }
    private String name(String id){
        if(HomeAppCatalog.WATCH_TV.equals(id))return "看电视";
        if(HomeAppCatalog.WALLPAPER.equals(id))return "壁纸设置";
        if(HomeAppCatalog.DESKTOP_SETTINGS.equals(id))return "桌面设置";
        if(HomeAppCatalog.MANAGEMENT.equals(id))return "应用管理";
        try{return new AppNameStore(this).display(id,getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(id,0)).toString());}
        catch(PackageManager.NameNotFoundException e){return id;}
    }
    private AppRepository.App metadata(String id){
        if(id.startsWith("quiet:"))return null;
        try{ApplicationInfo info=getPackageManager().getApplicationInfo(id,0);AppRepository.App app=new AppRepository.App();app.pkg=id;app.label=name(id);
            app.system=(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;app.installed=AppAvailability.installed(getPackageManager(),info);return app;
        }catch(PackageManager.NameNotFoundException e){return null;}
    }
    private String protection(){AppRepository.App app=metadata(pkg);return app==null?"保留桌面管理入口":new AppOperations(this).protection(app);}
    private boolean canHide(){
        AppRepository.App app=metadata(pkg);
        return app!=null && new AppOperations(this).hidingProtection(app).isEmpty() && new ChildModeStore(this).eligible(pkg) && new HomeAppCatalog(this).launchable().containsKey(pkg);
    }
    private void content(String page,String title){
        mode=page;++generation;
        if(page.equals("menu")){
            if(menuBox!=null)sheetContent.removeView(menuBox);sheet.box.setVisibility(View.GONE);
            menuBox=column();menuBox.setClickable(true);menuBox.setPadding(px(16),px(16),px(16),px(16));menuBox.setBackgroundDrawable(new MenuBackdrop());
            menuScroll=new ScrollView(this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().heightPixels-px(64),View.MeasureSpec.AT_MOST));}};menuScroll.setVerticalScrollBarEnabled(false);menuScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            box=column();menuScroll.addView(box);menuBox.addView(menuScroll);sheetContent.addView(menuBox,new FrameLayout.LayoutParams(px(200),-2,Gravity.TOP|Gravity.LEFT));return;
        }
        if(menuBox!=null)menuBox.setVisibility(View.GONE);sheet.box.setVisibility(View.VISIBLE);
        box=sheet.body;box.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(false);sheet.heading.setText(title);sheet.heading.setSingleLine(true);sheet.heading.setEllipsize(android.text.TextUtils.TruncateAt.END);sheet.scroll.scrollTo(0,0);
    }
    private void show(float width,View focus,boolean anchored){
        if(mode.equals("menu")){showMenu(focus);return;}
        sheet.box.getLayoutParams().width=px(width);sheet.show(null);View requested=retainedFocus.isEmpty()?null:sheet.box.findViewWithTag(retainedFocus);if(requested==null||!requested.isEnabled())requested=focus;retainedFocus="";View target=requested;
        sheet.box.post(()->{if(closing)return;if(target!=null){target.requestFocusFromTouch();Rect area=new Rect();target.getDrawingRect(area);target.requestRectangleOnScreen(area,false);}if(retainedScroll>0){sheet.scroll.scrollTo(0,px(retainedScroll));retainedScroll=0;}});
    }
    private Button option(String label,String icon,Runnable action){
        if(mode.equals("menu")){
            Button b=button(label,action);b.setTag("app-menu-"+label);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(px(14),0,px(12),0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(16));
            StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},menuSurface(0xffeff0f4,0xffeff0f4,0,10));background.addState(new int[0],menuSurface(0,0,0,10));b.setBackgroundDrawable(background);
            b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused},{}},new int[]{0xff777d88,0xff191d24,label.equals("卸载应用")?0xffe8aaa6:0xffeff0f4}));
            if(!icon.isEmpty()){Drawable image=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon(icon)).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(image,b.getTextColors());image.setBounds(0,0,px(12),px(12));b.setCompoundDrawables(image,null,null,null);b.setCompoundDrawablePadding(px(13));}
            box.addView(b,new LinearLayout.LayoutParams(-1,px(43)));return b;
        }
        Button b=button(label,action);b.setTag("app-menu-"+label);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(px(12.96f),0,px(12.96f),0);QuickControlPreviewView.type(b,19.2f,26.88f,unit,UiPalette.TEXT);b.setPadding(px(12.96f),0,px(12.96f),0);b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));b.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,UiPalette.EDGE));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(39.3574f));if(box.getChildCount()>0)p.topMargin=px(7.2f);box.addView(b,p);return b;
    }
    private void menu(){
        pickerMode="";content("menu",name(pkg));boolean real=!pkg.startsWith("quiet:")&&!pkg.isEmpty();String protectedReason=protection();Button first;
        first=option("移动","move",()->{setResult(RESULT_OK,new Intent().putExtra("action","move").putExtra("package",pkg).putExtra("dock",fromDock));finish();});
        boolean canAdd=new DockStore(this).loadAvailable().size()<new DockStore(this).capacity();
        if(fromDock){option("替换应用","replace",()->picker("replace",true));Button add=option("添加应用","plus",()->picker("add",true));add.setEnabled(canAdd);}
        Button home=option(fromDock?"移除首页位置":"添加到首页",fromDock?"pin-off":"pin",()->{if(fromDock)confirmUnpin();else pin(pkg);});home.setEnabled(fromDock||canAdd);
        Button hide=option("隐藏","eye-off",this::hide);hide.setEnabled(canHide());
        Button uninstall=option("卸载应用","trash",this::authorizeUninstall);uninstall.setEnabled(real&&protectedReason.isEmpty());
        Button cover=option("更换封面","image",this::coverMenu);cover.setEnabled(AppCoverPolicy.allowed(this,pkg));option("管理此应用","layout-grid",()->{startActivity(new Intent(this,AppManagementActivity.class).putExtra("management-page",SettingsPages.APPS).putExtra("selected-package",pkg).putExtra("focus-app-details",true).putExtra("backdrop",backdrop));finish();});
        show(200,first,true);
    }
    private GradientDrawable menuSurface(int top,int bottom,int stroke,int radius){GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});shape.setCornerRadius(px(radius));shape.setStroke(px(1),stroke);return shape;}
    private void showMenu(View focus){
        menuBox.measure(View.MeasureSpec.makeMeasureSpec(px(200),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().heightPixels-px(32),View.MeasureSpec.AT_MOST));
        int screenWidth=getResources().getDisplayMetrics().widthPixels,screenHeight=getResources().getDisplayMetrics().heightPixels,x=getIntent().getIntExtra("anchor-x",screenWidth/2),y=getIntent().getIntExtra("anchor-y",screenHeight/2),bottom=getIntent().getIntExtra("anchor-bottom",y);
        FrameLayout.LayoutParams position=(FrameLayout.LayoutParams)menuBox.getLayoutParams();position.leftMargin=Math.max(px(16),Math.min(screenWidth-px(216),x-px(100)));position.topMargin=y>menuBox.getMeasuredHeight()+px(16)?y-menuBox.getMeasuredHeight()-px(10):Math.min(screenHeight-menuBox.getMeasuredHeight()-px(16),bottom+px(10));menuBox.setLayoutParams(position);
        dialog.show();View requested=retainedFocus.isEmpty()?null:menuBox.findViewWithTag(retainedFocus);if(requested==null||!requested.isEnabled())requested=focus;retainedFocus="";View target=requested;menuBox.post(()->{if(closing)return;if(target!=null)target.requestFocusFromTouch();if(retainedScroll>0){menuScroll.scrollTo(0,px(retainedScroll));retainedScroll=0;}});
    }
    /** Keeps the 1.3.6 menu's round corners and wallpaper tint local to this surface. */
    private final class MenuBackdrop extends Drawable {
        private final Paint paint=new Paint(3);private final RectF area=new RectF();private final Path clip=new Path();private final int[] location=new int[2];private final GradientDrawable fallback=menuSurface(0xff30343e,0xff23272f,0x38ffffff,16);private Shader tint;
        @Override protected void onBoundsChange(Rect bounds){area.set(bounds);clip.reset();clip.addRoundRect(area,22*unit,22*unit,Path.Direction.CW);tint=new LinearGradient(0,bounds.top,0,bounds.bottom,0x30e6e8ec,0x202c3038,Shader.TileMode.CLAMP);fallback.setBounds(bounds);}
        @Override public void draw(Canvas canvas){if(backdrop==null||!GlassStyle.get(HomeAppActionsActivity.this).rendering()){fallback.draw(canvas);return;}float radius=22*unit;paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(0x18000000);int save=canvas.save();canvas.translate(0,2*unit);canvas.drawRoundRect(area,radius,radius,paint);canvas.restoreToCount(save);save=canvas.save();canvas.clipPath(clip);menuBox.getLocationOnScreen(location);canvas.translate(-location[0],-location[1]);paint.setAlpha(255);canvas.drawBitmap(backdrop,null,new RectF(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels),paint);canvas.restoreToCount(save);paint.setAlpha(255);paint.setShader(tint);canvas.drawRoundRect(area,radius,radius,paint);paint.setShader(null);float stroke=Math.max(1,.5f*unit);area.inset(stroke/2,stroke/2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(0x66ffffff);canvas.drawRoundRect(area,radius-stroke/2,radius-stroke/2,paint);area.inset(-stroke/2,-stroke/2);paint.setStyle(Paint.Style.FILL);}
        @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void changed(String focus){setResult(RESULT_OK,new Intent().putExtra("action","changed").putExtra("package",focus));finish();}
    private void changed(String focus,String notice,boolean success){setResult(RESULT_OK,new Intent().putExtra("action","changed").putExtra("package",focus).putExtra("feedback",notice).putExtra("feedback-success",success));finish();}
    private void failure(String message){if(dialog!=null&&dialog.isShowing()){error(message);return;}content("notice","未完成");box.addView(muted(message,14));Button close=option("返回","arrow-left",this::handleBack);show(400,close,false);}
    private void hide(){if(!canHide()){failure("此应用必须保留或已不可用");return;}if(new HomeVisibilityStore(this).hide(pkg))changed("","已隐藏，可在隐藏应用中恢复",true);else failure("保存失败，请重试");}
    private void confirmUnpin(){
        if(!fromDock||!new DockStore(this).loadAvailable().contains(pkg)){failure("首页位置已变化，请重新选择");return;}
        content("unpin","移除首页位置？");
        LinearLayout identity=row();identity.setGravity(Gravity.CENTER_VERTICAL);
        ResolveInfo entry=new HomeAppCatalog(this).find(pkg);Bitmap artwork=entry==null?HomeEntryArtwork.render(this,name(pkg),pkg):AppArtwork.load(this,entry);
        DesktopSettingsStore appearance=new DesktopSettingsStore(this);boolean rectangle="rectangle".equals(appearance.iconShape());
        ImageView image=new ImageView(this);image.setImageDrawable(new AppShapeDrawable(artwork,"circle".equals(appearance.iconShape()),AppCoverStore.get(this).contain(pkg)));image.setScaleType(ImageView.ScaleType.FIT_XY);image.setContentDescription(name(pkg));identity.addView(image,new LinearLayout.LayoutParams(px(78),px(rectangle?44:78)));
        LinearLayout copy=column();TextView title=label(name(pkg),18);QuickControlPreviewView.type(title,24,33.6f,unit,UiPalette.TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView description=muted("应用和数据保留，可在「全部应用」中找到。",12);QuickControlPreviewView.type(description,17.28f,27.2f,unit,UiPalette.SECONDARY);copy.addView(description,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=px(12.96f);identity.addView(copy,cp);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=px(12.96f);box.addView(identity,ip);
        sheet.settingsActions(true);Button cancel=sheet.action("取消",this::finish);cancel.setTag("app-unpin-cancel");
        Button confirm=sheet.action("移除首页位置",this::unpin);confirm.setTag("app-unpin-confirm");confirm.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.SELECTED,false,0x4dffffff));show(560,cancel,false);
    }
    private void unpin(){ArrayList<String> values=new DockStore(this).load();if(!values.remove(pkg)){failure("首页位置已变化，请重新选择");return;}if(new DockStore(this).saveOrder(values))changed("","已从首页移除，应用保留",true);else failure("保存失败，请重试");}
    private void pin(String id){
        ArrayList<String> values=new DockStore(this).loadAvailable();
        if(!available(id)){failure("应用状态已变化，请重新选择");return;}
        if(values.contains(id)){changed(id);return;}
        candidate=id;if(values.size()>=new DockStore(this).capacity()){menu();return;}
        values.add(id);if(new DockStore(this).saveOrder(values))changed(id,"已添加到首页",true);else failure("保存失败，请重试");
    }
    private boolean available(String id){for(HomeAppCatalog.Entry entry:new HomeAppCatalog(this).load(new DockStore(this).load()))if(id.equals(entry.id))return true;return false;}
    private void picker(String choice,boolean fromMenu){
        // Keep the current surface until the complete picker is ready; no short loading window.
        pickerMode=choice;pickerFromMenu=fromMenu;int request=++generation;
        worker.execute(()->{
            ArrayList<HomeAppCatalog.Entry> choices=new ArrayList<>();HashMap<String,Bitmap> artwork=new HashMap<>();String error=null;
            try{ArrayList<String> pinned=new DockStore(this).load();for(HomeAppCatalog.Entry entry:new HomeAppCatalog(this).load(pinned))if(!pinned.contains(entry.id)||"replace".equals(pickerMode)&&entry.id.equals(pkg)){choices.add(entry);artwork.put(entry.id,entry.info==null?HomeEntryArtwork.render(this,entry.label,entry.id):AppArtwork.load(this,entry.info));}}
            catch(RuntimeException e){error="无法读取应用列表";}
            String problem=error;runOnUiThread(()->{if(isFinishing()||UiCompat.destroyed(this)||request!=generation)return;if(problem!=null){failure(problem);return;}pictures.putAll(artwork);pickerGrid(choices);});
        });
    }
    private void pickerGrid(List<HomeAppCatalog.Entry> choices){
        content("picker","replace".equals(pickerMode)?"替换应用":"添加应用");LinearLayout grid=column();grid.setClipChildren(false);grid.setClipToPadding(false);box.addView(grid,new LinearLayout.LayoutParams(-1,-2));LinearLayout line=null;View first=null;DesktopSettingsStore appearance=new DesktopSettingsStore(this);boolean rectangle="rectangle".equals(appearance.iconShape());
        for(int i=0;i<choices.size();i++){
            HomeAppCatalog.Entry entry=choices.get(i);if(i%4==0){line=row();line.setGravity(Gravity.TOP);line.setClipChildren(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);if(i>0)rp.topMargin=px(10.56f);grid.addView(line,rp);}
            LinearLayout card=column();card.setGravity(Gravity.CENTER_HORIZONTAL);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);card.setTag("app-choice-"+entry.id);card.setId(androidx.core.view.ViewCompat.generateViewId());card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);card.setPadding(px(10.35f),px(10.35f),px(10.35f),px(10.35f));card.setBackgroundDrawable(new SettingsFocusDrawable(unit,8.64f,0x06ffffff,false,0x22ffffff));card.setContentDescription("选择 "+entry.label);
            ImageView image=new ImageView(this);image.setImageDrawable(new AppShapeDrawable(pictures.get(entry.id),"circle".equals(appearance.iconShape()),AppCoverStore.get(this).contain(entry.id)));image.setScaleType(ImageView.ScaleType.FIT_XY);card.addView(image,new LinearLayout.LayoutParams(-1,px(rectangle?78.164f:138.96f)));
            TextView title=center(entry.label,12);QuickControlPreviewView.type(title,15.36f,21.5f,unit,UiPalette.TEXT);title.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));title.setDuplicateParentStateEnabled(true);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,px(16.125f));tp.topMargin=px(7.68f);card.addView(title,tp);
            card.setOnClickListener(v->KeyFeedback.clicked(v,()->select(entry.id)));SettingsTouchClick.attach(card);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(i%4>0)cp.leftMargin=px(10.56f);line.addView(card,cp);if(first==null||entry.id.equals(candidate))first=card;
            if(i==choices.size()-1)for(int n=i%4+1;n<4;n++){LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,1,1);sp.leftMargin=px(10.56f);line.addView(new View(this),sp);}
        }
        if(choices.isEmpty())box.addView(center("所有可用应用已在首页",16));show(707.82f,first,false);
    }
    private void select(String id){
        if(!available(id)||new DockStore(this).loadAvailable().contains(id)&&!("replace".equals(pickerMode)&&id.equals(pkg))){failure("应用状态已变化，请重新选择");return;}
        if(!pickerMode.equals("replace")){pin(id);return;}
        ArrayList<String> values=new DockStore(this).loadAvailable();int index=values.indexOf(pkg);if(index<0){failure("原位置已变化，请重新选择");return;}
        values.set(index,id);if(new DockStore(this).saveOrder(values))changed(id,"首页应用已替换",true);else failure("保存失败，请重试");
    }
    private void authorizeUninstall(){
        if(!protection().isEmpty()){failure(protection());return;}
        confirmUninstall();
    }
    private void confirmUninstall(){
        AppRepository.App app=metadata(pkg);if(app==null||!app.installed||!protection().isEmpty()){failure("应用状态已变化，不能卸载");return;}
        content("uninstall","卸载应用？");sheet.text(name(pkg));TextView warning=muted(app.system?"此系统应用需在管理应用中继续处理，请先核对当前可用操作。":"卸载当前用户下的此应用，并删除该用户的应用数据。再次使用需要重新安装，数据不能恢复。",12);QuickControlPreviewView.type(warning,14.464f,23.8656f,unit,UiPalette.SECONDARY);box.addView(warning);sheet.settingsActions(true);Button cancel=sheet.action("取消",this::finish);
        Button confirm=sheet.action(app.system?"在应用管理中继续":"确认卸载应用",()->{
            AppRepository.App latest=metadata(pkg);
            if(latest==null||!latest.installed||latest.system!=app.system||!protection().isEmpty()||new ChildModeStore(this).hidden(pkg)){failure("应用状态已变化，请重新确认");return;}
            if(app.system){startActivity(new Intent(this,AppManagementActivity.class).putExtra("selected-package",pkg));finish();return;}
            ChildModeAuth.uninstall(this,pkg,stamp->{
                try{new ChildModeStore(this).requireUninstallAuthorization(pkg,stamp);}catch(java.io.IOException error){failure(error.getMessage());return;}
                Intent intent=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)).putExtra(Intent.EXTRA_RETURN_RESULT,true);
                waitingForDelete=true;
                if(ExternalActivityLauncher.dispatch("entry=app.uninstall "+DiagnosticLog.intent(intent),()->startActivityForResult(intent,DELETE)))dialog.dismiss();
                else{waitingForDelete=false;failure("系统卸载入口不可用，应用已保留");}
            },()->cancel.requestFocusFromTouch());
        });confirm.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.SELECTED,false,0x4dffffff));show(537.6f,cancel,false);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==DELETE){waitingForDelete=false;AppRepository.App current=metadata(pkg);boolean removed=current==null||!current.installed;if(removed){new DockStore(this).remove(pkg);new HomeOrderStore(this).remove(pkg);new HomeVisibilityStore(this).remove(pkg);}changed("",removed?"应用已卸载":result==RESULT_CANCELED?"已取消卸载":"卸载尚未完成，请核对应用状态",removed);}}
    private void coverMenu(){
        startActivity(new Intent(this,DesktopSettingsActivity.class).putExtra("management-page",SettingsPages.ICONS).putExtra("selected-package",pkg).putExtra("backdrop",backdrop));finish();
    }
    @Override public void handleBack(){finish();KeyFeedback.back(this);}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("app-menu-package",pkg);state.putBoolean("app-menu-dock",fromDock);state.putString("app-menu-mode",mode);state.putString("app-menu-picker",pickerMode);state.putBoolean("app-menu-from-menu",pickerFromMenu);state.putString("app-menu-candidate",candidate);state.putBoolean("app-menu-waiting-delete",waitingForDelete);View focus=dialog.getCurrentFocus();state.putString("app-menu-focus",focus!=null&&focus.getTag() instanceof String?(String)focus.getTag():"");state.putFloat("app-menu-scroll",(mode.equals("menu")?menuScroll:sheet.scroll).getScrollY()/unit);}
    @Override protected void onStop(){super.onStop();if(!waitingForDelete&&!isChangingConfigurations())finish();}
    @Override protected void onDestroy(){closing=true;++generation;ChildModeAuth.dismiss(this);if(dialog!=null)dialog.dismiss();worker.shutdown();super.onDestroy();}
}
