package de.tonmaschine.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.compose.ui.platform.LocalContext
import de.tonmaschine.android.storage.BackupFormat
import de.tonmaschine.android.storage.ConfigurationBackup
import de.tonmaschine.android.storage.ConfigurationBackupCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import de.tonmaschine.android.storage.NasConnection
import de.tonmaschine.android.storage.ProfileSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tonmaschine.android.model.*
import kotlinx.coroutines.delay

private val bg = Color(0xFF171B23)
private val panel = Color(0xFF232936)
private val blue = Color(0xFF76A9FF)
private val muted = Color(0xFFA7B4C8)
private val foreground = Color(0xFFF0F4FA)
private val powerGreen = Color(0xFF42D792)
private val powerRed = Color(0xFFEB5353)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { if (it) vm.connect() }
            val connect = {
                if (Build.VERSION.SDK_INT >= 37 &&
                    checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
                } else vm.connect()
            }
            LaunchedEffect(Unit) { if (Catalog.validIp(vm.ip.value)) connect() }
            Tonmaschine(vm, connect)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tonmaschine(vm: MainViewModel, connect: () -> Unit, section: Int = 0,
                connectionSettings: (() -> Unit)? = null) {
    val state by vm.device.collectAsState()
    val ip by vm.ip.collectAsState()
    val message by vm.message.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val selected by vm.selectedProfile.collectAsState()
    val profileSource by vm.source.collectAsState()
    val nasConfig by vm.nasConnection.collectAsState()
    val nasOnline by vm.nasOnline.collectAsState()
    val nasStatus by vm.nasStatus.collectAsState()
    val profileBusy by vm.profileBusy.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupStatus by remember { mutableStateOf("") }
    var exportPassword by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<BackupFormat?>(null) }
    var pendingImport by remember { mutableStateOf<ConfigurationBackup?>(null) }

    fun writeBackup(uri: Uri, format: BackupFormat) {
        scope.launch {
            try {
                val text = ConfigurationBackupCodec.encode(
                    vm.configurationBackup(), format, exportPassword)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Datei kann nicht geöffnet werden")
                }
                backupStatus = "Konfiguration als ${format.name} gesichert."
            } catch (_: Exception) {
                backupStatus = "Export fehlgeschlagen. Speicherort und Einstellungen prüfen."
            }
        }
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeBackup(uri, BackupFormat.JSON) }
    val txtExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) writeBackup(uri, BackupFormat.TXT) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > ConfigurationBackupCodec.MAX_BYTES)
                                throw IllegalArgumentException("Datei zu groß")
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: throw IllegalStateException("Datei kann nicht geöffnet werden")
                }
                pendingImport = ConfigurationBackupCodec.decode(text)
                backupStatus = ""
            } catch (_: Exception) {
                backupStatus = "Import fehlgeschlagen: keine gültige Tonmaschine-Sicherung."
            }
        }
    }
    fun requestExport(format: BackupFormat) {
        if (exportPassword && nasConfig.password.isNotEmpty()) {
            pendingExport = format
        } else {
            if (format == BackupFormat.JSON)
                jsonExportLauncher.launch("Tonmaschine-Konfiguration.json")
            else txtExportLauncher.launch("Tonmaschine-Konfiguration.txt")
        }
    }

    var settings by remember { mutableStateOf(false) }
    var profileDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var overwriteTarget by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<String?>(null) }
    var transferTarget by remember { mutableStateOf<String?>(null) }
    var transferRename by remember { mutableStateOf(false) }
    var transferName by remember { mutableStateOf("") }
    var descending by remember { mutableStateOf(false) }
    var nasPath by remember(nasConfig.folder) { mutableStateOf(nasConfig.folder) }
    var nasUser by remember(nasConfig.user) { mutableStateOf(nasConfig.user) }
    var nasDomain by remember(nasConfig.domain) { mutableStateOf(nasConfig.domain) }
    var nasPassword by remember(nasConfig.password) { mutableStateOf(nasConfig.password) }
    val active = state.connection == Connection.CONNECTED
    val controls = active && state.powerOn != false
    val embedded = section != 0
    val title = when (section) {
        1 -> "Klangsteuerung"
        2 -> "Klangprofile"
        3 -> "Feinabstimmung"
        else -> "TONMASCHINE"
    }
    LaunchedEffect(state.powerOn) {
        if (state.powerOn == false && !embedded) settings = false
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = blue, onPrimary = Color(0xFF101B29),
        background = bg, onBackground = foreground,
        surface = panel, onSurface = foreground,
        onSurfaceVariant = muted, outline = Color(0xFF71819D),
        surfaceVariant = Color(0xFF303746),
        secondary = blue, onSecondary = Color(0xFF101B29)
    )) {
        // A plain Column with a background does not provide a Material content color.
        // Surface propagates a readable light-on-dark LocalContentColor to plain Texts
        // (headings, switch labels, selected values, profile names, dialogs).
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = bg,
            contentColor = foreground
        ) {
            // Only confirmed standby shows the red wake button.
            // Lost Wi-Fi connectivity is not the same as "powered off".
            if (!embedded && active && state.powerOn == false) {
                StandbyPowerButton(onWake = { vm.setPower(true) })
            } else {
            Column(if (embedded) Modifier.fillMaxSize()
                else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("LG S95QR  ·  " + when (state.connection) {
                        Connection.CONNECTED -> "Verbunden"
                        Connection.CONNECTING -> "Verbinde …"
                        Connection.DISCONNECTED -> "Offline"
                    }, color = muted)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (embedded && section != 2 && connectionSettings != null) connectionSettings()
                            else settings = !settings
                        },
                        modifier = Modifier.size(48.dp)
                            .background(panel, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = if (embedded && section != 2) "Verbindungen öffnen"
                                else if (settings) "Einstellungen schließen" else "Einstellungen",
                            tint = foreground,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    IconButton(
                        onClick = { state.powerOn?.let { vm.setPower(!it) } },
                        enabled = active && state.powerOn != null,
                        modifier = Modifier.size(48.dp)
                            .background(when {
                                !active || state.powerOn == null -> panel
                                state.powerOn == false -> powerRed
                                else -> powerGreen
                            }, RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PowerSettingsNew,
                            contentDescription = when (state.powerOn) {
                                true -> "Soundbar ausschalten"
                                false -> "Soundbar einschalten"
                                null -> "Powerstatus unbekannt"
                            },
                            tint = if (active && state.powerOn != null) bg else muted,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            }
            if (settings) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Panel("Verbindung") {
                        if (connectionSettings == null) {
                            OutlinedTextField(ip, vm::updateIp, modifier = Modifier.fillMaxWidth(),
                                label = { Text("IPv4-Adresse der Soundbar") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = connect) { Text("Verbinden") }
                                OutlinedButton(onClick = vm::refresh, enabled = active) { Text("Aktualisieren") }
                            }
                        } else {
                            Button(onClick = connectionSettings) { Text("Verbindungen öffnen") }
                            OutlinedButton(onClick = vm::refresh, enabled = active) { Text("Aktualisieren") }
                        }
                        Text("Direkte WLAN-Verbindung auf TCP 9741.", color = muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Panel("Speicherort für Klangprofile") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = profileSource == ProfileSource.LOCAL,
                                onClick = { vm.selectProfileSource(ProfileSource.LOCAL) },
                                enabled = !profileBusy,
                                label = { Text("Lokal") }
                            )
                            FilterChip(
                                selected = profileSource == ProfileSource.NAS,
                                onClick = { vm.selectProfileSource(ProfileSource.NAS) },
                                enabled = !profileBusy,
                                label = { Text("NAS / Windows") }
                            )
                        }
                        Text("Bei NAS verwenden Windows und Android dieselbe Tonmaschine_Profile.json. Lokale Profile werden nicht automatisch kopiert.", color = muted)
                        OutlinedTextField(nasPath, { nasPath = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("NAS-Freigabe / Ordner") },
                            placeholder = { Text("smb://nas/Share/Profile") }, singleLine = true)
                        OutlinedTextField(nasUser, { nasUser = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("SMB-Benutzername (leer = anonym)") }, singleLine = true)
                        OutlinedTextField(nasPassword, { nasPassword = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("SMB-Passwort") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation())
                        OutlinedTextField(nasDomain, { nasDomain = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Domäne / Arbeitsgruppe (optional)") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                vm.saveNasConnection(NasConnection(nasPath, nasUser, nasDomain, nasPassword))
                            }, enabled = !profileBusy && nasPath.isNotBlank()) { Text("NAS verbinden") }
                            OutlinedButton(onClick = vm::refreshProfiles,
                                enabled = !profileBusy && profileSource == ProfileSource.NAS) { Text("Aktualisieren") }
                        }
                        Text(nasStatus, color = if (nasOnline) Color(0xFF9DE4BC) else muted)
                        if (!nasOnline && profileSource == ProfileSource.NAS) {
                            Text("Bei Verbindungsfehlern werden nur zwischengespeicherte NAS-Profile angezeigt. Speichern und Löschen sind gesperrt.", color = muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Panel("Konfiguration sichern und wiederherstellen") {
                        Text("Sichert Soundbar-IP, NAS-Pfad und eingegebene NAS-Zugangsdaten. " +
                            "Klangprofile selbst sind nicht enthalten.", color = muted)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = exportPassword,
                                onCheckedChange = { exportPassword = it }
                            )
                            Text("NAS-Passwort mit exportieren (unverschlüsselt)",
                                modifier = Modifier.weight(1f))
                        }
                        Text("Achtung: TXT und JSON sind unverschlüsselt. Eine Datei mit " +
                            "NAS-Passwort sicher aufbewahren und nicht weitergeben.", color = muted)
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { requestExport(BackupFormat.JSON) },
                                modifier = Modifier.weight(1f)
                            ) { Text("JSON exportieren") }
                            OutlinedButton(
                                onClick = { requestExport(BackupFormat.TXT) },
                                modifier = Modifier.weight(1f)
                            ) { Text("TXT exportieren") }
                        }
                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf(
                                    "application/json", "text/plain", "application/octet-stream"))
                            },
                            enabled = !profileBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Konfiguration importieren") }
                        if (backupStatus.isNotBlank()) Text(backupStatus, color = muted)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (section == 0 || section == 1) item {
                        Panel("Gesamtlautstärke") {
                            val actual = state.volume
                            val volumeReady = controls && actual != null
                            var draft by remember(actual) { mutableFloatStateOf((actual ?: 0).toFloat()) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { actual?.let { vm.setVolume((it - 1).coerceAtLeast(0)) } }, enabled = volumeReady) { Text("−") }
                                Slider(draft, onValueChange = { draft = it },
                                    onValueChangeFinished = { if (volumeReady) vm.setVolume(draft.toInt()) },
                                    modifier = Modifier.weight(1f).reserveSliderGesture(volumeReady), valueRange = 0f..100f, enabled = volumeReady)
                                TextButton(onClick = { actual?.let { vm.setVolume((it + 1).coerceAtMost(100)) } }, enabled = volumeReady) { Text("+") }
                                Text(actual?.let { "${draft.toInt()}" } ?: "–", modifier = Modifier.width(35.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (section == 0 || section == 1) item {
                        Panel("Eingang und Audioformat") {
                            var inputExpanded by remember { mutableStateOf(false) }
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Zuspieler", color = muted, style = MaterialTheme.typography.bodyMedium)
                                    Box {
                                        OutlinedButton(onClick = { inputExpanded = true }, enabled = controls && state.canSelectInput) {
                                            Text(Catalog.inputs[state.currentInput] ?: "Nicht erkannt")
                                        }
                                        DropdownMenu(expanded = inputExpanded, onDismissRequest = { inputExpanded = false }) {
                                            state.selectableInputs.forEach { code ->
                                                DropdownMenuItem(text = { Text(Catalog.inputs[code].orEmpty()) },
                                                    onClick = { inputExpanded = false; vm.chooseSource(code) })
                                            }
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Gemeldetes Format", color = muted,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(state.audioFormat, fontWeight = FontWeight.Bold, color = blue)
                                }
                            }
                        }
                    }
                    if (section == 0 || section == 1) item {
                        Panel("Klangoptionen") {
                            ToggleRow("Night Time", state.nightTime, controls) { vm.setOption("b_night_time", it) }
                            ToggleRow("DRC", state.drc, controls) { vm.setOption("b_drc", it) }
                            ToggleRow("Surround", state.surround, controls) { vm.setOption("b_rear", it) }
                        }
                    }
                    if (section == 0 || section == 1) item {
                        Panel("Klangmodus") {
                            val modes = state.selectableModes.entries.toList()
                            modes.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { (label, id) ->
                                        val checked = state.currentMode == id
                                        if (checked) Button(onClick = { vm.chooseMode(id) }, enabled = controls,
                                            modifier = Modifier.weight(1f).height(if (embedded) 48.dp else 64.dp)) { Text(label, maxLines = 2) }
                                        else OutlinedButton(onClick = { vm.chooseMode(id) }, enabled = controls,
                                            modifier = Modifier.weight(1f).height(if (embedded) 48.dp else 64.dp)) { Text(label, maxLines = 2) }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            if (!embedded) Text("Bass ${state.bass ?: "–"}  ·  Höhen ${state.treble ?: "–"}", color = muted)
                        }
                    }
                    if (section == 0 || section == 2) item {
                        Panel("Klangprofile") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${profiles.size} Profile · " + (if (profileSource == ProfileSource.NAS) "NAS" else "Lokal"),
                                    modifier = Modifier.weight(1f))
                                if (profileSource == ProfileSource.NAS) TextButton(
                                    onClick = vm::refreshProfiles, enabled = !profileBusy
                                ) { Text("↻") }
                                TextButton(onClick = { profileDialog = true }) { Text("Verwalten") }
                            }
                            if (profileSource == ProfileSource.NAS) {
                                Text(nasStatus, color = if (nasOnline) Color(0xFF9DE4BC) else muted)
                                if (!nasOnline) Text("Nur Lesekopie – Änderungen nicht möglich", color = muted)
                            }
                            if (section == 2) {
                                TextButton(onClick = { descending = !descending }) {
                                    Text(if (descending) "Sortierung: Z–A" else "Sortierung: A–Z")
                                }
                                Button(onClick = { name = ""; profileDialog = true },
                                    enabled = controls && !profileBusy && vm.canEditProfiles,
                                    modifier = Modifier.fillMaxWidth()) { Text("Aktuelle Einstellungen speichern") }
                                if (profiles.isEmpty()) Text("Noch keine Profile gespeichert.", color = muted)
                                Text("Tippen: anwenden · lange drücken: verwalten", color = muted,
                                    style = MaterialTheme.typography.bodySmall)
                                val sorted = profiles.keys.sortedBy { it.lowercase(java.util.Locale.ROOT) }
                                (if (descending) sorted.reversed() else sorted).forEach { profileName ->
                                    Surface(color = if (selected == profileName) Color(0xFF233D32) else Color(0xFF303746),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().combinedClickable(
                                            onClick = { if (controls) vm.applyProfile(profileName) },
                                            onLongClick = { actionTarget = profileName }
                                        )) {
                                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(profileName, fontWeight = FontWeight.SemiBold)
                                                if (selected == profileName) Text("Zuletzt gewählt", color = muted,
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                            TextButton(onClick = { actionTarget = profileName }) { Text("⋮") }
                                        }
                                    }
                                }
                            } else profiles.keys.sorted().forEach { profileName ->
                                OutlinedButton(onClick = { vm.applyProfile(profileName) }, enabled = controls,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text((if (selected == profileName) "✓  " else "") + profileName)
                                }
                            }
                        }
                    }
                    if (section == 0 || section == 3) item {
                        Panel("Lautsprecherpegel") {
                            if (section != 3) TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "Pegel ausblenden" else "Alle 7 Pegel anzeigen")
                            }
                            if (section == 3 || expanded) Catalog.levels.forEach { spec ->
                                val value = state.levels[spec.field]
                                val levelReady = controls && value != null
                                var draft by remember(spec.field, value) { mutableFloatStateOf((value ?: 0).toFloat()) }
                                Text("${spec.name}: ${value?.let { "%+d dB".format(draft.toInt()) } ?: "–"}")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { value?.let { vm.setLevel(spec, (it - 1).coerceAtLeast(spec.min)) } },
                                        enabled = levelReady && value != spec.min) { Text("−") }
                                    Slider(draft, onValueChange = { draft = it },
                                        onValueChangeFinished = { if (levelReady) vm.setLevel(spec, draft.toInt()) },
                                        modifier = Modifier.weight(1f).reserveSliderGesture(levelReady),
                                        enabled = levelReady, valueRange = spec.min.toFloat()..spec.max.toFloat(),
                                        steps = (spec.max - spec.min - 1).coerceAtLeast(0))
                                    TextButton(onClick = { value?.let { vm.setLevel(spec, (it + 1).coerceAtMost(spec.max)) } },
                                        enabled = levelReady && value != spec.max) { Text("+") }
                                }
                            }
                        }
                    }
                    if (section == 3) item {
                        Panel("Equalizer") {
                            Text("Bass: ${state.bass ?: "–"}")
                            Text("Höhen: ${state.treble ?: "–"}")
                            Text("Gemeldete Werte · direkte Änderung noch nicht implementiert.", color = muted)
                        }
                    }
                    if (section == 3) item {
                        Panel("Erweiterte Einstellungen") {
                            Text("Neural:X und IMAX Enhanced", fontWeight = FontWeight.SemiBold)
                            Text("Noch keine verifizierte Android-Steuerung verfügbar.", color = muted)
                        }
                    }
                }
            }
            Text(message, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                color = if (state.error != null) Color(0xFFFFB4AB) else muted,
                style = MaterialTheme.typography.bodySmall)
        }
        if (pendingExport != null) {
            val format = pendingExport!!
            AlertDialog(
                onDismissRequest = { pendingExport = null },
                title = { Text("NAS-Passwort unverschlüsselt sichern?") },
                text = { Text("Diese ${format.name}-Datei enthält dein NAS-Passwort " +
                    "im Klartext. Wer die Datei erhält, kann die Zugangsdaten lesen. " +
                    "Nur an einem geschützten Ort speichern.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingExport = null
                        if (format == BackupFormat.JSON)
                            jsonExportLauncher.launch("Tonmaschine-Konfiguration.json")
                        else txtExportLauncher.launch("Tonmaschine-Konfiguration.txt")
                    }) { Text("Trotzdem exportieren") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingExport = null }) { Text("Abbrechen") }
                }
            )
        }
        if (pendingImport != null) {
            val backup = pendingImport!!
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("Konfiguration wiederherstellen?") },
                text = {
                    Text(
                        "Vorhandene IP- und NAS-Einstellungen werden ersetzt.\n" +
                        "Soundbar-IP: ${backup.soundbarIp.ifBlank { "nicht eingetragen" }}\n" +
                        "NAS-Pfad: ${backup.nas.folder.ifBlank { "nicht eingetragen" }}\n" +
                        "NAS-Benutzer: ${backup.nas.user.ifBlank { "nicht eingetragen" }}\n" +
                        "NAS-Passwort enthalten: ${if (backup.nas.password.isNotEmpty()) "Ja" else "Nein"}\n" +
                        "Speicherort: ${if (backup.source == ProfileSource.NAS) "NAS" else "Lokal"}\n\n" +
                        "Das NAS-Passwort wird danach wieder verschlüsselt gespeichert. " +
                        "Lokale Klangprofile werden nicht gelöscht."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingImport = null
                        if (vm.restoreConfiguration(backup)) {
                            backupStatus = "Konfiguration importiert."
                            if (Catalog.validIp(backup.soundbarIp)) connect()
                        } else {
                            backupStatus = "Import fehlgeschlagen – Einstellungen prüfen."
                        }
                    }) { Text("Wiederherstellen") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImport = null }) { Text("Abbrechen") }
                }
            )
        }
        if (actionTarget != null) {
            val target = actionTarget!!
            AlertDialog(onDismissRequest = { actionTarget = null },
                title = { Text(target) },
                text = {
                    Column {
                        TextButton(onClick = { actionTarget = null; vm.applyProfile(target) },
                            enabled = controls) { Text("Anwenden") }
                        TextButton(onClick = {
                            actionTarget = null; transferTarget = target; transferName = target; transferRename = true
                        }, enabled = !profileBusy && vm.canEditProfiles) { Text("Umbenennen") }
                        TextButton(onClick = {
                            actionTarget = null; transferTarget = target; transferName = "$target – Kopie"; transferRename = false
                        }, enabled = !profileBusy && vm.canEditProfiles) { Text("Kopieren") }
                        TextButton(onClick = { actionTarget = null; overwriteTarget = target },
                            enabled = controls && !profileBusy && vm.canEditProfiles) { Text("Mit aktuellen Werten überschreiben") }
                        TextButton(onClick = { actionTarget = null; deleteTarget = target },
                            enabled = !profileBusy && vm.canEditProfiles) { Text("Löschen") }
                    }
                }, confirmButton = { TextButton(onClick = { actionTarget = null }) { Text("Schließen") } })
        }
        if (transferTarget != null) {
            val target = transferTarget!!
            val clean = transferName.trim()
            val validName = clean.isNotBlank() && clean !in profiles && clean.length <= 80
            AlertDialog(onDismissRequest = { transferTarget = null },
                title = { Text(if (transferRename) "Profil umbenennen" else "Profil kopieren") },
                text = {
                    OutlinedTextField(transferName, { transferName = it }, label = { Text("Neuer Name") },
                        singleLine = true, isError = !validName)
                },
                confirmButton = {
                    TextButton(onClick = { vm.transferProfile(target, clean, transferRename); transferTarget = null },
                        enabled = validName && !profileBusy && vm.canEditProfiles) { Text("Speichern") }
                },
                dismissButton = { TextButton(onClick = { transferTarget = null }) { Text("Abbrechen") } })
        }
        if (profileDialog) {
            AlertDialog(onDismissRequest = { profileDialog = false },
                title = { Text("Klangprofile verwalten") },
                text = {
                    Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                        if (profileSource == ProfileSource.NAS) Text(nasStatus, color = muted)
                        OutlinedTextField(name, { name = it }, label = { Text("Name / neuer Name") },
                            singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val trimmed = name.trim()
                            if (trimmed in profiles) overwriteTarget = trimmed
                            else vm.saveProfile(trimmed)
                        }, enabled = controls && name.isNotBlank() && !profileBusy && vm.canEditProfiles) {
                            Text("Aktuelle Werte speichern")
                        }
                        HorizontalDivider()
                        profiles.keys.sorted().forEach { item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(item, Modifier.weight(1f))
                                TextButton(onClick = { name = item }) { Text("Bearbeiten") }
                                TextButton(onClick = { deleteTarget = item }, enabled = !profileBusy && vm.canEditProfiles) { Text("Löschen") }
                            }
                        }
                    }
                }, confirmButton = { TextButton(onClick = { profileDialog = false }) { Text("Schließen") } })
        }
        if (overwriteTarget != null) {
            val target = overwriteTarget!!
            AlertDialog(
                onDismissRequest = { overwriteTarget = null },
                title = { Text("Profil überschreiben?") },
                text = { Text("Das Profil „${target}“ wird mit den momentan gemeldeten Soundbar-Einstellungen ersetzt. Bestehende Profile bleiben ansonsten unverändert.") },
                confirmButton = {
                    TextButton(onClick = { vm.saveProfile(target, overwrite = true); overwriteTarget = null },
                        enabled = controls && !profileBusy && vm.canEditProfiles) { Text("Überschreiben") }
                },
                dismissButton = { TextButton(onClick = { overwriteTarget = null }) { Text("Abbrechen") } }
            )
        }
        if (deleteTarget != null) {
            val target = deleteTarget!!
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Profil löschen?") },
                text = { Text("„${target}“ wird aus " + (if (profileSource == ProfileSource.NAS) "der gemeinsamen NAS-Datei" else "dem lokalen Speicher") + " entfernt.") },
                confirmButton = {
                    TextButton(onClick = { vm.deleteProfile(target); deleteTarget = null },
                        enabled = !profileBusy && vm.canEditProfiles) { Text("Endgültig löschen") }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") } }
            )
        }
        }
        }
    }
}
/** Reserve the slider touch before the Android parent handles horizontal motion. */
@Composable
private fun Modifier.reserveSliderGesture(enabled: Boolean): Modifier {
    val view = LocalView.current
    return if (!enabled) this else pointerInput(view) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            view.parent?.requestDisallowInterceptTouchEvent(true)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
            } finally {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}

@Composable
private fun StandbyPowerButton(onWake: () -> Unit) {
    var justPressed by remember { mutableStateOf(false) }
    LaunchedEffect(justPressed) {
        if (justPressed) {
            delay(700)
            justPressed = false
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = {
                if (!justPressed) {
                    justPressed = true
                    onWake()
                }
            },
            enabled = !justPressed,
            modifier = Modifier.size(172.dp)
                .background(if (justPressed) powerGreen else powerRed, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = "Soundbar einschalten",
                modifier = Modifier.size(92.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}
@Composable
private fun ToggleRow(label: String, value: Boolean?, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = value ?: false, onCheckedChange = onChanged, enabled = enabled && value != null)
    }
}
