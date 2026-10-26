package home.quiet.tv;

import java.io.*;
import java.net.*;

/** User-facing check failures and bounded diagnostics without server text or local paths. */
final class UpdateFailure {
    static final class HttpException extends IOException {
        final int status;
        HttpException(int status){super("Update HTTP failure");this.status=status;}
    }
    static final class ManifestException extends IOException {
        ManifestException(IOException cause){super("Update manifest rejected",cause);}
    }
    static String message(String stage,Throwable error){
        if("cache".equals(stage))return "更新信息保存失败，请检查电视存储空间后重试";
        if("key".equals(stage))return "无法读取更新校验信息，请重试";
        String tls=WallpaperTls.failureCode(error);
        if(tls.startsWith("certificate")||tls.startsWith("tls")||tls.equals("peer_verification"))return "安全连接失败，请检查电视时间或系统证书";
        for(int n=0;error!=null&&n<8;n++,error=error.getCause()){
            if(error instanceof ManifestException)return "更新清单校验失败，请稍后重试";
            if(error instanceof SocketTimeoutException)return "检查更新超时，请检查网络后重试";
            if(error instanceof UnknownHostException)return "更新服务地址解析失败，请检查网络";
            if(error instanceof ConnectException)return "无法连接更新服务，请检查网络";
            if(error instanceof HttpException)return "更新服务暂不可用，请稍后重试";
        }
        return "暂时无法检查更新，请重试";
    }
    static String details(String stage,Throwable error){
        String result="stage="+stage+" "+NetworkDiagnostics.failureDetails(error);
        for(int n=0;error!=null&&n<8;n++,error=error.getCause()){
            if(error instanceof HttpException)result+=" status="+((HttpException)error).status;
            if(error instanceof ManifestException){
                String reason=error.getCause()==null?"":error.getCause().getMessage();
                result+=" manifest="+("Update signature verification failed".equals(reason)?"signature":"Stale update manifest".equals(reason)?"stale_revision":"Unknown update signing key".equals(reason)?"signing_key":"invalid");
            }
        }
        return result;
    }
}
