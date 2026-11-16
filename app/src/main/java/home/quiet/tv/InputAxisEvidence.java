package home.quiet.tv;

/** Four explicitly selected joystick axes; no pointer coordinates, text or synthetic input. */
final class InputAxisEvidence {
    long at,count;
    private final float[] low={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
    private final float[] high={Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
    void add(long time,float[] values){at=time;count++;for(int i=0;i<4;i++)if(!Float.isNaN(values[i])&&!Float.isInfinite(values[i])){low[i]=Math.min(low[i],values[i]);high[i]=Math.max(high[i],values[i]);}}
    String summary(){StringBuilder value=new StringBuilder("samples="+count+" lastEventTime="+at);for(int i=0;i<4;i++)if(low[i]!=Float.POSITIVE_INFINITY)value.append(" axisSlot=").append(i).append(" observedMin=").append(low[i]).append(" observedMax=").append(high[i]);return value.toString();}
}
