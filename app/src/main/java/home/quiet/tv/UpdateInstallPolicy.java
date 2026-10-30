package home.quiet.tv;

import java.io.*;
import java.util.Locale;

/** Fixed installation reasons shared by routing, UI and bounded diagnostics. */
final class UpdateInstallPolicy {
    static boolean session(int sdk){return sdk>=21&&sdk<=23;}
    static boolean fallback(String reason){return "session_unsupported".equals(reason)||"confirmation_missing".equals(reason);}
    static String result(int status,String message){
        // STATUS_FAILURE_STORAGE includes unavailable media, not just insufficient space.
        if(status==3)return "cancelled";
        if(status==2)return "blocked";
        if(status==4)return "invalid";
        if(status==5)return "conflict";
        if(status==7)return "incompatible";
        if(status==6||status==1){
            String text=message==null?"":message.toLowerCase(Locale.ROOT);
            if(text.contains("install_failed_insufficient_storage")||text.contains("enospc")||text.contains("no space left on device"))return "storage_full";
        }
        return status==6?"storage_unavailable":"install_failed";
    }
    static String ioReason(Throwable error){
        for(int n=0;error!=null&&n<8;n++,error=error.getCause()){
            if(error instanceof SecurityException)return "blocked";
            String text=error.getMessage();if(text!=null){text=text.toLowerCase(Locale.ROOT);if(text.contains("enospc")||text.contains("no space left on device"))return "storage_full";}
        }
        return "io_failed";
    }
    static String message(String reason){
        switch(reason){
            case "storage_full":return "安装存储空间不足，请释放空间后重试";
            case "storage_unavailable":return "系统安装存储不可用，请重试或通过电脑更新";
            case "blocked":return "系统限制了本次安装，请检查安装授权";
            case "cancelled":return "已取消安装";
            case "invalid":return "安装包校验失败，请重新下载";
            case "conflict":return "安装包与当前应用冲突，无法覆盖安装";
            case "incompatible":return "安装包与当前系统不兼容";
            case "interrupted":return "上次安装尚未完成，可重试";
            case "confirmation_missing":case "session_unsupported":return "无法打开系统安装器，可通过电脑更新";
            case "io_failed":return "安装文件读写失败，请重试";
            default:return "系统安装失败，请重试或通过电脑更新";
        }
    }
    static long copy(InputStream input,OutputStream output,long bytes,String hash)throws IOException{
        try{
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[32768];long total=0;int count;
            while((count=input.read(buffer))!=-1){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                if(count>bytes-total)throw new UpdateTransfer.IntegrityException();
                output.write(buffer,0,count);digest.update(buffer,0,count);total+=count;
            }
            if(total!=bytes||!UpdateTransfer.hex(digest.digest()).equals(hash))throw new UpdateTransfer.IntegrityException();
            return total;
        }catch(java.security.NoSuchAlgorithmException error){throw new IOException(error);}
    }
}
