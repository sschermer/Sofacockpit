package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabHapticTrackerTest {
    @Test public void entryCenterAndButtonsAreDistinct(){
        LabHapticTracker t=new LabHapticTracker();
        t.begin(-1,0,0,100);
        assertEquals(LabHapticTracker.Cue.CIRCLE,t.move(0,20,0,200,6));
        assertEquals(LabHapticTracker.Cue.NONE,t.move(0,40,0,300,6));
        assertEquals(LabHapticTracker.Cue.DIRECTION,t.move(1,50,0,400,6));
        assertEquals(LabHapticTracker.Cue.CENTER,t.move(4,70,0,500,6));
        assertEquals(LabHapticTracker.Cue.NONE,t.move(-1,90,0,600,6));
        assertEquals(LabHapticTracker.Cue.BUTTON,t.move(10,100,0,700,6));
        assertEquals(LabHapticTracker.Cue.BUTTON,t.move(11,120,0,800,6));
        assertEquals(LabHapticTracker.Cue.CIRCLE,t.move(2,140,0,900,6));
    }
    @Test public void debounceAndReset(){
        LabHapticTracker t=new LabHapticTracker();
        t.begin(0,0,0,100);
        assertEquals(LabHapticTracker.Cue.NONE,t.move(1,3,0,200,6));
        assertEquals(LabHapticTracker.Cue.DIRECTION,t.move(1,12,0,250,6));
        assertEquals(LabHapticTracker.Cue.NONE,t.move(4,24,0,260,6));
        assertEquals(LabHapticTracker.Cue.CENTER,t.move(4,30,0,330,6));
        t.begin(4,30,0,400);
        assertEquals(LabHapticTracker.Cue.NONE,t.move(4,40,0,500,6));
    }
}
