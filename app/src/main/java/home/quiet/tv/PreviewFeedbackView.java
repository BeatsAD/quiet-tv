package home.quiet.tv;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;

/** Unboxed contextual help and a non-focusable synchronization status. */
final class PreviewFeedbackView extends LinearLayout {
    private final TextView note,status,supporting;
    private final LinearLayout state;
    private final SyncIcon icon;
    private final boolean chinese;
    private boolean syncing;
    PreviewFeedbackView(ManagementUi ui){
        super(ui);chinese=ui.chinese();setOrientation(VERTICAL);setFocusable(false);setTag("preview-feedback");setMinimumHeight(ui.px(30));
        LinearLayout summary=ui.row();summary.setGravity(Gravity.TOP);addView(summary,new LayoutParams(-1,-2));
        note=ui.muted("",12);note.setTag("preview-note");note.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.5f*ui.unit);note.setMaxLines(2);note.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams np=new LayoutParams(0,-2,1);np.rightMargin=ui.px(12);summary.addView(note,np);
        state=ui.row();state.setTag("preview-sync");state.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);state.setFocusable(false);state.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        icon=new SyncIcon(ui);LayoutParams ip=new LayoutParams(ui.px(10),ui.px(10));ip.rightMargin=ui.px(5);state.addView(icon,ip);
        status=ui.label("",12);status.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.5f*ui.unit);status.setSingleLine();state.addView(status,new LayoutParams(-2,-2));summary.addView(state,new LayoutParams(ui.px(84),-2));
        supporting=ui.muted("",12);supporting.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,11.5f*ui.unit);supporting.setVisibility(GONE);
        addView(supporting,new LayoutParams(-1,-2));
        androidx.core.view.ViewCompat.setImportantForAccessibility(icon,androidx.core.view.ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void render(String text,PreviewSyncState.Status phase,boolean visible){
        render(text,phase,visible,null);
    }
    void render(String text,PreviewSyncState.Status phase,boolean visible,String workingLabel){
        if(!note.getText().toString().equals(text))note.setText(text);
        int color=phase==PreviewSyncState.Status.SYNCED?UiPalette.STATUS_SUCCESS:phase==PreviewSyncState.Status.FAILED?UiPalette.STATUS_ERROR:UiPalette.STATUS_PROGRESS;
        String label=phase==PreviewSyncState.Status.SYNCED?(chinese?"预览就绪":"Preview ready"):phase==PreviewSyncState.Status.FAILED?(chinese?"未完成":"Incomplete"):workingLabel!=null?workingLabel:(chinese?"预览加载中":"Loading preview");
        if(!status.getText().toString().equals(label))status.setText(label);
        status.setTextColor(color);icon.render(color,phase);state.setVisibility(visible?VISIBLE:INVISIBLE);syncing=visible&&phase==PreviewSyncState.Status.SYNCING;animateIcon();
    }
    void supportingText(String text){
        if(!supporting.getText().toString().equals(text))supporting.setText(text);
        supporting.setVisibility(text.isEmpty()?GONE:VISIBLE);
    }
    void stop(){syncing=false;icon.spin(false);}
    private void animateIcon(){icon.spin(syncing&&isShown()&&hasWindowFocus()&&!SettingsFocusMotion.reduced());}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);animateIcon();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(icon!=null)animateIcon();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    private static final class SyncIcon extends View {
        private QuickControlIcon glyph=new QuickControlIcon("loader-circle");
        private PreviewSyncState.Status phase;
        private ValueAnimator animator;
        private float angle;
        SyncIcon(android.content.Context context){super(context);setFocusable(false);}
        void render(int color,PreviewSyncState.Status value){if(phase!=value){phase=value;glyph=new QuickControlIcon(value==PreviewSyncState.Status.SYNCED?"circle-check":value==PreviewSyncState.Status.FAILED?"circle-exclamation-point":"loader-circle");}glyph.color(color);invalidate();}
        void spin(boolean enabled){
            if(enabled){if(animator!=null)return;animator=ValueAnimator.ofFloat(0,360);animator.setDuration(1000);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());animator.addUpdateListener(a->{angle=(float)a.getAnimatedValue();invalidate();});animator.start();}
            else if(animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;angle=0;invalidate();}
        }
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int save=canvas.save();canvas.rotate(angle,getWidth()/2f,getHeight()/2f);glyph.setBounds(0,0,getWidth(),getHeight());glyph.draw(canvas);canvas.restoreToCount(save);}
    }
}
