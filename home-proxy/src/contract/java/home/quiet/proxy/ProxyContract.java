package home.quiet.proxy;

/** Shared production wire contract. Targets are compiled in; callers cannot supply a launch target. */
public final class ProxyContract {
    private ProxyContract(){}
    public static final int VERSION=1;
    public static final String PACKAGE="home.quiet.tv.proxy";
    public static final String HOME="home.quiet.proxy.ProxyHomeActivity";
    public static final String ENTRY="home.quiet.tv.HomeProxyEntryActivity";
    public static final String REQUEST="home.quiet.proxy.REQUEST";
    public static final String EPOCH="epoch",ORIGINAL="original",TOKEN="token";
    public static final String STATUS="status",PREPARE="prepare",ENABLE="enable",PAUSE="pause",ACK="ack";
    public static final String DEFER="defer";
    public static final String TARGET="TARGET",STOCK="ORIGINAL";
}
