package home.quiet.tv;

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
    private boolean checkable,checked;
    DesktopSettingsRow(ManagementUi ui,String text,String symbol,boolean navigation){
        super(ui);this.ui=ui;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(ui.px(navigation?14:18),0,ui.px(navigation?14:18),0);
        setId(androidx.core.view.ViewCompat.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        setSoundEffectsEnabled(false);setBackgroundDrawable(ui.listSurface());
        start=new ImageView(ui);int resource="shield".equals(symbol)?R.drawable.ic_manage_shield:0;
        if(resource!=0){start.setImageDrawable(UiCompat.drawable(ui,resource));start.setColorFilter(0xffa2a8b3);}else start.setImageDrawable(new Glyph(symbol,0xffa2a8b3));
        LayoutParams ip=new LayoutParams(ui.px(navigation?17:18),ui.px(navigation?17:18));ip.rightMargin=ui.px(13);addView(start,ip);
        LinearLayout copy=new LinearLayout(ui);copy.setOrientation(VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);addView(copy,new LayoutParams(0,-1,1));
        title=ui.label(text,16);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(title,new LayoutParams(-1,-2));
        description=ui.muted("",12);description.setMaxLines(2);description.setEllipsize(android.text.TextUtils.TruncateAt.END);description.setVisibility(GONE);
        LayoutParams dp=new LayoutParams(-1,-2);dp.topMargin=ui.px(4);copy.addView(description,dp);
        value=ui.muted("",13);value.setTextColor(0xffa2a8b3);value.setSingleLine(true);value.setMaxWidth(ui.px(300));value.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(value,new LayoutParams(-2,-1));
        end=new ImageView(ui);LayoutParams ep=new LayoutParams(ui.px(18),ui.px(24));ep.leftMargin=ui.px(12);addView(end,ep);end.setVisibility(GONE);
        for(int i=0;i<getChildCount();i++)androidx.core.view.ViewCompat.setImportantForAccessibility(getChildAt(i),androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        androidx.core.view.ViewCompat.setImportantForAccessibility(copy,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setContentDescription(text);
    }
    @Override public boolean performClick(){boolean accepted=super.performClick();if(accepted)KeyFeedback.confirm(getContext());return accepted;}
    void icon(int resource){start.setImageDrawable(UiCompat.drawable(ui,resource));start.clearColorFilter();}
    void value(String text){value.setText(text);updateDescription();}
    String value(){return value.getText().toString();}
    void compact(){start.setVisibility(GONE);title.setTextSize(TypedValue.COMPLEX_UNIT_PX,ui.px(14));value.setMaxWidth(ui.px(90));setPadding(ui.px(10),0,ui.px(10),0);}
    void description(String text){description.setText(text);description.setVisibility(text.isEmpty()?GONE:VISIBLE);title.setTextSize(TypedValue.COMPLEX_UNIT_PX,ui.px(text.isEmpty()?16:17));updateDescription();}
    private void updateDescription(){setContentDescription(title.getText()+(value.length()==0?"":"  "+value.getText())+(description.length()==0?"":"  "+description.getText()));}
    void tail(String symbol){end.setImageDrawable(new Glyph(symbol,0xffa2a8b3));end.setVisibility(VISIBLE);}
    void checked(boolean enabled){checkable=true;checked=enabled;end.setVisibility(VISIBLE);LayoutParams p=(LayoutParams)end.getLayoutParams();p.width=ui.px(44);end.setLayoutParams(p);end.setImageDrawable(new Toggle(enabled));}
    boolean checked(){return checked;}
    void available(boolean enabled){setEnabled(enabled);setAlpha(enabled?1f:.55f);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(checkable?Switch.class.getName():Button.class.getName());info.setCheckable(checkable);if(checkable)info.setChecked(checked);}
    private static final class Toggle extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean on;
        Toggle(boolean on){this.on=on;}
        @Override public void draw(Canvas c){Rect r=getBounds();int save=c.save();c.translate(r.left,r.top);c.scale(r.width()/44f,r.height()/24f);paint.setColor(on?0xffe1e6ed:0xff535860);c.drawRoundRect(new RectF(0,0,44,24),12,12,paint);paint.setColor(on?0xff26303b:0xffe1e3e5);c.drawCircle(on?32:12,12,9,paint);c.restoreToCount(save);}
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** Original static strokes also work on API 19 without vector inflation. */
    static final class Glyph extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final String kind;
        Glyph(String kind,int color){this.kind=kind;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        private void line(Canvas c,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,p);}
        @Override public void draw(Canvas c){Rect r=getBounds();int save=c.save();float size=Math.min(r.width(),r.height());c.translate(r.left+(r.width()-size)/2f,r.top+(r.height()-size)/2f);c.scale(size/24f,size/24f);
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
