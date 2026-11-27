package de.sofacockpit.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned authenticated encrypted configuration. No cleartext export mode. */
final class BackupCrypto {
    static final int MAX_BYTES = 1024 * 1024;
    static final int MIN_PASSWORD = 12;
    private static final byte[] MAGIC = "SOFACFG1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT = 16, IV = 12, HEADER = MAGIC.length + SALT + IV;
    private static final int ROUNDS = 600_000;

    static byte[] encrypt(byte[] plain, char[] password) throws GeneralSecurityException, IOException {
        if (plain.length > MAX_BYTES - HEADER - 16) throw new IOException("Konfiguration zu groß");
        byte[] salt = new byte[SALT], iv = new byte[IV];
        SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(iv);
        byte[] header = new byte[HEADER];
        System.arraycopy(MAGIC,0,header,0,MAGIC.length);
        System.arraycopy(salt,0,header,MAGIC.length,SALT);
        System.arraycopy(iv,0,header,MAGIC.length+SALT,IV);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv);
        cipher.updateAAD(header);
        byte[] encrypted = cipher.doFinal(plain);
        byte[] result = Arrays.copyOf(header, HEADER + encrypted.length);
        System.arraycopy(encrypted,0,result,HEADER,encrypted.length);
        return result;
    }
    static byte[] decrypt(byte[] input, char[] password) throws GeneralSecurityException, IOException {
        if (input.length < HEADER + 16 || input.length > MAX_BYTES ||
            !Arrays.equals(MAGIC, Arrays.copyOf(input, MAGIC.length)))
            throw new IOException("Keine unterstützte Sofacockpit-Konfiguration");
        byte[] salt = Arrays.copyOfRange(input,MAGIC.length,MAGIC.length+SALT);
        byte[] iv = Arrays.copyOfRange(input,MAGIC.length+SALT,HEADER);
        Cipher cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iv);
        cipher.updateAAD(input,0,HEADER);
        return cipher.doFinal(input,HEADER,input.length-HEADER);
    }
    private static Cipher cipher(int mode, char[] password, byte[] salt, byte[] iv) throws GeneralSecurityException {
        if (password == null || password.length < MIN_PASSWORD || password.length > 256)
            throw new GeneralSecurityException("Passwort: 12–256 Zeichen");
        PBEKeySpec spec = new PBEKeySpec(password,salt,ROUNDS,256);
        byte[] bytes = null;
        try {
            bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(bytes,"AES"),new GCMParameterSpec(128,iv));
            return cipher;
        } finally {
            spec.clearPassword();
            if (bytes != null) Arrays.fill(bytes,(byte)0);
        }
    }
}
