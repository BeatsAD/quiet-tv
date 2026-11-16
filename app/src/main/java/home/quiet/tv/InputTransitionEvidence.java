package home.quiet.tv;

import android.os.SystemClock;

/** Diagnostic correlation only. It never controls a guard delay, route or input selection. */
final class InputTransitionEvidence {
    private static String id="none";
    private static long until;
    static synchronized void begin(String source,String target){
        id=DiagnosticOperation.nextId(System.currentTimeMillis());until=SystemClock.uptimeMillis()+HomeGuardPolicy.INPUT_TRANSITION_MS;
        DiagnosticLog.info("input.transition.begin",fields()+" source="+source+" target="+DiagnosticLog.anonymous("input_target",target));
    }
    static synchronized void selected(String source){
        if(SystemClock.uptimeMillis()>=until)begin(source,"unknown");
        until=SystemClock.uptimeMillis()+HomeGuardPolicy.INPUT_TRANSITION_MS;DiagnosticLog.info("input.transition.selected",fields()+" source="+source+" outcome=dispatch_or_selection_callback_unverified");
    }
    static synchronized String id(){return id;}
    static synchronized void target(String source,String value){if(SystemClock.uptimeMillis()>=until)begin(source,value);DiagnosticLog.info("input.transition.target",fields()+" source="+source+" target="+DiagnosticLog.anonymous("input_target",value));}
    static synchronized String fields(){long remaining=Math.max(0,until-SystemClock.uptimeMillis());return "transition="+id+" transitionActive="+(remaining>0)+" stabilityRemainingMs="+remaining+" timing=diagnostic_copy_not_guard_decision";}
    static synchronized void cancelled(){DiagnosticLog.info("input.transition.cancelled",fields());until=0;}
    private InputTransitionEvidence(){}
}
