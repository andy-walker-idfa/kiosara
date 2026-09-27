package io.github.andy_walker_idfa.smarthome_dashboard.mqtt

import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatusSource
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatusSource
import io.github.andy_walker_idfa.smarthome_dashboard.network.WifiStatus
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoverySource
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.BatteryMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.SystemInfo
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardStatusSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Collects everything published to Home Assistant. Observed sources are only active while [changes] is collected. */
class DeviceStateSource(
    private val battery: BatteryMonitor,
    private val wifi: WifiStatus,
    private val dashboard: DashboardStatusSource,
    private val display: DisplayStatusSource,
    private val recovery: RecoverySource,
    private val kiosk: KioskStatusSource,
    private val settings: StateFlow<Settings>,
    private val systemInfo: SystemInfo,
    private val staticInfo: StaticInfo
) : MqttStateSource {
    // Declared before [sample], whose initializer already logs.
    private var lastMemoryLogElapsed: Long? = null

    private var sample: SystemSample = readSample()

    override val changes: Flow<Unit> =
        merge(
            battery.state,
            wifi.details,
            dashboard.status,
            display.status,
            recovery.last,
            kiosk.status,
            settings.map { it.web to it.screen }
        ).map { }

    override fun snapshot(resample: Boolean): Map<String, EntityValue> {
        if (resample) sample = readSample()
        return EntityStates.compute(
            StateInputs(
                battery = battery.state.value,
                wifi = wifi.details.value,
                dashboard = dashboard.status.value,
                display = display.status.value,
                screen = settings.value.screen,
                lastRecovery = recovery.last.value,
                kiosk = kiosk.status.value,
                web = settings.value.web,
                sample = sample,
                static = staticInfo
            )
        )
    }

    private fun readSample() = SystemSample(
        freeMemoryBytes = systemInfo.freeMemoryBytes(),
        freeStorageBytes = systemInfo.freeStorageBytes(),
        appMemoryBytes = systemInfo.appMemoryBytes().also(::logMemory),
        bootTimeMillis = systemInfo.bootTimeMillis(),
        deviceOwner = systemInfo.isDeviceOwner()
    )

    /** One log line every 30 minutes, so the log files show the memory trend (soak test). */
    private fun logMemory(appBytes: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastMemoryLogElapsed
        if (last != null && now - last < MEMORY_LOG_INTERVAL_MS) return
        lastMemoryLogElapsed = now
        AppLog.i(
            TAG,
            "Memory: app ${appBytes / BYTES_PER_MB} MB, free ${systemInfo.freeMemoryBytes() / BYTES_PER_MB} MB"
        )
    }

    private companion object {
        const val TAG = "Memory"
        const val MEMORY_LOG_INTERVAL_MS = 30 * 60_000L
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
