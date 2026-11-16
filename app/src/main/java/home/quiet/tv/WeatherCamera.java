package home.quiet.tv;

/** World axes are east/up/north. A fixed vertical FOV preserves TV composition. */
final class WeatherCamera {
    final float[] values=new float[4],heading=new float[2];
    private double pitch=Double.NaN,verticalFov,yaw;private float eye;private int width,height;
    int revision;
    boolean configure(double pitchDegrees,double verticalFovDegrees,double azimuthDegrees,float eyeHeight,int w,int h){
        if(pitch==pitchDegrees&&verticalFov==verticalFovDegrees&&yaw==azimuthDegrees&&eye==eyeHeight&&width==w&&height==h)return false;
        boolean jump=Double.isNaN(pitch)||Math.abs(pitch-pitchDegrees)>10||Math.abs(angle(azimuthDegrees-yaw))>20||Math.abs(verticalFov-verticalFovDegrees)>8||eye!=eyeHeight;
        pitch=pitchDegrees;verticalFov=verticalFovDegrees;yaw=azimuthDegrees;eye=eyeHeight;width=w;height=h;
        values[0]=(float)Math.sin(Math.toRadians(pitch));values[1]=(float)Math.cos(Math.toRadians(pitch));values[2]=(float)(Math.tan(Math.toRadians(verticalFov*.5))*w/h);values[3]=eye;
        heading[0]=(float)Math.sin(Math.toRadians(yaw));heading[1]=(float)Math.cos(Math.toRadians(yaw));revision++;return jump;
    }
    static double angle(double degrees){return ((degrees+180)%360+360)%360-180;}
    float right(double east,double north){return (float)(heading[1]*east-heading[0]*north);}
    float forward(double east,double north){return (float)(heading[0]*east+heading[1]*north);}
    void local(double east,double up,double north,float[] out){out[0]=right(east,north);out[1]=(float)up;out[2]=forward(east,north);}
    void project(float[] p,float[] out){
        float x=right(p[0],p[2]),z=forward(p[0],p[2]),f=p[1]*values[0]+z*values[1];
        if(f<=.001){out[0]=out[1]=-10;return;}
        out[0]=x/(f*values[2]);out[1]=(p[1]*values[1]-z*values[0])/(f*values[2]/(width/(float)height));
    }
    void ray(float x,float y,float[] out){
        float lx=x*values[2],ly=values[0]+y*values[1]*values[2]*height/width,lz=values[1]-y*values[0]*values[2]*height/width;
        float length=(float)Math.sqrt(lx*lx+ly*ly+lz*lz);out[0]=(heading[1]*lx+heading[0]*lz)/length;out[1]=ly/length;out[2]=(-heading[0]*lx+heading[1]*lz)/length;
    }
}
