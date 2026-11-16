package home.quiet.tv;

/** A candidate value stays separate from the last committed setting. */
final class SettingsRangeDraft {
    private final int minimum,step;
    private int maximum;
    private int original,candidate;
    private boolean editing;
    SettingsRangeDraft(int minimum,int maximum,int step){this.minimum=minimum;this.maximum=maximum;this.step=step;}
    void begin(int value){original=clamp(value);candidate=original;editing=true;}
    void preview(int value){if(editing)candidate=clamp(value);}
    void maximum(int value){maximum=Math.max(minimum,value);if(editing)candidate=clamp(candidate);}
    void move(int direction,long heldMillis){preview(candidate+direction*step*(heldMillis>=1200?2:1));}
    int original(){return original;}
    int value(){return candidate;}
    boolean editing(){return editing;}
    int commit(){original=candidate;editing=false;return candidate;}
    int cancel(){candidate=original;editing=false;return original;}
    private int clamp(int value){return Math.max(minimum,Math.min(maximum,value));}
}
