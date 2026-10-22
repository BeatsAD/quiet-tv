package home.quiet.tv.xiaomihelper;

/** Exact homepage matching avoids redirecting settings or source screens in the same package. */
final class RedirectPolicy {
    static final String STOCK_PACKAGE="com.mitv.tvhome";
    static boolean supported(String model,int sdk){
        return sdk==23 && "MiTV4-ANSM0".equalsIgnoreCase(model);
    }
    static boolean matches(boolean enabled,String selected,String packageName,String className){
        if(!enabled || !STOCK_PACKAGE.equals(packageName) || className==null)return false;
        String name=className.startsWith(".")?packageName+className:className;
        return (packageName+"/"+name).equals(selected);
    }
    private RedirectPolicy(){}
}
