package home.quiet.tv;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** One tap activates a focusable TV control; scrolling or an unmatched release never clicks. */
final class SettingsTouchClick implements View.OnTouchListener {
    private final int slop;
    private boolean tracking,dragged;
    private float x,y;
    private SettingsTouchClick(View view){slop=ViewConfiguration.get(view.getContext()).getScaledTouchSlop();}
    static void attach(View view){view.setOnTouchListener(new SettingsTouchClick(view));}
    @Override public boolean onTouch(View view,MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:tracking=true;dragged=false;x=event.getRawX();y=event.getRawY();break;
            case MotionEvent.ACTION_MOVE:if(tracking&&(Math.abs(event.getRawX()-x)>slop||Math.abs(event.getRawY()-y)>slop))dragged=true;break;
            case MotionEvent.ACTION_POINTER_DOWN:dragged=true;break;
            case MotionEvent.ACTION_CANCEL:tracking=false;view.setPressed(false);break;
            case MotionEvent.ACTION_UP:
                boolean tap=tracking&&!dragged&&!(view.isLongClickable()&&event.getEventTime()-event.getDownTime()>=ViewConfiguration.getLongPressTimeout())&&event.getX()>=0&&event.getX()<view.getWidth()&&event.getY()>=0&&event.getY()<view.getHeight();tracking=false;
                view.setPressed(false);if(tap){view.requestFocusFromTouch();view.performClick();return true;}return true;
        }
        return false;
    }
}
