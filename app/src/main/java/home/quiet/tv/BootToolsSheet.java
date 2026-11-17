package home.quiet.tv;

import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** The quick tool edits the existing single startup destination without launching it. */
final class BootToolsSheet {
    private static final class Target {
        final String id,label;
        final boolean hdmi,available;
        Target(String id,String label,boolean hdmi,boolean available){this.id=id;this.label=label;this.hdmi=hdmi;this.available=available;}
    }
    private final ManagementUi ui;
    private final Runnable closed;
    private final BootAppStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Target> targets=new ArrayList<>();
    private ManagementUi.Sheet sheet;
    private View origin;
    private Button pin;
    private boolean destroyed,loading;
    private int generation;
    private String focus="boot-home",pending,expected="",error="";
    private String phoneKey="";
    private final Runnable phoneChanged=this::phoneChanged;
    private void phoneChanged(){if(!open()||pending!=null)return;QuickControlStore.Snapshot quick=new QuickControlStore(new File(ui.getFilesDir(),"quick-controls")).cached();String key=current()+":"+store.label()+":"+store.watchTarget()+":"+store.tested()+":"+(quick==null?-1:quick.revision);if(key.equals(phoneKey))return;phoneKey=key;load();readPin();}

    BootToolsSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;store=new BootAppStore(ui);}
    private boolean open(){return !destroyed&&sheet!=null;}
    private String current(){return store.hdmiBoot()?"hdmi:"+store.watchTarget():store.target();}
    private String currentLabel(){return store.enabled()?store.label():"Quiet TV 首页";}
    private String tag(Target target){return target.id.isEmpty()?"boot-home":"boot-target-"+target.id;}
    private TextView text(String value,float size,float line,int color){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,size,line,ui.unit,color);return view;}
    private void identify(View view,String key){view.setTag(key);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on)focus=key;});}
    private Button button(String title,String key,Runnable action,boolean primary){
        Button view=ui.button(title,action);identify(view,key);view.setAllCaps(false);view.setMinHeight(0);view.setMinimumHeight(0);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(ui.px(12.96f),ui.px(7.2f),ui.px(12.96f),ui.px(7.2f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?(ui instanceof AppManagementActivity?UiPalette.SELECTED:0xe6ffffff):UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE,false,12));view.setTextColor(SettingsFocusDrawable.textColors(primary&&!(ui instanceof AppManagementActivity)?UiPalette.INK:UiPalette.TEXT));SettingsTouchClick.attach(view);return view;
    }
    void show(){if(open())return;focus="boot-home";pending=null;expected=error="";create();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet("开机直达");ManagementUi.Sheet own=sheet;Button close=sheet.settingsLayout("关闭",this::close);identify(close,"sheet-close");sheet.settingsActions(false);
        pin=button("固定到快捷控制","boot-pin",this::pin,false);pin.setEnabled(false);pin.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,9.6f*ui.unit);pin.setPadding(ui.px(6.72f),0,ui.px(6.72f),0);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,ui.px(24));pp.rightMargin=ui.px(9.6f);((LinearLayout)close.getParent()).addView(pin,1,pp);readPin();
        sheet.dialog.setOnKeyListener((dialog,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP)close();return true;});
        sheet.dialog.setOnDismissListener(dialog->{if(sheet!=own)return;sheet=null;++generation;if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();closed.run();});
        String target=focus;render();sheet.show(null);focus=target;restoreFocus();load();
    }
    private void readPin(){worker.execute(()->{QuickControlStore.Snapshot value=null;try{value=new QuickControlStore(new File(ui.getFilesDir(),"quick-controls")).read();}catch(IOException ignored){}QuickControlStore.Snapshot ready=value;main.post(()->{if(open()){boolean added=ready!=null&&ready.ids.contains("boot");pin.setText(added?"已固定":"固定到快捷控制");pin.setEnabled(ready!=null&&!added);}});});}
    private void pin(){pin.setEnabled(false);worker.execute(()->{String failure=null;try{QuickControlStore quick=new QuickControlStore(new File(ui.getFilesDir(),"quick-controls"));QuickControlStore.Snapshot before=quick.read();List<String> next=new ArrayList<>(before.ids);if(!next.contains("boot"))next.add("boot");quick.save(before.revision,next);}catch(IOException changed){failure=changed instanceof QuickControlStore.Changed?"快捷功能已变化，请重新打开后再固定":"固定失败，原快捷配置已保留";}String message=failure;main.post(()->{if(open()){error=message==null?"":message;render();readPin();}});});}
    private void load(){
        if(!open())return;int version=++generation;loading=true;render();String saved=store.watchTarget();boolean tested=store.tested();
        worker.execute(()->{
            List<Target> result=new ArrayList<>();String failure="";result.add(new Target("","Quiet TV 首页",false,true));
            try{
                for(HomeAppCatalog.Entry entry:new HomeAppCatalog(ui).load(Collections.emptyList()))if(entry.info!=null&&(entry.info.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0&&store.launchIntent(entry.id)!=null)result.add(new Target(entry.id,entry.label,false,true));
                List<HdmiTarget> inputs=HdmiTarget.available(ui);HdmiTarget paired=HdmiTarget.decode(saved);boolean pairedFound=false;
                for(HdmiTarget input:inputs){boolean matches=paired!=null&&paired.matches(input);pairedFound|=matches;result.add(new Target("hdmi:"+(matches?saved:input.encode()),matches?store.watchLabel():input.label,true,matches&&tested&&paired.prepare(ui)!=null));}
                if(paired!=null&&!pairedFound)result.add(new Target("hdmi:"+saved,paired.label,true,tested&&paired.prepare(ui)!=null));
                if(inputs.isEmpty()&&paired==null)for(int i=1;i<=3;i++)result.add(new Target("unavailable-hdmi-"+i,"HDMI "+i,true,false));
            }catch(Exception unavailable){failure="部分开机目标读取失败，请重新打开后重试";}
            String message=failure;main.post(()->{if(!open()||version!=generation)return;loading=false;targets.clear();targets.addAll(result);if(pending!=null&&find(pending)==null){pending=null;expected="";error="开机目标已不可用，请重新选择";}if(!message.isEmpty())error=message;render();});
        });
    }
    private void note(LinearLayout parent,String value,float size,float line,float top,float bottom){TextView copy=text(value,size,line,UiPalette.SECONDARY);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.px(top);lp.bottomMargin=ui.px(bottom);parent.addView(copy,lp);}
    private void restoreFocus(){if(!open())return;String key=focus;sheet.body.post(()->{if(!open())return;View view=sheet.box.findViewWithTag(key);if(view==null||!view.isEnabled()||!view.isShown())view=loading?null:sheet.box.findViewWithTag("boot-home");if(view!=null){view.requestFocusFromTouch();android.graphics.Rect rect=new android.graphics.Rect();view.getDrawingRect(rect);view.requestRectangleOnScreen(rect,false);}});}
    private Target find(String id){for(Target value:targets)if(value.id.equals(id))return value;return null;}
    private void render(){
        if(!open())return;String target=focus;sheet.body.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(false);
        Target chosen=pending==null?null:find(pending);
        if(chosen!=null)confirmation(chosen);
        else{
            note(sheet.body,"当前："+currentLabel()+"。开机或待机唤醒进入 Quiet TV 后打开自启应用，按 HOME 返回不重复启动。应用与看电视共用一个开机目标。",14.464f,23.8656f,7.68f,14.4f);
            if(loading)note(sheet.body,"正在读取开机目标…",14.464f,23.8656f,0,0);
            else{boolean hdmi=false;for(Target value:targets){Button choice=button((current().equals(value.id)?"已选择  ":"")+value.label,tag(value),()->choose(value),false);choice.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);choice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.4f*ui.unit);choice.setMinHeight(ui.px(39.36f));choice.setMinimumHeight(ui.px(39.36f));choice.setEnabled(value.available);choice.setAlpha(1f);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=ui.px(7.2f);sheet.body.addView(choice,lp);hdmi|=value.hdmi&&value.available;}if(!hdmi)note(sheet.body,"此机型的 HDMI 开机直达尚未验证，暂不可选。",14.464f,23.8656f,7.68f,14.4f);}
        }
        if(!error.isEmpty()){TextView problem=text(error,15.36f,23.04f,UiPalette.TEXT);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.px(12.8f);sheet.body.addView(problem,lp);}
        focus=target;restoreFocus();
    }
    private void confirmation(Target chosen){
        LinearLayout card=ui.column();card.setPadding(ui.px(14.4f),ui.px(14.4f),ui.px(14.4f),ui.px(14.4f));card.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,11.52f));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=cp.bottomMargin=ui.px(13.44f);sheet.body.addView(card,cp);
        TextView heading=text(chosen.hdmi?"开启开机自动看电视？":"替换开机直达？",21.12f,31.68f,UiPalette.TEXT);QuickControlPreviewView.weight(heading,500);card.addView(heading);
        note(card,chosen.hdmi?"下次完整开机进入 Quiet TV 后自动进入 "+chosen.label+"。待机唤醒不主动切源。"+(store.enabled()?"将替换当前开机目标："+currentLabel():""):"当前为 "+currentLabel()+"，改为 "+chosen.label+" 后，只保留新的开机目标。",16,27.2f,7.68f,0);
        LinearLayout actions=ui.row();actions.setGravity(Gravity.END);Button cancel=button(chosen.hdmi?"取消":"取消替换","boot-cancel",()->{++generation;loading=false;pending=null;expected=error="";focus=tag(chosen);render();},false);actions.addView(cancel,new LinearLayout.LayoutParams(-2,-1));Button confirm=button(chosen.hdmi?"开启":"确认替换","boot-confirm",()->commit(chosen),true);confirm.setEnabled(!loading);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-1);bp.leftMargin=ui.px(7.68f);actions.addView(confirm,bp);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,ui.px(34.56f));ap.topMargin=ui.px(18);card.addView(actions,ap);
    }
    private void choose(Target value){
        if(loading||!value.available)return;error="";if(current().equals(value.id)){ui.feedback("当前已使用此开机目标");return;}
        expected=current();if(value.hdmi||store.enabled()){pending=value.id;focus="boot-cancel";render();}else commit(value);
    }
    private void commit(Target value){
        if(loading)return;
        if(value.hdmi){loading=true;render();int version=++generation;worker.execute(()->{boolean valid=false;try{HdmiTarget target=HdmiTarget.decode(value.id.substring(5));valid=target!=null&&target.prepare(ui)!=null;}catch(Exception unavailable){}boolean ready=valid;main.post(()->{if(!open()||version!=generation)return;loading=false;if(!ready){error="信号源或固件已变化，请重新配对";render();return;}save(value);});});}else save(value);
    }
    private void save(Target value){
        try{synchronized(BootAppStore.class){if(!expected.equals(current()))throw new IOException("开机目标已变化，请重新选择");if(value.hdmi){if(!store.tested()||!value.id.equals("hdmi:"+store.watchTarget()))throw new IOException("请先测试当前 HDMI 并确认画面和声音");store.hdmiBoot(true);}else{if(!value.id.isEmpty()&&!availableApp(value.id))throw new IOException("此应用暂不可用，请重新选择");store.select(value.id,value.id.isEmpty()?"":value.label);}}
            pending=null;expected=error="";focus=tag(value);render();ui.success(value.id.isEmpty()?"已恢复开机进入首页":"开机直达已设为 "+value.label);
        }catch(IOException|RuntimeException failure){error=failure.getMessage()==null?"开机目标保存失败，请重试":failure.getMessage();render();ui.error("开机目标未保存");}
    }
    private boolean availableApp(String id){for(HomeAppCatalog.Entry value:new HomeAppCatalog(ui).load(Collections.emptyList()))if(id.equals(value.id)&&value.info!=null&&(value.info.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return store.launchIntent(id)!=null;return false;}
    void resume(){PhoneSettingsSession.get(ui).observeConfiguration(phoneChanged);if(open())load();}
    void pause(){PhoneSettingsSession.get(ui).forgetConfiguration(phoneChanged);++generation;loading=false;}
    void save(Bundle state){if(!open())return;Bundle value=new Bundle();value.putString("focus",focus);value.putString("pending",pending);value.putString("expected",expected);value.putString("error",error);state.putBundle("boot-tools",value);}
    void restore(Bundle state){Bundle value=state==null?null:state.getBundle("boot-tools");if(value==null)return;focus=value.getString("focus","boot-home");pending=value.getString("pending");expected=value.getString("expected","");error=value.getString("error","");create();}
    void close(){if(sheet!=null)sheet.dialog.dismiss();}
    void destroy(){PhoneSettingsSession.get(ui).forgetConfiguration(phoneChanged);destroyed=true;++generation;if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}main.removeCallbacksAndMessages(null);worker.shutdown();}
}
