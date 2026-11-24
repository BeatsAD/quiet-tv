package home.quiet.tv;

/** One capsule edge, with an inherited starting velocity and a stationary endpoint. */
final class TabSlideCurve {
    private final float start, distance, tangent, duration, exponent;
    private final boolean braking;

    TabSlideCurve(float start,float target,long duration){
        this(start,target,2.15f*(target-start)/duration,duration);
    }

    TabSlideCurve(float start,float target,float velocity,long duration){
        this.start=start;
        this.duration=duration;
        distance=target-start;
        tangent=velocity*duration;
        float slope=distance==0?0:tangent/distance;
        // With little distance left, an exponential ease-out can brake without overshooting.
        exponent=slope>3?slope:0;
        braking=distance*tangent<=0;
    }

    float position(float progress){
        float remaining=1-progress;
        if(exponent>0)return start+distance*(1-(float)Math.pow(remaining,exponent));
        return start+distance*progress*progress*(3-2*progress)
                +tangent*progress*remaining*inertia(remaining);
    }

    float velocity(float progress){
        float remaining=1-progress;
        if(exponent>0)return tangent*(float)Math.pow(remaining,exponent-1)/duration;
        return (distance*6*progress*remaining
                +tangent*inertia(remaining)*(1-(braking?7:3)*progress))/duration;
    }

    private float inertia(float remaining){
        // During a reversal the old velocity fades early, so the capsule turns before drifting far.
        if(!braking)return remaining;
        float squared=remaining*remaining;
        return squared*squared*remaining;
    }
}
