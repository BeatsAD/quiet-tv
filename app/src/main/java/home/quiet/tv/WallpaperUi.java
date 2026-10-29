package home.quiet.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Styles and small, version-independent artwork used by the wallpaper surfaces. */
final class WallpaperUi {
    static final int INK=0xfff4f5f7,MUTED=0xffaeb9c8;
    final Context context;
    final float unit;
    WallpaperUi(Context context,float unit){this.context=context;this.unit=unit;}
    int px(float value){return Math.round(value*unit);}
    TextView text(String value,int size){
        TextView view=new TextView(context);view.setText(value);view.setTextColor(INK);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create(size>=23?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return view;
    }
    GradientDrawable surface(int color,float radius,int stroke,float width){
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(px(radius));bg.setStroke(px(width),stroke);return bg;
    }
    Drawable focusSurface(int color,float radius){
        return ControlStyle.action(unit,radius,color,0x38ffffff);
    }
    TextView button(String value,boolean primary,Runnable action){
        TextView view=new KeyFeedback.Text(context);view.setText(value);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));view.setIncludeFontPadding(false);view.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusable(true);view.setGravity(Gravity.CENTER);
        view.setTextColor(INK);view.setMinHeight(px(primary?43:38));view.setPadding(px(primary?17:13),px(9),px(primary?17:13),px(9));
        view.setBackgroundDrawable(focusSurface(0xff242a33,100));
        view.setOnClickListener(v->action.run());return view;
    }
    TextView categoryTab(String value,Runnable action){
        TextView view=button(value,false,action);view.setMinHeight(0);view.setPadding(px(10),0,px(10),0);
        ControlStyle.choice(view,unit,100,Color.TRANSPARENT,0);return view;
    }
    TextView compactButton(String value,Runnable action){
        TextView view=button(value,false,action);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(11));
        view.setSingleLine(true);view.setMinHeight(0);view.setPadding(px(5),0,px(5),0);
        view.setBackgroundDrawable(focusSurface(Color.TRANSPARENT,100));return view;
    }
    Drawable capsule(){return surface(0xd92b2e34,100,0x28ffffff,.7f);}
    Drawable icon(int resource,int size,int color){
        Drawable art=UiCompat.drawable(context,resource).mutate();art.setColorFilter(color,PorterDuff.Mode.SRC_IN);art.setBounds(0,0,px(size),px(size));return art;
    }
    void icon(TextView view,int resource){view.setCompoundDrawables(icon(resource,16,view.getCurrentTextColor()),null,null,null);view.setCompoundDrawablePadding(px(7));}
    Drawable calendar(int size){
        Drawable art=new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF page=new RectF(3,5,21,22);
            @Override public void draw(Canvas canvas){int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);paint.setColor(INK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawRoundRect(page,2,2,paint);canvas.drawLine(3,10,21,10,paint);canvas.drawLine(7,2,7,7,paint);canvas.drawLine(17,2,17,7,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRect(7,14,10,17,paint);canvas.drawRect(14,14,17,17,paint);canvas.restoreToCount(save);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };art.setBounds(0,0,px(size),px(size));return art;
    }
    ImageView image(float outerRadius,float inset){
        // Subtract after pixel rounding so the inner and outer corner centers coincide.
        int radius=px(outerRadius)-px(inset);
        return new ImageView(context){
            final Path clip=new Path();final RectF imageBounds=new RectF();
            {setScaleType(ScaleType.CENTER_CROP);androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);}
            @Override protected void onDraw(Canvas canvas){
                imageBounds.set(0,0,getWidth(),getHeight());clip.reset();clip.addRoundRect(imageBounds,radius,radius,Path.Direction.CW);
                int saved=canvas.save();canvas.clipPath(clip);super.onDraw(canvas);canvas.restoreToCount(saved);
            }
        };
    }
    static Drawable shade(){
        return new Drawable(){
            final Paint paint=new Paint();
            @Override protected void onBoundsChange(Rect bounds){paint.setShader(new LinearGradient(0,0,0,bounds.height(),new int[]{0x9e05090f,0x1405090f,0x9905090f,0xff0a0e15},new float[]{0,.42f,.64f,1},Shader.TileMode.CLAMP));}
            @Override public void draw(Canvas canvas){canvas.drawRect(getBounds(),paint);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    /** Blur one small snapshot on opening, rather than continuously filtering a TV-sized frame. */
    static Bitmap backdrop(View background,View foreground){
        int width=320,height=Math.max(1,background.getHeight()*width/Math.max(1,background.getWidth()));
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);canvas.scale((float)width/Math.max(1,background.getWidth()),(float)height/Math.max(1,background.getHeight()));
        background.draw(canvas);foreground.draw(canvas);
        int[] pixels=new int[width*height],scratch=new int[pixels.length];image.getPixels(pixels,0,width,0,0,width,height);
        // Three separable box passes approximate the reference's soft Gaussian backdrop.
        for(int pass=0;pass<3;pass++){blur(pixels,scratch,width,height);blur(scratch,pixels,height,width);}
        image.setPixels(pixels,0,width,0,0,width,height);return image;
    }
    private static void blur(int[] input,int[] output,int width,int height){
        int radius=3,span=radius*2+1;
        for(int y=0;y<height;y++){
            int red=0,green=0,blue=0;
            for(int k=-radius;k<=radius;k++){int c=input[y*width+Math.max(0,Math.min(width-1,k))];red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c);}
            for(int x=0;x<width;x++){
                output[x*height+y]=Color.rgb(red/span,green/span,blue/span);
                int left=input[y*width+Math.max(0,x-radius)],right=input[y*width+Math.min(width-1,x+radius+1)];
                red+=Color.red(right)-Color.red(left);green+=Color.green(right)-Color.green(left);blue+=Color.blue(right)-Color.blue(left);
            }
        }
    }
}
