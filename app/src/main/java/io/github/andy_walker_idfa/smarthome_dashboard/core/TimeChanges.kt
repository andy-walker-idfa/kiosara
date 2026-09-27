package io.github.andy_walker_idfa.smarthome_dashboard.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits when the wall clock or the time zone changes (manual change, network time sync after boot, zone
 * change). Scheduled wall-clock work must be re-planned then. DST transitions need no event: they are
 * part of the zone rules.
 */
fun timeChanges(context: Context): Flow<Unit> = callbackFlow {
    val appContext = context.applicationContext
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
    val filter =
        IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
    // System broadcasts reach non-exported receivers.
    ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { appContext.unregisterReceiver(receiver) }
}
