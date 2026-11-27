package de.tonmaschine.android.network

import org.json.JSONObject

/** Monotonic deadlines for a single TCP session. A valid status report, not
 * merely a TCP connection, received bytes or a successful write, proves life. */
internal class SoundbarLinkHealth(started: Long, private val responseLimitMs: Long,
                                  private val writeLimitMs: Long) {
    @Volatile private var lastStatus = started
    @Volatile private var writeStarted: Long? = null
    @Volatile var failure: String? = null
        private set

    fun receivedStatus(now: Long) { lastStatus = now }
    fun beginWrite(now: Long) { writeStarted = now }
    fun endWrite() { writeStarted = null }
    fun check(now: Long, standby: Boolean): String? {
        failure?.let { return it }
        val writing = writeStarted
        val expired = when {
            writing != null && now - writing >= writeLimitMs -> "Soundbar-Schreibvorgang blockiert"
            !standby && now - lastStatus >= responseLimitMs -> "Keine aktuelle Soundbar-Antwort"
            else -> null
        }
        if (expired != null) failure = expired
        return expired
    }

    companion object {
        val reports = listOf("SPK_LIST_VIEW_INFO", "FUNC_VIEW_INFO", "EQ_VIEW_INFO", "SETTING_VIEW_INFO")
        fun statusData(packet: JSONObject): JSONObject? =
            if (packet.optString("result") == "ok" && packet.optString("msg") in reports)
                packet.optJSONObject("data") else null
    }
}
