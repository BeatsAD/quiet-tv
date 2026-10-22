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

public final class DockEditorActivity extends ManagementUi {
    private DockStore store;
    private ArrayList<String> packages,moving;
    private int movingIndex;
    private boolean initialWindowFocus=true;
    private LinearLayout content;
    private final ArrayList<View> slots=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final HashMap<String,Bitmap> artwork=new HashMap<>();
    private int artworkGeneration;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new DockStore(this);packages=store.load();toolbar(true);
        content=column();content.setGravity(Gravity.CENTER_VERTICAL);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
    }
    @Override protected void onResume(){super.onResume();int focus=slots.indexOf(getCurrentFocus());artworkGeneration++;artwork.clear();render(Math.max(0,focus));}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused && initialWindowFocus){initialWindowFocus=false;stage.post(()->slots.get(0).requestFocus());}}
    private ApplicationInfo info(String pkg){try{return getPackageManager().getApplicationInfo(pkg,HomeController.METADATA);}catch(PackageManager.NameNotFoundException e){return null;}}
    private String name(String pkg){ApplicationInfo info=info(pkg);return info==null?pkg:getPackageManager().getApplicationLabel(info).toString();}
    private void loadArtwork(ImageView image,String pkg,ApplicationInfo app){
        Bitmap cached=artwork.get(pkg);if(cached!=null){showArtwork(image,cached);return;}
        int generation=artworkGeneration;
        worker.execute(()->{Bitmap picture=AppArtwork.load(this,pkg,app);runOnUiThread(()->{
            if(isDestroyed() || generation!=artworkGeneration)return;
            artwork.put(pkg,picture);showArtwork(image,picture);
        });});
    }
    private void showArtwork(ImageView image,Bitmap picture){
        int color=picture.getPixel(0,0);image.setBackground(surface(color,color,0,10));image.setImageBitmap(picture);
    }
    private void render(int focus){
        content.removeAllViews();slots.clear();List<String> values=moving==null?packages:moving;
        if(moving!=null){content.addView(label("调整常用应用顺序",23));gap(content,11);content.addView(muted("左右移动当前应用，确定保存，返回取消。",12));gap(content,36);}
        LinearLayout cards=row();cards.setGravity(Gravity.TOP);cards.setClipChildren(false);content.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        int width=px((864-(DockStore.CAPACITY-1)*20)/(float)DockStore.CAPACITY),height=Math.round(width*203f/360f);
        for(int i=0;i<DockStore.CAPACITY;i++){
            final int slot=i;boolean occupied=i<values.size();String pkg=occupied?values.get(i):"";ApplicationInfo info=occupied?info(pkg):null;
            boolean unavailable=occupied && (info==null || !info.enabled || (info.flags&ApplicationInfo.FLAG_INSTALLED)==0);
            LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setClipChildren(false);card.setOnClickListener(v->edit(slot));card.setContentDescription("位置 "+(i+1)+"  "+(occupied?name(pkg):"添加常用应用"));slots.add(card);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);if(i>0)cp.leftMargin=px(20);cards.addView(card,cp);
            TextView number=muted(String.format(Locale.ROOT,"%02d",i+1),10);card.addView(number);gap(card,10);
            FrameLayout art=new FrameLayout(this);art.setDuplicateParentStateEnabled(true);art.setBackground(focusSurface(12));art.setPadding(px(3),px(3),px(3),px(3));card.addView(art,new LinearLayout.LayoutParams(-1,height));
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if(occupied){image.setBackground(surface(AppArtwork.NEUTRAL,AppArtwork.NEUTRAL,0,10));UiCompat.clipOutline(image);image.setScaleType(ImageView.ScaleType.FIT_CENTER);art.addView(image,new FrameLayout.LayoutParams(-1,-1));loadArtwork(image,pkg,info);}
            else{image.setImageDrawable(UiCompat.drawable(this,R.drawable.ic_manage_plus));art.addView(image,new FrameLayout.LayoutParams(px(28),px(28),Gravity.CENTER));}
            if(unavailable){ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(0);image.setColorFilter(new ColorMatrixColorFilter(matrix));image.setAlpha(.45f);}
            gap(card,16);TextView title=center(occupied?name(pkg):"添加常用应用",13);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);if(!occupied)title.setTextColor(MUTED);card.addView(title,new LinearLayout.LayoutParams(-1,px(20)));
            if(unavailable){gap(card,5);TextView note=center(info!=null && info.enabled?"未安装  保留位置":"已禁用  保留位置",10);note.setTextColor(MUTED);card.addView(note,new LinearLayout.LayoutParams(-1,px(16)));}
        }
        if(moving!=null){gap(content,35);TextView note=center("调整尚未保存",11);note.setTextColor(MUTED);content.addView(note,new LinearLayout.LayoutParams(-1,px(20)));gap(content,18);LinearLayout actions=row();actions.setGravity(Gravity.CENTER);content.addView(actions,new LinearLayout.LayoutParams(-1,px(36)));marginAdd(actions,button("取消调整",()->cancelMove()),-2,36,0);marginAdd(actions,button("保存顺序",()->saveMove()),-2,36,12);}
        else{gap(content,35);content.addView(center("请选择应用",23));gap(content,11);TextView note=center("确认进行对应的顺序、替换调整",12);note.setTextColor(MUTED);content.addView(note);}
        slots.get(Math.max(0,Math.min(focus,DockStore.CAPACITY-1))).requestFocus();
    }
    private void edit(int index){
        if(moving!=null)return;if(index>=packages.size()){choose(-1);return;}
        Sheet sheet=new Sheet(name(packages.get(index)));Button replace=sheet.option("替换应用",()->{sheet.dialog.dismiss();choose(index);});sheet.option("移除快捷入口",()->{sheet.dialog.dismiss();packages.remove(index);store.save(packages);render(Math.min(index,DockStore.CAPACITY-1));feedback("已移除快捷入口");});sheet.option("左右排序",()->{sheet.dialog.dismiss();moving=new ArrayList<>(packages);movingIndex=index;render(index);});sheet.action("取消",sheet.dialog::dismiss);sheet.show(replace);
    }
    private void choose(int replace){
        PackageManager pm=getPackageManager();LinkedHashMap<String,ResolveInfo> choices=new LinkedHashMap<>();
        for(Map.Entry<String,ResolveInfo> entry:new HomeAppCatalog(this).launchable().entrySet())if(!packages.contains(entry.getKey()))choices.put(entry.getKey(),entry.getValue());
        ArrayList<ResolveInfo> apps=new ArrayList<>(choices.values());Collections.sort(apps,(a,b)->a.loadLabel(pm).toString().compareTo(b.loadLabel(pm).toString()));if(apps.isEmpty()){message("没有可添加的已启用应用");return;}
        Sheet sheet=new Sheet(replace<0?"添加常用应用":"替换第 "+(replace+1)+" 个位置");Button first=null;
        for(ResolveInfo app:apps){Button option=sheet.option(app.loadLabel(pm)+"\n"+app.activityInfo.packageName,()->{String pkg=app.activityInfo.packageName;if(replace<0){if(packages.size()==DockStore.CAPACITY){message("常用应用已满，请选择位置替换");return;}packages.add(pkg);}else packages.set(replace,pkg);store.save(packages);sheet.dialog.dismiss();render(replace<0?packages.size()-1:replace);feedback("已保存常用应用");});option.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(12));Drawable icon=app.loadIcon(pm);icon.setBounds(0,0,px(28),px(28));option.setCompoundDrawables(icon,null,null,null);option.setCompoundDrawablePadding(px(12));if(first==null)first=option;}
        sheet.action("取消",sheet.dialog::dismiss);sheet.show(first);
    }
    private void saveMove(){packages=moving;moving=null;store.save(packages);render(movingIndex);feedback("已保存顺序");}
    private void cancelMove(){moving=null;render(movingIndex);}
    public void handleBack(){if(moving!=null)cancelMove();else super.handleBack();}
    // Public Activity override; AndroidX core marks its inherited bridge class library-only.
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(moving==null)return super.dispatchKeyEvent(event);int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER){if(slots.contains(getCurrentFocus())){if(event.getAction()==KeyEvent.ACTION_UP)saveMove();return true;}}
        if((key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) && slots.contains(getCurrentFocus())){if(event.getAction()==KeyEvent.ACTION_DOWN){int target=Math.max(0,Math.min(moving.size()-1,movingIndex+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));Collections.swap(moving,movingIndex,target);movingIndex=target;render(target);}return true;}
        return super.dispatchKeyEvent(event);
    }
}
