package de.sofacockpit.app;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** Scrollable, equally sized app slots. Align to whole slots after a swipe settles,
 * without interfering with taps, long presses, or the enclosing vertical scroll. */
final class LabAppStrip extends HorizontalScrollView {
    private final int maxVisible,minWidth,gap,height;
    private LinearLayout row;
    private int stride;
    private boolean touching;
    private final Runnable settle=()->{
        if(touching||row==null||!isAttachedToWindow())return;
        int max=Math.max(0,row.getWidth()-getWidth());
        int target=AppTileLayout.snap(getScrollX(),stride,max);
        if(target!=getScrollX())smoothScrollTo(target,0);
    };
    LabAppStrip(Context context,int maxVisible,int minWidthDp,int gapDp){
        super(context);
        this.maxVisible=maxVisible;minWidth=dp(minWidthDp);gap=dp(gapDp);height=dp(AppTileLayout.HEIGHT_DP);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setFillViewport(false);
    }
    void bind(LinearLayout content){
        row=content;
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBaselineAligned(false);
        addView(row,new HorizontalScrollView.LayoutParams(-2,-2));
    }
    void resizeTiles(){
        if(row==null)return;
        int viewport=getWidth();
        if(viewport<=0)viewport=maxVisible*minWidth+(maxVisible-1)*gap;
        AppTileLayout.Strip layout=AppTileLayout.strip(viewport,minWidth,gap,maxVisible);
        stride=layout.stride;
        int count=row.getChildCount();
        for(int i=0;i<count;i++){
            View tile=row.getChildAt(i);
            int margin=i+1<count?gap:0;
            android.view.ViewGroup.LayoutParams raw=tile.getLayoutParams();
            if(raw instanceof LinearLayout.LayoutParams){
                LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)raw;
                if(lp.width==layout.tileWidth&&lp.height==height&&lp.leftMargin==0&&
                    lp.rightMargin==margin&&lp.topMargin==0&&lp.bottomMargin==0&&lp.weight==0)continue;
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(layout.tileWidth,height);
            lp.rightMargin=margin;
            tile.setLayoutParams(lp);
        }
        int end=count>=layout.visible?layout.endPadding:0;
        if(row.getPaddingRight()!=end)row.setPadding(0,0,end,0);
        scheduleSettle();
    }
    private void scheduleSettle(){
        removeCallbacks(settle);
        if(!touching)postDelayed(settle,160);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        if(w>0&&w!=oldw)post(this::resizeTiles);
    }
    @Override protected void onScrollChanged(int l,int t,int oldl,int oldt){
        super.onScrollChanged(l,t,oldl,oldt);
        if(l!=oldl)scheduleSettle();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){touching=true;removeCallbacks(settle);}
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            touching=false;scheduleSettle();
        }
        return handled;
    }
    @Override protected void onDetachedFromWindow(){
        touching=false;removeCallbacks(settle);super.onDetachedFromWindow();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
