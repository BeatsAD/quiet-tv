package home.quiet.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** A 16:9 clock scene used by the full-screen saver and its TV preview. */
final class ScreensaverClockView extends View {
    private static final int WHITE=0xfff7f7f7, GREY=0xff898b8f, RED=0xffff453d, ORANGE=0xffff9d16;
    private static final String[] NUMBERS=new String[32];
    private static final String[] DAYS={"日","一","二","三","四","五","六"};
    private static final float[] SINES=new float[60], COSINES=new float[60];
    static {
        for(int i=0;i<NUMBERS.length;i++)NUMBERS[i]=String.valueOf(i);
        for(int i=0;i<60;i++){
            double angle=i*Math.PI/30;
            SINES[i]=(float)Math.sin(angle);
            COSINES[i]=(float)Math.cos(angle);
        }
    }

    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path handShape=new Path();
    private final Typeface digits, regular, heavy;
    private ScreensaverSettings settings;
    private int year=-1, month=-1, day=-1, hour, minute, second, firstColumn, monthLength;
    private String time="", lunar="", weekday="", monthTitle="";
    private List<Integer> lunarStarts=Collections.emptyList();

    ScreensaverClockView(Context context){
        super(context);
        setFocusable(false);
        digits=Build.VERSION.SDK_INT>=26?Fonts.outfit(context,300):Typeface.create("sans-serif-light",Typeface.NORMAL);
        regular=Build.VERSION.SDK_INT>=26?Fonts.outfit(context,500):Typeface.create("sans-serif-medium",Typeface.NORMAL);
        heavy=Build.VERSION.SDK_INT>=26?Fonts.outfit(context,650):Typeface.DEFAULT_BOLD;
    }

    void show(ScreensaverSettings next, Calendar now, String time){
        boolean monthDataChanged=settings==null||settings.clock.lunar!=next.clock.lunar
            ||settings.clock.calendar!=next.clock.calendar;
        boolean layoutChanged=settings==null||settings.clock!=next.clock||!settings.mode.equals(next.mode);
        settings=next;
        if(monthDataChanged)year=-1;
        updateTime(now,time);
        if(layoutChanged)invalidate();
    }

    void updateTime(Calendar now,String time){
        int currentYear=now.get(Calendar.YEAR), currentMonth=now.get(Calendar.MONTH);
        int currentDay=now.get(Calendar.DAY_OF_MONTH);
        boolean monthChanged=year!=currentYear||month!=currentMonth;
        boolean dateChanged=monthChanged||day!=currentDay;
        if(monthChanged){
            year=currentYear;
            month=currentMonth;
            monthTitle=NUMBERS[month+1]+"月";
            monthLength=now.getActualMaximum(Calendar.DAY_OF_MONTH);
            int weekday=now.get(Calendar.DAY_OF_WEEK)-Calendar.SUNDAY;
            firstColumn=(weekday-(currentDay-1)%7+7)%7;
            lunarStarts=settings.clock.lunar&&settings.clock.calendar?LunarDate.monthStarts(now):Collections.emptyList();
        }
        if(dateChanged){
            day=currentDay;
            weekday="周"+DAYS[now.get(Calendar.DAY_OF_WEEK)-Calendar.SUNDAY];
            lunar=settings.clock.lunar?LunarDate.screenText(now):"";
        }
        int h=now.get(Calendar.HOUR_OF_DAY), m=now.get(Calendar.MINUTE);
        int s=settings.secondsVisible()?now.get(Calendar.SECOND):0;
        boolean timeChanged=settings.analogVisible()?(hour!=h||minute!=m||second!=s):!this.time.equals(time);
        this.time=time;
        hour=h;
        minute=m;
        second=s;
        if(dateChanged||!settings.largeDateVisible()&&timeChanged)invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(settings==null||day<0)return;
        float scale=Math.min(getWidth()/960f,getHeight()/540f);
        canvas.save();
        canvas.translate((getWidth()-960*scale)/2,(getHeight()-540*scale)/2);
        canvas.scale(scale,scale);
        float center=settings.clock.calendar?277:480;
        if(settings.analogVisible()){
            drawDial(canvas,center,ScreensaverSettings.ClockOptions.SQUARE.equals(settings.clock.style));
        }else if(settings.largeDateVisible()){
            drawLargeDate(canvas,center);
        }else{
            drawTime(canvas,center);
        }
        if(settings.clock.calendar)drawCalendar(canvas);
        canvas.restore();
    }

    private void drawDial(Canvas canvas,float center,boolean square){
        canvas.save();
        canvas.translate(center,270);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for(int mark=0;mark<60;mark++){
            float x=SINES[mark], y=-COSINES[mark];
            boolean hourMark=mark%5==0;
            float edge=square?181.5f/Math.max(Math.abs(x),Math.abs(y)):181.5f;
            float length=square?(mark%15==0?37:hourMark?60.5f:18):(hourMark?13:11.5f);
            line(canvas,(edge-length)*x,(edge-length)*y,edge*x,edge*y,hourMark?3.7f:3.5f,hourMark?WHITE:0xff5e6063);
            if(hourMark&&(!square||mark%15==0)){
                int number=mark==0?12:mark/5;
                float radius=square?113:140;
                label(canvas,NUMBERS[number],x*radius,y*radius,square?53:43,WHITE,regular,Paint.Align.CENTER);
            }
        }
        float minuteAngle=AnalogClockView.minuteAngle(minute,second);
        drawHand(canvas,AnalogClockView.hourAngle(hour,minute)+second/120f,square?104:98,26,square?12.4f:11.8f);
        drawHand(canvas,minuteAngle,square?181:169,square?35:33,square?12.4f:10.4f);
        disk(canvas,0,0,11,WHITE);
        if(settings.secondsVisible()){
            canvas.save();
            canvas.rotate(second*6);
            line(canvas,0,29,0,-183,2.3f,ORANGE);
            canvas.restore();
            disk(canvas,0,0,7.2f,ORANGE);
            disk(canvas,0,0,3.7f,0xff000000);
        }else{
            disk(canvas,0,0,4.2f,0xff3c3e41);
        }
        canvas.restore();
    }

    private void drawHand(Canvas canvas,float angle,float reach,float base,float width){
        canvas.save();
        canvas.rotate(angle);
        line(canvas,0,1,0,-base,4.6f,WHITE);
        handShape.reset();
        handShape.moveTo(-width*.38f,-base);
        handShape.lineTo(-width/2,-reach);
        handShape.lineTo(width/2,-reach);
        handShape.lineTo(width*.38f,-base);
        handShape.close();
        paint.setColor(WHITE);
        canvas.drawPath(handShape,paint);
        disk(canvas,0,-reach,width/2,WHITE);
        disk(canvas,0,-base,width*.38f,WHITE);
        canvas.restore();
    }

    private void drawTime(Canvas canvas,float center){
        prepare(83,WHITE,digits,Paint.Align.CENTER);
        float cell=digitWidth(paint), colon=paint.measureText(":");
        int separators=settings.secondsVisible()?2:1;
        float x=center-((time.length()-separators)*cell+separators*colon)/2;
        float baseline=baseline(270);
        for(int i=0;i<time.length();i++){
            float width=time.charAt(i)==':'?colon:cell;
            canvas.drawText(time,i,i+1,x+width/2,baseline,paint);
            x+=width;
        }
    }

    static float digitWidth(Paint paint){
        float width=0;
        for(int i=0;i<10;i++)width=Math.max(width,paint.measureText(NUMBERS[i]));
        return width;
    }

    private void drawLargeDate(Canvas canvas,float center){
        prepare(43.5f,RED,Typeface.DEFAULT_BOLD,Paint.Align.LEFT);
        float weekdayWidth=paint.measureText(weekday), monthWidth=paint.measureText(monthTitle);
        float left=center-(weekdayWidth+monthWidth+13.5f)/2;
        label(canvas,weekday,left,145,43.5f,RED,Typeface.DEFAULT_BOLD,Paint.Align.LEFT);
        label(canvas,monthTitle,left+weekdayWidth+13.5f,145,43.5f,GREY,Typeface.DEFAULT_BOLD,Paint.Align.LEFT);
        prepare(224,WHITE,heavy,Paint.Align.CENTER);
        float width=paint.measureText(NUMBERS[day]);
        float size=width>222?224*222/width:224;
        paint.setTextSize(size);
        float numberWidth=paint.measureText(NUMBERS[day]);
        float lunarWidth=lunar.isEmpty()?0:49;
        float numberX=center-lunarWidth/2;
        label(canvas,NUMBERS[day],numberX,309,size,WHITE,heavy,Paint.Align.CENTER);
        if(!lunar.isEmpty()){
            float step=Math.min(41,164f/lunar.length());
            prepare(Math.min(34.5f,step*.88f),WHITE,Typeface.DEFAULT_BOLD,Paint.Align.CENTER);
            float x=numberX+numberWidth/2+31;
            for(int i=0;i<lunar.length();i++){
                float y=309+(i-(lunar.length()-1)/2f)*step;
                canvas.drawText(lunar,i,i+1,x,baseline(y),paint);
            }
        }
    }

    private void drawCalendar(Canvas canvas){
        float x=528, headingY=111;
        label(canvas,monthTitle,x,headingY,25.5f,RED,Typeface.DEFAULT_BOLD,Paint.Align.LEFT);
        if(!lunar.isEmpty()){
            float divider=x+paint.measureText(monthTitle)+12;
            paint.setStrokeCap(Paint.Cap.BUTT);
            line(canvas,divider,headingY-11.5f,divider,headingY+11.5f,.65f,0xff35383b);
            label(canvas,lunar,divider+10,headingY,23,GREY,Typeface.DEFAULT,Paint.Align.LEFT);
        }
        for(int i=0;i<7;i++)label(canvas,DAYS[i],543+i*50,161,22.5f,isWeekend(i)?GREY:WHITE,Typeface.DEFAULT,Paint.Align.CENTER);
        float step=firstColumn+monthLength>35?43.5f:53.5f;
        for(int number=1;number<=monthLength;number++){
            int index=firstColumn+number-1, column=index%7;
            float cellX=543+column*50, y=213.5f+index/7*step;
            if(number==day)disk(canvas,cellX,y,22,RED);
            else if(lunarStarts.contains(number)){
                paint.setStrokeCap(Paint.Cap.BUTT);
                line(canvas,cellX-13.5f,y+17,cellX+13.5f,y+17,2,RED);
            }
            label(canvas,NUMBERS[number],cellX,y,25,number!=day&&isWeekend(column)?GREY:WHITE,regular,Paint.Align.CENTER);
        }
    }

    private static boolean isWeekend(int column){return column==0||column==6;}
    private void disk(Canvas canvas,float x,float y,float radius,int color){
        paint.setColor(color);
        canvas.drawCircle(x,y,radius,paint);
    }
    private void line(Canvas canvas,float x1,float y1,float x2,float y2,float width,int color){
        paint.setColor(color);
        paint.setStrokeWidth(width);
        canvas.drawLine(x1,y1,x2,y2,paint);
    }
    private void label(Canvas canvas,String text,float x,float y,float size,int color,Typeface font,Paint.Align align){
        prepare(size,color,font,align);
        canvas.drawText(text,x,baseline(y),paint);
    }
    private float baseline(float y){return y-(paint.ascent()+paint.descent())/2;}
    private void prepare(float size,int color,Typeface font,Paint.Align align){
        paint.setColor(color);
        paint.setTypeface(font);
        paint.setTextSize(size);
        paint.setTextAlign(align);
    }

    @android.annotation.TargetApi(26)
    private static final class Fonts {
        static Typeface outfit(Context context,int weight){
            return new Typeface.Builder(context.getAssets(),"fonts/outfit.ttf")
                .setFontVariationSettings("'wght' "+weight).build();
        }
    }
}
