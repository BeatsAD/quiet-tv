package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;

/** Native 1920 x 1080 artboard matching prototypes/about. All images exist before the first draw. */
final class AboutView extends FrameLayout {
    private final Typeface font;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader glow,shade;
    private final View license,upstream,recovery,update;
    private boolean updateAvailable;
    private final TextView recoveryValue;
    private final ChildModePolicy.TriplePress recoveryPress=new ChildModePolicy.TriplePress();
    private final FrameLayout artboard;
    private final Runnable exit;

    AboutView(Context context,CharSequence name,String version,String channelName,Runnable exit,Runnable licenses,Runnable source,Runnable check) {
        super(context);setBackgroundColor(0xff020405);setTag("about-native");
        this.exit=exit;
        font=android.os.Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif",Typeface.NORMAL);
        glow=new RadialGradient(0,0,1,new int[]{0xff244c87,0xff112846,0xff070e18,0xff020405},new float[]{0,.29f,.58f,.85f},Shader.TileMode.CLAMP);
        Matrix matrix=new Matrix();matrix.setScale(1497.6f*(float)Math.sqrt(2),810*(float)Math.sqrt(2));matrix.postTranslate(422.4f,270);glow.setLocalMatrix(matrix);
        // CSS linear-gradient(100deg, #02070b08, #04070bb8), on the same artboard.
        shade=new LinearGradient(-63,357,1983,723,0x0802070b,0xb804070b,Shader.TileMode.CLAMP);
        artboard=new FrameLayout(context) {
            @Override protected void dispatchDraw(Canvas canvas) {
                paint.setShader(glow);canvas.drawRect(0,0,1920,1080,paint);
                paint.setShader(shade);canvas.drawRect(0,0,1920,1080,paint);paint.setShader(null);
                super.dispatchDraw(canvas);
                if(updateAvailable && update!=null){paint.setColor(0xffd9363e);canvas.drawCircle(update.getRight()-2,update.getTop()+2,9,paint);}

            }
        };
        artboard.setClipChildren(false);artboard.setPivotX(0);artboard.setPivotY(0);
        addView(artboard,new LayoutParams(1920,1080));
        ImageView brand=new ImageView(context);brand.setImageResource(R.drawable.launcher_icon);put(brand,136,315,160,160);
        SpannableString title=new SpannableString(name+".");title.setSpan(new ForegroundColorSpan(0xff89b8f5),title.length()-1,title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView titleView=label(title,104,500,0xfff2f4f8,136,515,626,120,"app-name");UiCompat.letterSpacing(titleView,-3f/104);
        TextView installed=label("版本 "+version,26,400,0xfff2f4f8,136,658,500,45,"version-name");
        int channelX=136+(int)Math.ceil(installed.getPaint().measureText(installed.getText().toString()))+16;
        TextView channel=label(channelName,19,400,0xffacc0db,channelX,658,87,45,"version-channel");
        channel.setGravity(Gravity.CENTER);channel.setBackground(surface(0x08ffffff,8,0x26ffffff));
        TextView checkText=label("检查更新",23,400,0xffeff0f4,136,750,188,64,null);checkText.setGravity(Gravity.CENTER);
        update=action(checkText,"check-update",136,750,188,64,check);update.setBackground(ControlStyle.action(1,32,0x08ffffff,0x26ffffff));
        label("开源项目",29,500,0xfff2f4f8,782,298,600,41,null);
        label("github.com/BeatsAD/quiet-tv",27,400,0xffdbe5f4,782,360,600,50,"source-address");
        ImageView project=code("project-qr",1576,250,208);project.setImageResource(R.drawable.ic_about_source);
        line(492);
        label("联系开发者",29,500,0xfff2f4f8,782,563,600,41,null);
        label("WenBin AI LAB",27,400,0xffc4d1e3,782,625,600,34,null);
        label("wenbinailab@gmail.com",24,400,0xff9eafc7,782,674,600,45,"developer-email");
        code("wechat-qr",1394,528,184).setImageBitmap(image("about/assets/wechat.webp"));
        code("official-qr",1600,528,184).setImageBitmap(image("about/assets/qq-group.webp"));
        label("微信",21,400,0xff9eafc7,1394,725,184,30,null).setGravity(Gravity.CENTER);
        label("扫码进 QQ交流群",21,400,0xff9eafc7,1600,725,184,30,null).setGravity(Gravity.CENTER);
        line(790);
        label("开源许可与致谢",23,500,0xffb9c8dc,782,819,1002,33,null);
        TextView mit=label("MIT 许可证 ↗",21,400,0xffa7b9d1,775,863,135,41,null);mit.setPadding(7,0,7,0);
        license=action(mit,"license-link",775,863,135,41,licenses);
        label("基于",21,400,0xff8394ad,939,863,45,41,null);
        TextView origin=label("tv-vibehack",21,400,0xffa7b9d1,978,863,124,41,null);origin.setPadding(7,0,7,0);
        upstream=action(origin,"upstream-link",978,863,124,41,source);
        TextView thanks=label("  感谢 Gathin",21,400,0xff8394ad,1102,863,300,41,null);
        int recoveryX=1102+(int)Math.ceil(thanks.getPaint().measureText(thanks.getText().toString()))+28;
        TextView recoveryText=label("恢复码",21,400,0xffa7b9d1,recoveryX,863,76,41,null);
        recovery=action(recoveryText,"recovery-link",recoveryX,863,76,41,this::revealRecovery);
        recovery.setOnFocusChangeListener((view,focused)->{if(!focused)recoveryPress.clear();artboard.invalidate();});
        recoveryValue=label("",21,400,0xffdbe5f4,recoveryX+104,863,220,41,"recovery-value");
        recoveryValue.setVisibility(View.INVISIBLE);
    }

    private GradientDrawable surface(int color,float radius,int stroke) {
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(radius);drawable.setStroke(1,stroke);return drawable;
    }
    private void put(View view,int x,int y,int w,int h) {
        LayoutParams params=new LayoutParams(w,h);params.leftMargin=x;params.topMargin=y;artboard.addView(view,params);
    }
    private TextView label(CharSequence value,float size,int weight,int color,int x,int y,int w,int h,String tag) {
        TextView view=new TextView(getContext());view.setText(value);view.setTag(tag);view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);view.setTypeface(android.os.Build.VERSION.SDK_INT>=28?Typeface.create(font,weight,false):Typeface.create(font,weight>=600?Typeface.BOLD:Typeface.NORMAL));if(android.os.Build.VERSION.SDK_INT>=26)view.setFontVariationSettings("'wght' "+weight);
        view.setIncludeFontPadding(false);view.setSingleLine(true);view.setGravity(Gravity.CENTER_VERTICAL);put(view,x,y,w,h);return view;
    }
    private View action(View view,String tag,int x,int y,int w,int h,Runnable run) {
        if(view.getParent()==null)put(view,x,y,w,h);
        view.setBackground(ControlStyle.action(2,5,Color.TRANSPARENT,0));
        view.setTag(tag);view.setFocusable(true);view.setFocusableInTouchMode(true);view.setOnClickListener(v->run.run());
        view.setOnFocusChangeListener((v,focused)->artboard.invalidate());return view;
    }
    private ImageView code(String tag,int x,int y,int size) {
        ImageView view=new ImageView(getContext());view.setTag(tag);view.setContentDescription(tag.equals("project-qr")?"开源项目二维码":tag.equals("wechat-qr")?"微信二维码":"QQ 群二维码");
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setPadding(4,4,4,4);view.setBackground(surface(Color.WHITE,size==208?14:10,Color.WHITE));UiCompat.clipOutline(view);put(view,x,y,size,size);return view;
    }
    private Bitmap image(String asset) {
        try(InputStream stream=getContext().getAssets().open(asset)) {
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);
            if(bitmap==null)throw new IllegalStateException("Invalid QR asset: "+asset);return bitmap;
        } catch(IOException e){throw new IllegalStateException("Missing QR asset",e);}
    }
    private void line(int y){View line=new View(getContext());line.setBackgroundColor(0x25ffffff);put(line,782,y,1002,1);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        float height=720,scale=Math.min(w/1920f,h/height);artboard.setScaleX(scale);artboard.setScaleY(scale);
        artboard.setTranslationX((w-1920*scale)/2);artboard.setTranslationY((h-height*scale)/2-220*scale);
    }
    private void revealRecovery(){
        if(!recoveryPress.press(android.os.SystemClock.elapsedRealtime()))return;
        String value=new ChildModeStore(getContext()).recovery();recoveryValue.setText(value.isEmpty()?"尚未设置家长密码":value);recoveryValue.setVisibility(View.VISIBLE);
    }
    void hideRecovery(){press(false);recoveryPress.clear();recoveryValue.setText("");recoveryValue.setVisibility(View.INVISIBLE);}
    void updateAvailable(boolean value){updateAvailable=value;update.setContentDescription(value?"检查更新，有新版本":"检查更新");artboard.invalidate();}
    void focusUpdate(){update.requestFocus();}
    void focusLicense(){license.requestFocus();}
    void move(int key) {
        View active=artboard.findFocus(),next=null;
        if(active==update){if(key==KeyEvent.KEYCODE_DPAD_UP){exit.run();return;}else if(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN)next=license;}
        else if(active==license){if(key==KeyEvent.KEYCODE_DPAD_RIGHT)next=upstream;else if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_LEFT)next=update;}
        else if(active==upstream){if(key==KeyEvent.KEYCODE_DPAD_LEFT)next=license;else if(key==KeyEvent.KEYCODE_DPAD_UP){exit.run();return;}else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)next=recovery;}
        else if(active==recovery){if(key==KeyEvent.KEYCODE_DPAD_LEFT)next=upstream;else if(key==KeyEvent.KEYCODE_DPAD_UP){exit.run();return;}}
        if(next!=null)next.requestFocus();
    }
    void press(boolean pressed){View view=artboard.findFocus();if(view!=null)view.setPressed(pressed);}
    void activate(){press(false);View view=artboard.findFocus();if(view!=null)view.performClick();}
}
