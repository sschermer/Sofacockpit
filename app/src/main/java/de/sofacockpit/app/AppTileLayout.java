package de.sofacockpit.app;

/** Shared geometry and fallback rules for all Lab app tiles. No Android dependency. */
final class AppTileLayout {
    static final int HEIGHT_DP=72;
    static final int ICON_WIDTH_DP=52;
    static final int ICON_ONLY_HEIGHT_DP=44;
    static final int COMBINED_ICON_HEIGHT_DP=28;
    private AppTileLayout(){}
    static AppPresentation.Mode displayed(AppPresentation.Mode requested,boolean iconAvailable){
        return iconAvailable?requested:AppPresentation.Mode.TEXT;
    }
    static final class Strip {
        final int visible,tileWidth,gap,endPadding,stride;
        Strip(int visible,int tileWidth,int gap,int endPadding){
            this.visible=visible;this.tileWidth=tileWidth;this.gap=gap;
            this.endPadding=endPadding;this.stride=tileWidth+gap;
        }
    }
    /** Fit whole slots in the actual viewport; only the final rounding pixels are padding. */
    static Strip strip(int width,int minWidth,int gap,int maxVisible){
        if(width<=0||minWidth<=0||gap<0||maxVisible<1)throw new IllegalArgumentException();
        int count=Math.max(1,Math.min(maxVisible,(width+gap)/(minWidth+gap)));
        int tile=Math.max(1,(width-(count-1)*gap)/count);
        return new Strip(count,tile,gap,width-count*tile-(count-1)*gap);
    }
    static int snap(int offset,int stride,int maxScroll){
        if(stride<=0||maxScroll<=0)return 0;
        long slot=Math.round((double)Math.max(0,offset)/stride);
        return (int)Math.min(maxScroll,slot*stride);
    }
}
