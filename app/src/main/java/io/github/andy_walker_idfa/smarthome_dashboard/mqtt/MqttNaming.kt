package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

/**
 * All MQTT topics and Home Assistant identifiers, derived only from the device id,
 * never from the package or display name. See the `ha-mqtt-discovery` skill.
 */
class MqttNaming(deviceId: String) {
    init {
        require(deviceId.length >= SHORT_ID_LENGTH) { "device id not initialised" }
    }

    val shortId: String = deviceId.take(SHORT_ID_LENGTH)

    /** Device identifier in HA, MQTT client id and node id of the discovery topic. */
    val nodeId: String = "$PROTOCOL_PREFIX$shortId"

    // Fixed since 0.8.0 (no settings): Home Assistant's defaults.
    val baseTopic: String = "$PROTOCOL_PREFIX_TOPIC/$shortId"

    val discoveryPrefix: String = DISCOVERY_PREFIX

    val statusTopic: String = "$discoveryPrefix/status"

    val availabilityTopic: String = "$baseTopic/availability"

    val discoveryTopic: String = "$discoveryPrefix/device/$nodeId/config"

    /** Subscription for all command topics. */
    val commandFilter: String = "$baseTopic/set/+"

    fun stateTopic(objectId: String) = "$baseTopic/state/$objectId"

    fun commandTopic(objectId: String) = "$baseTopic/set/$objectId"

    fun attributesTopic(objectId: String) = "$baseTopic/attr/$objectId"

    fun uniqueId(objectId: String) = "${nodeId}_$objectId"

    /** Returns the object id if [topic] is one of this device's command topics. */
    fun objectIdOfCommand(topic: String): String? = topic.removePrefix("$baseTopic/set/").takeIf {
        it != topic &&
            '/' !in it &&
            it.isNotEmpty()
    }

    companion object {
        const val SHORT_ID_LENGTH = 12
        const val DISCOVERY_PREFIX = "homeassistant"

        /** Fixed protocol prefix. Not the brand; must never change. */
        private const val PROTOCOL_PREFIX = "shdash_"
        private const val PROTOCOL_PREFIX_TOPIC = "shdash"
    }
}
