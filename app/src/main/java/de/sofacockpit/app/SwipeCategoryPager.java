package de.sofacockpit.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;

/** Category-local pager. Retains attached child views, protects nested controls,
 * locks vertical gestures to the child, and switches at most one page per swipe.
 */
final class SwipeCategoryPager extends FrameLayout {
    interface Listener { void selected(int index); }
    private final Listener listener;
    private final float slop, density;
    private int page, pointer = -1;
    private float downX, downY, offset;
    private boolean horizontal, vertical, protectedControl;
    private ValueAnimator animation;

    SwipeCategoryPager(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        density = getResources().getDisplayMetrics().density;
        setClipChildren(true);
    }
    void addPage(View child) {
        addView(child, new FrameLayout.LayoutParams(-1, -1));
        renderIdle();
    }
    void select(int index) {
        if (index < 0 || index >= getChildCount()) return;
        finishGesture();
        page = index;
        renderIdle();
    }
    void finishGesture() {
        cancelAnimation();
        horizontal = vertical = false;
        pointer = -1;
        renderIdle();
    }
    private void renderIdle() {
        offset = 0;
        for (int i=0;i<getChildCount();i++) {
            View child = getChildAt(i);
            child.setTranslationX(0);
            child.setVisibility(i == page ? VISIBLE : INVISIBLE);
        }
    }
    private void cancelAnimation() {
        if (animation != null) {
            ValueAnimator old = animation;
            animation = null;
            old.cancel();
        }
    }
    private void begin(MotionEvent event) {
        finishGesture();
        pointer = event.getPointerId(0);
        downX = event.getX(0); downY = event.getY(0);
        protectedControl = getChildCount() > page && protectedAt(getChildAt(page),downX,downY);
    }
    private boolean protectedAt(View view, float x, float y) {
        if (view instanceof SeekBar || view instanceof HorizontalScrollView ||
            view instanceof android.widget.EditText) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup)view;
        for (int i=group.getChildCount()-1;i>=0;i--) {
            View child = group.getChildAt(i);
            if (child.getVisibility()!=VISIBLE) continue;
            float cx=x+group.getScrollX()-child.getX(), cy=y+group.getScrollY()-child.getY();
            if (cx>=0 && cx<child.getWidth() && cy>=0 && cy<child.getHeight() &&
                protectedAt(child,cx,cy)) return true;
        }
        return false;
    }
    private boolean chooseDirection(MotionEvent event) {
        int p=event.findPointerIndex(pointer);
        if (p<0 || protectedControl || vertical) return false;
        float dx=event.getX(p)-downX, dy=event.getY(p)-downY;
        if (!horizontal && CockpitNavigation.vertical(dx,dy,slop)) vertical=true;
        if (!vertical && CockpitNavigation.horizontal(dx,dy,slop)) horizontal=true;
        return horizontal;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: begin(event); return false;
            case MotionEvent.ACTION_MOVE:
                if (chooseDirection(event)) { drag(event); return true; }
                return false;
            case MotionEvent.ACTION_POINTER_DOWN: protectedControl=true; return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: pointer=-1; return false;
            default: return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: begin(event); return true;
            case MotionEvent.ACTION_MOVE:
                if (horizontal || chooseDirection(event)) drag(event);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!horizontal) protectedControl=true;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex())==pointer) settle(page);
                return true;
            case MotionEvent.ACTION_UP:
                if (horizontal && pointer>=0) {
                    drag(event);
                    float threshold=Math.min(getWidth()*.22f,64f*density);
                    settle(CockpitNavigation.swipeTarget(page,getChildCount(),offset,threshold));
                }
                pointer=-1; return true;
            case MotionEvent.ACTION_CANCEL:
                if (horizontal) settle(page);
                pointer=-1; return true;
            default: return true;
        }
    }
    private void drag(MotionEvent event) {
        int p=event.findPointerIndex(pointer);
        if (p<0 || getWidth()<=0) return;
        float dx=event.getX(p)-downX;
        if ((page==0 && dx>0) || (page==getChildCount()-1 && dx<0)) dx=0;
        renderOffset(Math.max(-getWidth(),Math.min(getWidth(),dx)));
    }
    private void renderOffset(float value) {
        offset=value;
        for (int i=0;i<getChildCount();i++) {
            View child=getChildAt(i);
            child.setTranslationX((i-page)*(float)getWidth()+value);
            child.setVisibility(i==page || (i==page-1 && value>0) ||
                (i==page+1 && value<0) ? VISIBLE : INVISIBLE);
        }
    }
    private void settle(int target) {
        pointer=-1; horizontal=vertical=false;
        cancelAnimation();
        if (getWidth()==0 || (Math.abs(offset)<.5f && target==page)) { renderIdle(); return; }
        ValueAnimator next=ValueAnimator.ofFloat(offset,(page-target)*(float)getWidth());
        animation=next;
        next.setDuration(180);
        next.setInterpolator(new DecelerateInterpolator());
        next.addUpdateListener(a -> { if (animation==a) renderOffset((float)a.getAnimatedValue()); });
        next.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (animation!=a) return;
                animation=null;
                boolean changed=page!=target;
                page=target;
                renderIdle();
                if (changed) listener.selected(page);
            }
        });
        next.start();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        if (w!=oldw) finishGesture();
    }
    @Override protected void onDetachedFromWindow() {
        finishGesture();
        super.onDetachedFromWindow();
    }
}
