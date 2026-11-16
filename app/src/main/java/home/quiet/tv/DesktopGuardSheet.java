package home.quiet.tv;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Native presentation of the same desktop-management snapshot used by the phone client. */
final class DesktopGuardSheet {
    private final ManagementUi ui;
    private final Runnable closed;
    private final DiagnosticExportUi diagnostics;
    private final DesktopSwitch engine;
    private ManagementUi.Sheet sheet;
    private View origin;
    private Button close;
    private android.graphics.Bitmap guide;
    private String screen="home",focus="",error="",result="",confirmationRevision="";
    private JSONObject state=new JSONObject(),confirmation;
    private boolean destroyed,refreshing,working,listening,resumed;
    private Runnable sharedReturn;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refresh;
    private final Runnable poll=()->{if(open()&&resumed){refresh();schedule();}};
    DesktopGuardSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;diagnostics=new DiagnosticExportUi(ui);engine=DesktopSwitch.get(ui);}
    private boolean open(){return sheet!=null&&!destroyed;}
    android.app.Dialog dialog(){return sheet==null?null:sheet.dialog;}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value,float size,float line,int color){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,size,line,ui.unit,color);return view;}
    private void block(View view,float top,float bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(top);lp.bottomMargin=p(bottom);sheet.body.addView(view,lp);}
    private void copy(String value,boolean muted){block(text(value,muted?14.464f:17.28f,muted?23.8656f:29.376f,UiPalette.SECONDARY),12,12);}
    private void heading(String value){TextView view=text(value,17.28f,25.92f,UiPalette.TEXT);QuickControlPreviewView.weight(view,500);block(view,0,10.24f);}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});}
    private Button button(String title,String tag,Runnable action,boolean primary){Button view=ui.button(title,action);identify(view,tag);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE,false,12));view.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));return view;}
    private LinearLayout actions(){LinearLayout row=ui.row();row.setGravity(Gravity.END);row.setClipChildren(false);row.setClipToPadding(false);block(row,30,0);row.getLayoutParams().height=p(46.08f);return row;}
    private Button action(LinearLayout row,String title,String tag,Runnable action,boolean primary){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);if(row.getChildCount()>0)lp.leftMargin=p(10);Button b=button(title,tag,action,primary);row.addView(b,lp);return b;}
    private void row(LinearLayout group,String title,String value,String tag,Runnable operation,boolean available){DesktopSettingsRow row=new DesktopSettingsRow(ui,title,"",false);row.hideIcon();row.value(value);if(operation!=null){row.tail("arrow");identify(row,tag);row.setOnClickListener(v->operation.run());}else row.setFocusable(false);if(operation==null&&group.getChildCount()>0){View divider=new View(ui);divider.setBackgroundColor(UiPalette.EDGE);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,Math.max(1,p(.5f)));line.leftMargin=p(13);line.rightMargin=p(13);group.addView(divider,line);}row.available(available);row.setAlpha(1f);row.textSizes(13.92f,11.52f);group.addView(row,new LinearLayout.LayoutParams(-1,p(60)));}
    private JSONObject object(String key){JSONObject value=state.optJSONObject(key);return value==null?new JSONObject():value;}
    private void start(){resumed=true;if(!listening){engine.listen(changed);listening=true;}refresh();schedule();}
    private void schedule(){handler.removeCallbacks(poll);handler.postDelayed(poll,2000);}
    private void stop(){resumed=false;handler.removeCallbacks(poll);if(listening){engine.forget(changed);listening=false;}}
    private void refresh(){
        if(!open()||refreshing)return;refreshing=true;
        engine.worker.execute(()->{JSONObject next=engine.state();ui.runOnUiThread(()->{refreshing=false;if(!open())return;boolean first=state.optInt("presentationVersion")==0;boolean different=!next.toString().equals(state.toString());state=next;if(first)focus="";if(different&&!screen.equals("confirm"))render();});});
    }
    void show(){if(open())return;screen="home";focus="";error=result="";create();}
    void showIn(ManagementUi.Sheet value,Runnable returned,String initial,Bundle saved){sheet=value;sharedReturn=returned;close=(Button)sheet.box.findViewWithTag("sheet-close");screen=initial;error=result="";if(saved!=null)readState(saved);render();start();}
    void detach(){stop();diagnostics.hide();releaseGuide();sheet=null;sharedReturn=null;}
    void handleBack(){back();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet("桌面管理");sheet.contentWidth(560,560);ManagementUi.Sheet own=sheet;
        close=sheet.settingsLayout("关闭",this::back);identify(close,"sheet-close");sheet.settingsActions(false);
        sheet.dialog.setOnKeyListener((dialog,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP)back();return true;});
        sheet.dialog.setOnDismissListener(dialog->{if(sheet!=own)return;stop();diagnostics.hide();releaseGuide();sheet=null;if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();closed.run();});
        render();sheet.show(null);start();restoreFocus();
    }
    private void restoreFocus(){if(!open())return;String key=focus;sheet.body.post(()->{if(!open())return;View target=key.isEmpty()?null:sheet.box.findViewWithTag(key);if(target==null||!target.isEnabled()||!target.isShown())target=sheet.box.findViewWithTag("guard-primary");if(target==null||!target.isEnabled()||!target.isShown())target=close;if(target!=null){target.requestFocusFromTouch();android.graphics.Rect r=new android.graphics.Rect();target.getDrawingRect(r);target.requestRectangleOnScreen(r,false);}});}
    private void go(String value,String target){diagnostics.hide();screen=value;focus=target;error=result="";confirmation=null;render();}
    private void render(){
        if(!open())return;String target=focus;sheet.heading.setText("桌面管理");sheet.body.removeAllViews();releaseGuide();sheet.settingsActions(false);
        close.setText(screen.equals("home")&&sharedReturn==null?"关闭":"返回");close.setContentDescription(close.getText());
        if(screen.equals("logs")){diagnostics.show(sheet);return;}
        if(screen.equals("computer"))computer();else if(screen.equals("more"))more();else if(screen.equals("verify"))verification();else if(screen.startsWith("scene:"))scene();else if(screen.equals("confirm"))confirmation();else home();
        if(!error.isEmpty())copy(error,false);if(!result.isEmpty())copy(result,true);
        focus=target;restoreFocus();
    }
    private JSONObject choice(String id){JSONArray all=state.optJSONArray("managementActions");if(all!=null)for(int i=0;i<all.length();i++){JSONObject a=all.optJSONObject(i);if(a!=null&&id.equals(a.optString("id")))return a;}return null;}
    private void home(){
        if(state.optInt("presentationVersion")==0){copy("正在读取电视桌面状态",false);return;}
        JSONObject desktop=object("mainDesktop"),guard=object("guard");
        block(text("当前主桌面",14.464f,23.8656f,UiPalette.SECONDARY),24,10);
        LinearLayout hero=ui.row();TextView name=text(desktop.optString("label","待核对"),28,39,UiPalette.TEXT);QuickControlPreviewView.weight(name,600);hero.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=text(guard.optString("label"),16.64f,28.288f,guard.optString("state").equals("running")?UiPalette.STATUS_SUCCESS:UiPalette.SECONDARY);hero.addView(status,new LinearLayout.LayoutParams(-2,-2));block(hero,0,6);
        copy(desktop.optString("hint"),true);
        block(text("可用桌面",16.64f,25,UiPalette.TEXT),18,12);
        LinearLayout group=ui.listGroup();sheet.body.addView(group,new LinearLayout.LayoutParams(-1,-2));
        JSONArray entries=state.optJSONArray("desktops");if(entries!=null)for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry!=null)row(group,entry.optString("label"),entry.optString("detail"),"",null,true);}
        LinearLayout line=actions();action(line,"更多管理","guard-more",()->go("more","guard-original"),false);line.addView(new View(ui),new LinearLayout.LayoutParams(0,1,1));
        JSONObject pause=choice("guard-pause"),resume=choice("guard-resume"),primary=choice(state.optString("primaryAction"));
        JSONObject secondary=pause!=null?pause:resume;
        if(secondary!=null&&secondary!=primary&&!secondary.optString("scope").equals("primary"))actionButton(line,secondary,false,"guard-secondary");
        if(primary!=null)actionButton(line,primary,true,"guard-primary");
        else if(state.optBoolean("systemSelection"))action(line,"设置 Quiet TV 桌面","guard-primary",this::system,true);
        else action(line,"查看处理方式","guard-primary",()->go("computer","guard-computer-back"),true);
        if(primary!=null&&!primary.optBoolean("enabled"))copy(primary.optString("reason"),true);
        if(working||state.optBoolean("busy"))copy("正在处理桌面操作，请等待核对结果。",true);
    }
    private void actionButton(LinearLayout line,JSONObject a,boolean primary,String tag){Button b=action(line,a.optString("title"),tag,()->confirm(a),primary);b.setEnabled(!working&&!state.optBoolean("busy")&&a.optBoolean("enabled"));}
    private void confirm(JSONObject a){confirmation=a;confirmationRevision=state.optString("revision");screen="confirm";focus="guard-cancel";error=result="";render();}
    private void confirmation(){
        if(confirmation==null){screen="home";home();return;}heading(confirmation.optString("title")+"？");copy(confirmation.optString("explanation"),false);
        LinearLayout line=actions();action(line,"取消","guard-cancel",()->go("home",""),false);
        Button execute=action(line,working?"正在处理":"确认","guard-confirm",this::execute,true);execute.setEnabled(!working);
    }
    private void execute(){
        if(working||confirmation==null)return;working=true;String id=confirmation.optString("id"),revision=confirmationRevision;render();
        engine.worker.execute(()->{String failure="";JSONObject next=null;try{next=engine.manage(id,revision,()->!destroyed);}catch(Exception e){failure=DesktopSwitch.message(e);}
            JSONObject response=next;String problem=failure;ui.runOnUiThread(()->{working=false;if(!open())return;if(response!=null)state=response;error=problem;result=response==null?"":response.optString("message");screen="home";focus="";confirmation=null;render();refresh();});});
    }
    private void more(){
        heading("更多管理");LinearLayout group=ui.listGroup();sheet.body.addView(group,new LinearLayout.LayoutParams(-1,-2));
        JSONArray choices=state.optJSONArray("managementActions");if(choices!=null)for(int i=0;i<choices.length();i++){JSONObject a=choices.optJSONObject(i);if(a==null||!("original".equals(a.optString("scope"))||"return".equals(a.optString("scope"))&&a.optString("id").equals("quiet-disable")))continue;
            row(group,a.optString("title"),a.optBoolean("enabled")?"查看并确认":a.optString("reason"),"guard-action-"+a.optString("id"),()->{if(a.optBoolean("enabled"))confirm(a);else{error=a.optString("reason");render();}},!working);}
        row(group,"检查运行状态","只读取当前配置","guard-check",()->{result="已重新读取请求，验证记录保持原样。";refresh();render();},!working);
        row(group,"使用验证","HOME  开机  唤醒","guard-verify",()->go("verify","guard-scene-home"),true);
        row(group,"诊断详情","查看及导出","guard-logs",()->go("logs","diagnostic-retry"),true);
        row(group,"通过电脑设置与恢复","查看说明","guard-computer",()->go("computer","guard-computer-back"),true);
        if(state.optBoolean("systemSelection"))row(group,"系统默认桌面选择","在电视系统中完成","guard-system",this::system,!working);
        ManagementAdb management=ManagementAdb.existing();if(management==null||!management.adb.connected())row(group,"连接电视管理权限","按电视系统提示授权","guard-connect",()->{ManagementAdb.get(ui).requestAuthorization();result="请在电视确认系统调试授权。";render();},!working);
        if(!state.optString("problem").isEmpty())copy(state.optString("problem"),true);
    }
    private static String sceneTitle(String id){return id.equals("home")?"实体 HOME":id.equals("reboot")?"重启":id.equals("coldBoot")?"断电开机":"待机唤醒";}
    private void verification(){heading("使用验证");copy("按电视实际表现分别确认；服务连接不会自动记为通过。",true);LinearLayout group=ui.listGroup();sheet.body.addView(group,new LinearLayout.LayoutParams(-1,-2));for(String id:new String[]{"home","reboot","coldBoot","wake"})row(group,sceneTitle(id),object("verification").optString(id,"待验证"),"guard-scene-"+id,()->go("scene:"+id,"guard-result-pass"),true);}
    private void scene(){String id=screen.substring(6);heading(sceneTitle(id));copy("请完成对应的实体操作后，再记录电视实际表现。",false);if(!state.optBoolean("canVerify")){copy("当前路线尚不支持记录验证结果，请通过电脑核对。",true);return;}LinearLayout line=actions();for(String value:new String[]{"通过","失败","待验证"})action(line,value,"guard-result-"+value,()->saveVerification(id,value),false);}
    private void saveVerification(String id,String value){if(working)return;working=true;String revision=state.optString("revision");engine.worker.execute(()->{String problem="";JSONObject next=null;try{next=engine.verify(id,value,revision,()->!destroyed);}catch(Exception e){problem=DesktopSwitch.message(e);}String failure=problem;JSONObject response=next;ui.runOnUiThread(()->{working=false;if(!open())return;if(response!=null)state=response;screen="verify";error=failure;result=failure.isEmpty()?"已记录用户确认的实际结果。":"";render();});});}
    private void system(){Intent intent=MaintenanceHelpActivity.homeSettingsIntent(ui);if(intent==null||!ExternalActivityLauncher.open(ui,"maintenance.home_settings",intent)){go("computer","guard-computer-back");return;}result="请在电视系统中完成选择，返回后核对 HOME 行为。";refresh();render();}
    private void computer(){
        LinearLayout transfer=ui.row();transfer.setGravity(Gravity.CENTER_VERTICAL);transfer.setPadding(p(17.92f),p(17.92f),p(17.92f),p(17.92f));transfer.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,9.6f));block(transfer,14.08f,19.2f);
        ImageView qr=new QrCodeView(ui,ui.unit);qr.setContentDescription("电脑配置与恢复说明二维码");qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(p(5.12f),p(5.12f),p(5.12f),p(5.12f));
        try{com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(MaintenanceHelpActivity.INSTALL_PAGE,com.google.zxing.BarcodeFormat.QR_CODE,384,384,java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,2));int[] pixels=new int[384*384];for(int y=0;y<384;y++)for(int x=0;x<384;x++)pixels[y*384+x]=matrix.get(x,y)?0xff000000:0xffffffff;guide=android.graphics.Bitmap.createBitmap(pixels,384,384,android.graphics.Bitmap.Config.ARGB_8888);qr.setImageBitmap(guide);}catch(com.google.zxing.WriterException unavailable){qr.setContentDescription("电脑说明二维码不可用，请打开网址");}
        transfer.addView(qr,new LinearLayout.LayoutParams(p(184),p(184)));LinearLayout copy=ui.column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=p(24);transfer.addView(copy,cp);
        TextView title=text(DesktopTakeoverStatus.read(ui).code.equals("repair")?"通过电脑检查与修复":"通过电脑设置与恢复",19.2f,26.88f,UiPalette.TEXT);QuickControlPreviewView.weight(title,500);copy.addView(title);
        TextView note=text("扫码查看说明，在电脑上将请求复制给 AI 助手。",14.72f,25.024f,UiPalette.SECONDARY);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=np.bottomMargin=p(14.72f);copy.addView(note,np);
        TextView link=text("beatsad.github.io/quiet-tv/",18.688f,26.1632f,UiPalette.TEXT);link.setPaintFlags(link.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);copy.addView(link);
        copy("电脑会核对机型、固件和原有配置，具体改动由你确认。",false);copy("已有配置正常时无需重新配置；设置、修复和恢复分别处理。",true);
        sheet.body.addView(button("返回桌面管理","guard-computer-back",()->go("home","guard-computer"),false),new LinearLayout.LayoutParams(-2,p(46.08f)));
    }

    void resume(){if(open()){if(screen.equals("logs"))diagnostics.resume();start();}}
    void pause(){diagnostics.pause();stop();}
    private void back(){if(!open())return;if(screen.equals("home")){if(sharedReturn!=null)sharedReturn.run();else sheet.dialog.dismiss();}else if(screen.startsWith("scene:"))go("verify","guard-scene-"+screen.substring(6));else go("home","guard-more");}
    void save(Bundle saved){if(!open())return;Bundle value=new Bundle();value.putString("screen",screen.equals("confirm")?"home":screen);value.putString("focus",focus);saved.putBundle("desktop-guard",value);}
    private void readState(Bundle saved){Bundle value=saved.getBundle("desktop-guard");if(value==null)return;screen=value.getString("screen","home");if(screen.equals("pause")||screen.equals("restore"))screen="home";focus=value.getString("focus","");}
    void restore(Bundle saved){if(saved==null||!saved.containsKey("desktop-guard"))return;readState(saved);create();}
    private void releaseGuide(){if(guide!=null){guide.recycle();guide=null;}}
    void destroy(){destroyed=true;stop();diagnostics.destroy();if(sheet!=null&&sharedReturn==null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();}sheet=null;sharedReturn=null;releaseGuide();}
}
