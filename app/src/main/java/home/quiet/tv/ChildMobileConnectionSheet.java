package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Inline browser entry shares the existing phone settings session. */
final class ChildMobileConnectionSheet {
    private final ManagementUi ui;
    private final ChildQuizSettingsSheet.Authorize authorize;
    private final PhoneSettingsSession session;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout card;
    private final ImageView image;
    private final TextView status;
    private Bitmap qr;
    private volatile int generation;
    private boolean active,registered;
    private String address="";
    private android.app.Dialog settings;
    private final Runnable sessionChanged=this::render;
    private final BroadcastReceiver network=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){session.start();render();}};
    ChildMobileConnectionSheet(ManagementUi ui,ChildQuizSettingsSheet.Authorize authorize){
        this.ui=ui;this.authorize=authorize;session=PhoneSettingsSession.get(ui);card=ui.row();card.setTag("child-mobile-qr");card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(ui.px(12),ui.px(12),ui.px(12),ui.px(12));card.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));
        image=new QrCodeView(ui,ui.unit);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("手机网页儿童模式连接二维码");card.addView(image,new LinearLayout.LayoutParams(ui.px(108),ui.px(108)));
        LinearLayout copy=ui.column();TextView title=label("手机扫码管理儿童模式",14,UiPalette.TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));copy.addView(title);copy.addView(label("扫码打开网页",12,UiPalette.SECONDARY));status=label("同一局域网",11,UiPalette.FAINT);copy.addView(status);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=ui.px(16);card.addView(copy,cp);
        card.setOnClickListener(v->prepare());SettingsTouchClick.attach(card);
    }
    LinearLayout view(){return card;}
    private TextView label(String value,float size,int color){TextView text=ui.label(value,12);text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(size));text.setTextColor(color);text.setPadding(0,ui.px(3),0,ui.px(3));return text;}
    void resume(){if(active)return;active=true;session.observe(sessionChanged);androidx.core.content.ContextCompat.registerReceiver(ui,network,new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;prepare();}
    private void prepare(){if(!active)return;session.start();address="";render();}
    private void render(){
        if(!active)return;String state=session.state();if("qr-expired".equals(state)){session.start();state=session.state();}
        boolean available="waiting".equals(state)||"connected".equals(state);
        String message="connected".equals(state)?"手机已连接":"waiting".equals(state)?"同一局域网":"starting".equals(state)?"正在生成二维码…":"error".equals(state)?session.failure():"network-changed".equals(state)?"网络已变化，按确定重新连接":"连接已结束，按确定重新连接";
        status.setText(message);card.setContentDescription("手机扫码管理儿童模式  扫码打开网页  "+message);card.setFocusable(!available&&!"starting".equals(state));card.setFocusableInTouchMode(card.isFocusable());
        if(!available){generation++;address="";clearImage();return;}
        String next=session.address()+"#tab=child";if(next.equals(address))return;address=next;int job=++generation;clearImage();
        worker.execute(()->{if(job!=generation)return;try{Bitmap bitmap=qrImage(next);main.post(()->{
            if(!active||job!=generation){bitmap.recycle();return;}qr=bitmap;image.setImageBitmap(qr);((android.graphics.drawable.BitmapDrawable)image.getDrawable()).setFilterBitmap(false);
        });}catch(WriterException e){main.post(()->{if(active&&job==generation){address="";failed("二维码生成失败，按确定重试");}});}});
    }
    private static Bitmap qrImage(String value)throws WriterException{
        com.google.zxing.common.BitMatrix matrix=new QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,0,0,Collections.singletonMap(EncodeHintType.CHARACTER_SET,"UTF-8"));int size=matrix.getWidth()*4;int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x/4,y/4)?Color.BLACK:Color.WHITE;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    private void failed(String message){clearImage();status.setText(message);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setContentDescription("手机连接  "+message);}
    private void clearImage(){image.setImageDrawable(null);if(qr!=null){qr.recycle();qr=null;}}
    void show(){
        if(settings!=null){settings.show();return;}ManagementUi.Sheet sheet=ui.new Sheet("已连接手机");sheet.settingsLayout("关闭",sheet.dialog::dismiss);settings=sheet.dialog;sheet.dialog.setOnDismissListener(d->settings=null);
        boolean connected="connected".equals(session.state());sheet.text(connected?"手机网页已连接":"尚未连接手机网页");
        sheet.option(connected?"结束网页连接":"重新连接网页",()->authorize.run(()->{session.restart();sheet.dialog.dismiss();}));
        try{if(ui.getSharedPreferences("child-mobile",0).getBoolean("enabled",false)){ChildMobileServer server=ChildMobileServer.get(ui);JSONArray phones=server.phones();for(int i=0;i<phones.length();i++){JSONObject phone=phones.getJSONObject(i);String id=phone.getString("id");sheet.option("撤销手机助手  "+phone.getString("fingerprint"),()->authorize.run(()->{try{server.revoke(id);sheet.dialog.dismiss();show();}catch(IOException error){ui.feedback(error.getMessage());}}));}sheet.option("停止手机助手连接服务",()->authorize.run(()->{try{server.end();sheet.dialog.dismiss();}catch(IOException error){ui.feedback(error.getMessage());}}));}}catch(IOException|JSONException error){sheet.text("手机助手连接状态读取失败");}sheet.show(null);
    }
    void pause(){active=false;generation++;session.forget(sessionChanged);if(registered){ui.unregisterReceiver(network);registered=false;}if(settings!=null)settings.dismiss();address="";clearImage();}
    void destroy(){pause();worker.shutdown();}
}
