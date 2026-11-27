package de.sofacockpit.app.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Short, local T-Cast session, separate from the paired Remote-v2 transport.
 * Independently implemented from public protocol observations and on-device
 * PowerShell tests. No arbitrary commands, URLs, installation or shell access.
 */
public final class TclAppLauncher implements AutoCloseable {
    static final int PORT = 6553;
    static final int MAX_FRAME = 1024 * 1024;
    private static final String MARKET = "market://launch?id=";
    // Public, device-independent T-Cast transport constants, NOT user secrets.
    // The shared-key mode provides no cryptographic peer authentication.
    private static final byte[] KEY = "tnscreentnscreen".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = {0x12,0x34,0x56,0x78,(byte)0x90,(byte)0xab,(byte)0xcd,(byte)0xef,
                                      0x12,0x34,0x56,0x78,(byte)0x90,(byte)0xab,(byte)0xcd,(byte)0xef};
    public enum Status { APPS, SENT, UNAVAILABLE, NOT_INSTALLED, FAILED, BUSY, CANCELLED }
    public static final class App {
        public final String name, packageName;
        App(String name, String packageName) { this.name = name; this.packageName = packageName; }
        public boolean systemLike() {
            return packageName.startsWith("com.tcl.") || packageName.startsWith("com.android.") ||
                packageName.equals("com.google.android.play.games");
        }
    }
    public static final class Result {
        public final Status status;
        public final List<App> apps;
        Result(Status status) { this(status, Collections.emptyList()); }
        Result(Status status, List<App> apps) {
            this.status = status;
            this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        }
    }

    static boolean validPackage(String value) {
        return value != null && value.length() <= 255 &&
            value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    /** Only exact known root links; never replace a content URL based on its name. */
    public static String packageForLink(String link) {
        if (link == null) return null;
        if (link.startsWith(MARKET)) {
            String pkg = link.substring(MARKET.length());
            return validPackage(pkg) ? pkg : null;
        }
        switch (link) {
            case "netflix://": return "com.netflix.ninja";
            case "https://www.youtube.com": return "com.google.android.youtube.tv";
            case "https://tv.apple.com": return "com.apple.atve.androidtv.appletv";
            case "https://www.disneyplus.com": return "com.disney.disneyplus";
            case "https://app.primevideo.com": return "com.amazon.amazonvideo.livingroom";
            default: return null;
        }
    }

    static List<App> parseApps(String reply) throws IOException {
        if (reply == null || !reply.startsWith("223>>GET>>"))
            throw new IOException("Unexpected app-list response");
        Map<String, App> apps = new LinkedHashMap<>();
        for (String item : reply.substring("223>>GET>>".length()).split(">")) {
            String[] parts = item.split("::", 3);
            if (parts.length < 2 || !validPackage(parts[0]) || apps.containsKey(parts[0])) continue;
            String name = parts[1].replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
            if (name.isEmpty()) name = parts[0];
            if (name.length() > 48) {
                int end = 48;
                if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
                name = name.substring(0, end);
            }
            apps.put(parts[0], new App(name, parts[0]));
            if (apps.size() >= 500) break;
        }
        List<App> sorted = new ArrayList<>(apps.values());
        sorted.sort(Comparator.comparing((App a) -> a.name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(a -> a.packageName));
        return sorted;
    }

    static int algorithm(String reply) throws IOException {
        String[] fields = reply.split(">>", -1);
        if (fields.length < 6 || !fields[0].equals("159"))
            throw new IOException("Unexpected T-Cast identification");
        if (fields.length < 7 || fields[6].isEmpty() || fields[6].equals("-1") || fields[6].equals("0")) return 0;
        if (fields[6].equals("1")) return 1;
        throw new IOException("Unsupported T-Cast transport mode");
    }

    private static byte[] crypt(byte[] bytes, int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(mode, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(IV));
        return cipher.doFinal(bytes);
    }

    static void writeFrame(DataOutputStream out, String message, int mode)
            throws IOException, GeneralSecurityException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (mode == 1) bytes = crypt(bytes, Cipher.ENCRYPT_MODE);
        if (bytes.length <= 0 || bytes.length >= MAX_FRAME) throw new IOException("Invalid frame size");
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    static String readFrame(DataInputStream in, int mode) throws IOException, GeneralSecurityException {
        int length = in.readInt();
        if (length <= 0 || length >= MAX_FRAME) throw new IOException("Invalid frame size");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        if (mode == 1) bytes = crypt(bytes, Cipher.DECRYPT_MODE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String receive(DataInputStream in, int mode, String prefix)
            throws IOException, GeneralSecurityException {
        for (int i = 0; i < 32; i++) {
            String reply = readFrame(in, mode);
            if (reply.startsWith(prefix)) return reply;
        }
        throw new IOException("No matching response");
    }

    private final Socket socket = new Socket();
    @Override public void close() { try { socket.close(); } catch (IOException ignored) {} }

    /** packageName == null requests the inventory only. The owner enforces a whole-session deadline. */
    Result execute(InetAddress host, String packageName, BooleanSupplier active) {
        return execute(host, PORT, packageName, active);
    }

    Result execute(InetAddress host, int port, String packageName, BooleanSupplier active) {
        if (packageName != null && !validPackage(packageName)) return new Result(Status.FAILED);
        boolean negotiated = false;
        boolean attempted = false;
        try {
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            socket.connect(new InetSocketAddress(host, port), 1800);
            socket.setSoTimeout(3000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            writeFrame(out, "159>>Sofacockpit>>1>>" + UUID.randomUUID() + ">>1", 0);
            String identification = receive(in, 0, "159>>");
            negotiated = true;
            int mode = algorithm(identification);
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            writeFrame(out, "223>>GET", mode);
            List<App> apps = parseApps(receive(in, mode, "223>>GET>>"));
            if (packageName == null) return new Result(Status.APPS, apps);
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            boolean installed = false;
            for (App app : apps) if (app.packageName.equals(packageName)) { installed = true; break; }
            if (!installed) return new Result(Status.NOT_INSTALLED, apps);
            // Once any launch bytes may have been written, never retry or fall back.
            attempted = true;
            writeFrame(out, "223>>OPEN>>" + packageName, mode);
            return new Result(Status.SENT);
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            return new Result(negotiated || attempted || ex instanceof SecurityException ? Status.FAILED : Status.UNAVAILABLE);
        } finally { close(); }
    }
}
