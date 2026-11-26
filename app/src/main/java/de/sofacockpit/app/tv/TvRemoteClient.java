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
 * No automatic discovery or pairing. A previously active control session may
 * be restored once when returning to the foreground, not in the background.
 */
public final class TvRemoteClient implements AutoCloseable {
    public enum Phase { DISCONNECTED, CONNECTING, PAIRING, CODE, VERIFYING, READY, ERROR }
    public static final class State {
        public final Phase phase;
        public final String detail;
        public final boolean keys, apps;
        public final Boolean powerOn, muted;
        public final Integer volumeLevel, volumeMax;
        State(Phase phase, String detail, boolean keys) {
            this(phase, detail, keys, false, null, null, null, null);
        }
        State(Phase phase, String detail, boolean keys, boolean apps, Boolean powerOn,
              Integer volumeLevel, Integer volumeMax, Boolean muted) {
            this.phase = phase; this.detail = detail; this.keys = keys; this.apps = apps;
            this.powerOn = powerOn; this.volumeLevel = volumeLevel; this.volumeMax = volumeMax; this.muted = muted;
        }
        State withDetail(String value) {
            return new State(phase, value, keys, apps, powerOn, volumeLevel, volumeMax, muted);
        }
        boolean sameDeviceState(State other) {
            return other != null && phase == other.phase && keys == other.keys && apps == other.apps &&
                java.util.Objects.equals(powerOn, other.powerOn) &&
                java.util.Objects.equals(volumeLevel, other.volumeLevel) &&
                java.util.Objects.equals(volumeMax, other.volumeMax) &&
                java.util.Objects.equals(muted, other.muted);
        }
        public boolean connected() { return phase == Phase.READY; }
    }
    public interface Listener { void onState(State state); }

    private static final String LEGACY_ALIAS = "sofacockpit.tv.remote.rsa.v1";
    private static final String ALIAS = "sofacockpit.tv.remote.rsa.v3";
    private static final String PIN = "certificate_sha256", HOST = "last_address", KEY_ALIAS = "key_alias";
    private static final String PORTABLE_ALIAS = "sofacockpit.tv.portable.v1", WRAPPED_IDENTITY = "portable_identity";
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService sessions = Executors.newSingleThreadExecutor();
    // Independent of the reader and writer: can abort a stalled read or write.
    private final ScheduledExecutorService watchdogs = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor writers = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.AbortPolicy());
    private final Listener listener;
    private State state = new State(Phase.DISCONNECTED, "Nicht verbunden", false);
    private Session current;
    private boolean closed;
    private final ForegroundRecovery recovery = new ForegroundRecovery();
    private final RefreshIntent refreshIntent = new RefreshIntent();
    private RefreshProbe refreshProbe;

    public TvRemoteClient(Context context, Listener listener) {
        prefs = context.getApplicationContext().getSharedPreferences("tv_pairing", Context.MODE_PRIVATE);
        this.listener = listener;
    }
    public State state() { return state; }
    public boolean paired() { return prefs.contains(PIN); }
    public String lastAddress() { return prefs.getString(HOST, ""); }
    public boolean busy() { return current != null; }
    public boolean hasPortablePairing() {
        return paired() && PORTABLE_ALIAS.equals(prefs.getString(KEY_ALIAS, "")) && prefs.contains(WRAPPED_IDENTITY);
    }

    public void rememberAddress(String address) throws IOException {
        recovery.cancel();
        String host = address == null ? "" : address.trim();
        if (!host.isEmpty()) localAddress(host);
        boolean changed = !host.equals(lastAddress());
        if (!prefs.edit().putString(HOST, host).commit()) throw new IOException("TV-IP konnte nicht gespeichert werden.");
        if (changed) refreshIntent.clear();
    }

    /** Invoked only by a user-initiated encrypted backup worker. */
    public org.json.JSONObject exportConfiguration() throws Exception {
        java.util.Map<String, ?> snapshot = prefs.getAll();
        org.json.JSONObject config = new org.json.JSONObject().put("address", snapshot.containsKey(HOST) ? snapshot.get(HOST) : "");
        if (PORTABLE_ALIAS.equals(snapshot.get(KEY_ALIAS)) && snapshot.containsKey(PIN) && snapshot.containsKey(WRAPPED_IDENTITY)) {
            PortableTvIdentity identity = TvIdentityVault.open((String) snapshot.get(WRAPPED_IDENTITY));
            org.json.JSONObject pairing = identity.json().put("serverPin", snapshot.get(PIN));
            validatePin(pairing.getString("serverPin"));
            config.put("pairing", pairing);
        }
        return config;
    }
    public static final class PreparedConfiguration {
        public final String address;
        private final String pin, wrapped;
        PreparedConfiguration(String address, String pin, String wrapped) {
            this.address = address; this.pin = pin; this.wrapped = wrapped;
        }
        public boolean includesPairing() { return wrapped != null; }
    }
    /** Validate and wrap imported material before applying any device settings. */
    public PreparedConfiguration prepareConfiguration(org.json.JSONObject config) throws Exception {
        String host = config.getString("address").trim();
        if (!host.isEmpty()) localAddress(host);
        if (!config.has("pairing")) return new PreparedConfiguration(host, null, null);
        if (host.isEmpty()) throw new IOException("TV-Kopplung ohne Adresse");
        org.json.JSONObject pairing = config.getJSONObject("pairing");
        String pin = pairing.getString("serverPin");
        validatePin(pin);
        PortableTvIdentity identity = PortableTvIdentity.parse(pairing);
        return new PreparedConfiguration(host, pin, TvIdentityVault.seal(identity));
    }
    private static void validatePin(String pin) throws GeneralSecurityException {
        try {
            byte[] raw = java.util.Base64.getDecoder().decode(pin);
            if (raw.length != 32 || !java.util.Base64.getEncoder().encodeToString(raw).equals(pin))
                throw new GeneralSecurityException("Ungültiger TV-Zertifikatsfingerabdruck");
        } catch (IllegalArgumentException ex) { throw new GeneralSecurityException("Ungültiger TV-Zertifikatsfingerabdruck"); }
    }
    public void applyConfiguration(PreparedConfiguration config) throws IOException {
        disconnect();
        SharedPreferences.Editor edit = prefs.edit().putString(HOST, config.address);
        if (config.includesPairing())
            edit.putString(PIN, config.pin).putString(KEY_ALIAS, PORTABLE_ALIAS).putString(WRAPPED_IDENTITY, config.wrapped);
        else if (!config.address.equals(lastAddress()))
            edit.remove(PIN).remove(KEY_ALIAS).remove(WRAPPED_IDENTITY);
        // A settings-only backup preserves an existing pairing only for the same address.
        if (!edit.commit()) throw new IOException("TV-Konfiguration konnte nicht gespeichert werden.");
        update(Phase.DISCONNECTED, "Konfiguration geladen – bitte verbinden.", false);
    }

    private void update(Phase phase, String detail, boolean keys) {
        setState(new State(phase, detail, keys));
    }
    private void setState(State next) {
        state = next;
        if (next.connected() && current != null && paired() && current.host.equals(lastAddress()))
            refreshIntent.confirmed(current.host, next.powerOn);
        listener.onState(state);
    }
    private void publish(Session s, Phase phase, String detail, boolean keys) {
        main.post(() -> { if (!closed && current == s && !s.cancelled) update(phase, detail, keys); });
    }
    /** Keep live sockets across pause; restore a lost active session only on resume. */
    public void onBackground() {
        if (closed) return;
        recovery.background(current != null && state.connected() &&
            !Boolean.FALSE.equals(state.powerOn) ? current.host : null);
    }

    public void onForeground(boolean networkAllowed) {
        if (closed) return;
        recovery.foreground(SystemClock.elapsedRealtime(), networkAllowed);
        restoreForegroundSession();
    }

    private void restoreForegroundSession() {
        if (closed) return;
        String host = recovery.take(SystemClock.elapsedRealtime(),
            state.phase == Phase.ERROR && current == null, paired() ? lastAddress() : null);
        if (host != null) start(host, false);
    }

    public void start(String address, boolean pair) {
        recovery.cancel();
        if (closed) return;
        String host = address == null ? "" : address.trim();
        refreshIntent.begin(!pair && paired() && !host.isEmpty() ? host : null);
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
        refreshIntent.clear();
        recovery.cancel();
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
        closed = true; recovery.cancel(); refreshIntent.clear();
        cancelConnectionRefresh();
        cancelCurrent();
        sessions.shutdownNow(); writers.shutdownNow(); watchdogs.shutdownNow();
    }

    /** Pull-to-refresh is user initiated. No invented TV status request or key event:
     * observe a fresh inbound Remote-v2 frame, then reconnect once only if needed.
     * Do not disturb a pairing, manual disconnect, changed address or known standby.
     */
    public void refreshConnection(java.util.function.Consumer<String> done) {
        if (closed) { done.accept("TV: nicht verfügbar"); return; }
        if (refreshProbe != null) { done.accept("TV: Prüfung läuft bereits"); return; }
        if (state.phase == Phase.PAIRING || state.phase == Phase.CODE || state.phase == Phase.VERIFYING) {
            done.accept("TV: Kopplung läuft"); return;
        }
        if (!refreshIntent.eligible(lastAddress(), paired())) {
            done.accept(paired() ? "TV: keine gewünschte Verbindung" : "TV: zuerst koppeln"); return;
        }
        if (refreshIntent.standby()) { done.accept("TV: Standby, nicht aufgeweckt"); return; }
        refreshProbe = new RefreshProbe(done);
        refreshProbe.run();
    }

    public void cancelConnectionRefresh() {
        if (refreshProbe != null) {
            main.removeCallbacks(refreshProbe);
            refreshProbe = null;
        }
    }

    private final class RefreshProbe implements Runnable {
        private final java.util.function.Consumer<String> done;
        private final long started = SystemClock.elapsedRealtime();
        private long revision = refreshIntent.revision();
        private long deadline = started + 32_000L;
        private boolean restarted;
        RefreshProbe(java.util.function.Consumer<String> done) { this.done = done; }

        @Override public void run() {
            if (closed || refreshProbe != this) return;
            long now = SystemClock.elapsedRealtime();
            if (revision != refreshIntent.revision() || !refreshIntent.eligible(lastAddress(), paired())) {
                finish("TV: Verbindung geändert oder getrennt"); return;
            }
            if (refreshIntent.standby()) { finish("TV: Standby, nicht aufgeweckt"); return; }
            Session s = current;
            if (state.connected() && s != null && s.ready && s.liveness != null &&
                s.liveness.receivedSince(started)) {
                finish(restarted ? "TV: wieder verbunden" : "TV: Verbindung bestätigt"); return;
            }
            if (state.phase == Phase.PAIRING || state.phase == Phase.CODE || state.phase == Phase.VERIFYING) {
                finish("TV: Kopplung läuft"); return;
            }
            if (!restarted && ((state.phase == Phase.ERROR && s == null) ||
                (state.connected() && now - started >= 10_000L))) {
                restarted = true;
                start(lastAddress(), false);
                revision = refreshIntent.revision();
                deadline = now + 32_000L;
            } else if (restarted && state.phase == Phase.ERROR && s == null) {
                finish("TV: Wiederverbindung fehlgeschlagen"); return;
            } else if (now >= deadline) {
                finish("TV: keine neue Antwort bestätigt"); return;
            }
            main.postDelayed(this, 250L);
        }
        private void finish(String message) {
            main.removeCallbacks(this);
            if (refreshProbe != this) return;
            refreshProbe = null;
            done.accept(message);
        }
    }

    public void sendKey(int key) {
        if (!allowedKey(key)) { commandDetail("Dieser TV-Befehl wird nicht unterstützt."); return; }
        sendCommand(TvProtocol.key(key), false, null);
    }

    public void launchApp(String link, String label) {
        try { sendCommand(TvProtocol.appLink(link), true,
            "Startbefehl für " + label + " gesendet – Start am TV prüfen."); }
        catch (IllegalArgumentException ex) { commandDetail(ex.getMessage()); }
    }

    private void commandDetail(String detail) {
        if (!closed) setState(state.withDetail(detail));
    }

    private void sendCommand(byte[] message, boolean app, String sentDetail) {
        Session s = current;
        if (s == null || !s.ready || !state.connected()) {
            commandDetail("TV nicht verbunden. Zuerst verbinden."); return;
        }
        if (app ? !s.apps : !s.keys) {
            commandDetail(app ? "TV meldet keine App-Link-Unterstützung." : "TV meldet keine Tastenunterstützung.");
            return;
        }
        try {
            writers.execute(() -> {
                if (s.cancelled || !s.ready || (app ? !s.apps : !s.keys)) return;
                try {
                    s.send(message);
                    if (sentDetail != null) main.post(() -> {
                        if (!closed && current == s && !s.cancelled && state.connected()) commandDetail(sentDetail);
                    });
                } catch (IOException ex) {
                    s.writeFailed = true;
                    s.closeSocket();
                }
            });
        } catch (RejectedExecutionException ex) {
            if (!closed && current == s) commandDetail("Zu viele TV-Befehle. Bitte kurz warten.");
        }
    }
    static boolean allowedKey(int key) {
        return key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_HOME ||
            key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
            key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
            key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_POWER ||
            key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN ||
            key == KeyEvent.KEYCODE_VOLUME_MUTE ||
            key == KeyEvent.KEYCODE_MEDIA_REWIND || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key == KeyEvent.KEYCODE_SETTINGS ||
            key == KeyEvent.KEYCODE_TV_INPUT || key == KeyEvent.KEYCODE_TV_INPUT_HDMI_1 ||
            key == KeyEvent.KEYCODE_TV_INPUT_HDMI_2 || key == KeyEvent.KEYCODE_TV_INPUT_HDMI_3 ||
            key == KeyEvent.KEYCODE_TV_INPUT_HDMI_4;
    }

    private void run(Session s) {
        String error = null;
        try {
            if (s.cancelled) return;
            InetAddress host = localAddress(s.host);
            s.stage = "Client-Schlüssel";
            String pin = prefs.getString(PIN, null);
            // Existing successful pairings keep their original client identity.
            // Fresh pairing uses an exportable RSA identity encrypted by an Android Keystore AES key.
            String alias = s.pair ? PORTABLE_ALIAS : prefs.getString(KEY_ALIAS, LEGACY_ALIAS);
            Identity identity;
            String wrapped = null;
            if (PORTABLE_ALIAS.equals(alias)) {
                try {
                    PortableTvIdentity portable = s.pair ? PortableTvIdentity.create() :
                        TvIdentityVault.open(prefs.getString(WRAPPED_IDENTITY, null));
                    identity = new Identity(PORTABLE_ALIAS, portable.key, portable.cert);
                    if (s.pair) wrapped = TvIdentityVault.seal(portable);
                } catch (Exception ex) {
                    throw new GeneralSecurityException("Exportierbare TV-Identität konnte nicht eingerichtet werden.", ex);
                }
            } else identity = identity(alias);
            if (s.pair) {
                pin = pair(s, host, identity);
                synchronized (s) {
                    if (s.cancelled) return;
                    // Only after successful secret acknowledgement. Old trust is retained on failure.
                    if (!prefs.edit().putString(PIN, pin).putString(HOST, s.host).putString(KEY_ALIAS, alias)
                        .putString(WRAPPED_IDENTITY, wrapped).commit())
                        throw new IOException("Neue TV-Kopplung konnte nicht gespeichert werden.");
                }
                publish(s, Phase.CONNECTING, "Gekoppelt. Steuersitzung wird aufgebaut …", false);
            }
            if (pin == null) throw new IOException("Keine gespeicherte Kopplung.");
            remote(s, host, identity, pin);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            error = "Verbindung abgebrochen.";
        } catch (SocketTimeoutException ex) {
            error = s.stage + ": Zeitüberschreitung. TV, Heimnetz und lokale Netzwerkberechtigung prüfen.";
        } catch (SSLException ex) {
            error = s.stage + ": TLS-Verbindung fehlgeschlagen. " + diagnostic(ex);
        } catch (ConnectException | NoRouteToHostException ex) {
            error = "TV nicht erreichbar. IP-Adresse, Netzwerk und eingeschalteten TV prüfen.";
        } catch (GeneralSecurityException ex) {
            error = s.stage + ": Sicherer Schlüsselspeicher oder TLS konnte nicht eingerichtet werden. " + diagnostic(ex);
        } catch (EOFException ex) {
            error = "Der Fernseher hat die Verbindung beendet. Bitte erneut verbinden.";
        } catch (IOException | IllegalArgumentException ex) {
            error = s.stage + ": " + diagnostic(ex);
        } catch (SecurityException ex) {
            error = "Lokaler Netzwerkzugriff nicht erlaubt. Berechtigung in Android prüfen.";
        } catch (RuntimeException ex) {
            error = s.stage + ": TV-Verbindung fehlgeschlagen. " + diagnostic(ex);
        } finally {
            s.ready = false; s.keys = false; s.apps = false; s.closeSocket();
            final String reason = s.writeFailed ? "TV-Befehl konnte nicht gesendet werden. Bitte erneut verbinden." :
                (error == null ? "Verbindung beendet." : error);
            main.post(() -> {
                if (!closed && current == s && !s.cancelled) {
                    current = null;
                    writers.getQueue().clear();
                    update(Phase.ERROR, reason, false);
                    // The failure notification can reach the UI after resume.
                    restoreForegroundSession();
                }
            });
        }
    }

    private static String diagnostic(Throwable error) {
        // No application payloads, codes or key material are included in TLS diagnostics.
        StringBuilder result = new StringBuilder();
        for (int depth = 0; error != null && depth < 4; depth++, error = error.getCause()) {
            if (result.length() > 0) result.append(" / ");
            result.append(error.getClass().getSimpleName());
            String detail = error.getMessage();
            if (detail != null && !detail.isEmpty()) {
                detail = detail.replaceAll("[\\r\\n\\t]+", " ");
                result.append(": ").append(detail.substring(0, Math.min(180, detail.length())));
            }
        }
        return result.substring(0, Math.min(360, result.length()));
    }

    private String pair(Session s, InetAddress host, Identity identity) throws IOException, GeneralSecurityException, InterruptedException {
        s.stage = "Kopplung (Port 6467)";
        try (SSLSocket socket = open(s, host, TvProtocol.PAIR_PORT, identity, null)) {
            s.stage = "Kopplungsprotokoll";
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
        s.stage = "Steuerverbindung (Port 6466)";
        try (SSLSocket socket = open(s, host, TvProtocol.REMOTE_PORT, identity, pin)) {
            s.stage = "TV-Steuerprotokoll";
            publish(s, Phase.CONNECTING, "TV-Kopplung bestätigt. Steuersitzung wird ausgehandelt …", false);
            // An input-only 16 s timeout could disconnect during active key use:
            // the TV may suppress its normal idle pings while receiving commands.
            // Use a bidirectional watchdog instead, so a partial frame is never
            // discarded and restarted after a read timeout.
            socket.setSoTimeout(0);
            InputStream input = socket.getInputStream();
            TvProtocol.RemoteHandshake handshake = new TvProtocol.RemoteHandshake();
            RemoteLiveness live = new RemoteLiveness(SystemClock.elapsedRealtime());
            s.liveness = live;
            Socket raw = s.raw;
            if (raw == null || s.cancelled) throw new IOException("Abgebrochen");
            raw.setKeepAlive(true);
            java.util.concurrent.atomic.AtomicReference<RemoteLiveness.Expiry> timedOut =
                new java.util.concurrent.atomic.AtomicReference<>(RemoteLiveness.Expiry.NONE);
            ScheduledFuture<?> watch = watchdogs.scheduleAtFixedRate(() -> {
                if (s.cancelled) return;
                RemoteLiveness.Expiry expiry = live.expiry(SystemClock.elapsedRealtime());
                if (expiry != RemoteLiveness.Expiry.NONE &&
                    timedOut.compareAndSet(RemoteLiveness.Expiry.NONE, expiry)) {
                    // Invalidate the transport before unblocking the reader.
                    s.ready = false; s.keys = false; s.apps = false;
                    try { raw.close(); } catch (IOException ignored) {}
                }
            }, 1, 1, TimeUnit.SECONDS);
            boolean announced = false;
            State previous = null;
            try {
                while (!s.cancelled) {
                    byte[] frame = TvProtocol.readFrame(input);
                    byte[] reply = handshake.accept(frame);
                    live.inbound(SystemClock.elapsedRealtime(), handshake.ready());
                    if (reply != null) s.send(reply);
                    if (timedOut.get() != RemoteLiveness.Expiry.NONE) throw new IOException("TV-Zeitüberschreitung");
                    s.ready = handshake.ready();
                    s.keys = handshake.supportsKeys();
                    s.apps = handshake.supportsApps();
                    if (s.ready && !announced) {
                        synchronized (s) {
                            if (s.cancelled) return;
                            prefs.edit().putString(HOST, s.host).apply();
                        }
                        announced = true;
                    }
                    if (s.ready) {
                        State snapshot = new State(Phase.READY,
                            s.keys ? "Verbunden" : "Verbunden – TV meldet keine Tastenunterstützung.",
                            s.keys, s.apps, handshake.powerOn(), handshake.volumeLevel(), handshake.volumeMax(), handshake.muted());
                        if (!snapshot.sameDeviceState(previous)) {
                            previous = snapshot;
                            main.post(() -> {
                                if (!closed && current == s && !s.cancelled && s.ready) setState(snapshot);
                            });
                        }
                    } else if (announced) {
                        announced = false; previous = null;
                        publish(s, Phase.CONNECTING, "TV-Steuersitzung wird neu ausgehandelt …", false);
                    }
                }
            } catch (IOException ex) {
                RemoteLiveness.Expiry expiry = timedOut.get();
                if (expiry != RemoteLiveness.Expiry.NONE) {
                    String reason = expiry == RemoteLiveness.Expiry.HANDSHAKE
                        ? "Keine bestätigte TV-Steuersitzung"
                        : "TV-Verbindung unterbrochen: 25 s ohne Sende- oder Empfangsaktivität";
                    throw new IOException(reason + ". " + live.diagnostics(SystemClock.elapsedRealtime())
                        + ". " + handshake.diagnostics(), ex);
                }
                if (ex instanceof EOFException)
                    throw new IOException("TV hat die Steuerverbindung beendet. " + handshake.diagnostics(), ex);
                throw ex;
            } finally {
                watch.cancel(false);
                s.liveness = null;
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
        volatile boolean cancelled, ready, keys, apps, writeFailed;
        String stage = "TV-Verbindung";
        volatile Socket raw;
        volatile SSLSocket tls;
        volatile RemoteLiveness liveness;
        final Object outputLock = new Object();
        Session(String host, boolean pair) { this.host = host; this.pair = pair; }
        void send(byte[] message) throws IOException {
            synchronized (outputLock) {
                SSLSocket socket = tls;
                if (cancelled || socket == null || socket.isClosed()) throw new IOException("TV nicht verbunden.");
                TvProtocol.writeFrame(socket.getOutputStream(), message);
                RemoteLiveness live = liveness;
                if (live != null) live.outbound(SystemClock.elapsedRealtime());
            }
        }
        void closeSocket() {
            Socket r = raw; raw = null; tls = null;
            if (r != null) try { r.close(); } catch (IOException ignored) {}
        }
        void cancel() {
            synchronized (this) { cancelled = true; ready = false; keys = false; apps = false; }
            codes.clear(); codes.offer(""); // unblock a waiting pairing worker
            closeSocket(); // raw close unblocks reads without a TLS shutdown handshake on the UI thread
        }
    }

    private static final class Identity {
        final String alias;
        final PrivateKey key;
        final X509Certificate cert;
        Identity(String alias, PrivateKey key, X509Certificate cert) { this.alias = alias; this.key = key; this.cert = cert; }
    }
    private static Identity identity(String alias) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(alias)) {
            if (!ALIAS.equals(alias)) throw new GeneralSecurityException("Früherer Client-Schlüssel fehlt; bitte neu koppeln.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            long now = System.currentTimeMillis();
            generator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512, KeyProperties.DIGEST_SHA1)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                // Conscrypt constructs RSA-PSS padding before invoking the private-key
                // operation through RSA/ECB/NoPadding. Keystore maps private-key
                // Cipher.ENCRYPT_MODE to PURPOSE_SIGN. Do not authorize encryption
                // or decryption purposes, or use unpadded RSA to encrypt TV traffic.
                // https://github.com/google/conscrypt/issues/718
                // https://github.com/google/conscrypt/issues/1201
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setCertificateSubject(new X500Principal("CN=Sofacockpit-" + java.util.UUID.randomUUID()))
                .setCertificateSerialNumber(new BigInteger(63, new SecureRandom()).setBit(62))
                .setCertificateNotBefore(new Date(now - 86400000L))
                .setCertificateNotAfter(new Date(now + 10L * 365 * 86400000L))
                .build());
            generator.generateKeyPair();
        }
        PrivateKey privateKey = (PrivateKey)ks.getKey(alias, null);
        X509Certificate certificate = (X509Certificate)ks.getCertificate(alias);
        if (privateKey == null || certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey))
            throw new GeneralSecurityException("Ungültige Client-Identität");
        if (ALIAS.equals(alias)) {
            // Conscrypt may sign a digest it already computed. Validate that the
            // non-exportable key actually permits raw RSA signing before using TLS.
            byte[] challenge = new byte[32];
            new SecureRandom().nextBytes(challenge);
            Signature signer = Signature.getInstance("NONEwithRSA");
            signer.initSign(privateKey);
            signer.update(challenge);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("NONEwithRSA");
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(challenge);
            if (!verifier.verify(signature)) throw new GeneralSecurityException("TLS-RSA-Selbsttest fehlgeschlagen");
            certificate.verify(certificate.getPublicKey());
            // Exercise the exact private-key operation Conscrypt needs for
            // RSA-PSS TLS signatures, not just NONEwithRSA (PKCS#1 v1.5).
            // A random block below the RSA modulus avoids an invalid-input error.
            RSAPublicKey publicKey = (RSAPublicKey)certificate.getPublicKey();
            int modulusBytes = (publicKey.getModulus().bitLength() + 7) / 8;
            byte[] block = new byte[modulusBytes];
            new SecureRandom().nextBytes(block);
            block[0] = 0;
            javax.crypto.Cipher rawRsa = javax.crypto.Cipher.getInstance("RSA/ECB/NoPadding");
            rawRsa.init(javax.crypto.Cipher.ENCRYPT_MODE, privateKey);
            byte[] rawSignature = rawRsa.doFinal(block);
            BigInteger recovered = new BigInteger(1, rawSignature).modPow(
                publicKey.getPublicExponent(), publicKey.getModulus());
            if (!recovered.equals(new BigInteger(1, block))) {
                throw new GeneralSecurityException("RSA-PSS-TLS-Selbsttest fehlgeschlagen");
            }
        }
        return new Identity(alias, privateKey, certificate);
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
                return type != null && type.startsWith("RSA") ? new String[]{identity.alias} : null;
            }
            public String chooseClientAlias(String[] types, Principal[] issuers, Socket socket) {
                if (types != null) for (String type : types) if (getClientAliases(type, issuers) != null) return identity.alias;
                return null;
            }
            public String[] getServerAliases(String type, Principal[] issuers) { return null; }
            public String chooseServerAlias(String type, Principal[] issuers, Socket socket) { return null; }
            public X509Certificate[] getCertificateChain(String alias) {
                return identity.alias.equals(alias) ? new X509Certificate[]{identity.cert} : null;
            }
            public PrivateKey getPrivateKey(String alias) { return identity.alias.equals(alias) ? identity.key : null; }
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
        // TLS 1.3 where supported, with TLS 1.2 for older TVs. Never enable 1.0/1.1.
        java.util.List<String> modern = new java.util.ArrayList<>();
        for (String protocol : tls.getSupportedProtocols()) {
            if ("TLSv1.3".equals(protocol) || "TLSv1.2".equals(protocol)) modern.add(protocol);
        }
        if (modern.isEmpty()) throw new SSLHandshakeException("Keine TLS-1.2/1.3-Unterstützung");
        tls.setEnabledProtocols(modern.toArray(new String[0]));
        tls.setSoTimeout(10000);
        tls.startHandshake();
        peer(tls);
        return tls;
    }
}
