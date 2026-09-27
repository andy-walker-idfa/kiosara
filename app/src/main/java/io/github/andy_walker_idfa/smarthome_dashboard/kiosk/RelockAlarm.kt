package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import kotlinx.coroutines.launch

/**
 * Backup for the end of "Unlock for 15 minutes": the in-memory timer is lost if Android ends the app process while
 * another app (e.g. YouTube) is in front and no foreground service keeps it alive. This alarm starts the process
 * again and re-locks (the lock must resume automatically; user rule 4). Inexact but allowed in Doze.
 */
class RelockAlarm(context: Context) : RelockScheduler {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(delayMillis: Long) {
        alarms?.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMillis,
            pendingIntent()
        )
    }

    override fun cancel() {
        alarms?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, RelockReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Not exported: only the app's own alarm reaches it. */
class RelockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i(TAG, "Unlock period over (alarm)")
        val container = (context.applicationContext as DashboardApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.kioskController.onUnlockExpired()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Kiosk"
    }
}
