package de.sofacockpit.app;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StreamingShortcutsBackupTest {
    @Test public void orderedAndEmptyListsArePreserved() throws Exception {
        JSONArray values = new JSONArray().put(new JSONObject().put("name","Dyn").put("link","sport.dyn.app.android.prod"))
            .put(new JSONObject().put("name","Netflix").put("link","netflix://"));
        List<StreamingShortcuts.Entry> entries = StreamingShortcuts.parseEntries(values);
        assertEquals("Dyn",entries.get(0).name);
        assertEquals("market://launch?id=sport.dyn.app.android.prod",entries.get(0).link);
        assertEquals("Netflix",entries.get(1).name);
        assertTrue(StreamingShortcuts.parseEntries(new JSONArray()).isEmpty());
    }
    @Test public void duplicateAndUnsafeLinksRejected() throws Exception {
        JSONArray duplicate = new JSONArray().put(new JSONObject().put("name","Netflix").put("link","netflix://"))
            .put(new JSONObject().put("name","Netflix 2").put("link","netflix://"));
        try { StreamingShortcuts.parseEntries(duplicate); fail(); } catch (IllegalArgumentException expected) {}
        try {
            StreamingShortcuts.parseEntries(new JSONArray().put(new JSONObject().put("name","Bad").put("link","intent://x")));
            fail();
        } catch (IllegalArgumentException expected) {}
    }
}
