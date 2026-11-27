package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

public class TvProtocolTest {
    @Test public void newRemoteKeysAreExplicitlyAllowedAndEncodeCorrectly() throws Exception {
        int[] keys = {android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            android.view.KeyEvent.KEYCODE_SETTINGS,
            android.view.KeyEvent.KEYCODE_TV_INPUT,
            android.view.KeyEvent.KEYCODE_TV_INPUT_HDMI_1,
            android.view.KeyEvent.KEYCODE_TV_INPUT_HDMI_2,
            android.view.KeyEvent.KEYCODE_TV_INPUT_HDMI_3,
            android.view.KeyEvent.KEYCODE_TV_INPUT_HDMI_4};
        for (int key : keys) {
            assertTrue(TvRemoteClient.allowedKey(key));
            TvProtocol.Message encoded = TvProtocol.parse(TvProtocol.key(key)).child(10);
            assertEquals(key, encoded.integer(1,-1));
            assertEquals(3, encoded.integer(2,-1));
        }
        assertFalse(TvRemoteClient.allowedKey(android.view.KeyEvent.KEYCODE_UNKNOWN));
    }
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
        assertEquals(99, TvProtocol.parse(reply).child(1).integer(1, -1));
        assertFalse(h.ready());
        reply = h.accept(TvProtocol.bytes(2, new byte[0]));
        assertEquals(99, TvProtocol.parse(reply).child(2).integer(1, -1));
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
        assertEquals(611, TvProtocol.parse(reply).child(1).integer(1, -1));
        assertFalse(h.ready());
        assertArrayEquals(new byte[]{0x12, 0x03, 0x08, (byte)0xe3, 0x04},
            h.accept(new byte[]{0x12, 0x00}));
        assertFalse(h.ready());
        assertArrayEquals(new byte[]{0x4a, 0x02, 0x08, 0x7b},
            h.accept(new byte[]{0x42, 0x02, 0x08, 0x7b}));
        assertFalse(h.ready());
        assertNull(h.accept(new byte[]{(byte)0xc2, 0x02, 0x02, 0x08, 0x01}));
        assertTrue(h.ready());
        assertTrue(h.supportsKeys());
        assertEquals(Boolean.TRUE, h.powerOn());
        assertTrue(h.diagnostics().contains("TV-Funktionen=611, ausgehandelt=611"));
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
    @Test public void appLinkWireAndInputValidation() throws Exception {
        TvProtocol.Message msg = TvProtocol.parse(TvProtocol.appLink("netflix://"));
        assertEquals("netflix://", new String(msg.child(90).messages.get(1), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("market://launch?id=sport.dyn.app.android.prod",
            TvProtocol.normalizeLaunchLink("sport.dyn.app.android.prod"));
        assertEquals("https://tv.apple.com", TvProtocol.normalizeLaunchLink("https://tv.apple.com"));
        for (String bad : new String[]{"", "javascript://foo", "intent://x", "file:///etc", "https://u:p@host", "https://a b"}) {
            try { TvProtocol.normalizeLaunchLink(bad); fail(bad); }
            catch (IllegalArgumentException expected) {}
        }
    }
    @Test public void playStoreAppLinkEncodesPackageBindingAndFallbackInTheFrame() throws Exception {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        TvProtocol.writeFrame(stream, TvProtocol.playStoreAppLink("sport.dyn.app.android.prod"));
        // Independent wire fixture: frame length 93, field 90 length 90, URI length 65,
        // package field 2 length 19 (com.android.vending), boolean field 3 = true.
        assertArrayEquals(wireBytes("5dd2055a0a41"
            + "68747470733a2f2f706c61792e676f6f676c652e636f6d2f617070732f6c61756e63683f69643d"
            + "73706f72742e64796e2e6170702e616e64726f69642e70726f64"
            + "1213636f6d2e616e64726f69642e76656e64696e671801"), stream.toByteArray());
    }
    @Test public void ordinaryAppLinkStillEncodesOnlyTheUri() throws Exception {
        assertArrayEquals(wireBytes("d2050c0a0a6e6574666c69783a2f2f"), TvProtocol.appLink("netflix://"));
        TvProtocol.Message request = TvProtocol.parse(TvProtocol.appLink(
            "https://play.google.com/apps/launch?id=sport.dyn.app.android.prod")).child(90);
        assertEquals(1, request.messages.size());
        assertTrue(request.has(1));
        assertTrue(request.integers.isEmpty());
    }
    @Test public void playStoreAppLinkRejectsPackageInjectionAndOversizedUris() {
        for (String bad : new String[]{null, "", "sport", ".sport.dyn", "sport..dyn", "sport.dyn.",
                "sport.1dyn", "sport.dyn&other=value", "sport.dyn?id=other", "sport.dyn#fragment",
                "sport.dyn/path", "sport.dyn%26other=value", "market://launch?id=sport.dyn",
                " sport.dyn", "sport.dyn\n", "sport.d\u00ffn"}) {
            try { TvProtocol.playStoreAppLink(bad); fail(String.valueOf(bad)); }
            catch (IllegalArgumentException expected) {}
        }
        // The entire generated URI stays within the existing 2048-character link limit.
        String atLimit = "a." + "b".repeat(2007);
        assertEquals(2048, ("https://play.google.com/apps/launch?id=" + atLimit).length());
        assertNotNull(TvProtocol.playStoreAppLink(atLimit));
        try { TvProtocol.playStoreAppLink(atLimit + "b"); fail("Oversized URI accepted"); }
        catch (IllegalArgumentException expected) {}
    }
    private static byte[] wireBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    private static TvProtocol.RemoteHandshake readyForApps() throws IOException {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        h.accept(TvProtocol.bytes(1, TvProtocol.number(1, 611)));
        h.accept(TvProtocol.bytes(2, new byte[0]));
        h.accept(TvProtocol.bytes(40, TvProtocol.number(1, 1)));
        return h;
    }
    private static byte[] rejected(byte[] request) {
        return TvProtocol.bytes(3, TvProtocol.concat(TvProtocol.number(1, 1), TvProtocol.bytes(2, request)));
    }
    @Test public void rejectedAppLaunchDoesNotDestroyReadySession() throws Exception {
        TvProtocol.RemoteHandshake h = readyForApps();
        assertNull(h.accept(rejected(TvProtocol.appLink("sport.dyn.app.android.prod"))));
        assertTrue(h.takeAppLinkRejection());
        assertFalse(h.takeAppLinkRejection());
        assertTrue(h.ready());
        assertTrue(h.supportsKeys());
        assertTrue(h.supportsApps());
        assertEquals(Boolean.TRUE, h.powerOn());
        byte[] reply = h.accept(TvProtocol.bytes(8, TvProtocol.number(1, 123)));
        assertEquals(123, TvProtocol.parse(reply).child(9).integer(1, -1));
        assertTrue(h.ready());
        assertEquals("netflix://", new String(TvProtocol.parse(TvProtocol.appLink("netflix://"))
            .child(90).messages.get(1), java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void appLinkRejectionDetailsPreserveCodeAndRequestAndAreConsumed() throws Exception {
        TvProtocol.RemoteHandshake h = readyForApps();
        // RemoteError code 7, field 2 containing the rejected RemoteMessage with netflix://.
        byte[] error = wireBytes("1a130807120fd2050c0a0a6e6574666c69783a2f2f");
        byte[] request = wireBytes("d2050c0a0a6e6574666c69783a2f2f");
        assertNull(h.takeAppLinkRejectionDetails());
        assertNull(h.accept(error));
        error[0] = 0; // Neither the incoming buffer nor a returned copy may mutate the details.
        TvProtocol.AppLinkRejection details = h.takeAppLinkRejectionDetails();
        assertNotNull(details);
        assertEquals(7L, details.code);
        assertArrayEquals(request, details.request());
        byte[] returned = details.request();
        returned[0] = 0;
        assertArrayEquals(request, details.request());
        assertNull(h.takeAppLinkRejectionDetails());
        assertFalse(h.takeAppLinkRejection());
        assertTrue(h.ready());
        assertTrue(h.supportsApps());
        assertNull(h.accept(rejected(request)));
        assertTrue(h.takeAppLinkRejection());
        assertNull(h.takeAppLinkRejectionDetails());
    }
    private static TvProtocol.AppLinkRejection rejectionDetails(byte[] request) throws IOException {
        TvProtocol.RemoteHandshake h = readyForApps();
        h.accept(rejected(request));
        return h.takeAppLinkRejectionDetails();
    }
    @Test public void appLinkRejectionMatchesReorderedFieldsAndExplicitDefaults() throws Exception {
        String uri = "https://play.google.com/apps/launch?id=sport.dyn.app.android.prod";
        byte[] reordered = TvProtocol.bytes(90, TvProtocol.concat(TvProtocol.number(4, 0),
            TvProtocol.number(3, 1), TvProtocol.string(2, "com.android.vending"), TvProtocol.string(1, uri)));
        assertTrue(rejectionDetails(reordered).matchesRequest(
            TvProtocol.playStoreAppLink("sport.dyn.app.android.prod")));
        byte[] defaults = TvProtocol.bytes(90, TvProtocol.concat(TvProtocol.number(3, 0),
            TvProtocol.string(2, ""), TvProtocol.number(4, 0), TvProtocol.string(1, "netflix://")));
        assertTrue(rejectionDetails(defaults).matchesRequest(TvProtocol.appLink("netflix://")));
    }
    @Test public void appLinkRejectionDoesNotMatchDifferentUrlsPackagesOrFlags() throws Exception {
        String uri = "https://play.google.com/apps/launch?id=sport.dyn.app.android.prod";
        TvProtocol.AppLinkRejection details = rejectionDetails(TvProtocol.playStoreAppLink("sport.dyn.app.android.prod"));
        for (byte[] different : new byte[][]{
                TvProtocol.playStoreAppLink("com.google.android.youtube.tv"),
                TvProtocol.bytes(90, TvProtocol.concat(TvProtocol.string(1, uri),
                    TvProtocol.string(2, "com.example.other"), TvProtocol.number(3, 1))),
                TvProtocol.bytes(90, TvProtocol.concat(TvProtocol.string(1, uri),
                    TvProtocol.string(2, "com.android.vending"), TvProtocol.number(3, 0))),
                TvProtocol.bytes(90, TvProtocol.concat(TvProtocol.string(1, uri),
                    TvProtocol.string(2, "com.android.vending"), TvProtocol.number(3, 1), TvProtocol.number(4, 1))),
                TvProtocol.bytes(90, TvProtocol.concat(TvProtocol.string(1, uri),
                    TvProtocol.string(2, "com.android.vending"))),
                TvProtocol.appLink(uri), null, new byte[0], new byte[]{(byte)0xd2, 0x05, 0x02, 0x0a}})
            assertFalse(details.matchesRequest(different));
    }
    @Test public void appLinkRejectionDoesNotGuessAboutExtraOrDuplicateFields() throws Exception {
        byte[] uri = TvProtocol.string(1, "netflix://");
        for (byte[] ambiguous : new byte[][]{
                TvProtocol.bytes(90, TvProtocol.concat(uri, TvProtocol.number(5, 0))),
                TvProtocol.bytes(90, TvProtocol.concat(uri, uri)),
                TvProtocol.bytes(90, TvProtocol.concat(uri, new byte[]{0x2d, 0, 0, 0, 0})),
                TvProtocol.concat(TvProtocol.appLink("netflix://"), TvProtocol.number(1, 0))})
            assertFalse(rejectionDetails(ambiguous).matchesRequest(TvProtocol.appLink("netflix://")));
    }
    @Test public void configureClearsPendingAppLinkRejectionDetails() throws Exception {
        TvProtocol.RemoteHandshake h = readyForApps();
        assertNull(h.accept(wireBytes("1a130807120fd2050c0a0a6e6574666c69783a2f2f")));
        h.accept(new byte[]{0x0a, 0x03, 0x08, (byte)0xe3, 0x04});
        assertFalse(h.ready());
        assertNull(h.takeAppLinkRejectionDetails());
        assertFalse(h.takeAppLinkRejection());
    }
    @Test public void appLinkRejectionWithoutNegotiatedCapabilityStillFailsClosed() throws Exception {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        h.accept(new byte[]{0x0a, 0x02, 0x08, 0x63}); // Features 99, without APP_LINK.
        h.accept(new byte[]{0x12, 0x00});
        h.accept(new byte[]{(byte)0xc2, 0x02, 0x02, 0x08, 0x01});
        assertTrue(h.ready());
        assertFalse(h.supportsApps());
        try {
            h.accept(wireBytes("1a130807120fd2050c0a0a6e6574666c69783a2f2f"));
            fail("App error ignored without negotiated capability");
        } catch (IOException expected) {}
        assertNull(h.takeAppLinkRejectionDetails());
    }
    @Test public void otherProtocolErrorsAndPrematureAppErrorStillFailClosed() throws Exception {
        for (byte[] error : new byte[][]{rejected(TvProtocol.key(19)),
                TvProtocol.bytes(3, TvProtocol.number(1, 1))}) {
            TvProtocol.RemoteHandshake h = readyForApps();
            try { h.accept(error); fail("Non-app error ignored"); }
            catch (IOException expected) {}
            assertFalse(h.takeAppLinkRejection());
            assertNull(h.takeAppLinkRejectionDetails());
        }
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        try { h.accept(rejected(TvProtocol.appLink("netflix://"))); fail("Premature app error ignored"); }
        catch (IOException expected) {}
        assertFalse(h.ready());
        assertNull(h.takeAppLinkRejectionDetails());
    }
    @Test public void reportedVolumeAndCapabilities() throws Exception {
        TvProtocol.RemoteHandshake h = new TvProtocol.RemoteHandshake();
        h.accept(TvProtocol.bytes(1, TvProtocol.number(1, 611)));
        h.accept(TvProtocol.bytes(2, new byte[0]));
        assertFalse(h.supportsApps());
        assertNull(h.volumeLevel());
        h.accept(TvProtocol.bytes(50, TvProtocol.concat(TvProtocol.number(6, 100), TvProtocol.number(7, 27), TvProtocol.number(8, 1))));
        assertNull(h.volumeLevel());
        h.accept(TvProtocol.bytes(40, TvProtocol.number(1, 1)));
        assertTrue(h.supportsApps());
        assertEquals(Integer.valueOf(100), h.volumeMax());
        assertEquals(Integer.valueOf(27), h.volumeLevel());
        assertEquals(Boolean.TRUE, h.muted());
        h.accept(TvProtocol.bytes(50, TvProtocol.number(6, 100)));
        assertEquals(Integer.valueOf(0), h.volumeLevel());
        assertEquals(Boolean.FALSE, h.muted());
        h.accept(TvProtocol.bytes(40, new byte[0]));
        assertEquals(Boolean.FALSE, h.powerOn());
        h.accept(TvProtocol.bytes(1, TvProtocol.number(1, 35)));
        assertFalse(h.supportsApps());
        assertNull(h.volumeLevel());
        h.accept(TvProtocol.bytes(2, new byte[0]));
        h.accept(TvProtocol.bytes(40, new byte[0]));
        assertFalse(h.supportsApps());
        h.accept(TvProtocol.bytes(50, TvProtocol.number(6, 100)));
        assertNull(h.volumeLevel());
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
