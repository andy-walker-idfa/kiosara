@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.andy_walker_idfa.smarthome_dashboard.display

import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeSettingsRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayControllerTest {
    /** Wall clock starts at 2026-09-25 12:00 UTC; [jumpMillis] simulates clock changes. */
    private class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
        var jumpMillis = 0L

        override fun nowMillis(): Long = START + scheduler.currentTime + jumpMillis

        override fun elapsedRealtimeMillis(): Long = scheduler.currentTime

        companion object {
            val START: Long = Instant.parse("2026-09-25T12:00:00Z").toEpochMilli()
        }
    }

    private class Harness(
        val controller: DisplayController,
        val settings: FakeSettingsRepository,
        val clock: TestClock,
        val timeChanges: MutableSharedFlow<Unit>
    ) {
        val status get() = controller.status.value
        val screen get() = settings.state.value.screen
    }

    private fun TestScope.harness(screen: ScreenSettings = ScreenSettings(), visible: Boolean = true): Harness {
        val settings = FakeSettingsRepository(Settings(screen = screen))
        val clock = TestClock(testScheduler)
        val timeChanges = MutableSharedFlow<Unit>()
        val controller =
            DisplayController(
                scope = backgroundScope,
                settingsRepository = settings,
                clock = clock,
                zone = { ZoneOffset.UTC },
                systemBrightnessPercent = MutableStateFlow(40),
                timeChanges = timeChanges
            )
        controller.start()
        runCurrent()
        if (visible) controller.onDashboardVisible(true)
        runCurrent()
        return Harness(controller, settings, clock, timeChanges)
    }

    private val night1230to1300 = ScreenSettings(nightEnabled = true, nightStartMinutes = 750, nightEndMinutes = 780)
    private val nightNow = ScreenSettings(nightEnabled = true, nightStartMinutes = 11 * 60, nightEndMinutes = 13 * 60)

    @Test
    fun `by day the screen never goes dark`() = runTest {
        val h = harness()
        advanceTimeBy(480.minutes)
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
        assertFalse(h.status.pauseDashboard)
    }

    @Test
    fun `at night a touch wakes the screen for a minute`() = runTest {
        val h = harness(nightNow)
        assertTrue(h.status.night)
        // Showing the dashboard counts as a touch, so it starts awake.
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
        advanceTimeBy(61.seconds)
        assertEquals(ScreenMode.BLACK, h.status.mode)
        assertTrue(h.status.pauseDashboard)
        assertEquals(0f, h.status.windowBrightness, 0f)
        h.controller.onUserActivity()
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
        advanceTimeBy(59.seconds)
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
        advanceTimeBy(2.seconds)
        assertEquals(ScreenMode.BLACK, h.status.mode)
    }

    @Test
    fun `the schedule switches at its transitions`() = runTest {
        val h = harness(night1230to1300)
        assertFalse(h.status.night)
        advanceTimeBy(29.minutes + 59.seconds)
        assertFalse(h.status.night)
        advanceTimeBy(2.seconds)
        assertTrue(h.status.night)
        assertEquals(ScreenMode.BLACK, h.status.mode)
        advanceTimeBy(30.minutes)
        assertFalse(h.status.night)
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
    }

    @Test
    fun `Home Assistant overrides the schedule until the next transition`() = runTest {
        val h = harness(night1230to1300)
        assertTrue(h.controller.setNight(true))
        assertTrue(h.status.night)
        advanceTimeBy(29.minutes)
        assertTrue(h.status.night)
        advanceTimeBy(32.minutes)
        assertFalse("override ended, schedule again", h.status.night)
        // An override to what the schedule says anyway changes nothing.
        assertTrue(h.controller.setNight(false))
        assertFalse(h.status.night)
    }

    @Test
    fun `night commands are rejected while night mode is off`() = runTest {
        val h = harness(ScreenSettings(nightEnabled = false))
        assertFalse(h.controller.setNight(true))
        assertFalse(h.status.night)
    }

    @Test
    fun `changing the schedule drops the override`() = runTest {
        val h = harness(night1230to1300)
        h.controller.setNight(true)
        assertTrue(h.status.night)
        h.settings.update { it.copy(screen = it.screen.copy(nightStartMinutes = 760)) }
        runCurrent()
        assertFalse(h.status.night)
    }

    @Test
    fun `a clock change re-evaluates the schedule`() = runTest {
        val h = harness(ScreenSettings(nightEnabled = true))
        assertFalse(h.status.night)
        h.clock.jumpMillis = 11 * 3_600_000L
        h.timeChanges.emit(Unit)
        runCurrent()
        assertTrue("23:00 is night", h.status.night)
    }

    @Test
    fun `brightness from Home Assistant is saved as a fixed level`() = runTest {
        val h = harness()
        assertEquals(40, h.status.brightnessPercent)
        assertEquals(-1f, h.status.windowBrightness, 0f)
        h.controller.setBrightnessPercent(30)
        runCurrent()
        assertEquals(BrightnessMode.MANUAL, h.screen.brightnessMode)
        assertEquals(30, h.screen.manualBrightnessPercent)
        assertEquals(30, h.status.brightnessPercent)
        assertEquals(DisplayPolicy.toWindow(30), h.status.windowBrightness, 0f)
    }

    @Test
    fun `nothing is covered while the dashboard is hidden, and showing it again wakes`() = runTest {
        val h = harness(nightNow)
        h.controller.onDashboardVisible(false)
        advanceTimeBy(10.minutes)
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
        assertTrue(h.status.night)
        h.controller.onDashboardVisible(true)
        assertEquals(ScreenMode.ACTIVE, h.status.mode)
        advanceTimeBy(61.seconds)
        assertEquals(ScreenMode.BLACK, h.status.mode)
    }
}
