package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;

/** Native version-update content. Images exist before the first draw. */
final class AboutView extends FrameLayout {
    private final Typeface font;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final View license,upstream,recovery,update;
    private boolean updateAvailable;
    private final TextView recoveryValue;
    private final TextView memoryValue,storageValue,displayLabel,displayValue;
    private final FrameLayout artboard;
    private final Runnable exit;

    AboutView(Context context,CharSequence name,String version,String channelName,AboutDeviceInfo device,Runnable exit,Runnable licenses,Runnable source,Runnable check,Runnable recoveryAction) {
        super(context);setBackgroundColor(Color.TRANSPARENT);setTag("about-native");
        this.exit=exit;
        font=android.os.Build.VERSION.SDK_INT>=26?Typeface.createFromAsset(context.getAssets(),"fonts/outfit.ttf"):Typeface.create("sans-serif",Typeface.NORMAL);
        artboard=new FrameLayout(context) {
            @Override protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if(updateAvailable && update!=null){paint.setColor(0xffd9363e);canvas.drawCircle(update.getRight()-2,update.getTop()+2,9,paint);}

            }
        };
        artboard.setClipChildren(false);artboard.setPivotX(0);artboard.setPivotY(0);
        addView(artboard,new LayoutParams(1920,850));
        ImageView brand=new ImageView(context);brand.setImageResource(R.drawable.launcher_icon);put(brand,24,50,152,152);
        TextView titleView=label(name,84,500,0xfff2f4f8,24,270,470,110,"app-name");UiCompat.letterSpacing(titleView,-3f/104);
        TextView installed=label("版本 "+version,28,400,0xfff2f4f8,24,412,470,42,"version-name");
        int channelX=24+(int)Math.ceil(installed.getPaint().measureText(installed.getText().toString()))+16;
        TextView channel=label(channelName,21,400,0xffacc0db,channelX,412,94,42,"version-channel");
        channel.setGravity(Gravity.CENTER);channel.setBackgroundDrawable(surface(0x08ffffff,8,0x26ffffff));
        TextView checkText=label("检查更新",28,400,0xffeff0f4,24,560,400,72,null);checkText.setGravity(Gravity.CENTER);
        update=action(checkText,"check-update",24,560,400,72,check);update.setBackgroundDrawable(ControlStyle.action(1,36,0x08ffffff,0x26ffffff));

        divider(540,20,1,744);divider(1308,20,1,744);
        label("电视信息",34,500,0xfff2f4f8,600,20,670,48,null);
        multiline(label(device.model,36,500,0xfff2f4f8,600,76,670,120,"device-model"));
        label(device.brand,27,400,0xffc4d1e3,600,196,670,40,"device-brand");
        divider(600,244,670,1);
        metric("安卓版本",device.android,600,266,338,"device-android");
        metric("CPU",device.cpu,966,266,304,"device-cpu");
        metric("系统架构",device.abis,600,388,338,"device-abis");
        memoryValue=metric("运行内存","正在读取",966,388,304,"device-memory");
        storageValue=metric("存储空间","正在读取",600,510,338,"device-storage");
        displayLabel=label("当前显示模式",24,400,0xff9eafc7,966,510,304,34,"device-display-label");
        displayValue=multiline(label("正在读取",28,400,0xffdbe5f4,966,548,304,78,"device-display"));
        divider(600,638,670,1);
        label("固件版本",24,400,0xff9eafc7,600,654,670,34,null);
        multiline(label(device.firmware,28,400,0xffdbe5f4,600,692,670,84,"device-firmware"));

        label("开源项目",34,500,0xfff2f4f8,1368,20,528,48,null);
        label("github.com/BeatsAD/quiet-tv",26,400,0xffdbe5f4,1368,96,528,46,"source-address");
        divider(1368,176,528,1);
        code("official-qr",1500,320,264).setImageBitmap(image("about/assets/qq-group.webp"));
        label("扫码进 QQ 交流群",24,400,0xff9eafc7,1408,608,448,34,null).setGravity(Gravity.CENTER);

        divider(24,784,1872,1);
        TextView mit=label("MIT 许可证 ↗",24,400,0xffa7b9d1,17,796,184,42,null);mit.setPadding(7,0,7,0);
        license=action(mit,"license-link",17,796,184,42,licenses);
        label("基于",24,400,0xff8394ad,235,796,56,42,null);
        TextView origin=label("tv-vibehack",24,400,0xffa7b9d1,295,796,164,42,null);origin.setPadding(7,0,7,0);
        upstream=action(origin,"upstream-link",295,796,164,42,source);
        label("感谢 Gathin",24,400,0xff8394ad,500,796,260,42,null);
        TextView recoveryText=label("恢复码",24,400,0xffa7b9d1,1792,796,104,42,null);
        recovery=action(recoveryText,"recovery-link",1792,796,104,42,recoveryAction);
        recovery.setOnFocusChangeListener((view,focused)->{artboard.invalidate();});
        recoveryValue=label("",24,400,0xffdbe5f4,1370,796,394,42,"recovery-value");recoveryValue.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
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
        view.setIncludeFontPadding(false);view.setSingleLine(true);view.setEllipsize(TextUtils.TruncateAt.END);view.setGravity(Gravity.CENTER_VERTICAL);put(view,x,y,w,h);return view;
    }
    private TextView multiline(TextView view){view.setSingleLine(false);view.setMaxLines(2);view.setGravity(Gravity.TOP|Gravity.LEFT);return view;}
    private TextView metric(String title,String value,int x,int y,int width,String tag){
        label(title,24,400,0xff9eafc7,x,y,width,34,null);
        return multiline(label(value,28,400,0xffdbe5f4,x,y+38,width,78,tag));
    }
    void resources(AboutDeviceInfo.Resources info){memoryValue.setText(info.memory);storageValue.setText(info.storage);}
    void screen(AboutDeviceInfo.Screen screen){displayLabel.setText(screen.label);displayValue.setText(screen.value);}
    private View action(View view,String tag,int x,int y,int w,int h,Runnable run) {
        if(view.getParent()==null)put(view,x,y,w,h);
        view.setBackgroundDrawable(ControlStyle.action(2,5,Color.TRANSPARENT,0));
        view.setTag(tag);view.setFocusable(true);view.setFocusableInTouchMode(true);view.setOnClickListener(v->KeyFeedback.clicked(v,run));
        view.setOnFocusChangeListener((v,focused)->artboard.invalidate());return view;
    }
    private ImageView code(String tag,int x,int y,int size) {
        ImageView view=new ImageView(getContext());view.setTag(tag);view.setContentDescription(tag.equals("project-qr")?"开源项目二维码":tag.equals("wechat-qr")?"微信二维码":"QQ 群二维码");
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setPadding(4,4,4,4);view.setBackgroundDrawable(surface(Color.WHITE,size==208?14:10,Color.WHITE));UiCompat.clipOutline(view);put(view,x,y,size,size);return view;
    }
    private Bitmap image(String asset) {
        try(InputStream stream=getContext().getAssets().open(android.os.Build.VERSION.SDK_INT<18?asset.replace(".webp",".png"):asset)) {
            BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
            Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);
            if(bitmap==null)throw new IllegalStateException("Invalid QR asset: "+asset);return bitmap;
        } catch(IOException e){throw new IllegalStateException("Missing QR asset",e);}
    }
    private void divider(int x,int y,int width,int height){View line=new View(getContext());line.setBackgroundColor(0x25ffffff);put(line,x,y,width,height);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        float scale=Math.min(w/1920f,h/850f);artboard.setScaleX(scale);artboard.setScaleY(scale);
        artboard.setTranslationX((w-1920*scale)/2);artboard.setTranslationY((h-850*scale)/2);
    }
    void revealRecovery(){
        String value=new ChildModeStore(getContext()).recovery();recoveryValue.setText(value.isEmpty()?"尚未设置家长密码":value);recoveryValue.setVisibility(View.VISIBLE);
    }
    void hideRecovery(){press(false);recoveryValue.setText("");recoveryValue.setVisibility(View.INVISIBLE);}
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
