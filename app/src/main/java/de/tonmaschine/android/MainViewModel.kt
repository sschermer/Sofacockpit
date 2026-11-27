package de.tonmaschine.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.tonmaschine.android.model.*
import de.tonmaschine.android.network.SoundbarClient
import de.tonmaschine.android.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProfileStore(application)
    private val nasSettings = NasSettingsStore(application)
    private val _device = MutableStateFlow(DeviceState())
    val device = _device.asStateFlow()
    private val _message = MutableStateFlow("Nicht verbunden")
    val message = _message.asStateFlow()
    private val _ip = MutableStateFlow(store.ip())
    val ip = _ip.asStateFlow()
    private val _profiles = MutableStateFlow<Map<String, SoundProfile>>(emptyMap())
    val profiles = _profiles.asStateFlow()
    private val _selectedProfile = MutableStateFlow<String?>(null)
    val selectedProfile = _selectedProfile.asStateFlow()
    private val _source = MutableStateFlow(nasSettings.profileSource())
    val source = _source.asStateFlow()
    private val _nasConnection = MutableStateFlow(NasConnection())
    val nasConnection = _nasConnection.asStateFlow()
    private val _nasOnline = MutableStateFlow(false)
    val nasOnline = _nasOnline.asStateFlow()
    private val _nasStatus = MutableStateFlow("NAS nicht konfiguriert")
    val nasStatus = _nasStatus.asStateFlow()
    private val _profileBusy = MutableStateFlow(false)
    val profileBusy = _profileBusy.asStateFlow()
    private var client: SoundbarClient? = null
    private var generation = 0
    private var lastConfirmedPower: Boolean? = null
    private var connectionRefreshJob: Job? = null
    private val resumeIntent = SoundbarResumeIntent()
    private var reportSequence = 0L
    private val reportRevisions = mutableMapOf<String, Long>()
    private val refreshReports = listOf("SPK_LIST_VIEW_INFO", "FUNC_VIEW_INFO", "EQ_VIEW_INFO", "SETTING_VIEW_INFO")

    init {
        try { _nasConnection.value = nasSettings.loadConnection() }
        catch (_: Exception) { _nasStatus.value = "NAS-Zugangsdaten konnten nicht entschlüsselt werden. Bitte erneut eingeben." }
        if (_source.value == ProfileSource.NAS) {
            try { _profiles.value = nasSettings.loadCache() }
            catch (_: Exception) { _nasStatus.value = "NAS-Lesekopie beschädigt" }
            if (_nasConnection.value.folder.isNotBlank()) refreshProfiles()
        } else {
            try { _profiles.value = store.load() }
            catch (e: Exception) { _message.value = "Lokale Profildaten beschädigt: ${e.message}" }
        }
    }

    fun updateIp(text: String) {
        val address = text.trim()
        if (address != _ip.value && client != null) disconnect()
        _ip.value = address
    }

    /** Central connection management; no network action on saving/importing an IP. */
    fun saveConnectionAddress(text: String): Boolean {
        val address = text.trim()
        if (address.isNotEmpty() && !Catalog.validIp(address)) {
            _message.value = "Bitte eine gültige IPv4-Adresse eingeben"
            return false
        }
        return try {
            store.saveIp(address)
            if (address != _ip.value) disconnect()
            _ip.value = address
            true
        } catch (_: Exception) {
            _message.value = "Soundbar-IP konnte nicht gespeichert werden"
            false
        }
    }

    fun disconnect() {
        resumeIntent.cancel()
        generation++
        lastConfirmedPower = null
        client?.stop()
        client = null
        _device.value = DeviceState()
        _message.value = "Nicht verbunden"
    }

    /** Backup uses the already decrypted NAS settings in memory, never logging the password. */
    fun configurationBackup(): ConfigurationBackup = ConfigurationBackup(
        soundbarIp = _ip.value.trim(),
        nas = _nasConnection.value,
        source = _source.value
    )

    /** Only private local profiles; never read the displayed NAS cache for backup. */
    fun exportLocalProfiles(): String = ProfileCodec.encode(store.load())

    /** Restore the private local file only. Never call a shared NAS write path. */
    fun restoreLocalProfiles(encoded: String): Boolean {
        if (_profileBusy.value) return false
        return try {
            val restored = ProfileCodec.decode(encoded)
            store.save(restored)
            if (_source.value == ProfileSource.LOCAL) {
                _profiles.value = restored
                _selectedProfile.value = null
            }
            true
        } catch (_: Exception) {
            _message.value = "Lokale Klangprofile konnten nicht gespeichert werden"
            false
        }
    }

    /** Restore portable settings; shared NAS profiles are retrieved from the NAS afterwards. */
    @JvmOverloads
    fun restoreConfiguration(backup: ConfigurationBackup, refreshNas: Boolean = true): Boolean {
        if (_profileBusy.value) {
            _message.value = "NAS arbeitet noch – Import später wiederholen"
            return false
        }
        return try {
            backup.validate()
            val previousNas = _nasConnection.value
            // Validate before writing anything. Passwords go to encrypted AndroidKeystore storage.
            nasSettings.saveConnection(backup.nas)
            store.saveIp(backup.soundbarIp)
            nasSettings.saveSource(backup.source)
            if (previousNas != backup.nas) nasSettings.saveCache(emptyMap())
            _nasConnection.value = backup.nas
            if (_ip.value != backup.soundbarIp) disconnect()
            _ip.value = backup.soundbarIp
            _source.value = backup.source
            _nasOnline.value = false
            _selectedProfile.value = null
            if (backup.source == ProfileSource.NAS) {
                _profiles.value = if (previousNas == backup.nas) nasSettings.loadCache() else emptyMap()
                _nasStatus.value = "NAS-Konfiguration geladen – Profile aktualisieren"
                if (refreshNas && backup.nas.folder.isNotBlank()) refreshProfiles()
            } else {
                _profiles.value = store.load()
                _nasStatus.value = "Lokale Profile ausgewählt"
            }
            _message.value = "Konfiguration wiederhergestellt"
            true
        } catch (_: Exception) {
            _message.value = "Import fehlgeschlagen – Einstellungen prüfen"
            false
        }
    }

    fun connect() { startConnection() }

    private fun startConnection(): Boolean {
        if (!Catalog.validIp(ip.value)) {
            _message.value = "Bitte eine gültige IPv4-Adresse eingeben"
            return false
        }
        try { store.saveIp(ip.value) }
        catch (e: Exception) { _message.value = "IP konnte nicht gespeichert werden: ${e.message}"; return false }
        resumeIntent.cancel()
        generation++
        val id = generation
        lastConfirmedPower = null
        client?.stop()
        _device.value = DeviceState(connection = Connection.CONNECTING)
        _message.value = "Verbindung wird hergestellt …"
        client = SoundbarClient(ip.value, scope = viewModelScope,
            onPacket = { packet ->
                viewModelScope.launch {
                    if (generation == id) {
                        val previous = _device.value
                        val next = DeviceReducer.reduce(previous, packet)
                        if (next !== previous) {
                            reportSequence++
                            reportRevisions[packet.optString("msg")] = reportSequence
                            next.powerOn?.let { lastConfirmedPower = it }
                        }
                        _device.value = next
                        if (previous.connection != Connection.CONNECTED && next.connection == Connection.CONNECTED)
                            _message.value = "Verbunden"
                    }
                }
            },
            onConnection = { online, error ->
                viewModelScope.launch {
                    if (generation == id) {
                        // An open TCP socket is not proof of a responding soundbar.
                        _device.value = DeviceState(connection = Connection.CONNECTING, error = error)
                        _message.value = if (online) "Warte auf Soundbar-Antwort …" else error?.let { "Wiederverbinden: $it" } ?: "Verbinde …"
                    }
                }
            }).also { it.start() }
        return true
    }

    fun refresh() { client?.refresh() }

    private fun confirmedStandby() = lastConfirmedPower == false || _device.value.powerOn == false

    /** A requested session can already be lost or retrying when the app is left.
     * Remember the intent, not its transient connection color. */
    fun onBackground() {
        cancelConnectionRefresh()
        resumeIntent.background(generation, ip.value, client != null, confirmedStandby())
    }

    fun onForeground(networkAllowed: Boolean) {
        if (!resumeIntent.take(generation, ip.value, client != null, confirmedStandby(), networkAllowed)) return
        // Existing refresh probes real replies and reconnects once if necessary. No power command.
        refreshConnection(java.util.function.Consumer { })
    }

    /** Pull-to-refresh: only a previously requested client, never a new connection
     * to a merely stored IP. Read real responses and expedite at most one retry.
     */
    fun cancelConnectionRefresh() {
        connectionRefreshJob?.cancel()
        connectionRefreshJob = null
    }

    fun refreshConnection(done: java.util.function.Consumer<String>) {
        cancelConnectionRefresh()
        if (client == null) { done.accept("Soundbar: keine gewünschte Verbindung"); return }
        if (lastConfirmedPower == false || _device.value.powerOn == false) {
            done.accept("Soundbar: Standby, nicht aufgeweckt"); return
        }
        connectionRefreshJob = viewModelScope.launch {
            var expectedGeneration = generation
            var baseline = reportSequence
            var restarted = false
            fun changedIntent() = expectedGeneration != generation || client == null
            fun standby() = lastConfirmedPower == false || _device.value.powerOn == false
            fun receivedAll() = refreshReports.all { (reportRevisions[it] ?: 0L) > baseline }
            fun responding() = reportSequence > baseline && _device.value.connection == Connection.CONNECTED

            if (_device.value.connection == Connection.CONNECTING && _device.value.error != null) {
                // Existing retry backoff can reach 30 seconds. Pulling explicitly expedites it.
                if (!startConnection()) { done.accept("Soundbar: Neuverbindung nicht möglich"); return@launch }
                expectedGeneration = generation
                restarted = true
            } else if (_device.value.connection == Connection.CONNECTED) {
                client?.refresh()
            }
            // Give the existing connection a chance to answer before replacing it.
            repeat(if (restarted) 40 else 24) {
                delay(250)
                if (changedIntent()) { done.accept("Soundbar: Verbindung geändert oder getrennt"); return@launch }
                if (standby()) { done.accept("Soundbar: Standby, nicht aufgeweckt"); return@launch }
                if (receivedAll() && responding()) {
                    done.accept(if (restarted) "Soundbar: wieder verbunden, Werte aktualisiert" else "Soundbar: Werte aktualisiert")
                    return@launch
                }
            }
            if (responding()) {
                done.accept(if (restarted) "Soundbar: wieder verbunden" else "Soundbar: Antwort erhalten")
                return@launch
            }
            if (!restarted && !changedIntent() && !standby()) {
                baseline = reportSequence
                if (!startConnection()) { done.accept("Soundbar: Neuverbindung nicht möglich"); return@launch }
                expectedGeneration = generation
                repeat(40) {
                    delay(250)
                    if (changedIntent()) { done.accept("Soundbar: Verbindung geändert oder getrennt"); return@launch }
                    if (standby()) { done.accept("Soundbar: Standby, nicht aufgeweckt"); return@launch }
                    if (receivedAll() && responding()) { done.accept("Soundbar: wieder verbunden, Werte aktualisiert"); return@launch }
                }
                if (responding()) { done.accept("Soundbar: wieder verbunden"); return@launch }
            }
            done.accept("Soundbar: keine neue Antwort bestätigt")
        }
    }

    private fun send(packet: JSONObject, readBack: String? = null) {
        if (_device.value.connection != Connection.CONNECTED) return
        val target = client ?: return
        val id = generation
        val ok = target.send(packet)
        if (!ok) _message.value = "Befehl konnte nicht gesendet werden"
        else if (readBack != null) viewModelScope.launch {
            delay(450)
            if (generation == id && client === target && _device.value.connection == Connection.CONNECTED)
                target.send(SoundbarCommands.get(readBack))
        }
    }

    private fun changed() { _selectedProfile.value = null }
    fun setVolume(n: Int) = send(SoundbarCommands.volume(n))

    fun setPower(turnOn: Boolean) {
        if (_device.value.connection != Connection.CONNECTED) {
            _message.value = "Soundbar nicht erreichbar – keine Netzverbindung"
            return
        }
        val queued = client?.send(SoundbarCommands.power(turnOn)) ?: false
        if (!queued) {
            _message.value = "Ein-/Ausschaltbefehl konnte nicht gesendet werden"
            return
        }
        _message.value = if (turnOn) "Einschaltbefehl gesendet – warte auf Bestätigung" else
            "Ausschaltbefehl gesendet – warte auf Bestätigung"
        val requestGeneration = generation
        viewModelScope.launch {
            // Polling a sleeping device can wake it. Await unsolicited confirmation.
            delay(5500)
            if (generation == requestGeneration &&
                _device.value.connection == Connection.CONNECTED &&
                _device.value.powerOn != turnOn) {
                _message.value = "Keine Bestätigung der Soundbar – Ein-/Ausschalten am Gerät prüfen"
            }
        }
    }
    fun chooseSource(id: Int) {
        if (!_device.value.canSelectInput || id !in _device.value.selectableInputs) return
        changed()
        send(SoundbarCommands.source(id), "FUNC_VIEW_INFO")
    }
    fun chooseMode(id: Int) {
        if (id !in _device.value.selectableModes.values) return
        changed()
        send(SoundbarCommands.mode(id), "EQ_VIEW_INFO")
    }
    fun setOption(field: String, value: Boolean) {
        changed()
        send(SoundbarCommands.option(field, value), "SETTING_VIEW_INFO")
    }
    fun setLevel(spec: LevelSpec, value: Int) {
        changed()
        send(SoundbarCommands.level(spec, value), "SETTING_VIEW_INFO")
    }

    fun selectProfileSource(next: ProfileSource) {
        if (_profileBusy.value || next == _source.value) return
        try { nasSettings.saveSource(next) }
        catch (e: Exception) { _nasStatus.value = "Speicherort konnte nicht gespeichert werden"; return }
        _source.value = next
        _selectedProfile.value = null
        _nasOnline.value = false
        if (next == ProfileSource.LOCAL) {
            try {
                _profiles.value = store.load()
                _nasStatus.value = "Lokale Profile ausgewählt"
            } catch (e: Exception) {
                _profiles.value = emptyMap()
                _nasStatus.value = "Lokale Profildatei beschädigt"
            }
        } else {
            try { _profiles.value = nasSettings.loadCache() }
            catch (_: Exception) { _profiles.value = emptyMap() }
            refreshProfiles()
        }
    }

    fun saveNasConnection(info: NasConnection) {
        if (_profileBusy.value) return
        try {
            nasSettings.saveConnection(info)
            _nasConnection.value = info.copy(folder = info.folder.trim(), user = info.user.trim(), domain = info.domain.trim())
            // Selection changes only after credentials were successfully saved.
            nasSettings.saveSource(ProfileSource.NAS)
            _source.value = ProfileSource.NAS
            _nasOnline.value = false
            _selectedProfile.value = null
            try { _profiles.value = nasSettings.loadCache() }
            catch (_: Exception) { _profiles.value = emptyMap() }
            refreshProfiles()
        } catch (e: Exception) {
            _nasStatus.value = "NAS-Einstellungen ungültig: ${e.message ?: "Bitte Pfad prüfen"}"
        }
    }

    private fun remoteError(e: Exception): String {
        val password = _nasConnection.value.password
        val description = e.message?.take(180).orEmpty()
        return if (password.isNotEmpty()) description.replace(password, "***") else description
    }

    fun refreshProfiles() {
        if (_profileBusy.value || _source.value != ProfileSource.NAS) return
        val connection = _nasConnection.value
        if (connection.folder.isBlank()) {
            _nasOnline.value = false
            _nasStatus.value = "Bitte NAS-Freigabe unter IP / Einstellungen einrichten"
            return
        }
        _profileBusy.value = true
        _nasStatus.value = "NAS wird geprüft …"
        viewModelScope.launch {
            try {
                val remote = withContext(Dispatchers.IO) {
                    SmbProfileFiles(connection).use { SharedProfileRepository(it).load() }
                }
                if (_source.value == ProfileSource.NAS && _nasConnection.value == connection) {
                    _profiles.value = remote
                    _nasOnline.value = true
                    _nasStatus.value = "NAS verbunden · ${remote.size} Profile"
                    try { withContext(Dispatchers.IO) { nasSettings.saveCache(remote) } }
                    catch (_: Exception) { _nasStatus.value += " · Cache nicht aktualisiert" }
                }
            } catch (e: Exception) {
                if (_source.value == ProfileSource.NAS) {
                    _nasOnline.value = false
                    _nasStatus.value = "NAS nicht erreichbar: ${remoteError(e)}"
                    // Offline: cached profiles remain viewable/applicable, but never writable.
                    try { _profiles.value = nasSettings.loadCache() }
                    catch (_: Exception) { _profiles.value = emptyMap() }
                }
            } finally { _profileBusy.value = false }
        }
    }

    val canEditProfiles: Boolean
        get() = _source.value == ProfileSource.LOCAL || _nasOnline.value

    private fun currentProfile(name: String): SoundProfile? {
        val state = _device.value
        if (name.isBlank() || state.connection != Connection.CONNECTED ||
            state.currentMode !in Catalog.modes.values ||
            Catalog.levels.any { it.field !in state.levels } ||
            state.drc == null || state.nightTime == null || state.surround == null) {
            _message.value = "Profilname oder Gerätezustand unvollständig"
            return null
        }
        return SoundProfile(state.currentMode!!, state.levels, state.drc, state.nightTime, state.surround)
    }

    fun saveProfile(name: String, overwrite: Boolean = false) {
        val clean = name.trim()
        val profile = currentProfile(clean) ?: return
        if (_profileBusy.value || !canEditProfiles) {
            _message.value = "NAS offline: Speichern nicht möglich"; return
        }
        if (clean in _profiles.value && !overwrite) {
            _message.value = "Vorhandenes Profil nur nach Bestätigung überschreiben"; return
        }
        if (_source.value == ProfileSource.LOCAL) {
            try {
                val updated = _profiles.value + (clean to profile)
                store.save(updated)
                _profiles.value = updated
                _selectedProfile.value = clean
                _message.value = "Profil gespeichert: $clean"
            } catch (e: Exception) { _message.value = "Speichern fehlgeschlagen: ${e.message}" }
        } else remoteUpdate(clean, profile, overwrite)
    }

    fun deleteProfile(name: String) {
        if (_profileBusy.value || !canEditProfiles) {
            _message.value = "NAS offline: Löschen nicht möglich"; return
        }
        if (_source.value == ProfileSource.LOCAL) {
            try {
                val updated = _profiles.value - name
                store.save(updated)
                _profiles.value = updated
                if (_selectedProfile.value == name) _selectedProfile.value = null
                _message.value = "Profil gelöscht: $name"
            } catch (e: Exception) { _message.value = "Löschen fehlgeschlagen: ${e.message}" }
        } else remoteUpdate(name, null)
    }

    /** Copy/rename saved data, not current live device values; NAS changes use one lock. */
    fun transferProfile(from: String, newName: String, rename: Boolean) {
        val target = newName.trim()
        if (_profileBusy.value || !canEditProfiles || target.isBlank() || target.length > 80 ||
            from !in _profiles.value || target in _profiles.value) {
            _message.value = "Profilname oder Speicherort prüfen"
            return
        }
        if (_source.value == ProfileSource.LOCAL) {
            try {
                val updated = _profiles.value.toMutableMap()
                updated[target] = updated.getValue(from)
                if (rename) updated.remove(from)
                store.save(updated)
                _profiles.value = updated
                if (rename && _selectedProfile.value == from) _selectedProfile.value = target
                _message.value = if (rename) "Profil umbenannt" else "Profil kopiert"
            } catch (e: Exception) { _message.value = "Profiländerung fehlgeschlagen: ${e.message}" }
        } else {
            val connection = _nasConnection.value
            _profileBusy.value = true
            viewModelScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        SmbProfileFiles(connection).use {
                            SharedProfileRepository(it).transfer(from, target, rename)
                        }
                    }
                    if (_source.value == ProfileSource.NAS && _nasConnection.value == connection) {
                        _profiles.value = result
                        if (rename && _selectedProfile.value == from) _selectedProfile.value = target
                        _nasOnline.value = true
                        _nasStatus.value = "NAS verbunden · ${result.size} Profile"
                        _message.value = if (rename) "NAS-Profil umbenannt" else "NAS-Profil kopiert"
                        try { withContext(Dispatchers.IO) { nasSettings.saveCache(result) } }
                        catch (_: Exception) { _nasStatus.value += " · Cache nicht aktualisiert" }
                    }
                } catch (e: Exception) {
                    _message.value = "NAS-Profiländerung nicht bestätigt: ${remoteError(e)}"
                    _nasOnline.value = false
                    _nasStatus.value = "NAS prüfen und Profile aktualisieren"
                } finally { _profileBusy.value = false }
            }
        }
    }

    private fun remoteUpdate(name: String, updatedProfile: SoundProfile?, overwrite: Boolean = false) {
        if (_profileBusy.value || !_nasOnline.value) return
        val connection = _nasConnection.value
        _profileBusy.value = true
        _message.value = "NAS-Profil wird aktualisiert …"
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    SmbProfileFiles(connection).use {
                        SharedProfileRepository(it).update(name, updatedProfile, replaceExisting = overwrite)
                    }
                }
                if (_source.value == ProfileSource.NAS && _nasConnection.value == connection) {
                    _profiles.value = result
                    _nasOnline.value = true
                    _selectedProfile.value = if (updatedProfile == null) null else name
                    _nasStatus.value = "NAS verbunden · ${result.size} Profile"
                    _message.value = if (updatedProfile == null) "Profil auf NAS gelöscht: $name" else "Profil auf NAS gespeichert: $name"
                    try { withContext(Dispatchers.IO) { nasSettings.saveCache(result) } }
                    catch (_: Exception) { _nasStatus.value += " · Cache nicht aktualisiert" }
                }
            } catch (e: Exception) {
                _message.value = "NAS-Schreibvorgang nicht bestätigt: ${remoteError(e)}"
                // Never silently fall back to local storage on a write failure.
                _nasOnline.value = false
                _nasStatus.value = "NAS prüfen und Profile aktualisieren"
            } finally { _profileBusy.value = false }
        }
    }

    fun applyProfile(name: String) {
        val profile = _profiles.value[name] ?: return
        if (!profile.valid() || _device.value.connection != Connection.CONNECTED) return
        val target = client ?: return
        val id = generation
        viewModelScope.launch {
            if (generation != id || client !== target) return@launch
            target.send(SoundbarCommands.mode(profile.mode))
            Catalog.levels.forEach { spec ->
                profile.levels[spec.field]?.let { target.send(SoundbarCommands.level(spec, it)) }
            }
            target.send(SoundbarCommands.option("b_drc", profile.drc))
            target.send(SoundbarCommands.option("b_night_time", profile.nightTime))
            profile.surround?.let { target.send(SoundbarCommands.option("b_rear", it)) }
            delay(700)
            if (generation != id || client !== target || _device.value.connection != Connection.CONNECTED) return@launch
            target.refresh()
            _selectedProfile.value = name
            _message.value = "Profil übertragen – Rückmeldung abwarten"
        }
    }
    override fun onCleared() { resumeIntent.cancel(); cancelConnectionRefresh(); client?.stop(); super.onCleared() }
}
