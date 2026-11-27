package de.sofacockpit.app;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteMagnetFieldTest {
    @Test public void approachRetreatAndOnlyOneSnap(){
        RemoteMagnetField f=new RemoteMagnetField();
        RemoteMagnetField.Target key=RemoteMagnetField.Target.rectangle(10,100,0,160,60,8,28);
        java.util.List<RemoteMagnetField.Target> keys=Collections.singletonList(key);
        assertEquals(0,f.sample(60,30,keys,4).gain,0);
        float far=f.sample(88,30,keys,4).gain;
        float near=f.sample(104,30,keys,4).gain;
        assertTrue(far>0);assertTrue(near>far);assertTrue(near<=.8f);
        assertEquals(near,f.sample(104,30,keys,4).gain,0);
        assertTrue(f.sample(109,30,keys,4).snap);
        assertFalse(f.sample(130,30,keys,4).snap);
        assertEquals(0,f.sample(106,30,keys,4).gain,0);
        assertTrue(f.sample(100,30,keys,4).gain>0);
        assertTrue(f.sample(110,30,keys,4).snap);
        f.reset();assertTrue(f.sample(110,30,keys,4).snap);
    }
    @Test public void dialCoresStayInsideActualSectors(){
        RemoteMagnetField.Target[] targets=RemoteMagnetField.dial(100,100,100,.40f,28);
        for(int i=0;i<targets.length;i++){
            RemoteMagnetField.Target t=targets[i];
            assertEquals(i,de.sofacockpit.app.theme.DialGeometry.hit(t.x,t.y,200,.40f));
            for(int angle=0;angle<360;angle+=10){
                float px=t.x+(float)Math.cos(Math.toRadians(angle))*t.a;
                float py=t.y+(float)Math.sin(Math.toRadians(angle))*t.a;
                assertEquals(i,de.sofacockpit.app.theme.DialGeometry.hit(px,py,200,.40f));
            }
        }
        RemoteMagnetField f=new RemoteMagnetField();
        java.util.List<RemoteMagnetField.Target> keys=Arrays.asList(targets);
        assertTrue(f.sample(100,100,keys,4).snap);
        assertFalse(f.sample(100,100,keys,4).snap);
        assertTrue(f.sample(100,61,keys,4).gain>0);
        assertTrue(f.sample(100,30,keys,4).snap);
        assertEquals(0,f.sample(100,30,keys,4).gain,0);
    }
    @Test public void clippedAndDisabledTargetsCanBeOmitted(){
        RemoteMagnetField.Target key=RemoteMagnetField.Target.rectangle(10,0,0,100,48,8,28);
        assertTrue(key.visibleWithin(0,0,100,48));
        assertFalse(key.visibleWithin(0,20,100,48));
        RemoteMagnetField f=new RemoteMagnetField();
        assertFalse(f.sample(50,24,Collections.emptyList(),4).snap);
    }
    @Test public void samePcmStreamAcceptsChangingFieldAndOneSnap(){
        HapticStreamSignal s=new HapticStreamSignal(100);
        float[] samples=new float[4800*3];
        s.field(.25f,false);s.render(samples,true,true);
        float low=peak(samples);
        s.field(.8f,false);s.render(samples,true,true);
        assertTrue(peak(samples)>low);
        s.field(0,true);
        float[] click=new float[HapticStreamSignal.SNAP_FRAMES*3];
        s.render(click,true,true);assertEquals(.8f,peak(click),.01f);
        s.field(0,false);s.render(samples,true,true);assertEquals(0,peak(samples),0);
    }
    private static float peak(float[] block){
        float p=0;
        for(int i=0;i<block.length;i+=3){
            assertEquals(0,block[i],0);assertEquals(0,block[i+1],0);
            p=Math.max(p,Math.abs(block[i+2]));
        }
        return p;
    }
}
