package io.github.andy_walker_idfa.smarthome_dashboard.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

data class BatteryState(
    val levelPercent: Int,
    val charging: Boolean,
    val plugType: PlugType,
    val temperatureCelsius: Double,
    val health: BatteryHealth,
    val voltageVolts: Double
) {
    companion object {
        /** Pure mapping of the ACTION_BATTERY_CHANGED extras, unit-tested. */
        fun parse(
            level: Int,
            scale: Int,
            status: Int,
            plugged: Int,
            temperatureTenths: Int,
            health: Int,
            voltageMillis: Int
        ): BatteryState = BatteryState(
            levelPercent = if (scale > 0) (level * 100 / scale).coerceIn(0, 100) else level.coerceIn(0, 100),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
            plugType = PlugType.fromPlugged(plugged),
            temperatureCelsius = temperatureTenths / 10.0,
            health = BatteryHealth.fromCode(health),
            // Some devices report volts instead of millivolts.
            voltageVolts = if (voltageMillis in 1..100) voltageMillis.toDouble() else voltageMillis / 1000.0
        )
    }
}

enum class PlugType(val value: String) {
    AC("ac"),
    USB("usb"),
    WIRELESS("wireless"),
    DOCK("dock"),
    NONE("none");

    companion object {
        // BatteryManager.BATTERY_PLUGGED_DOCK (8) exists only from API 33; use the value directly.
        private const val PLUGGED_DOCK = 8

        fun fromPlugged(plugged: Int): PlugType = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> AC
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> USB
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> WIRELESS
            plugged and PLUGGED_DOCK != 0 -> DOCK
            else -> NONE
        }
    }
}

enum class BatteryHealth(val value: String) {
    GOOD("good"),
    OVERHEAT("overheat"),
    DEAD("dead"),
    OVER_VOLTAGE("over_voltage"),
    FAILURE("failure"),
    COLD("cold"),
    UNKNOWN("unknown");

    companion object {
        fun fromCode(code: Int): BatteryHealth = when (code) {
            BatteryManager.BATTERY_HEALTH_GOOD -> GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> COLD
            else -> UNKNOWN
        }
    }
}

/** Latest battery state from the sticky ACTION_BATTERY_CHANGED broadcast; null until the first one. */
interface BatteryMonitor {
    val state: StateFlow<BatteryState?>
}

class BroadcastBatteryMonitor(context: Context, scope: CoroutineScope) : BatteryMonitor {
    private val appContext = context.applicationContext

    override val state: StateFlow<BatteryState?> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        trySend(intent.toBatteryState())
                    }
                }
            // ACTION_BATTERY_CHANGED is sticky: registerReceiver returns the current state. Use that return
            // value instead of relying on the replayed callback: some builds (seen on the TB310FU, Android 13)
            // refuse to replay sticky broadcasts to RECEIVER_NOT_EXPORTED receivers ("Exported Denial ...
            // from null (uid=-1)"), while live system broadcasts are still delivered.
            val current =
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            current?.let { trySend(it.toBatteryState()) }
            awaitClose { appContext.unregisterReceiver(receiver) }
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private fun Intent.toBatteryState() = BatteryState.parse(
        level = getIntExtra(BatteryManager.EXTRA_LEVEL, 0),
        scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100),
        status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
        plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        temperatureTenths = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
        health = getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
        voltageMillis = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
