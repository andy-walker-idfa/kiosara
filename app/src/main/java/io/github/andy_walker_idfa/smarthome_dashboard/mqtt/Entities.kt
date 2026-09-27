package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.display.ScreenMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Home Assistant MQTT platforms used by this app. */
enum class Platform(val value: String) {
    SENSOR("sensor"),
    BINARY_SENSOR("binary_sensor"),
    NUMBER("number"),
    BUTTON("button"),
    TEXT("text"),
    SELECT("select"),
    SWITCH("switch")
}

/**
 * Definition of one HA entity. [extra] holds platform-specific discovery keys. [deadband] suppresses
 * publishing a numeric change smaller than this (full refreshes always publish).
 */
data class EntityDef(
    val id: String,
    val platform: Platform,
    val name: String,
    val deviceClass: String? = null,
    val stateClass: String? = null,
    val unit: String? = null,
    val diagnostic: Boolean = false,
    val config: Boolean = false,
    val icon: String? = null,
    val hasAttributes: Boolean = false,
    val deadband: Double? = null,
    /** HA `suggested_display_precision` (decimals shown in the UI); set for every numeric sensor. */
    val precision: Int? = null,
    /** Entity has a state topic of its own (buttons don't). */
    val hasState: Boolean = true,
    /** State comes from another entity's state topic (e.g. load_url shows current_url). */
    val stateFrom: String? = null,
    val acceptsCommands: Boolean = false,
    val extra: Map<String, Any> = emptyMap()
)

/**
 * The entity set (Phase 2 + Phase 3 display entities). Object ids are part of the HA unique_ids: never rename one; add a new id instead.
 * Keep this table in sync with the `ha-mqtt-discovery` skill and docs/home-assistant-setup.md.
 */
object Entities {
    const val BATTERY_LEVEL = "battery_level"
    const val CHARGING = "charging"
    const val PLUG_TYPE = "plug_type"
    const val BATTERY_TEMPERATURE = "battery_temperature"
    const val BATTERY_HEALTH = "battery_health"
    const val BATTERY_VOLTAGE = "battery_voltage"
    const val WIFI_RSSI = "wifi_rssi"
    const val IP_ADDRESS = "ip_address"
    const val LAST_BOOT = "last_boot"
    const val APP_STARTED = "app_started"
    const val FREE_MEMORY = "free_memory"
    const val FREE_STORAGE = "free_storage"
    const val APP_MEMORY = "app_memory"
    const val APP_VERSION = "app_version"
    const val ANDROID_VERSION = "android_version"
    const val DEVICE_OWNER = "device_owner"
    const val CURRENT_URL = "current_url"
    const val PAGE_ERROR = "page_error"
    const val LAST_INTERACTION = "last_interaction"
    const val VIEWPORT = "viewport"
    const val SCREEN_BRIGHTNESS = "screen_brightness"
    const val RELOAD = "reload"
    const val GO_HOME = "go_home"
    const val CLEAR_CACHE = "clear_cache"
    const val LOAD_URL = "load_url"
    const val RESTART_APP = "restart_app"
    const val LAST_RECOVERY = "last_recovery"
    const val KIOSK_LOCK = "kiosk_lock"
    const val SCREEN_STATE = "screen_state"
    const val NIGHT_MODE = "night_mode"

    val all: List<EntityDef> =
        listOf(
            EntityDef(BATTERY_LEVEL, Platform.SENSOR, "Battery", "battery", "measurement", "%", precision = 0),
            EntityDef(CHARGING, Platform.BINARY_SENSOR, "Charging", "battery_charging"),
            EntityDef(
                PLUG_TYPE,
                Platform.SENSOR,
                "Power source",
                "enum",
                diagnostic = true,
                extra = mapOf("options" to listOf("ac", "usb", "wireless", "dock", "none"))
            ),
            EntityDef(
                BATTERY_TEMPERATURE,
                Platform.SENSOR,
                "Battery temperature",
                "temperature",
                "measurement",
                "°C",
                deadband = 0.5,
                precision = 1
            ),
            EntityDef(
                BATTERY_HEALTH,
                Platform.SENSOR,
                "Battery health",
                "enum",
                diagnostic = true,
                extra = mapOf(
                    "options" to listOf("good", "overheat", "dead", "over_voltage", "failure", "cold", "unknown")
                )
            ),
            EntityDef(
                BATTERY_VOLTAGE, Platform.SENSOR, "Battery voltage", "voltage", "measurement", "V",
                diagnostic = true, deadband = 0.05, precision = 2
            ),
            EntityDef(
                WIFI_RSSI,
                Platform.SENSOR,
                "Wi-Fi signal",
                "signal_strength",
                "measurement",
                "dBm",
                diagnostic = true,
                deadband = 3.0,
                precision = 0
            ),
            EntityDef(IP_ADDRESS, Platform.SENSOR, "IP address", diagnostic = true, icon = "mdi:ip-network"),
            // Computed as now - elapsedRealtime, so it jitters; republish only on a real change (> 5 s).
            EntityDef(LAST_BOOT, Platform.SENSOR, "Last boot", "timestamp", diagnostic = true, deadband = 5.0),
            EntityDef(APP_STARTED, Platform.SENSOR, "App started", "timestamp", diagnostic = true),
            EntityDef(
                FREE_MEMORY, Platform.SENSOR, "Free memory", "data_size", "measurement", "MB",
                diagnostic = true, icon = "mdi:memory", deadband = 5.0, precision = 0
            ),
            EntityDef(
                FREE_STORAGE, Platform.SENSOR, "Free storage", "data_size", "measurement", "GB",
                diagnostic = true, deadband = 0.1, precision = 0
            ),
            // The app's own memory (PSS), sampled on refresh. For the soak test: a steady rise means a leak and
            // decides whether the postponed daily WebView restart is needed.
            EntityDef(
                APP_MEMORY, Platform.SENSOR, "App memory", "data_size", "measurement", "MB",
                diagnostic = true, icon = "mdi:memory", deadband = 5.0, precision = 0
            ),
            EntityDef(APP_VERSION, Platform.SENSOR, "App version", diagnostic = true, icon = "mdi:application"),
            EntityDef(
                ANDROID_VERSION,
                Platform.SENSOR,
                "Android version",
                diagnostic = true,
                icon = "mdi:android",
                hasAttributes = true
            ),
            EntityDef(
                DEVICE_OWNER,
                Platform.BINARY_SENSOR,
                "Device owner",
                diagnostic = true,
                icon = "mdi:shield-lock"
            ),
            EntityDef(
                CURRENT_URL,
                Platform.SENSOR,
                "Current URL",
                diagnostic = true,
                icon = "mdi:web",
                hasAttributes = true
            ),
            EntityDef(
                PAGE_ERROR,
                Platform.BINARY_SENSOR,
                "Page error",
                "problem",
                diagnostic = true,
                hasAttributes = true
            ),
            // "1072x640" in CSS pixels; details (px, dpi, device pixel ratio) in the attributes.
            EntityDef(
                VIEWPORT,
                Platform.SENSOR,
                "Dashboard viewport",
                diagnostic = true,
                icon = "mdi:monitor-screenshot",
                hasAttributes = true
            ),
            // Throttled to one update per minute (numeric value = epoch seconds).
            EntityDef(
                LAST_INTERACTION,
                Platform.SENSOR,
                "Last interaction",
                "timestamp",
                icon = "mdi:gesture-tap",
                deadband = 60.0
            ),
            EntityDef(
                SCREEN_BRIGHTNESS,
                Platform.NUMBER,
                "Screen brightness",
                unit = "%",
                icon = "mdi:brightness-6",
                acceptsCommands = true,
                extra = mapOf("min" to 0, "max" to 100, "step" to 1, "mode" to "slider")
            ),
            EntityDef(RELOAD, Platform.BUTTON, "Reload", icon = "mdi:reload", hasState = false, acceptsCommands = true),
            EntityDef(
                GO_HOME,
                Platform.BUTTON,
                "Go to start page",
                icon = "mdi:home",
                hasState = false,
                acceptsCommands = true
            ),
            EntityDef(
                CLEAR_CACHE,
                Platform.BUTTON,
                "Clear web cache",
                config = true,
                icon = "mdi:broom",
                hasState = false,
                acceptsCommands = true
            ),
            EntityDef(
                LOAD_URL,
                Platform.TEXT,
                "Load URL",
                config = true,
                icon = "mdi:web-plus",
                stateFrom = CURRENT_URL,
                acceptsCommands = true,
                extra = mapOf("mode" to "text", "max" to 255, "pattern" to "^https?://.+")
            ),
            // Phase 4a: recovery.
            EntityDef(
                RESTART_APP,
                Platform.BUTTON,
                "Restart app",
                config = true,
                icon = "mdi:restart",
                hasState = false,
                acceptsCommands = true
            ),
            // State: time of the last automatic recovery; attributes `kind` and `detail`.
            EntityDef(
                LAST_RECOVERY,
                Platform.SENSOR,
                "Last recovery",
                "timestamp",
                diagnostic = true,
                icon = "mdi:lifebuoy",
                hasAttributes = true
            ),
            // Phase 4b: kiosk lock state only; deliberately no command (HA must not be able to unlock).
            EntityDef(
                KIOSK_LOCK,
                Platform.BINARY_SENSOR,
                "Kiosk lock",
                "lock",
                diagnostic = true,
                hasAttributes = true
            ),
            // Phase 3: screen, screensaver, night mode.
            EntityDef(
                SCREEN_STATE,
                Platform.SENSOR,
                "Screen",
                "enum",
                icon = "mdi:monitor",
                extra = mapOf("options" to ScreenMode.haOptions)
            ),
            EntityDef(
                NIGHT_MODE,
                Platform.SWITCH,
                "Night mode",
                icon = "mdi:weather-night",
                acceptsCommands = true,
                extra = mapOf("optimistic" to false)
            )
        )

    val byId: Map<String, EntityDef> = all.associateBy { it.id }

    /**
     * Entities that existed in an earlier version and were removed, with their platform. Discovery lists each
     * as `{"platform": ...}`, which makes Home Assistant delete the entity; their retained states are cleared.
     * Kept permanently (it is harmless for panels that never had them), so a panel updated from any older
     * version is cleaned up. Their object ids must never be reused.
     */
    val removed: Map<String, Platform> =
        mapOf(
            // 0.4.0 only: app-side automatic brightness from the light sensor (removed in 0.4.1).
            "ambient_light" to Platform.SENSOR,
            "auto_brightness" to Platform.SWITCH,
            // 0.8.0: daytime screensaver, night display choice and additional dashboards dropped (user principle).
            "screensaver" to Platform.SWITCH,
            "screensaver_timeout" to Platform.NUMBER,
            "screensaver_brightness" to Platform.NUMBER,
            "screensaver_mode" to Platform.SELECT,
            "night_display_mode" to Platform.SELECT,
            "dashboard" to Platform.SELECT,
            // 0.8.1: the "Wake screen" button existed only for the wake-on-motion blueprint (removed, user decision).
            "wake" to Platform.BUTTON
        )
}

/**
 * Which entities a build publishes (user principle 2026-09-27: "fewer entities are always preferred; troubleshooting
 * data belongs in debug builds only").
 * - **Release** ([full] = false): only [RELEASE_IDS]. Every other entity is *retired*: listed as a platform-only
 *   discovery component so Home Assistant deletes it, with its retained state and attributes cleared. Commands to
 *   retired entities are rejected.
 * - **Debug** ([full] = true): everything (debug builds are a separate HA device, see CLAUDE.md "Device identity").
 */
class EntityCatalog(val full: Boolean) {
    val published: List<EntityDef> = if (full) Entities.all else Entities.all.filter { it.id in RELEASE_IDS }

    val publishedIds: Set<String> = published.mapTo(mutableSetOf()) { it.id }

    /** Entities Home Assistant must delete: earlier removals plus, in release, everything not published. */
    val retired: Map<String, Platform> =
        Entities.removed + Entities.all.filter { it.id !in publishedIds }.associate { it.id to it.platform }

    /** Retired entities whose retained attributes topic must be cleared too. */
    val retiredWithAttributes: Set<String> =
        Entities.all.filter { it.id !in publishedIds && it.hasAttributes }.mapTo(mutableSetOf()) { it.id }

    fun acceptsCommand(objectId: String): Boolean =
        objectId in publishedIds && Entities.byId[objectId]?.acceptsCommands == true

    companion object {
        /** The release set: showing the dashboard, night mode, the battery (charging blueprint). */
        val RELEASE_IDS: Set<String> =
            setOf(
                Entities.BATTERY_LEVEL,
                Entities.BATTERY_TEMPERATURE,
                Entities.NIGHT_MODE,
                Entities.SCREEN_BRIGHTNESS,
                Entities.RELOAD
            )
    }
}

/** Builds the retained device-based discovery payload (HA 2024.11+). */
object Discovery {
    data class DeviceInfo(
        val name: String,
        val manufacturer: String,
        val model: String,
        val versionName: String,
        val channel: String
    )

    fun build(naming: MqttNaming, device: DeviceInfo, catalog: EntityCatalog = EntityCatalog(full = true)): JsonObject =
        buildJsonObject {
            put(
                "device",
                buildJsonObject {
                    put("identifiers", JsonArray(listOf(JsonPrimitive(naming.nodeId))))
                    put("name", device.name)
                    put("manufacturer", device.manufacturer)
                    put("model", device.model)
                    put("sw_version", "${device.versionName} (${device.channel})")
                }
            )
            put(
                "origin",
                buildJsonObject {
                    put("name", ORIGIN_NAME)
                    put("sw_version", device.versionName)
                }
            )
            put("availability_topic", naming.availabilityTopic)
            put("qos", 1)
            put(
                "components",
                buildJsonObject {
                    for (def in catalog.published) put(def.id, component(def, naming))
                    for ((id, platform) in catalog.retired) put(id, buildJsonObject { put("platform", platform.value) })
                }
            )
        }

    private fun component(def: EntityDef, naming: MqttNaming) = buildJsonObject {
        put("platform", def.platform.value)
        put("unique_id", naming.uniqueId(def.id))
        put("name", def.name)
        def.deviceClass?.let { put("device_class", it) }
        def.stateClass?.let { put("state_class", it) }
        def.unit?.let { put("unit_of_measurement", it) }
        def.icon?.let { put("icon", it) }
        def.precision?.let { put("suggested_display_precision", it) }
        when {
            def.diagnostic -> put("entity_category", "diagnostic")
            def.config -> put("entity_category", "config")
        }
        if (def.hasState) put("state_topic", naming.stateTopic(def.stateFrom ?: def.id))
        if (def.hasAttributes) put("json_attributes_topic", naming.attributesTopic(def.id))
        if (def.acceptsCommands) put("command_topic", naming.commandTopic(def.id))
        for ((key, value) in def.extra) {
            when (value) {
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                is List<*> -> put(key, JsonArray(value.map { JsonPrimitive(it.toString()) }))
                else -> error("Unsupported discovery value for $key")
            }
        }
    }

    const val ORIGIN_NAME = "Kiosara"
}
