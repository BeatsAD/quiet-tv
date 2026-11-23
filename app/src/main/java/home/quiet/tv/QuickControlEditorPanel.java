package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** The shortcut editor keeps its list and preview behind the candidate dialog. */
final class QuickControlEditorPanel extends ManagementPanel {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private QuickControlStore store;private QuickControlStore.Snapshot committed;private final ArrayList<String> order=new ArrayList<>();
    private final LinkedHashMap<String,View> targets=new LinkedHashMap<>();
    private LinearLayout content,errorBox;private TextView capacity;private ScrollView scroll;private QuickControlPreviewView preview;private QuickActionTooltip tooltip;private Button restore;
    private boolean retentionDraft,retentionSave;
    private boolean legacy(){return committed!=null&&QuickControlStore.buttonCount(committed.ids)>QuickControlStore.MAX_BUTTONS;}
    private boolean hasCandidates(boolean range,String replace){
        for(String id:QuickControlStore.ALL)if(QuickControlStore.range(id)==range&&QuickControlStore.canAdd(order,id,replace))return true;
        return false;
    }
    private ArrayList<String> pending;private String selected="",moving="",focusTag="",saveFocus="",pendingNotice="快捷功能已保存",error="";private boolean busy,active,destroyed,conflict,refreshing;private int listScroll;private Bundle restored;
    private Picker picker;
    private final Runnable phoneChanged=this::phoneChanged;
    private void phoneChanged(){if(!active||destroyed)return;QuickControlStore.Snapshot next=store.cached();if(committed==null||next==null||next.revision!=committed.revision){if(!locked()&&!editing()&&!retentionDraft)load();}else updatePreview();}
    QuickControlEditorPanel(AppManagementActivity activity){super(activity);}
    private int p(float logical){return px(logical*.75f);}
    private String title(String id){return QuickControlStore.title(id,activity.chinese());}
    private TextView text(String value,float size,float height,int color){TextView t=label(value,13);QuickControlPreviewView.controlType(t,size,height,unit,color);return t;}
    @Override boolean useSettingListStyle(){return false;}
    @Override public void onCreate(Bundle state){
        store=new QuickControlStore(new File(activity.getFilesDir(),"quick-controls"));restored=state;if(state==null){String requested=activity.getIntent().getStringExtra("quick-selected");if(QuickControlStore.ALL.contains(requested)){restored=new Bundle();restored.putString("quick-selected",requested);restored.putString("quick-focus","managed-"+requested);restored.putBoolean("quick-content-focus",true);}}
        pageRoot=row();pageRoot.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);pageRoot.setClipChildren(false);pageRoot.setClipToPadding(false);activity.attachPanel(pageRoot);
        LinearLayout left=column();left.setClipChildren(false);left.setClipToPadding(false);pageRoot.addView(left,new LinearLayout.LayoutParams(p(614.4f),-1));LinearLayout leftHeader=row();leftHeader.setPadding(p(4.48f),0,p(4.48f),0);TextView heading=text("已添加",17.28f,25.92f,UiPalette.SECONDARY);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));leftHeader.addView(heading,new LinearLayout.LayoutParams(-2,-2));capacity=text("",14.72f,23,UiPalette.SECONDARY);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.leftMargin=p(16);leftHeader.addView(capacity,cp);leftHeader.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));restore=button("恢复默认",()->submit(QuickControlStore.DEFAULT,"managed-"+QuickControlStore.DEFAULT.get(0),"已恢复默认快捷功能"));styleButton(restore);register(restore,"quick-restore");leftHeader.addView(restore,new LinearLayout.LayoutParams(-2,p(37.36f)));restore.setOnFocusChangeListener((v,f)->{if(tooltip!=null)tooltip.hide();});LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,p(37.36f));hp.bottomMargin=p(8.16f);left.addView(leftHeader,hp);
        errorBox=column();errorBox.setVisibility(View.GONE);left.addView(errorBox,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout frame=new FrameLayout(activity);frame.setClipChildren(true);frame.setClipToPadding(true);left.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        scroll=new ScrollView(activity){@Override protected void dispatchDraw(Canvas c){int save=c.save();c.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(c);c.restoreToCount(save);}};scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setClipChildren(false);scroll.setClipToPadding(false);scroll.setPadding(p(16.48f),p(15.84f),p(16.48f),p(19.68f));content=column();content.setClipChildren(false);content.setClipToPadding(false);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-1);sp.setMargins(p(-12),p(-12),p(-12),p(-12));frame.addView(scroll,sp);
        LinearLayout right=column();right.setClipChildren(false);right.setClipToPadding(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(p(440),-1);rp.leftMargin=p(48);pageRoot.addView(right,rp);LinearLayout header=row();header.setClipChildren(false);header.setClipToPadding(false);TextView caption=text("面板预览",17.28f,25.92f,UiPalette.SECONDARY);caption.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));header.addView(caption,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams rh=new LinearLayout.LayoutParams(-1,p(37.36f));rh.bottomMargin=p(12);right.addView(header,rh);
        preview=new QuickControlPreviewView(activity);right.addView(preview,new LinearLayout.LayoutParams(-1,-2));tooltip=new QuickActionTooltip(activity,frame);scroll.getViewTreeObserver().addOnScrollChangedListener(tooltip::reposition);
        restore.setOnKeyListener((v,key,event)->{if(!arrow(key))return false;if(event.getAction()==KeyEvent.ACTION_DOWN){if(key==KeyEvent.KEYCODE_DPAD_UP)managementTab().requestFocusFromTouch();else if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_DOWN)focus(focusTag.isEmpty()||focusTag.equals("quick-restore")?firstTag():focusTag,false,true);}return true;});
    }
    private boolean editing(){return !moving.isEmpty()||picker!=null;}
    private boolean locked(){return busy||pending!=null||committed==null;}
    private String firstTag(){if(!error.isEmpty())return "quick-error-retry";return order.isEmpty()?"button-vacancy-0":"managed-"+(order.contains(selected)?selected:order.get(0));}
    private void load(){
        if(busy||refreshing||destroyed)return;boolean contentHadFocus=pageRoot.hasFocus();
        // Retain the editable surface while a warm tab checks for external changes.
        boolean refresh=committed!=null&&restored==null&&error.isEmpty()&&pending==null&&!editing()&&!retentionDraft;
        if(refresh)refreshing=true;else{busy=true;render();}worker.execute(()->{QuickControlStore.Snapshot value=null;try{value=store.read();}catch(IOException failure){}QuickControlStore.Snapshot ready=value;main.post(()->{
            if(destroyed)return;if(refresh)refreshing=false;else busy=false;
            // A user may have started editing or saving while the read was in flight.
            if(refresh&&(busy||editing()||pending!=null||retentionDraft))return;
            if(ready==null){error="无法读取快捷功能，原文件已保留。请检查存储后重新读取。";render();return;}
            if(refresh&&ready.revision==committed.revision&&ready.ids.equals(committed.ids)){updatePreview();phoneChanged();return;}
            committed=ready;order.clear();order.addAll(ready.ids);error="";conflict=false;pending=null;boolean restoreContent=contentHadFocus||pageRoot.hasFocus();
            if(restored!=null){Bundle saved=restored;restored=null;restoreContent|=saved.getBoolean("quick-content-focus");selected=saved.getString("quick-selected","");focusTag=saved.getString("quick-focus","");saveFocus=saved.getString("quick-save-focus","");pendingNotice=saved.getString("quick-save-notice","快捷功能已保存");listScroll=saved.getInt("quick-scroll");ArrayList<String> draft=saved.getStringArrayList("quick-order");boolean valid=draft!=null&&new HashSet<>(draft).size()==draft.size()&&QuickControlStore.ALL.containsAll(draft);
                if(valid)draft=new ArrayList<>(QuickControlStore.normalize(draft));
                if(valid&&(saved.getBoolean("quick-retention")||saved.getBoolean("quick-pending")||!saved.getString("quick-moving","").isEmpty())){
                    if(saved.getBoolean("quick-pending")&&ready.ids.equals(draft)){focusTag=saveFocus;}
                    else{retentionDraft=saved.getBoolean("quick-retention");order.clear();order.addAll(draft);moving=saved.getString("quick-moving","");if(saved.getBoolean("quick-pending")||saved.getLong("quick-revision",-1)!=ready.revision){pending=new ArrayList<>(draft);conflict=saved.getLong("quick-revision",-1)!=ready.revision;error=conflict?"实际配置已变化，草稿已保留。请重新读取后再编辑。":"本次修改尚未确认保存，可重试保存或重新读取。";}}
                }
            }
            if(!order.contains(selected))selected=order.isEmpty()?"":order.get(0);int savedScroll=listScroll;render();listScroll=savedScroll;scroll.post(()->scroll.scrollTo(0,savedScroll));if(active&&(restoreContent||!managementTabFocused()))focus(focusTag.isEmpty()?firstTag():focusTag,true);phoneChanged();
        });});
    }
    private void submit(List<String> next,String target,String notice){
        if(locked())return;
        if(legacy()&&!retentionSave&&!notice.equals("已恢复默认快捷功能")){ArrayList<String> draft=new ArrayList<>(next);order.clear();order.addAll(draft);retentionDraft=true;moving="";dismissPicker();render();focus(target,false);return;}
        try{QuickControlStore.validateLimit(committed.ids,next);}catch(IOException failure){error(failure.getMessage());return;}retentionDraft=false;pending=new ArrayList<>(next);order.clear();order.addAll(pending);saveFocus=target;pendingNotice=notice;persist();
    }
    private void persist(){
        if(busy||pending==null||committed==null)return;busy=true;error="";ArrayList<String> next=new ArrayList<>(pending);long revision=committed.revision;String notice=pendingNotice;if(picker==null)render();
        worker.execute(()->{QuickControlStore.Snapshot result=null;boolean changed=false;try{result=store.save(revision,next);}catch(QuickControlStore.Changed failure){changed=true;}catch(IOException failure){}QuickControlStore.Snapshot ready=result;boolean stale=changed;main.post(()->{
            if(destroyed)return;busy=false;dismissPicker();if(ready==null){conflict=stale;error=stale?"快捷功能已在其他位置修改，草稿已保留。请重新读取后再编辑。":"保存未确认，本次修改已保留。请重试保存或重新读取。";render();if(active)focus("quick-error-retry",false);return;}
            committed=ready;pending=null;order.clear();order.addAll(ready.ids);moving="";error="";conflict=false;if(!order.contains(selected))selected=order.isEmpty()?"":order.get(0);render();PhoneSettingsSession.get(activity).configurationChanged();if(active){focus(saveFocus,false);success(notice);}
        });});
    }
    private void reload(){if(busy||refreshing)return;retentionDraft=false;moving="";pending=null;restored=null;load();}
    private void render(){
        if(content==null)return;listScroll=scroll.getScrollY();tooltip.reset();content.removeAllViews();targets.clear();boolean enabled=!locked();restore.setEnabled(enabled&&!editing());restore.setAlpha(1f);renderError();
        capacity.setText(committed==null?"":"（"+QuickControlStore.buttonCount(order)+"/"+QuickControlStore.MAX_BUTTONS+"）");
        if(committed==null){content.addView(text(busy?"正在读取快捷功能…":"快捷功能读取失败",14.72f,22.08f,UiPalette.SECONDARY));updatePreview();return;}
        if(legacy()){
            StringBuilder hidden=new StringBuilder();for(String id:QuickControlStore.overflow(order)){if(hidden.length()>0)hidden.append("  ");hidden.append(title(id));}
            content.addView(text("旧配置已保留，请选定最多 8 个普通按钮后保存。"+(hidden.length()>0?"未显示："+hidden:""),14.72f,23,UiPalette.TEXT));
            Button save=button("保存保留内容（"+QuickControlStore.buttonCount(order)+" / 8）",()->{retentionSave=true;try{submit(new ArrayList<>(order),firstTag(),"保留内容已保存");}finally{retentionSave=false;}});styleButton(save);register(save,"quick-retain-save");save.setEnabled(enabled&&QuickControlStore.buttonCount(order)<=8);content.addView(save,new LinearLayout.LayoutParams(-1,p(40)));
        }
        for(int i=0;i<order.size();i++)if(!QuickControlStore.range(order.get(i)))managed(order.get(i),i,enabled);
        for(int i=QuickControlStore.buttonCount(order);i<QuickControlStore.MAX_BUTTONS;i++)vacancy(false,i,enabled&&!editing());
        int ranges=QuickControlStore.allRanges(order).size();
        gap(content,12);TextView rangeHeading=text("快捷调节（"+Math.min(ranges,QuickControlStore.RANGE_LIMIT)+"/"+QuickControlStore.RANGE_LIMIT+"）",17.28f,25.92f,UiPalette.SECONDARY);rangeHeading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));LinearLayout.LayoutParams rangeCaption=new LinearLayout.LayoutParams(-1,p(25));rangeCaption.bottomMargin=p(4.48f);content.addView(rangeHeading,rangeCaption);
        int rangeIndex=0;
        for(int i=0;i<order.size();i++)if(QuickControlStore.range(order.get(i))){
            if(rangeIndex==QuickControlStore.RANGE_LIMIT){
                TextView overflow=text("旧配置保留项  不显示在快捷面板",14.72f,23,UiPalette.SECONDARY);
                LinearLayout.LayoutParams note=new LinearLayout.LayoutParams(-1,-2);note.topMargin=p(12);content.addView(overflow,note);
            }
            managed(order.get(i),i,enabled);rangeIndex++;
        }
        for(int i=ranges;i<QuickControlStore.RANGE_LIMIT;i++)vacancy(true,i,enabled&&!editing());
        content.addView(new View(activity),new LinearLayout.LayoutParams(1,p(26.24f)));
        updatePreview();
        scroll.post(()->scroll.scrollTo(0,listScroll));
    }
    private void renderError(){errorBox.removeAllViews();errorBox.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);if(error.isEmpty())return;TextView message=text(error,15.36f,26.112f,UiPalette.TEXT);OperationFeedback.status(message,OperationFeedback.Kind.ERROR);message.setPadding(p(17.92f),p(12.8f),p(17.92f),p(12.8f));message.setMinHeight(p(54.144f));message.setBackgroundDrawable(surface(0x1affffff,0x1affffff,UiPalette.EDGE_STRONG,6.72f));errorBox.addView(message,new LinearLayout.LayoutParams(-1,-2));LinearLayout actions=row();if(pending!=null&&!conflict){Button retry=button("重试保存",this::persist);styleButton(retry);register(retry,"quick-error-retry");actions.addView(retry,new LinearLayout.LayoutParams(0,p(40),1));}Button read=button("重新读取",this::reload);styleButton(read);register(read,pending!=null&&!conflict?"quick-error-read":"quick-error-retry");actions.addView(read,new LinearLayout.LayoutParams(0,p(40),1));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=p(8);ap.bottomMargin=p(8);errorBox.addView(actions,ap);}
    private void managed(String id,int index,boolean enabled){
        int position=0;for(int i=0;i<index;i++)if(QuickControlStore.range(order.get(i))==QuickControlStore.range(id))position++;
        LinearLayout outer=column();ManagedRow row=new ManagedRow(id);outer.setClipChildren(false);outer.setClipToPadding(false);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);if(position>0)op.topMargin=p(4.48f);content.addView(outer,op);outer.addView(row,new LinearLayout.LayoutParams(-1,p(40.32f)));
        LinearLayout name=row();name.setPadding(p(8.32f),p(3.84f),p(8.32f),p(3.84f));row.title=name;name.setFocusable(true);name.setFocusableInTouchMode(true);name.setClickable(true);name.setSoundEffectsEnabled(false);name.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);name.setContentDescription("管理"+title(id));register(name,"managed-"+id);LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(0,-1,1);nl.rightMargin=p(4);row.addView(name,nl);
        TextView number=text(QuickControlStore.range(id)?"":String.valueOf(position+1),13.44f,18.5f,UiPalette.SECONDARY);number.setMinWidth(p(14.08f));name.addView(number,new LinearLayout.LayoutParams(-2,-2));ImageView icon=icon(leftGlyph(id),20.48f);LinearLayout.LayoutParams ip=(LinearLayout.LayoutParams)icon.getLayoutParams();ip.leftMargin=p(8.32f);name.addView(icon,ip);TextView label=text(title(id),16.64f,23.296f,UiPalette.TEXT);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=p(8.32f);name.addView(label,lp);
        name.setEnabled(enabled&&picker==null&&(moving.isEmpty()||moving.equals(id)));
        name.setOnFocusChangeListener((v,focused)->{row.invalidate();if(focused){focusTag="managed-"+id;choose(id);}tooltip.hide();});
        name.setOnClickListener(v->KeyFeedback.clicked(v,()->{
            if(moving.equals(id))submit(order,"managed-"+id,"顺序已保存");
            else focusFirstAction(id);
        }));
        touch(name);
        String[] actions=moving.equals(id)?new String[]{"up","down","commit","cancel"}:QuickControlStore.SETTINGS.equals(id)?new String[]{"add","sort"}:new String[]{"add","replace","remove","sort"};
        for(String action:actions){
            if(action.equals("add")&&!hasCandidates(QuickControlStore.range(id),""))continue;
            Button button=button("",()->action(id,action));
            register(button,actionTag(id,action));
            button.setContentDescription(actionDescription(id,action));
            button.setPadding(0,0,0,0);
            button.setBackgroundDrawable(new ActionSurface(action,button));
            boolean allowed=enabled&&(!editing()||moving.equals(id));
            if(action.equals("replace")&&!hasCandidates(QuickControlStore.range(id),id)
                    ||action.equals("sort")&&moveIndex(id,-1)<0&&moveIndex(id,1)<0
                    ||action.equals("up")&&moveIndex(id,-1)<0||action.equals("down")&&moveIndex(id,1)<0)allowed=false;
            button.setEnabled(allowed);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(p(35.84f),p(32));
            size.leftMargin=p(4.48f);
            row.addView(button,size);
            button.setOnFocusChangeListener((v,focused)->{
                row.invalidate();
                if(focused){focusTag=actionTag(id,action);choose(id);tooltip.show(v,actionLabel(action));}
                else tooltip.hide(v);
            });
            button.setOnHoverListener((v,event)->{
                if(event.getAction()==MotionEvent.ACTION_HOVER_MOVE||event.getAction()==MotionEvent.ACTION_HOVER_ENTER){v.setHovered(true);tooltip.show(v,actionLabel(action));}
                else if(event.getAction()==MotionEvent.ACTION_HOVER_EXIT){v.setHovered(false);if(!v.hasFocus())tooltip.hide(v);}
                return false;
            });
        }
        if(moving.equals(id)){TextView hint=text("上下调整  确定保存  返回取消",13.44f,18.816f,UiPalette.SECONDARY);LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2);h.setMargins(p(10.24f),p(7.04f),p(10.24f),p(8.96f));outer.addView(hint,h);}
    }
    private void childColors(TextView text,int normal){text.setDuplicateParentStateEnabled(true);text.setTextColor(SettingsFocusDrawable.textColors(normal));}
    private void choose(String id){selected=id;updatePreview();}
    private static String actionTag(String id,String action){return "action-"+id+"-"+action;}
    private ArrayList<String> rowTargets(String id){
        ArrayList<String> result=new ArrayList<>();
        result.add("managed-"+id);
        String prefix="action-"+id+"-";
        for(Map.Entry<String,View> entry:targets.entrySet())
            if(entry.getKey().startsWith(prefix)&&entry.getValue().isEnabled())result.add(entry.getKey());
        return result;
    }
    private void focusFirstAction(String id){
        ArrayList<String> row=rowTargets(id);
        if(row.size()>1)focus(row.get(1),false);
    }
    private ArrayList<String> verticalTargets(){
        ArrayList<String> result=new ArrayList<>();
        for(Map.Entry<String,View> entry:targets.entrySet()){
            String tag=entry.getKey();
            if(entry.getValue().isEnabled()&&(tag.startsWith("managed-")||tag.startsWith("button-vacancy-")||tag.startsWith("range-vacancy-")))result.add(tag);
        }
        return result;
    }
    private int moveIndex(String id,int direction){int from=order.indexOf(id);if(from<0)return -1;for(int i=from+direction;i>=0&&i<order.size();i+=direction)if(QuickControlStore.range(order.get(i))==QuickControlStore.range(id))return i;return -1;}
    private void vacancy(boolean range,int index,boolean enabled){
        String title=range?"添加调节项":"添加按钮";
        String tag=(range?"range-vacancy-":"button-vacancy-")+index;
        Button slot=button("＋  "+title,()->openPicker(range,"","",tag));
        register(slot,tag);
        slot.setContentDescription(title+"  第"+(index+1)+"个位置");
        slot.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13.5f*unit);
        slot.setTextColor(UiPalette.TEXT);
        slot.setEnabled(enabled);
        SurfaceDrawable border=new SurfaceDrawable(unit);
        border.setCornerRadius(p(12));
        slot.setBackgroundDrawable(border);
        styleVacancy(border,false,enabled);
        slot.setOnFocusChangeListener((v,focused)->{
            styleVacancy(border,focused,enabled);
            if(focused)focusTag=tag;
            tooltip.hide();
        });
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,p(40.32f));
        size.topMargin=p(4.48f);
        content.addView(slot,size);
    }
    private void styleVacancy(SurfaceDrawable border,boolean focused,boolean enabled){
        border.setColor(focused?0x27ffffff:0x08ffffff);
        border.setStroke(Math.max(1,p(focused?1.3f:1)),focused?UiPalette.TEXT:enabled?0xb3ffffff:UiPalette.EDGE,p(4),p(4));
    }
    private void openPicker(boolean range,String replace,String after,String origin){
        if(locked()||editing())return;
        tooltip.hide();
        picker=new Picker(range,replace,after,origin);
        picker.show();
    }
    private void dismissPicker(){
        if(picker==null)return;
        Picker open=picker;
        picker=null;
        open.sheet.dialog.dismiss();
    }
    private void cancelPicker(){
        if(picker==null||locked())return;
        String origin=picker.origin;
        dismissPicker();
        // The list stays mounted while the dialog is open.
        focus(origin,false);
        phoneChanged();
    }
    private void add(Picker open,String id){
        if(picker!=open||locked()||!QuickControlStore.canAdd(order,id,open.replace))return;
        ArrayList<String> next=new ArrayList<>(order);
        if(!open.replace.isEmpty())next.set(next.indexOf(open.replace),id);
        else next.add(open.after.isEmpty()?next.size():next.indexOf(open.after)+1,id);
        selected=id;
        submit(next,"managed-"+id,open.replace.isEmpty()?"已添加到快捷控制":"已替换，位置保持不变");
    }
    private void action(String id,String action){
        if(locked()||QuickControlStore.SETTINGS.equals(id)&&(action.equals("remove")||action.equals("replace")))return;
        tooltip.hide();
        boolean range=QuickControlStore.range(id);
        switch(action){
            case "add":openPicker(range,"",id,actionTag(id,action));break;
            case "replace":openPicker(range,id,"",actionTag(id,action));break;
            case "remove":{
                ArrayList<String> group=new ArrayList<>();
                for(String entry:order)if(QuickControlStore.range(entry)==range)group.add(entry);
                int index=group.indexOf(id);
                group.remove(id);
                ArrayList<String> next=new ArrayList<>(order);
                next.remove(id);
                selected=group.isEmpty()?"":group.get(Math.min(index,group.size()-1));
                String target=group.isEmpty()?"range-vacancy-0":"managed-"+selected;
                submit(next,target,"已移除快捷入口，内容保留");
                break;
            }
            case "sort":moving=id;render();focus("managed-"+id,false);break;
            case "up":step(-1);break;
            case "down":step(1);break;
            case "commit":submit(order,"managed-"+id,"顺序已保存");break;
            case "cancel":cancelMove();break;
        }
    }
    private boolean step(int direction){
        int from=order.indexOf(moving),to=moveIndex(moving,direction);
        boolean changed=from>=0&&to>=0;
        if(changed)Collections.swap(order,from,to);
        render();
        focus("managed-"+moving,false);
        return changed;
    }
    private void cancelMove(){String id=moving;moving="";order.clear();order.addAll(committed.ids);render();focus("managed-"+id,false);feedback("已取消本次排序");}
    private void register(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());targets.put(tag,view);}
    private void touch(View view){SettingsTouchClick.attach(view);}
    private void styleButton(Button b){b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,10.56f*unit);b.setPadding(p(15.08f),0,p(15.08f),0);b.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,UiPalette.CONTROL,false,0x4dffffff));}
    private ImageView icon(String glyph,float size){ImageView image=new ImageView(activity);image.setImageDrawable(new QuickControlIcon(glyph));image.setLayoutParams(new LinearLayout.LayoutParams(p(size),p(size)));return image;}
    private static String leftGlyph(String id){return QuickControlPreviewView.glyph(id);}
    private void focus(String tag,boolean restoreScroll){focus(tag,restoreScroll,false);}
    private void focus(String tag,boolean restoreScroll,boolean directionKey){
        if(directionKey)KeyFeedback.ownMove(content);
        Runnable move=()->{
            if(!active)return;
            if(restoreScroll)scroll.scrollTo(0,listScroll);
            View target=tag.equals("quick-restore")?restore:targets.get(tag);
            if(target==null||!target.isEnabled())target=targets.get(firstTag());
            if(target==null||!target.isEnabled())target=restore;
            if(!target.isEnabled())return;
            boolean changed=target!=getCurrentFocus();
            if(target.requestFocusFromTouch()&&changed&&directionKey)KeyFeedback.movedTo(target);
            if(target!=restore){
                Rect bounds=new Rect();
                target.getDrawingRect(bounds);
                bounds.inset(0,-p(5));
                target.requestRectangleOnScreen(bounds,false);
            }
        };
        if(directionKey&&!restoreScroll)move.run();
        else content.post(move);
    }
    private void updatePreview(){
        if(preview==null)return;
        preview.setVisibility(committed==null?View.INVISIBLE:View.VISIBLE);
        if(committed==null)return;
        DesktopSettingsStore desktop=new DesktopSettingsStore(this);
        WallpaperCatalog wallpaper=WallpaperStore.get(this).snapshot();
        ScreensaverSettings saver=ScreensaverStore.get(this).current();
        preview.render(order,selected,desktop.keySounds(),wallpaper.audible(),saver!=null&&saver.sound&&saver.video(wallpaper),desktop,activity.chinese());
    }
    @Override void focusManagementContent(){focus(focusTag.isEmpty()?firstTag():focusTag,true,KeyFeedback.isDirectionPress());}
    @Override public void handleBack(){tooltip.hide();if(busy)return;if(pending!=null){reload();return;}if(!moving.isEmpty()){cancelMove();return;}if(picker!=null){cancelPicker();return;}if(!managementTabFocused())managementTab().requestFocusFromTouch();else finish();}
    @Override void leaveManagement(Runnable action){if(busy){feedback("正在保存快捷功能，请稍候");managementTab().requestFocusFromTouch();return;}if(editing()||pending!=null){dismissPicker();moving="";pending=null;restored=null;if(committed!=null){order.clear();order.addAll(committed.ids);}render();}tooltip.reset();action.run();}
    @Override protected void onResume(){super.onResume();active=true;PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);if(committed==null||!editing()&&pending==null&&!retentionDraft)load();else{updatePreview();if(!focusTag.isEmpty())focus(focusTag,true);}}
    @Override protected void onPause(){PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);listScroll=scroll.getScrollY();tooltip.hide();super.onPause();}
    @Override protected void onStop(){active=false;tooltip.reset();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;dismissPicker();tooltip.destroy();worker.shutdown();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("quick-content-focus",pageRoot.hasFocus());state.putString("quick-selected",selected);View focused=getCurrentFocus();state.putString("quick-focus",pageRoot.hasFocus()&&focused!=null&&focused.getTag() instanceof String?(String)focused.getTag():focusTag);state.putString("quick-save-focus",saveFocus);state.putString("quick-save-notice",pendingNotice);state.putString("quick-moving",moving);state.putBoolean("quick-retention",retentionDraft);state.putBoolean("quick-pending",pending!=null);state.putLong("quick-revision",committed==null?-1:committed.revision);state.putStringArrayList("quick-order",new ArrayList<>(order));state.putInt("quick-scroll",scroll.getScrollY());}
    private static boolean arrow(int key){return key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT;}
    private String actionLabel(String action){switch(action){case "add":return "添加";case "replace":return "替换";case "remove":return "移除";case "sort":return "排序";case "up":return "上移";case "down":return "下移";case "commit":return "完成";default:return "取消";}}
    private String actionDescription(String id,String action){switch(action){case "add":return "在"+title(id)+"后添加功能";case "replace":return "替换"+title(id);case "remove":return "移除"+title(id);case "sort":return "排序"+title(id);default:return actionLabel(action);}}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(!pageRoot.hasFocus()||(!arrow(key)&&key!=KeyEvent.KEYCODE_MENU))return false;
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return true;
        if(locked())return false;
        View current=getCurrentFocus();
        String tag=current!=null&&current.getTag() instanceof String?(String)current.getTag():"";
        if(tag.equals("quick-restore"))return false;
        boolean vertical=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
        int direction=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1;
        if(!moving.isEmpty()&&vertical){
            KeyFeedback.ownMove(content);
            if(step(direction))KeyFeedback.moved(activity);
            return true;
        }
        String rowId="";
        if(tag.startsWith("managed-"))rowId=tag.substring(8);
        else if(tag.startsWith("action-"))rowId=tag.substring(7,tag.lastIndexOf('-'));
        if(vertical){
            ArrayList<String> rows=verticalTargets();
            int next=rows.indexOf(rowId.isEmpty()?tag:"managed-"+rowId)+direction;
            if(next>=0&&next<rows.size())focus(rows.get(next),false,true);
            else if(direction<0){
                tooltip.hide();
                if(restore.isEnabled())restore.requestFocusFromTouch();
                else managementTab().requestFocusFromTouch();
            }
        }else if(!rowId.isEmpty()){
            if(key==KeyEvent.KEYCODE_MENU)focusFirstAction(rowId);
            else{
                ArrayList<String> row=rowTargets(rowId);
                int next=row.indexOf(tag)+direction;
                if(next>=0&&next<row.size())focus(row.get(next),false,true);
            }
        }
        return true;
    }

    private final class ManagedRow extends LinearLayout {
        private final SurfaceDrawable glass=new SurfaceDrawable(unit);
        private final String id;
        private View title;

        ManagedRow(String id){
            super(activity);
            this.id=id;
            setGravity(Gravity.CENTER_VERTICAL);
            setClipChildren(false);
            setClipToPadding(false);
            setPadding(p(5),p(3),p(5),p(3));
            glass.setCornerRadius(p(15));
        }

        @Override public void onWindowFocusChanged(boolean focused){
            super.onWindowFocusChanged(focused);
            invalidate();
        }

        @Override protected void dispatchDraw(Canvas canvas){
            View child=getFocusedChild();
            boolean focused=child!=null&&child.hasFocus()&&hasWindowFocus();
            boolean wholeRow=focused&&child==title;
            glass.setColor(wholeRow?0x26ffffff:focused?0x1cffffff:UiPalette.CONTROL);
            int edge=wholeRow?0xedfffdf4:focused?0x72fffdf4:UiPalette.EDGE;
            int width=Math.max(1,p(wholeRow?1.3f:1));
            if(id.equals(moving))glass.setStroke(width,edge,p(5),p(4));
            else glass.setStroke(width,edge);
            glass.setBounds(0,0,getWidth(),getHeight());
            glass.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }

    private final class Picker {
        private static final int TILE_HEIGHT=67, ROW_GAP=7;
        final ManagementUi.Sheet sheet;
        final String origin,replace,after;
        final ArrayList<View> candidates=new ArrayList<>();
        private final Button close;
        private final int columns;
        private int headerReturnIndex;

        Picker(boolean range,String replace,String after,String origin){
            this.replace=replace;
            this.after=after;
            this.origin=origin;
            String heading=replace.isEmpty()?(range?"添加调节项":"添加快捷按钮"):(range?"替换调节项":"替换快捷按钮");
            ArrayList<String> available=new ArrayList<>();
            for(String id:QuickControlStore.ALL)
                if(QuickControlStore.range(id)==range&&QuickControlStore.canAdd(order,id,replace))available.add(id);
            columns=available.size()>9?4:3;
            sheet=activity.new Sheet(heading);
            close=sheet.settingsLayout("关闭",QuickControlEditorPanel.this::cancelPicker);
            sheet.settingsActions(false);
            sheet.contentSize(columns==4?568:448,470);
            sheet.scroll.setFocusable(false);
            sheet.scroll.setClipToPadding(true);

            LinearLayout line=null;
            for(String id:available){
                int column=candidates.size()%columns;
                if(column==0){
                    line=row();
                    LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,px(TILE_HEIGHT));
                    if(!candidates.isEmpty())size.topMargin=px(ROW_GAP);
                    sheet.body.addView(line,size);
                }
                View tile=candidate(id);
                LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1);
                if(column>0)size.leftMargin=px(8);
                line.addView(tile,size);
                candidates.add(tile);
            }
            if(candidates.isEmpty())sheet.text(range?"暂无可添加的调节项":"暂无可添加的快捷按钮");
            else for(int column=candidates.size()%columns;column>0&&column<columns;column++){
                LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-1,1);
                size.leftMargin=px(8);
                line.addView(new View(activity),size);
            }
            sheet.dialog.setOnKeyListener((dialog,key,event)->{
                if(KeyFeedback.backKey(activity,event,!locked(),QuickControlEditorPanel.this::cancelPicker))return true;
                if(!arrow(key))return locked();
                if(event.getAction()==KeyEvent.ACTION_DOWN&&!locked())move(key);
                return true;
            });
        }

        private View candidate(String id){
            LinearLayout tile=column();
            tile.setGravity(Gravity.CENTER);
            tile.setFocusableInTouchMode(true);
            tile.setClickable(true);
            tile.setSoundEffectsEnabled(false);
            tile.setContentDescription(title(id));
            tile.setPadding(px(6),0,px(6),0);
            tile.setBackgroundDrawable(new SettingsFocusDrawable(unit,15,UiPalette.CONTROL,false,UiPalette.EDGE));
            ImageView glyph=icon(leftGlyph(id),34);
            glyph.setDuplicateParentStateEnabled(true);
            tile.addView(glyph);
            TextView label=text(title(id),16.64f,23.296f,UiPalette.TEXT);
            childColors(label,UiPalette.TEXT);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
            size.topMargin=px(5);
            tile.addView(label,size);
            tile.setOnClickListener(v->{if(!locked())KeyFeedback.clicked(v,()->add(this,id));});
            touch(tile);
            return tile;
        }

        void show(){sheet.show(candidates.isEmpty()?close:candidates.get(0));}

        private void move(int key){
            View current=sheet.dialog.getCurrentFocus();
            int index=candidates.indexOf(current);
            View target=null;
            if(current==close){
                if(key==KeyEvent.KEYCODE_DPAD_DOWN&&!candidates.isEmpty())target=candidates.get(headerReturnIndex);
            }else if(index>=0){
                int column=index%columns;
                switch(key){
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(column>0)target=candidates.get(index-1);
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(column<columns-1&&index+1<candidates.size())target=candidates.get(index+1);
                        break;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if(index>=columns)target=candidates.get(index-columns);
                        else{
                            headerReturnIndex=index;
                            target=close;
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if((index/columns+1)*columns<candidates.size())
                            target=candidates.get(Math.min(index+columns,candidates.size()-1));
                        break;
                }
            }
            if(target!=null&&target!=current){
                KeyFeedback.ownMove(target);
                if(target.requestFocus())KeyFeedback.movedTo(target);
            }
        }
    }

    private final class ActionSurface extends LayerDrawable {
        private final Paint paint=new Paint(3);private final Path glyph;private final Button button;
        // AndroidX 1.12 retains this public parser for the API14 profile with a library-only annotation.
        @android.annotation.SuppressLint("RestrictedApi")
        ActionSurface(String action,Button button){super(new Drawable[]{new SettingsFocusDrawable(unit,999,Color.TRANSPARENT)});this.button=button;String data;switch(action){case "add":data="M8 2v12M2 8h12";break;case "replace":data="M2 5h11m-3-3 3 3-3 3M14 11H3m3-3-3 3 3 3";break;case "remove":data="M2 8h12";break;case "sort":data="M5 14V2m-3 3 3-3 3 3M11 2v12m-3-3 3 3 3-3";break;case "up":data="M8 14V2m-4 4 4-4 4 4";break;case "down":data="M8 2v12m-4-4 4 4 4-4";break;case "commit":data="M2 8l4 4L14 3";break;default:data="m3 3 10 10M13 3 3 13";}glyph=androidx.core.graphics.PathParser.createPathFromPathData(data);}
        @Override public void draw(Canvas c){if(button.isHovered()&&!button.hasFocus()){paint.setStyle(Paint.Style.FILL);paint.setColor(0x1affffff);c.drawOval(new RectF(getBounds()),paint);}super.draw(c);boolean focused=button.hasFocus()&&button.hasWindowFocus();Rect b=getBounds();float cx=b.exactCenterX(),cy=b.exactCenterY();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*unit);paint.setColor(focused?0x80000000:UiPalette.EDGE);c.drawCircle(cx,cy,p(10.56f)-.375f*unit,paint);int save=c.save();c.translate(cx-p(8),cy-p(8));c.scale(p(16)/16f,p(16)/16f);paint.setColor(!button.isEnabled()?UiPalette.FAINT:focused?UiPalette.INK:UiPalette.TEXT);paint.setStrokeWidth(1.5f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);c.drawPath(glyph,paint);c.restoreToCount(save);}
    }
}
