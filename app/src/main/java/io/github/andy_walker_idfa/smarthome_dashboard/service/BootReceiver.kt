package io.github.andy_walker_idfa.smarthome_dashboard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Starts the connection service after boot, also when the app is not the Home app. BOOT_COMPLETED is
 * an exemption from the background foreground-service start restrictions, and `specialUse` is not one
 * of the types Android 15+ forbids from BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AppLog.i(TAG, "Boot completed")
        val container = (context.applicationContext as DashboardApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                ConnectionService.startIfEnabled(
                    context.applicationContext,
                    container.settingsRepository.settings.first()
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
