package de.sofacockpit.app;

/** Distance-to-target field, with a one-time snap and a small re-entry hysteresis.
 * Pure geometry/time logic; Android output is deliberately separate. All lengths
 * are in dp, so the displayed target and the tactile target agree. */
final class MagnetField {
    static final long MAX_GESTURE_MS=5000;
    static final int MIN_APPROACH=10,MAX_APPROACH=90;
    enum Kind { QUIET, HUM, SNAP }
    static final class Reading {
        final Kind kind;
        final int percent;
        final boolean latched;
        Reading(Kind kind,int percent,boolean latched){
            this.kind=kind;this.percent=percent;this.latched=latched;
        }
    }
    private boolean active,latched;
    // Radial dead band filters the tiny touch-coordinate changes that are
    // common when the finger rests on a capacitive screen.
    static final double DISTANCE_DEADBAND_DP=1.25;
    private double lastHumDistance=Double.NaN;
    private int lastHumPercent;
    private long started;
    void begin(long now){started=now;active=true;latched=false;resetHum();}
    void stop(){active=false;latched=false;resetHum();}
    private void resetHum(){lastHumDistance=Double.NaN;lastHumPercent=0;}
    long remaining(long now){
        if(!active||now<started)return 0;
        return Math.max(0,MAX_GESTURE_MS-(now-started));
    }
    static float targetRadius(float width,float height){return .20f*Math.min(width,height);}
    static float haloRadius(float width,float height){return .46f*Math.min(width,height);}
    static int proximity(double distance,float inner,float outer){
        if(!(outer>inner)||distance>=outer)return 0;
        double progress=Math.max(0,Math.min(1,(outer-distance)/(outer-inner)));
        return (int)Math.round(MIN_APPROACH+(MAX_APPROACH-MIN_APPROACH)*Math.pow(progress,.75));
    }
    Reading sample(float x,float y,float width,float height,long now){
        if(remaining(now)==0){stop();return quiet(false);}
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)||
            width<=0||height<=0||x<0||x>=width||y<0||y>=height){
            latched=false;resetHum();return quiet(false);
        }
        float inner=targetRadius(width,height),outer=haloRadius(width,height);
        double distance=Math.hypot(x-width/2f,y-height/2f);
        if(latched){
            // Stay silent inside the target and through a narrow exit band.
            // Finger jitter at the rim must not cause a storm of snap impulses.
            if(distance<=inner+Math.min(6f,(outer-inner)*.20f))return quiet(true);
            latched=false;
        }
        if(distance<=inner){
            latched=true;resetHum();return new Reading(Kind.SNAP,100,true);
        }
        // Slight hysteresis at the halo boundary prevents starting and
        // stopping the motor repeatedly when a stationary finger straddles it.
        if(distance>=outer||(Double.isNaN(lastHumDistance)&&distance>outer-DISTANCE_DEADBAND_DP)){
            resetHum();return quiet(false);
        }
        if(!Double.isNaN(lastHumDistance)&&Math.abs(distance-lastHumDistance)<DISTANCE_DEADBAND_DP)
            return new Reading(Kind.HUM,lastHumPercent,false);
        lastHumDistance=distance;
        lastHumPercent=proximity(distance,inner,outer);
        return new Reading(Kind.HUM,lastHumPercent,false);
    }
    private static Reading quiet(boolean latched){return new Reading(Kind.QUIET,0,latched);}
}
