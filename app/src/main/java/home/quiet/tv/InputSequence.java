package home.quiet.tv;

import java.util.*;

/** Navigation sequences within one explicit probe window; no inferred physical-device identity. */
final class InputSequence {
    private static final class Down {final long at;final String focus;Down(long at,String focus){this.at=at;this.focus=focus;}}
    private final Map<String,Down> down=new LinkedHashMap<>();
    private int evicted;
    synchronized String key(String route,int device,int code,int action,int repeat,long at,String focus){
        String id=route+":"+device+":"+code;Down previous=down.get(id);
        if(action==0){
            String state=repeat>0?previous==null?"repeat_without_observed_down":"repeat":previous==null?"down":"down_before_observed_up";
            if(repeat==0){if(previous==null&&down.size()>=64){down.remove(down.keySet().iterator().next());evicted++;}down.put(id,new Down(at,focus));}
            return "sequence="+state;
        }
        if(action==1){down.remove(id);return "sequence="+(previous==null?"up_without_observed_down":"up")+" observedHoldMs="+(previous==null||at<previous.at?"unknown":at-previous.at)+" downFocus="+(previous==null?"unknown":previous.focus)+" upFocus="+focus;}
        return "sequence=other";
    }
    synchronized String end(){return "unmatchedDownInWindow="+down.size()+" trackingEvicted="+evicted+" evidence=window_boundary_or_missing_callback_not_proof_of_stuck_key";}
    synchronized void reset(){down.clear();evicted=0;}
}
