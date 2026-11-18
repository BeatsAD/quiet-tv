package home.quiet.tv;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.view.View;

/** A single perspective leaf per changed card, with one shared landing per time update. */
final class FlipClockView extends View {
    static final float HEIGHT_FACTOR=1.5f/1.23f;
    private static final android.animation.TimeInterpolator MOTION=androidx.core.view.animation.PathInterpolatorCompat.create(.35f,.04f,.62f,1);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics=new Paint.FontMetrics();
    private final RectF card=new RectF(),inside=new RectF(),bevel=new RectF(),hinge=new RectF();
    private final Rect damage=new Rect();
    private final Matrix projection=new Matrix();
    private final float[] source=new float[8],destination=new float[8];
    private String time="00:00",previous=time;
    private long tick=Long.MIN_VALUE;
    private float textSize,progress=1,preparedSize,cardWidth,cardHeight,baseline,digitAdvance;
    private Shader frameShade,upperShade,lowerShade,dropShade,hingeShade;
    private boolean active,seconds;
    private ValueAnimator animator;
    private Runnable landed;
    FlipClockView(Context context){super(context);paint.setTypeface(Build.VERSION.SDK_INT>=26?ClockFont.bold(context):Typeface.create("sans-serif",Typeface.BOLD));setFocusable(false);setContentDescription(time);}
    static float widthFactor(boolean seconds){int count=seconds?3:2;return count*1.5f/(1-(count-1)*.065f);}
    void size(float size){if(textSize==size)return;textSize=size;requestLayout();invalidate();}
    void active(boolean active){if(this.active==active)return;this.active=active;stop();}
    void onLanded(Runnable action){landed=action;}
    void time(String value,long tick){
        boolean nextSeconds=value.length()==8;
        if(nextSeconds!=seconds){stop();seconds=nextSeconds;time=previous=value;this.tick=tick;setContentDescription(value);requestLayout();invalidate();return;}
        if(value.equals(time)){this.tick=tick;return;}
        boolean animate=active&&isShown()&&hasWindowFocus()&&this.tick!=Long.MIN_VALUE&&tick==this.tick+1&&(Build.VERSION.SDK_INT<26||AnimationEnabled.enabled());
        String before=time;stop();previous=before;time=value;this.tick=tick;setContentDescription(value);
        if(animate){
            progress=0;ValueAnimator running=ValueAnimator.ofFloat(0,1);animator=running;
            running.setDuration(seconds&&before.regionMatches(0,value,0,5)?260:380);running.setInterpolator(MOTION);
            running.addUpdateListener(valueAnimator->{progress=(float)valueAnimator.getAnimatedValue();redraw();});
            running.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){if(animator!=animation)return;animator=null;progress=1;previous=time;redraw();if(landed!=null&&active&&isShown()&&hasWindowFocus())landed.run();}});
            running.start();
        }
        invalidate();
    }
    private void redraw(){if(damage.isEmpty())invalidate();else invalidate(damage);}
    void stop(){ValueAnimator running=animator;animator=null;if(running!=null)running.cancel();progress=1;previous=time;tick=Long.MIN_VALUE;invalidate();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=VISIBLE)stop();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE)stop();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)stop();}
    @Override protected void onMeasure(int width,int height){setMeasuredDimension(resolveSize(Math.round(textSize*widthFactor(seconds)),width),resolveSize(Math.round(textSize*HEIGHT_FACTOR),height));}
    private void prepare(float size){
        if(preparedSize==size)return;preparedSize=size;cardWidth=1.5f*size;cardHeight=HEIGHT_FACTOR*size;
        card.set(0,0,cardWidth,cardHeight);inside.set(cardWidth*.07f,cardHeight*.07f,cardWidth*.93f,cardHeight*.93f);bevel.set(cardWidth*.03f,cardHeight*.03f,cardWidth*.97f,cardHeight*.97f);hinge.set(0,0,cardWidth*.03f,cardHeight*.15f);
        frameShade=new LinearGradient(0,0,cardWidth,cardHeight,new int[]{0xff37383d,0xff17181b,0xff0b0b0d,0xff18191b,0xff333439},new float[]{0,.06f,.48f,.86f,1},Shader.TileMode.CLAMP);
        upperShade=new LinearGradient(inside.left,inside.top,inside.right,inside.bottom, new int[]{0xff35353d,0xff2d2d34,0xff29292f},null,Shader.TileMode.CLAMP);
        lowerShade=new LinearGradient(inside.left,inside.top,inside.right,inside.bottom,0xff28282f,0xff2d2d34,Shader.TileMode.CLAMP);
        dropShade=new LinearGradient(0,inside.centerY(),0,inside.bottom,0x77000000,0x00000000,Shader.TileMode.CLAMP);
        hingeShade=new LinearGradient(0,0,hinge.width(),0,new int[]{0xff1c1d21,0xff85868a,0xffc3c4c8,0xff77787d,0xff1a1b1e},new float[]{0,.31f,.45f,.63f,1},Shader.TileMode.CLAMP);
        paint.setTextSize(cardWidth*.64f);paint.getFontMetrics(metrics);paint.setTextAlign(Paint.Align.CENTER);digitAdvance=paint.measureText("0");baseline=inside.centerY()-(metrics.ascent+metrics.descent)/2;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float size=Math.min(textSize,Math.min(getWidth()/widthFactor(seconds),getHeight()/HEIGHT_FACTOR));if(size<=0)return;prepare(size);
        int count=seconds?3:2;float width=size*widthFactor(seconds),gap=width*.065f,left=(getWidth()-width)/2,top=(getHeight()-cardHeight)/2;
        damage.setEmpty();
        for(int i=0;i<count;i++){
            int offset=i*3;float x=left+i*(cardWidth+gap);boolean moving=progress<1&&!time.regionMatches(offset,previous,offset,2);
            int save=canvas.save();canvas.translate(x,top);frame(canvas);half(canvas,time,offset,true);half(canvas,moving?previous:time,offset,false);
            if(moving){damage.union((int)Math.floor(x),(int)Math.floor(top),(int)Math.ceil(x+cardWidth),(int)Math.ceil(top+cardHeight));leaf(canvas,offset);}
            paint.setShader(null);paint.setColor(0xff08090b);float seam=Math.max(1,cardHeight*.013f);canvas.drawRect(inside.left,inside.centerY()-seam/2,inside.right,inside.centerY()+seam/2,paint);
            hinge(canvas,cardWidth*.053f);hinge(canvas,cardWidth*.917f);canvas.restoreToCount(save);
            if(i<count-1){paint.setShader(null);paint.setColor(0xffb9b9c0);float middle=x+cardWidth+gap/2,radius=width*.0065f,dy=(width*.013f+width*.019f/(seconds?.82f:.64f))/2;canvas.drawCircle(middle,getHeight()/2f-dy,radius,paint);canvas.drawCircle(middle,getHeight()/2f+dy,radius,paint);}
        }
    }
    private void frame(Canvas canvas){
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setShader(frameShade);canvas.drawRoundRect(card,cardWidth*.13f,cardHeight*.16f,paint);paint.setShader(null);paint.setColor(0xff39393e);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,cardWidth*.003f));canvas.drawRoundRect(card,cardWidth*.13f,cardHeight*.16f,paint);paint.setColor(0xff08080a);canvas.drawRoundRect(bevel,cardWidth*.1f,cardHeight*.13f,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void half(Canvas canvas,String text,int offset,boolean upper){
        int save=canvas.save();canvas.clipRect(inside.left,upper?inside.top:inside.centerY(),inside.right,upper?inside.centerY():inside.bottom);
        paint.setAlpha(255);paint.setShader(upper?upperShade:lowerShade);canvas.drawRoundRect(inside,inside.width()*.08f,inside.height()*.085f,paint);paint.setShader(null);paint.setColor(0xffe7e7ed);
        canvas.drawText(text,offset,offset+1,inside.centerX()-digitAdvance/2,baseline,paint);canvas.drawText(text,offset+1,offset+2,inside.centerX()+digitAdvance/2,baseline,paint);canvas.restoreToCount(save);
    }
    private void leaf(Canvas canvas,int offset){
        double angle=Math.PI*progress;float sine=(float)Math.sin(angle),cosine=(float)Math.cos(angle);boolean back=progress>=.5f;
        int save=canvas.save();canvas.clipRect(inside.left,inside.centerY(),inside.right,inside.bottom);paint.setShader(dropShade);paint.setAlpha(Math.round(255*sine));canvas.drawRoundRect(inside,inside.width()*.08f,inside.height()*.085f,paint);paint.setAlpha(255);paint.setShader(null);canvas.restoreToCount(save);
        if(Math.abs(cosine)<.001f)return;
        float start=back?inside.centerY():inside.top,end=back?inside.bottom:inside.centerY(),distance=cardWidth*5;
        source[0]=inside.left;source[1]=start;source[2]=inside.right;source[3]=start;source[4]=inside.right;source[5]=end;source[6]=inside.left;source[7]=end;
        for(int i=0;i<4;i++){float y=source[i*2+1]-inside.centerY();if(back)y=-y;float scale=distance/(distance+y*sine);destination[i*2]=inside.centerX()+(source[i*2]-inside.centerX())*scale;destination[i*2+1]=inside.centerY()+y*cosine*scale;}
        if(!projection.setPolyToPoly(source,0,destination,0,4))return;
        save=canvas.save();canvas.concat(projection);half(canvas,back?time:previous,offset,!back);canvas.clipRect(inside.left,start,inside.right,end);paint.setColor(Color.argb(Math.round(56*sine),0,0,0));canvas.drawRoundRect(inside,inside.width()*.08f,inside.height()*.085f,paint);canvas.restoreToCount(save);
    }
    private void hinge(Canvas canvas,float left){
        int save=canvas.save();canvas.translate(left,cardHeight*.425f);paint.setShader(hingeShade);canvas.drawRoundRect(hinge,hinge.width()*.35f,hinge.width()*.35f,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff08090b);paint.setStrokeWidth(Math.max(1,cardWidth*.002f));canvas.drawRoundRect(hinge,hinge.width()*.35f,hinge.width()*.35f,paint);paint.setStyle(Paint.Style.FILL);canvas.restoreToCount(save);
    }
    @android.annotation.TargetApi(26) private static final class AnimationEnabled {static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}}
    @android.annotation.TargetApi(26) private static final class ClockFont {static Typeface bold(Context context){return new Typeface.Builder(context.getAssets(),"fonts/outfit.ttf").setFontVariationSettings("'wght' 700").build();}}
}
