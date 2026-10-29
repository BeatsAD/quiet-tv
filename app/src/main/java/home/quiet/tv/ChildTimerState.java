package home.quiet.tv;

/** Permission, a live service and its health are separate observations. */
enum ChildTimerState {
    OFF("计时服务未授权"), CONNECTING("已授权，计时服务尚未连接"),
    READY("计时服务已连接，到时限制效果需在本机验证"),
    FAULT("计时服务异常，请重新启用并核对"), UNKNOWN("计时授权状态无法读取，服务尚未连接");
    final String message;
    ChildTimerState(String message){this.message=message;}
    static ChildTimerState read(Boolean allowed,boolean connected,String fault){
        if(connected)return fault.isEmpty()?READY:FAULT;
        if(Boolean.FALSE.equals(allowed))return OFF;
        if(!fault.isEmpty())return FAULT;
        return Boolean.TRUE.equals(allowed)?CONNECTING:UNKNOWN;
    }
}
