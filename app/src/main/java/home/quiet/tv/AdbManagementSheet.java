package home.quiet.tv;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** TV and QR settings operate the same process-owned local management connection. */
final class AdbManagementSheet {
    private final ManagementUi ui;
    private final ManagementAdb connection;
    private final Runnable closed;
    private final ManagementAdb.Listener changed=this::render;
    private ManagementUi.Sheet sheet;
    private TextView status,description,debugging,port;
    private Button primary,check,close;
    private ImageView icon;
    private boolean listening;
    AdbManagementSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;connection=ManagementAdb.get(ui);}
    void show(){
        if(sheet!=null)return;
        sheet=ui.new Sheet("ADB 管理");sheet.contentWidth(440,560);close=sheet.settingsLayout("关闭",()->sheet.dialog.dismiss());
        close.setNextFocusLeftId(close.getId());close.setNextFocusRightId(close.getId());
        LinearLayout summary=ui.row();summary.setGravity(Gravity.CENTER_VERTICAL);
        icon=new ImageView(ui);
        summary.addView(icon,new LinearLayout.LayoutParams(ui.px(30),ui.px(30)));
        status=ui.label("",24);status.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams title=new LinearLayout.LayoutParams(0,-2,1);title.leftMargin=ui.px(14);summary.addView(status,title);
        ui.gap(sheet.body,12);sheet.body.addView(summary);ui.gap(sheet.body,4);
        description=ui.muted("",13);description.setLineSpacing(ui.px(4),1);sheet.body.addView(description);ui.gap(sheet.body,18);
        LinearLayout info=ui.column();info.setPadding(ui.px(16),ui.px(4),ui.px(16),ui.px(4));
        info.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,10));
        debugging=info(info,"系统调试");View divider=new View(ui);divider.setBackgroundColor(UiPalette.EDGE);info.addView(divider,new LinearLayout.LayoutParams(-1,ui.px(.5f)));port=info(info,"连接端口");sheet.body.addView(info);
        ui.gap(sheet.body,18);TextView note=ui.muted("断开后暂停自动重连，需要时可再次连接。",12);note.setLineSpacing(ui.px(4),1);sheet.body.addView(note);
        check=sheet.action("检查状态",()->{connection.check();render();});identify(check,"adb-check");
        primary=sheet.action("连接 ADB",()->{if(connection.state()!=AdbConnectionKeeper.State.DISCONNECTED)connection.pause();else connection.requestAuthorization();render();});identify(primary,"adb-primary");
        close.setNextFocusDownId(primary.getId());check.setNextFocusUpId(close.getId());primary.setNextFocusUpId(close.getId());check.setNextFocusDownId(check.getId());primary.setNextFocusDownId(primary.getId());check.setNextFocusLeftId(check.getId());check.setNextFocusRightId(primary.getId());primary.setNextFocusLeftId(check.getId());primary.setNextFocusRightId(primary.getId());
        sheet.dialog.setOnDismissListener(dialog->{pause();sheet=null;closed.run();});
        render();sheet.show(primary);resume();
    }
    private TextView info(LinearLayout group,String title){
        LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(ui.label(title,13),new LinearLayout.LayoutParams(0,-2,1));TextView value=ui.label("",13);value.setGravity(Gravity.END);row.addView(value,new LinearLayout.LayoutParams(-2,-2));group.addView(row,new LinearLayout.LayoutParams(-1,ui.px(34)));return value;
    }
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());}
    private void render(){
        if(sheet==null)return;
        AdbConnectionKeeper.State value=connection.state();status.setText(connection.label());description.setText(connection.description()+(connection.waitingAuthorization()?"\n剩余等待 "+connection.authorizationSeconds()+" 秒":""));debugging.setText(connection.debugging());port.setText(String.valueOf(connection.port()));
        int color;switch(connection.tone()){case "success":color=UiPalette.STATUS_SUCCESS;break;case "warning":color=UiPalette.STATUS_WARNING;break;case "progress":color=UiPalette.STATUS_PROGRESS;break;case "error":color=UiPalette.STATUS_ERROR;break;default:color=UiPalette.SECONDARY;}
        status.setTextColor(color);QuickControlIcon glyph=new QuickControlIcon(value==AdbConnectionKeeper.State.CONNECTED?"check-circle":"plugs");glyph.color(color);icon.setImageDrawable(glyph);
        primary.setText(value==AdbConnectionKeeper.State.CONNECTED?"断开连接":value==AdbConnectionKeeper.State.CONNECTING?"取消连接":"连接 ADB");
        check.setEnabled(value!=AdbConnectionKeeper.State.CONNECTING);
    }
    void resume(){if(sheet!=null&&!listening){connection.addListener(changed);listening=true;render();}}
    void pause(){if(listening){connection.removeListener(changed);listening=false;}}
    void save(Bundle state){state.putBoolean("adb-management-open",sheet!=null);}
    void restore(Bundle state){if(state!=null&&state.getBoolean("adb-management-open"))show();}
    void destroy(){pause();if(sheet!=null)sheet.dialog.dismiss();}
}
