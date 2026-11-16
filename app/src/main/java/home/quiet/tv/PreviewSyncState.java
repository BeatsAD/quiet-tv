package home.quiet.tv;

/** Results belong to one visual request; a failed request stays failed until replaced. */
final class PreviewSyncState {
    static final int LOCAL=1,BACKGROUND=2;
    enum Status {SYNCING,SYNCED,FAILED}
    private Object key;
    private long request;
    private int pending;
    private String error="";
    private Runnable observer;
    private DiagnosticOperation diagnostic;
    long begin(Object next,int required){
        if(key!=null&&key.equals(next))return request;
        if(diagnostic!=null)diagnostic.finish("CANCELLED","superseded");
        diagnostic=new DiagnosticOperation("preview.sync","required="+required);
        key=next;++request;pending=required;error="";changed();return request;
    }
    long request(){return request;}
    Status status(){return !error.isEmpty()?Status.FAILED:pending==0&&key!=null?Status.SYNCED:Status.SYNCING;}
    String error(){return error;}
    void ready(long expected,int part){if(expected!=request||key==null||!error.isEmpty()||(pending&part)==0)return;pending&=~part;if(diagnostic!=null){diagnostic.stage(part==LOCAL?"LOCAL_READY":"BACKGROUND_READY");if(pending==0)diagnostic.finish("COMPLETED","parts_ready_not_screen_verified");}changed();}
    void failed(long expected,String message){if(expected!=request||key==null||!error.isEmpty())return;error=message;if(diagnostic!=null)diagnostic.finish("FAILED","part_unavailable");changed();}
    void invalidate(){if(diagnostic!=null)diagnostic.finish("CANCELLED","view_inactive");key=null;++request;pending=LOCAL|BACKGROUND;error="";changed();}
    void observe(Runnable listener){observer=listener;}
    private void changed(){if(observer!=null)observer.run();}
}
