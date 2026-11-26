package de.sofacockpit.app.tv;

/** Bounded recovery of a lost transport just after a deliberate app-launch request.
 * Not a command retry and never a wake request.
 */
final class AppLaunchRecovery {
    private AppLaunchRecovery() {}
    static final long WINDOW_MS = 5_000L;

    static boolean eligible(long now, long lastAppLaunch, boolean foregroundAllowed,
                            boolean previouslyReady, Boolean powerOn, boolean hardFailure,
                            boolean samePairedTarget, boolean refreshRunning) {
        return lastAppLaunch >= 0 && now >= lastAppLaunch && now - lastAppLaunch <= WINDOW_MS &&
            foregroundAllowed && previouslyReady && Boolean.TRUE.equals(powerOn) &&
            !hardFailure && samePairedTarget && !refreshRunning;
    }
}
