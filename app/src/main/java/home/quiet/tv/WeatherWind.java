package home.quiet.tv;

/** Smoothed observations and integrated advection in the world east/north plane. */
final class WeatherWind {
    private WeatherAtmosphere target=WeatherAtmosphere.EMPTY;
    private boolean initialized;
    private double heading,speed=2.5,gust=2.5,gustTime;
    double windWeight,cloudWeight,rainWeight,cover,rain;
    double offsetX,offsetZ;
    float particleWind,particleNorth;
    void target(WeatherAtmosphere value){target=value==null?WeatherAtmosphere.EMPTY:value;}
    void reset(){offsetX=offsetZ=0;}
    void step(double seconds,double presetDx,double presetDz,double presetParticle){
        boolean directed=!Double.isNaN(target.windFrom),calm=target.windSpeed==0;
        double t=initialized?1-Math.exp(-Math.max(0,Math.min(.1,seconds))/1.5):1;
        double desired=Double.isNaN(target.windSpeed)?2.5:Math.min(25,target.windSpeed);
        if(directed)heading+=angle(target.windFrom-heading)*t;
        speed+=(desired-speed)*t;gust+=((Double.isNaN(target.gust)?desired:Math.max(desired,Math.min(35,target.gust)))-gust)*t;
        windWeight+=((directed||calm?1:0)-windWeight)*t;
        if(!Double.isNaN(target.cloudCover))cover+=(target.cloudCover-cover)*t;
        cloudWeight+=((Double.isNaN(target.cloudCover)?0:1)-cloudWeight)*t;
        if(!Double.isNaN(target.rainPerHour()))rain+=(Math.min(60,target.rainPerHour())-rain)*t;
        rainWeight+=((Double.isNaN(target.rainPerHour())?0:1)-rainWeight)*t;
        double dt=Math.max(0,Math.min(.1,seconds));gustTime+=dt;double pulse=(1-Math.cos(gustTime*2*Math.PI/47))*.5;
        double radians=Math.toRadians(heading),artSpeed=Math.min(.30,(speed+(gust-speed)*.15*pulse)*.012);
        // Texture coordinates advect opposite to physical motion; wind direction is "from".
        offsetX+=presetDx*(1-windWeight)+Math.sin(radians)*artSpeed*dt*windWeight;
        offsetZ+=presetDz*(1-windWeight)+Math.cos(radians)*artSpeed*dt*windWeight;
        double tilt=-Math.sin(radians)*Math.min(.65,(speed+(gust-speed)*.15)*.035);
        particleWind=(float)(presetParticle*(1-windWeight)+tilt*windWeight);particleNorth=(float)(-Math.cos(radians)*Math.min(.65,(speed+(gust-speed)*.15)*.035)*windWeight);initialized=true;
    }
    static double angle(double degrees){return ((degrees+180)%360+360)%360-180;}
    double coverage(double preset){return preset+(-.45+.75*cover-preset)*cloudWeight;}
    double rainAmount(double preset){return preset+((1-Math.exp(-rain/2))-preset)*rainWeight;}
}
