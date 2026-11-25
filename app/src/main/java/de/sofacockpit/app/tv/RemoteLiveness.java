package de.sofacockpit.app.tv;

/**
 * Monotonic session deadlines. Handshake has an independent, fixed deadline;
 * after RemoteStart, activity in either direction prevents an idle disconnect.
 * The watchdog closes the whole socket, never resumes a partially read frame.
 */
final class RemoteLiveness {
    static final long HANDSHAKE_TIMEOUT_MS = 20_000L;
    static final long IDLE_TIMEOUT_MS = 25_000L;
    enum Expiry { NONE, HANDSHAKE, IDLE }

    private long handshakeSince, lastInbound, lastOutbound;
    private boolean ready;

    RemoteLiveness(long now) {
        handshakeSince = lastInbound = lastOutbound = now;
    }

    synchronized void inbound(long now, boolean confirmed) {
        lastInbound = Math.max(lastInbound, now);
        if (ready && !confirmed) handshakeSince = now;
        ready = confirmed;
    }

    synchronized void outbound(long now) {
        lastOutbound = Math.max(lastOutbound, now);
    }

    synchronized Expiry expiry(long now) {
        if (!ready) return now - handshakeSince >= HANDSHAKE_TIMEOUT_MS
            ? Expiry.HANDSHAKE : Expiry.NONE;
        return now - Math.max(lastInbound, lastOutbound) >= IDLE_TIMEOUT_MS
            ? Expiry.IDLE : Expiry.NONE;
    }

    synchronized String diagnostics(long now) {
        return "Letzter Empfang vor " + Math.max(0, (now - lastInbound) / 1000)
            + " s, letzter Versand vor " + Math.max(0, (now - lastOutbound) / 1000) + " s";
    }
}
