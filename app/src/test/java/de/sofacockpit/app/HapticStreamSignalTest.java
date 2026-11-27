package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class HapticStreamSignalTest {
    private static float peak(float[] samples,int stride,int offset){
        float p=0;for(int i=offset;i<samples.length;i+=stride)p=Math.max(p,Math.abs(samples[i]));return p;
    }
    @Test public void directStereoIsSilentAndOnlyHapticChannelChanges(){
        HapticStreamSignal signal=new HapticStreamSignal(100);
        float[] block=new float[4800*3];
        signal.position(0);signal.render(block,true,true);assertEquals(0,peak(block,3,2),0);
        signal.position(.475f);signal.render(block,true,true);
        assertEquals(0,peak(block,3,0),0);assertEquals(0,peak(block,3,1),0);
        assertEquals(.32f,peak(block,3,2),.015f);
        signal.position(.85f);signal.render(block,true,true);
        assertEquals(.64f,peak(block,3,2),.015f);
        signal.render(block,true,false);assertEquals(0,peak(block,3,2),0);
    }
    @Test public void generatorHasMatchingStereoAndContinuesPhase(){
        HapticStreamSignal a=new HapticStreamSignal(50),b=new HapticStreamSignal(50);
        a.position(.85f);b.position(.85f);
        float[] whole=new float[2048],half=new float[1024];
        a.render(whole,false,true);
        b.render(half,false,true);
        for(int i=0;i<1024;i++)assertEquals(whole[i],half[i],0);
        b.render(half,false,true);
        for(int i=0;i<1024;i++)assertEquals(whole[i+1024],half[i],0);
        for(int i=0;i<whole.length;i+=2)assertEquals(whole[i],whole[i+1],0);
        assertTrue(peak(whole,2,0)<=.32f);
    }
    @Test public void snapInSameStreamThenSilenceAndHysteresis(){
        HapticStreamSignal s=new HapticStreamSignal(100);
        s.position(.85f);
        float[] block=new float[4800*3];s.render(block,true,true);
        s.position(.90f);assertTrue(s.latched());
        float[] click=new float[HapticStreamSignal.SNAP_FRAMES*3];s.render(click,true,true);
        assertEquals(.8f,peak(click,3,2),.01f);
        s.position(.99f);s.render(block,true,true);assertEquals(0,peak(block,3,2),0);
        s.position(.89f);assertTrue(s.latched());
        s.position(.87f);assertFalse(s.latched());
        s.position(.95f);s.render(click,true,true);assertEquals(.8f,peak(click,3,2),.01f);
    }
    @Test public void gainLimitsAndLinearPlateau(){
        assertEquals(0,HapticStreamSignal.approach(0),0);
        assertEquals(.4f,HapticStreamSignal.approach(.475f),.0001f);
        assertEquals(.8f,HapticStreamSignal.approach(.85f),.0001f);
        HapticStreamSignal s=new HapticStreamSignal(0);s.position(.95f);
        float[] block=new float[4800*3];s.render(block,true,true);assertEquals(0,peak(block,3,2),0);
    }
}
