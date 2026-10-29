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
        return ControlStyle.action(ui.unit,radius/2,primary?0xff30343c:color,primary?0x25ffffff:border);
    }
    void choice(Button b,float radius){ControlStyle.choice(b,ui.unit,radius/2,0x04ffffff,0x1dffffff);}
    ImageView image(int resource,int color){ImageView image=new ImageView(ui);image.setImageDrawable(UiCompat.drawable(ui,resource));image.setColorFilter(color);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setFocusable(false);androidx.core.view.ViewCompat.setImportantForAccessibility(image,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
    Button button(String label,Runnable click,boolean primary){
        Button b=ui.button(label,click);b.setSingleLine(true);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,font(21.12f));b.setTypeface(Typeface.DEFAULT);b.setTextColor(ManagementUi.TEXT);b.setPadding(p(26.88f),0,p(26.88f),0);b.setBackgroundDrawable(focus(primary?0xffe7e9ec:0x06ffffff,primary?0xffe7e9ec:0x25ffffff,30,primary));return b;
    }
    ImageView appIcon(android.graphics.drawable.Drawable art,float size,float radius){ImageView image=new ImageView(ui){private final Path clip=new Path();private final RectF imageBounds=new RectF();@Override protected void onDraw(Canvas canvas){imageBounds.set(0,0,getWidth(),getHeight());clip.reset();clip.addRoundRect(imageBounds,p(radius),p(radius),Path.Direction.CW);int save=canvas.save();canvas.clipPath(clip);super.onDraw(canvas);canvas.restoreToCount(save);}};image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageDrawable(art);image.setBackgroundDrawable(shape(0x09ffffff,0,radius,1));return image;}
    void place(FrameLayout parent,View view,float x,float y,float w,float h){FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(p(w),p(h));lp.leftMargin=p(x);lp.topMargin=p(y);parent.addView(view,lp);}
    void gap(LinearLayout parent,float height){parent.addView(new View(ui),new LinearLayout.LayoutParams(1,p(height)));}
    EditText number(String value,String hint,int digits){
        EditText input=ui.input(value,hint);input.setId(androidx.core.view.ViewCompat.generateViewId());input.setContentDescription(hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(digits)});
        input.setGravity(Gravity.CENTER);input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,font(23.04f));input.setIncludeFontPadding(false);
        input.setMinWidth(0);input.setMinimumWidth(0);input.setMinHeight(0);input.setMinimumHeight(0);input.setPadding(p(10),0,p(10),0);
        input.setSelectAllOnFocus(true);ControlStyle.choice(input,ui.unit,5.76f,0x80080e15,0x25ffffff);
        if(android.os.Build.VERSION.SDK_INT>=21)input.setShowSoftInputOnFocus(false);return input;
    }
    private interface NumberKey {void enter(String value);}
    // Both duration dialogs use the same remote keypad; the selected field stays marked while typing.
    LinearLayout numberPad(Sheet sheet,EditText... inputs){
        LinearLayout pad=ui.column();pad.setClipChildren(false);Button[] keys=new Button[12];EditText[] target={inputs[0]};boolean[] replace={true};
        inputs[0].setSelected(true);
        NumberKey enter=value->{
            EditText input=target[0];String old=input.getText().toString();
            if(value.equals("清空"))input.setText("");
            else if(value.equals("退格"))input.setText(old.isEmpty()?"":old.substring(0,old.length()-1));
            else input.setText((replace[0]?"":old)+value);
            replace[0]=false;input.setSelection(input.length());
        };
        for(int row=0;row<4;row++){
            if(row>0)gap(pad,9.6f);LinearLayout line=ui.row();line.setClipChildren(false);pad.addView(line,new LinearLayout.LayoutParams(-1,p(40)));
            for(int col=0;col<3;col++){
                int index=row*3+col;String value=index<9?String.valueOf(index+1):index==9?"清空":index==10?"0":"退格";
                Button key=button(value,()->enter.enter(value),false);key.setId(androidx.core.view.ViewCompat.generateViewId());key.setContentDescription(value);
                key.setPadding(0,0,0,0);key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,font(19.2f));key.setBackgroundDrawable(focus(0x05ffffff,0x0effffff,9.6f,false));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(col>0)lp.leftMargin=p(9.6f);line.addView(key,lp);keys[index]=key;
            }
        }
        for(int i=0;i<inputs.length;i++){
            EditText input=inputs[i];int index=i;
            input.setOnFocusChangeListener((v,focused)->{if(focused){target[0]=input;replace[0]=true;for(EditText field:inputs)field.setSelected(field==input);}});
            input.setOnClickListener(v->{target[0]=input;replace[0]=true;for(EditText field:inputs)field.setSelected(field==input);keys[1].requestFocus();});
            input.setOnKeyListener((v,key,event)->{
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_DOWN)keys[1].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_UP)keys[1].requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP){if(event.getAction()==KeyEvent.ACTION_DOWN){View next=input.focusSearch(View.FOCUS_UP);if(next!=null)next.requestFocus();}return true;}
                if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT){if(event.getAction()==KeyEvent.ACTION_DOWN)inputs[Math.max(0,Math.min(inputs.length-1,index+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)))].requestFocus();return true;}return false;
            });
        }
        for(int i=0;i<keys.length;i++){
            final int index=i;keys[i].setOnKeyListener((v,key,event)->{
                if(key!=KeyEvent.KEYCODE_DPAD_UP && key!=KeyEvent.KEYCODE_DPAD_DOWN && key!=KeyEvent.KEYCODE_DPAD_LEFT && key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN){
                    if(key==KeyEvent.KEYCODE_DPAD_UP){if(index<3)target[0].requestFocus();else keys[index-3].requestFocus();}
                    else if(key==KeyEvent.KEYCODE_DPAD_DOWN){if(index<9)keys[index+3].requestFocus();else sheet.actions.getChildAt(index==9?0:sheet.actions.getChildCount()-1).requestFocus();}
                    else keys[index/3*3+Math.max(0,Math.min(2,index%3+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)))].requestFocus();
                }return true;
            });
        }
        sheet.dialog.setOnKeyListener((dialog,key,event)->{
            if(pad.getVisibility()!=View.VISIBLE)return false;
            int digit=key>=KeyEvent.KEYCODE_0 && key<=KeyEvent.KEYCODE_9?key-KeyEvent.KEYCODE_0:key>=KeyEvent.KEYCODE_NUMPAD_0 && key<=KeyEvent.KEYCODE_NUMPAD_9?key-KeyEvent.KEYCODE_NUMPAD_0:-1;
            if(digit<0 && key!=KeyEvent.KEYCODE_DEL)return false;
            if(event.getAction()==KeyEvent.ACTION_UP)enter.enter(digit<0?"退格":String.valueOf(digit));return true;
        });
        pad.setTag(keys);return pad;
    }
    void numberActions(Sheet sheet,LinearLayout pad){
        Button[] keys=(Button[])pad.getTag();for(int i=0;i<sheet.actions.getChildCount();i++){
            View action=sheet.actions.getChildAt(i);if(action.getId()==View.NO_ID)action.setId(androidx.core.view.ViewCompat.generateViewId());action.setNextFocusUpId(keys[i==0?9:11].getId());
        }
    }
    final class Sheet {
        final Dialog dialog=new KeyFeedback.Dialog(ui);
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
            float padding=width>700?51.84f:57.6f,vertical=width>700?51.84f:40.32f;box.setPadding(p(padding+1),p(vertical+1),p(padding+1),p(vertical+1));box.setBackgroundDrawable(ui.surface(0xff292c33,0xff1b1e24,0x38ffffff,14.4f));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(p(width),-2,Gravity.CENTER);screen.addView(box,bp);
            ImageView icon=image(emblem,ManagementUi.MUTED);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(p(48),p(48));ip.gravity=Gravity.CENTER_HORIZONTAL;box.addView(icon,ip);gap(box,23.2f);
            TextView heading=text(title,36.48f,51,ManagementUi.TEXT);heading.setGravity(Gravity.CENTER);UiCompat.letterSpacing(heading,.04f);box.addView(heading);
            if(!description.isEmpty()){gap(box,15.36f);TextView sub=text(description,18.24f,31,ManagementUi.MUTED);sub.setGravity(Gravity.CENTER);box.addView(sub);}
            box.addView(body,new LinearLayout.LayoutParams(-1,-2));gap(box,width>700?38.4f:15.36f);actions.setGravity(Gravity.CENTER);box.addView(actions,new LinearLayout.LayoutParams(-1,p(64.32f)));
            FrameLayout outer=new FrameLayout(ui);outer.addView(screen,new FrameLayout.LayoutParams(-1,-1));
            Button close=ui.iconButton("关闭弹窗",R.drawable.ic_child_close,()->{dialog.dismiss();KeyFeedback.back(ui);});close.setBackgroundDrawable(focus(0,0,22,false));FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(p(42.24f),p(42.24f),Gravity.TOP|Gravity.RIGHT);cp.topMargin=p(20.2f);cp.rightMargin=p(20.2f);FrameLayout closeLayer=new FrameLayout(ui);closeLayer.setClipChildren(false);closeLayer.addView(close,cp);screen.addView(closeLayer,new FrameLayout.LayoutParams(p(width),p(80),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
            // The close control follows the centered panel, including its variable height.
            box.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)closeLayer.getLayoutParams();if(lp.topMargin!=t){lp.topMargin=t;closeLayer.setLayoutParams(lp);}});
            dialog.setContentView(outer);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        Button action(String label,Runnable click,boolean primary){Button b=button(label,click,primary);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(actions.getChildCount()>0)p.leftMargin=ChildModeUi.this.p(15.36f);actions.addView(b,p);return b;}
        void show(View focus){dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().getDecorView().setSystemUiVisibility(5894);if(focus!=null)focus.requestFocus();}
        void release(){if(backdrop!=null){backdrop.recycle();backdrop=null;}}
    }
}
