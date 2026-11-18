package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One visible file tool; receiving, browsing and previewing never publish a wallpaper. */
final class FileToolsSheet {
    static final int PICK=7441,TREE=7442,PERMISSION=7443,INSTALL=7444;
    private final ManagementUi ui;
    private final Runnable closed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ManagementUi.Sheet sheet,confirmation;
    private FileTransferStore store;
    private final PhoneSettingsSession phone;
    private volatile int jobVersion;
    private int listVersion,source,treeSource;
    private boolean active,destroyed,busy,loading,temporaryDirectory,retaining,external,applying,installerOpened;
    private String deleteReturnFocus="file-source-0",deleteError="";
    private boolean receivedChanged,reopenDeleteAfterLoad;
    private String phase="list",error="",notice="",focus="file-source-0",receivingName="";
    private File folder,boundary,installSource;
    private Uri tree,current;
    private final ArrayDeque<Uri> parents=new ArrayDeque<>();
    private FileTransferStore.Entry receipt;
    private FileTransferSource.Item selected;
    private WallpaperImport.Prepared prepared;
    private WallpaperCatalog.Entry mediaEntry;
    private VideoWallpaperPlayer video;
    private FrameLayout previewHost;
    private Bitmap photo;
    private Button closeButton;
    private TextView progressText;
    private ProgressBar progress;
    private View origin;
    private long lastProgress,receivedBytes,expectedBytes;
    private List<?> entries=Collections.emptyList();
    FileToolsSheet(ManagementUi ui,Runnable closed){this.ui=ui;this.closed=closed;phone=PhoneSettingsSession.get(ui);}
    private int p(float n){return ui.px(n*.75f);}
    private boolean open(){return !destroyed&&sheet!=null;}
    private String name(){return receipt!=null?receipt.name:selected!=null?selected.name:"";}
    private long bytes(){return receipt!=null?receipt.bytes:selected!=null?selected.bytes:0;}
    private String kind(){return FileTransferPolicy.apk(name())?"应用安装包":FileTransferPolicy.image(name())?"图片":WallpaperImport.video(name(),null)?"视频":"文件";}
    private TextView copy(String text,float size,float line,int color){TextView value=ui.label(text,12);QuickControlPreviewView.type(value,size,line,ui.unit,color);return value;}
    private void note(String text){TextView value=copy(text,17.28f,29.376f,UiPalette.SECONDARY);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(17.28f);lp.bottomMargin=p(17.28f);if(sheet.body.getChildCount()>0){Object tag=sheet.body.getChildAt(sheet.body.getChildCount()-1).getTag();if("file-body-heading".equals(tag))lp.topMargin=p(7.04f);else if("file-body-note".equals(tag))lp.topMargin=0;}value.setTag("file-body-note");sheet.body.addView(value,lp);}
    private void muted(String text){TextView value=copy(text,14.464f,23.8656f,UiPalette.SECONDARY);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.px(7.68f);lp.bottomMargin=ui.px(14.4f);sheet.body.addView(value,lp);}
    private void heading(String text){TextView value=copy(text,17.28f,25.92f,UiPalette.SECONDARY);QuickControlPreviewView.weight(value,500);value.setTag("file-body-heading");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=ui.px(7.68f);sheet.body.addView(value,lp);}
    private Button button(String text,String tag,Runnable action,boolean primary){Button value=ui.button(text,()->{if(!busy||tag.equals("file-cancel-receive"))action.run();});value.setEnabled(!busy||tag.equals("file-cancel-receive"));value.setTag(tag);value.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});value.setId(androidx.core.view.ViewCompat.generateViewId());value.setFocusableInTouchMode(true);value.setAllCaps(false);value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.712f*ui.unit);value.setPadding(ui.px(12.96f),ui.px(7.2f),ui.px(12.96f),ui.px(7.2f));value.setMinHeight(0);value.setMinimumHeight(0);value.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,21.12f,primary?(ui instanceof AppManagementActivity?UiPalette.SELECTED:0xe6ffffff):UiPalette.CONTROL,false,primary?0x4dffffff:UiPalette.EDGE,false,12));value.setTextColor(SettingsFocusDrawable.textColors(primary&&!(ui instanceof AppManagementActivity)?UiPalette.INK:UiPalette.TEXT));SettingsTouchClick.attach(value);return value;}
    private Button compact(String text,String tag,Runnable action,boolean primary){Button value=button(text,tag,action,primary);sheet.body.addView(value,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));return value;}
    private Button menu(String text,String tag,Runnable action,boolean primary){Button value=button(text,tag,action,primary);value.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.4f*ui.unit);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ui.px(39.36f));lp.bottomMargin=ui.px(7.2f);sheet.body.addView(value,lp);return value;}
    private void identify(View value,String tag){value.setTag(tag);value.setOnFocusChangeListener((v,on)->{if(on)focus=tag;});value.setId(androidx.core.view.ViewCompat.generateViewId());value.setFocusableInTouchMode(true);}
    void show(){if(open())return;entries=Collections.emptyList();installerOpened=false;phase="list";source=0;folder=boundary=null;tree=current=null;parents.clear();receipt=null;selected=null;error=notice="";receivedChanged=false;focus="file-source-0";create();phone.start();}
    private void create(){
        origin=ui.getCurrentFocus();sheet=ui.new Sheet("文件传输助手");ManagementUi.Sheet own=sheet;closeButton=sheet.settingsLayout("返回",this::back);closeButton.setOnFocusChangeListener((v,on)->{if(on)focus="sheet-close";});sheet.settingsActions(false);
        LinearLayout title=(LinearLayout)closeButton.getParent();title.removeView(closeButton);title.addView(closeButton,0,new LinearLayout.LayoutParams(ui.px(36),ui.px(36)));closeButton.setText("");closeButton.setContentDescription("返回");Drawable back=UiCompat.drawable(ui,R.drawable.ic_manage_back);back.setBounds(0,0,ui.px(20),ui.px(20));closeButton.setCompoundDrawables(back,null,null,null);closeButton.setGravity(Gravity.CENTER);closeButton.setPadding(ui.px(8),0,ui.px(8),0);
        sheet.heading.getLayoutParams().height=-1;sheet.heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,22*ui.unit);((LinearLayout.LayoutParams)sheet.heading.getLayoutParams()).leftMargin=ui.px(16);title.getLayoutParams().height=ui.px(36);
        sheet.dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(event.getAction()==KeyEvent.ACTION_UP)back();return true;});sheet.dialog.setOnDismissListener(d->{if(sheet==own){dismissConfirmation();sheet=null;++jobVersion;++listVersion;busy=false;stopPreview();if(!retaining)discard();if(origin!=null&&origin.isShown())origin.requestFocusFromTouch();closed.run();}});
        String target=focus;render();sheet.show(null);focus=target;restoreFocus();if(store==null)worker.execute(()->{FileTransferStore ready=null;try{ready=new FileTransferStore(new File(ui.getFilesDir(),"received-files"));}catch(IOException ignored){}FileTransferStore result=ready;main.post(()->{if(!open())return;store=result;if(result==null){error="文件接收目录不可用，已有文件已保留";render();}else readList();});});else if(phase.equals("list"))readList();
    }
    private void remember(){if(sheet==null||confirmation!=null)return;View view=sheet.dialog.getCurrentFocus();if(view!=null&&view.getTag() instanceof String)focus=(String)view.getTag();}
    private String detailFocus(){return FileTransferPolicy.apk(name())?"file-install":WallpaperImport.media(name())?"file-preview":receipt==null?"file-save":"file-download";}
    private String defaultFocus(){
        switch(phase){
            case "list":return "file-source-"+source;
            case "directory":return "file-system-pick";
            case "receiving":return "file-cancel-receive";
            case "expired":return "file-restart";
            case "install":return "file-return-detail";
            case "preview":return mediaEntry==null?"file-back":"file-preview";
            default:return detailFocus();
        }
    }
    private View focusTarget(String tag){View value=sheet.box.findViewWithTag(tag);return value!=null&&value.isShown()&&value.isEnabled()&&value.isFocusable()?value:null;}
    private void restoreFocus(){
        if(sheet==null)return;ManagementUi.Sheet own=sheet;String target=focus;
        own.body.post(()->{if(!open()||sheet!=own||confirmation!=null)return;View value=focusTarget(target);
            if(value==null&&phase.equals("list")&&loading)return;
            if(value==null)value=focusTarget(defaultFocus());
            if(value==null||!value.requestFocusFromTouch()){value=focusTarget("sheet-close");if(value==null||!value.requestFocusFromTouch())return;}
            Rect bounds=new Rect();value.getDrawingRect(bounds);value.requestRectangleOnScreen(bounds,false);
        });
    }
    private void confirmDelete(String target){
        if(confirmation!=null||receipt==null||busy)return;
        deleteReturnFocus=focus=target;
        deleteError="";
        showDeleteConfirmation();
    }

    private void showDeleteConfirmation(){
        if(!open()||receipt==null||confirmation!=null)return;
        View target=focusTarget(deleteReturnFocus);
        if(target!=null)target.requestFocusFromTouch();
        ManagementUi.Sheet modal=ui.new Sheet("删除文件？",sheet);
        confirmation=modal;
        Button close=modal.settingsLayout("取消",()->{if(!busy)modal.dialog.dismiss();});
        close.setVisibility(View.GONE);
        modal.contentSize(400,340);
        modal.settingsActions(true);

        LinearLayout info=ui.row();
        info.setBackgroundDrawable(ui.surface(0x30201c18,0x30201c18,UiPalette.EDGE,9));
        info.setPadding(ui.px(12),ui.px(8),ui.px(12),ui.px(8));
        TextView file=copy(receipt.name,16,24,UiPalette.TEXT);
        file.setMaxLines(2);
        file.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(file,new LinearLayout.LayoutParams(0,-2,1));
        TextView size=copy(FileTransferSource.size(receipt.bytes),13.44f,20,UiPalette.SECONDARY);
        LinearLayout.LayoutParams sizeLayout=new LinearLayout.LayoutParams(-2,-2);
        sizeLayout.leftMargin=ui.px(10);
        info.addView(size,sizeLayout);
        LinearLayout.LayoutParams infoLayout=new LinearLayout.LayoutParams(-1,-2);
        infoLayout.topMargin=ui.px(6);
        modal.body.addView(info,infoLayout);
        modal.text("仅移除这份接收文件。");

        TextView problem=copy(deleteError,13.44f,20,UiPalette.STATUS_ERROR);
        problem.setVisibility(deleteError.isEmpty()?View.GONE:View.VISIBLE);
        modal.body.addView(problem);
        Button cancel=modal.action("取消",()->{if(!busy)modal.dialog.dismiss();});
        cancel.setTag("file-delete-cancel");
        Button remove=modal.action("删除文件",()->delete(modal,problem));
        remove.setTag("file-delete-confirm");
        remove.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,0x46a44642,false,0x88f29e96));
        remove.setTextColor(SettingsFocusDrawable.textColors(0xffffe8e5,UiPalette.INK));
        configureConfirmationFocus(cancel,remove);
        modal.dialog.setOnKeyListener((dialog,key,event)->{
            if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;
            if(event.getAction()==KeyEvent.ACTION_UP&&!busy)modal.dialog.dismiss();
            return true;
        });
        modal.dialog.setOnDismissListener(dialog->{
            if(confirmation!=modal)return;
            confirmation=null;
            focus=deleteReturnFocus;
            if(receivedChanged&&phase.equals("list"))readList();else restoreFocus();
        });
        modal.show(cancel);
        cancel.requestFocusFromTouch();
    }

    private void configureConfirmationFocus(Button cancel,Button remove){
        cancel.setId(androidx.core.view.ViewCompat.generateViewId());
        remove.setId(androidx.core.view.ViewCompat.generateViewId());
        cancel.setFocusableInTouchMode(true);
        remove.setFocusableInTouchMode(true);
        cancel.setNextFocusLeftId(cancel.getId());
        cancel.setNextFocusRightId(remove.getId());
        remove.setNextFocusLeftId(cancel.getId());
        remove.setNextFocusRightId(remove.getId());
        for(Button value:new Button[]{cancel,remove}){
            value.setNextFocusUpId(value.getId());
            value.setNextFocusDownId(value.getId());
        }
    }

    private void dismissConfirmation(){
        if(confirmation==null)return;
        ManagementUi.Sheet modal=confirmation;
        confirmation=null;
        modal.dialog.setOnDismissListener(null);
        modal.dialog.dismiss();
    }

    private void fileName(String text){note(text);TextView name=(TextView)sheet.body.getChildAt(sheet.body.getChildCount()-1);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);}
    // The list owns its scrolling; an outer ScrollView would measure it without a height limit.
    private void listViewport(boolean listing){
        if(listing&&sheet.body.getParent()==sheet.scroll){
            sheet.scroll.removeView(sheet.body);sheet.body.setPadding(0,0,0,ui.px(18.75f));sheet.box.addView(sheet.body,sheet.box.indexOfChild(sheet.scroll),new LinearLayout.LayoutParams(-1,0,1));sheet.scroll.setVisibility(View.GONE);
        }else if(!listing&&sheet.body.getParent()==sheet.box){
            sheet.box.removeView(sheet.body);sheet.body.setPadding(0,0,0,0);sheet.scroll.addView(sheet.body,new ScrollView.LayoutParams(-1,-2));sheet.scroll.setVisibility(View.VISIBLE);
        }
    }
    private void render(){
        if(!open())return;if(!phase.equals("preview"))stopPreview();closeButton.setEnabled(!applying);sheet.body.removeAllViews();sheet.actions.removeAllViews();sheet.settingsActions(false);listViewport(phase.equals("list"));progressText=null;progress=null;
        if(phase.equals("list"))list();else if(phase.equals("directory"))directory();else if(phase.equals("receiving"))receiving();
        else if(phase.equals("expired")){note("未完成读取的文件未保存。");compact("返回文件列表","file-restart",()->{go("list","file-source-"+source);readList();},true);}
        else if(phase.equals("install")){note(installerOpened?"请在系统安装器中确认，返回不表示安装成功。":"由电视系统确认安装包与安装来源授权。");LinearLayout actions=ui.row();actions.setGravity(Gravity.END);Button back=button("返回文件","file-return-detail",()->go("detail","file-install"),false);actions.addView(back,new LinearLayout.LayoutParams(-2,-1));if(!installerOpened){Button install=button("使用系统安装器","file-installer",this::install,true);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-2,-1);ip.leftMargin=ui.px(7.68f);actions.addView(install,ip);}LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,ui.px(34.56f));ap.topMargin=ui.px(5.04f);sheet.body.addView(actions,ap);}
        else detail();
        boolean compact=Arrays.asList("install","receiving","expired").contains(phase);
        sheet.heading.setText(compact?(phase.equals("install")?installerOpened?"安装结果待核对":"允许安装应用":phase.equals("receiving")?"正在读取文件":"读取已中断"):"文件传输助手");
        sheet.heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(compact?19.2f:22)*ui.unit);
        if(compact)sheet.fitContent(400,460);else{sheet.fixedSize(800,440);sheet.scroll.setFillViewport(true);}
        if(!error.isEmpty()){TextView value=copy(error,15.36f,23.04f,UiPalette.TEXT);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=p(12.8f);sheet.body.addView(value,lp);restoreFocus();sheet.body.post(()->{if(open()&&value.getParent()!=null){Rect rect=new Rect();value.getDrawingRect(rect);value.requestRectangleOnScreen(rect,false);}});}else restoreFocus();
    }
    private void go(String next,String target){OperationFeedback.get(ui).hide();error="";phase=next;focus=target;render();}
    private void list(){
        LinearLayout content=ui.column();
        content.setTag("file-panel");
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,0,1);
        sheet.body.addView(content,layout);

        View rule=new View(ui);rule.setBackgroundColor(UiPalette.EDGE);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,Math.max(1,ui.px(.5f)));
        rp.bottomMargin=ui.px(7);content.addView(rule,rp);
        LinearLayout tabs=ui.row();
        String[] names={"已接收","本地存储","U 盘"};
        for(int i=0;i<names.length;i++){
            final int at=i;
            Button tab=button(names[i],"file-source-"+i,()->source(at),false);
            tab.setOnFocusChangeListener((view,on)->{if(on){focus="file-source-"+at;if(source!=at)source(at);}});
            tab.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14.4f*ui.unit);
            tab.setSelected(source==i);
            tab.setTextColor(SettingsFocusDrawable.textColors(source==i?UiPalette.INK:UiPalette.TEXT));
            tab.setBackgroundDrawable(source==i?ui.surface(Color.WHITE,Color.WHITE,Color.TRANSPARENT,999):
                    new SettingsFocusDrawable(ui.unit,999,Color.TRANSPARENT,false,0));
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,ui.px(30));
            tp.rightMargin=ui.px(8);tabs.addView(tab,tp);
        }
        View space=new View(ui);tabs.addView(space,new LinearLayout.LayoutParams(0,1,1));
        tabs.addView(copy(loading?"读取中":entries.size()+(source==0?" 个文件":" 项"),13.44f,20,UiPalette.SECONDARY));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,ui.px(31));
        tp.bottomMargin=ui.px(5);content.addView(tabs,tp);
        if(source>0){LinearLayout location=ui.row();location.setGravity(Gravity.CENTER_VERTICAL);TextView currentPath=copy(tree!=null?current.toString():folder!=null?folder.getAbsolutePath():"选择电视存储目录",13.44f,20,UiPalette.SECONDARY);currentPath.setSingleLine();currentPath.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);location.addView(currentPath,new LinearLayout.LayoutParams(0,-2,1));Button up=button("上一级","file-up",this::up,false);up.setEnabled(folder!=null||tree!=null);location.addView(up,new LinearLayout.LayoutParams(-2,ui.px(32)));Button options=button("选择目录","file-options",()->go("directory","file-system-pick"),false);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-2,ui.px(32));op.leftMargin=ui.px(8);location.addView(options,op);content.addView(location,new LinearLayout.LayoutParams(-1,ui.px(40)));}
        ScrollView scroll=new ScrollView(ui){@Override protected void dispatchDraw(Canvas canvas){int save=canvas.save();canvas.clipRect(getScrollX(),getScrollY(),getScrollX()+getWidth(),getScrollY()+getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(save);}};scroll.setTag("file-list-scroll");scroll.setVerticalScrollBarEnabled(false);LinearLayout rows=ui.column();rows.setTag("file-rows");scroll.addView(rows);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));if(!loading)for(Object value:entries)addFile(rows,value);
        if(loading||entries.isEmpty()){TextView empty=copy(loading?"正在读取文件…":notice.isEmpty()?"暂无文件":notice,15,24,UiPalette.SECONDARY);empty.setPadding(0,ui.px(24),0,ui.px(24));rows.addView(empty);}
        TextView hint=copy("上下选择文件  左右选择操作  确定执行",12.8f,19.2f,UiPalette.SECONDARY);hint.setPadding(0,ui.px(8),0,0);content.addView(hint);
    }
    private void readList(){
        if(!open()||store==null||!phase.equals("list")||confirmation!=null)return;receivedChanged=false;int version=++listVersion;loading=true;render();int at=source;File path=folder;Uri root=tree,location=current;
        worker.execute(()->{List<?> entries;String problem="";try{entries=at==0?store.list():root!=null?FileTransferSource.Documents.children(ui,root,location):path!=null?FileTransferSource.children(path):FileTransferSource.volumes(ui,at==2);}catch(IOException|RuntimeException failure){entries=Collections.emptyList();problem="目录无法读取，请检查连接或重新授权";}List<?> values=entries;String message=problem;main.post(()->{if(!open()||version!=listVersion||!phase.equals("list"))return;loading=false;this.entries=values;if(!message.isEmpty())error=message;notice=values.isEmpty()?(source==1&&tree==null&&FileTransferSource.partialMediaAccess(ui)?"当前没有已授权的照片和视频，可在读取选项中重新选择":source==0?"还没有接收文件":source==2?"未发现可读取的 U 盘，请检查连接或授权目录":"没有可读取的文件，可通过读取选项选择文件或授权目录"):"";render();if(reopenDeleteAfterLoad){reopenDeleteAfterLoad=false;showDeleteConfirmation();}});});
    }
    private void addFile(LinearLayout parent,Object item){
        FileTransferStore.Entry entry=item instanceof FileTransferStore.Entry?(FileTransferStore.Entry)item:null;
        FileTransferSource.Item local=entry==null?(FileTransferSource.Item)item:null;
        String title=entry!=null?entry.name:local.name;
        boolean directory=local!=null&&local.directory;
        boolean apk=FileTransferPolicy.apk(title),media=WallpaperImport.media(title);
        long size=entry!=null?entry.bytes:local.bytes;
        String tag=entry!=null?"file-"+entry.id:"file-source-item-"+local.uri;
        String kind=directory?"文件夹":apk?"应用安装包":FileTransferPolicy.image(title)?"图片":media?"视频":"文件";
        int icon=directory?R.drawable.ic_transfer_folder:apk?R.drawable.ic_transfer_apk:
                FileTransferPolicy.image(title)?R.drawable.ic_transfer_image:media?R.drawable.ic_transfer_video:R.drawable.ic_transfer_file;

        int rowCount=parent.getChildCount();
        FileRow previousRow=rowCount==0?null:(FileRow)parent.getChildAt(rowCount-1);
        FileRow line=new FileRow();
        parent.addView(line,new LinearLayout.LayoutParams(-1,ui.px(58)));

        LinearLayout file=ui.row();
        file.setSoundEffectsEnabled(false);
        file.setPadding(ui.px(10),0,ui.px(10),0);
        identify(file,tag);
        file.setContentDescription(title+"  "+kind+(directory?"":"  "+FileTransferSource.size(size)));
        ImageView image=new ImageView(ui);
        image.setImageDrawable(UiCompat.drawable(ui,icon));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ui.px(26),ui.px(26));
        ip.rightMargin=ui.px(12);
        file.addView(image,ip);

        LinearLayout labels=ui.column();labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=copy(title,16,21,UiPalette.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name);
        labels.addView(copy(kind+(directory?"":"  "+FileTransferSource.size(size)),11.52f,16,UiPalette.SECONDARY));
        file.addView(labels,new LinearLayout.LayoutParams(0,-1,1));
        androidx.core.view.ViewCompat.setImportantForAccessibility(labels,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        line.addView(file,new LinearLayout.LayoutParams(0,-1,1));
        file.setEnabled(!busy);
        line.file=file;
        line.controls[0]=file;

        Runnable select=()->{receipt=entry;selected=local;installerOpened=false;};
        file.setOnClickListener(v->{
            if(busy||store==null)return;
            select.run();
            if(directory){
                if(tree!=null){parents.push(current);current=local.uri;}
                else{if(folder==null)boundary=local.file;folder=local.file;}
                focus="file-up";readList();
            }else if(apk)install();
            else if(media)preparePreview();
            else sendDownload();
            KeyFeedback.confirm(ui);
        });
        SettingsTouchClick.attach(file);
        if(!directory){
            if(apk)fileAction(line,1,"安装","file-install-"+tag,()->{select.run();install();});
            else if(media)fileAction(line,1,"预览","file-preview-"+tag,()->{select.run();preparePreview();});
            else line.addView(new View(ui),fileActionSize());
            fileAction(line,2,"发送","file-send-"+tag,()->{select.run();sendDownload();});
            if(entry!=null)fileAction(line,3,"删除","file-delete-"+entry.id,()->{select.run();confirmDelete("file-delete-"+entry.id);});
            else line.addView(new View(ui),fileActionSize());
        }
        View above=previousRow==null?focusTarget(source==0?"file-source-0":"file-options"):previousRow.file;
        View previous=null;
        for(View action:line.controls){
            if(action==null)continue;
            action.setNextFocusDownId(action.getId());
            if(above!=null)action.setNextFocusUpId(above.getId());
            if(previous!=null){
                previous.setNextFocusRightId(action.getId());
                action.setNextFocusLeftId(previous.getId());
            }
            previous=action;
        }
        if(previous!=null)previous.setNextFocusRightId(previous.getId());
        file.setNextFocusLeftId(file.getId());
        if(previousRow!=null)for(View action:previousRow.controls){
            if(action!=null)action.setNextFocusDownId(file.getId());
        }
    }

    private LinearLayout.LayoutParams fileActionSize(){
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(ui.px(66),ui.px(28));
        size.leftMargin=ui.px(8);
        return size;
    }

    private void fileAction(FileRow row,int column,String title,String tag,Runnable action){
        Button button=button(title,tag,action,false);
        button.setContentDescription(title+"  "+row.file.getContentDescription());
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13.44f*ui.unit);
        button.setPadding(0,0,0,0);
        button.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,999,0x15ffffff,false,0x3dffffff));
        row.addView(button,fileActionSize());
        row.controls[column]=button;
    }

    private final class FileRow extends LinearLayout {
        final View[] controls=new View[4];
        final SurfaceDrawable glass=new SurfaceDrawable(ui.unit);
        final Paint rule=new Paint();
        View file;

        FileRow(){
            super(ui);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(0,0,ui.px(15),ui.px(6));
            setClipChildren(false);
            setClipToPadding(false);
            glass.setCornerRadius(ui.px(12));
            rule.setColor(UiPalette.EDGE);
        }

        @Override public void requestChildFocus(View child,View focused){
            super.requestChildFocus(child,focused);
            invalidate();
        }

        @Override public void clearChildFocus(View child){
            super.clearChildFocus(child);
            invalidate();
        }

        @Override public void onWindowFocusChanged(boolean focused){
            super.onWindowFocusChanged(focused);
            invalidate();
        }

        @Override protected void dispatchDraw(Canvas canvas){
            View current=getFocusedChild();
            if(current!=null){
                boolean fileFocused=current==file&&hasWindowFocus();
                glass.setColor(fileFocused?0x225a5b54:0x1953544e);
                glass.setStroke(Math.max(1,ui.px(fileFocused?1.1f:.75f)),fileFocused?0xedfffdf4:0x72fffdf4);
                glass.setBounds(0,0,getWidth(),getHeight()-getPaddingBottom());
                glass.draw(canvas);
            }else{
                int y=getHeight()-ui.px(3);
                canvas.drawRect(0,y,getWidth(),y+Math.max(1,ui.px(.5f)),rule);
            }
            super.dispatchDraw(canvas);
        }
    }

    private void source(int at){if(busy)return;source=at;folder=boundary=null;tree=current=null;parents.clear();error="";temporaryDirectory=false;focus="file-source-"+at;if(at>0){if(Build.VERSION.SDK_INT>=21){tree=FileTransferSource.Documents.remembered(ui,at==2);if(tree!=null){if(FileTransferSource.Documents.persistedRead(ui,tree))current=FileTransferSource.Documents.root(tree);else{tree=null;error="目录授权已失效，请重新授权";}}}else try{folder=boundary=FileTransferSource.LocalDirectories.remembered(ui,at==2);}catch(IOException ignored){error="已记住的目录不可用，请重新选择";}}readList();}
    private void up(){focus="file-up";if(tree!=null){if(parents.isEmpty()){tree=current=null;temporaryDirectory=false;focus="file-source-"+source;readList();}else{current=parents.pop();readList();}}else if(folder!=null){if(folder.equals(boundary)){folder=boundary=null;focus="file-source-"+source;}else folder=folder.getParentFile();readList();}}
    private DesktopSettingsRow directoryRow(LinearLayout group,String title,String value,String tag,Runnable action,boolean enabled){
        DesktopSettingsRow row=new DesktopSettingsRow(ui,title,"",false);row.hideIcon();row.value(value);row.tail("arrow");row.textSizes(14.016f,11.52f);identify(row,tag);row.setEnabled(enabled);row.setAlpha(1f);row.setOnClickListener(v->{if(!busy)action.run();});SettingsTouchClick.attach(row);group.addView(row,new LinearLayout.LayoutParams(-1,ui.px(44.535f)));return row;
    }
    private void directory(){
        heading("读取本地文件");LinearLayout group=ui.listGroup();LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=p(10.24f);gp.bottomMargin=p(21.12f);sheet.body.addView(group,gp);
        directoryRow(group,"系统选择文件","单个文件","file-system-pick",this::pick,true);
        if(Build.VERSION.SDK_INT>=21)directoryRow(group,"授权目录",tree!=null?"已授权目录":"选择可读取目录","file-tree-pick",this::pickTree,true);
        directoryRow(group,"记住当前目录",folder!=null?folder.getName():tree!=null?"已授权目录":"请先授权目录","file-remember-directory",this::rememberDirectory,tree!=null||folder!=null);
        directoryRow(group,"重新选择目录","保留已接收文件","file-reset-directory",()->{folder=boundary=null;tree=current=null;parents.clear();go("list","file-options");readList();},true);
        directoryRow(group,FileTransferSource.partialMediaAccess(ui)?"重新选择照片和视频":"重新读取存储","刷新","file-reread",()->{if(!FileTransferSource.requestPermission(ui,PERMISSION,true)){go("list","file-options");readList();}},true);
        Button back=button("返回","file-directory-back",()->{go("list","file-options");readList();},false);sheet.body.addView(back,new LinearLayout.LayoutParams(-2,ui.px(34.56f)));
    }
    private void rememberDirectory(){try{if(tree!=null){if(!FileTransferSource.Documents.persistedRead(ui,tree))throw new IOException("系统只提供临时目录授权，请重新授权");}else if(folder!=null)FileTransferSource.LocalDirectories.remember(ui,source==2,folder);go("list","file-options");readList();ui.success("已记住当前目录");}catch(IOException failure){error=failure.getMessage();render();}}
    private void pick(){try{Intent intent=new Intent(Build.VERSION.SDK_INT>=19?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);external=true;sheet.dialog.hide();ui.startActivityForResult(intent,PICK);}catch(ActivityNotFoundException|SecurityException failure){external=false;sheet.dialog.show();error="系统文件选择器不可用，可浏览本地存储或使用局域网传输";render();}}
    private void pickTree(){if(Build.VERSION.SDK_INT<21)return;try{treeSource=source==2?2:1;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);external=true;sheet.dialog.hide();ui.startActivityForResult(intent,TREE);}catch(ActivityNotFoundException|SecurityException failure){external=false;sheet.dialog.show();error="目录授权入口不可用，可选择单个文件或使用局域网传输";render();}}
    private void detail(){
        heading(name());muted(kind()+"  "+FileTransferSource.size(bytes()));((LinearLayout.LayoutParams)sheet.body.getChildAt(sheet.body.getChildCount()-1).getLayoutParams()).topMargin=0;if(busy){note("正在处理文件…");return;}if(phase.equals("preview"))previewBody();
        if(FileTransferPolicy.apk(name()))menu("安装应用","file-install",()->{installerOpened=false;go("install","file-installer");},false);
        if(WallpaperImport.media(name()))menu(phase.equals("preview")?"设为壁纸":"预览并设为壁纸","file-preview",()->{if(phase.equals("preview"))applyWallpaper();else preparePreview();},true).setEnabled(!phase.equals("preview")||mediaEntry!=null);
        menu("传到手机或电脑","file-download",this::sendDownload,false);
        if(receipt!=null)menu("删除文件","file-delete",()->confirmDelete("file-delete"),false);else menu("保存到已接收","file-save",()->copyToReceived("list"),false);
        menu("返回","file-back",()->{discard();go("list",receipt!=null?"file-"+receipt.id:"file-source-"+source);readList();},false);((LinearLayout.LayoutParams)sheet.body.getChildAt(sheet.body.getChildCount()-1).getLayoutParams()).bottomMargin=0;
    }
    private void delete(ManagementUi.Sheet modal,TextView problem){
        if(confirmation!=modal||receipt==null||busy)return;
        FileTransferStore.Entry entry=receipt;
        String nextFocus="file-source-"+source;
        for(int i=0;i<entries.size();i++){
            if(!(entries.get(i) instanceof FileTransferStore.Entry)||!entry.id.equals(((FileTransferStore.Entry)entries.get(i)).id))continue;
            int next=i+1<entries.size()?i+1:i-1;
            if(next>=0)nextFocus="file-"+((FileTransferStore.Entry)entries.get(next)).id;
            break;
        }
        String target=nextFocus;
        busy=true;
        modal.box.setEnabled(false);
        modal.dialog.setCancelable(false);
        View remove=modal.box.findViewWithTag("file-delete-confirm");
        View cancel=modal.box.findViewWithTag("file-delete-cancel");
        remove.setEnabled(false);
        cancel.setEnabled(false);
        int version=++jobVersion;
        worker.execute(()->{
            String failure="";
            try{store.remove(entry.id);}catch(IOException error){failure="文件删除未完成，请重新读取核对";}
            String message=failure;
            main.post(()->{
                if(!open()||version!=jobVersion)return;
                busy=false;
                if(message.isEmpty()){
                    dismissConfirmation();
                    receipt=null;phase="list";focus=target;error="";
                    phone.configurationChanged();
                    if(!loading)readList();
                    ui.success("接收文件已删除");
                }else{
                    deleteError=message;problem.setText(message);problem.setVisibility(View.VISIBLE);
                    modal.box.setEnabled(true);modal.dialog.setCancelable(true);
                    cancel.setEnabled(true);remove.setEnabled(true);cancel.requestFocusFromTouch();
                }
            });
        });
    }
    private InputStream input(FileTransferSource.Item item)throws IOException{InputStream value=item.file!=null?new FileInputStream(item.file):ui.getContentResolver().openInputStream(item.uri);if(value==null)throw new IOException("文件无法读取");return value;}
    private void copyToReceived(String next){if(selected==null||busy)return;FileTransferSource.Item item=selected;busy=true;receivedBytes=expectedBytes=0;phase="receiving";focus="file-cancel-receive";receivingName=item.name;int version=++jobVersion;render();worker.execute(()->{try(InputStream in=input(item)){FileTransferStore.Entry entry=store.receive(in,item.bytes,item.name,()->!destroyed&&version==jobVersion,(bytes,total)->progress(bytes,total,version));main.post(()->{if(!open()||version!=jobVersion)return;busy=false;receipt=entry;selected=null;source=0;phone.configurationChanged();if(next.equals("download")){phase="detail";focus="file-download";download();}else{phase="list";focus="file-"+entry.id;readList();ui.success("文件已保存到已接收");}});}catch(IOException|RuntimeException failure){main.post(()->{if(!open()||version!=jobVersion)return;busy=false;phase="detail";focus=detailFocus();error="读取未完成，请检查文件、授权或存储空间";render();});}});}
    private void receiving(){fileName(receivingName);progress=new ProgressBar(ui,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);sheet.body.addView(progress,new LinearLayout.LayoutParams(-1,ui.px(10)));progress.setProgress(expectedBytes>0?(int)Math.min(1000,receivedBytes*1000/expectedBytes):0);progressText=copy(expectedBytes>0?Math.min(100,receivedBytes*100/expectedBytes)+"%":FileTransferSource.size(receivedBytes),16,26,UiPalette.SECONDARY);sheet.body.addView(progressText);compact("取消当前文件","file-cancel-receive",()->{++jobVersion;busy=false;go("list","file-source-"+source);readList();ui.feedback("已取消当前文件读取");},false);}
    private void progress(long bytes,long total,int version){long now=SystemClock.elapsedRealtime();if(now-lastProgress<250&&bytes!=total)return;lastProgress=now;main.post(()->{if(!open()||!phase.equals("receiving")||version!=jobVersion)return;receivedBytes=bytes;expectedBytes=total;if(progress!=null)progress.setProgress(total>0?(int)Math.min(1000,bytes*1000/total):0);if(progressText!=null)progressText.setText(total>0?Math.min(100,bytes*100/total)+"%":FileTransferSource.size(bytes));});}
    private void sendDownload(){
        if(WallpaperUploadPanel.lanAddress(ui)==null){ui.error("未连接局域网，请连接 Wi-Fi 或网线");return;}
        String state=phone.state();
        if(!state.equals("waiting")&&!state.equals("connected")){ui.error("请在快捷控制中连接手机后重试");return;}
        if(receipt!=null)download();else copyToReceived("download");
    }
    private void download(){if(receipt==null||busy)return;FileTransferStore.Entry entry=receipt;busy=true;render();int version=++jobVersion;worker.execute(()->{String failure="";try{phone.offerFile(entry.id);}catch(IOException problem){failure=problem.getMessage();}String message=failure;main.post(()->{if(!open()||version!=jobVersion)return;busy=false;if(message.isEmpty()){phase="list";focus="file-"+entry.id;readList();ui.success("已发送到手机设置网页");}else{error=message;render();}});});}
    private void install(){if(receipt!=null){launchInstall(receipt.id,null,null);return;}if(selected==null||busy)return;FileTransferSource.Item item=selected;busy=true;render();int version=++jobVersion;worker.execute(()->{File temp=null;try{File cache=new File(ui.getCacheDir(),"selected-apks");if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("安装准备目录不可用");temp=File.createTempFile("selected-apk-",".apk",cache);try(InputStream in=input(item);FileOutputStream output=new FileOutputStream(temp)){FileTransferPolicy.copy(in,output,item.bytes,Math.min(FileTransferPolicy.MAX_FILE,ui.getCacheDir().getUsableSpace()-FileTransferPolicy.RESERVE),()->!destroyed&&version==jobVersion,(a,b)->{});output.getFD().sync();}File ready=temp;main.post(()->{if(!open()||version!=jobVersion){if(!ready.delete())DiagnosticLog.info("file_tools.install_source_retained","cancelled");return;}busy=false;installSource=ready;launchInstall(UUID.randomUUID().toString(),ready,item.name);});}catch(IOException|RuntimeException failure){if(temp!=null&&!temp.delete())DiagnosticLog.info("file_tools.install_source_retained","failed");main.post(()->{if(open()&&version==jobVersion){busy=false;error="安装文件准备失败，请检查文件和存储空间";render();}});}});}
    private void launchInstall(String id,File input,String name){Intent intent=new Intent(ui,ApkInstallActivity.class).putExtra("file",id).putExtra("begin-install",true);if(!SettingsFocusMotion.reduced())try{int width=ui.getResources().getDisplayMetrics().widthPixels,height=ui.getResources().getDisplayMetrics().heightPixels;Bitmap backdrop=Bitmap.createBitmap(320,Math.max(1,Math.round(320f*height/width)),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(backdrop);canvas.scale(320f/width,320f/width);ui.drawSheetBackdrop(canvas);intent.putExtra("backdrop",backdrop);}catch(OutOfMemoryError unavailable){DiagnosticLog.info("file_tools.backdrop_unavailable","memory");}if(input!=null)intent.putExtra("source-file",input.getAbsolutePath()).putExtra("source-name",name);external=true;sheet.dialog.hide();try{ui.startActivityForResult(intent,INSTALL);}catch(ActivityNotFoundException|SecurityException failure){external=false;sheet.dialog.show();error="系统安装入口不可用，请通过电脑安装";render();}}
    private void preparePreview(){
        if(busy)return;discard();busy=true;error="";int version=++jobVersion;FileTransferStore.Entry selectedReceipt=receipt;FileTransferSource.Item item=selected;phase="preview";render();worker.execute(()->{WallpaperImport.Prepared ready=null;Bitmap image=null;WallpaperCatalog.Entry existing=null;try{String known=selectedReceipt==null?"":ui.getSharedPreferences("file-transfer-wallpapers",0).getString(selectedReceipt.id,"");existing=WallpaperStore.get(ui).snapshot().find(known);if(existing==null){try(InputStream in=selectedReceipt!=null?new FileInputStream(store.file(selectedReceipt.id)):input(item)){ready=WallpaperImport.prepare(ui,in,selectedReceipt!=null?selectedReceipt.bytes:item.bytes,selectedReceipt!=null?selectedReceipt.name:item.name,WallpaperImport.video(selectedReceipt!=null?selectedReceipt.name:item.name,null),()->!destroyed&&version==jobVersion,"source=file_tools_preview");}}if(ready!=null&&ready.video==null){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(ready.width/options.inSampleSize>1280||ready.height/options.inSampleSize>720)options.inSampleSize*=2;image=BitmapFactory.decodeFile(ready.existing().getPath(),options);if(image==null)throw new IOException("图片无法读取");}else if(existing!=null&&!existing.video())image=WallpaperImages.decode(ui,WallpaperStore.get(ui),existing,1280,720);WallpaperImport.Prepared draft=ready;Bitmap picture=image;WallpaperCatalog.Entry catalogEntry=existing;main.post(()->{if(!open()||version!=jobVersion){if(draft!=null)draft.discard();return;}prepared=draft;photo=picture;mediaEntry=catalogEntry!=null?catalogEntry:entry(draft);busy=false;focus="file-preview";render();});}catch(IOException|RuntimeException|OutOfMemoryError failure){if(ready!=null)ready.discard();main.post(()->{if(open()&&version==jobVersion){busy=false;error="文件无法预览，请检查格式、授权或存储空间";phase="detail";focus="file-back";render();}});}});
    }
    private WallpaperCatalog.Entry entry(WallpaperImport.Prepared draft){WallpaperVideo.Info info=draft.video;return info==null?new WallpaperCatalog.Entry(draft.id,draft.name,draft.width,draft.height,(draft.existing()==null?0:draft.existing().length())):new WallpaperCatalog.Entry(draft.id,draft.name,draft.width,draft.height,(draft.existing()==null?0:draft.existing().length()),"video",info.mime,info.durationMs,info.fps,info.rotation,info.pixelRatio);}
    private void previewBody(){if(mediaEntry==null)return;stopPreview();FrameLayout frame=new SurfaceCorners.Frame(ui);frame.setBackgroundDrawable(ui.surface(Color.BLACK,Color.BLACK,0,9.6f));UiCompat.clipOutline(frame);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,ui.px(182.4f));fp.topMargin=0;fp.bottomMargin=ui.px(3.84f);sheet.body.addView(frame,fp);if(!mediaEntry.video()){ImageView image=new ImageView(ui);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(photo);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));return;}float aspect=VideoGeometry.aspect(mediaEntry.width,mediaEntry.height,mediaEntry.pixelRatio,mediaEntry.rotation);int maximumWidth=ui.px(500.1f),maximumHeight=ui.px(182.4f),width=Math.min(maximumWidth,Math.round(maximumHeight*aspect)),height=Math.min(maximumHeight,Math.round(maximumWidth/aspect));previewHost=new FrameLayout(ui);frame.addView(previewHost,new FrameLayout.LayoutParams(width,height,Gravity.CENTER));video=new VideoWallpaperPlayer(ui,previewHost,true);video.setSound(false);File file=prepared==null?null:prepared.existing();video.play(mediaEntry,file,new VideoWallpaperPlayer.Listener(){public void ready(){}public void stopped(){}public void failed(VideoWallpaperPlayer.Failure reason){if(open()&&phase.equals("preview")){error=reason.hint(true);mediaEntry=null;phase="detail";focus="file-back";render();}}});}
    private void stopPreview(){if(video!=null){video.destroy();video=null;}previewHost=null;}
    private void discard(){stopPreview();WallpaperImport.Prepared value=prepared;prepared=null;mediaEntry=null;photo=null;if(value!=null)value.discard();}
    private void applyWallpaper(){
        if(busy||mediaEntry==null)return;stopPreview();busy=applying=true;int version=++jobVersion;
        WallpaperImport.Prepared draft=prepared;WallpaperCatalog.Entry chosen=mediaEntry;String receiptId=receipt==null?null:receipt.id,title=name();render();
        worker.execute(()->{try{
            WallpaperCatalog.Entry saved=draft==null?chosen:draft.save(title,()->!destroyed&&version==jobVersion);
            main.post(()->{if(!open()||version!=jobVersion||!active)return;try{
                WallpaperStore.get(ui).select(saved.id);
                if(receiptId!=null)ui.getSharedPreferences("file-transfer-wallpapers",0).edit().putString(receiptId,saved.id).apply();
                busy=applying=false;PhoneSettingsSession.get(ui).configurationChanged();ui.success("文件已导入并设为壁纸");close();
            }catch(IOException failure){busy=applying=false;error="壁纸保存失败，文件和预览已保留";render();}});
        }catch(IOException|RuntimeException failure){main.post(()->{if(open()&&version==jobVersion){busy=applying=false;error="壁纸保存失败，文件和预览已保留";render();}});}});
    }
    void activityResult(int request,int result,Intent data){
        if(request!=PICK&&request!=TREE&&request!=INSTALL)return;if(!open())return;external=false;sheet.dialog.show();if(request==INSTALL){if(installSource!=null){if(installSource.isFile()&&!installSource.delete())DiagnosticLog.info("file_tools.install_source_retained","installer_return");installSource=null;}phase="install";installerOpened=data!=null&&data.getBooleanExtra("installer-opened",false);focus="file-return-detail";error="";if(data!=null&&data.getBooleanExtra("installation-verified",false)){phase="detail";focus="file-install";render();ui.success("应用已安装");}else render();return;}
        if(result!=android.app.Activity.RESULT_OK||data==null||data.getData()==null){error="未选择文件或目录，可继续浏览存储或使用局域网传输";render();return;}Uri uri=data.getData();if(request==TREE){if(!FileTransferSource.Documents.validTree(uri)){error="目录授权无效，请重新选择";render();return;}source=treeSource;tree=uri;current=FileTransferSource.Documents.root(uri);folder=boundary=null;parents.clear();temporaryDirectory=false;try{FileTransferSource.Documents.remember(ui,source==2,uri,data.getFlags());}catch(IOException failure){temporaryDirectory=true;error=failure.getMessage();}phase="list";focus="file-options";readList();}
        else{int version=++jobVersion;worker.execute(()->{try{FileTransferSource.Item item=FileTransferSource.selected(ui,uri);main.post(()->{if(open()&&version==jobVersion){receipt=null;selected=item;source=1;go("detail",detailFocus());}});}catch(IOException|RuntimeException failure){main.post(()->{if(open()&&version==jobVersion){error="选中文件无法读取，请重新选择或检查授权";render();}});}});}
    }
    void permissionResult(int request){if(request==PERMISSION&&open()){go("list","file-options");readList();}}
    private String phoneFilesKey="";
    private final Runnable phoneChanged=this::phoneChanged;
    private void phoneChanged(){String[] names=new File(ui.getFilesDir(),"received-files").list();if(names==null)return;java.util.Arrays.sort(names);String key=java.util.Arrays.toString(names);if(key.equals(phoneFilesKey))return;phoneFilesKey=key;if(open()&&active&&phase.equals("list")){if(busy||confirmation!=null)receivedChanged=true;else readList();}}
    void resume(){active=true;phone.observeConfiguration(phoneChanged);retaining=false;if(open()&&!external){sheet.dialog.show();if(phone.state().equals("closed"))phone.start();if(phase.equals("list"))readList();else if(phase.equals("preview")){if((mediaEntry==null||!mediaEntry.video()&&photo==null)&&!busy)preparePreview();else render();}else render();}}
    void pause(){active=false;phone.forgetConfiguration(phoneChanged);if(!busy||confirmation==null){++jobVersion;busy=applying=false;}stopPreview();if(phase.equals("receiving")){phase="list";focus="file-source-"+source;}}
    void save(Bundle state){if(!open())return;retaining=true;remember();Bundle value=new Bundle();value.putString("phase",phase.equals("receiving")?"expired":phase);value.putBoolean("confirm-delete",confirmation!=null&&!busy);value.putInt("source",source);value.putInt("tree-source",treeSource);value.putString("delete-return-focus",deleteReturnFocus);value.putBoolean("installer-opened",installerOpened);value.putString("focus",phase.equals("receiving")||phase.equals("expired")?"file-restart":focus);value.putString("error",error);value.putBoolean("external",external);value.putBoolean("temporary-directory",temporaryDirectory);if(folder!=null)value.putString("folder",folder.getAbsolutePath());if(boundary!=null)value.putString("boundary",boundary.getAbsolutePath());if(tree!=null){value.putString("tree",tree.toString());value.putString("current",current.toString());ArrayList<String> links=new ArrayList<>();for(Uri uri:parents)links.add(uri.toString());value.putStringArrayList("parents",links);}if(receipt!=null)value.putString("receipt",receipt.id);if(selected!=null){value.putString("selected-uri",selected.uri.toString());value.putString("selected-name",selected.name);value.putLong("selected-bytes",selected.bytes);if(selected.file!=null)value.putString("selected-file",selected.file.getAbsolutePath());}if(prepared!=null)prepared.saveState(value);if(installSource!=null)value.putString("install-source",installSource.getAbsolutePath());state.putBundle("file-tools",value);}
    void restore(Bundle state){Bundle value=state==null?null:state.getBundle("file-tools");if(value==null)return;phase=value.getString("phase","list");source=value.getInt("source");treeSource=value.getInt("tree-source",1);installerOpened=value.getBoolean("installer-opened");boolean confirm=value.getBoolean("confirm-delete")||phase.equals("delete");if(phase.equals("delete"))phase=value.getString("delete-return-phase","list");if(phase.equals("download"))phase="list";deleteReturnFocus=value.getString("delete-return-focus","file-source-0");focus=confirm?deleteReturnFocus:phase.equals("expired")?"file-restart":value.getString("focus","file-source-"+source);error=value.getString("error","");external=value.getBoolean("external");temporaryDirectory=value.getBoolean("temporary-directory");String path=value.getString("folder"),edge=value.getString("boundary");if(path!=null)folder=new File(path);if(edge!=null)boundary=new File(edge);if(value.containsKey("tree")){tree=Uri.parse(value.getString("tree"));current=Uri.parse(value.getString("current"));ArrayList<String> links=value.getStringArrayList("parents");if(links!=null)for(String uri:links)parents.addLast(Uri.parse(uri));}try{store=new FileTransferStore(new File(ui.getFilesDir(),"received-files"));if(value.containsKey("receipt"))receipt=store.entry(value.getString("receipt"));if(value.containsKey("selected-uri"))selected=new FileTransferSource.Item(value.getString("selected-name"),value.getLong("selected-bytes"),false,Uri.parse(value.getString("selected-uri")),value.containsKey("selected-file")?new File(value.getString("selected-file")):null);if(value.containsKey("draft-id")){prepared=WallpaperImport.Prepared.restore(ui,value);mediaEntry=entry(prepared);}}catch(IOException|RuntimeException failure){phase="list";error="原文件或预览已不可用，请重新选择";}if(value.containsKey("install-source"))installSource=new File(value.getString("install-source"));create();if(phase.equals("preview")&&prepared!=null&&prepared.video==null){busy=true;WallpaperImport.Prepared draft=prepared;int version=++jobVersion;render();worker.execute(()->{BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(draft.width/options.inSampleSize>1280||draft.height/options.inSampleSize>720)options.inSampleSize*=2;Bitmap image=BitmapFactory.decodeFile(draft.existing().getPath(),options);main.post(()->{if(!open()||version!=jobVersion)return;busy=false;photo=image;if(image==null){error="原预览无法读取，请返回后重试";mediaEntry=null;phase="detail";}render();});});}if(external)sheet.dialog.hide();else if(confirm&&receipt!=null){
        if(phase.equals("list")&&loading)reopenDeleteAfterLoad=true;
        else showDeleteConfirmation();
    }}
    private void back(){if(applying)return;if(confirmation!=null){if(!busy)confirmation.dialog.dismiss();return;}if(!phase.equals("list")){++jobVersion;busy=false;discard();go("list",receipt!=null?"file-"+receipt.id:"file-source-"+source);readList();}else if(!busy&&(folder!=null||tree!=null))up();else close();}
    void close(){if(applying||confirmation!=null&&busy)return;dismissConfirmation();if(sheet!=null)sheet.dialog.dismiss();}
    void destroy(){phone.forgetConfiguration(phoneChanged);dismissConfirmation();destroyed=true;if(sheet!=null){sheet.dialog.setOnDismissListener(null);sheet.dialog.dismiss();sheet=null;}++jobVersion;++listVersion;stopPreview();if(!retaining)discard();main.removeCallbacksAndMessages(null);worker.shutdown();}
}
