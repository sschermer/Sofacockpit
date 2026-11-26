package de.sofacockpit.app.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import de.sofacockpit.app.SystemBarInsets;
import de.sofacockpit.app.theme.*;
import java.util.function.Supplier;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.CheckBox;
import android.widget.Toast;
import de.tonmaschine.android.MainViewModel;
import de.tonmaschine.android.model.Connection;
import de.tonmaschine.android.model.DeviceState;
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
    private Dialog dialog;
    private final MainViewModel soundbar;
    private final SharedPreferences options;
    private final Runnable exportBackup, importBackup;
    private final Supplier<ThemePack> theme;
    private ThemePack pack() { return theme.get(); }
    private CockpitTheme palette() { return pack().theme; }
    private TextView soundStatus, pairingStatus;
    private EditText soundAddress;
    private Button soundConnect, soundDisconnect, soundSave, tvSave, bothConnect;
    private CheckBox autoConnect;
    private TextView status, searchStatus;
    private EditText address, code;
    private Button connect, rePair, disconnect, search, confirm, wakeSetup;
    private LinearLayout found, codeArea;
    private Discovery discovery;
    public TvConnectionDialog(Activity activity, TvRemoteClient client, MainViewModel soundbar,
                              SharedPreferences options, Runnable exportBackup, Runnable importBackup, Supplier<ThemePack> theme) {
        this.activity = activity;
        this.client = client;
        this.soundbar = soundbar;
        this.options = options;
        this.exportBackup = exportBackup;
        this.importBackup = importBackup;
        this.theme = theme;
    }
    public void open() {
        if (dialog != null && dialog.isShowing()) return;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette().background);
        scroll.addView(layout);
        Button back = button("‹  Zurück zur Fernbedienung", this::close);
        layout.addView(back);
        layout.addView(heading("Verbindungen", 24));
        layout.addView(heading("Fernseher", 18));
        status = text("");
        layout.addView(status);
        pairingStatus = text("");
        layout.addView(pairingStatus);
        address = new EditText(activity);
        address.setTextColor(palette().text);
        address.setHintTextColor(palette().muted);
        address.setBackgroundTintList(ColorStateList.valueOf(palette().accent));
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setHint("TV-IP-Adresse");
        address.setText(client.lastAddress());
        layout.addView(address);
        tvSave = button("TV-Adresse speichern", () -> {
            try {
                client.rememberAddress(address.getText().toString());
                toast("TV-Adresse gespeichert.");
            } catch (Exception ex) { address.setError("Gültige lokale IP-Adresse eingeben."); }
        });
        layout.addView(tvSave);
        wakeSetup = button("Wake-on-LAN einrichten", this::openWakeSettings);
        layout.addView(wakeSetup);
        search = button("Fernseher suchen", () -> { if (networkAllowed()) startSearch(); });
        layout.addView(search);
        searchStatus = text("IP-Adresse auch manuell möglich.");
        layout.addView(searchStatus);
        found = new LinearLayout(activity);
        found.setOrientation(LinearLayout.VERTICAL);
        layout.addView(found);

        connect = button("Koppeln", () -> {
            stopSearch();
            if (networkAllowed()) client.start(address.getText().toString(), !client.paired());
        });
        layout.addView(connect);
        rePair = button("Neu koppeln", () -> {
            stopSearch();
            new AlertDialog.Builder(activity).setTitle("TV neu koppeln?")
                .setMessage("Eine neue, exportierbare TV-Identität wird erst gespeichert, wenn die Kopplung bestätigt wurde. Danach kann die Kopplung verschlüsselt gesichert werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Koppeln", (d, w) -> { if (networkAllowed()) client.start(address.getText().toString(), true); })
                .show();
        });
        layout.addView(rePair);
        disconnect = button("Verbindung abbrechen / trennen", client::disconnect);
        layout.addView(disconnect);

        codeArea = new LinearLayout(activity);
        codeArea.setOrientation(LinearLayout.VERTICAL);
        codeArea.addView(text("Code vom TV (6 Zeichen, 0–9 und A–F)"));
        code = new EditText(activity);
        code.setTextColor(palette().text);
        code.setHintTextColor(palette().muted);
        code.setBackgroundTintList(ColorStateList.valueOf(palette().accent));
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

        layout.addView(heading("Soundbar", 18));
        soundStatus = text("");
        layout.addView(soundStatus);
        soundAddress = new EditText(activity);
        soundAddress.setTextColor(palette().text);
        soundAddress.setHintTextColor(palette().muted);
        soundAddress.setBackgroundTintList(ColorStateList.valueOf(palette().accent));
        soundAddress.setSingleLine(true);
        soundAddress.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        soundAddress.setHint("Soundbar-IPv4-Adresse");
        soundAddress.setText(soundbar.getIp().getValue());
        layout.addView(soundAddress);
        soundSave = button("Sound-Adresse speichern", () -> {
            if (soundbar.saveConnectionAddress(soundAddress.getText().toString())) toast("Sound-Adresse gespeichert.");
            else soundAddress.setError("Gültige IPv4-Adresse eingeben.");
        });
        layout.addView(soundSave);
        soundConnect = button("Soundbar verbinden", this::connectSound);
        layout.addView(soundConnect);
        soundDisconnect = button("Soundbar trennen", soundbar::disconnect);
        layout.addView(soundDisconnect);
        bothConnect = button("Beide Geräte verbinden", () -> {
            if (!networkAllowed()) return;
            if (!client.busy()) {
                if (address.getText().toString().trim().isEmpty()) address.setError("TV-Adresse fehlt");
                else client.start(address.getText().toString(), !client.paired());
            }
            if (soundbar.getDevice().getValue().getConnection() == Connection.DISCONNECTED) connectSound();
        });
        layout.addView(bothConnect);
        autoConnect = new CheckBox(activity);
        autoConnect.setText("Beim App-Start automatisch verbinden");
        autoConnect.setTextColor(palette().text);
        autoConnect.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{palette().accent,palette().muted}));
        autoConnect.setChecked(options.getBoolean("auto_connect", false));
        autoConnect.setOnCheckedChangeListener((b, enabled) -> options.edit().putBoolean("auto_connect", enabled).apply());
        layout.addView(autoConnect);
        layout.addView(text("Nur normale Verbindungen, keine Einschaltbefehle. Je nach Gerät kann bereits ein Netzwerkzugriff aufwecken. Standard: aus. Eine zuvor aktive TV-Sitzung kann nach Rückkehr aus dem Hintergrund einmal wiederhergestellt werden; manuell getrennte Geräte bleiben getrennt."));
        layout.addView(heading("Konfiguration", 18));
        layout.addView(text("Passwortgeschützte Datei mit TV-/Soundbar-Adresse, exportierbarer TV-Kopplung, Streaming-Auswahl, Auto-Verbindung und gewähltem Theme. Eigene Theme-Dateien, NAS- und Klangprofileinstellungen bleiben getrennt."));
        layout.addView(button("Konfiguration exportieren", exportBackup));
        layout.addView(button("Konfiguration importieren", importBackup));

        dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            SystemBarInsets.install(dialog.getWindow(), scroll);
        }
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(d -> {
            stopSearch();
            code.setText("");
            dialog = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
        }
        render(client.state());
        renderSound();
    }
    public void render(TvRemoteClient.State state) {
        if (dialog == null || !dialog.isShowing()) return;
        boolean busy = client.busy();
        boolean paired = client.paired();
        status.setText(state.detail);
        status.setTextColor(state.connected() ? palette().accent :
            (busy ? CockpitTheme.PENDING : CockpitTheme.ERROR));
        pairingStatus.setText(client.hasPortablePairing() ? "TV-Kopplung exportierbar" :
            (paired ? "Alte Kopplung – für Export einmal neu koppeln." : "Noch nicht gekoppelt"));
        connect.setText(paired ? "Verbinden" : "Koppeln");
        connect.setEnabled(!busy);
        rePair.setVisibility(paired && !busy ? View.VISIBLE : View.GONE);
        disconnect.setVisibility(busy ? View.VISIBLE : View.GONE);
        address.setEnabled(!busy);
        search.setEnabled(!busy);
        tvSave.setEnabled(!busy);
        wakeSetup.setText(client.wakeConfigured() ? "Wake-on-LAN bearbeiten" : "Wake-on-LAN einrichten");
        wakeSetup.setEnabled(paired && (!busy || state.connected()));
        for (int i = 0; i < found.getChildCount(); i++) found.getChildAt(i).setEnabled(!busy);
        boolean asking = state.phase == TvRemoteClient.Phase.CODE;
        boolean verifying = state.phase == TvRemoteClient.Phase.VERIFYING;
        codeArea.setVisibility(asking || verifying ? View.VISIBLE : View.GONE);
        confirm.setEnabled(asking);
        code.setEnabled(asking);
    }
    public void renderSound() {
        if (dialog == null || !dialog.isShowing() || soundStatus == null) return;
        DeviceState state = soundbar.getDevice().getValue();
        boolean active = state.getConnection() == Connection.CONNECTED;
        boolean connecting = state.getConnection() == Connection.CONNECTING;
        soundStatus.setText(soundbar.getMessage().getValue());
        soundStatus.setTextColor(active ? palette().accent :
            (connecting && state.getError() == null ? CockpitTheme.PENDING : CockpitTheme.ERROR));
        soundAddress.setEnabled(!active && !connecting);
        soundSave.setEnabled(!active && !connecting);
        soundConnect.setEnabled(!active && !connecting);
        soundDisconnect.setEnabled(active || connecting);
    }

    public void reloadAddresses() {
        if (dialog == null || !dialog.isShowing()) return;
        address.setText(client.lastAddress());
        soundAddress.setText(soundbar.getIp().getValue());
        autoConnect.setChecked(options.getBoolean("auto_connect", false));
        render(client.state());
        renderSound();
    }
    private void openWakeSettings() {
        if (!address.getText().toString().trim().equals(client.lastAddress())) {
            toast("Zuerst die LAN-IPv4-Adresse des TV speichern und verbinden.");
            return;
        }
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);
        fields.addView(text("Die LAN-MAC-Adresse des Fernsehers aus der FRITZ!Box eingeben, nicht die WLAN-MAC. Nur die Power-Taste bei fehlender TV-Verbindung sendet Aufweckpakete. Leeres Feld deaktiviert Wake-on-LAN."));
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        input.setHint("AA:BB:CC:DD:EE:FF");
        input.setText(client.wakeMac());
        fields.addView(input);
        AlertDialog setup = new AlertDialog.Builder(activity).setTitle("Wake-on-LAN")
            .setView(fields).setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", null).create();
        setup.show();
        setup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                client.rememberWakeMac(input.getText().toString());
                setup.dismiss();
                toast(client.wakeConfigured() ? "Wake-on-LAN gespeichert." : "Wake-on-LAN deaktiviert.");
            } catch (Exception ex) {
                input.setError(ex.getMessage() == null ? "Einstellung konnte nicht gespeichert werden." : ex.getMessage());
            }
        });
    }

    private void connectSound() {
        if (!networkAllowed()) return;
        String ip = soundAddress.getText().toString().trim();
        if (ip.isEmpty() || !soundbar.saveConnectionAddress(ip)) {
            soundAddress.setError("Gültige IPv4-Adresse eingeben."); return;
        }
        soundbar.connect();
    }
    private boolean networkAllowed() {
        if (Build.VERSION.SDK_INT >= 37 &&
            activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{"android.permission.ACCESS_LOCAL_NETWORK"}, 601);
            toast("Netzwerkzugriff erlauben und danach erneut verbinden.");
            return false;
        }
        return true;
    }
    private void toast(String value) { Toast.makeText(activity, value, Toast.LENGTH_LONG).show(); }
    private TextView heading(String value, int size) {
        TextView view = text(value);
        view.setTextSize(size * palette().fontScale);
        view.setTypeface(ThemeArt.typeface(palette(),Typeface.BOLD));
        view.setTextColor(palette().text);
        view.setPadding(0, dp(15), 0, dp(7));
        return view;
    }
    public void close() {
        stopSearch();
        if (dialog != null) dialog.dismiss();
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(activity);
        b.setText(label);
        b.setTextColor(palette().text);
        b.setTextSize(14 * palette().fontScale);
        b.setTypeface(ThemeArt.typeface(palette(),Typeface.BOLD));
        b.setAllCaps(false);
        b.setBackground(ThemeArt.surface(pack(),palette().control,palette().cornerDp,
            activity.getResources().getDisplayMetrics().density,false,true));
        b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private TextView text(String s) {
        TextView v = new TextView(activity);
        v.setText(s);
        v.setTextColor(palette().muted);
        v.setTypeface(ThemeArt.typeface(palette(),Typeface.NORMAL));
        v.setTextSize(14 * palette().fontScale);
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
