package home.quiet.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Non-interactive header overlay, so the existing about button keeps its focus identity. */
final class UpdateBadgeView extends View {
    private final View anchor;private final float unit;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean available;
    UpdateBadgeView(Context context,View anchor,float unit){super(context);this.anchor=anchor;this.unit=unit;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);setClickable(false);}
    void available(boolean value){available=value;anchor.setContentDescription(value?"关于，有新版本":"关于");invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!available||anchor.getWidth()==0)return;
        int[] button=new int[2],overlay=new int[2];anchor.getLocationInWindow(button);getLocationInWindow(overlay);
        float right=button[0]-overlay[0]+anchor.getWidth()+13*unit,top=button[1]-overlay[1]-7*unit;
        RectF bounds=new RectF(right-47*unit,top,right,top+17*unit);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xffd9363e);canvas.drawRoundRect(bounds,5*unit,5*unit,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,unit));paint.setColor(Color.WHITE);canvas.drawRoundRect(bounds,5*unit,5*unit,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextSize(10*unit);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics font=paint.getFontMetrics();canvas.drawText("新版本",bounds.centerX(),bounds.centerY()-(font.ascent+font.descent)/2,paint);
    }
}
