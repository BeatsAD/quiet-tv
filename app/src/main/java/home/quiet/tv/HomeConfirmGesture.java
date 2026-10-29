package home.quiet.tv;

/** Short presses only. The caller owns the delayed single action and its focus identity. */
final class HomeConfirmGesture {
    static final long WINDOW_MS=400;
    enum Result { IGNORE,WAIT,DOUBLE }
    private int downKey=-1,pendingKey=-1;
    private long downTime,pendingTime,pendingDownTime;
    private boolean repeated;
    void down(int key,long time,int repeat){
        if(repeat!=0){repeated=true;pendingKey=-1;return;}
        downKey=key;downTime=time;repeated=false;
    }
    Result up(int key,long down,long time,boolean canceled,long longPressMs){
        boolean shortPress=downKey==key&&downTime==down&&!repeated&&!canceled&&time>=down&&time-down<longPressMs;
        downKey=-1;
        if(!shortPress){pendingKey=-1;return Result.IGNORE;}
        if(pendingKey==key&&down>pendingDownTime&&time>=pendingTime&&time-pendingTime<=WINDOW_MS){pendingKey=-1;return Result.DOUBLE;}
        if(pendingKey==key&&down==pendingDownTime){pendingKey=-1;return Result.IGNORE;}
        pendingKey=key;pendingTime=time;pendingDownTime=down;return Result.WAIT;
    }
    boolean pending(){return pendingKey!=-1;}
    void cancel(){downKey=-1;pendingKey=-1;repeated=false;}
}
