package io.github.andy_walker_idfa.smarthome_dashboard.core

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Read side for the log viewer. */
interface LogReader {
    /** The newest [maxLines] lines of the current and the previous file, oldest first. */
    fun readTail(maxLines: Int): List<String>
}

/**
 * Masks anything that looks sensitive before a line is written: URL query strings, `user:password@` in addresses,
 * and `password=`/`token=`-style values. A safety net on top of the rule "never log secrets". Pure; unit-tested.
 */
object LogRedactor {
    private val query = Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\s?#]*)\?[^\s#]*""")
    private val userInfo = Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://)[^\s/@]+@""")
    private val keyValue = Regex("""(?i)\b(password|passwd|pwd|secret|token|access_token|pin)(\s*[=:]\s*)\S+""")

    fun redact(message: String): String = message
        .replace(userInfo, "$1…@")
        .replace(query, "$1?…")
        .replace(keyValue, "$1$2…")
}

/**
 * Rotating log files in [dir]: `app.log` and the previous `app.1.log`, each up to [maxBytes] (2 × 1 MB by
 * default). Plain JVM, so it is unit-testable.
 *
 * - One writer thread with a bounded queue ([QUEUE_CAPACITY]); overflow is counted and noted as "N lines dropped".
 * - Buffered: flushed [FLUSH_DELAY_MS] after the first unwritten line, and at once for warnings and errors. Nothing
 *   runs while there is nothing to write. No `fsync`: the kernel keeps the lines if the app is killed.
 * - Identical consecutive lines are summarised ("previous line repeated N times"), so an error loop can't push the
 *   whole history out.
 * - Exceptions are written as class, a few stack frames and the "caused by" classes, never their messages (which
 *   may contain paths, addresses or keys).
 * - [readTail] runs on the writer thread (after a flush), so it never sees a half-rotated file.
 */
class FileLogSink(
    private val dir: File,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    /** Reports write failures (to logcat in the app); must not log to this sink. */
    private val onError: (IOException) -> Unit = {}
) : LogSink,
    LogReader {
    private sealed interface Task {
        data class Line(val timeMillis: Long, val level: Char, val tag: String, val message: String) : Task

        class Flush(val done: CountDownLatch) : Task

        class Read(val maxLines: Int, val done: CountDownLatch) : Task {
            @Volatile var result: List<String> = emptyList()
        }
    }

    private val queue = LinkedBlockingQueue<Task>(QUEUE_CAPACITY)
    private val dropped = AtomicInteger()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSXXX")

    private var writer: BufferedWriter? = null
    private var written = 0L
    private var flushDeadline: Long? = null
    private var lastKey: String? = null
    private var lastLevel = 'I'
    private var lastTag = ""
    private var repeats = 0

    private val thread =
        Thread(::loop, "FileLogSink").apply {
            isDaemon = true
            start()
        }

    override fun write(level: Char, tag: String, message: String, error: Throwable?) {
        // One log call = one line: CR/LF inside a message could otherwise forge entries in the viewer.
        val oneLine = message.replace('\r', ' ').replace('\n', ' ')
        val text = LogRedactor.redact(oneLine) + (error?.let(::describe).orEmpty())
        if (!queue.offer(Task.Line(clock.nowMillis(), level, tag, text))) dropped.incrementAndGet()
    }

    override fun flushNow(timeoutMillis: Long): Boolean {
        // A crash on the writer thread itself must not wait for itself.
        if (Thread.currentThread() === thread) return false
        val task = Task.Flush(CountDownLatch(1))
        if (!queue.offer(task)) return false
        return task.done.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    override fun readTail(maxLines: Int): List<String> {
        val task = Task.Read(maxLines, CountDownLatch(1))
        if (!queue.offer(task, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return emptyList()
        task.done.await(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return task.result
    }

    private fun loop() {
        while (true) {
            val deadline = flushDeadline
            val task =
                if (deadline == null) {
                    queue.take()
                } else {
                    queue.poll((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
                }
            try {
                when (task) {
                    null -> flush()

                    is Task.Line -> handle(task)

                    is Task.Flush -> {
                        flush()
                        task.done.countDown()
                    }

                    is Task.Read -> {
                        flush()
                        task.result = tail(task.maxLines)
                        task.done.countDown()
                    }
                }
            } catch (e: IOException) {
                // Drop the writer and try again with the next line.
                onError(e)
                closeWriter()
            }
        }
    }

    private fun handle(line: Task.Line) {
        val lost = dropped.getAndSet(0)
        if (lost > 0) append(line.timeMillis, 'W', "FileLog", "$lost lines dropped (log queue full)")
        val key = "${line.level}|${line.tag}|${line.message}"
        if (key == lastKey) {
            repeats++
            scheduleFlush()
            return
        }
        writeRepeats(line.timeMillis)
        lastKey = key
        lastLevel = line.level
        lastTag = line.tag
        append(line.timeMillis, line.level, line.tag, line.message)
        if (line.level == 'W' || line.level == 'E') flush() else scheduleFlush()
    }

    private fun writeRepeats(timeMillis: Long) {
        if (repeats == 0) return
        append(timeMillis, lastLevel, lastTag, "(previous line repeated $repeats times)")
        repeats = 0
    }

    private fun append(timeMillis: Long, level: Char, tag: String, message: String) {
        val time = formatter.format(Instant.ofEpochMilli(timeMillis).atZone(zone()))
        val text = "$time $level $tag: $message\n"
        val bytes = text.toByteArray(Charsets.UTF_8).size
        if (written + bytes > maxBytes) rotate()
        val out = writer ?: openWriter()
        out.write(text)
        written += bytes
    }

    private fun scheduleFlush() {
        if (flushDeadline == null) flushDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FLUSH_DELAY_MS)
    }

    private fun flush() {
        // Keeps lastKey: further repeats of the same line keep being counted (an error loop flushes every line).
        writeRepeats(clock.nowMillis())
        writer?.flush()
        flushDeadline = null
    }

    private fun rotate() {
        closeWriter()
        val current = File(dir, CURRENT)
        val previous = File(dir, PREVIOUS)
        previous.delete()
        current.renameTo(previous)
        written = 0
    }

    private fun openWriter(): BufferedWriter {
        dir.mkdirs()
        val file = File(dir, CURRENT)
        written = file.length()
        return BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8)).also { writer = it }
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (e: IOException) {
            // Already failing; nothing more to do.
        }
        writer = null
    }

    private fun tail(maxLines: Int): List<String> {
        val current = tailOf(File(dir, CURRENT), maxLines)
        if (current.size >= maxLines) return current
        return tailOf(File(dir, PREVIOUS), maxLines - current.size) + current
    }

    /** The last [maxLines] lines, reading only the end of the file. */
    private fun tailOf(file: File, maxLines: Int): List<String> {
        if (maxLines <= 0 || !file.exists()) return emptyList()
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val start = (length - maxLines.toLong() * AVERAGE_LINE_BYTES).coerceAtLeast(0)
            val bytes = ByteArray((length - start).toInt())
            raf.seek(start)
            raf.readFully(bytes)
            val lines = String(bytes, Charsets.UTF_8).split('\n').filter { it.isNotEmpty() }
            // The first line may be cut off when reading from the middle of the file.
            val whole = if (start > 0) lines.drop(1) else lines
            return whole.takeLast(maxLines)
        }
    }

    companion object {
        const val CURRENT = "app.log"
        const val PREVIOUS = "app.1.log"
        const val DEFAULT_MAX_BYTES = 1_000_000L
        const val QUEUE_CAPACITY = 2_000
        const val FLUSH_DELAY_MS = 5_000L
        private const val READ_TIMEOUT_MS = 2_000L
        private const val AVERAGE_LINE_BYTES = 256
        private const val STACK_FRAMES = 3

        /** Class, a few frames and the "caused by" classes; never messages. */
        fun describe(error: Throwable): String {
            val frames = error.stackTrace.take(STACK_FRAMES).joinToString(" < ") { "${it.className}.${it.methodName}" }
            val causes = generateSequence(error.cause) { it.cause }.take(STACK_FRAMES).joinToString("") {
                " / caused by ${it.javaClass.name}"
            }
            return " [${error.javaClass.name}${if (frames.isEmpty()) "" else " at $frames"}$causes]"
        }
    }
}
