package io.github.andy_walker_idfa.smarthome_dashboard.distribution

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** Distribution channel of the `github` (sideload) flavor. */
object FlavorDistribution : Distribution {
    override val channel: String = "github"

    // A dedicated, always-powered wall panel must keep its Home Assistant connection; the direct
    // exemption request is allowed outside Google Play. The store flavor uses the settings list instead.
    @SuppressLint("BatteryLife")
    override fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    override val batteryOptimizationHint: Int? = null
}
