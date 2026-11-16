package home.quiet.tv;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;

/** Unboxed contextual help and a non-focusable synchronization status. */
final class PreviewFeedbackView extends LinearLayout {
    private final TextView note,status;
    private final LinearLayout state;
    private final SyncIcon icon;
    private final boolean chinese;
    private boolean syncing;
    PreviewFeedbackView(ManagementUi ui){
        super(ui);chinese=ui.chinese();setOrientation(HORIZONTAL);setGravity(Gravity.TOP);setFocusable(false);setTag("preview-feedback");setMinimumHeight(ui.px(30));
        note=ui.muted("",12);note.setTag("preview-note");note.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.5f*ui.unit);note.setMaxLines(2);note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams np=new LayoutParams(0,-2,1);np.rightMargin=ui.px(12);addView(note,np);
        state=ui.row();state.setTag("preview-sync");state.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);state.setFocusable(false);state.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        icon=new SyncIcon(ui);LayoutParams ip=new LayoutParams(ui.px(12),ui.px(12));ip.rightMargin=ui.px(5);state.addView(icon,ip);
        status=ui.label("",12);status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.5f*ui.unit);status.setSingleLine();state.addView(status,new LayoutParams(-2,-2));addView(state,new LayoutParams(ui.px(84),-2));
        androidx.core.view.ViewCompat.setImportantForAccessibility(icon,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void render(String text,PreviewSyncState.Status phase,boolean visible){
        render(text,phase,visible,null);
    }
    void render(String text,PreviewSyncState.Status phase,boolean visible,String workingLabel){
        if(!note.getText().toString().equals(text))note.setText(text);
        int color=phase==PreviewSyncState.Status.SYNCED?0xff6ad49c:phase==PreviewSyncState.Status.FAILED?0xffef8585:0xff64b5f6;
        String label=phase==PreviewSyncState.Status.SYNCED?(chinese?"已同步":"Synced"):phase==PreviewSyncState.Status.FAILED?(chinese?"同步失败":"Sync failed"):workingLabel!=null?workingLabel:(chinese?"同步中":"Syncing");
        if(!status.getText().toString().equals(label))status.setText(label);
        status.setTextColor(color);icon.color(color,phase==PreviewSyncState.Status.FAILED);state.setVisibility(visible?VISIBLE:INVISIBLE);syncing=visible&&phase==PreviewSyncState.Status.SYNCING;animateIcon();
    }
    void stop(){syncing=false;icon.spin(false);}
    private void animateIcon(){icon.spin(syncing&&isShown()&&hasWindowFocus());}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);animateIcon();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(icon!=null)animateIcon();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    private static final class SyncIcon extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc=new RectF(3,3,21,21);
        private ValueAnimator animator;
        private float angle;
        private boolean failed;
        SyncIcon(android.content.Context context){super(context);setFocusable(false);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        void color(int color,boolean error){if(paint.getColor()!=color||failed!=error){paint.setColor(color);failed=error;invalidate();}}
        void spin(boolean enabled){
            if(enabled){if(animator!=null)return;animator=ValueAnimator.ofFloat(0,360);animator.setDuration(1000);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{angle=(float)a.getAnimatedValue();invalidate();});animator.start();}
            else if(animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;angle=0;invalidate();}
        }
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int save=canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);canvas.rotate(angle,12,12);canvas.drawArc(arc,205,135,false,paint);canvas.drawArc(arc,25,135,false,paint);Path arrows=new Path();arrows.moveTo(17,3);arrows.lineTo(21,3);arrows.lineTo(21,7);arrows.moveTo(7,21);arrows.lineTo(3,21);arrows.lineTo(3,17);canvas.drawPath(arrows,paint);if(failed){canvas.drawLine(12,8,12,12,paint);canvas.drawPoint(12,16,paint);}canvas.restoreToCount(save);}
    }
}
