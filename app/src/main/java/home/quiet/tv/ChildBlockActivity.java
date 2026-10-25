package home.quiet.tv;

import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

/** Private launch gate and expiry screen. Returning cannot uncover the restricted application. */
public final class ChildBlockActivity extends ManagementUi {
    @Override boolean useSettingListStyle(){return false;}
    private String pkg;
    private ChildModeStore store;
    private boolean launched;
    private int theme;
    private ChildRestView rest;
    private android.app.Dialog grantDialog;
    static Intent intent(Context context,String pkg){return new Intent(context,ChildBlockActivity.class).putExtra("package",pkg);}
    static boolean gate(Context context,String pkg){
        ChildModeStore store=new ChildModeStore(context);ChildModePolicy.Rule r=store.rule(pkg);
        if(r.hidden)return true;
        if(r.locked || r.limited() && (r.remaining()<=0 || !ChildUsageService.ready(context))){context.startActivity(intent(context,pkg));return true;}
        if(r.limited())ChildUsageService.watch(pkg);else ChildUsageService.cancel();return false;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);theme=state==null?new java.util.Random().nextInt(3):state.getInt("rest-theme",0);store=new ChildModeStore(this);pkg=getIntent().getStringExtra("package");if(pkg==null || !store.eligible(pkg)){finish();return;}render();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String next=intent.getStringExtra("package");if(!java.util.Objects.equals(pkg,next)){clearRest();theme=new java.util.Random().nextInt(3);}pkg=next;launched=false;if(pkg==null || !store.eligible(pkg)){finish();return;}render();}
    @Override protected void onResume(){super.onResume();if(pkg!=null && !isFinishing()){ChildUsageService.blockVisible(pkg);if(!launched)render();}}
    @Override boolean loadWallpaper(){return false;}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("rest-theme",theme);}
    @Override protected void onDestroy(){clearRest();super.onDestroy();}
    private void clearRest(){if(rest!=null){rest.release();stage.removeView(rest);rest=null;}}
    @Override protected void onStop(){ChildModeAuth.dismiss(this);if(grantDialog!=null)grantDialog.dismiss();clearRest();super.onStop();}
    private void render(){
        if(pkg==null)return;ChildModePolicy.Rule r=store.rule(pkg);
        boolean needsService=!r.locked && r.limited() && !ChildUsageService.ready(this);
        if(!r.hidden && !r.locked && r.limited() && r.remaining()<=0 && !needsService){
            root.setVisibility(android.view.View.GONE);
            if(rest==null){rest=new ChildRestView(this,theme,r.mode==ChildModePolicy.DAILY,this::handleBack,()->ChildModeAuth.show(this,this::grant));stage.addView(rest,new android.widget.FrameLayout.LayoutParams(-1,-1));}
            else rest.daily(r.mode==ChildModePolicy.DAILY);
            return;
        }
        clearRest();root.setVisibility(android.view.View.VISIBLE);
        root.removeAllViews();LinearLayout box=column();box.setGravity(Gravity.CENTER);root.addView(box,new LinearLayout.LayoutParams(-1,-1));
        String name=pkg;try{name=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        box.addView(center(name,18));gap(box,24);
        box.addView(center(r.hidden?"此应用已隐藏":r.locked?"此应用已上锁":needsService?"需要开启计时服务":r.remaining()<=0?r.mode==ChildModePolicy.DAILY?"今天的使用时间已用完":"本次的使用时间已用完":"可以继续使用",28));gap(box,18);
        if(needsService){TextView note=center(store.fault().isEmpty()?"开启儿童模式无障碍服务后，才能计时和显示到时限制":store.fault(),12);note.setTextColor(MUTED);box.addView(note);gap(box,22);}
        LinearLayout actions=row();actions.setGravity(Gravity.CENTER);box.addView(actions,new LinearLayout.LayoutParams(-1,px(42)));marginAdd(actions,button("返回桌面",this::handleBack),-2,42,0);
        if(r.hidden)return;
        if(needsService)marginAdd(actions,button("开启计时服务",()->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(android.content.ActivityNotFoundException e){message("当前电视没有可用的无障碍设置入口");}}),-2,42,12);
        else if(r.locked || r.remaining()<=0)marginAdd(actions,button("家长授权",()->ChildModeAuth.show(this,()->{if(store.rule(pkg).locked)launch();else grant();})),-2,42,12);
        else marginAdd(actions,button("继续使用",this::launch),-2,42,12);
    }
    private void grant(){
        ChildModeUi visual=new ChildModeUi(this);ChildModeUi.Sheet sheet=visual.new Sheet("追加使用时间","",R.drawable.ic_child_time,633.6f);
        TextView error=visual.text("",16.32f,28,0xffe3aba1);error.setGravity(Gravity.CENTER);error.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout presets=row();visual.gap(sheet.body,19.2f);sheet.body.addView(presets,new LinearLayout.LayoutParams(-1,visual.p(48)));
        for(int minutes:new int[]{15,30}){Button b=visual.button(minutes+" 分钟",()->add(minutes,sheet,error),false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(presets.getChildCount()>0)lp.leftMargin=visual.p(12);presets.addView(b,lp);}
        visual.gap(sheet.body,19.2f);EditText custom=visual.number("","自定义分钟",4);sheet.body.addView(custom,new LinearLayout.LayoutParams(-1,visual.p(64)));
        visual.gap(sheet.body,19.2f);LinearLayout pad=visual.numberPad(sheet,custom);sheet.body.addView(pad);visual.gap(sheet.body,12);sheet.body.addView(error);
        sheet.action("取消",sheet.dialog::dismiss,false);sheet.action("追加",()->{try{add(Integer.parseInt(custom.getText().toString()),sheet,error);}catch(NumberFormatException e){error.setText("请输入 1–1440 分钟");}},true);
        visual.numberActions(sheet,pad);grantDialog=sheet.dialog;sheet.dialog.setOnDismissListener(d->{sheet.release();if(grantDialog==sheet.dialog)grantDialog=null;});sheet.show(custom);
    }
    private void add(int minutes,ChildModeUi.Sheet sheet,TextView error){try{if(!store.grant(pkg,minutes*ChildModePolicy.MINUTE)){error.setText("保存失败，请重试");return;}sheet.dialog.dismiss();launch();}catch(IllegalArgumentException e){error.setText(e.getMessage());}}
    private void launch(){
        ChildModePolicy.Rule r=store.rule(pkg);if(r.hidden)return;
        ResolveInfo target=new HomeAppCatalog(this).launchable().get(pkg);if(target==null){message("应用暂不可用");return;}
        if(!r.locked && r.limited()){if(!ChildUsageService.ready(this) || r.remaining()<=0){render();return;}ChildUsageService.watch(pkg);}else ChildUsageService.cancel();
        try{launched=true;startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg,target.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));finish();}
        catch(android.content.ActivityNotFoundException|SecurityException e){launched=false;message("应用暂不可用");}
    }
    @Override public void handleBack(){ChildUsageService.cancel();startActivity(new Intent(this,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
}
