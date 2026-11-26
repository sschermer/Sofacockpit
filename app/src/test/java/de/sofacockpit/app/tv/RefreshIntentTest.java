package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class RefreshIntentTest {
    @Test public void savedAddressAloneIsNotPermissionToReconnect() {
        RefreshIntent intent = new RefreshIntent();
        assertFalse(intent.eligible("192.168.1.2", true));
        intent.begin("192.168.1.2");
        assertTrue(intent.eligible("192.168.1.2", true));
        assertFalse(intent.eligible("192.168.1.3", true));
        assertFalse(intent.eligible("192.168.1.2", false));
    }
    @Test public void manualDisconnectInvalidatesPendingRefresh() {
        RefreshIntent intent = new RefreshIntent();
        intent.begin("192.168.1.2");
        long pending = intent.revision();
        intent.clear();
        assertNotEquals(pending,intent.revision());
        assertFalse(intent.eligible("192.168.1.2",true));
    }
    @Test public void pairingAloneDoesNotEnableReconnectUntilConfirmed() {
        RefreshIntent intent = new RefreshIntent();
        intent.begin(null);
        assertFalse(intent.eligible("192.168.1.2",true));
        intent.confirmed("192.168.1.2",true);
        assertTrue(intent.eligible("192.168.1.2",true));
    }
    @Test public void confirmedStandbyPersistsUntilAwakeOrExplicitNewConnection() {
        RefreshIntent intent = new RefreshIntent();
        intent.begin("192.168.1.2");
        intent.confirmed("192.168.1.2",false);
        assertTrue(intent.standby());
        intent.confirmed("192.168.1.2",null);
        assertTrue(intent.standby());
        intent.confirmed("192.168.1.2",true);
        assertFalse(intent.standby());
        intent.confirmed("192.168.1.2",false);
        intent.begin("192.168.1.2");
        assertFalse(intent.standby());
    }
}
