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
    private Panel panel;
    AdbConnectionPanel(ManagementUi ui,AdbController adb,ExecutorService worker){this.ui=ui;this.adb=adb;this.worker=worker;}
    void close(){if(panel!=null){panel.cancel();panel=null;}}
    void show(Runnable connected){close();panel=new Panel(connected);panel.show();}
    private final class Panel {
        final ManagementUi.Sheet sheet=ui.new Sheet("本机 ADB 连接");
        final SetupOperations.Ticket attempt=new SetupOperations.Ticket();
        final Runnable connected;
        final EditText port=ui.input("","连接端口"),pairPort=ui.input("","配对端口"),code=ui.input("","六位配对码（已配对可留空）");
        final TextView status=ui.muted("",12);
        LocalAdbDiscovery discovery;
        Button settings,connect;
        boolean wireless,working,complete;
        Panel(Runnable connected){this.connected=connected;port.setInputType(2);pairPort.setInputType(2);code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);code.setSaveEnabled(false);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});}
        void show(){
            Button cancel=sheet.action("取消",sheet.dialog::dismiss);
            settings=sheet.action("开发者设置",()->{sheet.dialog.dismiss();try{ui.startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(android.content.ActivityNotFoundException|SecurityException e){ui.message("请在电视系统设置中打开开发者选项，开启调试。");}});
            connect=sheet.action("连接",this::connect);
            sheet.dialog.setOnDismissListener(dialog->{if(!complete)cancel();});body(false);sheet.show(connect);
        }
        void body(boolean tls){
            if(working)return;if(discovery!=null)discovery.close();wireless=tls;sheet.body.removeAllViews();
            sheet.text(tls?"在系统无线调试中打开配对页面；配对与连接端口不同。没有配对页面时请使用 TCP 或取消。返回后配对码可能失效，必要时手动填写本机端口。":"开启电视网络／ADB 调试，连接时在电视弹窗允许 Quiet TV。仅连接当前电视。");ui.gap(sheet.body,12);
            LinearLayout modes=ui.row();sheet.body.addView(modes);Button tcp=ui.button("传统 TCP"+(tls?"":" ✓"),()->body(false));modes.addView(tcp,new LinearLayout.LayoutParams(0,ui.px(36),1));
            Button pairing=ui.button("无线配对（实验）"+(tls?" ✓":""),()->body(true));pairing.setEnabled(AdbController.pairingAvailable());pairing.setAlpha(pairing.isEnabled()?1f:.4f);modes.addView(pairing,new LinearLayout.LayoutParams(0,ui.px(36),1));ui.gap(sheet.body,12);
            if(tls){add("配对端口",pairPort);add("配对码",code);}
            port.setText(tls?ui.getSharedPreferences("adb",0).getString("tlsPort",""):String.valueOf(ui.getSharedPreferences("adb",0).getInt("port",5555)));add("连接端口",port);
            status.setText(tls?"正在识别本机端口；也可手动输入。已配对时可留空配对码，仅连接。":"TCP 能力待连接核对；无线配对需要 Android 13+ 与可用原生库。");ui.gap(sheet.body,10);sheet.body.addView(status);
            if(tls)discovery=new LocalAdbDiscovery(ui,(pairingPort,value)->{if(working || !attempt.active())return;EditText field=pairingPort?pairPort:port;if(field.getText().length()==0)field.setText(String.valueOf(value));});
        }
        void add(String text,EditText field){sheet.body.addView(ui.muted(text,11));ui.gap(sheet.body,4);sheet.body.addView(field,new LinearLayout.LayoutParams(-1,ui.px(38)));ui.gap(sheet.body,8);}
        int parse(EditText field){try{int value=Integer.parseInt(field.getText().toString());if(value>0 && value<65536)return value;}catch(NumberFormatException ignored){}field.setError("端口必须在 1～65535 之间");return -1;}
        void connect(){
            if(working)return;int connectionPort=parse(port);if(connectionPort<0)return;
            String secret=code.getText().toString();boolean needsPair=wireless && !secret.isEmpty();
            if(needsPair && !secret.matches("[0-9]{6}")){code.setError("请输入六位配对码");return;}
            int pairingPort=needsPair?parse(pairPort):-1;if(needsPair && pairingPort<0)return;
            if(wireless)ui.getSharedPreferences("adb",0).edit().putString("tlsPort",String.valueOf(connectionPort)).apply();else ui.getSharedPreferences("adb",0).edit().putInt("port",connectionPort).apply();
            working=true;if(discovery!=null)discovery.close();settings.setEnabled(false);connect.setEnabled(false);port.setEnabled(false);pairPort.setEnabled(false);code.setEnabled(false);code.setText("");status.setText(needsPair?"正在配对，取消会关闭连接…":"正在连接并核对本机身份…");
            worker.execute(()->{try{
                attempt.check();if(needsPair){adb.pair(pairingPort,secret);attempt.check();}adb.connect(connectionPort);attempt.check();
                ui.runOnUiThread(()->{if(!attempt.active() || ui.isFinishing() || ui.isDestroyed())return;complete=true;sheet.dialog.dismiss();panel=null;connected.run();});
            }catch(Exception e){ui.runOnUiThread(()->{if(!attempt.active() || ui.isFinishing() || ui.isDestroyed())return;working=false;status.setText("未连接："+e.getMessage());settings.setEnabled(true);connect.setEnabled(true);port.setEnabled(true);pairPort.setEnabled(true);code.setEnabled(true);});}});
        }
        void cancel(){if(!attempt.active())return;attempt.cancel();if(discovery!=null)discovery.close();code.setText("");if(!complete)adb.disconnect();if(sheet.dialog.isShowing())sheet.dialog.dismiss();}
    }
}
