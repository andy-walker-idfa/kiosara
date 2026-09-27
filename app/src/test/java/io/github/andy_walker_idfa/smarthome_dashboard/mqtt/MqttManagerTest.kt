@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.FakeDisplayCommands
import io.github.andy_walker_idfa.smarthome_dashboard.settings.DeviceSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeClock
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeNetworkMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttManagerTest {
    private val deviceId = "0123456789abcdef0123456789abcdef"
    private val base = "shdash/0123456789ab"

    private class FakeStateSource : MqttStateSource {
        val values = MutableStateFlow(mapOf("battery_level" to EntityValue("50", 50.0)))
        override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

        override fun snapshot(resample: Boolean) = values.value
    }

    private class Harness(
        val manager: MqttManager,
        val settings: MutableStateFlow<Settings>,
        val network: FakeNetworkMonitor,
        val connections: MutableList<FakeMqttConnection>,
        val display: FakeDisplayCommands,
        val source: FakeStateSource
    ) {
        val current get() = connections.last()
    }

    private fun TestScope.harness(
        mqtt: MqttSettings = MqttSettings(enabled = true, host = "broker", username = "panel"),
        failFirst: Int = 0,
        online: Boolean = true
    ): Harness {
        val settings =
            MutableStateFlow(
                Settings(
                    device = DeviceSettings(id = deviceId),
                    web = WebSettings(startUrl = "http://ha/"),
                    mqtt = mqtt
                )
            )
        val network = FakeNetworkMonitor(online)
        val connections = mutableListOf<FakeMqttConnection>()
        val display = FakeDisplayCommands()
        val source = FakeStateSource()
        val manager =
            MqttManager(
                settings = settings,
                secretStore = FakeSecretStore(mutableMapOf(MqttSettings.PASSWORD_SECRET_KEY to "pw")),
                networkMonitor = network,
                connectionFactory = {
                    FakeMqttConnection(failConnect = connections.size < failFirst).also {
                        connections +=
                            it
                    }
                },
                stateSource = source,
                commandHandler = MqttCommandHandler(WebCommandBus(), display) { settings.value },
                deviceInfo = { Discovery.DeviceInfo(it, "M", "X", "0.3.0", "github") },
                clock = FakeClock(testScheduler),
                random = Random(0)
            )
        backgroundScope.launch { manager.run() }
        runCurrent()
        return Harness(manager, settings, network, connections, display, source)
    }

    @Test
    fun `connects with LWT and credentials, then publishes discovery, availability and states`() = runTest {
        val h = harness()
        assertTrue(h.manager.status.value is MqttStatus.Connected)
        val options = h.current.options!!
        assertEquals("shdash_0123456789ab", options.clientId)
        assertEquals("panel", options.username)
        assertEquals("pw", options.password)
        assertEquals("$base/availability", options.willTopic)
        assertEquals("offline", options.willPayload)
        assertEquals(listOf("homeassistant/status", "$base/set/+"), h.current.subscriptions)

        val topics = h.current.published.map { it.topic }
        assertEquals("homeassistant/device/shdash_0123456789ab/config", topics[0])
        // Retained states of removed entities are cleared right after discovery.
        val cleared = Entities.removed.keys.map { Published("$base/state/$it", "", true) }
        assertEquals(cleared, h.current.published.subList(1, 1 + cleared.size))
        val next = 1 + cleared.size
        assertEquals(Published("$base/availability", "online", true), h.current.published[next])
        assertEquals(Published("$base/state/battery_level", "50", true), h.current.published[next + 1])
        assertTrue(h.current.published.all { it.retain })
    }

    @Test
    fun `retained commands are ignored, live commands are executed`() = runTest {
        val h = harness()
        h.current.deliver("$base/set/screen_brightness", "10", retained = true)
        runCurrent()
        assertEquals(emptyList<String>(), h.display.calls)
        h.current.deliver("$base/set/screen_brightness", "10")
        runCurrent()
        assertEquals(listOf("brightness=10"), h.display.calls)
    }

    @Test
    fun `HA birth message triggers a delayed full re-publish`() = runTest {
        val h = harness()
        val before = h.current.published.size
        h.current.deliver("homeassistant/status", "online")
        runCurrent()
        assertEquals(before, h.current.published.size)
        advanceTimeBy(5_001)
        val republished = h.current.published.drop(before).map { it.topic }
        assertEquals("homeassistant/device/shdash_0123456789ab/config", republished.first())
        assertTrue("$base/state/battery_level" in republished)
    }

    @Test
    fun `state changes publish only changed values`() = runTest {
        val h = harness()
        val before = h.current.published.size
        h.source.values.value = mapOf("battery_level" to EntityValue("51", 51.0))
        h.source.changes.emit(Unit)
        advanceTimeBy(600)
        assertEquals(listOf(Published("$base/state/battery_level", "51", true)), h.current.published.drop(before))
    }

    @Test
    fun `failed connects retry with backoff`() = runTest {
        val h = harness(failFirst = 2)
        assertTrue(h.manager.status.value is MqttStatus.Failed)
        assertEquals(1, h.connections.size)
        advanceTimeBy(1_001)
        assertEquals(2, h.connections.size)
        advanceTimeBy(2_001)
        assertEquals(3, h.connections.size)
        assertTrue(h.manager.status.value is MqttStatus.Connected)
    }

    @Test
    fun `lost connection reconnects`() = runTest {
        val h = harness()
        h.current.dropConnection()
        runCurrent()
        assertTrue(h.manager.status.value is MqttStatus.Failed)
        advanceTimeBy(1_001)
        assertEquals(2, h.connections.size)
        assertTrue(h.manager.status.value is MqttStatus.Connected)
    }

    @Test
    fun `disabling publishes offline and disconnects`() = runTest {
        val h = harness()
        val connection = h.current
        h.settings.value = h.settings.value.copy(mqtt = h.settings.value.mqtt.copy(enabled = false))
        runCurrent()
        assertEquals(Published("$base/availability", "offline", true), connection.published.last())
        assertTrue(connection.disconnected)
        assertEquals(MqttStatus.Disabled, h.manager.status.value)
    }

    @Test
    fun `waits for network and configuration`() = runTest {
        val offline = harness(online = false)
        assertEquals(MqttStatus.WaitingForNetwork, offline.manager.status.value)
        assertTrue(offline.connections.isEmpty())
        offline.network.state.value = true
        runCurrent()
        assertEquals(1, offline.connections.size)

        val unconfigured = harness(mqtt = MqttSettings(enabled = true, host = ""))
        assertEquals(MqttStatus.NotConfigured, unconfigured.manager.status.value)
    }
}
