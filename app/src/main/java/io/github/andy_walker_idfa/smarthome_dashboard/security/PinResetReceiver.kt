package io.github.andy_walker_idfa.smarthome_dashboard.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import kotlinx.coroutines.launch

/**
 * Clears a forgotten settings PIN. Exported, but protected by `android.permission.DUMP`, which the adb shell holds
 * and no ordinary app can get (signature|privileged|development; only `pm grant` over adb). Documented in
 * docs/home-assistant-setup.md and docs/store-policy-notes.md:
 *
 * `adb shell am broadcast -a io.github.andy_walker_idfa.smarthome_dashboard.action.RESET_PIN
 *   -n io.github.andy_walker_idfa.smarthome_dashboard/.security.PinResetReceiver`
 */
class PinResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        AppLog.w(TAG, "PIN reset requested via adb")
        val container = (context.applicationContext as DashboardApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.settingsLock.clearPin()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PinReset"
        const val ACTION = "io.github.andy_walker_idfa.smarthome_dashboard.action.RESET_PIN"
    }
}
