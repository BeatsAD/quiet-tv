package home.quiet.tv;

/** A test receipt is never physical evidence. UI and computer confirmations share this gate. */
final class InputPairingState {
    static final long REQUEST_MS=30000,CONFIRM_MS=600000;
    final String request,owner,candidate,scope,revision;
    final long started;
    volatile String state="PENDING",label="",observed="",source="",failure="",savedKind="";
    long dispatchedAt;
    InputPairingState(String request,String owner,String candidate,String scope,String revision,long now){
        this.request=request;this.owner=owner;this.candidate=candidate;this.scope=scope;this.revision=revision;started=now;
    }
    boolean pending(long now){expire(now);return "PENDING".equals(state)||"PREPARING".equals(state);}
    void expire(long now){
        if(now<started || ("PENDING".equals(state)||"PREPARING".equals(state))&&now-started>REQUEST_MS
                ||"DISPATCHED_UNVERIFIED".equals(state)&&now-dispatchedAt>CONFIRM_MS)state="EXPIRED";
    }
    void dispatched(boolean opened,long now){
        expire(now);if(!"PREPARING".equals(state)&&!"DISPATCHING".equals(state))return;
        state=opened?"DISPATCHED_UNVERIFIED":"FAILED";dispatchedAt=now;
    }
    void confirm(String token,String currentScope,String currentRevision,boolean worked,String evidence,String note,long now){
        expire(now);
        if(!request.equals(token)||!"DISPATCHED_UNVERIFIED".equals(state))throw new IllegalStateException("测试已取消或过期，请重新测试");
        if(!scope.equals(currentScope)||!revision.equals(currentRevision))throw new IllegalStateException("入口或配置已变化，请重新测试");
        if(!"user".equals(evidence)&&!"agent".equals(evidence))throw new IllegalArgumentException("需要实际观察来源");
        if(note==null||note.trim().isEmpty()||note.length()>300)throw new IllegalArgumentException("请记录实际结果，最多300字");
        observed=note.trim();source=evidence;state=worked?"CONFIRMED":"REJECTED";
    }
    void cancel(){if(!"SAVED".equals(state))state="CANCELLED_UNVERIFIED";}
}
