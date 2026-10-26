package home.quiet.tv;

import java.io.*;

/** Display aspect, including non-square pixels; Media3 sizes have already applied rotation. */
final class VideoGeometry {
    static boolean manualRotation(int sdk,int rotation){return sdk<27&&rotation%180!=0;}
    static float aspect(int width,int height,float pixelRatio,int rotation){
        if(width<=0||height<=0)return 16f/9f;
        if(pixelRatio<=0||Float.isNaN(pixelRatio)||Float.isInfinite(pixelRatio))pixelRatio=1f;
        float aspect=width*pixelRatio/height;
        return rotation%180==0?aspect:1f/aspect;
    }
    /** Older platform extractors omit MP4 pasp even when the container declares non-square pixels. */
    static float pixelRatio(File file,float fallback){
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){
            float ratio=pasp(input,0,input.length(),0,new int[]{0});return ratio>0?ratio:fallback;
        }catch(IOException e){return fallback;}
    }
    private static float pasp(RandomAccessFile input,long position,long end,int depth,int[] boxes)throws IOException{
        if(depth>8)return 0;
        while(position+8<=end&&boxes[0]++<1024){
            input.seek(position);long size=input.readInt()&0xffffffffL;int type=input.readInt(),header=8;
            if(size==1){if(end-position<16)return 0;size=input.readLong();header=16;}
            if(size==0)size=end-position;if(size<header||size>end-position)return 0;
            long content=position+header,next=position+size;
            if(type==0x70617370&&size-header>=8){input.seek(content);long w=input.readInt()&0xffffffffL,h=input.readInt()&0xffffffffL;return w>0&&h>0?w/(float)h:0;}
            long children=-1;
            if(type==0x6d6f6f76||type==0x7472616b||type==0x6d646961||type==0x6d696e66||type==0x7374626c)children=content;
            else if(type==0x73747364)children=content+8;
            else if(type==0x61766331||type==0x61766333||type==0x68766331||type==0x68657631||type==0x6d703476||type==0x76703039||type==0x61763031)children=content+78;
            if(children>=0&&children<next){float ratio=pasp(input,children,next,depth+1,boxes);if(ratio>0)return ratio;}
            position=next;
        }
        return 0;
    }
}
