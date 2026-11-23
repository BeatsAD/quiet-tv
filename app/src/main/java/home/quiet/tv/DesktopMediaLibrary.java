package home.quiet.tv;

import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** One inline material library for wallpaper and screensaver candidates. */
final class DesktopMediaLibrary {
    interface Actions {
        String selected();boolean select(String id);void imported(String id,boolean apply)throws IOException;void removed();
    }
    final LinearLayout library;
    private final ManagementUi ui;
    private final WallpaperStore media;
    private final DesktopMediaImportPanel importer;
    private final Actions actions;
    private final Runnable rewired;
    private final String target,context,prefix;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String type="image",removing="";
    private boolean destroyed,removeBusy;
    private static final int QUICK_BATCH=6; // Three complete two-column rows.
    private static final int COLUMNS=2;
    private volatile int generation;
    private final List<Thumbnail> thumbnails=new ArrayList<>();
    private final Rect visibleArea=new Rect();
    private final int[] position=new int[2];
    private boolean thumbnailLoading;
    private ScrollView viewport;
    private ViewTreeObserver thumbnailTree;
    private final Runnable thumbnailWindow=this::updateThumbnailWindow;
    private final ViewTreeObserver.OnScrollChangedListener thumbnailScroll=this::scheduleThumbnailWindow;
    private final ViewTreeObserver.OnGlobalLayoutListener thumbnailLayout=this::scheduleThumbnailWindow;
    private final ViewTreeObserver.OnGlobalFocusChangeListener thumbnailFocus=(oldFocus,newFocus)->scheduleThumbnailWindow();
    private boolean quick,firstDrawReady,resumed;
    private Runnable nextBatch;
    private final Runnable append=()->{
        if(!resumed||!firstDrawReady||!libraryShown()||nextBatch==null)return;
        Runnable batch=nextBatch;nextBatch=null;batch.run();
    };
    private ManagementUi.Sheet removeSheet;
    DesktopMediaLibrary(ManagementUi ui,String target,DesktopMediaImportPanel importer,Actions actions,Runnable rewired){this.ui=ui;this.target=target;context=target.equals("screensaver")?"屏保":"壁纸";prefix=target.equals("screensaver")?"saver-media-":"wall-media-";this.importer=importer;this.actions=actions;this.rewired=rewired;media=WallpaperStore.get(ui);library=ui.column();
        library.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){
                for(ViewParent parent=view.getParent();parent!=null;parent=parent.getParent()){
                    if(parent instanceof ScrollView){
                        viewport=(ScrollView)parent;
                        break;
                    }
                }
                thumbnailTree=view.getViewTreeObserver();
                thumbnailTree.addOnGlobalLayoutListener(thumbnailLayout);
                thumbnailTree.addOnScrollChangedListener(thumbnailScroll);
                thumbnailTree.addOnGlobalFocusChangeListener(thumbnailFocus);
                scheduleThumbnailWindow();
            }
            @Override public void onViewDetachedFromWindow(View view){
                stopWatchingThumbnails();
                for(Thumbnail thumbnail:thumbnails)thumbnail.keep(false);
            }
        });
    }
    private boolean saver(){return target.equals("screensaver");}
    private int p(float value){return ui.px(value*.75f);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private TextView label(String text,float size){TextView view=ui.label(text,12);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(size));view.setTextColor(UiPalette.SECONDARY);return view;}
    void chooseType(String id){WallpaperCatalog.Entry entry=media.snapshot().find(id);type=entry!=null&&entry.video()?"video":"image";}
    void focus(String id){View view=library.findViewWithTag(prefix+id);if(view!=null)view.post(view::requestFocusFromTouch);}
    void save(Bundle state){state.putString(target+"-library-type",type);if(removeSheet!=null)state.putString(target+"-remove",removing);}
    void restore(Bundle state){type=state.getString(target+"-library-type","image");render();String id=state.getString(target+"-remove","");if(!id.isEmpty())confirmRemove(id);}
    void quickEntry(){quick=true;}
    void firstDrawReady(){firstDrawReady=true;scheduleBatch();}
    void resume(){resumed=true;scheduleBatch();scheduleThumbnailWindow();}
    void pause(){resumed=false;library.removeCallbacks(append);}
    private boolean libraryShown(){return library.isShown();}
    void visible(boolean visible){
        library.setVisibility(visible?View.VISIBLE:View.GONE);
        scheduleBatch();
        scheduleThumbnailWindow();
    }
    private void scheduleBatch(){
        library.removeCallbacks(append);
        if(resumed&&firstDrawReady&&nextBatch!=null&&library.isShown())androidx.core.view.ViewCompat.postOnAnimation(library,append);
    }
    void destroy(){destroyed=true;++generation;nextBatch=null;pause();stopWatchingThumbnails();for(Thumbnail thumbnail:thumbnails)thumbnail.keep(false);thumbnails.clear();if(removeSheet!=null){removeSheet.dialog.setOnDismissListener(null);removeSheet.dialog.dismiss();removeSheet=null;}worker.shutdown();}
    private Button smallButton(String text,Runnable action){Button b=ui.button(text,action);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(13.44f));b.setPadding(p(10.24f),p(5.12f),p(10.24f),p(5.12f));b.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,11.52f,UiPalette.CONTROL,false,UiPalette.EDGE));return b;}
    private void changeType(String kind){if(type.equals(kind))return;type=kind;render();View selected=library.findViewWithTag(target+"-library-type-"+kind);if(selected!=null)selected.requestFocusFromTouch();}
    void render(){render(quick);}
    void renderAll(){render(false);}
    private void render(boolean staged){
        int request=++generation;nextBatch=null;library.removeCallbacks(append);thumbnails.clear();library.removeAllViews();library.setPadding(p(10.24f),p(10.24f),p(10.24f),p(10.24f));
        LinearLayout heading=ui.row();for(String kind:new String[]{"image","video"}){Button filter=smallButton(kind.equals("image")?tr("图片","Images"):tr("视频","Videos"),()->changeType(kind));filter.setTag(target+"-library-type-"+kind);filter.setOnFocusChangeListener((view,focused)->{if(focused)view.post(()->{if(view.hasFocus())changeType(kind);});});filter.setSelected(type.equals(kind));LinearLayout.LayoutParams filterPosition=new LinearLayout.LayoutParams(-2,p(33.28f));if(heading.getChildCount()>0)filterPosition.leftMargin=p(8.32f);heading.addView(filter,filterPosition);}View spacer=new View(ui);heading.addView(spacer,new LinearLayout.LayoutParams(0,1,1));heading.addView(smallButton(target.equals("wallpaper")?tr("上传壁纸","Upload wallpaper"):tr("导入素材","Import media"),()->importer.show(target,type,(target,id,apply)->actions.imported(id,apply))),new LinearLayout.LayoutParams(-2,p(33.28f)));library.addView(heading);LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,p(8.32f));library.addView(new View(ui),gap);
        WallpaperCatalog catalog=media.snapshot();
        if("image".equals(type)&&catalog.builtinHidden(WallpaperCatalog.BUILTIN,saver())){
            Button restore=smallButton(tr("恢复默认素材","Restore default media"),this::restoreBuiltin);
            restore.setTag(target+"-restore-built-in");library.addView(restore,new LinearLayout.LayoutParams(-1,p(36)));
        }
        ArrayList<WallpaperCatalog.Entry> entries=new ArrayList<>();for(WallpaperCatalog.Entry entry:catalog.entries())if(!WallpaperCatalog.DAILY.equals(entry.id)&&!WallpaperCatalog.DEFAULT.equals(entry.id)
                &&!catalog.builtinHidden(entry.id,saver())&&entry.video()==type.equals("video"))entries.add(entry);
        LinearLayout information=ui.row();information.setGravity(Gravity.CENTER_VERTICAL);information.setPadding(0,p(7.68f),0,0);TextView info=label("",11.264f);info.setTag(prefix+"info");information.addView(info,new LinearLayout.LayoutParams(0,-2,1));library.addView(information);
        appendCards(request,entries,0,staged?Math.min(QUICK_BATCH,entries.size()):entries.size(),staged);
        refreshSelection();
    }
    private void appendCards(int request,List<WallpaperCatalog.Entry> entries,int start,int end,boolean staged){
        if(request!=generation)return;
        String selected=actions.selected();
        LinearLayout line=null;for(int i=start;i<end;i++){
            WallpaperCatalog.Entry saved=entries.get(i),current=staged&&start>0?media.entry(saved.id):saved;
            if(current==null){render();return;}
            WallpaperCatalog.Entry entry=current;
            if(i%2==0){line=ui.row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);if(i>0)lp.topMargin=p(8.32f);library.addView(line,library.getChildCount()-1,lp);}
            LinearLayout card=ui.column();card.setPadding(p(3.84f),p(3.84f),p(3.84f),p(3.84f));card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);card.setContentDescription(tr(context.equals("屏保")?"应用屏保：":"应用壁纸：",context.equals("屏保")?"Apply screensaver: ":"Apply wallpaper: ")+entry.label(ui.chinese()));card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);card.setTag(prefix+entry.id);card.setId(androidx.core.view.ViewCompat.generateViewId());card.setSelected(entry.id.equals(selected));card.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,7.68f,UiPalette.CONTROL,false,UiPalette.EDGE));SettingsTouchClick.attach(card);
            ImageView art=new ImageView(ui){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.round(View.MeasureSpec.getSize(w)*9f/16),View.MeasureSpec.EXACTLY));}};art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setTag(prefix+"art-"+entry.id);art.setBackgroundColor(0xff000000);card.addView(art,new LinearLayout.LayoutParams(-1,-2));TextView title=label(entry.label(ui.chinese()),13.44f);title.setTag(prefix+"title-"+entry.id);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));title.setDuplicateParentStateEnabled(true);title.setPadding(p(3.2f),p(5.76f),p(3.2f),p(5.76f));if(WallpaperCatalog.BUILTIN.equals(entry.id)){
                LinearLayout caption=ui.row();caption.setDuplicateParentStateEnabled(true);caption.setGravity(Gravity.CENTER_VERTICAL);card.addView(caption,new LinearLayout.LayoutParams(-1,-2));
                caption.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                TextView badge=label(tr("默认","Default"),10);badge.setTextColor(UiPalette.TEXT);badge.setPadding(p(7),p(2),p(7),p(2));badge.setBackgroundDrawable(ui.surface(0xff33363b,0xff33363b,0,20));caption.addView(badge,new LinearLayout.LayoutParams(-2,-2));
            }else card.addView(title,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(i%2==1)cp.leftMargin=p(8.32f);line.addView(card,cp);
            card.setOnClickListener(v->{boolean accepted=actions.select(entry.id);refreshSelection();if(accepted)KeyFeedback.confirm(ui);});if(entry.custom()||WallpaperCatalog.BUILTIN.equals(entry.id))card.setOnLongClickListener(v->{confirmRemove(entry.id);return true;});
            thumbnails.add(new Thumbnail(entry,art));
        }
        if(end==entries.size()&&end%2==1){View filler=new View(ui);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,1,1);lp.leftMargin=p(8.32f);line.addView(filler,lp);}
        rewired.run();
        scheduleThumbnailWindow();
        if(staged){
            if(start==0)quickCards("phase=initial generation="+request+" count="+end+" total="+entries.size());
            if(end==entries.size())quickCards("phase=complete generation="+request+" count="+end);
        }
        if(end<entries.size()){
            nextBatch=()->appendCards(request,entries,end,Math.min(end+QUICK_BATCH,entries.size()),true);
            scheduleBatch();
        }
    }
    private static void quickCards(String detail){
        DiagnosticLog.info("wallpaper.quick_cards",detail);
        if(BuildConfig.INDEPENDENT_BUILD)android.util.Log.i("QuietTVPerf","wallpaper.quick_cards "+detail);
    }
    void refreshSelection(){String id=actions.selected();markSelection(library,id);TextView info=library.findViewWithTag(prefix+"info");WallpaperCatalog.Entry entry=media.snapshot().find(id);if(info!=null){((View)info.getParent()).setVisibility(entry==null||WallpaperCatalog.DEFAULT.equals(entry.id)?View.GONE:View.VISIBLE);info.setText(entry==null||WallpaperCatalog.DEFAULT.equals(entry.id)?"":(entry.video()?tr("视频","Video"):tr("图片","Image"))+"  "+(entry.width>0?entry.width+" × "+entry.height+"  ":"")+android.text.format.Formatter.formatShortFileSize(ui,entry.bytes)+(entry.video()&&entry.durationMs>0?"  "+String.format(Locale.ROOT,"%d:%02d",entry.durationMs/60000,entry.durationMs/1000%60):""));}}
    void refreshNames(){
        for(WallpaperCatalog.Entry entry:media.snapshot().custom){TextView title=library.findViewWithTag(prefix+"title-"+entry.id);if(title!=null)title.setText(entry.label(ui.chinese()));View card=library.findViewWithTag(prefix+entry.id);if(card!=null)card.setContentDescription(tr(context.equals("屏保")?"应用屏保：":"应用壁纸：",context.equals("屏保")?"Apply screensaver: ":"Apply wallpaper: ")+entry.label(ui.chinese()));}
    }
    void refreshThumbnails(){
        for(Thumbnail thumbnail:thumbnails){
            if(!thumbnail.entry.video())continue;
            String key=media.imageKey(thumbnail.entry.id);
            if(!key.equals(thumbnail.key)){
                thumbnail.key=key;
                thumbnail.requested=false;
            }
        }
        scheduleThumbnailWindow();
    }
    private void scheduleThumbnailWindow(){
        library.removeCallbacks(thumbnailWindow);
        if(!destroyed&&androidx.core.view.ViewCompat.isAttachedToWindow(library))
            androidx.core.view.ViewCompat.postOnAnimation(library,thumbnailWindow);
    }
    private void stopWatchingThumbnails(){
        library.removeCallbacks(thumbnailWindow);
        if(thumbnailTree!=null&&thumbnailTree.isAlive()){
            if(android.os.Build.VERSION.SDK_INT>=16)thumbnailTree.removeOnGlobalLayoutListener(thumbnailLayout);
            else thumbnailTree.removeGlobalOnLayoutListener(thumbnailLayout);
            thumbnailTree.removeOnScrollChangedListener(thumbnailScroll);
            thumbnailTree.removeOnGlobalFocusChangeListener(thumbnailFocus);
        }
        thumbnailTree=null;
        viewport=null;
    }
    private void updateThumbnailWindow(){
        if(destroyed)return;
        int first=-1,last=-1;
        boolean shown=library.isShown()&&library.getWindowVisibility()==View.VISIBLE;
        if(shown&&(viewport==null||viewport.getLocalVisibleRect(visibleArea))){
            int top=0,bottom=0;
            if(viewport!=null){
                viewport.getLocationInWindow(position);
                top=position[1]+visibleArea.top;
                bottom=position[1]+visibleArea.bottom;
            }
            for(int i=0;i<thumbnails.size();i++){
                ImageView art=thumbnails.get(i).art;
                if(viewport!=null){
                    art.getLocationInWindow(position);
                    if(art.getHeight()==0||position[1]>=bottom||position[1]+art.getHeight()<=top)continue;
                }else if(!art.getLocalVisibleRect(visibleArea))continue;
                if(first<0)first=i;
                last=i;
            }
        }
        // Keep one row on each side without removing focusable cards.
        int start=first<0?0:Math.max(0,first/COLUMNS*COLUMNS-COLUMNS);
        int end=last<0?0:(last/COLUMNS+2)*COLUMNS;
        for(int i=0;i<thumbnails.size();i++){
            Thumbnail thumbnail=thumbnails.get(i);
            thumbnail.keep(shown&&(i>=start&&i<end||thumbnail.focused()));
        }
        loadNextThumbnail();
    }
    private void loadNextThumbnail(){
        if(destroyed||thumbnailLoading)return;
        Thumbnail next=null;
        for(Thumbnail thumbnail:thumbnails){
            if(!thumbnail.wanted||thumbnail.requested)continue;
            if(thumbnail.focused()){
                next=thumbnail;
                break;
            }
            if(next==null)next=thumbnail;
        }
        if(next!=null)loadThumbnail(next);
    }
    private void loadThumbnail(Thumbnail thumbnail){
        thumbnailLoading=true;
        thumbnail.requested=true;
        int request=generation;
        String key=thumbnail.key;
        worker.execute(()->{
            Bitmap decoded=null;
            if(request==generation){
                try{decoded=WallpaperImages.decode(ui,media,thumbnail.entry,320,180);}
                catch(IOException|RuntimeException|OutOfMemoryError ignored){}
            }
            Bitmap ready=decoded;
            ui.runOnUiThread(()->{
                thumbnailLoading=false;
                if(!destroyed&&request==generation&&thumbnail.wanted&&key.equals(thumbnail.key)){
                    thumbnail.requested=true;
                    thumbnail.art.setImageBitmap(ready);
                }else if(ready!=null)ready.recycle();
                loadNextThumbnail();
            });
        });
    }
    private final class Thumbnail {
        final WallpaperCatalog.Entry entry;
        final ImageView art;
        String key;
        boolean wanted,requested;
        Thumbnail(WallpaperCatalog.Entry entry,ImageView art){
            this.entry=entry;
            this.art=art;
            key=entry.video()?media.imageKey(entry.id):entry.id;
        }
        boolean focused(){return ((View)art.getParent()).isFocused();}
        void keep(boolean visible){
            if(wanted==visible)return;
            wanted=visible;
            if(!visible){
                requested=false;
                art.setImageDrawable(null);
            }
        }
    }
    private void restoreBuiltin(){
        View button=library.findViewWithTag(target+"-restore-built-in");if(button!=null)button.setEnabled(false);
        worker.execute(()->{String failure="";try{media.builtinVisible(saver(),true);}catch(IOException problem){failure=tr("无法恢复默认素材","Unable to restore default media");}
            String message=failure;ui.runOnUiThread(()->{if(destroyed)return;if(!message.isEmpty()){if(button!=null)button.setEnabled(true);ui.error(message);return;}
                PhoneSettingsSession.get(ui).configurationChanged();render();focus(WallpaperCatalog.BUILTIN);ui.success(saver()?tr("默认素材已恢复，可沿用此前选择","Default media restored; previous selection is available"):tr("默认素材已恢复","Default media restored"));
            });
        });
    }
    private void confirmRemove(String id){
        WallpaperCatalog.Entry entry=media.snapshot().find(id);boolean builtin=WallpaperCatalog.BUILTIN.equals(id);if(removeSheet!=null||entry==null||!entry.custom()&&!builtin)return;removing=id;removeBusy=false;removeSheet=ui.new Sheet(target.equals("wallpaper")?tr("删除壁纸","Delete wallpaper"):tr("删除素材","Delete media"));ManagementUi.Sheet current=removeSheet;current.confirmationLayout(()->{if(!removeBusy)current.dialog.dismiss();});current.settingsActions(true);current.body.addView(label(tr("从素材库移除“","Remove “")+entry.label(ui.chinese())+tr("”？原始文件不受影响。","” from the library? The original file is retained."),17.92f));current.body.addView(label(builtin?tr("只从当前素材库移除，可恢复。正在使用时会改用其他图片；没有图片时使用备用背景。","Only removes it from this library and can be undone. If in use, another image or a fallback background is used."):tr("剩余不足两个时自动关闭轮换，间隔设置保留。","Rotation stops with fewer than two items; the interval is retained."),15.36f));TextView error=label("",15.36f);error.setTextColor(UiPalette.TEXT);error.setVisibility(View.GONE);current.body.addView(error);Button cancel=current.action(tr("取消","Cancel"),()->{if(!removeBusy)current.dialog.dismiss();});Button remove=current.action(tr("删除","Delete"),()->{if(removeBusy)return;removeBusy=true;cancel.setEnabled(false);for(int i=0;i<current.actions.getChildCount();i++)current.actions.getChildAt(i).setEnabled(false);worker.execute(()->{String failure="";try{if(builtin)media.builtinVisible(saver(),false);else media.remove(id);}catch(IOException problem){failure=problem.getMessage();}String message=failure.isEmpty()?"":tr("素材删除失败，请重试","Unable to delete media. Retry.");ui.runOnUiThread(()->{if(destroyed||removeSheet!=current)return;removeBusy=false;if(!message.isEmpty()){error.setText(message);error.setVisibility(View.VISIBLE);for(int i=0;i<current.actions.getChildCount();i++)current.actions.getChildAt(i).setEnabled(true);return;}current.dialog.dismiss();if(builtin)PhoneSettingsSession.get(ui).configurationChanged();actions.removed();render();ui.success(target.equals("wallpaper")?tr("壁纸已删除","Wallpaper deleted"):tr("素材已删除","Media deleted"));});});});
        current.dialog.setOnKeyListener((dialog,key,event)->KeyFeedback.backKey(ui,event,!removeBusy,current.dialog::dismiss));current.dialog.setOnDismissListener(d->{if(removeSheet==current){removeSheet=null;removing="";removeBusy=false;}});current.show(cancel);
    }
    private void markSelection(View view,String id){if(view.isFocusable()&&view.getTag() instanceof String&&((String)view.getTag()).startsWith(prefix))view.setSelected(view.getTag().equals(prefix+id));if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)markSelection(((android.view.ViewGroup)view).getChildAt(i),id);}
}
