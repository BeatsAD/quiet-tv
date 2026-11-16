package home.quiet.tv;

import java.util.Calendar;
import java.util.TimeZone;

/** NOAA solar-position approximation. UTC plus longitude also works for remote cities. */
final class WeatherIllumination {
    final long minute,utc,sampledNanos;
    final double latitude,longitude;final boolean lunarPosition,live;
    final double altitude;
    final float night,warm,moonLight;
    final boolean rising;
    final float[] sun,solar,lunar;
    final float solarGain,lunarGain;
    final WeatherMoon moon;

    private WeatherIllumination(long minute,double altitude,double hourAngle,long now,double latitude,double longitude,boolean follow,boolean live){
        utc=now;sampledNanos=System.nanoTime();this.latitude=latitude;this.longitude=longitude;this.live=live;lunarPosition=follow&&WeatherMoonPosition.valid(latitude,longitude);
        moon=WeatherMoon.at(now);
        this.minute=minute;this.altitude=altitude;rising=hourAngle<0;
        night=(float)(1-smooth(-6,3,altitude));
        warm=(float)((1-smooth(0,14,altitude))*smooth(-8,-2,altitude));
        double elevation=Math.toRadians(altitude);
        float x=(float)(Math.sin(hourAngle)*.8*Math.cos(elevation));
        // Time-of-day fallback when location is unavailable.
        float y=(float)Math.sin(elevation),z=(float)Math.max(.12,Math.cos(elevation));
        if(lunarPosition){
            WeatherMoonPosition position=WeatherMoonPosition.at(now,latitude,longitude);
            double moonElevation=Math.toRadians(position.apparentAltitude());
            solar=new float[]{(float)position.sunEast,(float)position.sunUp,(float)position.sunNorth};
            double horizontal=Math.hypot(position.east,position.north),c=horizontal>1e-12?Math.cos(moonElevation)/horizontal:0;
            lunar=new float[]{(float)(position.east*c),(float)Math.sin(moonElevation),(float)(position.north*c)};
            sun=new float[]{(float)(position.sunEast*(1-night)+position.east*c*night),(float)(position.sunUp*(1-night)+Math.sin(moonElevation)*night),(float)(position.sunNorth*(1-night)+position.north*c*night)};
            moonLight=(float)(moon.illuminated*smooth(-1,3,position.altitude));
        }else{float heading=latitude<0?1:-1;sun=new float[]{heading*(x*(1-night)+.5f*night),y*(1-night)+.6f*night,heading*(z*(1-night)+1.4f*night)};solar=sun;lunar=new float[]{0,1,0};moonLight=1;}
        solarGain=(float)smooth(-1,5,altitude);lunarGain=lunarPosition?night*moonLight:0;
    }
    static WeatherIllumination at(long now,double latitude,double longitude,boolean follow){
        return at(now,latitude,longitude,follow,true);
    }
    static WeatherIllumination at(long now,double latitude,double longitude,boolean follow,boolean live){
        long minute=now/60000;
        if(!follow)return new WeatherIllumination(Long.MIN_VALUE,35,-.7,now,latitude,longitude,follow,live);
        if(Double.isNaN(latitude)||Double.isNaN(longitude)||Math.abs(latitude)>90||Math.abs(longitude)>180){
            Calendar local=Calendar.getInstance();local.setTimeInMillis(now);
            double hour=local.get(Calendar.HOUR_OF_DAY)+local.get(Calendar.MINUTE)/60d;
            return new WeatherIllumination(minute,45*Math.sin((hour-6)*Math.PI/12),(hour-12)*Math.PI/12,now,latitude,longitude,follow,live);
        }
        Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));utc.setTimeInMillis(now);
        double hour=utc.get(Calendar.HOUR_OF_DAY)+utc.get(Calendar.MINUTE)/60d+utc.get(Calendar.SECOND)/3600d;
        double g=2*Math.PI/utc.getActualMaximum(Calendar.DAY_OF_YEAR)*(utc.get(Calendar.DAY_OF_YEAR)-1+(hour-12)/24);
        double equation=229.18*(.000075+.001868*Math.cos(g)-.032077*Math.sin(g)-.014615*Math.cos(2*g)-.040849*Math.sin(2*g));
        double decl=.006918-.399912*Math.cos(g)+.070257*Math.sin(g)-.006758*Math.cos(2*g)+.000907*Math.sin(2*g)-.002697*Math.cos(3*g)+.00148*Math.sin(3*g);
        double solarMinutes=((hour*60+equation+4*longitude)%1440+1440)%1440;
        double angle=Math.toRadians(solarMinutes/4-180),lat=Math.toRadians(latitude);
        double sine=Math.sin(lat)*Math.sin(decl)+Math.cos(lat)*Math.cos(decl)*Math.cos(angle);
        return new WeatherIllumination(minute,Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,sine)))),angle,now,latitude,longitude,follow,live);
    }
    String period(boolean chinese){if(altitude<=-6)return chinese?"夜晚":"Night";if(altitude<8)return rising?(chinese?"日出":"Sunrise"):(chinese?"日落":"Sunset");return chinese?"白天":"Day";}
    private static double smooth(double a,double b,double value){double t=Math.max(0,Math.min(1,(value-a)/(b-a)));return t*t*(3-2*t);}
}
