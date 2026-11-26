package de.sofacockpit.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BackupCryptoTest {
    @Test public void encryptedRoundTripAndAuthentication() throws Exception {
        char[] password = "long-test-password-123".toCharArray();
        byte[] plain = "{\"tv\":\"test\"}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = BackupCrypto.encrypt(plain, password);
        assertFalse(new String(encrypted, StandardCharsets.UTF_8).contains("{\"tv\""));
        assertArrayEquals(plain, BackupCrypto.decrypt(encrypted, password));
        assertFalse(Arrays.equals(encrypted, BackupCrypto.encrypt(plain,password)));
        try { BackupCrypto.decrypt(encrypted,"another-password".toCharArray()); fail(); }
        catch (java.security.GeneralSecurityException expected) {}
        encrypted[encrypted.length-1] ^= 1;
        try { BackupCrypto.decrypt(encrypted,password); fail(); }
        catch (java.security.GeneralSecurityException expected) {}
    }
    @Test public void sizeAndPasswordLimits() throws Exception {
        try { BackupCrypto.decrypt(new byte[BackupCrypto.MAX_BYTES+1], "long-test-password".toCharArray()); fail(); }
        catch (java.io.IOException expected) {}
        try { BackupCrypto.encrypt(new byte[1], "short".toCharArray()); fail(); }
        catch (java.security.GeneralSecurityException expected) {}
    }
}
