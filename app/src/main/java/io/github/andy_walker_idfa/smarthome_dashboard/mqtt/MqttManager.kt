package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Backoff
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.network.NetworkMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.security.SecretStore
import io.github.andy_walker_idfa.smarthome_dashboard.settings.DeviceSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Connection state shown in Settings. */
sealed interface MqttStatus {
    data object Disabled : MqttStatus

    data object NotConfigured : MqttStatus

    data object WaitingForNetwork : MqttStatus

    data object Connecting : MqttStatus

    data class Connected(val sinceMillis: Long) : MqttStatus

    data class Failed(val message: String, val retryAtMillis: Long) : MqttStatus
}

/** Current entity values and a signal when any source changed. */
interface MqttStateSource {
    val changes: Flow<Unit>

    /** [resample] re-reads values that are not observed (memory, storage, device owner). */
    fun snapshot(resample: Boolean): Map<String, EntityValue>
}

/**
 * Owns the MQTT session with the user's broker: connect with retry/backoff, Home Assistant discovery,
 * availability (LWT), state publishing and command handling. [run] is called by the foreground service
 * and runs until cancelled.
 *
 * Rules (see the `ha-mqtt-discovery` skill): clean session; retained discovery/availability/states;
 * commands are never retained, so retained command messages are ignored; on HA's `online` birth
 * message everything is re-published after a random 1-5 s delay.
 */
@OptIn(FlowPreview::class)
class MqttManager(
    private val settings: StateFlow<Settings>,
    private val secretStore: SecretStore,
    private val networkMonitor: NetworkMonitor,
    private val connectionFactory: () -> MqttConnection,
    private val stateSource: MqttStateSource,
    private val commandHandler: MqttCommandHandler,
    private val deviceInfo: (deviceName: String) -> Discovery.DeviceInfo,
    private val clock: Clock,
    /** Release: the small entity set; debug: everything. */
    private val catalog: EntityCatalog = EntityCatalog(full = true),
    private val backoff: Backoff = Backoff(initialMillis = 1_000, maxMillis = 120_000),
    private val random: Random = Random.Default
) {
    private val mutableStatus = MutableStateFlow<MqttStatus>(MqttStatus.Disabled)
    val status: StateFlow<MqttStatus> = mutableStatus.asStateFlow()

    private val reconnectRequests = MutableStateFlow(0)

    /** The connection of the running session (for [goodbye]). */
    @Volatile private var active: Pair<MqttConnection, MqttNaming>? = null
    private val differ = StateDiffer()
    private val json = Json { encodeDefaults = true }

    /**
     * Before an intentional app restart: publish `offline` and disconnect, so HA shows the panel unavailable
     * right away instead of after the keep-alive timeout. Bounded by [GOODBYE_TIMEOUT_MS].
     */
    suspend fun goodbye() {
        val (connection, naming) = active ?: return
        withTimeoutOrNull(GOODBYE_TIMEOUT_MS) {
            runCatching { connection.publish(naming.availabilityTopic, OFFLINE, retain = true) }
        }
        connection.disconnect()
    }

    /** Drops the current connection attempt/backoff and connects again (e.g. after the password changed). */
    fun reconnectNow() = reconnectRequests.update { it + 1 }

    suspend fun run() {
        val connectionInputs = settings.map { ConnectionKey.from(it) }.distinctUntilChanged()
        combine(connectionInputs, networkMonitor.isConnected, reconnectRequests) { key, online, _ -> key to online }
            .collectLatest { (key, online) -> runFor(key, online) }
    }

    private suspend fun runFor(key: ConnectionKey, online: Boolean) {
        when {
            !key.mqtt.enabled -> return setStatus(MqttStatus.Disabled)

            !key.mqtt.isConfigured || key.deviceId.length < MqttNaming.SHORT_ID_LENGTH -> return setStatus(
                MqttStatus.NotConfigured
            )

            !online -> return setStatus(MqttStatus.WaitingForNetwork)
        }
        val naming = MqttNaming(key.deviceId)
        var attempt = 0
        while (true) {
            setStatus(MqttStatus.Connecting)
            val connection = connectionFactory()
            var connected = false
            var logReason = "Connection lost"
            val failure: String =
                try {
                    val lost = CompletableDeferred<Throwable?>()
                    val inbox = Channel<IncomingMessage>(INBOX_CAPACITY, BufferOverflow.DROP_OLDEST)
                    connection.connect(
                        options = connectOptions(key.mqtt, naming),
                        onMessage = { inbox.trySend(it) },
                        onConnectionLost = { lost.complete(it) }
                    )
                    connected = true
                    attempt = 0
                    AppLog.i(TAG, "Connected to ${key.mqtt.host}:${key.mqtt.port}")
                    setStatus(MqttStatus.Connected(clock.nowMillis()))
                    session(connection, naming, inbox, lost)
                    "Connection lost"
                } catch (e: CancellationException) {
                    if (connected) withContext(NonCancellable) { sayGoodbye(connection, naming) }
                    throw e
                } catch (e: Exception) {
                    connection.disconnect()
                    logReason = logSafeReason(e)
                    e.message ?: e.javaClass.simpleName
                }
            val wait = backoff.delayFor(attempt++)
            // The full message is shown in Settings only; the log gets our own texts or the exception class.
            AppLog.w(TAG, "MQTT: $logReason; retrying in ${wait / 1000} s")
            setStatus(MqttStatus.Failed(failure, clock.nowMillis() + wait))
            delay(wait)
        }
    }

    private suspend fun session(
        connection: MqttConnection,
        naming: MqttNaming,
        inbox: Channel<IncomingMessage>,
        lost: CompletableDeferred<Throwable?>
    ) = coroutineScope {
        differ.reset()
        active = connection to naming
        connection.subscribe(naming.statusTopic)
        connection.subscribe(naming.commandFilter)
        publishAll(connection, naming, resample = true)

        launch {
            for (message in inbox) {
                when {
                    message.topic == naming.statusTopic -> if (message.payload.trim() == HA_ONLINE) {
                        launch {
                            delay(random.nextLong(BIRTH_DELAY_MIN_MS, BIRTH_DELAY_MAX_MS))
                            AppLog.i(TAG, "Home Assistant came online; re-publishing")
                            publishAll(connection, naming, resample = false)
                        }
                    }

                    else -> naming.objectIdOfCommand(message.topic)?.let { objectId ->
                        if (message.retained) {
                            AppLog.w(TAG, "Ignored retained command on $objectId")
                        } else {
                            // A bad command must never drop the session. Log the object id only.
                            try {
                                commandHandler.handle(objectId, message.payload)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLog.e(TAG, "Command $objectId failed: ${e.javaClass.simpleName}")
                            }
                        }
                    }
                }
            }
        }
        launch {
            stateSource.changes.debounce(CHANGE_DEBOUNCE_MS).collect {
                publishStates(connection, naming, force = false, resample = false)
            }
        }
        launch {
            while (true) {
                delay(FULL_REFRESH_MS)
                publishStates(connection, naming, force = true, resample = true)
            }
        }
        val cause = lost.await()
        AppLog.w(TAG, "Connection lost: ${cause?.javaClass?.simpleName ?: "no cause"}")
        active = null
        coroutineContext.cancelChildren()
    }

    private suspend fun publishAll(connection: MqttConnection, naming: MqttNaming, resample: Boolean) {
        publishDiscovery(connection, naming)
        connection.publish(naming.availabilityTopic, ONLINE, retain = true)
        publishStates(connection, naming, force = true, resample = resample)
    }

    private suspend fun publishDiscovery(connection: MqttConnection, naming: MqttNaming) {
        val current = settings.value
        val payload =
            Discovery.build(
                naming,
                deviceInfo(DeviceSettings.DEFAULT_NAME),
                catalog
            )
        connection.publish(naming.discoveryTopic, json.encodeToString(JsonObject.serializer(), payload), retain = true)
        // An empty retained message deletes the broker's stale state of each removed entity.
        for (id in catalog.retired.keys) connection.publish(naming.stateTopic(id), "", retain = true)
        for (id in catalog.retiredWithAttributes) connection.publish(naming.attributesTopic(id), "", retain = true)
    }

    private suspend fun publishStates(
        connection: MqttConnection,
        naming: MqttNaming,
        force: Boolean,
        resample: Boolean
    ) {
        val changed = differ.changes(stateSource.snapshot(resample).filterKeys { it in catalog.publishedIds }, force)
        for ((id, value) in changed) {
            connection.publish(naming.stateTopic(id), value.state, retain = true)
            value.attributes?.let {
                connection.publish(
                    naming.attributesTopic(id),
                    json.encodeToString(JsonObject.serializer(), it),
                    retain = true
                )
            }
        }
    }

    /** Clean disconnect: the broker does not send the LWT then, so publish `offline` ourselves. */
    private suspend fun sayGoodbye(connection: MqttConnection, naming: MqttNaming) {
        withTimeoutOrNull(GOODBYE_TIMEOUT_MS) {
            runCatching { connection.publish(naming.availabilityTopic, OFFLINE, retain = true) }
        }
        connection.disconnect()
        setStatus(MqttStatus.Disabled)
    }

    private fun connectOptions(mqtt: MqttSettings, naming: MqttNaming) = ConnectOptions(
        host = mqtt.host.trim(),
        port = mqtt.port,
        clientId = naming.nodeId,
        username = mqtt.username.trim().ifBlank { null },
        password = secretStore.get(MqttSettings.PASSWORD_SECRET_KEY),
        keepAliveSeconds = KEEP_ALIVE_SECONDS,
        willTopic = naming.availabilityTopic,
        willPayload = OFFLINE
    )

    private fun setStatus(status: MqttStatus) {
        mutableStatus.value = status
    }

    /** Settings that require a new connection when they change. */
    private data class ConnectionKey(val deviceId: String, val mqtt: MqttSettings) {
        companion object {
            fun from(s: Settings) = ConnectionKey(
                s.device.id,
                s.mqtt
            )
        }
    }

    /** Log text for a connection failure: never an exception message, except our own broker-reply texts. */
    private fun logSafeReason(e: Exception): String =
        if (e is MqttRejectedException) e.message.orEmpty() else e.javaClass.simpleName

    private companion object {
        const val TAG = "Mqtt"
        const val ONLINE = "online"
        const val OFFLINE = "offline"
        const val HA_ONLINE = "online"
        const val KEEP_ALIVE_SECONDS = 30
        const val INBOX_CAPACITY = 64
        const val CHANGE_DEBOUNCE_MS = 500L

        /** Full re-publish of all states (safety net; changes are published immediately). */
        const val FULL_REFRESH_MS = 5 * 60_000L
        const val BIRTH_DELAY_MIN_MS = 1_000L
        const val BIRTH_DELAY_MAX_MS = 5_000L
        const val GOODBYE_TIMEOUT_MS = 2_000L
    }
}
