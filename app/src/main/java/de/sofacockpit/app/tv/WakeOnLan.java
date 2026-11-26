package de.sofacockpit.app.tv;

import java.util.Arrays;
import java.util.Locale;

/** Pure packet construction and directed-broadcast calculation; no I/O. */
final class WakeOnLan {
    private WakeOnLan() {}
    static final int PORT = 9, PACKETS = 3, CONNECT_ATTEMPTS = 3;

    static String normalizeMac(String input) {
        String mac = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (mac.isEmpty()) return "";
        if (mac.matches("[0-9A-F]{12}")) mac = mac.replaceAll("(.{2})(?=.)", "$1:");
        else if (mac.matches("[0-9A-F]{2}(-[0-9A-F]{2}){5}")) mac = mac.replace('-', ':');
        if (!mac.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}") ||
            (Integer.parseInt(mac.substring(0, 2), 16) & 1) != 0 ||
            "00:00:00:00:00:00".equals(mac))
            throw new IllegalArgumentException("Gültige LAN-MAC-Adresse des Fernsehers eingeben.");
        return mac;
    }

    static byte[] packet(String mac) {
        String value = normalizeMac(mac);
        if (value.isEmpty()) throw new IllegalArgumentException("LAN-MAC-Adresse fehlt.");
        String[] parts = value.split(":");
        byte[] address = new byte[6];
        for (int i = 0; i < 6; i++) address[i] = (byte) Integer.parseInt(parts[i], 16);
        byte[] packet = new byte[102];
        Arrays.fill(packet, 0, 6, (byte) 0xff);
        for (int i = 0; i < 16; i++) System.arraycopy(address, 0, packet, 6 + 6 * i, 6);
        return packet;
    }

    static boolean useWake(boolean connected, boolean busy, boolean configured) {
        return !connected && !busy && configured;
    }

    /** Return null for a different subnet, network/broadcast address or an unsuitable prefix. */
    static byte[] broadcastFor(byte[] local, int prefix, byte[] target) {
        if (local.length != 4 || target.length != 4 || prefix < 1 || prefix > 30) return null;
        int own = ipv4(local), tv = ipv4(target), mask = -1 << (32 - prefix);
        int network = own & mask, broadcast = network | ~mask;
        if ((tv & mask) != network || tv == network || tv == broadcast || tv == own) return null;
        return new byte[]{(byte)(broadcast >>> 24), (byte)(broadcast >>> 16),
            (byte)(broadcast >>> 8), (byte)broadcast};
    }

    private static int ipv4(byte[] bytes) {
        int value = 0;
        for (byte b : bytes) value = (value << 8) | (b & 0xff);
        return value;
    }
}
