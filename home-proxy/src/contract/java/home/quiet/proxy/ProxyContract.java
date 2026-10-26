package home.quiet.proxy;

/** Shared production wire contract. Targets are compiled in; callers cannot supply a launch target. */
public final class ProxyContract {
    private ProxyContract(){}
    public static final int VERSION=1;
    public static final String PACKAGE="home.quiet.tv.proxy";
    public static final String XIAOMI_PACKAGE="com.ts.launcher";
    public static boolean isPackage(String name){return PACKAGE.equals(name) || XIAOMI_PACKAGE.equals(name);}
    // Pin an existing route, including helpers configured before the package field existed.
    public static String packageFor(boolean xiaomi,boolean selected,String saved){
        if(selected)return isPackage(saved)?saved:PACKAGE;
        return xiaomi?XIAOMI_PACKAGE:PACKAGE;
    }
    public static final String HOME="home.quiet.proxy.ProxyHomeActivity";
    public static final String ENTRY="home.quiet.tv.HomeProxyEntryActivity";
    public static final String REQUEST="home.quiet.proxy.REQUEST";
    public static final String EPOCH="epoch",ORIGINAL="original",TOKEN="token";
    public static final String STATUS="status",PREPARE="prepare",ENABLE="enable",PAUSE="pause",ACK="ack";
    public static final String DEFER="defer";
    public static final String TARGET="TARGET",STOCK="ORIGINAL";
}
