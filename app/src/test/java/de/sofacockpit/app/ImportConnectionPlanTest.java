package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImportConnectionPlanTest {
    @Test public void bothConfiguredDevicesConnect() {
        ImportConnectionPlan plan = ImportConnectionPlan.create(true,true,"192.168.1.2","192.168.1.3");
        assertEquals("192.168.1.2",plan.tvAddress);
        assertEquals("192.168.1.3",plan.soundAddress);
        assertTrue(plan.hasTargets());
        assertFalse(plan.tvNeedsPairing);
    }
    @Test public void disabledOptionNeverStartsConnections() {
        ImportConnectionPlan plan = ImportConnectionPlan.create(false,true,"192.168.1.2","192.168.1.3");
        assertFalse(plan.hasTargets());
    }
    @Test public void missingPairingDoesNotTriggerAutomaticPairingOrBlockSound() {
        ImportConnectionPlan plan = ImportConnectionPlan.create(true,false,"192.168.1.2","192.168.1.3");
        assertEquals("",plan.tvAddress);
        assertEquals("192.168.1.3",plan.soundAddress);
        assertTrue(plan.tvNeedsPairing);
        assertTrue(plan.hasTargets());
    }
    @Test public void missingAddressesAreSkipped() {
        ImportConnectionPlan plan = ImportConnectionPlan.create(true,true,""," ");
        assertFalse(plan.hasTargets());
        assertFalse(plan.tvNeedsPairing);
        assertTrue(ImportConnectionPlan.create(true,true,"192.168.1.2","").hasTargets());
    }
}
