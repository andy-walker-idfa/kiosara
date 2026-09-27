package io.github.andy_walker_idfa.smarthome_dashboard.core

/**
 * Exponential backoff: [initialMillis] × 2^attempt, capped at [maxMillis].
 * `attempt` is zero-based (the first retry waits [initialMillis]).
 */
class Backoff(private val initialMillis: Long = 2_000, private val maxMillis: Long = 60_000) {
    init {
        require(initialMillis > 0) { "initialMillis must be positive" }
        require(maxMillis >= initialMillis) { "maxMillis must be >= initialMillis" }
    }

    fun delayFor(attempt: Int): Long {
        require(attempt >= 0) { "attempt must be >= 0" }
        // Shifting by 62 or more would overflow; by then the cap has long been reached anyway.
        if (attempt >= MAX_SHIFT) return maxMillis
        val delay = initialMillis shl attempt
        return if (delay <= 0 || delay > maxMillis) maxMillis else delay
    }

    private companion object {
        const val MAX_SHIFT = 30
    }
}
