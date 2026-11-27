package de.sofacockpit.app;

import android.content.Context;
import android.content.SharedPreferences;
import de.sofacockpit.app.tv.TvProtocol;
import de.sofacockpit.app.tv.TclAppLauncher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Local favorites; live TCL inventories are not persisted or exported. */
final class StreamingShortcuts {
    static final class Entry {
        final String name, link;
        Entry(String name, String link) {
            this.name = name == null ? "" : name.trim();
            if (this.name.isEmpty() || this.name.length() > 48)
                throw new IllegalArgumentException("Name mit 1–48 Zeichen eingeben.");
            this.link = TvProtocol.normalizeLaunchLink(link);
        }
    }

    private final SharedPreferences prefs;
    final List<Entry> entries = new ArrayList<>();
    StreamingShortcuts(Context context) {
        prefs = context.getSharedPreferences("streaming_shortcuts", Context.MODE_PRIVATE);
        String saved = prefs.getString("entries", null);
        if (saved == null) {
            // New installations start without synthetic app selections.
            save();
        } else {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length() && entries.size() < 50; i++) {
                    JSONObject item = array.getJSONObject(i);
                    try { entries.add(new Entry(item.getString("name"), item.getString("link"))); }
                    catch (IllegalArgumentException ignored) {}
                }
            } catch (JSONException ignored) {
                // Keep a deliberately empty or unreadable saved list editable; don't overwrite it silently.
            }
        }
    }
    static boolean sameTarget(Entry first, Entry second) {
        if (first.name.equalsIgnoreCase(second.name) || first.link.equals(second.link)) return true;
        String a = TclAppLauncher.packageForLink(first.link), b = TclAppLauncher.packageForLink(second.link);
        return a != null && a.equals(b);
    }
    boolean contains(Entry candidate) {
        return conflict(entries, null, candidate) != null;
    }
    /** Check the exact proposed entry; skip only the entry being deliberately replaced. */
    static Entry conflict(List<Entry> saved, Entry old, Entry candidate) {
        for (Entry entry : saved)
            if (entry != old && sameTarget(entry, candidate)) return entry;
        return null;
    }
    static List<Entry> updated(List<Entry> saved, Entry old, Entry replacement) {
        List<Entry> next = new ArrayList<>(saved);
        int index = old == null ? -1 : next.indexOf(old);
        if (old != null && index < 0) throw new IllegalArgumentException("Favorit wurde geändert. Verwaltung erneut öffnen.");
        Entry duplicate = conflict(saved, old, replacement);
        if (duplicate != null)
            throw new IllegalArgumentException("Name oder Ziel bereits vorhanden: " + duplicate.name +
                ". Unter Gespeicherte Favoriten verwalten korrigieren.");
        if (old == null) {
            if (next.size() >= 50) throw new IllegalArgumentException("Maximal 50 Schnellwahltasten.");
            next.add(replacement);
        } else next.set(index, replacement);
        return next;
    }
    static List<Entry> moved(List<Entry> saved, Entry entry, int direction) {
        List<Entry> next = new ArrayList<>(saved);
        int index = next.indexOf(entry);
        if (index < 0 || (direction != -1 && direction != 1) ||
            index + direction < 0 || index + direction >= next.size())
            throw new IllegalArgumentException("Favoritenreihenfolge wurde geändert. Verwaltung erneut öffnen.");
        java.util.Collections.swap(next, index, index + direction);
        return next;
    }
    static List<Entry> removed(List<Entry> saved, Entry entry) {
        List<Entry> next = new ArrayList<>(saved);
        if (!next.remove(entry)) throw new IllegalArgumentException("Favorit nicht mehr vorhanden.");
        return next;
    }
    /** A reassign action replaces the target deliberately, retaining label and position. */
    static Entry forInstalledApp(Entry old, TclAppLauncher.App app) {
        return new Entry(old == null ? app.name : old.name, app.packageName);
    }
    /** All stored favorites, not only the launchable subset; in saved order. */
    List<Entry> savedFavorites() { return new ArrayList<>(entries); }
    JSONArray exportEntries() {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            try { array.put(new JSONObject().put("name", entry.name).put("link", entry.link)); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        return array;
    }
    static List<Entry> parseEntries(JSONArray array) throws JSONException {
        if (array.length() > 50) throw new IllegalArgumentException("Zu viele Streaming-Dienste");
        List<Entry> parsed = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            Entry entry = new Entry(item.getString("name"), item.getString("link"));
            for (Entry existing : parsed) if (existing.name.equalsIgnoreCase(entry.name) || existing.link.equals(entry.link))
                throw new IllegalArgumentException("Doppelte Streaming-Einträge");
            parsed.add(entry);
        }
        return parsed;
    }
    void replaceEntries(List<Entry> list) {
        entries.clear();
        entries.addAll(list);
        save();
    }
    void save() {
        prefs.edit().putString("entries", exportEntries().toString()).apply();
    }
}
