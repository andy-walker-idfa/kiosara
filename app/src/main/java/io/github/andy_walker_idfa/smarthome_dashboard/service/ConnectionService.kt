package io.github.andy_walker_idfa.smarthome_dashboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.MainActivity
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service (type `specialUse`) that keeps the MQTT connection to the user's Home Assistant
 * alive while the screen is off or the dashboard is not shown. It runs only while MQTT is enabled and
 * stops itself when it gets disabled. See docs/store-policy-notes.md for the policy justification.
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mqttJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (mqttJob == null) {
            val container = (application as DashboardApplication).container
            AppLog.i(TAG, "Service started")
            mqttJob = scope.launch { container.mqttManager.run() }
            scope.launch {
                // The DataStore flow (not the pre-seeded StateFlow), so the first value is the stored one.
                container.settingsRepository.settings.first { !it.mqtt.enabled }
                AppLog.i(TAG, "MQTT disabled; stopping service")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.i(TAG, "Service stopped")
        // Cancelling lets MqttManager publish "offline" and disconnect cleanly (non-cancellable block).
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Must be called within 5 s of every start. Android refuses it in some background situations (e.g. a
     * START_STICKY restart without battery-optimization exemption); then the service stops cleanly.
     */
    private fun enterForeground(): Boolean = try {
        createChannel(this)
        val type = if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (e: IllegalStateException) {
        AppLog.e(TAG, "Could not enter foreground", e)
        false
    } catch (e: SecurityException) {
        AppLog.e(TAG, "Could not enter foreground", e)
        false
    }

    private fun buildNotification(): Notification {
        val openDashboard =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_notification_text))
            .setContentIntent(openDashboard)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "Service"
        private const val CHANNEL_ID = "connection"
        private const val NOTIFICATION_ID = 1

        private fun createChannel(context: Context) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setShowBadge(false)
                }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /**
         * Starts the service if MQTT is enabled. Only call where a foreground-service start is allowed:
         * app in the foreground, BOOT_COMPLETED, MY_PACKAGE_REPLACED.
         */
        fun startIfEnabled(context: Context, settings: Settings) {
            if (!settings.mqtt.enabled) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) is an IllegalStateException.
                AppLog.w(TAG, "Foreground service start not allowed now", e)
            }
        }
    }
}
