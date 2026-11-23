package home.quiet.tv;

import android.content.SharedPreferences;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Collections;

/** A QR entry into the existing phone session, bound to one package. */
final class AppRenameSheet {
    private final ManagementUi ui;
    private final PhoneSettingsSession session;
    private final SharedPreferences preferences;
    private final Handler main=new Handler(Looper.getMainLooper());
    private ManagementUi.Sheet sheet;
    private String pkg,alias,address="";
    private ImageView image;
    private TextView status;
    private Button retry;
    private Bitmap qr;
    private Runnable saved;
    private View origin;
    private final Runnable sessionChanged=this::render;
    private final SharedPreferences.OnSharedPreferenceChangeListener names=(prefs,key)->{if(AppNameStore.KEY.equals(key))main.post(this::changed);};
    AppRenameSheet(ManagementUi ui){this.ui=ui;session=PhoneSettingsSession.get(ui);preferences=ui.getSharedPreferences("dock",0);}
    void show(AppRepository.App app,Runnable saved){
        if(sheet!=null)return;pkg=app.pkg;alias=new AppNameStore(ui).alias(pkg);this.saved=saved;origin=ui.getCurrentFocus();
        sheet=ui.new Sheet("重命名应用");sheet.settingsLayout("关闭",this::close);sheet.contentWidth(380,500);sheet.dialog.setOnDismissListener(dialog->release());
        sheet.text(app.label);if(!alias.isEmpty())sheet.text("原名称："+app.originalName);
        LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);
        image=new QrCodeView(ui,ui.unit);image.setContentDescription("手机重命名应用二维码");image.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(image,new LinearLayout.LayoutParams(ui.px(120),ui.px(120)));
        LinearLayout copy=ui.column();copy.addView(ui.label("请用手机扫码修改应用名称",14));copy.addView(ui.muted("手机与电视连接同一局域网",11));status=ui.muted("正在生成二维码…",11);copy.addView(status);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=ui.px(18);row.addView(copy,cp);sheet.body.addView(row);
        retry=sheet.action("重新连接",session::restart);retry.setVisibility(View.GONE);
        preferences.registerOnSharedPreferenceChangeListener(names);session.observe(sessionChanged);session.start();render();sheet.show(sheet.box.findViewWithTag("sheet-close"));
    }
    private void render(){
        if(sheet==null)return;String state=session.state();boolean available="waiting".equals(state)||"connected".equals(state);retry.setVisibility("waiting".equals(state)||"starting".equals(state)?View.GONE:View.VISIBLE);sheet.settingsActions(retry.getVisibility()==View.VISIBLE);
        String message="connected".equals(state)?"手机已连接，请在手机保存":"waiting".equals(state)?"等待手机扫码":"starting".equals(state)?"正在生成二维码…":"error".equals(state)?session.failure():"连接已失效，请重新连接";
        status.setText(available?message+"\n配对码  "+session.pairingCode():message);
        String next=available?session.address()+"?appRename="+android.net.Uri.encode(pkg):"";if(next.equals(address))return;address=next;releaseQr();if(next.isEmpty())return;
        try{BitMatrix matrix=new QRCodeWriter().encode(next,BarcodeFormat.QR_CODE,0,0,Collections.singletonMap(EncodeHintType.MARGIN,2));int size=matrix.getWidth()*8;int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x/8,y/8)?Color.BLACK:Color.WHITE;qr=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);image.setImageBitmap(qr);}
        catch(WriterException error){status.setText("二维码生成失败，请重新连接");retry.setVisibility(View.VISIBLE);}
    }
    private void changed(){if(sheet==null||new AppNameStore(ui).alias(pkg).equals(alias))return;Runnable completed=saved;close();if(completed!=null)main.post(completed);}
    private void releaseQr(){if(image!=null)image.setImageDrawable(null);if(qr!=null){qr.recycle();qr=null;}}
    private void release(){if(sheet==null)return;sheet=null;session.forget(sessionChanged);preferences.unregisterOnSharedPreferenceChangeListener(names);releaseQr();address="";saved=null;if(origin!=null&&origin.getWindowToken()!=null)origin.requestFocus();origin=null;}
    void close(){if(sheet!=null){ManagementUi.Sheet old=sheet;release();old.dialog.setOnDismissListener(null);old.dialog.dismiss();}}
}
