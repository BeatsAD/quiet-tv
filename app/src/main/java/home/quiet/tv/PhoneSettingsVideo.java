package home.quiet.tv;

import fi.iki.elonen.NanoHTTPD;
import java.io.*;

/** Safari probes and seeks with byte ranges. Never buffer a wallpaper video in memory. */
final class PhoneSettingsVideo {
    private PhoneSettingsVideo(){}
    static String mime(File file)throws IOException{
        byte[] header=new byte[4096];int total=0;try(InputStream input=new FileInputStream(file)){int n;while(total<header.length&&(n=input.read(header,total,header.length-total))>0)total+=n;}return mime(header,total);
    }
    static String mime(byte[] header,int size){
        // Stored .mp4 paths may contain MKV/MOV; codec MIME (video/avc) is not a container MIME.
        if(size>=12){String box=new String(header,4,4,Charsets.UTF_8),brand=new String(header,8,4,Charsets.UTF_8);if(box.equals("ftyp"))return brand.equals("qt  ")?"video/quicktime":"video/mp4";if(new String(header,0,4,Charsets.UTF_8).equals("RIFF")&&brand.equals("AVI "))return "video/x-msvideo";}
        if(size>=4){if(header[0]==0x1a&&header[1]==0x45&&(header[2]&255)==0xdf&&(header[3]&255)==0xa3)return new String(header,0,size,Charsets.UTF_8).contains("webm")?"video/webm":"video/x-matroska";if(header[0]==0&&header[1]==0&&header[2]==1&&(header[3]&255)==0xba)return "video/mpeg";}
        return "application/octet-stream";
    }
    static long[] range(String header,long size){
        if(size<1)throw new IllegalArgumentException();
        if(header==null)return new long[]{0,size-1};
        if(!header.matches("bytes=[0-9]*-[0-9]*"))throw new IllegalArgumentException();
        String[] pair=header.substring(6).split("-",-1);
        try{
            if(pair[0].isEmpty()){long suffix=Long.parseLong(pair[1]);if(suffix<1)throw new IllegalArgumentException();return new long[]{Math.max(0,size-suffix),size-1};}
            long start=Long.parseLong(pair[0]),end=pair[1].isEmpty()?size-1:Math.min(size-1,Long.parseLong(pair[1]));
            if(start>=size||end<start)throw new IllegalArgumentException();return new long[]{start,end};
        }catch(NumberFormatException e){throw new IllegalArgumentException(e);}
    }
    static NanoHTTPD.Response response(PhoneSettingsServer.Download file,String header,boolean head,PhoneSettingsServer.Active active)throws IOException {
        if(!active.valid()){file.input.close();throw new PhoneSettingsServer.Ended();}
        long[] range;
        try{range=range(header,file.bytes);}catch(IllegalArgumentException e){file.input.close();NanoHTTPD.Response result=NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.RANGE_NOT_SATISFIABLE,"text/plain","");result.addHeader("Content-Range","bytes */"+file.bytes);result.addHeader("Accept-Ranges","bytes");return result;}
        long count=range[1]-range[0]+1;
        InputStream body;
        if(head){file.input.close();body=new ByteArrayInputStream(new byte[0]);}
        else {
            try{long skip=range[0];while(skip>0){if(!active.valid())throw new PhoneSettingsServer.Ended();long n=file.input.skip(skip);if(n==0){if(file.input.read()<0)throw new EOFException();n=1;}skip-=n;}}
            catch(IOException|RuntimeException e){file.input.close();throw e;}
            body=new FilterInputStream(file.input){long remaining=count;
                private void valid()throws IOException{if(!active.valid())throw new PhoneSettingsServer.Ended();}
                public int read()throws IOException{valid();if(remaining==0)return -1;int n=in.read();if(n>=0)remaining--;return n;}
                public int read(byte[] b,int off,int length)throws IOException{valid();if(length==0)return 0;if(remaining==0)return -1;int n=in.read(b,off,(int)Math.min(length,remaining));if(n>0)remaining-=n;return n;}
            };
        }
        NanoHTTPD.Response result=NanoHTTPD.newFixedLengthResponse(header==null?NanoHTTPD.Response.Status.OK:NanoHTTPD.Response.Status.PARTIAL_CONTENT,file.mime,body,count);
        result.addHeader("Accept-Ranges","bytes");if(header!=null)result.addHeader("Content-Range","bytes "+range[0]+"-"+range[1]+"/"+file.bytes);return result;
    }
}
