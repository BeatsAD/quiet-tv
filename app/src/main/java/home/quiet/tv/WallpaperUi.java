package home.quiet.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Styles and small, version-independent artwork used by the wallpaper surfaces. */
final class WallpaperUi {
    static final int INK=UiPalette.TEXT,MUTED=UiPalette.SECONDARY;
    static final int TOOLBAR_WIDTH=78,TOOLBAR_HEIGHT=30,CATEGORY_PADDING=4,TOOLBAR_PADDING=16;
    static final int CARD_RADIUS=16,CARD_INSET=5;
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
        GradientDrawable bg=new SurfaceDrawable(unit);bg.setColor(color);bg.setCornerRadius(px(radius));bg.setStroke(px(width),stroke);return bg;
    }
    Drawable focusSurface(int color,float radius){
        return ControlStyle.action(unit,radius,color,0x38ffffff);
    }
    TextView button(String value,boolean primary,Runnable action){
        TextView view=new KeyFeedback.Text(context);view.setText(value);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));view.setIncludeFontPadding(false);view.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusable(true);view.setGravity(Gravity.CENTER);
        view.setTextColor(INK);view.setMinHeight(px(primary?43:38));view.setPadding(px(primary?17:13),px(9),px(primary?17:13),px(9));
        view.setBackgroundDrawable(focusSurface(0xff000000,100));
        view.setOnClickListener(v->action.run());return view;
    }
    static void toolbarFocus(TextView... controls){
        java.util.ArrayList<TextView> available=new java.util.ArrayList<>();
        for(TextView control:controls)if(control.getVisibility()==View.VISIBLE&&control.isEnabled())available.add(control);
        for(int i=0;i<available.size();i++){
            TextView control=available.get(i);if(i>0)control.setNextFocusLeftId(available.get(i-1).getId());
            control.setNextFocusRightId(available.get(Math.min(i+1,available.size()-1)).getId());
            final int index=i;control.setOnKeyListener((v,key,event)->{
                if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
                if(key==KeyEvent.KEYCODE_DPAD_LEFT&&index==0)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN)available.get(Math.max(0,Math.min(available.size()-1,index+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)))).requestFocus();return true;
            });
        }
    }
    TextView categoryTab(String value,Runnable action){
        TextView view=button(value,false,action);view.setMinHeight(0);view.setPadding(px(10),0,px(10),0);
        ControlStyle.choice(view,unit,100,Color.TRANSPARENT,0);return view;
    }
    void toolbarSize(TextView view){
        // Match the category capsule after each part has been rounded to pixels.
        view.setLayoutParams(new LinearLayout.LayoutParams(-2,px(TOOLBAR_HEIGHT)+2*px(CATEGORY_PADDING)));
        view.setBackgroundDrawable(ControlStyle.action(unit,100,0xff000000,0x38ffffff,0));
        view.setSingleLine(true);view.setMinHeight(0);view.setPadding(px(TOOLBAR_PADDING),0,px(TOOLBAR_PADDING),0);view.setCompoundDrawablePadding(px(4));
    }
    TextView compactButton(String value,Runnable action){
        TextView view=button(value,false,action);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(11));
        view.setSingleLine(true);view.setMinHeight(0);view.setPadding(px(5),0,px(5),0);
        view.setBackgroundDrawable(focusSurface(Color.TRANSPARENT,100));return view;
    }
    Drawable capsule(){return surface(0xd9000000,100,0x28ffffff,.7f);}
    Drawable icon(int resource,int size,int color){
        Drawable art=UiCompat.drawable(context,resource).mutate();art.setColorFilter(color,PorterDuff.Mode.SRC_IN);art.setBounds(0,0,px(size),px(size));return art;
    }
    Drawable icon(String name,int size,int color){
        Drawable art=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon(name)).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(art,new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{UiPalette.FAINT,color}));art.setBounds(0,0,px(size),px(size));return art;
    }
    void icon(TextView view,String name){view.setCompoundDrawables(icon(name,12,view.getCurrentTextColor()),null,null,null);view.setCompoundDrawablePadding(px(7));}
    void icon(TextView view,int resource){view.setCompoundDrawables(icon(resource,16,view.getCurrentTextColor()),null,null,null);view.setCompoundDrawablePadding(px(7));}
    Drawable calendar(int size){
        Drawable art=new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final RectF page=new RectF(3,5,21,22);
            @Override public void draw(Canvas canvas){int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);paint.setColor(INK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawRoundRect(page,2,2,paint);canvas.drawLine(3,10,21,10,paint);canvas.drawLine(7,2,7,7,paint);canvas.drawLine(17,2,17,7,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRect(7,14,10,17,paint);canvas.drawRect(14,14,17,17,paint);canvas.restoreToCount(save);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };art.setBounds(0,0,px(size),px(size));return art;
    }
    static float pickerUnit(float baseUnit) {return baseUnit * 960f / 1040f;}
    static ImageView pickerThumbnail(Context context, float baseUnit) {
        // Resolve the outer tier before insetting the image, so its mask matches the border.
        float unit = pickerUnit(baseUnit);
        int padding = Math.round(CARD_INSET * unit);
        int outerRadius = Math.round(CARD_RADIUS * unit);
        ImageView thumbnail = new ImageView(context) {
            private final Path clip = new Path();
            private final RectF imageBounds = new RectF();

            @Override protected void onDraw(Canvas canvas) {
                imageBounds.set(-padding, -padding, getWidth() + padding, getHeight() + padding);
                float radius = Math.max(0, SurfaceCorners.radius(imageBounds, outerRadius, unit) - padding);
                imageBounds.set(0, 0, getWidth(), getHeight());
                SurfaceCorners.path(clip, imageBounds, radius);
                int saved = canvas.save();
                canvas.clipPath(clip);
                super.onDraw(canvas);
                canvas.restoreToCount(saved);
            }
        };
        if (android.os.Build.VERSION.SDK_INT < 18) thumbnail.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        androidx.core.view.ViewCompat.setImportantForAccessibility(thumbnail, androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return thumbnail;
    }
    static Drawable shade(){
        return new Drawable(){
            final Paint paint=new Paint();
            @Override protected void onBoundsChange(Rect bounds){paint.setShader(new LinearGradient(0,0,0,bounds.height(),new int[]{0x9e000000,0x14000000,0x99000000,0xff000000},new float[]{0,.42f,.64f,1},Shader.TileMode.CLAMP));}
            @Override public void draw(Canvas canvas){canvas.drawRect(getBounds(),paint);}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
            @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    /** Blur one small snapshot on opening, rather than continuously filtering a TV-sized frame. */
    static Bitmap backdrop(View background,View foreground){
        if(!GlassStyle.get(background.getContext()).rendering())return null;
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
