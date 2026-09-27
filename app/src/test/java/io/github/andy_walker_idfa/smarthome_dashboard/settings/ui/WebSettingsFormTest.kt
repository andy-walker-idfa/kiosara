package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.WebSettingsForm.Field
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSettingsFormTest {
    private val base = WebSettings(startUrl = "https://ha.local:8123/", trustedCertSha256 = "AA:BB")

    @Test
    fun `unchanged form produces the same settings`() {
        val result = WebSettingsForm.from(base).toSettings(base)
        assertEquals(WebSettingsForm.Result.Valid(base), result)
    }

    @Test
    fun `invalid fields are all reported`() {
        val form = WebSettingsForm.from(base).copy(startUrl = "ftp://x", textZoomPercent = "10")
        assertEquals(WebSettingsForm.Result.Invalid(setOf(Field.START_URL, Field.TEXT_ZOOM)), form.toSettings(base))
    }

    @Test
    fun `text size is saved`() {
        val result = WebSettingsForm.from(base).copy(textZoomPercent = "125").toSettings(base)
        assertEquals(125, (result as WebSettingsForm.Result.Valid).settings.textZoomPercent)
    }

    @Test
    fun `the pinned certificate stays with the same host and is dropped for a new one`() {
        val samePath = WebSettingsForm.from(base).copy(startUrl = "https://ha.local:8123/lovelace/0").toSettings(base)
        assertEquals("AA:BB", (samePath as WebSettingsForm.Result.Valid).settings.trustedCertSha256)
        val otherHost = WebSettingsForm.from(base).copy(startUrl = "https://other.local:8123/").toSettings(base)
        assertEquals("", (otherHost as WebSettingsForm.Result.Valid).settings.trustedCertSha256)
    }

    @Test
    fun `start URL without scheme is normalised`() {
        val result = WebSettingsForm.from(base).copy(startUrl = "192.168.1.10:8123").toSettings(base)
        assertEquals("http://192.168.1.10:8123", (result as WebSettingsForm.Result.Valid).settings.startUrl)
    }
}
