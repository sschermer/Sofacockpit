package de.sofacockpit.app.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** User-initiated mDNS discovery and pairing dialog. No sockets or protocol code here. */
public final class TvConnectionDialog {
    private final Activity activity;
    private final TvRemoteClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private TextView status, searchStatus;
    private EditText address, code;
    private Button connect, rePair, disconnect, search, confirm;
    private LinearLayout found, codeArea;
    private Discovery discovery;
    public TvConnectionDialog(Activity activity, TvRemoteClient client) {
        this.activity = activity;
        this.client = client;
    }
    public void open() {
        if (dialog != null && dialog.isShowing()) return;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(layout);
        status = text("");
        layout.addView(status);
        address = new EditText(activity);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setHint("TV-IP-Adresse");
        address.setText(client.lastAddress());
        layout.addView(address);
        search = button("Fernseher suchen", () -> startSearch());
        layout.addView(search);
        searchStatus = text("IP-Adresse auch manuell möglich.");
        layout.addView(searchStatus);
        found = new LinearLayout(activity);
        found.setOrientation(LinearLayout.VERTICAL);
        layout.addView(found);

        connect = button("Koppeln", () -> {
            stopSearch();
            client.start(address.getText().toString(), !client.paired());
        });
        layout.addView(connect);
        rePair = button("Neu koppeln", () -> {
            stopSearch();
            new AlertDialog.Builder(activity).setTitle("TV neu koppeln?")
                .setMessage("Die gespeicherte TV-Identität wird erst ersetzt, wenn die neue Kopplung bestätigt wurde.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Koppeln", (d, w) -> client.start(address.getText().toString(), true))
                .show();
        });
        layout.addView(rePair);
        disconnect = button("Verbindung abbrechen / trennen", client::disconnect);
        layout.addView(disconnect);

        codeArea = new LinearLayout(activity);
        codeArea.setOrientation(LinearLayout.VERTICAL);
        codeArea.addView(text("Code vom TV (6 Zeichen, 0–9 und A–F)"));
        code = new EditText(activity);
        code.setSingleLine(true);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS |
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        code.setHint("Kopplungscode");
        codeArea.addView(code);
        confirm = button("Code bestätigen", () -> {
            String value = code.getText().toString();
            code.setText("");
            client.submitCode(value);
        });
        codeArea.addView(confirm);
        layout.addView(codeArea);

        dialog = new AlertDialog.Builder(activity).setTitle("Fernseher verbinden")
            .setView(scroll).setNegativeButton("Schließen", null).create();
        dialog.setOnDismissListener(d -> {
            stopSearch();
            code.setText("");
            dialog = null;
        });
        dialog.show();
        render(client.state());
    }
    public void render(TvRemoteClient.State state) {
        if (dialog == null || !dialog.isShowing()) return;
        boolean busy = client.busy();
        boolean paired = client.paired();
        status.setText((paired ? "Kopplung gespeichert\n" : "Noch nicht gekoppelt\n") + state.detail);
        connect.setText(paired ? "Verbinden" : "Koppeln");
        connect.setEnabled(!busy);
        rePair.setVisibility(paired && !busy ? View.VISIBLE : View.GONE);
        disconnect.setVisibility(busy ? View.VISIBLE : View.GONE);
        address.setEnabled(!busy);
        search.setEnabled(!busy);
        for (int i = 0; i < found.getChildCount(); i++) found.getChildAt(i).setEnabled(!busy);
        boolean asking = state.phase == TvRemoteClient.Phase.CODE;
        boolean verifying = state.phase == TvRemoteClient.Phase.VERIFYING;
        codeArea.setVisibility(asking || verifying ? View.VISIBLE : View.GONE);
        confirm.setEnabled(asking);
        code.setEnabled(asking);
    }
    public void close() {
        stopSearch();
        if (dialog != null) dialog.dismiss();
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(activity);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private TextView text(String s) {
        TextView v = new TextView(activity);
        v.setText(s);
        v.setPadding(0, dp(5), 0, dp(5));
        return v;
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }

    private void startSearch() {
        stopSearch();
        found.removeAllViews();
        searchStatus.setText("Suche im Heimnetz …");
        NsdManager manager = (NsdManager)activity.getSystemService(Context.NSD_SERVICE);
        if (manager == null) {
            searchStatus.setText("Gerätesuche nicht verfügbar. Bitte IP-Adresse eingeben.");
            return;
        }
        Discovery d = new Discovery(manager);
        discovery = d;
        try {
            manager.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, d);
            main.postDelayed(() -> {
                if (discovery == d) {
                    stopSearch();
                    searchStatus.setText(found.getChildCount() == 0 ?
                        "Kein TV gefunden. Bitte IP-Adresse eingeben oder erneut suchen." :
                        "Fernseher auswählen oder IP-Adresse eingeben.");
                }
            }, 12000);
        } catch (RuntimeException ex) {
            stopSearch();
            searchStatus.setText("Gerätesuche fehlgeschlagen. Netzwerk und Berechtigung prüfen.");
        }
    }
    private void stopSearch() {
        Discovery d = discovery;
        discovery = null;
        if (d != null) d.stop();
    }
    private final class Discovery implements NsdManager.DiscoveryListener {
        private final NsdManager manager;
        private final ArrayDeque<NsdServiceInfo> pending = new ArrayDeque<>();
        private final Set<String> seenServices = new HashSet<>();
        private final Set<String> seenAddresses = new HashSet<>();
        private boolean resolving, stopped;
        Discovery(NsdManager manager) { this.manager = manager; }
        private boolean active() { return !stopped && discovery == this && dialog != null && dialog.isShowing(); }
        void stop() {
            stopped = true;
            pending.clear();
            try { manager.stopServiceDiscovery(this); } catch (RuntimeException ignored) {}
        }
        @Override public void onDiscoveryStarted(String serviceType) {}
        @Override public void onDiscoveryStopped(String serviceType) {}
        @Override public void onStartDiscoveryFailed(String serviceType, int error) {
            main.post(() -> {
                if (!active()) return;
                stopSearch();
                if (error == 7) {
                    searchStatus.setText("Lokaler Netzwerkzugriff verweigert (7). Android-Einstellungen → Apps → Sofacockpit → Berechtigungen → Geräte in der Nähe / Lokales Netzwerk erlauben. Danach erneut suchen.");
                } else {
                    searchStatus.setText("Suche konnte nicht gestartet werden (" + error + "). IP-Adresse manuell eingeben.");
                }
            });
        }
        @Override public void onStopDiscoveryFailed(String serviceType, int error) {}
        @Override public void onServiceLost(NsdServiceInfo info) {}
        @Override public void onServiceFound(NsdServiceInfo info) {
            main.post(() -> {
                if (!active() || seenServices.size() >= 30 || !seenServices.add(info.getServiceName())) return;
                pending.offer(info);
                resolveNext();
            });
        }
        private void resolveNext() {
            if (!active() || resolving || pending.isEmpty()) return;
            resolving = true;
            NsdServiceInfo service = pending.poll();
            try {
                manager.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo info, int error) {
                        main.post(() -> { resolving = false; resolveNext(); });
                    }
                    @Override public void onServiceResolved(NsdServiceInfo info) {
                        main.post(() -> {
                            resolving = false;
                            if (!active()) return;
                            InetAddress host = info.getHost();
                            if (host != null && host.getHostAddress() != null && seenAddresses.add(host.getHostAddress())) {
                                String ip = host.getHostAddress();
                                Button item = button(info.getServiceName() + "\n" + ip, () -> {
                                    address.setText(ip);
                                    searchStatus.setText("Fernseher ausgewählt. Jetzt verbinden oder koppeln.");
                                });
                                item.setEnabled(!client.busy());
                                found.addView(item);
                                searchStatus.setText("TV gefunden – zum Auswählen antippen.");
                            }
                            resolveNext();
                        });
                    }
                });
            } catch (RuntimeException ex) {
                resolving = false;
                resolveNext();
            }
        }
    }
}
