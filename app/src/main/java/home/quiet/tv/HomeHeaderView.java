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
    final ArrayList<String> buttonIds=new ArrayList<>();
    private final LinearLayout information,intro,greetingRow,dateColumn;
    private final FrameLayout clockHost;
    private final AnalogClockView analogClock;
    private String previewClockStyle;
    private final TextView lunarDate;
    private final GreetingAvatarView avatar;
    private final GreetingAvatarStore avatars;
    private final View divider;
    private final TextView buttonLabel,movieTitle,movieSummary;
    private final LinearLayout movie;
    private String movieKey="";
    private final float unit;
    private final boolean chinese;
    private final Typeface typeface;
    private final DesktopSettingsStore settings;
    private String arranged="";
    private Map<String,?> appliedSettings;private String appliedHeader;
    private float applicationAreaTop=386;
    private boolean weatherAvailable;
    private boolean clockActive;
    private String calendarDay="",lunarDay="";
    private final SimpleDateFormat minuteFormat=new SimpleDateFormat("HH:mm",Locale.US),secondFormat=new SimpleDateFormat("HH:mm:ss",Locale.US);
    private final Runnable secondsTick=new Runnable(){public void run(){if(secondsVisible()){updateTime(Calendar.getInstance());postDelayed(this,1000-System.currentTimeMillis()%1000);}}};
    HomeHeaderView(Context context,float unit,boolean chinese,Typeface typeface){
        super(context);this.unit=unit;this.chinese=chinese;this.typeface=typeface;settings=new DesktopSettingsStore(context);avatars=new GreetingAvatarStore(context);
        setClipChildren(false);setFocusable(false);
        information=new LinearLayout(context);information.setOrientation(LinearLayout.VERTICAL);place(information,510,-2,24,32);
        intro=new LinearLayout(context);intro.setGravity(Gravity.CENTER_VERTICAL);
        timeRow=new LinearLayout(context);timeRow.setGravity(Gravity.CENTER_VERTICAL);
        greeting=text("",14,600,UiPalette.TEXT);UiCompat.letterSpacing(greeting,.14f);greeting.setMaxWidth(px(288));
        greetingRow=new LinearLayout(context);greetingRow.setGravity(Gravity.CENTER_VERTICAL);greetingRow.setFocusable(false);
        avatar=new GreetingAvatarView(context);greetingRow.addView(avatar,new LinearLayout.LayoutParams(px(28),px(28)));
        greetingRow.addView(greeting,new LinearLayout.LayoutParams(-2,-1));
        weather=text("",14,400,UiPalette.TEXT);weather.setCompoundDrawablePadding(px(6));
        clock=text("",50,400,UiPalette.TEXT);clock.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=21)clock.setFontFeatureSettings("tnum");
        clockHost=new FrameLayout(context);clockHost.addView(clock,new FrameLayout.LayoutParams(-2,-1));
        analogClock=new AnalogClockView(context);analogClock.setVisibility(GONE);clockHost.addView(analogClock,new FrameLayout.LayoutParams(-1,-1));
        date=text("",14,500,UiPalette.TEXT);UiCompat.letterSpacing(date,.08f);
        lunarDate=text("",12,400,0xddffffff);dateColumn=new LinearLayout(context);dateColumn.setOrientation(LinearLayout.VERTICAL);dateColumn.setGravity(Gravity.CENTER_VERTICAL);
        dateColumn.addView(date,new LinearLayout.LayoutParams(-1,px(22)));LinearLayout.LayoutParams lunarPosition=new LinearLayout.LayoutParams(-1,px(18));lunarPosition.topMargin=px(2);dateColumn.addView(lunarDate,lunarPosition);
        divider=new View(context);divider.setBackgroundColor(0x70ffffff);
        buttons=new FrameLayout(context);buttons.setClipChildren(false);buttons.setClipToPadding(false);place(buttons,125.76f,35.52f,0,24);
        for(int i=0;i<4;i++){
            TextView pill=text("",14,400,UiPalette.TEXT);pill.setGravity(Gravity.CENTER);
            pill.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,0x2e000000,false,0x4dffffff));
            pills.add(pill);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(35.52f),px(35.52f));p.leftMargin=px(i*45.12f);buttons.addView(pill,p);
        }
        buttonLabel=text("",12,400,UiPalette.TEXT);buttonLabel.setGravity(Gravity.CENTER);buttonLabel.setVisibility(INVISIBLE);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(120),px(26));lp.topMargin=px(39);buttons.addView(buttonLabel,lp);
        movie=new LinearLayout(context);movie.setOrientation(LinearLayout.VERTICAL);movie.setFocusable(false);movie.setVisibility(GONE);place(movie,360,-2,50,148);
        movieTitle=text("",24,500,UiPalette.TEXT);movieTitle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);movieSummary=text("",14,400,UiPalette.TEXT);movieSummary.setSingleLine(false);movieSummary.setMaxLines(3);movieSummary.setLineSpacing(px(3),1);movieSummary.setGravity(Gravity.START|Gravity.TOP);
        for(TextView value:new TextView[]{movieTitle,movieSummary})value.setShadowLayer(px(3),0,px(1),0xcc000000);
        movie.addView(movieTitle,new LinearLayout.LayoutParams(-1,px(32)));LinearLayout.LayoutParams summary=new LinearLayout.LayoutParams(-1,-2);summary.topMargin=px(6);movie.addView(movieSummary,summary);
        avatar.changed=this::apply;apply();
    }
    void movie(MovieWallpaperFeed feed){
        String key=feed==null?"":feed.revision+":"+feed.hash;if(key.equals(movieKey))return;movieKey=key;
        movie.setVisibility(feed!=null&&settings.showMovieInfo()?VISIBLE:GONE);movieTitle.setText(feed==null?"":feed.title);movieSummary.setText(feed==null?"":feed.summary);apply();
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
    private void setPillIcon(TextView pill,String glyph){Drawable icon=new QuickControlIcon(glyph);int size=px(18);icon.setBounds(0,0,size,size);int padding=(px(35.52f)-size)/2;pill.setPadding(padding,0,padding,0);pill.setCompoundDrawables(icon,null,null,null);}
    void setPillIcon(TextView pill,int resource){setPillIcon(pill,resource==R.drawable.ic_wifi?"header-wifi":resource==R.drawable.ic_ethernet?"header-ethernet":"header-wifi-off");}
    private String headerGlyph(String id){return HomeButtonStore.glyph(id);}
    void buttonBackdrop(DockBackdropDrawable.Background background,View origin){for(TextView pill:pills)pill.setBackgroundDrawable(new DockBackdropDrawable(background,pill,origin,unit,true,true));}
    private void detach(View v){if(v.getParent()!=null)((android.view.ViewGroup)v.getParent()).removeView(v);}
    private void add(LinearLayout parent,View v,int width,int height,int top){detach(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:px(width),height<0?height:px(height));p.topMargin=px(top);parent.addView(v,p);}
    void previewClockStyle(String value){previewClockStyle=value;}
    void applicationAreaTop(float value){if(applicationAreaTop!=value){applicationAreaTop=value;apply();}}
    void apply(){
        Map<String,?> values=getContext().getSharedPreferences("desktop-settings",0).getAll();
        String header=previewClockStyle+":"+weatherAvailable+":"+new WeatherStore(getContext()).enabled()+":"+movieKey+":"+applicationAreaTop+":"+avatar.getVisibility()+":"+(lunarDate.length()>0);
        if(values.equals(appliedSettings)&&header.equals(appliedHeader))return;
        appliedSettings=new HashMap<>(values);appliedHeader=header;
        boolean gallery="gallery".equals(settings.layout());
        String style=previewClockStyle==null?settings.clockStyle():previewClockStyle;
        boolean analog=!"digital".equals(style);String arrangement=settings.layout()+":"+analog;
        boolean tallClock=analog&&settings.showTime();
        movie.setVisibility(!movieKey.isEmpty()&&settings.showMovieInfo()?VISIBLE:GONE);
        boolean galleryMovie=gallery&&movie.getVisibility()==VISIBLE;
        int galleryOffset=(int)Math.max(0,Math.min(56,applicationAreaTop-280));
        FrameLayout.LayoutParams moviePosition=(FrameLayout.LayoutParams)movie.getLayoutParams();int movieWidth=px(gallery?420:360),movieLeft=px(gallery?270:50),movieTop=px(gallery?204+galleryOffset:tallClock?190:148);
        if(moviePosition.width!=movieWidth||moviePosition.leftMargin!=movieLeft||moviePosition.topMargin!=movieTop){moviePosition.width=movieWidth;moviePosition.leftMargin=movieLeft;moviePosition.topMargin=movieTop;movie.setLayoutParams(moviePosition);}
        movieTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?18:24));movieTitle.setGravity(gallery?Gravity.CENTER:Gravity.START|Gravity.CENTER_VERTICAL);movieSummary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?12:14));movieSummary.setMaxLines(gallery||tallClock?2:3);movieSummary.setGravity((gallery?Gravity.CENTER_HORIZONTAL:Gravity.START)|Gravity.TOP);
        LinearLayout.LayoutParams movieTitlePosition=(LinearLayout.LayoutParams)movieTitle.getLayoutParams(),movieSummaryPosition=(LinearLayout.LayoutParams)movieSummary.getLayoutParams();int titleHeight=px(gallery?26:32),summaryGap=px(gallery?4:6);
        if(movieTitlePosition.height!=titleHeight){movieTitlePosition.height=titleHeight;movieTitle.setLayoutParams(movieTitlePosition);}if(movieSummaryPosition.topMargin!=summaryGap){movieSummaryPosition.topMargin=summaryGap;movieSummary.setLayoutParams(movieSummaryPosition);}
        if(!arranged.equals(arrangement)){
            for(View v:new View[]{greetingRow,weather,clockHost,dateColumn,divider,intro,timeRow})detach(v);
            information.removeAllViews();
            if(gallery){
                add(information,greetingRow,-2,28,0);add(information,clockHost,analog?112:-1,analog?112:86,0);add(information,dateColumn,-1,-2,0);add(information,weather,-2,26,3);
            }else{
                add(information,intro,-1,28,0);add(intro,greetingRow,-2,-1,0);detach(weather);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-1,1);intro.addView(weather,wp);
                add(information,timeRow,-1,analog?110:64,3);add(timeRow,clockHost,analog?110:-2,-1,0);add(timeRow,divider,1,28,0);LinearLayout.LayoutParams dp=(LinearLayout.LayoutParams)divider.getLayoutParams();dp.leftMargin=px(16);dp.rightMargin=px(16);
                detach(dateColumn);timeRow.addView(dateColumn,new LinearLayout.LayoutParams(0,-1,1));
            }
            FrameLayout.LayoutParams textPosition=(FrameLayout.LayoutParams)clock.getLayoutParams();textPosition.width=gallery?-1:-2;clock.setLayoutParams(textPosition);
            arranged=arrangement;
        }
        LinearLayout.LayoutParams timePosition=(LinearLayout.LayoutParams)timeRow.getLayoutParams();if(!gallery){int height=px(analog&&settings.showTime()?110:64);if(timePosition.height!=height){timePosition.height=height;timeRow.setLayoutParams(timePosition);}}
        if(gallery){LinearLayout.LayoutParams clockPosition=(LinearLayout.LayoutParams)clockHost.getLayoutParams();int size=px(galleryMovie?80:112),height=analog?size:px(galleryMovie?64:86),width=analog?size:-1;if(clockPosition.width!=width||clockPosition.height!=height){clockPosition.width=width;clockPosition.height=height;clockHost.setLayoutParams(clockPosition);}}
        information.setGravity((gallery?Gravity.CENTER_HORIZONTAL:Gravity.START)|Gravity.TOP);
        for(TextView v:new TextView[]{greeting,clock,date,lunarDate,weather})v.setGravity(gallery?Gravity.CENTER:Gravity.CENTER_VERTICAL);
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?(galleryMovie?58:79):50));
        clock.setMinWidth(gallery?0:(int)Math.ceil(clock.getPaint().measureText(settings.showSeconds()?"88:88:88":"88:88")));
        lunarDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?13:12));
        greeting.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?15:14));date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?15:14));weather.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(gallery?15:14));
        UiCompat.letterSpacing(greeting,gallery?.02f:.14f);UiCompat.letterSpacing(date,gallery?.02f:.08f);
        FrameLayout.LayoutParams ip=(FrameLayout.LayoutParams)information.getLayoutParams();int width=px(gallery?538:510),left=px(gallery?211:24),top=px(gallery?(galleryMovie?8+galleryOffset:settings.showTime()?(analog?24:75):97):32);
        if(ip.width!=width||ip.leftMargin!=left||ip.topMargin!=top){ip.width=width;ip.leftMargin=left;ip.topMargin=top;information.setLayoutParams(ip);}
        greeting.setVisibility(settings.greetingEnabled()?VISIBLE:GONE);clockHost.setVisibility(settings.showTime()?VISIBLE:GONE);clock.setVisibility(analog?GONE:VISIBLE);analogClock.setVisibility(analog?VISIBLE:GONE);analogClock.style(style);dateColumn.setVisibility(settings.showDate()?VISIBLE:GONE);lunarDate.setVisibility(settings.showLunarDate()&&lunarDate.length()>0?VISIBLE:GONE);
        avatar.show(settings.avatarEnabled()?avatars.existing(settings.avatarFile()):null,settings.avatarEnabled());
        boolean hasAvatar=avatar.getVisibility()==VISIBLE,hasGreeting=settings.greetingEnabled()||hasAvatar;
        LinearLayout.LayoutParams gp=(LinearLayout.LayoutParams)greeting.getLayoutParams();int gap=hasAvatar&&settings.greetingEnabled()?px(8):0;
        if(gp.leftMargin!=gap){gp.leftMargin=gap;greeting.setLayoutParams(gp);}greetingRow.setVisibility(hasGreeting?VISIBLE:GONE);
        LinearLayout.LayoutParams gr=(LinearLayout.LayoutParams)greetingRow.getLayoutParams();int rowHeight=hasAvatar?px(28):px(22);
        if(gallery&&gr.height!=rowHeight){gr.height=rowHeight;greetingRow.setLayoutParams(gr);}
        if(!gallery){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)intro.getLayoutParams();if(p.height!=rowHeight){p.height=rowHeight;intro.setLayoutParams(p);}}
        weather.setVisibility(weatherAvailable&&new WeatherStore(getContext()).enabled()?VISIBLE:GONE);
        if(gallery){
            boolean previous=false;
            for(View item:new View[]{greetingRow,clockHost,dateColumn,weather}){
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)item.getLayoutParams();int spacing=previous&&item.getVisibility()==VISIBLE?px(galleryMovie?4:8):0;
                if(p.topMargin!=spacing){p.topMargin=spacing;item.setLayoutParams(p);}if(item.getVisibility()==VISIBLE)previous=true;
            }
        }else{
            intro.setVisibility(hasGreeting||weather.getVisibility()==VISIBLE?VISIBLE:GONE);
            timeRow.setVisibility(settings.showTime()||settings.showDate()?VISIBLE:GONE);divider.setVisibility(settings.showTime()&&settings.showDate()?VISIBLE:GONE);
            LinearLayout.LayoutParams wp=(LinearLayout.LayoutParams)weather.getLayoutParams();int margin=hasGreeting?px(16):0;if(wp.leftMargin!=margin){wp.leftMargin=margin;weather.setLayoutParams(wp);}
        }
        information.setVisibility(hasGreeting||weather.getVisibility()==VISIBLE||settings.showTime()||settings.showDate()?VISIBLE:GONE);
        if(buttonIds.isEmpty())buttonIds.add("control");
        try{List<String> ids=HomeButtonStore.visibleIds(new HomeButtonStore(getContext()).read().ids);buttonIds.clear();buttonIds.addAll(ids);}catch(java.io.IOException error){DiagnosticLog.info("home.buttons.unavailable","keep existing view");}
        int count=buttonIds.size();
        FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)buttons.getLayoutParams();bounds.width=px(count>3?170.88f:125.76f);bounds.leftMargin=0;bounds.rightMargin=px(24);bounds.gravity=Gravity.RIGHT|Gravity.TOP;buttons.setLayoutParams(bounds);
        for(int i=0;i<pills.size();i++){TextView pill=pills.get(i);boolean show=i<count;pill.setVisibility(show?VISIBLE:GONE);if(show){String id=buttonIds.get(i);if(!id.equals(pill.getTag())){pill.setTag(id);pill.setContentDescription(HomeButtonStore.title(id,chinese));setPillIcon(pill,headerGlyph(id));}FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)pill.getLayoutParams();int margin=bounds.width-p.width-px((count-1-i)*45.12f);if(p.leftMargin!=margin){p.leftMargin=margin;pill.setLayoutParams(p);}}}
        buttons.setVisibility(count==0?GONE:VISIBLE);
        syncSeconds();
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);for(TextView pill:pills)SettingsFocusMotion.update(pill,focused&&(pill.isFocused()||pill.isActivated()));}
    void focusButton(int index){
        buttonLabel.setVisibility(INVISIBLE);if(index<0||index>=buttonIds.size())return;
        buttonLabel.setText(HomeButtonStore.title(buttonIds.get(index),chinese));
        FrameLayout.LayoutParams label=(FrameLayout.LayoutParams)buttonLabel.getLayoutParams(),button=(FrameLayout.LayoutParams)pills.get(index).getLayoutParams();
        int left=button.leftMargin+(button.width-label.width)/2;if(label.leftMargin!=left){label.leftMargin=left;buttonLabel.setLayoutParams(label);}
    }
    void updateClock(boolean fixedGreeting){
        Calendar now=Calendar.getInstance();String automatic=DesktopSettingsStore.automaticGreeting(chinese,now.get(Calendar.HOUR_OF_DAY));String custom=settings.greetingText(fixedGreeting?tr("欢迎回家","Welcome home"):"");greeting.setText(custom.isEmpty()?automatic:custom);
        minuteFormat.setTimeZone(now.getTimeZone());secondFormat.setTimeZone(now.getTimeZone());updateTime(now);
        String day=now.get(Calendar.YEAR)+"-"+now.get(Calendar.MONTH)+"-"+now.get(Calendar.DAY_OF_MONTH)+":"+now.getTimeZone().getID();
        if(!day.equals(calendarDay)){calendarDay=day;date.setText(new SimpleDateFormat(chinese?"M月d日  EEEE":"EEEE, MMMM d",chinese?Locale.SIMPLIFIED_CHINESE:Locale.US).format(now.getTime()));}
        if(settings.showLunarDate()&&!day.equals(lunarDay)){lunarDay=day;lunarDate.setText(LunarDate.format(now));}apply();
    }
    private void updateTime(Calendar now){String value=(settings.showSeconds()?secondFormat:minuteFormat).format(now.getTime());if(!value.contentEquals(clock.getText()))clock.setText(value);analogClock.time(now,settings.showSeconds(),value);}
    void clockActive(boolean active){clockActive=active;syncSeconds();}
    private boolean secondsVisible(){android.os.PowerManager power=(android.os.PowerManager)getContext().getSystemService(Context.POWER_SERVICE);return clockActive&&getWindowToken()!=null&&getWindowVisibility()==VISIBLE&&isShown()&&settings.showTime()&&settings.showSeconds()&&power!=null&&power.isScreenOn();}
    private void syncSeconds(){removeCallbacks(secondsTick);if(settings!=null&&clock!=null&&secondsVisible()){updateTime(Calendar.getInstance());postDelayed(secondsTick,1000-System.currentTimeMillis()%1000);}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncSeconds();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(secondsTick);super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);syncSeconds();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncSeconds();}
    void weather(WeatherData data){weatherAvailable=data!=null;if(data==null){weather.setText("");weather.setCompoundDrawables(null,null,null,null);}else{WeatherIcon icon=new WeatherIcon(data.icon);icon.setBounds(0,0,px(18),px(18));weather.setCompoundDrawables(icon,null,null,null);weather.setText(String.format(Locale.ROOT,"%s  %s  %s",data.temperatureText(),data.description,data.city));}apply();}
}
