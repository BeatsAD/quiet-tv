package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** One remote focus target per setting; child artwork is presentation only. */
final class DesktopSettingsRow extends LinearLayout {
    private final TextView title,value;
    private final ImageView end;
    private final ManagementUi ui;
    private boolean checkable,checked;
    DesktopSettingsRow(ManagementUi ui,String text,String symbol,boolean navigation){
        super(ui);this.ui=ui;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(ui.px(navigation?14:20),0,ui.px(navigation?14:20),0);
        setId(View.generateViewId());setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},ui.surface(0xff282b2f,0xff282b2f,0xfff2f3f4,9));
        background.addState(new int[]{android.R.attr.state_selected},ui.surface(0xff282b2f,0xff282b2f,0,9));
        background.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));setBackground(background);
        ImageView start=new ImageView(ui);start.setImageDrawable(new Glyph(symbol,0xffa7abb2));
        LayoutParams ip=new LayoutParams(ui.px(19),ui.px(19));ip.rightMargin=ui.px(13);addView(start,ip);
        title=ui.label(text,navigation?16:18);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(title,new LayoutParams(0,-1,1));
        value=ui.muted("",14);value.setSingleLine(true);value.setMaxWidth(ui.px(300));value.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(value,new LayoutParams(-2,-1));
        end=new ImageView(ui);LayoutParams ep=new LayoutParams(ui.px(18),ui.px(24));ep.leftMargin=ui.px(12);addView(end,ep);end.setVisibility(GONE);
        for(int i=0;i<getChildCount();i++)getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(text);
    }
    void value(String text){value.setText(text);setContentDescription(title.getText()+(text.isEmpty()?"":"  "+text));}
    String value(){return value.getText().toString();}
    void tail(String symbol){end.setImageDrawable(new Glyph(symbol,0xffa7abb2));end.setVisibility(VISIBLE);}
    void checked(boolean enabled){checkable=true;checked=enabled;end.setVisibility(VISIBLE);LayoutParams p=(LayoutParams)end.getLayoutParams();p.width=ui.px(44);end.setLayoutParams(p);end.setImageDrawable(new Toggle(enabled));}
    boolean checked(){return checked;}
    void available(boolean enabled){setEnabled(enabled);setAlpha(enabled?1f:.55f);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(checkable?Switch.class.getName():Button.class.getName());info.setCheckable(checkable);if(checkable)info.setChecked(checked);}
    private static final class Toggle extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean on;
        Toggle(boolean on){this.on=on;}
        @Override public void draw(Canvas c){Rect r=getBounds();int save=c.save();c.translate(r.left,r.top);c.scale(r.width()/44f,r.height()/24f);paint.setColor(on?0xffd9e5d5:0xff535860);c.drawRoundRect(new RectF(0,0,44,24),12,12,paint);paint.setColor(on?0xff252d24:0xffe1e3e5);c.drawCircle(on?32:12,12,9,paint);c.restoreToCount(save);}
        @Override public void setAlpha(int a){paint.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    /** Original static strokes also work on API 19 without vector inflation. */
    static final class Glyph extends Drawable {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final String kind;
        Glyph(String kind,int color){this.kind=kind;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
        private void line(Canvas c,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,p);}
        @Override public void draw(Canvas c){Rect r=getBounds();int save=c.save();c.translate(r.left,r.top);c.scale(r.width()/24f,r.height()/24f);
            switch(kind){
                case "desktop":c.drawRoundRect(new RectF(3,4,21,20),2,2,p);line(c,3,9,21,9);line(c,9,9,9,20);break;
                case "clock":c.drawCircle(12,12,9,p);line(c,12,6,12,12,16,14);break;
                case "tools":for(int i=0;i<3;i++){float y=6+i*6,x=i==1?15:8;line(c,3,y,21,y);line(c,x,y-2,x,y+2);}break;
                case "weather":c.drawCircle(8,8,4,p);line(c,8,1,8,2);line(c,1,8,2,8);line(c,3,3,4,4);line(c,13,3,14,2);Path cloud=new Path();cloud.moveTo(8,20);cloud.cubicTo(2,20,3,12,8,13);cloud.cubicTo(9,7,17,7,18,13);cloud.cubicTo(24,13,23,20,18,20);cloud.close();c.drawPath(cloud,p);break;
                case "locate":c.drawCircle(12,12,7,p);c.drawCircle(12,12,3,p);line(c,12,1,12,5);line(c,12,19,12,23);line(c,1,12,5,12);line(c,19,12,23,12);break;
                case "pin":c.drawCircle(12,9,2.5f,p);Path pin=new Path();pin.moveTo(12,22);pin.cubicTo(-5,6,8,-3,17,4);pin.cubicTo(25,10,15,19,12,22);c.drawPath(pin,p);break;
                case "refresh":c.drawArc(new RectF(4,4,20,20),45,290,false,p);line(c,20,3,20,9,14,9);break;
                case "shield":line(c,12,2,21,6,20,15,17,20,12,23,7,20,4,15,3,6,12,2);break;
                case "logs":c.drawRoundRect(new RectF(5,2,19,22),2,2,p);line(c,8,8,16,8);line(c,8,12,16,12);line(c,8,16,14,16);break;
                case "info":c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawPoint(12,7,p);break;
                case "arrow":line(c,9,5,16,12,9,19);break;
                default:break;
            }c.restoreToCount(save);}
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
