package de.sofacockpit.app;

/** Pure output planner. No periodic refresh: one finite plateau covers the
 * remaining touch budget. Replace only for meaningful amplitude changes.
 * Every duration is bounded by the original gesture deadline. */
final class MagnetOutput {
    static final long TICK_MS=45, MIN_CHANGE_MS=60, SNAP_MS=42;
    static final int MIN_CHANGE_AMPLITUDE=4, DEFAULT_AMPLITUDE=-1;
    enum Kind { NONE, STOP, HUM, SNAP }
    static final class Plan {
        final Kind kind;
        final int amplitude;
        final long duration;
        final long[] times;
        final int[] amplitudes;
        Plan(Kind kind,int amplitude,long duration,long[] times,int[] amplitudes){
            this.kind=kind;this.amplitude=amplitude;this.duration=duration;
            this.times=times;this.amplitudes=amplitudes;
        }
        boolean hasRamp(){return times!=null;}
    }
    private static final Plan NONE=new Plan(Kind.NONE,0,0,null,null);
    private static final Plan STOP=new Plan(Kind.STOP,0,0,null,null);
    private int runningAmplitude;
    private long lastChange,snapUntil;

    void reset(){runningAmplitude=0;lastChange=0;snapUntil=0;}

    Plan update(MagnetField.Reading reading,int strength,boolean amplitudeControl,long now,long remaining){
        if(remaining<=0||strength<=0)return quiet(now,true);
        int maximum=Math.max(1,Math.round(255f*Math.min(100,strength)/100f));
        if(reading.kind==MagnetField.Kind.SNAP){
            long duration=Math.min(SNAP_MS,remaining);
            reset();snapUntil=now+duration;
            return new Plan(Kind.SNAP,amplitudeControl?maximum:DEFAULT_AMPLITUDE,duration,null,null);
        }
        // Do not cut a short snap off when the next sample inside the target is quiet.
        if(now<snapUntil)return NONE;
        if(reading.kind==MagnetField.Kind.QUIET)return quiet(now,false);
        int target=amplitudeControl?
            Math.max(1,Math.round(maximum*reading.percent/100f)):DEFAULT_AMPLITUDE;
        int previous=runningAmplitude;
        if(previous!=0){
            if(previous==target)return NONE;
            if(now-lastChange<MIN_CHANGE_MS)return NONE;
            if(amplitudeControl&&previous>0&&Math.abs(previous-target)<MIN_CHANGE_AMPLITUDE)return NONE;
        }
        runningAmplitude=target;lastChange=now;snapUntil=0;
        // An uninterrupted nonzero staircase requests a brief transition from the
        // preceding level, then holds the destination for the remaining time.
        // Android may still restart the motor when replacing the effect.
        if(amplitudeControl&&previous>0&&remaining>40){
            int delta=target-previous;
            return new Plan(Kind.HUM,target,remaining,
                new long[]{10,10,10,remaining-30},
                new int[]{previous,previous+Math.round(delta/3f),
                    previous+Math.round(2*delta/3f),target});
        }
        return new Plan(Kind.HUM,target,remaining,null,null);
    }
    private Plan quiet(long now,boolean force){
        if(!force&&now<snapUntil)return NONE;
        boolean hadOutput=runningAmplitude!=0||now<snapUntil;
        reset();
        return hadOutput?STOP:NONE;
    }
}
