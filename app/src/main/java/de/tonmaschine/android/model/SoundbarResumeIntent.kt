package de.tonmaschine.android.model

/** One foreground check of a still desired soundbar session, including a lost/retrying session. */
internal class SoundbarResumeIntent {
    private data class Target(val generation: Int, val address: String)
    private var target: Target? = null

    fun background(generation: Int, address: String, desired: Boolean) {
        target = if (desired && address.isNotBlank()) Target(generation, address) else null
    }

    fun cancel() { target = null }

    fun take(generation: Int, address: String, desired: Boolean, networkAllowed: Boolean): Boolean {
        val previous = target
        target = null
        return previous != null && previous.generation == generation && previous.address == address &&
            desired && networkAllowed
    }
}
