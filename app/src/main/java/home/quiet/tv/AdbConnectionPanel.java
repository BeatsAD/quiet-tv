package home.quiet.tv;

import android.provider.Settings;
import android.widget.*;
import java.util.concurrent.ExecutorService;

/** Shared foreground connection sheet. Dismissal invalidates its continuation immediately. */
final class AdbConnectionPanel {
    private final ManagementUi ui;
    private final AdbController adb;
    private final AdbSession session;
    private final ExecutorService worker;
    private Panel panel;
    AdbConnectionPanel(ManagementUi ui,AdbController adb,ExecutorService worker){this(ui,adb,worker,null);}
    AdbConnectionPanel(ManagementUi ui,AdbController adb,ExecutorService worker,AdbSession session){this.ui=ui;this.adb=adb;this.worker=session==null?worker:session.worker();this.session=session;}
    void close(){if(panel!=null){panel.cancel();panel=null;}}
    void suspend(){if(panel!=null){panel.cancel(false);panel=null;}}
    void show(Runnable connected){show(connected,"连接");}
    void show(Runnable connected,String label){close();
        if(session!=null && adb.connected()){
            ManagementUi.Sheet info=ui.new Sheet("本机 ADB 连接");info.text(session.state().description());Button back=info.action("返回",info.dialog::dismiss);
            info.action("断开连接",()->{info.dialog.dismiss();session.pause();});info.show(back);return;
        }
        if(session!=null)session.openManual();panel=new Panel(connected,label);panel.show();}
    private final class Panel {
        final SetupActivity setup=ui instanceof SetupActivity?(SetupActivity)ui:null;
        final ManagementUi.Sheet sheet=setup==null?ui.new Sheet("本机 ADB 连接"):setup.setupSheet("连接电视");
        ManagementUi.Sheet guide;
        final String continuation;
        final SetupOperations.Ticket attempt=new SetupOperations.Ticket();
        final Runnable connected;
        final EditText port=ui.input("","连接端口"),pairPort=ui.input("","配对端口"),code=ui.input("","六位配对码（已配对可留空）");
        final TextView status=ui.muted("",12);
        LocalAdbDiscovery discovery;
        Button settings,connect;
        boolean wireless,working,complete,advanced;
        Panel(Runnable connected,String label){this.connected=connected;continuation=label;port.setInputType(2);pairPort.setInputType(2);code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);port.setTag("connection-port");pairPort.setTag("pairing-port");code.setTag("pairing-code");if(setup!=null || ui instanceof BootAdActivity){remoteInput(port);remoteInput(pairPort);remoteInput(code);}code.setSaveEnabled(false);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});}
        // Native EditText consumes D-pad vertical keys, trapping focus above the action row.
        void remoteInput(EditText field){
            if(android.os.Build.VERSION.SDK_INT>=21)field.setShowSoftInputOnFocus(false);
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)ui.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            field.setOnKeyListener((view,key,event)->{
                if(key==android.view.KeyEvent.KEYCODE_DPAD_UP || key==android.view.KeyEvent.KEYCODE_DPAD_DOWN){
                    if(event.getAction()==android.view.KeyEvent.ACTION_DOWN){android.view.View next=field.focusSearch(key==android.view.KeyEvent.KEYCODE_DPAD_UP?android.view.View.FOCUS_UP:android.view.View.FOCUS_DOWN);if(next!=null && next!=field){keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);next.requestFocus();}}return true;
                }
                if(key==android.view.KeyEvent.KEYCODE_DPAD_CENTER || key==android.view.KeyEvent.KEYCODE_ENTER){if(event.getAction()==android.view.KeyEvent.ACTION_UP)keyboard.showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);return true;}return false;
            });
            field.setOnFocusChangeListener((view,focused)->{if(!focused)keyboard.hideSoftInputFromWindow(field.getWindowToken(),0);});
        }
        void show(){
            if(setup==null)sheet.action("取消",sheet.dialog::dismiss);
            settings=setup==null?sheet.action("开发者设置",this::openSettings):setup.setupButton("打开系统设置","settings",this::settingsGuide,false);
            connect=setup==null?sheet.action("连接",this::connect):setup.setupButton(continuation,"connect",this::connect,true);
            if(setup!=null){LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-1);sp.rightMargin=ui.px(10);sheet.actions.addView(settings,sp);sheet.actions.addView(connect,new LinearLayout.LayoutParams(-2,-1));}
            sheet.dialog.setOnDismissListener(dialog->{if(!complete)cancel();});body(session!=null && AdbController.pairingAvailable() && "tls".equals(ui.getSharedPreferences("adb",0).getString("lastTransport","tcp")));sheet.show(connect);
        }
        void openSettings(){sheet.dialog.dismiss();if(!new SystemSettingsRouter(ui).open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))ui.message("未找到可用的开发者设置，请使用电视系统提供的调试入口。");}
        void settingsGuide(){
            guide=setup.setupSheet("在电视系统设置中操作");guide.text("在电视系统提供的开发者选项或厂商调试入口中开启网络 / ADB 调试；工厂菜单的 ADBD 开关不保证可以连接。");guide.action("打开开发者选项",()->{guide.dialog.dismiss();openSettings();});Button back=guide.action("返回连接",guide.dialog::dismiss);guide.show(back);
        }
        void instruction(String number,String title,String detail){LinearLayout row=ui.row();TextView badge=ui.center(number,10);badge.setBackground(ui.surface(0x05ffffff,0x05ffffff,0x30ffffff,16));row.addView(badge,new LinearLayout.LayoutParams(ui.px(22),ui.px(22)));LinearLayout copy=ui.column();copy.addView(ui.label(title,12));ui.gap(copy,5);copy.addView(ui.muted(detail,10));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=ui.px(12);row.addView(copy,cp);sheet.body.addView(row);ui.gap(sheet.body,18);}
        void body(boolean tls){
            if(working)return;android.view.View focused=sheet.dialog.getCurrentFocus();Object focusTag=focused==null?null:focused.getTag();boolean restoreFocus=sheet.dialog.isShowing();if(discovery!=null)discovery.close();wireless=tls;sheet.body.removeAllViews();
            if(setup!=null){
                if(session!=null && session.state().debug==AdbState.Debug.ENABLED)sheet.text("系统 ADB 已开启，请连接本机并确认授权。");
                else {instruction("1","找到电视调试入口","开发者选项或厂商提供的调试菜单");instruction("2",tls?"开启无线调试":"开启网络 / ADB 调试",tls?"选择「使用配对码配对设备」":"仅开启 USB 调试可能无法连接");}
            }
            else if(!tls && session!=null && session.state().debug==AdbState.Debug.ENABLED)sheet.text("系统 ADB 已开启；如出现调试授权弹窗，请允许 Quiet TV。");
            else sheet.text(tls?"在系统无线调试中打开配对页面；配对与连接端口不同。没有配对页面时请使用 TCP 或取消。返回后配对码可能失效，必要时手动填写本机端口。":"开启电视网络／ADB 调试；如出现授权弹窗，请允许 Quiet TV。未出现弹窗时，以连接及身份核对结果为准。仅连接当前电视。");ui.gap(sheet.body,12);
            if(setup!=null && !tls){Button expand=setup.setupButton(advanced?"连接设置  ⌄":"连接设置  ›","connection-settings",()->{advanced=!advanced;body(false);},false);expand.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);sheet.body.addView(expand,new LinearLayout.LayoutParams(-1,ui.px(32)));ui.gap(sheet.body,8);}
            if(setup==null || advanced || tls){
            LinearLayout modes=ui.row();modes.setGravity(android.view.Gravity.CENTER);sheet.body.addView(modes);Button tcp=ui.button("传统 TCP"+(tls?"":" ✓"),()->body(false));tcp.setTag("tcp-mode");modes.addView(tcp,new LinearLayout.LayoutParams(ui.px(156),ui.px(36)));
            Button pairing=ui.button("无线配对（实验）"+(tls?" ✓":""),()->body(true));pairing.setTag("pairing-mode");pairing.setEnabled(AdbController.pairingAvailable());pairing.setAlpha(pairing.isEnabled()?1f:.4f);if(!BuildConfig.LEGACY_19){LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ui.px(156),ui.px(36));pp.leftMargin=ui.px(8);modes.addView(pairing,pp);}ui.gap(sheet.body,12);
            if(tls){add("配对端口",pairPort);add("配对码",code);}
            port.setText(tls?"":String.valueOf(ui.getSharedPreferences("adb",0).getInt("port",5555)));add("连接端口",port);
            }else port.setText(String.valueOf(ui.getSharedPreferences("adb",0).getInt("port",5555)));
            status.setText(session!=null && !session.state().detail.isEmpty()?session.state().detail:setup!=null?(tls?"配对与连接端口不同，可手动填写。":""):tls?"正在识别本机端口；也可手动输入。已配对时可留空配对码，仅连接。":"TCP 能力待连接核对。");ui.gap(sheet.body,10);sheet.body.addView(status);
            if(restoreFocus)sheet.body.post(()->{if(!sheet.dialog.isShowing())return;android.view.View target=focusTag==null?null:sheet.dialog.getWindow().getDecorView().findViewWithTag(focusTag);if(target==null || !target.isEnabled())target=sheet.body.findViewWithTag("connection-settings");if(target==null)target=port;target.requestFocus();});
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
            final int request=session!=null?session.beginManual(wireless):adb.prepare();
            working=true;if(setup!=null)connect.setText("正在连接…");if(discovery!=null)discovery.close();settings.setEnabled(false);connect.setEnabled(false);port.setEnabled(false);pairPort.setEnabled(false);code.setEnabled(false);code.setText("");status.setText(needsPair?"正在配对，取消会关闭连接…":"正在连接并核对本机身份…");
            if(setup!=null){android.view.View cancel=sheet.dialog.getWindow().getDecorView().findViewWithTag("close-dialog");if(cancel!=null)cancel.requestFocus();}
            worker.execute(()->{try{
                attempt.check();if(needsPair){adb.pair(pairingPort,secret,request);attempt.check();}adb.connect(connectionPort,request);attempt.check();
                ui.runOnUiThread(()->{if(!attempt.active() || ui.isFinishing() || ui.isDestroyed())return;if(request!=adb.generation() || !adb.connected()){working=false;status.setText("连接已取消，请重新连接");settings.setEnabled(true);connect.setEnabled(true);port.setEnabled(true);pairPort.setEnabled(true);code.setEnabled(true);return;}complete=true;if(session!=null)session.manualConnected(wireless);sheet.dialog.dismiss();panel=null;connected.run();});
            }catch(Exception e){ui.runOnUiThread(()->{if(!attempt.active() || ui.isFinishing() || ui.isDestroyed())return;working=false;if(session!=null)session.manualFailed(e.getMessage()==null?"连接失败":e.getMessage());if(setup!=null)connect.setText(continuation);status.setText("未连接："+e.getMessage());settings.setEnabled(true);connect.setEnabled(true);port.setEnabled(true);pairPort.setEnabled(true);code.setEnabled(true);});}});
        }
        void cancel(){cancel(true);}
        void cancel(boolean release){if(!attempt.active())return;attempt.cancel();if(discovery!=null)discovery.close();if(guide!=null)guide.dialog.dismiss();code.setText("");if(!complete && release){if(session!=null)session.cancelManual();else adb.disconnect();}if(sheet.dialog.isShowing())sheet.dialog.dismiss();}
    }
}
