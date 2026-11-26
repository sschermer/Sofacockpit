package de.sofacockpit.app.tv;

/** UI-thread policy: recover a previously established session once, only on resume. */
final class ForegroundRecovery {
    // Cover delayed socket errors and the 25-second liveness watchdog.
    static final long RESUME_GRACE_MS = 30_000L;
    private String host;
    private boolean foreground;
    private long expiresAt;

    void background(String previouslyConnectedHost) {
        host = previouslyConnectedHost;
        foreground = false;
        expiresAt = 0;
    }

    void foreground(long now, boolean allowed) {
        foreground = true;
        expiresAt = now + RESUME_GRACE_MS;
        if (!allowed) host = null;
    }

    String take(long now, boolean disconnectedByError, String savedAddress) {
        if (!foreground || host == null) return null;
        if (now > expiresAt || !host.equals(savedAddress)) {
            cancel();
            return null;
        }
        if (!disconnectedByError) return null;
        String result = host;
        cancel();
        return result;
    }

    void cancel() {
        host = null;
        expiresAt = 0;
    }
}
