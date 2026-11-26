package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CockpitNavigationTest {
    @Test public void tabsAndDirectSelection() {
        CockpitNavigation n = new CockpitNavigation();
        assertEquals(CockpitNavigation.START,n.category());
        assertEquals(1,CockpitNavigation.pageCount(0));
        assertEquals(3,CockpitNavigation.pageCount(1));
        assertEquals(3,CockpitNavigation.pageCount(2));
        n.select(2,2); assertEquals(2,n.page());
        n.select(1,0); assertEquals(0,n.page());
        assertFalse(CockpitNavigation.valid(0,1));
        assertFalse(CockpitNavigation.valid(2,3));
        assertFalse(CockpitNavigation.valid(-1,0));
    }
    @Test public void swipesStayInTheirCategoryAndDoNotWrap() {
        assertEquals(1,CockpitNavigation.swipeTarget(0,3,-100,64));
        assertEquals(2,CockpitNavigation.swipeTarget(1,3,-100,64));
        assertEquals(2,CockpitNavigation.swipeTarget(2,3,-100,64));
        assertEquals(0,CockpitNavigation.swipeTarget(0,3,100,64));
        assertEquals(1,CockpitNavigation.swipeTarget(1,3,30,64));
        assertEquals(0,CockpitNavigation.swipeTarget(1,3,100,64));
        assertFalse(CockpitNavigation.horizontal(25,50,8));
        assertTrue(CockpitNavigation.vertical(25,50,8));
        assertTrue(CockpitNavigation.horizontal(50,25,8));
    }
}
