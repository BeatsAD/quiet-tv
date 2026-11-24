package home.quiet.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** The same artwork, label and focus layout for apps and downloadable icons. */
class IconGridTile extends FrameLayout {
    static final int COLUMNS=6;
    final ImageView image;
    final TextView name;
    private final AppManagementActivity activity;
    private final Paint focusPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF focusBounds=new RectF();
    private boolean squareArt;

    IconGridTile(AppManagementActivity activity,ImageView image){
        super(activity);
        this.activity=activity;
        this.image=image;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipChildren(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(image);
        name=activity.center("",14);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        addView(name);
        SettingsTouchClick.attach(this);
    }

    void squareArt(boolean value){
        if(squareArt==value)return;
        squareArt=value;
        requestLayout();
    }

    @Override protected void onFocusChanged(boolean focused,int direction,android.graphics.Rect previous){
        super.onFocusChanged(focused,direction,previous);
        name.setTypeface(activity.navigationTypeface(focused));
        invalidate();
    }

    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);invalidate();}

    @Override protected void dispatchDraw(Canvas canvas){
        if(isFocused()&&hasWindowFocus()){
            focusPaint.setColor(0x26ffffff);
            focusBounds.set(0,0,getWidth(),getHeight());
            float radius=activity.px(16);
            SurfaceCorners.draw(canvas,focusBounds,radius,radius,focusPaint);
        }
        super.dispatchDraw(canvas);
    }

    @Override protected void onMeasure(int width,int height){
        int available=MeasureSpec.getSize(width);
        int artWidth=Math.max(1,available-activity.px(12));
        int artHeight=Math.max(1,Math.round(artWidth*9/16f));
        int labelHeight=activity.px(18);
        setMeasuredDimension(available,artHeight+labelHeight+activity.px(28));
        image.measure(MeasureSpec.makeMeasureSpec(squareArt?artHeight:artWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(artHeight,MeasureSpec.EXACTLY));
        name.measure(MeasureSpec.makeMeasureSpec(artWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(labelHeight,MeasureSpec.EXACTLY));
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int imageLeft=(getWidth()-image.getMeasuredWidth())/2;
        int imageTop=activity.px(10);
        int labelLeft=(getWidth()-name.getMeasuredWidth())/2;
        int labelTop=imageTop+image.getMeasuredHeight()+activity.px(6);
        image.layout(imageLeft,imageTop,imageLeft+image.getMeasuredWidth(),imageTop+image.getMeasuredHeight());
        name.layout(labelLeft,labelTop,labelLeft+name.getMeasuredWidth(),labelTop+name.getMeasuredHeight());
    }
}
