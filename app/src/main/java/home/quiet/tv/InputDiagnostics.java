package home.quiet.tv;

import android.app.Activity;
import android.os.SystemClock;
import android.view.*;

/** App-side evidence only; no text, coordinates, device names or system key interception. */
final class InputDiagnostics {
    private static long navigationAt,pointerAt,homeAt;
    private static int navigationCount,pointerCount;
    static synchronized String key(KeyEvent event){
        navigationAt=SystemClock.uptimeMillis();navigationCount++;
        return " scanCode="+event.getScanCode()+" inputSource="+event.getSource()+" deviceId="+event.getDeviceId()
            +" downTime="+event.getDownTime()+" ageMs="+Math.max(0,navigationAt-event.getEventTime())+" cancelled="+event.isCanceled();
    }
    static void pointer(Activity activity,MotionEvent event){
        if(event.getActionMasked()!=MotionEvent.ACTION_DOWN)return;
        synchronized(InputDiagnostics.class){pointerAt=SystemClock.uptimeMillis();pointerCount++;}
        DiagnosticLog.info("input.pointer","page="+activity.getClass().getSimpleName()+" inputSource="+event.getSource()+" "+focus(activity));
    }
    static String focus(Activity activity){
        View decor=activity.getWindow().getDecorView(),focused=activity.getCurrentFocus();
        return "windowFocus="+decor.hasWindowFocus()+" touchMode="+decor.isInTouchMode()+" focusClass="+(focused==null?"none":focused.getClass().getSimpleName());
    }
    static void home(Activity activity,boolean home,String via){
        if(home)synchronized(InputDiagnostics.class){homeAt=SystemClock.uptimeMillis();}
        DiagnosticLog.info("home.intent","via="+via+" home="+home+" "+focus(activity));
    }
    static synchronized String summary(){return "本进程导航事件数："+navigationCount+"  最近导航 uptimeMs："+navigationAt
        +"\n指针按下次数："+pointerCount+"  最近指针 uptimeMs："+pointerAt+"\n最近标准 HOME Intent uptimeMs："+homeAt
        +"\n范围：仅应用实际收到的事件。标准 HOME 通常由系统处理；未记录不代表实体键未发出。系统设置内的输入、输入法和其他服务拦截需系统侧证据。逐键校准沿用现有 RemoteKeyMap。";}
}
