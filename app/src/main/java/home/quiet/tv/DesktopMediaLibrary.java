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
        String selected();void select(String id);void imported(String id,boolean apply)throws IOException;void removed();
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
    private int generation;
    private Button removeMedia;
    private ManagementUi.Sheet removeSheet;
    DesktopMediaLibrary(ManagementUi ui,String target,DesktopMediaImportPanel importer,Actions actions,Runnable rewired){this.ui=ui;this.target=target;context=target.equals("screensaver")?"屏保":"壁纸";prefix=target.equals("screensaver")?"saver-media-":"wall-media-";this.importer=importer;this.actions=actions;this.rewired=rewired;media=WallpaperStore.get(ui);library=ui.column();}
    private int p(float value){return ui.px(value*.75f);}
    private String tr(String zh,String en){return ui.tr(zh,en);}
    private TextView label(String text,float size){TextView view=ui.label(text,12);view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(size));view.setTextColor(UiPalette.SECONDARY);return view;}
    void chooseType(String id){WallpaperCatalog.Entry entry=media.snapshot().find(id);type=entry!=null&&entry.video()?"video":"image";}
    void focus(String id){View view=library.findViewWithTag(prefix+id);if(view!=null)view.post(view::requestFocusFromTouch);}
    void save(Bundle state){state.putString(target+"-library-type",type);if(removeSheet!=null)state.putString(target+"-remove",removing);}
    void restore(Bundle state){type=state.getString(target+"-library-type","image");render();String id=state.getString(target+"-remove","");if(!id.isEmpty())confirmRemove(id);}
    void destroy(){destroyed=true;++generation;if(removeSheet!=null){removeSheet.dialog.setOnDismissListener(null);removeSheet.dialog.dismiss();removeSheet=null;}worker.shutdown();}
    private Button smallButton(String text,Runnable action){Button b=ui.button(text,action);b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(13.44f));b.setPadding(p(10.24f),p(5.12f),p(10.24f),p(5.12f));b.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,11.52f,UiPalette.CONTROL,false,UiPalette.EDGE));return b;}
    private void changeType(String kind){if(type.equals(kind))return;type=kind;render();View selected=library.findViewWithTag(target+"-library-type-"+kind);if(selected!=null)selected.requestFocusFromTouch();}
    void render(){
        int request=++generation;library.removeAllViews();removeMedia=null;library.setPadding(p(10.24f),p(10.24f),p(10.24f),p(10.24f));
        LinearLayout heading=ui.row();for(String kind:new String[]{"image","video"}){Button filter=smallButton(kind.equals("image")?tr("图片","Images"):tr("视频","Videos"),()->changeType(kind));filter.setTag(target+"-library-type-"+kind);filter.setOnFocusChangeListener((view,focused)->{if(focused)view.post(()->{if(view.hasFocus())changeType(kind);});});filter.setSelected(type.equals(kind));LinearLayout.LayoutParams filterPosition=new LinearLayout.LayoutParams(-2,p(33.28f));if(heading.getChildCount()>0)filterPosition.leftMargin=p(8.32f);heading.addView(filter,filterPosition);}View spacer=new View(ui);heading.addView(spacer,new LinearLayout.LayoutParams(0,1,1));heading.addView(smallButton(target.equals("wallpaper")?tr("上传壁纸","Upload wallpaper"):tr("导入素材","Import media"),()->importer.show(target,type,(target,id,apply)->actions.imported(id,apply))),new LinearLayout.LayoutParams(-2,p(33.28f)));library.addView(heading);LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,p(8.32f));library.addView(new View(ui),gap);
        WallpaperCatalog catalog=media.snapshot();ArrayList<WallpaperCatalog.Entry> entries=new ArrayList<>();for(WallpaperCatalog.Entry entry:catalog.entries())if(!WallpaperCatalog.DAILY.equals(entry.id)&&entry.video()==type.equals("video"))entries.add(entry);
        LinearLayout line=null;for(int i=0;i<entries.size();i++){
            WallpaperCatalog.Entry entry=entries.get(i);if(i%2==0){line=ui.row();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);if(i>0)lp.topMargin=p(8.32f);library.addView(line,lp);}
            LinearLayout card=ui.column();card.setPadding(p(3.84f),p(3.84f),p(3.84f),p(3.84f));card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);card.setContentDescription(tr(context.equals("屏保")?"预览屏保：":"应用壁纸：",context.equals("屏保")?"Preview screensaver: ":"Apply wallpaper: ")+entry.label(ui.chinese()));card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);card.setTag(prefix+entry.id);card.setId(androidx.core.view.ViewCompat.generateViewId());card.setSelected(entry.id.equals(actions.selected()));card.setBackgroundDrawable(new SettingsFocusDrawable(ui.unit,7.68f,UiPalette.CONTROL,false,UiPalette.EDGE));SettingsTouchClick.attach(card);
            ImageView art=new ImageView(ui){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.round(View.MeasureSpec.getSize(w)*9f/16),View.MeasureSpec.EXACTLY));}};art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setTag(prefix+"art-"+entry.id);art.setBackgroundColor(0xff000000);card.addView(art,new LinearLayout.LayoutParams(-1,-2));TextView title=label(entry.label(ui.chinese()),13.44f);title.setTag(prefix+"title-"+entry.id);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTextColor(SettingsFocusDrawable.textColors(UiPalette.TEXT));title.setDuplicateParentStateEnabled(true);title.setPadding(p(3.2f),p(5.76f),p(3.2f),p(5.76f));card.addView(title,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(i%2==1)cp.leftMargin=p(8.32f);line.addView(card,cp);
            card.setOnClickListener(v->{actions.select(entry.id);refreshSelection();});if(target.equals("wallpaper")&&entry.custom())card.setOnLongClickListener(v->{confirmRemove(entry.id);return true;});worker.execute(()->{Bitmap bitmap=null;try{bitmap=WallpaperCatalog.DEFAULT.equals(entry.id)?WallpaperController.defaultThumbnail():WallpaperImages.decode(ui,media,entry,320,180);}catch(IOException|RuntimeException|OutOfMemoryError ignored){}Bitmap ready=bitmap;ui.runOnUiThread(()->{if(destroyed||request!=generation){if(ready!=null)ready.recycle();return;}art.setImageBitmap(ready);});});
        }
        if(entries.size()%2==1){View filler=new View(ui);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,1,1);lp.leftMargin=p(8.32f);line.addView(filler,lp);}
        LinearLayout information=ui.row();information.setGravity(Gravity.CENTER_VERTICAL);information.setPadding(0,p(7.68f),0,0);TextView info=label("",11.264f);info.setTag(prefix+"info");information.addView(info,new LinearLayout.LayoutParams(0,-2,1));if(target.equals("screensaver")){removeMedia=smallButton(tr("移除素材","Remove media"),()->confirmRemove(actions.selected()));removeMedia.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,p(11.264f));removeMedia.setPadding(p(3.84f),p(3.84f),p(3.84f),p(3.84f));information.addView(removeMedia,new LinearLayout.LayoutParams(-2,-2));}library.addView(information);refreshSelection();rewired.run();
    }
    void refreshSelection(){String id=actions.selected();markSelection(library,id);TextView info=library.findViewWithTag(prefix+"info");WallpaperCatalog.Entry entry=media.snapshot().find(id);if(removeMedia!=null)removeMedia.setVisibility(entry!=null&&entry.custom()?View.VISIBLE:View.GONE);if(info!=null){((View)info.getParent()).setVisibility(entry==null||WallpaperCatalog.DEFAULT.equals(entry.id)?View.GONE:View.VISIBLE);info.setText(entry==null||WallpaperCatalog.DEFAULT.equals(entry.id)?"":(entry.video()?tr("视频","Video"):tr("图片","Image"))+"  "+(entry.width>0?entry.width+" × "+entry.height+"  ":"")+android.text.format.Formatter.formatShortFileSize(ui,entry.bytes)+(entry.video()&&entry.durationMs>0?"  "+String.format(Locale.ROOT,"%d:%02d",entry.durationMs/60000,entry.durationMs/1000%60):""));}}
    void refreshNames(){
        for(WallpaperCatalog.Entry entry:media.snapshot().custom){TextView title=library.findViewWithTag(prefix+"title-"+entry.id);if(title!=null)title.setText(entry.label(ui.chinese()));View card=library.findViewWithTag(prefix+entry.id);if(card!=null)card.setContentDescription(tr(context.equals("屏保")?"预览屏保：":"应用壁纸：",context.equals("屏保")?"Preview screensaver: ":"Apply wallpaper: ")+entry.label(ui.chinese()));}
    }
    void refreshThumbnails(){
        int request=generation;for(WallpaperCatalog.Entry entry:media.snapshot().custom){View view=library.findViewWithTag(prefix+"art-"+entry.id);if(!(view instanceof ImageView)||!entry.video()||!media.poster(entry.id).isFile())continue;ImageView art=(ImageView)view;worker.execute(()->{Bitmap bitmap=null;try{bitmap=WallpaperImages.decode(ui,media,entry,320,180);}catch(IOException|RuntimeException|OutOfMemoryError ignored){}Bitmap ready=bitmap;ui.runOnUiThread(()->{if(destroyed||request!=generation){if(ready!=null)ready.recycle();return;}art.setImageBitmap(ready);});});}
    }
    private void confirmRemove(String id){
        WallpaperCatalog.Entry entry=media.snapshot().find(id);if(removeSheet!=null||entry==null||!entry.custom())return;removing=id;removeBusy=false;removeSheet=ui.new Sheet(target.equals("wallpaper")?tr("删除壁纸","Delete wallpaper"):tr("移除素材","Remove media"));ManagementUi.Sheet current=removeSheet;Button close=current.settingsLayout(tr("关闭","Close"),()->{if(!removeBusy)current.dialog.dismiss();});current.settingsActions(true);current.body.addView(label(tr("从素材库移除“","Remove “")+entry.label(ui.chinese())+tr("”？原始文件不受影响。","” from the library? The original file is retained."),17.92f));current.body.addView(label(tr("剩余不足两个时自动关闭轮换，间隔设置保留。","Rotation stops with fewer than two items; the interval is retained."),15.36f));TextView error=label("",15.36f);error.setTextColor(UiPalette.TEXT);error.setVisibility(View.GONE);current.body.addView(error);Button cancel=current.action(tr("取消","Cancel"),()->{if(!removeBusy)current.dialog.dismiss();});Button remove=current.action(target.equals("wallpaper")?tr("删除","Delete"):tr("移除素材","Remove media"),()->{if(removeBusy)return;removeBusy=true;close.setEnabled(false);cancel.setEnabled(false);for(int i=0;i<current.actions.getChildCount();i++)current.actions.getChildAt(i).setEnabled(false);worker.execute(()->{String failure="";try{media.remove(id);}catch(IOException problem){failure=problem.getMessage();}String message=failure;ui.runOnUiThread(()->{if(destroyed||removeSheet!=current)return;removeBusy=false;if(!message.isEmpty()){error.setText(message);error.setVisibility(View.VISIBLE);close.setEnabled(true);for(int i=0;i<current.actions.getChildCount();i++)current.actions.getChildAt(i).setEnabled(true);return;}current.dialog.dismiss();actions.removed();render();});});});
        current.dialog.setOnKeyListener((dialog,key,event)->{if(key!=KeyEvent.KEYCODE_BACK&&key!=KeyEvent.KEYCODE_ESCAPE)return false;if(!removeBusy&&event.getAction()==KeyEvent.ACTION_UP)current.dialog.dismiss();return true;});current.dialog.setOnDismissListener(d->{if(removeSheet==current){removeSheet=null;removing="";removeBusy=false;}});current.show(cancel);
    }
    private void markSelection(View view,String id){if(view.isFocusable()&&view.getTag() instanceof String&&((String)view.getTag()).startsWith(prefix))view.setSelected(view.getTag().equals(prefix+id));if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)markSelection(((android.view.ViewGroup)view).getChildAt(i),id);}
}
