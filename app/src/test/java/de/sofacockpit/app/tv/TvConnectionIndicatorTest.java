package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.sofacockpit.app.tv.TvConnectionIndicator.*;

public class TvConnectionIndicatorTest {
    @Test public void realConnectionAndPendingTakePrecedence() {
        assertEquals(CONNECTED, resolve(true, false, false, true, false));
        assertEquals(CONNECTING, resolve(false, true, false, true, false));
    }
    @Test public void normalStandbyDisconnectOrPassiveFailureWithWakeIsNeutral() {
        assertEquals(WAKE_READY, resolve(false, false, false, true, false));
        assertEquals(WAKE_READY, resolve(false, false, true, true, false));
    }
    @Test public void explicitFailedAttemptOrSecurityFailureRemainsError() {
        assertEquals(ERROR, resolve(false, false, true, true, true));
        assertEquals(ERROR, resolve(false, false, true, false, false));
    }
    @Test public void unconfiguredDisconnectedIsNotAnErrorOrWakeReady() {
        assertEquals(DISCONNECTED, resolve(false, false, false, false, false));
    }
}
