@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.andy_walker_idfa.smarthome_dashboard.web

import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryKind
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoverySink
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardWatchdogTest {
    private val start = "http://ha:8123/"

    private class Harness(val controller: DashboardController, val recoveries: MutableList<RecoveryKind>) {
        fun drain(): List<WebAction> {
            val requests = controller.state.value.requests
            requests.forEach { controller.onRequestHandled(it.id) }
            return requests.map { it.action }
        }
    }

    private fun TestScope.harness(): Harness {
        val recoveries = mutableListOf<RecoveryKind>()
        val controller =
            DashboardController(
                scope = backgroundScope,
                settingsRepository = FakeSettingsRepository(Settings(web = WebSettings(startUrl = start))),
                networkMonitor = FakeNetworkMonitor(true),
                commandBus = WebCommandBus(),
                clock = FakeClock(testScheduler),
                statusSink = DashboardStatusStore(),
                recovery = RecoverySink { kind, _ -> recoveries += kind }
            )
        controller.start()
        controller.onVisibilityChanged(true)
        runCurrent()
        controller.onPageStarted(start)
        controller.onPageFinished(start)
        val h = Harness(controller, recoveries)
        h.drain()
        return h
    }

    @Test
    fun `an answered probe changes nothing`() = runTest {
        val h = harness()
        advanceTimeBy(61.seconds)
        val probe = h.drain().single() as WebAction.ProbeRenderer
        h.controller.onProbeAnswered(probe.id)
        advanceTimeBy(20.seconds)
        assertEquals(emptyList<WebAction>(), h.drain())
        assertTrue(h.recoveries.isEmpty())
    }

    @Test
    fun `an unanswered probe terminates the renderer once, then the page reloads`() = runTest {
        val h = harness()
        advanceTimeBy(61.seconds)
        assertTrue(h.drain().single() is WebAction.ProbeRenderer)
        advanceTimeBy(11.seconds)
        assertEquals(listOf(WebAction.TerminateRenderer), h.drain())
        assertEquals(listOf(RecoveryKind.PAGE_UNRESPONSIVE), h.recoveries)
        // The host rebuilds the WebView and reports the death; it isn't recorded a second time.
        h.controller.onRendererGone(crashed = false)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
        assertEquals(listOf(RecoveryKind.PAGE_UNRESPONSIVE), h.recoveries)
    }

    @Test
    fun `a renderer that hangs while loading is terminated`() = runTest {
        val h = harness()
        h.controller.onPageStarted("chrome://hang")
        advanceTimeBy(61.seconds)
        assertTrue(h.drain().single() is WebAction.ProbeRenderer)
        advanceTimeBy(11.seconds)
        assertEquals(listOf(WebAction.TerminateRenderer), h.drain())
    }

    @Test
    fun `renderer unresponsive to input for 15 s is terminated`() = runTest {
        val h = harness()
        h.controller.onRendererUnresponsive()
        advanceTimeBy(10.seconds)
        h.controller.onRendererUnresponsive()
        assertEquals(emptyList<WebAction>(), h.drain())
        advanceTimeBy(6.seconds)
        h.controller.onRendererUnresponsive()
        assertEquals(listOf(WebAction.TerminateRenderer), h.drain())
    }

    @Test
    fun `responsive again resets the unresponsive timer`() = runTest {
        val h = harness()
        h.controller.onRendererUnresponsive()
        advanceTimeBy(10.seconds)
        h.controller.onRendererResponsive()
        h.controller.onRendererUnresponsive()
        advanceTimeBy(10.seconds)
        h.controller.onRendererUnresponsive()
        assertEquals(emptyList<WebAction>(), h.drain())
    }

    @Test
    fun `a renderer crash or kill is recorded`() = runTest {
        val h = harness()
        h.controller.onRendererGone(crashed = true)
        h.controller.onRendererGone(crashed = false)
        assertEquals(listOf(RecoveryKind.PAGE_CRASH, RecoveryKind.PAGE_KILLED), h.recoveries)
    }

    @Test
    fun `the HA frontend is checked every 5 minutes`() = runTest {
        val h = harness()
        val checks = mutableListOf<WebAction>()
        repeat(5) {
            advanceTimeBy(61.seconds)
            for (action in h.drain()) {
                if (action is WebAction.ProbeRenderer) h.controller.onProbeAnswered(action.id) else checks += action
            }
        }
        assertEquals(listOf(WebAction.CheckHaConnection), checks)
        assertTrue(h.recoveries.isEmpty())
    }

    @Test
    fun `two disconnected HA checks in a row reload the page`() = runTest {
        val h = harness()
        h.controller.onHaConnectionChecked(HaConnection.DISCONNECTED)
        assertEquals(emptyList<WebAction>(), h.drain())
        h.controller.onHaConnectionChecked(HaConnection.UNKNOWN)
        h.controller.onHaConnectionChecked(HaConnection.DISCONNECTED)
        assertEquals(listOf(WebAction.Reload), h.drain())
        assertEquals(listOf(RecoveryKind.FRONTEND_DISCONNECTED), h.recoveries)
    }

    @Test
    fun `a connected answer resets the disconnected count`() = runTest {
        val h = harness()
        h.controller.onHaConnectionChecked(HaConnection.DISCONNECTED)
        h.controller.onHaConnectionChecked(HaConnection.CONNECTED)
        h.controller.onHaConnectionChecked(HaConnection.DISCONNECTED)
        assertEquals(emptyList<WebAction>(), h.drain())
    }

    @Test
    fun `no probes while paused or hidden`() = runTest {
        val h = harness()
        h.controller.onDashboardPaused(true)
        // Wake between two watchdog ticks (every minute).
        advanceTimeBy(10.minutes + 30.seconds)
        assertEquals(emptyList<WebAction>(), h.drain())
        h.controller.onDashboardPaused(false)
        advanceTimeBy(11.seconds)
        // The wake check after a long pause.
        assertEquals(listOf(WebAction.CheckHaConnection), h.drain())
        h.controller.onVisibilityChanged(false)
        advanceTimeBy(10.minutes)
        assertEquals(emptyList<WebAction>(), h.drain())
    }
}
