package de.sofacockpit.app.tv;

import de.sofacockpit.app.theme.CockpitTheme;

/** Presentation only: a saved wake target is not evidence of network reachability. */
public enum TvConnectionIndicator {
    CONNECTED("Verbunden"),
    WAKE_READY("Einschaltbereit"),
    CONNECTING("Verbinde …"),
    ERROR("Verbindungsfehler"),
    DISCONNECTED("Nicht verbunden");

    public final String label;
    TvConnectionIndicator(String label) { this.label = label; }

    public static TvConnectionIndicator resolve(boolean connected, boolean connecting,
                                                 boolean failed, boolean wakeConfigured,
                                                 boolean actionableFailure) {
        if (connected) return CONNECTED;
        if (connecting) return CONNECTING;
        if (failed && (actionableFailure || !wakeConfigured)) return ERROR;
        if (wakeConfigured) return WAKE_READY;
        return DISCONNECTED;
    }

    public int color(CockpitTheme theme) {
        switch (this) {
            case CONNECTED: return theme.accent;
            case WAKE_READY: return theme.text;
            case CONNECTING: return CockpitTheme.PENDING;
            case ERROR: return CockpitTheme.ERROR;
            default: return theme.muted;
        }
    }
}
