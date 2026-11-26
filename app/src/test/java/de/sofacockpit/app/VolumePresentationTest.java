package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.sofacockpit.app.VolumePresentation.Mode.*;

public class VolumePresentationTest {
    @Test public void usesReportedArcOrEarcWhenTvLevelMissing() {
        assertEquals(SOUNDBAR, VolumePresentation.select(true, true, null, null, true, true, 7, 24));
        assertEquals(SOUNDBAR, VolumePresentation.select(true, true, null, null, true, true, 20, 0));
        assertEquals("HDMI ARC", VolumePresentation.arcLabel(7));
        assertEquals("HDMI eARC", VolumePresentation.arcLabel(20));
    }
    @Test public void usesTvWhenSoundbarOffOfflineOrDifferentInput() {
        assertEquals(TV, VolumePresentation.select(true, true, 18, 60, true, false, 20, 24));
        assertEquals(TV, VolumePresentation.select(true, true, 18, 60, false, true, 20, 24));
        assertEquals(TV, VolumePresentation.select(true, true, 18, 60, true, true, 1, 24));
        assertEquals(TV, VolumePresentation.select(true, true, 18, 60, true, true, 15, 24));
    }
    @Test public void bothValidValuesDoNotProveRouting() {
        assertEquals(BOTH, VolumePresentation.select(true, true, 18, 60, true, true, 20, 24));
        assertEquals(BOTH, VolumePresentation.select(true, null, 0, 100, true, true, 7, 100));
    }
    @Test public void staleStandbyInvalidAndUnknownValuesAreNotInvented() {
        assertEquals(UNKNOWN, VolumePresentation.select(false, true, 18, 60, false, true, 20, 24));
        assertEquals(UNKNOWN, VolumePresentation.select(true, false, 18, 60, true, false, 20, 24));
        assertEquals(UNKNOWN, VolumePresentation.select(true, true, 18, 0, true, true, 20, 101));
        assertEquals(UNKNOWN, VolumePresentation.select(true, true, 61, 60, true, null, 20, 24));
        assertEquals(UNKNOWN, VolumePresentation.select(true, true, null, null, true, true, null, 24));
        assertEquals(UNKNOWN, VolumePresentation.select(true, true, null, null, true, true, 15, 24));
    }
}
