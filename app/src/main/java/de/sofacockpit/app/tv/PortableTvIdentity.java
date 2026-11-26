package de.sofacockpit.app.tv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

/** Portable, per-installation RSA client identity, never stored in plaintext.
 * Minimal DER encoder for the fixed, self-signed X.509 certificate profile.
 * All cryptographic operations use standard Java providers.
 */
final class PortableTvIdentity {
    final PrivateKey key;
    final X509Certificate cert;
    private PortableTvIdentity(PrivateKey key, X509Certificate cert) {
        this.key = key; this.cert = cert;
    }

    static PortableTvIdentity create() throws Exception {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, random);
        KeyPair pair = generator.generateKeyPair();
        byte[] alg = seq(der(6, new byte[]{42,(byte)134,72,(byte)134,(byte)247,13,1,1,11}), der(5, new byte[0]));
        byte[] name = seq(der(0x31, seq(der(6,new byte[]{85,4,3}),
            der(12, ("Sofacockpit-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8)))));
        long now = System.currentTimeMillis();
        byte[] serial = new BigInteger(128, random).setBit(127).toByteArray();
        byte[] tbs = seq(der(0xa0, der(2, new byte[]{2})), der(2, serial), alg, name,
            seq(time(now - 86400000L), time(now + 10L * 365 * 86400000L)), name, pair.getPublic().getEncoded());
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate(), random);
        signature.update(tbs);
        byte[] encoded = seq(tbs, alg, der(3, join(new byte[]{0}, signature.sign())));
        X509Certificate cert = certificate(encoded);
        return validated(pair.getPrivate(), cert);
    }

    JSONObject json() throws Exception {
        return new JSONObject().put("privateKey", Base64.getEncoder().encodeToString(key.getEncoded()))
            .put("certificate", Base64.getEncoder().encodeToString(cert.getEncoded()));
    }

    static PortableTvIdentity parse(JSONObject obj) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(obj.getString("privateKey"));
        byte[] certBytes = Base64.getDecoder().decode(obj.getString("certificate"));
        try {
            if (keyBytes.length > 8192 || certBytes.length > 8192) throw new GeneralSecurityException("TV-Identität zu groß");
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            return validated(key, certificate(certBytes));
        } finally { Arrays.fill(keyBytes, (byte)0); }
    }

    private static PortableTvIdentity validated(PrivateKey key, X509Certificate cert) throws GeneralSecurityException {
        if (!(key instanceof RSAPrivateCrtKey) || !(cert.getPublicKey() instanceof RSAPublicKey))
            throw new GeneralSecurityException("Ungültige RSA-TV-Identität");
        RSAPrivateCrtKey priv = (RSAPrivateCrtKey) key;
        RSAPublicKey pub = (RSAPublicKey) cert.getPublicKey();
        if (pub.getModulus().bitLength() != 2048 || !priv.getModulus().equals(pub.getModulus()) ||
            !priv.getPublicExponent().equals(pub.getPublicExponent()))
            throw new GeneralSecurityException("TV-Schlüssel und Zertifikat passen nicht zusammen");
        cert.checkValidity();
        cert.verify(pub);
        byte[] check = new byte[32]; new SecureRandom().nextBytes(check);
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(key); sign.update(check); byte[] proof = sign.sign();
        sign.initVerify(pub); sign.update(check);
        if (!sign.verify(proof)) throw new GeneralSecurityException("TV-Schlüsselprüfung fehlgeschlagen");
        return new PortableTvIdentity(key, cert);
    }

    private static X509Certificate certificate(byte[] bytes) throws GeneralSecurityException {
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available() != 0) throw new GeneralSecurityException("Ungültiges Zertifikat");
        return cert;
    }
    private static byte[] time(long millis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        calendar.setTimeInMillis(millis);
        boolean utc = calendar.get(Calendar.YEAR) >= 1950 && calendar.get(Calendar.YEAR) <= 2049;
        SimpleDateFormat format = new SimpleDateFormat(utc ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return der(utc ? 23 : 24, format.format(new Date(millis)).getBytes(StandardCharsets.US_ASCII));
    }
    private static byte[] seq(byte[]... fields) { return der(0x30, join(fields)); }
    private static byte[] join(byte[]... fields) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] b : fields) out.write(b, 0, b.length);
        return out.toByteArray();
    }
    private static byte[] der(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int n = value.length;
        if (n < 128) out.write(n);
        else {
            int bytes = (32 - Integer.numberOfLeadingZeros(n) + 7) / 8;
            out.write(128 | bytes);
            for (int i = bytes - 1; i >= 0; i--) out.write((n >>> (8*i)) & 255);
        }
        out.write(value, 0, value.length);
        return out.toByteArray();
    }
}
