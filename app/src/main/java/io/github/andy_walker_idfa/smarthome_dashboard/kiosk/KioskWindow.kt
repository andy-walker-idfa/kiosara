package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.PowerManager
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog

/** Window-level kiosk behaviour: immersive full screen, screen kept on, fixed orientation. */
object KioskWindow {
    fun keepScreenOn(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Switches the screen on when the dashboard is resumed while it is off: after an update, a restart or the end of
     * "Unlock for 15 minutes", Android's screen timeout may have turned it off while another app was in front, and
     * the dashboard would stay dark. A short wake lock wakes it; FLAG_KEEP_SCREEN_ON keeps it on from then on.
     * (`setTurnScreenOn` and the manifest attribute didn't wake the TB310FU in this situation.) Night uses the
     * app's own black screen, never screen-off, so this doesn't fight them.
     */
    @Suppress("DEPRECATION") // SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP is the only app-level way to wake it.
    fun wakeScreenIfOff(activity: Activity) {
        val power = activity.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        AppLog.i(TAG, "Screen was off; switching it on for the dashboard")
        power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "shdash:wake-dashboard"
        ).acquire(WAKE_MS)
    }

    private const val TAG = "Kiosk"
    private const val WAKE_MS = 1_000L

    /** Hides the status and navigation bars. A swipe shows them briefly; call again when focus returns. */
    fun enterImmersive(activity: Activity) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Landscape, either way up (fixed since 0.8.0; wall panels are mounted in landscape). */
    fun applyOrientation(activity: Activity) {
        val requested = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }
}
