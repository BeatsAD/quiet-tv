package home.quiet.tv;

import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native TV dialogs; no updater controls or content are rendered in a WebView. */
final class UpdatePanel {
    private final Activity activity;private final UpdateRepository updates;private final float unit;
    private Dialog dialog;private LinearLayout box,actions;private TextView heading,progressText,status;private ProgressBar progress;
    private String mode="";private boolean closed,permissionReturning;
    private UpdateManifest.Artifact target;
    UpdatePanel(Activity activity){this.activity=activity;updates=UpdateRepository.get(activity);unit=activity.getResources().getDisplayMetrics().widthPixels/1920f;}
    private int px(float value){return Math.round(value*unit);}
    private TextView text(String value,int size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));view.setTextColor(color);view.setIncludeFontPadding(false);return view;}
    private void gap(int value){box.addView(new View(activity),new LinearLayout.LayoutParams(1,px(value)));}
    private void begin(String state,String title){
        dismiss();mode=state;dialog=new KeyFeedback.Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(px(60),px(48),px(60),px(48));
        GradientDrawable background=new GradientDrawable();background.setColor(0xff141a23);background.setCornerRadius(px(28));background.setStroke(Math.max(1,px(1)),0x38ffffff);box.setBackgroundDrawable(background);
        box.addView(text("软件更新",20,0xff96a8bf));gap(16);heading=text(title,38,0xfff2f4f8);box.addView(heading);gap(22);
        actions=new LinearLayout(activity);actions.setGravity(Gravity.END);actions.setClipChildren(false);box.setClipChildren(false);
        dialog.setContentView(box);dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d,key,event)->{if((key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE)&&event.getAction()==KeyEvent.ACTION_UP){if(!event.isCanceled()){back();KeyFeedback.back(activity);}return true;}return key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_ESCAPE;});
    }
    private Button button(String title,Runnable run){
        Button view=new KeyFeedback.Button(activity);view.setText(title);view.setAllCaps(false);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(25));view.setTextColor(0xffeff0f4);
        view.setMinimumHeight(0);view.setMinHeight(0);view.setMinimumWidth(0);view.setMinWidth(0);view.setPadding(px(28),0,px(28),0);view.setFocusableInTouchMode(true);
        if(Build.VERSION.SDK_INT>=21)view.setStateListAnimator(null);view.setBackgroundDrawable(ControlStyle.action(unit,35,0xff242a33,0x38ffffff));view.setOnClickListener(v->run.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,px(70));params.leftMargin=px(20);actions.addView(view,params);return view;
    }
    private void show(Button focus){
        gap(32);box.addView(actions,new LinearLayout.LayoutParams(-1,-2));dialog.show();Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(px(990),-2);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=.72f;window.setAttributes(params);window.getDecorView().setSystemUiVisibility(5894);}
        if(focus!=null)focus.requestFocus();
    }
    void check(){closed=false;begin("checking","正在检查更新");Button cancel=button("取消",()->{updates.cancelCheck();dismiss();});show(cancel);updates.check(true);changed();}
    void changed(){
        if(closed||dialog==null||activity.isFinishing())return;
        if(mode.equals("checking")&&!updates.checking){
            if(!updates.checkError.isEmpty()){
                begin("error",updates.checkError);Button retry=button("重试",this::check);
                if(updates.available!=null)button("查看已缓存更新",()->{if(updates.ready)ready();else details();});else button("关闭",this::dismiss);show(retry);
            }else if(updates.available==null){
                boolean incompatible=!updates.unavailableReason.isEmpty();begin("current",incompatible?"发现新版，暂无匹配安装包":"暂无可用更新");
                if(incompatible)box.addView(text(updates.unavailableReason,25,0xffb5c0cf));show(button("关闭",this::dismiss));
            }
            else if(updates.ready)ready();else details();
        }else if(mode.equals("download")){
            if(updates.downloading){heading.setText(updates.verifying?"正在校验安装包":"正在下载更新");
                int percent=updates.total==0?0:(int)(updates.received*100/updates.total);progress.setProgress(percent);progressText.setText(percent+"%  "+size(updates.received)+" / "+size(updates.total));status.setText(updates.switching?"正在切换下载线路":"");
            }else if(!updates.downloadError.isEmpty()){
                begin("error",updates.downloadError);box.addView(text("可重新下载，现有应用不会受影响。",25,0xffb5c0cf));button("稍后",this::dismiss);show(button("重新下载",this::download));
            }else if(updates.ready)ready();
        }
    }
    private void details(){
        target=updates.available;if(target==null){check();return;}
        begin("details","Quiet TV "+target.version()+"  "+channel(target.release.channel));
        String architecture=target.abis.isEmpty()?"":target.abis.size()>1?"通用":target.abis.get(0).equals("arm64-v8a")?"ARM64":target.abis.get(0).equals("armeabi-v7a")?"ARM32":target.abis.get(0);
        box.addView(text("构建号 "+target.code+"  "+system(target.minSdk)+(architecture.isEmpty()?"":"  "+architecture)+"  "+size(target.bytes),21,0xff8c9cb2));gap(28);
        LinearLayout notes=new LinearLayout(activity);notes.setOrientation(LinearLayout.VERTICAL);
        section(notes,"新增功能",target.release.features);section(notes,"问题修复",target.release.fixes);
        ScrollView scroll=new ScrollView(activity){@Override protected void onMeasure(int width,int height){super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(px(360),View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(notes);scroll.setFocusable(true);
        // Long notes scroll independently; actions stay visible and reachable with DPAD.
        box.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        button("稍后",this::dismiss);show(button("下载更新",this::download));
    }
    private void section(LinearLayout notes,String title,List<String> entries){
        if(entries.isEmpty())return;TextView label=text(title,22,0xff90a4bf);LinearLayout.LayoutParams group=new LinearLayout.LayoutParams(-1,-2);group.topMargin=px(notes.getChildCount()==0?0:20);notes.addView(label,group);
        for(String entry:entries){TextView line=text(entry,25,0xffe0e6ef);line.setPadding(0,px(9),0,px(5));notes.addView(line,new LinearLayout.LayoutParams(-1,-2));}
    }
    private void download(){
        if(target!=null&&(updates.available==null||!target.sha256.equals(updates.available.sha256))){check();return;}
        begin("download","正在下载更新");progressText=text("0%",28,0xffeff0f4);box.addView(progressText);gap(20);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);box.addView(progress,new LinearLayout.LayoutParams(-1,px(9)));gap(14);
        status=text("",21,0xff8e9fb4);box.addView(status);show(button("取消下载",this::cancelDownload));updates.download();changed();
    }
    private void cancelDownload(){
        // Keep the same network task while the user decides; it may finish behind this confirmation.
        begin("cancel","取消本次下载？");box.addView(text("取消后可重新下载。",25,0xffb5c0cf));button("取消下载",()->{updates.cancelDownload();dismiss();});show(button("继续下载",this::resumeDownload));
    }
    private void resumeDownload(){if(updates.ready){ready();return;}if(!updates.downloading){changedDownloadResult();return;}
        begin("download","正在下载更新");progressText=text("",28,0xffeff0f4);box.addView(progressText);gap(20);progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);box.addView(progress,new LinearLayout.LayoutParams(-1,px(9)));status=text("",21,0xff8e9fb4);box.addView(status);show(button("取消下载",this::cancelDownload));changed();
    }
    private void changedDownloadResult(){if(updates.ready)ready();else{begin("error","更新下载或校验失败");button("稍后",this::dismiss);show(button("重新下载",this::download));}}
    private void ready(){
        target=updates.available;if(target==null){dismiss();return;}begin("ready","更新已准备好");box.addView(text("Quiet TV "+target.version()+"  "+channel(target.release.channel),25,0xffb5c0cf));gap(18);
        box.addView(text("安装时 Quiet TV 可能暂时退出。完成后可重新打开或按 HOME 返回。",25,0xffb5c0cf));button("稍后安装",this::dismiss);show(button("安装更新",this::install));
    }
    private void install(){
        if(target==null||updates.available==null||!target.sha256.equals(updates.available.sha256)||!updates.ready){check();return;}
        if(!UpdateInstaller.permitted(activity)){
            begin("permission","允许 Quiet TV 安装更新");box.addView(text("继续后，请按系统提示允许 Quiet TV 安装应用，再确认安装。",25,0xffb5c0cf));
            button("稍后",this::dismiss);button("其他更新方式",this::restricted);show(button("继续",()->{permissionReturning=true;dispatchInstall();}));return;
        }
        dispatchInstall();
    }
    private void dispatchInstall(){
        begin("verify","正在核对安装包");show(null);
        updates.install((file,error)->{if(closed||activity.isFinishing())return;if(file==null){begin("error",error);show(button("关闭",this::dismiss));return;}
            if(UpdateInstaller.launch(activity,file)){updates.dispatched();dismiss();}else restricted("无法打开系统安装器");});
    }
    private void restricted(){restricted("");}
    private void restricted(String reason){
        begin("guide",reason.isEmpty()?"通过电脑更新":reason);box.addView(text("可在电脑上通过 Agent 协助更新，保留已有应用和配置。",25,0xffb5c0cf));gap(18);
        try{
            int size=192;com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode("https://beatsad.github.io/quiet-tv/",com.google.zxing.BarcodeFormat.QR_CODE,size,size,Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,2));
            int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            ImageView qr=new ImageView(activity);qr.setImageBitmap(android.graphics.Bitmap.createBitmap(pixels,size,size,android.graphics.Bitmap.Config.ARGB_8888));qr.setContentDescription("更新安装指引二维码");qr.setBackgroundColor(Color.WHITE);box.addView(qr,new LinearLayout.LayoutParams(px(230),px(230)));
        }catch(com.google.zxing.WriterException ignored){}
        gap(14);box.addView(text("安装指引：beatsad.github.io/quiet-tv/",23,0xffb5c0cf));show(button("关闭",this::dismiss));
    }
    void resumed(){closed=false;if(permissionReturning){permissionReturning=false;if(updates.ready&&UpdateInstaller.permitted(activity))ready();}}
    private void back(){if(mode.equals("verify"))return;if(mode.equals("download")){cancelDownload();return;}if(mode.equals("checking"))updates.cancelCheck();if(mode.equals("cancel")){resumeDownload();return;}dismiss();}
    void suspend(){closed=true;if(updates.downloading)updates.cancelDownload();if(mode.equals("checking"))updates.cancelCheck();dismiss();}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}mode="";}
    private static String system(int sdk){switch(sdk){case 14:return "安卓4.0至4.4";case 19:return "安卓4.4";case 21:return "安卓5及以上";case 23:return "安卓6及以上";case 26:return "安卓8及以上";case 31:return "安卓12及以上";default:return "安卓兼容版";}}
    static String channel(String value){return "stable".equals(value)?"正式版":"experimental".equals(value)?"实验版":"测试版";}
    private String size(long value){return String.format(Locale.CHINA,"%.1f MB",value/1048576d);}
}
