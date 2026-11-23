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
    private final DesktopSwitch engine;
    private ManagementUi.Sheet sheet;
    private View origin;
    private Button close;
    private android.graphics.Bitmap guide;
    private String screen="home",focus="",error="",result="",confirmationRevision="",confirmationFocus="";
    private JSONObject state=new JSONObject(),confirmation;
    private boolean destroyed,refreshing,working,listening,resumed;
    private Runnable sharedReturn;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable changed=this::refresh;
    private final Runnable poll=()->{if(open()&&resumed){refresh();schedule();}};
    DesktopGuardSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;engine=DesktopSwitch.get(ui);}
    private boolean open(){return sheet!=null&&!destroyed;}
    android.app.Dialog dialog(){return sheet==null?null:sheet.dialog;}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value,float size,float line,int color){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,size,line,ui.unit,color);return view;}
    private void block(View view,float top,float bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(top);lp.bottomMargin=p(bottom);sheet.body.addView(view,lp);}
    private void copy(String value,boolean muted){block(text(value,muted?14.464f:17.28f,muted?23.8656f:29.376f,UiPalette.SECONDARY),12,12);}
    private void heading(String value){TextView view=text(value,17.28f,25.92f,UiPalette.TEXT);QuickControlPreviewView.weight(view,500);block(view,0,10.24f);}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});}
    private Button button(String title,String tag,Runnable action,boolean primary){Button view=ui.button(title,action);identify(view,tag);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE).outlineOnFocus());view.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));return view;}
    private LinearLayout actions(){LinearLayout row=ui.row();row.setGravity(Gravity.END);row.setClipChildren(false);row.setClipToPadding(false);block(row,30,0);row.getLayoutParams().height=p(46.08f);return row;}
    private Button action(LinearLayout row,String title,String tag,Runnable action,boolean primary){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);if(row.getChildCount()>0)lp.leftMargin=p(10);Button b=button(title,tag,action,primary);row.addView(b,lp);return b;}
    private void row(LinearLayout group,String title,String value,String tag,Runnable operation,boolean available){
        if(group.getChildCount()>0){
            View divider=new View(ui);divider.setBackgroundColor(UiPalette.EDGE);
            LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,Math.max(1,p(.5f)));
            line.leftMargin=line.rightMargin=p(13);group.addView(divider,line);
        }
        DesktopSettingsRow row=new DesktopSettingsRow(ui,title,"",false);
        row.hideIcon();row.value(value);row.textSizes(13.92f,11.52f);
        if(operation!=null){
            row.tail("arrow");identify(row,tag);row.setOnClickListener(v->operation.run());
        }else{
            row.setFocusable(false);row.setClickable(false);
        }
        row.available(available);group.addView(row,new LinearLayout.LayoutParams(-1,p(60)));
    }
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
    void detach(){stop();releaseGuide();sheet=null;sharedReturn=null;}
    boolean canGoBack(){return open()&&!working;}
    void handleBack(){back();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet("桌面管理");ManagementUi.Sheet own=sheet;
        close=sheet.settingsLayout("关闭",this::back);sheet.contentSize(560,470);identify(close,"sheet-close");sheet.settingsActions(false);
        sheet.dialog.setOnKeyListener((dialog,key,event)->KeyFeedback.backKey(ui,event,canGoBack(),this::back));
        sheet.dialog.setOnDismissListener(dialog->{if(sheet!=own)return;stop();releaseGuide();sheet=null;if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();closed.run();});
        render();sheet.show(null);start();restoreFocus();
    }
    private void restoreFocus(){
        if(!open())return;
        String key=focus;
        sheet.body.post(()->{
            if(!open())return;
            View target=focusable(key);
            if(target==null)for(String fallback:new String[]{"guard-settings-quiet","guard-settings-original","guard-settings-guard","guard-recovery","guard-computer-back"}){
                target=focusable(fallback);
                if(target!=null)break;
            }
            if(target==null)target=close;
            target.requestFocusFromTouch();
            android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);target.requestRectangleOnScreen(rect,false);
        });
    }
    private View focusable(String key){
        View view=key.isEmpty()?null:sheet.box.findViewWithTag(key);
        return view!=null&&view.isEnabled()&&view.isShown()&&view.isFocusable()?view:null;
    }
    private void go(String value,String target){OperationFeedback.get(ui).hide();screen=value;focus=target;error=result="";confirmation=null;render();}
    private void render(){
        if(!open())return;
        String target=focus;sheet.heading.setText("桌面管理");sheet.body.removeAllViews();releaseGuide();sheet.settingsActions(false);
        close.setText(screen.equals("home")&&sharedReturn==null?"关闭":"返回");close.setContentDescription(close.getText());
        close.setVisibility(screen.equals("confirm")?View.GONE:View.VISIBLE);
        if(screen.equals("computer"))computer();
        else if(screen.equals("confirm"))confirmation();
        else home();
        if(!error.isEmpty())copy(error,false);
        if(!result.isEmpty())copy(result,true);
        focus=target;restoreFocus();
    }
    private void home(){
        JSONObject settings=state.optJSONObject("settings");
        if(settings==null){copy("正在读取电视桌面状态",false);return;}
        boolean available=!working&&!state.optBoolean("busy");
        LinearLayout group=ui.listGroup();block(group,8,0);
        JSONArray rows=settings.optJSONArray("rows");
        if(rows!=null)for(int i=0;i<rows.length();i++){
            JSONObject entry=rows.optJSONObject(i);
            if(entry==null)continue;
            JSONObject action=entry.optJSONObject("action");
            String tag="guard-settings-"+entry.optString("id");
            Runnable clicked=action!=null&&action.optBoolean("enabled")?()->confirm(action,tag):null;
            row(group,entry.optString("title"),entry.optString("value"),tag,clicked,clicked==null||available);
        }
        String recovery=settings.optString("recovery"),reason=settings.optString("reason");
        if(!reason.isEmpty()&&!reason.equals(error))copy(reason,true);
        if(recovery.equals("system"))row(group,"设置 Quiet TV 桌面","","guard-recovery",this::system,available);
        else if(recovery.equals("authorize"))row(group,"去授权","在电视上确认","guard-recovery",this::authorize,available);
        else if(recovery.equals("computer"))row(group,"通过电脑设置与恢复","","guard-recovery",()->go("computer","guard-computer-back"),available);
        if(!available)copy("正在处理桌面操作，请等待核对结果。",true);
    }
    private void authorize(){
        ManagementAdb.get(ui).requestAuthorization();
        ui.feedback("请在电视上确认系统调试授权。");refresh();
    }
    private void confirm(JSONObject action,String tag){
        confirmation=action;confirmationRevision=state.optString("revision");confirmationFocus=tag;
        screen="confirm";focus="guard-cancel";error=result="";render();
    }
    private void confirmation(){
        if(confirmation==null){screen="home";home();return;}heading(confirmation.optString("title")+"？");copy(confirmation.optString("explanation"),false);
        LinearLayout line=actions();action(line,"取消","guard-cancel",this::back,false).setEnabled(!working);
        Button execute=action(line,working?"正在处理":"确认","guard-confirm",this::execute,true);execute.setEnabled(!working);
    }
    private void execute(){
        if(working||confirmation==null)return;working=true;String id=confirmation.optString("id"),revision=confirmationRevision;render();
        engine.worker.execute(()->{String failure="";JSONObject next=null;try{next=engine.manage(id,revision,()->!destroyed);}catch(Exception e){failure=DesktopSwitch.message(e);}
            JSONObject response=next;String problem=failure;ui.runOnUiThread(()->{working=false;if(!open())return;if(response!=null)state=response;error=problem;result=response==null?"":response.optString("message");screen="home";focus=problem.isEmpty()?"":confirmationFocus;confirmation=null;render();refresh();if(!problem.isEmpty())ui.error("桌面操作未完成，请核对当前状态");else ui.success("桌面配置已更新，请核对遥控器 HOME 键的实际表现");});});
    }
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
        sheet.body.addView(button("返回桌面管理","guard-computer-back",()->go("home","guard-recovery"),false),new LinearLayout.LayoutParams(-2,p(46.08f)));
    }

    void resume(){if(open())start();}
    void pause(){stop();}
    private void back(){
        if(!canGoBack())return;
        if(screen.equals("home")){
            if(sharedReturn!=null)sharedReturn.run();else sheet.dialog.dismiss();
        }else go("home",screen.equals("confirm")?confirmationFocus:"guard-recovery");
    }
    void save(Bundle saved){if(!open())return;Bundle value=new Bundle();value.putString("screen",screen.equals("confirm")?"home":screen);value.putString("focus",focus);saved.putBundle("desktop-guard",value);}
    private void readState(Bundle saved){Bundle value=saved.getBundle("desktop-guard");if(value==null)return;screen=value.getString("screen","home");if(!screen.equals("computer"))screen="home";focus=value.getString("focus","");}
    void restore(Bundle saved){if(saved==null||!saved.containsKey("desktop-guard"))return;readState(saved);create();}
    private void releaseGuide(){if(guide!=null){guide.recycle();guide=null;}}
    void destroy(){destroyed=true;stop();if(sheet!=null&&sharedReturn==null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();}sheet=null;sharedReturn=null;releaseGuide();}
}
