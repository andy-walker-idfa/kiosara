package io.github.andy_walker_idfa.smarthome_dashboard.core

import android.util.Log

/** Where log lines go besides logcat (the rotating log files, Phase 6). */
interface LogSink {
    fun write(level: Char, tag: String, message: String, error: Throwable?)

    /** Writes everything pending to the file, waiting at most [timeoutMillis]. Returns false on timeout. */
    fun flushNow(timeoutMillis: Long): Boolean
}

/**
 * Central logging facade. Every app tag is prefixed with "SHDash" so logcat can be filtered with one pattern.
 * Lines also go to the installed [LogSink] (the log files viewable in Settings): info, warnings and errors always,
 * debug lines only in debug builds.
 *
 * Never pass secrets (passwords, tokens, PINs), payloads or URLs with tokens to these functions. The file sink
 * additionally masks anything that looks sensitive and never writes exception messages.
 */
object AppLog {
    private const val PREFIX = "SHDash"

    @Volatile private var sink: LogSink? = null

    @Volatile private var debugToFile = false

    /** Called once per process, as early as possible (main process only). */
    fun install(sink: LogSink, debugToFile: Boolean) {
        this.sink = sink
        this.debugToFile = debugToFile
    }

    /**
     * Writes all pending lines to the file before the process ends (crash, freeze, "Restart app"). Bounded, so a
     * stuck write can't block a restart.
     */
    fun flushNow(timeoutMillis: Long = FLUSH_TIMEOUT_MS) {
        sink?.flushNow(timeoutMillis)
    }

    fun d(tag: String, message: String) {
        Log.d("$PREFIX/$tag", message)
        if (debugToFile) sink?.write('D', tag, message, null)
    }

    fun i(tag: String, message: String) {
        Log.i("$PREFIX/$tag", message)
        sink?.write('I', tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w("$PREFIX/$tag", message, error)
        sink?.write('W', tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e("$PREFIX/$tag", message, error)
        sink?.write('E', tag, message, error)
    }

    private const val FLUSH_TIMEOUT_MS = 300L
}
