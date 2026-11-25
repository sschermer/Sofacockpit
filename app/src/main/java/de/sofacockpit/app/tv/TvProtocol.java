package de.sofacockpit.app.tv;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Small, dependency-free implementation of the wire subset needed for Android TV
 * Remote v2. Protocol references (not copied source): androidtvremote2/polo.proto,
 * remotemessage.proto and remote.py, see README. All lengths are bounded.
 */
public final class TvProtocol {
    private TvProtocol() {}
    static final int MAX_FRAME = 256 * 1024;
    public static final int PAIR_PORT = 6467, REMOTE_PORT = 6466;
    public static final int KEY_FEATURE = 2, PING_FEATURE = 1, POWER_FEATURE = 32,
        VOLUME_FEATURE = 64, APP_LINK_FEATURE = 512;
    // Negotiate only features that this client implements.
    static final int REQUESTED_FEATURES = PING_FEATURE | KEY_FEATURE | POWER_FEATURE | VOLUME_FEATURE | APP_LINK_FEATURE;

    static byte[] concat(byte[]... items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] item : items) out.write(item, 0, item.length);
        return out.toByteArray();
    }
    static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        do {
            int b = (int)(value & 127);
            value >>>= 7;
            out.write(b | (value == 0 ? 0 : 128));
        } while (value != 0);
        return out.toByteArray();
    }
    static long readVarint(InputStream in) throws IOException {
        long n = 0;
        for (int i = 0; i < 10; i++) {
            int b = in.read();
            if (b < 0) throw new EOFException("Unvollständige Nachricht");
            if (i == 9 && (b & 254) != 0) throw new IOException("Ungültiger Varint");
            n |= (long)(b & 127) << (7 * i);
            if ((b & 128) == 0) return n;
        }
        throw new IOException("Ungültiger Varint");
    }
    static byte[] number(int tag, long n) { return concat(varint((long)tag << 3), varint(n)); }
    static byte[] bytes(int tag, byte[] data) { return concat(varint(((long)tag << 3) | 2), varint(data.length), data); }
    static byte[] string(int tag, String text) { return bytes(tag, text.getBytes(StandardCharsets.UTF_8)); }
    static byte[] readBytes(InputStream in, long size) throws IOException {
        if (size < 0 || size > MAX_FRAME) throw new IOException("Nachricht zu groß");
        byte[] data = new byte[(int)size];
        int pos = 0;
        while (pos < data.length) {
            int n = in.read(data, pos, data.length - pos);
            if (n < 0) throw new EOFException("Unvollständige Nachricht");
            if (n > 0) pos += n;
        }
        return data;
    }
    static byte[] readFrame(InputStream in) throws IOException {
        long size = readVarint(in);
        if (size <= 0) throw new IOException("Leere Nachricht");
        return readBytes(in, size);
    }
    static void writeFrame(OutputStream out, byte[] message) throws IOException {
        if (message.length == 0 || message.length > MAX_FRAME) throw new IOException("Ungültige Nachrichtengröße");
        out.write(varint(message.length));
        out.write(message);
        out.flush();
    }

    static final class Message {
        final Map<Integer, Long> integers = new HashMap<>();
        final Map<Integer, byte[]> messages = new HashMap<>();
        long integer(int tag, long fallback) { return integers.getOrDefault(tag, fallback); }
        boolean has(int tag) { return messages.containsKey(tag); }
        Message child(int tag) throws IOException {
            byte[] data = messages.get(tag);
            if (data == null) throw new IOException("Protokollfeld fehlt: " + tag);
            return parse(data);
        }
    }
    static Message parse(byte[] data) throws IOException {
        Message m = new Message();
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        while (in.available() > 0) {
            long key = readVarint(in);
            int tag = (int)(key >>> 3);
            if (key <= 0 || key > 0xffffffffL || tag <= 0) throw new IOException("Ungültiges Protokollfeld");
            switch ((int)(key & 7)) {
                case 0: m.integers.put(tag, readVarint(in)); break;
                case 1: readBytes(in, 8); break;
                case 2: m.messages.put(tag, readBytes(in, readVarint(in))); break;
                case 5: readBytes(in, 4); break;
                default: throw new IOException("Unbekannter Protobuf-Datentyp");
            }
        }
        return m;
    }

    private static byte[] pairing(int field, byte[] payload) {
        return concat(number(1, 2), number(2, 200), bytes(field, payload));
    }
    static byte[] pairRequest() { return pairing(10, concat(string(1, "atvremote"), string(2, "Sofacockpit"))); }
    private static byte[] hexEncoding() { return concat(number(1, 3), number(2, 6)); }
    static byte[] pairOptions() { return pairing(20, concat(bytes(1, hexEncoding()), number(3, 1))); }
    static byte[] pairConfiguration() { return pairing(30, concat(bytes(1, hexEncoding()), number(2, 1))); }
    static byte[] pairSecret(byte[] proof) { return pairing(40, bytes(1, proof)); }
    static void expectPair(byte[] data, int field) throws IOException {
        Message m = parse(data);
        long status = m.integer(2, -1);
        if (status == 402) throw new IOException("Der Fernseher hat den Kopplungscode abgelehnt. Bitte erneut koppeln.");
        if (status != 200 || m.integer(1, -1) != 2 || !m.has(field))
            throw new IOException("TV-Kopplung abgelehnt oder inkompatibel (Status " + status + ")");
    }
    private static byte[] unsigned(BigInteger value) {
        byte[] b = value.toByteArray();
        return b.length > 1 && b[0] == 0 ? Arrays.copyOfRange(b, 1, b.length) : b;
    }
    static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static byte[] proof(RSAPublicKey client, RSAPublicKey tv, String code) {
        if (code == null || !code.matches("[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Bitte sechs Zeichen (0–9, A–F) eingeben.");
        int check = Integer.parseInt(code.substring(0, 2), 16);
        byte[] nonce = {(byte)Integer.parseInt(code.substring(2, 4), 16),
                        (byte)Integer.parseInt(code.substring(4, 6), 16)};
        byte[] hash = sha256(concat(unsigned(client.getModulus()), unsigned(client.getPublicExponent()),
                                   unsigned(tv.getModulus()), unsigned(tv.getPublicExponent()), nonce));
        if ((hash[0] & 255) != check)
            throw new IllegalArgumentException("Code stimmt nicht. Bitte die Anzeige am TV prüfen.");
        return hash;
    }

    static byte[] key(int code) {
        // RemoteKeyInject.direction SHORT = 3.
        return bytes(10, concat(number(1, code), number(2, 3)));
    }

    /** Only explicit user-selected app links. Never send arbitrary Android intent URIs. */
    public static String normalizeLaunchLink(String input) {
        String value = input == null ? "" : input.trim();
        String packagePattern = "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+";
        if (value.length() > 2048 || value.matches("(?s).*\\s.*"))
            throw new IllegalArgumentException("Ungültiger oder zu langer App-Link.");
        if (value.matches(packagePattern)) return "market://launch?id=" + value;
        if (value.matches("market://launch\\?id=" + packagePattern)) return value;
        if (value.startsWith("https://")) {
            try {
                java.net.URI uri = new java.net.URI(value);
                if (uri.getHost() != null && uri.getUserInfo() == null) return value;
            } catch (java.net.URISyntaxException ignored) {}
        }
        // App-specific schemes commonly used by Android TV. No file, intent or script links.
        if (value.matches("(netflix|nflx|disneyplus|plex|spotify|waipu|zattoo|crunchyroll|viki|arte|twitch|vnd\\.youtube|vnd\\.youtube\\.launch)://[^\\s]*"))
            return value;
        throw new IllegalArgumentException("HTTPS-Link, unterstützten App-Link oder Android-Paketnamen eingeben.");
    }
    static byte[] appLink(String link) {
        return bytes(90, string(1, normalizeLaunchLink(link)));
    }

    /** The TV drives negotiation. Receiving remote_start (even when power is off)
     * after configure/active confirms a real remote session, not just an open socket. */
    static final class RemoteHandshake {
        private int features, advertisedFeatures;
        private int configureCount, activeCount, startCount, pingCount, otherCount;
        private boolean configured, activated, ready;
        private Boolean powerOn, muted;
        private Integer volumeLevel, volumeMax;
        private final java.util.SortedSet<Integer> seenFields = new java.util.TreeSet<>();
        boolean ready() { return ready; }
        boolean supportsKeys() { return ready && (features & KEY_FEATURE) != 0; }
        Boolean powerOn() { return powerOn; }
        boolean supportsApps() { return ready && (features & APP_LINK_FEATURE) != 0; }
        Integer volumeLevel() { return ready ? volumeLevel : null; }
        Integer volumeMax() { return ready ? volumeMax : null; }
        Boolean muted() { return ready ? muted : null; }
        String diagnostics() {
            return "TV-Funktionen=" + advertisedFeatures + ", ausgehandelt=" + features +
                "; Configure=" + configureCount + ", Active=" + activeCount +
                ", Start=" + startCount + ", Ping=" + pingCount +
                ", Sonstige=" + otherCount + "; Felder=" + seenFields;
        }
        byte[] accept(byte[] data) throws IOException {
            Message m = parse(data);
            // Record field numbers only, never message payloads, PINs or credentials.
            for (int tag : m.messages.keySet()) if (seenFields.size() < 24) seenFields.add(tag);
            for (int tag : m.integers.keySet()) if (seenFields.size() < 24) seenFields.add(tag);
            if (m.has(1)) {
                configureCount++;
                configured = true; activated = false; ready = false; powerOn = null;
                volumeLevel = null; volumeMax = null; muted = null;
                advertisedFeatures = (int)m.child(1).integer(1, 0);
                features = advertisedFeatures & REQUESTED_FEATURES;
                byte[] info = concat(number(3, 1), string(4, "1"), string(5, "atvremote"), string(6, "1.0.0"));
                return bytes(1, concat(number(1, features), bytes(2, info)));
            }
            if (m.has(2)) {
                if (!configured) throw new IOException("TV-Konfiguration fehlt");
                activeCount++;
                activated = true;
                return bytes(2, number(1, features));
            }
            if (m.has(8)) {
                pingCount++;
                return bytes(9, number(1, m.child(8).integer(1, 0)));
            }
            if (m.has(40)) {
                startCount++;
                if (!configured || !activated)
                    throw new IOException("TV-Steuersitzung nicht vollständig ausgehandelt. " + diagnostics());
                // Proto3 default false is a legitimate start notification for standby.
                powerOn = m.child(40).integer(1, 0) != 0;
                ready = true;
                return null;
            }
            if (m.has(50) && configured && (features & VOLUME_FEATURE) != 0) {
                Message volume = m.child(50);
                long max = volume.integer(6, 0), level = volume.integer(7, 0);
                if (max > 0 && max <= Integer.MAX_VALUE && level >= 0 && level <= max) {
                    volumeMax = (int) max; volumeLevel = (int) level;
                    muted = volume.integer(8, 0) != 0;
                } else {
                    volumeMax = null; volumeLevel = null; muted = null;
                }
                return null;
            }
            if (m.has(3) && m.child(3).integer(1, 0) != 0)
                throw new IOException("TV meldet einen Protokollfehler. " + diagnostics());
            otherCount++;
            return null;
        }
    }
}
