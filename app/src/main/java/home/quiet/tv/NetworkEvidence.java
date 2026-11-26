package home.quiet.tv;

import java.util.UUID;

/** One source attempt, using its own clock so telemetry never advances a transfer's policy clock. */
final class NetworkEvidence {
    final String parent,source,kind;
    final String attempt=UUID.randomUUID().toString().substring(0,12);
    final long started=System.nanoTime();
    volatile String stage="connect";long bytes;int status=Integer.MIN_VALUE,redirects;
    boolean verified;
    NetworkEvidence(String parent,String kind,String source){this.parent=parent;this.kind=kind;this.source=source;DiagnosticLog.info("network.source.begin",base());}
    static String request(){return UUID.randomUUID().toString();}
    void phase(String value){stage=value;}
    void status(int value){status=value;}
    void add(long count){bytes+=count;}
    String base(){return "request="+parent+" attempt="+attempt+" kind="+kind+" source="+source;}
    String result(String outcome,Throwable failure){return base()+" outcome="+outcome+" stage="+stage+" verifiedBody="+verified+" http="+(status==Integer.MIN_VALUE?"unknown":status)+" receivedBytes="+bytes+" redirects="+redirects+" durationMs="+Math.max(0,(System.nanoTime()-started)/1000000)
        +" errorType="+(failure==null?"none":failure.getClass().getSimpleName())+(failure==null?"":" "+NetworkDiagnostics.failureDetails(failure));}
    void finish(String outcome,Throwable failure){if(outcome.startsWith("verified"))verified=true;DiagnosticLog.info("network.source.result",result(outcome,failure));}
}
