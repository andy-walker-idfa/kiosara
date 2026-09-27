package io.github.andy_walker_idfa.smarthome_dashboard.distribution

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.annotation.StringRes

/**
 * Capabilities that differ between distribution channels. Each flavor source set (`src/github`,
 * `src/store`) provides `FlavorDistribution`; `main` code only sees this interface. Channel-specific
 * behaviour lives here (battery-optimization flow now, self-updater in Phase 7), never behind runtime flags.
 */
interface Distribution {
    /** Short channel name shown in Settings, e.g. "github" or "store". */
    val channel: String

    /** Screen that lets the user exempt the app from battery optimization. */
    fun batteryOptimizationIntent(context: Context): Intent

    /** Instructions shown next to the button, or null if the screen is self-explanatory. */
    @get:StringRes
    val batteryOptimizationHint: Int?

    companion object {
        fun isIgnoringBatteryOptimizations(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
}
