package home.quiet.tv;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The same interval choices serve desktop settings and the wallpaper library. */
final class WallpaperIntervalEditor extends LinearLayout {
    interface Save {void apply(int seconds);}

    private final WallpaperUi ui;
    private final boolean chinese;
    private final Runnable rewired;
    private final Save save;
    private final TextView secondsTab,minutesTab,customButton,suffix,minimum,apply;
    private final TextView[] presets=new TextView[6];
    private final EditText number;
    private boolean minutes,custom,selected=true;
    private int seconds;

    WallpaperIntervalEditor(Context context,float unit,boolean chinese,int seconds,Save save,Runnable rewired){
        super(context);
        ui=new WallpaperUi(context,unit);this.chinese=chinese;this.save=save;this.rewired=rewired;
        setOrientation(VERTICAL);setClipChildren(false);setClipToPadding(false);
        setPadding(ui.px(9.6f),ui.px(6),ui.px(9.6f),ui.px(9.6f));
        LinearLayout units=row();
        secondsTab=choice(units,tr("秒","Seconds"),()->changeUnit(false));
        minutesTab=choice(units,tr("分钟","Minutes"),()->changeUnit(true));
        for(int first=0;first<presets.length;first+=3){
            LinearLayout choices=row();
            for(int i=first;i<first+3;i++){
                final int index=i;
                presets[i]=choice(choices,"",()->save.apply(WallpaperRotation.presets(minutes)[index]));
            }
        }
        LinearLayout customRow=row();
        customButton=choice(customRow,tr("自定义","Custom"),this::toggleCustom);
        LinearLayout entry=row();
        number=new EditText(context);number.setId(androidx.core.view.ViewCompat.generateViewId());
        number.setTag("wall-custom-interval");number.setSingleLine(true);
        number.setContentDescription(tr("自定义轮换间隔","Custom rotation interval"));
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        number.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(14));
        number.setPadding(ui.px(10),0,ui.px(10),0);
        number.setBackgroundDrawable(new SettingsFocusDrawable(unit,10,UiPalette.CONTROL,false,UiPalette.EDGE));
        number.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
        number.setImeOptions(EditorInfo.IME_ACTION_DONE);
        number.setOnEditorActionListener((view,action,event)->{
            if(action!=EditorInfo.IME_ACTION_DONE)return false;
            submit();return true;
        });
        entry.addView(number,new LayoutParams(0,ui.px(38),1));
        suffix=ui.text("",12);suffix.setTextColor(UiPalette.SECONDARY);
        suffix.setPadding(ui.px(10),0,ui.px(6),0);entry.addView(suffix);
        minimum=ui.text("",11);minimum.setTextColor(UiPalette.SECONDARY);
        minimum.setPadding(ui.px(6),ui.px(3),0,ui.px(3));addView(minimum);
        LinearLayout actions=row();
        apply=choice(actions,tr("应用间隔","Apply interval"),this::submit);
        reset(seconds);
    }

    private String tr(String zh,String en){return chinese?zh:en;}
    private LinearLayout row(){
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);row.setClipToPadding(false);
        addView(row,new LayoutParams(-1,-2));return row;
    }
    private TextView choice(LinearLayout row,String title,Runnable action){
        TextView button=ui.button(title,false,action);
        button.setFocusableInTouchMode(true);button.setSingleLine(true);
        LayerDrawable background=new LayerDrawable(new Drawable[]{
            new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE)
        });
        int inset=ui.px(3);
        background.setLayerInset(0,inset,inset,inset,inset);
        button.setBackgroundDrawable(background);
        button.setTextColor(SettingsFocusDrawable.textColors(UiPalette.SECONDARY,UiPalette.INK));
        button.setPadding(ui.px(6),0,ui.px(6),0);
        LayoutParams size=new LayoutParams(0,ui.px(38),1);
        if(row.getChildCount()>0)size.leftMargin=ui.px(3);
        row.addView(button,size);return button;
    }
    private boolean preset(int seconds){
        for(int value:WallpaperRotation.presets(minutes))if(value==seconds)return true;
        return false;
    }
    void refresh(int seconds){refresh(seconds,true);}
    void refresh(int seconds,boolean selected){
        if(this.seconds==seconds&&this.selected==selected)return;
        reset(seconds,selected);
    }
    void reset(int seconds){
        reset(seconds,true);
    }
    private void reset(int seconds,boolean selected){
        this.selected=selected;
        this.seconds=seconds;minutes=WallpaperRotation.minuteUnit(seconds);
        custom=selected&&!preset(seconds);loadNumber();render();
    }
    private void changeUnit(boolean minutes){
        if(this.minutes==minutes)return;
        this.minutes=minutes;custom=false;loadNumber();render();
    }
    private void toggleCustom(){
        custom=!custom;render();
        if(custom)number.requestFocusFromTouch();
    }
    private void loadNumber(){
        number.setError(null);
        number.setText(minutes?(seconds%60==0?String.valueOf(seconds/60):""):String.valueOf(seconds));
    }
    private void render(){
        secondsTab.setSelected(!minutes);minutesTab.setSelected(minutes);
        int[] values=WallpaperRotation.presets(minutes);
        for(int i=0;i<values.length;i++){
            String label=WallpaperRotation.label(values[i],minutes,chinese);
            boolean checked=selected&&!custom&&values[i]==seconds;
            presets[i].setText(SettingsSelectionMark.label(label,checked,ui.unit));
            presets[i].setContentDescription(label);
            presets[i].setSelected(checked);
        }
        customButton.setSelected(custom);
        customButton.setText(SettingsSelectionMark.label(tr("自定义","Custom"),custom,ui.unit));
        suffix.setText(minutes?tr("分钟","min"):tr("秒","sec"));
        minimum.setText(minutes?tr("1–1440 分钟","1–1440 minutes"):tr("最短 3 秒","Minimum 3 seconds"));
        int visibility=custom?View.VISIBLE:View.GONE;
        ((View)number.getParent()).setVisibility(visibility);minimum.setVisibility(visibility);
        ((View)apply.getParent()).setVisibility(visibility);post(rewired);
    }
    private void submit(){
        int value;
        try{value=Integer.parseInt(number.getText().toString());}
        catch(NumberFormatException invalid){value=0;}
        if(minutes&&value>=1&&value<=WallpaperRotation.MAX_SECONDS/60||!minutes&&WallpaperRotation.valid(value)){
            save.apply(minutes?value*60:value);
        }else number.setError(minutes?tr("请输入 1–1440 的整数分钟","Enter 1–1440 whole minutes")
                :tr("请输入 3–86400 的整数秒","Enter 3–86400 whole seconds"));
    }
}
