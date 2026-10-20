package home.quiet.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;

/** One outline per setup window, independent of the control's selected/readonly appearance. */
final class SetupFocus implements ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnWindowFocusChangeListener,
        View.OnAttachStateChangeListener {
    private final ViewGroup host;
    private final float unit;
    interface Changed { void accept(View view); }
    private final Changed changed;
    private final RectF rect=new RectF();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;
    private ViewTreeObserver observer;
    private final Drawable outline=new Drawable(){
        @Override public void draw(Canvas canvas){if(!rect.isEmpty())canvas.drawRoundRect(rect,radius,radius,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };
    SetupFocus(ViewGroup host,float unit,Changed changed){
        this.host=host;this.unit=unit;this.changed=changed;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*unit);paint.setColor(Color.WHITE);
        host.getOverlay().add(outline);host.addOnAttachStateChangeListener(this);
        if(host.isAttachedToWindow())onViewAttachedToWindow(host);
    }
    static void passiveContainers(View view){
        if(view instanceof ScrollView){view.setFocusable(false);view.setFocusableInTouchMode(false);}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)passiveContainers(group.getChildAt(i));}
    }
    @Override public void onViewAttachedToWindow(View view){
        observer=host.getViewTreeObserver();observer.addOnGlobalFocusChangeListener(this);
        observer.addOnPreDrawListener(this);observer.addOnWindowFocusChangeListener(this);update();
    }
    @Override public void onViewDetachedFromWindow(View view){
        if(observer!=null && observer.isAlive()){observer.removeOnGlobalFocusChangeListener(this);observer.removeOnPreDrawListener(this);observer.removeOnWindowFocusChangeListener(this);}
        observer=null;
    }
    @Override public void onGlobalFocusChanged(View old,View focused){
        if(changed!=null && focused!=null)changed.accept(focused);
        // Parent focus pointers can still be changing inside this callback. Repaint the next frame
        // even when selected-only backgrounds do not invalidate themselves after a focus change.
        update();outline.invalidateSelf();host.postInvalidateOnAnimation();
    }
    @Override public void onWindowFocusChanged(boolean focused){update();outline.invalidateSelf();host.postInvalidateOnAnimation();}
    @Override public boolean onPreDraw(){passiveContainers(host);update();return true;}
    private void update(){
        View focused=host.findFocus();RectF next=new RectF();
        if(host.hasWindowFocus() && focused!=null && focused!=host && focused.isShown() && focused.isEnabled() && !(focused instanceof ScrollView)){
            Rect visible=new Rect();
            if(focused.getGlobalVisibleRect(visible)){
                int[] origin=new int[2],windowOrigin=new int[2];host.getLocationOnScreen(origin);focused.getRootView().getLocationOnScreen(windowOrigin);
                next.set(visible);next.offset(windowOrigin[0]-origin[0],windowOrigin[1]-origin[1]);next.inset(-3*unit,-3*unit);
                String tag=String.valueOf(focused.getTag());radius=(tag.startsWith("app-")?13:focused instanceof Button?27:11)*unit;
            }
        }
        if(!next.equals(rect) || outline.getBounds().width()!=host.getWidth() || outline.getBounds().height()!=host.getHeight()){
            rect.set(next);outline.setBounds(0,0,host.getWidth(),host.getHeight());outline.invalidateSelf();
        }
    }
}
