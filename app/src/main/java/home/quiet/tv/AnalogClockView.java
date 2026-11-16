package home.quiet.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import java.util.Calendar;

/** Passive clock face. HomeHeaderView owns time updates and their visibility lifecycle. */
final class AnalogClockView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] numbers=new float[24];
    private boolean light,seconds;
    private int hour=-1,minute=-1,second=-1;
    AnalogClockView(Context context){
        super(context);setFocusable(false);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        for(int i=0;i<12;i++){double a=i*Math.PI/6;numbers[i*2]=(float)Math.sin(a)*36;numbers[i*2+1]=-(float)Math.cos(a)*36;}
    }
    void style(String style){boolean value="analog-light".equals(style);if(light!=value){light=value;invalidate();}}
    void time(Calendar now,boolean showSeconds,String description){
        int h=now.get(Calendar.HOUR_OF_DAY),m=now.get(Calendar.MINUTE),s=showSeconds?now.get(Calendar.SECOND):0;
        if(hour==h&&minute==m&&second==s&&seconds==showSeconds)return;
        hour=h;minute=m;second=s;seconds=showSeconds;setContentDescription(description);invalidate();
    }
    static float hourAngle(int hour,int minute){return (hour%12)*30+minute*.5f;}
    static float minuteAngle(int minute,int second){return minute*6+second*.1f;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(hour<0)return;
        int save=canvas.save();canvas.translate(getWidth()/2f,getHeight()/2f);float scale=Math.min(getWidth(),getHeight())/100f;canvas.scale(scale,scale);
        paint.setStyle(Paint.Style.FILL);paint.setColor(light?0xfffafafa:0xd9000000);canvas.drawCircle(0,0,50,paint);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<60;i++){
            int tick=canvas.save();canvas.rotate(i*6);paint.setStrokeWidth(i%5==0?.95f:.7f);paint.setColor(light?(i%5==0?0xff666666:0xffb0b0b0):(i%5==0?0xffb8b8b8:0xff777777));
            canvas.drawLine(0,-46,0,i%5==0?-42.8f:-44,paint);canvas.restoreToCount(tick);
        }
        paint.setColor(light?0xff202020:0xffd9d9d9);paint.setTextSize(10.5f);paint.setTextAlign(Paint.Align.CENTER);
        float baseline=-(paint.ascent()+paint.descent())/2;
        for(int i=0;i<12;i++)canvas.drawText(String.valueOf(i==0?12:i),numbers[i*2],numbers[i*2+1]+baseline,paint);
        hand(canvas,hourAngle(hour,minute),26,4.4f);hand(canvas,minuteAngle(minute,second),37.5f,3.2f);
        canvas.drawCircle(0,0,1.8f,paint);
        if(seconds){paint.setColor(light?0xffff9500:0xffdddddd);int needle=canvas.save();canvas.rotate(second*6);paint.setStrokeWidth(.55f);canvas.drawLine(0,7,0,-44,paint);canvas.restoreToCount(needle);canvas.drawCircle(0,0,1.8f,paint);paint.setColor(light?0xfffafafa:0xff333333);canvas.drawCircle(0,0,.8f,paint);}
        canvas.restoreToCount(save);
    }
    private void hand(Canvas canvas,float angle,float length,float width){int save=canvas.save();canvas.rotate(angle);paint.setStrokeWidth(width);canvas.drawLine(0,2,0,-length,paint);canvas.restoreToCount(save);}
}
