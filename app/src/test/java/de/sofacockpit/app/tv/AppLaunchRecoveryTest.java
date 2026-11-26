package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppLaunchRecoveryTest {
    @Test public void recentLaunchOfAwakeTvPermitsOneNewSession() {
        assertTrue(AppLaunchRecovery.eligible(1500, 1000, true, true, true, false, true, false));
        // A replacement session starts without a launch timestamp.
        assertFalse(AppLaunchRecovery.eligible(1600, -1, true, true, true, false, true, false));
    }

    @Test public void staleOrCancelledLaunchDoesNotPermitRecovery() {
        assertFalse(AppLaunchRecovery.eligible(6001, 1000, true, true, true, false, true, false));
        assertFalse(AppLaunchRecovery.eligible(900, 1000, true, true, true, false, true, false));
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, false, true, true, false, true, false));
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, true, false, true, false, true, false));
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, true, true, true, false, false, false));
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, true, true, true, false, true, true));
    }

    @Test public void standbyUnknownPowerAndSecurityErrorsAreExcluded() {
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, true, true, false, false, true, false));
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, true, true, null, false, true, false));
        assertFalse(AppLaunchRecovery.eligible(1500, 1000, true, true, true, true, true, false));
    }
}
