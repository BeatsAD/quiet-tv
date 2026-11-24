package home.quiet.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Phone uploads save each confirmed item; local selections keep an explicit TV save. */
final class DesktopMediaImportPanel {
    static final int FILE_REQUEST=7314,READ_REQUEST=7315;
    private static final int USB_DIRECTORY_REQUEST=7316,USB_INSTALL_REQUEST=7317;
    interface Target {void saved(String target,String id,boolean apply)throws IOException;}
    private final ManagementUi ui;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService previews=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean receiving=new AtomicBoolean();
    private ManagementUi.Sheet sheet;
    private WallpaperUploadServer server;
    private volatile int generation;
    private volatile boolean destroyed,committing;
    private WallpaperImport.Prepared pending;
    private final ArrayList<String> receivedIds=new ArrayList<>();
    private String source="phone",name="",target="screensaver",notice="",address="",code="",host="";
    private volatile String type="image",savedId="";
    private boolean nameDirty,expired,retaining,browsing;
    private Target callback;
    private EditText nameField;
    private TextView connection,metadata,status;
    private ImageView qrView;
    private Button saveOnly,saveApply,restartPhone,closeButton;
    private View typeChoice;
    private final ArrayList<Button> sourceButtons=new ArrayList<>();
    private SettingsTabBar sourceTabs;
    private Bitmap qr;
    private View origin;
    private File boundary,folder;
    private Uri usbTree,usbDirectory;
    private final ArrayDeque<Uri> usbParents=new ArrayDeque<>();
    private final ArrayList<String> usbFolders=new ArrayList<>();
    private boolean usbVolumePicker;
    private UsbFileGrid usbGrid;
    private Button usbMore,usbUp;
    private FileTransferSource.Item usbSelection;
    private String usbFocus="";
    private boolean focusFiles;
    private String fileFocus="";
    private final Runnable check=new Runnable(){public void run(){if(sheet==null||server==null)return;if(committing){main.postDelayed(this,2000);return;}if(server.expired()||!host.equals(WallpaperUploadPanel.lanAddress(ui))){expired=true;stopServer();captureName();render();}else main.postDelayed(this,2000);}};
    DesktopMediaImportPanel(ManagementUi ui){this.ui=ui;}
    private int p(float n){return ui.px(n*.75f);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private boolean wallUpload(){return "wall".equals(target);}
    private boolean wallpaperUpload(){return "wallpaper".equals(target)||wallUpload();}
    private String problem(Throwable error){return error.getMessage()==null?tr("操作未完成，请重试","Operation not completed. Try again."):error.getMessage();}
    private TextView text(String value,float size,int color){TextView t=ui.label(value,12);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(size));t.setTextColor(color);return t;}
    private Button button(String label,String tag,Runnable action){Button b=ui.button(label,action);b.setTag(tag);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(16));b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));b.setPadding(p(12),p(8),p(12),p(8));b.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,UiPalette.CONTROL,false,UiPalette.EDGE));return b;}
    void show(String target,String type,Target callback){if(sheet!=null||destroyed)return;this.target=target;this.type=type;this.callback=callback;source="phone";name="";nameDirty=false;notice="";savedId="";expired=false;pending=null;receivedIds.clear();browsing=false;boundary=folder=null;retaining=false;open();startPhone();}
    private void open(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet(wallpaperUpload()?tr("上传壁纸","Upload wallpaper"):tr("导入素材","Import media"));closeButton=sheet.settingsLayout(tr("返回","Back"),this::back);sheet.settingsActions(true);ManagementUi.Sheet current=sheet;
        if(wallpaperUpload()){
            ((FrameLayout.LayoutParams)sheet.box.getLayoutParams()).width=ui.px(480);
            sheet.heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,22*ui.unit);
            ViewGroup title=(ViewGroup)closeButton.getParent();title.getLayoutParams().height=ui.px(30);sheet.heading.getLayoutParams().height=ui.px(30);
            closeButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*ui.unit);closeButton.getLayoutParams().width=ui.px(48);closeButton.getLayoutParams().height=ui.px(28);
        }
        sheet.contentSize(wallpaperUpload()?480:448,440);
        sheet.dialog.setOnKeyListener((d,key,event)->KeyFeedback.backKey(ui,event,sheet!=null&&!committing,this::back));sheet.dialog.setOnCancelListener(d->back());sheet.dialog.setOnDismissListener(d->{if(sheet==current)release();});render();sheet.show(sheet.body.findViewWithTag("import-source-"+source));
    }
    private void captureName(){if(nameField!=null)name=nameField.getText().toString();}
    private void render(){
        if(sheet==null)return;View focused=sheet.dialog.getCurrentFocus();String focus=focused!=null&&focused.getTag() instanceof String?(String)focused.getTag():"";boolean wallpaper=wallpaperUpload();captureName();
        if(sourceTabs==null)sheet.body.removeAllViews();
        else if(sheet.body.getChildCount()>1)sheet.body.removeViews(1,sheet.body.getChildCount()-1);
        sheet.actions.removeAllViews();nameField=null;connection=metadata=status=null;qrView=null;saveOnly=saveApply=restartPhone=null;typeChoice=null;
        usbGrid=null;
        if(wallpaper){
            sheet.heading.setText(wallpaperUsb()?tr("U 盘文件","USB files"):tr("上传壁纸","Upload wallpaper"));
            sheet.scroll.setFillViewport(wallpaperUsb());
            if(wallpaperUsb())sheet.fixedSize(672,442);
            else sheet.contentSize(480,440);
        }
        if(sourceTabs==null)createSourceTabs(wallpaper);
        for(Button tab:sourceButtons){
            tab.setSelected(("import-source-"+source).equals(tab.getTag()));
            if(!wallpaper)tab.setTypeface(ui.navigationTypeface(tab.isSelected()));
        }
        if(wallpaper){
            for(Button tab:sourceButtons)if(tab.isSelected())closeButton.setNextFocusDownId(tab.getId());
        }
        if(wallpaperUsb()){usbContent(focus);return;}
        if(wallpaper&&source.equals("phone")){sheet.settingsActions(false);wallpaperPhoneContent();updateState();restoreFocus(focus);return;}
        if(expired){TextView title=text(tr("上传会话已过期","Upload session expired"),19.2f,UiPalette.TEXT);title.setPadding(0,p(19.2f),0,0);sheet.body.addView(title);sheet.body.addView(text(tr("文件名和类型已保留，重新开启后继续。","Name and media type are retained."),15.36f,UiPalette.SECONDARY));sheet.body.addView(button(tr("重新开启","Start again"),"import-restart",()->{expired=false;notice="";render();startPhone();}));return;}
        if(source.equals("phone")){phoneContent();savedContent();}else{fileContent();
        if(!wallpaper||pending!=null||!savedId.isEmpty()){TextView label=text(tr("文件名称","File name"),16.64f,UiPalette.SECONDARY);QuickControlPreviewView.type(label,16.64f,23.434f,ui.unit,UiPalette.SECONDARY);LinearLayout.LayoutParams labelPosition=new LinearLayout.LayoutParams(-1,-2);labelPosition.topMargin=p(19.2f);sheet.body.addView(label,labelPosition);
        nameField=ui.input(name,tr("选择或上传素材后填写名称","Choose or upload a file"));nameField.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,12,0x1f000000,false,UiPalette.EDGE));nameField.setContentDescription(tr("素材文件名称","Media file name"));nameField.setTag("import-name");nameField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});nameField.setEnabled(!committing&&!receiving.get()&&savedId.isEmpty());nameField.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){name=s.toString();nameDirty=true;updateState();}public void afterTextChanged(Editable value){}});LinearLayout.LayoutParams inputPosition=new LinearLayout.LayoutParams(-1,p(57.929f));inputPosition.topMargin=p(10.24f);sheet.body.addView(nameField,inputPosition);}
        if(!wallUpload()){DisplayAppearanceChoice choice=new DisplayAppearanceChoice(ui,"import-type",tr("文件类型","File type"),new String[]{"image","video"},new String[]{tr("图片","Image"),tr("视频","Video")},new DisplayAppearanceChoice.Access(){public String read(){return type;}public void save(String value){type=value;updateState();}},true,new DisplayAppearanceChoice.Preview(){public void value(String key,Object value){}public void clear(String key){}},this::updateState);typeChoice=choice.dialogStyle();LinearLayout.LayoutParams typePosition=new LinearLayout.LayoutParams(-1,-2);typePosition.topMargin=p(19.2f);sheet.body.addView(choice,typePosition);}}
        metadata=text("",14.464f,UiPalette.SECONDARY);QuickControlPreviewView.type(metadata,14.464f,23.8656f,ui.unit,UiPalette.SECONDARY);LinearLayout.LayoutParams metaPosition=new LinearLayout.LayoutParams(-1,-2);metaPosition.topMargin=p(10.24f);sheet.body.addView(metadata,metaPosition);if(!wallpaper){TextView requirements=text(tr("图片输入不超过 60 MB；素材库不再限定数量与总容量，受可用存储空间限制。视频不预设文件大小或帧率上限，播放取决于电视解码能力。","Images up to 60 MB. Library size depends on available storage. Videos have no fixed size or frame-rate limit; playback depends on device support."),14.464f,UiPalette.SECONDARY);QuickControlPreviewView.type(requirements,14.464f,23.8656f,ui.unit,UiPalette.SECONDARY);LinearLayout.LayoutParams requirementPosition=new LinearLayout.LayoutParams(-1,-2);requirementPosition.topMargin=p(19.2f);sheet.body.addView(requirements,requirementPosition);}
        if(wallpaper){
            status=text(notice,15.36f,UiPalette.TEXT);status.setTag("import-notice");sheet.body.addView(status);sheet.settingsActions(pending!=null||!savedId.isEmpty());
            saveOnly=sheet.action(tr("保存壁纸","Save wallpaper"),()->save(false));saveOnly.setTag("import-save");updateState();restoreFocus(focus);return;
        }
        status=text(notice,15.36f,UiPalette.TEXT);status.setTag("import-notice");sheet.body.addView(status);Button cancel=sheet.action(source.equals("phone")?tr("关闭","Close"):tr("取消","Cancel"),this::close);cancel.setEnabled(!committing);saveOnly=sheet.action(source.equals("phone")?tr("完成","Done"):tr("仅保存","Save only"),()->save(false));saveOnly.setTag("import-save");saveApply=sheet.action(source.equals("phone")?tr("使用所选素材","Use selected media"):target.equals("screensaver")?tr("导入并用于屏保","Import and use for screensaver"):tr("导入并用于桌面","Import and use for desktop"),()->save(true));saveApply.setTag("import-apply");ui.emphasize(saveApply,true);for(int n=0;n<sheet.actions.getChildCount();n++)((TextView)sheet.actions.getChildAt(n)).setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));updateState();
    }
    private void createSourceTabs(boolean wallpaper){
        sourceTabs=wallpaper?new SettingsTabBar(ui,ui.unit,false){
            private final Paint divider=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                divider.setColor(UiPalette.EDGE);
                divider.setStrokeWidth(ui.px(.75f));
                for(int n=1;n<3;n++)canvas.drawLine(getWidth()*n/3f,ui.px(8),getWidth()*n/3f,getHeight()-ui.px(8),divider);
            }
        }:ui.settingsTabs();
        int inset=ui.px(wallpaper?2:3);
        sourceTabs.setPadding(inset,inset,inset,inset);
        sourceTabs.setWillNotDraw(!wallpaper);
        sourceTabs.setBackgroundDrawable(ui.surface(0x1f000000,0x1f000000,wallpaper?UiPalette.EDGE_STRONG:0,999));
        String[] sources=wallpaper?new String[]{"phone","local","usb"}:new String[]{"local","usb","phone"};
        for(String id:sources){
            String name=id.equals("local")?tr("本机文件","Local files"):id.equals("usb")
                    ?tr(wallpaper?"U盘":"U 盘","USB drive"):tr(wallpaper?"扫码上传":"手机上传",wallpaper?"Scan to upload":"Phone upload");
            SettingsTabBar.Tab tab=ui.settingsTab(sourceTabs,name,()->changeSource(id));
            tab.setTag("import-source-"+id);
            tab.setId(androidx.core.view.ViewCompat.generateViewId());
            tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,wallpaper?18*ui.unit:p(16));
            if(wallpaper)tab.setPadding(0,0,0,0);
            else tab.setPadding(ui.px(12),ui.px(6),ui.px(12),ui.px(6));
            boolean light=!wallpaper||!id.equals("usb");
            tab.selectedSurface(light?0xe6ffffff:0x55ffffff,light);
            tab.setOnFocusChangeListener((view,on)->{if(on&&!source.equals(id))changeSource(id);});
            sourceButtons.add(tab);
            LinearLayout.LayoutParams size=wallpaper?new LinearLayout.LayoutParams(0,-1,1)
                    :new LinearLayout.LayoutParams(ui.px(id.equals("usb")?54:72),ui.px(30));
            if(!wallpaper&&sourceTabs.getChildCount()>0)size.leftMargin=ui.px(3);
            sourceTabs.addView(tab,size);
        }
        sheet.body.addView(sourceTabs,new LinearLayout.LayoutParams(wallpaper?-1:-2,ui.px(wallpaper?38.4f:36)));
        if(wallpaper)for(int n=0;n<sourceButtons.size();n++){
            Button tab=sourceButtons.get(n);
            tab.setNextFocusLeftId(sourceButtons.get(Math.max(0,n-1)).getId());
            tab.setNextFocusRightId(sourceButtons.get(Math.min(2,n+1)).getId());
            tab.setNextFocusUpId(closeButton.getId());
        }
    }
    private void wallpaperPhoneContent(){
        TextView hint=ui.label(tr("手机与电视连接同一网络","Connect your phone and TV to the same network"),16);hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16.64f*ui.unit);hint.setTextColor(UiPalette.TEXT);hint.setGravity(Gravity.CENTER);LinearLayout.LayoutParams hintPosition=new LinearLayout.LayoutParams(-1,ui.px(26.88f));hintPosition.topMargin=ui.px(18);sheet.body.addView(hint,hintPosition);
        qrView=new QrCodeView(ui,ui.unit);qrView.setTag("import-qr");qrView.setContentDescription(tr("壁纸上传二维码","Wallpaper upload QR code"));qrView.setScaleType(ImageView.ScaleType.FIT_CENTER);qrView.setImageBitmap(qr);qrView.setPadding(ui.px(6),ui.px(6),ui.px(6),ui.px(6));LinearLayout.LayoutParams qrPosition=new LinearLayout.LayoutParams(ui.px(192),ui.px(192));qrPosition.gravity=Gravity.CENTER_HORIZONTAL;qrPosition.topMargin=ui.px(18);sheet.body.addView(qrView,qrPosition);
        connection=ui.label("",16);connection.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,16*ui.unit);connection.setTextColor(UiPalette.SECONDARY);connection.setGravity(Gravity.CENTER);LinearLayout.LayoutParams addressPosition=new LinearLayout.LayoutParams(-1,ui.px(24));addressPosition.topMargin=ui.px(14);sheet.body.addView(connection,addressPosition);
        TextView requirements=text(WallpaperUploadPanel.requirements(ui.chinese(),!wallUpload()),14.464f,UiPalette.SECONDARY);
        requirements.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams requirementsPosition=new LinearLayout.LayoutParams(-1,-2);
        requirementsPosition.topMargin=ui.px(8);
        sheet.body.addView(requirements,requirementsPosition);
        status=text(expired?tr("上传已结束","Upload ended"):notice,15.36f,UiPalette.TEXT);status.setGravity(Gravity.CENTER);status.setTag("import-notice");sheet.body.addView(status);if(expired)notice=tr("上传已结束","Upload ended");
        restartPhone=button(tr("重新开启","Start again"),"import-restart",()->{expired=false;notice="";render();startPhone();});LinearLayout.LayoutParams retryPosition=new LinearLayout.LayoutParams(-2,ui.px(32));retryPosition.gravity=Gravity.CENTER_HORIZONTAL;retryPosition.topMargin=ui.px(8);sheet.body.addView(restartPhone,retryPosition);
    }
    private void restoreFocus(String tag){
        ManagementUi.Sheet current=sheet;String selected="import-source-"+source;String next=tag.isEmpty()||tag.startsWith("import-source-")&&!tag.equals(selected)?selected:tag;String currentSource=source;sheet.body.post(()->{if(sheet!=current||!source.equals(currentSource))return;View focused=sheet.box.findViewWithTag(next);if(focused!=null&&focused.isShown()&&focused.isEnabled())focused.requestFocusFromTouch();});
    }
    private void updateState(){
        boolean valid=pending!=null&&(pending.video!=null)==type.equals("video")||!savedId.isEmpty();boolean enabled=(wallpaperUsb()?usbSelection!=null:valid)&&!committing&&!receiving.get();if(saveOnly!=null)saveOnly.setEnabled(enabled);if(saveApply!=null){saveApply.setEnabled(enabled);saveApply.setAlpha(1f);}if(saveOnly!=null)saveOnly.setAlpha(1f);
        boolean editable=!committing&&!receiving.get()&&savedId.isEmpty();for(Button button:sourceButtons){button.setEnabled(!committing&&!receiving.get()&&(savedId.isEmpty()||wallpaperUpload()));button.setAlpha(1f);}if(nameField!=null)nameField.setEnabled(editable);if(typeChoice!=null)enable(typeChoice,editable);if(closeButton!=null)closeButton.setEnabled(!committing);if(restartPhone!=null)restartPhone.setVisibility(server==null&&(!notice.isEmpty()||expired)?View.VISIBLE:View.GONE);
        WallpaperCatalog.Entry selected=WallpaperStore.get(ui).snapshot().find(savedId);if(metadata!=null)metadata.setText(pending==null?(selected==null?tr("尚未选择素材","No media selected"):(selected.video()?tr("视频","Video"):tr("图片","Image"))+"  "+selected.width+" × "+selected.height+"  "+FileTransferSource.size(selected.bytes)):((pending.video==null)?tr("图片","Image"):tr("视频","Video"))+"  "+pending.width+" × "+pending.height+"  "+FileTransferSource.size(pending.existing()==null?0:pending.existing().length()));if(status!=null){status.setText(receiving.get()?tr("正在接收素材…","Receiving media…"):notice);status.setVisibility(status.getText().length()==0?View.GONE:View.VISIBLE);}if(metadata!=null&&wallpaperUpload())metadata.setVisibility(pending==null&&selected==null?View.GONE:View.VISIBLE);if(connection!=null)connection.setText(address.isEmpty()?notice.isEmpty()?tr("正在开启上传…","Starting upload…"):wallpaperUpload()?"":notice:wallpaperUpload()?address:address+"  "+tr("连接码","Code")+"  "+code);
    }
    private void enable(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)enable(((ViewGroup)view).getChildAt(n),enabled);}
    private void phoneContent(){
        LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(p(17.92f),p(17.92f),p(17.92f),p(17.92f));row.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,9.6f));LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(-1,-2);position.topMargin=p(14.08f);sheet.body.addView(row,position);
        qrView=new QrCodeView(ui,ui.unit);qrView.setContentDescription(tr("素材上传二维码","Media upload QR code"));qrView.setImageBitmap(qr);qrView.setPadding(p(5.12f),p(5.12f),p(5.12f),p(5.12f));row.addView(qrView,new LinearLayout.LayoutParams(p(115.2f),p(115.2f)));
        LinearLayout copy=ui.column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=p(25.6f);row.addView(copy,cp);TextView title=text(tr("手机多选并保存素材","Select and save media on your phone"),19.2f,UiPalette.TEXT);QuickControlPreviewView.type(title,19.2f,26.667f,ui.unit,UiPalette.TEXT);QuickControlPreviewView.weight(title,500);copy.addView(title);TextView hint=text(tr("同一网络扫码，支持图片与视频混合多选","Scan on the same network to select images and videos together"),14.72f,UiPalette.SECONDARY);QuickControlPreviewView.type(hint,14.72f,25.024f,ui.unit,UiPalette.SECONDARY);LinearLayout.LayoutParams hintPosition=new LinearLayout.LayoutParams(-1,-2);hintPosition.setMargins(0,p(14.72f),0,p(14.72f));copy.addView(hint,hintPosition);connection=text("",12.16f,UiPalette.SECONDARY);QuickControlPreviewView.type(connection,12.16f,16.97f,ui.unit,UiPalette.SECONDARY);copy.addView(connection);restartPhone=button(tr("重新开启","Start again"),"import-restart",this::startPhone);copy.addView(restartPhone);updateState();
    }
    private void savedContent(){
        if(receivedIds.isEmpty())return;
        sheet.body.addView(text(tr("已保存 ","Saved ")+receivedIds.size()+tr(" 个素材"," items"),16.64f,UiPalette.TEXT));
        ScrollView scroll=new ScrollView(ui);LinearLayout list=ui.column();scroll.addView(list);sheet.body.addView(scroll,new LinearLayout.LayoutParams(-1,p(Math.min(179.2f,receivedIds.size()*44.8f))));
        WallpaperCatalog catalog=WallpaperStore.get(ui).snapshot();for(String id:receivedIds){WallpaperCatalog.Entry entry=catalog.find(id);if(entry==null)continue;
            Button row=button(entry.label(ui.chinese())+"  "+(entry.video()?tr("视频","Video"):tr("图片","Image")),"import-saved-"+id,()->{if(receiving.get()||committing)return;savedId=id;name=entry.label(ui.chinese());type=entry.video()?"video":"image";pending=null;render();});if(id.equals(savedId)){android.graphics.drawable.Drawable tick=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon("check")).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(tick,row.getTextColors());tick.setBounds(0,0,ui.px(12),ui.px(12));row.setCompoundDrawables(tick,null,null,null);row.setCompoundDrawablePadding(p(6));}row.setEnabled(!receiving.get()&&!committing);list.addView(row,new LinearLayout.LayoutParams(-1,p(44.8f)));
        }
    }
    private boolean wallpaperUsb(){return "wallpaper".equals(target)&&source.equals("usb");}
    private void changeSource(String id){
        if(source.equals(id)||committing||receiving.get()||!savedId.isEmpty()&&!wallpaperUpload())return;
        captureName();if(wallpaperUpload()&&!savedId.isEmpty()){pending=null;savedId="";name="";nameDirty=false;}
        if("wallpaper".equals(target)&&id.equals("usb")&&pending!=null){worker.execute(pending::discard);pending=null;name="";nameDirty=false;}
        source=id;expired=false;notice="";browsing=wallpaperUsb();boundary=folder=null;usbTree=usbDirectory=null;usbParents.clear();usbFolders.clear();usbVolumePicker=false;usbSelection=null;usbFocus="";focusFiles=false;fileFocus="";
        stopServer();render();restoreFocus("import-source-"+source);if(source.equals("phone"))startPhone();
    }
    private void fileContent(){
        LinearLayout block=ui.column();block.setClipToPadding(false);block.setPadding(p(19.2f),p(19.2f),p(19.2f),p(19.2f));if(wallpaperUpload())block.setPadding(0,0,0,0);else block.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,9.6f));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(14.08f);sheet.body.addView(block,lp);if(!wallpaperUpload())block.addView(text(source.equals("usb")?tr("U 盘文件","USB files"):tr("本机文件","Local files"),19.2f,UiPalette.TEXT));
        block.addView(button(browsing&&FileTransferSource.partialMediaAccess(ui)?tr("重新选择照片和视频","Reselect photos and videos"):tr("选择文件","Choose file"),"import-file",this::systemPicker));
        if(browsing)browse(block);
    }
    private void systemPicker(){
        if(committing||receiving.get())return;
        captureName();if(browsing&&!wallpaperUsb()){browseFiles(true);return;}
        for(String action:Build.VERSION.SDK_INT>=19?new String[]{Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT}:new String[]{Intent.ACTION_GET_CONTENT})try{
            Intent intent=new Intent(action).addCategory(Intent.CATEGORY_OPENABLE);
            if(wallpaperUsb())intent.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","application/vnd.android.package-archive"});
            else intent.setType(type.equals("video")?"video/*":"image/*");
            ui.startActivityForResult(intent,FILE_REQUEST);return;
        }catch(ActivityNotFoundException|SecurityException unavailable){}
        if(wallpaperUsb()){notice=tr("系统文件选择器不可用","System file picker unavailable");updateState();}
        else browseFiles(true);
    }
    private void pickUsbDirectory(){
        if(Build.VERSION.SDK_INT<21||committing||receiving.get())return;
        try{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            ui.startActivityForResult(intent,USB_DIRECTORY_REQUEST);
        }catch(ActivityNotFoundException|SecurityException unavailable){
            notice=tr("系统目录授权不可用，可直接浏览 U 盘或扫码上传","Folder access unavailable. Browse the USB drive or scan to upload.");updateState();
        }
    }
    private void browseFiles(boolean requestPermission){browsing=true;focusFiles=true;if(requestPermission&&FileTransferSource.requestPermission(ui,READ_REQUEST,true))return;render();}
    private void browse(LinearLayout block){
        int request=generation;File current=folder;
        boolean usb=source.equals("usb");
        worker.execute(()->{
            List<FileTransferSource.Item> items=Collections.emptyList();String error="";
            try{items=current==null?FileTransferSource.volumes(ui,usb):FileTransferSource.children(current);}
            catch(IOException|RuntimeException problem){error=problem(problem);}
            List<FileTransferSource.Item> ready=items;String failure=error;
            main.post(()->{
                if(sheet==null||request!=generation||!browsing||block.getParent()!=sheet.body)return;
                boolean root=current==null;
                if(!root)block.addView(button(tr("上一级","Parent folder"),"import-parent",()->{
                    if(committing||receiving.get())return;
                    folder=current.equals(boundary)?null:current.getParentFile();
                    focusFiles=true;render();
                }));
                if(!failure.isEmpty())block.addView(text(failure,15.36f,UiPalette.TEXT));
                ScrollView scroll=new ScrollView(ui);LinearLayout list=ui.column();scroll.addView(list);
                for(FileTransferSource.Item item:ready){
                    if(!item.directory&&(!WallpaperImport.media(item.name)||WallpaperImport.video(item.name,null)!=type.equals("video")))continue;
                    String label=root?(usb?tr("U 盘  ","USB drive  ")+item.name:tr("本地存储","Local storage")):item.name;
                    Button entry=button(label,"import-entry-"+item.name,()->{
                        if(committing||receiving.get())return;
                        if(!item.directory){choose(item);return;}
                        if(current==null)boundary=item.file;
                        folder=item.file;
                        focusFiles=true;render();
                    });
                    list.addView(entry,new LinearLayout.LayoutParams(-1,p(44.8f)));
                }
                if(list.getChildCount()>0)block.addView(scroll,new LinearLayout.LayoutParams(-1,p(Math.min(153.6f,44.8f*list.getChildCount()))));
                else if(failure.isEmpty()){
                        boolean partial=!usb&&FileTransferSource.partialMediaAccess(ui);
                        if(partial||ready.isEmpty())block.addView(text(partial?tr("当前没有已授权的照片和视频，请重新选择。","No selected photos or videos are available. Reselect media."):tr("没有可读取的文件或存储位置，可使用手机上传。","No readable storage or files. Phone upload is available."),15.36f,UiPalette.SECONDARY));
                }
                if(focusFiles)block.post(()->{
                    if(sheet==null||request!=generation||block.getParent()!=sheet.body)return;
                    focusFiles=false;View next=fileFocus.isEmpty()?null:sheet.box.findViewWithTag(fileFocus);fileFocus="";
                    if(next==null)next=list.getChildAt(0);
                    if(next==null)next=root?null:block.findViewWithTag("import-parent");
                    if(next==null)next=block.findViewWithTag("import-file");
                    if(next!=null){next.requestFocusFromTouch();android.graphics.Rect bounds=new android.graphics.Rect();next.getDrawingRect(bounds);next.requestRectangleOnScreen(bounds,false);}
                });
            });
        });
    }
    private void usbContent(String focus){
        LinearLayout bar=ui.row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundDrawable(ui.surface(UiPalette.CONTROL,UiPalette.CONTROL,UiPalette.EDGE,999));
        LinearLayout.LayoutParams barSize=new LinearLayout.LayoutParams(-1,ui.px(27));
        barSize.topMargin=ui.px(10);
        barSize.bottomMargin=ui.px(11);
        sheet.body.addView(bar,barSize);

        usbUp=null;
        if(usbHasParent()){
            usbUp=button("‹","usb-up",this::usbParent);
            usbUp.setContentDescription(tr("上一级","Parent folder"));
            usbUp.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(20));
            usbUp.setPadding(0,0,0,0);
            bar.addView(usbUp,new LinearLayout.LayoutParams(ui.px(34),-1));
        }
        TextView path=ui.label(tr("U 盘","USB drive")+usbPath(),12);
        path.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(13));
        path.setSingleLine();
        path.setEllipsize(TextUtils.TruncateAt.START);
        path.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        path.setPadding(ui.px(11),0,ui.px(4),0);
        bar.addView(path,new LinearLayout.LayoutParams(0,-1,1));
        usbMore=button("⋯","usb-more",this::usbOptions);
        usbMore.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,ui.px(17));
        usbMore.setPadding(0,0,0,0);
        usbMore.setContentDescription(tr("U 盘选项","USB options"));
        bar.addView(usbMore,new LinearLayout.LayoutParams(ui.px(40),-1));
        Button usbTab=sourceButtons.get(2);
        usbTab.setNextFocusDownId(usbMore.getId());
        usbMore.setNextFocusUpId(usbTab.getId());
        usbMore.setOnKeyListener(this::usbHeaderKey);
        if(usbUp!=null){
            usbUp.setNextFocusUpId(usbTab.getId());
            usbUp.setNextFocusRightId(usbMore.getId());
            usbMore.setNextFocusLeftId(usbUp.getId());
            usbUp.setOnKeyListener(this::usbHeaderKey);
        }

        status=ui.label("",11);
        status.setGravity(Gravity.CENTER_VERTICAL);
        sheet.body.addView(status);
        saveOnly=sheet.action("",this::useUsbSelection);
        saveOnly.setTag("usb-action");
        saveOnly.setMinWidth(ui.px(96));
        saveOnly.setOnKeyListener((view,key,event)->{
            if(key!=KeyEvent.KEYCODE_DPAD_UP||usbGrid==null||usbSelection==null)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN)usbGrid.focus(UsbFileGrid.tag(usbSelection));
            return true;
        });
        updateUsbState();
        restoreFocus(focus);
        listUsb(focus,bar);
    }

    private boolean usbHeaderKey(View view,int key,KeyEvent event){
        if(key!=KeyEvent.KEYCODE_DPAD_DOWN||usbGrid==null)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN)usbGrid.focus("");
        return true;
    }

    private String usbPath(){
        StringBuilder path=new StringBuilder();
        for(String name:usbFolders)path.append(" / ").append(name);
        return path.toString();
    }

    private boolean usbHasParent(){
        return !usbParents.isEmpty()||folder!=null&&(!folder.equals(boundary)||usbVolumePicker);
    }

    private void back(){
        if(committing)return;
        if(wallpaperUsb()&&usbHasParent())usbParent();
        else close();
    }

    private void usbParent(){
        if(!usbHasParent()||committing)return;
        Uri previous=usbDirectory!=null?usbDirectory:Uri.fromFile(folder);
        if(usbDirectory!=null)usbDirectory=usbParents.pop();
        else folder=folder.equals(boundary)?null:folder.getParentFile();
        if(!usbFolders.isEmpty())usbFolders.remove(usbFolders.size()-1);
        usbNavigate("usb-item-"+previous);
    }

    private void usbNavigate(String focus){
        usbSelection=null;
        savedId="";
        notice="";
        usbFocus=focus;
        render();
    }

    private void openUsbDirectory(Uri tree){
        if(Build.VERSION.SDK_INT<21)return;
        usbTree=tree;
        usbDirectory=FileTransferSource.Documents.root(tree);
        usbParents.clear();
        usbFolders.clear();
        String document=android.provider.DocumentsContract.getTreeDocumentId(tree);
        int slash=Math.max(document.lastIndexOf('/'),document.lastIndexOf(':'));
        if(slash>=0&&slash+1<document.length())usbFolders.add(document.substring(slash+1));
        boundary=folder=null;
        usbNavigate("usb-first");
    }

    private void usbOptions(){
        if(committing||receiving.get())return;
        ManagementUi.Sheet options=ui.new Sheet(tr("U 盘选项","USB options"),sheet);
        options.settingsLayout(tr("返回","Back"),options.dialog::dismiss);
        options.settingsActions(false);
        Button refresh=options.option(tr("重新读取","Refresh"),()->{
            options.dialog.dismiss();
            if(FileTransferSource.requestPermission(ui,READ_REQUEST,true))return;
            notice="";
            render();
        });
        if(Build.VERSION.SDK_INT>=21){
            options.option(tr("授权目录","Grant folder access"),()->{options.dialog.dismiss();pickUsbDirectory();});
            if(usbDirectory!=null)options.option(tr("查看 U 盘","Browse USB drives"),()->{
                options.dialog.dismiss();usbTree=usbDirectory=null;usbParents.clear();usbFolders.clear();
                usbNavigate("usb-first");
            });
        }
        options.option(tr("系统选择文件","System file picker"),()->{options.dialog.dismiss();systemPicker();});
        if(Build.VERSION.SDK_INT>=29&&usbDirectory==null)
            options.text(tr("找不到 APK 时，请授权安装包所在目录","If an APK is missing, grant access to its folder."));
        options.show(refresh);
    }

    private void listUsb(String focus,View pathBar){
        ManagementUi.Sheet currentSheet=sheet;
        int request=generation;
        File directory=folder;
        Uri tree=usbTree,document=usbDirectory;
        boolean checkGrant=usbFocus.isEmpty()&&!usbVolumePicker;
        worker.execute(()->{
            List<FileTransferSource.Item> items=new ArrayList<>();
            String error="";
            Uri remembered=null;
            try{
                if(document!=null&&Build.VERSION.SDK_INT>=21)items=FileTransferSource.Documents.children(ui,tree,document);
                else if(directory!=null)items=FileTransferSource.children(directory);
                else{
                    items=FileTransferSource.volumes(ui,true);
                    if(Build.VERSION.SDK_INT>=21&&tree==null&&checkGrant){
                        Uri grant=FileTransferSource.Documents.remembered(ui,true);
                        if(grant!=null&&FileTransferSource.Documents.persistedRead(ui,grant))remembered=grant;
                    }
                }
                Iterator<FileTransferSource.Item> entries=items.iterator();
                while(entries.hasNext()){
                    FileTransferSource.Item item=entries.next();
                    if(!item.directory&&!WallpaperImport.media(item.name)&&!FileTransferPolicy.apk(item.name))entries.remove();
                }
            }catch(IOException|RuntimeException failure){error=problem(failure);}
            List<FileTransferSource.Item> ready=items;
            String failure=error;
            Uri grant=remembered;
            main.post(()->{
                if(sheet!=currentSheet||request!=generation||pathBar.getParent()!=sheet.body)return;
                boolean volumes=directory==null&&document==null;
                if(volumes&&grant!=null){openUsbDirectory(grant);return;}
                if(volumes){
                    usbVolumePicker=ready.size()>1;
                    if(ready.size()==1){
                        boundary=folder=ready.get(0).file;
                        render();
                        return;
                    }
                }
                if(ready.isEmpty()){
                    if(notice.isEmpty())notice=failure.isEmpty()?(volumes
                            ?tr("没有可读取的 U 盘，可重新读取或授权目录","No readable USB drive. Refresh or grant folder access.")
                            :tr("此目录没有支持的文件","No supported files in this folder.")):failure;
                    updateUsbState();
                    if(!usbFocus.isEmpty()){
                        usbFocus="";
                        (usbUp==null?usbMore:usbUp).requestFocusFromTouch();
                    }
                    return;
                }
                UsbFileGrid grid=new UsbFileGrid(ui,previews,ready,(usbUp==null?usbMore:usbUp).getId(),item->{
                    if(!item.directory){selectUsb(item);return;}
                    if(document!=null){usbParents.push(document);usbDirectory=item.uri;usbFolders.add(item.name);}
                    else{
                        if(volumes){boundary=item.file;if(usbVolumePicker)usbFolders.add(item.name);}
                        else usbFolders.add(item.name);
                        folder=item.file;
                    }
                    usbNavigate("usb-first");
                });
                usbGrid=grid;
                sheet.body.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
                updateUsbState();
                String next=usbFocus.isEmpty()?focus:usbFocus;
                usbFocus="";
                if(next.startsWith("usb-item-")||next.equals("usb-first"))grid.focus(next);
            });
        });
    }

    private void selectUsb(FileTransferSource.Item item){
        usbSelection=item;
        savedId="";
        notice="";
        updateUsbState();
        Button action=saveOnly;
        action.post(()->{if(saveOnly==action&&sheet!=null)action.requestFocusFromTouch();});
    }

    private void updateUsbState(){
        boolean selected=usbSelection!=null;
        sheet.settingsActions(selected);
        if(usbGrid!=null)usbGrid.select(selected?UsbFileGrid.tag(usbSelection):null,committing);
        saveOnly.setText(selected&&FileTransferPolicy.apk(usbSelection.name)
                ?tr("安装应用","Install app"):tr("保存壁纸","Save wallpaper"));
        if(usbUp!=null)usbUp.setEnabled(!committing);
        if(usbMore!=null)usbMore.setEnabled(!committing);
        updateState();
    }

    private void useUsbSelection(){
        if(committing||usbSelection==null)return;
        FileTransferSource.Item item=usbSelection;
        if(FileTransferPolicy.apk(item.name)){
            try{
                ui.startActivityForResult(ApkInstallActivity.fromFile(ui,item),USB_INSTALL_REQUEST);
            }catch(ActivityNotFoundException|SecurityException unavailable){
                notice=tr("安装入口不可用","Installer unavailable");updateState();
            }
            return;
        }
        if(!savedId.isEmpty()){finishSave(WallpaperStore.get(ui).snapshot().find(savedId),false);return;}
        committing=true;
        notice=tr("正在保存壁纸…","Saving wallpaper…");
        updateUsbState();
        int request=generation;
        worker.execute(()->{
            WallpaperCatalog.Entry entry=null;
            String error="";
            try(InputStream input=ui.getContentResolver().openInputStream(item.uri)){
                if(input==null)throw new IOException(tr("文件无法读取","Cannot read file"));
                entry=WallpaperImport.accept(ui,input,item.bytes,item.name,WallpaperImport.video(item.name,null),
                        ()->request==generation&&!destroyed,"source=desktop_usb");
            }catch(IOException|RuntimeException failure){error=problem(failure);}
            WallpaperCatalog.Entry ready=entry;
            String failure=error;
            main.post(()->{
                if(sheet==null||request!=generation||destroyed)return;
                committing=false;
                notice=failure;
                if(ready==null)updateUsbState();
                else finishSave(ready,false);
            });
        });
    }

    void activityResult(int request,int result,Intent data){
        if(sheet==null)return;
        if(request==USB_INSTALL_REQUEST&&wallpaperUsb()){
            if(data!=null&&data.getBooleanExtra("installation-verified",false))ui.success(tr("应用已安装","App installed"));
            return;
        }
        if(request==USB_DIRECTORY_REQUEST&&Build.VERSION.SDK_INT>=21&&wallpaperUsb()){
            if(result!=Activity.RESULT_OK||data==null)return;
            Uri tree=data.getData();
            if(!FileTransferSource.Documents.validTree(tree)){
                notice=tr("目录授权无效，请重新选择","Invalid folder access. Choose a folder again.");updateState();return;
            }
            notice="";
            try{FileTransferSource.Documents.remember(ui,true,tree,data.getFlags());}
            catch(IOException failure){notice=problem(failure);}
            openUsbDirectory(tree);return;
        }
        if(request!=FILE_REQUEST)return;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null){if(!wallpaperUsb())browseFiles(false);return;}
        try{
            FileTransferSource.Item item=FileTransferSource.selected(ui,data.getData());
            if(wallpaperUsb()){
                if(!WallpaperImport.media(item.name)&&!FileTransferPolicy.apk(item.name))throw new IOException(tr("不支持此文件格式","Unsupported file format"));
                selectUsb(item);
                notice=item.name;
                updateState();
                saveOnly.requestFocusFromTouch();
            }else choose(item);
        }catch(IOException error){notice=problem(error);updateState();}
    }
    void permissionResult(int request){if(request==READ_REQUEST&&sheet!=null){browsing=true;render();}}
    private void choose(FileTransferSource.Item item){
        if(!receiving.compareAndSet(false,true))return;int request=generation;notice="";updateState();worker.execute(()->{try(InputStream input=ui.getContentResolver().openInputStream(item.uri)){if(input==null)throw new IOException("文件无法读取");WallpaperImport.Prepared ready=WallpaperImport.prepare(ui,input,item.bytes,item.name,type.equals("video"),()->request==generation&&!destroyed,"source=desktop_file");received(ready,request);}catch(IOException|RuntimeException error){main.post(()->{receiving.set(false);if(sheet!=null&&request==generation){notice=problem(error);updateState();}});}});
    }
    private void received(WallpaperImport.Prepared prepared,int request){received(prepared,request,false);}
    private void received(WallpaperImport.Prepared prepared,int request,boolean saved){main.post(()->{if(!saved)receiving.set(false);if(sheet==null||destroyed||request!=generation){prepared.discard();return;}WallpaperImport.Prepared previous=pending;pending=prepared;
        if(saved){if(!receivedIds.contains(prepared.id))receivedIds.add(prepared.id);savedId=prepared.id;type=prepared.video==null?"image":"video";nameDirty=false;}
        captureName();if(!nameDirty){name=prepared.name.replaceFirst("(?i)\\.[a-z0-9]{2,5}$","");if(name.length()>48)name=name.substring(0,Character.isHighSurrogate(name.charAt(47))?47:48);}nameField=null;notice=saved?(wallpaperUpload()?tr("已保存 ","Saved ")+receivedIds.size()+tr(" 项"," items"):tr("素材已保存，上传可继续；选择一项用于桌面或屏保。","Media saved. Continue uploading, or select one to use.")):"";render();if(previous!=null)worker.execute(previous::discard);if(!saved&&saveOnly!=null)saveOnly.requestFocusFromTouch();});}
    private void startPhone(){
        if(sheet==null||committing||receiving.get()||!source.equals("phone"))return;captureName();stopServer();expired=false;address=code="";notice="";host=WallpaperUploadPanel.lanAddress(ui);if(host==null){notice=tr("请先连接局域网，再重新开启","Connect to the local network, then start again");updateState();return;}int request=generation;String selectedHost=host;boolean videos=!wallUpload();updateState();
        worker.execute(()->{WallpaperUploadServer candidate=null;try{
            WallpaperUploadServer.Importer importer=new WallpaperUploadServer.Importer(){public String accept(InputStream in,long length,String name,WallpaperUploadServer.Active active)throws IOException{return receive(in,length,name,false,active,request);}public String acceptVideo(InputStream in,long length,String name,WallpaperUploadServer.Active active,long upload)throws IOException{return receive(in,length,name,true,active,request);}};
            String capabilities=WallpaperVideo.capabilities(videos);
            candidate=new WallpaperUploadServer(selectedHost,8765,asset->ui.getAssets().open("wallpaper-upload/"+asset),importer,WallpaperUploadServer.MAX_UPLOAD,capabilities,videos);try{candidate.listen();}catch(IOException occupied){candidate.stop();candidate=new WallpaperUploadServer(selectedHost,0,asset->ui.getAssets().open("wallpaper-upload/"+asset),importer,WallpaperUploadServer.MAX_UPLOAD,capabilities,videos);candidate.listen();}
            String url=candidate.address();int size=wallpaperUpload()?Math.max(240,ui.px(180)):240;BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,size,size,Collections.singletonMap(EncodeHintType.MARGIN,1));Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;bitmap.setPixels(pixels,0,size,0,0,size,size);WallpaperUploadServer ready=candidate;main.post(()->{if(sheet==null||request!=generation||destroyed){ready.stop();bitmap.recycle();return;}server=ready;address=ready.shortAddress();code=ready.connectionCode();qr=bitmap;if(qrView!=null)qrView.setImageBitmap(qr);updateState();main.postDelayed(check,2000);});
        }catch(IOException|WriterException error){if(candidate!=null)candidate.stop();main.post(()->{if(sheet!=null&&request==generation){notice=problem(error);updateState();}});}});
    }
    private String receive(InputStream input,long length,String filename,boolean video,WallpaperUploadServer.Active active,int request)throws IOException{
        if(video&&wallUpload())throw new IOException(tr("请选择静态壁纸","Choose a static wallpaper"));
        if(committing)throw new IOException("素材正在保存，请稍后重试");if(!receiving.compareAndSet(false,true))throw new IOException("已有素材正在接收");main.post(this::updateState);
        try{WallpaperImport.Prepared prepared=WallpaperImport.prepare(ui,input,length,filename,video,()->active.valid()&&request==generation&&!destroyed,"source=desktop_phone");try{prepared.save(filename,()->active.valid()&&request==generation&&!destroyed);received(prepared,request,true);return receivedJson(prepared);}finally{prepared.discard();}}
        catch(IOException|RuntimeException error){main.post(()->{if(sheet!=null&&request==generation)notice=problem(error);});throw error;}
        finally{receiving.set(false);main.post(()->{if(sheet!=null&&request==generation)render();});}
    }
    private String receivedJson(WallpaperImport.Prepared prepared)throws IOException{try{return new org.json.JSONObject().put("id",prepared.id).put("kind",prepared.video==null?"image":"video").put("width",prepared.width).put("height",prepared.height).put("durationMs",prepared.video==null?0:prepared.video.durationMs).put("saved",true).put("pending",false).toString();}catch(org.json.JSONException error){throw new IOException(error);}}
    private void stopServer(){++generation;main.removeCallbacks(check);if(server!=null){server.stop();server=null;}if(qr!=null){if(qrView!=null)qrView.setImageDrawable(null);qr.recycle();qr=null;}}
    private void save(boolean apply){
        if(committing||receiving.get()||pending==null&&savedId.isEmpty())return;captureName();if(savedId.isEmpty()&&name.trim().isEmpty()){notice=tr("请填写文件名称","Enter a file name");updateState();return;}committing=true;int request=generation;WallpaperImport.Prepared prepared=pending;String title=name,id=savedId;render();
        worker.execute(()->{WallpaperCatalog.Entry entry=null;String error="";try{entry=id.isEmpty()?prepared.save(title,()->request==generation&&!destroyed):WallpaperStore.get(ui).snapshot().find(id);if(entry==null)throw new IOException("已保存素材无法读取");}catch(IOException problem){error=problem(problem);}WallpaperCatalog.Entry ready=entry;String failure=error;main.post(()->{if(sheet==null||request!=generation||destroyed)return;if(ready==null){committing=false;notice=failure;render();return;}committing=false;finishSave(ready,apply);});});
    }
    private void finishSave(WallpaperCatalog.Entry entry,boolean apply){
        if(entry==null){notice=tr("已保存素材无法读取","Saved media unavailable");updateState();return;}
        savedId=entry.id;
        try{
            callback.saved(target,entry.id,apply);
            ui.success(apply?tr("素材已保存并应用","Media saved and applied"):tr("素材已保存","Media saved"));
            sheet.dialog.dismiss();
        }catch(IOException error){
            notice=tr("素材已保存，应用未完成","Media saved; applying failed")+"  "+problem(error);
            render();
        }
    }
    void close(){if(sheet==null||committing)return;captureName();String imported=savedId;if(imported.isEmpty()&&wallpaperUpload()&&!receivedIds.isEmpty())imported=receivedIds.get(receivedIds.size()-1);if(!imported.isEmpty()&&callback!=null)try{callback.saved(target,imported,false);}catch(IOException error){ui.error(problem(error));}sheet.dialog.dismiss();}
    private void release(){stopServer();sheet=null;sourceTabs=null;sourceButtons.clear();usbGrid=null;usbSelection=null;usbFocus="";usbVolumePicker=false;usbFolders.clear();usbUp=usbMore=null;usbTree=usbDirectory=null;usbParents.clear();if(!retaining&&pending!=null)worker.execute(pending::discard);pending=null;nameField=null;typeChoice=null;callback=null;if(origin!=null&&androidx.core.view.ViewCompat.isAttachedToWindow(origin))origin.requestFocusFromTouch();origin=null;}
    void saveState(Bundle state){if(sheet==null)return;captureName();Bundle value=new Bundle();value.putString("source",source);value.putString("type",type);value.putString("name",name);value.putBoolean("name-dirty",nameDirty);value.putString("target",target);value.putString("notice",notice);value.putBoolean("expired",expired);value.putString("saved-id",savedId);value.putStringArrayList("received-ids",new ArrayList<>(receivedIds));value.putBoolean("browsing",browsing);if(boundary!=null)value.putString("boundary",boundary.getPath());if(folder!=null)value.putString("folder",folder.getPath());if(usbTree!=null){value.putParcelable("usb-tree",usbTree);value.putParcelable("usb-directory",usbDirectory);value.putParcelableArrayList("usb-parents",new ArrayList<>(usbParents));}if(wallpaperUsb()){
        value.putStringArrayList("usb-folders",new ArrayList<>(usbFolders));value.putBoolean("usb-volume-picker",usbVolumePicker);
        if(usbSelection!=null){
            value.putParcelable("usb-selected-uri",usbSelection.uri);
            value.putString("usb-selected-name",usbSelection.name);
            value.putLong("usb-selected-size",usbSelection.bytes);
        }
    }if(pending!=null)pending.saveState(value);View focused=sheet.dialog.getCurrentFocus();if(focused!=null&&focused.getTag() instanceof String)value.putString("focus",(String)focused.getTag());value.putFloat("scroll",sheet.scroll.getScrollY()/ui.unit);state.putBundle("desktop-media-import",value);retaining=true;}
    void restore(Bundle state,Target callback){Bundle value=state==null?null:state.getBundle("desktop-media-import");if(value==null)return;this.callback=callback;source=value.getString("source","phone");type=value.getString("type","image");name=value.getString("name","");nameDirty=value.getBoolean("name-dirty");target=value.getString("target","screensaver");notice=value.getString("notice","");expired=value.getBoolean("expired");savedId=value.getString("saved-id","");receivedIds.clear();ArrayList<String> restored=value.getStringArrayList("received-ids");if(restored!=null)for(String id:restored)if(WallpaperStore.get(ui).snapshot().find(id)!=null)receivedIds.add(id);restoreFolder(value);if(value.containsKey("draft-id"))try{pending=WallpaperImport.Prepared.restore(ui,value);WallpaperCatalog.Entry saved=WallpaperStore.get(ui).snapshot().find(pending.id);if(saved!=null)savedId=saved.id;}catch(IOException error){notice=problem(error);}retaining=false;String focus=value.getString("focus","");if(wallpaperUsb()&&focus.startsWith("usb-item-"))usbFocus=focus;
        open();if(source.equals("phone")&&!expired)startPhone();if(browsing&&(focus.startsWith("import-entry-")||focus.equals("import-parent"))){fileFocus=focus;focusFiles=true;}sheet.body.post(()->{if(sheet==null)return;View view=sheet.box.findViewWithTag(focus);if(view!=null&&view.isEnabled())view.requestFocusFromTouch();sheet.scroll.scrollTo(0,Math.round(value.getFloat("scroll")*ui.unit));});}
    private void restoreFolder(Bundle state){
        browsing=wallpaperUsb()||state.getBoolean("browsing");
        usbTree=usbDirectory=null;usbParents.clear();
        if(wallpaperUsb()){
            usbFolders.clear();
            ArrayList<String> labels=state.getStringArrayList("usb-folders");
            if(labels!=null)usbFolders.addAll(labels);
            usbVolumePicker=state.getBoolean("usb-volume-picker");
            Uri selected=state.getParcelable("usb-selected-uri");
            if(selected!=null)usbSelection=new FileTransferSource.Item(state.getString("usb-selected-name"),state.getLong("usb-selected-size"),false,selected,null);
        }
        if(Build.VERSION.SDK_INT>=21&&wallpaperUsb()){
            Uri tree=state.getParcelable("usb-tree");
            if(FileTransferSource.Documents.validTree(tree)){
                usbTree=tree;usbDirectory=state.getParcelable("usb-directory");
                ArrayList<Uri> parents=state.getParcelableArrayList("usb-parents");if(parents!=null)usbParents.addAll(parents);
            }
        }
        String base=state.getString("boundary",""),path=state.getString("folder","");boundary=folder=null;if(base.isEmpty()||path.isEmpty())return;
        try{File root=new File(base).getCanonicalFile(),current=new File(path).getCanonicalFile();for(FileTransferSource.Item volume:FileTransferSource.volumes(ui,source.equals("usb")))if(root.equals(volume.file.getCanonicalFile())&&(current.equals(root)||current.getPath().startsWith(root.getPath()+File.separator))){boundary=root;folder=current;return;}}catch(IOException|SecurityException unavailable){notice=tr("原目录无法读取，请重新选择文件","Previous folder unavailable. Choose a file again.");}
    }
    void destroy(){destroyed=true;stopServer();if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}if(!retaining&&pending!=null)worker.execute(pending::discard);pending=null;previews.shutdownNow();worker.shutdown();}
    void resume(){retaining=false;if(sheet!=null&&source.equals("phone")&&!expired&&server==null)startPhone();else if(sheet!=null&&browsing&&!committing)render();}
    void pause(){captureName();if(!committing){stopServer();address=code="";}}
}
