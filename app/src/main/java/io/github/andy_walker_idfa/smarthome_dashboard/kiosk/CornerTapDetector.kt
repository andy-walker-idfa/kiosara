package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

/**
 * The way into Settings: [taps] taps in the top-right corner within [windowMillis]. Taps elsewhere are ignored (they
 * don't start over), so one slightly-off tap doesn't spoil the sequence. The taps still reach the page (keep that
 * corner free of controls). Pure; unit-tested.
 */
class CornerTapDetector(private val taps: Int = TAPS, private val windowMillis: Long = WINDOW_MS) {
    private val times = ArrayDeque<Long>()

    /** Feed each touch-down; returns true when the sequence is complete. */
    fun onTap(inCorner: Boolean, timeMillis: Long): Boolean {
        if (!inCorner) return false
        times.addLast(timeMillis)
        prune(timeMillis)
        if (times.size < taps) return false
        times.clear()
        return true
    }

    /** Corner taps counted so far (for the on-screen dots); 0 once the window has passed. */
    fun progress(timeMillis: Long): Int {
        prune(timeMillis)
        return times.size
    }

    fun reset() = times.clear()

    private fun prune(now: Long) {
        while (times.isNotEmpty() && now - times.first() > windowMillis) times.removeFirst()
    }

    companion object {
        const val TAPS = 5
        const val WINDOW_MS = 4_000L

        /** Side of the corner square in dp (about 12 mm on a 200 dpi tablet). */
        const val CORNER_SIZE_DP = 100

        /** True if ([x], [y]) is in the top-right square of [cornerSize] on a window [width] wide. */
        fun isInTopRight(x: Float, y: Float, width: Int, cornerSize: Float): Boolean =
            x >= width - cornerSize && y <= cornerSize
    }
}
