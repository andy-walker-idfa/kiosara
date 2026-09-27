package io.github.andy_walker_idfa.smarthome_dashboard.display

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The app's own day/night schedule: night from [start] to [end] (local time, minutes after midnight),
 * possibly across midnight. Start == end means the schedule is off (never night).
 *
 * Works on wall-clock time and the time zone, so callers must re-evaluate after clock or time zone changes.
 */
object NightSchedule {
    private const val MINUTES_PER_HOUR = 60

    fun isNight(start: Int, end: Int, time: ZonedDateTime): Boolean {
        val start = normalize(start)
        val end = normalize(end)
        if (start == end) return false
        val minute = time.hour * MINUTES_PER_HOUR + time.minute
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun isNight(start: Int, end: Int, nowMillis: Long, zone: ZoneId): Boolean =
        isNight(start, end, Instant.ofEpochMilli(nowMillis).atZone(zone))

    /**
     * The next moment (epoch ms, strictly after [nowMillis]) at which the schedule switches, or null if it
     * never does. A start or end inside a DST gap moves to the first valid local time after the gap.
     */
    fun nextTransition(start: Int, end: Int, nowMillis: Long, zone: ZoneId): Long? {
        val start = normalize(start)
        val end = normalize(end)
        if (start == end) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return (0L..2L)
            .flatMap { days -> listOf(start, end).map { today.plusDays(days) to it } }
            .map { (date, minute) ->
                val local = LocalTime.of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)
                ZonedDateTime.of(date, local, zone).toInstant().toEpochMilli()
            }
            .filter { it > nowMillis }
            .minOrNull()
    }

    /** Any stored value to 0..1439, so a corrupt or imported value can't crash the schedule. */
    private fun normalize(minutes: Int) = Math.floorMod(minutes, MINUTES_PER_DAY)

    private const val MINUTES_PER_DAY = 24 * 60

    /** "22:00" for 1320. */
    fun format(minutes: Int): String {
        val m = normalize(minutes)
        return String.format(Locale.ROOT, "%02d:%02d", m / MINUTES_PER_HOUR, m % MINUTES_PER_HOUR)
    }

    /** Parses "H:mm" or "HH:mm" (24 h); null if invalid. */
    fun parse(text: String): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text.trim()) ?: return null
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        if (hours > 23 || minutes > 59) return null
        return hours * MINUTES_PER_HOUR + minutes
    }
}
