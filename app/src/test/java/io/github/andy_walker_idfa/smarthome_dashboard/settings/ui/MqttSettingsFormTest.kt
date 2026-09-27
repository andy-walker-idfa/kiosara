package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttSettingsFormTest {
    private val valid = MqttSettingsForm.from(MqttSettings(enabled = true, host = "192.168.1.10", username = "panel"))

    @Test
    fun `valid form keeps the stored password when the field is empty`() {
        val result = valid.validate() as MqttSettingsForm.Result.Valid
        assertEquals("192.168.1.10", result.settings.host)
        assertEquals(1883, result.settings.port)
        assertEquals(null, result.newPassword)
        assertEquals(
            "secret",
            (valid.copy(newPassword = "secret").validate() as MqttSettingsForm.Result.Valid).newPassword
        )
    }

    @Test
    fun `address shows the port only when it is not the default`() {
        assertEquals("192.168.1.10", valid.address)
        assertEquals("broker.lan:8883", MqttSettingsForm.from(MqttSettings(host = "broker.lan", port = 8883)).address)
        assertEquals("", MqttSettingsForm.from(MqttSettings()).address)
    }

    @Test
    fun `address with a port is parsed`() {
        val result = valid.copy(address = " mqtt://Broker.lan:8883/ ").validate() as MqttSettingsForm.Result.Valid
        assertEquals("broker.lan", result.settings.host)
        assertEquals(8883, result.settings.port)
        assertEquals("ha.local" to 1883, MqttSettingsForm.parseAddress("ha.local"))
    }

    @Test
    fun `invalid addresses are rejected`() {
        for (bad in listOf("", "host:0", "host:70000", "host:abc", ":1883", "a b", "http://x/y")) {
            assertNull(bad, MqttSettingsForm.parseAddress(bad))
        }
        assertEquals(MqttSettingsForm.Result.InvalidAddress, valid.copy(address = "host:70000").validate())
        assertEquals(MqttSettingsForm.Result.InvalidAddress, valid.copy(address = "").validate())
    }

    @Test
    fun `address may be empty while MQTT is disabled, but a typed one must be valid`() {
        assertTrue(valid.copy(enabled = false, address = "").validate() is MqttSettingsForm.Result.Valid)
        assertEquals(
            MqttSettingsForm.Result.InvalidAddress,
            valid.copy(enabled = false, address = "x:0").validate()
        )
    }
}
