package home.quiet.tv;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native TV dialogs; no updater controls or content are rendered in a WebView. */
final class UpdatePanel {
    private final ManagementUi activity;private final UpdateRepository updates;private final float unit;
    private Dialog dialog;private LinearLayout box,actions;private TextView heading,progressText,status,installStatus;private ProgressBar progress;
    private String mode="",guideOrigin="";private boolean closed,permissionReturning;
    private boolean fileWaitReady;
    private UpdateManifest.Artifact target;
    private UpdateInstaller.LaunchResult permissionFailure=UpdateInstaller.LaunchResult.MISSING;
    private ManagementUi.Sheet sheet;
    private Runnable returned;
    private View origin;
    private boolean borrowed,checked,active=true,reminder;
    private Runnable reminderDrawn;
    private View detailsHeader,detailsFooterRule;
    private int compactSheetWidth;
    UpdatePanel(ManagementUi activity){this.activity=activity;updates=UpdateRepository.get(activity);unit=activity.getResources().getDisplayMetrics().widthPixels/1920f;}
    private int px(float value){return Math.round(value*unit);}
    private int p(float value){return Math.round(value*1.5f*unit);}
    private TextView text(String value,int size,int color){boolean muted=size<=23;TextView view=activity.label(value,12);QuickControlPreviewView.type(view,muted?14.464f:17.28f,muted?23.8656f:29.376f,activity.unit,UiPalette.SECONDARY);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(muted?10.24f:17.28f);lp.bottomMargin=p(muted?19.2f:17.28f);view.setLayoutParams(lp);return view;}
    private void begin(String state,String title){
        clearDetailsLayout();
        closed=false;mode=state;
        if(sheet==null){origin=activity.getCurrentFocus();sheet=activity.new Sheet("软件更新");sheet.settingsLayout("关闭",this::handleBack);dialog=sheet.dialog;BackNavigation.install(dialog,this::handleBack);dialog.setOnDismissListener(d->{clearDetailsLayout();sheet=null;dialog=null;mode="";if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();});}
        Button navigation=(Button)sheet.box.findViewWithTag("sheet-close");
        navigation.setVisibility(state.equals("cancel")||state.equals("file_retry")?View.GONE:View.VISIBLE);
        navigation.setText(borrowed||state.equals("notes")?"返回":"关闭");
        navigation.setContentDescription(null);
        navigation.setOnClickListener(v->handleBack());
        sheet.heading.setText("软件更新");sheet.contentWidth(450,450);sheet.settingsActions(false);sheet.actions.removeAllViews();sheet.scroll.setFocusable(!state.equals("details"));box=sheet.body;box.removeAllViews();installStatus=null;
        if(!state.equals("details")){
            heading=activity.label(title,12);
            QuickControlPreviewView.type(heading,17.28f,25.92f,activity.unit,UiPalette.TEXT);
            QuickControlPreviewView.weight(heading,500);
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
            hp.bottomMargin=p(10.24f);
            box.addView(heading,hp);
            if(state.equals("error"))OperationFeedback.status(heading,OperationFeedback.Kind.ERROR);
            else if(state.equals("installed")||state.equals("ready"))OperationFeedback.status(heading,OperationFeedback.Kind.SUCCESS);
            else if(Arrays.asList("checking","download","install").contains(state))OperationFeedback.status(heading,OperationFeedback.Kind.PROGRESS);
        }
        actions=activity.row();actions.setGravity(Gravity.END);actions.setClipChildren(false);actions.setClipToPadding(false);
        dialog.setOnKeyListener((d,key,event)->{if((key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE)&&event.getAction()==KeyEvent.ACTION_UP){if(!event.isCanceled()){handleBack();KeyFeedback.back(activity);}return true;}return key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE;});
    }
    private Button button(String title,Runnable run){
        Button view=activity.button(title,run);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*activity.unit);view.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));view.setTag("update-"+mode+"-"+actions.getChildCount());view.setId(androidx.core.view.ViewCompat.generateViewId());style(view,false);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,p(46.88f));if(actions.getChildCount()>0)params.leftMargin=p(8);actions.addView(view,params);return view;
    }
    private void style(Button view,boolean primary){view.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));view.setBackgroundDrawable(new SettingsFocusDrawable(activity.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE).outlineOnFocus());}
    private void show(Button focus){
        boolean direct=mode.equals("checking");actions.setGravity(Gravity.END);
        if(focus!=null&&!mode.equals("checking")&&!mode.equals("download")&&!mode.equals("installed"))style(focus,true);
        if(actions.getChildCount()>0){
            if(mode.equals("details")){sheet.settingsActions(true);sheet.actions.addView(actions,new LinearLayout.LayoutParams(-1,-1));}
            else{LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,p(46.88f));lp.topMargin=p(direct?0:24);box.addView(actions,lp);}
        }
        int previous=0;for(int i=0;i<box.getChildCount();i++){View view=box.getChildAt(i);LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)view.getLayoutParams();int bottom=lp.bottomMargin;lp.topMargin=Math.max(0,lp.topMargin-previous);previous=bottom;}
        if(!dialog.isShowing())sheet.show(null);if(focus!=null)focus.requestFocusFromTouch();
    }
    void openIn(ManagementUi.Sheet value,Runnable back,Bundle state){
        sheet=value;dialog=value.dialog;returned=back;borrowed=true;active=true;closed=false;checked=state!=null&&state.getBoolean("checked");permissionReturning=state!=null&&state.getBoolean("permissionReturning");String previous=state==null?"":state.getString("mode","");target=updates.available;
        if(updates.installBusy||updates.installNotice||previous.equals("install")||previous.equals("file_wait")||previous.equals("pending"))installationChanged();
        else if(previous.equals("guide")){restricted();guideOrigin=state.getString("guideOrigin","");}
        else if(previous.equals("cancel")&&updates.downloading)cancelDownload();
        else if(previous.equals("file_retry"))retryFileInstall();
        else if(permissionReturning||previous.equals("permission"))permission(false);
        else if(previous.equals("permissionMissing"))permissionUnavailable(UpdateInstaller.LaunchResult.valueOf(state.getString("permissionFailure","MISSING")));
        else if(previous.equals("incompatible")&&!updates.unavailableReason.isEmpty())incompatible();
        else if(previous.equals("current"))current();
        else if(previous.equals("error")){if(!updates.downloadError.isEmpty())changedDownloadResult();else{begin("checking","正在检查更新");show(null);changed();}}
        else if(updates.downloading||updates.verifying)resumeDownload();else if(updates.ready){if(reminder)details();else ready();}
        else if(updates.checking){begin("checking","正在检查更新");show(button("取消",()->{updates.cancelCheck();current();}));}
        else if(target!=null)details();else current();
    }
    private void current(){
        begin("current","Quiet TV "+BuildConfig.VERSION_NAME);box.addView(text(channel(BuildConfig.UPDATE_CHANNEL)+"  构建 "+BuildConfig.VERSION_CODE,21,0));
        String message=!updates.checkError.isEmpty()?"上次检查未完成":updates.available!=null?"发现可用更新":checked||updates.checked?"暂无可用更新":"尚未检查更新";
        box.addView(text(message,25,0));if(updates.available!=null){button("检查更新",this::check);show(button("查看更新",this::details));}else show(button(checked||updates.checked?"重新检查":"检查更新",this::check));
    }
    void check(){closed=false;begin("checking","正在检查更新");Button cancel=button("取消",()->{updates.cancelCheck();current();});show(cancel);updates.check(true);changed();}
    private void checkFailed(){begin("error","检查更新失败");box.addView(text(updates.checkError,25,0));show(button("重试",this::check));}
    void changed(){
        if(closed||!active||activity.isFinishing())return;
        if(mode.equals("install")||updates.installNotice||(mode.equals("pending")&&fileWaitReady!=updates.ready)){installationChanged();return;}
        if(dialog==null)return;
        if(mode.equals("checking")&&!updates.checking){
            if(!updates.checkError.isEmpty())checkFailed();
            else if(updates.available==null){checked=updates.checked;if(updates.unavailableReason.isEmpty())current();else incompatible();}
            else if(updates.ready)ready();else details();
        }else if(mode.equals("download")){
            if(updates.downloading||updates.verifying){UpdateState state=updates.snapshot();heading.setText(updates.verifying?"正在校验安装包":"正在下载更新");
                int percent=updates.total==0?0:(int)(updates.received*100/updates.total);progress.setProgress(percent);progressText.setText(percent+"%");status.setText((state.download==UpdateState.Download.SWITCHING?"正在切换下载线路  ":"")+size(updates.received)+" / "+size(updates.total));actions.setVisibility(updates.verifying?View.GONE:View.VISIBLE);
            }else if(!updates.downloadError.isEmpty())changedDownloadResult();else if(updates.ready)ready();
        }
    }
    private void version(){
        TextView version=text("Quiet TV "+target.version(),25,UiPalette.TEXT);QuickControlPreviewView.type(version,28.8f,40,activity.unit,UiPalette.TEXT);QuickControlPreviewView.weight(version,500);LinearLayout.LayoutParams vp=(LinearLayout.LayoutParams)version.getLayoutParams();vp.topMargin=0;vp.bottomMargin=p(8);box.addView(version);
        TextView metadata=text(channel(target.release.channel)+"  构建 "+target.code+"  "+size(target.bytes),21,0);LinearLayout.LayoutParams mp=(LinearLayout.LayoutParams)metadata.getLayoutParams();mp.topMargin=0;mp.bottomMargin=p(16);box.addView(metadata);
    }
    void openReminder(ManagementUi.Sheet value,Runnable back,Runnable shown){
        reminder=true;reminderDrawn=shown;
        openIn(value,back,null);
    }
    private void details(){
        target=updates.available;
        if(target==null){current();return;}
        begin("details","");
        boolean hasNotes=!target.release.features.isEmpty()||!target.release.fixes.isEmpty();
        prepareDetailsLayout(hasNotes);

        Button later=button("稍后",this::dismiss);
        Button next=button(updates.ready?"安装更新":"下载更新",updates.ready?this::install:this::download);
        later.setMinWidth(px(145));
        next.setMinWidth(px(180));
        ((LinearLayout.LayoutParams)next.getLayoutParams()).leftMargin=px(20);
        Button initial=reminder?later:next;
        Button close=(Button)sheet.box.findViewWithTag("sheet-close");
        close.setNextFocusDownId(initial.getId());
        later.setNextFocusUpId(close.getId());
        next.setNextFocusUpId(close.getId());
        if(hasNotes){
            LinearLayout notes=activity.column();
            section(notes,"新增功能",target.release.features);
            section(notes,"问题修复",target.release.fixes);
            box.addView(new UpdateNotesScroll(notes,close,later,next,initial),new LinearLayout.LayoutParams(-1,-2));
        }
        show(initial);
        if(reminder)style(next,true);
        if(reminderDrawn!=null){
            View anchor=sheet.box;android.view.ViewTreeObserver observer=anchor.getViewTreeObserver();
            observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                public boolean onPreDraw(){
                    if(!anchor.hasWindowFocus()||!anchor.isShown()||anchor.getAlpha()==0)return true;
                    android.view.ViewTreeObserver current=anchor.getViewTreeObserver();
                    if(current.isAlive())current.removeOnPreDrawListener(this);
                    Runnable shown=reminderDrawn;reminderDrawn=null;
                    if(shown!=null)shown.run();return true;
                }
            });
        }
    }
    private void prepareDetailsLayout(boolean hasNotes){
        FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)sheet.box.getLayoutParams();
        compactSheetWidth=frame.width;
        frame.width=ViewGroup.LayoutParams.WRAP_CONTENT;
        sheet.box.setLayoutParams(frame);
        sheet.contentWidth(620,620);
        sheet.coolTint(true);
        sheet.heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(26));
        sheet.scroll.scrollTo(0,0);
        sheet.scroll.setVisibility(hasNotes?View.VISIBLE:View.GONE);

        LinearLayout header=activity.column();
        detailsHeader=header;
        header.setTag("update-version-header");
        TextView version=detailsText("Quiet TV "+target.version(),36,44,UiPalette.TEXT);
        QuickControlPreviewView.weight(version,650);
        header.addView(version,new LinearLayout.LayoutParams(-1,-2));
        String metadata="当前版本 "+BuildConfig.VERSION_NAME+"  "+channel(target.release.channel)+"  构建 "+target.code+"  "+size(target.bytes);
        TextView versionInfo=detailsText(metadata,15.36f,22.4f,UiPalette.SECONDARY);
        LinearLayout.LayoutParams infoParams=new LinearLayout.LayoutParams(-1,-2);
        infoParams.bottomMargin=px(18);
        header.addView(versionInfo,infoParams);
        if(hasNotes)header.addView(detailsRule(),new LinearLayout.LayoutParams(-1,Math.max(1,px(1))));
        sheet.box.addView(header,sheet.box.indexOfChild(sheet.scroll),new LinearLayout.LayoutParams(-1,-2));

        detailsFooterRule=detailsRule();
        LinearLayout.LayoutParams rule=new LinearLayout.LayoutParams(-1,Math.max(1,px(1)));
        rule.bottomMargin=px(16);
        sheet.box.addView(detailsFooterRule,sheet.box.indexOfChild(sheet.actions),rule);
    }
    private View detailsRule(){
        View rule=new View(activity);
        rule.setBackgroundColor(0x32ffffff);
        return rule;
    }
    private TextView detailsText(String title,float size,float height,int color){
        TextView label=activity.label(title,12);
        QuickControlPreviewView.type(label,size,height,activity.unit,color);
        return label;
    }
    private void clearDetailsLayout(){
        if(detailsHeader==null||sheet==null)return;
        sheet.box.removeView(detailsHeader);
        sheet.box.removeView(detailsFooterRule);
        detailsHeader=null;
        detailsFooterRule=null;
        FrameLayout.LayoutParams frame=(FrameLayout.LayoutParams)sheet.box.getLayoutParams();
        frame.width=compactSheetWidth;
        sheet.box.setLayoutParams(frame);
        sheet.contentWidth(450,450);
        sheet.coolTint(false);
        ManagementUi.styleSheetHeading(sheet.heading,activity.unit);
        sheet.scroll.setVisibility(View.VISIBLE);
        sheet.scroll.scrollTo(0,0);
        sheet.settingsActions(false);
        sheet.actions.removeAllViews();
        View close=sheet.box.findViewWithTag("sheet-close");
        close.setNextFocusDownId(View.NO_ID);
    }
    /** The full-width release notes keep the version header and actions outside their viewport. */
    private final class UpdateNotesScroll extends ScrollView {
        private final LinearLayout notes;
        private final Button close,later,next,preferred;
        UpdateNotesScroll(LinearLayout notes,Button close,Button later,Button next,Button preferred){
            super(activity);
            this.notes=notes;this.close=close;this.later=later;this.next=next;this.preferred=preferred;
            addView(notes,new FrameLayout.LayoutParams(-1,-2));
            setId(androidx.core.view.ViewCompat.generateViewId());
            setTag("update-full-notes");
            setFocusableInTouchMode(true);
            setScrollbarFadingEnabled(false);
            setVerticalFadingEdgeEnabled(true);
            setFadingEdgeLength(px(18));
            if(Build.VERSION.SDK_INT>=29)Api29.styleScrollBar(this,Math.max(2,px(5)));
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            setPadding(0,0,p(10),0);
            StateListDrawable outline=new StateListDrawable();
            outline.addState(new int[]{android.R.attr.state_focused},activity.surface(Color.TRANSPARENT,Color.TRANSPARENT,0x75ffffff,8));
            setBackgroundDrawable(outline);
        }
        @Override protected void onMeasure(int width,int height){
            int screenHeight=activity.getResources().getDisplayMetrics().heightPixels;
            int viewport=Math.min(p(252),Math.max(p(64),screenHeight-px(450)));
            if(View.MeasureSpec.getMode(height)!=View.MeasureSpec.UNSPECIFIED)
                viewport=Math.min(viewport,View.MeasureSpec.getSize(height));
            super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(viewport,View.MeasureSpec.AT_MOST));
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            super.onLayout(changed,l,t,r,b);
            boolean overflow=notes.getHeight()>getHeight();
            setVerticalScrollBarEnabled(overflow);
            setFocusable(overflow);
            close.setNextFocusDownId(overflow?getId():preferred.getId());
            int up=overflow?getId():close.getId();
            later.setNextFocusUpId(up);
            next.setNextFocusUpId(up);
        }
        @Override public boolean dispatchKeyEvent(KeyEvent event){
            int key=event.getKeyCode();
            if(!isFocused()||(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN))
                return super.dispatchKeyEvent(event);
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if(key==KeyEvent.KEYCODE_DPAD_UP&&getScrollY()==0)close.requestFocusFromTouch();
                else if(key==KeyEvent.KEYCODE_DPAD_DOWN&&getScrollY()>=notes.getHeight()-getHeight())
                    preferred.requestFocusFromTouch();
                else scrollBy(0,key==KeyEvent.KEYCODE_DPAD_DOWN?p(50):-p(50));
            }
            return true;
        }
    }
    @android.annotation.TargetApi(29)
    private static final class Api29 {
        static void styleScrollBar(ScrollView view,int width){
            GradientDrawable track=new GradientDrawable();
            track.setColor(0x2effffff);
            track.setCornerRadius(width/2f);
            GradientDrawable thumb=new GradientDrawable();
            thumb.setColor(0xccffffff);
            thumb.setCornerRadius(width/2f);
            view.setScrollBarSize(width);
            view.setVerticalScrollbarTrackDrawable(track);
            view.setVerticalScrollbarThumbDrawable(thumb);
        }
    }
    private void section(LinearLayout notes,String title,List<String> entries){
        if(entries.isEmpty())return;
        TextView label=detailsText(title,17.28f,23.04f,0xffa8ccea);
        QuickControlPreviewView.weight(label,600);
        LinearLayout.LayoutParams group=new LinearLayout.LayoutParams(-1,-2);
        group.topMargin=px(notes.getChildCount()==0?17:20);
        group.bottomMargin=px(4);
        notes.addView(label,group);
        for(String entry:entries){
            TextView line=detailsText(entry,17.28f,26.4f,0xe6ffffff);
            notes.addView(line,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void download(){
        if(target==null||updates.available==null||!target.sha256.equals(updates.available.sha256)){check();return;}
        downloadBody();updates.download();changed();
    }
    private void downloadBody(){
        target=updates.available;begin("download",updates.verifying?"正在校验安装包":"正在下载更新");if(target!=null)box.addView(text("Quiet TV "+target.version()+"  "+channel(target.release.channel),21,0));
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setPadding(0,0,0,0);progress.setContentDescription("更新下载进度");
        GradientDrawable track=new GradientDrawable();track.setColor(UiPalette.CONTROL);track.setCornerRadius(p(4.48f));GradientDrawable fill=new GradientDrawable();fill.setColor(UiPalette.TEXT);fill.setCornerRadius(p(4.48f));LayerDrawable layers=new LayerDrawable(new Drawable[]{track,new ClipDrawable(fill,Gravity.LEFT,ClipDrawable.HORIZONTAL)});layers.setId(0,android.R.id.background);layers.setId(1,android.R.id.progress);progress.setProgressDrawable(layers);box.addView(progress,new LinearLayout.LayoutParams(-1,p(6)));
        LinearLayout values=activity.row();values.setGravity(Gravity.CENTER_VERTICAL);status=text("0.0 MB / "+size(updates.total),21,0);progressText=text("0%",28,0);progressText.setGravity(Gravity.END);values.addView(status,new LinearLayout.LayoutParams(0,-2,1));values.addView(progressText,new LinearLayout.LayoutParams(-2,-2));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=p(12);box.addView(values,vp);show(button("取消下载",this::cancelDownload));
    }
    private void cancelDownload(){
        begin("cancel","取消本次下载？");Button cancel=button("取消",this::resumeDownload);button("确认取消下载",()->{updates.cancelDownload();current();});show(cancel);
    }
    private void resumeDownload(){if(updates.ready){ready();return;}if(!updates.downloading&&!updates.verifying){changedDownloadResult();return;}downloadBody();changed();}
    private void changedDownloadResult(){if(updates.ready)ready();else{begin("error","下载更新失败");box.addView(text(updates.downloadError.isEmpty()?"更新下载或校验失败":updates.downloadError,25,0));show(button("重新下载",this::download));}}
    private void ready(){
        target=updates.available;if(target==null){current();return;}begin("ready","更新已准备好");version();
        box.addView(text("请在系统安装器中确认安装。",21,0));button("稍后安装",this::current);show(button("安装更新",this::install));
    }
    private void install(){
        if(target==null||updates.available==null||!target.sha256.equals(updates.available.sha256)||!updates.ready){check();return;}
        if(updates.snapshot().install==UpdateState.Install.FILE_DISPATCHED){retryFileInstall();return;}
        if(UpdateInstaller.permission(activity)!=UpdateInstaller.Permission.ALLOWED){
            permission(false);return;
        }
        dispatchInstall();
    }
    private void permission(boolean returning){
        boolean unknown=UpdateInstaller.permission(activity)==UpdateInstaller.Permission.UNKNOWN;
        begin("permission",unknown?"无法读取安装授权状态":returning?"尚未允许安装更新":"允许 Quiet TV 安装更新");
        box.addView(text("可前往系统设置授权，或使用系统安装器确认安装。安装权限由电视系统检查。",25,UiPalette.SECONDARY));
        button("返回",this::ready);button("去系统设置",()->{
            permissionReturning=true;
            DiagnosticLog.info("update.permission","phase=request api="+Build.VERSION.SDK_INT);
            UpdateInstaller.LaunchResult result=UpdateInstaller.openPermission(activity);
            if(result!=UpdateInstaller.LaunchResult.DISPATCHED){permissionReturning=false;permissionUnavailable(result);}
        });show(button("使用系统安装器",this::dispatchInstall));
    }
    private void permissionUnavailable(UpdateInstaller.LaunchResult result){
        permissionFailure=result;
        String title=result==UpdateInstaller.LaunchResult.MISSING?"系统未提供安装来源授权入口":result==UpdateInstaller.LaunchResult.BLOCKED?"系统限制了授权页访问":"安装授权页打开失败";
        begin("permissionMissing",title);box.addView(text("可尝试系统安装器，或通过电脑更新。",25,0));
        button("通过电脑更新",this::restricted);show(button("使用系统安装器",this::dispatchInstall));
    }
    private void dispatchInstall(){
        permissionReturning=false;
        if(target==null||updates.available==null||!target.sha256.equals(updates.available.sha256)||!updates.ready){check();return;}
        begin("install","正在准备安装");show(null);updates.install();
    }
    private void installationChanged(){
        UpdateState.Install state=updates.snapshot().install;
        if(state==UpdateState.Install.PREPARED){updates.dispatchInstall(activity);return;}
        if(state==UpdateState.Install.CONFIRMATION){updates.confirmInstall(activity);return;}
        if(state==UpdateState.Install.FILE_DISPATCHED||state==UpdateState.Install.SUBMITTED||state==UpdateState.Install.AWAITING_VERSION){
            updates.installNotice=false;
            if(!mode.equals("pending")||fileWaitReady!=updates.ready){
                fileWaitReady=updates.ready;begin("pending","安装结果待核对");
                box.addView(text("请在系统安装器中完成安装，再核对已安装版本。",25,0));
                button("稍后",()->{if(updates.ready)ready();else current();});
                show(button("核对结果",this::checkInstalled));
            }return;
        }
        if(state==UpdateState.Install.CONFIRMING){dismiss();return;}
        if(state==UpdateState.Install.PREPARING||state==UpdateState.Install.COMMITTING){
            if(!mode.equals("install")){begin("install","正在准备安装");show(null);}return;
        }
        updates.installNotice=false;
        if(state==UpdateState.Install.SUCCESS){begin("installed","更新已安装");box.addView(text("已核对 Quiet TV "+BuildConfig.VERSION_NAME+"  构建 "+BuildConfig.VERSION_CODE,25,0));show(button("完成",this::current));return;}
        begin("error",updates.installError.isEmpty()?"安装结果待核对":updates.installError);
        Button focus=button("关闭",this::dismiss);
        if(state==UpdateState.Install.FAILED||state==UpdateState.Install.INTERRUPTED)button("通过电脑更新",()->restricted(updates.installError));
        if(updates.ready)focus=button("重试",()->{if(target==null)ready();else install();});
        show(focus);
    }
    private void checkInstalled(){
        if(updates.snapshot().install==UpdateState.Install.FILE_DISPATCHED)updates.fileInstallReturned(Integer.MIN_VALUE,null);else updates.reconcile();
        if(updates.snapshot().install==UpdateState.Install.SUCCESS){installationChanged();return;}
        if(!mode.equals("pending"))return;if(installStatus==null){installStatus=activity.label("",12);QuickControlPreviewView.type(installStatus,15.36f,23.04f,activity.unit,UiPalette.TEXT);installStatus.setTag("update-install-result");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(16);box.addView(installStatus,lp);}installStatus.setText("已安装版本尚未变化，请完成系统安装后再核对");
    }
    private void retryFileInstall(){
        begin("file_retry","重新打开安装器？");
        box.addView(text("请先确认上次安装器已关闭，避免重复安装。",25,UiPalette.SECONDARY));
        button("取消",this::installationChanged);show(button("已关闭，重新安装",()->{if(updates.retryFileInstall()){target=updates.available;install();}}));
    }
    private void restricted(){restricted("");}
    private void restricted(String reason){
        guideOrigin=mode;begin("guide","通过电脑更新");heading.setVisibility(View.GONE);LinearLayout guide=activity.row();guide.setGravity(Gravity.CENTER_VERTICAL);guide.setPadding(p(17.92f),p(17.92f),p(17.92f),p(17.92f));guide.setBackgroundDrawable(activity.surface(0x06ffffff,0x06ffffff,0x20ffffff,12.8f));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=p(14.08f);gp.bottomMargin=p(19.2f);box.addView(guide,gp);
        try{
            int size=Math.max(384,px(480));com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode("https://beatsad.github.io/quiet-tv/",com.google.zxing.BarcodeFormat.QR_CODE,size,size,Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,2));
            int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            ImageView qr=new QrCodeView(activity,activity.unit);qr.setImageBitmap(android.graphics.Bitmap.createBitmap(pixels,size,size,android.graphics.Bitmap.Config.ARGB_8888));qr.setContentDescription("更新安装指引二维码");qr.setScaleType(ImageView.ScaleType.FIT_CENTER);guide.addView(qr,new LinearLayout.LayoutParams(p(184),p(184)));
        }catch(com.google.zxing.WriterException ignored){}
        LinearLayout info=activity.column();LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=p(24);guide.addView(info,ip);info.addView(text("通过电脑更新",25,0));info.addView(text("扫码查看说明，在电脑上将请求复制给 AI 助手。",21,0));info.addView(text("beatsad.github.io/quiet-tv/",21,0));box.addView(text("保留现有应用和配置，按本机系统选择适用的更新包。",25,0));show(button("返回更新",()->{if(guideOrigin.equals("incompatible"))incompatible();else if(updates.ready)ready();else current();}));
    }
    private void incompatible(){begin("incompatible","发现新版，暂无匹配安装包");box.addView(text("当前版本 "+BuildConfig.VERSION_NAME+"  构建 "+BuildConfig.VERSION_CODE,25,0));box.addView(text(updates.unavailableReason,25,0));button("返回",this::current);show(button("查看更新帮助",this::restricted));}
    void resumed(){active=true;closed=false;updates.fileInstallReturned(Integer.MIN_VALUE,null);changed();if(permissionReturning){permissionReturning=false;UpdateInstaller.Permission permission=UpdateInstaller.permission(activity);DiagnosticLog.info("update.permission","phase=return state="+permission);if(updates.ready&&permission==UpdateInstaller.Permission.ALLOWED)ready();else if(permission!=UpdateInstaller.Permission.ALLOWED)permission(true);}}
    void pause(){active=false;}
    void save(Bundle state){state.putString("mode",mode);state.putString("guideOrigin",guideOrigin);state.putString("permissionFailure",permissionFailure.name());state.putBoolean("checked",checked);state.putBoolean("permissionReturning",permissionReturning);}
    void handleBack(){if(mode.equals("notes"))details();else if(mode.equals("cancel"))resumeDownload();else if(mode.equals("file_retry"))installationChanged();else close();}
    void close(){dismiss();}
    void suspend(){detach();}
    void detach(){closed=true;clearDetailsLayout();if(!borrowed&&dialog!=null)dialog.dismiss();sheet=null;dialog=null;mode="";borrowed=false;returned=null;reminder=false;reminderDrawn=null;}
    private void dismiss(){Runnable back=returned;boolean shared=borrowed;detach();if(shared&&back!=null)back.run();}
    private static String system(int sdk){switch(sdk){case 14:return "安卓4.0至4.4";case 19:return "安卓4.4";case 21:return "安卓5及以上";case 23:return "安卓6及以上";case 26:return "安卓8及以上";case 31:return "安卓12及以上";default:return "安卓兼容版";}}
    static String channel(String value){return "independent".equals(value)?"测试版":"stable".equals(value)?"正式版":"experimental".equals(value)?"实验版":"测试版";}
    private String size(long value){return String.format(Locale.CHINA,"%.1f MB",value/1048576d);}
}
