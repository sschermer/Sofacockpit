package de.sofacockpit.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** Standard Android pull-to-refresh on native scroll pages; keep horizontal gestures
 * and gesture-sensitive controls out of the refresh detector. */
final class NativePullRefreshLayout extends SwipeRefreshLayout {
    private final int slop;
    private float x,y;
    private boolean blocked;
    private int direction;
    NativePullRefreshLayout(Context context,View content,int accent,int background,Runnable refresh) {
        super(context);
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        addView(content,new ViewGroup.LayoutParams(-1,-1));
        setColorSchemeColors(accent);
        setProgressBackgroundColorSchemeColor(background);
        setOnChildScrollUpCallback((layout,child)->content.canScrollVertically(-1));
        setOnRefreshListener(refresh::run);
    }
    private boolean protectedAt(View v,float x,float y){
        if(v instanceof SeekBar||v instanceof HorizontalScrollView||v instanceof EditText)return true;
        if(!(v instanceof ViewGroup))return false;
        ViewGroup group=(ViewGroup)v;
        for(int i=group.getChildCount()-1;i>=0;i--){
            View child=group.getChildAt(i);
            if(child.getVisibility()!=View.VISIBLE)continue;
            float cx=x+group.getScrollX()-child.getX(),cy=y+group.getScrollY()-child.getY();
            if(cx>=0&&cy>=0&&cx<child.getWidth()&&cy<child.getHeight()&&protectedAt(child,cx,cy))return true;
        }
        return false;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            x=e.getX();y=e.getY();direction=0;blocked=protectedAt(this,x,y);
        } else if(action==MotionEvent.ACTION_POINTER_DOWN){
            if(direction==0)blocked=true;
        } else if(action==MotionEvent.ACTION_MOVE&&!blocked&&direction==0){
            float dx=e.getX()-x,dy=e.getY()-y;
            if(Math.abs(dx)>slop||Math.abs(dy)>slop){
                if(Math.abs(dx)>Math.abs(dy))blocked=true;
                else if(Math.abs(dy)>=Math.abs(dx))direction=1;
            }
        }
        boolean handled=super.dispatchTouchEvent(e);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){blocked=false;direction=0;}
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        return !blocked&&super.onInterceptTouchEvent(e);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        return !blocked&&super.onTouchEvent(e);
    }
    @Override public boolean onStartNestedScroll(View child,View target,int axes){
        return !blocked&&super.onStartNestedScroll(child,target,axes);
    }
    @Override public boolean onStartNestedScroll(View child,View target,int axes,int type){
        return !blocked&&super.onStartNestedScroll(child,target,axes,type);
    }
}
