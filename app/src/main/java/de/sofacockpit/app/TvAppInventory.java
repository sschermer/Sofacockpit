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
        AVAILABLE("Installierte TV-App zugeordnet"),
        UNCONFIRMED("TV-App-Bestand nicht aktuell bestätigt"),
        NOT_REPORTED("Zugeordnete App aktuell nicht gemeldet"),
        UNMAPPED("Individueller App-/Inhaltslink · keine eindeutige TV-App-Zuordnung");
        final String label;
        FavoriteStatus(String label) { this.label = label; }
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
