package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.DeviceSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.SettingsDraft.Field
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.SettingsDraft.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDraftTest {
    private val configured =
        Settings(
            device = DeviceSettings(id = "0123456789abcdef0123456789abcdef"),
            web = WebSettings(startUrl = "http://192.168.1.10:8123/", trustedCertSha256 = "AA"),
            mqtt = MqttSettings(enabled = true, host = "192.168.1.10", username = "panel")
        )

    @Test
    fun `fresh install requires only the Home Assistant URL`() {
        val result = SettingsDraft.from(Settings()).validate(Settings(), hasStoredPassword = false)
        assertEquals(SettingsDraft.Result.Invalid(setOf(Field.START_URL)), result)
        assertEquals(Section.DASHBOARD, (result as SettingsDraft.Result.Invalid).firstSection)

        val withUrl = SettingsDraft.from(Settings()).let { it.copy(web = it.web.copy(startUrl = "192.168.1.10:8123")) }
        assertTrue(withUrl.validate(Settings(), hasStoredPassword = false) is SettingsDraft.Result.Valid)
    }

    @Test
    fun `MQTT address, username and password are required only while MQTT is on`() {
        val draft = SettingsDraft.from(configured).let { it.copy(mqtt = it.mqtt.copy(address = "", username = "")) }
        val result = draft.validate(configured, hasStoredPassword = false) as SettingsDraft.Result.Invalid
        assertEquals(setOf(Field.MQTT_ADDRESS, Field.MQTT_USERNAME, Field.MQTT_PASSWORD), result.fields)
        assertEquals(Section.INTEGRATION, result.firstSection)

        val off = draft.copy(mqtt = draft.mqtt.copy(enabled = false))
        assertTrue(off.validate(configured, hasStoredPassword = false) is SettingsDraft.Result.Valid)
    }

    @Test
    fun `a stored password satisfies the password requirement`() {
        assertTrue(
            SettingsDraft.from(configured).validate(configured, hasStoredPassword = true) is SettingsDraft.Result.Valid
        )
        val typed = SettingsDraft.from(configured).let { it.copy(mqtt = it.mqtt.copy(newPassword = "pw")) }
        val result = typed.validate(configured, hasStoredPassword = false) as SettingsDraft.Result.Valid
        assertEquals("pw", result.newPassword)
    }

    @Test
    fun `valid result merges web and MQTT settings and keeps the rest`() {
        val draft =
            SettingsDraft.from(configured).let {
                it.copy(web = it.web.copy(textZoomPercent = "120"), mqtt = it.mqtt.copy(address = "192.168.1.10:1884"))
            }
        val settings = (draft.validate(configured, hasStoredPassword = true) as SettingsDraft.Result.Valid).settings
        assertEquals("0123456789abcdef0123456789abcdef", settings.device.id)
        assertEquals(120, settings.web.textZoomPercent)
        assertEquals("AA", settings.web.trustedCertSha256)
        assertEquals(1884, settings.mqtt.port)
        assertEquals("192.168.1.10", settings.mqtt.host)
    }

    @Test
    fun `dirty tracking includes a newly typed password`() {
        val draft = SettingsDraft.from(configured)
        assertFalse(draft.isDirty(configured))
        assertTrue(draft.copy(web = draft.web.copy(textZoomPercent = "110")).isDirty(configured))
        assertTrue(draft.copy(mqtt = draft.mqtt.copy(newPassword = "pw")).isDirty(configured))
        // Changes made outside the draft (the pinned certificate) don't make it dirty.
        assertFalse(draft.isDirty(configured.copy(web = configured.web.copy(trustedCertSha256 = ""))))
    }
}
