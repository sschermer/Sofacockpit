package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppTileLayoutTest {
    @Test public void missingIconsFallbackWithoutChangingSavedChoice(){
        assertEquals(AppPresentation.Mode.TEXT,AppTileLayout.displayed(AppPresentation.Mode.ICON,false));
        assertEquals(AppPresentation.Mode.TEXT,AppTileLayout.displayed(AppPresentation.Mode.BOTH,false));
        for(AppPresentation.Mode mode:AppPresentation.Mode.values())
            assertEquals(mode,AppTileLayout.displayed(mode,true));
        assertEquals(72,AppTileLayout.HEIGHT_DP);
        assertTrue(AppTileLayout.ICON_ONLY_HEIGHT_DP<AppTileLayout.HEIGHT_DP);
    }
    @Test public void allSlotsFitAndFinalScrollAlignsForDifferentWidths(){
        for(int viewport=120;viewport<=1000;viewport++){
            for(int maxVisible:new int[]{3,4}){
                AppTileLayout.Strip s=AppTileLayout.strip(viewport,80,8,maxVisible);
                assertTrue(s.visible>=1&&s.visible<=maxVisible);
                assertEquals(viewport,s.visible*s.tileWidth+(s.visible-1)*s.gap+s.endPadding);
                int total=10*s.tileWidth+9*s.gap+s.endPadding;
                int maxScroll=total-viewport;
                assertEquals((10-s.visible)*s.stride,maxScroll);
                assertEquals(maxScroll,AppTileLayout.snap(maxScroll,s.stride,maxScroll));
            }
        }
    }
    @Test public void settledScrollNeverLeavesPartialLeadingSlot(){
        int stride=98,max=7*stride;
        assertEquals(0,AppTileLayout.snap(20,stride,max));
        assertEquals(stride,AppTileLayout.snap(60,stride,max));
        assertEquals(3*stride,AppTileLayout.snap(3*stride+12,stride,max));
        assertEquals(max,AppTileLayout.snap(20*stride,stride,max));
        assertEquals(0,AppTileLayout.snap(200,stride,0));
    }
}
