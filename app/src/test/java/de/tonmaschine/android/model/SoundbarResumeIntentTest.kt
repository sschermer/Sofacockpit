package de.tonmaschine.android.model

import org.junit.Test
import org.junit.Assert.*

class SoundbarResumeIntentTest {
    @Test fun restoresOnlyOnceForRequestedSessionEvenIfAlreadyLost() {
        val intent = SoundbarResumeIntent()
        intent.background(5, "device", true, false)
        assertTrue(intent.take(5, "device", true, false, true))
        assertFalse(intent.take(5, "device", true, false, true))
    }

    @Test fun lostOrRetryingSessionCanRecoverAfterEveryForegroundReturn() {
        val intent = SoundbarResumeIntent()
        // Desired is independent of the last displayed connection state.
        intent.background(5, "device", true, false)
        assertTrue(intent.take(5, "device", true, false, true))
        intent.background(6, "device", true, false)
        assertTrue(intent.take(6, "device", true, false, true))
    }

    @Test fun storedAddressOrStandbyDoNotPermitProbe() {
        val intent = SoundbarResumeIntent()
        intent.background(5, "device", false, false)
        assertFalse(intent.take(5, "device", true, false, true))
        intent.background(5, "device", true, true)
        assertFalse(intent.take(5, "device", true, false, true))
        intent.background(5, "device", true, false)
        assertFalse(intent.take(5, "device", true, true, true))
    }

    @Test fun disconnectChangedSessionOrDeniedPermissionInvalidateIntent() {
        val intent = SoundbarResumeIntent()
        intent.background(5, "device", true, false)
        assertFalse(intent.take(6, "device", true, false, true))
        intent.background(5, "device", true, false)
        assertFalse(intent.take(5, "other", true, false, true))
        intent.background(5, "device", true, false)
        assertFalse(intent.take(5, "device", false, false, true))
        intent.background(5, "device", true, false)
        assertFalse(intent.take(5, "device", true, false, false))
        intent.background(5, "device", true, false)
        intent.cancel()
        assertFalse(intent.take(5, "device", true, false, true))
    }
}
