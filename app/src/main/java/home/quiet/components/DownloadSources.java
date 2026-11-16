package home.quiet.components;

/** Shared source order for application updates and optional components. */
public final class DownloadSources {
    private static final String[] HOSTS={"ghfast.top","gh-proxy.com","v4.gh-proxy.org"};
    private DownloadSources(){}
    public static String[] values(){return new String[]{"https://"+HOSTS[0]+"/","https://"+HOSTS[1]+"/","https://"+HOSTS[2]+"/",""};}
    public static boolean mirror(String host){for(String root:HOSTS)if(host.equals(root)||host.endsWith("."+root))return true;return false;}
}
