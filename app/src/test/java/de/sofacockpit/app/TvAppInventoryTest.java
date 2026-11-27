package de.sofacockpit.app;

import de.sofacockpit.app.tv.TclAppLauncher;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class TvAppInventoryTest {
    private static TclAppLauncher.App app(String name, String pkg) {
        return new TclAppLauncher.App(name, pkg);
    }

    @Test public void acceptsExactlyReportedAppsAndFindsByPackage() {
        TvAppInventory inventory = new TvAppInventory();
        inventory.device("tv-a");
        inventory.received(Arrays.asList(app("Dyn", "sport.dyn.app.android.prod"),
            app("YouTube", "com.google.android.youtube.tv")));
        assertTrue(inventory.verified());
        assertEquals(2, inventory.visible(false).size());
        assertEquals("Dyn", inventory.find("sport.dyn.app.android.prod").name);
        assertNull(inventory.find("com.netflix.ninja"));
        inventory.received(Collections.singletonList(app("Netflix", "com.netflix.ninja")));
        assertNull(inventory.find("sport.dyn.app.android.prod"));
        assertEquals(1, inventory.apps().size());
    }

    @Test public void filtersSystemAppsWithoutLosingThem() {
        TvAppInventory inventory = new TvAppInventory();
        inventory.device("a");
        inventory.received(Arrays.asList(app("Settings", "com.android.settings"),
            app("Netflix", "com.netflix.ninja")));
        assertEquals(1, inventory.visible(false).size());
        assertEquals(2, inventory.visible(true).size());
    }

    @Test public void failureKeepsLastKnownSnapshotButMarksItStale() {
        TvAppInventory inventory = new TvAppInventory();
        inventory.device("a");
        inventory.received(Collections.singletonList(app("Dyn", "sport.dyn.app.android.prod")));
        inventory.stale();
        assertFalse(inventory.verified());
        assertTrue(inventory.hasSnapshot());
        assertEquals(1, inventory.apps().size());
    }

    @Test public void deviceSwitchClearsOldSnapshot() {
        TvAppInventory inventory = new TvAppInventory();
        inventory.device("ip|pin1");
        inventory.received(Collections.singletonList(app("Dyn", "sport.dyn.app.android.prod")));
        inventory.device("ip|pin2");
        assertFalse(inventory.verified());
        assertFalse(inventory.hasSnapshot());
        assertTrue(inventory.apps().isEmpty());
        inventory.device("ip|pin2");
        inventory.received(Collections.emptyList());
        assertTrue(inventory.verified());
        assertTrue(inventory.hasSnapshot());
    }

    @Test public void savedNativeAndLegacyPackagesAreRecognized() {
        assertEquals("sport.dyn.app.android.prod",
            TclAppLauncher.packageForLink("sport.dyn.app.android.prod"));
        assertEquals("com.netflix.ninja", TclAppLauncher.packageForLink("netflix://"));
        assertNull(TclAppLauncher.packageForLink("https://example.com/movie/123"));
    }
}
