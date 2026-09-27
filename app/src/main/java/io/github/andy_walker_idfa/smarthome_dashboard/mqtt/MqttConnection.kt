package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

data class ConnectOptions(
    val host: String,
    val port: Int,
    val clientId: String,
    val username: String?,
    val password: String?,
    val keepAliveSeconds: Int,
    /** Retained last will; null for short-lived test connections. */
    val willTopic: String?,
    val willPayload: String?
)

/** The broker answered the connect with an error; [message] is a user-readable English description. */
class MqttRejectedException(message: String, cause: Throwable) : Exception(message, cause)

data class IncomingMessage(val topic: String, val payload: String, val retained: Boolean)

/**
 * Minimal MQTT client abstraction, so the connection manager can be unit-tested with a fake and the
 * library can be swapped. Reconnection is NOT done here; [MqttManager] owns the retry policy.
 */
interface MqttConnection {
    /**
     * Connects with a clean session and a retained LWT. [onMessage] and [onConnectionLost] are called
     * from a library thread. [onConnectionLost] fires only after a successful connect.
     */
    suspend fun connect(
        options: ConnectOptions,
        onMessage: (IncomingMessage) -> Unit,
        onConnectionLost: (Throwable?) -> Unit
    )

    suspend fun subscribe(topicFilter: String)

    /** QoS 1. */
    suspend fun publish(topic: String, payload: String, retain: Boolean)

    suspend fun disconnect()
}

/** [MqttConnection] backed by the HiveMQ MQTT client (MQTT 3.1.1), with automatic reconnect disabled. */
class HiveMqConnection : MqttConnection {
    private var client: Mqtt3AsyncClient? = null

    @Volatile
    private var connected = false

    override suspend fun connect(
        options: ConnectOptions,
        onMessage: (IncomingMessage) -> Unit,
        onConnectionLost: (Throwable?) -> Unit
    ) {
        val newClient =
            MqttClient.builder()
                .useMqttVersion3()
                .identifier(options.clientId)
                .serverHost(options.host)
                .serverPort(options.port)
                .addDisconnectedListener { context ->
                    if (connected) {
                        connected = false
                        onConnectionLost(context.cause)
                    }
                }.buildAsync()
        client = newClient
        // Register before subscribing so no message is missed.
        newClient.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            // Commands and HA's birth message are tiny; anything bigger is dropped before it is copied and decoded.
            // (The client has already received the packet; limit the broker's max_packet_size as well.)
            val size = publish.payload.map { it.remaining() }.orElse(0)
            if (size > MAX_PAYLOAD_BYTES) {
                AppLog.w(TAG, "Ignored an MQTT message of $size bytes")
            } else {
                onMessage(
                    IncomingMessage(publish.topic.toString(), publish.payloadAsBytes.decodeToString(), publish.isRetain)
                )
            }
        }
        var connect = newClient.connectWith().cleanSession(true).keepAlive(options.keepAliveSeconds)
        if (options.willTopic != null) {
            connect = connect.willPublish()
                .topic(options.willTopic)
                .payload(options.willPayload.orEmpty().encodeToByteArray())
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(true)
                .applyWillPublish()
        }
        if (!options.username.isNullOrBlank()) {
            connect = connect.simpleAuth()
                .username(options.username)
                .password(options.password.orEmpty().encodeToByteArray())
                .applySimpleAuth()
        }
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { connect.send().await() }
        } catch (e: Mqtt3ConnAckException) {
            throw MqttRejectedException(describe(e.mqttMessage.returnCode), e)
        }
        connected = true
    }

    override suspend fun subscribe(topicFilter: String) {
        requireClient().subscribeWith().topicFilter(topicFilter).qos(MqttQos.AT_LEAST_ONCE).send().await()
    }

    override suspend fun publish(topic: String, payload: String, retain: Boolean) {
        requireClient().publishWith()
            .topic(topic)
            .payload(payload.encodeToByteArray())
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(retain)
            .send()
            .await()
    }

    override suspend fun disconnect() {
        val current = client ?: return
        connected = false
        client = null
        runCatching { withTimeout(DISCONNECT_TIMEOUT_MS) { current.disconnect().await() } }
    }

    private fun requireClient() = checkNotNull(client) { "not connected" }

    private fun describe(code: Mqtt3ConnAckReturnCode): String = when (code) {
        Mqtt3ConnAckReturnCode.NOT_AUTHORIZED, Mqtt3ConnAckReturnCode.BAD_USER_NAME_OR_PASSWORD ->
            "The broker rejected the login (wrong username or password)"

        Mqtt3ConnAckReturnCode.IDENTIFIER_REJECTED -> "The broker rejected the client ID"

        Mqtt3ConnAckReturnCode.SERVER_UNAVAILABLE -> "The broker is unavailable"

        else -> "The broker refused the connection ($code)"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val DISCONNECT_TIMEOUT_MS = 3_000L
        const val TAG = "MqttConnection"

        /** Larger than any command (load_url: 255 characters) or HA birth message. */
        const val MAX_PAYLOAD_BYTES = 1_024
    }
}
