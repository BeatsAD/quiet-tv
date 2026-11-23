package home.quiet.tv;

/** Existing management page IDs remain stable for saved state and legacy entry points. */
final class SettingsPages {
    static final int APPS=0,CHILD=3,HELP=4,DIAGNOSTICS=5,FILES=6,DESKTOP=7,WEATHER=8,STARTUP=9,QUICK=10,GENERAL=11,ABOUT=12,TOOLS=13,ICONS=14,COUNT=15;
    static final int[] ORDER={DESKTOP,QUICK,APPS,ICONS,CHILD,TOOLS,GENERAL,HELP};
    private static final String[] ZH={"管理应用","首页应用","应用排序","儿童模式","更新与帮助","诊断日志","文件传输","桌面显示","天气设置","信号源配置","快捷控制","通用设置","关于 Quiet TV","工具","图标库"};
    private static final String[] EN={"Manage apps","Home apps","App order","Child mode","Updates & help","Diagnostic logs","File transfer","Desktop","Weather","Inputs","Quick controls","General","About Quiet TV","Tools","Icon library"};
    static int valid(int page){return page>=0&&page<COUNT&&page!=1&&page!=2?page:0;}
    static String title(int page,boolean chinese){return (chinese?ZH:EN)[valid(page)];}
    static int section(int page){
        switch(valid(page)){
            case DIAGNOSTICS:case ABOUT:return HELP;
            case FILES:return TOOLS;
            case WEATHER:case STARTUP:return GENERAL;
            default:return valid(page);
        }
    }
    private SettingsPages(){}
}
