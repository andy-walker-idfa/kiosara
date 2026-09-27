package io.github.andy_walker_idfa.smarthome_dashboard.core

import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.LogFilter
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.LogLines
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileLogSinkTest {
    @get:Rule val temp = TemporaryFolder()

    private val start = Instant.parse("2026-09-26T09:02:08.242Z").toEpochMilli()

    private class FixedClock(var now: Long) : Clock {
        override fun nowMillis() = now

        override fun elapsedRealtimeMillis() = now
    }

    private fun sink(maxBytes: Long = FileLogSink.DEFAULT_MAX_BYTES) =
        FileLogSink(temp.root, FixedClock(start), { ZoneOffset.ofHours(2) }, maxBytes)

    private fun current() = File(temp.root, FileLogSink.CURRENT)

    @Test
    fun `line format with local time and offset`() {
        val s = sink()
        s.write('I', "Mqtt", "Connected", null)
        assertTrue(s.flushNow(2_000))
        assertEquals("2026-09-26 11:02:08.242+02:00 I Mqtt: Connected\n", current().readText())
    }

    @Test
    fun `exception messages never reach the file`() {
        val s = sink()
        val error = IllegalStateException("password=hunter2 at /data/secret", RuntimeException("inner detail"))
        s.write('E', "Store", "Failed", error)
        s.flushNow(2_000)
        val text = current().readText()
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("caused by java.lang.RuntimeException"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("/data/secret"))
        assertFalse(text.contains("inner detail"))
    }

    @Test
    fun `a message can't forge extra lines`() {
        val s = sink()
        s.write('W', "MqttCommand", "Rejected command x\n2026-09-26 11:02:08.242+02:00 E Kiosk: fake\rline", null)
        s.flushNow(2_000)
        assertEquals(
            "2026-09-26 11:02:08.242+02:00 W MqttCommand: Rejected command x " +
                "2026-09-26 11:02:08.242+02:00 E Kiosk: fake line\n",
            current().readText()
        )
    }

    @Test
    fun `sensitive parts are masked`() {
        assertEquals(
            "Loaded http://ha.local:8123/lovelace?… and https://…@host/x",
            LogRedactor.redact(
                "Loaded http://ha.local:8123/lovelace?auth_callback=1&code=abc and https://user:pw@host/x"
            )
        )
        assertEquals("token=… password: …", LogRedactor.redact("token=abc123 password: secret"))
        assertEquals("Connected to 192.168.1.2:1883", LogRedactor.redact("Connected to 192.168.1.2:1883"))
    }

    @Test
    fun `repeated lines are summarised`() {
        val s = sink()
        repeat(5) { s.write('W', "Mqtt", "Connection refused", null) }
        s.write('I', "Mqtt", "Connected", null)
        s.flushNow(2_000)
        val lines = current().readLines()
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith("W Mqtt: (previous line repeated 4 times)"))
        assertTrue(lines[2].endsWith("I Mqtt: Connected"))
    }

    @Test
    fun `warnings are written at once, info lines within the flush delay`() {
        val s = sink()
        s.write('I', "A", "info", null)
        Thread.sleep(300)
        assertFalse(current().exists() && current().readText().contains("info"))
        s.write('W', "A", "warning", null)
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline &&
            !(current().exists() && current().readText().contains("warning"))
        ) {
            Thread.sleep(20)
        }
        assertTrue(current().readText().contains("warning"))
    }

    @Test
    fun `rotation keeps two bounded files and the tail reads across them in order`() {
        val s = sink(maxBytes = 2_000)
        for (i in 1..200) s.write('I', "T", "line %03d".format(i), null)
        s.flushNow(5_000)
        val previous = File(temp.root, FileLogSink.PREVIOUS)
        assertTrue(previous.exists())
        assertTrue(current().length() <= 2_000)
        assertTrue(previous.length() <= 2_000)
        assertFalse(File(temp.root, "app.2.log").exists())
        val tail = s.readTail(30)
        assertEquals(30, tail.size)
        assertTrue(tail.last().endsWith("line 200"))
        assertTrue(tail.first().endsWith("line 171"))
    }

    @Test
    fun `viewer filters by level and shows the newest first`() {
        val lines =
            listOf(
                "2026-09-26 11:00:00.000+02:00 I A: one",
                "2026-09-26 11:00:01.000+02:00 E Crash: boom",
                "  at some.Frame.method",
                "2026-09-26 11:00:02.000+02:00 W A: careful"
            )
        assertEquals(
            listOf(
                "2026-09-26 11:00:02.000+02:00 W A: careful",
                "  at some.Frame.method",
                "2026-09-26 11:00:01.000+02:00 E Crash: boom",
                "2026-09-26 11:00:00.000+02:00 I A: one"
            ),
            LogLines.select(lines, LogFilter.ALL)
        )
        assertEquals(
            listOf("  at some.Frame.method", "2026-09-26 11:00:01.000+02:00 E Crash: boom"),
            LogLines.select(lines, LogFilter.ERRORS)
        )
        assertEquals(3, LogLines.select(lines, LogFilter.WARNINGS).size)
    }
}
