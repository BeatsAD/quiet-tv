package home.quiet.tv;

import java.io.IOException;
import java.util.*;
import static android.opengl.GLES30.*;

/** The prototype Gaussian image pyramid, sampled continuously by screen height. */
@androidx.annotation.RequiresApi(21)
final class ProgressiveWallpaperRenderer {
    private final WallpaperGl gl;
    private final WallpaperGl.Program copy,base,downsample,composite;
    private final ArrayList<WallpaperGl.Target> levels=new ArrayList<>();
    private int pyramid,width,height,source;
    private boolean dirty=true;
    private float[] previousCrop;
    ProgressiveWallpaperRenderer(WallpaperGl gl)throws IOException{this.gl=gl;copy=gl.new Program("vertex.glsl","copy.glsl");base=gl.new Program("vertex.glsl","base-copy.glsl");downsample=gl.new Program("vertex.glsl","downsample.glsl");composite=gl.new Program("vertex.glsl","composite.glsl");}
    void sourceChanged(){dirty=true;}
    private void resize(int w,int h){
        if(w==width&&h==height)return;
        for(WallpaperGl.Target target:levels)target.destroy();levels.clear();if(pyramid!=0)gl.removeTexture(pyramid);
        width=w;height=h;int count=Math.min(10,1+(int)Math.floor(Math.log(Math.max(w,h))/Math.log(2)));
        pyramid=gl.texture(GL_TEXTURE_2D);glTexStorage2D(GL_TEXTURE_2D,count,GL_RGBA8,w,h);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,count-1);
        for(int i=0;i<count;i++)levels.add(gl.new Target(Math.max(1,w>>i),Math.max(1,h>>i),1,false));dirty=true;
    }
    void render(int texture,int w,int h,float[] crop,int strength,float brightness,int destination){
        if(strength==0){glBindFramebuffer(GL_FRAMEBUFFER,destination);glViewport(0,0,w,h);glDisable(GL_BLEND);base.use();gl.bind(0,GL_TEXTURE_2D,texture);base.i("source",0);base.f("crop",crop);base.f("brightness",brightness);gl.draw();gl.check();return;}
        resize(w,h);if(texture!=source||!Arrays.equals(crop,previousCrop)){source=texture;previousCrop=crop.clone();dirty=true;}
        glDisable(GL_BLEND);
        if(dirty){for(int i=0;i<levels.size();i++){
            WallpaperGl.Target target=levels.get(i),previous=i>0?levels.get(i-1):null;WallpaperGl.Program program=i>0?downsample:copy;
            target.bind();program.use();gl.bind(0,GL_TEXTURE_2D,i>0?previous.textures[0]:texture);program.i("source",0);
            if(i>0)program.f("texel",1f/previous.w,1f/previous.h);else program.f("crop",crop);
            gl.draw();glBindTexture(GL_TEXTURE_2D,pyramid);glCopyTexSubImage2D(GL_TEXTURE_2D,i,0,0,0,0,target.w,target.h);
        }dirty=false;}
        glBindFramebuffer(GL_FRAMEBUFFER,destination);glViewport(0,0,w,h);base.use();gl.bind(0,GL_TEXTURE_2D,pyramid);base.i("source",0);base.f("crop",0,0,1,1);base.f("brightness",brightness);gl.draw();
        if(strength>0){glEnable(GL_BLEND);glBlendFunc(GL_ONE,GL_ONE_MINUS_SRC_ALPHA);composite.use();gl.bind(0,GL_TEXTURE_2D,pyramid);composite.i("source",0);composite.f("maximumSigma",strength/100f*w*72/1280);composite.f("maximumLevel",levels.size()-1);composite.f("brightness",brightness);gl.draw();glDisable(GL_BLEND);}gl.check();
    }
}
