package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why the app recovered from a problem. [value] is the `kind` attribute of the HA "Last recovery" sensor. */
enum class RecoveryKind(val value: String) {
    /** The page stopped answering; its renderer was terminated and the page reloaded. */
    PAGE_UNRESPONSIVE("page_unresponsive"),

    /** The page's renderer crashed; the WebView was rebuilt. */
    PAGE_CRASH("page_crash"),

    /** Android killed the page's renderer (e.g. low memory); the WebView was rebuilt. */
    PAGE_KILLED("page_killed"),

    /** The HA frontend stayed disconnected from Home Assistant; the page was reloaded. */
    FRONTEND_DISCONNECTED("frontend_disconnected"),

    /** The app crashed (restarted if the dashboard was visible). */
    APP_CRASH("app_crash"),

    /** The app's main thread froze (or Android reported an ANR); the app was restarted. */
    APP_FREEZE("app_freeze"),

    /** Android ended the app (low memory, resource use, ...). */
    APP_KILLED("app_killed"),

    /** Restarted by the "Restart app" button in Home Assistant. */
    REMOTE_RESTART("remote_restart")
}

data class RecoveryRecord(val kind: RecoveryKind, val detail: String?, val timeMillis: Long)

/** Write side, used by the watchdogs and the crash handler. */
fun interface RecoverySink {
    fun record(kind: RecoveryKind, detail: String?)
}

/** Read side, used by the MQTT publisher. */
interface RecoverySource {
    val last: StateFlow<RecoveryRecord?>
}

/**
 * Persists the last recovery, recent restart times (for the crash-loop guard) and the "expected exit" marker in
 * SharedPreferences. Writes are synchronous (`commit`), because the process may die right after a record (crash path).
 * No secrets, URLs or payloads are stored: `detail` is an exception class name or a short reason.
 */
class RecoveryStore(context: Context, private val clock: Clock) :
    RecoverySink,
    RecoverySource {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val mutable = MutableStateFlow(read())
    override val last: StateFlow<RecoveryRecord?> = mutable.asStateFlow()

    override fun record(kind: RecoveryKind, detail: String?) {
        val record = RecoveryRecord(kind, detail?.take(MAX_DETAIL), clock.nowMillis())
        prefs.edit(commit = true) {
            putString(KEY_KIND, record.kind.value)
            putString(KEY_DETAIL, record.detail)
            putLong(KEY_TIME, record.timeMillis)
        }
        mutable.value = record
        AppLog.w(TAG, "Recovery: ${kind.value}${detail?.let { " ($it)" }.orEmpty()}")
    }

    /** Wall-clock times of recent automatic restarts (crash, freeze). */
    fun restartTimes(): List<Long> =
        prefs.getString(KEY_RESTARTS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun addRestartTime(timeMillis: Long) {
        val kept = (restartTimes() + timeMillis).takeLast(MAX_RESTART_TIMES)
        prefs.edit(commit = true) { putString(KEY_RESTARTS, kept.joinToString(",")) }
    }

    /** Marks the coming process exit as intentional, so it isn't reported as "killed" on the next start. */
    fun markExpectedExit() {
        prefs.edit(commit = true) { putLong(KEY_EXPECTED_EXIT, clock.nowMillis()) }
    }

    /** Returns the time of the intentional exit marker and clears it, or null. */
    fun takeExpectedExit(): Long? {
        val time = prefs.getLong(KEY_EXPECTED_EXIT, 0L).takeIf { it > 0 } ?: return null
        prefs.edit(commit = true) { remove(KEY_EXPECTED_EXIT) }
        return time
    }

    /** Timestamp of the newest system exit record already processed. */
    var lastSeenExitMillis: Long
        get() = prefs.getLong(KEY_LAST_EXIT, 0L)
        set(value) {
            prefs.edit { putLong(KEY_LAST_EXIT, value) }
        }

    private fun read(): RecoveryRecord? {
        val kind = RecoveryKind.entries.firstOrNull { it.value == prefs.getString(KEY_KIND, null) } ?: return null
        return RecoveryRecord(kind, prefs.getString(KEY_DETAIL, null), prefs.getLong(KEY_TIME, 0L))
    }

    private companion object {
        const val TAG = "Recovery"
        const val PREFS = "recovery"
        const val KEY_KIND = "kind"
        const val KEY_DETAIL = "detail"
        const val KEY_TIME = "time"
        const val KEY_RESTARTS = "restarts"
        const val KEY_EXPECTED_EXIT = "expected_exit"
        const val KEY_LAST_EXIT = "last_exit"
        const val MAX_DETAIL = 120
        const val MAX_RESTART_TIMES = 10
    }
}
