@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.andy_walker_idfa.smarthome_dashboard.web

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardControllerTest {
    private val start = "http://ha:8123/"

    private class Harness(
        val controller: DashboardController,
        val settings: FakeSettingsRepository,
        val network: FakeNetworkMonitor,
        val bus: WebCommandBus
    ) {
        /** Returns and acknowledges all pending actions, like MainActivity does. */
        fun drain(): List<WebAction> {
            val requests = controller.state.value.requests
            requests.forEach { controller.onRequestHandled(it.id) }
            return requests.map { it.action }
        }
    }

    private fun TestScope.harness(
        web: WebSettings = WebSettings(startUrl = start),
        connected: Boolean = true
    ): Harness {
        val settings = FakeSettingsRepository(Settings(web = web))
        val network = FakeNetworkMonitor(connected)
        val bus = WebCommandBus()
        val controller =
            DashboardController(
                backgroundScope,
                settings,
                network,
                bus,
                FakeClock(testScheduler),
                DashboardStatusStore(),
                watchdogEnabled = false
            )
        controller.start()
        controller.onVisibilityChanged(true)
        runCurrent()
        return Harness(controller, settings, network, bus)
    }

    private fun Harness.loadOk(url: String = start) {
        controller.onPageStarted(url)
        controller.onPageFinished(url)
    }

    private fun Harness.loadFail(url: String = start) {
        controller.onPageStarted(url)
        controller.onMainFrameError(url, LoadProblem.Network("net::ERR_CONNECTION_REFUSED"))
        controller.onPageFinished(url)
    }

    @Test
    fun `loads the start URL once settings arrive`() = runTest {
        val h = harness()
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
        h.loadOk()
        assertEquals(PageState.Loaded, h.controller.state.value.page)
    }

    @Test
    fun `failed load shows error and retries with exponential backoff`() = runTest {
        val h = harness()
        h.drain()
        h.loadFail()
        val error = h.controller.state.value.page as PageState.Error
        assertEquals(2_000L, error.retryAtElapsedMillis!! - testScheduler.currentTime)

        advanceTimeBy(1_999)
        assertTrue(h.drain().isEmpty())
        advanceTimeBy(2)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())

        h.loadFail()
        advanceTimeBy(4_001)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())

        h.loadOk()
        assertEquals(PageState.Loaded, h.controller.state.value.page)
        // Backoff resets after a success.
        h.loadFail()
        advanceTimeBy(2_001)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
    }

    @Test
    fun `retries pause while offline and resume immediately on reconnect`() = runTest {
        val h = harness()
        h.drain()
        h.network.state.value = false
        runCurrent()
        h.loadFail()
        val error = h.controller.state.value.page as PageState.Error
        assertTrue(error.waitingForNetwork)
        assertNull(error.retryAtElapsedMillis)

        advanceTimeBy(10.minutes)
        assertTrue(h.drain().isEmpty())

        h.network.state.value = true
        runCurrent()
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
    }

    @Test
    fun `short network blip does not reload a healthy page but a long outage does`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk()

        h.network.state.value = false
        runCurrent()
        advanceTimeBy(5.seconds)
        h.network.state.value = true
        runCurrent()
        assertTrue(h.drain().isEmpty())

        h.network.state.value = false
        runCurrent()
        advanceTimeBy(45.seconds)
        h.network.state.value = true
        runCurrent()
        assertEquals(listOf(WebAction.Reload), h.drain())
    }

    @Test
    fun `page loaded while offline is reloaded on reconnect even after a short blip`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk()
        h.network.state.value = false
        runCurrent()
        // e.g. a renderer restart while offline: HA's service worker serves a cached shell.
        h.loadOk()
        advanceTimeBy(3.seconds)
        h.network.state.value = true
        runCurrent()
        assertEquals(listOf(WebAction.Reload), h.drain())
    }

    @Test
    fun `starting offline does not count as a reconnect until the network returns`() = runTest {
        val h = harness(connected = false)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
    }

    @Test
    fun `when night starts the start page is loaded, once per night`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk("http://ha:8123/lovelace/cameras")
        // The first value only records the state (e.g. the app starts in the middle of the night).
        h.controller.onNightChanged(true)
        assertTrue(h.drain().isEmpty())
        h.controller.onNightChanged(false)
        h.controller.onNightChanged(false)
        assertTrue(h.drain().isEmpty())
        h.controller.onNightChanged(true)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
        h.controller.onNightChanged(true)
        assertTrue(h.drain().isEmpty())
    }

    @Test
    fun `night start does nothing while the app is not set up`() = runTest {
        val h = harness(WebSettings())
        h.drain()
        h.controller.onNightChanged(false)
        h.controller.onNightChanged(true)
        assertTrue(h.drain().isEmpty())
    }

    @Test
    fun `without a start URL the page is NotConfigured and nothing loads`() = runTest {
        val h = harness(WebSettings(startUrl = ""))
        assertEquals(PageState.NotConfigured, h.controller.state.value.page)
        assertTrue(h.drain().isEmpty())

        h.bus.send(WebCommand.LoadStartUrl)
        h.controller.retryNow()
        runCurrent()
        assertTrue(h.drain().isEmpty())

        h.settings.state.value = Settings(web = WebSettings(startUrl = start))
        runCurrent()
        assertEquals(PageState.Loading, h.controller.state.value.page)
        assertEquals(listOf(WebAction.LoadUrl(start)), h.drain())
    }

    @Test
    fun `clearing the start URL returns to NotConfigured`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk()
        h.settings.state.value = Settings(web = WebSettings(startUrl = ""))
        runCurrent()
        assertEquals(PageState.NotConfigured, h.controller.state.value.page)
        assertTrue(h.drain().isEmpty())
    }

    @Test
    fun `changing the start URL loads it`() = runTest {
        val h = harness()
        h.drain()
        h.settings.state.value = Settings(web = WebSettings(startUrl = "http://ha:8123/lovelace/kiosk"))
        runCurrent()
        assertEquals(listOf(WebAction.LoadUrl("http://ha:8123/lovelace/kiosk")), h.drain())
    }

    @Test
    fun `commands from the bus become web actions`() = runTest {
        val h = harness()
        h.drain()
        h.bus.send(WebCommand.ReloadPage)
        h.bus.send(WebCommand.LoadUrl("http://ha:8123/cams"))
        h.bus.send(WebCommand.ClearCache)
        runCurrent()
        assertEquals(
            listOf(WebAction.Reload, WebAction.LoadUrl("http://ha:8123/cams"), WebAction.ClearCacheAndReload),
            h.drain()
        )
    }

    @Test
    fun `renderer death reloads the current URL until it becomes a crash loop`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk("http://ha:8123/lovelace/0")
        repeat(3) {
            h.controller.onRendererGone(crashed = true)
            assertEquals(listOf(WebAction.LoadUrl("http://ha:8123/lovelace/0")), h.drain())
            advanceTimeBy(10.seconds)
        }
        h.controller.onRendererGone(crashed = true)
        assertTrue(h.drain().isEmpty())
        val error = h.controller.state.value.page as PageState.Error
        assertEquals(LoadProblem.RendererCrashLoop, error.problem)
    }

    @Test
    fun `certificate is trusted only for the start URL host and pinned fingerprint`() = runTest {
        val h = harness(WebSettings(startUrl = "https://ha.local/"))
        h.drain()
        assertFalse(h.controller.shouldTrustCertificate("ha.local", "AA:BB"))

        h.controller.onMainFrameError("https://ha.local/", LoadProblem.UntrustedCertificate("ha.local", "AA:BB"))
        val problem = (h.controller.state.value.page as PageState.Error).problem as LoadProblem.UntrustedCertificate
        assertTrue(problem.trustable)

        h.controller.trustCertificate(problem)
        runCurrent()
        assertTrue(h.controller.shouldTrustCertificate("HA.local", "aa:bb"))
        assertFalse(h.controller.shouldTrustCertificate("evil.local", "AA:BB"))
        assertFalse(h.controller.shouldTrustCertificate("ha.local", "CC:DD"))
        // Pinning while in error retries straight away.
        assertEquals(listOf(WebAction.LoadUrl("https://ha.local/")), h.drain())
    }

    @Test
    fun `certificate for another host is not trustable`() = runTest {
        val h = harness(WebSettings(startUrl = "https://ha.local/"))
        h.drain()
        h.controller.onMainFrameError("https://cdn.example/", LoadProblem.UntrustedCertificate("cdn.example", "AA:BB"))
        val problem = (h.controller.state.value.page as PageState.Error).problem as LoadProblem.UntrustedCertificate
        assertFalse(problem.trustable)
    }

    @Test
    fun `after a long pause a disconnected frontend is reloaded after 10 s`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk()
        h.controller.onDashboardPaused(true)
        advanceTimeBy(5.minutes)
        h.controller.onDashboardPaused(false)
        advanceTimeBy(9.seconds)
        runCurrent()
        assertEquals(emptyList<WebAction>(), h.drain())
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(listOf(WebAction.CheckHaConnection), h.drain())
        h.controller.onHaConnectionChecked(HaConnection.DISCONNECTED)
        assertEquals(listOf(WebAction.Reload), h.drain())
    }

    @Test
    fun `a connected frontend or a short pause needs nothing`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk()
        h.controller.onDashboardPaused(true)
        advanceTimeBy(1.minutes)
        h.controller.onDashboardPaused(false)
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(emptyList<WebAction>(), h.drain())

        h.controller.onDashboardPaused(true)
        advanceTimeBy(10.minutes)
        h.controller.onDashboardPaused(false)
        advanceTimeBy(11.seconds)
        runCurrent()
        assertEquals(listOf(WebAction.CheckHaConnection), h.drain())
        h.controller.onHaConnectionChecked(HaConnection.CONNECTED)
        h.controller.onHaConnectionChecked(HaConnection.UNKNOWN)
        assertEquals(emptyList<WebAction>(), h.drain())
    }

    @Test
    fun `sleeping again cancels the pending check`() = runTest {
        val h = harness()
        h.drain()
        h.loadOk()
        h.controller.onDashboardPaused(true)
        advanceTimeBy(5.minutes)
        h.controller.onDashboardPaused(false)
        advanceTimeBy(5.seconds)
        h.controller.onDashboardPaused(true)
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(emptyList<WebAction>(), h.drain())
    }
}
