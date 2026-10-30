package home.quiet.tv;

import java.io.*;
import java.util.Locale;

/** Legacy installer copy failures with fixed codes, never paths or exception text. */
final class UpdateInstallPreparation {
    static final class Failure extends IOException {
        final String reason,step,location;
        final long requiredBytes,availableBytes;
        Failure(String reason,String step,String location,long required,long available,Throwable cause){
            super("Update installation preparation failed",cause);
            this.reason=reason;this.step=step;this.location=location;requiredBytes=required;availableBytes=available;
        }
        String userMessage(){
            switch(reason){
                case "storage_full":return "电视存储空间不足，无法准备安装文件，请释放空间后重试";
                case "directory_unavailable":return "安装文件目录不可用，请通过电脑更新";
                case "copy_mismatch":return "安装文件副本校验失败，请重试";
                default:return "安装文件读写失败，请重试或通过电脑更新";
            }
        }
        String details(){return "reason="+reason+" step="+step+" location="+location+" requiredBytes="+requiredBytes+" availableBytes="+availableBytes;}
    }
    static final class Prepared {
        final File file;final long availableBytes;final boolean reused;
        Prepared(File file,long available,boolean reused){this.file=file;availableBytes=available;this.reused=reused;}
    }
    static Prepared prepare(File root,String location,File source,long bytes,String hash)throws Failure{
        String step="directory";long available=-1;
        try{
            if(root==null)throw new Failure("directory_unavailable",step,location,bytes,available,null);
            File directory=new File(root,"update-install");
            if(!directory.isDirectory()&&!directory.mkdirs()&&!directory.isDirectory())
                throw new Failure("directory_unavailable",step,location,bytes,space(root),null);
            available=space(directory);
            File target=new File(directory,hash+".apk");
            if(target.isFile()){
                try{UpdateTransfer.verify(target,bytes,hash);return new Prepared(target,available,true);}
                catch(IOException invalid){/* Replace only this artifact's unusable installer copy. */}
            }
            step="capacity";
            // File.getUsableSpace() also returns zero when the query is unsupported.
            // Only a positive measurement or an explicit ENOSPC establishes low capacity.
            // Rewriting this artifact's partial copy releases its existing allocation.
            long additional=bytes-(target.isFile()?Math.min(bytes,target.length()):0);
            if(available>0&&available<additional)throw new Failure("storage_full",step,location,bytes,available,null);
            step="copy";
            try(InputStream input=new FileInputStream(source);FileOutputStream output=new FileOutputStream(target)){
                byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
                step="sync";output.getFD().sync();
            }
            step="verify";UpdateTransfer.verify(target,bytes,hash);
            return new Prepared(target,available,false);
        }catch(Failure failure){throw failure;}
        catch(IOException|SecurityException failure){throw new Failure(reason(step,failure),step,location,bytes,available,failure);}
    }
    private static long space(File directory){try{long value=directory.getUsableSpace();return value>0?value:-1;}catch(SecurityException failure){return -1;}}
    static String reason(String step,Throwable failure){
        for(int n=0;failure!=null&&n<8;n++,failure=failure.getCause()){
            if(failure instanceof SecurityException)return "directory_unavailable";
            if(failure instanceof UpdateTransfer.IntegrityException)return "copy_mismatch";
            String message=failure.getMessage();if(message==null)continue;String lower=message.toLowerCase(Locale.ROOT);
            // Inspect known platform tokens locally, but never retain the original text.
            if(lower.contains("enospc")||lower.contains("no space left on device"))return "storage_full";
            if(lower.contains("eacces")||lower.contains("erofs")||lower.contains("permission denied")||lower.contains("read-only file system"))return "directory_unavailable";
        }
        return "directory".equals(step)?"directory_unavailable":"io_failed";
    }
}
