package home.quiet.tv;

/** Existing management page IDs remain stable for saved state and legacy entry points. */
final class SettingsPages {
    static final int DESKTOP=7,WEATHER=8,COUNT=9;
    static final int[] ORDER={DESKTOP,WEATHER,0,3,6,4,5};
    private static final String[] ZH={"应用管理","首页应用","应用排序","儿童模式","版本更新","诊断日志","文件传输","桌面显示","天气设置"};
    private static final String[] EN={"App Management","Home apps","App order","Child mode","Updates","Diagnostics","File transfer","Desktop","Weather"};
    static int valid(int page){return page>=0&&page<COUNT&&page!=1&&page!=2?page:0;}
    static String title(int page,boolean chinese){return (chinese?ZH:EN)[valid(page)];}
    private SettingsPages(){}
}
