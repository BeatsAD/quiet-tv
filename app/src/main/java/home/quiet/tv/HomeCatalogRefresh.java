package home.quiet.tv;

import java.util.*;

/** Main-thread catalog requests: compare in-flight inputs, never unfinished revisions. */
final class HomeCatalogRefresh {
    static final class Input {
        final List<String> pinned;
        final String order;
        final String visibility;
        final long child,cover,packages;
        final boolean homeApps;
        Input(List<String> pinned,String order,long child,long cover,long packages,boolean homeApps){
            this(pinned,order,child,cover,packages,homeApps,"");
        }
        Input(List<String> pinned,String order,long child,long cover,long packages,boolean homeApps,String visibility){
            this.pinned=Collections.unmodifiableList(new ArrayList<>(pinned));this.order=order;
            this.child=child;this.cover=cover;this.packages=packages;this.homeApps=homeApps;
            this.visibility=visibility;
        }
        @Override public boolean equals(Object value){
            if(!(value instanceof Input))return false;
            Input other=(Input)value;
            return pinned.equals(other.pinned)&&order.equals(other.order)&&child==other.child
                &&cover==other.cover&&packages==other.packages&&homeApps==other.homeApps&&visibility.equals(other.visibility);
        }
        @Override public int hashCode(){return Objects.hash(pinned,order,child,cover,packages,homeApps,visibility);}
    }
    private Input applied,inFlight;
    boolean loading(){return inFlight!=null;}
    boolean start(Input requested){
        if(loading()||requested.equals(applied))return false;
        inFlight=requested;return true;
    }
    boolean finish(Input latest){
        Input finished=inFlight;inFlight=null;
        if(finished==null||!finished.equals(latest))return false;
        applied=finished;return true;
    }
    void failed(){inFlight=null;}
}
