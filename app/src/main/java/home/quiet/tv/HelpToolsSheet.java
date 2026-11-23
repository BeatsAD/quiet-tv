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
    private final DesktopGuardSheet guard;
    private final UpdatePanel updates;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final UpdateRepository.Listener changed=this::updatesChanged;
    private ManagementUi.Sheet sheet;
    private Button close;
    private View origin;
    private String screen="help",entry="help",parent="",focus="",error="";
    private int topic=-1,aboutTab;
    private boolean active=true,destroyed;
    private Bitmap image;
    private AboutSheetContent about;
    private boolean openAboutLicense;
    private Bundle childState;
    static final String[] TITLES={"按 HOME 回到原桌面","开机没有进入 Quiet TV","更新无法安装","如何恢复原桌面","开机广告可以关闭吗"};
    static final String[] CONTENT={"先查看桌面接管状态。若需要处理，通过电脑检查当前机型的 HOME 配置，保留已有恢复记录。","默认桌面设置完成后，仍需分别确认重启、断电开机与待机唤醒。请通过电脑核对原有配置。","按提示允许安装来源，或使用系统安装器确认。返回应用不代表安装成功，需核对版本；无法安装时可通过电脑更新。","在桌面管理中选择切换到原桌面；再次切回时恢复切换前的设置。状态待核对时先检查原有恢复记录。","在「通用设置 → 桌面与启动 → 开机广告」查看支持情况。关闭效果因电视机型和系统版本而异。如果仍有广告，可通过电脑 AI 排查原因。"};
    private static final String[] ACTIONS={"查看桌面状态","设置与电脑协助","查看软件更新","桌面恢复","电脑 AI 排查"};
    HelpToolsSheet(AppManagementActivity ui,Runnable returned){this.ui=ui;this.returned=returned;diagnostics=new DiagnosticExportUi(ui);guard=new DesktopGuardSheet(ui,()->{});updates=new UpdatePanel(ui);UpdateRepository.get(ui).add(changed);}
    private void updatesChanged(){if(open()&&screen.equals("update")&&active)updates.changed();}
    String entry(){return entry;}
    private boolean open(){return sheet!=null&&!destroyed;}
    private int p(float value){return ui.px(value*.75f);}
    private TextView text(String value,float size,float line){TextView view=ui.label(value,12);QuickControlPreviewView.type(view,size,line,ui.unit,UiPalette.SECONDARY);return view;}
    private void block(View view,float top,float bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);int previous=sheet.body.getChildCount()==0?0:((LinearLayout.LayoutParams)sheet.body.getChildAt(sheet.body.getChildCount()-1).getLayoutParams()).bottomMargin;lp.topMargin=Math.max(0,p(top)-previous);lp.bottomMargin=p(bottom);sheet.body.addView(view,lp);}
    private void copy(String value,boolean muted){block(text(value,muted?14.464f:17.28f,muted?23.8656f:29.376f),muted?10.24f:17.28f,muted?19.2f:17.28f);}
    private void heading(String value){TextView title=text(value,17.28f,25.92f);QuickControlPreviewView.weight(title,500);block(title,0,10.24f);}
    private void identify(View view,String tag){view.setTag(tag);view.setId(androidx.core.view.ViewCompat.generateViewId());view.setFocusableInTouchMode(true);view.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});}
    private Button button(String title,String tag,Runnable action,boolean primary){Button view=ui.button(title,action);identify(view,tag);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE).outlineOnFocus());view.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));return view;}
    private LinearLayout actions(){LinearLayout row=ui.row();row.setGravity(Gravity.END);row.setClipChildren(false);row.setClipToPadding(false);block(row,24,0);row.getLayoutParams().height=p(46.08f);return row;}
    private void action(LinearLayout row,String title,String tag,Runnable run,boolean primary){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);if(row.getChildCount()>0)lp.leftMargin=p(8);row.addView(button(title,tag,run,primary),lp);}
    private LinearLayout group(String title){if(!title.isEmpty()){TextView label=text(title,17.28f,25.92f);QuickControlPreviewView.weight(label,500);block(label,0,8);}LinearLayout group=ui.listGroup();group.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));block(group,0,16);return group;}
    private void row(LinearLayout group,String title,String value,String tag,Runnable action){DesktopSettingsRow row=new DesktopSettingsRow(ui,title,"",false);row.hideIcon();row.value(value);row.tail("arrow");row.textSizes(13.92f,11.52f);identify(row,tag);row.setOnClickListener(v->action.run());group.addView(row,new LinearLayout.LayoutParams(-1,p(59.629f)));}
    void show(String kind){
        if(open())return;
        entry=screen=kind;parent=error="";focus=kind.equals("logs")?"diagnostic-retry":kind.equals("specialty")?"diagnostic-mark":"";
        topic=-1;childState=null;active=true;aboutTab=0;openAboutLicense=false;
        create();
    }
    private void create(){
        origin=ui.getCurrentFocus();
        sheet=ui.new Sheet("");
        close=sheet.settingsLayout("关闭",this::back);
        if(entry.equals("about"))sheet.fixedSize(808,480);
        else if(entry.equals("community"))sheet.contentSize(560,280);
        else sheet.contentSize(entry.equals("help")?560:450,460);
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
        else if(!screen.equals("update")&&!screen.equals("guard"))restoreFocus();
    }
    private void go(String next,String previous,String target){leave();screen=next;parent=previous;focus=target;error="";childState=null;render();}
    private void leave(){
        if(about!=null){about.detach();about=null;}
        diagnostics.hide();updates.detach();guard.detach();
        if(image!=null){image.recycle();image=null;}
    }
    private void restoreFocus(){if(!open())return;String key=focus;sheet.body.post(()->{if(!open())return;View target=sheet.box.findViewWithTag(key);if(target==null||!target.isShown()||!target.isEnabled())target=close;target.requestFocusFromTouch();android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);target.requestRectangleOnScreen(rect,false);});}
    private void render(){
        if(!open())return;String key=focus;sheet.body.removeAllViews();sheet.settingsActions(false);close.setOnClickListener(v->back());close.setVisibility(View.VISIBLE);close.setText(!parent.isEmpty()||screen.equals("help")&&topic>=0?"返回":"关闭");close.setContentDescription(close.getText());sheet.dialog.setOnKeyListener((d,k,e)->KeyFeedback.backKey(ui,e,open()&&(!screen.equals("guard")||guard.canGoBack()),this::back));
        sheet.heading.setText(screen.equals("community")?"加入交流群":"常见问题");
        if(screen.equals("help"))help();else if(screen.equals("community"))community();else if(screen.equals("boot-ads-guide")){sheet.heading.setText("电脑 AI 排查");BootAdSheet.guideContent(ui,sheet);}else if(screen.equals("logs"))diagnostics.show(sheet);else if(screen.equals("specialty"))diagnostics.show(sheet,DiagnosticExportUi.Page.SPECIALTY);else if(screen.equals("guard")){guard.showIn(sheet,()->go("help","","help-topic-action"),"home",childState);childState=null;}else if(screen.equals("update")){updates.openIn(sheet,this::returnFromUpdate,childState);childState=null;}
        if(!error.isEmpty()){TextView problem=text(error,15.36f,23.04f);problem.setTextColor(UiPalette.TEXT);block(problem,16,0);}focus=key;if(!screen.equals("update")&&!screen.equals("guard"))restoreFocus();
    }
    private void help(){if(topic<0){LinearLayout list=group("");for(int i=0;i<TITLES.length;i++){final int id=i;row(list,TITLES[i],"","help-topic-"+i,()->{topic=id;focus="help-topic-action";render();});}}else{heading(TITLES[topic]);copy(CONTENT[topic],false);LinearLayout line=actions();action(line,"返回常见问题","help-topic-back",()->{int previous=topic;topic=-1;focus="help-topic-"+previous;render();},false);action(line,ACTIONS[topic],"help-topic-action",()->go(topic==2?"update":topic==4?"boot-ads-guide":"guard","help",""),true);action(line,"诊断日志","help-topic-logs",()->go("logs","help","diagnostic-retry"),false);}}
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
    private void returnFromUpdate(){String previous=parent;if(previous.isEmpty()){if(open())sheet.dialog.dismiss();}else go(previous,"",previous.equals("about")?"help-about-update":"help-topic-action");}
    private void back(){
        if(!open())return;if(about!=null&&about.licenseOpen()){about.backFromLicense();return;}if(screen.equals("guard")){guard.handleBack();return;}if(screen.equals("update")){updates.handleBack();return;}
        if(screen.equals("help")&&topic>=0){int previous=topic;topic=-1;focus="help-topic-"+previous;render();return;}
        if(!parent.isEmpty()){String previous=parent;go(previous,"","help-topic-logs");return;}
        sheet.dialog.dismiss();
    }
    void resume(){
        active=true;
        if(!open())return;
        if(about!=null)about.resume();
        else if(screen.equals("update"))updates.resumed();
        else if(screen.equals("guard"))guard.resume();
        else if(screen.equals("logs")||screen.equals("specialty"))diagnostics.resume();
    }
    void permissionResult(int request){diagnostics.permissionResult(request);}
    void pause(){
        active=false;
        if(about!=null)about.pause();
        updates.pause();guard.pause();diagnostics.pause();
    }
    void save(Bundle state){
        if(!open())return;
        Bundle value=new Bundle();
        value.putString("screen",screen);value.putString("entry",entry);value.putString("parent",parent);
        value.putString("focus",focus);value.putInt("topic",topic);
        if(about!=null){
            value.putInt("about-tab",about.tab());
            value.putBoolean("about-license",about.licenseOpen());
            View focused=sheet.dialog.getCurrentFocus();
            Object tag=focused==null?null:focused.getTag();
            value.putString("focus",tag instanceof String&&!"about-content-scroll".equals(tag)?(String)tag:about.tabFocus());
        }else if(screen.equals("update")){
            Bundle child=new Bundle();updates.save(child);value.putBundle("child",child);
        }else if(screen.equals("guard")){
            Bundle child=new Bundle();guard.save(child);value.putBundle("child",child);
        }
        state.putBundle("help-tools",value);
    }
    void restore(Bundle state){
        Bundle value=state==null?null:state.getBundle("help-tools");
        if(value==null)return;
        screen=value.getString("screen","help");entry=value.getString("entry","help");
        parent=value.getString("parent","");focus=value.getString("focus","");
        topic=value.getInt("topic",-1);childState=value.getBundle("child");active=true;
        if(entry.equals("about")){
            boolean source=screen.equals("opensource")||screen.equals("license");
            aboutTab=value.getInt("about-tab",source?2:0);
            openAboutLicense=value.getBoolean("about-license",screen.equals("license"));
            if(!value.containsKey("about-tab"))focus=source?"help-opensource":"help-device";
            screen="about";parent="";
        }
        create();
    }
    void destroy(){destroyed=true;UpdateRepository.get(ui).remove(changed);leave();diagnostics.destroy();guard.destroy();worker.shutdownNow();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
