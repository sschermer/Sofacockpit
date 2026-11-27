package de.sofacockpit.app.tv;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TclAppLauncherTest {
    @Test public void exactRootLinksAndPackageNamesOnly() {
        assertEquals("sport.dyn.app.android.prod", TclAppLauncher.packageForLink("market://launch?id=sport.dyn.app.android.prod"));
        assertEquals("com.netflix.ninja", TclAppLauncher.packageForLink("netflix://"));
        assertEquals("com.google.android.youtube.tv", TclAppLauncher.packageForLink("https://www.youtube.com"));
        assertNull(TclAppLauncher.packageForLink("https://www.youtube.com/watch?v=1"));
        assertNull(TclAppLauncher.packageForLink("market://launch?id=com.example.app>>OPEN"));
        assertNull(TclAppLauncher.packageForLink("https://www.dyn.sport/"));
    }

    @Test public void parseInventoryAndHideOnlyLikelySystemApps() throws Exception {
        List<TclAppLauncher.App> apps = TclAppLauncher.parseApps("223>>GET>>com.tcl.tv::TV::icon>sport.dyn.app.android.prod::Dyn>bad::Broken>sport.dyn.app.android.prod::Duplicate>com.google.android.youtube.tv::YouTube");
        assertEquals(3, apps.size());
        assertEquals("Dyn", apps.get(0).name);
        assertFalse(apps.get(0).systemLike());
        assertTrue(apps.get(1).systemLike());
        assertFalse(apps.get(2).systemLike());
    }

    @Test public void frameRoundTripAndLimits() throws Exception {
        for (int mode : new int[]{0,1}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            TclAppLauncher.writeFrame(new DataOutputStream(bytes), "223>>OPEN>>sport.dyn.app.android.prod", mode);
            assertEquals("223>>OPEN>>sport.dyn.app.android.prod",
                TclAppLauncher.readFrame(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), mode));
        }
        try {
            TclAppLauncher.readFrame(new DataInputStream(new ByteArrayInputStream(new byte[]{0,16,0,0})), 0);
            fail();
        } catch (IOException expected) {}
        try { TclAppLauncher.algorithm("159>>1>>x>>x>>x>>x>>2"); fail(); }
        catch (IOException expected) {}
    }

    /** Loopback fake TCL: exercises framing, negotiation, app list and a single OPEN. */
    private void session(int mode, String requested, TclAppLauncher.Status expected, boolean open) throws Exception {
        ExecutorService server = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             TclAppLauncher client = new TclAppLauncher()) {
            Future<?> remote = server.submit(() -> {
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(peer.getInputStream());
                    DataOutputStream out = new DataOutputStream(peer.getOutputStream());
                    assertTrue(TclAppLauncher.readFrame(in, 0).startsWith("159>>Sofacockpit>>1>>"));
                    TclAppLauncher.writeFrame(out, "159>>1>>version>>software>>model>>mac>>" + mode, 0);
                    assertEquals("223>>GET", TclAppLauncher.readFrame(in, mode));
                    TclAppLauncher.writeFrame(out, "150>>", mode);
                    TclAppLauncher.writeFrame(out, "223>>GET>>sport.dyn.app.android.prod::Dyn", mode);
                    if (open) assertEquals("223>>OPEN>>sport.dyn.app.android.prod", TclAppLauncher.readFrame(in, mode));
                    assertEquals(-1, in.read());
                } catch (Exception e) { throw new AssertionError(e); }
            });
            TclAppLauncher.Result result = client.execute(InetAddress.getLoopbackAddress(), listener.getLocalPort(), requested, () -> true);
            assertEquals(expected, result.status);
            if (expected == TclAppLauncher.Status.APPS) assertEquals(1, result.apps.size());
            remote.get(5, TimeUnit.SECONDS);
        } finally { server.shutdownNow(); }
    }

    @Test public void plaintextAndEncryptedSessions() throws Exception {
        session(0, "sport.dyn.app.android.prod", TclAppLauncher.Status.SENT, true);
        session(1, "sport.dyn.app.android.prod", TclAppLauncher.Status.SENT, true);
        session(1, null, TclAppLauncher.Status.APPS, false);
        session(0, "com.example.missing", TclAppLauncher.Status.NOT_INSTALLED, false);
    }

    @Test public void invalidPackageAndCancellationDoNotConnect() {
        try (TclAppLauncher client = new TclAppLauncher()) {
            assertEquals(TclAppLauncher.Status.FAILED, client.execute(InetAddress.getLoopbackAddress(), "bad>>OPEN", () -> true).status);
        }
        try (TclAppLauncher client = new TclAppLauncher()) {
            assertEquals(TclAppLauncher.Status.CANCELLED, client.execute(InetAddress.getLoopbackAddress(), null, () -> false).status);
        }
    }
}
