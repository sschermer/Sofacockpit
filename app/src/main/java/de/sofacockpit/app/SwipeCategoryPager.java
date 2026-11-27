package de.sofacockpit.app;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Lab uses explicit page selection, preserving child gestures. */
final class SwipeCategoryPager extends FrameLayout {
    interface Listener { void selected(int index); }
    private int page;
    SwipeCategoryPager(Context context, Listener listener) {
        super(context);
        setClipChildren(true);
    }
    void addPage(View child) {
        addView(child,new FrameLayout.LayoutParams(-1,-1));
        render();
    }
    void select(int index) {
        if(index<0||index>=getChildCount())return;
        page=index;render();
    }
    void finishGesture(){render();}
    private void render(){
        for(int i=0;i<getChildCount();i++)getChildAt(i).setVisibility(i==page?VISIBLE:INVISIBLE);
    }
}
