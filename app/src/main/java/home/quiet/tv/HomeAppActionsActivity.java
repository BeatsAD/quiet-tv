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
    private LinearLayout box;
    private Bitmap backdrop;
    private String pkg,mode="menu",pickerMode="",candidate="";
    private boolean fromDock,pickerFromMenu,waitingForDelete;
    private int generation;
    private final HashMap<String,Bitmap> pictures=new HashMap<>();
    @Override boolean loadWallpaper(){return false;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setContentView(new FrameLayout(this));
        pkg=getIntent().getStringExtra("package");if(pkg==null)pkg="";
        fromDock=getIntent().getBooleanExtra("dock",false);
        backdrop=getIntent().getParcelableExtra("backdrop");
        dialog=new KeyFeedback.Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);dialog.setOnCancelListener(d->{handleBack();KeyFeedback.returned(this);});
        if(getIntent().getBooleanExtra("add",false))picker("add",false);else menu();
    }
    private String name(String id){
        if(HomeAppCatalog.WALLPAPER.equals(id))return "壁纸设置";
        if(HomeAppCatalog.DESKTOP_SETTINGS.equals(id))return "桌面设置";
        if(HomeAppCatalog.MANAGEMENT.equals(id))return "应用管理";
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(id,0)).toString();}
        catch(PackageManager.NameNotFoundException e){return id;}
    }
    private AppRepository.App metadata(String id){
        if(id.startsWith("quiet:"))return null;
        try{ApplicationInfo info=getPackageManager().getApplicationInfo(id,0);AppRepository.App app=new AppRepository.App();app.pkg=id;app.label=name(id);
            app.system=(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;app.installed=AppAvailability.installed(getPackageManager(),info);return app;
        }catch(PackageManager.NameNotFoundException e){return null;}
    }
    private String protection(){AppRepository.App app=metadata(pkg);return app==null?"保留桌面管理入口":new AppOperations(this).protection(app);}
    private void content(String page,String title){
        mode=page;++generation;box=column();box.setPadding(px(16),px(16),px(16),px(16));box.setBackgroundDrawable(surface(0xff30343e,0xff23272f,0x38ffffff,16));
        if((page.equals("menu")||page.equals("picker"))&&backdrop!=null)box.setBackgroundDrawable(new DockBackdropDrawable((canvas,paint)->{paint.setAlpha(255);canvas.drawBitmap(backdrop,null,new RectF(0,0,getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels),paint);},box,getWindow().getDecorView(),unit));
        if(!title.isEmpty()){TextView heading=label(title,22);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.setPadding(px(8),px(4),px(8),px(14));box.addView(heading);}
        dialog.setContentView(box);
    }
    private void show(int width,View focus,boolean anchored){
        Window window=dialog.getWindow();window.setWindowAnimations(0);window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.35f);window.getDecorView().setSystemUiVisibility(5894);window.setLayout(px(width),-2);
        if(anchored){
            box.measure(View.MeasureSpec.makeMeasureSpec(px(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(px(490),View.MeasureSpec.AT_MOST));
            int screenWidth=getResources().getDisplayMetrics().widthPixels,screenHeight=getResources().getDisplayMetrics().heightPixels;
            int x=getIntent().getIntExtra("anchor-x",screenWidth/2),y=getIntent().getIntExtra("anchor-y",screenHeight/2),bottom=getIntent().getIntExtra("anchor-bottom",y);
            WindowManager.LayoutParams p=window.getAttributes();p.gravity=Gravity.TOP|Gravity.LEFT;p.x=Math.max(px(16),Math.min(screenWidth-px(width+16),x-px(width/2f)));
            p.y=y>box.getMeasuredHeight()+px(16)?y-box.getMeasuredHeight()-px(10):Math.min(screenHeight-box.getMeasuredHeight()-px(16),bottom+px(10));window.setAttributes(p);
        }else{WindowManager.LayoutParams p=window.getAttributes();p.gravity=Gravity.CENTER;p.x=0;p.y=0;window.setAttributes(p);}
        dialog.show();if(focus!=null)focus.requestFocus();
    }
    private Button option(String label,int icon,Runnable action){
        Button b=button(label,action);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(px(14),0,px(12),0);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(16));b.setFocusableInTouchMode(true);
        StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},surface(0xffeff0f4,0xffeff0f4,0,10));background.addState(new int[]{},surface(0,0,0,10));b.setBackgroundDrawable(background);
        b.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused},{}},new int[]{0xff777d88,0xff191d24,label.equals("卸载")?0xffe8aaa6:TEXT}));
        if(icon!=0){Drawable image=UiCompat.drawable(this,icon);image.setBounds(0,0,px(21),px(21));b.setCompoundDrawables(image,null,null,null);b.setCompoundDrawablePadding(px(13));b.setOnFocusChangeListener((v,focus)->{image.setColorFilter(focus?0xff191d24:label.equals("卸载")?0xffe8aaa6:TEXT,PorterDuff.Mode.SRC_IN);});}
        box.addView(b,new LinearLayout.LayoutParams(-1,px(43)));return b;
    }
    private void menu(){
        pickerMode="";content("menu","");boolean real=!pkg.startsWith("quiet:")&&!pkg.isEmpty();String protectedReason=protection();Button first;
        first=option("移动",R.drawable.ic_app_move,()->{setResult(RESULT_OK,new Intent().putExtra("action","move").putExtra("package",pkg).putExtra("dock",fromDock));finish();});
        if(fromDock){option("替换应用",R.drawable.ic_manage_refresh,()->picker("replace",true));option("添加应用",R.drawable.ic_manage_plus,()->picker("add",true));}
        option(fromDock?"移出首页":"添加到首页",fromDock?R.drawable.ic_manage_back:R.drawable.ic_manage_plus,()->{if(fromDock)unpin();else pin(pkg);});
        Button hide=option("隐藏",R.drawable.ic_child_hidden,this::hide);hide.setEnabled(real&&protectedReason.isEmpty()&&new ChildModeStore(this).eligible(pkg));
        Button uninstall=option("卸载",R.drawable.ic_manage_trash,this::authorizeUninstall);uninstall.setEnabled(real&&protectedReason.isEmpty());
        show(200,first,true);
    }
    private void changed(String focus){setResult(RESULT_OK,new Intent().putExtra("action","changed").putExtra("package",focus));finish();}
    private void failure(String message){content("notice","未完成");box.addView(muted(message,14));Button close=option("返回",R.drawable.ic_manage_back,this::handleBack);show(400,close,false);}
    private void hide(){if(!protection().isEmpty()||!new ChildModeStore(this).eligible(pkg)){failure("此应用必须保留");return;}if(new HomeVisibilityStore(this).hide(pkg)){Toast.makeText(this,"已隐藏，可在“应用管理 → 隐藏应用”中恢复",Toast.LENGTH_SHORT).show();changed("");}else failure("保存失败，请重试");}
    private void unpin(){ArrayList<String> values=new DockStore(this).load();values.remove(pkg);if(new DockStore(this).saveOrder(values))changed("");else failure("保存失败，请重试");}
    private void pin(String id){
        ArrayList<String> values=new DockStore(this).load();
        if(!available(id)){failure("应用状态已变化，请重新选择");return;}
        if(values.contains(id)){changed(id);return;}
        candidate=id;if(values.size()>=DockStore.CAPACITY){positions();return;}
        values.add(id);if(new DockStore(this).saveOrder(values))changed(id);else failure("保存失败，请重试");
    }
    private boolean available(String id){for(HomeAppCatalog.Entry entry:new HomeAppCatalog(this).load(new DockStore(this).load()))if(id.equals(entry.id))return true;return false;}
    private void picker(String choice,boolean fromMenu){
        // Keep the current surface until the complete picker is ready; no short loading window.
        pickerMode=choice;pickerFromMenu=fromMenu;int request=++generation;
        worker.execute(()->{
            ArrayList<HomeAppCatalog.Entry> choices=new ArrayList<>();HashMap<String,Bitmap> artwork=new HashMap<>();String error=null;
            try{ArrayList<String> pinned=new DockStore(this).load();for(HomeAppCatalog.Entry entry:new HomeAppCatalog(this).load(pinned))if(!pinned.contains(entry.id)){choices.add(entry);artwork.put(entry.id,entry.info==null?HomeEntryArtwork.render(this,entry.label,entry.id):AppArtwork.load(this,entry.info));}}
            catch(RuntimeException e){error="无法读取应用列表";}
            String problem=error;runOnUiThread(()->{if(isFinishing()||UiCompat.destroyed(this)||request!=generation)return;if(problem!=null){failure(problem);return;}pictures.putAll(artwork);pickerGrid(choices);});
        });
    }
    private void pickerGrid(List<HomeAppCatalog.Entry> choices){
        content("picker",pickerMode.equals("replace")?"替换应用":"添加应用");
        ScrollView scroll=new ScrollView(this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(px(340),View.MeasureSpec.AT_MOST));}};
        scroll.setVerticalScrollBarEnabled(false);scroll.setHorizontalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout grid=column();grid.setPadding(px(5),px(16),px(5),px(8));scroll.addView(grid);box.addView(scroll);View first=null;LinearLayout line=null;
        for(int i=0;i<choices.size();i++){
            HomeAppCatalog.Entry entry=choices.get(i);if(i%5==0){line=row();line.setGravity(Gravity.TOP);grid.addView(line);}
            LinearLayout card=column();card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);card.setPadding(px(5),px(5),px(5),px(7));StateListDrawable focusBorder=new StateListDrawable();focusBorder.addState(new int[]{android.R.attr.state_focused},surface(0,0,0xfff3f4f7,10));focusBorder.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));card.setBackgroundDrawable(focusBorder);card.setContentDescription("选择 "+entry.label);
            ImageView image=new ImageView(this);androidx.core.graphics.drawable.RoundedBitmapDrawable cover=androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(getResources(),pictures.get(entry.id));cover.setCornerRadius(px(11));cover.setAntiAlias(true);image.setImageDrawable(cover);image.setScaleType(ImageView.ScaleType.FIT_XY);LinearLayout.LayoutParams imageSize=new LinearLayout.LayoutParams(px(116),px(116*203f/360f));imageSize.gravity=Gravity.CENTER_HORIZONTAL;card.addView(image,imageSize);
            TextView title=center(entry.label,12);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(title,new LinearLayout.LayoutParams(-1,px(28)));
            card.setOnClickListener(v->KeyFeedback.clicked(v,()->select(entry.id)));line.addView(card,new LinearLayout.LayoutParams(0,-2,1));if(first==null||entry.id.equals(candidate))first=card;
            if(i==choices.size()-1)for(int n=i%5+1;n<5;n++)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        }
        if(choices.isEmpty())box.addView(center("暂无可添加的应用",16));Button cancel=option("取消",0,()->{if(pickerFromMenu)menu();else finish();});show(700,first==null?cancel:first,false);
    }
    private void select(String id){
        if(!available(id)||new DockStore(this).load().contains(id)){failure("应用状态已变化，请重新选择");return;}
        if(!pickerMode.equals("replace")){pin(id);return;}
        ArrayList<String> values=new DockStore(this).load();int index=values.indexOf(pkg);if(index<0){failure("原位置已变化，请重新选择");return;}
        values.set(index,id);if(new DockStore(this).saveOrder(values))changed(id);else failure("保存失败，请重试");
    }
    private void positions(){
        content("positions","选择替换位置");box.addView(muted("首页已满，被替换的应用将回到第二屏",14));Button first=null;
        for(String target:new DockStore(this).load()){if(new ChildModeStore(this).hidden(target))continue;Button choice=option(name(target),0,()->{
            ArrayList<String> values=new DockStore(this).load();int index=values.indexOf(target);
            if(index<0||!available(candidate)||values.contains(candidate)){failure("应用状态已变化，请重新选择");return;}
            values.set(index,candidate);if(new DockStore(this).saveOrder(values))changed(candidate);else failure("保存失败，请重试");
        });if(first==null)first=choice;}
        if(first==null)box.addView(muted("没有可替换的位置，请先在家长管理中调整",14));
        Button cancel=option("取消",0,this::handleBack);show(470,first==null?cancel:first,false);
    }
    private void authorizeUninstall(){
        if(!protection().isEmpty()){failure(protection());return;}
        if(new ChildModeStore(this).configured()){dialog.dismiss();ChildModeAuth.show(this,this::confirmUninstall,this::finish);}else confirmUninstall();
    }
    private void confirmUninstall(){
        AppRepository.App app=metadata(pkg);if(app==null||!app.installed||!protection().isEmpty()){failure("应用状态已变化，不能卸载");return;}
        content("uninstall","卸载 "+name(pkg)+"？");box.addView(muted("卸载后将删除此应用的数据",14));Button cancel=option("取消",0,this::menu);
        option(app.system?"在应用管理中继续":"卸载",R.drawable.ic_manage_trash,()->{
            AppRepository.App latest=metadata(pkg);
            if(latest==null||!latest.installed||latest.system!=app.system||!protection().isEmpty()||new ChildModeStore(this).hidden(pkg)){failure("应用状态已变化，请重新确认");return;}
            if(app.system){startActivity(new Intent(this,AppManagementActivity.class).putExtra("selected-package",pkg));finish();return;}
            Intent intent=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)).putExtra(Intent.EXTRA_RETURN_RESULT,true);
            waitingForDelete=true;
            if(ExternalActivityLauncher.dispatch("entry=app.uninstall "+DiagnosticLog.intent(intent),()->startActivityForResult(intent,DELETE)))dialog.dismiss();
            else{waitingForDelete=false;failure("系统卸载入口不可用，应用已保留");}
        });show(470,cancel,false);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==DELETE){waitingForDelete=false;AppRepository.App current=metadata(pkg);if(current==null||!current.installed){new DockStore(this).remove(pkg);new HomeOrderStore(this).remove(pkg);new HomeVisibilityStore(this).remove(pkg);}changed("");}}
    @Override public void handleBack(){
        if(mode.equals("picker")){if(pickerFromMenu)menu();else finish();}
        else if((mode.equals("positions")||mode.equals("notice"))&&!pickerMode.isEmpty())picker(pickerMode,pickerFromMenu);
        else if(!mode.equals("menu"))menu();else finish();
        KeyFeedback.back(this);
    }
    @Override protected void onStop(){super.onStop();if(!waitingForDelete&&!isChangingConfigurations())finish();}
    @Override protected void onDestroy(){++generation;ChildModeAuth.dismiss(this);if(dialog!=null)dialog.dismiss();worker.shutdownNow();super.onDestroy();}
}
