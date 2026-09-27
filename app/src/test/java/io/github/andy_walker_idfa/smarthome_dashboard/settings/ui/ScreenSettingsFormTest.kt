package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSettingsFormTest {
    private val defaults = ScreenSettings()
    private val form = ScreenSettingsForm.from(defaults)

    private fun invalid(f: ScreenSettingsForm) = (f.toSettings(defaults) as ScreenSettingsForm.Result.Invalid).fields

    @Test
    fun `defaults round-trip`() {
        assertEquals("22:00", form.nightStart)
        assertEquals("06:30", form.nightEnd)
        assertEquals(ScreenSettingsForm.Result.Valid(defaults), form.toSettings(defaults))
    }

    @Test
    fun `defaults match the agreed behaviour`() {
        assertEquals(BrightnessMode.SYSTEM, defaults.brightnessMode)
        assertFalse(defaults.nightEnabled)
        assertEquals(60, ScreenSettings.NIGHT_WAKE_SECONDS)
    }

    @Test
    fun `edits are applied`() {
        val edited =
            form.copy(
                brightnessMode = BrightnessMode.MANUAL,
                manualBrightnessPercent = "35",
                nightEnabled = true,
                nightStart = "23:15",
                nightEnd = "7:00"
            )
        val s = (edited.toSettings(defaults) as ScreenSettingsForm.Result.Valid).settings
        assertEquals(BrightnessMode.MANUAL, s.brightnessMode)
        assertEquals(35, s.manualBrightnessPercent)
        assertTrue(s.nightEnabled)
        assertEquals(23 * 60 + 15, s.nightStartMinutes)
        assertEquals(7 * 60, s.nightEndMinutes)
    }

    @Test
    fun `invalid values are reported`() {
        assertEquals(
            setOf(ScreenSettingsForm.Field.MANUAL_BRIGHTNESS, ScreenSettingsForm.Field.NIGHT_START),
            invalid(form.copy(manualBrightnessPercent = "101", nightStart = "25:00"))
        )
    }

    @Test
    fun `settings draft saves the screen section`() {
        val base = Settings(web = WebSettings(startUrl = "http://ha.local:8123/"))
        val draft = SettingsDraft.from(base).let { it.copy(screen = it.screen.copy(nightEnabled = true)) }
        assertTrue(draft.isDirty(base))
        val valid = draft.validate(base, hasStoredPassword = false) as SettingsDraft.Result.Valid
        assertTrue(valid.settings.screen.nightEnabled)

        val bad = SettingsDraft.from(base).let { it.copy(screen = it.screen.copy(nightEnd = "x")) }
        val result = bad.validate(base, hasStoredPassword = false) as SettingsDraft.Result.Invalid
        assertEquals(setOf(SettingsDraft.Field.NIGHT_END), result.fields)
        assertEquals(SettingsDraft.Section.SCREEN, result.firstSection)
    }
}
