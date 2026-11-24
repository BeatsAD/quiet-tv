package home.quiet.tv;

import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A library picker bound to one managed app for the lifetime of the sheet. */
final class IconLibraryPanel extends ManagementPanel {
    private static final String[] SHAPE_ZH={"圆角矩形","圆形","圆角正方形"},SHAPE_EN={"Rounded rectangle","Circle","Rounded square"};
    private static final String[] FORM_ZH={"文字＋图标","纯图标","透明背景"},FORM_EN={"Text + icon","Icon only","Transparent"};
    private static final int COLUMNS=4;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),thumbnailWorkers=Executors.newFixedThreadPool(2);
    private final Set<IconLibraryTransfer.Request> artworkRequests=Collections.newSetFromMap(new ConcurrentHashMap<IconLibraryTransfer.Request,Boolean>());
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AppCoverStore covers;
    private final Context artworkContext;
    private final String pkg,label;
    private final Runnable changed,closed;
    private ManagementUi.Sheet sheet;
    private ResolveInfo target;
    private final ArrayList<Button> selectors=new ArrayList<>(),toolbarControls=new ArrayList<>();
    private final android.util.LruCache<String,Bitmap> thumbs;
    private IconLibraryCatalog catalog;
    private List<IconLibraryCatalog.Item> items=Collections.emptyList();
    private String theme="",style="apple",shape="rectangle",form="icon",galleryFocusTag="";
    private volatile boolean destroyed;private boolean busy,reloadPending,refreshing;
    private String refreshMessage="";
    private volatile int loading,thumbnails;
    private int focusRequest;
    private int scroll;
    private RecyclerView library;
    private GalleryAdapter adapter;
    private TextView empty,status;
    private Button restore,uploadAction,refresh,galleryOrigin;
    private IconLibraryReplaceSheet replacement;
    private ManagementUi.Sheet restoreSheet;
    private PopupWindow popup;
    private IconUploadSession upload;
    private final Runnable catalogChanged=()->{if(showing){if(busy||refreshing||popup!=null||replacement!=null||restoreSheet!=null)reloadPending=true;else{remember();load();}}};
    private final Runnable phoneChanged=this::refreshCurrentIfChanged;
    private final BroadcastReceiver packages=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){if(showing){if(busy||refreshing||popup!=null||replacement!=null||restoreSheet!=null)reloadPending=true;else{remember();load();}}}};
    IconLibraryPanel(AppManagementActivity activity,String pkg,String label,Runnable changed,Runnable closed){
        super(activity);
        this.pkg=pkg;this.label=label;this.changed=changed;this.closed=closed;
        covers=AppCoverStore.get(activity);
        artworkContext=activity;
        thumbs=IconLibraryRepository.get(artworkContext).thumbnails;
    }

    private String tr(String zh,String en){return activity.tr(zh,en);}

    void show(){
        shape=new DesktopSettingsStore(activity).iconShape();
        sheet=activity.new Sheet(tr("更换「"+label+"」的图标","Change icon for "+label));
        Button close=sheet.settingsLayout(tr("关闭","Close"),this::close);
        sheet.settingsActions(false);
        sheet.fixedSize(660,382);
        sheet.box.setTag("app-icon-picker");
        sheet.dialog.setOnDismissListener(dialog->release());
        // The grid is its own bounded scrolling surface.
        sheet.scroll.removeView(sheet.body);
        sheet.scroll.setVisibility(View.GONE);
        pageRoot=sheet.body;
        pageRoot.setPadding(0,0,0,px(14));
        sheet.box.addView(pageRoot,sheet.box.indexOfChild(sheet.scroll),new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout toolbar=row();
        toolbar.setTag("icon-library-toolbar");
        pageRoot.addView(toolbar,new LinearLayout.LayoutParams(-1,px(34)));
        selector(toolbar,"icons-style",this::pickStyles);
        selector(toolbar,"icons-form",this::pickForms);
        uploadAction=control(toolbar,tr("扫码上传","Scan to upload"),"icons-upload",this::upload);
        controlGlyph(uploadAction,"qr-code");
        restore=control(toolbar,tr("恢复默认图标","Restore default"),"icons-restore",this::restore);
        refresh=control(toolbar,tr("刷新","Refresh"),"icons-refresh",this::refreshOnline);
        refresh.setContentDescription(tr("刷新图标库","Refresh icon library"));
        for(Button control:toolbarControls)control.setNextFocusUpId(close.getId());

        FrameLayout gallery=new FrameLayout(activity);
        LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,0,1);
        space.topMargin=px(8);
        pageRoot.addView(gallery,space);
        library=new RecyclerView(activity);
        library.setTag("icon-library-grid");
        library.setLayoutManager(new GridLayoutManager(activity,COLUMNS));
        library.setItemAnimator(null);
        library.setVerticalScrollBarEnabled(false);
        adapter=new GalleryAdapter();
        library.setAdapter(adapter);
        gallery.addView(library,new FrameLayout.LayoutParams(-1,-1));
        empty=center(tr("正在读取图标库…","Loading icon library…"),14);
        empty.setTag("icon-library-empty");
        gallery.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        status=muted("",12);
        status.setTag("icon-library-status");
        pageRoot.addView(status,new LinearLayout.LayoutParams(-1,-2));
        IntentFilter changes=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED);
        changes.addDataScheme("package");
        registerReceiver(packages,changes);
        showing=true;
        PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);
        IconLibraryRepository.get(artworkContext).observe(catalogChanged);
        updateSelectors();
        enabled();
        sheet.show(close);
        load();
    }
    static String shapeName(String shape,boolean chinese){
        return (chinese?SHAPE_ZH:SHAPE_EN)[Arrays.asList(IconLibraryCatalog.SHAPES).indexOf(shape)];
    }
    private String valid(String value,String[] choices){return IconLibraryCatalog.contains(choices,value)?value:choices[0];}
    private Button control(LinearLayout parent,String title,String tag,Runnable action){
        Button button=button(title,action);
        button.setTag(tag);
        button.setId(androidx.core.view.ViewCompat.generateViewId());
        button.setSingleLine(true);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13*unit);
        button.setPadding(px(9),0,px(9),0);
        button.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
        button.setBackgroundDrawable(new LibraryControlDrawable());
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-2,-1);
        if(parent.getChildCount()>0)size.leftMargin=px(8);
        parent.addView(button,size);
        wireToolbar(button);
        return button;
    }
    private void controlGlyph(Button button,String name){android.graphics.drawable.Drawable glyph=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon(name)).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(glyph,button.getTextColors());controlGlyph(button,glyph);}
    private void controlGlyph(Button button,android.graphics.drawable.Drawable glyph){glyph.setBounds(0,0,px(12),px(12));button.setCompoundDrawables(glyph,null,null,null);button.setCompoundDrawablePadding(px(6));button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int inset=Math.max(px(6),Math.round((r-l-glyph.getBounds().width()-button.getCompoundDrawablePadding()-button.getPaint().measureText(button.getText().toString()))/2));if(button.getPaddingLeft()!=inset)button.setPadding(inset,0,inset,0);});}
    private void selector(LinearLayout parent,String tag,Runnable action){Button b=control(parent,"",tag,action);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);selectorArrow(b,false);selectors.add(b);}
    private final class LibraryControlDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF area=new RectF();
        private boolean state(int attr){for(int value:getState())if(value==attr)return true;return false;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] values){invalidateSelf();return true;}
        @Override public void draw(Canvas canvas){
            boolean focused=state(android.R.attr.state_enabled)&&state(android.R.attr.state_focused)&&state(android.R.attr.state_window_focused);area.set(getBounds());float radius=px(12);
            paint.setStyle(Paint.Style.FILL);paint.setColor(focused?UiPalette.TEXT:UiPalette.CONTROL);SurfaceCorners.draw(canvas,area,radius,radius,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(.5f));paint.setColor(UiPalette.EDGE_STRONG);area.inset(paint.getStrokeWidth()/2,paint.getStrokeWidth()/2);SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void wireToolbar(Button button){
        int position=toolbarControls.size();toolbarControls.add(button);button.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){galleryOrigin=button;focusLibrary(true);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int step=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;for(int i=position+step;i>=0&&i<toolbarControls.size();i+=step)if(toolbarControls.get(i).isShown()&&toolbarControls.get(i).isEnabled()){toolbarControls.get(i).requestFocus();break;}return true;}
            return false;
        });
    }
    private void focusToolbar(View from){
        if(galleryOrigin!=null&&galleryOrigin.isShown()&&galleryOrigin.isEnabled()&&galleryOrigin.requestFocus())return;
        int[] location=new int[2];from.getLocationOnScreen(location);int center=location[0]+from.getWidth()/2,distance=Integer.MAX_VALUE;Button nearest=selectors.get(0);
        for(Button button:toolbarControls)if(button.isShown()&&button.isEnabled()){button.getLocationOnScreen(location);int delta=Math.abs(center-location[0]-button.getWidth()/2);if(delta<distance){distance=delta;nearest=button;}}nearest.requestFocus();
    }
    private void selectorArrow(Button button,boolean open){android.graphics.drawable.Drawable arrow=new SelectorChevron(open);arrow.setBounds(0,0,px(14),px(14));button.setCompoundDrawables(null,null,arrow,null);button.setCompoundDrawablePadding(px(4));}
    private static final class SelectorChevron extends android.graphics.drawable.Drawable {
        private final QuickControlIcon icon=new QuickControlIcon("caret-down");private final boolean open;
        SelectorChevron(boolean open){this.open=open;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] state){return icon.setState(state);}
        @Override protected void onBoundsChange(Rect bounds){icon.setBounds(bounds);}
        @Override public void draw(Canvas canvas){int save=canvas.save();if(open)canvas.rotate(180,getBounds().exactCenterX(),getBounds().exactCenterY());icon.draw(canvas);canvas.restoreToCount(save);}
        @Override public void setAlpha(int value){icon.setAlpha(value);}
        @Override public void setColorFilter(ColorFilter value){icon.setColorFilter(value);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void ui(Runnable action){main.post(()->{if(!destroyed&&!isDestroyed())action.run();});}
    private void load(){
        int run=++loading;
        worker.execute(()->{
            IconLibraryCatalog next=null;
            boolean failed=false;
            try{next=IconLibraryCatalog.read(artworkContext);}
            catch(IOException|RuntimeException unavailable){failed=true;}
            final IconLibraryCatalog loaded=next;
            final boolean artworkUnavailable=failed;
            ResolveInfo entry=null;
            try{if(AppCoverPolicy.allowed(activity,pkg))entry=new HomeAppCatalog(activity).find(pkg);}
            catch(RuntimeException unavailable){}
            final ResolveInfo app=entry;
            ui(()->{
                if(run!=loading||!showing)return;
                if(app==null){
                    error(tr("应用已不可用，请重新选择","The app is no longer available"));
                    close();
                    return;
                }
                target=app;
                catalog=loaded;
                updateSelectors();
                filter();
                if(scroll>0)library.scrollToPosition(Math.min(scroll,Math.max(0,items.size()-1)));
                if(artworkUnavailable)status.setText(tr("图标素材无法读取，仍可扫码上传","Artwork unavailable; phone upload is available"));
                else if(!refreshMessage.isEmpty())status.setText(refreshMessage);
                refreshMessage="";
                if(!items.isEmpty()&&sheet.box.findViewWithTag("sheet-close").isFocused())focusLibrary();
            });
        });
    }

    private void updateSelectors(){
        form=valid(form,IconLibraryCatalog.forms(shape));
        if(catalog!=null&&!catalog.styles.isEmpty()&&!IconLibraryCatalog.contains(catalog.styleIds(),style)){
            style=catalog.styles.get(0).id;scroll=0;
        }
        ArrayList<String> forms=availableForms();
        if(!forms.isEmpty()&&!forms.contains(form))form=forms.get(0);
        selectors.get(0).setText(catalog==null?IconLibraryCatalog.legacyStyleName(style,activity.chinese()):catalog.styleName(style,activity.chinese()));
        selectors.get(1).setText(formLabel(form));
        selectors.get(1).setVisibility(forms.size()>1?View.VISIBLE:View.GONE);
    }

    private ArrayList<String> availableForms(){
        ArrayList<String> values=new ArrayList<>();
        if(catalog!=null)for(String option:IconLibraryCatalog.forms(shape))if(!catalog.style(style,shape,option).isEmpty())values.add(option);
        return values;
    }

    private String formLabel(String value){
        return (activity.chinese()?FORM_ZH:FORM_EN)[Arrays.asList(IconLibraryCatalog.FORMS).indexOf(value)];
    }

    private void pickStyles(){
        if(catalog==null||catalog.styles.isEmpty())return;
        String[] values=catalog.styleIds(),labels=new String[values.length];
        for(int i=0;i<values.length;i++)labels[i]=catalog.styleName(values[i],activity.chinese());
        menu(selectors.get(0),labels,Arrays.asList(values).indexOf(style),index->{
            style=values[index];reloadSelection();
        });
    }

    private void pickForms(){
        ArrayList<String> values=availableForms();
        String[] labels=new String[values.size()];
        for(int i=0;i<labels.length;i++)labels[i]=formLabel(values.get(i));
        menu(selectors.get(1),labels,values.indexOf(form),index->{
            form=values.get(index);reloadSelection();
        });
    }

    private void reloadSelection(){
        updateSelectors();
        scroll=0;
        filter();
        library.scrollToPosition(0);
    }
    private void refreshOnline(){
        if(refreshing)return;remember();refreshing=true;refreshMessage="";refresh.setText(tr("刷新中…","Refreshing…"));status.setText(tr("正在刷新图标库…","Refreshing icon library…"));enabled();
        IconLibraryRepository.get(artworkContext).refresh((changed,error)->ui(()->{refreshing=false;refresh.setText(tr("刷新","Refresh"));enabled();refreshMessage=!error.isEmpty()?tr(error,"Unable to refresh. Try again later."):changed?tr("图标库已更新","Icon library updated"):tr("已是最新图标库","Icon library is up to date");if(changed)reloadPending=true;if(reloadPending)reloadIfPending();else{status.setText(refreshMessage);refreshMessage="";}}));
    }
    @android.annotation.SuppressLint("NotifyDataSetChanged") private void filter(){
        cancelArtwork();++thumbnails;items=catalog==null?Collections.emptyList():catalog.style(style,shape,form);adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);empty.setText(catalog!=null&&catalog.items.isEmpty()?tr("图标素材待接入","Icon artwork will be added in the next stage"):tr("此组合暂无图标","No artwork for these options"));
        IconLibraryCatalog.Item chosen=null;for(IconLibraryCatalog.Item item:items)if(item.id.equals(theme))chosen=item;if(chosen==null&&!items.isEmpty())chosen=items.get(0);if(chosen!=null)select(chosen);else theme="";enabled();
    }
    private void upload(){
        if(busy||target==null)return;
        if(upload!=null)upload.close();
        upload=new IconUploadSession(activity,pkg,label,sheet,()->{
            upload=null; // Let the completed HTTP session answer a pending receipt query.
            saved();
            activity.success(tr("已替换「"+label+"」图标","Icon replaced for "+label));
        });
        upload.show();
    }
    private void saved(){changed.run();close();}
    private void cancelArtwork(){for(IconLibraryTransfer.Request request:artworkRequests)request.cancel();artworkRequests.clear();}
    private void select(IconLibraryCatalog.Item item){theme=item.id;galleryFocusTag="icon-item-"+item.id;}
    private void refreshCurrentIfChanged(){
        if(!showing)return;
        enabled();
        if(replacement!=null){replacement.desktopShapeChanged();return;}
        String currentShape=new DesktopSettingsStore(activity).iconShape();
        if(!shape.equals(currentShape)&&restoreSheet==null)showMatchingShape(currentShape);
    }
    private void enabled(){
        boolean wasFocused=restore.isFocused();
        restore.setEnabled(!busy&&target!=null&&covers.custom(pkg));
        selectors.get(0).setEnabled(!busy&&catalog!=null&&!catalog.styles.isEmpty());
        selectors.get(1).setEnabled(!busy);
        refresh.setEnabled(!busy&&!refreshing);
        uploadAction.setEnabled(!busy&&target!=null);
        library.setEnabled(!busy);
        if(wasFocused&&!restore.isEnabled()&&showing)focusLibrary();
    }
    private interface Pick {void select(int index);}
    private void menu(Button origin,String[] labels,int current,Pick picked){
        if(popup!=null)popup.dismiss();
        LinearLayout rows=new KeyFeedback.Menu(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        int side=px(18),top=px(12),bottom=px(24);
        int extraSide=side-px(5);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        rows.setPadding(side,top,side,bottom);
        ScrollView scroll=activity.settingsScroll();
        scroll.setClipChildren(false);
        scroll.addView(rows);
        SettingsSheetBackdrop backdrop=new SettingsSheetBackdrop(activity,scroll).cornerRadius(12);
        scroll.setBackgroundDrawable(backdrop);
        backdrop.followWindowFocus();
        android.text.TextPaint optionPaint=new android.text.TextPaint(origin.getPaint());
        optionPaint.setTextSize(12*unit);
        optionPaint.setTypeface(activity.navigationTypeface(false));
        int menuWidth=origin.getWidth();
        for(String label:labels)menuWidth=Math.max(menuWidth,(int)Math.ceil(android.text.Layout.getDesiredWidth(SettingsSelectionMark.label(label,true,unit),optionPaint))+px(34));
        menuWidth=Math.min(menuWidth,getResources().getDisplayMetrics().widthPixels-px(24)-2*extraSide);
        float textWidth=Math.max(0,menuWidth-px(34)-px(15.36f));
        int menuHeight=px(Math.min(222,labels.length*32))+top+bottom;
        PopupWindow menu=new KeyFeedback.Popup(activity,scroll,menuWidth+2*extraSide,menuHeight);
        popup=menu;
        menu.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        menu.setOutsideTouchable(true);
        Button selected=null;
        for(int i=0;i<labels.length;i++){
            int index=i;
            Button option=button(labels[i],()->{picked.select(index);menu.dismiss();});
            option.setTag("icons-menu-"+(selectors.indexOf(origin)+1)+"-"+i);
            option.setText(android.text.TextUtils.ellipsize(labels[i],optionPaint,textWidth,android.text.TextUtils.TruncateAt.END));
            if(i==current){
                QuickControlIcon check=new QuickControlIcon("check");
                check.setBounds(0,0,px(13),px(13));
                option.setCompoundDrawables(null,null,check,null);
            }
            option.setContentDescription(labels[i]+(i==current?tr("  已选择","  Selected"):""));
            option.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);
            option.setPadding(px(12),0,px(12),0);
            option.setSingleLine(true);
            option.setTypeface(activity.navigationTypeface(false));
            option.setSelected(i==current);
            option.setBackgroundDrawable(new SettingsFocusDrawable(unit,999,Color.TRANSPARENT));
            option.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));
            if(i==0)option.setOnKeyListener(new View.OnKeyListener(){
                private long upPressDownTime=-1;

                @Override public boolean onKey(View view,int key,KeyEvent event){
                    if(key!=KeyEvent.KEYCODE_DPAD_UP)return false;
                    // A key release can reach this row after focus moved here on key down.
                    if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){
                        upPressDownTime=event.getDownTime();
                    }else if(event.getAction()==KeyEvent.ACTION_UP){
                        boolean startedHere=event.getDownTime()==upPressDownTime;
                        upPressDownTime=-1;
                        if(startedHere&&!event.isCanceled())menu.dismiss();
                    }
                    return true;
                }
            });
            rows.addView(option,new LinearLayout.LayoutParams(-1,px(32)));
            if(i==current)selected=option;
        }
        CharSequence previousHint=status.getText();
        String menuHint=tr("↑ 返回筛选  确定  选择","↑ Back to filter  OK  Select");
        menu.setOnDismissListener(()->{
            if(popup==menu)popup=null;
            selectorArrow(origin,false);
            if(menuHint.contentEquals(status.getText()))status.setText(previousHint);
            if(showing)origin.requestFocus();
            reloadIfPending();
        });
        status.setText(menuHint);
        selectorArrow(origin,true);
        menu.showAsDropDown(origin,-extraSide,px(4));
        if(selected!=null)selected.requestFocus();
        else if(rows.getChildCount()>0)rows.getChildAt(0).requestFocus();
    }
    private IconLibraryCatalog.Item item(){for(IconLibraryCatalog.Item item:items)if(item.id.equals(theme))return item;return null;}
    private void apply(){
        IconLibraryCatalog.Item chosen=item();
        if(busy||replacement!=null||restoreSheet!=null||chosen==null||target==null)return;
        replacement=new IconLibraryReplaceSheet(activity,artworkContext,pkg,label,target,chosen,shape,form,catalog.revision,sheet,this::saved,this::showMatchingShape,()->{
            replacement=null;
            if(showing){refreshCurrentIfChanged();focusLibrary();reloadIfPending();}
        });
        replacement.show();
        KeyFeedback.confirm(activity);
    }
    private void showMatchingShape(String currentShape){
        shape=currentShape;
        galleryOrigin=selectors.get(0);
        reloadSelection();
        if(galleryOrigin.isEnabled())galleryOrigin.requestFocus();
    }

    private void restore(){
        if(busy||replacement!=null||restoreSheet!=null||target==null||!covers.custom(pkg))return;
        String expected=covers.key(pkg),previewShape=shape;
        ResolveInfo app=target;
        ManagementUi.Sheet confirm=activity.new Sheet(tr("恢复默认图标","Restore default icon"),sheet);
        restoreSheet=confirm;
        confirm.confirmationLayout(()->{if(!busy)confirm.dialog.dismiss();});
        confirm.box.setTag("icon-restore-sheet");
        confirm.text(label);
        ImageView[] art=IconLibraryReplaceSheet.comparison(activity,confirm.body,tr("默认图标","Default icon"));
        TextView result=muted(tr("正在读取图标…","Loading icons…"),12);
        confirm.body.addView(result);
        Bitmap[] preview=new Bitmap[2];

        Button cancel=confirm.action(tr("取消","Cancel"),()->confirm.dialog.dismiss());
        cancel.setTag("icon-restore-cancel");
        cancel.setId(androidx.core.view.ViewCompat.generateViewId());
        Button accept=confirm.action(tr("恢复默认","Restore default"),()->{
            if(busy||preview[1]==null)return;
            busy=true;
            confirm.dialog.setCancelable(false);
            confirm.box.setEnabled(false);
            for(int i=0;i<confirm.actions.getChildCount();i++)confirm.actions.getChildAt(i).setEnabled(false);
            result.setText(tr("正在恢复默认图标…","Restoring default icon…"));
            worker.execute(()->{
                String error=null;
                try{
                    AppCoverPolicy.require(activity,pkg);
                    synchronized(DesktopSettingsStore.MUTATIONS){
                        AppCoverPolicy.requireDesktopShape(activity,previewShape);
                        synchronized(covers){
                            if(!expected.equals(covers.key(pkg)))throw new IOException(tr("当前图标已变化，请重新确认","Icon changed; confirm again"));
                            covers.restore(pkg);
                        }
                    }
                }catch(IOException|RuntimeException failure){error=failure.getMessage();if(error==null)error=tr("恢复默认失败","Unable to restore default icon");}
                String failure=error;
                main.post(()->{
                    busy=false;
                    if(failure==null)PhoneSettingsSession.get(activity).configurationChanged();
                    if(destroyed)return;
                    if(failure==null){
                        confirm.dialog.dismiss();
                        saved();
                        activity.success(tr("已恢复「"+label+"」默认图标","Default icon restored for "+label));
                    }else if(restoreSheet==confirm){
                        result.setText(failure);
                        confirm.dialog.setCancelable(true);
                        confirm.box.setEnabled(true);
                        cancel.setEnabled(true);
                        cancel.requestFocus();
                    }
                });
            });
        });
        accept.setTag("icon-restore-confirm");
        accept.setId(androidx.core.view.ViewCompat.generateViewId());
        accept.setEnabled(false);
        cancel.setNextFocusRightId(accept.getId());
        accept.setNextFocusLeftId(cancel.getId());
        confirm.dialog.setOnDismissListener(dialog->{
            if(restoreSheet==confirm)restoreSheet=null;
            for(int i=0;i<art.length;i++){art[i].setImageDrawable(null);if(preview[i]!=null)preview[i].recycle();}
            if(showing){
                refreshCurrentIfChanged();
                if(restore.isEnabled())restore.requestFocus();else focusLibrary();
                reloadIfPending();
            }
        });
        confirm.show(cancel);
        worker.execute(()->{
            Bitmap before=null,after=null;
            try{
                before=AppArtwork.custom(activity,pkg,expected);
                after=AppArtwork.load(activity,app,false);
            }catch(RuntimeException|OutOfMemoryError unavailable){}
            Bitmap current=before,original=after;
            main.post(()->{
                if(destroyed||restoreSheet!=confirm||current==null||original==null){
                    if(current!=null)current.recycle();
                    if(original!=null)original.recycle();
                    if(restoreSheet==confirm)result.setText(tr("无法读取图标，请重新打开","Unable to read icons; reopen the dialog"));
                    return;
                }
                preview[0]=current;
                preview[1]=original;
                art[0].setImageDrawable(new AppShapeDrawable(current,previewShape,AppArtwork.displayedAsContain(activity,pkg)));
                art[1].setImageDrawable(new AppShapeDrawable(original,previewShape,false));
                result.setText("");
                accept.setEnabled(true);
            });
        });
    }
    private void reloadIfPending(){if(reloadPending&&showing&&!busy&&!refreshing&&popup==null&&replacement==null&&restoreSheet==null){reloadPending=false;remember();load();}}
    private void focusLibrary(){focusLibrary(false);}
    private void focusLibrary(boolean directionKey){
        focusLibraryPosition(Math.max(0,adapter.position(galleryFocusTag)),directionKey);
    }
    private void focusLibraryPosition(int position,boolean directionKey){
        if(items.isEmpty())return;
        int requested=Math.min(position,items.size()-1);
        int request=++focusRequest;
        RecyclerView.ViewHolder visible=library.findViewHolderForAdapterPosition(requested);
        if(visible!=null){visible.itemView.requestFocus();return;}
        if(directionKey)KeyFeedback.ownMove(library);
        int version=thumbnails;
        library.scrollToPosition(requested);
        library.post(()->{
            if(destroyed||!showing||version!=thumbnails||request!=focusRequest)return;
            RecyclerView.ViewHolder holder=library.findViewHolderForAdapterPosition(requested);
            if(holder==null||holder.itemView.hasFocus())return;
            if(holder.itemView.requestFocus()&&directionKey)KeyFeedback.movedTo(holder.itemView);
        });
    }
    void close(){if(sheet!=null)sheet.dialog.dismiss();}
    private void remember(){scroll=Math.max(0,((GridLayoutManager)library.getLayoutManager()).findFirstVisibleItemPosition());}

    private void release(){
        if(destroyed)return;
        destroyed=true;showing=false;
        if(replacement!=null)replacement.destroy();
        if(restoreSheet!=null)restoreSheet.dialog.dismiss();
        if(upload!=null)upload.close();
        if(popup!=null)popup.dismiss();
        cancelArtwork();
        IconLibraryRepository.get(artworkContext).forget(catalogChanged);
        PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);
        unregisterReceiver(packages);
        library.setAdapter(null);
        worker.shutdown();
        thumbnailWorkers.shutdownNow();
        closed.run();
    }
    private final class Tile extends IconGridTile {
        IconLibraryTransfer.Request request;
        Tile(){
            super(IconLibraryPanel.this.activity,new ImageView(IconLibraryPanel.this.activity));
            setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN||!(getTag() instanceof String))return false;
                int position=adapter.position((String)getTag());
                if(position<0)return false;
                if(key==KeyEvent.KEYCODE_DPAD_UP){if(position<COLUMNS){++focusRequest;focusToolbar(this);}else focusLibraryPosition(position-COLUMNS,true);return true;}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN){if((position/COLUMNS+1)*COLUMNS<items.size())focusLibraryPosition(Math.min(position+COLUMNS,items.size()-1),true);return true;}
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(position%COLUMNS>0)focusLibraryPosition(position-1,true);return true;}
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(position%COLUMNS<COLUMNS-1&&position+1<items.size())focusLibraryPosition(position+1,true);return true;}
                return false;
            });
        }
    }
    private final class GalleryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        int position(String tag){for(int i=0;i<items.size();i++)if(("icon-item-"+items.get(i).id).equals(tag))return i;return -1;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){return new RecyclerView.ViewHolder(new Tile()){};}
        @Override public int getItemCount(){return items.size();}
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            Tile tile=(Tile)holder.itemView;if(tile.request!=null)tile.request.cancel();tile.setEnabled(!busy&&target!=null);tile.squareArt(!"rectangle".equals(shape));IconLibraryCatalog.Item item=items.get(position);int available=Math.max(library.getWidth(),library.getMeasuredWidth())-library.getPaddingLeft()-library.getPaddingRight(),artWidth=Math.max(1,(available-px(4)*(COLUMNS-1))/COLUMNS-px(12)),width="rectangle".equals(shape)?Math.min(720,artWidth):Math.min(512,Math.round(artWidth*9/16f));String key=catalog.revision+":"+item.style+":"+item.id+":"+shape+":"+form+":"+width+":"+activity.chinese();tile.setTag("icon-item-"+item.id);tile.setContentDescription(item.label(activity.chinese()));tile.name.setText(item.label(activity.chinese()));tile.name.setTypeface(activity.navigationTypeface(tile.isFocused()));tile.name.setVisibility("text".equals(form)?View.INVISIBLE:View.VISIBLE);tile.setOnFocusChangeListener((v,focused)->{if(focused)select(item);});tile.setOnClickListener(v->{if(!theme.equals(item.id))select(item);apply();});tile.image.setTag(key);tile.image.setImageBitmap(thumbs.get(key));tile.requestLayout();
            if(thumbs.get(key)==null){int run=thumbnails;String selectedShape=shape,selectedForm=form;IconLibraryTransfer.Request request=new IconLibraryTransfer.Request();tile.request=request;artworkRequests.add(request);
                thumbnailWorkers.execute(()->{try{request.check();if(run!=thumbnails||destroyed)return;
                    Bitmap image=IconLibraryArtwork.render(artworkContext,item,selectedShape,selectedForm,width,activity.chinese(),true,request);
                    ui(()->{if(run!=thumbnails){image.recycle();return;}thumbs.put(key,image);if(key.equals(tile.image.getTag()))tile.image.setImageBitmap(image);});
                }catch(IOException|RuntimeException failure){/* Opening confirmation exposes download errors. */}finally{artworkRequests.remove(request);}});
            }
        }
        @Override public void onViewRecycled(RecyclerView.ViewHolder holder){Tile tile=(Tile)holder.itemView;if(tile.request!=null)tile.request.cancel();tile.image.setImageDrawable(null);}
    }

}
