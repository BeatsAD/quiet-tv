package home.quiet.tv;

import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;

/** A presentation snapshot of the existing restore journal and any live work. */
final class DesktopRestoreState {
    enum Phase {
        IDLE("idle"), RESTORING("restoring"), RECOVERING("recovering"),
        COMPLETE("complete"), ROLLED_BACK("rolled-back"), FAILED("failed"), NEEDS_REVIEW("needs-review");

        final String wire;
        Phase(String wire){this.wire=wire;}
    }

    final Phase phase;
    final String taskId,message;
    private final String recordPhase,before,target;

    private DesktopRestoreState(Phase phase,String taskId,String message,String recordPhase,String before,String target){
        this.phase=phase;this.taskId=taskId;this.message=message;
        this.recordPhase=recordPhase;this.before=before;this.target=target;
    }

    static DesktopRestoreState idle(){return new DesktopRestoreState(Phase.IDLE,"","","","","");}

    static DesktopRestoreState fromJournal(JSONObject record,String problem){
        if(record==null)return idle();
        String phase=record.optString("phase"),before=record.optString("before"),target=record.optString("target");
        if("complete".equals(phase))return new DesktopRestoreState(Phase.COMPLETE,"","桌面配置已恢复",phase,before,target);
        if("rolled-back".equals(phase))return new DesktopRestoreState(Phase.ROLLED_BACK,"","已恢复到操作前的桌面配置",phase,before,target);
        return new DesktopRestoreState(Phase.NEEDS_REVIEW,"",
                problem.isEmpty()?"桌面恢复尚未完成，请核对恢复记录":problem,phase,before,target);
    }

    static DesktopRestoreState unreadable(IOException failure){
        return new DesktopRestoreState(Phase.NEEDS_REVIEW,"",failure.getMessage(),"unknown","","");
    }

    DesktopRestoreState restoring(String taskId){
        return new DesktopRestoreState(Phase.RESTORING,taskId,"正在恢复桌面",recordPhase,before,target);
    }

    DesktopRestoreState recovering(){
        return new DesktopRestoreState(Phase.RECOVERING,"","正在核对并恢复原桌面配置",recordPhase,before,target);
    }

    DesktopRestoreState afterAttempt(DesktopRestoreState attempt,String problem){
        // A failure before creating a restore journal must not present an earlier restore as its result.
        if(!problem.isEmpty()&&!needsReview()&&recordPhase.equals(attempt.recordPhase)
                &&before.equals(attempt.before)&&target.equals(attempt.target)){
            return new DesktopRestoreState(Phase.FAILED,"",problem,recordPhase,before,target);
        }
        return this;
    }

    boolean running(){return phase==Phase.RESTORING||phase==Phase.RECOVERING;}
    boolean needsReview(){return phase==Phase.NEEDS_REVIEW;}
    boolean canChangeBackups(){return !running()&&!needsReview();}
    boolean coverDesktop(){return needsReview()||phase==Phase.RECOVERING||(phase==Phase.RESTORING&&!taskId.isEmpty());}

    JSONObject lastRecord()throws JSONException {
        JSONObject result=new JSONObject();
        if(!recordPhase.isEmpty())result.put("phase",recordPhase).put("before",before).put("target",target);
        return result;
    }

    JSONObject json()throws JSONException {
        return new JSONObject().put("phase",phase.wire).put("message",message).put("canChangeBackups",canChangeBackups()).put("canRetry",needsReview());
    }
}
