package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayCommands
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.UrlValidator
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommand
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import kotlin.math.roundToInt

/**
 * Remote "Load URL" policy. Commands arrive from the network, so only explicit http(s) URLs are
 * accepted, and only on the start URL's host.
 */
object RemoteUrlPolicy {
    /** Returns the URL to load, or null if it must be rejected. */
    fun check(url: String, web: WebSettings, allowAnyHost: Boolean): String? {
        val trimmed = url.trim()
        // Require an explicit scheme: never guess for remote input.
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val normalized = UrlValidator.normalize(trimmed) ?: return null
        if (allowAnyHost) return normalized
        val host = UrlValidator.hostOf(normalized) ?: return null
        val configuredUrls = listOf(web.startUrl)
        val allowedHosts = configuredUrls.mapNotNull(UrlValidator::hostOf).toSet()
        return normalized.takeIf { host in allowedHosts }
    }
}

/** App-level actions from Home Assistant. */
fun interface AppControl {
    /** Restarts the app; returns false if that isn't possible now (the dashboard isn't visible). */
    fun restartApp(): Boolean
}

/**
 * Maps MQTT command topics to app actions. Only the range-checked brightness is written into settings; URLs from
 * `load_url` (debug builds only) never are, and they must point to the start URL's host.
 */
class MqttCommandHandler(
    private val webCommands: WebCommandBus,
    private val display: DisplayCommands,
    private val app: AppControl = AppControl { false },
    private val catalog: EntityCatalog = EntityCatalog(full = true),
    private val currentSettings: () -> Settings
) {
    /** Returns true if the command was accepted. */
    fun handle(objectId: String, payload: String): Boolean {
        val settings = currentSettings()
        val accepted =
            when (objectId) {
                // Not published in this build (release: the small entity set).
                !in catalog.publishedIds -> false

                Entities.RELOAD -> webCommands.send(WebCommand.ReloadPage).let { true }

                Entities.GO_HOME -> webCommands.send(WebCommand.LoadStartUrl).let { true }

                Entities.CLEAR_CACHE -> webCommands.send(WebCommand.ClearCache).let { true }

                Entities.SCREEN_BRIGHTNESS ->
                    intIn(payload, 0..ScreenSettings.MAX_PERCENT)?.let(display::setBrightnessPercent) != null

                Entities.RESTART_APP -> app.restartApp()

                Entities.NIGHT_MODE -> onOff(payload)?.let(display::setNight) == true

                Entities.LOAD_URL -> {
                    val url = RemoteUrlPolicy.check(payload, settings.web, allowAnyHost = false)
                    url?.let { webCommands.send(WebCommand.LoadUrl(it)) } != null
                }

                else -> false
            }
        // Log the command name only: payloads may contain URLs. The name comes from the topic, which anyone with
        // broker access can choose, so anything that isn't a plain object id is not logged verbatim.
        val name = objectId.takeIf { SAFE_OBJECT_ID.matches(it) } ?: "(invalid name)"
        if (accepted) AppLog.i(TAG, "Command $name") else AppLog.w(TAG, "Rejected command $name")
        return accepted
    }

    private fun intIn(payload: String, range: IntRange): Int? =
        payload.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.roundToInt()?.takeIf { it in range }

    private fun onOff(payload: String): Boolean? = when (payload.trim().uppercase()) {
        "ON" -> true
        "OFF" -> false
        else -> null
    }

    private companion object {
        const val TAG = "MqttCommand"
        val SAFE_OBJECT_ID = Regex("[a-z0-9_]{1,40}")
    }
}
