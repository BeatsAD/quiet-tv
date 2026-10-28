package home.quiet.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** The three quiet storybook endings share native text and remote controls. */
final class ChildRestView extends FrameLayout {
    private static final String[] ART={"forest","cloud","space"};
    private final int theme,ink,muted,accent;
    private final FrameLayout content;
    private final ImageView background;
    private final TextView brand,eyebrow,title,note;
    private final Button leave,parent;
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Bitmap bitmap;
    private boolean released;
    private float scale=1;
    private int layoutWidth,layoutHeight;

    ChildRestView(ManagementUi ui,int theme,boolean daily,Runnable onLeave,Runnable onParent){
        super(ui);this.theme=theme;setTag("child-rest-"+ART[theme]);
        ink=theme==2?0xfffff3dc:theme==1?0xff203851:0xff202523;
        muted=theme==2?0xffd1c9f0:theme==1?0xff536f89:0xff62675f;
        accent=theme==2?0xffffc68a:theme==1?0xff287ff2:0xff728964;
        setBackgroundColor(theme==2?0xff25264e:0xfffaf3e4);
        background=new ImageView(ui);background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        androidx.core.view.ViewCompat.setImportantForAccessibility(background,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);addView(background,new LayoutParams(-1,-1));
        content=new FrameLayout(ui);content.setClipChildren(false);content.setClipToPadding(false);addView(content);
        brand=text("Quiet TV",Color.WHITE,true);eyebrow=text("",muted,false);daily(daily);
        title=text(theme==1?"看了这么久，我们休息一下吧":"看了这么久，\n我们休息一下吧",ink,true);
        title.setLineSpacing(0,1.13f);if(Build.VERSION.SDK_INT>=28)title.setFallbackLineSpacing(false);
        note=text(theme==0?"伸个懒腰，和小熊一起放松一下。":theme==1?"让眼睛歇一歇，和小云朵伸个懒腰。":"小小探险家，也需要休息一下。",muted,false);
        leave=ui.button("好呀，去休息",onLeave);leave.setId(androidx.core.view.ViewCompat.generateViewId());leave.setTextColor(theme==2?0xff25264e:Color.WHITE);
        parent=ui.button("家长授权",onParent);parent.setId(androidx.core.view.ViewCompat.generateViewId());parent.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{}},new int[]{ManagementUi.TEXT,muted}));
        leave.setContentDescription("好呀，去休息，返回桌面");parent.setContentDescription("家长授权");
        leave.setSingleLine(true);parent.setSingleLine(true);leave.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        leave.setBackgroundDrawable(control(accent));parent.setBackgroundDrawable(control(Color.TRANSPARENT));
        Drawable lock=UiCompat.drawable(ui,R.drawable.ic_child_lock).mutate();lock.setColorFilter(muted,PorterDuff.Mode.SRC_IN);parent.setCompoundDrawables(lock,null,null,null);parent.setOnFocusChangeListener((view,focused)->lock.setColorFilter(focused?ManagementUi.TEXT:muted,PorterDuff.Mode.SRC_IN));
        content.addView(leave);content.addView(parent);
        leave.setNextFocusDownId(parent.getId());leave.setNextFocusUpId(parent.getId());leave.setNextFocusLeftId(parent.getId());leave.setNextFocusRightId(parent.getId());
        parent.setNextFocusUpId(leave.getId());parent.setNextFocusDownId(leave.getId());parent.setNextFocusLeftId(leave.getId());parent.setNextFocusRightId(leave.getId());
        leave.requestFocus();load(ui);
    }
    void daily(boolean daily){eyebrow.setText(daily?"今天的观看时间结束啦":"这一轮观看结束啦");}
    private TextView text(String value,int color,boolean bold){
        TextView v=new TextView(getContext());v.setText(value);v.setTextColor(color);v.setIncludeFontPadding(false);v.setGravity(Gravity.CENTER_VERTICAL);v.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);content.addView(v);return v;
    }
    private Drawable control(int color){
        return ControlStyle.action(getResources().getDisplayMetrics().widthPixels/960f,100,color,0);
    }
    private void place(View v,float x,float y,float w,float h,float font){
        LayoutParams p=new LayoutParams(Math.round(w*scale),Math.round(h*scale));p.leftMargin=Math.round(x*scale);p.topMargin=Math.round(y*scale);v.setLayoutParams(p);
        if(v instanceof TextView)((TextView)v).setTextSize(TypedValue.COMPLEX_UNIT_PX,font*scale);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
        if(w!=layoutWidth || h!=layoutHeight){
        layoutWidth=w;layoutHeight=h;scale=Math.min(w/1920f,h/1080f);
        LayoutParams board=new LayoutParams(Math.round(1920*scale),Math.round(1080*scale),Gravity.CENTER);content.setLayoutParams(board);
        place(brand,72,48,320,60,44);
        if(theme==0){place(eyebrow,1140,194,708,62,40);place(title,1140,278,708,246,88);place(note,1140,540,708,86,36);place(leave,1190,684,590,140,42);place(parent,1330,858,320,76,34);}
        else if(theme==1){
            eyebrow.setGravity(Gravity.CENTER);title.setGravity(Gravity.CENTER);note.setGravity(Gravity.CENTER);
            place(eyebrow,300,572,1320,62,40);place(title,160,644,1600,120,80);place(note,280,774,1360,68,36);place(leave,704,858,512,112,40);place(parent,1610,968,240,70,30);
        }else{place(eyebrow,128,220,850,66,40);place(title,128,290,850,250,92);place(note,128,550,850,74,36);place(leave,128,664,640,124,42);place(parent,128,818,310,76,34);}
        Drawable lock=parent.getCompoundDrawables()[0];lock.setBounds(0,0,Math.round(38*scale),Math.round(38*scale));parent.setCompoundDrawables(lock,null,null,null);parent.setCompoundDrawablePadding(Math.round(16*scale));
        int group=lock.getBounds().width()+parent.getCompoundDrawablePadding()+Math.round(parent.getPaint().measureText(parent.getText().toString()));
        int inset=Math.max(0,(parent.getLayoutParams().width-group)/2);parent.setPadding(inset,0,inset,0);
        int padding=Math.round(18*scale);leave.setPadding(padding,0,padding,0);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
    private void load(Context context){
        ActivityManager memory=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean small=Build.VERSION.SDK_INT<=19 || memory!=null && (UiCompat.lowRam(memory) || memory.getMemoryClass()<=128);
        loader.execute(()->{
            Bitmap decoded=null;BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inSampleSize=small?2:1;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            try(InputStream input=context.getAssets().open("child-rest/"+ART[theme]+".webp")){decoded=BitmapFactory.decodeStream(input,null,options);}catch(IOException|OutOfMemoryError ignored){}
            final Bitmap ready=decoded;main.post(()->{if(released){if(ready!=null)ready.recycle();return;}bitmap=ready;background.setImageBitmap(ready);});
        });
    }
    void release(){released=true;loader.shutdownNow();background.setImageDrawable(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
