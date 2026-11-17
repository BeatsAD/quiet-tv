package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** Inline options preview on focus; confirmation alone saves a choice. */
final class DisplayAppearanceChoice extends LinearLayout {
    interface Preview {void value(String key,Object value);void clear(String key);}
    interface Access {String read();void save(String value)throws IOException;}
    interface Failure {void failed(String message);}
    private Failure failure;
    private String operationError="";
    private final ManagementUi ui;private final Access access;private final boolean inline;private final String key,title;private final String[] values,names;private final Preview preview;private final Runnable rewired;
    private final ArrayList<View[]> rows=new ArrayList<>();private String committed,draft;private boolean opened,updating;private TextView pendingLabel;
    private boolean dialogStyle,optionsOnly,twoColumns,solidSelection,available=true,saving,asyncSave;
    private DesktopSettingsRow trigger;
    private View expandedContent;
    private UiMotion.Exit collapsing;
    private androidx.core.view.OneShotPreDrawListener pendingReflow;
    private final android.view.ViewTreeObserver.OnGlobalFocusChangeListener outside=(old,next)->{if(inside(old)&&!inside(next))cancel(false);};
    DisplayAppearanceChoice(ManagementUi ui,DesktopSettingsStore store,String key,String title,String[] values,String[] names,Preview preview,Runnable rewired){
        this(ui,key,title,values,names,new Access(){public String read(){return "clock-style".equals(key)?store.clockStyle():"app-title-position".equals(key)?store.appTitlePosition():"icon-size".equals(key)?store.iconSize():"icon-shape".equals(key)?store.iconShape():String.valueOf(store.appearance(key));}public void save(String value)throws IOException{store.saveAppearance(key,"dock-limit".equals(key)?Integer.valueOf(value):value);}},values.length<=3,preview,rewired);asyncSave=true;
    }
    DisplayAppearanceChoice(ManagementUi ui,String key,String title,String[] values,String[] names,Access access,boolean inline,Preview preview,Runnable rewired){
        super(ui);this.ui=ui;failure=ui::feedback;this.access=access;this.inline=inline;this.key=key;this.title=title;this.values=values;this.names=names;this.preview=preview;this.rewired=rewired;setOrientation(VERTICAL);setClipChildren(false);setClipToPadding(false);committed=current();draft=committed;render();
    }
    private int p(float n){return ui.px(n*.75f);}
    private String current(){return access.read();}
    private boolean inside(View v){while(v!=null){if(v==this)return true;v=v.getParent() instanceof View?(View)v.getParent():null;}return false;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnGlobalFocusChangeListener(outside);}
    @Override protected void onDetachedFromWindow(){if(collapsing!=null){collapsing.finish();collapsing=null;}if(pendingReflow!=null){pendingReflow.removeListener();pendingReflow=null;}getViewTreeObserver().removeOnGlobalFocusChangeListener(outside);super.onDetachedFromWindow();}
    private Object value(String text){return "dock-limit".equals(key)?Integer.valueOf(text):text;}
    private TextView label(String text,float size,float height){TextView t=ui.label(text,12);QuickControlPreviewView.type(t,size,height,ui.unit,UiPalette.TEXT);return t;}
    private void identify(View v,String tag){v.setTag(tag);v.setId(androidx.core.view.ViewCompat.generateViewId());v.setFocusableInTouchMode(true);}
    private String name(String value){int i=Arrays.asList(values).indexOf(value);return i<0?value:names[i];}
    private int gridColumns(List<Button> buttons){
        if(getWidth()==0)return 2;
        int width=getWidth()-getPaddingLeft()-getPaddingRight()-4*p(4.8f),column=(width-p(8))/2;
        for(Button button:buttons)if(Math.ceil(android.text.Layout.getDesiredWidth(SettingsSelectionMark.label(button.getContentDescription().toString(),true,ui.unit),button.getPaint()))+button.getCompoundPaddingLeft()+button.getCompoundPaddingRight()>column)return 1;
        return 2;
    }
    private void render(){
        if(pendingReflow!=null)pendingReflow.removeListener();pendingReflow=UiMotion.reflow(this,()->pendingReflow=null);updating=true;boolean wasExpanded=expandedContent!=null;
        if(collapsing!=null){collapsing.finish();collapsing=null;}
        if(wasExpanded&&!opened&&!inline&&!optionsOnly)collapsing=UiMotion.collapse(expandedContent,()->collapsing=null);
        expandedContent=null;
        if(trigger!=null&&!inline&&!optionsOnly){for(int i=getChildCount()-1;i>=0;i--)if(getChildAt(i)!=trigger)removeViewAt(i);}else{trigger=null;removeAllViews();}
        rows.clear();setPadding(inline?p(16):0,inline?p(12):0,inline?p(16):0,inline?p(12):0);pendingLabel=null;
        if(inline){LinearLayout caption=ui.row();TextView heading=label(title,dialogStyle?18.56f:16.64f,dialogStyle?26.26f:23);QuickControlPreviewView.weight(heading,500);caption.addView(heading,new LayoutParams(0,-2,1));pendingLabel=label(draft.equals(committed)?"":"待确定",12.16f,18.24f);caption.addView(pendingLabel);LayoutParams cp=new LayoutParams(-1,p(dialogStyle?26.26f:23));cp.bottomMargin=p(8);addView(caption,cp);}
        else if(!optionsOnly){if(trigger==null){trigger=new DesktopSettingsRow(ui,title,"",false);trigger.setOnClickListener(v->{if(opened)cancel(true);else{opened=true;draft=committed;render();focusValue(committed);}});identify(trigger,"appearance-"+key);addView(trigger,new LayoutParams(-1,p(dialogStyle?59.95f:59.63f)));}int height=p(dialogStyle?59.95f:59.63f);if(trigger.getLayoutParams().height!=height){trigger.getLayoutParams().height=height;trigger.requestLayout();}trigger.displayStyle();if(dialogStyle)trigger.textSizes(14.016f,11.52f);trigger.hideIcon();trigger.value(name(committed));trigger.tail(opened?"settings-up":"arrow");rows.add(new View[]{trigger});}
        if(inline||opened||optionsOnly){LinearLayout expanded=ui.column();if(!inline&&!optionsOnly){expanded.setPadding(p(twoColumns?4.8f:8.32f),p(6.4f),p(twoColumns?4.8f:8.32f),p(8.32f));expanded.setBackgroundDrawable(ui.surface(0x1f000000,0x1f000000,0,15));}addView(expanded,new LayoutParams(-1,-2));LinearLayout options=new LinearLayout(ui);options.setOrientation(inline?HORIZONTAL:VERTICAL);options.setClipChildren(false);options.setClipToPadding(false);if(!inline&&!optionsOnly){ScrollView scroll=new ScrollView(ui){@Override protected void onMeasure(int width,int height){super.onMeasure(width,MeasureSpec.makeMeasureSpec(p(238.08f),MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setPadding(p(twoColumns?4.8f:25.6f),p(10.24f),p(twoColumns?4.8f:25.6f),p(10.24f));scroll.setClipToPadding(true);scroll.addView(options,new ScrollView.LayoutParams(-1,-2));expanded.addView(scroll,new LayoutParams(-1,-2));}else expanded.addView(options,new LayoutParams(-1,-2));ArrayList<View> segment=new ArrayList<>();
            ArrayList<Button> buttons=new ArrayList<>();
            for(int i=0;i<values.length;i++){String id=values[i];Button b=ui.button(names[i],()->choose(id));b.setText(SettingsSelectionMark.label(names[i],committed.equals(id),ui.unit));identify(b,"appearance-"+key+"-"+id);b.setContentDescription(names[i]);b.setSelected(committed.equals(id));b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(inline?(dialogStyle?12f:10.08f):11.52f)*ui.unit);b.setIncludeFontPadding(false);b.setPadding(p(inline?8:12.8f),p(inline?9.96f:8.96f),p(inline?8:12.8f),p(inline?9.96f:8.96f));if(inline||twoColumns)b.setSingleLine(true);b.setGravity(Gravity.CENTER);b.setBackgroundDrawable(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new SettingsFocusDrawable(ui.unit,12,committed.equals(id)?0x1cffffff:UiPalette.CONTROL,false,committed.equals(id)?0x4dffffff:UiPalette.EDGE,twoColumns&&committed.equals(id)),new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)}));if(solidSelection){android.graphics.drawable.StateListDrawable surface=new android.graphics.drawable.StateListDrawable();surface.addState(new int[]{android.R.attr.state_selected},ui.surface(UiPalette.TEXT,UiPalette.TEXT,0,12));surface.addState(new int[0],new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));b.setBackgroundDrawable(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{surface,new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)}));b.setPadding(p(inline?8:12.8f),p(9.96f),p(inline?8:12.8f),p(9.96f));b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected},{android.R.attr.state_focused,android.R.attr.state_window_focused},{}},new int[]{UiPalette.INK,UiPalette.INK,UiPalette.TEXT}));}else b.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));if("icon-shape".equals(key)){android.graphics.drawable.Drawable shape=new ShapeIcon(id);shape.setBounds(0,0,p("rectangle".equals(id)?48.64f:32),p(40.96f));b.setCompoundDrawables(null,shape,null,null);b.setCompoundDrawablePadding(p(7.04f));}b.setOnFocusChangeListener((v,f)->{if(f){draft=id;if(pendingLabel!=null)pendingLabel.setText(draft.equals(committed)?"":"待确定");preview.value(key,value(id));}});
                buttons.add(b);
            }
            int columns=twoColumns?gridColumns(buttons):1;LinearLayout gridRow=null;ArrayList<View> gridSegment=new ArrayList<>();
            for(int i=0;i<buttons.size();i++){Button b=buttons.get(i);
                if(twoColumns){if(i%columns==0){gridRow=ui.row();gridRow.setClipChildren(false);gridSegment.clear();LayoutParams rp=new LayoutParams(-1,-2);if(i>0)rp.topMargin=p(6.4f);options.addView(gridRow,rp);}b.setMinHeight(p(43.52f));b.setMinimumHeight(p(43.52f));LayoutParams bp=new LayoutParams(0,-1,1);if(i%columns>0)bp.leftMargin=p(8);gridRow.addView(b,bp);gridSegment.add(b);if(i%columns==columns-1||i==values.length-1){rows.add(gridSegment.toArray(new View[0]));if(columns>1&&i%columns==0){LayoutParams space=new LayoutParams(0,-1,1);space.leftMargin=p(8);gridRow.addView(new View(ui),space);}}}
                else{LayoutParams bp=new LayoutParams(inline?0:-1,p(inline?(dialogStyle?51.2f:"icon-shape".equals(key)?86.4f:43.52f):43.52f),inline?1:0);if(inline){if(i>0)bp.leftMargin=p(8);}else bp.setMargins(p(3.2f),p(3.2f),p(3.2f),p(3.2f));options.addView(b,bp);if(inline)segment.add(b);else rows.add(new View[]{b});}}
            if(inline)rows.add(segment.toArray(new View[0]));
            if(!inline&&!optionsOnly){expandedContent=expanded;if(!wasExpanded){expanded.setAlpha(0);UiMotion.reveal(expanded,-p(6));}}
        }
        available(available);updating=false;post(rewired);
    }
    private void choose(String id){
        if(saving)return;
        // Custom accessors can update Views or open another surface as part of saving.
        if(!asyncSave){try{access.save(id);chosen("");}catch(IOException error){chosen(error.getMessage());}return;}
        saving=true;ui.saveSetting(()->access.save(id),this::chosen);
    }
    private void chosen(String error){saving=false;if(error.isEmpty()){operationError="";committed=current();draft=committed;opened=false;preview.clear(key);boolean focused=hasFocus();render();if(focused&&isShown())focusValue(committed);}else{operationError=error;failure.failed(error);rewired.run();}}

    private void focusValue(String id){post(()->{String tag="appearance-"+key+(inline||opened||optionsOnly?"-"+id:"");View v=findViewWithTag(tag);if(v!=null){v.requestFocusFromTouch();Rect r=new Rect();v.getDrawingRect(r);v.requestRectangleOnScreen(r,false);}});}
    void focusTrigger(){focusValue(committed);}
    void available(boolean enabled){available=enabled;for(View[] row:rows)for(View view:row)view.setEnabled(enabled);setAlpha(ui.unifiedSettings()?1f:enabled?1f:.55f);}
    void refresh(){String actual=current();if(!actual.equals(committed)){committed=actual;if(trigger!=null&&!opened){draft=actual;trigger.value(name(actual));}else if(!hasFocus()){draft=actual;render();}}}
    void refreshConfirmed(){
        String actual=current();if(actual.equals(committed))return;View focused=findFocus();Object tag=focused==null?null:focused.getTag();committed=actual;draft=actual;render();
        if(tag!=null)post(()->{View next=findViewWithTag(tag);if(next!=null)next.requestFocusFromTouch();});
    }
    void cancel(boolean focus){if(updating)return;boolean wasOpen=opened;if(opened||!draft.equals(committed))operationError="";opened=false;draft=committed;preview.clear(key);if(pendingLabel!=null)pendingLabel.setText("");if(wasOpen)render();if(focus)focusValue(committed);}
    List<View[]> navigation(){
        ArrayList<View[]> active=new ArrayList<>();
        for(View[] row:rows){ArrayList<View> controls=new ArrayList<>();for(View view:row)if(view.getVisibility()==View.VISIBLE&&view.isFocusable()&&view.isEnabled())controls.add(view);if(!controls.isEmpty())active.add(controls.toArray(new View[0]));}
        return active;
    }
    DisplayAppearanceChoice errors(Failure handler){failure=handler;return this;}
    DisplayAppearanceChoice dialogStyle(){dialogStyle=true;render();return this;}
    DisplayAppearanceChoice optionsOnly(){optionsOnly=true;render();return this;}
    DisplayAppearanceChoice twoColumns(){twoColumns=true;render();return this;}
    DisplayAppearanceChoice solidSelection(){solidSelection=true;render();return this;}
    boolean isOpen(){return opened;}
    boolean drafting(){return !draft.equals(committed);}
    String error(){return operationError;}
    boolean back(){if(!optionsOnly&&(opened||!draft.equals(committed))){cancel(true);return true;}return false;}
    private final class ShapeIcon extends android.graphics.drawable.Drawable {
        final String shape;final Paint paint=new Paint(3);ShapeIcon(String shape){this.shape=shape;}@Override public boolean isStateful(){return true;}@Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
        @Override public void draw(Canvas c){Rect b=getBounds();RectF r=new RectF(b.left+.75f*ui.unit,b.top+p(4.48f),b.right-.75f*ui.unit,b.bottom-p(4.48f));boolean focused=false,enabled=false,window=false;for(int s:getState()){if(s==android.R.attr.state_focused)focused=true;if(s==android.R.attr.state_enabled)enabled=true;if(s==android.R.attr.state_window_focused)window=true;}focused&=enabled&&window;float radius="circle".equals(shape)?r.height()/2:p(SurfaceCorners.CONTROL);paint.setStyle(Paint.Style.FILL);paint.setColor(focused?0x33000000:0x21ffffff);SurfaceCorners.draw(c,r,radius,radius,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*ui.unit);paint.setColor(!enabled?UiPalette.FAINT:focused?UiPalette.INK:UiPalette.TEXT);SurfaceCorners.draw(c,r,radius,radius,paint);}
        @Override public void setAlpha(int value){}@Override public void setColorFilter(ColorFilter value){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
