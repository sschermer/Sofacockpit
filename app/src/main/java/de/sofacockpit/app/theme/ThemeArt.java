package de.sofacockpit.app.theme;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

/** Shared native drawing primitives, also used by Compose and live previews. */
public final class ThemeArt {
    private ThemeArt() {}
    public static Typeface typeface(CockpitTheme t,int style) {
        return Typeface.create("mono".equals(t.font)?"monospace":
            "condensed".equals(t.font)?"sans-serif-condensed":"sans-serif",style);
    }
    public static int mix(int a,int b,float f) {
        return Color.rgb(Math.round(Color.red(a)*(1-f)+Color.red(b)*f),
            Math.round(Color.green(a)*(1-f)+Color.green(b)*f),
            Math.round(Color.blue(a)*(1-f)+Color.blue(b)*f));
    }
    public static Drawable surface(ThemePack p,int color,float radius,float density,boolean selected,boolean clickable) {
        Drawable bg=new Drawable() {
            @Override public void draw(Canvas c) {
                Rect b=getBounds();
                drawPanel(c,new RectF(b),p,color,radius*density,selected);
            }
            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(ColorFilter filter) {}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
        if(!clickable) return bg;
        return new RippleDrawable(ColorStateList.valueOf(0x24ffffff),bg,null);
    }
    public static void drawPanel(Canvas c,RectF r,ThemePack pack,int color,float radius,boolean selected) {
        if(r.width()<=0||r.height()<=0)return;
        CockpitTheme t=pack.theme;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float cut=Math.min(radius,Math.min(r.width(),r.height())*.22f);
        Path shape=new Path();
        if(t.style.equals("hud")&&cut>0){
            shape.moveTo(r.left+cut,r.top);shape.lineTo(r.right-cut,r.top);shape.lineTo(r.right,r.top+cut);
            shape.lineTo(r.right,r.bottom-cut);shape.lineTo(r.right-cut,r.bottom);shape.lineTo(r.left+cut,r.bottom);
            shape.lineTo(r.left,r.bottom-cut);shape.lineTo(r.left,r.top+cut);shape.close();
        } else shape.addRoundRect(r,cut,cut,Path.Direction.CW);
        if(t.style.equals("flat")) p.setColor(color);
        else p.setShader(new LinearGradient(r.left,r.top,r.left,r.bottom,
            mix(color,Color.WHITE,t.style.equals("metal")?.075f:.035f),
            mix(color,Color.BLACK,.12f),Shader.TileMode.CLAMP));
        c.drawPath(shape,p);p.setShader(null);
        c.save();c.clipPath(shape);
        if(pack.texture!=null) {
            p.setAlpha(20);c.drawBitmap(pack.texture,null,r,p);p.setAlpha(255);
        } else if(t.style.equals("metal")) {
            p.setStrokeWidth(1);p.setColor(0x07ffffff);
            for(float y=r.top+2;y<r.bottom;y+=3) c.drawLine(r.left,y,r.right,y,p);
        }
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r.width()/350));
        p.setColor(selected?t.accent:t.border);p.setAlpha(selected?255:t.style.equals("flat")?90:145);
        c.drawPath(shape,p);
        if(t.style.equals("flat")&&selected){
            p.setAlpha(255);p.setStrokeWidth(Math.max(2,r.height()*.055f));
            c.drawLine(r.left+2,r.bottom-2,r.right-2,r.bottom-2,p);
        }
    }
    public static void drawDial(Canvas c,float left,float top,float size,ThemePack pack,int pressed,boolean enabled) {
        if(size<=2)return;
        CockpitTheme t=pack.theme;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        c.save(); c.translate(left+size/2,top+size/2);
        float r=size/2-2, inner=r*t.centerRatio;
        RectF outer=new RectF(-r,-r,r,r);
        int rim=t.style.equals("hud")||t.style.equals("soft")?t.accent:t.border;
        if(!t.style.equals("flat")) {
            p.setShader(new RadialGradient(0,-r*.35f,r*1.6f,
                new int[]{mix(t.dial,Color.WHITE,.13f),t.dial,mix(t.dial,Color.BLACK,.3f)},null,Shader.TileMode.CLAMP));
        } else p.setColor(t.dial);
        c.drawCircle(0,0,r,p);p.setShader(null);
        c.save();Path clip=new Path();clip.addCircle(0,0,r,Path.Direction.CW);c.clipPath(clip);
        if(pack.texture!=null){p.setAlpha(22);c.drawBitmap(pack.texture,null,outer,p);p.setAlpha(255);}
        else if(t.style.equals("metal")) {
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.7f);p.setColor(0x0bffffff);
            for(float d=inner+3;d<r;d+=3)c.drawCircle(0,0,d,p);
            p.setStyle(Paint.Style.FILL);
        }
        if(pressed>=0&&pressed<4&&enabled){
            p.setColor(mix(t.dial,t.accent,.35f));
            c.drawArc(outer,-135+90*pressed,90,true,p);
        }
        c.restore();
        p.setStyle(Paint.Style.STROKE);p.setColor(t.border);p.setStrokeWidth(Math.max(1,size*.0035f));
        for(int i=0;i<4;i++){
            double a=Math.toRadians(-135+i*90);
            c.drawLine((float)Math.cos(a)*inner,(float)Math.sin(a)*inner,
                (float)Math.cos(a)*r,(float)Math.sin(a)*r,p);
        }
        p.setColor(rim);p.setStrokeWidth(Math.max(1,size*.004f));c.drawCircle(0,0,r,p);
        if(t.style.equals("hud")){
            p.setStrokeWidth(Math.max(2,size*.012f));RectF arc=new RectF(-r+5,-r+5,r-5,r-5);
            for(int i=0;i<4;i++)c.drawArc(arc,i*90+20,50,false,p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(pressed==DialGeometry.OK&&enabled?mix(t.control,t.accent,.4f):t.control);
        c.drawCircle(0,0,inner,p);
        p.setStyle(Paint.Style.STROKE);p.setColor(rim);p.setStrokeWidth(Math.max(1,size*.004f));c.drawCircle(0,0,inner,p);
        float a=r*.69f, w=size*.037f;
        for(int i=0;i<4;i++){
            c.save();c.rotate(i*90);
            if(pack.arrow!=null){
                p.setStyle(Paint.Style.FILL);p.setAlpha(enabled?255:100);
                float icon=size*.12f;
                c.drawBitmap(pack.arrow,null,new RectF(-icon/2,-a-icon/2,icon/2,-a+icon/2),p);p.setAlpha(255);
            } else {
                p.setColor(t.text);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,size*.007f));
                p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
                Path arrow=new Path();arrow.moveTo(-w,-a+w*.38f);arrow.lineTo(0,-a-w*.55f);arrow.lineTo(w,-a+w*.38f);
                c.drawPath(arrow,p);
            }
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);p.setColor(t.text);p.setTypeface(typeface(t,Typeface.BOLD));
        p.setTextSize(size*.072f*t.fontScale);p.setTextAlign(Paint.Align.CENTER);
        c.drawText("OK",0,-(p.ascent()+p.descent())/2,p);
        c.restore();
    }
}
