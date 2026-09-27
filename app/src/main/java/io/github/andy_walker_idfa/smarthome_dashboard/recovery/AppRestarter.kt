package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.content.Context
import android.os.Process
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import kotlin.system.exitProcess

/**
 * Restarts the whole app process and brings the dashboard back. Possible only while one of the app's activities
 * is visible (Android blocks activity starts from the background; an alarm-based restart would be blocked too).
 */
class AppRestarter(
    context: Context,
    private val store: RecoveryStore,
    private val foreground: ForegroundTracker,
    private val clock: Clock,
    private val dashboardActivity: String
) {
    private val appContext = context.applicationContext

    val canRestart: Boolean get() = foreground.isVisible

    /**
     * Automatic restart after a crash or freeze. Returns false (and does nothing) if the app isn't visible or
     * the crash-loop guard says stop; otherwise it doesn't return.
     */
    fun restartAfterFailure(): Boolean {
        val now = clock.nowMillis()
        if (!foreground.isVisible || !CrashLoopGuard.mayRestart(store.restartTimes(), now)) return false
        store.addRestartTime(now)
        restartNow()
    }

    /** Restart requested by the user (Home Assistant). Returns false if the app isn't visible. */
    fun restartOnRequest(): Boolean {
        if (!foreground.isVisible) return false
        restartNow()
    }

    private fun restartNow(): Nothing {
        AppLog.w(TAG, "Restarting the app")
        store.markExpectedExit()
        // The lines that explain the restart must reach the log file before the process ends.
        AppLog.flushNow()
        RestartActivity.start(appContext, dashboardActivity)
        Process.killProcess(Process.myPid())
        exitProcess(EXIT_CODE)
    }

    private companion object {
        const val TAG = "Restart"
        const val EXIT_CODE = 10
    }
}
