package de.tonmaschine.android.model

import org.junit.Assert.*
import org.junit.Test
import de.tonmaschine.android.model.SoundbarRecoveryPolicy.Action.*
import de.tonmaschine.android.model.SoundbarRecoveryPolicy.Trigger.*

class SoundbarRecoveryPolicyTest {
    @Test fun lostSessionReplacedForEveryRecoveryTriggerEvenWithOldStandby() {
        for (trigger in SoundbarRecoveryPolicy.Trigger.values()) {
            assertEquals(REPLACE, SoundbarRecoveryPolicy.action(trigger,true,false,true,false))
            assertEquals(REPLACE, SoundbarRecoveryPolicy.action(trigger,true,false,false,false))
        }
        assertTrue(SoundbarRecoveryPolicy.limitUntilAwake(false))
        assertFalse(SoundbarRecoveryPolicy.limitUntilAwake(true))
        assertFalse(SoundbarRecoveryPolicy.limitUntilAwake(null))
    }
    @Test fun actualForegroundReplacesOldAwakeSocketAndFocusDoesNotDisruptHealthySocket() {
        assertEquals(REPLACE, SoundbarRecoveryPolicy.action(FOREGROUND,true,true,false,false))
        assertEquals(NONE, SoundbarRecoveryPolicy.action(FOCUS_RETURN,true,true,false,false))
        assertEquals(PROBE, SoundbarRecoveryPolicy.action(REFRESH,true,true,false,false))
    }
    @Test fun respectsExplicitDisconnectPowerOffAndLiveStandby() {
        for (trigger in SoundbarRecoveryPolicy.Trigger.values()) {
            assertEquals(NONE, SoundbarRecoveryPolicy.action(trigger,false,false,false,false))
            assertEquals(STANDBY, SoundbarRecoveryPolicy.action(trigger,true,false,false,true))
            assertEquals(STANDBY, SoundbarRecoveryPolicy.action(trigger,true,true,true,false))
        }
    }
}
