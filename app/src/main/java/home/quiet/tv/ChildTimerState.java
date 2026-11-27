package home.quiet.tv;

/** Permission, a live service and its health are separate observations. */
enum ChildTimerState {
    OFF("未开启","计时服务未开启"), CONNECTING("连接中","已授权，计时服务尚未连接"),
    READY("已连接","计时服务已连接"),
    FAULT("服务异常","计时服务异常，请重新启用并核对"), UNKNOWN("状态待核对","计时授权状态无法读取，服务尚未连接");
    final String label,message;
    ChildTimerState(String label,String message){this.label=label;this.message=message;}
    static ChildTimerState read(Boolean allowed,boolean connected,String fault){
        if(connected)return fault.isEmpty()?READY:FAULT;
        if(Boolean.FALSE.equals(allowed))return OFF;
        if(!fault.isEmpty())return FAULT;
        return Boolean.TRUE.equals(allowed)?CONNECTING:UNKNOWN;
    }
}
