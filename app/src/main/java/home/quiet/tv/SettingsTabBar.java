package home.quiet.tv;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Layout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

/** One focus capsule for the settings tabs, independent of page and key dispatch. */
@SuppressLint("ViewConstructor") // The native settings toolbar supplies its scene scale.
class SettingsTabBar extends LinearLayout {
    private static final LinearInterpolator LINEAR_TIME=new LinearInterpolator();
    private final float unit;
    private final boolean expandFocus;
    private final RectF capsule=new RectF(), destination=new RectF();
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusChanged=(before,after)->updateFocus(true);
    private View focusedTab;
    private ValueAnimator slide;
    private CapsuleSlide trajectory;
    private Boolean keySlide;
    private int layoutWidth;

    SettingsTabBar(Context context,float unit) {
        this(context,unit,true);
    }

    SettingsTabBar(Context context,float unit,boolean expandFocus) {
        super(context);
        this.unit=unit;
        this.expandFocus=expandFocus;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        fill.setColor(UiPalette.TEXT);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusChanged);
        updateFocus(false);
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChanged);
        stopSlide();
        focusedTab=null;
        destination.setEmpty();
        setCapsule(0,0,0,0);
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        updateFocus(false);
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        super.onLayout(changed,left,top,right,bottom);
        int width=right-left;
        float scale=layoutWidth>0?width/(float)layoutWidth:1f;
        layoutWidth=width;
        updateFocus(false,scale);
    }

    void requestKeyFocus(View tab,boolean alongRow) {
        keySlide=alongRow;
        try {
            tab.requestFocus();
        } finally {
            keySlide=null;
        }
    }

    private void updateFocus(boolean animate) {
        updateFocus(animate,1f);
    }

    private void updateFocus(boolean animate,float scale) {
        View target=getFocusedChild();
        if (!hasWindowFocus() || !(target instanceof Tab) || !target.isEnabled() || target.getWidth()==0) target=null;

        RectF bounds=new RectF();
        boolean reduced=SettingsFocusMotion.reduced();
        if (target!=null) {
            bounds.set(target.getLeft(),target.getTop(),target.getRight(),target.getBottom());
            if (expandFocus && !reduced) bounds.inset(-bounds.width()*.03f,-bounds.height()*.03f);
        }
        if (target==focusedTab && bounds.equals(destination)) return;

        boolean adjacent=target!=null && focusedTab!=null
                && Math.abs(indexOfChild(target)-indexOfChild(focusedTab))==1;
        boolean resize=target!=null && target==focusedTab && slide!=null;
        long duration=getResources().getInteger(R.integer.quiet_motion_panel_ms);
        if (resize) duration=Math.max(1,slide.getDuration()-slide.getCurrentPlayTime());
        float progress=slide==null?0:slide.getAnimatedFraction();
        CapsuleSlide previous=trajectory;
        boolean alongRow=keySlide==null?adjacent:keySlide;
        focusedTab=target;
        destination.set(bounds);
        stopSlide();
        if (reduced || target==null || capsule.isEmpty() || !(resize || animate && alongRow)) {
            setCapsule(bounds.left,bounds.top,bounds.right,bounds.bottom);
            return;
        }

        // Retarget from the last drawn position and the velocity at the same animation fraction.
        RectF start=new RectF(capsule);
        if(resize){start.left*=scale;start.right*=scale;}
        CapsuleSlide motion=new CapsuleSlide(start,bounds,previous,progress,scale,duration);
        ValueAnimator animation=ValueAnimator.ofFloat(0,1);
        animation.setDuration(duration);
        animation.setInterpolator(LINEAR_TIME);
        animation.addUpdateListener(value->{
            float fraction=value.getAnimatedFraction();
            setCapsule(motion.left.position(fraction),motion.top.position(fraction),
                    motion.right.position(fraction),motion.bottom.position(fraction));
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ended) {
                if (slide!=ended) return;
                slide=null;
                trajectory=null;
                setCapsule(bounds.left,bounds.top,bounds.right,bounds.bottom);
            }
        });
        slide=animation;
        trajectory=motion;
        animation.start();
    }

    private void stopSlide() {
        if (slide==null) return;
        ValueAnimator previous=slide;
        slide=null;
        trajectory=null;
        previous.cancel();
    }

    private void setCapsule(float left,float top,float right,float bottom) {
        float changedLeft=capsule.isEmpty()?left:Math.min(left,capsule.left);
        float changedRight=Math.max(right,capsule.right);
        capsule.set(left,top,right,bottom);
        invalidate();
        for (int index=0;index<getChildCount();index++) {
            View child=getChildAt(index);
            if (child==focusedTab || child.getRight()>changedLeft && child.getLeft()<changedRight) {
                child.invalidate();
            }
        }
    }

    private static final class CapsuleSlide {
        final TabSlideCurve left,top,right,bottom;

        CapsuleSlide(RectF start,RectF end,CapsuleSlide previous,float progress,float scale,long duration) {
            left=edge(start.left,end.left,previous==null?null:previous.left,progress,scale,duration);
            top=edge(start.top,end.top,previous==null?null:previous.top,progress,1f,duration);
            right=edge(start.right,end.right,previous==null?null:previous.right,progress,scale,duration);
            bottom=edge(start.bottom,end.bottom,previous==null?null:previous.bottom,progress,1f,duration);
        }

        private static TabSlideCurve edge(float start,float end,TabSlideCurve previous,float progress,float scale,long duration) {
            return previous==null?new TabSlideCurve(start,end,duration)
                    :new TabSlideCurve(start,end,previous.velocity(progress)*scale,duration);
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        if (!capsule.isEmpty()) {
            if (!SettingsFocusMotion.reduced()) fill.setShadowLayer(11.52f*unit,0,6.72f*unit,0x55000000);
            float radius=capsule.height()/2;
            canvas.drawRoundRect(capsule,radius,radius,fill);
            fill.clearShadowLayer();
        }
        super.dispatchDraw(canvas);
    }

    @SuppressLint("ViewConstructor") // Tabs are created by their owning settings bar.
    static final class Tab extends KeyFeedback.Button {
        private static final int[] LABEL_COLORS={UiPalette.SECONDARY,UiPalette.INK,UiPalette.INK,UiPalette.SECONDARY};
        private final SettingsTabBar bar;
        private final TextPaint labelPaint=new TextPaint();
        private final TextPaint measurePaint=new TextPaint();
        private final Paint underlinePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF underlineBounds=new RectF();
        private Typeface regularLabel,selectedLabel;
        private boolean underline;

        Tab(SettingsTabBar bar) {
            super(bar.getContext());
            this.bar=bar;
        }

        void reserveLabelWidth(Typeface regular,Typeface selected) {
            regularLabel=regular;
            selectedLabel=selected;
            setTypeface(isSelected()?selected:regular);
        }

        @Override protected void onMeasure(int width,int height) {
            if(regularLabel!=null && MeasureSpec.getMode(width)!=MeasureSpec.EXACTLY){
                // Keep the toolbar stable when selection changes the font weight.
                measurePaint.set(getPaint());
                measurePaint.setTypeface(regularLabel);
                float regular=Layout.getDesiredWidth(getText(),measurePaint);
                measurePaint.setTypeface(selectedLabel);
                float selected=Layout.getDesiredWidth(getText(),measurePaint);
                int desired=(int)Math.ceil(Math.max(regular,selected))+getCompoundPaddingLeft()+getCompoundPaddingRight();
                width=MeasureSpec.makeMeasureSpec(resolveSize(desired,width),MeasureSpec.EXACTLY);
            }
            super.onMeasure(width,height);
        }

        void selectedSurface(int color,boolean light) {
            StateListDrawable background=new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_enabled,android.R.attr.state_focused,
                    android.R.attr.state_window_focused},new ColorDrawable(Color.TRANSPARENT));
            background.addState(new int[]{android.R.attr.state_selected},
                    ((ManagementUi)getContext()).surface(color,color,0,999));
            background.addState(new int[0],new ColorDrawable(Color.TRANSPARENT));
            setBackgroundDrawable(background);
            setTextColor(new ColorStateList(new int[][]{
                    {-android.R.attr.state_enabled,android.R.attr.state_selected},
                    {-android.R.attr.state_enabled},
                    {android.R.attr.state_focused,android.R.attr.state_window_focused},
                    {android.R.attr.state_selected},{}},
                    new int[]{light?UiPalette.INK_FAINT:UiPalette.FAINT,
                            UiPalette.FAINT,UiPalette.INK,light?UiPalette.INK:UiPalette.TEXT,UiPalette.SECONDARY}));
        }

        @Override public void setEnabled(boolean enabled) {
            boolean changed=isEnabled()!=enabled;
            super.setEnabled(enabled);
            if (bar!=null && changed && isFocused()) bar.updateFocus(false);
        }

        void underlineSelection() {
            underline=true;
            selectedSurface(Color.TRANSPARENT,false);
        }

        @Override protected void onDraw(Canvas canvas) {
            Layout layout=getLayout();
            boolean overlaps=getRight()>bar.capsule.left && getLeft()<bar.capsule.right;
            if (bar.slide==null || layout==null || !overlaps && !isFocused()) {
                super.onDraw(canvas);
            } else {
                drawSlidingLabel(canvas,layout);
            }
            if (underline && isSelected()) {
                float center=getWidth()/2f, height=3*bar.unit, halfWidth=9*bar.unit;
                underlinePaint.setColor(bar.capsule.contains(getLeft()+center,getBottom()-height/2)
                        ?UiPalette.INK:UiPalette.TEXT);
                int saved=canvas.save();
                canvas.translate(getScrollX(),getScrollY());
                underlineBounds.set(center-halfWidth,getHeight()-height,center+halfWidth,getHeight());
                canvas.drawRoundRect(underlineBounds,height/2,height/2,underlinePaint);
                canvas.restoreToCount(saved);
            }
        }

        private void drawSlidingLabel(Canvas canvas,Layout layout) {
            // Keep the labels in place and use dark ink only where the moving capsule covers them.
            // A single-line TextView may scroll its wide internal layout to center the label.
            float origin=getScrollX()-getLeft();
            float left=bar.capsule.left+origin, right=bar.capsule.right+origin;
            float edge=1/(right-left+1);
            labelPaint.set(getPaint());
            labelPaint.setColor(UiPalette.TEXT);
            labelPaint.setShader(new LinearGradient(left-.5f,0,right+.5f,0,LABEL_COLORS,
                    new float[]{0,edge,1-edge,1},Shader.TileMode.CLAMP));
            canvas.drawText(getText().toString(),getCompoundPaddingLeft()+layout.getLineLeft(0),getBaseline(),labelPaint);
            labelPaint.setShader(null);
        }
    }
}
