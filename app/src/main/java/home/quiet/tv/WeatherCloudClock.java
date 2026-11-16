package home.quiet.tv;

/** Absolute cloud sampling slots. Missed slots are skipped, never queued. */
final class WeatherCloudClock {
    private static final double EPSILON=.0005;
    private double step=1/15d;
    private double next=Double.NaN,last=Double.NaN;
    void rate(int hz){double value=1d/hz;if(value!=step){step=value;reset();}}
    void reset(){next=last=Double.NaN;}
    boolean sample(double time,boolean force){
        if(Double.isNaN(next)||time<last)next=time;
        last=time;
        boolean due=time+EPSILON>=next;
        if(!due&&!force)return false;
        if(due)next+=(Math.floor((time-next+EPSILON)/step)+1)*step;
        return true;
    }
}
