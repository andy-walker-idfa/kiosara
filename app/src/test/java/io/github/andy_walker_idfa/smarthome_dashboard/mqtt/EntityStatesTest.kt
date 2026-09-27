package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatus
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatus
import io.github.andy_walker_idfa.smarthome_dashboard.network.WifiDetails
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.BatteryHealth
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.BatteryState
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.PlugType
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardStatus
import io.github.andy_walker_idfa.smarthome_dashboard.web.PageErrorInfo
import io.github.andy_walker_idfa.smarthome_dashboard.web.Viewport
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityStatesTest {
    private val web = WebSettings(startUrl = "http://ha.local:8123/")

    private fun inputs(dashboard: DashboardStatus = DashboardStatus(), battery: BatteryState? = battery()) =
        StateInputs(
            battery = battery,
            wifi = WifiDetails(rssiDbm = -61, ipAddress = "192.168.1.20"),
            dashboard = dashboard,
            display = DisplayStatus(brightnessPercent = 42),
            screen = ScreenSettings(),
            lastRecovery = null,
            kiosk = KioskStatus(),
            web = web,
            sample = SystemSample(
                freeMemoryBytes = 1536L * 1024 * 1024,
                freeStorageBytes = 12_345_678_901,
                appMemoryBytes = 150L * 1024 * 1024,
                bootTimeMillis = 1_758_000_000_123,
                deviceOwner = false
            ),
            static = StaticInfo("0.3.0 (github)", "13", 33, "2026-01-05", appStartedMillis = 1_758_000_100_000)
        )

    private fun battery() = BatteryState(55, true, PlugType.AC, 31.46, BatteryHealth.GOOD, 4.123)

    @Test
    fun `battery and system values are formatted for HA`() {
        val s = EntityStates.compute(inputs())
        assertEquals("55", s[Entities.BATTERY_LEVEL]!!.state)
        assertEquals("ON", s[Entities.CHARGING]!!.state)
        assertEquals("ac", s[Entities.PLUG_TYPE]!!.state)
        assertEquals("31.5", s[Entities.BATTERY_TEMPERATURE]!!.state)
        assertEquals("good", s[Entities.BATTERY_HEALTH]!!.state)
        assertEquals("4.12", s[Entities.BATTERY_VOLTAGE]!!.state)
        assertEquals("-61", s[Entities.WIFI_RSSI]!!.state)
        assertEquals("192.168.1.20", s[Entities.IP_ADDRESS]!!.state)
        assertEquals("2025-09-16T05:20:00Z", s[Entities.LAST_BOOT]!!.state)
        assertEquals("1536", s[Entities.FREE_MEMORY]!!.state)
        assertEquals("150", s[Entities.APP_MEMORY]!!.state)
        assertEquals("11.5", s[Entities.FREE_STORAGE]!!.state)
        assertEquals("0.3.0 (github)", s[Entities.APP_VERSION]!!.state)
        assertEquals("OFF", s[Entities.DEVICE_OWNER]!!.state)
        assertEquals("42", s[Entities.SCREEN_BRIGHTNESS]!!.state)
        assertEquals("None", s[Entities.LAST_INTERACTION]!!.state)
        assertEquals("None", s[Entities.CURRENT_URL]!!.state)
        assertEquals("OFF", s[Entities.PAGE_ERROR]!!.state)
    }

    @Test
    fun `viewport is published in CSS pixels with details in the attributes`() {
        assertEquals("None", EntityStates.compute(inputs())[Entities.VIEWPORT]!!.state)
        val value =
            EntityStates.compute(inputs(DashboardStatus(viewport = Viewport(1340, 800, 200))))[Entities.VIEWPORT]!!
        assertEquals("1072x640", value.state)
        assertEquals(JsonPrimitive(1.25), value.attributes!!["device_pixel_ratio"])
        assertEquals(JsonPrimitive(1340), value.attributes!!["width_px"])
    }

    @Test
    fun `battery entities are omitted until the first battery broadcast`() {
        val s = EntityStates.compute(inputs(battery = null))
        assertFalse(s.containsKey(Entities.BATTERY_LEVEL))
        assertFalse(s.containsKey(Entities.BATTERY_TEMPERATURE))
    }

    @Test
    fun `page error carries details as attributes`() {
        val error =
            PageErrorInfo(
                "network",
                "net::ERR_CONNECTION_REFUSED",
                "http://ha.local:8123/?code=secret",
                1_758_000_000_000
            )
        val s = EntityStates.compute(inputs(DashboardStatus(pageError = error)))
        val value = s[Entities.PAGE_ERROR]!!
        assertEquals("ON", value.state)
        assertEquals(JsonPrimitive("http://ha.local:8123/"), value.attributes!!["url"])
        assertEquals(JsonPrimitive("net::ERR_CONNECTION_REFUSED"), value.attributes!!["description"])
    }

    @Test
    fun `login codes are stripped from URLs`() {
        assertEquals(
            "http://ha.local:8123/?auth_callback=1",
            EntityStates.sanitizeUrl("http://ha.local:8123/?auth_callback=1&code=abc&state=xyz")
        )
        assertEquals(
            "http://ha.local:8123/lovelace/0",
            EntityStates.sanitizeUrl("http://ha.local:8123/lovelace/0?code=abc")
        )
        assertEquals("http://h/x?a=1#frag", EntityStates.sanitizeUrl("http://h/x?a=1&access_token=t#frag"))
        assertEquals("http://h/x", EntityStates.sanitizeUrl("http://h/x"))
    }

    @Test
    fun `current URL state omits the query, attributes keep the sanitized full URL`() {
        val url = "http://ha.local:8123/auth/authorize?response_type=code&client_id=x&code=secret"
        val value = EntityStates.compute(inputs(DashboardStatus(currentUrl = url)))[Entities.CURRENT_URL]!!
        assertEquals("http://ha.local:8123/auth/authorize", value.state)
        assertEquals(
            JsonPrimitive("http://ha.local:8123/auth/authorize?response_type=code&client_id=x"),
            value.attributes!!["url"]
        )
        val long = "http://ha.local:8123/" + "a".repeat(400)
        assertEquals(
            255,
            EntityStates.compute(inputs(DashboardStatus(currentUrl = long)))[Entities.CURRENT_URL]!!.state.length
        )
    }

    @Test
    fun `differ publishes everything on force and only significant changes otherwise`() {
        val differ = StateDiffer()
        val base = mapOf("battery_temperature" to EntityValue("30.0", 30.0), "ip_address" to EntityValue("1.1.1.1"))
        assertEquals(2, differ.changes(base, force = false).size)

        val small = base + ("battery_temperature" to EntityValue("30.3", 30.3))
        assertTrue(differ.changes(small, force = false).isEmpty())

        val big = base + ("battery_temperature" to EntityValue("30.6", 30.6))
        assertEquals(setOf("battery_temperature"), differ.changes(big, force = false).keys)

        val newIp = big + ("ip_address" to EntityValue("1.1.1.2"))
        assertEquals(setOf("ip_address"), differ.changes(newIp, force = false).keys)

        assertEquals(2, differ.changes(newIp, force = true).size)
    }

    @Test
    fun `differ treats attribute changes as changes`() {
        val differ = StateDiffer()
        differ.changes(
            mapOf(
                "page_error" to EntityValue(
                    "ON",
                    attributes = buildJsonObject {
                        put("url", "a")
                    }
                )
            ),
            force = false
        )
        val changed = differ.changes(
            mapOf(
                "page_error" to EntityValue(
                    "ON",
                    attributes = buildJsonObject {
                        put("url", "b")
                    }
                )
            ),
            force = false
        )
        assertEquals(1, changed.size)
    }
}
