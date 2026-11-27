package de.sofacockpit.app.tv;

/** In-memory observations of one explicit launch test, never proof that an app opened. */
public final class AppLaunchTest {
    public enum Target {
        YOUTUBE("YouTube", "com.google.android.youtube.tv"),
        DYN("Dyn", "sport.dyn.app.android.prod");

        public final String label;
        final String packageName;
        Target(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    enum Phase { PREPARING, SENT, OBSERVED, REJECTED, CONNECTION_LOST, NOT_SENT, STOPPED }

    public static final class Result {
        public final Target target;
        public final String message;
        public final boolean written, connectionLost;
        public final Long rejectionCode;

        private Result(Target target, String message, boolean written, boolean connectionLost, Long rejectionCode) {
            this.target = target;
            this.message = message;
            this.written = written;
            this.connectionLost = connectionLost;
            this.rejectionCode = rejectionCode;
        }
    }

    private final Target target;
    private Phase phase = Phase.PREPARING;
    private boolean written, connectionLost;
    private Long rejectionCode;
    private String reason = "";

    AppLaunchTest(Target target) { this.target = target; }

    void sent() {
        // A reader can receive a rejection before the writer callback reaches the UI.
        written = true;
        if (phase == Phase.PREPARING) phase = Phase.SENT;
    }

    void rejected(long code) {
        rejectionCode = code;
        phase = Phase.REJECTED;
    }

    void disconnected(boolean transmissionStarted) {
        if (!transmissionStarted) {
            notSent("Die Verbindung endete vor dem Senden.");
            return;
        }
        connectionLost = true;
        if (rejectionCode == null) phase = Phase.CONNECTION_LOST;
    }

    void notSent(String detail) {
        phase = Phase.NOT_SENT;
        reason = detail;
    }

    void stop(String detail, boolean transmissionStarted) {
        if (!transmissionStarted) {
            notSent(detail);
        } else if (rejectionCode == null && !connectionLost) {
            phase = Phase.STOPPED;
            reason = detail;
        }
    }

    void observationEnded() {
        if (phase == Phase.SENT) phase = Phase.OBSERVED;
    }

    public Result result() {
        String message;
        switch (phase) {
            case SENT:
                message = "Befehl gesendet. TV-Rückmeldungen werden fünf Sekunden beobachtet. " +
                    "Der App-Start selbst wird nicht bestätigt; bitte am TV prüfen.";
                break;
            case OBSERVED:
                message = "Befehl gesendet. Im Beobachtungszeitraum keine zuordenbare Ablehnung und " +
                    "kein Verbindungsabbruch gemeldet. Keine Bestätigung des App-Starts; bitte am TV prüfen.";
                break;
            case REJECTED:
                message = "TV hat diesen Startaufruf abgelehnt (Code " + rejectionCode + ").";
                if (connectionLost) message += " Danach wurde die Steuerverbindung unterbrochen.";
                break;
            case CONNECTION_LOST:
                message = written ? "Befehl gesendet; danach Steuerverbindung unterbrochen." :
                    "Verbindung während der Übertragung unterbrochen. Vollständiger Versand nicht bestätigt.";
                message += " Ein App-Start ist nicht bestätigt.";
                break;
            case NOT_SENT:
                message = "Nicht gesendet. " + reason;
                break;
            case STOPPED:
                message = (written ? "Befehl gesendet. " : "Versand nicht bestätigt. ") + reason +
                    " Keine Bestätigung des App-Starts.";
                break;
            default:
                message = "Testbefehl wird vorbereitet …";
        }
        return new Result(target, message, written, connectionLost, rejectionCode);
    }
}
