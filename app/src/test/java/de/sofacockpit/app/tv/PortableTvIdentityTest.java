package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;

public class PortableTvIdentityTest {
    @Test public void identityRoundTripsAndRetainsPairingProof() throws Exception {
        PortableTvIdentity original = PortableTvIdentity.create();
        PortableTvIdentity restored = PortableTvIdentity.parse(original.json());
        assertArrayEquals(original.cert.getEncoded(), restored.cert.getEncoded());
        assertArrayEquals(original.key.getEncoded(), restored.key.getEncoded());
        original.cert.verify(restored.cert.getPublicKey());
    }
    @Test public void mismatchedIdentityRejected() throws Exception {
        PortableTvIdentity a = PortableTvIdentity.create(), b = PortableTvIdentity.create();
        JSONObject bad = a.json().put("certificate", b.json().getString("certificate"));
        try { PortableTvIdentity.parse(bad); fail(); }
        catch (java.security.GeneralSecurityException expected) {}
    }
}
