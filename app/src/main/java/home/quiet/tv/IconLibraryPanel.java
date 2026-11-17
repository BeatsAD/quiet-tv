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

/** Retained native library. Browsing selects; a separate confirmation commits. */
final class IconLibraryPanel extends ManagementPanel {
    private static final String[] STYLE_ZH={"苹果风","谷歌风","扁平化","拟物化","轻拟物","3D 风格","像素风格"},STYLE_EN={"Apple","Google","Flat","Skeuomorphic","Soft","3D","Pixel"};
    private static final String[] SHAPE_ZH={"长方形","圆形","方形"},SHAPE_EN={"Rectangle","Circle","Square"};
    private static final String[] FORM_ZH={"文字＋图标","纯图标","透明背景"},FORM_EN={"Text + icon","Icon only","Transparent"};
    private static final int COLUMNS=6;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(),thumbnailWorkers=Executors.newFixedThreadPool(2);
    private final Set<IconLibraryTransfer.Request> artworkRequests=Collections.newSetFromMap(new ConcurrentHashMap<IconLibraryTransfer.Request,Boolean>());
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AppCoverStore covers;
    private final Context artworkContext;
    private final ArrayList<Target> targets=new ArrayList<>();
    private final ArrayList<Button> selectors=new ArrayList<>(),toolbarControls=new ArrayList<>();
    private final android.util.LruCache<String,Bitmap> thumbs=new android.util.LruCache<String,Bitmap>(4*1024*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getRowBytes()*value.getHeight();}};
    private IconLibraryCatalog catalog;
    private List<IconLibraryCatalog.Item> items=Collections.emptyList();
    private String pkg="",theme="",style="apple",shape="rectangle",form="icon",focusTag="icons-target",galleryFocusTag="";
    private volatile boolean destroyed;private boolean busy,reloadPending;
    private volatile int loading,thumbnails;
    private int scroll;
    private RecyclerView library;
    private GalleryAdapter adapter;
    private TextView empty,status;
    private Button restore,batchAction,uploadAction;
    private IconLibraryReplaceSheet replacement;
    private ManagementUi.Sheet restoreSheet;
    private PopupWindow popup;
    private IconUploadSession upload;
    private IconLibraryBatchSheet batch;
    private final Runnable catalogChanged=()->{if(showing){if(busy||replacement!=null||restoreSheet!=null)reloadPending=true;else load();}};
    private final Runnable phoneChanged=this::refreshCurrentIfChanged;
    private final BroadcastReceiver packages=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){if(showing){if(busy||replacement!=null||restoreSheet!=null)reloadPending=true;else load();}}};
    private static final class Target {
        final String pkg,label;final ResolveInfo info;
        Target(String pkg,String label,ResolveInfo info){this.pkg=pkg;this.label=label;this.info=info;}
    }
    IconLibraryPanel(AppManagementActivity activity){this(activity,activity);}
    IconLibraryPanel(AppManagementActivity activity,Context artworkContext){super(activity);covers=AppCoverStore.get(activity);this.artworkContext=artworkContext;}
    private String tr(String zh,String en){return activity.tr(zh,en);}
    @Override public void onCreate(Bundle saved){
        if(saved!=null){pkg=saved.getString("package","");theme=saved.getString("theme","");style=valid(saved.getString("style"),IconLibraryCatalog.STYLES);shape=valid(saved.getString("shape"),IconLibraryCatalog.SHAPES);form=valid(saved.getString("form"),IconLibraryCatalog.FORMS);scroll=saved.getInt("scroll");focusTag=saved.getString("focus","icons-target");}
        else{String requested=activity.getIntent().getStringExtra("selected-package");if(requested!=null)pkg=requested;}
        pageRoot=column();pageRoot.setClipChildren(false);pageRoot.setClipToPadding(false);activity.attachPanel(pageRoot);
        LinearLayout left=column();left.setClipChildren(false);left.setClipToPadding(false);pageRoot.addView(left,new LinearLayout.LayoutParams(-1,0,1));
        ((FrameLayout.LayoutParams)pageRoot.getLayoutParams()).topMargin=px(4);
        LinearLayout toolbar=row();toolbar.setTag("icon-library-toolbar");toolbar.setClipChildren(false);toolbar.setClipToPadding(false);left.addView(toolbar,new LinearLayout.LayoutParams(-1,px(36)));
        batchAction=control(toolbar,tr("一键替换","Replace all"),"icons-batch",true,this::batch);controlGlyph(batchAction,"grid-2x2-check");
        uploadAction=control(toolbar,tr("扫码上传","Scan to upload"),"icons-upload",false,this::upload);controlGlyph(uploadAction,"qr-code");
        selector(toolbar,"icons-target",this::pickApps);selector(toolbar,"icons-style",()->pick(1,IconLibraryCatalog.STYLES,activity.chinese()?STYLE_ZH:STYLE_EN));selector(toolbar,"icons-shape",()->pick(2,IconLibraryCatalog.SHAPES,activity.chinese()?SHAPE_ZH:SHAPE_EN));selector(toolbar,"icons-form",this::pickForms);
        restore=control(toolbar,tr("恢复默认","Restore default"),"icons-restore",false,this::restore);
        FrameLayout gallery=new FrameLayout(activity);LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.topMargin=px(14);left.addView(gallery,gp);
        library=new RecyclerView(activity);library.setTag("icon-library-grid");library.setLayoutManager(new GridLayoutManager(activity,COLUMNS));library.setItemAnimator(null);library.setClipToPadding(true);library.setPadding(0,0,0,0);library.addItemDecoration(new RecyclerView.ItemDecoration(){@Override public void getItemOffsets(Rect out,View view,RecyclerView parent,RecyclerView.State state){int position=parent.getChildAdapterPosition(view);if(position<0)return;int col=position%COLUMNS,gap=px(4);out.set(col*gap/COLUMNS,0,gap-(col+1)*gap/COLUMNS,0);}});library.setVerticalScrollBarEnabled(true);adapter=new GalleryAdapter();library.setAdapter(adapter);gallery.addView(library,new FrameLayout.LayoutParams(-1,-1));
        empty=center(tr("正在读取图标库…","Loading icon library…"),14);empty.setPadding(px(16),0,px(16),0);empty.setTag("icon-library-empty");gallery.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        status=muted(tr("确定  打开替换确认    返回  上一级","OK  Confirm replacement    Back  Previous"),12);status.setTag("icon-library-status");LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,px(26));sp.topMargin=px(6);pageRoot.addView(status,sp);
        IntentFilter changes=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);changes.addAction(Intent.ACTION_PACKAGE_REMOVED);changes.addAction(Intent.ACTION_PACKAGE_CHANGED);changes.addDataScheme("package");registerReceiver(packages,changes);
        updateSelectors();enabled();
    }
    private String valid(String value,String[] choices){return IconLibraryCatalog.contains(choices,value)?value:choices[0];}
    private Button control(LinearLayout parent,String title,String tag,boolean primary,Runnable action){
        Button b=button(title,action);b.setTag(tag);b.setId(androidx.core.view.ViewCompat.generateViewId());b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,13*unit);b.setPadding(px(10),0,px(10),0);b.setTypeface(activity.navigationTypeface(primary));b.setTextColor(primary?new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{UiPalette.FAINT,0xff151a20}):SettingsFocusDrawable.textColors(UiPalette.TEXT));b.setBackgroundDrawable(new LibraryControlDrawable(primary));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(parent.getChildCount()>0)p.leftMargin=px(10);parent.addView(b,p);wireToolbar(b);
        b.setOnFocusChangeListener((v,focused)->{if(focused&&("icons-batch".equals(tag)||"icons-upload".equals(tag)))status.setText("icons-batch".equals(tag)?tr("确定  一键匹配并预览    返回  上一级","OK  Match and review    Back  Previous"):tr("确定  扫码上传    返回  上一级","OK  Scan to upload    Back  Previous"));});return b;
    }
    private void controlGlyph(Button button,String name){android.graphics.drawable.Drawable glyph=androidx.core.graphics.drawable.DrawableCompat.wrap(new QuickControlIcon(name)).mutate();androidx.core.graphics.drawable.DrawableCompat.setTintList(glyph,button.getTextColors());glyph.setBounds(0,0,px(12),px(12));button.setCompoundDrawables(glyph,null,null,null);button.setCompoundDrawablePadding(px(6));button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int inset=Math.max(px(6),Math.round((r-l-glyph.getBounds().width()-button.getCompoundDrawablePadding()-button.getPaint().measureText(button.getText().toString()))/2));if(button.getPaddingLeft()!=inset)button.setPadding(inset,0,inset,0);});}
    private void selector(LinearLayout parent,String tag,Runnable action){Button b=control(parent,"",tag,false,action);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);selectorArrow(b,false);selectors.add(b);}
    private final class LibraryControlDrawable extends android.graphics.drawable.Drawable {
        private final boolean primary;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF area=new RectF();private Shader gradient;
        LibraryControlDrawable(boolean primary){this.primary=primary;}
        private boolean state(int attr){for(int value:getState())if(value==attr)return true;return false;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] values){invalidateSelf();return true;}
        @Override protected void onBoundsChange(Rect bounds){gradient=new LinearGradient(0,bounds.top,0,bounds.bottom,0xfff1f8ff,0xffd2e8f9,Shader.TileMode.CLAMP);}
        @Override public void draw(Canvas canvas){
            boolean focused=state(android.R.attr.state_enabled)&&state(android.R.attr.state_focused)&&state(android.R.attr.state_window_focused);area.set(getBounds());float radius=px(12);
            paint.setStyle(Paint.Style.FILL);paint.setShader(primary&&state(android.R.attr.state_enabled)&&!focused?gradient:null);paint.setColor(focused?UiPalette.TEXT:primary?0xffe3f1fc:UiPalette.CONTROL);SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(primary&&focused?1:.5f));paint.setColor(primary?(focused?0xff9bd8ff:0x40ffffff):UiPalette.EDGE_STRONG);area.inset(paint.getStrokeWidth()/2,paint.getStrokeWidth()/2);SurfaceCorners.draw(canvas,area,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private void wireToolbar(Button button){
        int position=toolbarControls.size();toolbarControls.add(button);button.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){focusLibrary();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP){managementTab().requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int step=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;for(int i=position+step;i>=0&&i<toolbarControls.size();i+=step)if(toolbarControls.get(i).isEnabled()){toolbarControls.get(i).requestFocus();break;}return true;}
            return false;
        });
    }
    private void focusToolbar(View from){
        int[] location=new int[2];from.getLocationOnScreen(location);int center=location[0]+from.getWidth()/2,distance=Integer.MAX_VALUE;Button nearest=selectors.get(0);
        for(Button button:toolbarControls)if(button.isEnabled()){button.getLocationOnScreen(location);int delta=Math.abs(center-location[0]-button.getWidth()/2);if(delta<distance){distance=delta;nearest=button;}}nearest.requestFocus();
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
        int run=++loading;worker.execute(()->{try{IconLibraryCatalog loaded=null;String catalogError=null;try{loaded=IconLibraryCatalog.read(artworkContext);}catch(IOException|RuntimeException failure){catalogError=failure.getMessage();}final IconLibraryCatalog next=loaded;final String artworkError=catalogError;ArrayList<Target> apps=new ArrayList<>();for(Map.Entry<String,ResolveInfo> entry:new HomeAppCatalog(activity).launchable().entrySet())if(!AppCoverPolicy.ownReadOnly(activity,entry.getKey())&&entry.getValue().activityInfo.applicationInfo.enabled&&AppAvailability.installed(getPackageManager(),entry.getValue().activityInfo.applicationInfo))apps.add(new Target(entry.getKey(),entry.getValue().loadLabel(getPackageManager()).toString(),entry.getValue()));Collections.sort(apps,(a,b)->java.text.Collator.getInstance().compare(a.label,b.label));List<String> dock=new DockStore(activity).load();
            ui(()->{if(run!=loading||!showing)return;catalog=next;targets.clear();targets.addAll(apps);if(target()==null){pkg="";for(String id:dock)for(Target app:targets)if(pkg.isEmpty()&&id.equals(app.pkg))pkg=id;if(pkg.isEmpty()&&!targets.isEmpty())pkg=targets.get(0).pkg;}updateSelectors();filter();if(scroll>0)library.scrollToPosition(Math.min(scroll,Math.max(0,items.size()-1)));if(artworkError!=null)status.setText(tr("图标素材无法读取，仍可扫码上传","Artwork unavailable; phone upload is available"));refreshCurrent();});
        }catch(RuntimeException failure){ui(()->{if(run!=loading)return;empty.setText(tr("图标库读取失败","Unable to load icon library"));empty.setVisibility(View.VISIBLE);adapter.notifyDataSetChanged();status.setText(failure.getMessage());enabled();});}});
    }
    private Target target(){for(Target target:targets)if(target.pkg.equals(pkg))return target;return null;}
    private void updateSelectors(){form=valid(form,IconLibraryCatalog.forms(shape));Target target=target();selectors.get(0).setText(target==null?tr("选择应用","Choose app"):target.label);selectors.get(0).setContentDescription(tr("目标应用：","Target app: ")+selectors.get(0).getText());String[] values={style,shape,form};String[][] options={IconLibraryCatalog.STYLES,IconLibraryCatalog.SHAPES,IconLibraryCatalog.FORMS},titles=activity.chinese()?new String[][]{STYLE_ZH,SHAPE_ZH,FORM_ZH}:new String[][]{STYLE_EN,SHAPE_EN,FORM_EN};for(int i=0;i<3;i++)selectors.get(i+1).setText(titles[i][Arrays.asList(options[i]).indexOf(values[i])]);}
    @android.annotation.SuppressLint("NotifyDataSetChanged") private void filter(){
        cancelArtwork();++thumbnails;items=catalog==null?Collections.emptyList():catalog.style(style,shape,form);adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);empty.setText(catalog!=null&&catalog.items.isEmpty()?tr("图标素材待接入","Icon artwork will be added in the next stage"):tr("此组合暂无图标","No artwork for these options"));
        IconLibraryCatalog.Item chosen=null;for(IconLibraryCatalog.Item item:items)if(item.id.equals(theme))chosen=item;if(chosen==null&&!items.isEmpty())chosen=items.get(0);if(chosen!=null)select(chosen);else theme="";enabled();
    }
    private void upload(){
        Target target=target();if(busy||target==null)return;if(upload!=null)upload.destroy();upload=new IconUploadSession(activity,pkg,target.label,shape,()->{uploaded();feedback(tr("已替换「"+target.label+"」图标","Icon replaced for "+target.label));});upload.show();
    }
    private void uploaded(){if(destroyed||!showing)return;refreshCurrent();status.setText(tr("已替换图标","Icon replaced"));}
    private void cancelArtwork(){for(IconLibraryTransfer.Request request:artworkRequests)request.cancel();artworkRequests.clear();}
    private void select(IconLibraryCatalog.Item item){theme=item.id;galleryFocusTag="icon-item-"+item.id;status.setText(tr("确定  打开替换确认    返回  上一级","OK  Confirm replacement    Back  Previous"));}
    private void refreshCurrentIfChanged(){if(showing)refreshCurrent();}
    private void refreshCurrent(){enabled();}
    private void enabled(){if(restore==null)return;boolean focused=restore.isFocused();restore.setEnabled(!busy&&target()!=null&&covers.custom(pkg));for(Button selector:selectors)selector.setEnabled(!busy);batchAction.setEnabled(!busy);uploadAction.setEnabled(!busy&&target()!=null);library.setEnabled(!busy);if(focused&&!restore.isEnabled()&&showing)focusLibrary();}
    private void pickApps(){if(targets.isEmpty()){status.setText(tr("没有可替换图标的应用","No eligible apps"));return;}String[] labels=new String[targets.size()];int current=0;for(int i=0;i<targets.size();i++){labels[i]=targets.get(i).label;if(targets.get(i).pkg.equals(pkg))current=i;}menu(selectors.get(0),labels,current,index->{pkg=targets.get(index).pkg;scroll=0;updateSelectors();filter();library.scrollToPosition(0);refreshCurrent();});}
    private interface Pick {void select(int index);}
    private void pickForms(){String[] labels=activity.chinese()?FORM_ZH:FORM_EN;pick(3,IconLibraryCatalog.forms(shape),"rectangle".equals(shape)?labels:Arrays.copyOfRange(labels,1,labels.length));}
    private void pick(int field,String[] values,String[] labels){String value=field==1?style:field==2?shape:form;menu(selectors.get(field),labels,Arrays.asList(values).indexOf(value),index->{if(field==1)style=values[index];else if(field==2)shape=values[index];else form=values[index];updateSelectors();scroll=0;filter();library.scrollToPosition(0);});}
    private void menu(Button origin,String[] labels,int current,Pick picked){
        if(popup!=null)popup.dismiss();LinearLayout rows=new KeyFeedback.Menu(activity);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(px(5),px(5),px(5),px(5));ScrollView scroll=activity.settingsScroll();scroll.addView(rows);android.text.TextPaint optionPaint=new android.text.TextPaint(origin.getPaint());optionPaint.setTextSize(12*unit);optionPaint.setTypeface(activity.navigationTypeface(false));int menuWidth=Math.max(origin.getWidth(),px(150));for(String label:labels)menuWidth=Math.max(menuWidth,(int)Math.ceil(android.text.Layout.getDesiredWidth(SettingsSelectionMark.label(label,true,unit),optionPaint))+px(34));menuWidth=Math.min(menuWidth,getResources().getDisplayMetrics().widthPixels-px(24));float textWidth=Math.max(0,menuWidth-px(34)-px(15.36f));PopupWindow menu=new PopupWindow(scroll,menuWidth,Math.min(px(232),px(labels.length*32+10)),true);popup=menu;menu.setBackgroundDrawable(surface(0xfa242426,0xfa19191b,UiPalette.EDGE_STRONG,12));menu.setOutsideTouchable(true);if(Build.VERSION.SDK_INT>=21)menu.setElevation(px(6));Button selected=null;
        for(int i=0;i<labels.length;i++){
            int index=i;Button option=button(labels[i],()->{picked.select(index);menu.dismiss();});option.setTag("icons-menu-"+selectors.indexOf(origin)+"-"+i);option.setText(SettingsSelectionMark.label(android.text.TextUtils.ellipsize(labels[i],optionPaint,textWidth,android.text.TextUtils.TruncateAt.END).toString(),i==current,unit));option.setContentDescription(labels[i]+(i==current?tr("  已选择","  Selected"):""));option.setGravity(Gravity.CENTER);option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,12*unit);option.setPadding(px(12),0,px(12),0);option.setSingleLine(true);option.setEllipsize(android.text.TextUtils.TruncateAt.END);option.setTypeface(activity.navigationTypeface(false));option.setSelected(i==current);
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_enabled,android.R.attr.state_focused,android.R.attr.state_window_focused},surface(UiPalette.TEXT,UiPalette.TEXT,0,6));background.addState(new int[]{android.R.attr.state_enabled,android.R.attr.state_pressed},surface(UiPalette.SELECTED,UiPalette.SELECTED,0,6));background.addState(new int[0],new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            option.setBackgroundDrawable(background);rows.addView(option,new LinearLayout.LayoutParams(-1,px(32)));if(i==current)selected=option;
        }
        menu.setOnDismissListener(()->{if(popup==menu)popup=null;selectorArrow(origin,false);if(showing)origin.requestFocus();});selectorArrow(origin,true);menu.showAsDropDown(origin,0,px(4));if(selected!=null)selected.requestFocus();
    }
    private IconLibraryCatalog.Item item(){for(IconLibraryCatalog.Item item:items)if(item.id.equals(theme))return item;return null;}
    private void apply(){
        IconLibraryCatalog.Item chosen=item();Target target=target();if(busy||replacement!=null||restoreSheet!=null||chosen==null||target==null)return;
        replacement=new IconLibraryReplaceSheet(activity,artworkContext,pkg,target.label,target.info,chosen,shape,form,catalog.revision,this::refreshCurrent,()->{replacement=null;if(showing){refreshCurrent();focusLibrary();reloadIfPending();}});replacement.show();
    }
    private void restore(){
        Target target=target();if(busy||replacement!=null||restoreSheet!=null||target==null||!covers.custom(pkg))return;String id=pkg,expected=covers.key(pkg),label=target.label;
        ManagementUi.Sheet confirm=activity.new Sheet(tr("恢复默认图标","Restore default icon"));restoreSheet=confirm;Button close=confirm.settingsLayout(tr("关闭","Close"),()->{if(!busy)confirm.dialog.dismiss();});confirm.box.setTag("icon-restore-sheet");confirm.text(tr("恢复「"+label+"」的原始图标或横幅？","Restore the original icon or banner for "+label+"?"));
        Button cancel=confirm.action(tr("取消","Cancel"),()->confirm.dialog.dismiss());cancel.setTag("icon-restore-cancel");cancel.setId(androidx.core.view.ViewCompat.generateViewId());
        Button accept=confirm.action(tr("恢复默认","Restore default"),()->{
            if(busy)return;busy=true;enabled();confirm.dialog.setCancelable(false);confirm.box.setEnabled(false);close.setEnabled(false);cancel.setEnabled(false);for(int i=0;i<confirm.actions.getChildCount();i++)confirm.actions.getChildAt(i).setEnabled(false);confirm.text(tr("正在恢复默认图标…","Restoring default icon…"));
            worker.execute(()->{String error=null;try{AppCoverPolicy.require(activity,id);synchronized(covers){if(!expected.equals(covers.key(id)))throw new IOException(tr("当前图标已变化，请重新确认","Icon changed; confirm again"));covers.restore(id);}}catch(IOException|RuntimeException failure){error=failure.getMessage();if(error==null)error=tr("恢复默认失败","Unable to restore default icon");}String failure=error;ui(()->{
                busy=false;if(failure==null){PhoneSettingsSession.get(activity).configurationChanged();confirm.dialog.dismiss();if(showing)feedback(tr("已恢复「"+label+"」默认图标","Default icon restored for "+label));}
                else if(restoreSheet==confirm){confirm.dialog.setCancelable(true);confirm.box.setEnabled(true);close.setEnabled(true);cancel.setEnabled(true);confirm.text(failure);cancel.requestFocus();}refreshCurrent();
            });});
        });accept.setTag("icon-restore-confirm");accept.setId(androidx.core.view.ViewCompat.generateViewId());cancel.setNextFocusRightId(accept.getId());accept.setNextFocusLeftId(cancel.getId());cancel.setNextFocusLeftId(cancel.getId());accept.setNextFocusRightId(accept.getId());close.setNextFocusDownId(cancel.getId());cancel.setNextFocusUpId(close.getId());accept.setNextFocusUpId(close.getId());
        confirm.dialog.setOnDismissListener(d->{if(restoreSheet==confirm)restoreSheet=null;if(showing){refreshCurrent();if(restore.isEnabled())restore.requestFocus();else focusLibrary();reloadIfPending();}});confirm.show(cancel);
    }
    private void reloadIfPending(){if(reloadPending&&showing&&!busy&&replacement==null&&restoreSheet==null){reloadPending=false;load();}}
    private void focusLibrary(){focusLibraryPosition(Math.max(0,adapter.position(galleryFocusTag)));}
    private void focusLibraryPosition(int position){if(items.isEmpty())return;position=Math.min(position,items.size()-1);RecyclerView.ViewHolder visible=library.findViewHolderForAdapterPosition(position);if(visible!=null){visible.itemView.requestFocus();return;}int requested=position;library.scrollToPosition(requested);library.post(()->{if(destroyed||!showing)return;RecyclerView.ViewHolder holder=library.findViewHolderForAdapterPosition(requested);if(holder!=null)holder.itemView.requestFocus();});}
    private void batch(){if(busy)return;if(batch!=null)batch.close();batch=new IconLibraryBatchSheet(activity,artworkContext,style,shape,form,this::uploaded);batch.show();}
    @Override void focusManagementContent(){View target=pageRoot.findViewWithTag(focusTag);if(target!=null&&target.isShown()&&target.isEnabled())target.requestFocus();else focusLibrary();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();return busy&&(key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER);}
    @Override public void handleBack(){if(popup!=null){popup.dismiss();return;}if(busy)return;if(!managementTabFocused())managementTab().requestFocus();else super.handleBack();}
    @Override protected void onResume(){super.onResume();PhoneSettingsSession.get(activity).observeConfiguration(phoneChanged);IconLibraryRepository.get(artworkContext).observe(catalogChanged);load();}
    @Override protected void onPause(){remember();super.onPause();if(replacement!=null)replacement.destroy();if(restoreSheet!=null)restoreSheet.dialog.dismiss();if(batch!=null)batch.close();if(upload!=null)upload.close();cancelArtwork();IconLibraryRepository.get(artworkContext).forget(catalogChanged);++loading;++thumbnails;if(popup!=null)popup.dismiss();PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);}
    @Override protected void onSaveInstanceState(Bundle saved){remember();saved.putString("package",pkg);saved.putString("theme",theme);saved.putString("style",style);saved.putString("shape",shape);saved.putString("form",form);saved.putString("focus",focusTag);saved.putInt("scroll",scroll);}
    private void remember(){View focus=getCurrentFocus();if(focus!=null&&pageRoot.hasFocus()&&focus.getTag() instanceof String)focusTag=(String)focus.getTag();scroll=Math.max(0,((GridLayoutManager)library.getLayoutManager()).findFirstVisibleItemPosition());}
    @Override protected void onDestroy(){destroyed=true;if(replacement!=null)replacement.destroy();if(restoreSheet!=null)restoreSheet.dialog.dismiss();if(batch!=null)batch.destroy();if(upload!=null)upload.destroy();cancelArtwork();IconLibraryRepository.get(artworkContext).forget(catalogChanged);++loading;++thumbnails;PhoneSettingsSession.get(activity).forgetConfiguration(phoneChanged);unregisterReceiver(packages);if(popup!=null)popup.dismiss();library.setAdapter(null);thumbs.evictAll();worker.shutdown();thumbnailWorkers.shutdown();}
    private final class Tile extends FrameLayout {
        IconLibraryTransfer.Request request;final ImageView image;final TextView name;private final Paint focusPaint=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF focusInset=new RectF();
        Tile(){super(activity);setFocusable(true);setFocusableInTouchMode(true);setClipChildren(false);image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);addView(image);name=center("",14);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(name);SettingsTouchClick.attach(this);setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN||!(getTag() instanceof String))return false;int position=adapter.position((String)getTag());if(position<0)return false;
            if(key==KeyEvent.KEYCODE_DPAD_UP){if(position<COLUMNS)focusToolbar(this);else focusLibraryPosition(position-COLUMNS);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){if((position/COLUMNS+1)*COLUMNS<items.size())focusLibraryPosition(Math.min(position+COLUMNS,items.size()-1));return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(position%COLUMNS>0)focusLibraryPosition(position-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(position%COLUMNS<COLUMNS-1&&position+1<items.size())focusLibraryPosition(position+1);return true;}return false;
        });}
        @Override protected void onFocusChanged(boolean focused,int direction,Rect previous){super.onFocusChanged(focused,direction,previous);name.setTypeface(activity.navigationTypeface(focused));invalidate();}
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);invalidate();}
        @Override protected void dispatchDraw(Canvas canvas){if(isFocused()&&hasWindowFocus()){focusPaint.setColor(0x26ffffff);focusInset.set(0,0,getWidth(),getHeight());SurfaceCorners.draw(canvas,focusInset,px(16),px(16),focusPaint);}super.dispatchDraw(canvas);}
        @Override protected void onMeasure(int width,int height){int w=MeasureSpec.getSize(width),art=Math.max(1,w-px(12)),h=Math.round(art*9/16f);setMeasuredDimension(w,px(122));image.measure(MeasureSpec.makeMeasureSpec(art,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));name.measure(MeasureSpec.makeMeasureSpec(art,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(px(18),MeasureSpec.EXACTLY));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int inset=(getWidth()-image.getMeasuredWidth())/2,top=px(10),label=top+image.getMeasuredHeight()+px(6);image.layout(inset,top,getWidth()-inset,top+image.getMeasuredHeight());name.layout(inset,label,getWidth()-inset,label+name.getMeasuredHeight());}
    }
    private final class GalleryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        int position(String tag){for(int i=0;i<items.size();i++)if(("icon-item-"+items.get(i).id).equals(tag))return i;return -1;}
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){return new RecyclerView.ViewHolder(new Tile()){};}
        @Override public int getItemCount(){return items.size();}
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){
            Tile tile=(Tile)holder.itemView;if(tile.request!=null)tile.request.cancel();tile.setEnabled(!busy&&target()!=null);IconLibraryCatalog.Item item=items.get(position);int available=Math.max(library.getWidth(),library.getMeasuredWidth())-library.getPaddingLeft()-library.getPaddingRight(),artWidth=Math.max(1,(available-px(4)*(COLUMNS-1))/COLUMNS-px(12)),width="rectangle".equals(shape)?Math.min(720,artWidth):Math.min(512,Math.round(artWidth*9/16f));String key=catalog.revision+":"+item.style+":"+item.id+":"+shape+":"+form+":"+width;tile.setTag("icon-item-"+item.id);tile.setContentDescription(item.label(activity.chinese()));tile.name.setText(item.label(activity.chinese()));tile.name.setTypeface(activity.navigationTypeface(tile.isFocused()));tile.name.setVisibility("text".equals(form)?View.INVISIBLE:View.VISIBLE);tile.setOnFocusChangeListener((v,focused)->{if(focused)select(item);});tile.setOnClickListener(v->{if(!theme.equals(item.id))select(item);apply();});tile.image.setTag(key);tile.image.setImageBitmap(thumbs.get(key));tile.requestLayout();
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
