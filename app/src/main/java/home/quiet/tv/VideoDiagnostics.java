package home.quiet.tv;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded media diagnostics. Never include exception messages, names, paths or URLs. */
final class VideoDiagnostics {
    private static final AtomicLong uploads=new AtomicLong();
    static long nextUpload(){return uploads.incrementAndGet();}
    static final class Failure extends IOException {
        final String stage,reason;
        Failure(String stage,String reason,String message){super(message);this.stage=stage;this.reason=reason;}
    }
    static Failure failure(String stage,String reason,String message){return new Failure(stage,reason,message);}
    static String reason(Throwable error){
        if(error instanceof Failure)return ((Failure)error).reason;
        if(error instanceof OutOfMemoryError)return "out_of_memory";
        if(error instanceof EOFException)return "incomplete_input";
        if(error instanceof FileNotFoundException)return "file_missing_or_denied";
        if(error instanceof java.net.SocketTimeoutException)return "io_timeout";
        if(error instanceof InterruptedIOException)return "io_interrupted";
        if(error instanceof SecurityException)return "permission_denied";
        return error instanceof IOException?"io_error":"platform_error";
    }
    static String failureDetails(Throwable error){
        String stage=error instanceof Failure?" check="+((Failure)error).stage:"";
        return "reason="+reason(error)+stage+" "+causes(error);
    }
    static String causes(Throwable error){
        StringBuilder out=new StringBuilder();
        for(int n=0;error!=null&&n<6;n++,error=error.getCause()){
            if(n>0)out.append(' ');out.append("cause").append(n).append('=').append(token(error.getClass().getName()));
            if(error.getCause()==error)break;
        }
        return out.toString();
    }
    static String token(String value){return value!=null&&value.length()<=160&&value.matches("[A-Za-z0-9_.$-]+")?value:"unknown";}
    static String mime(String value){return value!=null&&value.length()<=96&&value.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")?value:"unknown";}
    static String wallpaper(String id){return id!=null&&id.matches("user-[a-f0-9-]{36}")?id:"builtin";}
    static String platformReason(int what,int extra){
        if(what==100)return "media_server_died";
        switch(extra){
            case -1004:return "media_io";
            case -1007:return "malformed_media";
            case -1010:return "unsupported_media";
            case -110:return "media_timeout";
            case Integer.MIN_VALUE:return "media_system_error";
            default:return "media_unknown";
        }
    }
    static String container(File file){
        try(DataInputStream input=new DataInputStream(new FileInputStream(file))){
            int first=input.readInt(),second=input.readInt();
            if(first==0x1a45dfa3)return "ebml";
            if(second==0x66747970)return "iso_bmff";
            if(first==0x52494646)return "riff";
            if(first==0x4f676753)return "ogg";
        }catch(IOException ignored){}
        return "unknown";
    }
    private VideoDiagnostics(){}
}
