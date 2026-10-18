package home.quiet.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Shared native TV controls: Android focus navigation stays inside the management screens. */
abstract class ManagementUi extends Activity {
    LinearLayout root,header;
    float unit;
    int px(float value){return Math.round(value*unit);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);unit=getResources().getDisplayMetrics().widthPixels/960f;
        getWindow().getDecorView().setSystemUiVisibility(5894);
        root=column();root.setPadding(px(40),px(24),px(40),px(20));root.setBackgroundColor(0xff10131b);setContentView(root);
        header=row();root.addView(header,new LinearLayout.LayoutParams(-1,px(52)));
    }
    LinearLayout row(){LinearLayout view=new LinearLayout(this);view.setOrientation(0);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(1);return view;}
    TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(size));view.setTextColor(0xffe8eaf1);view.setPadding(px(6),px(5),px(6),px(5));return view;}
    Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(TypedValue.COMPLEX_UNIT_PX,px(15));b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},new int[]{0xff10131b,Color.WHITE}));
        android.graphics.drawable.StateListDrawable bg=new android.graphics.drawable.StateListDrawable();
        for(boolean focused:new boolean[]{true,false}){GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(px(9));shape.setColor(focused?0xffe6ebff:0xff262c3b);shape.setStroke(px(1),focused?Color.WHITE:0xff46516a);bg.addState(focused?new int[]{android.R.attr.state_focused}:new int[]{},shape);}
        b.setBackground(bg);b.setPadding(px(12),px(8),px(12),px(8));b.setOnClickListener(v->action.run());b.setMinimumHeight(px(40));b.setMinHeight(px(40));return b;}
    void addButton(LinearLayout parent,String text,Runnable action){Button b=button(text,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(42));p.topMargin=px(8);parent.addView(b,p);}
    void message(String text){new android.app.AlertDialog.Builder(this).setMessage(text).setPositiveButton("确定",null).show();}
}
