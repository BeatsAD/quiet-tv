package home.quiet.tv;

/** Rolling completion budget, excluding allocation warmup and input pauses. */
final class WeatherFrameBudget {
    private final byte[] window=new byte[30];
    private int warmup=3,index,count,slow,cloudFrames,slowCloudFrames,severe,stable;
    private long cooldownUntil,stableSince,lastNow;
    void reset(){clear();warmup=3;cooldownUntil=lastNow=0;}
    private void clear(){java.util.Arrays.fill(window,(byte)0);index=count=slow=cloudFrames=slowCloudFrames=severe=stable=0;stableSince=0;}
    int quality(int current,long nanos,boolean cloudUpdated,long now){
        if(now<lastNow)reset();lastNow=now;
        if(nanos<=0)return current;
        if(warmup>0){warmup--;return current;}
        byte old=window[index];if(count==window.length){if((old&1)!=0)slow--;if((old&2)!=0){cloudFrames--;if((old&1)!=0)slowCloudFrames--;}}else count++;
        byte flags=(byte)((nanos>40000000L?1:0)|(cloudUpdated?2:0));window[index]=flags;index=(index+1)%window.length;
        if((flags&1)!=0)slow++;if(cloudUpdated){cloudFrames++;if((flags&1)!=0)slowCloudFrames++;}
        severe=nanos>100000000L?severe+1:0;
        if(nanos<=35000000L){if(stable++==0)stableSince=now;}else{stable=0;stableSince=0;}
        if(now<cooldownUntil)return current;
        if(current<4&&(severe>=2||count>=12&&slow>=4&&slow*5>=count)){changed(now);return current+1;}
        if(current>0&&stable>=900&&now-stableSince>=30000000000L){changed(now);return current-1;}
        return current;
    }
    private void changed(long now){clear();warmup=3;cooldownUntil=now+2000000000L;}
    int cloudFrames(){return cloudFrames;}
    int slowCloudFrames(){return slowCloudFrames;}
}
