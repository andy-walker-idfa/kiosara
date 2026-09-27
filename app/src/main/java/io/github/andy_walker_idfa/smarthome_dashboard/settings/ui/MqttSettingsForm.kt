package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.UrlValidator

/** Editable text form of [MqttSettings]. Kept free of Android types so the validation can be unit-tested. */
data class MqttSettingsForm(
    val enabled: Boolean,
    /** Broker address: `host` or `host:port` (port 1883 when omitted). */
    val address: String,
    val username: String,
    /** New password; blank keeps the stored one. */
    val newPassword: String
) {
    sealed interface Result {
        /** [newPassword] is null when the stored password should be kept. */
        data class Valid(val settings: MqttSettings, val newPassword: String?) : Result

        /** The address is invalid (or missing while the integration is on). */
        data object InvalidAddress : Result
    }

    fun validate(): Result {
        val parsed = parseAddress(address)
        // The address is required only while the integration is on; a typed address must always be valid.
        if (parsed == null && (enabled || address.isNotBlank())) return Result.InvalidAddress
        val (host, port) = parsed ?: ("" to MqttSettings.DEFAULT_PORT)
        return Result.Valid(
            MqttSettings(enabled = enabled, host = host, port = port, username = username.trim()),
            newPassword.takeIf { it.isNotEmpty() }
        )
    }

    companion object {
        fun from(mqtt: MqttSettings) = MqttSettingsForm(
            enabled = mqtt.enabled,
            address = formatAddress(mqtt.host, mqtt.port),
            username = mqtt.username,
            newPassword = ""
        )

        /** `host` for the default port, otherwise `host:port`; blank while no host is set. */
        fun formatAddress(host: String, port: Int): String = when {
            host.isBlank() -> ""
            port == MqttSettings.DEFAULT_PORT -> host
            else -> "$host:$port"
        }

        /**
         * Parses `host` or `host:port` (an optional `mqtt://` or `tcp://` prefix is ignored). Returns null for a
         * blank or invalid address.
         */
        fun parseAddress(text: String): Pair<String, Int>? {
            val value = text.trim().removePrefix("mqtt://").removePrefix("tcp://").trimEnd('/')
            if (value.isEmpty()) return null
            val colon = value.lastIndexOf(':')
            val host = if (colon < 0) value else value.substring(0, colon)
            val port =
                if (colon < 0) {
                    MqttSettings.DEFAULT_PORT
                } else {
                    value.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..MqttSettings.MAX_PORT } ?: return null
                }
            if (host.isEmpty() || !UrlValidator.isValidHostOrBlank(host)) return null
            return host.lowercase() to port
        }
    }
}
