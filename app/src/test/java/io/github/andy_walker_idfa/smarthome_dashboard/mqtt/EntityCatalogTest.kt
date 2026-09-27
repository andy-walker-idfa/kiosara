package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.FakeDisplayCommands
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityCatalogTest {
    private val naming = MqttNaming("0123456789abcdef0123456789abcdef")
    private val release = EntityCatalog(full = false)

    @Test
    fun `release publishes only the five user-facing entities`() {
        assertEquals(
            setOf("battery_level", "battery_temperature", "night_mode", "screen_brightness", "reload"),
            release.publishedIds
        )
    }

    @Test
    fun `release retires every other entity so HA deletes it`() {
        val components =
            Discovery.build(
                naming,
                Discovery.DeviceInfo("Wall panel", "LENOVO", "TB310FU", "0.7.2", "github"),
                release
            )["components"]!!.jsonObject
        assertEquals(Entities.all.size + Entities.removed.size, components.size)
        for ((id, component) in components) {
            val keys = component.jsonObject.keys
            if (id in release.publishedIds) {
                assertTrue(id, "unique_id" in keys)
            } else {
                assertEquals(id, setOf("platform"), keys)
            }
        }
        assertEquals("sensor", components["free_memory"]!!.jsonObject["platform"]!!.jsonPrimitive.content)
        assertTrue("current_url" in release.retiredWithAttributes)
        assertFalse("battery_level" in release.retired)
    }

    @Test
    fun `release rejects commands to retired entities`() {
        val display = FakeDisplayCommands()
        val handler = MqttCommandHandler(WebCommandBus(), display, { true }, release) { Settings(web = WebSettings()) }
        assertFalse(handler.handle("wake", "PRESS"))
        assertTrue(handler.handle(Entities.SCREEN_BRIGHTNESS, "40"))
        assertFalse(handler.handle(Entities.RESTART_APP, "PRESS"))
        assertFalse(handler.handle(Entities.SCREEN_STATE, "black"))
        assertFalse(handler.handle(Entities.LOAD_URL, "http://x/"))
        assertEquals(listOf("brightness=40"), display.calls)
    }

    @Test
    fun `debug publishes everything`() {
        val debug = EntityCatalog(full = true)
        assertEquals(Entities.all.size, debug.publishedIds.size)
        assertEquals(Entities.removed, debug.retired)
    }
}
