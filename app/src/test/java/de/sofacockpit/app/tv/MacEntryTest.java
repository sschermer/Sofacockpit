package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class MacEntryTest {
    @Test public void addsSeparatorsAndUppercaseAsYouType() {
        assertEquals("", MacEntry.format(""));
        assertEquals("A", MacEntry.format("a"));
        assertEquals("A2:", MacEntry.format("a2"));
        assertEquals("A2:B", MacEntry.format("a2:b"));
        assertEquals("A2:BC:", MacEntry.format("a2bc"));
        assertEquals("02:12:34:56:78:9A", MacEntry.format("02123456789a"));
        assertEquals("02:12:34:56:78:9A", MacEntry.format("02-12-34-56-78-9a"));
        assertEquals("02:12:34:56:78:9A", WakeOnLan.normalizeMac(MacEntry.format("02:12:34:56:78:9a")));
    }

    @Test public void invalidOrTooLongPasteIsRejected() {
        for (String value : new String[]{"02:12:34:56:78:9G", "02123456789A0", "not a MAC"}) {
            try { MacEntry.format(value); fail("Invalid entry accepted"); }
            catch (IllegalArgumentException expected) {}
        }
    }

    @Test public void cursorSkipsGeneratedSeparator() {
        assertEquals(0, MacEntry.cursorAfterDigits("02:1", 0));
        assertEquals(1, MacEntry.cursorAfterDigits("02:1", 1));
        assertEquals(3, MacEntry.cursorAfterDigits("02:1", 2));
        assertEquals(4, MacEntry.cursorAfterDigits("02:1", 3));
        assertEquals(3, MacEntry.cursorAfterDigits("02:", 2));
        assertEquals(17, MacEntry.cursorAfterDigits("02:12:34:56:78:9A", 12));
    }
}
