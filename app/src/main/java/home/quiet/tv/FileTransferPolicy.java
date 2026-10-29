package home.quiet.tv;

import java.io.*;
import java.util.Locale;

/** Bounds apply to every source, including streams whose size is unknown. */
final class FileTransferPolicy {
    static final long MAX_FILE=200L*1024*1024,MAX_TOTAL=500L*1024*1024,RESERVE=32L*1024*1024;
    static final int MAX_FILES=200;
    static final long SESSION_MS=10L*60*1000;
    interface Active {boolean valid();}
    interface Progress {void changed(long received,long expected);}
    static boolean id(String value){return value!=null&&value.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
    static String name(String value)throws IOException{
        if(value==null)throw new IOException("缺少文件名");
        String clean=value.replaceAll("[\\p{Cntrl}/\\\\]"," ").trim();
        if(clean.isEmpty()||clean.equals(".")||clean.equals(".."))throw new IOException("文件名无效");
        return clean.substring(0,Math.min(160,clean.length()));
    }
    static boolean image(String name){return name!=null&&name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp)$");}
    static boolean video(String name){return name!=null&&name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mov|mkv|webm)$");}
    static long copy(InputStream input,OutputStream output,long expected,long maximum,Active active,Progress progress)throws IOException{
        if(expected==0||expected>maximum||maximum<=0)throw new IOException("文件超过大小限制或存储空间不足");
        byte[] buffer=new byte[16384];long total=0;
        while(expected<0||total<expected){
            if(!active.valid())throw new IOException("传输已取消");
            int count=input.read(buffer,0,(int)Math.min(buffer.length,expected<0?buffer.length:expected-total));
            if(count<0)break;
            total+=count;if(total>maximum)throw new IOException("文件超过大小限制或存储空间不足");
            output.write(buffer,0,count);progress.changed(total,expected);
        }
        if(total==0||expected>=0&&total!=expected)throw new IOException("文件传输不完整");
        if(!active.valid())throw new IOException("传输已取消");
        return total;
    }
    private FileTransferPolicy(){}
}
