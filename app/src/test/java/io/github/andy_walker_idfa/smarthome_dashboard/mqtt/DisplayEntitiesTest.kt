package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatus
import io.github.andy_walker_idfa.smarthome_dashboard.display.FakeDisplayCommands
import io.github.andy_walker_idfa.smarthome_dashboard.display.ScreenMode
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatus
import io.github.andy_walker_idfa.smarthome_dashboard.network.WifiDetails
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryKind
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryRecord
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardStatus
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screen entities: brightness, wake, night mode (0.8.0: no screensaver). */
class DisplayEntitiesTest {
    private val naming = MqttNaming("0123456789abcdef0123456789abcdef")
    private val web = WebSettings(startUrl = "http://ha.local:8123/")

    private fun components() = Discovery.build(
        naming,
        Discovery.DeviceInfo("Wall panel", "LENOVO", "TB310FU", "0.4.1", "github")
    )["components"]!!.jsonObject

    private fun states(
        display: DisplayStatus,
        screen: ScreenSettings = ScreenSettings(),
        recovery: RecoveryRecord? = null,
        kiosk: KioskStatus = KioskStatus()
    ) = EntityStates.compute(
        StateInputs(
            battery = null,
            wifi = WifiDetails(null, null),
            dashboard = DashboardStatus(),
            display = display,
            screen = screen,
            lastRecovery = recovery,
            kiosk = kiosk,
            web = web,
            sample = SystemSample(0, 0, 0, 0, false),
            static = StaticInfo("0.4.0 (github)", "13", 33, "", 0)
        )
    )

    @Test
    fun `discovery has the display entities with the right platforms`() {
        val c = components()
        fun platform(id: String) = c[id]!!.jsonObject["platform"]!!.jsonPrimitive.content
        assertEquals("switch", platform(Entities.NIGHT_MODE))
        assertEquals(setOf("platform"), c["wake"]!!.jsonObject.keys)
        assertEquals("number", platform(Entities.SCREEN_BRIGHTNESS))
        assertEquals(
            listOf("active", "black"),
            (c[Entities.SCREEN_STATE]!!.jsonObject["options"] as JsonArray).map { it.jsonPrimitive.content }
        )
        assertEquals("false", c[Entities.NIGHT_MODE]!!.jsonObject["optimistic"]!!.jsonPrimitive.content)
    }

    @Test
    fun `removed entities are published as platform-only components so HA deletes them`() {
        val c = components()
        assertEquals(
            mapOf("platform" to "sensor"),
            c["ambient_light"]!!.jsonObject.mapValues {
                it.value.jsonPrimitive.content
            }
        )
        assertEquals(
            mapOf("platform" to "switch"),
            c["auto_brightness"]!!.jsonObject.mapValues {
                it.value.jsonPrimitive.content
            }
        )
        for (id in listOf(
            "screensaver",
            "screensaver_timeout",
            "screensaver_mode",
            "night_display_mode",
            "dashboard"
        )) {
            assertEquals(id, setOf("platform"), c[id]!!.jsonObject.keys)
        }
        assertTrue(Entities.removed.keys.none { it in Entities.byId })
        val s = states(DisplayStatus())
        for (id in Entities.removed.keys) assertNull(id, s[id])
    }

    @Test
    fun `states reflect the display`() {
        val s = states(DisplayStatus(mode = ScreenMode.BLACK, night = true, brightnessPercent = 35))
        assertEquals("black", s[Entities.SCREEN_STATE]!!.state)
        assertEquals("ON", s[Entities.NIGHT_MODE]!!.state)
        assertEquals("35", s[Entities.SCREEN_BRIGHTNESS]!!.state)
        assertEquals("OFF", states(DisplayStatus())[Entities.NIGHT_MODE]!!.state)
    }

    @Test
    fun `last recovery and restart app`() {
        val c = components()
        assertEquals("button", c[Entities.RESTART_APP]!!.jsonObject["platform"]!!.jsonPrimitive.content)
        assertEquals("timestamp", c[Entities.LAST_RECOVERY]!!.jsonObject["device_class"]!!.jsonPrimitive.content)
        assertEquals(EntityStates.NONE, states(DisplayStatus())[Entities.LAST_RECOVERY]!!.state)
        val s =
            states(
                DisplayStatus(),
                recovery = RecoveryRecord(RecoveryKind.APP_CRASH, "java.lang.IllegalStateException", 1_758_000_000_000)
            )[Entities.LAST_RECOVERY]!!
        assertEquals("2025-09-16T05:20:00Z", s.state)
        assertEquals("app_crash", s.attributes!!["kind"]!!.jsonPrimitive.content)
        assertEquals("java.lang.IllegalStateException", s.attributes!!["detail"]!!.jsonPrimitive.content)

        var allowed = true
        val handler = MqttCommandHandler(WebCommandBus(), FakeDisplayCommands(), { allowed }) { Settings(web = web) }
        assertTrue(handler.handle(Entities.RESTART_APP, "PRESS"))
        allowed = false
        assertFalse(handler.handle(Entities.RESTART_APP, "PRESS"))
    }

    @Test
    fun `kiosk lock is a state sensor without a command`() {
        val c = components()[Entities.KIOSK_LOCK]!!.jsonObject
        assertEquals("binary_sensor", c["platform"]!!.jsonPrimitive.content)
        assertEquals("lock", c["device_class"]!!.jsonPrimitive.content)
        assertEquals("diagnostic", c["entity_category"]!!.jsonPrimitive.content)
        assertFalse(c.containsKey("command_topic"))
        // HA lock class: OFF = locked, ON = unlocked.
        val locked =
            states(DisplayStatus(), kiosk = KioskStatus(deviceOwner = true, lockEnabled = true, lockActive = true))
        assertEquals("OFF", locked[Entities.KIOSK_LOCK]!!.state)
        assertEquals("ON", locked[Entities.DEVICE_OWNER]!!.state)
        assertEquals("ON", states(DisplayStatus())[Entities.KIOSK_LOCK]!!.state)
        val handler = MqttCommandHandler(WebCommandBus(), FakeDisplayCommands()) { Settings(web = web) }
        assertFalse(handler.handle(Entities.KIOSK_LOCK, "OFF"))
    }

    @Test
    fun `display commands`() {
        val display = FakeDisplayCommands()
        val handler = MqttCommandHandler(WebCommandBus(), display) { Settings(web = web) }
        assertFalse("removed in 0.8.1", handler.handle("wake", "PRESS"))
        assertTrue(handler.handle(Entities.NIGHT_MODE, "ON"))
        assertTrue(handler.handle(Entities.SCREEN_BRIGHTNESS, "20.4"))
        assertEquals(listOf("night=true", "brightness=20"), display.calls)

        assertFalse(handler.handle(Entities.NIGHT_MODE, "maybe"))
        for (bad in listOf("NaN", "Infinity", "-Infinity", "1e999")) {
            assertFalse(bad, handler.handle(Entities.SCREEN_BRIGHTNESS, bad))
        }
        display.nightEnabled = false
        assertFalse(handler.handle(Entities.NIGHT_MODE, "ON"))
        // Removed entities (0.4.1, 0.8.0): their commands are rejected.
        assertFalse(handler.handle("auto_brightness", "ON"))
        assertFalse(handler.handle("screensaver", "ON"))
        assertFalse(handler.handle("screensaver_timeout", "10"))
    }
}
