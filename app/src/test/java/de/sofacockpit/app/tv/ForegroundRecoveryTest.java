package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class ForegroundRecoveryTest {
    private static final String TV = "192.168.1.10";
    @Test public void errorWhileBackgroundedIsRecoveredOnlyOnResume() {
        ForegroundRecovery r = new ForegroundRecovery();
        r.background(TV);
        assertNull(r.take(100, true, TV));
        r.foreground(200, true);
        assertEquals(TV, r.take(200, true, TV));
        assertNull(r.take(201, true, TV));
    }
    @Test public void healthySessionIsNotReplacedAndLateErrorCanRecover() {
        ForegroundRecovery r = new ForegroundRecovery();
        r.background(TV);
        r.foreground(1000, true);
        assertNull(r.take(1000, false, TV));
        assertEquals(TV, r.take(26_000, true, TV));
        assertNull(r.take(26_001, true, TV));
    }
    @Test public void unrelatedLaterForegroundFailureDoesNotRetry() {
        ForegroundRecovery r = new ForegroundRecovery();
        r.background(TV);
        r.foreground(1000, true);
        assertNull(r.take(31_001, true, TV));
    }
    @Test public void manualDisconnectAndImportCancelRecovery() {
        ForegroundRecovery r = new ForegroundRecovery();
        r.background(TV);
        r.foreground(100, true);
        r.cancel();
        assertNull(r.take(101, true, TV));
    }
    @Test public void missingPairingDifferentHostOrMissingPermissionNeverReconnect() {
        ForegroundRecovery r = new ForegroundRecovery();
        r.background(TV); r.foreground(100,true);
        assertNull(r.take(101,true,null));
        r.background(TV); r.foreground(200,true);
        assertNull(r.take(201,true,"192.168.1.11"));
        r.background(TV); r.foreground(300,false);
        assertNull(r.take(301,true,TV));
    }
    @Test public void noPreviousActiveSessionMeansNoRecovery() {
        ForegroundRecovery r = new ForegroundRecovery();
        r.background(null);
        r.foreground(200,true);
        assertNull(r.take(201,true,TV));
    }
}
