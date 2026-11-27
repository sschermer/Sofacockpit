package de.tonmaschine.android.network

import de.tonmaschine.android.protocol.TemescalCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class SoundbarClientRecoveryTest {
    @Test(timeout = 15000)
    fun silentTcpSocketIsReplacedAndFreshReplyConfirmsRecovery() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failure = AtomicReference<Throwable>()
        val recovered = CountDownLatch(1)
        val watchdogReported = CountDownLatch(1)
        val server = ServerSocket(0, 2, InetAddress.getLoopbackAddress())
        server.soTimeout = 8000
        val simulator = thread(isDaemon = true) {
            try {
                server.accept().use { first ->
                    first.soTimeout = 5000
                    // Accept and read the initial request, but never reply.
                    TemescalCodec.readJson(first.getInputStream())
                    val buffer = ByteArray(4096)
                    while (first.getInputStream().read(buffer) >= 0) { }
                }
                server.accept().use { second ->
                    second.soTimeout = 4000
                    val request = JSONObject(TemescalCodec.readJson(second.getInputStream()))
                    assertEquals("get", request.getString("cmd"))
                    val reply = JSONObject().put("result","ok").put("msg","SPK_LIST_VIEW_INFO")
                        .put("data",JSONObject().put("i_vol",23).put("b_powerstatus",true))
                    second.getOutputStream().write(TemescalCodec.encryptJson(reply.toString()))
                    second.getOutputStream().flush()
                    recovered.await(3, TimeUnit.SECONDS)
                }
            } catch (e: Throwable) { failure.set(e) }
        }
        val client = SoundbarClient(
            ip = server.inetAddress.hostAddress!!, port = server.localPort, scope = scope,
            onPacket = { packet ->
                if (packet.optJSONObject("data")?.optInt("i_vol") == 23) recovered.countDown()
            },
            onConnection = { _, error ->
                if (error == "Keine aktuelle Soundbar-Antwort") watchdogReported.countDown()
            },
            responseLimitMs = 1000, writeLimitMs = 1000
        )
        try {
            client.start()
            assertTrue("Silent session not detected", watchdogReported.await(6, TimeUnit.SECONDS))
            assertTrue("Fresh status not received", recovered.await(6, TimeUnit.SECONDS))
            simulator.join(1000)
            failure.get()?.let { throw AssertionError("Simulator failure", it) }
        } finally {
            client.stop()
            scope.cancel()
            server.close()
            simulator.join(1000)
        }
    }
}
