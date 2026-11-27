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
                    if (requested != null && !requested.equals("sport.dyn.app.android.prod")) {
                        assertEquals("223>>PACKAGEINFO>>" + requested, TclAppLauncher.readFrame(in, mode));
                        TclAppLauncher.writeFrame(out, "223>>PACKAGEINFO>>" + requested + ">>", mode);
                    }
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

    private static final String PRIME = TclAppLauncher.PRIME_PACKAGE;
    private static final String PRIME_RESPONSE = "223>>PACKAGEINFO>>" + PRIME +
        ">>Prime Video>>http://tv.invalid:34831/icon.jpg>>606024130>>6.24.13+v16.0.1.93-allAbis>>659456>>17047552>>26551808>>0";

    @Test public void validatesPositivePackageInfoWithoutInventingApps() {
        TclAppLauncher.App app = TclAppLauncher.parsePackageInfo(PRIME_RESPONSE, PRIME);
        assertNotNull(app); assertEquals(PRIME, app.packageName);
        assertEquals("Prime Video", app.name); assertTrue(app.supplemental);
        assertEquals("http://tv.invalid:34831/icon.jpg", app.iconUrl);
        assertNull(TclAppLauncher.parsePackageInfo(PRIME_RESPONSE, "com.example.other"));
        assertNull(TclAppLauncher.parsePackageInfo("223>>PACKAGEINFO>>" + PRIME + ">>", PRIME));
        assertNull(TclAppLauncher.parsePackageInfo(PRIME_RESPONSE.replace("606024130", "ERROR"), PRIME));
        assertNull(TclAppLauncher.parsePackageInfo(PRIME_RESPONSE.replace("Prime Video", ""), PRIME));
        assertNull(TclAppLauncher.parsePackageInfo(null, PRIME));
        assertEquals(PRIME, TclAppLauncher.supplementCandidates(java.util.Arrays.asList(PRIME, "bad>>OPEN")).get(0));
    }

    private void supplementarySession(boolean launch, boolean timeout, boolean positive) throws Exception {
        ExecutorService server = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             TclAppLauncher client = new TclAppLauncher()) {
            Future<?> remote = server.submit(() -> {
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(5000);
                    DataInputStream in = new DataInputStream(peer.getInputStream());
                    DataOutputStream out = new DataOutputStream(peer.getOutputStream());
                    assertTrue(TclAppLauncher.readFrame(in, 0).startsWith("159>>"));
                    TclAppLauncher.writeFrame(out, "159>>1>>version>>software>>model>>mac>>1", 0);
                    assertEquals("223>>GET", TclAppLauncher.readFrame(in, 1));
                    TclAppLauncher.writeFrame(out, "223>>GET>>com.netflix.ninja::Netflix::http://tv.invalid/Netflix.png", 1);
                    assertEquals("223>>PACKAGEINFO>>" + PRIME, TclAppLauncher.readFrame(in, 1));
                    if (!timeout) TclAppLauncher.writeFrame(out, positive ? PRIME_RESPONSE : "223>>PACKAGEINFO>>" + PRIME + ">>", 1);
                    if (launch && positive && !timeout)
                        assertEquals("223>>OPEN>>" + PRIME, TclAppLauncher.readFrame(in, 1));
                    assertEquals(-1, in.read());
                } catch (Exception e) { throw new AssertionError(e); }
            });
            TclAppLauncher.Result result = client.execute(InetAddress.getLoopbackAddress(), listener.getLocalPort(),
                launch ? PRIME : null, java.util.Arrays.asList("com.netflix.ninja", PRIME, PRIME), () -> true);
            assertEquals(launch ? (positive && !timeout ? TclAppLauncher.Status.SENT : TclAppLauncher.Status.NOT_INSTALLED)
                : TclAppLauncher.Status.APPS, result.status);
            assertEquals(positive && !timeout ? 2 : 1, result.apps.size());
            if (!positive || timeout) assertTrue(result.supplementsIncomplete);
            if (positive && !timeout) assertTrue(result.apps.get(1).supplemental);
            remote.get(6, TimeUnit.SECONDS);
        } finally { server.shutdownNow(); }
    }

    @Test public void targetedInventoryAndNativeOpen() throws Exception {
        supplementarySession(false, false, true);
        supplementarySession(true, false, true);
        supplementarySession(false, false, false);
        supplementarySession(true, false, false);
        supplementarySession(false, true, false);
        supplementarySession(true, true, false);
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
