package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class HapticReferenceRampTest {
    @Test public void waveformIsOneFiniteRampWithoutOffGapsBeforeHold(){
        HapticReferenceRamp.Waveform plan=HapticReferenceRamp.waveform(100);
        int rise=(int)(HapticReferenceRamp.RISE_MS/HapticReferenceRamp.STEP_MS);
        long total=0;
        for(int i=0;i<plan.times.length;i++){
            assertTrue(plan.times[i]>0);total+=plan.times[i];
            assertTrue(plan.amplitudes[i]>=0&&plan.amplitudes[i]<=255);
            if(i<rise){assertTrue(plan.amplitudes[i]>0);
                if(i>0)assertTrue(plan.amplitudes[i]>=plan.amplitudes[i-1]);}
            else if(i>rise)assertTrue(plan.amplitudes[i]<=plan.amplitudes[i-1]);
        }
        assertEquals(HapticReferenceRamp.DURATION_MS,total);
        assertEquals(255,plan.amplitudes[rise-1]);
        assertEquals(255,plan.amplitudes[rise]);assertEquals(400,plan.times[rise]);
        assertEquals(0,plan.amplitudes[plan.amplitudes.length-1]);
        for(int a:HapticReferenceRamp.waveform(0).amplitudes)assertEquals(0,a);
        for(int a:HapticReferenceRamp.waveform(80).amplitudes)assertTrue(a<=204);
    }
    @Test public void nativeEnvelopeRespectsLimitsAndExactTimeline(){
        HapticReferenceRamp.EnvelopePlan plan=HapticReferenceRamp.envelope(20,1000,16,5000);
        assertNotNull(plan);assertEquals(5,plan.times.length);
        long total=0;
        for(long duration:plan.times){assertTrue(duration>=20&&duration<=1000);total+=duration;}
        assertEquals(3700,total);
        assertEquals(1f,plan.intensities[2],0.0001f);
        assertEquals(1f,plan.intensities[3],0.0001f);
        assertEquals(0f,plan.intensities[4],0.0001f);
        HapticReferenceRamp.EnvelopePlan small=HapticReferenceRamp.envelope(50,300,16,4800);
        assertNotNull(small);
        assertTrue(small.times.length<=16);
        for(long duration:small.times)assertTrue(duration>=50&&duration<=300);
    }
    @Test public void unavailableNativeLimitsDoNotSilentlyShortenReference(){
        assertNull(HapticReferenceRamp.envelope(20,1000,16,3000));
        assertNull(HapticReferenceRamp.envelope(20,100,16,5000));
        assertNull(HapticReferenceRamp.envelope(350,1000,16,5000));
        assertNull(HapticReferenceRamp.envelope(0,1000,16,5000));
        assertNull(HapticReferenceRamp.envelope(20,1000,4,5000));
    }
}
