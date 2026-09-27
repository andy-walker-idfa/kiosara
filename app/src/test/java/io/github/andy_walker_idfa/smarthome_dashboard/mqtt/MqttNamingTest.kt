package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MqttNamingTest {
    private val id = "0123456789abcdef0123456789abcdef"

    @Test
    fun `defaults derive everything from the device id`() {
        val n = MqttNaming(id)
        assertEquals("0123456789ab", n.shortId)
        assertEquals("shdash_0123456789ab", n.nodeId)
        assertEquals("shdash/0123456789ab", n.baseTopic)
        assertEquals("homeassistant/status", n.statusTopic)
        assertEquals("homeassistant/device/shdash_0123456789ab/config", n.discoveryTopic)
        assertEquals("shdash/0123456789ab/availability", n.availabilityTopic)
        assertEquals("shdash/0123456789ab/state/battery_level", n.stateTopic("battery_level"))
        assertEquals("shdash/0123456789ab/set/+", n.commandFilter)
        assertEquals("shdash_0123456789ab_battery_level", n.uniqueId("battery_level"))
    }

    @Test
    fun `command object ids are parsed only from own command topics`() {
        val n = MqttNaming(id)
        assertEquals("reload", n.objectIdOfCommand("shdash/0123456789ab/set/reload"))
        assertNull(n.objectIdOfCommand("shdash/0123456789ab/state/reload"))
        assertNull(n.objectIdOfCommand("shdash/0123456789ab/set/a/b"))
        assertNull(n.objectIdOfCommand("other/set/reload"))
    }
}
