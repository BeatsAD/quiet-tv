package home.quiet.tv;

/** Reduce ordered foreground/background events without resurrecting an older activity. */
final class UsageForeground {
    private String component="";
    private long at=Long.MIN_VALUE;
    boolean accept(String pkg,String cls,int type,long time,long now){
        if(pkg==null || cls==null || cls.isEmpty() || (type!=1 && type!=2) || time>now || time<at)return false;
        String next=pkg+"/"+(cls.startsWith(".")?pkg+cls:cls);
        if(type==2 && !component.isEmpty() && !component.equals(next))return false;
        component=next;at=time;return true;
    }
}
