package io.github.andy_walker_idfa.smarthome_dashboard.display

import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The display as it is now: rendered by the dashboard activity, published to Home Assistant. */
data class DisplayStatus(
    /** Effective mode: [ScreenMode.ACTIVE] whenever the dashboard is not visible. */
    val mode: ScreenMode = ScreenMode.ACTIVE,
    /** Window brightness for the dashboard (-1 = follow the system). */
    val windowBrightness: Float = -1f,
    /** True while the dashboard should be paused (covered by the black night screen). */
    val pauseDashboard: Boolean = false,
    val night: Boolean = false,
    /** Brightness of the normal (active) screen in percent: the system level or the fixed one. */
    val brightnessPercent: Int = 0
)

/** Read side for the dashboard activity, the MQTT publisher and Settings. */
interface DisplayStatusSource {
    val status: StateFlow<DisplayStatus>
}

/** Commands from Home Assistant. */
interface DisplayCommands {
    /** Sets a fixed brightness (saved). */
    fun setBrightnessPercent(percent: Int)

    /** Overrides the schedule until its next start or end. Returns false while night mode is off in Settings. */
    fun setNight(night: Boolean): Boolean
}

/**
 * Decides what the dashboard screen shows: brightness (system or fixed) and night mode (black, a touch wakes it for
 * a minute). App-scoped, so the state is right for Home Assistant even while the activity is gone.
 * Rules: [DisplayPolicy]. Night schedule: [NightSchedule].
 *
 * Timers use monotonic time for durations (stay awake) and wall-clock time for the schedule. Every input
 * re-evaluates and re-plans the single timer; a wait is never longer than [MAX_WAIT_MS], so a missed clock change
 * can't leave the screen in the wrong state for long.
 *
 * Thread-safe: inputs arrive from the main thread (touches), MQTT threads and the settings flow.
 */
class DisplayController(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
    private val systemBrightnessPercent: StateFlow<Int>,
    private val timeChanges: Flow<Unit>
) : DisplayStatusSource,
    DisplayCommands {
    private val lock = Any()
    private val mutableStatus = MutableStateFlow(DisplayStatus())
    override val status: StateFlow<DisplayStatus> = mutableStatus.asStateFlow()

    private var screen: ScreenSettings? = null
    private var visible = false
    private var nightWakeUntilElapsed: Long? = null

    /** Home Assistant's override of the schedule, valid until [overrideUntilMillis] (wall clock). */
    private var override: Boolean? = null
    private var overrideUntilMillis = Long.MAX_VALUE

    private var timerJob: Job? = null
    private var started = false

    fun start() {
        check(!started) { "start() must be called once" }
        started = true
        scope.launch {
            settingsRepository.settings.map { it.screen }.distinctUntilChanged().collect(::onScreenSettings)
        }
        scope.launch { systemBrightnessPercent.collect { evaluate() } }
        scope.launch { timeChanges.collect { evaluate("time changed") } }
    }

    // region Inputs from the dashboard activity

    /** The dashboard became visible (started) or hidden. Becoming visible counts as user activity. */
    fun onDashboardVisible(isVisible: Boolean) {
        synchronized(lock) {
            visible = isVisible
            if (isVisible) recordActivity()
        }
        evaluate()
    }

    /** A touch or key press on the dashboard. Always wakes the screen. */
    fun onUserActivity() {
        synchronized(lock) { recordActivity() }
        evaluate()
    }

    // endregion

    // region DisplayCommands (Home Assistant)

    override fun setBrightnessPercent(percent: Int) {
        val value = percent.coerceIn(0, ScreenSettings.MAX_PERCENT)
        scope.launch {
            settingsRepository.update {
                it.copy(
                    screen = it.screen.copy(brightnessMode = BrightnessMode.MANUAL, manualBrightnessPercent = value)
                )
            }
        }
    }

    override fun setNight(night: Boolean): Boolean {
        val current = synchronized(lock) { screen } ?: return false
        if (!current.nightEnabled) return false
        synchronized(lock) {
            val now = clock.nowMillis()
            val scheduled = NightSchedule.isNight(current.nightStartMinutes, current.nightEndMinutes, now, zone())
            if (night == scheduled) {
                override = null
            } else {
                override = night
                overrideUntilMillis =
                    NightSchedule.nextTransition(current.nightStartMinutes, current.nightEndMinutes, now, zone())
                        ?: Long.MAX_VALUE
            }
        }
        evaluate("night override")
        return true
    }

    // endregion

    private fun onScreenSettings(new: ScreenSettings) {
        synchronized(lock) {
            val old = screen
            screen = new
            if (old != null &&
                (
                    old.nightStartMinutes != new.nightStartMinutes || old.nightEndMinutes != new.nightEndMinutes ||
                        !new.nightEnabled
                    )
            ) {
                override = null
            }
        }
        evaluate()
    }

    private fun recordActivity() {
        nightWakeUntilElapsed = clock.elapsedRealtimeMillis() + ScreenSettings.NIGHT_WAKE_SECONDS * MILLIS_PER_SECOND
    }

    private fun evaluate(reason: String? = null) {
        synchronized(lock) {
            val s = screen ?: return
            val nowElapsed = clock.elapsedRealtimeMillis()
            val nowMillis = clock.nowMillis()
            val night = isNight(s, nowMillis)
            val input = DisplayPolicy.Input(visible, night, nowElapsed, nightWakeUntilElapsed)
            val mode = DisplayPolicy.mode(input)
            val new =
                DisplayStatus(
                    mode = mode,
                    windowBrightness = DisplayPolicy.windowBrightness(mode, s),
                    pauseDashboard = mode.coversDashboard,
                    night = night,
                    brightnessPercent =
                        if (s.brightnessMode == BrightnessMode.SYSTEM) {
                            systemBrightnessPercent.value
                        } else {
                            s.manualBrightnessPercent
                        }
                )
            val old = mutableStatus.value
            if (old.mode != new.mode || old.night != new.night) {
                val why = reason?.let { "; $it" }.orEmpty()
                AppLog.i(TAG, "Screen ${new.mode.value}, night=${new.night}$why")
            }
            mutableStatus.value = new

            val nextTransition =
                if (s.nightEnabled) {
                    NightSchedule.nextTransition(s.nightStartMinutes, s.nightEndMinutes, nowMillis, zone())
                } else {
                    null
                }
            val waits =
                listOfNotNull(DisplayPolicy.nextDeadline(input)?.minus(nowElapsed), nextTransition?.minus(nowMillis))
            scheduleTimer((waits.minOrNull() ?: MAX_WAIT_MS).coerceIn(MIN_WAIT_MS, MAX_WAIT_MS))
        }
    }

    private fun isNight(s: ScreenSettings, nowMillis: Long): Boolean {
        if (!s.nightEnabled) return false
        if (override != null && nowMillis >= overrideUntilMillis) override = null
        return override ?: NightSchedule.isNight(s.nightStartMinutes, s.nightEndMinutes, nowMillis, zone())
    }

    private fun scheduleTimer(waitMillis: Long) {
        timerJob?.cancel()
        timerJob =
            scope.launch {
                delay(waitMillis)
                evaluate()
            }
    }

    companion object {
        private const val TAG = "Display"
        private const val MILLIS_PER_SECOND = 1_000L

        /** Safety net for missed clock changes. */
        const val MAX_WAIT_MS = 15 * 60_000L
        private const val MIN_WAIT_MS = 1L
    }
}
