package de.sofacockpit.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.widget.CheckBox;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import de.sofacockpit.app.tv.TvRemoteClient;
import de.sofacockpit.app.theme.ThemeStore;
import de.sofacockpit.app.theme.CockpitTheme;
import de.tonmaschine.android.MainViewModel;
import de.tonmaschine.android.model.Catalog;
import de.tonmaschine.android.model.Connection;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-controlled SAF export/import; no network access or credential logging. */
final class ConnectionBackupManager implements AutoCloseable {
    private final ComponentActivity activity;
    private final TvRemoteClient tv;
    private final MainViewModel soundbar;
    private final StreamingShortcuts shortcuts;
    private final SharedPreferences options;
    private final ThemeStore themes;
    private final Runnable changed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> create;
    private final ActivityResultLauncher<String[]> open;
    private static final String LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";
    private final ActivityResultLauncher<String> networkPermission;
    private ImportConnectionPlan pendingConnection;
    private volatile boolean closed;
    private boolean busy;

    ConnectionBackupManager(ComponentActivity activity, TvRemoteClient tv, MainViewModel soundbar,
                            StreamingShortcuts shortcuts, SharedPreferences options, ThemeStore themes, Runnable changed) {
        this.activity = activity; this.tv = tv; this.soundbar = soundbar;
        this.shortcuts = shortcuts; this.options = options; this.themes = themes; this.changed = changed;
        create = activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"),
            uri -> { if (uri != null && !closed) password(uri, true); });
        open = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null && !closed) password(uri, false); });
        networkPermission = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            ImportConnectionPlan plan = pendingConnection;
            pendingConnection = null;
            if (closed || activity.isFinishing() || plan == null) return;
            if (Boolean.TRUE.equals(granted)) startImportedConnections(plan);
            else toast("Konfiguration geladen. Lokaler Netzwerkzugriff nicht erlaubt.");
        });
    }

    void requestExport() {
        if (busy || closed) return;
        if (tv.paired() && !tv.hasPortablePairing()) {
            new AlertDialog.Builder(activity).setTitle("TV-Kopplung noch nicht exportierbar")
                .setMessage("Die alte TV-Identität ist nicht exportierbar. Einmal neu koppeln, um auch die TV-Kopplung zu sichern. Du kannst trotzdem die Geräteadressen und Schnellwahltasten sichern.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Nur Einstellungen sichern", (d,w) -> create.launch("Sofacockpit.sofacfg")).show();
        } else create.launch("Sofacockpit.sofacfg");
    }
    void requestImport() {
        if (!busy && !closed) open.launch(new String[]{"*/*"});
    }

    private void password(Uri uri, boolean exporting) {
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        TextView note = new TextView(activity);
        note.setText(exporting ? "Mindestens 12 Zeichen. Ohne dieses Passwort lässt sich die Sicherung nicht wiederherstellen." :
            "Passwort der verschlüsselten Sofacockpit-Datei eingeben.");
        fields.addView(note);
        EditText first = passwordField("Passwort");
        fields.addView(first);
        EditText second = exporting ? passwordField("Passwort wiederholen") : null;
        if (second != null) fields.addView(second);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(exporting ? "Sicherung verschlüsseln" : "Sicherung entschlüsseln")
            .setView(fields).setNegativeButton("Abbrechen", null)
            .setPositiveButton(exporting ? "Exportieren" : "Prüfen", null).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = first.getText().toString();
            if (value.length() < BackupCrypto.MIN_PASSWORD || value.length() > 256) {
                first.setError("12–256 Zeichen"); return;
            }
            if (second != null && !value.contentEquals(second.getText())) {
                second.setError("Passwörter stimmen nicht überein."); return;
            }
            char[] pass = value.toCharArray();
            first.setText("");
            if (second != null) second.setText("");
            dialog.dismiss();
            if (exporting) exportFile(uri, pass); else importFile(uri, pass);
        });
    }
    private EditText passwordField(String hint) {
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setHint(hint);
        field.setSaveEnabled(false);
        return field;
    }
    private void exportFile(Uri uri, char[] password) {
        if (closed || busy) { Arrays.fill(password, '\0'); return; }
        busy = true;
        final String soundIp = soundbar.getIp().getValue();
        final boolean auto = options.getBoolean("auto_connect", false);
        final String themeId = themes.selectedId();
        final String streams = shortcuts.exportEntries().toString();
        worker.execute(() -> {
            byte[] plain = null;
            try {
                JSONObject root = new JSONObject().put("version",1).put("tv",tv.exportConfiguration())
                    .put("soundbarIp",soundIp).put("autoConnect",auto).put("themeId",themeId)
                    .put("streaming",new org.json.JSONArray(streams));
                plain = root.toString().getBytes(StandardCharsets.UTF_8);
                byte[] encrypted = BackupCrypto.encrypt(plain,password);
                try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("Datei nicht beschreibbar");
                    out.write(encrypted);
                    out.flush();
                }
                finish(root.getJSONObject("tv").has("pairing") ? "Konfiguration mit TV-Kopplung exportiert." :
                    "Konfiguration ohne TV-Kopplung exportiert.");
            } catch (Exception e) { finish("Export fehlgeschlagen. Datei und gespeicherte Einstellungen prüfen."); }
            finally { if (plain != null) Arrays.fill(plain,(byte)0); Arrays.fill(password,'\0'); }
        });
    }

    private void importFile(Uri uri, char[] password) {
        if (closed || busy) { Arrays.fill(password, '\0'); return; }
        busy = true;
        worker.execute(() -> {
            byte[] plain = null;
            try {
                byte[] encrypted;
                try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Datei nicht lesbar");
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    for (int n; (n = in.read(buffer)) != -1;) {
                        if (out.size() + n > BackupCrypto.MAX_BYTES) throw new IOException("Datei zu groß");
                        out.write(buffer,0,n);
                    }
                    encrypted = out.toByteArray();
                }
                plain = BackupCrypto.decrypt(encrypted,password);
                JSONObject root = new JSONObject(new String(plain,StandardCharsets.UTF_8));
                if (root.getInt("version") != 1) throw new IOException("Unbekannte Version");
                String soundIp = root.getString("soundbarIp").trim();
                if (!soundIp.isEmpty() && !Catalog.INSTANCE.validIp(soundIp)) throw new IOException("Ungültige Soundbar-IP");
                List<StreamingShortcuts.Entry> streams = StreamingShortcuts.parseEntries(root.getJSONArray("streaming"));
                boolean auto = root.getBoolean("autoConnect");
                String themeId = root.has("themeId") ? root.getString("themeId") : null;
                if (themeId != null && !CockpitTheme.validId(themeId)) throw new IOException("Ungültige Theme-ID");
                TvRemoteClient.PreparedConfiguration tvConfig = tv.prepareConfiguration(root.getJSONObject("tv"));
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing()) return;
                    LinearLayout fields = new LinearLayout(activity);
                    fields.setOrientation(LinearLayout.VERTICAL);
                    int padding = (int)(20 * activity.getResources().getDisplayMetrics().density);
                    fields.setPadding(padding, 0, padding, padding);
                    CheckBox connectNow = new CheckBox(activity);
                    connectNow.setText("TV und Soundbar nach dem Import automatisch verbinden");
                    connectNow.setChecked(true);
                    fields.addView(connectNow);
                    TextView connectionNote = new TextView(activity);
                    connectionNote.setText("Nur vorhandene Geräteadressen und TV-Kopplung verwenden. Keine automatische Neukopplung oder Einschaltbefehle; ein Netzwerkzugriff kann je nach Gerät aufwecken.");
                    fields.addView(connectionNote);
                    AlertDialog confirmation = new AlertDialog.Builder(activity).setTitle("Konfiguration übernehmen?")
                        .setMessage("Geräteadressen, Streaming-Liste und Auto-Verbindung werden ersetzt. Beide Geräte werden getrennt. " +
                            (tvConfig.includesPairing() ? "Die gespeicherte TV-Identität wird wiederhergestellt." :
                            "Die Datei enthält keine TV-Kopplung. Eine vorhandene Kopplung bleibt nur bei identischer TV-Adresse erhalten.") +
                            (themeId == null ? "" : themes.has(themeId) ?
                                " Das gesicherte Design wird übernommen." :
                                " Eigenes Design fehlt: Minimal Premium wird verwendet. Theme-Datei separat importieren.") +
                            " Es werden keine Steuerbefehle gesendet.")
                        .setView(fields)
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Übernehmen", (d,w) -> {
                            try {
                                tv.applyConfiguration(tvConfig);
                                soundbar.disconnect();
                                if (!soundbar.saveConnectionAddress(soundIp)) throw new IOException("Soundbar-IP");
                                shortcuts.replaceEntries(streams);
                                options.edit().putBoolean("auto_connect", auto).apply();
                                if (themeId != null) themes.select(themeId);
                                changed.run();
                            } catch (Exception ex) {
                                toast("Import konnte nicht vollständig gespeichert werden. Einstellungen prüfen.");
                                return;
                            }
                            // Independent of the persistent auto-connect-on-start preference.
                            ImportConnectionPlan plan = ImportConnectionPlan.create(connectNow.isChecked(),
                                tv.paired(), tv.lastAddress(), soundbar.getIp().getValue());
                            connectAfterImport(plan);
                        }).create();
                    confirmation.setOnDismissListener(d -> busy = false);
                    confirmation.show();
                });
            } catch (Exception e) {
                finish("Import fehlgeschlagen: falsches Passwort, beschädigte oder nicht unterstützte Datei.");
            } finally {
                if (plain != null) Arrays.fill(plain,(byte)0);
                Arrays.fill(password,'\0');
            }
        });
    }
    private void connectAfterImport(ImportConnectionPlan plan) {
        if (!plan.hasTargets()) {
            toast(plan.tvNeedsPairing ? "Konfiguration geladen. TV muss noch gekoppelt werden." :
                "Konfiguration geladen.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 37 && activity.checkSelfPermission(LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            pendingConnection = plan;
            try { networkPermission.launch(LOCAL_NETWORK); }
            catch (RuntimeException ex) {
                pendingConnection = null;
                toast("Konfiguration geladen. Netzwerkberechtigung bitte manuell prüfen.");
            }
            return;
        }
        startImportedConnections(plan);
    }

    private void startImportedConnections(ImportConnectionPlan plan) {
        // Recheck the saved configuration after a possible permission dialog.
        // Do not replace a session the user has started in the meantime.
        boolean tvStarted = false, soundStarted = false;
        if (!plan.tvAddress.isEmpty() && plan.tvAddress.equals(tv.lastAddress()) && tv.paired() && !tv.busy()) {
            try { tv.start(plan.tvAddress, false); tvStarted = true; }
            catch (RuntimeException ignored) { /* Connection status remains authoritative. */ }
        }
        if (!plan.soundAddress.isEmpty() && plan.soundAddress.equals(soundbar.getIp().getValue()) &&
                soundbar.getDevice().getValue().getConnection() == Connection.DISCONNECTED) {
            try { soundbar.connect(); soundStarted = true; }
            catch (RuntimeException ignored) { /* The other device's connection is independent. */ }
        }
        String message = tvStarted && soundStarted ? "Konfiguration geladen. Verbinde TV und Soundbar …" :
            tvStarted ? "Konfiguration geladen. Verbinde TV …" :
            soundStarted ? "Konfiguration geladen. Verbinde Soundbar …" :
            "Konfiguration geladen. Verbindungsstatus prüfen.";
        if (plan.tvNeedsPairing) message += " TV muss noch gekoppelt werden.";
        toast(message);
    }

    private void finish(String message) {
        activity.runOnUiThread(() -> { busy = false; if (!closed && !activity.isFinishing()) toast(message); });
    }
    private void toast(String message) { Toast.makeText(activity,message,Toast.LENGTH_LONG).show(); }
    @Override public void close() { closed = true; pendingConnection = null; worker.shutdown(); }
}
