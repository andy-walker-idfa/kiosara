package io.github.andy_walker_idfa.smarthome_dashboard.recovery

/**
 * Stops automatic restarts when they repeat: at most [MAX_RESTARTS] within [WINDOW_MS]. After that, Android's
 * normal crash handling takes over (the connection service still restarts itself). Pure; unit-tested.
 */
object CrashLoopGuard {
    const val MAX_RESTARTS = 5
    const val WINDOW_MS = 30 * 60_000L

    /** [previous] are the wall-clock times of earlier automatic restarts. */
    fun mayRestart(previous: List<Long>, nowMillis: Long): Boolean =
        previous.count { nowMillis - it in 0 until WINDOW_MS } < MAX_RESTARTS
}
