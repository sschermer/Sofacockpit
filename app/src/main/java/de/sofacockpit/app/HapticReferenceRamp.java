package de.sofacockpit.app;

import java.util.ArrayList;

/** A single finite reference effect. The requested timeline is independent of
 * touch coordinates and has no explicit off gaps before the final fade. */
final class HapticReferenceRamp {
    static final long RISE_MS=3000,HOLD_MS=400,FALL_MS=300,
        DURATION_MS=RISE_MS+HOLD_MS+FALL_MS;
    static final int STEP_MS=25;
    static final class Waveform {
        final long[] times;
        final int[] amplitudes;
        Waveform(long[] times,int[] amplitudes){this.times=times;this.amplitudes=amplitudes;}
    }
    static Waveform waveform(int strength){
        int maximum=Math.round(255f*Math.max(0,Math.min(100,strength))/100f);
        int rise=(int)(RISE_MS/STEP_MS),fall=(int)(FALL_MS/STEP_MS);
        long[] times=new long[rise+1+fall];
        int[] values=new int[times.length];
        for(int i=0;i<rise;i++){
            times[i]=STEP_MS;
            values[i]=maximum==0?0:Math.max(1,Math.round(maximum*(i+1f)/rise));
        }
        times[rise]=HOLD_MS;values[rise]=maximum;
        for(int i=0;i<fall;i++){
            times[rise+1+i]=STEP_MS;
            values[rise+1+i]=Math.round(maximum*(fall-i-1f)/fall);
        }
        return new Waveform(times,values);
    }

    static final class EnvelopePlan {
        final long[] times;
        final float[] intensities;
        EnvelopePlan(long[] times,float[] intensities){this.times=times;this.intensities=intensities;}
    }
    /** Split each phase into supported control point durations; do not silently
     * shorten the test, or rely on framework adaptation to hardware limits. */
    static EnvelopePlan envelope(long minimum,long maximum,int maxPoints,long maxDuration){
        if(minimum<=0||maximum<minimum||maxPoints<=0||maxDuration<DURATION_MS)return null;
        ArrayList<Long> times=new ArrayList<>();
        ArrayList<Float> values=new ArrayList<>();
        if(!phase(times,values,RISE_MS,0f,1f,minimum,maximum,maxPoints)
            ||!phase(times,values,HOLD_MS,1f,1f,minimum,maximum,maxPoints)
            ||!phase(times,values,FALL_MS,1f,0f,minimum,maximum,maxPoints))return null;
        long[] d=new long[times.size()];float[] v=new float[d.length];
        for(int i=0;i<d.length;i++){d[i]=times.get(i);v[i]=values.get(i);}
        return new EnvelopePlan(d,v);
    }
    private static boolean phase(ArrayList<Long> times,ArrayList<Float> values,
                                 long duration,float start,float end,long min,long max,int points){
        long count=1+(duration-1)/max;
        if(count>points-times.size()||duration/count<min)return false;
        long elapsed=0;
        for(int i=1;i<=count;i++){
            long next=duration*i/count;
            times.add(next-elapsed);
            values.add(i==count?end:start+(end-start)*next/duration);
            elapsed=next;
        }
        return true;
    }
}
