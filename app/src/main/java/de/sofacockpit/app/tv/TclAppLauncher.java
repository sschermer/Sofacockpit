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
    private static final int MAX_SUPPLEMENT_PROBES = 8;
    private static final int PACKAGE_TIMEOUT_MS = 1000;
    public static final String PRIME_PACKAGE = "com.amazon.amazonvideo.livingroom";
    private static final String[] KNOWN_PACKAGES = { PRIME_PACKAGE, "com.netflix.ninja", "com.google.android.youtube.tv", "com.apple.atve.androidtv.appletv", "com.disney.disneyplus", "sport.dyn.app.android.prod" };
    // Public, device-independent T-Cast transport constants, NOT user secrets.
    // The shared-key mode provides no cryptographic peer authentication.
    private static final byte[] KEY = "tnscreentnscreen".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = {0x12,0x34,0x56,0x78,(byte)0x90,(byte)0xab,(byte)0xcd,(byte)0xef,
                                      0x12,0x34,0x56,0x78,(byte)0x90,(byte)0xab,(byte)0xcd,(byte)0xef};
    public enum Status { APPS, SENT, UNAVAILABLE, NOT_INSTALLED, FAILED, BUSY, CANCELLED }
    public static final class App {
        public final String name, packageName, iconUrl;
        public final boolean supplemental;
        public App(String name, String packageName) { this(name, packageName, "", false); }
        public App(String name, String packageName, String iconUrl, boolean supplemental) {
            this.name = name; this.packageName = packageName;
            this.iconUrl = iconUrl == null ? "" : iconUrl;
            this.supplemental = supplemental;
        }
        public boolean systemLike() {
            return packageName.startsWith("com.tcl.") || packageName.startsWith("com.android.") ||
                packageName.equals("com.google.android.play.games");
        }
    }
    public static final class Result {
        public final Status status;
        public final List<App> apps;
        public final boolean supplementsIncomplete;
        Result(Status status) { this(status, Collections.emptyList()); }
        Result(Status status, List<App> apps) { this(status, apps, false); }
        Result(Status status, List<App> apps, boolean supplementsIncomplete) {
            this.status = status;
            this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
            this.supplementsIncomplete = supplementsIncomplete;
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
        if (validPackage(link)) return link;
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
            String[] parts = item.split("::", 4);
            if (parts.length < 2 || !validPackage(parts[0]) || apps.containsKey(parts[0])) continue;
            String name = cleanName(parts[1]);
            if (name.isEmpty()) name = parts[0];
            apps.put(parts[0], new App(name, parts[0], parts.length >= 3 ? parts[2] : "", false));
            if (apps.size() >= 500) break;
        }
        return sortedApps(apps.values());
    }

    private static String cleanName(String value) {
        String name = value.replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
        if (name.length() > 48) {
            int end = 48;
            if (Character.isHighSurrogate(name.charAt(end - 1))) end--;
            name = name.substring(0, end);
        }
        return name;
    }

    private static List<App> sortedApps(Collection<App> apps) {
        List<App> sorted = new ArrayList<>(apps);
        sorted.sort(Comparator.comparing((App a) -> a.name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(a -> a.packageName));
        return sorted;
    }

    /** Only the positively observed PACKAGEINFO shape is accepted. Missing, mismatched
     * or unknown responses are not evidence of absence or installation. */
    static App parsePackageInfo(String reply, String requested) {
        if (!validPackage(requested) || reply == null) return null;
        String[] fields = reply.split(">>", -1);
        if (fields.length != 11 || !"223".equals(fields[0]) || !"PACKAGEINFO".equals(fields[1])
                || !requested.equals(fields[2]) || fields[4].isEmpty()
                || !fields[5].matches("[0-9]{1,19}") || fields[6].trim().isEmpty()) return null;
        for (int i = 7; i < 11; i++) if (!fields[i].matches("[0-9]{1,19}")) return null;
        String name = cleanName(fields[3]);
        if (name.isEmpty()) return null;
        return new App(name, requested, fields[4], true);
    }

    /** A bounded, deterministic candidate set; these are queries, not synthetic apps. */
    public static List<String> supplementCandidates(Collection<String> saved) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        packages.add(PRIME_PACKAGE);
        if (saved != null) for (String pkg : saved) if (validPackage(pkg)) packages.add(pkg);
        packages.addAll(Arrays.asList(KNOWN_PACKAGES));
        return new ArrayList<>(packages);
    }

    private App queryPackage(DataInputStream in, DataOutputStream out, int mode, String pkg)
            throws IOException, GeneralSecurityException {
        socket.setSoTimeout(PACKAGE_TIMEOUT_MS);
        writeFrame(out, "223>>PACKAGEINFO>>" + pkg, mode);
        return parsePackageInfo(receive(in, mode, "223>>PACKAGEINFO>>"), pkg);
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
        return execute(host, PORT, packageName, Collections.emptyList(), active);
    }

    Result execute(InetAddress host, String packageName, Collection<String> candidates, BooleanSupplier active) {
        return execute(host, PORT, packageName, candidates, active);
    }

    Result execute(InetAddress host, int port, String packageName, BooleanSupplier active) {
        return execute(host, port, packageName, Collections.emptyList(), active);
    }

    Result execute(InetAddress host, int port, String packageName, Collection<String> candidates, BooleanSupplier active) {
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
            Map<String, App> byPackage = new LinkedHashMap<>();
            for (App app : parseApps(receive(in, mode, "223>>GET>>"))) byPackage.put(app.packageName, app);
            if (packageName == null) {
                boolean incomplete = false;
                int probes = 0;
                for (String pkg : new LinkedHashSet<>(candidates)) {
                    if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
                    if (!validPackage(pkg) || byPackage.containsKey(pkg)) continue;
                    if (probes++ >= MAX_SUPPLEMENT_PROBES || byPackage.size() >= 500) { incomplete = true; break; }
                    try {
                        App app = queryPackage(in, out, mode, pkg);
                        if (app != null) byPackage.put(pkg, app);
                        else incomplete = true;
                    } catch (IOException | GeneralSecurityException ex) {
                        // A timed-out/partial frame makes the stream unsafe to continue.
                        // Keep the already validated GET and positive package responses.
                        incomplete = true; break;
                    }
                }
                if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
                return new Result(Status.APPS, sortedApps(byPackage.values()), incomplete);
            }
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            if (!byPackage.containsKey(packageName)) {
                App app = null;
                try { app = queryPackage(in, out, mode, packageName); }
                catch (IOException | GeneralSecurityException ignored) {
                    // No OPEN attempted; a saved link can still be offered by the caller.
                }
                if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
                if (app == null) return new Result(Status.NOT_INSTALLED, sortedApps(byPackage.values()), true);
                byPackage.put(packageName, app);
            }
            List<App> apps = sortedApps(byPackage.values());
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            // Once any launch bytes may have been written, never retry or fall back.
            attempted = true;
            writeFrame(out, "223>>OPEN>>" + packageName, mode);
            return new Result(Status.SENT, apps);
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            if (!active.getAsBoolean()) return new Result(Status.CANCELLED);
            return new Result(negotiated || attempted || ex instanceof SecurityException ? Status.FAILED : Status.UNAVAILABLE);
        } finally { close(); }
    }
}
