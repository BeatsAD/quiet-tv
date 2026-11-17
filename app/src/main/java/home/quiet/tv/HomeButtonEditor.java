package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** One sheet switches between the three slots and the function catalog. */
final class HomeButtonEditor {
    private final ManagementUi ui;private final HomeButtonStore store;private final Runnable changed,closed;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private ManagementUi.Sheet sheet;private Button close;private HomeButtonStore.Snapshot saved;private String selected="",replace="",error="",target="",notice="";private boolean catalog,busy,destroyed,closeAfter,rendering;
    private ArrayList<String> pending,moving;private long expected;private Bundle restored;private String restoredFocusTag="";private int restoredScrollY,generation;
    private TextView count;private LinearLayout contextActions;
    private final ArrayList<View> positions=new ArrayList<>(),operations=new ArrayList<>();
    private final Map<String,View> controls=new LinkedHashMap<>();
    HomeButtonEditor(ManagementUi ui,Runnable changed,Runnable closed){this.ui=ui;this.changed=changed;this.closed=closed;store=new HomeButtonStore(ui);}
    private int p(float n){return ui.px(n*.75f);}
    private String title(String id){return HomeButtonStore.title(id,ui.chinese());}
    void show(String initial){
        if(sheet!=null)return;++generation;busy=false;catalog=false;replace="";error="";pending=null;moving=null;saved=null;selected=initial==null?"":initial;sheet=ui.new Sheet("首页按钮");close=sheet.settingsLayout("关闭",this::back);sheet.settingsActions(false);
        LinearLayout header=(LinearLayout)sheet.heading.getParent();LinearLayout.LayoutParams heading=(LinearLayout.LayoutParams)sheet.heading.getLayoutParams();heading.width=-2;heading.weight=0;sheet.heading.setLayoutParams(heading);
        count=ui.label("",12);count.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.52f*ui.unit);count.setTextColor(UiPalette.SECONDARY);LinearLayout.LayoutParams counter=new LinearLayout.LayoutParams(0,-1,1);counter.leftMargin=ui.px(10);header.addView(count,1,counter);
        sheet.fixedSize(440,212);sheet.dialog.setOnCancelListener(d->back());sheet.dialog.setOnKeyListener((d,key,event)->key(key,event));BackNavigation.install(sheet.dialog,this::back);
        sheet.dialog.setOnDismissListener(d->{++generation;busy=false;sheet=null;closed.run();});sheet.show(null);load();
    }
    private void load(){if(busy)return;busy=true;int request=generation;render();worker.execute(()->{HomeButtonStore.Snapshot read=null;String failure="";try{read=store.read();}catch(IOException e){failure=e.getMessage();}HomeButtonStore.Snapshot ready=read;String message=failure;main.post(()->{
        if(destroyed||sheet==null||request!=generation)return;busy=false;if(ready==null){error=message;render();return;}saved=ready;error="";
        if(restored!=null){Bundle state=restored;restored=null;selected=state.getString("home-button-selected","");catalog=state.getBoolean("home-button-catalog");replace=state.getString("home-button-replace","");pending=state.getStringArrayList("home-button-pending");expected=state.getLong("home-button-expected");target=state.getString("home-button-target","");notice=state.getString("home-button-notice","");closeAfter=state.getBoolean("home-button-close");moving=state.getStringArrayList("home-button-moving");
            if(moving!=null&&(state.getLong("home-button-move-revision",-1)!=saved.revision||moving.size()<2||moving.size()!=saved.ids.size()||!new HashSet<>(moving).equals(new HashSet<>(saved.ids))))moving=null;
            if(pending!=null&&pending.equals(saved.ids)){pending=null;moving=null;catalog=false;replace="";changed.run();if(closeAfter){sheet.dialog.dismiss();return;}}
            else if(pending!=null)error=expected==saved.revision?"修改尚未保存，请重试或重新读取":"首页按钮已变化，请重新读取";
        }
        if(!saved.ids.contains(selected))selected=saved.ids.isEmpty()?"":saved.ids.get(0);render();String restoredFocus=restoredFocusTag;int restoredScroll=restoredScrollY;restoredFocusTag="";restoredScrollY=0;focus(restoredFocus.isEmpty()?catalog?firstCatalog():"slot-"+selected:restoredFocus);if(restoredScroll>0)sheet.scroll.post(()->{if(sheet!=null)sheet.scroll.scrollTo(0,restoredScroll);});
    });});}
    private TextView text(String value,float size,float height,int color){TextView t=ui.label(value,12);QuickControlPreviewView.type(t,size,height,ui.unit,color);return t;}
    private Button button(String label,String tag,Runnable action,boolean primary){Button b=ui.button(label,action);b.setTag(tag);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setFocusableInTouchMode(true);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);b.setPadding(p(18.28f),0,p(18.28f),0);b.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,UiPalette.EDGE));b.setEnabled(!busy&&pending==null);controls.put(tag,b);return b;}
    private void render(){
        if(sheet==null)return;rendering=true;try{
            sheet.body.removeAllViews();positions.clear();operations.clear();contextActions=null;controls.clear();controls.put("sheet-close",close);
            sheet.heading.setText(catalog?"选择首页按钮":moving!=null?"移动首页按钮":"首页按钮");count.setText(saved==null?"":saved.ids.size()+" / 3");count.setVisibility(catalog?View.INVISIBLE:View.VISIBLE);close.setText(moving!=null?"取消":"关闭");sheet.box.setEnabled(!busy);
            sheet.fixedSize(440,catalog?420:!error.isEmpty()?270:212);
            if(!error.isEmpty()){
                TextView message=text(error,15.36f,26.112f,UiPalette.TEXT);sheet.body.addView(message);LinearLayout actions=ui.row();
                if(pending!=null&&saved!=null&&saved.revision==expected){Button retry=button("重试保存","retry",this::persist,false);retry.setEnabled(!busy);actions.addView(retry,new LinearLayout.LayoutParams(0,p(46.08f),1));}
                Button reload=button("重新读取","reload",()->{pending=null;moving=null;restored=null;load();},false);reload.setEnabled(!busy);actions.addView(reload,new LinearLayout.LayoutParams(0,p(46.08f),1));sheet.body.addView(actions);
            }
            if(saved==null){if(error.isEmpty())sheet.body.addView(text("正在读取首页按钮…",15.36f,23.04f,UiPalette.SECONDARY));return;}
            if(catalog){renderCatalog();return;}
            List<String> ids=moving==null?saved.ids:moving;
            LinearLayout slots=ui.row();slots.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.TOP);slots.setClipChildren(false);slots.setClipToPadding(false);slots.setPadding(0,p(8),0,p(8));sheet.body.addView(slots,new LinearLayout.LayoutParams(-1,p(132)));
            for(int index=0;index<3;index++)slot(slots,index<ids.size()?ids.get(index):"",index);
            contextActions=ui.row();contextActions.setGravity(Gravity.CENTER);contextActions.setClipChildren(false);sheet.body.addView(contextActions,new LinearLayout.LayoutParams(-1,p(46.08f)));renderActions();
        }finally{rendering=false;}
    }
    private void renderActions(){
        if(contextActions==null)return;for(View view:operations)controls.remove(view.getTag());operations.clear();contextActions.removeAllViews();
        if(moving!=null){TextView hint=text("左右移动  确定保存  返回取消",13.44f,23.04f,UiPalette.SECONDARY);contextActions.addView(hint);}
        else if(!selected.isEmpty()){
            action("替换","replace",()->openCatalog(selected));if(saved.ids.size()>1)action("移动","move",this::beginMove);
            action("移除","remove",()->{ArrayList<String> next=new ArrayList<>(saved.ids);int index=next.indexOf(selected);next.remove(selected);String neighbor=next.isEmpty()?"":next.get(Math.min(index,next.size()-1));submit(next,neighbor,false,"已移除首页按钮");});
        }
        for(View position:positions)position.setActivated(!selected.isEmpty()&&("slot-"+selected).equals(position.getTag()));wireEditor();
    }
    private void action(String name,String tag,Runnable action){
        Button b=button(name,tag,action,false);b.setPadding(p(13.8f),0,p(13.8f),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(p(82),p(38.4f));if(!operations.isEmpty())lp.leftMargin=p(12);contextActions.addView(b,lp);operations.add(b);
    }
    private GradientDrawable circle(boolean add,boolean focused,boolean enabled){
        GradientDrawable shape=new GradientDrawable();shape.setShape(GradientDrawable.OVAL);shape.setColor(focused?UiPalette.TEXT:Color.TRANSPARENT);
        int edge=enabled&&add?UiPalette.EDGE_STRONG:UiPalette.EDGE;if(!focused){if(add)shape.setStroke(Math.max(1,p(.96f)),edge,p(4),p(4));else shape.setStroke(Math.max(1,p(.96f)),edge);}return shape;
    }
    private Drawable slotBackground(boolean add){
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{-android.R.attr.state_enabled},circle(add,false,false));
        states.addState(new int[]{android.R.attr.state_focused,android.R.attr.state_window_focused},circle(add,true,true));states.addState(new int[]{android.R.attr.state_activated,android.R.attr.state_window_focused},circle(add,true,true));states.addState(new int[]{},circle(add,false,true));return states;
    }
    private void slot(LinearLayout row,String id,int index){
        boolean add=id.isEmpty();LinearLayout item=ui.column();item.setGravity(Gravity.CENTER_HORIZONTAL);item.setClipChildren(false);
        FrameLayout circle=new FrameLayout(ui);circle.setFocusable(true);circle.setFocusableInTouchMode(true);circle.setClickable(true);SettingsTouchClick.attach(circle);circle.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);circle.setTag(add?"slot-empty-"+index:"slot-"+id);circle.setId(androidx.core.view.ViewCompat.generateViewId());circle.setContentDescription(add?"添加首页按钮  位置 "+(index+1):"编辑"+title(id));circle.setBackgroundDrawable(slotBackground(add));circle.setEnabled(!busy&&pending==null&&(!add||moving==null));controls.put((String)circle.getTag(),circle);positions.add(circle);
        ImageView icon=new ImageView(ui);icon.setDuplicateParentStateEnabled(true);icon.setImageDrawable(new QuickControlIcon(add?"plus":HomeButtonStore.glyph(id)));FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(p(33.28f),p(33.28f),Gravity.CENTER);circle.addView(icon,ip);item.addView(circle,new LinearLayout.LayoutParams(p(88),p(88)));
        TextView name=text(add?"添加首页按钮":title(id),15.36f,23.04f,UiPalette.TEXT);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,p(23.04f));np.topMargin=p(8);item.addView(name,np);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(p(140.8f),p(120));if(index>0)lp.leftMargin=p(19.2f);row.addView(item,lp);
        circle.setOnFocusChangeListener((v,focused)->{if(focused&&!rendering&&moving==null&&!selected.equals(id)){selected=id;renderActions();}});
        circle.setOnClickListener(v->{if(moving!=null){int from=moving.indexOf(selected),to=moving.indexOf(id);if(from==to)confirmMove();else if(from>=0&&to>=0){moving.remove(from);moving.add(to,selected);render();focus("slot-"+selected);}return;}if(add)openCatalog("");else focus("replace");});item.setOnClickListener(v->{if(circle.isEnabled()){circle.requestFocusFromTouch();circle.performClick();}});
    }
    private void wireEditor(){
        if(positions.isEmpty())return;View selectedView=controls.get("slot-"+selected),first=selectedView==null?positions.get(0):selectedView;close.setNextFocusDownId(first.getId());
        for(int index=0;index<positions.size();index++){View view=positions.get(index);view.setNextFocusLeftId(positions.get(Math.max(0,index-1)).getId());view.setNextFocusRightId(positions.get(Math.min(positions.size()-1,index+1)).getId());view.setNextFocusUpId(close.getId());view.setNextFocusDownId(operations.isEmpty()?close.getId():operations.get(0).getId());}
        for(int index=0;index<operations.size();index++){View view=operations.get(index);view.setNextFocusLeftId(operations.get(Math.max(0,index-1)).getId());view.setNextFocusRightId(operations.get(Math.min(operations.size()-1,index+1)).getId());view.setNextFocusUpId(first.getId());view.setNextFocusDownId(view.getId());}
    }
    private boolean key(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())back();return true;}
        if(moving==null||busy||pending!=null)return false;
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)move(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())confirmMove();return true;}
        return key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
    }
    private String firstCatalog(){for(String id:HomeButtonStore.EDITABLE)if(!saved.ids.contains(id))return "catalog-"+id;return "catalog-back";}
    private void openCatalog(String id){replace=id;catalog=true;render();focus(firstCatalog());}
    private void renderCatalog(){
        LinearLayout grid=ui.column();grid.setPadding(p(3.84f),p(3.84f),p(3.84f),p(3.84f));sheet.body.addView(grid);LinearLayout row=null;
        for(int i=0;i<HomeButtonStore.EDITABLE.size();i++){if(i%3==0){row=ui.row();row.setClipChildren(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);if(i>0)rp.topMargin=p(9.6f);grid.addView(row,rp);}String id=HomeButtonStore.EDITABLE.get(i);LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(p(7.4f),p(11.24f),p(7.4f),p(11.24f));item.setTag("catalog-"+id);item.setId(androidx.core.view.ViewCompat.generateViewId());item.setContentDescription(title(id));item.setFocusable(true);item.setFocusableInTouchMode(true);item.setClickable(true);SettingsTouchClick.attach(item);item.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);item.setEnabled(!busy&&pending==null&&!saved.ids.contains(id)&&(!replace.isEmpty()||saved.ids.size()<3));item.setAlpha(1f);item.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,7.68f,UiPalette.CONTROL,false,UiPalette.EDGE));controls.put("catalog-"+id,item);ImageView image=new ImageView(ui);image.setDuplicateParentStateEnabled(true);image.setImageDrawable(new QuickControlIcon(HomeButtonStore.glyph(id)));item.addView(image,new LinearLayout.LayoutParams(p(24),p(24)));TextView label=text(title(id),14.72f,20.608f,UiPalette.TEXT);label.setDuplicateParentStateEnabled(true);label.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.topMargin=p(7.04f);item.addView(label,tp);if(saved.ids.contains(id)){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,p(15.232f));ap.topMargin=p(7.04f);item.addView(text("已添加",10.88f,15.232f,UiPalette.SECONDARY),ap);}boolean addedRow=false;for(int j=i/3*3;j<Math.min(i/3*3+3,HomeButtonStore.EDITABLE.size());j++)addedRow|=saved.ids.contains(HomeButtonStore.EDITABLE.get(j));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,p(addedRow?95.187f:80.64f),1);if(i%3>0)lp.leftMargin=p(9.6f);row.addView(item,lp);item.setOnClickListener(v->{ArrayList<String> next=new ArrayList<>(saved.ids);if(replace.isEmpty())next.add(id);else next.set(next.indexOf(replace),id);submit(next,id,false,replace.isEmpty()?"已添加首页按钮":"首页按钮已替换");});}
        int remainder=HomeButtonStore.EDITABLE.size()%3;if(remainder>0)for(int i=remainder;i<3;i++){View spacer=new View(ui);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,1,1);lp.leftMargin=p(9.6f);row.addView(spacer,lp);}Button back=button("返回","catalog-back",this::back,false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,p(46.08f));bp.topMargin=p(24);bp.gravity=Gravity.RIGHT;sheet.body.addView(back,bp);
    }
    private void beginMove(){if(busy||pending!=null||saved.ids.size()<2||selected.isEmpty())return;moving=new ArrayList<>(saved.ids);render();focus("slot-"+selected);}
    private void move(int delta){if(moving==null)return;int index=moving.indexOf(selected),next=index+delta;if(index<0||next<0||next>=moving.size())return;Collections.swap(moving,index,next);render();focus("slot-"+selected);}
    private void confirmMove(){if(moving==null)return;if(moving.equals(saved.ids)){moving=null;render();focus("slot-"+selected);return;}submit(new ArrayList<>(moving),selected,false,"按钮顺序已保存");}
    private void submit(ArrayList<String> ids,String focus,boolean close,String message){if(busy||pending!=null)return;pending=ids;expected=saved.revision;target=focus;closeAfter=close;notice=message;persist();}
    private void persist(){if(busy||pending==null)return;busy=true;int request=generation;long revision=expected;error="";render();ArrayList<String> ids=new ArrayList<>(pending);worker.execute(()->{HomeButtonStore.Snapshot result=null;String failure="";try{result=store.save(revision,ids);}catch(IOException e){failure=e.getMessage();}HomeButtonStore.Snapshot ready=result;String message=failure;main.post(()->{if(destroyed||sheet==null||request!=generation)return;busy=false;if(ready==null){error=message;render();focus("retry");return;}saved=ready;pending=null;moving=null;catalog=false;replace="";selected=target;changed.run();ui.feedback(notice);if(closeAfter)sheet.dialog.dismiss();else{render();focus("slot-"+selected);}});});}
    private void focus(String tag){if(sheet==null)return;sheet.body.post(()->{if(sheet==null)return;View v=controls.get(tag);if(v==null||!v.isEnabled())v=controls.get(pending!=null?"retry":catalog?firstCatalog():"slot-empty-0");if(v==null)v=controls.get("reload");if(v!=null&&v.isEnabled()){v.requestFocusFromTouch();Rect area=new Rect();v.getDrawingRect(area);v.requestRectangleOnScreen(area,false);}});}
    void back(){if(sheet==null||busy)return;if(pending!=null){pending=null;moving=null;load();return;}if(moving!=null){moving=null;render();focus("slot-"+selected);return;}if(catalog){catalog=false;replace="";render();focus("slot-"+selected);}else sheet.dialog.dismiss();}
    String focusId(){return selected;}
    void saveState(Bundle state){if(sheet==null)return;View active=sheet.dialog.getCurrentFocus();state.putString("home-button-focus",active!=null&&active.getTag() instanceof String?(String)active.getTag():"");state.putInt("home-button-scroll",sheet.scroll.getScrollY());state.putBoolean("home-button-open",true);state.putString("home-button-selected",selected);state.putBoolean("home-button-catalog",catalog);state.putStringArrayList("home-button-moving",moving);if(moving!=null&&saved!=null)state.putLong("home-button-move-revision",saved.revision);state.putString("home-button-replace",replace);state.putStringArrayList("home-button-pending",pending);state.putLong("home-button-expected",expected);state.putString("home-button-target",target);state.putString("home-button-notice",notice);state.putBoolean("home-button-close",closeAfter);}
    void restore(Bundle state){if(state!=null&&state.getBoolean("home-button-open")){restored=state;restoredFocusTag=state.getString("home-button-focus","");restoredScrollY=state.getInt("home-button-scroll");show(state.getString("home-button-selected"));}}
    void destroy(){destroyed=true;worker.shutdown();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
