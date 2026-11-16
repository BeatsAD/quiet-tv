package home.quiet.tv;

import android.graphics.Bitmap;
import android.os.Build;
import java.nio.ByteBuffer;
import static android.opengl.GLES30.*;

/** One bounded capture of the rendered framebuffer, owned by its GLES thread. */
@androidx.annotation.RequiresApi(21)
final class WallpaperFrameReadback {
    private final WallpaperGl gl;
    private WallpaperGl.Target target;
    private int buffer;
    private long fence;
    private ByteBuffer direct;

    WallpaperFrameReadback(WallpaperGl gl){this.gl=gl;}

    void capture(int sourceWidth,int sourceHeight,int width,int height){
        if(target==null||target.w!=width||target.h!=height){
            if(target!=null)target.destroy();
            target=gl.new Target(width,height,1,false);
        }
        try{
            glBindFramebuffer(GL_READ_FRAMEBUFFER,0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target.fbo);
            glBlitFramebuffer(0,0,sourceWidth,sourceHeight,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_LINEAR);
            glBindFramebuffer(GL_FRAMEBUFFER,target.fbo);
            if(Build.VERSION.SDK_INT>=24){
                if(buffer==0){int[] names={0};glGenBuffers(1,names,0);buffer=names[0];}
                glBindBuffer(GL_PIXEL_PACK_BUFFER,buffer);
                // Orphan any cancelled transfer rather than waiting to reuse its storage.
                glBufferData(GL_PIXEL_PACK_BUFFER,width*height*4,null,GL_STREAM_READ);
                Api24.read(width,height);
                fence=glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE,0);
                if(fence==0)throw new IllegalStateException("Capture fence unavailable");
            }else{
                // The Java PBO-offset overload was added in API24. Older GLES3
                // devices still perform their small read on the GLES worker.
                direct=ByteBuffer.allocateDirect(width*height*4);
                glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,direct);
            }
            gl.check();
        }finally{
            glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
            glBindFramebuffer(GL_FRAMEBUFFER,0);
        }
    }

    Bitmap takeIfReady(){
        if(direct==null){
            if(fence==0)return null;
            int status=glClientWaitSync(fence,0,0);
            if(status==GL_TIMEOUT_EXPIRED)return null;
            if(status==GL_WAIT_FAILED)throw new IllegalStateException("Capture fence failed");
        }
        Bitmap result=null;
        boolean mapped=false,valid=true;
        try{
            ByteBuffer pixels=direct;
            if(pixels==null){
                glBindBuffer(GL_PIXEL_PACK_BUFFER,buffer);
                pixels=(ByteBuffer)glMapBufferRange(GL_PIXEL_PACK_BUFFER,0,target.w*target.h*4,GL_MAP_READ_BIT);
                if(pixels==null)throw new IllegalStateException("Capture buffer unavailable");
                mapped=true;
            }
            int[] colors=new int[target.w*target.h];pixels.position(0);
            for(int y=0;y<target.h;y++)for(int x=0;x<target.w;x++){
                int red=pixels.get()&255,green=pixels.get()&255,blue=pixels.get()&255;
                pixels.get();colors[(target.h-1-y)*target.w+x]=0xff000000|(red<<16)|(green<<8)|blue;
            }
            result=Bitmap.createBitmap(colors,target.w,target.h,Bitmap.Config.ARGB_8888);
        }finally{
            if(mapped)valid=glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
            glBindBuffer(GL_PIXEL_PACK_BUFFER,0);
            cancel();
        }
        if(!valid){if(result!=null)result.recycle();throw new IllegalStateException("Capture buffer invalidated");}
        return result;
    }

    void cancel(){if(fence!=0){glDeleteSync(fence);fence=0;}direct=null;}
    void destroy(){
        cancel();if(buffer!=0){glDeleteBuffers(1,new int[]{buffer},0);buffer=0;}
        if(target!=null){target.destroy();target=null;}
    }
    @androidx.annotation.RequiresApi(24)
    private static final class Api24 {
        static void read(int width,int height){glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,0);}
    }
}
