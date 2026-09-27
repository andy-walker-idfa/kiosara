package io.github.andy_walker_idfa.smarthome_dashboard.display

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightScheduleTest {
    private val prague = ZoneId.of("Europe/Prague")
    private val start = 22 * 60
    private val end = 6 * 60 + 30

    private fun at(text: String) = LocalDateTime.parse(text).atZone(prague)

    private fun millis(text: String) = at(text).toInstant().toEpochMilli()

    @Test
    fun `overnight schedule`() {
        assertFalse(NightSchedule.isNight(start, end, at("2026-09-25T21:59")))
        assertTrue(NightSchedule.isNight(start, end, at("2026-09-25T22:00")))
        assertTrue(NightSchedule.isNight(start, end, at("2026-09-26T03:00")))
        assertTrue(NightSchedule.isNight(start, end, at("2026-09-26T06:29")))
        assertFalse(NightSchedule.isNight(start, end, at("2026-09-26T06:30")))
    }

    @Test
    fun `same-day schedule and start equals end`() {
        assertTrue(NightSchedule.isNight(60, 300, at("2026-09-25T02:00")))
        assertFalse(NightSchedule.isNight(60, 300, at("2026-09-25T05:00")))
        assertFalse(NightSchedule.isNight(600, 600, at("2026-09-25T10:00")))
        assertNull(NightSchedule.nextTransition(600, 600, millis("2026-09-25T10:00"), prague))
    }

    @Test
    fun `next transition is the nearest start or end`() {
        assertEquals(
            millis("2026-09-25T22:00"),
            NightSchedule.nextTransition(start, end, millis("2026-09-25T12:00"), prague)
        )
        assertEquals(
            millis("2026-09-26T06:30"),
            NightSchedule.nextTransition(start, end, millis("2026-09-25T22:00"), prague)
        )
        assertEquals(
            millis("2026-09-26T06:30"),
            NightSchedule.nextTransition(start, end, millis("2026-09-26T01:00"), prague)
        )
    }

    @Test
    fun `DST spring forward moves a start inside the gap to the end of the gap`() {
        // 2026-03-29: 02:00 -> 03:00 in Prague.
        val gapStart = 2 * 60 + 30
        val next = NightSchedule.nextTransition(gapStart, 5 * 60, millis("2026-03-29T01:00"), prague)!!
        assertEquals(ZonedDateTime.of(LocalDateTime.parse("2026-03-29T03:30"), prague).toInstant().toEpochMilli(), next)
        assertTrue(NightSchedule.isNight(gapStart, 5 * 60, next, prague))
    }

    @Test
    fun `DST fall back keeps the schedule in local time`() {
        // 2026-10-25: 03:00 -> 02:00 in Prague. 06:30 local is still the end.
        val next = NightSchedule.nextTransition(start, end, millis("2026-10-25T01:00"), prague)
        assertEquals(millis("2026-10-25T06:30"), next)
    }

    @Test
    fun `parse and format`() {
        assertEquals(1320, NightSchedule.parse("22:00"))
        assertEquals(390, NightSchedule.parse("6:30"))
        assertNull(NightSchedule.parse("24:00"))
        assertNull(NightSchedule.parse("7"))
        assertNull(NightSchedule.parse("07:60"))
        assertEquals("06:30", NightSchedule.format(390))
        // Corrupt stored values don't crash.
        assertEquals("00:30", NightSchedule.format(1470))
        assertTrue(NightSchedule.nextTransition(-5, 99_999, millis("2026-09-25T12:00"), prague) != null)
    }
}
