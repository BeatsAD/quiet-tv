package home.quiet.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The live Home and its non-interactive preview use the same 960-wide layout. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic views require the logical scene scale.
final class HomeHeaderView extends FrameLayout {
    final TextView greeting,weather,clock,date;
    final LinearLayout timeRow;
    final FrameLayout buttons;
    final ArrayList<TextView> pills=new ArrayList<>();
    private final LinearLayout information,intro;
    private final View divider;
    private final float unit;
    private final boolean chinese;
    private final Typeface typeface;
    private final DesktopSettingsStore settings;
    private String arranged="";
    private boolean weatherAvailable;
    HomeHeaderView(Context context,float unit,boolean chinese,Typeface typeface){
        super(context);this.unit=unit;this.chinese=chinese;this.typeface=typeface;settings=new DesktopSettingsStore(context);
        setClipChildren(false);setFocusable(false);
        information=new LinearLayout(context);information.setOrientation(LinearLayout.VERTICAL);place(information,510,-2,50,32);
        intro=new LinearLayout(context);intro.setGravity(Gravity.CENTER_VERTICAL);
        timeRow=new LinearLayout(context);timeRow.setGravity(Gravity.CENTER_VERTICAL);
        greeting=text("",14,600,0xffe6e7ed);UiCompat.letterSpacing(greeting,.14f);greeting.setMaxWidth(px(288));
        weather=text("",14,400,0xffe6e7ed);weather.setCompoundDrawablePadding(px(6));
        clock=text("",50,400,0xfff1f2f6);clock.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=21)clock.setFontFeatureSettings("tnum");
        date=text("",14,500,0xffe6e7ed);UiCompat.letterSpacing(date,.08f);
        divider=new View(context);divider.setBackgroundColor(0x70e6e7ed);
        buttons=new FrameLayout(context);place(buttons,132,36,780,67);
        String[] titles={tr("信号源","Inputs"),tr("网络","Network"),tr("设置","Settings")};int[] icons={R.drawable.ic_input,R.drawable.ic_ethernet,R.drawable.ic_settings};
        for(int i=0;i<3;i++){
            TextView pill=text("",14,400,0xffededf1);pill.setGravity(Gravity.CENTER);pill.setContentDescription(titles[i]);
            pill.setBackground(ControlStyle.action(unit,100,0x550b2538,0));
            setPillIcon(pill,icons[i]);pills.add(pill);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(36),px(36));p.leftMargin=px(i*48);buttons.addView(pill,p);
        }
        apply();
    }
    private int px(float n){return Math.round(n*unit);}
    private String tr(String zh,String en){return chinese?zh:en;}
    private TextView text(String value,int size,int weight,int color){
        TextView v=new TextView(getContext());v.setText(value);v.setTextColor(color);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));
        Typeface base=chinese?Typeface.create("sans-serif",Typeface.NORMAL):typeface;
        v.setTypeface(Build.VERSION.SDK_INT>=28?Typeface.create(base,weight,false):Typeface.create(base,weight>=600?Typeface.BOLD:Typeface.NORMAL));
        v.setIncludeFontPadding(false);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private void place(View child,float width,float height,float x,float y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width<0?(int)width:px(width),height<0?(int)height:px(height));p.leftMargin=px(x);p.topMargin=px(y);addView(child,p);}
    static GradientDrawable pillSurface(float unit){GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x38ffffff,0x1cffffff,0x12ffffff});bg.setCornerRadius(22*unit);bg.setStroke(Math.max(1,Math.round(.5f*unit)),0x78ffffff);return bg;}
    void setPillIcon(TextView pill,int resource){Drawable icon=UiCompat.drawable(getContext(),resource);int size=px(18);icon.setBounds(0,0,size,size);int padding=(px(36)-size)/2;pill.setPadding(padding,0,padding,0);pill.setCompoundDrawables(icon,null,null,null);}
    private void detach(View v){if(v.getParent()!=null)((android.view.ViewGroup)v.getParent()).removeView(v);}
    private void add(LinearLayout parent,View v,int width,int height,int top){detach(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:px(width),height<0?height:px(height));p.topMargin=px(top);parent.addView(v,p);}
    void apply(){
        boolean gallery="gallery".equals(settings.layout());
        if(!arranged.equals(settings.layout())){
            for(View v:new View[]{greeting,weather,clock,date,divider,intro,timeRow})detach(v);
            information.removeAllViews();
            if(gallery){
                add(information,greeting,-2,22,0);add(information,clock,-1,86,0);add(information,date,-1,22,0);add(information,weather,-2,26,3);
            }else{
                add(information,intro,-1,22,0);add(intro,greeting,-2,-1,0);detach(weather);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-1,1);intro.addView(weather,wp);
                add(information,timeRow,-1,64,3);add(timeRow,clock,-2,-1,0);add(timeRow,divider,1,28,0);LinearLayout.LayoutParams dp=(LinearLayout.LayoutParams)divider.getLayoutParams();dp.leftMargin=px(16);dp.rightMargin=px(16);
                detach(date);timeRow.addView(date,new LinearLayout.LayoutParams(0,-1,1));
            }
            arranged=settings.layout();
        }
        information.setGravity(gallery?Gravity.CENTER_HORIZONTAL:Gravity.START);
        for(TextView v:new TextView[]{greeting,clock,date,weather})v.setGravity(gallery?Gravity.CENTER:Gravity.CENTER_VERTICAL);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?79:50));
        greeting.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?15:14));date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?15:14));weather.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?15:14));
        UiCompat.letterSpacing(greeting,gallery?.02f:.14f);UiCompat.letterSpacing(date,gallery?.02f:.08f);
        FrameLayout.LayoutParams ip=(FrameLayout.LayoutParams)information.getLayoutParams();ip.width=px(gallery?538:510);ip.leftMargin=px(gallery?211:50);ip.topMargin=px(gallery?(settings.showTime()?75:97):32);information.setLayoutParams(ip);
        greeting.setVisibility(settings.greetingEnabled()?VISIBLE:GONE);clock.setVisibility(settings.showTime()?VISIBLE:GONE);date.setVisibility(settings.showDate()?VISIBLE:GONE);
        weather.setVisibility(weatherAvailable&&new WeatherStore(getContext()).enabled()?VISIBLE:GONE);
        if(gallery){
            boolean previous=false;
            for(View item:new View[]{greeting,clock,date,weather}){
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)item.getLayoutParams();int spacing=previous&&item.getVisibility()==VISIBLE?px(8):0;
                if(p.topMargin!=spacing){p.topMargin=spacing;item.setLayoutParams(p);}if(item.getVisibility()==VISIBLE)previous=true;
            }
        }else{
            intro.setVisibility(greeting.getVisibility()==VISIBLE||weather.getVisibility()==VISIBLE?VISIBLE:GONE);
            timeRow.setVisibility(settings.showTime()||settings.showDate()?VISIBLE:GONE);divider.setVisibility(settings.showTime()&&settings.showDate()?VISIBLE:GONE);
            LinearLayout.LayoutParams wp=(LinearLayout.LayoutParams)weather.getLayoutParams();wp.leftMargin=settings.greetingEnabled()?px(16):0;weather.setLayoutParams(wp);
        }
        information.setVisibility(greeting.getVisibility()==VISIBLE||weather.getVisibility()==VISIBLE||settings.showTime()||settings.showDate()?VISIBLE:GONE);
        boolean[] shown={settings.showInput(),settings.showNetwork(),settings.showSettings()};int count=0;for(boolean show:shown)if(show)count++;
        int n=0;for(int i=0;i<pills.size();i++){TextView pill=pills.get(i);pill.setVisibility(shown[i]?VISIBLE:GONE);if(shown[i]){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)pill.getLayoutParams();p.leftMargin=px(96-(count-1-n++)*48);pill.setLayoutParams(p);}}
        buttons.setVisibility(count==0?GONE:VISIBLE);FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)buttons.getLayoutParams();bp.topMargin=px(gallery?24:67);buttons.setLayoutParams(bp);
    }
    void updateClock(boolean fixedGreeting){Calendar now=Calendar.getInstance();int hour=now.get(Calendar.HOUR_OF_DAY);String automatic=hour<11?tr("早上好","Good morning"):hour<18?tr("下午好","Good afternoon"):tr("晚上好","Good evening");String custom=settings.greetingText(fixedGreeting?tr("欢迎回家","Welcome home"):"");greeting.setText(custom.isEmpty()?automatic:custom);clock.setText(new SimpleDateFormat("HH:mm",Locale.US).format(now.getTime()));date.setText(new SimpleDateFormat(chinese?"M月d日  EEEE":"EEEE, MMMM d",chinese?Locale.SIMPLIFIED_CHINESE:Locale.US).format(now.getTime()));apply();}
    void weather(WeatherData data){weatherAvailable=data!=null;if(data==null){weather.setText("");weather.setCompoundDrawables(null,null,null,null);}else{WeatherIcon icon=new WeatherIcon(data.icon);icon.setBounds(0,0,px(18),px(18));weather.setCompoundDrawables(icon,null,null,null);weather.setText(String.format(Locale.ROOT,"%s  %s  %s",data.temperatureText(),data.description,data.city));}apply();}
}
