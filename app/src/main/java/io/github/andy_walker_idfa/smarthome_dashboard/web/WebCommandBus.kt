package io.github.andy_walker_idfa.smarthome_dashboard.web

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Commands other features (settings UI now, MQTT and the REST API later) send to the dashboard. */
sealed interface WebCommand {
    /** Stable name for logs (class names are obfuscated in release builds; never includes URLs). */
    val logName: String

    data object ReloadPage : WebCommand {
        override val logName = "ReloadPage"
    }

    data object LoadStartUrl : WebCommand {
        override val logName = "LoadStartUrl"
    }

    data class LoadUrl(val url: String) : WebCommand {
        override val logName get() = "LoadUrl"
    }

    data object ClearCache : WebCommand {
        override val logName = "ClearCache"
    }

    /** Honoured in debug builds only. */
    data object SimulateRendererCrash : WebCommand {
        override val logName = "SimulateRendererCrash"
    }

    /** Honoured in debug builds only. */
    data object SimulateRendererHang : WebCommand {
        override val logName = "SimulateRendererHang"
    }
}

/**
 * App-wide, buffered command channel. Commands are buffered until the dashboard collects them, so a
 * command sent while no collector is active is delivered later instead of being dropped.
 */
class WebCommandBus {
    private val channel = Channel<WebCommand>(capacity = CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val commands: Flow<WebCommand> = channel.receiveAsFlow()

    fun send(command: WebCommand) {
        channel.trySend(command)
    }

    private companion object {
        const val CAPACITY = 16
    }
}
