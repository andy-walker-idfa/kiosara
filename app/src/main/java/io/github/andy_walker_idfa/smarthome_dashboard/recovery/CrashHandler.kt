package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog

/**
 * Uncaught exceptions: record the crash, then restart the app while it is still visible. Restarting ourselves
 * (instead of letting Android handle the crash) avoids the "app keeps stopping" dialog and Android marking the
 * app as "bad" after two quick crashes, which would stop the boot and service restarts. When the app isn't
 * visible, or the crash-loop guard says stop, Android's normal handling applies.
 */
object CrashHandler {
    fun install(store: RecoveryStore, restarter: AppRestarter) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                // Class name and stack frames only: exception messages may contain URLs or other user data.
                val frames = error.stackTrace.take(STACK_FRAMES).joinToString("\n  at ")
                AppLog.e(TAG, "Uncaught ${error.javaClass.name} on ${thread.name}\n  at $frames")
                store.record(RecoveryKind.APP_CRASH, error.javaClass.name)
                restarter.restartAfterFailure()
            } catch (t: Throwable) {
                AppLog.e(TAG, "Crash handling failed: ${t.javaClass.name}")
            }
            AppLog.flushNow()
            previous?.uncaughtException(thread, error)
        }
    }

    private const val TAG = "Crash"
    private const val STACK_FRAMES = 12
}

/**
 * Detects a frozen main thread (our own, not the web page). Android 13 would show an "App isn't responding"
 * dialog that waits forever on an unattended panel. A background thread posts a tick to the main thread every
 * [TICK_MS]; if a tick stays unprocessed for [FREEZE_MS], the app records the freeze and restarts (or, when not
 * visible, ends itself so the connection service restarts).
 *
 * Uses uptime, which stops in deep sleep, so a sleeping device is never taken for a frozen one.
 */
class MainThreadWatchdog(private val store: RecoveryStore, private val restarter: AppRestarter) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("MainThreadWatchdog").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile private var lastTickPostedUptime = 0L

    @Volatile private var tickPending = false

    fun start() {
        handler.post(::check)
    }

    private fun check() {
        val now = SystemClock.uptimeMillis()
        if (tickPending) {
            if (now - lastTickPostedUptime >= FREEZE_MS && !Debug.isDebuggerConnected()) onFrozen(now)
        } else {
            tickPending = true
            lastTickPostedUptime = now
            mainHandler.post { tickPending = false }
        }
        handler.postDelayed(::check, TICK_MS)
    }

    private fun onFrozen(now: Long) {
        AppLog.e(TAG, "Main thread frozen for ${(now - lastTickPostedUptime) / 1000} s")
        store.record(RecoveryKind.APP_FREEZE, "main thread")
        if (!restarter.restartAfterFailure()) {
            store.markExpectedExit()
            AppLog.flushNow()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private companion object {
        const val TAG = "Watchdog"
        const val TICK_MS = 5_000L
        const val FREEZE_MS = 20_000L
    }
}
