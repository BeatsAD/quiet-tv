package home.quiet.tv;

/** Low precision lunar orbit with the principal perturbations (Schlyter), WGS84 observer.
 * Geometric topocentric altitude is separate from refraction and the upper-limb horizon.
 * https://www.stjarnhimlen.se/comp/ppcomp.html
 */
final class WeatherMoonPosition {
    final double altitude,azimuth,distance,semidiameter;
    final double east,north,up,sunEast,sunNorth,sunUp;
    private WeatherMoonPosition(double[] moon,double[] sun){
        east=moon[0];north=moon[1];up=moon[2];distance=Math.sqrt(east*east+north*north+up*up);
        altitude=Math.toDegrees(Math.asin(up/distance));azimuth=wrap(Math.toDegrees(Math.atan2(east,north)));
        semidiameter=Math.toDegrees(Math.asin(.2725076/distance));
        sunEast=sun[0];sunNorth=sun[1];sunUp=sun[2];
    }
    static boolean valid(double latitude,double longitude){return !Double.isNaN(latitude)&&!Double.isNaN(longitude)&&Math.abs(latitude)<=90&&Math.abs(longitude)<=180;}
    static WeatherMoonPosition at(long utc,double latitude,double longitude){
        if(!valid(latitude,longitude))throw new IllegalArgumentException("Coordinates required");
        double jd=utc/86400000d+2440587.5,d=jd-2451543.5;
        double ms=wrap(356.0470+.9856002585*d),ws=282.9404+4.70935e-5*d;
        double es=.016709-1.151e-9*d,ee=anomaly(ms,es);
        double sx=Math.cos(ee)-es,sy=Math.sqrt(1-es*es)*Math.sin(ee),sl=Math.toDegrees(Math.atan2(sy,sx))+ws;
        double node=125.1228-.0529538083*d,w=318.0634+.1643573223*d,m=wrap(115.3654+13.0649929509*d);
        double e=anomaly(m,.0549),x=60.2666*(Math.cos(e)-.0549),y=60.2666*Math.sqrt(1-.0549*.0549)*Math.sin(e);
        double v=Math.toDegrees(Math.atan2(y,x)),r=Math.hypot(x,y),arg=v+w;
        x=r*(cos(node)*cos(arg)-sin(node)*sin(arg)*cos(5.1454));
        y=r*(sin(node)*cos(arg)+cos(node)*sin(arg)*cos(5.1454));
        double z=r*sin(arg)*sin(5.1454),lon=Math.toDegrees(Math.atan2(y,x)),lat=Math.toDegrees(Math.atan2(z,Math.hypot(x,y)));
        double lm=m+w+node,ls=ms+ws,elong=lm-ls,f=lm-node;
        lon+=-1.274*sin(m-2*elong)+.658*sin(2*elong)-.186*sin(ms)-.059*sin(2*m-2*elong)-.057*sin(m-2*elong+ms)+.053*sin(m+2*elong)+.046*sin(2*elong-ms)+.041*sin(m-ms)-.035*sin(elong)-.031*sin(m+ms)-.015*sin(2*f-2*elong)+.011*sin(m-4*elong);
        lat+=-.173*sin(f-2*elong)-.055*sin(m-f-2*elong)-.046*sin(m+f-2*elong)+.033*sin(f+2*elong)+.017*sin(2*m+f);
        r+=-.58*cos(m-2*elong)-.46*cos(2*elong);
        double ob=23.4393-3.563e-7*d,lst=wrap(280.46061837+360.98564736629*(jd-2451545)+longitude);
        double[] moon=equatorial(lon,lat,r,ob),sun=equatorial(sl,0,1,ob);
        double n=1/Math.sqrt(1-.00669437999014*sin(latitude)*sin(latitude));
        moon[0]-=n*cos(latitude)*cos(lst);moon[1]-=n*cos(latitude)*sin(lst);moon[2]-=n*(1-.00669437999014)*sin(latitude);
        return new WeatherMoonPosition(local(moon,latitude,lst),local(sun,latitude,lst));
    }
    private static double[] equatorial(double lon,double lat,double r,double ob){double x=r*cos(lon)*cos(lat),y=r*sin(lon)*cos(lat),z=r*sin(lat);return new double[]{x,y*cos(ob)-z*sin(ob),y*sin(ob)+z*cos(ob)};}
    private static double[] local(double[] p,double lat,double lst){double forward=cos(lst)*p[0]+sin(lst)*p[1];return new double[]{-sin(lst)*p[0]+cos(lst)*p[1],-sin(lat)*forward+cos(lat)*p[2],cos(lat)*forward+sin(lat)*p[2]};}
    private static double anomaly(double m,double e){double mr=Math.toRadians(m),a=mr;for(int i=0;i<5;i++)a-=(a-e*Math.sin(a)-mr)/(1-e*Math.cos(a));return a;}
    private static double sin(double d){return Math.sin(Math.toRadians(d));}
    private static double cos(double d){return Math.cos(Math.toRadians(d));}
    private static double wrap(double d){return (d%360+360)%360;}
    double horizon(){return altitude+34d/60+semidiameter;}
    double apparentAltitude(){double a=Math.max(-1,altitude);double refraction=1.02/(60*Math.tan(Math.toRadians(a+10.3/(a+5.11))));return altitude+refraction;}

    /** Events in the caller's explicit 24 hour UTC window; no assumed device timezone.
     * Missing events stay MIN_VALUE, including circumpolar days and one-event days. */
    static long[] riseSet(long start,double latitude,double longitude){
        long rise=Long.MIN_VALUE,set=Long.MIN_VALUE,t=start;double before=at(t,latitude,longitude).horizon();
        for(long next=start+300000;next<=start+86400000L;next+=300000){
            double after=at(next,latitude,longitude).horizon();
            if((before<0)!=(after<0)){
                long lo=t,hi=next;while(hi-lo>1000){long mid=lo+(hi-lo)/2;if((at(mid,latitude,longitude).horizon()<0)==(before<0))lo=mid;else hi=mid;}
                long crossing=lo+(hi-lo)/2;if(crossing<start+86400000L){if(before<0)rise=crossing;else set=crossing;}
            }
            t=next;before=after;
        }
        return new long[]{rise,set};
    }

    /** Two minute endpoints, linearly interpolated in a local unit-vector space.
     * Ephemeris/allocations run once per minute, never once per rendered frame. */
    static final class Track {
        private long minute=Long.MIN_VALUE;private double latitude=Double.NaN,longitude=Double.NaN;
        private int lightCameraRevision=-1;
        private WeatherMoonPosition from,to;
        private final float[] fromDirection=new float[3],toDirection=new float[3],localMoon=new float[3],localSun=new float[3];
        final float[] direction=new float[3],light=new float[3];
        double altitude,azimuth;float angularRadius,visibility;boolean visible;
        void sample(long utc,double lat,double lon,WeatherCamera camera){
            long m=utc/60000;if(utc<0&&utc%60000!=0)m--;boolean changed=m!=minute||lat!=latitude||lon!=longitude;
            if(changed){
                from=m==minute+1&&lat==latitude&&lon==longitude?to:at(m*60000,lat,lon);to=at((m+1)*60000,lat,lon);minute=m;latitude=lat;longitude=lon;
                direction(from,fromDirection);direction(to,toDirection);
            }
            double f=(utc-m*60000)/60000d;
            altitude=mix(from.altitude,to.altitude,f);azimuth=wrap(from.azimuth+WeatherCamera.angle(to.azimuth-from.azimuth)*f);
            double length=0;for(int i=0;i<3;i++){direction[i]=(float)mix(fromDirection[i],toDirection[i],f);length+=direction[i]*direction[i];}
            float norm=(float)Math.sqrt(length);for(int i=0;i<3;i++)direction[i]/=norm;
            double radius=mix(from.semidiameter,to.semidiameter,f);angularRadius=(float)Math.toRadians(radius);
            double fraction=Math.max(0,Math.min(1,(altitude+34d/60+radius)/(2*radius)));visibility=(float)(fraction*fraction*(3-2*fraction));visible=visibility>0;
            if(changed||camera.revision!=lightCameraRevision){
                camera.local(fromDirection[0],fromDirection[1],fromDirection[2],localMoon);camera.local(from.sunEast,from.sunUp,from.sunNorth,localSun);
                float[] c=camera.values;double mf=localMoon[1]*c[0]+localMoon[2]*c[1],sf=localSun[1]*c[0]+localSun[2]*c[1];
                double lx=localSun[0]*mf-localMoon[0]*sf,ly=(localSun[1]*c[1]-localSun[2]*c[0])*mf-(localMoon[1]*c[1]-localMoon[2]*c[0])*sf,len=Math.hypot(lx,ly);
                WeatherMoon phase=WeatherMoon.at(utc);double radial=Math.abs(Math.sin(phase.phase*2*Math.PI));
                light[0]=(float)(len<1e-9?0:lx/len*radial);light[1]=(float)(len<1e-9?0:ly/len*radial);light[2]=phase.light[2];lightCameraRevision=camera.revision;
            }
        }
        private static void direction(WeatherMoonPosition p,float[] out){double h=Math.hypot(p.east,p.north),a=Math.toRadians(p.apparentAltitude()),c=h>1e-12?Math.cos(a)/h:0;out[0]=(float)(p.east*c);out[1]=(float)Math.sin(a);out[2]=(float)(p.north*c);}
        private static double mix(double a,double b,double f){return a+(b-a)*f;}
    }
}
