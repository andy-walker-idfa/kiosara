package io.github.andy_walker_idfa.smarthome_dashboard.settings

import kotlinx.serialization.Serializable

/**
 * Root settings object, persisted as JSON. Secrets never live here; they go in
 * [io.github.andy_walker_idfa.smarthome_dashboard.security.SecretStore].
 *
 * Every field has a default so older files decode cleanly; keys of removed settings are ignored (0.8.0 removed
 * everything a normal user doesn't set up: user principle "fewer settings are always preferred"). Bump
 * [CURRENT_SCHEMA_VERSION] and add a step to [SettingsSerializer.migrate] only when a change can't be expressed by
 * defaults alone (for example a renamed or re-interpreted field).
 */
@Serializable
data class Settings(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val device: DeviceSettings = DeviceSettings(),
    val web: WebSettings = WebSettings(),
    val mqtt: MqttSettings = MqttSettings(),
    val screen: ScreenSettings = ScreenSettings(),
    val security: SecuritySettings = SecuritySettings()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Identity of this panel towards Home Assistant (MQTT discovery unique_ids, device identifiers, default
 * topics). Deliberately independent of the package name and the display name, so a rebrand never
 * changes the entities in HA.
 */
@Serializable
data class DeviceSettings(
    /** Filled on first start by `DeviceIdentity`; blank only until then. */
    val id: String = ""
) {
    companion object {
        /** Device name in Home Assistant (fixed; rename the device in Home Assistant if you like). */
        const val DEFAULT_NAME = "Wall panel"
    }
}

/**
 * MQTT connection to the user's broker (Home Assistant discovery). The password is not here: it is
 * stored in [io.github.andy_walker_idfa.smarthome_dashboard.security.SecretStore] under
 * [PASSWORD_SECRET_KEY]. Topics are fixed (Home Assistant's defaults; see `MqttNaming`).
 */
@Serializable
data class MqttSettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val username: String = ""
) {
    val isConfigured: Boolean get() = host.isNotBlank() && port in 1..MAX_PORT

    companion object {
        const val DEFAULT_PORT = 1883
        const val MAX_PORT = 65535
        const val PASSWORD_SECRET_KEY = "mqtt_password"
    }
}

/**
 * The dashboard. Fixed behaviour (no settings): landscape either way up, the page decides its scale, the WebView's
 * own user agent, media may autoplay, no periodic reload (the watchdog handles frozen pages), back to the start page
 * when night starts.
 */
@Serializable
data class WebSettings(
    /** Blank until the user enters their Home Assistant URL ("Not set up" screen). */
    val startUrl: String = "",
    val textZoomPercent: Int = 100,
    /**
     * Colon-separated upper-case hex SHA-256 of a pinned self-signed certificate for the start URL's host; set via
     * "Trust this certificate" on the error screen. Blank = none.
     */
    val trustedCertSha256: String = ""
)

/**
 * Brightness and night mode. Brightness and the night state can also be changed from Home Assistant.
 * Fixed behaviour (no settings): at night the screen is black; a touch wakes it for [NIGHT_WAKE_SECONDS]; the
 * dashboard is paused while black.
 */
@Serializable
data class ScreenSettings(
    val brightnessMode: BrightnessMode = BrightnessMode.SYSTEM,
    /** Used in [BrightnessMode.MANUAL]. */
    val manualBrightnessPercent: Int = DEFAULT_MANUAL_PERCENT,
    val nightEnabled: Boolean = false,
    /** Local time, minutes after midnight. Start == end means never night. */
    val nightStartMinutes: Int = DEFAULT_NIGHT_START,
    val nightEndMinutes: Int = DEFAULT_NIGHT_END
) {
    companion object {
        const val MAX_PERCENT = 100
        const val DEFAULT_MANUAL_PERCENT = 60
        const val DEFAULT_NIGHT_START = 22 * 60
        const val DEFAULT_NIGHT_END = 6 * 60 + 30
        const val MINUTES_PER_DAY = 24 * 60

        /** After a touch at night, the screen stays on this long before it goes black again. */
        const val NIGHT_WAKE_SECONDS = 60
    }
}

/**
 * Settings files written by 0.4.0 may contain `AUTO` (the removed app-side automatic brightness); with
 * `coerceInputValues` an unknown value decodes to the default, [SYSTEM], whose Android adaptive brightness
 * replaces it.
 */
@Serializable
enum class BrightnessMode {
    /** Follow Android's brightness, including its adaptive brightness (the app changes nothing). */
    SYSTEM,

    /** A fixed level ([ScreenSettings.manualBrightnessPercent]); Home Assistant's brightness sets this. */
    MANUAL
}

/**
 * Settings protection. The PIN hash itself is in the SecretStore ([PIN_SECRET_KEY]); this flag records that a PIN
 * was set, so a PIN that can no longer be read is noticed (and the owner is let in with a notice: fail open).
 */
@Serializable
data class SecuritySettings(
    val pinEnabled: Boolean = false,
    /**
     * Kiosk lock (Lock Task Mode). Effective only while the app is device owner and a PIN is set; switched off
     * automatically when the PIN goes away. Persists until the user switches it on again (off by default).
     */
    val kioskLock: Boolean = false
) {
    companion object {
        const val PIN_SECRET_KEY = "settings_pin"
    }
}
