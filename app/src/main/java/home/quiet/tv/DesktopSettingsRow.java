package home.quiet.tv;

import android.animation.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.TypedValue;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** One remote focus target per setting; child artwork is presentation only. */
final class DesktopSettingsRow extends LinearLayout {
    private final TextView title,value,description;
    private final ImageView start,end;
    private final ManagementUi ui;
    private boolean checkable,checked,childStyle,fullColorIcon;
    private Toggle toggle;
    private ValueAnimator chevron;
    private String tailGlyph="";
    private float tailRotation;
    DesktopSettingsRow(ManagementUi ui,String text,String symbol,boolean navigation){
        super(ui);this.ui=ui;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setClipToPadding(false);
        setPadding(ui.px(ui.unifiedSettings()?9.6f:navigation?14:18),0,ui.px(ui.unifiedSettings()?9.6f:navigation?14:18),0);
        setId(androidx.core.view.ViewCompat.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        setSoundEffectsEnabled(false);setBackgroundDrawable(ui.listSurface());
        if(ui.unifiedSettings())SettingsTouchClick.attach(this);
        start=new ImageView(ui);int resource="shield".equals(symbol)?R.drawable.ic_manage_shield:0;
        if(resource!=0){start.setImageDrawable(UiCompat.drawable(ui,resource));start.setColorFilter(UiPalette.SECONDARY);}else start.setImageDrawable(new Glyph(symbol,UiPalette.SECONDARY));
        LayoutParams ip=new LayoutParams(ui.px(navigation?17:18),ui.px(navigation?17:18));ip.rightMargin=ui.px(13);addView(start,ip);
        LinearLayout copy=new LinearLayout(ui);copy.setOrientation(VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);addView(copy,new LayoutParams(0,-1,1));
        title=ui.label(text,ui.unifiedSettings()?13:16);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title,new LayoutParams(-1,-2));
        if(ui.unifiedSettings())title.setTextSize(TypedValue.COMPLEX_UNIT_PX,13.92f*ui.unit);
        description=ui.muted("",12);description.setMaxLines(2);description.setEllipsize(android.text.TextUtils.TruncateAt.END);description.setVisibility(GONE);
        LayoutParams dp=new LayoutParams(-1,-2);dp.topMargin=ui.px(4);copy.addView(description,dp);
        value=ui.muted("",ui.unifiedSettings()?11:13);value.setTextColor(UiPalette.SECONDARY);value.setSingleLine(true);value.setMaxWidth(ui.px(300));value.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(value,new LayoutParams(-2,-1));
        if(ui.unifiedSettings()){value.setTextSize(TypedValue.COMPLEX_UNIT_PX,11.52f*ui.unit);value.setTextColor(UiPalette.SECONDARY);}
        end=new ImageView(ui);LayoutParams ep=new LayoutParams(ui.px(ui.unifiedSettings()?9.6f:18),ui.px(ui.unifiedSettings()?18:24));ep.leftMargin=ui.px(ui.unifiedSettings()?9:12);addView(end,ep);end.setVisibility(GONE);
        for(int i=0;i<getChildCount();i++)androidx.core.view.ViewCompat.setImportantForAccessibility(getChildAt(i),androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        androidx.core.view.ViewCompat.setImportantForAccessibility(copy,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setContentDescription(text);
    }
    @Override public boolean performClick(){boolean accepted=super.performClick();if(accepted)KeyFeedback.confirm(getContext());return accepted;}
    @Override public void setPressed(boolean pressed){boolean changed=pressed!=isPressed();super.setPressed(pressed);if(changed&&ui!=null&&ui.unifiedSettings())SettingsFocusMotion.update(this,isFocused()&&hasWindowFocus());}
    void icon(int resource){start.setImageDrawable(UiCompat.drawable(ui,resource));start.clearColorFilter();}
    void icon(Drawable drawable){start.setImageDrawable(drawable);start.clearColorFilter();}
    void brandIcon(int resource){fullColorIcon=true;icon(resource);start.setVisibility(VISIBLE);LayoutParams size=(LayoutParams)start.getLayoutParams();size.width=size.height=ui.px(24);size.rightMargin=ui.px(12);start.setLayoutParams(size);}
    void value(String text){value.setText(text);updateDescription();}
    String value(){return value.getText().toString();}
    void hideIcon(){start.setVisibility(GONE);}
    void hideTail(){end.setVisibility(GONE);}
    void childStyle(){childStyle=true;textSizes(12.48f,10.56f);LayoutParams icon=(LayoutParams)start.getLayoutParams();icon.width=icon.height=ui.px(19.2f);icon.rightMargin=ui.px(12);start.setLayoutParams(icon);if(checkable)checked(checked);}
    void compact(){start.setVisibility(GONE);title.setTextSize(TypedValue.COMPLEX_UNIT_PX,ui.px(14));value.setMaxWidth(ui.px(90));setPadding(ui.px(10),0,ui.px(10),0);}
    void displayStyle(){title.setTextSize(TypedValue.COMPLEX_UNIT_PX,12.48f*ui.unit);title.setIncludeFontPadding(true);value.setTextSize(TypedValue.COMPLEX_UNIT_PX,10.56f*ui.unit);value.setIncludeFontPadding(true);value.setMaxWidth(ui.px(126));}
    void tileStyle(){hideIcon();displayStyle();title.setSingleLine(false);title.setEllipsize(null);setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));}
    void compactTile(){tileStyle();setPadding(ui.px(9.6f),0,ui.px(9.6f),0);((LayoutParams)end.getLayoutParams()).leftMargin=ui.px(8);if(ui.chinese())title.setSingleLine(true);}
    void description(String text){description.setText(text);description.setVisibility(text.isEmpty()?GONE:VISIBLE);title.setTextSize(TypedValue.COMPLEX_UNIT_PX,ui.px(ui.unifiedSettings()?(text.isEmpty()?13:14):(text.isEmpty()?16:17)));updateDescription();}
    private void updateDescription(){setContentDescription(title.getText()+(value.length()==0?"":"  "+value.getText())+(description.length()==0?"":"  "+description.getText()));}
    void tail(String symbol){
        String glyph="settings-up".equals(symbol)?"chevron-up":"down".equals(symbol)?"chevron-down":("arrow".equals(symbol)||"settings-arrow".equals(symbol))?"chevron-right":null;
        String kind=glyph==null?symbol:glyph;boolean shown=end.getVisibility()==VISIBLE;
        if(!kind.equals(tailGlyph)){
            float previous=tailRotation+end.getRotation(),target="chevron-up".equals(glyph)?-90:"chevron-down".equals(glyph)?90:0;
            boolean animate=shown&&tailGlyph.startsWith("chevron-")&&glyph!=null&&UiMotion.enabled(this);
            if(chevron!=null)chevron.cancel();tailGlyph=kind;tailRotation=target;
            if(glyph==null)end.setImageDrawable(new Glyph(symbol,UiPalette.FAINT));else{QuickControlIcon icon=new QuickControlIcon(glyph);icon.color(UiPalette.FAINT);end.setImageDrawable(icon);}
            if(animate){float offset=previous-target;end.setRotation(offset);chevron=ValueAnimator.ofFloat(offset,0);chevron.setDuration(160);chevron.setInterpolator(UiMotion.curve());chevron.addUpdateListener(a->end.setRotation((float)a.getAnimatedValue()));chevron.start();}else end.setRotation(0);
        }
        LayoutParams size=(LayoutParams)end.getLayoutParams();int side=ui.px(ui.unifiedSettings()?12:18);if(size.width!=side||size.height!=side){size.width=size.height=side;end.setLayoutParams(size);}end.setVisibility(VISIBLE);
    }
    void checked(boolean enabled){
        boolean changed=checkable&&checked!=enabled;checkable=true;checked=enabled;end.setVisibility(VISIBLE);LayoutParams p=(LayoutParams)end.getLayoutParams();int width=ui.px(childStyle?36.48f:ui.unifiedSettings()?33:44),height=ui.px(childStyle?20.16f:ui.unifiedSettings()?18:24);if(p.width!=width||p.height!=height){p.width=width;p.height=height;end.setLayoutParams(p);}
        boolean focused=ui.unifiedSettings()&&isFocused()&&hasWindowFocus();
        if(toggle==null||toggle.settings!=ui.unifiedSettings()||toggle.child!=childStyle){if(toggle!=null)toggle.finish();toggle=new Toggle(enabled,focused,ui.unifiedSettings(),childStyle,isEnabled());end.setImageDrawable(toggle);}else toggle.update(enabled,focused,isEnabled(),changed&&UiMotion.enabled(this));
        if(ui.unifiedSettings()&&getLayoutParams()!=null){int rowHeight=ui.px(childStyle?44.16f:42);if(getLayoutParams().height!=rowHeight){getLayoutParams().height=rowHeight;requestLayout();}}
    }
    boolean checkedControl(){return checkable;}
    boolean checked(){return checked;}
    @Override protected void drawableStateChanged(){
        super.drawableStateChanged();
        if(ui==null||!ui.unifiedSettings()||title==null)return;
        boolean focused=isEnabled()&&isFocused()&&hasWindowFocus();
        title.setTextColor(!isEnabled()?UiPalette.FAINT:focused?UiPalette.INK:UiPalette.TEXT);value.setTextColor(!isEnabled()?UiPalette.FAINT:focused?UiPalette.INK_SECONDARY:UiPalette.SECONDARY);description.setTextColor(!isEnabled()?UiPalette.FAINT:focused?UiPalette.INK_SECONDARY:UiPalette.SECONDARY);
        if(fullColorIcon)start.clearColorFilter();else if(!isEnabled())start.setColorFilter(UiPalette.FAINT);else if(focused)start.setColorFilter(UiPalette.INK);else start.clearColorFilter();
        if(checkable){end.clearColorFilter();if(toggle!=null)toggle.update(checked,focused,isEnabled(),false);}else if(!isEnabled())end.setColorFilter(UiPalette.FAINT);else if(focused)end.setColorFilter(UiPalette.INK_FAINT);else end.clearColorFilter();
    }
    private void finishMotion(){if(toggle!=null)toggle.finish();if(chevron!=null){chevron.cancel();chevron=null;}if(end!=null)end.setRotation(0);}
    @Override protected void onDetachedFromWindow(){finishMotion();super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)finishMotion();}
    void displayDescription(String text){description(text);displayStyle();if(!text.isEmpty()){QuickControlPreviewView.type(description,13.44f,20.832f,ui.unit,isFocused()&&hasWindowFocus()?UiPalette.INK_SECONDARY:UiPalette.SECONDARY);((LayoutParams)description.getLayoutParams()).topMargin=ui.px(6);if(getLayoutParams()!=null)getLayoutParams().height=ui.px(57.12f);}}
    void textSizes(float titleSize,float valueSize){title.setTextSize(TypedValue.COMPLEX_UNIT_PX,titleSize*ui.unit);value.setTextSize(TypedValue.COMPLEX_UNIT_PX,valueSize*ui.unit);}
    void available(boolean enabled){setEnabled(enabled);setAlpha(ui.unifiedSettings()?1f:enabled?1f:.55f);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(checkable?Switch.class.getName():Button.class.getName());info.setCheckable(checkable);if(checkable)info.setChecked(checked);}
    private static final class Toggle extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF track=new RectF();final boolean settings,child;
        boolean on,focused,enabled;float progress;ValueAnimator animation;
        Toggle(boolean on,boolean focused,boolean settings,boolean child,boolean enabled){this.on=on;this.focused=focused;this.settings=settings;this.child=child;this.enabled=enabled;progress=on?1:0;}
        void update(boolean on,boolean focused,boolean enabled,boolean animate){
            this.focused=focused;this.enabled=enabled;
            if(this.on!=on){this.on=on;if(animation!=null)animation.cancel();if(animate&&enabled&&!SettingsFocusMotion.reduced()){animation=ValueAnimator.ofFloat(progress,on?1:0);animation.setDuration(160);animation.setInterpolator(UiMotion.curve());animation.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidateSelf();});animation.start();}else finish();}
            if(!enabled)finish();invalidateSelf();
        }
        void finish(){if(animation!=null){animation.cancel();animation=null;}progress=on?1:0;invalidateSelf();}
        private int blend(int off,int on){float t=progress;return Color.argb(Math.round(Color.alpha(off)+(Color.alpha(on)-Color.alpha(off))*t),Math.round(Color.red(off)+(Color.red(on)-Color.red(off))*t),Math.round(Color.green(off)+(Color.green(on)-Color.green(off))*t),Math.round(Color.blue(off)+(Color.blue(on)-Color.blue(off))*t));}
        @Override public void draw(Canvas c){
            Rect r=getBounds();int save=c.save();c.translate(r.left,r.top);
            if(settings){
                float width=child?48.64f:44,height=child?26.88f:24,x=child?13.1467f+progress*19.84f:12+progress*20,y=child?13.1467f:12,radius=child?8.64f:9;
                c.scale(r.width()/width,r.height()/height);paint.setColor(blend(focused?0x26000000:0x26ffffff,UiPalette.SWITCH_ON));track.set(0,0,width,height);c.drawRoundRect(track,height/2,height/2,paint);
                paint.setColor(enabled?Color.WHITE:UiPalette.FAINT);paint.setShadowLayer(2,0,1,0x33000000);c.drawCircle(x,y,radius,paint);paint.clearShadowLayer();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.5f);paint.setColor(0x14000000);c.drawCircle(x,y,radius-.25f,paint);paint.setStyle(Paint.Style.FILL);
            }else if(child){
                c.scale(r.width()/48.64f,r.height()/26.88f);paint.setColor(blend(0x10ffffff,UiPalette.SECONDARY));track.set(0,0,48.64f,26.88f);c.drawRoundRect(track,20,20,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(blend(0x26ffffff,UiPalette.SECONDARY));track.set(.5f,.5f,48.14f,26.38f);c.drawRoundRect(track,20,20,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(blend(UiPalette.FAINT,0xff000000));c.drawCircle(13.1467f+progress*19.84f,13.1467f,8.64f,paint);
            }else{
                c.scale(r.width()/44f,r.height()/24f);paint.setColor(blend(UiPalette.INK_SECONDARY,UiPalette.TEXT));track.set(0,0,44,24);c.drawRoundRect(track,12,12,paint);paint.setColor(blend(UiPalette.TEXT,0xff000000));c.drawCircle(12+progress*20,12,9,paint);
            }c.restoreToCount(save);
        }
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** Original static strokes also work on API 19 without vector inflation. */
    static final class Glyph extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final String kind;
        Glyph(String kind,int color){this.kind=kind;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        private void line(Canvas c,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,p);}
        @Override public void draw(Canvas c){Rect r=getBounds();int save=c.save();if("settings-arrow".equals(kind)){c.translate(r.left,r.top);c.scale(r.width()/24f,r.height()/24f);line(c,5,6,19,12,5,18);c.restoreToCount(save);return;}float size=Math.min(r.width(),r.height());c.translate(r.left+(r.width()-size)/2f,r.top+(r.height()-size)/2f);c.scale(size/24f,size/24f);
            switch(kind){
                case "sound":line(c,3,9,7,9,12,5,12,19,7,15,3,15,3,9);Path waves=new Path();waves.addArc(new RectF(9,7,19,17),-60,120);waves.addArc(new RectF(6,3,22,21),-55,110);c.drawPath(waves,p);break;
                case "desktop":c.drawRoundRect(new RectF(3,4,21,20),2,2,p);line(c,3,9,21,9);line(c,9,9,9,20);break;
                case "clock":c.drawCircle(12,12,9,p);line(c,12,6,12,12,16,14);break;
                case "tools":for(int i=0;i<3;i++){float y=6+i*6,x=i==1?15:8;line(c,3,y,21,y);line(c,x,y-2,x,y+2);}break;
                case "weather":c.drawCircle(8,8,4,p);line(c,8,1,8,2);line(c,1,8,2,8);line(c,3,3,4,4);line(c,13,3,14,2);Path cloud=new Path();cloud.moveTo(8,20);cloud.cubicTo(2,20,3,12,8,13);cloud.cubicTo(9,7,17,7,18,13);cloud.cubicTo(24,13,23,20,18,20);cloud.close();c.drawPath(cloud,p);break;
                case "locate":c.drawCircle(12,12,7,p);c.drawCircle(12,12,3,p);line(c,12,1,12,5);line(c,12,19,12,23);line(c,1,12,5,12);line(c,19,12,23,12);break;
                case "pin":c.drawCircle(12,9,2.5f,p);Path pin=new Path();pin.moveTo(12,22);pin.cubicTo(10,20,4,14,4,9);pin.cubicTo(4,4.58f,7.58f,1,12,1);pin.cubicTo(16.42f,1,20,4.58f,20,9);pin.cubicTo(20,14,14,20,12,22);pin.close();c.drawPath(pin,p);break;
                case "refresh":Path refresh=new Path();refresh.addArc(new RectF(4,4,20,20),45,270);refresh.lineTo(20,9);c.drawPath(refresh,p);line(c,20,3,20,9,14,9);break;
                case "shield":line(c,12,2,21,6,20,15,17,20,12,23,7,20,4,15,3,6,12,2);break;
                case "logs":c.drawRoundRect(new RectF(5,2,19,22),2,2,p);line(c,8,8,16,8);line(c,8,12,16,12);line(c,8,16,14,16);break;
                case "info":c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawPoint(12,7,p);break;
                case "arrow":line(c,9,5,16,12,9,19);break;
                case "down":line(c,5,9,12,16,19,9);break;
                default:break;
            }c.restoreToCount(save);}
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
