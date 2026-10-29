package home.quiet.tv;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** The wallpaper and app-cover uploaders use this exact native layout. */
final class ImageUploadDialog {
    final Dialog dialog;
    final ImageView background,qr;
    final TextView address,status,retry;
    private final TextView code;
    private final Activity activity;
    private final WallpaperUi ui;
    private final boolean chinese;
    private final Bitmap backdrop;
    ImageUploadDialog(Activity activity,boolean chinese,Bitmap backdrop,String title,String requirements,String note,Runnable closeAction,Runnable reconnect){
        this(activity,chinese,backdrop,title,requirements,note,closeAction,reconnect,null);
    }
    ImageUploadDialog(Activity activity,boolean chinese,Bitmap backdrop,String title,String requirements,String note,Runnable closeAction,Runnable reconnect,String selection){
        this.activity=activity;this.ui=new WallpaperUi(activity,activity.getResources().getDisplayMetrics().widthPixels/960f);this.chinese=chinese;this.backdrop=backdrop;
        dialog=new KeyFeedback.Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(activity);background=new ImageView(activity);background.setScaleType(ImageView.ScaleType.FIT_XY);background.setImageBitmap(backdrop);stage.addView(background,new FrameLayout.LayoutParams(-1,-1));
        View dim=new View(activity);dim.setBackgroundColor(0xbf070b12);stage.addView(dim,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout panel=new FrameLayout(activity);panel.setBackgroundDrawable(ui.surface(0xff222831,23,0x20ffffff,1));if(android.os.Build.VERSION.SDK_INT>=21)panel.setElevation(ui.px(18));
        LinearLayout box=column();box.setPadding(ui.px(30),ui.px(30),ui.px(30),ui.px(30));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);TextView heading=ui.text(title,23);heading.setSingleLine();heading.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.button("",false,()->{closeAction.run();KeyFeedback.back(activity);});close.setContentDescription(tr("关闭上传","Close upload"));close.setPadding(ui.px(8),ui.px(8),ui.px(8),ui.px(8));close.setCompoundDrawables(ui.icon(R.drawable.wp_x,17,WallpaperUi.INK),null,null,null);close.setBackgroundDrawable(ui.focusSurface(0xff343c48,100));
        header.addView(close,new LinearLayout.LayoutParams(ui.px(33),ui.px(33)));box.addView(header,new LinearLayout.LayoutParams(-1,ui.px(42)));
        LinearLayout connection=new LinearLayout(activity);connection.setGravity(Gravity.CENTER_VERTICAL);connection.setMinimumHeight(ui.px(258));LinearLayout steps=column();
        step(steps,"1",tr("连接同一个局域网","Connect to the same local network"),tr("电视可使用 Wi-Fi 或网线连接","Your TV can use Wi-Fi or Ethernet"),46);
        LinearLayout second=step(steps,"2",tr("手机扫码，或在电脑浏览器打开","Scan with your phone, or open on your computer"),null,64);
        address=ui.text(tr("正在开启上传…","Starting upload…"),15);address.setPadding(0,ui.px(8),0,0);second.addView(address);
        LinearLayout third=step(steps,"3",selection==null?tr("选择图片，上传后在电视上应用","Upload a photo, then apply it on your TV"):selection,requirements,46);
        if(note!=null){TextView hint=ui.text(note,11);hint.setTextColor(WallpaperUi.MUTED);hint.setPadding(0,ui.px(8),0,0);third.addView(hint);}
        connection.addView(steps,new LinearLayout.LayoutParams(0,-2,1));connection.addView(new View(activity),new LinearLayout.LayoutParams(ui.px(28),1));
        LinearLayout qrColumn=column();qrColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout qrBox=new FrameLayout(activity);qrBox.setBackgroundDrawable(ui.surface(Color.WHITE,15,Color.TRANSPARENT,0));qrBox.setPadding(ui.px(13),ui.px(13),ui.px(13),ui.px(13));
        qr=new ImageView(activity);qr.setContentDescription(tr("上传地址二维码","Upload address QR code"));qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qrBox.addView(qr,new FrameLayout.LayoutParams(-1,-1));qrColumn.addView(qrBox,new LinearLayout.LayoutParams(ui.px(170),ui.px(170)));
        code=ui.text("",32);code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);code.setGravity(Gravity.CENTER);code.setLineSpacing(ui.px(4),1);code.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams codeParams=new LinearLayout.LayoutParams(-1,ui.px(64));codeParams.topMargin=ui.px(12);qrColumn.addView(code,codeParams);
        connection.addView(qrColumn,new LinearLayout.LayoutParams(ui.px(190),-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=ui.px(24);box.addView(connection,cp);
        status=ui.text("",12);status.setTextColor(WallpaperUi.MUTED);status.setVisibility(View.GONE);androidx.core.view.ViewCompat.setAccessibilityLiveRegion(status,androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=ui.px(16);box.addView(status,st);
        retry=ui.button(tr("重新连接","Reconnect"),false,reconnect);retry.setVisibility(View.GONE);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);rp.topMargin=ui.px(12);box.addView(retry,rp);close.setNextFocusDownId(retry.getId());retry.setNextFocusUpId(close.getId());
        panel.addView(box,new FrameLayout.LayoutParams(-1,-2));
        stage.addView(panel,new FrameLayout.LayoutParams(ui.px(680),-2,Gravity.CENTER));dialog.setContentView(stage);
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setDimAmount(0);window.getDecorView().setSystemUiVisibility(5894);}
        dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null)w.setLayout(-1,-1);close.requestFocus();});
    }
    private String tr(String zh,String en){return chinese?zh:en;}
    void resetConnection(){
        address.setText(tr("正在开启上传…","Starting upload…"));address.setVisibility(View.VISIBLE);code.setText("");code.setContentDescription(null);code.setVisibility(View.INVISIBLE);qr.setImageDrawable(null);
    }
    void connected(String url,String digits){
        address.setText(url);connectionCode(digits);code.setVisibility(View.VISIBLE);
    }
    private void connectionCode(String digits){
        String prefix=tr("连接码\n","Connection code\n");android.text.SpannableString value=new android.text.SpannableString(prefix+digits);
        value.setSpan(new android.text.style.AbsoluteSizeSpan(ui.px(11)),0,prefix.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new android.text.style.ForegroundColorSpan(WallpaperUi.MUTED),0,prefix.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        code.setText(value);code.setContentDescription(tr("连接码  ","Connection code  ")+digits);
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout step(LinearLayout parent,String number,String title,String detail,int minimum){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.TOP);TextView badge=ui.text(number,11);badge.setGravity(Gravity.CENTER);badge.setBackgroundDrawable(ui.surface(0xff3d4857,100,Color.TRANSPARENT,0));row.addView(badge,new LinearLayout.LayoutParams(ui.px(23),ui.px(23)));
        LinearLayout body=column();body.setPadding(ui.px(11),ui.px(2),0,0);body.addView(ui.text(title,13));
        if(detail!=null){TextView hint=ui.text(detail,11);hint.setTextColor(WallpaperUi.MUTED);hint.setPadding(0,ui.px(5),0,0);body.addView(hint);}row.addView(body,new LinearLayout.LayoutParams(0,-2,1));row.setMinimumHeight(ui.px(minimum));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);if(parent.getChildCount()>0)p.topMargin=ui.px(12);parent.addView(row,p);return body;
    }
    void release(){background.setImageDrawable(null);qr.setImageDrawable(null);if(!backdrop.isRecycled())backdrop.recycle();}
}
