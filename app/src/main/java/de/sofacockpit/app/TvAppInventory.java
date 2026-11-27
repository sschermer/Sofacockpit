package de.sofacockpit.app;

import de.sofacockpit.app.tv.TclAppLauncher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One in-memory, device-scoped T-Cast inventory. Failed requests never replace a confirmed list. */
final class TvAppInventory {
    private String device = "";
    private List<TclAppLauncher.App> apps = Collections.emptyList();
    private boolean verified;

    void device(String identity) {
        String key = identity == null ? "" : identity;
        if (!device.equals(key)) {
            device = key;
            apps = Collections.emptyList();
            verified = false;
        }
    }
    String device() { return device; }
    void received(List<TclAppLauncher.App> reported) {
        apps = Collections.unmodifiableList(new ArrayList<>(reported));
        verified = true;
    }
    /** OPEN checks only its target; don't discard other previously verified supplemental
     * entries from the same device. A full explicit refresh replaces them all. */
    void receivedLaunch(List<TclAppLauncher.App> reported, String requestedPackage) {
        java.util.Map<String,TclAppLauncher.App> merged = new java.util.LinkedHashMap<>();
        for (TclAppLauncher.App app : reported) merged.put(app.packageName, app);
        if (verified) for (TclAppLauncher.App app : apps) {
            if (app.supplemental && !app.packageName.equals(requestedPackage))
                merged.putIfAbsent(app.packageName, app);
        }
        List<TclAppLauncher.App> sorted = new ArrayList<>(merged.values());
        sorted.sort(java.util.Comparator.comparing((TclAppLauncher.App a) -> a.name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(a -> a.packageName));
        received(sorted);
    }
    void stale() { verified = false; }
    boolean verified() { return verified; }
    boolean hasSnapshot() { return !apps.isEmpty() || verified; }
    List<TclAppLauncher.App> apps() { return apps; }
    List<TclAppLauncher.App> visible(boolean includeSystem) {
        List<TclAppLauncher.App> result = new ArrayList<>();
        for (TclAppLauncher.App app : apps)
            if (includeSystem || !app.systemLike()) result.add(app);
        return result;
    }
    enum FavoriteStatus {
        AVAILABLE("TV-App erkannt (Start am TV prüfen)", ""),
        UNCONFIRMED("TV-App-Liste noch nicht aktuell bestätigt", "Ungeprüft"),
        NOT_REPORTED("Das zugeordnete Paket fehlt in der vom TV gemeldeten App-Liste. Die App kann dennoch installiert sein.", "Nicht gemeldet"),
        UNMAPPED("Individueller App-/Inhaltslink · keine eindeutige TV-App-Zuordnung", "Nicht zugeordnet");
        final String label, shortLabel;
        FavoriteStatus(String label, String shortLabel) { this.label = label; this.shortLabel = shortLabel; }
    }
    /** Home presentation contains every stored favorite, independently of the live inventory. */
    static final class FavoriteTile {
        final StreamingShortcuts.Entry entry;
        final FavoriteStatus status;
        FavoriteTile(StreamingShortcuts.Entry entry, FavoriteStatus status) {
            this.entry = entry; this.status = status;
        }
        boolean canTryStart() { return status != FavoriteStatus.UNMAPPED; }
    }
    List<FavoriteTile> homeFavorites(List<StreamingShortcuts.Entry> saved) {
        List<FavoriteTile> result = new ArrayList<>();
        for (StreamingShortcuts.Entry entry : saved)
            result.add(new FavoriteTile(entry, favoriteStatus(entry)));
        return result;
    }
    FavoriteStatus favoriteStatus(StreamingShortcuts.Entry entry) {
        String pkg = TclAppLauncher.packageForLink(entry.link);
        if (pkg == null) return FavoriteStatus.UNMAPPED;
        if (!verified) return FavoriteStatus.UNCONFIRMED;
        return find(pkg) != null ? FavoriteStatus.AVAILABLE : FavoriteStatus.NOT_REPORTED;
    }
    List<StreamingShortcuts.Entry> activeFavorites(List<StreamingShortcuts.Entry> saved) {
        List<StreamingShortcuts.Entry> active = new ArrayList<>();
        for (StreamingShortcuts.Entry entry : saved) {
            if (favoriteStatus(entry) == FavoriteStatus.AVAILABLE) active.add(entry);
        }
        return active;
    }
    TclAppLauncher.App find(String packageName) {
        if (packageName == null) return null;
        for (TclAppLauncher.App app : apps)
            if (app.packageName.equals(packageName)) return app;
        return null;
    }
}
