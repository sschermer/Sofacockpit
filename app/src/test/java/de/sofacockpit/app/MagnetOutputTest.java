package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.sofacockpit.app.MagnetOutput.Kind.*;

public class MagnetOutputTest {
    private static MagnetField.Reading hum(int percent){
        return new MagnetField.Reading(MagnetField.Kind.HUM,percent,false);
    }
    private static MagnetField.Reading quiet(){
        return new MagnetField.Reading(MagnetField.Kind.QUIET,0,false);
    }
    private static MagnetField.Reading snap(){
        return new MagnetField.Reading(MagnetField.Kind.SNAP,100,true);
    }
    @Test public void stationaryFingerStartsOnlyOneFiniteVibration(){
        MagnetOutput output=new MagnetOutput();
        MagnetOutput.Plan first=output.update(hum(50),100,true,100,4900);
        assertEquals(HUM,first.kind);assertEquals(128,first.amplitude);
        assertEquals(4900,first.duration);assertFalse(first.hasRamp());
        for(long now=145;now<5000;now+=45)
            assertEquals(NONE,output.update(hum(50),100,true,now,5000-now).kind);
        assertEquals(STOP,output.update(hum(50),100,true,5000,0).kind);
        assertEquals(NONE,output.update(quiet(),100,true,5001,0).kind);
    }
    @Test public void jitterAndRateLimitDoNotRestartMotor(){
        MagnetOutput output=new MagnetOutput();
        assertEquals(HUM,output.update(hum(50),100,true,0,5000).kind);
        assertEquals(NONE,output.update(hum(51),100,true,100,4900).kind);
        assertEquals(NONE,output.update(hum(49),100,true,200,4800).kind);
        MagnetOutput.Plan next=output.update(hum(65),100,true,300,4700);
        assertEquals(HUM,next.kind);assertTrue(next.hasRamp());
        assertEquals(128,next.amplitudes[0]);assertEquals(166,next.amplitude);
        assertEquals(next.amplitude,next.amplitudes[3]);
        long total=0;
        for(int i=0;i<next.times.length;i++){
            total+=next.times[i];assertTrue(next.amplitudes[i]>0);
            if(i>0)assertTrue(next.amplitudes[i]>=next.amplitudes[i-1]);
        }
        assertEquals(4700,total);
        assertEquals(NONE,output.update(hum(80),100,true,330,4670).kind);
        MagnetOutput.Plan retreat=output.update(hum(35),100,true,500,4500);
        assertEquals(HUM,retreat.kind);assertTrue(retreat.hasRamp());
        for(int i=1;i<retreat.amplitudes.length;i++)assertTrue(retreat.amplitudes[i]<=retreat.amplitudes[i-1]);
        assertEquals(NONE,output.update(hum(35),100,true,1000,4000).kind);
    }
    @Test public void snapIsImmediateAndNotTruncatedByQuietSample(){
        MagnetOutput output=new MagnetOutput();
        output.update(hum(89),100,true,100,4900);
        MagnetOutput.Plan click=output.update(snap(),100,true,110,4890);
        assertEquals(SNAP,click.kind);assertEquals(255,click.amplitude);assertEquals(42,click.duration);
        assertEquals(NONE,output.update(quiet(),100,true,120,4880).kind);
        assertEquals(NONE,output.update(quiet(),100,true,200,4800).kind);
        assertEquals(HUM,output.update(hum(70),100,true,210,4790).kind);
        assertEquals(STOP,output.update(quiet(),100,true,211,4789).kind);
    }
    @Test public void noAmplitudeControlUsesConstantHumInsteadOfPulses(){
        MagnetOutput output=new MagnetOutput();
        MagnetOutput.Plan first=output.update(hum(10),90,false,100,4900);
        assertEquals(HUM,first.kind);assertEquals(-1,first.amplitude);assertEquals(4900,first.duration);
        assertEquals(NONE,output.update(hum(90),90,false,500,4500).kind);
        assertEquals(SNAP,output.update(snap(),90,false,900,4100).kind);
    }
    @Test public void deadlineAndResetBoundEveryPlan(){
        MagnetOutput output=new MagnetOutput();
        output.update(hum(20),100,true,0,5000);
        MagnetOutput.Plan nearEnd=output.update(hum(70),100,true,4980,20);
        assertEquals(HUM,nearEnd.kind);assertEquals(20,nearEnd.duration);assertFalse(nearEnd.hasRamp());
        MagnetOutput.Plan click=output.update(snap(),100,true,4995,5);
        assertEquals(SNAP,click.kind);assertEquals(5,click.duration);
        output.reset();
        assertEquals(HUM,output.update(hum(50),50,true,6000,5000).kind);
        assertEquals(STOP,output.update(hum(50),0,true,6001,4999).kind);
    }
}
