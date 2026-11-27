package de.sofacockpit.app;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class HdmiInputProfileTest {
    @Test public void allConfirmedLegacyPortsRemainDirectAfterUpdate() {
        Set<String> saved = new HashSet<>();
        for (int port = 1; port <= 4; port++)
            saved.add("hdmi_native_hw15_v1_tv-a_" + port);
        for (int port = 1; port <= 4; port++) {
            assertTrue(HdmiInputProfile.canSelect("tv-a", port, saved::contains));
            assertFalse(HdmiInputProfile.canSelect("tv-b", port, saved::contains));
        }
    }

    @Test public void explicitModelSelectionEnablesAllPortsOnlyForThatDevice() {
        Set<String> saved = new HashSet<>();
        saved.add(HdmiInputProfile.profileKey("tv-b"));
        for (int port = 1; port <= 4; port++) {
            assertTrue(HdmiInputProfile.canSelect("tv-b", port, saved::contains));
            assertFalse(HdmiInputProfile.canSelect("tv-a", port, saved::contains));
        }
        assertFalse(HdmiInputProfile.canSelect("tv-b", 0, saved::contains));
        assertFalse(HdmiInputProfile.canSelect("tv-b", 5, saved::contains));
        assertFalse(HdmiInputProfile.canSelect(null, 1, saved::contains));
        assertFalse(HdmiInputProfile.canSelect("", 1, saved::contains));
    }

    @Test public void partialConfirmationDoesNotImplyOtherPorts() {
        Set<String> saved = new HashSet<>();
        saved.add("hdmi_native_hw15_v1_tv-a_2");
        assertTrue(HdmiInputProfile.canSelect("tv-a", 2, saved::contains));
        assertFalse(HdmiInputProfile.canSelect("tv-a", 1, saved::contains));
    }
}
