package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.security.SecureRandom;

/** One fixed diagnostic ZIP, a random URL, and a ten-minute page-scoped lifetime. */
final class DiagnosticsDownloadServer extends NanoHTTPD {
    static final long LIFETIME_MS=10L*60*1000;
    private final File file;
    private final String host,path;
    private final long started=System.nanoTime();
    private volatile boolean closed;
    DiagnosticsDownloadServer(String host,File file){
        super(host,0);this.host=host;this.file=file;
        byte[] token=new byte[16];new SecureRandom().nextBytes(token);StringBuilder text=new StringBuilder();
        for(byte b:token)text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));path="/"+text+"/QuietTV-diagnostics.zip";
    }
    String address(){return "http://"+host+":"+getListeningPort()+path;}
    @Override public void stop(){closed=true;super.stop();}
    @Override public Response serve(IHTTPSession session){
        Response response;
        if(closed || (System.nanoTime()-started)/1000000>=LIFETIME_MS)response=newFixedLengthResponse(Response.Status.GONE,"text/plain","Expired");
        else if(!path.equals(session.getUri()))response=newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Not found");
        else if(session.getMethod()!=Method.GET)response=newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,"text/plain","GET only");
        else try{response=newFixedLengthResponse(Response.Status.OK,"application/zip",new FileInputStream(file),file.length());response.addHeader("Content-Disposition","attachment; filename=QuietTV-diagnostics.zip");}
        catch(IOException failure){response=newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain","Unavailable");}
        response.addHeader("Cache-Control","no-store");response.addHeader("Referrer-Policy","no-referrer");response.addHeader("X-Content-Type-Options","nosniff");response.addHeader("Connection","close");return response;
    }
}
