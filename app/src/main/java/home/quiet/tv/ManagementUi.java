package home.quiet.tv;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Native management surfaces share the launcher's quiet glass and white focus outline. */
abstract class ManagementUi extends androidx.activity.ComponentActivity {
    static final int TEXT=0xffeff0f4,MUTED=0xff9298a4;
    LinearLayout root,header;
    FrameLayout stage;
    float unit;
    int px(float value){return Math.round(value*unit);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);BackNavigation.install(this,this::handleBack);unit=getResources().getDisplayMetrics().widthPixels/960f;
        getWindow().getDecorView().setSystemUiVisibility(5894);
        stage=new FrameLayout(this);stage.setBackgroundColor(0xff0e0f13);setContentView(stage);
        android.content.SharedPreferences prefs=getSharedPreferences("wallpapers",0);
        if(!prefs.getBoolean("default",!prefs.contains("index"))){
            String[] files={"big-sur-coastline","big-sur-mountains","big-sur-road"};
            int index=prefs.getInt("index",0);
            if(index>=0 && index<files.length)try(java.io.InputStream input=getAssets().open("wallpapers/"+files[index]+".jpg")){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;
                ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(BitmapFactory.decodeStream(input,null,options));stage.addView(image,new FrameLayout.LayoutParams(-1,-1));
            }catch(java.io.IOException ignored){}
        }
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xf00c0d11,0xf50b0c10}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        root=column();root.setPadding(px(48),px(28),px(48),px(16));stage.addView(root,new FrameLayout.LayoutParams(-1,-1));
        header=row();root.addView(header,new LinearLayout.LayoutParams(-1,px(38)));
        Button back=iconButton("返回",R.drawable.ic_manage_back,this::finish);header.addView(back,new LinearLayout.LayoutParams(px(34),px(34)));
        TextView title=label("应用管理",27);title.setPadding(px(14),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
    }
    public void handleBack(){finish();}
    LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));view.setTextColor(TEXT);view.setGravity(Gravity.CENTER_VERTICAL);view.setIncludeFontPadding(false);return view;}
    TextView muted(String text,int size){TextView view=label(text,size);view.setTextColor(MUTED);return view;}
    TextView center(String text,int size){TextView view=label(text,size);view.setGravity(Gravity.CENTER);return view;}
    GradientDrawable surface(int top,int bottom,int stroke,float radius){GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});shape.setCornerRadius(px(radius));shape.setStroke(px(1),stroke);return shape;}
    Drawable focusSurface(float radius){StateListDrawable state=new StateListDrawable();state.addState(new int[]{android.R.attr.state_focused},surface(0x24ffffff,0x16ffffff,0xfff3f4f7,radius));state.addState(new int[]{android.R.attr.state_selected},surface(0x14ffffff,0x10ffffff,0x1effffff,radius));state.addState(new int[]{},surface(0x10ffffff,0x06ffffff,0x22ffffff,radius));return state;}
    void focusable(View view,float radius){view.setFocusable(true);view.setBackground(focusSurface(radius));}
    Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(13));b.setTextColor(TEXT);b.setIncludeFontPadding(false);b.setGravity(Gravity.CENTER);b.setMinimumWidth(0);b.setMinWidth(0);b.setMinimumHeight(0);b.setMinHeight(0);b.setPadding(px(16),0,px(16),0);b.setBackground(focusSurface(24));b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
    Drawable listSurface(){StateListDrawable state=new StateListDrawable();state.addState(new int[]{android.R.attr.state_focused},surface(0x24ffffff,0x16ffffff,-1,12));state.addState(new int[]{android.R.attr.state_selected},surface(0x14ffffff,0x10ffffff,0x1effffff,12));state.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));return state;}
    Button iconButton(String description,int resource,Runnable action){
        Button b=button("",action);b.setContentDescription(description);
        Drawable icon=getDrawable(resource);icon.setBounds(0,0,px(16),px(16));b.setCompoundDrawables(icon,null,null,null);
        b.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int horizontal=(right-left-icon.getBounds().width())/2,vertical=(bottom-top-icon.getBounds().height())/2;
            view.setPadding(horizontal,vertical,horizontal,vertical);
        });return b;
    }
    Button iconText(String text,int resource,Runnable action){Button b=button(text,action);Drawable icon=getDrawable(resource);icon.setBounds(0,0,px(15),px(15));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(px(7));return b;}
    void gap(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,px(height)));}
    void marginAdd(LinearLayout parent,View view,int width,int height,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:px(width),height<0?height:px(height));p.leftMargin=px(left);parent.addView(view,p);}
    LinearLayout toolbar(boolean dock){
        LinearLayout tools=row();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(54));p.topMargin=px(22);root.addView(tools,p);
        tab(tools,"应用列表",!dock,()->{if(dock){startActivity(new Intent(this,AppManagementActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));finish();}});
        tab(tools,"常用应用",dock,()->{if(!dock)startActivity(new Intent(this,DockEditorActivity.class));});
        tools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        View line=new View(this);line.setBackgroundColor(0x18ffffff);root.addView(line,new LinearLayout.LayoutParams(-1,px(1)));return tools;
    }
    private void tab(LinearLayout tools,String text,boolean selected,Runnable action){
        LinearLayout tab=column();LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(px(97),-1);tp.rightMargin=px(16);tools.addView(tab,tp);
        Button b=button(text,action);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(16));b.setPadding(px(3),0,px(3),0);b.setTextColor(selected?TEXT:MUTED);
        StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{android.R.attr.state_focused},surface(0x18ffffff,0x10ffffff,-1,8));bg.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));b.setBackground(bg);tab.addView(b,new LinearLayout.LayoutParams(-1,0,1));
        View underline=new View(this);underline.setBackgroundColor(selected?TEXT:Color.TRANSPARENT);tab.addView(underline,new LinearLayout.LayoutParams(-1,px(2)));
    }
    void footer(String help){LinearLayout footer=row();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(18));p.topMargin=px(16);root.addView(footer,p);footer.addView(muted(help,10),new LinearLayout.LayoutParams(0,-1,1));footer.addView(muted("Quiet TV",10));}
    ImageView icon(android.graphics.drawable.Drawable art,int size){ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(surface(0x08ffffff,0x08ffffff,0,size*.24f));image.setClipToOutline(true);image.setImageDrawable(art);image.setLayoutParams(new LinearLayout.LayoutParams(px(size),px(size)));return image;}
    EditText input(String text,String hint){EditText edit=new EditText(this);edit.setText(text);edit.setHint(hint);edit.setSingleLine(true);edit.setTextColor(TEXT);edit.setHintTextColor(MUTED);edit.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(14));edit.setPadding(px(14),px(10),px(14),px(10));edit.setBackground(focusSurface(10));return edit;}
    final class Sheet {
        final Dialog dialog=new Dialog(ManagementUi.this);
        final LinearLayout body=column(),actions=row(),box=column();
        final TextView heading;
        Sheet(String title){
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);box.setPadding(px(30),px(27),px(30),px(25));box.setBackground(surface(0xff292c33,0xff1b1e24,0x38ffffff,21));
            heading=center(title,23);box.addView(heading,new LinearLayout.LayoutParams(-1,-2));gap(box,18);
            ScrollView scroll=new ScrollView(ManagementUi.this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getMode(h)==View.MeasureSpec.UNSPECIFIED?px(240):Math.min(px(240),View.MeasureSpec.getSize(h)),View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(body);box.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
            actions.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,px(42));ap.topMargin=px(24);box.addView(actions,ap);
            dialog.setContentView(box);Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.65f);
            // Bound long package histories and app pickers without clipping the dialog actions.
        }
        void text(String text){TextView description=center(text,13);description.setTextColor(0xffb0b4be);description.setLineSpacing(px(5),1);body.addView(description,new LinearLayout.LayoutParams(-1,-2));}
        Button action(String text,Runnable action){Button b=button(text,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.setMargins(px(5),0,px(5),0);actions.addView(b,p);return b;}
        Button option(String text,Runnable action){Button b=button(text,action);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setBackground(focusSurface(9));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(43));p.bottomMargin=px(6);body.addView(b,p);return b;}
        Button option(String text,int resource,Runnable action){Button b=option(text,action);Drawable icon=getDrawable(resource);icon.setBounds(0,0,px(17),px(17));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(px(10));return b;}
        void show(View focus){dialog.show();Window window=dialog.getWindow();window.setLayout(px(444),-2);window.getDecorView().setSystemUiVisibility(5894);if(focus!=null)focus.requestFocus();}
    }
    void message(String text){Sheet sheet=new Sheet("提示");sheet.text(text);Button close=sheet.action("确定",sheet.dialog::dismiss);sheet.show(close);}
    private TextView feedback;
    void feedback(String text){if(feedback!=null)stage.removeView(feedback);TextView toast=center(text,13);toast.setPadding(px(20),px(12),px(20),px(12));toast.setBackground(surface(0xff292e34,0xff23272d,0x30ffffff,28));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=px(32);stage.addView(toast,p);feedback=toast;toast.announceForAccessibility(text);toast.postDelayed(()->{stage.removeView(toast);if(feedback==toast)feedback=null;},3000);}
}
