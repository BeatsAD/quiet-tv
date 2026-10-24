package home.quiet.tv;

/** System debugging and this application's verified shell connection are independent. */
final class AdbState {
    enum Debug { CHECKING, ENABLED, DISABLED, UNKNOWN }
    enum Connection { DISCONNECTED, CONNECTING, CONNECTED, FAILED }
    final Debug debug;
    final Connection connection;
    final String detail;
    final boolean paused;
    AdbState(Debug debug,Connection connection,String detail,boolean paused){
        this.debug=connection==Connection.CONNECTED?Debug.ENABLED:debug;
        this.connection=connection;this.detail=detail;this.paused=paused;
    }
    String label(){
        if(connection==Connection.CONNECTED)return "ADB 已连接";
        if(connection==Connection.CONNECTING)return "ADB 正在连接";
        if(debug==Debug.ENABLED)return "ADB 已开启";
        if(debug==Debug.DISABLED)return "ADB 未开启";
        return debug==Debug.CHECKING?"ADB 检测中":"ADB 状态待确认";
    }
    String description(){return label()+(paused?"  自动连接已暂停":"")+(detail.isEmpty()?"":"  "+detail);}
}
