package de.sofacockpit.app;

/** Geometry-independent tactile transition state. Never executes a command. */
final class LabHapticTracker {
    static final int NONE=-1, FIRST_BUTTON=10;
    enum Cue { NONE, CIRCLE, DIRECTION, CENTER, BUTTON }
    private int zone=NONE;
    private float lastX,lastY;
    private long lastTime;
    void begin(int start,float x,float y,long now) {
        zone=start;lastX=x;lastY=y;lastTime=now-1000;
    }
    Cue move(int next,float x,float y,long now,float margin) {
        if(next==zone)return Cue.NONE;
        if(next==NONE){zone=NONE;return Cue.NONE;}
        if(now-lastTime<65 || Math.hypot(x-lastX,y-lastY)<margin)return Cue.NONE;
        int previous=zone;
        zone=next;lastX=x;lastY=y;lastTime=now;
        if(next>=FIRST_BUTTON)return Cue.BUTTON;
        if(previous==NONE||previous>=FIRST_BUTTON)return Cue.CIRCLE;
        return next==4?Cue.CENTER:Cue.DIRECTION;
    }
    void reset(){zone=NONE;}
}
