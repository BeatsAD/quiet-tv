package home.quiet.tv;

/** Geometry in the caller's coordinate system; transforms never change focus order. */
final class DockMotion {
    static float scale(int index,int focus) {
        return focus<0?1:index==focus?1.12f:Math.abs(index-focus)==1?1.04f:1;
    }

    static float[] translations(int count,int focus,float width,float gap,float padding,float bandWidth,float safe) {
        float[] result=new float[count];
        if(count==0||focus<0)return result;
        float occupied=0;
        for(int i=0;i<count;i++)occupied+=width*scale(i,focus);
        float spacing=count==1?0:Math.min(gap,(bandWidth-2*safe-occupied)/(count-1));
        float span=occupied+spacing*(count-1);
        float center=padding+(count*width+(count-1)*gap)/2;
        float left=Math.max(safe,Math.min(center-span/2,bandWidth-safe-span));
        for(int i=0;i<count;i++){
            float scaled=width*scale(i,focus);
            result[i]=left+scaled/2-(padding+i*(width+gap)+width/2);
            left+=scaled+spacing;
        }
        return result;
    }
}
