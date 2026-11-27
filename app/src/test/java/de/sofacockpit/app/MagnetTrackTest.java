package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.sofacockpit.app.MagnetField.Kind.*;

public class MagnetTrackTest {
    private static final MagnetTrack.Curve LINEAR=MagnetTrack.Curve.LINEAR;
    private static final MagnetTrack.Curve QUADRATIC=MagnetTrack.Curve.QUADRATIC;
    private static MagnetField.Reading sample(MagnetTrack track,float x,long now,MagnetTrack.Curve curve){
        return track.sample(x,50,400,100,now,curve);
    }
    @Test public void silentLeftAndTwoDistinctCurves(){
        MagnetTrack linear=new MagnetTrack(),quad=new MagnetTrack();
        linear.begin(0);quad.begin(0);
        assertEquals(QUIET,sample(linear,0,10,LINEAR).kind);
        assertEquals(QUIET,sample(linear,80,20,LINEAR).kind);
        assertEquals(40,sample(linear,190,30,LINEAR).percent);
        assertEquals(20,sample(quad,190,30,QUADRATIC).percent);
        assertEquals(80,sample(linear,300,40,LINEAR).percent);
        assertEquals(80,sample(quad,300,40,QUADRATIC).percent);
    }
    @Test public void plateauThenUnmistakableSnapAndSilence(){
        MagnetTrack track=new MagnetTrack();track.begin(0);
        assertEquals(80,sample(track,300,10,LINEAR).percent);
        assertEquals(80,sample(track,359,100,LINEAR).percent);
        MagnetField.Reading snap=sample(track,360,120,LINEAR);
        assertEquals(SNAP,snap.kind);assertEquals(100,snap.percent);
        assertEquals(QUIET,sample(track,390,140,LINEAR).kind);
        assertTrue(sample(track,357,200,LINEAR).latched);
        assertEquals(HUM,sample(track,352,250,LINEAR).kind);
        assertEquals(SNAP,sample(track,360,300,LINEAR).kind);
    }
    @Test public void xOnlyJitterRetreatAndDeadline(){
        MagnetTrack track=new MagnetTrack();track.begin(100);
        MagnetField.Reading mid=sample(track,190,110,LINEAR);
        assertEquals(mid.percent,track.sample(190.7f,90,400,100,200,LINEAR).percent);
        assertTrue(sample(track,150,300,LINEAR).percent<mid.percent);
        assertEquals(QUIET,sample(track,70,400,LINEAR).kind);
        assertEquals(QUIET,track.sample(190,-1,400,100,500,LINEAR).kind);
        assertEquals(QUIET,sample(track,390,5100,LINEAR).kind);
        track.begin(6000);
        assertEquals(SNAP,sample(track,390,6001,LINEAR).kind);
        track.stop();assertEquals(QUIET,sample(track,390,6002,LINEAR).kind);
    }
    @Test public void motorPlanKeepsPlateauAndJumpsToFullSnap(){
        MagnetTrack track=new MagnetTrack();track.begin(0);
        MagnetOutput output=new MagnetOutput();
        MagnetOutput.Plan plateau=output.update(sample(track,330,100,LINEAR),100,true,100,4900);
        assertEquals(MagnetOutput.Kind.HUM,plateau.kind);assertEquals(204,plateau.amplitude);
        assertEquals(MagnetOutput.Kind.NONE,output.update(sample(track,359,900,LINEAR),100,true,900,4100).kind);
        MagnetOutput.Plan snap=output.update(sample(track,360,901,LINEAR),100,true,901,4099);
        assertEquals(MagnetOutput.Kind.SNAP,snap.kind);assertEquals(255,snap.amplitude);assertEquals(42,snap.duration);
        assertEquals(MagnetOutput.Kind.NONE,output.update(sample(track,390,1000,LINEAR),100,true,1000,4000).kind);
    }
}
