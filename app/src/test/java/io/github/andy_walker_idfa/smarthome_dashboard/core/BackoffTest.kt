package io.github.andy_walker_idfa.smarthome_dashboard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffTest {
    private val backoff = Backoff(initialMillis = 2_000, maxMillis = 60_000)

    @Test
    fun `doubles from the initial delay`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L), (0..4).map(backoff::delayFor))
    }

    @Test
    fun `caps at the maximum`() {
        assertEquals(60_000L, backoff.delayFor(5))
        assertEquals(60_000L, backoff.delayFor(29))
    }

    @Test
    fun `huge attempt counts do not overflow`() {
        assertEquals(60_000L, backoff.delayFor(Int.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative attempt is rejected`() {
        backoff.delayFor(-1)
    }
}
