package home.quiet.tv;

import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A local draft: picking up and dropping never writes the saved home order. */
public final class HomeSortActivity extends ManagementUi {
    // Returning to the singleTask Home can destroy this Activity instead of merely pausing it.
    private static Bundle pendingDraft;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<String> defaults=new ArrayList<>(),original=new ArrayList<>(),draft=new ArrayList<>();
    private final HashMap<String,HomeAppCatalog.Entry> entries=new HashMap<>();
    private final HashMap<String,Drawable> artwork=new HashMap<>();
    private HomeOrderStore store;
    private RecyclerView list;
    private GridLayoutManager layout;
    private Cards adapter;
    private LinearLayout controls,left,actions;
    private Button saveButton,resetButton;
    private Bitmap wallpaper;
    private ArrayList<String> beforeMove;
    private String moving="",selected="";
    private boolean loaded,refreshing,refreshAgain,saving,resetRequested,beforeReset,savedCustom,dialogOpen,leaving;
    private Bundle restored;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getData()!=null){String id=intent.getData().getSchemeSpecificPart();store.remove(id);new DockStore(context).remove(id);}
            refresh();
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new HomeOrderStore(this);restored=state!=null?state:pendingDraft;pendingDraft=null;
        root.setPadding(px(48),px(22),px(48),px(16));
        LinearLayout tools=toolbar(2);LinearLayout.LayoutParams tp=(LinearLayout.LayoutParams)tools.getLayoutParams();tp.topMargin=px(14);tp.height=px(44);tools.setLayoutParams(tp);
        list=new RecyclerView(this);layout=new GridLayoutManager(this,4);list.setLayoutManager(layout);adapter=new Cards();list.setAdapter(adapter);
        list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(px(5),px(5),px(5),px(5));list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams grid=new LinearLayout.LayoutParams(-1,0,1);grid.topMargin=px(18);grid.leftMargin=px(-10);grid.rightMargin=px(-10);root.addView(list,grid);
        controls=row();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,px(40));cp.topMargin=px(14);root.addView(controls,cp);
        left=row();controls.addView(left,new LinearLayout.LayoutParams(0,-1,1));actions=row();controls.addView(actions,new LinearLayout.LayoutParams(-2,-1));updateControls();
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");registerReceiver(packagesChanged,filter);
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onPause(){if(loaded && !leaving && (dirty() || !moving.isEmpty()))pendingDraft=snapshot();super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(packagesChanged);worker.shutdown();super.onDestroy();}
    private boolean dirty(){return !draft.equals(original) || resetRequested && savedCustom;}
    @android.annotation.SuppressLint("NotifyDataSetChanged") // A package refresh can replace both metadata and membership.
    private void refresh(){
        if(refreshing || saving){refreshAgain=true;return;}refreshing=true;
        worker.execute(()->{
            try{
                ArrayList<String> pinned=new DockStore(this).load();ArrayList<HomeAppCatalog.Entry> available=new HomeAppCatalog(this).load(pinned);
                ArrayList<String> ids=HomeAppCatalog.gridIds(available,pinned),order=store.order(ids);boolean custom=store.custom();
                Bitmap preview=wallpaper;
                if(preview==null)try(InputStream input=getAssets().open("wallpapers/big-sur-coastline.jpg")){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;preview=BitmapFactory.decodeStream(input,null,options);}
                final Bitmap image=preview;
                runOnUiThread(()->{
                    if(isDestroyed() || isFinishing())return;
                    boolean editing=loaded && (dirty() || !moving.isEmpty());
                    defaults.clear();defaults.addAll(ids);original.clear();original.addAll(order);savedCustom=custom;wallpaper=image;
                    entries.clear();for(HomeAppCatalog.Entry entry:available)entries.put(entry.id,entry);artwork.clear();
                    ArrayList<String> next=editing?HomeOrderStore.project(resetRequested?ids:draft,ids):new ArrayList<>(order);
                    if(restored!=null && restored.containsKey("draft")){
                        next=HomeOrderStore.project(restored.getStringArrayList("draft"),ids);selected=restored.getString("selected","");resetRequested=restored.getBoolean("reset");
                        moving=restored.getString("moving","");beforeReset=restored.getBoolean("beforeReset");beforeMove=restored.getStringArrayList("beforeMove");
                        restored=null;
                    }
                    draft.clear();draft.addAll(next);
                    if(beforeMove!=null)beforeMove=HomeOrderStore.project(beforeMove,ids);
                    if(!draft.contains(moving)){moving="";beforeMove=null;}
                    if(!draft.contains(selected))selected=draft.isEmpty()?"":draft.get(0);
                    boolean first=!loaded;loaded=true;refreshing=false;adapter.notifyDataSetChanged();updateControls();
                    if(first || list.hasFocus())focusEntry(selected);
                    if(refreshAgain){refreshAgain=false;refresh();}
                });
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed() || isFinishing())return;refreshing=false;feedback("无法读取应用，请重新进入");});}
        });
    }
    private void updateControls(){
        left.removeAllViews();actions.removeAllViews();
        if(moving.isEmpty()){TextView state=muted(dirty()?"未保存":"",11);left.addView(state);}
        else{
            String[] labels={"←","↑","↓","→"},names={"将应用左移","将应用上移","将应用下移","将应用右移"};int[] dx={-1,0,0,1},dy={0,-1,1,0};
            for(int i=0;i<labels.length;i++){final int step=i;Button arrow=button(labels[i],()->shift(dx[step],dy[step]));arrow.setContentDescription(names[i]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(38),-1);p.rightMargin=px(6);arrow.setPadding(0,0,0,0);left.addView(arrow,p);}
        }
        resetButton=button(moving.isEmpty()?"恢复默认":"取消移动",()->{if(moving.isEmpty())restoreDefault();else cancelMove();});actions.addView(resetButton,new LinearLayout.LayoutParams(-2,-1));
        saveButton=iconText(saving?"保存中":"保存顺序",R.drawable.ic_home_save,()->save(null));marginAdd(actions,saveButton,-2,40,10);
        boolean canSave=loaded && dirty() && moving.isEmpty() && !saving;saveButton.setEnabled(canSave);resetButton.setEnabled(loaded && !saving);
        saveButton.setTextColor(canSave?0xff171a20:MUTED);
        Drawable saveIcon=saveButton.getCompoundDrawables()[0].mutate();saveIcon.setColorFilter(canSave?0xff171a20:MUTED,android.graphics.PorterDuff.Mode.SRC_IN);
        if(canSave){
            android.graphics.drawable.LayerDrawable focused=new android.graphics.drawable.LayerDrawable(new Drawable[]{surface(0,0,TEXT,24),surface(0xfff5f6f9,0xffeff0f4,0,22)});
            focused.setLayerInset(1,px(3),px(3),px(3),px(3));
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},focused);background.addState(new int[]{},surface(0xffeff0f4,0xffe4e7ee,0,24));saveButton.setBackground(background);
        }
    }
    private void pick(String id){
        if(saving || !loaded || !draft.contains(id))return;
        if(!moving.isEmpty()){
            int from=draft.indexOf(moving),target=draft.indexOf(id);if(target>=0 && !moving.equals(id)){HomeOrderStore.move(draft,from,target);resetRequested=false;}
            String picked=moving;moving="";beforeMove=null;renderMove(picked,from,target);return;
        }
        selected=id;moving=id;beforeMove=new ArrayList<>(draft);beforeReset=resetRequested;render(id);
    }
    private void shift(int dx,int dy){
        if(moving.isEmpty() || saving)return;int from=draft.indexOf(moving),target=HomeOrderStore.target(from,draft.size(),4,dx,dy);
        if(from==target)return;HomeOrderStore.move(draft,from,target);resetRequested=false;renderMove(moving,from,target);
    }
    private void cancelMove(){if(moving.isEmpty() || saving)return;String id=moving;int from=draft.indexOf(id),to=beforeMove.indexOf(id);draft.clear();draft.addAll(beforeMove);moving="";beforeMove=null;resetRequested=beforeReset;renderMove(id,from,to);}
    private void render(String focus){adapter.notifyItemRangeChanged(0,draft.size());updateControls();focusEntry(focus);}
    private void renderMove(String id,int from,int to){
        if(from!=to)adapter.notifyItemMoved(from,to);
        adapter.notifyItemRangeChanged(Math.min(from,to),Math.abs(from-to)+1);updateControls();focusEntry(id);
    }
    private void focusEntry(String id){
        int position=draft.indexOf(id);if(position<0)return;selected=id;
        layout.scrollToPosition(position);list.post(()->{if(!focusHolder(position))list.post(()->focusHolder(position));});
    }
    private boolean focusHolder(int position){
        if(isDestroyed() || isFinishing())return true;RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);
        if(holder==null)return false;holder.itemView.requestFocus();return true;
    }
    private void restoreDefault(){
        if(saving)return;Sheet sheet=new Sheet("恢复默认顺序？");sheet.text("保存后生效。");dialogOpen=true;
        Button cancel=sheet.action("取消",sheet.dialog::dismiss);sheet.action("恢复默认",()->{draft.clear();draft.addAll(defaults);resetRequested=true;sheet.dialog.dismiss();render(draft.get(0));});
        sheet.dialog.setOnDismissListener(d->{dialogOpen=false;resetButton.requestFocus();});sheet.show(cancel);
    }
    private void save(Runnable after){
        if(saving || !moving.isEmpty() || !loaded)return;
        saving=true;updateControls();final ArrayList<String> value=new ArrayList<>(draft),base=new ArrayList<>(defaults);final boolean reset=resetRequested;
        worker.execute(()->{
            boolean success=store.save(value,base,reset);
            runOnUiThread(()->{
                if(isDestroyed() || isFinishing())return;saving=false;
                if(success){original.clear();original.addAll(value);savedCustom=!reset;resetRequested=false;pendingDraft=null;feedback("已保存");}
                else feedback("保存失败，请重试");
                updateControls();resetButton.requestFocus();
                if(success && after!=null)after.run();
                else if(refreshAgain){refreshAgain=false;refresh();}
            });
        });
    }
    @Override void leaveManagement(Runnable action){
        if(saving || dialogOpen)return;
        if(!moving.isEmpty()){feedback("请先放下应用");focusEntry(moving);return;}
        Runnable exit=()->{leaving=true;pendingDraft=null;action.run();};
        if(!dirty()){exit.run();return;}
        Sheet sheet=new Sheet("保存这次调整吗？");dialogOpen=true;
        sheet.action("放弃修改",()->{sheet.dialog.dismiss();exit.run();});sheet.action("保存并退出",()->{sheet.dialog.dismiss();save(exit);});
        Button keep=sheet.action("继续编辑",sheet.dialog::dismiss);
        sheet.dialog.setOnDismissListener(d->{dialogOpen=false;focusEntry(selected);});sheet.show(keep);
    }
    @Override public void handleBack(){if(saving)return;if(!moving.isEmpty())cancelMove();else leaveManagement(this::finish);}
    private Bundle snapshot(){
        Bundle state=new Bundle();state.putStringArrayList("draft",new ArrayList<>(draft));state.putString("selected",selected);state.putBoolean("reset",resetRequested);state.putString("moving",moving);state.putStringArrayList("beforeMove",beforeMove==null?null:new ArrayList<>(beforeMove));state.putBoolean("beforeReset",beforeReset);return state;
    }
    @Override protected void onSaveInstanceState(Bundle state){if(loaded)state.putAll(snapshot());super.onSaveInstanceState(state);}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(dialogOpen)return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();boolean direction=key>=KeyEvent.KEYCODE_DPAD_UP && key<=KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(saving && (direction || confirm || key==KeyEvent.KEYCODE_BACK))return true;
        if(!moving.isEmpty()){
            if(direction){if(event.getAction()==KeyEvent.ACTION_DOWN)shift(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);return true;}
            if(confirm){if(event.getAction()==KeyEvent.ACTION_UP)pick(moving);return true;}
        }
        if(list.hasFocus()){
            if(confirm){if(event.getAction()==KeyEvent.ACTION_UP)pick(selected);return true;}
            if(direction){
                if(event.getAction()==KeyEvent.ACTION_UP)return true;int current=draft.indexOf(selected);
                int next=HomeOrderStore.target(current,draft.size(),4,key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);
                if(next!=current)focusEntry(draft.get(next));
                else if(key==KeyEvent.KEYCODE_DPAD_DOWN)resetButton.requestFocus();
                else if(key==KeyEvent.KEYCODE_DPAD_UP){View tab=findSortTab(root);if(tab!=null)tab.requestFocus();}
                return true;
            }
        }
        if(direction && key==KeyEvent.KEYCODE_DPAD_UP && controls.hasFocus()){
            if(event.getAction()==KeyEvent.ACTION_DOWN)focusEntry(selected);return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private View findSortTab(View view){
        if(view instanceof Button && "首页排序".contentEquals(((Button)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findSortTab(group.getChildAt(i));if(found!=null)return found;}}return null;
    }
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout card;final FrameLayout art;final ImageView image,wallpaperIcon;final TextView title,number,badge;
            Holder(LinearLayout card,FrameLayout art,ImageView image,ImageView wallpaperIcon,TextView title,TextView number,TextView badge){super(card);this.card=card;this.art=art;this.image=image;this.wallpaperIcon=wallpaperIcon;this.title=title;this.number=number;this.badge=badge;}
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setClipChildren(false);card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);p.leftMargin=px(10);p.rightMargin=px(10);p.bottomMargin=px(18);card.setLayoutParams(p);
            FrameLayout art=new FrameLayout(HomeSortActivity.this){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.round(View.MeasureSpec.getSize(w)*203f/360f),View.MeasureSpec.EXACTLY));}};
            art.setDuplicateParentStateEnabled(true);art.setPadding(px(3),px(3),px(3),px(3));card.addView(art,new LinearLayout.LayoutParams(-1,-2));
            ImageView image=new ImageView(HomeSortActivity.this);image.setBackground(surface(0,0,0,10));UiCompat.clipOutline(image);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
            ImageView wallpaperIcon=new ImageView(HomeSortActivity.this);wallpaperIcon.setImageResource(R.drawable.ic_home_wallpaper);art.addView(wallpaperIcon,new FrameLayout.LayoutParams(px(42),px(42),Gravity.CENTER));
            TextView badge=center("正在移动",10);badge.setTextColor(0xff22262e);badge.setPadding(px(6),0,px(6),0);badge.setBackground(surface(0xffeff0f4,0xffeff0f4,0,4));
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,px(23),Gravity.TOP|Gravity.END);bp.setMargins(px(7),px(7),px(7),0);art.addView(badge,bp);
            gap(card,8);LinearLayout caption=row();card.addView(caption,new LinearLayout.LayoutParams(-1,px(21)));
            TextView title=label("",13);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(title,new LinearLayout.LayoutParams(0,-1,1));
            TextView number=muted("",10);number.setPadding(px(5),0,0,0);caption.addView(number);
            return new Holder(card,art,image,wallpaperIcon,title,number,badge);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            String id=draft.get(position);HomeAppCatalog.Entry entry=entries.get(id);boolean picked=id.equals(moving);
            holder.wallpaperIcon.setVisibility(HomeAppCatalog.WALLPAPER.equals(id)?View.VISIBLE:View.GONE);
            holder.title.setText(entry.label);holder.number.setText(String.format(Locale.ROOT,"%02d",position+1));holder.badge.setVisibility(picked?View.VISIBLE:View.GONE);
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
            background.addState(new int[]{android.R.attr.state_focused},surface(0xff3b3e46,0xff1b1d24,TEXT,12));
            background.addState(new int[]{},surface(0xff373b43,0xff1b1d24,picked?TEXT:0x24ffffff,12));holder.art.setBackground(background);
            holder.card.setContentDescription(entry.label+"，第 "+(position+1)+" 个位置"+(picked?"，正在移动":""));
            holder.card.setOnFocusChangeListener((v,focus)->{if(focus)selected=id;});holder.card.setOnClickListener(v->pick(id));
            FrameLayout.LayoutParams ip=(FrameLayout.LayoutParams)holder.image.getLayoutParams();ip.width=-1;ip.height=-1;ip.gravity=Gravity.CENTER;
            holder.image.setScaleType(ImageView.ScaleType.FIT_XY);
            if(HomeAppCatalog.WALLPAPER.equals(id))holder.image.setImageBitmap(wallpaper);
            else if(HomeAppCatalog.MANAGEMENT.equals(id)){holder.image.setImageDrawable(UiCompat.drawable(HomeSortActivity.this,R.drawable.ic_manage_apps));ip.width=px(48);ip.height=px(48);holder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);}
            else{
                Drawable art=artwork.get(id);boolean banner=true;PackageManager pm=getPackageManager();
                if(art==null){if(android.os.Build.VERSION.SDK_INT>=21){art=entry.info.activityInfo.loadBanner(pm);if(art==null)art=entry.info.activityInfo.applicationInfo.loadBanner(pm);}if(art==null){banner=false;art=entry.info.loadIcon(pm);}artwork.put(id,art);}
                else banner=android.os.Build.VERSION.SDK_INT>=21 && (entry.info.activityInfo.banner!=0 || entry.info.activityInfo.applicationInfo.banner!=0);
                holder.image.setImageDrawable(art);if(!banner){ip.width=px(48);ip.height=px(48);holder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);}
            }
            holder.image.setLayoutParams(ip);
        }
        @Override public int getItemCount(){return draft.size();}
    }
}
