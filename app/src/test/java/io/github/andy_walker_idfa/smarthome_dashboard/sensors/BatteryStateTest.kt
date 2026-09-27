package io.github.andy_walker_idfa.smarthome_dashboard.sensors

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryStateTest {
    @Test
    fun `parses the battery broadcast extras`() {
        val s =
            BatteryState.parse(
                level = 43,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
                plugged = BatteryManager.BATTERY_PLUGGED_AC,
                temperatureTenths = 312,
                health = BatteryManager.BATTERY_HEALTH_GOOD,
                voltageMillis = 4012
            )
        assertEquals(BatteryState(43, true, PlugType.AC, 31.2, BatteryHealth.GOOD, 4.012), s)
    }

    @Test
    fun `handles odd scales, unplugged, unknown health and volt-reporting devices`() {
        val s = BatteryState.parse(
            level = 150,
            scale = 200,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING,
            plugged = 0,
            temperatureTenths = -50,
            health = 999,
            voltageMillis = 4
        )
        assertEquals(75, s.levelPercent)
        assertEquals(false, s.charging)
        assertEquals(PlugType.NONE, s.plugType)
        assertEquals(-5.0, s.temperatureCelsius, 0.0)
        assertEquals(BatteryHealth.UNKNOWN, s.health)
        assertEquals(4.0, s.voltageVolts, 0.0)
    }

    @Test
    fun `plug types map to HA enum values`() {
        assertEquals("usb", PlugType.fromPlugged(BatteryManager.BATTERY_PLUGGED_USB).value)
        assertEquals("wireless", PlugType.fromPlugged(BatteryManager.BATTERY_PLUGGED_WIRELESS).value)
        assertEquals("dock", PlugType.fromPlugged(8).value)
    }
}
