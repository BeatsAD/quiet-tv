package home.quiet.tv;

import android.app.Dialog;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Child-mode measurements mirror child-lock.css on a 1920 x 1080 artboard. */
final class ChildModeUi {
    final ManagementUi ui;
    ChildModeUi(ManagementUi ui){this.ui=ui;}
    int p(float art){return ui.px(art/2);}
    float font(float art){return art*ui.unit/2;}
    TextView text(String value,float size,float height,int color){
        TextView text=new TextView(ui);text.setText(value);text.setTextColor(color);text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,font(size));text.setTypeface(Typeface.DEFAULT);text.setIncludeFontPadding(false);text.setGravity(Gravity.CENTER_VERTICAL);text.setLayoutParams(new LinearLayout.LayoutParams(-1,p(height)));return text;
    }
    GradientDrawable shape(int color,int border,float radius,float stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(p(radius));d.setStroke(Math.max(1,p(stroke)),border);return d;}
    Drawable focus(int color,int border,float radius,boolean primary){
        StateListDrawable d=new StateListDrawable();Drawable focused=ui.surface(0x24ffffff,0x16ffffff,0xfff3f4f7,radius/2);
        if(primary){
            // Keep the white focus outline and its separating gap inside the control bounds.
            LayerDrawable layers=new LayerDrawable(new Drawable[]{shape(0,0xfff3f4f7,radius,2),shape(0xffe7e9ec,0,radius-4,0)});
            layers.setLayerInset(1,p(4),p(4),p(4),p(4));focused=layers;
        }
        d.addState(new int[]{android.R.attr.state_focused},focused);d.addState(new int[]{android.R.attr.state_selected},shape(0x0cffffff,0xffdbe1e9,radius,1));d.addState(new int[]{},shape(color,border,radius,1));return d;
    }
    ImageView image(int resource,int color){ImageView image=new ImageView(ui);image.setImageDrawable(UiCompat.drawable(ui,resource));image.setColorFilter(color);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setFocusable(false);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
    Button button(String label,Runnable click,boolean primary){
        Button b=ui.button(label,click);b.setSingleLine(true);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,font(21.12f));b.setTypeface(Typeface.DEFAULT);b.setTextColor(primary?0xff191d23:ManagementUi.TEXT);b.setPadding(p(26.88f),0,p(26.88f),0);b.setBackground(focus(primary?0xffe7e9ec:0x06ffffff,primary?0xffe7e9ec:0x25ffffff,30,primary));return b;
    }
    ImageView appIcon(android.graphics.drawable.Drawable art,float size,float radius){ImageView image=new ImageView(ui){private final Path clip=new Path();private final RectF imageBounds=new RectF();@Override protected void onDraw(Canvas canvas){imageBounds.set(0,0,getWidth(),getHeight());clip.reset();clip.addRoundRect(imageBounds,p(radius),p(radius),Path.Direction.CW);int save=canvas.save();canvas.clipPath(clip);super.onDraw(canvas);canvas.restoreToCount(save);}};image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageDrawable(art);image.setBackground(shape(0x09ffffff,0,radius,1));return image;}
    void place(FrameLayout parent,View view,float x,float y,float w,float h){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(p(w),p(h));lp.leftMargin=p(x);lp.topMargin=p(y);parent.addView(view,lp);}
    void gap(LinearLayout parent,float height){parent.addView(new View(ui),new LinearLayout.LayoutParams(1,p(height)));}
    final class Sheet {
        final Dialog dialog=new Dialog(ui);
        final LinearLayout box=ui.column(),body=ui.column(),actions=ui.row();
        private final float width;
        private Bitmap backdrop;
        Sheet(String title,String description,int emblem,float width){
            this.width=width;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            box.setClipChildren(false);body.setClipChildren(false);actions.setClipChildren(false);FrameLayout screen=new FrameLayout(ui);screen.setClipChildren(false);screen.setBackgroundColor(0xff0e0f13);
            if(ui.stage.getWidth()>0 && ui.stage.getHeight()>0){
                backdrop=Bitmap.createBitmap(Math.max(1,ui.stage.getWidth()/16),Math.max(1,ui.stage.getHeight()/16),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(backdrop);c.scale(backdrop.getWidth()/(float)ui.stage.getWidth(),backdrop.getHeight()/(float)ui.stage.getHeight());ui.stage.draw(c);ImageView blurred=new ImageView(ui);blurred.setImageBitmap(backdrop);blurred.setScaleType(ImageView.ScaleType.FIT_XY);screen.addView(blurred,new FrameLayout.LayoutParams(-1,-1));
            }
            View dim=new View(ui);dim.setBackgroundColor(0xa6000000);screen.addView(dim,new FrameLayout.LayoutParams(-1,-1));
            float padding=width>700?51.84f:57.6f,vertical=width>700?51.84f:40.32f;box.setPadding(p(padding+1),p(vertical+1),p(padding+1),p(vertical+1));box.setBackground(ui.surface(0xff292c33,0xff1b1e24,0x38ffffff,14.4f));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(p(width),-2,Gravity.CENTER);screen.addView(box,bp);
            ImageView icon=image(emblem,ManagementUi.MUTED);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(p(48),p(48));ip.gravity=Gravity.CENTER_HORIZONTAL;box.addView(icon,ip);gap(box,23.2f);
            TextView heading=text(title,36.48f,51,ManagementUi.TEXT);heading.setGravity(Gravity.CENTER);UiCompat.letterSpacing(heading,.04f);box.addView(heading);
            if(!description.isEmpty()){gap(box,15.36f);TextView sub=text(description,18.24f,31,ManagementUi.MUTED);sub.setGravity(Gravity.CENTER);box.addView(sub);}
            box.addView(body,new LinearLayout.LayoutParams(-1,-2));gap(box,width>700?38.4f:15.36f);actions.setGravity(Gravity.CENTER);box.addView(actions,new LinearLayout.LayoutParams(-1,p(64.32f)));
            FrameLayout outer=new FrameLayout(ui);outer.addView(screen,new FrameLayout.LayoutParams(-1,-1));
            Button close=ui.iconButton("关闭弹窗",R.drawable.ic_child_close,dialog::dismiss);close.setBackground(focus(0,0,22,false));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(p(42.24f),p(42.24f),Gravity.TOP|Gravity.RIGHT);cp.topMargin=p(20.2f);cp.rightMargin=p(20.2f);FrameLayout closeLayer=new FrameLayout(ui);closeLayer.setClipChildren(false);closeLayer.addView(close,cp);screen.addView(closeLayer,new FrameLayout.LayoutParams(p(width),p(80),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
            // The close control follows the centered panel, including its variable height.
            box.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)closeLayer.getLayoutParams();if(lp.topMargin!=t){lp.topMargin=t;closeLayer.setLayoutParams(lp);}});
            dialog.setContentView(outer);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        Button action(String label,Runnable click,boolean primary){Button b=button(label,click,primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(actions.getChildCount()>0)p.leftMargin=ChildModeUi.this.p(15.36f);actions.addView(b,p);return b;}
        void show(View focus){dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);if(focus!=null)focus.requestFocus();}
        void release(){if(backdrop!=null){backdrop.recycle();backdrop=null;}}
    }
}
