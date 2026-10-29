package home.quiet.tv;

import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Moving is a local preview; OK saves and Back restores the pre-move order. */
final class HomeSortPanel extends ManagementPanel {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<String> defaults=new ArrayList<>(),lastSaved=new ArrayList<>(),order=new ArrayList<>();
    private final HashMap<String,HomeAppCatalog.Entry> entries=new HashMap<>();
    private final HashMap<String,Bitmap> artwork=new HashMap<>();
    private HomeOrderStore store;
    private RecyclerView list;
    private GridLayoutManager layout;
    private Cards adapter;
    private TextView statusHint;
    private String moving="",selected="";
    private boolean loaded,refreshing,refreshAgain;
    private int pendingSaves;
    private Runnable afterSave;
    private Bundle restored;
    private ArrayList<String> pinnedSnapshot;
    private ArrayList<String> beforeMove;
    private String visibilitySnapshot="";
    private long coverRevision=-1,childRevision=-1;
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return;
            if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && intent.getData()!=null){String id=intent.getData().getSchemeSpecificPart();store.remove(id);new DockStore(context).remove(id);new HomeVisibilityStore(context).remove(id);}
            stopMoving();refresh();
        }
    };
    HomeSortPanel(AppManagementActivity activity){super(activity);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new HomeOrderStore(this);restored=state;
        LinearLayout body=managementContent();statusHint=managementHint("确定选中应用，方向键调整顺序");managementInfo.addView(statusHint,new LinearLayout.LayoutParams(-1,-1));
        list=new RecyclerView(this);layout=new GridLayoutManager(this,HomeActivity.APP_COLUMNS);list.setLayoutManager(layout);adapter=new Cards();list.setAdapter(adapter);
        list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(px(5),px(5),px(5),px(5));list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams grid=new LinearLayout.LayoutParams(-1,0,1);grid.leftMargin=px(-10);grid.rightMargin=px(-10);body.addView(list,grid);
        IntentFilter filter=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addAction(Intent.ACTION_PACKAGE_CHANGED);filter.addDataScheme("package");registerReceiver(packagesChanged,filter);
    }
    @Override protected void onResume(){super.onResume();if(!loaded||!new DockStore(this).load().equals(pinnedSnapshot)||coverRevision!=AppCoverStore.get(this).revision()||childRevision!=new ChildModeStore(this).revision()||!visibilitySnapshot.equals(new HomeVisibilityStore(this).signature()))refresh();}
    @Override void focusManagementContent(){list.requestFocus();if(loaded)focusEntry(selected);}
    @Override protected void onPause(){stopMoving();super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(packagesChanged);worker.shutdown();super.onDestroy();}
    @android.annotation.SuppressLint("NotifyDataSetChanged") // A package refresh can replace both metadata and membership.
    private void refresh(){
        if(refreshing || pendingSaves>0){refreshAgain=true;return;}refreshing=true;refreshAgain=false;
        worker.execute(()->{
            try{
                long covers=AppCoverStore.get(this).revision(),children=new ChildModeStore(this).revision();
                ArrayList<String> pinned=new DockStore(this).load();ArrayList<HomeAppCatalog.Entry> available=new HomeAppCatalog(this).load(pinned);
                ArrayList<String> ids=HomeAppCatalog.gridIds(available,pinned),saved=store.order(ids);
                HashMap<String,Bitmap> pictures=new HashMap<>();
                for(HomeAppCatalog.Entry entry:available){
                    if(entry.info!=null)pictures.put(entry.id,AppArtwork.load(this,entry.info));
                    else pictures.put(entry.id,HomeEntryArtwork.render(this,entry.label,entry.id));
                }
                runOnUiThread(()->{
                    if(isDestroyed() || isFinishing())return;
                    coverRevision=covers;childRevision=children;visibilitySnapshot=new HomeVisibilityStore(this).signature();pinnedSnapshot=new ArrayList<>(pinned);defaults.clear();defaults.addAll(ids);order.clear();order.addAll(saved);lastSaved.clear();lastSaved.addAll(saved);
                    entries.clear();for(HomeAppCatalog.Entry entry:available)entries.put(entry.id,entry);artwork.clear();artwork.putAll(pictures);
                    if(restored!=null){selected=restored.getString("selected","");restored=null;}
                    if(!order.contains(moving))moving="";
                    if(!order.contains(selected))selected=order.isEmpty()?"":order.get(0);
                    boolean listFocused=list.hasFocus();loaded=true;refreshing=false;adapter.notifyDataSetChanged();updateHint();
                    if(listFocused)focusEntry(selected,true);
                    if(refreshAgain){refreshAgain=false;refresh();}
                });
            }catch(Exception e){runOnUiThread(()->{if(isDestroyed() || isFinishing())return;refreshing=false;feedback("无法读取应用，请重新进入");});}
        });
    }
    private void updateHint(){statusHint.setText(moving.isEmpty()?"确定选中应用，方向键调整顺序":"方向键移动  确定放下保存  返回取消");}
    private void pick(String id){
        if(!loaded || refreshing || pendingSaves>0 || !order.contains(id))return;
        if(!moving.isEmpty()){
            String picked=moving;moving="";beforeMove=null;saveOrder();render(picked);return;
        }
        selected=id;moving=id;beforeMove=new ArrayList<>(order);render(id);
    }
    private void shift(int dx,int dy){
        if(moving.isEmpty() || refreshing)return;int from=order.indexOf(moving),target=HomeOrderStore.target(from,order.size(),HomeActivity.APP_COLUMNS,dx,dy);
        if(from==target)return;HomeOrderStore.move(order,from,target);renderMove(moving,from,target);KeyFeedback.moved(activity);
    }
    private void endMove(){String id=moving;if(beforeMove!=null){order.clear();order.addAll(beforeMove);}moving="";beforeMove=null;render(id);}
    private void render(String focus){adapter.notifyItemRangeChanged(0,order.size());updateHint();focusEntry(focus);}
    private void renderMove(String id,int from,int to){
        // A swap changes two positions; an insertion notification would describe a different order.
        adapter.notifyItemRangeChanged(Math.min(from,to),Math.abs(from-to)+1);updateHint();focusEntry(id);
    }
    private void saveOrder(){
        final ArrayList<String> value=new ArrayList<>(order),base=new ArrayList<>(defaults);pendingSaves++;
        worker.execute(()->{
            boolean saved;try{saved=store.save(value,base,false);}catch(RuntimeException e){saved=false;}
            final boolean success=saved;
            runOnUiThread(()->{
                pendingSaves--;if(success){lastSaved.clear();lastSaved.addAll(value);}
                if(isDestroyed() || isFinishing())return;
                if(!success && pendingSaves==0){order.clear();order.addAll(lastSaved);moving="";render(order.contains(selected)?selected:order.isEmpty()?"":order.get(0));feedback("保存失败，已恢复上次保存的顺序");}
                if(pendingSaves==0 && afterSave!=null){Runnable action=afterSave;afterSave=null;action.run();}
                else if(pendingSaves==0 && refreshAgain)refresh();
            });
        });
    }
    private void focusEntry(String id){
        focusEntry(id,false);
    }
    private void focusEntry(String id,boolean preserveTab){
        int position=order.indexOf(id);if(position<0)return;selected=id;
        layout.scrollToPosition(position);list.post(()->{if(preserveTab && managementTabFocused())return;if(!focusHolder(position))list.post(()->{if(!preserveTab || !managementTabFocused())focusHolder(position);});});
    }
    private boolean focusHolder(int position){
        if(!showing || isDestroyed() || isFinishing())return true;RecyclerView.ViewHolder holder=list.findViewHolderForAdapterPosition(position);
        return holder!=null && holder.itemView.requestFocusFromTouch();
    }
    private void stopMoving(){if(!moving.isEmpty())endMove();}
    @Override void leaveManagement(Runnable action){stopMoving();if(pendingSaves>0)afterSave=action;else action.run();}
    @Override public void handleBack(){if(!moving.isEmpty())endMove();else leaveManagement(this::finish);}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected);super.onSaveInstanceState(state);}
    @android.annotation.SuppressLint("RestrictedApi")
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();boolean direction=key>=KeyEvent.KEYCODE_DPAD_UP && key<=KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean confirm=key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(!moving.isEmpty()){
            if(direction){if(event.getAction()==KeyEvent.ACTION_DOWN)shift(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);return true;}
            if(confirm){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){pick(moving);KeyFeedback.confirm(activity);}return true;}
        }
        if(list.hasFocus()){
            if(confirm){if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){pick(selected);KeyFeedback.confirm(activity);}return true;}
            if(direction){
                if(event.getAction()==KeyEvent.ACTION_UP)return true;int current=order.indexOf(selected);
                int next=HomeOrderStore.target(current,order.size(),HomeActivity.APP_COLUMNS,key==KeyEvent.KEYCODE_DPAD_LEFT?-1:key==KeyEvent.KEYCODE_DPAD_RIGHT?1:0,key==KeyEvent.KEYCODE_DPAD_UP?-1:key==KeyEvent.KEYCODE_DPAD_DOWN?1:0);
                if(next!=current){focusEntry(order.get(next));KeyFeedback.moved(activity);}
                else if(key==KeyEvent.KEYCODE_DPAD_UP){View tab=findSortTab(root);if(tab!=null)tab.requestFocus();}
                return true;
            }
        }
        return false;
    }
    private View findSortTab(View view){
        if(view instanceof Button && "应用排序".contentEquals(((Button)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findSortTab(group.getChildAt(i));if(found!=null)return found;}}return null;
    }
    private final class Cards extends RecyclerView.Adapter<Cards.Holder>{
        final class Holder extends RecyclerView.ViewHolder {
            final LinearLayout card;final FrameLayout art;final ImageView image;final TextView title,number,badge;
            Holder(LinearLayout card,FrameLayout art,ImageView image,TextView title,TextView number,TextView badge){super(card);this.card=card;this.art=art;this.image=image;this.title=title;this.number=number;this.badge=badge;}
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=column();card.setFocusable(true);card.setClickable(true);card.setClipChildren(false);card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);p.leftMargin=px(10);p.rightMargin=px(10);p.bottomMargin=px(18);card.setLayoutParams(p);
            FrameLayout art=new FrameLayout(activity){@Override protected void onMeasure(int w,int h){super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(Math.round(View.MeasureSpec.getSize(w)*203f/360f),View.MeasureSpec.EXACTLY));}};
            art.setDuplicateParentStateEnabled(true);art.setPadding(px(3),px(3),px(3),px(3));card.addView(art,new LinearLayout.LayoutParams(-1,-2));
            ImageView image=new ImageView(activity);image.setBackgroundDrawable(surface(0,0,0,10));UiCompat.clipOutline(image);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView badge=center("正在移动",10);badge.setTextColor(0xff22262e);badge.setPadding(px(6),0,px(6),0);badge.setBackgroundDrawable(surface(0xffeff0f4,0xffeff0f4,0,4));
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,px(23),Gravity.TOP|Gravity.END);bp.setMargins(px(7),px(7),px(7),0);art.addView(badge,bp);
            gap(card,8);LinearLayout caption=row();card.addView(caption,new LinearLayout.LayoutParams(-1,px(21)));
            TextView title=label("",13);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(title,new LinearLayout.LayoutParams(0,-1,1));
            TextView number=muted("",10);number.setPadding(px(5),0,0,0);caption.addView(number);
            return new Holder(card,art,image,title,number,badge);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            String id=order.get(position);HomeAppCatalog.Entry entry=entries.get(id);boolean picked=id.equals(moving);
            holder.title.setText(entry.label);holder.number.setText(String.format(Locale.ROOT,"%02d",position+1));holder.badge.setVisibility(picked?View.VISIBLE:View.GONE);
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
            background.addState(new int[]{android.R.attr.state_focused},surface(0xff3b3e46,0xff1b1d24,TEXT,12));
            background.addState(new int[]{},surface(0xff373b43,0xff1b1d24,picked?TEXT:0x24ffffff,12));holder.art.setBackgroundDrawable(background);
            holder.card.setContentDescription(entry.label+"，第 "+(position+1)+" 个位置"+(picked?"，正在移动":""));
            holder.card.setOnFocusChangeListener((v,focus)->{if(focus)selected=id;});holder.card.setOnClickListener(v->{pick(id);KeyFeedback.confirm(activity);});
            FrameLayout.LayoutParams ip=(FrameLayout.LayoutParams)holder.image.getLayoutParams();ip.width=-1;ip.height=-1;ip.gravity=Gravity.CENTER;
            holder.image.setScaleType(ImageView.ScaleType.FIT_XY);
            holder.image.setBackgroundDrawable(surface(0,0,0,10));
            Bitmap picture=artwork.get(id);int color=picture.getPixel(0,0);
            holder.image.setBackgroundDrawable(surface(color,color,0,10));holder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);holder.image.setImageBitmap(picture);
            holder.image.setLayoutParams(ip);
        }
        @Override public int getItemCount(){return order.size();}
    }
}
