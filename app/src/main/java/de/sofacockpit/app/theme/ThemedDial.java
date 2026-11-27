package de.sofacockpit.app.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import java.util.List;
import java.util.function.IntConsumer;

/** One continuous circular pad, all five regions have their own accessibility node. */
public final class ThemedDial extends View {
    private static final String[] LABELS={"Oben","Rechts","Unten","Links","OK"};
    private final ThemePack pack;
    private final IntConsumer command;
    private final ExploreByTouchHelper accessibility;
    private int side,pressed=DialGeometry.NONE;
    private float left,top,downX,downY;
    private final int slop;

    public ThemedDial(Context context,ThemePack pack,IntConsumer command) {
        super(context);this.pack=pack;this.command=command;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setMinimumHeight(Math.round(240*getResources().getDisplayMetrics().density));
        setFocusable(true);
        accessibility=new ExploreByTouchHelper(this) {
            @Override protected int getVirtualViewAt(float x,float y){
                int id=hit(x,y);return id<0?INVALID_ID:id;
            }
            @Override protected void getVisibleVirtualViews(List<Integer> ids){for(int i=0;i<5;i++)ids.add(i);}
            @Override protected void onPopulateNodeForVirtualView(int id,AccessibilityNodeInfoCompat n){
                n.setClassName("android.widget.Button");n.setContentDescription(LABELS[id]);
                n.setEnabled(isEnabled());n.setFocusable(true);n.setClickable(isEnabled());
                if(isEnabled())n.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                float cx=left+side/2f,cy=top+side/2f,r=side/2f,inner=r*pack.theme.centerRatio;
                Rect b;
                switch(id){
                    case 0:b=new Rect((int)(cx-r*.70f),(int)top,(int)(cx+r*.70f),(int)(cy-inner));break;
                    case 1:b=new Rect((int)(cx+inner),(int)(cy-r*.70f),(int)(left+side),(int)(cy+r*.70f));break;
                    case 2:b=new Rect((int)(cx-r*.70f),(int)(cy+inner),(int)(cx+r*.70f),(int)(top+side));break;
                    case 3:b=new Rect((int)left,(int)(cy-r*.70f),(int)(cx-inner),(int)(cy+r*.70f));break;
                    default:b=new Rect((int)(cx-inner),(int)(cy-inner),(int)(cx+inner),(int)(cy+inner));
                }
                n.setBoundsInParent(b);
            }
            @Override protected boolean onPerformActionForVirtualView(int id,int action,Bundle args){
                if(action!=AccessibilityNodeInfoCompat.ACTION_CLICK||!isEnabled()||id<0||id>4)return false;
                activate(id);return true;
            }
        };
        ViewCompat.setAccessibilityDelegate(this,accessibility);
        ViewCompat.setImportantForAccessibility(this,ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w);
        int height=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?width:resolveSize(width,h);
        side=Math.min(width,height);setMeasuredDimension(width,height);
        left=(width-side)/2f;top=(height-side)/2f;
    }
    @Override protected void onDraw(Canvas c){ThemeArt.drawDial(c,left,top,side,pack,pressed,isEnabled());}
    private int hit(float x,float y){return DialGeometry.hit(x-left,y-top,side,pack.theme.centerRatio);}
    /** Local coordinates; returns -1 outside the circle. No device action. */
    public int regionAt(float x,float y){return hit(x,y);}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled())return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                pressed=hit(e.getX(),e.getY());downX=e.getX();downY=e.getY();invalidate();
                return pressed>=0;
            case MotionEvent.ACTION_MOVE:
                if(pressed>=0&&(hit(e.getX(),e.getY())!=pressed||
                    Math.hypot(e.getX()-downX,e.getY()-downY)>slop*1.5f)){pressed=-1;invalidate();}
                return true;
            case MotionEvent.ACTION_UP:
                int id=pressed;pressed=-1;invalidate();
                if(id>=0&&id==hit(e.getX(),e.getY()))activate(id);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:pressed=-1;invalidate();return true;
            default:return true;
        }
    }
    private void activate(int id){
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        command.accept(id);
        accessibility.sendEventForVirtualView(id,AccessibilityEvent.TYPE_VIEW_CLICKED);
    }
    @Override public void setEnabled(boolean enabled){
        if(isEnabled()==enabled && getAlpha()==(enabled?1f:.42f)) return;
        super.setEnabled(enabled);pressed=-1;setAlpha(enabled?1f:.42f);invalidate();
        if(accessibility!=null)accessibility.invalidateRoot();
    }
    @Override public boolean dispatchHoverEvent(MotionEvent e){return accessibility.dispatchHoverEvent(e)||super.dispatchHoverEvent(e);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return accessibility.dispatchKeyEvent(e)||super.dispatchKeyEvent(e);}
    @Override protected void onFocusChanged(boolean gain,int direction,Rect prev){
        super.onFocusChanged(gain,direction,prev);if(accessibility!=null)accessibility.onFocusChanged(gain,direction,prev);
    }
}
