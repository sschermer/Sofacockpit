package de.sofacockpit.app;

import java.util.List;

/** Geometry and one-shot tactile latching, independent of device commands.
 * Smaller tactile cores fit inside the existing hit regions; the latter
 * are not changed. The strongest nearby field wins between adjacent keys. */
final class RemoteMagnetField {
    static final float MAX_APPROACH=.80f;
    static final int NONE=-1;
    static final class Target {
        final int id;
        final float x,y,a,b,halo;
        final boolean circle;
        private Target(int id,float x,float y,float a,float b,float halo,boolean circle){
            this.id=id;this.x=x;this.y=y;this.a=a;this.b=b;this.halo=halo;this.circle=circle;
        }
        static Target circle(int id,float x,float y,float radius,float halo){
            return new Target(id,x,y,radius,radius,halo,true);
        }
        static Target rectangle(int id,float left,float top,float right,float bottom,float inset,float halo){
            float w=right-left,h=bottom-top;
            float margin=Math.max(0,Math.min(inset,Math.min(w,h)*.2f));
            return new Target(id,(left+right)*.5f,(top+bottom)*.5f,w*.5f-margin,h*.5f-margin,halo,false);
        }
        boolean visibleWithin(float l,float t,float r,float b){
            return a>0&&this.b>0&&x-a>=l&&y-this.b>=t&&x+a<=r&&y+this.b<=b;
        }
        float distance(float px,float py){
            float dx=Math.abs(px-x),dy=Math.abs(py-y);
            if(circle)return (float)Math.hypot(dx,dy)-a;
            float u=dx-a,v=dy-b;
            return (float)Math.hypot(Math.max(0,u),Math.max(0,v))+Math.min(Math.max(u,v),0);
        }
        float gain(float distance){
            return halo>0?MAX_APPROACH*Math.max(0,Math.min(1,1-distance/halo)):0;
        }
    }
    static final class Reading {
        final float gain;
        final boolean snap;
        final int target;
        Reading(float gain,boolean snap,int target){this.gain=gain;this.snap=snap;this.target=target;}
    }
    private int latched=NONE;
    void reset(){latched=NONE;}
    Reading sample(float x,float y,List<Target> targets,float hysteresis){
        if(!Float.isFinite(x)||!Float.isFinite(y)){reset();return new Reading(0,false,NONE);}
        Target inside=null,held=null,nearest=null;
        float insideDistance=0,heldDistance=Float.POSITIVE_INFINITY,bestGain=0;
        for(Target target:targets){
            if(target.a<=0||target.b<=0)continue;
            float d=target.distance(x,y);
            if(target.id==latched){held=target;heldDistance=d;}
            if(d<=0&&(inside==null||d<insideDistance)){
                inside=target;insideDistance=d;
            }
            float gain=target.gain(d);
            if(gain>bestGain){nearest=target;bestGain=gain;}
        }
        if(inside!=null&&inside.id!=latched){
            latched=inside.id;return new Reading(0,true,latched);
        }
        if(held!=null&&heldDistance<=Math.max(0,hysteresis))
            return new Reading(0,false,latched);
        latched=NONE;
        if(nearest==null)return new Reading(0,false,NONE);
        return new Reading(bestGain,false,nearest.id);
    }
    /** Core centers correspond to the five actual DialGeometry sectors. */
    static Target[] dial(float cx,float cy,float radius,float centerRatio,float halo){
        float inner=radius*Math.max(0,Math.min(1,centerRatio));
        float width=radius-inner;
        float offset=inner+width*.5f;
        float core=width*.33f;
        return new Target[]{
            Target.circle(0,cx,cy-offset,core,halo),
            Target.circle(1,cx+offset,cy,core,halo),
            Target.circle(2,cx,cy+offset,core,halo),
            Target.circle(3,cx-offset,cy,core,halo),
            Target.circle(4,cx,cy,inner*.72f,halo)
        };
    }
}
