package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Result of a one-off "Test connection" from Settings. Failure texts are English (project convention). */
sealed interface MqttTestResult {
    data object Success : MqttTestResult

    data class Failure(val reason: String) : MqttTestResult
}

/**
 * Tries the connection with the values currently entered in Settings (not yet saved). Uses its own client
 * id and no last will, so it neither kicks the running session off the broker nor marks the panel offline.
 */
class MqttConnectionTester(private val connectionFactory: () -> MqttConnection) {
    suspend fun test(mqtt: MqttSettings, deviceId: String, password: String?): MqttTestResult {
        val host = mqtt.host.trim()
        if (deviceId.length <
            MqttNaming.SHORT_ID_LENGTH
        ) {
            return MqttTestResult.Failure("Device ID is not ready yet; try again.")
        }
        val naming = MqttNaming(deviceId)
        val connection = connectionFactory()
        return try {
            connection.connect(
                ConnectOptions(
                    host = host,
                    port = mqtt.port,
                    clientId = "${naming.nodeId}_test",
                    username = mqtt.username.trim().ifBlank { null },
                    password = password,
                    keepAliveSeconds = KEEP_ALIVE_SECONDS,
                    willTopic = null,
                    willPayload = null
                ),
                onMessage = {},
                onConnectionLost = {}
            )
            MqttTestResult.Success
        } catch (e: TimeoutCancellationException) {
            MqttTestResult.Failure(describeFailure(e, host, mqtt.port))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MqttTestResult.Failure(describeFailure(e, host, mqtt.port))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEEP_ALIVE_SECONDS = 30

        /** Maps a connect failure (anywhere in the cause chain) to a precise, user-readable reason. */
        fun describeFailure(error: Throwable, host: String, port: Int): String {
            val chain = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
            return when {
                chain.any {
                    it is MqttRejectedException
                } -> chain.first { it is MqttRejectedException }.message.orEmpty()

                chain.any { it is UnknownHostException || it is UnresolvedAddressException } ->
                    "Host \"$host\" not found. Check the broker address."

                chain.any { it is ConnectException } ->
                    "Connection refused by $host:$port. Is the broker running, and is the port correct?"

                chain.any { it is NoRouteToHostException } ->
                    "$host is unreachable. Check the address and that the tablet is on the same network."

                chain.any { it is TimeoutCancellationException || it is SocketTimeoutException } ->
                    "No answer from $host:$port (timeout). Check the address, port and firewall."

                else -> error.message ?: error.javaClass.simpleName
            }
        }

        private const val MAX_CAUSE_DEPTH = 8
    }
}
