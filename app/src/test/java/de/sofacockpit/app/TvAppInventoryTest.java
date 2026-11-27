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

    @Test public void favoritesRemainOrderedAndAreNotDeletedWhenMissing() {
        TvAppInventory inventory = new TvAppInventory();
        inventory.device("tv");
        StreamingShortcuts.Entry dyn = new StreamingShortcuts.Entry("Dyn", "sport.dyn.app.android.prod");
        StreamingShortcuts.Entry netflix = new StreamingShortcuts.Entry("Mein Netflix", "netflix://");
        java.util.List<StreamingShortcuts.Entry> saved = Arrays.asList(dyn, netflix);
        inventory.received(Collections.singletonList(app("Netflix", "com.netflix.ninja")));
        assertEquals(Collections.singletonList(netflix), inventory.activeFavorites(saved));
        assertEquals(2, saved.size());
        inventory.received(Arrays.asList(app("Dyn", "sport.dyn.app.android.prod"),
            app("Netflix", "com.netflix.ninja")));
        assertEquals(saved, inventory.activeFavorites(saved));
        inventory.stale();
        assertTrue(inventory.activeFavorites(saved).isEmpty());
    }

    @Test public void savedNativeAndLegacyPackagesAreRecognized() {
        assertEquals("sport.dyn.app.android.prod",
            TclAppLauncher.packageForLink("sport.dyn.app.android.prod"));
        assertEquals("com.netflix.ninja", TclAppLauncher.packageForLink("netflix://"));
        assertNull(TclAppLauncher.packageForLink("https://example.com/movie/123"));
    }

    @Test public void homeFavoritesStayVisibleInSavedOrderAcrossInventoryChanges() {
        TvAppInventory inventory = new TvAppInventory();
        inventory.device("tv");
        StreamingShortcuts.Entry netflix = new StreamingShortcuts.Entry("Netflix", "netflix://");
        StreamingShortcuts.Entry prime = new StreamingShortcuts.Entry("Prime Video", "https://app.primevideo.com");
        StreamingShortcuts.Entry custom = new StreamingShortcuts.Entry("Serie", "https://www.netflix.com/title/80057281");
        java.util.List<StreamingShortcuts.Entry> saved = Arrays.asList(prime, netflix, custom);
        java.util.List<TvAppInventory.FavoriteTile> tiles = inventory.homeFavorites(saved);
        assertEquals(3, tiles.size());
        assertSame(prime, tiles.get(0).entry);
        assertSame(netflix, tiles.get(1).entry);
        assertSame(custom, tiles.get(2).entry);
        assertEquals(TvAppInventory.FavoriteStatus.UNCONFIRMED, tiles.get(0).status);
        assertTrue(tiles.get(0).canTryStart());
        assertEquals(TvAppInventory.FavoriteStatus.UNMAPPED, tiles.get(2).status);
        assertFalse(tiles.get(2).canTryStart());
        inventory.received(Collections.singletonList(app("Netflix", "com.netflix.ninja")));
        tiles = inventory.homeFavorites(saved);
        assertEquals(3, tiles.size());
        assertSame(prime, tiles.get(0).entry);
        assertEquals(TvAppInventory.FavoriteStatus.NOT_REPORTED, tiles.get(0).status);
        assertEquals(TvAppInventory.FavoriteStatus.AVAILABLE, tiles.get(1).status);
        assertEquals(Collections.singletonList(netflix), inventory.activeFavorites(saved));
        assertEquals(1, inventory.visible(false).size());
        inventory.received(Arrays.asList(app("Prime Video", "com.amazon.amazonvideo.livingroom"),
            app("Netflix", "com.netflix.ninja")));
        tiles = inventory.homeFavorites(saved);
        assertEquals(TvAppInventory.FavoriteStatus.AVAILABLE, tiles.get(0).status);
        assertEquals(Arrays.asList(prime, netflix), inventory.activeFavorites(saved));
        inventory.stale();
        assertEquals(3, inventory.homeFavorites(saved).size());
        assertEquals(TvAppInventory.FavoriteStatus.UNCONFIRMED, inventory.homeFavorites(saved).get(0).status);
        assertTrue(inventory.activeFavorites(saved).isEmpty());
        inventory.device("tv-other");
        assertEquals(3, inventory.homeFavorites(saved).size());
        assertTrue(inventory.visible(false).isEmpty());
        assertEquals(3, saved.size());
    }
}
