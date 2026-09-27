package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.FakeDisplayCommands
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommand
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryAndCommandsTest {
    private val naming = MqttNaming("0123456789abcdef0123456789abcdef")
    private val web = WebSettings(startUrl = "http://ha.local:8123/")

    private fun discovery(): JsonObject = Discovery.build(
        naming,
        Discovery.DeviceInfo("Wall panel", "LENOVO", "TB310FU", "0.3.0", "github")
    )

    @Test
    fun `discovery has one device block, origin, availability and all components`() {
        val d = discovery()
        val device = d["device"]!!.jsonObject
        assertEquals("shdash_0123456789ab", (device["identifiers"] as JsonArray)[0].jsonPrimitive.content)
        assertEquals("Wall panel", device["name"]!!.jsonPrimitive.content)
        assertEquals("0.3.0 (github)", device["sw_version"]!!.jsonPrimitive.content)
        assertFalse(device.containsKey("serial_number"))
        assertEquals("Kiosara", d["origin"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("shdash/0123456789ab/availability", d["availability_topic"]!!.jsonPrimitive.content)
        assertEquals(Entities.all.size + Entities.removed.size, d["components"]!!.jsonObject.size)
    }

    @Test
    fun `components have the right topics and categories`() {
        val c = discovery()["components"]!!.jsonObject
        val battery = c["battery_level"]!!.jsonObject
        assertEquals("sensor", battery["platform"]!!.jsonPrimitive.content)
        assertEquals("shdash_0123456789ab_battery_level", battery["unique_id"]!!.jsonPrimitive.content)
        assertEquals("shdash/0123456789ab/state/battery_level", battery["state_topic"]!!.jsonPrimitive.content)
        assertFalse(battery.containsKey("command_topic"))
        assertFalse(battery.containsKey("object_id"))

        val reload = c["reload"]!!.jsonObject
        assertFalse(reload.containsKey("state_topic"))
        assertEquals("shdash/0123456789ab/set/reload", reload["command_topic"]!!.jsonPrimitive.content)

        assertEquals(
            "shdash/0123456789ab/state/current_url",
            c["load_url"]!!.jsonObject["state_topic"]!!.jsonPrimitive.content
        )
        assertEquals("%", c["screen_brightness"]!!.jsonObject["unit_of_measurement"]!!.jsonPrimitive.content)
        assertEquals(setOf("platform"), c["dashboard"]!!.jsonObject.keys)
        assertEquals("diagnostic", c["ip_address"]!!.jsonObject["entity_category"]!!.jsonPrimitive.content)
        // Sensors may only be diagnostic, never config.
        for ((_, component) in c) {
            val obj = component.jsonObject
            if (obj["platform"]!!.jsonPrimitive.content in setOf("sensor", "binary_sensor")) {
                assertTrue(obj["entity_category"]?.jsonPrimitive?.content in setOf(null, "diagnostic"))
            }
        }
    }

    @Test
    fun `every numeric sensor has a display precision`() {
        val c = discovery()["components"]!!.jsonObject
        for ((id, component) in c) {
            val obj = component.jsonObject
            if (obj["state_class"]?.jsonPrimitive?.content == "measurement") {
                assertTrue("$id has no precision", obj.containsKey("suggested_display_precision"))
            }
        }
        assertEquals("0", c["free_memory"]!!.jsonObject["suggested_display_precision"]!!.jsonPrimitive.content)
        assertEquals("0", c["free_storage"]!!.jsonObject["suggested_display_precision"]!!.jsonPrimitive.content)
        assertEquals("1", c["battery_temperature"]!!.jsonObject["suggested_display_precision"]!!.jsonPrimitive.content)
    }

    @Test
    fun `remote URLs must be explicit http(s) on configured hosts unless allowed`() {
        assertEquals(
            "http://ha.local:8123/x",
            RemoteUrlPolicy.check("http://ha.local:8123/x", web, allowAnyHost = false)
        )
        assertNull(RemoteUrlPolicy.check("http://evil.example/", web, allowAnyHost = false))
        assertEquals("https://evil.example/", RemoteUrlPolicy.check("https://evil.example/", web, allowAnyHost = true))
        for (bad in listOf(
            "javascript:alert(1)",
            "file:///sdcard/x",
            "intent://x#Intent;end",
            "data:text/html,x",
            "ha.local:8123/x"
        )) {
            assertNull(bad, RemoteUrlPolicy.check(bad, web, allowAnyHost = true))
        }
    }

    @Test
    fun `commands map to web commands and brightness`() = runTest {
        val bus = WebCommandBus()
        val display = FakeDisplayCommands()
        val settings = Settings(web = web)
        val handler = MqttCommandHandler(bus, display) { settings }

        assertTrue(handler.handle(Entities.RELOAD, "PRESS"))
        assertEquals(WebCommand.ReloadPage, bus.commands.first())
        assertTrue(handler.handle(Entities.LOAD_URL, "http://ha.local:8123/cams"))
        assertEquals(WebCommand.LoadUrl("http://ha.local:8123/cams"), bus.commands.first())
        assertTrue(handler.handle(Entities.SCREEN_BRIGHTNESS, "37.6"))
        assertEquals(listOf("brightness=38"), display.calls)

        assertFalse(handler.handle(Entities.SCREEN_BRIGHTNESS, "150"))
        assertFalse("removed in 0.8.0", handler.handle("dashboard", "Start"))
        assertFalse(handler.handle(Entities.LOAD_URL, "http://evil.example/"))
        assertFalse(handler.handle(Entities.BATTERY_LEVEL, "5"))
        assertNull(withTimeoutOrNull(100) { bus.commands.first() })
    }
}
