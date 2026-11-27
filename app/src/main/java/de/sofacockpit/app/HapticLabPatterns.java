package de.sofacockpit.app;

/** Motor-independent finite patterns and spatial exploration geometry. */
final class HapticLabPatterns {
    enum Cue {
        TICK("Feiner Tick",new long[]{14},new int[]{110}),
        CLICK("Klick",new long[]{18,12},new int[]{255,65}),
        DOUBLE("Doppelklick",new long[]{22,36,22},new int[]{255,0,255}),
        THUD("Harter Anschlag",new long[]{45,24},new int[]{255,145}),
        RISE("Ansteigend",new long[]{20,20,20,20,25,12},new int[]{35,75,130,195,255,65}),
        FALL("Abfallend",new long[]{25,20,20,20,20,12},new int[]{255,195,130,75,35,12}),
        WAVE("Weiche Welle",new long[]{20,25,25,30,25,25,20},new int[]{30,80,165,255,165,80,30}),
        RATCHET("Rastpunkt",new long[]{13,12,22},new int[]{145,0,255}),
        FINE("Feines Raster",new long[]{16},new int[]{200}),
        COARSE("Grobes Raster",new long[]{28,12},new int[]{255,100}),
        CENTER("Erhöhte OK-Taste",new long[]{20,15,35,16},new int[]{180,0,255,80}),
        LEVEL("Stärkestufe",new long[]{32},new int[]{255});
        final String label;
        final long[] times;
        final int[] amplitudes;
        Cue(String label,long[] times,int[] amplitudes){
            this.label=label;this.times=times;this.amplitudes=amplitudes;
        }
        long duration(){long result=0;for(long n:times)result+=n;return result;}
        int[] scaled(int strength,boolean amplitudeControl){
            int[] result=new int[amplitudes.length];
            int level=Math.max(0,Math.min(100,strength));
            for(int i=0;i<result.length;i++){
                int amp=Math.round(amplitudes[i]*level/100f);
                result[i]=amp==0?0:amplitudeControl?amp:-1;
            }
            return result;
        }
    }
    enum Surface {
        RELIEF("Relief","Außen glatt; äußere Kante, Ring, innere Kante und erhöhte Mitte ertasten."),
        TEXTURES("Texturen","Links glatt, Mitte fein geriffelt, rechts grob geriffelt. Langsam und schnell streichen."),
        DETENTS("Rastleiste","Von links nach rechts: einzelne mechanisch wirkende Rastpunkte."),
        RAMP("Stärkerampe","Von links nach rechts: zehn ansteigende Stärkestufen bis zur gewählten Maximalstärke."),
        MAGNET("Magnetismus","Von außen zur OK-Taste streichen: stärkeres Dauersignal im Annäherungsring, kräftiges Einrasten und anschließend Ruhe. Zum erneuten Einrasten deutlich aus der Taste herausstreichen.");
        final String label,description;
        Surface(String label,String description){this.label=label;this.description=description;}
    }
    static final long MIN_INTERVAL=60,MAX_GESTURE_MS=5000;
    static final class Signal {
        final Cue cue;
        final int scale;
        Signal(Cue cue,int scale){this.cue=cue;this.scale=scale;}
    }
    static int region(Surface surface,float x,float y,float width,float height){
        if(width<=0||height<=0||x<0||x>=width||y<0||y>=height)return -1;
        switch(surface){
            case RELIEF:
                double r=Math.hypot(x-width/2f,y-height/2f)/(Math.min(width,height)*.43f);
                if(r>1)return -1;
                if(r>.78)return 0;
                if(r>.43)return 1;
                if(r>.29)return 2;
                return 3;
            case TEXTURES:return Math.min(2,(int)(3*x/width));
            case DETENTS:return Math.min(9,(int)(10*x/width));
            case RAMP:return Math.min(9,(int)(10*x/width));
            default:return -1;
        }
    }
    /** Motion-driven, no scheduled pulses. Re-arm only with a new touch. */
    static final class Gesture {
        private Surface surface;
        private boolean active;
        private long started,last;
        private float oldX,oldY,distance;
        private int zone;
        Signal begin(Surface s,float x,float y,float width,float height,long now){
            surface=s;active=true;started=now;last=now;oldX=x;oldY=y;distance=0;
            zone=region(s,x,y,width,height);
            return initial(s,zone);
        }
        void stop(){active=false;distance=0;}
        Signal move(float x,float y,float width,float height,long now){
            if(!active)return null;
            if(now<started||now-started>=MAX_GESTURE_MS){stop();return null;}
            float traveled=(float)Math.hypot(x-oldX,y-oldY);
            if(traveled<.8f)return null;
            oldX=x;oldY=y;
            distance=Math.min(80,distance+traveled);
            int next=region(surface,x,y,width,height);
            if(next<0){
                if(surface==Surface.RELIEF&&zone>=0&&now-last>=MIN_INTERVAL){
                    zone=-1;distance=0;last=now;
                    return new Signal(Cue.FALL,85);
                }
                zone=-1;distance=0;return null;
            }
            if(now-last<MIN_INTERVAL)return null;
            Signal signal=null;
            if(next!=zone){
                if(surface==Surface.RELIEF){
                    if(next==3)signal=new Signal(Cue.CENTER,100);
                    else if(next==0)signal=new Signal(zone<0?Cue.RISE:Cue.FALL,90);
                    else signal=new Signal(next>zone?Cue.CLICK:Cue.TICK,next==2?95:70);
                }else signal=initial(surface,next);
                zone=next;distance=0;
            }else{
                if(surface==Surface.TEXTURES&&next==1&&distance>=9)
                    signal=new Signal(Cue.FINE,75);
                else if(surface==Surface.TEXTURES&&next==2&&distance>=23)
                    signal=new Signal(Cue.COARSE,100);
                else if(surface==Surface.RELIEF&&next==1&&distance>=24)
                    signal=new Signal(Cue.TICK,55);
            }
            if(signal!=null){last=now;distance=0;}
            return signal;
        }
        private static Signal initial(Surface surface,int zone){
            if(zone<0)return null;
            switch(surface){
                case RELIEF:
                    if(zone==0)return new Signal(Cue.RISE,90);
                    if(zone==1)return new Signal(Cue.TICK,55);
                    if(zone==2)return new Signal(Cue.CLICK,95);
                    return new Signal(Cue.CENTER,100);
                case TEXTURES:
                    return zone==0?null:new Signal(zone==1?Cue.FINE:Cue.COARSE,zone==1?75:100);
                case DETENTS:return new Signal(Cue.RATCHET,100);
                case RAMP:return new Signal(Cue.LEVEL,10*(zone+1));
                default:return null;
            }
        }
    }
}
