package home.quiet.tv;

import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Help destinations replace one dialog body and retain the production update and recovery policies. */
final class HelpToolsSheet {
    private final AppManagementActivity ui;
    private final Runnable returned;
    private final DiagnosticExportUi diagnostics;
    private final UpdatePanel updates;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final UpdateRepository.Listener changed=this::updatesChanged;
    private ManagementUi.Sheet sheet;
    private Button close;
    private View origin;
    private String entry="",focus="";
    private int aboutTab;
    private boolean active=true,destroyed;
    private Bitmap image;
    private AboutSheetContent about;
    private boolean openAboutLicense;
    private Bundle childState;
    HelpToolsSheet(AppManagementActivity ui,Runnable returned){this.ui=ui;this.returned=returned;diagnostics=new DiagnosticExportUi(ui);updates=new UpdatePanel(ui);UpdateRepository.get(ui).add(changed);}
    private void updatesChanged(){if(open()&&entry.equals("update")&&active)updates.changed();}
    String entry(){return entry;}
    private boolean open(){return sheet!=null&&!destroyed;}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});}
    void show(String kind){
        if(open())return;
        entry=kind;focus=kind.equals("logs")?"diagnostic-retry":"";
        childState=null;active=true;aboutTab=0;openAboutLicense=false;
        create();
    }
    private void create(){
        origin=ui.getCurrentFocus();
        sheet=ui.new Sheet("");
        close=sheet.settingsLayout("关闭",this::back);
        if(entry.equals("about"))sheet.fixedSize(808,480);
        else if(entry.equals("community"))sheet.contentSize(560,280);
        else sheet.contentSize(450,460);
        identify(close,"sheet-close");
        ManagementUi.Sheet own=sheet;
        BackNavigation.install(sheet.dialog,this::back);
        sheet.dialog.setOnDismissListener(d->{
            if(sheet!=own)return;
            leave();sheet=null;
            if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();
            returned.run();
        });
        if(entry.equals("about")){
            sheet.box.setTag("about-dialog");
            sheet.settingsActions(false);
            sheet.dialog.setOnKeyListener((d,k,e)->KeyFeedback.backKey(ui,e,open(),this::back));
            about=new AboutSheetContent(ui,sheet,close,worker);
            about.start(aboutTab,active);
            if(focus.isEmpty())focus=about.tabFocus();
        }else render();
        sheet.show(null);
        if(about!=null&&openAboutLicense)about.showLicense();
        else if(!entry.equals("update"))restoreFocus();
    }
    private void leave(){
        if(about!=null){about.detach();about=null;}
        diagnostics.hide();updates.detach();
        if(image!=null){image.recycle();image=null;}
    }
    private void restoreFocus(){if(!open())return;String key=focus;sheet.body.post(()->{if(!open())return;View target=sheet.box.findViewWithTag(key);if(target==null||!target.isShown()||!target.isEnabled())target=close;target.requestFocusFromTouch();android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);target.requestRectangleOnScreen(rect,false);});}
    private void render(){
        String key=focus;
        sheet.settingsActions(false);
        close.setContentDescription(close.getText());
        sheet.dialog.setOnKeyListener((d,k,e)->KeyFeedback.backKey(ui,e,open(),this::back));
        if(entry.equals("community")){
            sheet.heading.setText("加入交流群");
            community();
        }else if(entry.equals("logs"))diagnostics.show(sheet);
        else if(entry.equals("update")){
            updates.openIn(sheet,()->{if(open())sheet.dialog.dismiss();},childState);
            childState=null;
        }
        focus=key;
    }
    private void community(){
        sheet.box.setTag("community-dialog");
        close.setNextFocusUpId(close.getId());
        close.setNextFocusDownId(close.getId());
        close.setNextFocusLeftId(close.getId());
        close.setNextFocusRightId(close.getId());

        LinearLayout line=ui.row();
        line.setTag("community-content");
        line.setPadding(ui.px(12),0,ui.px(12),0);
        int qrSize=ui.px(168);
        sheet.body.addView(line,new LinearLayout.LayoutParams(-1,qrSize));

        FrameLayout paper=new SurfaceCorners.Frame(ui);
        paper.setTag("community-qr-paper");
        paper.setBackgroundDrawable(ui.surface(0xfff8f8fc,0xfff8f8fc,0,8));
        if(Build.VERSION.SDK_INT<18)paper.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        line.addView(paper,new LinearLayout.LayoutParams(qrSize,qrSize));

        ImageView qr=new ImageView(ui);
        qr.setTag("community-qr");
        qr.setContentDescription("QQ 交流群二维码");
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setPadding(ui.px(9),ui.px(9),ui.px(9),ui.px(9));
        paper.addView(qr,new FrameLayout.LayoutParams(-1,-1));
        String asset=Build.VERSION.SDK_INT<18?"about/assets/qq-group.png":"phone-settings/assets/qq-group.webp";
        try(InputStream input=ui.getAssets().open(asset)){
            image=BitmapFactory.decodeStream(input);
            if(image==null)throw new IOException();
            qr.setImageBitmap(image);
        }catch(IOException missing){
            TextView unavailable=ui.label("二维码暂不可用",14);
            unavailable.setTag("community-qr-error");
            unavailable.setTextColor(UiPalette.INK);
            unavailable.setGravity(Gravity.CENTER);
            paper.addView(unavailable,new FrameLayout.LayoutParams(-1,-1));
        }

        LinearLayout info=ui.column();
        info.setTag("community-info");
        LinearLayout.LayoutParams infoSize=new LinearLayout.LayoutParams(0,-2,1);
        infoSize.leftMargin=ui.px(30);
        line.addView(info,infoSize);

        TextView hint=ui.label("用手机 QQ 扫码加入",22);
        hint.setTag("community-hint");
        QuickControlPreviewView.weight(hint,600);
        LinearLayout.LayoutParams hintSize=new LinearLayout.LayoutParams(-1,-2);
        hintSize.bottomMargin=ui.px(16);
        info.addView(hint,hintSize);
        communityDetail(info,"QQ群号","1126775126","community-number");
        communityDetail(info,"入群暗号","Quiet是最好的","community-alternate");
    }

    private void communityDetail(LinearLayout info,String label,String value,String tag){
        LinearLayout detail=ui.row();
        TextView name=ui.label(label,15);
        name.setTag(tag+"-label");
        name.setTextColor(UiPalette.SECONDARY);
        detail.addView(name,new LinearLayout.LayoutParams(ui.px(94),-1));

        TextView copy=ui.label(value,15);
        copy.setTag(tag);
        copy.setTextColor(0xe6ffffff);
        detail.addView(copy,new LinearLayout.LayoutParams(0,-1,1));
        info.addView(detail,new LinearLayout.LayoutParams(-1,ui.px(28)));
    }
    private void back(){
        if(!open())return;if(about!=null&&about.licenseOpen()){about.backFromLicense();return;}if(entry.equals("update")){updates.handleBack();return;}
        sheet.dialog.dismiss();
    }
    void resume(){
        active=true;
        if(!open())return;
        if(about!=null)about.resume();
        else if(entry.equals("update"))updates.resumed();
        else if(entry.equals("logs"))diagnostics.resume();
    }
    void permissionResult(int request){diagnostics.permissionResult(request);}
    void pause(){
        active=false;
        if(about!=null)about.pause();
        updates.pause();diagnostics.pause();
    }
    void save(Bundle state){
        if(!open())return;
        Bundle value=new Bundle();
        value.putString("entry",entry);value.putString("focus",focus);
        if(about!=null){
            value.putInt("about-tab",about.tab());
            value.putBoolean("about-license",about.licenseOpen());
            View focused=sheet.dialog.getCurrentFocus();
            Object tag=focused==null?null:focused.getTag();
            value.putString("focus",tag instanceof String&&!"about-content-scroll".equals(tag)?(String)tag:about.tabFocus());
        }else if(entry.equals("update")){
            Bundle child=new Bundle();updates.save(child);value.putBundle("child",child);
        }
        state.putBundle("help-tools",value);
    }
    void restore(Bundle state){
        Bundle value=state==null?null:state.getBundle("help-tools");
        if(value==null)return;
        entry=value.getString("entry","");
        if(entry.isEmpty()||entry.equals("help"))return;
        focus=value.getString("focus","");childState=value.getBundle("child");active=true;
        if(entry.equals("specialty")){entry="logs";focus="diagnostic-retry";}
        if(entry.equals("about")){
            String previousScreen=value.getString("screen","");
            boolean source=previousScreen.equals("opensource")||previousScreen.equals("license");
            aboutTab=value.getInt("about-tab",source?2:0);
            openAboutLicense=value.getBoolean("about-license",previousScreen.equals("license"));
            if(!value.containsKey("about-tab"))focus=source?"help-opensource":"help-device";
        }
        create();
    }
    void destroy(){destroyed=true;UpdateRepository.get(ui).remove(changed);leave();diagnostics.destroy();worker.shutdownNow();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
