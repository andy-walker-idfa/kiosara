package io.github.andy_walker_idfa.smarthome_dashboard.core

import android.os.SystemClock

/** Time source, abstracted so time-dependent logic can be tested with virtual time. */
interface Clock {
    /** Wall-clock time in epoch milliseconds. Can jump (NTP, user changes). */
    fun nowMillis(): Long

    /** Monotonic milliseconds since boot, including deep sleep. Use for durations and timeouts. */
    fun elapsedRealtimeMillis(): Long
}

object SystemClockImpl : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
