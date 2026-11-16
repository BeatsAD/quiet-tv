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

/** Inline pairing owns only its current offer; saved phone authorizations outlive the page. */
final class ChildMobileConnectionSheet {
    private final ManagementUi ui;
    private final ChildQuizSettingsSheet.Authorize authorize;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinearLayout card;
    private final ImageView image;
    private final TextView status;
    private ChildMobileServer server;
    private Bitmap qr;
    private volatile int generation;
    private boolean active,reading,registered;
    private String offer="",host="";
    private android.app.Dialog approval,settings;
    private final Runnable refresh=()->{if(!active)return;if(server!=null&&server.pairingPending()){main.postDelayed(this.refresh,10_000);return;}prepare();};
    private final BroadcastReceiver network=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){String next=WallpaperUploadPanel.lanAddress(ui);if(!(host==null?next==null:host.equals(next)))prepare();}};
    ChildMobileConnectionSheet(ManagementUi ui,ChildQuizSettingsSheet.Authorize authorize){
        this.ui=ui;this.authorize=authorize;card=ui.row();card.setTag("child-mobile-qr");card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(ui.px(12),ui.px(12),ui.px(12),ui.px(12));card.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));
        image=new QrCodeView(ui,ui.unit);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("手机助手儿童模式连接二维码");card.addView(image,new LinearLayout.LayoutParams(ui.px(108),ui.px(108)));
        LinearLayout copy=ui.column();TextView title=label("手机扫码管理儿童模式",14,UiPalette.TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));copy.addView(title);copy.addView(label("使用手机助手扫码",12,UiPalette.SECONDARY));status=label("同一局域网",11,UiPalette.FAINT);copy.addView(status);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=ui.px(16);card.addView(copy,cp);
        card.setOnClickListener(v->prepare());SettingsTouchClick.attach(card);
    }
    LinearLayout view(){return card;}
    private TextView label(String value,float size,int color){TextView text=ui.label(value,12);text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(size));text.setTextColor(color);text.setPadding(0,ui.px(3),0,ui.px(3));return text;}
    void resume(){if(active)return;active=true;androidx.core.content.ContextCompat.registerReceiver(ui,network,new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);registered=true;prepare();}
    private void prepare(){
        if(!active||reading)return;reading=true;int job=++generation;main.removeCallbacks(refresh);host=WallpaperUploadPanel.lanAddress(ui);status.setText("正在准备连接…");clearImage();
        worker.execute(()->{if(job!=generation)return;try{ChildMobileServer ready=ChildMobileServer.get(ui);main.post(()->{
            if(!active||job!=generation)return;
            if(!ready.host.equals(WallpaperUploadPanel.lanAddress(ui))){reading=false;prepare();return;}
            server=ready;
            // Offer ownership stays on the lifecycle thread; encoding never blocks it.
            try{JSONObject value=server.offer(this::approve);String session=value.getString("sessionId");offer=session;String payload=value.toString();
                worker.execute(()->{if(job!=generation){ready.clearOffer(session);return;}try{Bitmap bitmap=qrImage(payload);main.post(()->{
                    if(!active||job!=generation){bitmap.recycle();ready.clearOffer(session);return;}
                    reading=false;
                    if(!ready.host.equals(WallpaperUploadPanel.lanAddress(ui))){bitmap.recycle();ready.clearOffer(session);offer="";prepare();return;}
                    clearImage();qr=bitmap;image.setImageBitmap(qr);((android.graphics.drawable.BitmapDrawable)image.getDrawable()).setFilterBitmap(false);status.setText("同一局域网");card.setContentDescription("手机扫码管理儿童模式  使用手机助手扫码  同一局域网");card.setFocusable(false);main.postDelayed(refresh,270_000);
                });}catch(Exception e){main.post(()->{ready.clearOffer(session);if(active&&job==generation){reading=false;offer="";failed("连接准备失败，按确定重试");}});}});
            }catch(Exception e){reading=false;failed("连接准备失败，按确定重试");}
        });}catch(IOException e){main.post(()->{if(active&&job==generation){reading=false;failed(e.getMessage()==null?"连接准备失败，按确定重试":e.getMessage()+"  按确定重试");}});}});
    }
    private static Bitmap qrImage(String value)throws WriterException{
        com.google.zxing.common.BitMatrix matrix=new QRCodeWriter().encode(value,BarcodeFormat.QR_CODE,0,0,Collections.singletonMap(EncodeHintType.CHARACTER_SET,"UTF-8"));int size=matrix.getWidth()*4;int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x/4,y/4)?Color.BLACK:Color.WHITE;
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    private void failed(String message){clearImage();status.setText(message);card.setFocusable(true);card.setFocusableInTouchMode(true);card.setContentDescription("手机连接  "+message);}
    private void clearImage(){image.setImageDrawable(null);if(qr!=null){qr.recycle();qr=null;}}
    private void approve(String fingerprint,Runnable allow,Runnable deny){
        if(!active){deny.run();return;}ManagementUi.Sheet ask=ui.new Sheet("允许此手机访问儿童模式？");ask.text("手机标识  "+fingerprint.substring(0,8));ask.text("修改规则与立即答题需要家长密码。");boolean[] decided={false};ask.dialog.setOnDismissListener(d->{approval=null;if(!decided[0])deny.run();});
        Button reject=ask.action("拒绝",()->{decided[0]=true;deny.run();ask.dialog.dismiss();});ask.action("允许",()->authorize.run(()->{if(active){decided[0]=true;allow.run();ask.dialog.dismiss();status.setText("已允许此手机连接");}else{deny.run();ask.dialog.dismiss();}}));approval=ask.dialog;ask.show(reject);
    }
    void show(){
        if(settings!=null){settings.show();return;}ManagementUi.Sheet sheet=ui.new Sheet("已连接手机");sheet.settingsLayout("关闭",sheet.dialog::dismiss);settings=sheet.dialog;sheet.dialog.setOnDismissListener(d->settings=null);
        try{if(server==null){sheet.text("手机扫码连接后会在这里显示。");}else{JSONArray phones=server.phones();if(phones.length()==0)sheet.text("尚未连接手机。");for(int i=0;i<phones.length();i++){JSONObject phone=phones.getJSONObject(i);String id=phone.getString("id");sheet.option("撤销手机  "+phone.getString("fingerprint"),()->authorize.run(()->{try{server.revoke(id);sheet.dialog.dismiss();show();}catch(IOException error){ui.feedback(error.getMessage());}}));}sheet.option("停止连接服务",()->authorize.run(()->{try{server.end();sheet.dialog.dismiss();main.removeCallbacks(refresh);failed("连接已停止，按确定重新连接");}catch(IOException error){ui.feedback(error.getMessage());}}));}}catch(IOException|JSONException error){sheet.text("手机连接状态读取失败");}sheet.show(null);
    }
    void pause(){active=false;generation++;reading=false;main.removeCallbacks(refresh);if(registered){ui.unregisterReceiver(network);registered=false;}if(server!=null&&!offer.isEmpty())server.clearOffer(offer);offer="";if(approval!=null)approval.dismiss();approval=null;if(settings!=null)settings.dismiss();clearImage();}
    void destroy(){pause();worker.shutdown();}
}
