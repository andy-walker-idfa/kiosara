package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.MainActivity
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.service.ConnectionService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Relaunches the dashboard after the app has been updated (the update kills the running process).
 * Android 10+ restricts activity starts from the background, so whether this start is allowed depends
 * on the exemptions that apply. See "Relaunch after update" in CLAUDE.md for the tested behaviour.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppLog.i(TAG, "App updated; starting dashboard")
        val launch =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(launch)
        } catch (e: RuntimeException) {
            AppLog.e(TAG, "Could not start dashboard after update", e)
        }
        // MY_PACKAGE_REPLACED is an exemption from the background foreground-service start restrictions.
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
        const val TAG = "UpdateReceiver"
    }
}
