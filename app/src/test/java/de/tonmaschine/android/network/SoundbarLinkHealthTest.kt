package de.tonmaschine.android.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SoundbarLinkHealthTest {
    @Test fun onlyFreshStatusExtendsDeadline() {
        val h = SoundbarLinkHealth(100, 18000, 5000)
        assertNull(h.check(18099, false))
        h.receivedStatus(18000)
        assertNull(h.check(35999, false))
        assertEquals("Keine aktuelle Soundbar-Antwort", h.check(36000, false))
        assertNotNull(h.check(36001, true))
    }
    @Test fun standbySuppressesReadDeadlineButNotBlockedWrite() {
        val h = SoundbarLinkHealth(0, 18000, 5000)
        assertNull(h.check(100000, true))
        h.beginWrite(100000)
        assertNull(h.check(104999, true))
        assertEquals("Soundbar-Schreibvorgang blockiert", h.check(105000, true))
    }
    @Test fun completedWriteDoesNotResetResponseDeadline() {
        val h = SoundbarLinkHealth(0, 18000, 5000)
        h.beginWrite(10000)
        h.endWrite()
        assertNull(h.check(17999, false))
        assertNotNull(h.check(18000, false))
    }
    @Test fun validatesStatusResponseInsteadOfAnyPacket() {
        assertNull(SoundbarLinkHealth.statusData(JSONObject().put("msg","SPK_LIST_VIEW_INFO").put("data",JSONObject())))
        assertNull(SoundbarLinkHealth.statusData(JSONObject().put("msg","SPK_LIST_VIEW_INFO").put("result","error").put("data",JSONObject())))
        assertNull(SoundbarLinkHealth.statusData(JSONObject().put("msg","UNKNOWN").put("result","ok").put("data",JSONObject())))
        assertNull(SoundbarLinkHealth.statusData(JSONObject().put("msg","EQ_VIEW_INFO").put("result","ok")))
        assertNotNull(SoundbarLinkHealth.statusData(JSONObject().put("msg","EQ_VIEW_INFO").put("result","ok").put("data",JSONObject())))
    }
}
