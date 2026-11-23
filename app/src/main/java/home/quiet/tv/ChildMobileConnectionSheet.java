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
    private android.app.Dialog settings, binding, approval;
    private String bindingHost;
    private final Runnable sessionChanged=this::render;
    private final BroadcastReceiver network=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){session.start();render();if(binding!=null&&!android.text.TextUtils.equals(bindingHost,WallpaperUploadPanel.lanAddress(ui)))binding.dismiss();}};
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
        String message="connected".equals(state)?"手机已连接":"waiting".equals(state)?"同一局域网，首次扫码配对":"starting".equals(state)?"正在生成二维码…":"error".equals(state)?session.failure():"network-changed".equals(state)?"网络已变化，按确定重新连接":"连接已结束，按确定重新连接";
        if(available)message+="\n配对码  "+session.pairingCode();
        status.setText(message);card.setContentDescription("手机扫码管理儿童模式  扫码打开网页  "+message);card.setFocusable(!available&&!"starting".equals(state));card.setFocusableInTouchMode(card.isFocusable());
        if(!available){generation++;address="";clearImage();return;}
        String next=session.address()+"?tab=child";if(next.equals(address))return;address=next;int job=++generation;clearImage();
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
        if(session.paired())sheet.option("解除全部网页配对",()->authorize.run(()->{session.revoke();sheet.dialog.dismiss();}));
        sheet.option("绑定 Quiet TV 手机助手",()->authorize.run(()->{
            sheet.dialog.dismiss();
            showAssistantBinding();
        }));
        try{if(ui.getSharedPreferences("child-mobile",0).getBoolean("enabled",false)){ChildMobileServer server=ChildMobileServer.get(ui);JSONArray phones=server.phones();for(int i=0;i<phones.length();i++){JSONObject phone=phones.getJSONObject(i);String id=phone.getString("id");sheet.option("撤销手机助手  "+phone.getString("fingerprint"),()->authorize.run(()->{try{server.revoke(id);sheet.dialog.dismiss();show();ui.success("已撤销此手机的连接授权");}catch(IOException error){ui.error(error.getMessage());}}));}sheet.option("停止手机助手连接服务",()->authorize.run(()->{try{server.end();sheet.dialog.dismiss();ui.success("手机助手连接服务已停止");}catch(IOException error){ui.error(error.getMessage());}}));}}catch(IOException|JSONException error){sheet.text("手机助手连接状态读取失败");}sheet.show(null);
    }
    private void showAssistantBinding(){
        if(!active||binding!=null)return;
        ManagementUi.Sheet sheet=ui.new Sheet("绑定 Quiet TV 手机助手");
        Button close=sheet.settingsLayout("关闭",sheet.dialog::dismiss);
        TextView message=label("正在准备安全连接…",13,UiPalette.TEXT);
        sheet.body.addView(message);
        ImageView code=new ImageView(ui);
        code.setScaleType(ImageView.ScaleType.FIT_CENTER);
        code.setContentDescription("Quiet TV 手机助手绑定二维码");
        code.setVisibility(View.GONE);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(ui.px(160),ui.px(160));
        size.gravity=Gravity.CENTER_HORIZONTAL;
        size.topMargin=ui.px(12);
        sheet.body.addView(code,size);
        bindingHost=WallpaperUploadPanel.lanAddress(ui);
        binding=sheet.dialog;

        // The offer and approval belong to this dialog. Existing phone credentials are retained.
        final class Offer {
            ChildMobileServer server;
            String id;
            Bitmap bitmap;
            void clear(){
                if(server!=null&&id!=null){server.clearOffer(id);id=null;}
                code.setImageDrawable(null);
                code.setVisibility(View.GONE);
                if(bitmap!=null){bitmap.recycle();bitmap=null;}
            }
        }
        Offer offer=new Offer();
        Runnable expire=()->{
            if(binding!=sheet.dialog)return;
            if(approval!=null)approval.dismiss();
            offer.clear();
            message.setText("二维码已过期，请关闭后重新打开绑定入口。");
        };
        sheet.dialog.setOnDismissListener(dialog->{
            if(binding!=sheet.dialog)return;
            binding=null;
            bindingHost=null;
            main.removeCallbacks(expire);
            if(approval!=null)approval.dismiss();
            offer.clear();
        });
        sheet.show(close);
        worker.execute(()->{
            try{
                ChildMobileServer server=ChildMobileServer.get(ui);
                main.post(()->{
                    if(binding!=sheet.dialog)return;
                    if(!server.host.equals(bindingHost)){sheet.dialog.dismiss();return;}
                    try{
                        JSONObject payload=server.offer((fingerprint,allow,deny)->
                            confirmAssistantBinding(sheet.dialog,message,fingerprint,allow,deny));
                        offer.server=server;
                        offer.id=payload.getString("sessionId");
                        String value=payload.toString();
                        main.postDelayed(expire,5*60_000);
                        worker.execute(()->{
                            try{
                                Bitmap bitmap=qrImage(value);
                                main.post(()->{
                                    if(binding!=sheet.dialog||offer.id==null){bitmap.recycle();return;}
                                    offer.bitmap=bitmap;
                                    code.setImageBitmap(bitmap);
                                    ((android.graphics.drawable.BitmapDrawable)code.getDrawable()).setFilterBitmap(false);
                                    code.setVisibility(View.VISIBLE);
                                    message.setText("在 Quiet TV 手机助手的儿童模式中选择扫码绑定，完成电视确认后在手机查看结果。");
                                });
                            }catch(WriterException error){main.post(()->{
                                if(binding!=sheet.dialog||offer.id==null)return;
                                main.removeCallbacks(expire);
                                offer.clear();
                                message.setText("无法生成绑定二维码，请关闭后重试。");
                            });}
                        });
                    }catch(IOException|JSONException error){message.setText("无法准备绑定，请关闭后重试。");}
                });
            }catch(IOException error){main.post(()->{
                if(binding==sheet.dialog)message.setText(error.getMessage());
            });}
        });
    }
    private void confirmAssistantBinding(android.app.Dialog owner,TextView message,String fingerprint,Runnable allow,Runnable deny){
        if(!active||binding!=owner||!owner.isShowing()){deny.run();return;}
        ManagementUi.Sheet ask=ui.new Sheet("允许此手机访问儿童模式？");
        ask.confirmationLayout(ask.dialog::dismiss);
        ask.text("手机标识  "+fingerprint.substring(0,8));
        ask.text("修改规则与立即答题仍需家长验证。");
        approval=ask.dialog;
        boolean[] accepted={false};
        ask.dialog.setOnDismissListener(dialog->{
            approval=null;
            if(!accepted[0])deny.run();
        });
        Button reject=ask.action("拒绝",ask.dialog::dismiss);
        ask.action("允许",()->authorize.run(()->{
            if(!active||binding!=owner||approval!=ask.dialog||!owner.isShowing())return;
            accepted[0]=true;
            allow.run();
            ask.dialog.dismiss();
            message.setText("已处理授权，请在手机查看绑定结果。");
        }));
        ask.show(reject);
    }
    void pause(){active=false;generation++;session.forget(sessionChanged);if(registered){ui.unregisterReceiver(network);registered=false;}if(settings!=null)settings.dismiss();if(binding!=null)binding.dismiss();address="";clearImage();}
    void destroy(){pause();worker.shutdown();}
}
