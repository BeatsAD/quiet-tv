package home.quiet.tv;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

final class DockEditorPanel extends ManagementPanel {
    private DockStore store;
    private ArrayList<String> packages,lastSaved;
    private ArrayList<String> beforeMove;
    private boolean moving;
    private int pendingSaves;
    private Runnable afterSave;
    private int movingIndex;
    private LinearLayout content;
    private TextView description;
    private final ArrayList<View> slots=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final HashMap<String,Bitmap> artwork=new HashMap<>();
    private int artworkGeneration;
    private long coverRevision=-1,childRevision=-1;
    DockEditorPanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new DockStore(this);packages=store.load();lastSaved=new ArrayList<>(packages);
        content=managementContent();description=managementHint("");managementInfo.addView(description,new LinearLayout.LayoutParams(-1,-1));
    }
    @Override protected void onResume(){super.onResume();ArrayList<String> latest=store.load();long covers=AppCoverStore.get(this).revision(),children=new ChildModeStore(this).revision();if(slots.isEmpty()||!latest.equals(packages)||covers!=coverRevision||children!=childRevision){coverRevision=covers;childRevision=children;packages=latest;lastSaved=new ArrayList<>(latest);artworkGeneration++;artwork.clear();render(0);}}
    @Override protected void onPause(){if(moving)cancelMove();super.onPause();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
    @Override void focusManagementContent(){if(!slots.isEmpty())slots.get(0).requestFocus();}
    private ApplicationInfo info(String pkg){try{return getPackageManager().getApplicationInfo(pkg,HomeController.METADATA);}catch(PackageManager.NameNotFoundException e){return null;}}
    private boolean builtIn(String pkg){return HomeAppCatalog.DESKTOP_SETTINGS.equals(pkg)||HomeAppCatalog.WALLPAPER.equals(pkg);}
    private String name(String pkg){if(HomeAppCatalog.DESKTOP_SETTINGS.equals(pkg))return activity.tr("桌面设置","Desktop Settings");if(HomeAppCatalog.WALLPAPER.equals(pkg))return activity.tr("壁纸设置","Wallpaper Settings");ApplicationInfo info=info(pkg);return info==null?pkg:getPackageManager().getApplicationLabel(info).toString();}
    private void loadArtwork(ImageView image,String pkg,ApplicationInfo app){
        Bitmap cached=artwork.get(pkg);if(cached!=null){showArtwork(image,cached);return;}
        int generation=artworkGeneration;
        worker.execute(()->{Bitmap picture=builtIn(pkg)?HomeEntryArtwork.render(this,name(pkg),pkg):AppArtwork.load(this,pkg,app);runOnUiThread(()->{
            if(isDestroyed() || generation!=artworkGeneration)return;
            artwork.put(pkg,picture);showArtwork(image,picture);
        });});
    }
    private void showArtwork(ImageView image,Bitmap picture){
        int color=picture.getPixel(0,0);image.setBackgroundDrawable(surface(color,color,0,10));image.setImageBitmap(picture);
    }
    private void render(int focus){
        content.removeAllViews();slots.clear();List<String> values=packages;
        description.setText(moving?"左右移动  确定放下保存  返回取消":"确定调整顺序或替换应用");
        LinearLayout cards=row();cards.setGravity(Gravity.TOP);cards.setClipChildren(false);content.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        int width=px((864-(DockStore.CAPACITY-1)*20)/(float)DockStore.CAPACITY),height=Math.round(width*203f/360f);
        for(int i=0;i<DockStore.CAPACITY;i++){
            final int slot=i;boolean occupied=i<values.size();String pkg=occupied?values.get(i):"";ApplicationInfo info=occupied?info(pkg):null;
            boolean unavailable=occupied && !builtIn(pkg) && (info==null || !info.enabled || !AppAvailability.installed(getPackageManager(),info));
            LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setClipChildren(false);card.setNextFocusUpId(managementTab().getId());card.setOnClickListener(v->{edit(slot);KeyFeedback.confirm(activity);});card.setContentDescription("位置 "+(i+1)+"  "+(occupied?name(pkg):"添加常用应用")+(moving && slot==movingIndex?"  正在移动":""));slots.add(card);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)cp.leftMargin=px(20);cards.addView(card,cp);
            TextView number=muted(String.format(Locale.ROOT,"%02d",i+1),10);card.addView(number);gap(card,10);
            FrameLayout art=new FrameLayout(this);art.setDuplicateParentStateEnabled(true);art.setBackgroundDrawable(focusSurface(12));art.setPadding(px(3),px(3),px(3),px(3));card.addView(art,new LinearLayout.LayoutParams(-1,height));
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if(occupied){image.setBackgroundDrawable(surface(AppArtwork.NEUTRAL,AppArtwork.NEUTRAL,0,10));UiCompat.clipOutline(image);image.setScaleType(ImageView.ScaleType.FIT_CENTER);art.addView(image,new FrameLayout.LayoutParams(-1,-1));loadArtwork(image,pkg,info);}
            else{image.setImageDrawable(UiCompat.drawable(this,R.drawable.ic_manage_plus));art.addView(image,new FrameLayout.LayoutParams(px(28),px(28),Gravity.CENTER));}
            if(unavailable || occupied && new ChildModeStore(this).hidden(pkg)){ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);image.setColorFilter(new ColorMatrixColorFilter(matrix));image.setAlpha(.45f);}
            gap(card,16);TextView title=center(occupied?name(pkg):"添加常用应用",13);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);if(!occupied)title.setTextColor(MUTED);card.addView(title,new LinearLayout.LayoutParams(-1,px(20)));
            gap(card,5);TextView note=center(occupied && new ChildModeStore(this).hidden(pkg)?"已隐藏  保留位置":unavailable?info!=null && info.enabled?"未安装  保留位置":"已禁用  保留位置":"",10);note.setTextColor(MUTED);card.addView(note,new LinearLayout.LayoutParams(-1,px(16)));
        }
        if(moving || !managementTabFocused())slots.get(Math.max(0,Math.min(focus,DockStore.CAPACITY-1))).requestFocusFromTouch();
    }
    private void edit(int index){
        if(pendingSaves>0)return;if(moving){endMove();return;}if(index>=packages.size()){choose(-1);return;}
        Sheet sheet=new Sheet(name(packages.get(index)));Button replace=sheet.option("替换应用",()->{sheet.dialog.dismiss();choose(index);});sheet.option("移除快捷入口",()->{sheet.dialog.dismiss();packages.remove(index);render(Math.min(index,DockStore.CAPACITY-1));saveOrder();});sheet.option("左右排序",()->{sheet.dialog.dismiss();moving=true;beforeMove=new ArrayList<>(packages);movingIndex=index;render(index);});sheet.action("取消",sheet.dialog::dismiss);sheet.show(replace);
    }
    private void choose(int replace){
        PackageManager pm=getPackageManager();LinkedHashMap<String,ResolveInfo> choices=new LinkedHashMap<>();
        for(Map.Entry<String,ResolveInfo> entry:new HomeAppCatalog(this).launchable().entrySet())if(!packages.contains(entry.getKey()) && !new ChildModeStore(this).hidden(entry.getKey())&&!new HomeVisibilityStore(this).hidden(entry.getKey()))choices.put(entry.getKey(),entry.getValue());
        ArrayList<ResolveInfo> apps=new ArrayList<>(choices.values());Collections.sort(apps,(a,b)->a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString()));if(apps.isEmpty()){message("没有可添加的已启用应用");return;}
        Sheet sheet=new Sheet(replace<0?"添加常用应用":"替换第 "+(replace+1)+" 个位置");Button first=null;
        for(ResolveInfo app:apps){Button option=sheet.option(app.loadLabel(pm)+"\n"+app.activityInfo.packageName,()->{String pkg=app.activityInfo.packageName;if(replace<0){if(packages.size()==DockStore.CAPACITY){message("常用应用已满，请选择位置替换");return;}packages.add(pkg);}else packages.set(replace,pkg);sheet.dialog.dismiss();render(replace<0?packages.size()-1:replace);saveOrder();});option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));Drawable icon=app.loadIcon(pm);icon.setBounds(0,0,px(28),px(28));option.setCompoundDrawables(icon,null,null,null);option.setCompoundDrawablePadding(px(12));if(first==null)first=option;}
        sheet.action("取消",sheet.dialog::dismiss);sheet.show(first);
    }
    private void endMove(){moving=false;beforeMove=null;render(movingIndex);saveOrder();}
    private void cancelMove(){packages=new ArrayList<>(beforeMove);moving=false;beforeMove=null;render(movingIndex);}
    private void saveOrder(){
        final ArrayList<String> value=new ArrayList<>(packages);pendingSaves++;
        worker.execute(()->{
            boolean saved;try{saved=store.saveOrder(value);}catch(RuntimeException e){saved=false;}
            final boolean success=saved;
            runOnUiThread(()->{
                pendingSaves--;if(success)lastSaved=new ArrayList<>(value);
                if(isDestroyed() || isFinishing())return;
                if(!success && pendingSaves==0){int focus=slots.indexOf(getCurrentFocus());String selected=focus>=0 && focus<packages.size()?packages.get(focus):"";packages=new ArrayList<>(lastSaved);moving=false;movingIndex=Math.max(0,packages.indexOf(selected));render(movingIndex);feedback("保存失败，已恢复上次保存的顺序");}
                if(pendingSaves==0 && afterSave!=null){Runnable action=afterSave;afterSave=null;action.run();}
            });
        });
    }
    @Override void leaveManagement(Runnable action){if(moving)cancelMove();if(pendingSaves>0)afterSave=action;else action.run();}
    public void handleBack(){if(moving)cancelMove();else leaveManagement(this::finish);}
    // Public Activity override; AndroidX core marks its inherited bridge class library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(!moving)return false;int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER){if(slots.contains(getCurrentFocus())){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){endMove();KeyFeedback.confirm(activity);}return true;}}
        if((key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) && slots.contains(getCurrentFocus())){if(event.getAction()==KeyEvent.ACTION_DOWN){int target=Math.max(0,Math.min(packages.size()-1,movingIndex+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));if(target!=movingIndex){HomeOrderStore.move(packages,movingIndex,target);movingIndex=target;render(target);KeyFeedback.moved(activity);}}return true;}
        if((key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) && slots.contains(getCurrentFocus()))return true;
        return false;
    }
}
