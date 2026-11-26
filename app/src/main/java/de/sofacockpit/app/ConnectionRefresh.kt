package de.sofacockpit.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.sofacockpit.app.tv.TvRemoteClient
import de.tonmaschine.android.MainViewModel
import de.sofacockpit.app.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.function.Consumer
import java.util.function.BooleanSupplier

/** One explicit refresh batch. Device checks run in parallel; UI uses actual completions. */
class ConnectionRefresh(
    private val tv: TvRemoteClient,
    private val sound: MainViewModel,
    private val updates: UpdateManager,
    private val networkAllowed: BooleanSupplier,
    private val stateChanged: Consumer<Boolean>,
    private val message: Consumer<String>
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    private var closed = false
    private var batch: Batch? = null

    private class Batch(val started: Long, val targets: Set<String>) {
        val results = linkedMapOf<String, String>()
        var finishing = false
    }

    fun request(category: Int) {
        if (closed || batch != null) return
        val targets = when (category) {
            CockpitNavigation.START -> setOf("TV", "Soundbar")
            CockpitNavigation.TV -> setOf("TV")
            CockpitNavigation.SOUND -> setOf("Soundbar")
            else -> return
        }
        val b = Batch(SystemClock.elapsedRealtime(), targets)
        batch = b
        setRefreshing(true)
        updates.setConnectionRefreshBusy(true)
        updates.checkOnRefresh()
        handler.postDelayed({
            if (batch === b) {
                targets.filter { it !in b.results }.forEach { b.results[it] = "$it: keine neue Antwort bestätigt" }
                finish(b)
            }
        }, 45_000L)
        if (!networkAllowed.getAsBoolean()) {
            targets.forEach { complete(b, it, "$it: Netzwerkberechtigung fehlt") }
            return
        }
        if ("TV" in targets) tv.refreshConnection { complete(b, "TV", it) }
        if ("Soundbar" in targets) sound.refreshConnection { complete(b, "Soundbar", it) }
    }
    private fun complete(b: Batch, key: String, result: String) {
        if (closed || batch !== b || key !in b.targets || b.results.containsKey(key)) return
        b.results[key] = result
        if (b.results.size == b.targets.size) finish(b)
    }
    private fun finish(b: Batch) {
        if (closed || batch !== b || b.finishing) return
        b.finishing = true
        val delay = (600L - (SystemClock.elapsedRealtime() - b.started)).coerceAtLeast(0L)
        handler.postDelayed({
            if (!closed && batch === b) {
                batch = null
                setRefreshing(false)
                message.accept(b.targets.mapNotNull { b.results[it] }.joinToString("\n"))
                updates.setConnectionRefreshBusy(false)
            }
        }, delay)
    }
    private fun setRefreshing(active: Boolean) {
        mutableRefreshing.value = active
        stateChanged.accept(active)
    }
    /** Do not start a delayed reconnect after the user has left the foreground. */
    fun cancel() {
        if (batch == null) return
        batch = null
        handler.removeCallbacksAndMessages(null)
        tv.cancelConnectionRefresh()
        sound.cancelConnectionRefresh()
        setRefreshing(false)
        updates.setConnectionRefreshBusy(false)
    }
    override fun close() {
        cancel()
        closed = true
        handler.removeCallbacksAndMessages(null)
    }
}
