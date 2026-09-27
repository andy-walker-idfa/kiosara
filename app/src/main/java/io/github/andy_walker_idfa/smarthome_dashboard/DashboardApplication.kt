package io.github.andy_walker_idfa.smarthome_dashboard

import android.app.Application
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.FileLogSink
import io.github.andy_walker_idfa.smarthome_dashboard.core.SystemClockImpl
import io.github.andy_walker_idfa.smarthome_dashboard.di.AppContainer
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.CrashHandler
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.ExitReasons
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.MainThreadWatchdog
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.launch

class DashboardApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The restart trampoline runs in its own process and must not open DataStore, the log files or services.
        if (getProcessName() != packageName) return
        // Log files first, so even start-up problems end up in them.
        val logSink =
            FileLogSink(File(filesDir, LOG_DIR), SystemClockImpl) { e ->
                Log.w("SHDash/FileLog", "Log file write failed: ${e.javaClass.simpleName}")
            }
        AppLog.install(logSink, debugToFile = BuildConfig.DEBUG)
        AppLog.i(
            TAG,
            "=== Start ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}, ${BuildConfig.FLAVOR}), " +
                "Android ${Build.VERSION.RELEASE}, zone ${ZoneId.systemDefault()}, " +
                "${SystemClock.elapsedRealtime() / 1000} s since boot ==="
        )
        container = AppContainer(this, logSink)
        registerActivityLifecycleCallbacks(container.foregroundTracker)
        CrashHandler.install(container.recoveryStore, container.appRestarter)
        MainThreadWatchdog(container.recoveryStore, container.appRestarter).start()
        container.appScope.launch { ExitReasons.check(this@DashboardApplication, container.recoveryStore) }
        // Lets chrome://inspect attach to the dashboard in debug builds only.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    private companion object {
        const val TAG = "App"
        const val LOG_DIR = "logs"
    }
}
