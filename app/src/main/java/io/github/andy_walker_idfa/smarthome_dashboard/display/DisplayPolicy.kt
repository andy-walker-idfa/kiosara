package io.github.andy_walker_idfa.smarthome_dashboard.display

import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import kotlin.math.pow

/** What the dashboard screen shows. [value] is the Home Assistant `screen_state` option (debug builds). */
enum class ScreenMode(val value: String) {
    ACTIVE("active"),
    BLACK("black");

    /** Black covers the dashboard completely, so it can be paused. */
    val coversDashboard: Boolean get() = this == BLACK

    companion object {
        val haOptions: List<String> = entries.map { it.value }
    }
}

/**
 * The screen rules (0.8.0: no daytime screensaver). Pure and unit-tested.
 *
 * 1. Dashboard not visible (Settings open, app in the background): [ScreenMode.ACTIVE]; nothing is covered.
 * 2. Night: [ScreenMode.BLACK], except for [ScreenSettings.NIGHT_WAKE_SECONDS] after the last touch.
 * 3. Otherwise [ScreenMode.ACTIVE].
 */
object DisplayPolicy {
    data class Input(
        val visible: Boolean,
        val night: Boolean,
        val nowElapsed: Long,
        /** End of the stay-awake period after a touch at night, or null. */
        val nightWakeUntilElapsed: Long?
    )

    fun mode(input: Input): ScreenMode = with(input) {
        if (visible && night && !nightAwake()) ScreenMode.BLACK else ScreenMode.ACTIVE
    }

    /** The next elapsed time at which [mode] can change without any other input, or null. */
    fun nextDeadline(input: Input): Long? = with(input) {
        if (visible && night) nightWakeUntilElapsed?.takeIf { it > nowElapsed } else null
    }

    private fun Input.nightAwake() = nightWakeUntilElapsed != null && nowElapsed < nightWakeUntilElapsed

    /**
     * The window brightness for [mode]: -1 follows the system, 0 is the lowest backlight (Black, behind a black
     * overlay), otherwise at least [MIN_WINDOW_BRIGHTNESS].
     */
    fun windowBrightness(mode: ScreenMode, screen: ScreenSettings): Float = when (mode) {
        ScreenMode.BLACK -> 0f

        ScreenMode.ACTIVE ->
            if (screen.brightnessMode == BrightnessMode.SYSTEM) -1f else toWindow(screen.manualBrightnessPercent)
    }

    /**
     * Percent (as on a brightness slider) to a window brightness. The window value is close to linear light
     * output, while people perceive brightness roughly logarithmically, so a 2.2 gamma makes 50 % look like
     * half brightness.
     */
    fun toWindow(percent: Int): Float = (percent.coerceIn(0, ScreenSettings.MAX_PERCENT) / 100.0).pow(GAMMA).toFloat()
        .coerceAtLeast(MIN_WINDOW_BRIGHTNESS)

    const val MILLIS_PER_MINUTE = 60_000L
    const val GAMMA = 2.2

    /** 0 is documented as the lowest level; vendors differ, so normal screens stay slightly above it. */
    const val MIN_WINDOW_BRIGHTNESS = 0.01f
}
