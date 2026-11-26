package de.sofacockpit.app.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.BooleanSupplier;

/** Explicit local Wake-on-LAN only, using the actual IPv4 prefix, not a fixed /24. */
final class LanWakeSender {
    private LanWakeSender() {}
    private static final class Route {
        final Network network;
        final InetAddress local, broadcast;
        Route(Network network, InetAddress local, InetAddress broadcast) {
            this.network = network; this.local = local; this.broadcast = broadcast;
        }
    }

    static void send(Context context, InetAddress host, String mac, BooleanSupplier cancelled)
        throws IOException, InterruptedException {
        if (!(host instanceof Inet4Address)) throw new IOException("Wake-on-LAN benötigt eine lokale IPv4-Adresse.");
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) throw new IOException("Kein Heimnetz verfügbar.");
        Route selected = null;
        boolean ambiguous = false;
        Network active = manager.getActiveNetwork();
        // Do not use cellular or VPN networks, and never broadcast to a foreign subnet.
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            LinkProperties properties = manager.getLinkProperties(network);
            if (capabilities == null || properties == null ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                  capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue;
            Route match = null;
            for (LinkAddress link : properties.getLinkAddresses()) {
                if (!(link.getAddress() instanceof Inet4Address)) continue;
                byte[] broadcast = WakeOnLan.broadcastFor(link.getAddress().getAddress(),
                    link.getPrefixLength(), host.getAddress());
                if (broadcast != null) {
                    match = new Route(network, link.getAddress(), InetAddress.getByAddress(broadcast));
                    break;
                }
            }
            if (match == null) continue;
            if (network.equals(active)) { selected = match; ambiguous = false; break; }
            if (selected != null) ambiguous = true;
            else selected = match;
        }
        if (selected == null || ambiguous)
            throw new IOException("Kein eindeutiges WLAN/LAN im selben IPv4-Teilnetz wie der TV.");
        if (cancelled.getAsBoolean()) throw new IOException("Aufwecken abgebrochen.");
        byte[] bytes = WakeOnLan.packet(mac);
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(selected.local, 0))) {
            selected.network.bindSocket(socket);
            socket.setBroadcast(true);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, selected.broadcast, WakeOnLan.PORT);
            for (int i = 0; i < WakeOnLan.PACKETS; i++) {
                if (cancelled.getAsBoolean()) throw new IOException("Aufwecken abgebrochen.");
                socket.send(packet);
                if (i + 1 < WakeOnLan.PACKETS) Thread.sleep(300);
            }
        }
    }
}
