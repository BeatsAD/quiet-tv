package home.quiet.tv;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Source rows are a draft. Confirm is the only action that applies a source. */
final class DailyWallpaperPanel {
    private final Activity activity;
    private final WallpaperStore store;
    private final DailyWallpaperController daily;
    private final WallpaperUi ui;
    private final boolean chinese;
    private final Map<String,LinearLayout> rows=new LinkedHashMap<>();
    private final Map<String,TextView> radios=new LinkedHashMap<>();
    private Dialog dialog;
    private String draft;
    private TextView status,credit,check,confirm;
    DailyWallpaperPanel(Activity activity,WallpaperStore store,DailyWallpaperController daily,float unit,boolean chinese){this.activity=activity;this.store=store;this.daily=daily;this.ui=new WallpaperUi(activity,unit);this.chinese=chinese;}
    private int px(int n){return ui.px(n);}
    private String tr(String zh,String en){return chinese?zh:en;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    void show(Bitmap backdrop,Runnable closed){
        if(dialog!=null){backdrop.recycle();return;}daily.cancel();draft=store.snapshot().dailySource;
        dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout stage=new FrameLayout(activity);ImageView background=new ImageView(activity);background.setScaleType(ImageView.ScaleType.FIT_XY);background.setImageBitmap(backdrop);stage.addView(background,new FrameLayout.LayoutParams(-1,-1));
        View dim=new View(activity);dim.setBackgroundColor(0xbf070b12);stage.addView(dim,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=column();panel.setPadding(px(30),px(28),px(30),px(28));panel.setBackground(ui.surface(0xff222831,23,0x20ffffff,1));if(Build.VERSION.SDK_INT>=21)panel.setElevation(px(18));
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(ui.text(tr("每日壁纸","Daily wallpaper"),23),new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.button("",false,this::close);close.setContentDescription(tr("关闭每日设置","Close daily settings"));close.setPadding(px(8),px(8),px(8),px(8));close.setCompoundDrawables(ui.icon(R.drawable.wp_x,17,WallpaperUi.INK),null,null,null);close.setBackground(ui.focusSurface(0xff343c48,100));header.addView(close,new LinearLayout.LayoutParams(px(33),px(33)));panel.addView(header,new LinearLayout.LayoutParams(-1,px(42)));
        TextView source=ui.text(tr("壁纸来源","Wallpaper source"),12);source.setTextColor(WallpaperUi.MUTED);source.setPadding(0,px(20),0,px(10));panel.addView(source);
        rows.clear();radios.clear();
        for(String id:new String[]{WallpaperCatalog.BING,WallpaperCatalog.BT}){
            LinearLayout row=new LinearLayout(activity);row.setId(View.generateViewId());row.setFocusable(true);row.setClickable(true);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(px(16),px(12),px(16),px(12));
            LinearLayout labels=column();labels.addView(ui.text(WallpaperCatalog.sourceLabel(id,chinese),15));TextView description=ui.text(WallpaperCatalog.BING.equals(id)?tr("每日精选一张壁纸","One curated wallpaper each day"):tr("每天随机选择一张风景","One random landscape each day"),11);description.setTextColor(WallpaperUi.MUTED);description.setPadding(0,px(6),0,0);labels.addView(description);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            TextView radio=ui.text("",12);radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(radio,new LinearLayout.LayoutParams(px(22),px(22)));radios.put(id,radio);rows.put(id,row);
            row.setOnClickListener(v->{daily.cancel();draft=id;refresh();});row.setOnFocusChangeListener((v,focus)->row.setBackground(ui.focusSurface(id.equals(draft)?0xff354252:0xff2a323e,12)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,px(67));rp.bottomMargin=px(10);panel.addView(row,rp);
        }
        status=ui.text("",12);status.setTextColor(WallpaperUi.MUTED);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setPadding(0,px(5),0,px(8));panel.addView(status,new LinearLayout.LayoutParams(-1,-2));
        credit=ui.text("",10);credit.setTextColor(WallpaperUi.MUTED);credit.setMaxLines(3);credit.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(credit,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);
        check=ui.button(tr("检查更新","Check for updates"),false,()->daily.check(draft,(success,message)->refresh()));ui.icon(check,R.drawable.wp_rotate_ccw);actions.addView(check);actions.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
        confirm=ui.button(tr("确认来源","Confirm source"),true,()->daily.apply(draft,(success,message)->{if(success)close();else refresh();}));actions.addView(confirm);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=px(23);panel.addView(actions,ap);
        close.setNextFocusDownId(rows.get(WallpaperCatalog.BING).getId());rows.get(WallpaperCatalog.BING).setNextFocusUpId(close.getId());rows.get(WallpaperCatalog.BING).setNextFocusDownId(rows.get(WallpaperCatalog.BT).getId());rows.get(WallpaperCatalog.BT).setNextFocusUpId(rows.get(WallpaperCatalog.BING).getId());rows.get(WallpaperCatalog.BT).setNextFocusDownId(confirm.getId());
        check.setNextFocusUpId(rows.get(WallpaperCatalog.BT).getId());confirm.setNextFocusUpId(rows.get(WallpaperCatalog.BT).getId());check.setNextFocusRightId(confirm.getId());confirm.setNextFocusLeftId(check.getId());
        stage.addView(panel,new FrameLayout.LayoutParams(px(520),-2,Gravity.CENTER));dialog.setContentView(stage);
        dialog.setOnDismissListener(d->{dialog=null;daily.cancel();rows.clear();radios.clear();background.setImageDrawable(null);backdrop.recycle();closed.run();});
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setDimAmount(0);window.getDecorView().setSystemUiVisibility(5894);}
        refresh();dialog.show();if(window!=null)window.setLayout(-1,-1);rows.get(draft).requestFocus();
    }
    void refresh(){
        if(dialog==null)return;
        for(Map.Entry<String,LinearLayout> item:rows.entrySet()){
            boolean selected=item.getKey().equals(draft);item.getValue().setSelected(selected);item.getValue().setContentDescription(WallpaperCatalog.sourceLabel(item.getKey(),chinese)+(selected?tr("，已选择",", selected"):""));item.getValue().setBackground(ui.focusSurface(selected?0xff354252:0xff2a323e,12));radios.get(item.getKey()).setBackground(radio(selected));
        }
        status.setText(daily.status(draft));WallpaperCatalog.DailyImage image=store.snapshot().dailyImages.get(draft);
        String attribution=image==null?"":image.copyright;
        credit.setText(attribution);credit.setVisibility(attribution.isEmpty()?View.GONE:View.VISIBLE);
        // Keep focus on the pressed control during a request; duplicate presses are ignored.
        check.setAlpha(daily.busy()?.5f:1);confirm.setAlpha(daily.busy()?.5f:1);
    }
    private Drawable radio(boolean selected){
        return new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){Rect b=getBounds();float radius=Math.min(b.width(),b.height())/2f-px(2);paint.setColor(selected?WallpaperUi.INK:WallpaperUi.MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(1));canvas.drawCircle(b.exactCenterX(),b.exactCenterY(),radius,paint);if(selected){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(b.exactCenterX(),b.exactCenterY(),radius-px(4),paint);}}
            @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    void close(){if(dialog!=null)dialog.dismiss();}
}
