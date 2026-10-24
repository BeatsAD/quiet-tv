package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small original line drawings; no fonts, downloaded images, bitmaps or animation. */
final class WeatherIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;
    static int kind(String code){
        int value;try{value=Integer.parseInt(code);}catch(NumberFormatException e){return 8;}
        if(value==100)return 0;if(value==150)return 1;
        if((value>=101&&value<=104)||(value>=151&&value<=153))return 2;
        if(value>=302&&value<=304)return 5;
        if(value>=300&&value<400)return 3;
        if(value>=400&&value<500)return 4;
        if(value>=500&&value<600)return 6;
        if(value==900||value==901)return 7;return 8;
    }
    WeatherIcon(String code){kind=kind(code);paint.setColor(0xffe6e7ed);paint.setStrokeWidth(1.7f);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();int save=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);
        if(kind==0){canvas.drawCircle(12,12,4,paint);for(int n=0;n<8;n++){double angle=n*Math.PI/4;canvas.drawLine(12+(float)Math.cos(angle)*7,12+(float)Math.sin(angle)*7,12+(float)Math.cos(angle)*9,12+(float)Math.sin(angle)*9,paint);}}
        else if(kind==1){Path moon=new Path();moon.moveTo(16,3);moon.cubicTo(4,2,1,18,12,21);moon.cubicTo(16,22,20,19,21,15);moon.cubicTo(13,18,9,8,16,3);canvas.drawPath(moon,paint);}
        else if(kind>=2&&kind<=5){
            Path cloud=new Path();cloud.moveTo(5,15);cloud.cubicTo(-1,14,1,7,6,8);cloud.cubicTo(6,1,16,1,18,8);cloud.cubicTo(24,7,25,15,19,15);cloud.close();canvas.drawPath(cloud,paint);
            if(kind==3)for(int n=0;n<3;n++)canvas.drawLine(7+n*5,18,5+n*5,22,paint);
            if(kind==4)for(int n=0;n<3;n++){float x=6+n*6;canvas.drawLine(x,18,x,22,paint);canvas.drawLine(x-1.6f,19,x+1.6f,21,paint);canvas.drawLine(x-1.6f,21,x+1.6f,19,paint);}
            if(kind==5){Path bolt=new Path();bolt.moveTo(13,16);bolt.lineTo(9,20);bolt.lineTo(14,20);bolt.lineTo(11,24);canvas.drawPath(bolt,paint);}
        }else if(kind==6){for(int n=0;n<4;n++)canvas.drawLine(n%2==0?3:6,5+n*5,n%2==0?18:21,5+n*5,paint);}
        else if(kind==7){canvas.drawRoundRect(new RectF(9,3,15,17),3,3,paint);canvas.drawCircle(12,18,4,paint);}
        else{canvas.drawCircle(12,12,9,paint);canvas.drawLine(12,6,12,13,paint);canvas.drawPoint(12,17,paint);}
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
