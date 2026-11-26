package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class RemoteLivenessTest {
    @Test public void refreshRequiresNewInboundFrameNotJustOutboundTraffic() {
        RemoteLiveness live = new RemoteLiveness(1000);
        assertFalse(live.receivedSince(1000));
        live.inbound(2000, true);
        assertFalse(live.receivedSince(3000));
        live.outbound(4000);
        assertFalse(live.receivedSince(3000));
        live.inbound(5000, true);
        assertTrue(live.receivedSince(3000));
        live.inbound(6000, false);
        assertFalse(live.receivedSince(3000));
    }

    @Test public void handshakeHasFixedDeadlineDespitePingsAndWrites() {
        RemoteLiveness live = new RemoteLiveness(1000);
        live.inbound(10_000, false);
        live.outbound(19_000);
        assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(20_999));
        assertEquals(RemoteLiveness.Expiry.HANDSHAKE, live.expiry(21_000));
    }

    @Test public void commandsKeepConfirmedSessionAliveWithoutIncomingPings() {
        RemoteLiveness live = new RemoteLiveness(0);
        live.inbound(1000, true);
        for (long now = 5000; now <= 120_000; now += 5000) {
            live.outbound(now);
            assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(now + 4999));
        }
        assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(144_999));
        assertEquals(RemoteLiveness.Expiry.IDLE, live.expiry(145_000));
    }

    @Test public void receivedPingsAndRepliesKeepConfirmedSessionAlive() {
        RemoteLiveness live = new RemoteLiveness(0);
        live.inbound(1000, true);
        live.inbound(5000, true);
        live.outbound(5001);
        live.inbound(10_000, true);
        assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(34_999));
        assertEquals(RemoteLiveness.Expiry.IDLE, live.expiry(35_000));
    }

    @Test public void renegotiationHasNewIndependentDeadline() {
        RemoteLiveness live = new RemoteLiveness(0);
        live.inbound(1000, true);
        live.inbound(12_000, false);
        live.outbound(15_000);
        live.inbound(29_000, false);
        assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(31_999));
        assertEquals(RemoteLiveness.Expiry.HANDSHAKE, live.expiry(32_000));
        live.inbound(31_500, true);
        assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(32_000));
    }

    @Test public void lateClockSamplesCannotMoveActivityBackwards() {
        RemoteLiveness live = new RemoteLiveness(0);
        live.inbound(1000, true);
        live.outbound(20_000);
        live.outbound(19_900);
        assertEquals(RemoteLiveness.Expiry.NONE, live.expiry(44_999));
        assertEquals(RemoteLiveness.Expiry.IDLE, live.expiry(45_000));
    }
}
