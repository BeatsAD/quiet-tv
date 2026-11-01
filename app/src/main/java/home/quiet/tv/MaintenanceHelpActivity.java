package home.quiet.tv;

import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** Small offline escape route; technical configuration belongs to the computer Agent. */
public final class MaintenanceHelpActivity extends ManagementUi {
    static final String INSTALL_PAGE="https://beatsad.github.io/quiet-tv/";
    private TextView status,next;
    private Button choose;
    private android.graphics.Bitmap qrBitmap;
    private Button[] actions;
    static Intent homeSettingsIntent(Context context){
        if(!BuildConfig.DECLARES_HOME)return null;
        try{
            Intent intent=new HomeController(context).requestIntent();if(intent==null)return null;
            android.content.pm.ResolveInfo resolved=context.getPackageManager().resolveActivity(intent,0);
            return resolved!=null && resolved.activityInfo!=null && TvInputController.canOpen(context,new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name))?intent:null;
        }catch(RuntimeException unavailable){return null;}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);((TextView)header.getChildAt(1)).setText("设为桌面与恢复");
        LinearLayout columns=row();columns.setGravity(android.view.Gravity.TOP);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=px(28);root.addView(columns,p);
        LinearLayout body=column();columns.addView(body,new LinearLayout.LayoutParams(0,-1,1));
        status=label("",22);status.setTag("takeover-status");body.addView(status);gap(body,12);
        next=muted("",14);next.setTag("takeover-next");body.addView(next);gap(body,22);
        choose=button("选择系统默认桌面",()->{
            Intent intent=homeSettingsIntent(this);
            if(intent==null || !ExternalActivityLauncher.open(this,"maintenance.home_settings",intent))message("系统未提供可用入口，请扫描右侧二维码通过电脑配置。");
        });choose.setTag("choose-home");body.addView(choose,new LinearLayout.LayoutParams(-1,px(42)));gap(body,10);
        Button pause=button("暂停桌面接管",()->confirm(false));body.addView(pause,new LinearLayout.LayoutParams(-1,px(42)));gap(body,10);
        Button restore=button("恢复原桌面",()->confirm(true));body.addView(restore,new LinearLayout.LayoutParams(-1,px(42)));gap(body,10);
        Button logs=button("导出诊断",()->startActivity(new Intent(this,AppManagementActivity.class).putExtra("management-page",5)));body.addView(logs,new LinearLayout.LayoutParams(-1,px(42)));
        LinearLayout computer=column();computer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(px(300),-1);cp.leftMargin=px(36);columns.addView(computer,cp);
        computer.addView(label("通过电脑配置",20));gap(computer,12);
        try{
            int size=384;com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(INSTALL_PAGE,com.google.zxing.BarcodeFormat.QR_CODE,size,size,java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,2));
            int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?android.graphics.Color.BLACK:android.graphics.Color.WHITE;
            qrBitmap=android.graphics.Bitmap.createBitmap(pixels,size,size,android.graphics.Bitmap.Config.ARGB_8888);
            ImageView qr=new ImageView(this);qr.setImageBitmap(qrBitmap);qr.setContentDescription("电脑配置说明二维码");computer.addView(qr,new LinearLayout.LayoutParams(px(194),px(194)));
        }catch(com.google.zxing.WriterException unavailable){computer.addView(muted("请在电脑上打开下方网址",14));}
        gap(computer,10);computer.addView(center("扫码查看说明，在电脑上将请求复制给 AI 助手。",14));gap(computer,10);computer.addView(center(INSTALL_PAGE,11));
        android.view.View back=header.getChildAt(0);back.setId(androidx.core.view.ViewCompat.generateViewId());
        actions=new Button[]{choose,pause,restore,logs};for(Button button:actions){button.setId(androidx.core.view.ViewCompat.generateViewId());button.setFocusableInTouchMode(true);}
        refresh();(choose.getVisibility()==android.view.View.VISIBLE?choose:pause).requestFocus();
    }
    private void refresh(){
        DesktopTakeoverStatus value=DesktopTakeoverStatus.read(this);status.setText(value.title);next.setText(value.next);choose.setVisibility(homeSettingsIntent(this)!=null?android.view.View.VISIBLE:android.view.View.GONE);
        android.view.View previous=header.getChildAt(0);
        for(Button button:actions)if(button.getVisibility()==android.view.View.VISIBLE){previous.setNextFocusDownId(button.getId());button.setNextFocusUpId(previous.getId());button.setNextFocusLeftId(button.getId());button.setNextFocusRightId(button.getId());previous=button;}
        previous.setNextFocusDownId(previous.getId());
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){super.onDestroy();if(qrBitmap!=null){qrBitmap.recycle();qrBitmap=null;}}
    private void confirm(boolean restore){
        Sheet sheet=new Sheet(restore?"恢复原桌面？":"暂停桌面接管？");
        sheet.text(restore?"先暂停自动接管，再打开原桌面或系统默认桌面设置。":"暂停后不再自动返回 Quiet TV，系统默认桌面保持当前设置。");
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("继续",()->{sheet.dialog.dismiss();perform(restore);});sheet.show(cancel);
    }
    private void perform(boolean restore){
        HomeGuardState state=new HomeGuardState(this);state.pause("用户从帮助页暂停接管");refresh();
        if(state.preferences.getBoolean("proxyPausePending",false)){message("接管暂停尚未确认，请通过电脑恢复。");return;}
        if(!restore){message("已暂停自动接管。");return;}
        try{
            HomeController home=new HomeController(this);String original=DeviceMaintenanceReceiver.original(this);
            if(original.isEmpty() || !home.component(original,true)){message("原桌面记录缺失或暂不可用，请通过电脑恢复。");return;}
            HomeController.State current=home.inspect();
            if(current.known && original.equals(current.actual)){
                startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(original)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return;
            }
            // The app has no system privilege to choose HOME; the system owns this confirmation.
            Intent settings=new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
            if(!ExternalActivityLauncher.open(this,"maintenance.restore",settings)){
                startActivity(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(original)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                message("已暂停并打开原桌面，默认桌面仍需通过电脑恢复。");
            }
        }catch(Exception failure){message("已暂停接管，恢复未完成，请通过电脑继续。");}
    }
}
