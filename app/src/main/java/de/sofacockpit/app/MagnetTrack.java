package de.sofacockpit.app;

/** Horizontal comparison geometry, deliberately independent of y within the pad.
 * The existing motor planner is shared with the circular magnet experiment. */
final class MagnetTrack {
    static final double START=.20, PLATEAU=.75, TARGET=.90;
    static final int PLATEAU_PERCENT=80;
    enum Curve { LINEAR, QUADRATIC }

    private boolean active,latched;
    private long started;
    private double lastHumX=Double.NaN;
    private int lastHumPercent;

    void begin(long now){started=now;active=true;latched=false;resetHum();}
    void stop(){active=false;latched=false;resetHum();}
    private void resetHum(){lastHumX=Double.NaN;lastHumPercent=0;}
    long remaining(long now){
        if(!active||now<started)return 0;
        return Math.max(0,MagnetField.MAX_GESTURE_MS-(now-started));
    }
    /** Relative intensity before the target, not an absolute motor percentage. */
    static double intensity(double position,Curve curve){
        double progress=Math.max(0,Math.min(1,(position-START)/(PLATEAU-START)));
        if(curve==Curve.QUADRATIC)progress*=progress;
        return PLATEAU_PERCENT*progress;
    }
    MagnetField.Reading sample(float x,float y,float width,float height,long now,Curve curve){
        if(remaining(now)==0){stop();return quiet(false);}
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(width)||!Float.isFinite(height)||
            width<=0||height<=0||x<0||x>=width||y<0||y>=height){
            latched=false;resetHum();return quiet(false);
        }
        double target=width*TARGET;
        if(latched){
            if(x>=target-Math.min(6, width*.02))return quiet(true);
            latched=false;
        }
        if(x>=target){
            latched=true;resetHum();
            return new MagnetField.Reading(MagnetField.Kind.SNAP,100,true);
        }
        // Left zone is truly silent, with a small re-entry buffer.
        double start=width*START;
        if(x<=start||(Double.isNaN(lastHumX)&&x<=start+MagnetField.DISTANCE_DEADBAND_DP)){
            resetHum();return quiet(false);
        }
        // Reach the exact plateau at its boundary, without waiting for movement
        // beyond it. Else ignore sub-dp horizontal coordinate jitter.
        int percent=(int)Math.round(intensity(x/(double)width,curve));
        if(percent<=0){resetHum();return quiet(false);}
        if(percent!=PLATEAU_PERCENT&&!Double.isNaN(lastHumX)&&
            Math.abs(x-lastHumX)<MagnetField.DISTANCE_DEADBAND_DP)
            return new MagnetField.Reading(MagnetField.Kind.HUM,lastHumPercent,false);
        lastHumX=x;lastHumPercent=percent;
        return new MagnetField.Reading(MagnetField.Kind.HUM,percent,false);
    }
    private static MagnetField.Reading quiet(boolean latched){
        return new MagnetField.Reading(MagnetField.Kind.QUIET,0,latched);
    }
}
