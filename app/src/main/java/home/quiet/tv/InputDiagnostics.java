package home.quiet.tv;

import android.app.Activity;
import android.os.SystemClock;
import android.view.*;
import java.util.*;

/** App-side evidence only; no text, coordinates, device names or system key interception. */
final class InputDiagnostics {
    private static long navigationAt,pointerAt,homeAt;
    private static int navigationCount,pointerCount;
    private static final InputSequence sequence=new InputSequence();
    private static final Map<Integer,String> devices=new LinkedHashMap<>();
    private static final Map<Integer,InputAxisEvidence> motion=new LinkedHashMap<>();
    static synchronized String key(KeyEvent event){
        navigationAt=SystemClock.uptimeMillis();navigationCount++;
        return " scanCode="+event.getScanCode()+" inputSource="+event.getSource()+" deviceId="+event.getDeviceId()
            +" downTime="+event.getDownTime()+" ageMs="+Math.max(0,navigationAt-event.getEventTime())+" cancelled="+event.isCanceled()
            +" flags="+event.getFlags()+" repeatCount="+event.getRepeatCount()+" longPress="+event.isLongPress();
    }
    static void windowStarted(){sequence.reset();synchronized(motion){motion.clear();}synchronized(devices){devices.clear();}DiagnosticProbe.observed("input.probe.begin","scope=navigation_and_joystick callbacks_only");}
    static void windowEnded(String id){DiagnosticProbe.observed("input.probe.result","markedIncident="+id+" "+sequence.end());synchronized(motion){for(Map.Entry<Integer,InputAxisEvidence> item:motion.entrySet())DiagnosticProbe.observed("input.axes.result","markedIncident="+id+" deviceId="+item.getKey()+" axisSlots=X,Y,HAT_X,HAT_Y "+item.getValue().summary());}}
    static String target(View view){return view==null?"none":view.getClass().getSimpleName()+":"+view.getId()+":"+Integer.toHexString(System.identityHashCode(view));}
    static boolean window(View root,KeyEvent event,String source,KeyFeedback.Dispatch next){
        if(!DiagnosticProbe.active()||event==null||!DiagnosticLog.navigationKey(event.getKeyCode())||!DiagnosticProbe.allow("input.enhanced",0))return next.run();
        String route=source+":"+Integer.toHexString(System.identityHashCode(root)),focus=viewFocus(root),id=correlation(event);
        try{String paired=sequence.key(route,event.getDeviceId(),event.getKeyCode(),event.getAction(),event.getRepeatCount(),event.getEventTime(),focus);
            long axis; synchronized(motion){InputAxisEvidence value=motion.get(event.getDeviceId());axis=value==null?-1:value.at;}
            DiagnosticProbe.observed("input.window.received","key="+id+" route="+route+" focus="+focus+" flags="+event.getFlags()+" repeatCount="+event.getRepeatCount()+" cancelled="+event.isCanceled()+" longPress="+event.isLongPress()+" "+paired
                +" axisEventTime="+(axis<0?"unknown":axis)+" axisLink=temporal_same_logical_device_not_proof "+device(event.getDeviceId()));
        }catch(RuntimeException unavailable){}
        boolean handled=false,completed=false;try{handled=next.run();completed=true;return handled;}
        finally{DiagnosticProbe.observed("input.window.result","key="+id+" route="+route+" completed="+completed+" handled="+(completed?Boolean.toString(handled):"unknown")+" focus="+viewFocus(root));}
    }
    private static String viewFocus(View root){try{return target(root.findFocus());}catch(RuntimeException unavailable){return "unknown";}}
    static void focusChanged(View root,View before,View after,boolean navigation){
        if(DiagnosticProbe.active()&&DiagnosticProbe.allow("input.focus",50))DiagnosticProbe.observed("input.focus.change","root="+Integer.toHexString(System.identityHashCode(root))+" before="+target(before)+" after="+target(after)+" cause="+(navigation?"navigation_dispatch_pending":"programmatic_or_other_unknown"));
    }
    static void deviceChanged(int id){synchronized(devices){devices.remove(id);}DiagnosticLog.info("input.device.metadata",device(id));}
    static String device(int id){try{
        synchronized(devices){String cached=devices.get(id);if(cached!=null)return cached;}
        InputDevice device=InputDevice.getDevice(id);if(device==null)return "deviceId="+id+" metadata=unavailable btMapping=unknown kernelMapping=unknown";
        String value="deviceId="+id+" sources="+device.getSources()+" keyboardType="+device.getKeyboardType()+" descriptor="+(android.os.Build.VERSION.SDK_INT>=16?Api16.descriptor(device):"unsupported")
            +" virtual="+(android.os.Build.VERSION.SDK_INT>=16?Api16.virtual(device):"unknown")+(android.os.Build.VERSION.SDK_INT>=19?Api19.hardware(device):"")+" btMapping=unknown kernelMapping=unknown";
        synchronized(devices){if(devices.size()>=32)devices.remove(devices.keySet().iterator().next());devices.put(id,value);}return value;
    }catch(RuntimeException unavailable){return "deviceId="+id+" metadata=unknown type="+unavailable.getClass().getSimpleName();}}
    static void motion(MotionEvent event){
        try{motionObserved(event);}catch(RuntimeException unavailable){}
    }
    private static void motionObserved(MotionEvent event){
        if(!DiagnosticProbe.active()||event==null||(event.getSource()&InputDevice.SOURCE_CLASS_JOYSTICK)==0)return;
        int id=event.getDeviceId();long count; synchronized(motion){InputAxisEvidence state=motion.get(id);if(state==null){if(motion.size()>=8)return;state=new InputAxisEvidence();motion.put(id,state);}state.add(event.getEventTime(),new float[]{event.getAxisValue(MotionEvent.AXIS_X),event.getAxisValue(MotionEvent.AXIS_Y),event.getAxisValue(MotionEvent.AXIS_HAT_X),event.getAxisValue(MotionEvent.AXIS_HAT_Y)});count=state.count;}
        if(!DiagnosticProbe.allow("input.axes",250))return;
        try{InputDevice device=InputDevice.getDevice(id);StringBuilder fields=new StringBuilder();
            for(int axis:new int[]{MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y}){
                float value=event.getAxisValue(axis);if(Float.isNaN(value)||Float.isInfinite(value))continue;
                InputDevice.MotionRange range=device==null?null:device.getMotionRange(axis,event.getSource());
                fields.append(" axis=").append(axis).append(" value=").append(value);
                if(range!=null)fields.append(" min=").append(range.getMin()).append(" max=").append(range.getMax()).append(" flat=").append(range.getFlat()).append(" fuzz=").append(range.getFuzz());else fields.append(" range=unknown");
            }
            DiagnosticProbe.observed("input.axes","eventTime="+event.getEventTime()+" samples="+count+" "+device(id)+fields+" synthesizedKey=not_inferred");
        }catch(RuntimeException unavailable){DiagnosticProbe.observed("input.axes","state=unavailable type="+unavailable.getClass().getSimpleName());}
    }
    static String devicesSnapshot(){StringBuilder text=new StringBuilder("[导出时输入设备元信息]\n");try{int count=0;for(int id:InputDevice.getDeviceIds()){if(count++>=32){text.append("其余设备省略\n");break;}text.append(device(id)).append('\n');}}catch(RuntimeException unavailable){text.append("state=unknown type=").append(unavailable.getClass().getSimpleName()).append('\n');}return text.toString();}
    @androidx.annotation.RequiresApi(16) private static final class Api16 {static String descriptor(InputDevice device){return DiagnosticLog.anonymous("input",device.getDescriptor());}static boolean virtual(InputDevice device){return device.isVirtual();}}
    @androidx.annotation.RequiresApi(19) private static final class Api19 {static String hardware(InputDevice device){return " vendorId="+device.getVendorId()+" productId="+device.getProductId();}}
    private static boolean observed(KeyEvent event){return event!=null&&DiagnosticLog.navigationKey(event.getKeyCode())&&event.getRepeatCount()==0
        &&(event.getAction()==KeyEvent.ACTION_DOWN||event.getAction()==KeyEvent.ACTION_UP);}
    static String correlation(KeyEvent event){return event.getDeviceId()+":"+event.getDownTime()+":"+event.getEventTime()+":"+event.getKeyCode()+":"+event.getAction();}
    static boolean dispatch(Activity activity,KeyEvent event,String source,KeyFeedback.Dispatch next){
        if(!observed(event))return next.run();
        String id=correlation(event);route(id,source,"received",event,readFocus(activity));
        boolean handled=false,completed=false;
        try{handled=next.run();completed=true;return handled;}
        finally{route(id,source,"result",event,"completed="+completed+" handled="+(completed?Boolean.toString(handled):"unknown")+" "+readFocus(activity));}
    }
    static boolean accessibility(KeyEvent event,boolean filter,String foreground,KeyFeedback.Dispatch next){
        if(DiagnosticProbe.active()&&event!=null&&DiagnosticLog.navigationKey(event.getKeyCode())&&DiagnosticProbe.allow("input.accessibility_extra",0))
            DiagnosticProbe.observed("input.accessibility.extra","key="+correlation(event)+" flags="+event.getFlags()+" repeatCount="+event.getRepeatCount()+" cancelled="+event.isCanceled()+" longPress="+event.isLongPress());
        if(!observed(event))return next.run();
        String id=correlation(event);String context="filterRequestedAtReceive="+filter+" foregroundAtReceive="+component(foreground);
        route(id,"accessibility","received",event,context);boolean consumed=false,completed=false;
        try{consumed=next.run();completed=true;return consumed;}
        finally{route(id,"accessibility","result",event,"completed="+completed+" consumed="+(completed?Boolean.toString(consumed):"unknown")+" "+context);}
    }
    private static String component(String value){return value!=null&&value.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")?value:"unknown";}
    private static String readFocus(Activity activity){try{return "page="+activity.getClass().getSimpleName()+" "+focus(activity);}catch(RuntimeException unavailable){return "windowFocus=unknown diagnosticRead="+unavailable.getClass().getSimpleName();}}
    private static void route(String id,String source,String phase,KeyEvent event,String detail){
        try{DiagnosticLog.info("input.route."+phase,"key="+id+" source="+source+" keyCode="+event.getKeyCode()+" scanCode="+event.getScanCode()
            +" deviceId="+event.getDeviceId()+" inputSource="+event.getSource()+" eventTime="+event.getEventTime()+" uptimeMs="+SystemClock.uptimeMillis()+" "+detail);}catch(RuntimeException unavailable){/* Diagnostics must preserve dispatch results and exceptions. */}
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
        +"\ninput.route 以 deviceId、downTime、eventTime、键码与动作关联无障碍和应用回调；consumed 是本服务实际返回值，handled 是应用分发返回值。均不证明画面变化或其他服务的处理结果。仅记录导航键；标准 HOME 通常由系统处理，未记录不代表实体键未发出。系统设置内的输入需电脑端系统取证。";}
}
