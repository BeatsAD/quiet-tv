package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
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
    private int topic=-1,generation;
    private boolean active=true,destroyed;
    private Bitmap image;
    private WebView license;
    private TextView memoryValue,storageValue;
    private Bundle childState;
    static final String[] TITLES={"按 HOME 回到原桌面","开机没有进入 Quiet TV","更新无法安装","如何恢复原桌面","开机广告可以关闭吗"};
    static final String[] CONTENT={"先查看桌面接管状态。若需要处理，通过电脑检查当前机型的 HOME 配置，保留已有恢复记录。","默认桌面设置完成后，仍需分别确认重启、断电开机与待机唤醒。请通过电脑核对原有配置。","按提示允许安装来源，或使用系统安装器确认。返回应用不代表安装成功，需核对版本；无法安装时可通过电脑更新。","在桌面管理中选择切换到原桌面；再次切回时恢复切换前的设置。状态待核对时先检查原有恢复记录。","支持情况取决于品牌、机型与固件。当前没有可通用于各电视的关闭方式，可先通过电脑查看适配说明。"};
    private static final String[] ACTIONS={"查看桌面状态","设置与电脑协助","查看软件更新","桌面恢复","查看电脑说明"};
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
    private Button button(String title,String tag,Runnable action,boolean primary){Button view=ui.button(title,action);identify(view,tag);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);view.setPadding(p(17.28f),p(9.6f),p(17.28f),p(9.6f));view.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?UiPalette.SELECTED:UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE,false,12));view.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));return view;}
    private LinearLayout actions(){LinearLayout row=ui.row();row.setGravity(Gravity.END);row.setClipChildren(false);row.setClipToPadding(false);block(row,24,0);row.getLayoutParams().height=p(46.08f);return row;}
    private void action(LinearLayout row,String title,String tag,Runnable run,boolean primary){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1);if(row.getChildCount()>0)lp.leftMargin=p(8);row.addView(button(title,tag,run,primary),lp);}
    private LinearLayout group(String title){if(!title.isEmpty()){TextView label=text(title,17.28f,25.92f);QuickControlPreviewView.weight(label,500);block(label,0,8);}LinearLayout group=ui.listGroup();group.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,15));block(group,0,16);return group;}
    private void row(LinearLayout group,String title,String value,String tag,Runnable action){DesktopSettingsRow row=new DesktopSettingsRow(ui,title,"",false);row.hideIcon();row.value(value);row.tail("arrow");row.textSizes(13.92f,11.52f);identify(row,tag);row.setOnClickListener(v->action.run());group.addView(row,new LinearLayout.LayoutParams(-1,p(59.629f)));}
    void show(String kind){if(open())return;entry=screen=kind;parent=error="";focus=kind.equals("about")?"help-device":"";topic=-1;childState=null;active=true;create();}
    private void create(){origin=ui.getCurrentFocus();sheet=ui.new Sheet("");close=sheet.settingsLayout("关闭",this::back);identify(close,"sheet-close");ManagementUi.Sheet own=sheet;BackNavigation.install(sheet.dialog,this::back);sheet.dialog.setOnDismissListener(d->{if(sheet!=own)return;leave();sheet=null;if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();returned.run();});render();sheet.show(null);if(!screen.equals("update")&&!screen.equals("guard"))restoreFocus();}
    private void go(String next,String previous,String target){leave();screen=next;parent=previous;focus=target;error="";childState=null;render();}
    private void leave(){++generation;memoryValue=storageValue=null;diagnostics.hide();updates.detach();guard.detach();if(image!=null){image.recycle();image=null;}if(license!=null){license.stopLoading();license.destroy();license=null;}}
    private void restoreFocus(){if(!open())return;String key=focus;sheet.body.post(()->{if(!open())return;View target=sheet.box.findViewWithTag(key);if(target==null||!target.isShown()||!target.isEnabled())target=close;target.requestFocusFromTouch();android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);target.requestRectangleOnScreen(rect,false);});}
    private void render(){
        if(!open())return;String key=focus;sheet.body.removeAllViews();sheet.settingsActions(false);close.setOnClickListener(v->back());close.setText(!parent.isEmpty()||screen.equals("help")&&topic>=0?"返回":"关闭");close.setContentDescription(close.getText());sheet.dialog.setOnKeyListener((d,k,e)->{if(k!=KeyEvent.KEYCODE_BACK&&k!=KeyEvent.KEYCODE_ESCAPE)return false;if(e.getAction()==KeyEvent.ACTION_UP)back();return true;});
        android.widget.FrameLayout.LayoutParams size=(android.widget.FrameLayout.LayoutParams)sheet.box.getLayoutParams();size.width=p(screen.equals("community")?656:screen.equals("about")||screen.equals("device")||screen.equals("opensource")||screen.equals("license")||screen.equals("update")?600:448);sheet.box.setLayoutParams(size);
        sheet.heading.setText(screen.equals("about")?"关于 Quiet TV":screen.equals("community")?"加入交流群":screen.equals("device")?"设备信息":screen.equals("opensource")?"开源信息":screen.equals("license")?"开源许可与致谢":"常见问题");
        if(screen.equals("help"))help();else if(screen.equals("community"))community();else if(screen.equals("about"))about();else if(screen.equals("device"))device();else if(screen.equals("opensource"))openSource();else if(screen.equals("license"))license();else if(screen.equals("logs"))diagnostics.show(sheet);else if(screen.equals("guard")){guard.showIn(sheet,()->go("help","","help-topic-action"),topic==4?"computer":"home",childState);childState=null;}else if(screen.equals("update")){updates.openIn(sheet,this::returnFromUpdate,childState);childState=null;}
        if(!error.isEmpty()){TextView problem=text(error,15.36f,23.04f);problem.setTextColor(UiPalette.TEXT);block(problem,16,0);}focus=key;if(!screen.equals("update")&&!screen.equals("guard"))restoreFocus();
    }
    private void help(){if(topic<0){LinearLayout list=group("");for(int i=0;i<TITLES.length;i++){final int id=i;row(list,TITLES[i],"","help-topic-"+i,()->{topic=id;focus="help-topic-action";render();});}}else{heading(TITLES[topic]);copy(CONTENT[topic],false);LinearLayout line=actions();action(line,"返回常见问题","help-topic-back",()->{int previous=topic;topic=-1;focus="help-topic-"+previous;render();},false);action(line,ACTIONS[topic],"help-topic-action",()->go(topic==2?"update":"guard","help",""),true);action(line,"导出诊断","help-topic-logs",()->go("logs","help","diagnostic-retry"),false);}}
    private void community(){
        LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);block(line,0,0);
        try(InputStream input=ui.getAssets().open(Build.VERSION.SDK_INT<18?"about/assets/qq-group.png":"about/assets/qq-group.webp")){image=BitmapFactory.decodeStream(input);if(image==null)throw new IOException();ImageView qr=new QrCodeView(ui,ui.unit);qr.setImageBitmap(image);qr.setContentDescription("QQ 交流群二维码");qr.setScaleType(ImageView.ScaleType.FIT_CENTER);qr.setPadding(p(8),p(8),p(8),p(8));line.addView(qr,new LinearLayout.LayoutParams(p(307.2f),p(307.2f)));}catch(IOException missing){copy("二维码暂不可用",false);}
        LinearLayout info=ui.column();LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=p(24);line.addView(info,ip);TextView title=text("提反馈、提建议",24,36);QuickControlPreviewView.weight(title,500);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.bottomMargin=p(12);info.addView(title,tp);TextView hint=text("用手机 QQ 扫码，或搜索群号加入。",17.28f,27.648f);info.addView(hint);TextView label=text("QQ群号",17.28f,27.648f);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(24);lp.bottomMargin=p(8);info.addView(label,lp);TextView number=text("1126775126",28,42);QuickControlPreviewView.weight(number,500);UiCompat.letterSpacing(number,.04f);info.addView(number);
    }
    private TextView value(LinearLayout group,String label,String value){LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,p(8),0,p(8));TextView name=text(label,18.688f,28.032f),detail=text(value,18.688f,28.032f);detail.setGravity(Gravity.END);line.addView(name,new LinearLayout.LayoutParams(-2,-2));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,-2,1);dp.leftMargin=p(16);line.addView(detail,dp);group.addView(line,new LinearLayout.LayoutParams(-1,-2));return detail;}
    private void aboutBrand(){
        LinearLayout brand=ui.row();brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(ui);logo.setImageResource(R.drawable.launcher_icon);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        androidx.core.view.ViewCompat.setImportantForAccessibility(logo,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(logo,new LinearLayout.LayoutParams(p(72),p(72)));
        LinearLayout copy=ui.column();TextView title=text("Quiet TV",22,33);title.setTextColor(UiPalette.TEXT);QuickControlPreviewView.weight(title,500);copy.addView(title);
        TextView version=text(BuildConfig.VERSION_NAME+"  "+UpdatePanel.channel(BuildConfig.UPDATE_CHANNEL)+"  构建 "+BuildConfig.VERSION_CODE,14.464f,23.8656f);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=p(10);copy.addView(version,vp);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.leftMargin=p(24);brand.addView(copy,size);block(brand,24,32);
    }
    private void about(){
        aboutBrand();LinearLayout links=group("");
        row(links,"设备信息","","help-device",()->go("device","about","sheet-close"));
        row(links,"开源信息","","help-opensource",()->go("opensource","about","help-project"));
    }
    private void device(){
        AboutDeviceInfo device=new AboutDeviceInfo(ui);LinearLayout details=group("");details.setPadding(p(16),p(8),p(16),p(8));
        value(details,"电视型号",device.model);value(details,"安卓版本",device.android);value(details,"系统架构",device.abis);
        memoryValue=value(details,"运行内存","读取中…");storageValue=value(details,"存储空间","读取中…");
        AboutDeviceInfo.Screen display=AboutDeviceInfo.screen(ui.getWindowManager().getDefaultDisplay());value(details,display.label,display.value);value(details,"固件版本",device.firmware);
        int read=++generation;worker.execute(()->{AboutDeviceInfo.Resources resources=AboutDeviceInfo.resources(ui);ui.runOnUiThread(()->{if(open()&&active&&read==generation&&memoryValue!=null){memoryValue.setText(resources.memory);storageValue.setText(resources.storage);storageValue.setContentDescription("应用可访问的数据分区  "+resources.storage);}});});
    }
    private void openSource(){
        LinearLayout links=group("");row(links,"项目主页","","help-project",()->project("https://github.com/BeatsAD/quiet-tv"));
        row(links,"许可证与致谢","","help-license",()->go("license","opensource","help-license-document"));
        copy("github.com/BeatsAD/quiet-tv",true);
        try{
            int side=Math.max(384,p(224));com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode("https://github.com/BeatsAD/quiet-tv",com.google.zxing.BarcodeFormat.QR_CODE,side,side,java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN,2));
            int[] pixels=new int[side*side];for(int y=0;y<side;y++)for(int x=0;x<side;x++)pixels[y*side+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            image=Bitmap.createBitmap(pixels,side,side,Bitmap.Config.ARGB_8888);ImageView qr=new QrCodeView(ui,ui.unit);qr.setImageBitmap(image);qr.setContentDescription("项目主页二维码");qr.setScaleType(ImageView.ScaleType.FIT_CENTER);block(qr,0,0);qr.getLayoutParams().width=qr.getLayoutParams().height=p(224);
        }catch(com.google.zxing.WriterException unavailable){copy("二维码暂不可用",true);}
    }
    private void project(String address){try{ui.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));}catch(ActivityNotFoundException unavailable){ui.feedback("请用手机扫描二维码查看");}}
    private void license(){license=new WebView(ui);identify(license,"help-license-document");license.setBackgroundColor(0xff000000);WebSettings settings=license.getSettings();settings.setJavaScriptEnabled(false);settings.setTextZoom(240);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);if(Build.VERSION.SDK_INT>=21)settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);license.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,String url){return true;}});block(license,0,0);license.getLayoutParams().height=p(300);try(InputStream input=ui.getAssets().open("about/license.html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)out.write(bytes,0,count);String document=out.toString("UTF-8").replace("</head>","<style>body>a,body>h1{display:none}</style></head>");license.loadDataWithBaseURL("https://quiet-tv.invalid/about/",document,"text/html","UTF-8",null);}catch(IOException unavailable){error="许可证暂不可用";}}
    private void returnFromUpdate(){String previous=parent;if(previous.isEmpty()){if(open())sheet.dialog.dismiss();}else go(previous,"",previous.equals("about")?"help-about-update":"help-topic-action");}
    private void back(){
        if(!open())return;if(screen.equals("guard")){guard.handleBack();return;}if(screen.equals("update")){updates.handleBack();return;}
        if(screen.equals("help")&&topic>=0){int previous=topic;topic=-1;focus="help-topic-"+previous;render();return;}
        if(!parent.isEmpty()){String previous=parent;String target=screen.equals("device")?"help-device":screen.equals("opensource")?"help-opensource":screen.equals("license")?"help-license":"help-topic-logs";go(previous,previous.equals("opensource")?"about":"",target);return;}
        sheet.dialog.dismiss();
    }
    void resume(){active=true;if(!open())return;if(screen.equals("update"))updates.resumed();else if(screen.equals("guard"))guard.resume();else if(screen.equals("logs"))diagnostics.resume();else if(screen.equals("device")&&memoryValue!=null&&memoryValue.getText().toString().equals("读取中…"))render();if(license!=null)license.onResume();}
    void permissionResult(int request){diagnostics.permissionResult(request);}
    void pause(){active=false;++generation;updates.pause();guard.pause();diagnostics.pause();if(license!=null)license.onPause();}
    void save(Bundle state){if(!open())return;Bundle value=new Bundle();value.putString("screen",screen);value.putString("entry",entry);value.putString("parent",parent);value.putString("focus",focus);value.putInt("topic",topic);if(screen.equals("update")){Bundle child=new Bundle();updates.save(child);value.putBundle("child",child);}else if(screen.equals("guard")){Bundle child=new Bundle();guard.save(child);value.putBundle("child",child);}state.putBundle("help-tools",value);}
    void restore(Bundle state){Bundle value=state==null?null:state.getBundle("help-tools");if(value==null)return;screen=value.getString("screen","help");entry=value.getString("entry","help");parent=value.getString("parent","");focus=value.getString("focus","");topic=value.getInt("topic",-1);childState=value.getBundle("child");active=true;create();}
    void destroy(){destroyed=true;UpdateRepository.get(ui).remove(changed);leave();diagnostics.destroy();guard.destroy();worker.shutdownNow();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}}
}
