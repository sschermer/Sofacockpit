package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.sofacockpit.app.HapticLabPatterns.*;

public class HapticLabPatternsTest {
    @Test public void strengthRangeAndFiniteEffects(){
        for(Cue cue:Cue.values()){
            assertTrue(cue.duration()>0&&cue.duration()<=240);
            assertEquals(cue.times.length,cue.amplitudes.length);
            for(int a:cue.scaled(0,true))assertEquals(0,a);
            int[] max=cue.scaled(100,true),ninety=cue.scaled(90,true);
            for(int i=0;i<max.length;i++){
                assertTrue(max[i]>=0&&max[i]<=255);
                assertTrue(ninety[i]<=max[i]);
                assertEquals(cue.amplitudes[i],max[i]);
                assertTrue(cue.scaled(100,false)[i]==0||cue.scaled(100,false)[i]==-1);
            }
        }
        assertEquals(255,Cue.LEVEL.scaled(100,true)[0]);
        assertEquals(230,Cue.LEVEL.scaled(90,true)[0]);
        assertEquals(204,Cue.LEVEL.scaled(80,true)[0]);
    }
    @Test public void reliefRecognizesLayersAndCenter(){
        assertEquals(-1,region(Surface.RELIEF,1,1,200,200));
        assertEquals(0,region(Surface.RELIEF,180,100,200,200));
        assertEquals(1,region(Surface.RELIEF,160,100,200,200));
        assertEquals(2,region(Surface.RELIEF,130,100,200,200));
        assertEquals(3,region(Surface.RELIEF,100,100,200,200));
        Gesture g=new Gesture();
        assertNull(g.begin(Surface.RELIEF,1,100,200,200,100));
        assertEquals(Cue.RISE,g.move(180,100,200,200,200).cue);
        assertNull(g.move(100,100,200,200,210));
        assertEquals(Cue.CENTER,g.move(99,100,200,200,300).cue);
        assertNull(g.move(99,100,200,200,400));
        assertEquals(Cue.FALL,g.move(199,100,200,200,500).cue);
        g.stop();
        assertNull(g.move(100,100,200,200,600));
    }
    @Test public void texturedMotionAndTimeout(){
        Gesture g=new Gesture();
        assertNull(g.begin(Surface.TEXTURES,10,50,300,100,0));
        assertEquals(Cue.FINE,g.move(130,50,300,100,80).cue);
        assertNull(g.move(133,50,300,100,160));
        assertEquals(Cue.FINE,g.move(144,50,300,100,240).cue);
        assertEquals(Cue.COARSE,g.move(240,50,300,100,320).cue);
        assertNull(g.move(245,50,300,100,400));
        assertEquals(Cue.COARSE,g.move(270,50,300,100,480).cue);
        assertNull(g.move(299,50,300,100,5000));
        assertNull(g.move(200,50,300,100,5100));
        assertEquals(100,g.begin(Surface.RAMP,299,50,300,100,6000).scale);
        assertEquals(10,g.move(1,50,300,100,6100).scale);
    }
    @Test public void detentsAndMotionDebounce(){
        Gesture g=new Gesture();
        assertEquals(Cue.RATCHET,g.begin(Surface.DETENTS,5,20,300,100,0).cue);
        assertNull(g.move(35,20,300,100,30));
        assertEquals(Cue.RATCHET,g.move(36,20,300,100,80).cue);
        assertNull(g.move(36,20,300,100,200));
    }
}
