package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.display.NightSchedule
import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ScreenSettings

/** Editable form of [ScreenSettings]. Kept free of Android types so the validation can be unit-tested. */
data class ScreenSettingsForm(
    val brightnessMode: BrightnessMode,
    val manualBrightnessPercent: String,
    val nightEnabled: Boolean,
    val nightStart: String,
    val nightEnd: String
) {
    enum class Field { MANUAL_BRIGHTNESS, NIGHT_START, NIGHT_END }

    sealed interface Result {
        data class Valid(val settings: ScreenSettings) : Result

        data class Invalid(val fields: Set<Field>) : Result
    }

    /** Validates the form and merges it into [base] (which supplies the fields not on this form). */
    fun toSettings(base: ScreenSettings): Result {
        val invalid = mutableSetOf<Field>()
        val manual =
            manualBrightnessPercent.trim().toIntOrNull()?.takeIf { it in 0..ScreenSettings.MAX_PERCENT } ?: run {
                invalid += Field.MANUAL_BRIGHTNESS
                0
            }
        val start = NightSchedule.parse(nightStart) ?: 0.also { invalid += Field.NIGHT_START }
        val end = NightSchedule.parse(nightEnd) ?: 0.also { invalid += Field.NIGHT_END }
        if (invalid.isNotEmpty()) return Result.Invalid(invalid)
        return Result.Valid(
            base.copy(
                brightnessMode = brightnessMode,
                manualBrightnessPercent = manual,
                nightEnabled = nightEnabled,
                nightStartMinutes = start,
                nightEndMinutes = end
            )
        )
    }

    companion object {
        fun from(s: ScreenSettings) = ScreenSettingsForm(
            brightnessMode = s.brightnessMode,
            manualBrightnessPercent = s.manualBrightnessPercent.toString(),
            nightEnabled = s.nightEnabled,
            nightStart = NightSchedule.format(s.nightStartMinutes),
            nightEnd = NightSchedule.format(s.nightEndMinutes)
        )
    }
}
