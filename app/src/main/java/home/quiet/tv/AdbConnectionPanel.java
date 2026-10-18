package home.quiet.tv;

import android.content.Intent;
import android.provider.Settings;
import android.widget.*;
import java.util.concurrent.ExecutorService;

/** Shared foreground connection sheet. Dismissal invalidates its continuation immediately. */
final class AdbConnectionPanel {
    private final ManagementUi ui;
    private final AdbController adb;
    private final ExecutorService worker;
    private ManagementUi.Sheet sheet;
    private SetupOperations.Ticket ticket;
    AdbConnectionPanel(ManagementUi ui,AdbController adb,ExecutorService worker){this.ui=ui;this.adb=adb;this.worker=worker;}
    void close(){if(ticket!=null)ticket.cancel();if(sheet!=null){sheet.dialog.dismiss();sheet=null;}}
    void show(Runnable connected){
        close();ManagementUi.Sheet current=ui.new Sheet("本机 ADB 连接");sheet=current;SetupOperations.Ticket attempt=new SetupOperations.Ticket();ticket=attempt;
        current.text("开启电视开发者选项中的网络／ADB 调试。连接时请在电视弹窗允许 Quiet TV。\n当前连接方式：传统 TCP，端口需与电视设置一致。");ui.gap(current.body,12);
        EditText port=ui.input(String.valueOf(ui.getSharedPreferences("adb",0).getInt("port",5555)),"调试端口");port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);current.body.addView(port,new LinearLayout.LayoutParams(-1,ui.px(42)));
        TextView status=ui.muted("连接能力待核对；只连接当前电视",12);ui.gap(current.body,12);current.body.addView(status);
        Button cancel=current.action("取消",current.dialog::dismiss);
        Button settings=current.action("开发者设置",()->{current.dialog.dismiss();try{ui.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(android.content.ActivityNotFoundException|SecurityException e){ui.message("请在电视系统设置中打开开发者选项，开启调试。");}});
        Button connect=current.action("连接",()->{
            int value;try{value=Integer.parseInt(port.getText().toString());if(value<1||value>65535)throw new NumberFormatException();}catch(NumberFormatException e){port.setError("端口必须在 1～65535 之间");return;}
            ui.getSharedPreferences("adb",0).edit().putInt("port",value).apply();port.setEnabled(false);settings.setEnabled(false);current.actions.getChildAt(2).setEnabled(false);cancel.requestFocus();status.setText("连接中，请在电视弹窗允许授权…");
            worker.execute(()->{try{
                attempt.check();adb.connect(value);attempt.check();
                ui.runOnUiThread(()->{if(!attempt.active() || ui.isFinishing() || ui.isDestroyed())return;ticket=null;current.dialog.setOnDismissListener(null);current.dialog.dismiss();sheet=null;connected.run();});
            }catch(Exception e){ui.runOnUiThread(()->{if(!attempt.active() || ui.isFinishing() || ui.isDestroyed())return;status.setText("未连接："+e.getMessage());port.setEnabled(true);settings.setEnabled(true);current.actions.getChildAt(2).setEnabled(true);});}});
        });
        current.dialog.setOnDismissListener(dialog->{attempt.cancel();adb.disconnect();});current.show(connect);
    }
}
