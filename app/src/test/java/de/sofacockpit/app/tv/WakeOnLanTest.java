package de.sofacockpit.app.tv;

import org.junit.Test;
import java.net.InetAddress;
import java.util.Arrays;
import static org.junit.Assert.*;

public class WakeOnLanTest {
    @Test public void packetContainsSixFFBytesAndSixteenCopiesOfMac() {
        byte[] packet = WakeOnLan.packet("02:12:34:56:78:9A");
        assertEquals(102, packet.length);
        for (int i = 0; i < 6; i++) assertEquals((byte)0xff, packet[i]);
        byte[] mac = {2, 0x12, 0x34, 0x56, 0x78, (byte)0x9a};
        for (int i = 0; i < 16; i++)
            assertArrayEquals(mac, Arrays.copyOfRange(packet, 6 + i * 6, 12 + i * 6));
        assertEquals(9, WakeOnLan.PORT);
    }

    @Test public void normalizesAndRejectsInvalidMacs() {
        assertEquals("02:12:34:56:78:9A", WakeOnLan.normalizeMac("02-12-34-56-78-9a"));
        assertEquals("02:12:34:56:78:9A", WakeOnLan.normalizeMac("02123456789a"));
        assertEquals("", WakeOnLan.normalizeMac(" "));
        for (String invalid : new String[]{"11:12:34:56:78:9A", "FF:FF:FF:FF:FF:FF",
            "00:00:00:00:00:00", "02:12:34:56:78", "bad", "02-12:34-56:78-9A"}) {
            try { WakeOnLan.normalizeMac(invalid); fail("Invalid MAC accepted"); }
            catch (IllegalArgumentException expected) {}
        }
    }

    @Test public void calculatesActualBroadcastAndRejectsOtherSubnets() throws Exception {
        byte[] local = ip("192.0.2.130"), host = ip("192.0.2.150");
        assertArrayEquals(ip("192.0.2.191"), WakeOnLan.broadcastFor(local, 26, host));
        assertArrayEquals(ip("192.0.2.255"), WakeOnLan.broadcastFor(local, 24, host));
        assertArrayEquals(ip("192.0.3.255"), WakeOnLan.broadcastFor(local, 23, host));
        assertNull(WakeOnLan.broadcastFor(local, 26, ip("192.0.2.12")));
        assertNull(WakeOnLan.broadcastFor(local, 26, ip("192.0.2.128")));
        assertNull(WakeOnLan.broadcastFor(local, 26, ip("192.0.2.191")));
        assertNull(WakeOnLan.broadcastFor(local, 26, local));
        assertNull(WakeOnLan.broadcastFor(local, 32, host));
    }

    @Test public void offlineWakeRequiresConfigurationAndNoOtherSession() {
        assertTrue(WakeOnLan.useWake(false, false, true));
        assertFalse(WakeOnLan.useWake(true, false, true));
        assertFalse(WakeOnLan.useWake(false, true, true));
        assertFalse(WakeOnLan.useWake(false, false, false));
        assertEquals(3, WakeOnLan.PACKETS);
        assertEquals(3, WakeOnLan.CONNECT_ATTEMPTS);
    }
    private static byte[] ip(String address) throws Exception { return InetAddress.getByName(address).getAddress(); }
}
