package home.quiet.tv;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** A non-focusable result capsule owned by the window that performed the operation. */
final class OperationFeedback {
    enum Kind {SUCCESS,ERROR,PROGRESS,INFO}
    interface HomeAnchor {int top();}
    interface Space {void reserve(float height);}
    private static final WeakHashMap<Activity,WeakReference<OperationFeedback>> instances=new WeakHashMap<>();
    private static final Executor filter=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final Activity activity;
    private final float unit;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Host> windows=new ArrayList<>();
    private LinearLayout capsule;
    private SettingsSheetBackdrop backdrop;
    private Icon icon;
    private Host owner;
    private Kind kind;
    private long protectedUntil;
    private Runnable queued;
    private String text="";
    private int generation;
    private DockBackdropDrawable.Background homeBackground;
    private HomeAnchor homeAnchor;
    private static final class Host {
        final Dialog dialog;final LinearLayout box;final View actions;final Space space;
        Host(Dialog dialog,LinearLayout box,View actions,Space space){this.dialog=dialog;this.box=box;this.actions=actions;this.space=space;}
    }
    private OperationFeedback(Activity activity){this.activity=activity;unit=activity.getResources().getDisplayMetrics().widthPixels/960f;activity.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){hide();windows.clear();}});}
    static OperationFeedback get(Activity activity){
        WeakReference<OperationFeedback> ref=instances.get(activity);OperationFeedback value=ref==null?null:ref.get();
        if(value==null){value=new OperationFeedback(activity);instances.put(activity,new WeakReference<>(value));}
        return value;
    }
    static void success(Activity activity,String text){get(activity).show(Kind.SUCCESS,text);}
    static void error(Activity activity,String text){get(activity).show(Kind.ERROR,text);}
    static void info(Activity activity,String text){get(activity).show(Kind.INFO,text);}
    static View progressIcon(Context context){return new Icon(context,Kind.PROGRESS);}
    static void status(TextView view,Kind kind){float scale=view.getResources().getDisplayMetrics().widthPixels/960f;QuickControlIcon glyph=new QuickControlIcon(kind==Kind.SUCCESS?"circle-check":kind==Kind.PROGRESS?"loader-circle":"circle-exclamation-point");glyph.color(kind==Kind.SUCCESS?UiPalette.STATUS_SUCCESS:kind==Kind.ERROR?UiPalette.STATUS_ERROR:kind==Kind.PROGRESS?UiPalette.STATUS_PROGRESS:UiPalette.SECONDARY);int size=Math.round(16*scale);glyph.setBounds(0,0,size,size);view.setCompoundDrawables(glyph,null,null,null);view.setCompoundDrawablePadding(Math.round(8*scale));}
    static void clear(Activity activity){WeakReference<OperationFeedback> ref=instances.remove(activity);OperationFeedback value=ref==null?null:ref.get();if(value!=null){value.hide();value.windows.clear();}}
    void home(DockBackdropDrawable.Background background,HomeAnchor anchor){homeBackground=background;homeAnchor=anchor;}
    void watch(Dialog dialog,LinearLayout box,View actions,Space space){
        Host host=new Host(dialog,box,actions,space);
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){dismissVisible();windows.remove(host);windows.add(host);}
            public void onViewDetachedFromWindow(View view){windows.remove(host);if(owner==host)dismissVisible();}
        });
    }
    private int px(float value){return Math.round(value*unit);}
    void show(Kind next,String message){
        if(message==null||message.trim().isEmpty())return;
        // Resolve after the caller has closed its sheet or returned to its parent window.
        int request=++generation;main.postDelayed(()->{if(request==generation)present(next,message);},next==Kind.PROGRESS?200:0);
    }
    private Host currentWindow(){for(int i=windows.size()-1;i>=0;i--){Host host=windows.get(i);if(host.dialog.isShowing()&&host.box.getWindowToken()!=null)return host;}return null;}
    private void present(Kind next,String message){
        if(activity.isFinishing()||UiCompat.destroyed(activity))return;
        long remaining=defer(kind,next,protectedUntil,SystemClock.uptimeMillis());
        if(capsule!=null&&remaining>0){if(queued!=null)main.removeCallbacks(queued);queued=()->{queued=null;present(next,message);};main.postDelayed(queued,remaining);return;}
        Host host=currentWindow();
        if(capsule!=null&&owner==host&&kind==next&&text.equals(message)){scheduleHide(next,message);return;}
        dismissVisible();owner=host;kind=next;text=message;protectedUntil=next==Kind.ERROR?SystemClock.uptimeMillis()+1500:0;
        capsule=new LinearLayout(activity);capsule.setTag("operation-feedback");capsule.setOrientation(LinearLayout.HORIZONTAL);capsule.setGravity(Gravity.CENTER_VERTICAL);capsule.setPadding(px(20),px(10),px(20),px(10));capsule.setMinimumHeight(px(42));capsule.setFocusable(false);capsule.setClickable(false);capsule.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        icon=new Icon(activity,next);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(px(18),px(18));ip.rightMargin=px(12);capsule.addView(icon,ip);
        TextView copy=new TextView(activity);copy.setText(message);copy.setTextColor(UiPalette.TEXT);copy.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,14*unit);copy.setIncludeFontPadding(false);copy.setMaxLines(2);copy.setMaxWidth(px(host==null?360:Math.max(100,Math.min(360,host.box.getWidth()/unit-90))));capsule.addView(copy,new LinearLayout.LayoutParams(-2,-2));
        backdrop=new SettingsSheetBackdrop(capsule,unit,(canvas,paint)->drawBackground(canvas),filter).capsule();capsule.setBackgroundDrawable(backdrop);
        if(host!=null){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.gravity=Gravity.CENTER_HORIZONTAL;lp.topMargin=px(8);lp.bottomMargin=px(8);int index=host.box.indexOfChild(host.actions);host.box.addView(capsule,index<0?host.box.getChildCount():index,lp);}
        else{
            ViewGroup root=(ViewGroup)activity.getWindow().getDecorView();FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.leftMargin=lp.rightMargin=px(24);lp.bottomMargin=px(28);
            int[] point=new int[2];root.getLocationOnScreen(point);Rect visible=new Rect();root.getWindowVisibleDisplayFrame(visible);int bottom=point[1]+root.getHeight();lp.bottomMargin=Math.max(lp.bottomMargin,bottom-visible.bottom+px(12));
            int top=homeAnchor==null?0:homeAnchor.top();if(top>point[1]+px(120)&&top<bottom)lp.bottomMargin=Math.max(lp.bottomMargin,bottom-top+px(16));root.addView(capsule,lp);
        }
        LinearLayout shown=capsule;shown.post(()->{if(capsule!=shown)return;if(owner!=null)owner.space.reserve(shown.getHeight()/unit+16);shown.setVisibility(View.INVISIBLE);backdrop.prepare();shown.setVisibility(View.VISIBLE);UiCompat.announce(copy,message);});
        scheduleHide(next,message);
    }
    private void drawBackground(Canvas canvas){
        if(homeBackground!=null&&owner==null){homeBackground.draw(canvas,new Paint(Paint.FILTER_BITMAP_FLAG));return;}
        activity.getWindow().getDecorView().draw(canvas);
        if(owner!=null){View decor=owner.dialog.getWindow().getDecorView();int[] point=new int[2];decor.getLocationOnScreen(point);int save=canvas.save();canvas.translate(point[0],point[1]);decor.draw(canvas);canvas.restoreToCount(save);}
    }
    private final Runnable dismiss=this::hide;
    static long defer(Kind current,Kind next,long until,long now){return current==Kind.ERROR&&(next==Kind.SUCCESS||next==Kind.INFO)?Math.max(0,until-now):0;}
    static int duration(Kind type,String text,boolean dialog){return type==Kind.PROGRESS||type==Kind.ERROR&&dialog?0:type==Kind.ERROR||text.length()>18?5000:3000;}
    private void scheduleHide(Kind next,String message){
        main.removeCallbacks(dismiss);int timeout=duration(next,message,owner!=null);if(timeout==0)return;
        if(Build.VERSION.SDK_INT>=29)timeout=Api29.timeout(activity,timeout);
        main.postDelayed(dismiss,timeout);
    }
    void hide(){
        ++generation;
        dismissVisible();
    }
    private void dismissVisible(){
        main.removeCallbacks(dismiss);if(queued!=null){main.removeCallbacks(queued);queued=null;}
        if(owner!=null)owner.space.reserve(0);
        if(icon!=null){icon.stop();icon=null;}if(capsule!=null){ViewParent parent=capsule.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(capsule);capsule=null;}
        if(backdrop!=null){backdrop.release();backdrop=null;}owner=null;text="";
    }
    @android.annotation.TargetApi(29) private static final class Api29 {
        static int timeout(Activity activity,int value){android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)activity.getSystemService(Activity.ACCESSIBILITY_SERVICE);return manager==null?value:manager.getRecommendedTimeoutMillis(value,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT|android.view.accessibility.AccessibilityManager.FLAG_CONTENT_ICONS);}
    }
    private static final class Icon extends View {
        private final QuickControlIcon glyph;
        private final Kind type;
        private ValueAnimator spin;private float angle;
        Icon(Context context,Kind type){super(context);this.type=type;glyph=new QuickControlIcon(type==Kind.SUCCESS?"circle-check":type==Kind.PROGRESS?"loader-circle":"circle-exclamation-point");glyph.color(type==Kind.SUCCESS?UiPalette.STATUS_SUCCESS:type==Kind.ERROR?UiPalette.STATUS_ERROR:type==Kind.PROGRESS?UiPalette.STATUS_PROGRESS:UiPalette.SECONDARY);androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void start(){
            if(spin!=null||type!=Kind.PROGRESS||SettingsFocusMotion.reduced())return;
            spin=ValueAnimator.ofFloat(0,360);
            spin.setDuration(1000);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.addUpdateListener(value->{angle=(float)value.getAnimatedValue();invalidate();});
            spin.start();
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(getWindowVisibility()==VISIBLE)start();}
        @Override protected void onWindowVisibilityChanged(int visibility){
            super.onWindowVisibilityChanged(visibility);
            if(visibility==VISIBLE&&androidx.core.view.ViewCompat.isAttachedToWindow(this))start();
            else stop();
        }
        void stop(){if(spin!=null){spin.cancel();spin.removeAllUpdateListeners();spin=null;}}
        @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){int save=canvas.save();canvas.rotate(angle,getWidth()/2f,getHeight()/2f);glyph.setBounds(0,0,getWidth(),getHeight());glyph.draw(canvas);canvas.restoreToCount(save);}
    }
}
