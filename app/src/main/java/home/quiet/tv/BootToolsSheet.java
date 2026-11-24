package home.quiet.tv;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** The quick tool edits the existing single startup destination without launching it. */
final class BootToolsSheet {
    private static final int COLUMNS=4;
    private static final class Target {
        final String id,label;
        final boolean hdmi,available;
        final Drawable icon;
        Target(String id,String label,boolean hdmi,boolean available){this(id,label,hdmi,available,null);}
        Target(String id,String label,boolean hdmi,boolean available,Drawable icon){this.id=id;this.label=label;this.hdmi=hdmi;this.available=available;this.icon=icon;}
    }
    private final ManagementUi ui;
    private final Runnable closed;
    private final BootAppStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<Target> targets=new ArrayList<>();
    private ManagementUi.Sheet sheet;
    private View origin;
    private Button close;
    private boolean destroyed,loading;
    private int generation;
    private String focus="boot-home",pending,expected="",error="";
    private String phoneKey="";
    private final Runnable phoneChanged=this::phoneChanged;
    private void phoneChanged(){if(!open()||pending!=null)return;String key=current()+":"+store.label()+":"+store.watchTarget()+":"+store.tested();if(key.equals(phoneKey))return;phoneKey=key;load();}

    BootToolsSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;store=new BootAppStore(ui);}
    private boolean open(){return !destroyed&&sheet!=null;}
    private String current(){return store.hdmiBoot()?"hdmi:"+store.watchTarget():store.target();}
    private String currentLabel(){return store.enabled()?store.label():"Quiet TV 首页";}
    private String tag(Target target){return target.id.isEmpty()?"boot-home":"boot-target-"+target.id;}
    private TextView text(String value,float size,float line,int color){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,size,line,ui.unit,color);return view;}
    private void identify(View view,String key){view.setTag(key);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on&&(!loading||!"sheet-close".equals(key)))focus=key;});}
    private Button button(String title,String key,Runnable action,boolean primary){
        Button view=ui.button(title,action);identify(view,key);view.setAllCaps(false);view.setMinHeight(0);view.setMinimumHeight(0);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(ui.px(12.96f),ui.px(7.2f),ui.px(12.96f),ui.px(7.2f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?(ui instanceof AppManagementActivity?UiPalette.SELECTED:0xe6ffffff):UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE).outlineOnFocus());view.setTextColor(SettingsFocusDrawable.textColors(primary&&!(ui instanceof AppManagementActivity)?UiPalette.INK:UiPalette.TEXT));SettingsTouchClick.attach(view);return view;
    }
    void show(){if(open())return;String saved=current();focus=saved.isEmpty()?"boot-home":"boot-target-"+saved;pending=null;expected=error="";create();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet("开机直达");ManagementUi.Sheet own=sheet;close=sheet.settingsLayout("关闭",this::close);identify(close,"sheet-close");sheet.settingsActions(false);sheet.contentSize(448,380);
        sheet.dialog.setOnKeyListener((dialog,key,event)->KeyFeedback.backKey(ui,event,sheet!=null,this::close));
        sheet.dialog.setOnDismissListener(dialog->{if(sheet!=own)return;sheet=null;++generation;if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();closed.run();});
        String target=focus;render();sheet.show(null);focus=target;restoreFocus();load();
    }
    private void load(){
        if(!open())return;int version=++generation;loading=true;render();String saved=store.watchTarget();boolean tested=store.tested();
        worker.execute(()->{
            List<Target> result=new ArrayList<>();String failure="";result.add(new Target("","Quiet TV 首页",false,true));
            try{
                for(HomeAppCatalog.Entry entry:new HomeAppCatalog(ui).load(Collections.emptyList()))if(entry.info!=null&&(entry.info.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0&&store.launchIntent(entry.id)!=null)result.add(new Target(entry.id,entry.label,false,true,entry.info.loadIcon(ui.getPackageManager())));
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
        else if(loading)note(sheet.body,"正在读取开机目标…",14.464f,23.8656f,0,0);
        else grid();
        if(!error.isEmpty()){TextView problem=text(error,15.36f,23.04f,UiPalette.TEXT);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.px(12.8f);sheet.body.addView(problem,lp);}
        focus=target;restoreFocus();
    }
    private void grid(){
        String saved=current();
        List<View> choices=new ArrayList<>();
        LinearLayout row=null;
        for(int i=0;i<targets.size();i++){
            if(i%COLUMNS==0){
                row=ui.row();
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
                rp.bottomMargin=ui.px(10);sheet.body.addView(row,rp);
            }
            Target target=targets.get(i);
            boolean selected=saved.equals(target.id);
            LinearLayout cell=ui.column();cell.setGravity(Gravity.CENTER_HORIZONTAL);
            // Keep the caption outside the focus plate. The cell is the only keyboard/accessibility item.
            identify(cell,tag(target));cell.setEnabled(target.available);cell.setFocusable(target.available);
            cell.setClickable(target.available);
            if(target.available)cell.setOnClickListener(v->choose(target));
            cell.setContentDescription(selected?"已选择  "+target.label:target.label);
            FrameLayout plate=new FrameLayout(ui);plate.setDuplicateParentStateEnabled(true);
            plate.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));
            cell.addView(plate,new LinearLayout.LayoutParams(-1,ui.px(59)));
            ImageView icon=new ImageView(ui);icon.setDuplicateParentStateEnabled(true);
            if(target.icon!=null)icon.setImageDrawable(target.icon);
            else icon.setImageDrawable(new QuickControlIcon(target.hdmi?"plugs":target.id.isEmpty()?"left-desktop":"app-window"));
            plate.addView(icon,new FrameLayout.LayoutParams(ui.px(31),ui.px(31),Gravity.CENTER));
            if(selected){
                ImageView check=new ImageView(ui);check.setDuplicateParentStateEnabled(true);
                QuickControlIcon markIcon=new QuickControlIcon("circle-check");
                markIcon.setTintList(SettingsFocusDrawable.textColors(UiPalette.STATUS_SUCCESS,0xff146c2e));
                check.setImageDrawable(markIcon);
                FrameLayout.LayoutParams mark=new FrameLayout.LayoutParams(ui.px(13),ui.px(13),Gravity.TOP|Gravity.RIGHT);
                mark.topMargin=mark.rightMargin=ui.px(5);plate.addView(check,mark);
            }
            TextView name=text(target.label,11.52f,17.28f,target.available?UiPalette.TEXT:UiPalette.FAINT);
            name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams caption=new LinearLayout.LayoutParams(-1,ui.px(19));
            caption.topMargin=ui.px(3);cell.addView(name,caption);
            androidx.core.view.ViewCompat.setImportantForAccessibility(name,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);
            if(i%COLUMNS!=0)cp.leftMargin=ui.px(8);
            row.addView(cell,cp);choices.add(cell);
        }
        if(row!=null)for(int i=choices.size()%COLUMNS;i>0&&i<COLUMNS;i++){
            LinearLayout.LayoutParams empty=new LinearLayout.LayoutParams(0,1,1);
            empty.leftMargin=ui.px(8);row.addView(new View(ui),empty);
        }
        for(int i=0;i<choices.size();i++){
            View card=choices.get(i);
            if(!card.isEnabled())continue;
            int start=i/COLUMNS*COLUMNS,end=Math.min(start+COLUMNS,choices.size());
            int left=i-1,right=i+1;
            while(left>=start&&!choices.get(left).isEnabled())left--;
            while(right<end&&!choices.get(right).isEnabled())right++;
            card.setNextFocusLeftId(left>=start?choices.get(left).getId():card.getId());
            card.setNextFocusRightId(right<end?choices.get(right).getId():card.getId());
            card.setNextFocusUpId(gridNeighbor(choices,i,-COLUMNS,close.getId()));
            card.setNextFocusDownId(gridNeighbor(choices,i,COLUMNS,card.getId()));
        }
    }
    private int gridNeighbor(List<View> choices,int index,int step,int fallback){
        int column=index%COLUMNS;
        for(int start=index-column+step;start>=0&&start<choices.size();start+=step){
            View nearest=null;int distance=COLUMNS;
            for(int c=0;c<COLUMNS&&start+c<choices.size();c++){
                View candidate=choices.get(start+c);
                if(candidate.isEnabled()&&Math.abs(c-column)<distance){nearest=candidate;distance=Math.abs(c-column);}
            }
            if(nearest!=null)return nearest.getId();
        }
        return fallback;
    }
    private void confirmation(Target chosen){
        LinearLayout card=ui.column();card.setClipToPadding(false);card.setPadding(ui.px(14.4f),ui.px(14.4f),ui.px(14.4f),ui.px(14.4f));card.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,11.52f));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=cp.bottomMargin=ui.px(13.44f);sheet.body.addView(card,cp);
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
