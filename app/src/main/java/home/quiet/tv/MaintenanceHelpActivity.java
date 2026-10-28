package home.quiet.tv;

import android.content.*;
import android.os.Bundle;
import android.widget.*;

/** Small offline escape route; technical configuration belongs to the computer Agent. */
public final class MaintenanceHelpActivity extends ManagementUi {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);((TextView)header.getChildAt(1)).setText("帮助与恢复");
        LinearLayout body=column();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=px(30);root.addView(body,p);
        body.addView(label("需要安装、修复或管理系统应用？",21));gap(body,12);
        body.addView(muted("请在电脑上打开安装页，将请求复制给 AI 助手。",15));gap(body,8);
        body.addView(label("https://beatsad.github.io/quiet-tv/",17));gap(body,28);
        Button pause=button("暂停桌面接管",()->confirm(false));body.addView(pause,new LinearLayout.LayoutParams(-1,px(42)));gap(body,12);
        Button restore=button("恢复原桌面",()->confirm(true));body.addView(restore,new LinearLayout.LayoutParams(-1,px(42)));
        gap(body,16);body.addView(muted("恢复时保留应用和数据；需要时请在系统提示中选择原桌面。",13));
        pause.setId(androidx.core.view.ViewCompat.generateViewId());restore.setId(androidx.core.view.ViewCompat.generateViewId());
        pause.setNextFocusDownId(restore.getId());restore.setNextFocusUpId(pause.getId());pause.requestFocus();
    }
    private void confirm(boolean restore){
        Sheet sheet=new Sheet(restore?"恢复原桌面？":"暂停桌面接管？");
        sheet.text(restore?"先暂停自动接管，再打开原桌面或系统默认桌面设置。":"暂停后不再自动返回 Quiet TV，系统默认桌面保持当前设置。");
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);
        sheet.action("继续",()->{sheet.dialog.dismiss();perform(restore);});sheet.show(cancel);
    }
    private void perform(boolean restore){
        HomeGuardState state=new HomeGuardState(this);state.pause("用户从帮助页暂停接管");
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
