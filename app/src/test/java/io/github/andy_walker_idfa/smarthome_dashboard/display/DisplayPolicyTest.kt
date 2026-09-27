package io.github.andy_walker_idfa.smarthome_dashboard.display

import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayPolicyTest {
    private fun input(now: Long = 0, visible: Boolean = true, night: Boolean = false, wakeUntil: Long? = null) =
        DisplayPolicy.Input(visible, night, now, wakeUntil)

    @Test
    fun `by day the screen is active`() {
        assertEquals(ScreenMode.ACTIVE, DisplayPolicy.mode(input(now = 999_999)))
        assertNull(DisplayPolicy.nextDeadline(input()))
    }

    @Test
    fun `night is black and wakes for the stay-awake period`() {
        assertEquals(ScreenMode.BLACK, DisplayPolicy.mode(input(night = true)))
        assertEquals(ScreenMode.ACTIVE, DisplayPolicy.mode(input(night = true, now = 10, wakeUntil = 60_000)))
        assertEquals(60_000L, DisplayPolicy.nextDeadline(input(night = true, now = 10, wakeUntil = 60_000)))
        assertEquals(ScreenMode.BLACK, DisplayPolicy.mode(input(night = true, now = 60_000, wakeUntil = 60_000)))
        assertNull(DisplayPolicy.nextDeadline(input(night = true, now = 60_000, wakeUntil = 60_000)))
    }

    @Test
    fun `nothing is covered while the dashboard is not visible`() {
        assertEquals(ScreenMode.ACTIVE, DisplayPolicy.mode(input(visible = false, night = true)))
        assertNull(DisplayPolicy.nextDeadline(input(visible = false, night = true, wakeUntil = 60_000)))
    }

    @Test
    fun `window brightness`() {
        val system = ScreenSettings(brightnessMode = BrightnessMode.SYSTEM)
        assertEquals(-1f, DisplayPolicy.windowBrightness(ScreenMode.ACTIVE, system), 0f)
        assertEquals(0f, DisplayPolicy.windowBrightness(ScreenMode.BLACK, system), 0f)
        val manual = ScreenSettings(brightnessMode = BrightnessMode.MANUAL, manualBrightnessPercent = 100)
        assertEquals(1f, DisplayPolicy.windowBrightness(ScreenMode.ACTIVE, manual), 0f)
        assertEquals(
            0.218f,
            DisplayPolicy.windowBrightness(ScreenMode.ACTIVE, manual.copy(manualBrightnessPercent = 50)),
            0.001f
        )
        assertEquals(
            DisplayPolicy.MIN_WINDOW_BRIGHTNESS,
            DisplayPolicy.windowBrightness(ScreenMode.ACTIVE, manual.copy(manualBrightnessPercent = 0)),
            0f
        )
        assertEquals(0f, DisplayPolicy.windowBrightness(ScreenMode.BLACK, manual), 0f)
    }
}
