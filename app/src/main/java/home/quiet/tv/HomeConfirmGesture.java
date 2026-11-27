package home.quiet.tv;

/** Short presses only. The caller owns the delayed single action and its focus identity. */
final class HomeConfirmGesture {
    static final long WINDOW_MS=400;
    enum Result { IGNORE,WAIT,DOUBLE }
    private int downKey=-1,pendingKey=-1;
    private long pendingTime,pendingDownTime;
    private boolean repeated;
    void down(int key,long time,int repeat){
        if(repeat!=0){repeated=true;pendingKey=-1;return;}
        downKey=key;repeated=false;
    }
    Result up(int key,long down,long time,boolean canceled){
        boolean matchedDown=downKey==key;
        boolean repeatHeld=matchedDown&&repeated;
        downKey=-1;repeated=false;
        if(canceled){pendingKey=-1;return Result.IGNORE;}
        if(pendingKey==key&&!matchedDown&&down>0&&down==pendingDownTime){pendingKey=-1;return Result.IGNORE;}
        boolean secondPress=pendingKey==key&&matchedDown&&!repeatHeld&&time>=pendingTime&&time-pendingTime<=WINDOW_MS
            &&(down<=0||pendingDownTime<=0||down>pendingDownTime);
        if(secondPress){pendingKey=-1;return Result.DOUBLE;}
        pendingKey=key;pendingTime=time;pendingDownTime=down;return Result.WAIT;
    }
    boolean pending(){return pendingKey!=-1;}
    void cancel(){downKey=-1;pendingKey=-1;repeated=false;}
}
