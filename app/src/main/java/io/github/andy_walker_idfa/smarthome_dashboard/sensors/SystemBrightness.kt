package io.github.andy_walker_idfa.smarthome_dashboard.sensors

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** The system (not window) screen brightness in percent, observed live. */
interface SystemBrightness {
    val percent: StateFlow<Int>
}

class SettingsSystemBrightness(context: Context, scope: CoroutineScope) : SystemBrightness {
    private val resolver = context.applicationContext.contentResolver

    override val percent: StateFlow<Int> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(read())
                    }
                }
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                observer
            )
            trySend(read())
            awaitClose { resolver.unregisterContentObserver(observer) }
        }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), read())

    // SCREEN_BRIGHTNESS is 0-255 on AOSP; some OEMs use a larger range, hence the clamp.
    private fun read(): Int {
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_RAW)
        return (raw * 100 / MAX_RAW).coerceIn(0, 100)
    }

    private companion object {
        const val MAX_RAW = 255
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
