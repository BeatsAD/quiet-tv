package home.quiet.tv;

/** One intercepted HOME gesture. A short press never waits to see if another press follows. */
final class HomeKeyPolicy {
    static final int NONE=0,HOME=1,RECENTS=2;
    private long pressedAt=-1,lastRelease=-1;
    private long passedDown=-1;
    void pass(long downTime){passedDown=downTime;}
    boolean passed(long downTime){return downTime>0 && passedDown==downTime;}
    boolean pressed(){return pressedAt>=0;}
    void down(long at){if(!pressed())pressedAt=at;}
    int up(long at,boolean cancelled,long hold,long twice){
        long began=pressedAt;pressedAt=-1;
        if(began<0 || at<began || cancelled){lastRelease=-1;return NONE;}
        if(at-began>=hold || lastRelease>=0 && began>=lastRelease && began-lastRelease<twice){lastRelease=-1;return RECENTS;}
        lastRelease=at;return HOME;
    }
    void reset(){pressedAt=-1;lastRelease=-1;}
}
