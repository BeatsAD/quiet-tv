package home.quiet.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

/** Shared clock/weather grouping for the screensaver and the settings preview. */
final class ScreensaverInformationView extends FrameLayout {
    private final TextView clock,weather;
    private final LinearLayout[] regions=new LinearLayout[ScreensaverSettings.POSITIONS.length];
    private final float clockSize,weatherSize;
    private ScreensaverSettings settings;
    ScreensaverInformationView(Context context,float clockSize,float weatherSize){
        super(context);this.clockSize=clockSize;this.weatherSize=weatherSize;setFocusable(false);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        for(int i=0;i<regions.length;i++){
            String position=ScreensaverSettings.POSITIONS[i];boolean center=ScreensaverSettings.CENTER.equals(position),right=position.endsWith("-right");
            LinearLayout region=new LinearLayout(context);region.setOrientation(LinearLayout.VERTICAL);region.setGravity(center?Gravity.CENTER_HORIZONTAL:right?Gravity.RIGHT:Gravity.LEFT);regions[i]=region;
            int gravity=center?Gravity.CENTER:(position.startsWith("top-")?Gravity.TOP:Gravity.BOTTOM)|(right?Gravity.RIGHT:Gravity.LEFT);
            addView(region,new LayoutParams(-2,-2,gravity));
        }
        clock=text();clock.setGravity(Gravity.CENTER_VERTICAL);clock.setTypeface(Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif-light",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=26)ClockFont.apply(clock);
        weather=text();weather.setTextSize(TypedValue.COMPLEX_UNIT_PX,weatherSize);weather.setEllipsize(TextUtils.TruncateAt.END);
    }
    private TextView text(){TextView view=new TextView(getContext());view.setIncludeFontPadding(false);view.setSingleLine(true);view.setTextColor(UiPalette.TEXT);view.setShadowLayer(weatherSize*.625f,0,weatherSize/10,0x77000000);return view;}
    void show(ScreensaverSettings settings,String time,String weatherText){
        this.settings=settings;clock.setText(time);clock.setVisibility(settings.showTime||ScreensaverSettings.CLOCK.equals(settings.mode)?VISIBLE:GONE);weather.setText(weatherText);weather.setVisibility(settings.showWeather&&!weatherText.isEmpty()?VISIBLE:GONE);
        place(clock,settings.timePosition);place(weather,settings.weatherPosition);geometry(getWidth(),getHeight());
    }
    private void place(TextView view,String position){
        LinearLayout target=regions[java.util.Arrays.asList(ScreensaverSettings.POSITIONS).indexOf(position)];
        if(view.getParent()!=target){if(view.getParent()!=null)((ViewGroup)view.getParent()).removeView(view);target.addView(view,view==clock?0:target.getChildCount(),new LinearLayout.LayoutParams(-2,-2));}
    }
    @Override protected void onMeasure(int width,int height){geometry(MeasureSpec.getSize(width),MeasureSpec.getSize(height));super.onMeasure(width,height);}
    private void geometry(int width,int height){
        if(settings==null||width==0)return;boolean centered=ScreensaverSettings.CENTER.equals(settings.timePosition);float size=centered?clockSize:clockSize*.5f;clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);
        int marginX=Math.round(width*.08f),marginY=Math.round(height*.08f);clock.setMaxWidth(width-2*marginX);weather.setMaxWidth(width-2*marginX);
        LinearLayout.LayoutParams time=(LinearLayout.LayoutParams)clock.getLayoutParams();int timeHeight=Math.round(size*1.2f);if(time.height!=timeHeight){time.height=timeHeight;clock.setLayoutParams(time);}
        LinearLayout.LayoutParams conditions=(LinearLayout.LayoutParams)weather.getLayoutParams();int gap=clock.getVisibility()==VISIBLE&&settings.timePosition.equals(settings.weatherPosition)?Math.round(width*10f/960):0;if(conditions.topMargin!=gap){conditions.topMargin=gap;weather.setLayoutParams(conditions);}
        for(int i=0;i<regions.length;i++){String position=ScreensaverSettings.POSITIONS[i];boolean center=ScreensaverSettings.CENTER.equals(position),right=position.endsWith("-right");LayoutParams p=(LayoutParams)regions[i].getLayoutParams();int left=!center&&!right?marginX:0,end=right?marginX:0,top=position.startsWith("top-")?marginY:0,bottom=position.startsWith("bottom-")?marginY:0;if(p.leftMargin!=left||p.rightMargin!=end||p.topMargin!=top||p.bottomMargin!=bottom){p.leftMargin=left;p.rightMargin=end;p.topMargin=top;p.bottomMargin=bottom;regions[i].setLayoutParams(p);}}
    }
    @android.annotation.TargetApi(26) private static final class ClockFont {static void apply(TextView view){view.setFontVariationSettings("'wght' 300");}}
}
