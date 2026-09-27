package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog

/**
 * On start, reads Android's record of why the previous app process ended (API 30+). It also covers what the
 * app can't catch itself: native crashes, ANRs and kills by the system. Pure mapping in [classify].
 */
object ExitReasons {
    /** Minimum gap between our own crash record and the system's record of the same crash. */
    private const val SAME_EVENT_MS = 60_000L

    fun check(context: Context, store: RecoveryStore) {
        val expectedExit = store.takeExpectedExit()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            checkApi30(context, store, expectedExit)
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "Can't read exit reasons: ${e.javaClass.simpleName}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkApi30(context: Context, store: RecoveryStore, expectedExit: Long?) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val info =
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
                .firstOrNull { it.processName == context.packageName } ?: return
        if (info.timestamp <= store.lastSeenExitMillis) return
        store.lastSeenExitMillis = info.timestamp
        AppLog.i(TAG, "Previous exit: reason=${info.reason} status=${info.status}")

        // Our own restart (crash handler, watchdog, "Restart app") was already recorded.
        if (expectedExit != null && info.timestamp - expectedExit in 0..SAME_EVENT_MS) return
        val last = store.last.value
        val (kind, detail) = classify(info.reason, info.status) ?: return
        if (kind == RecoveryKind.APP_CRASH && last?.kind == RecoveryKind.APP_CRASH &&
            info.timestamp - last.timeMillis in 0..SAME_EVENT_MS
        ) {
            return
        }
        store.record(kind, detail)
    }

    /** Maps an exit reason to a recovery record, or null for normal exits (update, user, own exit). */
    fun classify(reason: Int, status: Int): Pair<RecoveryKind, String>? = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> RecoveryKind.APP_CRASH to "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> RecoveryKind.APP_CRASH to "native crash"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> RecoveryKind.APP_CRASH to "initialization failure"
        ApplicationExitInfo.REASON_ANR -> RecoveryKind.APP_FREEZE to "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> RecoveryKind.APP_KILLED to "low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> RecoveryKind.APP_KILLED to "excessive resource use"
        ApplicationExitInfo.REASON_SIGNALED -> RecoveryKind.APP_KILLED to "signal $status"
        else -> null
    }

    private const val TAG = "ExitReasons"
    private const val MAX_RECORDS = 5
}
