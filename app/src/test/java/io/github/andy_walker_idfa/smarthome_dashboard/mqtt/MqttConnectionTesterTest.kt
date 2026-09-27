package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttConnectionTesterTest {
    private val deviceId = "0123456789abcdef0123456789abcdef"
    private val mqtt = MqttSettings(enabled = true, host = "broker.local", port = 1883, username = "panel")

    @Test
    fun `success uses a separate client id, no last will, and disconnects`() = runTest {
        val connection = FakeMqttConnection()
        val result = MqttConnectionTester { connection }.test(mqtt, deviceId, "pw")
        assertEquals(MqttTestResult.Success, result)
        val options = connection.options!!
        assertEquals("shdash_0123456789ab_test", options.clientId)
        assertNull(options.willTopic)
        assertEquals("panel", options.username)
        assertEquals("pw", options.password)
        assertTrue(connection.disconnected)
        assertTrue(connection.published.isEmpty())
    }

    @Test
    fun `failure is reported, not thrown`() = runTest {
        val result = MqttConnectionTester { FakeMqttConnection(failConnect = true) }.test(mqtt, deviceId, "pw")
        assertTrue(result is MqttTestResult.Failure)
    }

    @Test
    fun `failures are described precisely`() {
        fun d(e: Throwable) = MqttConnectionTester.describeFailure(e, "broker.local", 1883)
        assertEquals(
            "The broker rejected the login (wrong username or password)",
            d(MqttRejectedException("The broker rejected the login (wrong username or password)", IOException()))
        )
        assertEquals(
            "Host \"broker.local\" not found. Check the broker address.",
            d(IOException(UnknownHostException("x")))
        )
        assertEquals(
            "Connection refused by broker.local:1883. Is the broker running, and is the port correct?",
            d(RuntimeException(ConnectException("refused")))
        )
        assertTrue(d(NoRouteToHostException()).startsWith("broker.local is unreachable"))
    }

    @Test
    fun `missing device id is reported`() = runTest {
        val result = MqttConnectionTester { FakeMqttConnection() }.test(mqtt, "", "pw")
        assertTrue(result is MqttTestResult.Failure)
    }
}
