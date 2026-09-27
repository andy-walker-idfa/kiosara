package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatus
import io.github.andy_walker_idfa.smarthome_dashboard.display.ScreenMode
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatus
import io.github.andy_walker_idfa.smarthome_dashboard.network.WifiDetails
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryRecord
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.BatteryState
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A state to publish: the payload, an optional numeric value (for deadbands) and optional JSON attributes. */
data class EntityValue(val state: String, val numeric: Double? = null, val attributes: JsonObject? = null)

/** Values that are sampled only on refresh (not observed). */
data class SystemSample(
    val freeMemoryBytes: Long,
    val freeStorageBytes: Long,
    val appMemoryBytes: Long,
    val bootTimeMillis: Long,
    val deviceOwner: Boolean
)

data class StaticInfo(
    val appVersion: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val appStartedMillis: Long
)

data class StateInputs(
    val battery: BatteryState?,
    val wifi: WifiDetails,
    val dashboard: DashboardStatus,
    val display: DisplayStatus,
    val screen: ScreenSettings,
    val lastRecovery: RecoveryRecord?,
    val kiosk: KioskStatus,
    val web: WebSettings,
    val sample: SystemSample,
    val static: StaticInfo
)

/** Pure mapping from app state to HA entity payloads. Unit-tested. */
object EntityStates {
    /** HA MQTT treats this payload as "unknown". */
    const val NONE = "None"
    private const val ON = "ON"
    private const val OFF = "OFF"
    private const val HA_MAX_STATE_LENGTH = 255
    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val BYTES_PER_GB = BYTES_PER_MB * 1024.0

    /** Query parameters that may carry credentials (HA puts login codes in `code`/`state`). */
    private val SENSITIVE_QUERY_KEYS = setOf("code", "state", "access_token", "token")

    fun compute(inputs: StateInputs): Map<String, EntityValue> {
        val out = linkedMapOf<String, EntityValue>()
        inputs.battery?.let { b ->
            out[Entities.BATTERY_LEVEL] = EntityValue(b.levelPercent.toString(), b.levelPercent.toDouble())
            out[Entities.CHARGING] = EntityValue(onOff(b.charging))
            out[Entities.PLUG_TYPE] = EntityValue(b.plugType.value)
            out[Entities.BATTERY_TEMPERATURE] = EntityValue(fmt(b.temperatureCelsius, 1), b.temperatureCelsius)
            out[Entities.BATTERY_HEALTH] = EntityValue(b.health.value)
            out[Entities.BATTERY_VOLTAGE] = EntityValue(fmt(b.voltageVolts, 2), b.voltageVolts)
        }
        out[Entities.WIFI_RSSI] =
            inputs.wifi.rssiDbm?.let { EntityValue(it.toString(), it.toDouble()) } ?: EntityValue(NONE)
        out[Entities.IP_ADDRESS] = EntityValue(inputs.wifi.ipAddress ?: NONE)

        val boot = inputs.sample.bootTimeMillis
        out[Entities.LAST_BOOT] = EntityValue(iso(boot), boot / 1000.0)
        out[Entities.APP_STARTED] = EntityValue(iso(inputs.static.appStartedMillis))
        val freeMb = inputs.sample.freeMemoryBytes / BYTES_PER_MB
        out[Entities.FREE_MEMORY] = EntityValue(freeMb.toLong().toString(), freeMb)
        val appMb = inputs.sample.appMemoryBytes / BYTES_PER_MB
        out[Entities.APP_MEMORY] = EntityValue(appMb.toLong().toString(), appMb)
        val freeGb = inputs.sample.freeStorageBytes / BYTES_PER_GB
        out[Entities.FREE_STORAGE] = EntityValue(fmt(freeGb, 1), freeGb)
        out[Entities.APP_VERSION] = EntityValue(inputs.static.appVersion)
        out[Entities.ANDROID_VERSION] =
            EntityValue(
                inputs.static.androidVersion,
                attributes = buildJsonObject {
                    put("sdk", inputs.static.sdkInt)
                    put("security_patch", inputs.static.securityPatch)
                }
            )
        out[Entities.DEVICE_OWNER] = EntityValue(onOff(inputs.kiosk.deviceOwner || inputs.sample.deviceOwner))

        val url = inputs.dashboard.currentUrl?.let(::sanitizeUrl)
        // State: address without query/fragment, so HA shows it compactly (long states break its layout).
        // Attributes: the full address (credentials removed).
        out[Entities.CURRENT_URL] =
            EntityValue(
                url?.let(::displayUrl)?.take(HA_MAX_STATE_LENGTH) ?: NONE,
                attributes = buildJsonObject {
                    put("url", url)
                    put("dashboard_visible", inputs.dashboard.active)
                }
            )
        val error = inputs.dashboard.pageError
        out[Entities.PAGE_ERROR] =
            EntityValue(
                onOff(error != null),
                attributes = buildJsonObject {
                    if (error != null) {
                        put("kind", error.kind)
                        put("description", error.description)
                        put("url", sanitizeUrl(error.url))
                        put("since", iso(error.sinceMillis))
                    }
                }
            )
        val viewport = inputs.dashboard.viewport
        out[Entities.VIEWPORT] =
            if (viewport == null) {
                EntityValue(NONE)
            } else {
                EntityValue(
                    "${viewport.cssWidth}x${viewport.cssHeight}",
                    attributes = buildJsonObject {
                        put("css_width", viewport.cssWidth)
                        put("css_height", viewport.cssHeight)
                        put("width_px", viewport.widthPx)
                        put("height_px", viewport.heightPx)
                        put("density_dpi", viewport.densityDpi)
                        put("device_pixel_ratio", viewport.devicePixelRatio)
                    }
                )
            }
        out[Entities.LAST_INTERACTION] =
            inputs.dashboard.lastInteractionMillis?.let { EntityValue(iso(it), it / 1000.0) } ?: EntityValue(NONE)
        putDisplay(out, inputs)
        val kiosk = inputs.kiosk
        // HA's "lock" device class: ON means unlocked, OFF means locked.
        out[Entities.KIOSK_LOCK] =
            EntityValue(
                onOff(!kiosk.lockActive),
                attributes = buildJsonObject {
                    put("enabled", kiosk.lockEnabled)
                    put("unlocked_until", kiosk.unlockedUntilMillis?.let(::iso))
                }
            )
        val recovery = inputs.lastRecovery
        out[Entities.LAST_RECOVERY] =
            if (recovery == null) {
                EntityValue(NONE, attributes = buildJsonObject { })
            } else {
                EntityValue(
                    iso(recovery.timeMillis),
                    attributes = buildJsonObject {
                        put("kind", recovery.kind.value)
                        put("detail", recovery.detail)
                    }
                )
            }
        return out
    }

    private fun putDisplay(out: MutableMap<String, EntityValue>, inputs: StateInputs) {
        val display = inputs.display
        val screen = inputs.screen
        // The level of the normal screen (manual or system).
        out[Entities.SCREEN_BRIGHTNESS] = EntityValue(display.brightnessPercent.toString())
        out[Entities.SCREEN_STATE] = EntityValue(display.mode.value)
        out[Entities.NIGHT_MODE] = EntityValue(onOff(display.night))
    }

    /** The URL without query string and fragment. */
    fun displayUrl(url: String): String = url.substringBefore('#').substringBefore('?')

    /** Removes query parameters that may carry login codes or tokens. */
    fun sanitizeUrl(url: String): String {
        val hash = url.indexOf('#')
        val fragment = if (hash >= 0) url.substring(hash) else ""
        val withoutFragment = if (hash >= 0) url.substring(0, hash) else url
        val question = withoutFragment.indexOf('?')
        if (question < 0) return url
        val kept =
            withoutFragment.substring(question + 1)
                .split('&')
                .filter { it.isNotEmpty() && it.substringBefore('=').lowercase() !in SENSITIVE_QUERY_KEYS }
        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return withoutFragment.substring(0, question) + query + fragment
    }

    fun iso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).truncatedTo(ChronoUnit.SECONDS).toString()

    private fun onOff(value: Boolean) = if (value) ON else OFF

    private fun fmt(value: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", value)
}

/**
 * Decides which values need publishing: everything on a full refresh, otherwise only changes. Numeric
 * entities with a deadband publish only when they moved at least that much since the last publish.
 */
class StateDiffer {
    private val lastPublished = mutableMapOf<String, EntityValue>()

    fun changes(snapshot: Map<String, EntityValue>, force: Boolean): Map<String, EntityValue> {
        val out = snapshot.filter { (id, value) -> force || isSignificant(id, lastPublished[id], value) }
        lastPublished.putAll(out)
        return out
    }

    /** Forget everything, e.g. after a reconnect (the next publish is a full one anyway). */
    fun reset() = lastPublished.clear()

    private fun isSignificant(id: String, old: EntityValue?, new: EntityValue): Boolean {
        if (old == null || old.attributes != new.attributes) return true
        val deadband = Entities.byId[id]?.deadband
        if (deadband != null && old.numeric != null && new.numeric != null) {
            return abs(new.numeric - old.numeric) >= deadband
        }
        return old.state != new.state
    }
}
