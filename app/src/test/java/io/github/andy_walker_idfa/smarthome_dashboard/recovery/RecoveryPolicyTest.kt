package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.app.ApplicationExitInfo
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.CornerTapDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPolicyTest {
    private val minute = 60_000L

    @Test
    fun `crash loop guard allows five restarts in 30 minutes`() {
        val now = 100 * minute
        assertTrue(CrashLoopGuard.mayRestart(emptyList(), now))
        assertTrue(CrashLoopGuard.mayRestart(List(4) { now - it * minute }, now))
        assertFalse(CrashLoopGuard.mayRestart(List(5) { now - it * minute }, now))
        // Older restarts don't count.
        assertTrue(CrashLoopGuard.mayRestart(List(5) { now - 31 * minute - it }, now))
    }

    @Test
    fun `exit reasons map to recovery kinds, normal exits are ignored`() {
        assertEquals(RecoveryKind.APP_CRASH, ExitReasons.classify(ApplicationExitInfo.REASON_CRASH, 0)?.first)
        assertEquals(RecoveryKind.APP_CRASH, ExitReasons.classify(ApplicationExitInfo.REASON_CRASH_NATIVE, 0)?.first)
        assertEquals(RecoveryKind.APP_FREEZE, ExitReasons.classify(ApplicationExitInfo.REASON_ANR, 0)?.first)
        assertEquals(
            RecoveryKind.APP_KILLED to "low memory",
            ExitReasons.classify(ApplicationExitInfo.REASON_LOW_MEMORY, 0)
        )
        assertEquals("signal 9", ExitReasons.classify(ApplicationExitInfo.REASON_SIGNALED, 9)?.second)
        for (normal in listOf(
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED,
            ApplicationExitInfo.REASON_USER_STOPPED
        )) {
            assertNull(ExitReasons.classify(normal, 0))
        }
    }

    @Test
    fun `five quick taps in the corner open settings`() {
        val taps = CornerTapDetector()
        for (i in 0 until 4) assertFalse(taps.onTap(inCorner = true, timeMillis = i * 400L))
        assertTrue(taps.onTap(inCorner = true, timeMillis = 1_600))
        // Starts over after a completed sequence.
        assertFalse(taps.onTap(inCorner = true, timeMillis = 1_700))
    }

    @Test
    fun `slow taps don't count, a tap elsewhere doesn't spoil the sequence`() {
        val slow = CornerTapDetector()
        for (i in 0 until 5) assertFalse(slow.onTap(inCorner = true, timeMillis = i * 1_100L))
        val withMiss = CornerTapDetector()
        repeat(3) { withMiss.onTap(inCorner = true, timeMillis = it * 300L) }
        assertFalse(withMiss.onTap(inCorner = false, timeMillis = 950))
        assertEquals(3, withMiss.progress(1_000))
        assertFalse(withMiss.onTap(inCorner = true, timeMillis = 1_200))
        assertTrue(withMiss.onTap(inCorner = true, timeMillis = 1_500))
        assertEquals(0, withMiss.progress(1_600))
    }

    @Test
    fun `progress runs out with the window`() {
        val taps = CornerTapDetector()
        taps.onTap(inCorner = true, timeMillis = 0)
        taps.onTap(inCorner = true, timeMillis = 500)
        assertEquals(2, taps.progress(1_000))
        assertEquals(1, taps.progress(4_200))
        assertEquals(0, taps.progress(4_600))
    }

    @Test
    fun `top-right corner`() {
        assertTrue(CornerTapDetector.isInTopRight(1300f, 10f, width = 1340, cornerSize = 80f))
        assertFalse(CornerTapDetector.isInTopRight(10f, 10f, width = 1340, cornerSize = 80f))
        assertFalse(CornerTapDetector.isInTopRight(1300f, 100f, width = 1340, cornerSize = 80f))
    }
}
