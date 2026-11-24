package home.quiet.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Shared clock/weather grouping for the screensaver and the settings preview. */
final class ScreensaverInformationView extends FrameLayout {
    private final TextView clock,weather;
    private final DailyQuoteView quote;
    private final ScreensaverClockView scene;
    private final FlipClockView flip;
    private final Calendar local=new GregorianCalendar();
    private final Date instant=new Date();
    private final SimpleDateFormat minuteFormat=new SimpleDateFormat("HH:mm",Locale.US);
    private final SimpleDateFormat secondFormat=new SimpleDateFormat("HH:mm:ss",Locale.US);
    private String timeText="";
    private boolean tabular;
    private final LinearLayout[] regions=new LinearLayout[ScreensaverSettings.POSITIONS.length];
    private final float clockSize,weatherSize;
    private ScreensaverSettings settings;
    ScreensaverInformationView(Context context,float clockSize,float weatherSize){
        super(context);this.clockSize=clockSize;this.weatherSize=weatherSize;setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        scene=new ScreensaverClockView(context);
        scene.setVisibility(GONE);
        addView(scene,new LayoutParams(-1,-1));
        for(int i=0;i<regions.length;i++){
            String position=ScreensaverSettings.POSITIONS[i];boolean center=ScreensaverSettings.CENTER.equals(position),right=position.endsWith("-right");
            LinearLayout region=new LinearLayout(context);region.setOrientation(LinearLayout.VERTICAL);region.setGravity(center?Gravity.CENTER_HORIZONTAL:right?Gravity.RIGHT:Gravity.LEFT);regions[i]=region;
            int gravity=center?Gravity.CENTER:(position.startsWith("top-")?Gravity.TOP:Gravity.BOTTOM)|(right?Gravity.RIGHT:Gravity.LEFT);
            addView(region,new LayoutParams(-2,-2,gravity));
        }
        clock=text();clock.setGravity(Gravity.CENTER_VERTICAL);clock.setTypeface(Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif-light",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=26)ClockFont.apply(clock);
        flip=new FlipClockView(context);addView(flip,new LayoutParams(-2,-2,Gravity.CENTER));
        quote=new DailyQuoteView(context,weatherSize/19.2f,true);
        addView(quote,new LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        weather=text();weather.setTextSize(TypedValue.COMPLEX_UNIT_PX,weatherSize);weather.setEllipsize(TextUtils.TruncateAt.END);
    }
    private TextView text(){TextView view=new TextView(getContext());view.setIncludeFontPadding(false);view.setSingleLine(true);view.setTextColor(UiPalette.TEXT);view.setShadowLayer(weatherSize*.17f,0,weatherSize*.052f,UiPalette.INK);return view;}
    void show(ScreensaverSettings settings,long now,String weatherText){
        this.settings=settings;
        TimeZone zone=TimeZone.getDefault();
        local.setTimeZone(zone);
        minuteFormat.setTimeZone(zone);
        secondFormat.setTimeZone(zone);
        boolean composed=settings.composedClock(),flipping=settings.flipping();
        scene.setVisibility(composed?VISIBLE:GONE);
        flip.setVisibility(flipping?VISIBLE:GONE);
        clock.setVisibility(!composed&&!flipping&&(settings.showTime||settings.digitalMode())?VISIBLE:GONE);
        if(!TextUtils.equals(weather.getText(),weatherText))weather.setText(weatherText);
        weather.setVisibility(settings.showWeather&&!weatherText.isEmpty()?VISIBLE:GONE);
        place(clock,settings.timePosition);
        place(weather,settings.weatherPosition);
        String text=timeAt(now);
        if(composed)scene.show(settings,local,text);
        else if(flipping)flip.time(text,now/settings.tickMillis());
        else updateDigits(text);
        geometry(getWidth(),getHeight());
    }
    void refreshQuote(long now,boolean enabled,boolean demand,boolean retry){
        DailyQuote.Entry value=null;
        if(enabled){
            DailyQuote content=DailyQuote.get(getContext());
            String day=DailyQuote.date(now);
            value=content.current(day);
            if(demand&&value==null)content.request(day,retry);
        }
        quote.show(value);
    }
    void updateTime(long now){
        if(settings==null)return;
        String text=timeAt(now);
        if(settings.composedClock())scene.updateTime(local,text);
        else if(settings.flipping())flip.time(text,now/settings.tickMillis());
        else if(clock.getVisibility()==VISIBLE)updateDigits(text);
    }
    private String timeAt(long now){
        local.setTimeInMillis(now);
        if(settings.largeDateVisible()||settings.analogVisible())return "";
        instant.setTime(now);
        return (settings.tickMillis()==1000?secondFormat:minuteFormat).format(instant);
    }
    private void updateDigits(String text){
        boolean fixed=settings.secondsVisible();
        if(timeText.equals(text)&&tabular==fixed)return;
        timeText=text;
        tabular=fixed;
        if(!fixed){clock.setText(text);return;}
        SpannableString line=new SpannableString(text);
        line.setSpan(new SecondsDigits(),0,line.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clock.setText(line);
    }
    void active(boolean active){flip.active(active);}
    void resetTime(){flip.stop();}
    void onLanded(Runnable action){flip.onLanded(action);}
    private void place(View view,String position){
        LinearLayout target=regions[java.util.Arrays.asList(ScreensaverSettings.POSITIONS).indexOf(position)];
        if(view.getParent()!=target){if(view.getParent()!=null)((ViewGroup)view.getParent()).removeView(view);target.addView(view,view==weather?target.getChildCount():0,new LinearLayout.LayoutParams(-2,-2));}
    }
    @Override protected void onMeasure(int width,int height){geometry(MeasureSpec.getSize(width),MeasureSpec.getSize(height));super.onMeasure(width,height);}
    private void geometry(int width,int height){
        if(settings==null||width==0)return;boolean centered=ScreensaverSettings.CENTER.equals(settings.timePosition);float size=centered?clockSize:clockSize*.5f;clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        float flipSize=Math.min(width*(settings.flipShowSeconds?.82f:.64f)/FlipClockView.widthFactor(settings.flipShowSeconds),height*.62f/FlipClockView.HEIGHT_FACTOR);flip.size(flipSize);
        int marginX=Math.round(width*.08f),marginY=Math.round(height*.08f);
        LayoutParams quotePosition=(LayoutParams)quote.getLayoutParams();
        int quoteWidth=Math.round(width*.66f);
        boolean bottomWeather=weather.getVisibility()==VISIBLE&&(settings.weatherPosition.startsWith("bottom-")||centered&&settings.composedClock()&&ScreensaverSettings.CENTER.equals(settings.weatherPosition));
        int quoteBottom=Math.round(height*.15f)+(bottomWeather?Math.round(weatherSize*2):0);
        if(quotePosition.width!=quoteWidth||quotePosition.bottomMargin!=quoteBottom){
            quotePosition.width=quoteWidth;quotePosition.bottomMargin=quoteBottom;quote.setLayoutParams(quotePosition);
        }
        clock.setMaxWidth(width-2*marginX);weather.setMaxWidth(width-2*marginX);
        LinearLayout.LayoutParams time=(LinearLayout.LayoutParams)clock.getLayoutParams();int timeHeight=Math.round(size*1.2f);if(time.height!=timeHeight){time.height=timeHeight;clock.setLayoutParams(time);}
        LinearLayout.LayoutParams conditions=(LinearLayout.LayoutParams)weather.getLayoutParams();int gap=!settings.flipping()&&clock.getVisibility()==VISIBLE&&settings.timePosition.equals(settings.weatherPosition)?Math.round(width*10f/960):0;if(conditions.topMargin!=gap){conditions.topMargin=gap;weather.setLayoutParams(conditions);}
        for(int i=0;i<regions.length;i++){String position=ScreensaverSettings.POSITIONS[i];boolean center=ScreensaverSettings.CENTER.equals(position),right=position.endsWith("-right"),weatherBelow=center&&settings.composedClock(),belowFlip=center&&settings.flipping();LayoutParams p=(LayoutParams)regions[i].getLayoutParams();int left=!center&&!right?marginX:0,end=right?marginX:0,top=belowFlip?Math.round(height/2f+flipSize*FlipClockView.HEIGHT_FACTOR/2+width*.02f):position.startsWith("top-")?marginY:0,bottom=position.startsWith("bottom-")||weatherBelow?marginY:0;int gravity=belowFlip?Gravity.TOP|Gravity.CENTER_HORIZONTAL:center?(weatherBelow?Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL:Gravity.CENTER):p.gravity;if(p.gravity!=gravity||p.leftMargin!=left||p.rightMargin!=end||p.topMargin!=top||p.bottomMargin!=bottom){p.gravity=gravity;p.leftMargin=left;p.rightMargin=end;p.topMargin=top;p.bottomMargin=bottom;regions[i].setLayoutParams(p);}}
    }
    private static final class SecondsDigits extends ReplacementSpan {
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
            if(metrics!=null)paint.getFontMetricsInt(metrics);
            return (int)Math.ceil(6*ScreensaverClockView.digitWidth(paint)+2*paint.measureText(":"));
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,int y,int bottom,Paint paint){
            float cell=ScreensaverClockView.digitWidth(paint), colon=paint.measureText(":");
            Paint.Align previous=paint.getTextAlign();
            paint.setTextAlign(Paint.Align.CENTER);
            for(int i=start;i<end;i++){
                float width=text.charAt(i)==':'?colon:cell;
                canvas.drawText(text,i,i+1,x+width/2,y,paint);
                x+=width;
            }
            paint.setTextAlign(previous);
        }
    }

    @android.annotation.TargetApi(26) private static final class ClockFont {static void apply(TextView view){view.setFontVariationSettings("'wght' 300");}}
}
