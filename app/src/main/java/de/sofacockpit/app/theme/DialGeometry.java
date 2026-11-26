package de.sofacockpit.app.theme;

/** Cardinal sectors use diagonals as boundaries; center and outside are explicit. */
public final class DialGeometry {
    private DialGeometry() {}
    public static final int UP=0, RIGHT=1, DOWN=2, LEFT=3, OK=4, NONE=-1;
    public static int hit(float x,float y,float size,float centerRatio) {
        if(size<=0) return NONE;
        float dx=x-size/2,dy=y-size/2,r=size/2;
        float squared=dx*dx+dy*dy;
        if(squared>r*r) return NONE;
        if(squared<=r*r*centerRatio*centerRatio) return OK;
        return Math.abs(dx)>Math.abs(dy)?(dx>0?RIGHT:LEFT):(dy>0?DOWN:UP);
    }
}
