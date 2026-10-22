package home.quiet.tv.xiaomihelper;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public final class HomeRedirectService extends AccessibilityService {
    private long lastRedirect=-1000;
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        HelperState state=new HelperState(this);
        String pkg=event.getPackageName()==null?"":event.getPackageName().toString();
        String cls=event.getClassName()==null?null:event.getClassName().toString();
        if(!RedirectPolicy.matches(state.enabled(),state.selected(),pkg,cls))return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastRedirect<1000)return;
        lastRedirect=now;
        state.redirect();
    }
    @Override public void onInterrupt(){}
}
