package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

public class TvProtocolTest {
    @Test public void pairingWireFields() throws Exception {
        TvProtocol.Message p = TvProtocol.parse(TvProtocol.pairRequest());
        assertEquals(2, p.integer(1, -1));
        assertEquals(200, p.integer(2, -1));
        assertEquals("atvremote", new String(p.child(10).messages.get(1), java.nio.charset.StandardCharsets.UTF_8));
        TvProtocol.Message o = TvProtocol.parse(TvProtocol.pairOptions()).child(20);
        assertEquals(1, o.integer(3, -1));
        assertEquals(3, o.child(1).integer(1, -1));
        assertEquals(6, o.child(1).integer(2, -1));
        TvProtocol.Message c = TvProtocol.parse(TvProtocol.pairConfiguration()).child(30);
        assertEquals(1, c.integer(2, -1));
        assertEquals(6, c.child(1).integer(2, -1));
    }
    @Test public void frameRoundTripAndTruncation() throws Exception {
        byte[] original = new byte[300];
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        TvProtocol.writeFrame(stream, original);
        assertArrayEquals(original, TvProtocol.readFrame(new ByteArrayInputStream(stream.toByteArray())));
        try { TvProtocol.readFrame(new ByteArrayInputStream(new byte[]{3, 1})); fail(); }
        catch (EOFException expected) {}
        try { TvProtocol.readFrame(new ByteArrayInputStream(TvProtocol.varint(TvProtocol.MAX_FRAME + 1))); fail(); }
        catch (IOException expected) {}
    }
    @Test public void negotiationAndPing() throws Exception {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        byte[] reply = h.accept(TvProtocol.bytes(1, TvProtocol.number(1, 511)));
        assertEquals(35, TvProtocol.parse(reply).child(1).integer(1, -1));
        assertFalse(h.ready());
        reply = h.accept(TvProtocol.bytes(2, new byte[0]));
        assertEquals(35, TvProtocol.parse(reply).child(2).integer(1, -1));
        assertFalse(h.ready());
        reply = h.accept(TvProtocol.bytes(8, TvProtocol.number(1, 123)));
        assertEquals(123, TvProtocol.parse(reply).child(9).integer(1, -1));
        h.accept(TvProtocol.bytes(40, new byte[0]));
        assertTrue(h.ready());
        assertTrue(h.supportsKeys());
        assertEquals(Boolean.FALSE, h.powerOn());
        assertArrayEquals(new byte[]{82, 4, 8, 19, 16, 3}, TvProtocol.key(19));
    }
    @Test public void powerStatusNegotiatedFromRealWireFields() throws Exception {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        // RemoteConfigure.code1 = 611 (PING|KEY|POWER|VOLUME|APP_LINK);
        // these bytes are independent of the encoder under test.
        byte[] reply = h.accept(new byte[]{0x0a, 0x03, 0x08, (byte)0xe3, 0x04});
        assertEquals(35, TvProtocol.parse(reply).child(1).integer(1, -1));
        assertFalse(h.ready());
        assertArrayEquals(new byte[]{0x12, 0x02, 0x08, 0x23},
            h.accept(new byte[]{0x12, 0x00}));
        assertFalse(h.ready());
        assertArrayEquals(new byte[]{0x4a, 0x02, 0x08, 0x7b},
            h.accept(new byte[]{0x42, 0x02, 0x08, 0x7b}));
        assertFalse(h.ready());
        assertNull(h.accept(new byte[]{(byte)0xc2, 0x02, 0x02, 0x08, 0x01}));
        assertTrue(h.ready());
        assertTrue(h.supportsKeys());
        assertEquals(Boolean.TRUE, h.powerOn());
        assertTrue(h.diagnostics().contains("TV-Funktionen=611, ausgehandelt=35"));
        assertTrue(h.diagnostics().contains("Configure=1, Active=1, Start=1, Ping=1"));
    }
    @Test public void pingAloneIsNotReadyAndDiagnosticShowsMissingStart() throws Exception {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        h.accept(new byte[]{0x0a, 0x02, 0x08, 0x23});
        h.accept(new byte[]{0x12, 0x00});
        h.accept(new byte[]{0x42, 0x02, 0x08, 0x01});
        assertFalse(h.ready());
        assertFalse(h.supportsKeys());
        assertNull(h.powerOn());
        assertTrue(h.diagnostics().contains("Configure=1, Active=1, Start=0, Ping=1"));
    }
    @Test public void missingKeyCapabilityAndEarlyStart() throws Exception {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        try { h.accept(TvProtocol.bytes(40, new byte[0])); fail(); }
        catch (IOException expected) {}
        h.accept(TvProtocol.bytes(1, TvProtocol.number(1, 1)));
        h.accept(TvProtocol.bytes(2, new byte[0]));
        h.accept(TvProtocol.bytes(40, new byte[0]));
        assertTrue(h.ready()); assertFalse(h.supportsKeys());
    }
    @Test public void pairingProofAndInvalidCode() {
        RSAPublicKey client = key("80a1", "010001");
        RSAPublicKey tv = key("ffb2", "010001");
        byte[] expected = TvProtocol.sha256(new byte[]{(byte)0x80,(byte)0xa1,1,0,1,(byte)0xff,(byte)0xb2,1,0,1,0x12,0x34});
        String valid = String.format(java.util.Locale.ROOT, "%02X1234", expected[0] & 255);
        assertArrayEquals(expected, TvProtocol.proof(client, tv, valid));
        String invalid = String.format(java.util.Locale.ROOT, "%02X1234", (expected[0] + 1) & 255);
        try { TvProtocol.proof(client, tv, invalid); fail(); }
        catch (IllegalArgumentException expectedError) {}
    }
    @Test public void rejectedPairing() throws Exception {
        byte[] reject = TvProtocol.concat(TvProtocol.number(1, 2), TvProtocol.number(2, 402));
        try { TvProtocol.expectPair(reject, 41); fail(); }
        catch (IOException error) { assertTrue(error.getMessage().contains("Kopplungscode")); }
    }
    private static RSAPublicKey key(String modulus, String exponent) {
        return new RSAPublicKey() {
            public BigInteger getModulus() { return new BigInteger(modulus, 16); }
            public BigInteger getPublicExponent() { return new BigInteger(exponent, 16); }
            public String getAlgorithm() { return "RSA"; }
            public String getFormat() { return null; }
            public byte[] getEncoded() { return null; }
        };
    }
}
