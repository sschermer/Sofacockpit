package de.tonmaschine.android.model

/** The last reported standby is not proof that a lost socket is still asleep.
 * A deliberate disconnect or an explicit app power-off is different. */
internal object SoundbarRecoveryPolicy {
    enum class Trigger { FOREGROUND, FOCUS_RETURN, REFRESH }
    enum class Action { NONE, STANDBY, PROBE, REPLACE }
    fun action(trigger: Trigger, desired: Boolean, connected: Boolean,
               liveStandby: Boolean, explicitlyOff: Boolean): Action {
        if (!desired) return Action.NONE
        if (explicitlyOff || (connected && liveStandby)) return Action.STANDBY
        if (!connected || trigger == Trigger.FOREGROUND) return Action.REPLACE
        return if (trigger == Trigger.REFRESH) Action.PROBE else Action.NONE
    }
    /** One attempt only if the old session last reported standby. A real new
     * powered-on status, not merely a TCP connect, permits regular retries. */
    fun limitUntilAwake(lastPower: Boolean?): Boolean = lastPower == false
}
