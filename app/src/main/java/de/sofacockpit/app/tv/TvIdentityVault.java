package de.sofacockpit.app.tv;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Software RSA identity wrapped by a non-exportable, per-installation AES key. */
final class TvIdentityVault {
    private static final String ALIAS = "sofacockpit.tv.portable.wrap.v1";
    private static final byte[] AAD = "Sofacockpit TV identity v1".getBytes(StandardCharsets.UTF_8);

    private static synchronized SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            if (!create) throw new java.security.GeneralSecurityException("TV-Speicherschlüssel fehlt");
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
            return generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    static String seal(PortableTvIdentity identity) throws Exception {
        byte[] plain = identity.json().toString().getBytes(StandardCharsets.UTF_8);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
            cipher.updateAAD(AAD);
            byte[] iv = cipher.getIV(), encrypted = cipher.doFinal(plain);
            if (iv.length != 12) throw new java.security.GeneralSecurityException("Ungültige IV-Länge");
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } finally { Arrays.fill(plain, (byte)0); }
    }
    static PortableTvIdentity open(String wrapped) throws Exception {
        if (wrapped == null || wrapped.length() > 40000) throw new java.security.GeneralSecurityException("TV-Identität fehlt");
        byte[] packed = Base64.getDecoder().decode(wrapped);
        if (packed.length < 28) throw new java.security.GeneralSecurityException("TV-Identität beschädigt");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, packed, 0, 12));
        cipher.updateAAD(AAD);
        byte[] plain = cipher.doFinal(packed, 12, packed.length - 12);
        try { return PortableTvIdentity.parse(new JSONObject(new String(plain, StandardCharsets.UTF_8))); }
        finally { Arrays.fill(plain, (byte)0); }
    }
}
