package home.quiet.tv;

import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;

/** A concise action label confined to the editor's scrolling viewport. */
final class QuickActionTooltip extends View {
    private final ManagementUi ui;
    private final View viewport;
    private final Paint surface=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect bounds=new Rect(),visible=new Rect(),stageBounds=new Rect();
    private final RectF pill=new RectF();
    private final Point anchorPosition=new Point(),stagePosition=new Point();
    private View target;
    private String caption="";

    QuickActionTooltip(ManagementUi ui,View viewport){
        super(ui);this.ui=ui;this.viewport=viewport;
        setVisibility(GONE);setFocusable(false);
        androidx.core.view.ViewCompat.setImportantForAccessibility(this,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.setColor(Color.WHITE);text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));text.setTextSize(ui.px(12));
        ui.stage.addView(this,new FrameLayout.LayoutParams(1,1));
    }

    void show(View anchor,String label){target=anchor;caption=label;reposition();}
    void reposition(){
        if(target==null)return;
        if(!target.isShown()||!target.isEnabled()||!target.hasWindowFocus()||!(target.hasFocus()||target.isHovered())
                ||!viewport.getGlobalVisibleRect(bounds)||!target.getGlobalVisibleRect(visible,anchorPosition)||!bounds.contains(visible)
                ||visible.width()<target.getWidth()-2||visible.height()<target.getHeight()-2
                ||!ui.stage.getGlobalVisibleRect(stageBounds,stagePosition)){hide();return;}
        int width=Math.max(ui.px(44),Math.round(text.measureText(caption))+ui.px(20)),height=ui.px(24),gap=ui.px(5);
        if(width>bounds.width()){hide();return;}
        int x=Math.max(bounds.left,Math.min(bounds.right-width,anchorPosition.x+target.getWidth()/2-width/2));
        int y=anchorPosition.y-gap-height;
        if(y<bounds.top)y=anchorPosition.y+target.getHeight()+gap;
        if(y+height>bounds.bottom){hide();return;}
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)getLayoutParams();
        int left=x-stagePosition.x,top=y-stagePosition.y;
        if(p.leftMargin!=left||p.topMargin!=top||p.width!=width||p.height!=height){
            p.width=width;p.height=height;p.leftMargin=left;p.topMargin=top;setLayoutParams(p);
        }
        if(getVisibility()!=VISIBLE){setVisibility(VISIBLE);bringToFront();}
        invalidate();
    }
    void hide(View anchor){if(target==anchor)hide();}
    void hide(){target=null;setVisibility(GONE);}
    void reset(){hide();}
    void destroy(){hide();ui.stage.removeView(this);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)hide();}
    @Override protected void onDraw(Canvas canvas){
        float border=Math.max(1,ui.px(.6f));pill.set(border/2,border/2,getWidth()-border/2,getHeight()-border/2);
        surface.setStyle(Paint.Style.FILL);surface.setColor(0xed30343a);canvas.drawRoundRect(pill,ui.px(8),ui.px(8),surface);
        surface.setStyle(Paint.Style.STROKE);surface.setStrokeWidth(border);surface.setColor(0x66ffffff);canvas.drawRoundRect(pill,ui.px(8),ui.px(8),surface);
        canvas.drawText(caption,(getWidth()-text.measureText(caption))/2,(getHeight()-text.ascent()-text.descent())/2,text);
    }
}
