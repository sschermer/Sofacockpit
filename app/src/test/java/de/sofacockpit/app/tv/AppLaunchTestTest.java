package de.sofacockpit.app.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppLaunchTestTest {
    @Test public void sentAndFinishedObservationDoNotClaimThatTheAppOpened() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        test.sent();
        AppLaunchTest.Result sent = test.result();
        assertEquals(AppLaunchTest.Target.DYN, sent.target);
        assertTrue(sent.written);
        assertFalse(sent.connectionLost);
        assertNull(sent.rejectionCode);
        assertTrue(sent.message.contains("wird nicht bestätigt"));

        test.observationEnded();
        AppLaunchTest.Result observed = test.result();
        assertTrue(observed.written);
        assertFalse(observed.connectionLost);
        assertNull(observed.rejectionCode);
        assertTrue(observed.message.contains("keine zuordenbare Ablehnung"));
        assertTrue(observed.message.contains("Keine Bestätigung des App-Starts"));
    }

    @Test public void earlyRejectionSurvivesTheWriterCallbackAndObservationEnd() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.YOUTUBE);
        test.rejected(7);
        assertEquals(Long.valueOf(7), test.result().rejectionCode);
        assertFalse(test.result().written);

        test.sent();
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertTrue(result.written);
        assertFalse(result.connectionLost);
        assertEquals(Long.valueOf(7), result.rejectionCode);
        assertTrue(result.message.contains("abgelehnt (Code 7)"));
        assertFalse(result.message.contains("keine zuordenbare Ablehnung"));
    }

    @Test public void rejectionAndSubsequentConnectionLossAreBothPreserved() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        test.sent();
        test.rejected(7);
        test.disconnected(true);
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertTrue(result.written);
        assertTrue(result.connectionLost);
        assertEquals(Long.valueOf(7), result.rejectionCode);
        assertTrue(result.message.contains("abgelehnt (Code 7)"));
        assertTrue(result.message.contains("Danach wurde die Steuerverbindung unterbrochen"));
    }

    @Test public void disconnectBeforeTransmissionReportsNotSent() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        test.disconnected(false);
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertFalse(result.written);
        assertNull(result.rejectionCode);
        assertTrue(result.message.startsWith("Nicht gesendet."));
        assertTrue(result.message.contains("vor dem Senden"));
    }

    @Test public void transportFailureBeforeWriteConfirmationDoesNotClaimDelivery() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        // The write started, then I/O failed before the successful-flush callback.
        test.disconnected(true);
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertFalse(result.written);
        assertTrue(result.connectionLost);
        assertNull(result.rejectionCode);
        assertTrue(result.message.contains("Vollständiger Versand nicht bestätigt"));
        assertTrue(result.message.contains("App-Start ist nicht bestätigt"));
        assertFalse(result.message.contains("Befehl gesendet"));
    }

    @Test public void connectionLossAfterConfirmedWriteKeepsDeliverySeparateFromAppLaunch() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.YOUTUBE);
        test.sent();
        test.disconnected(true);
        AppLaunchTest.Result result = test.result();
        assertTrue(result.written);
        assertTrue(result.connectionLost);
        assertNull(result.rejectionCode);
        assertTrue(result.message.contains("Befehl gesendet; danach Steuerverbindung unterbrochen"));
        assertTrue(result.message.contains("App-Start ist nicht bestätigt"));
    }

    @Test public void stoppingBeforeTransmissionReportsNotSentAndKeepsTheReason() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        test.stop("Vom Nutzer beendet.", false);
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertFalse(result.written);
        assertFalse(result.connectionLost);
        assertNull(result.rejectionCode);
        assertTrue(result.message.startsWith("Nicht gesendet."));
        assertTrue(result.message.contains("Vom Nutzer beendet."));
    }

    @Test public void stoppingDuringTransmissionDoesNotConfirmTheWrite() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        test.stop("Beobachtung beendet.", true);
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertFalse(result.written);
        assertFalse(result.connectionLost);
        assertNull(result.rejectionCode);
        assertTrue(result.message.startsWith("Versand nicht bestätigt."));
        assertTrue(result.message.contains("Beobachtung beendet."));
        assertTrue(result.message.contains("Keine Bestätigung des App-Starts"));
    }

    @Test public void stoppingAfterTransmissionPreservesTheWriteWithoutClaimingLaunchSuccess() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.YOUTUBE);
        test.sent();
        test.stop("Beobachtung beendet.", true);
        test.observationEnded();
        AppLaunchTest.Result result = test.result();
        assertTrue(result.written);
        assertFalse(result.connectionLost);
        assertNull(result.rejectionCode);
        assertTrue(result.message.startsWith("Befehl gesendet."));
        assertTrue(result.message.contains("Beobachtung beendet."));
        assertTrue(result.message.contains("Keine Bestätigung des App-Starts"));
    }

    @Test public void stoppingDoesNotHideAnExistingRejectionOrConnectionLoss() {
        AppLaunchTest rejected = new AppLaunchTest(AppLaunchTest.Target.DYN);
        rejected.sent();
        rejected.rejected(7);
        rejected.stop("Beobachtung beendet.", true);
        assertEquals(Long.valueOf(7), rejected.result().rejectionCode);
        assertTrue(rejected.result().message.contains("abgelehnt (Code 7)"));

        rejected.disconnected(true);
        rejected.stop("Erneut beendet.", true);
        assertEquals(Long.valueOf(7), rejected.result().rejectionCode);
        assertTrue(rejected.result().connectionLost);
        assertTrue(rejected.result().message.contains("Steuerverbindung unterbrochen"));

        AppLaunchTest lost = new AppLaunchTest(AppLaunchTest.Target.YOUTUBE);
        lost.disconnected(true);
        lost.stop("Beobachtung beendet.", true);
        assertTrue(lost.result().connectionLost);
        assertTrue(lost.result().message.contains("Vollständiger Versand nicht bestätigt"));
    }

    @Test public void resultSnapshotsStayUnchangedWhenTheTestReceivesMoreEvents() {
        AppLaunchTest test = new AppLaunchTest(AppLaunchTest.Target.DYN);
        AppLaunchTest.Result preparing = test.result();
        String preparingMessage = preparing.message;
        test.sent();
        AppLaunchTest.Result sent = test.result();
        String sentMessage = sent.message;
        test.rejected(7);
        test.disconnected(true);
        AppLaunchTest.Result rejected = test.result();

        assertEquals(AppLaunchTest.Target.DYN, preparing.target);
        assertFalse(preparing.written);
        assertFalse(preparing.connectionLost);
        assertNull(preparing.rejectionCode);
        assertEquals(preparingMessage, preparing.message);
        assertTrue(sent.written);
        assertFalse(sent.connectionLost);
        assertNull(sent.rejectionCode);
        assertEquals(sentMessage, sent.message);
        assertTrue(rejected.written);
        assertTrue(rejected.connectionLost);
        assertEquals(Long.valueOf(7), rejected.rejectionCode);
        assertEquals(AppLaunchTest.Target.DYN, rejected.target);
    }
}
