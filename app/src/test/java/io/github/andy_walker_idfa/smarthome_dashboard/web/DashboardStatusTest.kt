@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.andy_walker_idfa.smarthome_dashboard.web

import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStatusTest {
    @Test
    fun `controller mirrors page, errors, visibility and interaction into the status store`() = runTest {
        val store = DashboardStatusStore()
        val start = "http://ha:8123/"
        val controller =
            DashboardController(
                backgroundScope,
                FakeSettingsRepository(Settings(web = WebSettings(startUrl = start))),
                FakeNetworkMonitor(true),
                WebCommandBus(),
                FakeClock(testScheduler),
                store
            )
        controller.start()
        controller.onVisibilityChanged(true)
        runCurrent()
        assertTrue(store.status.value.active)

        controller.onPageStarted(start)
        controller.onPageFinished(start)
        assertEquals(start, store.status.value.currentUrl)
        assertNull(store.status.value.pageError)

        controller.onPageStarted(start)
        controller.onMainFrameError(start, LoadProblem.Http(502))
        val error = store.status.value.pageError
        assertNotNull(error)
        assertEquals("http", error!!.kind)
        assertEquals("HTTP 502", error.description)

        // A retry that fails again keeps the original "since".
        advanceTimeBy(3_000)
        controller.onPageStarted(start)
        controller.onMainFrameError(start, LoadProblem.Http(503))
        assertEquals(error.sinceMillis, store.status.value.pageError!!.sinceMillis)

        controller.onPageStarted(start)
        controller.onPageFinished(start)
        assertNull(store.status.value.pageError)

        controller.onViewportChanged(1340, 800, 200)
        assertEquals(Viewport(1340, 800, 200), store.status.value.viewport)
        assertEquals(1072, store.status.value.viewport!!.cssWidth)

        controller.onUserInteraction()
        assertEquals(testScheduler.currentTime, store.status.value.lastInteractionMillis)
        controller.onVisibilityChanged(false)
        assertEquals(false, store.status.value.active)
    }
}
