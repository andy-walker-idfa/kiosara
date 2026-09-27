package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings

/**
 * Everything editable on the Settings screen, saved together with one Save button. The pinned certificate is
 * changed from the error screen and is not part of the draft. Kept free of Android types so validation and dirty
 * tracking can be unit-tested.
 */
data class SettingsDraft(val web: WebSettingsForm, val mqtt: MqttSettingsForm, val screen: ScreenSettingsForm) {
    /** Screen sections in screen order, used to scroll to the first invalid field. */
    enum class Section { DASHBOARD, INTEGRATION, SCREEN }

    enum class Field(val section: Section) {
        START_URL(Section.DASHBOARD),
        TEXT_ZOOM(Section.DASHBOARD),
        MQTT_ADDRESS(Section.INTEGRATION),
        MQTT_USERNAME(Section.INTEGRATION),
        MQTT_PASSWORD(Section.INTEGRATION),
        MANUAL_BRIGHTNESS(Section.SCREEN),
        NIGHT_START(Section.SCREEN),
        NIGHT_END(Section.SCREEN)
    }

    sealed interface Result {
        /** [newPassword] is null when the stored password is kept. */
        data class Valid(val settings: Settings, val newPassword: String?) : Result

        data class Invalid(val fields: Set<Field>) : Result {
            /** The section to scroll to (screen order). */
            val firstSection: Section get() = fields.minOf { it.section }
        }
    }

    /**
     * Validates everything. Required: the Home Assistant URL always; broker address, username and password
     * only while the Home Assistant integration is switched on (a stored password counts).
     */
    fun validate(base: Settings, hasStoredPassword: Boolean): Result {
        val invalid = mutableSetOf<Field>()

        val webResult = web.toSettings(base.web)
        if (webResult is WebSettingsForm.Result.Invalid) invalid += webResult.fields.map(::webField)

        val mqttResult = mqtt.validate()
        if (mqttResult is MqttSettingsForm.Result.InvalidAddress) invalid += Field.MQTT_ADDRESS
        if (mqtt.enabled) {
            if (mqtt.username.isBlank()) invalid += Field.MQTT_USERNAME
            if (mqtt.newPassword.isEmpty() && !hasStoredPassword) invalid += Field.MQTT_PASSWORD
        }

        val screenResult = screen.toSettings(base.screen)
        if (screenResult is ScreenSettingsForm.Result.Invalid) invalid += screenResult.fields.map(::screenField)

        if (invalid.isNotEmpty()) return Result.Invalid(invalid)
        val validMqtt = mqttResult as MqttSettingsForm.Result.Valid
        return Result.Valid(
            base.copy(
                web = (webResult as WebSettingsForm.Result.Valid).settings,
                mqtt = validMqtt.settings,
                screen = (screenResult as ScreenSettingsForm.Result.Valid).settings
            ),
            validMqtt.newPassword
        )
    }

    /** True if saving would change something (a newly typed password counts). */
    fun isDirty(base: Settings): Boolean = this != from(base)

    companion object {
        fun from(settings: Settings) = SettingsDraft(
            web = WebSettingsForm.from(settings.web),
            mqtt = MqttSettingsForm.from(settings.mqtt),
            screen = ScreenSettingsForm.from(settings.screen)
        )

        private fun webField(field: WebSettingsForm.Field): Field = when (field) {
            WebSettingsForm.Field.START_URL -> Field.START_URL
            WebSettingsForm.Field.TEXT_ZOOM -> Field.TEXT_ZOOM
        }

        private fun screenField(field: ScreenSettingsForm.Field): Field = when (field) {
            ScreenSettingsForm.Field.MANUAL_BRIGHTNESS -> Field.MANUAL_BRIGHTNESS
            ScreenSettingsForm.Field.NIGHT_START -> Field.NIGHT_START
            ScreenSettingsForm.Field.NIGHT_END -> Field.NIGHT_END
        }
    }
}
