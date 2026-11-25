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
        assertEquals(3, TvProtocol.parse(reply).child(1).integer(1, -1));
        assertFalse(h.ready());
        reply = h.accept(TvProtocol.bytes(2, new byte[0]));
        assertEquals(3, TvProtocol.parse(reply).child(2).integer(1, -1));
        assertFalse(h.ready());
        reply = h.accept(TvProtocol.bytes(8, TvProtocol.number(1, 123)));
        assertEquals(123, TvProtocol.parse(reply).child(9).integer(1, -1));
        h.accept(TvProtocol.bytes(40, new byte[0]));
        assertTrue(h.ready());
        assertTrue(h.supportsKeys());
        assertArrayEquals(new byte[]{82, 4, 8, 19, 16, 3}, TvProtocol.key(19));
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
