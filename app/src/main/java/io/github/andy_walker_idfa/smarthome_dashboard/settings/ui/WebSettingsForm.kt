package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import io.github.andy_walker_idfa.smarthome_dashboard.settings.UrlValidator
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings

/**
 * Editable text form of the [WebSettings] fields on the Settings screen. Kept free of Android types so the
 * validation can be unit-tested.
 */
data class WebSettingsForm(val startUrl: String, val textZoomPercent: String) {
    enum class Field { START_URL, TEXT_ZOOM }

    sealed interface Result {
        data class Valid(val settings: WebSettings) : Result

        data class Invalid(val fields: Set<Field>) : Result
    }

    /** Validates the form and merges it into [base] (which supplies the fields not on this form). */
    fun toSettings(base: WebSettings): Result {
        val invalid = mutableSetOf<Field>()
        val url = UrlValidator.normalize(startUrl) ?: run {
            invalid += Field.START_URL
            ""
        }
        val zoom = textZoomPercent.trim().toIntOrNull()?.takeIf { it in MIN_TEXT_ZOOM..MAX_TEXT_ZOOM } ?: run {
            invalid += Field.TEXT_ZOOM
            0
        }
        if (invalid.isNotEmpty()) return Result.Invalid(invalid)

        val sameHost = UrlValidator.hostOf(url) == UrlValidator.hostOf(base.startUrl)
        return Result.Valid(
            base.copy(
                startUrl = url,
                textZoomPercent = zoom,
                // A pinned certificate belongs to the start URL's host; a new host drops it.
                trustedCertSha256 = if (sameHost) base.trustedCertSha256 else ""
            )
        )
    }

    companion object {
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 300

        fun from(web: WebSettings) = WebSettingsForm(
            startUrl = web.startUrl,
            textZoomPercent = web.textZoomPercent.toString()
        )
    }
}
