package de.tonmaschine.android.network

import de.tonmaschine.android.protocol.TemescalCodec
import de.tonmaschine.android.model.SoundbarCommands
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * One reader/writer, serialized commands. A separate, session-scoped watchdog
 * closes a silent or write-blocked socket, even when the I/O loop cannot run.
 * No command from a failed socket is replayed on a subsequent socket.
 * Confirmed standby suppresses polling/recovery until an unsolicited awake
 * report or an explicitly requested fresh client. No power commands here.
 */
class SoundbarClient(
    private val ip: String,
    private val port: Int = 9741,
    private val scope: CoroutineScope,
    private val onPacket: (JSONObject) -> Unit,
    private val onConnection: (Boolean, String?) -> Unit,
    private val responseLimitMs: Long = 18000L,
    private val writeLimitMs: Long = 5000L,
    private val limitRecoveryUntilAwake: Boolean = false
) {
    private val queue = LinkedBlockingQueue<JSONObject>(128)
    private val lock = Any()
    private var task: Job? = null
    @Volatile private var active = false
    @Volatile private var connected = false
    @Volatile private var currentSocket: Socket? = null
    @Volatile private var powerOn: Boolean? = null
    private val initialMessages = SoundbarLinkHealth.reports

    fun start() {
        synchronized(lock) {
            if (active) return
            active = true
            task = scope.launch(Dispatchers.IO) { runLoop() }
        }
    }

    fun stop() {
        val socket = synchronized(lock) {
            active = false
            connected = false
            task?.cancel()
            task = null
            queue.clear()
            currentSocket
        }
        try { socket?.close() } catch (_: Exception) {}
    }

    fun send(packet: JSONObject): Boolean = synchronized(lock) {
        active && connected && queue.offer(packet)
    }

    fun refresh() = initialMessages.forEach { send(SoundbarCommands.get(it)) }
    fun refreshSignal() {
        send(SoundbarCommands.get("SPK_LIST_VIEW_INFO"))
        send(SoundbarCommands.get("FUNC_VIEW_INFO"))
    }

    private fun now() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())

    private suspend fun runLoop() {
        var retryMs = 1000L
        var awakeConfirmed = !limitRecoveryUntilAwake
        while (active && scope.isActive) {
            // A confirmed sleeping soundbar may be woken by TCP traffic. Leave
            // a lost standby session alone until the user explicitly reconnects.
            if (powerOn == false) {
                delay(1000)
                continue
            }
            var socket: Socket? = null
            var watchdog: Job? = null
            var health: SoundbarLinkHealth? = null
            var failure: String? = null
            try {
                onConnection(false, null)
                val session = Socket()
                socket = session
                synchronized(lock) {
                    if (active) currentSocket = session
                }
                if (!active || !scope.isActive) break
                session.connect(InetSocketAddress(ip, port), 5000)
                session.soTimeout = 300
                session.keepAlive = true
                synchronized(lock) {
                    if (active && currentSocket === session) {
                        queue.clear()
                        initialMessages.forEach { queue.offer(SoundbarCommands.get(it)) }
                        connected = true
                    }
                }
                if (!active || !scope.isActive) break
                onConnection(true, null)
                val live = SoundbarLinkHealth(now(), responseLimitMs, writeLimitMs)
                health = live
                watchdog = scope.launch(Dispatchers.IO) {
                    while (isActive && active && currentSocket === session && !session.isClosed) {
                        delay(250)
                        if (!active || currentSocket !== session || session.isClosed) break
                        if (live.check(now(), powerOn == false) != null) {
                            // Only close this exact socket, never a newer session.
                            synchronized(lock) {
                                if (currentSocket === session) {
                                    connected = false
                                    queue.clear()
                                }
                            }
                            try { session.close() } catch (_: Exception) {}
                            break
                        }
                    }
                }
                val input = session.getInputStream()
                val output = session.getOutputStream()
                var nextSignal = now() + 6000
                while (active && scope.isActive && session.isConnected && !session.isClosed) {
                    live.beginWrite(now())
                    try {
                        repeat(8) {
                            val pending = queue.poll() ?: return@repeat
                            output.write(TemescalCodec.encryptJson(pending.toString()))
                        }
                        output.flush()
                    } finally { live.endWrite() }
                    try {
                        val packet = JSONObject(TemescalCodec.readJson(input))
                        val data = SoundbarLinkHealth.statusData(packet)
                        if (data != null) {
                            live.receivedStatus(now())
                            retryMs = 1000L
                            (data.opt("b_powerstatus") as? Boolean)?.let {
                                powerOn = it
                                if (it) awakeConfirmed = true
                            }
                        }
                        if (active && currentSocket === session && !session.isClosed) onPacket(packet)
                    } catch (_: SocketTimeoutException) {
                        // Safe only between frames. Partial-frame errors still break the session.
                    }
                    val current = now()
                    if (powerOn != false && current >= nextSignal) {
                        refreshSignal()
                        nextSignal = current + 6000
                    }
                    live.check(current, powerOn == false)?.let { throw SocketTimeoutException(it) }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                failure = health?.failure ?: error.message ?: error.javaClass.simpleName
            } finally {
                watchdog?.cancel()
                synchronized(lock) {
                    connected = false
                    queue.clear()
                    if (currentSocket === socket) currentSocket = null
                }
                try { socket?.close() } catch (_: Exception) {}
            }
            if (!active || !scope.isActive) break
            val detail = health?.failure ?: failure ?: "Soundbar-Verbindung beendet"
            onConnection(false, if (powerOn == false) "Zuletzt Standby – bei Bedarf neu verbinden" else detail)
            if (!awakeConfirmed) break // one bounded attempt from a previously sleeping session
            delay(retryMs)
            // Backoff grows until an actual valid status response, not TCP open.
            retryMs = (retryMs * 2).coerceAtMost(30000L)
        }
    }
}
