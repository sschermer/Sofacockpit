package de.sofacockpit.app;

import android.content.Context;
import android.content.SharedPreferences;
import de.sofacockpit.app.tv.TvProtocol;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** The local shortcut list is not an inventory of installed TV apps. */
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

    static final Entry[] CATALOG = {
        new Entry("Netflix", "netflix://"),
        new Entry("Prime Video", "https://app.primevideo.com"),
        new Entry("Apple TV+", "https://tv.apple.com"),
        new Entry("Disney+", "https://www.disneyplus.com"),
        // Play Store package from the public Dyn listing; native launch is unverified.
        new Entry("Dyn", "sport.dyn.app.android.prod"),
        new Entry("YouTube", "https://www.youtube.com"),
        new Entry("ARD Mediathek", "https://www.ardmediathek.de"),
        new Entry("ZDF Mediathek", "https://www.zdf.de/filme"),
        new Entry("Waipu.tv", "waipu://tv"),
        new Entry("Crunchyroll", "crunchyroll://"),
        new Entry("Zattoo", "zattoo://zattoo.com"),
        new Entry("Arte", "arte://display"),
        new Entry("Spotify", "spotify://"),
        new Entry("Paramount+", "https://www.paramountplus.com/"),
        new Entry("Rakuten Viki", "viki://home")
    };
    private final SharedPreferences prefs;
    final List<Entry> entries = new ArrayList<>();
    StreamingShortcuts(Context context) {
        prefs = context.getSharedPreferences("streaming_shortcuts", Context.MODE_PRIVATE);
        String saved = prefs.getString("entries", null);
        if (saved == null) {
            for (int i = 0; i < 5; i++) entries.add(CATALOG[i]);
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
    boolean contains(Entry candidate) {
        for (Entry entry : entries) if (entry.name.equalsIgnoreCase(candidate.name) || entry.link.equals(candidate.link)) return true;
        return false;
    }
    void save() {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            try { array.put(new JSONObject().put("name", entry.name).put("link", entry.link)); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        prefs.edit().putString("entries", array.toString()).apply();
    }
}
