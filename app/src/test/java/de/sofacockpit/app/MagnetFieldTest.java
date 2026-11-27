package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.sofacockpit.app.MagnetField.Kind.*;

public class MagnetFieldTest {
    @Test public void approachAndRetreatTrackDistance(){
        MagnetField f=new MagnetField();f.begin(0);
        assertEquals(QUIET,f.sample(1,100,200,200,1).kind);
        MagnetField.Reading far=f.sample(12,100,200,200,10);
        MagnetField.Reading middle=f.sample(35,100,200,200,20);
        MagnetField.Reading near=f.sample(59,100,200,200,30);
        assertEquals(HUM,far.kind);assertEquals(HUM,middle.kind);assertEquals(HUM,near.kind);
        assertTrue(far.percent<middle.percent);assertTrue(middle.percent<near.percent);
        assertTrue(near.percent<=MagnetField.MAX_APPROACH);
        assertEquals(middle.percent,f.sample(35,100,200,200,40).percent);
        assertEquals(far.percent,f.sample(12,100,200,200,50).percent);
        assertEquals(QUIET,f.sample(1,100,200,200,60).kind);
    }
    @Test public void stationaryApproachSustainsAndSnapIsOneShot(){
        MagnetField f=new MagnetField();f.begin(100);
        assertEquals(HUM,f.sample(150,100,200,200,101).kind);
        assertEquals(HUM,f.sample(150,100,200,200,800).kind);
        MagnetField.Reading snap=f.sample(139,100,200,200,900);
        assertEquals(SNAP,snap.kind);assertEquals(100,snap.percent);assertTrue(snap.latched);
        assertEquals(QUIET,f.sample(100,100,200,200,1000).kind);
        assertTrue(f.sample(144,100,200,200,1010).latched);
        assertEquals(QUIET,f.sample(139,100,200,200,1020).kind);
        assertEquals(HUM,f.sample(147,100,200,200,1030).kind);
        assertEquals(SNAP,f.sample(139,100,200,200,1040).kind);
    }
    @Test public void stationaryJitterDoesNotModulateLevel(){
        MagnetField f=new MagnetField();f.begin(0);
        MagnetField.Reading first=f.sample(150,100,200,200,1);
        assertEquals(HUM,first.kind);
        assertEquals(first.percent,f.sample(150.6f,100,200,200,100).percent);
        assertEquals(first.percent,f.sample(149.4f,100,200,200,200).percent);
        assertTrue(f.sample(146,100,200,200,300).percent>first.percent);
    }
    @Test public void outerBoundaryDoesNotChatter(){
        MagnetField f=new MagnetField();f.begin(0);
        assertEquals(QUIET,f.sample(191.5f,100,200,200,1).kind);
        assertEquals(HUM,f.sample(189,100,200,200,2).kind);
        assertEquals(HUM,f.sample(191.5f,100,200,200,3).kind);
        assertEquals(QUIET,f.sample(192.1f,100,200,200,4).kind);
        assertEquals(QUIET,f.sample(191.5f,100,200,200,5).kind);
    }
    @Test public void directTouchAndFiveSecondDeadline(){
        MagnetField f=new MagnetField();f.begin(100);
        assertEquals(SNAP,f.sample(100,100,200,200,100).kind);
        assertEquals(1,f.remaining(5099));
        assertEquals(QUIET,f.sample(150,100,200,200,5100).kind);
        assertEquals(0,f.remaining(5100));
        assertEquals(QUIET,f.sample(100,100,200,200,6000).kind);
        f.begin(7000);
        assertEquals(SNAP,f.sample(100,100,200,200,7000).kind);
        f.stop();assertEquals(0,f.remaining(7001));
        assertEquals(QUIET,f.sample(150,100,200,200,7001).kind);
    }
    @Test public void invalidCoordinatesAndOutsideAreQuiet(){
        MagnetField f=new MagnetField();f.begin(1);
        assertEquals(QUIET,f.sample(Float.NaN,20,200,200,2).kind);
        assertEquals(QUIET,f.sample(200,100,200,200,3).kind);
        assertEquals(QUIET,f.sample(10,10,0,200,4).kind);
        assertEquals(QUIET,f.sample(100,0,200,200,5).kind);
    }
}
