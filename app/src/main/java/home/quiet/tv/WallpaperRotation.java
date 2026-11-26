package home.quiet.tv;

final class WallpaperRotation {
    static final int MIN_SECONDS=3, MAX_SECONDS=24*60*60, DEFAULT_SECONDS=5*60;
    private static final int[] SECOND_PRESETS={3,5,10,15,30,45};
    private static final int[] MINUTE_PRESETS={60,300,600,900,1800,3600};

    static boolean valid(int seconds){return seconds>=MIN_SECONDS&&seconds<=MAX_SECONDS;}
    static int fromMinutes(int minutes){return minutes>=1&&minutes<=MAX_SECONDS/60?minutes*60:DEFAULT_SECONDS;}
    static int legacyMinutes(int seconds){return (seconds+59)/60;}
    static int[] presets(boolean minutes){return (minutes?MINUTE_PRESETS:SECOND_PRESETS).clone();}
    static boolean minuteUnit(int seconds){return seconds>=60&&seconds%60==0;}
    static String label(int seconds,boolean chinese){return label(seconds,minuteUnit(seconds),chinese);}
    static String label(int seconds,boolean minutes,boolean chinese){
        return (minutes?seconds/60:seconds)+" "+(minutes?(chinese?"分钟":"min"):(chinese?"秒":"sec"));
    }
    private WallpaperRotation(){}
}
