package home.quiet.tv;

/** Low-precision geocentric lunar elongation; not a moonrise/set or pointing ephemeris. */
final class WeatherMoon {
    private static volatile WeatherMoon cached;
    final long hour;final double phase,illuminated;
    final float[] light;
    private WeatherMoon(long hour,double degrees){this.hour=hour;phase=wrap(degrees)/360;double a=phase*2*Math.PI;illuminated=(1-Math.cos(a))*.5;light=new float[]{(float)Math.sin(a),0,(float)-Math.cos(a)};}
    static WeatherMoon at(long utc){
        long hour=utc/3600000L;if(utc<0&&utc%3600000L!=0)hour--;WeatherMoon value=cached;if(value!=null&&value.hour==hour)return value;
        double d=hour/24d+2440587.5-2451545,t=d/36525;
        // Principal lunar longitude terms and the solar equation of centre (degrees).
        double m=wrap(357.5291092+35999.0502909*t),l=wrap(218.3164477+481267.88123421*t),a=wrap(134.9633964+477198.8675055*t),e=wrap(297.8501921+445267.1114034*t),f=wrap(93.2720950+483202.0175233*t);
        double moon=l+6.288774*sin(a)+1.274027*sin(2*e-a)+.658314*sin(2*e)+.213618*sin(2*a)-.185116*sin(m)-.114332*sin(2*f)+.058793*sin(2*e-2*a)+.057066*sin(2*e-m-a)+.053322*sin(2*e+a)+.045758*sin(2*e-m)-.040923*sin(m-a)-.034720*sin(e)-.030383*sin(m+a)+.015327*sin(2*e-2*f)-.012528*sin(2*f+a)+.010980*sin(a-4*e);
        double sun=wrap(280.46646+36000.76983*t)+1.914602*sin(m)+.019993*sin(2*m)+.000289*sin(3*m);
        value=new WeatherMoon(hour,moon-sun);cached=value;return value;
    }
    private static double sin(double degrees){return Math.sin(Math.toRadians(degrees));}
    private static double wrap(double degrees){return (degrees%360+360)%360;}
}
