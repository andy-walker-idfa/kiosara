package io.github.andy_walker_idfa.smarthome_dashboard.distribution

import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.R

/** Distribution channel of the `store` flavor (F-Droid, IzzyOnDroid, Google Play). */
object FlavorDistribution : Distribution {
    override val channel: String = "store"

    // Google Play restricts REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, so open the list and explain instead.
    override fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    override val batteryOptimizationHint: Int = R.string.battery_optimization_store_hint
}
