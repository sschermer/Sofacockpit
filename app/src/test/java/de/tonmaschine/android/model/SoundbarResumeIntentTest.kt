package de.tonmaschine.android.model

import org.junit.Test
import org.junit.Assert.*

class SoundbarResumeIntentTest {
    @Test fun restoresOnlyOnceForRequestedSessionEvenIfAlreadyLost() {
        val intent = SoundbarResumeIntent()
        intent.background(5, "device", true)
        assertTrue(intent.take(5, "device", true, true))
        assertFalse(intent.take(5, "device", true, true))
    }
    @Test fun powerIsNotPartOfRememberedIntent() {
        val intent = SoundbarResumeIntent()
        // A previous standby report must not erase the connection intention.
        intent.background(5, "device", true)
        assertTrue(intent.take(5, "device", true, true))
        intent.background(6, "device", true)
        assertTrue(intent.take(6, "device", true, true))
    }
    @Test fun disconnectChangedSessionOrDeniedPermissionInvalidateIntent() {
        val intent = SoundbarResumeIntent()
        intent.background(5, "device", false)
        assertFalse(intent.take(5, "device", true, true))
        intent.background(5, "device", true)
        assertFalse(intent.take(6, "device", true, true))
        intent.background(5, "device", true)
        assertFalse(intent.take(5, "other", true, true))
        intent.background(5, "device", true)
        assertFalse(intent.take(5, "device", false, true))
        intent.background(5, "device", true)
        assertFalse(intent.take(5, "device", true, false))
        intent.background(5, "device", true)
        intent.cancel()
        assertFalse(intent.take(5, "device", true, true))
    }
}
