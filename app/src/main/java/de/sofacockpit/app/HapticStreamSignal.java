package de.sofacockpit.app;

/** Motor-independent continuous PCM carrier and spatial gain.
 * The producer never restarts its oscillator on a position change. */
final class HapticStreamSignal {
    static final int RATE=48000, SNAP_FRAMES=RATE*42/1000;
    static final float START=.20f, PLATEAU=.75f, TARGET=.90f;
    static final float PLATEAU_GAIN=.80f;
    static final double CARRIER_HZ=175.0;
    private final double step=2*Math.PI*CARRIER_HZ/RATE;
    // Approximately 12 ms time constant, with no discrete on/off between levels.
    private final double smoothing=1-Math.exp(-1.0/(RATE*.012));
    private final float maximum;
    private double phase,gain;
    private float desired;
    private int snapRemaining;
    private boolean latched;

    HapticStreamSignal(int strength){maximum=Math.max(0,Math.min(100,strength))/100f;}
    static float approach(float position){
        if(!Float.isFinite(position))return 0;
        return PLATEAU_GAIN*Math.max(0,Math.min(1,(position-START)/(PLATEAU-START)));
    }
    synchronized void position(float position){
        if(!Float.isFinite(position)){desired=0;snapRemaining=0;latched=false;return;}
        if(latched&&position<TARGET-.02f)latched=false;
        if(position>=TARGET){
            if(!latched){latched=true;snapRemaining=SNAP_FRAMES;}
            desired=0;
        }else if(latched)desired=0;
        else desired=maximum*approach(position);
    }
    /** Spatial production field. Its geometry supplies a one-time snap;
     * ongoing gain updates use the existing phase-continuous carrier. */
    synchronized void field(float gain,boolean snap){
        desired=maximum*(Float.isFinite(gain)?Math.max(0,Math.min(PLATEAU_GAIN,gain)):0);
        if(snap){desired=0;snapRemaining=SNAP_FRAMES;}
    }
    synchronized boolean latched(){return latched;}
    synchronized void render(float[] out,boolean direct,boolean allowSignal){
        int channels=direct?3:2;
        if(out.length%channels!=0)throw new IllegalArgumentException("whole PCM frames required");
        for(int i=0;i<out.length;i+=channels){
            double amplitude;
            if(!allowSignal){
                amplitude=0;gain=0;
            }else if(snapRemaining>0){
                amplitude=maximum;gain=maximum;
                if(--snapRemaining==0)gain=0;
            }else{
                gain+=(desired-gain)*smoothing;
                if(desired==0&&gain<.00005)gain=0;
                amplitude=gain;
            }
            float sample=(float)(.80*amplitude*Math.sin(phase));
            phase+=step;if(phase>=2*Math.PI)phase-=2*Math.PI;
            // Direct: silent L/R, signal only on haptic A. Generator: stereo
            // carrier. Its per-track audio gain is limited by the session.
            out[i]=direct?0:sample;out[i+1]=direct?0:sample;
            if(direct)out[i+2]=sample;
        }
    }
}
