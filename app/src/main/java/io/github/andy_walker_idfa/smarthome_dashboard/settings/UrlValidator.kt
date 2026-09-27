package io.github.andy_walker_idfa.smarthome_dashboard.settings

import java.net.URI
import java.net.URISyntaxException

/** Validation and normalisation for URLs typed into settings. */
object UrlValidator {
    /**
     * Returns the normalised URL, or null if [input] isn't a usable http(s) URL.
     * A missing scheme defaults to `http://`, which is how HA is reached on the LAN.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri =
            try {
                URI(withScheme)
            } catch (_: URISyntaxException) {
                return null
            }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

    /** Extracts the lower-cased host of [url], or null if it has none. */
    fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase()
    } catch (_: URISyntaxException) {
        null
    }

    /** Validates a bare host name or IPv4 address (the MQTT broker). Blank is allowed. */
    fun isValidHostOrBlank(host: String): Boolean {
        if (host.isBlank()) return true
        return HOST_PATTERN.matches(host.trim())
    }

    private val HOST_PATTERN = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$")
}
