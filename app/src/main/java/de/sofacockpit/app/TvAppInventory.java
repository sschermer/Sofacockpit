package de.sofacockpit.app;

import de.sofacockpit.app.tv.TclAppLauncher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Device-scoped live inventory plus a presentation-only last known snapshot. */
final class TvAppInventory {
    private String device = "";
    private List<TclAppLauncher.App> apps = Collections.emptyList();
    private boolean verified, snapshot;
    private final java.util.Set<String> confirmed=new java.util.HashSet<>();

    void device(String identity) {
        String key = identity == null ? "" : identity;
        if (!device.equals(key)) {
            device = key;
            apps = Collections.emptyList();
            verified=false;snapshot=false;confirmed.clear();
        }
    }
    String device() { return device; }
    void restored(List<TclAppLauncher.App> cached){
        if(cached==null||snapshot)return;
        apps=Collections.unmodifiableList(new ArrayList<>(cached));
        snapshot=true;verified=false;confirmed.clear();
    }
    void received(List<TclAppLauncher.App> reported){received(reported,false);}
    void received(List<TclAppLauncher.App> reported,boolean incomplete){
        java.util.Map<String,TclAppLauncher.App> merged=new java.util.LinkedHashMap<>();
        confirmed.clear();
        for(TclAppLauncher.App app:reported){merged.put(app.packageName,app);confirmed.add(app.packageName);}
        if(incomplete)for(TclAppLauncher.App app:apps){
            if(merged.size()>=TvInventoryCache.MAX_APPS)break;
            if(app.supplemental)merged.putIfAbsent(app.packageName,app);
        }
        replaceSorted(merged.values());
    }
    private void replaceSorted(java.util.Collection<TclAppLauncher.App> values){
        List<TclAppLauncher.App> sorted=new ArrayList<>(values);
        sorted.sort(java.util.Comparator.comparing((TclAppLauncher.App a)->a.name,String.CASE_INSENSITIVE_ORDER).thenComparing(a->a.packageName));
        apps=Collections.unmodifiableList(sorted);snapshot=true;verified=true;
    }
    boolean confirmed(String pkg){return verified&&confirmed.contains(pkg);}
    List<TclAppLauncher.App> confirmedVisible(boolean includeSystem){
        List<TclAppLauncher.App> result=new ArrayList<>();
        for(TclAppLauncher.App app:visible(includeSystem))if(confirmed(app.packageName))result.add(app);
        return result;
    }
    /** OPEN checks only its target; don't discard other previously verified supplemental
     * entries from the same device. A full explicit refresh replaces them all. */
    void receivedLaunch(List<TclAppLauncher.App> reported, String requestedPackage) {
        java.util.Map<String,TclAppLauncher.App> merged = new java.util.LinkedHashMap<>();
        java.util.Set<String> previous=new java.util.HashSet<>(confirmed);confirmed.clear();
        for(TclAppLauncher.App app:reported){merged.put(app.packageName,app);confirmed.add(app.packageName);}
        if(snapshot)for(TclAppLauncher.App app:apps){
            if(merged.size()>=TvInventoryCache.MAX_APPS)break;
            if(app.supplemental&&!app.packageName.equals(requestedPackage)){
                merged.putIfAbsent(app.packageName,app);
                if(previous.contains(app.packageName))confirmed.add(app.packageName);
            }
        }
        replaceSorted(merged.values());
    }
    void stale(){verified=false;confirmed.clear();}
    boolean verified(){return verified;}
    boolean hasSnapshot(){return snapshot;}
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
        if(confirmed(pkg))return FavoriteStatus.AVAILABLE;
        return find(pkg)!=null?FavoriteStatus.UNCONFIRMED:FavoriteStatus.NOT_REPORTED;
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
