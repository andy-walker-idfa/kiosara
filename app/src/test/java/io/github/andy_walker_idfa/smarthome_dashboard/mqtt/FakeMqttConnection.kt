package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.security.SecretStore
import java.io.IOException

data class Published(val topic: String, val payload: String, val retain: Boolean)

/** Records everything; lets tests deliver messages and drop the connection. */
class FakeMqttConnection(private val failConnect: Boolean = false) : MqttConnection {
    val published = mutableListOf<Published>()
    val subscriptions = mutableListOf<String>()
    var options: ConnectOptions? = null
    var disconnected = false
    private var onMessage: ((IncomingMessage) -> Unit)? = null
    private var onLost: ((Throwable?) -> Unit)? = null

    override suspend fun connect(
        options: ConnectOptions,
        onMessage: (IncomingMessage) -> Unit,
        onConnectionLost: (Throwable?) -> Unit
    ) {
        this.options = options
        if (failConnect) throw IOException("Connection refused")
        this.onMessage = onMessage
        this.onLost = onConnectionLost
    }

    override suspend fun subscribe(topicFilter: String) {
        subscriptions += topicFilter
    }

    override suspend fun publish(topic: String, payload: String, retain: Boolean) {
        published += Published(topic, payload, retain)
    }

    override suspend fun disconnect() {
        disconnected = true
    }

    fun deliver(topic: String, payload: String, retained: Boolean = false) =
        onMessage!!(IncomingMessage(topic, payload, retained))

    fun dropConnection() = onLost!!(IOException("socket closed"))
}

class FakeSecretStore(private val values: MutableMap<String, String> = mutableMapOf()) : SecretStore {
    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String): Boolean {
        values[key] = value
        return true
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
