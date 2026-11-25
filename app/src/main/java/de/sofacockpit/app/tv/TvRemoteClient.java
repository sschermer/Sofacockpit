package de.sofacockpit.app.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.KeyEvent;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/**
 * One explicit pairing or control session at a time. All network I/O and key
 * generation run off the UI thread; callbacks and public methods use the UI thread.
 * No automatic discovery, pairing or connection at app start.
 */
public final class TvRemoteClient implements AutoCloseable {
    public enum Phase { DISCONNECTED, CONNECTING, PAIRING, CODE, VERIFYING, READY, ERROR }
    public static final class State {
        public final Phase phase;
        public final String detail;
        public final boolean keys;
        State(Phase phase, String detail, boolean keys) {
            this.phase = phase; this.detail = detail; this.keys = keys;
        }
        public boolean connected() { return phase == Phase.READY; }
    }
    public interface Listener { void onState(State state); }

    private static final String ALIAS = "sofacockpit.tv.remote.rsa.v1";
    private static final String PIN = "certificate_sha256", HOST = "last_address";
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService sessions = Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor writers = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    private final Listener listener;
    private State state = new State(Phase.DISCONNECTED, "Nicht verbunden", false);
    private Session current;
    private boolean closed;

    public TvRemoteClient(Context context, Listener listener) {
        prefs = context.getApplicationContext().getSharedPreferences("tv_pairing", Context.MODE_PRIVATE);
        this.listener = listener;
    }
    public State state() { return state; }
    public boolean paired() { return prefs.contains(PIN); }
    public String lastAddress() { return prefs.getString(HOST, ""); }
    public boolean busy() { return current != null; }

    private void update(Phase phase, String detail, boolean keys) {
        state = new State(phase, detail, keys);
        listener.onState(state);
    }
    private void publish(Session s, Phase phase, String detail, boolean keys) {
        main.post(() -> { if (!closed && current == s && !s.cancelled) update(phase, detail, keys); });
    }
    public void start(String address, boolean pair) {
        if (closed) return;
        String host = address == null ? "" : address.trim();
        if (host.isEmpty()) { update(Phase.ERROR, "Bitte eine TV-IP-Adresse auswählen oder eingeben.", false); return; }
        if (!pair && !paired()) { update(Phase.ERROR, "Noch kein Fernseher gekoppelt. Bitte zuerst koppeln.", false); return; }
        cancelCurrent();
        Session s = new Session(host, pair);
        current = s;
        update(pair ? Phase.PAIRING : Phase.CONNECTING,
            pair ? "Kopplung wird vorbereitet …" : "Verbindung wird aufgebaut …", false);
        sessions.execute(() -> run(s));
    }
    public void submitCode(String code) {
        Session s = current;
        if (s == null || state.phase != Phase.CODE) return;
        String value = code == null ? "" : code.trim();
        if (!value.matches("[0-9a-fA-F]{6}")) {
            update(Phase.CODE, "Bitte sechs Zeichen (0–9, A–F) eingeben.", false);
            return;
        }
        if (s.codes.offer(value)) update(Phase.VERIFYING, "Kopplungscode wird geprüft …", false);
    }
    public void disconnect() {
        cancelCurrent();
        if (!closed) update(Phase.DISCONNECTED, "Nicht verbunden", false);
    }
    private void cancelCurrent() {
        Session s = current;
        current = null;
        if (s != null) s.cancel();
        writers.getQueue().clear();
    }
    @Override public void close() {
        closed = true; cancelCurrent();
        sessions.shutdownNow(); writers.shutdownNow();
    }

    public void sendKey(int key) {
        Session s = current;
        if (!allowedKey(key)) {
            if (!closed && state.connected()) update(Phase.READY, "Dieser Befehl ist noch nicht implementiert.", state.keys);
            return;
        }
        if (s == null || !s.ready || !s.keys || !state.connected()) return;
        try {
            writers.execute(() -> {
                if (s.cancelled || !s.ready || !s.keys) return;
                try { s.send(TvProtocol.key(key)); }
                catch (IOException ex) {
                    s.writeFailed = true;
                    s.closeSocket();
                }
            });
        } catch (RejectedExecutionException ex) {
            if (!closed && current == s) update(Phase.READY, "Zu viele Tastenbefehle. Bitte kurz warten.", state.keys);
        }
    }
    private static boolean allowedKey(int key) {
        return key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_HOME ||
            key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
            key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
            key == KeyEvent.KEYCODE_DPAD_CENTER;
    }

    private void run(Session s) {
        String error = null;
        try {
            if (s.cancelled) return;
            InetAddress host = localAddress(s.host);
            Identity identity = identity();
            String pin = prefs.getString(PIN, null);
            if (s.pair) {
                pin = pair(s, host, identity);
                synchronized (s) {
                    if (s.cancelled) return;
                    // Only after successful secret acknowledgement. Old trust is retained on failure.
                    prefs.edit().putString(PIN, pin).putString(HOST, s.host).apply();
                }
                publish(s, Phase.CONNECTING, "Gekoppelt. Steuersitzung wird aufgebaut …", false);
            }
            if (pin == null) throw new IOException("Keine gespeicherte Kopplung.");
            remote(s, host, identity, pin);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            error = "Verbindung abgebrochen.";
        } catch (SocketTimeoutException ex) {
            error = "Zeitüberschreitung. Ist der TV eingeschaltet und im selben Heimnetz?";
        } catch (SSLHandshakeException ex) {
            error = "TLS-Verbindung abgelehnt. TV-Zertifikat oder Kopplung stimmt nicht. Prüfe den TV und kopple bei Bedarf neu.";
        } catch (ConnectException | NoRouteToHostException ex) {
            error = "TV nicht erreichbar. IP-Adresse, Netzwerk und eingeschalteten TV prüfen.";
        } catch (GeneralSecurityException ex) {
            error = "Sicherer Schlüsselspeicher oder TLS konnte nicht eingerichtet werden.";
        } catch (EOFException ex) {
            error = "Der Fernseher hat die Verbindung beendet. Bitte erneut verbinden.";
        } catch (IOException | IllegalArgumentException ex) {
            error = ex.getMessage() == null ? "TV-Verbindung fehlgeschlagen." : ex.getMessage();
        } catch (SecurityException ex) {
            error = "Lokaler Netzwerkzugriff nicht erlaubt. Berechtigung in Android prüfen.";
        } catch (RuntimeException ex) {
            error = "TV-Verbindung fehlgeschlagen (" + ex.getClass().getSimpleName() + ").";
        } finally {
            s.ready = false; s.keys = false; s.closeSocket();
            final String reason = s.writeFailed ? "Tastenbefehl konnte nicht gesendet werden. Bitte erneut verbinden." :
                (error == null ? "Verbindung beendet." : error);
            main.post(() -> {
                if (!closed && current == s && !s.cancelled) {
                    current = null;
                    writers.getQueue().clear();
                    update(Phase.ERROR, reason, false);
                }
            });
        }
    }

    private String pair(Session s, InetAddress host, Identity identity) throws IOException, GeneralSecurityException, InterruptedException {
        try (SSLSocket socket = open(s, host, TvProtocol.PAIR_PORT, identity, null)) {
            X509Certificate peer = peer(socket);
            InputStream input = socket.getInputStream();
            s.send(TvProtocol.pairRequest());
            TvProtocol.expectPair(TvProtocol.readFrame(input), 11);
            s.send(TvProtocol.pairOptions());
            TvProtocol.expectPair(TvProtocol.readFrame(input), 20);
            s.send(TvProtocol.pairConfiguration());
            TvProtocol.expectPair(TvProtocol.readFrame(input), 31);

            long deadline = SystemClock.elapsedRealtime() + 90000;
            publish(s, Phase.CODE, "Sechsstelligen Code vom TV eingeben (0–9, A–F).", false);
            byte[] proof;
            while (true) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) throw new IOException("Kopplungscode abgelaufen. Bitte erneut koppeln.");
                String code = s.codes.poll(remaining, TimeUnit.MILLISECONDS);
                if (s.cancelled) throw new InterruptedException();
                if (code == null) throw new IOException("Kopplungscode abgelaufen. Bitte erneut koppeln.");
                try {
                    proof = TvProtocol.proof((RSAPublicKey)identity.cert.getPublicKey(), (RSAPublicKey)peer.getPublicKey(), code);
                    break;
                } catch (IllegalArgumentException bad) {
                    // Invalid checksum is never sent to the TV. Allow correction while the prompt remains open.
                    publish(s, Phase.CODE, bad.getMessage(), false);
                }
            }
            s.send(TvProtocol.pairSecret(proof));
            TvProtocol.expectPair(TvProtocol.readFrame(input), 41);
            return fingerprint(peer);
        } finally { s.closeSocket(); }
    }
    private void remote(Session s, InetAddress host, Identity identity, String pin)
        throws IOException, GeneralSecurityException {
        try (SSLSocket socket = open(s, host, TvProtocol.REMOTE_PORT, identity, pin)) {
            socket.setSoTimeout(16000);
            InputStream input = socket.getInputStream();
            TvProtocol.RemoteHandshake handshake = new TvProtocol.RemoteHandshake();
            long deadline = SystemClock.elapsedRealtime() + 20000;
            boolean announced = false;
            while (!s.cancelled) {
                if (!handshake.ready() && SystemClock.elapsedRealtime() > deadline)
                    throw new IOException("Keine bestätigte TV-Steuersitzung. Bitte erneut verbinden.");
                byte[] reply = handshake.accept(TvProtocol.readFrame(input));
                if (reply != null) s.send(reply);
                s.ready = handshake.ready();
                s.keys = handshake.supportsKeys();
                if (s.ready && !announced) {
                    synchronized (s) {
                        if (s.cancelled) return;
                        prefs.edit().putString(HOST, s.host).apply();
                    }
                    publish(s, Phase.READY, s.keys ? "Verbunden" : "Verbunden – TV meldet keine Tastenunterstützung.", s.keys);
                    announced = true;
                } else if (!s.ready && announced) {
                    announced = false;
                    deadline = SystemClock.elapsedRealtime() + 20000;
                    publish(s, Phase.CONNECTING, "TV-Steuersitzung wird neu ausgehandelt …", false);
                }
            }
        } finally { s.closeSocket(); }
    }

    private static InetAddress localAddress(String value) throws IOException {
        InetAddress address;
        if (value.matches("[0-9.]+")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) throw new IOException("Ungültige IPv4-Adresse.");
            byte[] b = new byte[4];
            for (int i=0; i<4; i++) {
                if (parts[i].isEmpty() || parts[i].length() > 3) throw new IOException("Ungültige IPv4-Adresse.");
                int part = Integer.parseInt(parts[i]);
                if (part > 255) throw new IOException("Ungültige IPv4-Adresse.");
                b[i] = (byte)part;
            }
            address = InetAddress.getByAddress(b);
        } else if (value.contains(":") && value.matches("[0-9A-Fa-f:.]+(%[A-Za-z0-9_.-]+)?")) {
            address = InetAddress.getByName(value); // numeric IPv6 only; no DNS lookup of arbitrary names
        } else {
            throw new IOException("Bitte eine gültige lokale IP-Adresse eingeben.");
        }
        boolean ula = address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
        if (!address.isSiteLocalAddress() && !address.isLinkLocalAddress() && !ula)
            throw new IOException("Nur lokale Heimnetz-Adressen werden unterstützt.");
        return address;
    }

    private static final class Session {
        final String host;
        final boolean pair;
        final BlockingQueue<String> codes = new ArrayBlockingQueue<>(1);
        volatile boolean cancelled, ready, keys, writeFailed;
        volatile Socket raw;
        volatile SSLSocket tls;
        final Object outputLock = new Object();
        Session(String host, boolean pair) { this.host = host; this.pair = pair; }
        void send(byte[] message) throws IOException {
            synchronized (outputLock) {
                SSLSocket socket = tls;
                if (cancelled || socket == null || socket.isClosed()) throw new IOException("TV nicht verbunden.");
                TvProtocol.writeFrame(socket.getOutputStream(), message);
            }
        }
        void closeSocket() {
            Socket r = raw; raw = null; tls = null;
            if (r != null) try { r.close(); } catch (IOException ignored) {}
        }
        void cancel() {
            synchronized (this) { cancelled = true; ready = false; keys = false; }
            codes.clear(); codes.offer(""); // unblock a waiting pairing worker
            closeSocket(); // raw close unblocks reads without a TLS shutdown handshake on the UI thread
        }
    }

    private static final class Identity {
        final PrivateKey key;
        final X509Certificate cert;
        Identity(PrivateKey key, X509Certificate cert) { this.key = key; this.cert = cert; }
    }
    private static Identity identity() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            long now = System.currentTimeMillis();
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512, KeyProperties.DIGEST_SHA1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setCertificateSubject(new X500Principal("CN=Sofacockpit-" + java.util.UUID.randomUUID()))
                .setCertificateSerialNumber(new BigInteger(63, new SecureRandom()).setBit(62))
                .setCertificateNotBefore(new Date(now - 86400000L))
                .setCertificateNotAfter(new Date(now + 10L * 365 * 86400000L))
                .build());
            generator.generateKeyPair();
        }
        PrivateKey privateKey = (PrivateKey)ks.getKey(ALIAS, null);
        X509Certificate certificate = (X509Certificate)ks.getCertificate(ALIAS);
        if (privateKey == null || certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey))
            throw new GeneralSecurityException("Ungültige Client-Identität");
        return new Identity(privateKey, certificate);
    }
    private static String fingerprint(X509Certificate certificate) throws CertificateException {
        return Base64.encodeToString(TvProtocol.sha256(certificate.getEncoded()), Base64.NO_WRAP);
    }
    private static X509Certificate peer(SSLSocket socket) throws SSLPeerUnverifiedException {
        java.security.cert.Certificate[] certs = socket.getSession().getPeerCertificates();
        if (certs.length == 0 || !(certs[0] instanceof X509Certificate) || !(certs[0].getPublicKey() instanceof RSAPublicKey))
            throw new SSLPeerUnverifiedException("TV-RSA-Zertifikat fehlt");
        return (X509Certificate)certs[0];
    }
    private static SSLSocket open(Session s, InetAddress host, int port, Identity identity, String expectedPin)
        throws IOException, GeneralSecurityException {
        if (s.cancelled) throw new IOException("Abgebrochen");
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException {
                throw new CertificateException("Client validation not applicable");
            }
            public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
                if (chain == null || chain.length == 0 || !(chain[0].getPublicKey() instanceof RSAPublicKey))
                    throw new CertificateException("TV-RSA certificate missing");
                // null pin is permitted only on the user-initiated pairing channel;
                // trust is established by the code binding both public keys.
                if (expectedPin != null && !MessageDigest.isEqual(
                        fingerprint(chain[0]).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        expectedPin.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                    throw new CertificateException("TV certificate changed");
            }
        };
        if (port != TvProtocol.PAIR_PORT && expectedPin == null) throw new GeneralSecurityException("No TV pin");
        X509KeyManager keyManager = new X509KeyManager() {
            public String[] getClientAliases(String type, Principal[] issuers) {
                return type != null && type.startsWith("RSA") ? new String[]{ALIAS} : null;
            }
            public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                if (types != null) for (String type : types) if (getClientAliases(type, issuers) != null) return ALIAS;
                return null;
            }
            public String[] getServerAliases(String type, Principal[] issuers) { return null; }
            public String chooseServerAlias(String type, Principal[] issuers, Socket socket) { return null; }
            public X509Certificate[] getCertificateChain(String alias) {
                return ALIAS.equals(alias) ? new X509Certificate[]{identity.cert} : null;
            }
            public PrivateKey getPrivateKey(String alias) { return ALIAS.equals(alias) ? identity.key : null; }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[]{keyManager}, new TrustManager[]{trust}, new SecureRandom());
        Socket raw = new Socket();
        s.raw = raw;
        if (s.cancelled) { raw.close(); throw new IOException("Abgebrochen"); }
        raw.connect(new InetSocketAddress(host, port), 5000);
        raw.setSoTimeout(10000);
        raw.setTcpNoDelay(true);
        SSLSocket tls = (SSLSocket)context.getSocketFactory().createSocket(raw, host.getHostAddress(), port, true);
        s.tls = tls;
        tls.setUseClientMode(true);
        tls.setEnabledProtocols(new String[]{"TLSv1.2"}); // compatible with Android TV Remote v2 mutual TLS
        tls.setSoTimeout(10000);
        tls.startHandshake();
        peer(tls);
        return tls;
    }
}
