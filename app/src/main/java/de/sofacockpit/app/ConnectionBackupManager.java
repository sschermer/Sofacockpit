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
import de.sofacockpit.app.theme.ThemePack;
import de.sofacockpit.app.theme.ThemeBackup;
import de.tonmaschine.android.model.ProfileCodec;
import android.widget.ScrollView;
import org.json.JSONArray;
import de.tonmaschine.android.MainViewModel;
import de.tonmaschine.android.model.Catalog;
import de.tonmaschine.android.model.Connection;
import de.tonmaschine.android.storage.ConfigurationBackup;
import de.tonmaschine.android.storage.ConfigurationBackupCodec;
import de.tonmaschine.android.storage.BackupFormat;
import de.tonmaschine.android.storage.ProfileSource;
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
    private final VoiceAliasStore voiceAliases;
    private final SharedPreferences updatePrefs;
    private final Runnable changed;
    private static final int MAX_LOCAL_PROFILE_BYTES=1024*1024;
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
        voiceAliases=new VoiceAliasStore(activity);
        updatePrefs=activity.getSharedPreferences("software_updates",ComponentActivity.MODE_PRIVATE);
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
        CheckBox includeHdmiImages=new CheckBox(activity);
        CheckBox includeThemes=new CheckBox(activity);
        CheckBox includeProfiles=new CheckBox(activity);
        if(exporting){
            includeHdmiImages.setText("HDMI-Bilder mitsichern (optional)");
            includeHdmiImages.setChecked(false);
            fields.addView(includeHdmiImages);
            includeThemes.setText("Eigene Designs mitsichern (optional)");
            includeThemes.setChecked(false);fields.addView(includeThemes);
            includeProfiles.setText("Lokale Klangprofile mitsichern (optional)");
            includeProfiles.setChecked(false);fields.addView(includeProfiles);
            TextView details=new TextView(activity);
            details.setText("Alle übrigen persönlichen Einstellungen einschließlich TV-Kopplung (soweit exportierbar), Wake-on-LAN und NAS-Zugangsdaten werden verschlüsselt gesichert. NAS-Profildateien und Caches bleiben außen vor.");
            fields.addView(details);
        }
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(exporting ? "Sicherung verschlüsseln" : "Sicherung entschlüsseln")
            .setView(scroll(fields)).setNegativeButton("Abbrechen", null)
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
            if (exporting) exportFile(uri, pass,includeHdmiImages.isChecked(),includeThemes.isChecked(),includeProfiles.isChecked()); else importFile(uri, pass);
        });
    }
    private ScrollView scroll(LinearLayout fields){
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(fields,new ScrollView.LayoutParams(-1,-2));
        return scroll;
    }
    private static String validatedProfiles(JSONObject json)throws IOException{
        String encoded=json.toString();
        if(encoded.getBytes(StandardCharsets.UTF_8).length>MAX_LOCAL_PROFILE_BYTES)
            throw new IOException("Lokale Klangprofile zu groß");
        return ProfileCodec.INSTANCE.encode(ProfileCodec.INSTANCE.decode(encoded));
    }
    private EditText passwordField(String hint) {
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setHint(hint);
        field.setSaveEnabled(false);
        return field;
    }
    private void exportFile(Uri uri, char[] password, boolean includeImages,boolean includeThemes,boolean includeProfiles) {
        if (closed || busy) { Arrays.fill(password, '\0'); return; }
        busy = true;
        final String soundIp = soundbar.getIp().getValue();
        final boolean auto = options.getBoolean("auto_connect", false);
        final String themeId = themes.selectedId();
        final String streams = shortcuts.exportEntries().toString();
        final String appPresentation = AppPresentation.load(options.getString(AppPresentation.PREF,null)).toJson().toString();
        final ConfigurationBackup profileConnection=soundbar.configurationBackup();
        final HdmiSettings.Snapshot hdmi;
        final java.util.Map<String,?> optionSnapshot=options.getAll();
        final boolean haptics=options.getBoolean("lab_haptics",true);
        final boolean autoUpdates=updatePrefs.getBoolean("auto_check",true);
        final String skipped=updatePrefs.getString("skipped_all","");
        final List<VoiceAliases.Entry> aliases;
        try{hdmi=HdmiSettings.capture(options,includeImages);aliases=voiceAliases.read();}
        catch(RuntimeException e){Arrays.fill(password,'\0');busy=false;toast("Persönliche Konfiguration ungültig. Bitte prüfen.");return;}
        worker.execute(() -> {
            byte[] plain = null;
            try {
                JSONObject tvJson=tv.exportConfiguration();
                String deviceKey=TvRemoteClient.backupDeviceKey(tvJson);
                Integer directMask=deviceKey==null?null:HdmiInputProfile.mask(deviceKey,k->Boolean.TRUE.equals(optionSnapshot.get(k)));
                JSONObject root = new JSONObject().put("version",1).put("tv",tvJson)
                    .put("soundbarIp",soundIp).put("autoConnect",auto).put("themeId",themeId)
                    .put("streaming",new org.json.JSONArray(streams))
                    .put("appPresentation",new JSONObject(appPresentation))
                    .put("profileConnection",new JSONObject(ConfigurationBackupCodec.INSTANCE.encode(profileConnection,BackupFormat.JSON,true)))
                    .put("hdmi",hdmi.toJson())
                    .put("personal",PersonalBackup.capture(haptics,autoUpdates,skipped,aliases,directMask));
                if(includeThemes)root.put("importedThemes",themes.exportImported());
                if(includeProfiles)root.put("localProfiles",new JSONObject(validatedProfiles(new JSONObject(soundbar.exportLocalProfiles()))));
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
                AppPresentation presentation = root.has("appPresentation") ?
                    AppPresentation.parse(root.getJSONObject("appPresentation")) : null;
                ConfigurationBackup profileConnection=root.has("profileConnection")?
                    ConfigurationBackupCodec.INSTANCE.decode(root.getJSONObject("profileConnection").toString()):null;
                if(profileConnection!=null&&!soundIp.equals(profileConnection.getSoundbarIp()))
                    throw new IOException("Abweichende Soundbar-Adressen");
                HdmiSettings.Snapshot hdmi=root.has("hdmi")?HdmiSettings.Snapshot.parse(root.getJSONObject("hdmi")):null;
                if(hdmi!=null&&hdmi.images!=null)for(String image:hdmi.images)if(image!=null){
                    android.graphics.Bitmap decoded=HdmiImages.decode(image);
                    decoded.recycle();
                }
                String themeId = root.has("themeId") ? root.getString("themeId") : null;
                if (themeId != null && !CockpitTheme.validId(themeId)) throw new IOException("Ungültige Theme-ID");
                TvRemoteClient.PreparedConfiguration tvConfig = tv.prepareConfiguration(root.getJSONObject("tv"));
                PersonalBackup personal=root.has("personal")?PersonalBackup.parse(root.getJSONObject("personal")):null;
                if(personal!=null&&personal.hdmiMask!=null&&tvConfig.deviceSettingsKey()==null)
                    throw new IOException("HDMI-Direktwahl ohne Gerätebindung");
                List<ThemePack> importedThemes=root.has("importedThemes")?
                    ThemeBackup.prepare(root.getJSONArray("importedThemes")):null;
                String localProfiles=root.has("localProfiles")?validatedProfiles(root.getJSONObject("localProfiles")):null;
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
                    CheckBox restoreImages=new CheckBox(activity);
                    if(hdmi!=null&&hdmi.images!=null){
                        restoreImages.setText("HDMI-Bilder aus Sicherung übernehmen (ersetzt alle vier Bildzuordnungen)");
                        restoreImages.setChecked(true);
                        fields.addView(restoreImages);
                    }
                    CheckBox restoreThemes=new CheckBox(activity);
                    if(importedThemes!=null){
                        restoreThemes.setText("Eigene Designs übernehmen ("+importedThemes.size()+"); gleiche IDs ersetzen, andere behalten");
                        restoreThemes.setChecked(true);fields.addView(restoreThemes);
                    }
                    CheckBox restoreProfiles=new CheckBox(activity);
                    if(localProfiles!=null){
                        restoreProfiles.setText("Lokale Klangprofile übernehmen (ersetzt alle lokalen Profile, nicht NAS)");
                        restoreProfiles.setChecked(true);fields.addView(restoreProfiles);
                    }
                    TextView connectionNote = new TextView(activity);
                    connectionNote.setText("MAC und HDMI-Direktwahl nur für die passende TV-Kopplung. Ohne passende Kopplung bleiben diese Freigaben inaktiv. Keine automatische Neukopplung oder Einschaltbefehle; ein Netzwerkzugriff kann je nach Gerät aufwecken.");
                    fields.addView(connectionNote);
                    AlertDialog confirmation = new AlertDialog.Builder(activity).setTitle("Konfiguration übernehmen?")
                        .setMessage("Geräteadressen, Streaming-Liste und Auto-Verbindung werden ersetzt. Beide Geräte werden getrennt. " +
                            (tvConfig.includesPairing() ? "Die gespeicherte TV-Identität wird wiederhergestellt." :
                            "Die Datei enthält keine TV-Kopplung. Eine vorhandene Kopplung bleibt nur bei identischer TV-Adresse erhalten.") +
                            (themeId==null?"":" Das gesicherte Design wird ausgewählt, wenn verfügbar; sonst Minimal Premium.") +
                            (presentation==null?"":" Die App-Darstellung wird übernommen.")+
                            (profileConnection==null?"":" Profilspeicherort und NAS-Zugangsdaten werden ersetzt; lokale und gemeinsame NAS-Profildateien bleiben unverändert.")+
                            (hdmi==null?"":" HDMI-Namen, Favoriten und Darstellung werden übernommen.")+
                            (personal==null?"":" Gesicherte persönliche Bedienungs-, Sprach- und Update-Einstellungen werden übernommen.")+
                            " Es werden keine Steuerbefehle gesendet.")
                        .setView(scroll(fields))
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Übernehmen", (d,w) -> {
                            try {
                                if((profileConnection!=null||(localProfiles!=null&&restoreProfiles.isChecked()))&&Boolean.TRUE.equals(soundbar.getProfileBusy().getValue())){
                                    toast("NAS arbeitet noch. Import später wiederholen.");return;
                                }
                                if(importedThemes!=null&&restoreThemes.isChecked()){
                                    try{themes.validateRestore(importedThemes);}
                                    catch(IOException e){toast("Zu viele eigene Designs. Vorhandene Designs verwalten oder Übernahme abwählen.");return;}
                                }
                                tv.applyConfiguration(tvConfig);
                                soundbar.disconnect();
                                if(profileConnection!=null){
                                    if(!soundbar.restoreConfiguration(profileConnection,false))
                                        throw new IOException("Profilspeicher");
                                }else if (!soundbar.saveConnectionAddress(soundIp)) throw new IOException("Soundbar-IP");
                                shortcuts.replaceEntries(streams);
                                SharedPreferences.Editor edit=options.edit().putBoolean("auto_connect",auto);
                                if(presentation!=null)edit.putString(AppPresentation.PREF,presentation.toJson().toString());
                                if(hdmi!=null)HdmiSettings.restore(edit,hdmi,restoreImages.isChecked());
                                if(personal!=null){
                                    if(personal.haptics!=null)edit.putBoolean("lab_haptics",personal.haptics);
                                    if(personal.hdmiMask!=null)
                                        HdmiInputProfile.restoreMask(tvConfig.deviceSettingsKey(),tv.savedDeviceKey(),personal.hdmiMask,
                                            (key,value)->edit.putBoolean(key,value));
                                    if(personal.aliases!=null)voiceAliases.write(personal.aliases);
                                    SharedPreferences.Editor updateEdit=updatePrefs.edit();
                                    if(personal.autoUpdates!=null)updateEdit.putBoolean("auto_check",personal.autoUpdates);
                                    if(personal.skippedLabVersion!=null)updateEdit.putString("skipped_all",personal.skippedLabVersion);
                                    if(!updateEdit.commit())throw new IOException("Update-Einstellungen");
                                }
                                if(!edit.commit())throw new IOException("Konfiguration nicht gespeichert");
                                if(importedThemes!=null&&restoreThemes.isChecked())themes.restoreImported(importedThemes);
                                if(localProfiles!=null&&restoreProfiles.isChecked()&&!soundbar.restoreLocalProfiles(localProfiles))
                                    throw new IOException("Lokale Klangprofile");
                                if (themeId != null) themes.select(themeId);
                                changed.run();
                            } catch (Exception ex) {
                                toast("Import konnte nicht vollständig gespeichert werden. Einstellungen prüfen.");
                                return;
                            }
                            // Independent of the persistent auto-connect-on-start preference.
                            ImportConnectionPlan plan = ImportConnectionPlan.create(connectNow.isChecked(),
                                tv.paired(), tv.lastAddress(), soundbar.getIp().getValue());
                            if(profileConnection!=null&&profileConnection.getSource()==ProfileSource.NAS&&
                                connectNow.isChecked()&&(Build.VERSION.SDK_INT<37||
                                activity.checkSelfPermission(LOCAL_NETWORK)==PackageManager.PERMISSION_GRANTED))
                                soundbar.refreshProfiles();
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
            try { tv.startPassive(plan.tvAddress); tvStarted = true; }
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
