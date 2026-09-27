@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import io.github.andy_walker_idfa.smarthome_dashboard.settings.SecuritySettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeClock
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeSettingsRepository
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

class FakeKioskPolicy(var owner: Boolean = false) : KioskPolicy {
    var allowlisted = false
    var locked = false
    var cleared = false

    override val isDeviceOwner: Boolean get() = owner

    override fun allowLockTask(): Boolean {
        if (!owner) return false
        allowlisted = true
        return true
    }

    override fun isLockTaskPermitted(): Boolean = owner && allowlisted

    override fun isInLockTask(): Boolean = locked

    override fun clearDeviceOwner() {
        check(!locked) { "Lock task must be stopped before clearing device owner" }
        owner = false
        allowlisted = false
        cleared = true
    }
}

class KioskControllerTest {
    private class Harness(
        val controller: KioskController,
        val policy: FakeKioskPolicy,
        val settings: FakeSettingsRepository,
        var broughtToFront: Int = 0
    ) {
        val status get() = controller.status.value
        val stored get() = settings.state.value.security
    }

    private fun TestScope.harness(owner: Boolean, security: SecuritySettings = SecuritySettings()): Harness {
        val policy = FakeKioskPolicy(owner)
        val settings = FakeSettingsRepository(Settings(security = security))
        lateinit var h: Harness
        val controller =
            KioskController(backgroundScope, settings, policy, FakeClock(testScheduler)) { h.broughtToFront++ }
        h = Harness(controller, policy, settings)
        controller.start()
        runCurrent()
        return h
    }

    @Test
    fun `state a - not device owner - the lock can't be switched on`() = runTest {
        val h = harness(owner = false, SecuritySettings(pinEnabled = true))
        assertFalse(h.status.deviceOwner)
        assertFalse(h.status.canEnable)
        assertFalse(h.controller.setLockEnabled(true))
        assertFalse(h.status.shouldLock)
        assertFalse(h.policy.allowlisted)
    }

    @Test
    fun `state b - device owner, lock off - allowlisted but nothing is locked`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true))
        assertTrue(h.status.deviceOwner)
        assertTrue(h.policy.allowlisted)
        assertFalse(h.status.shouldLock)
    }

    @Test
    fun `the lock needs a PIN`() = runTest {
        val h = harness(owner = true)
        assertFalse(h.status.canEnable)
        assertFalse(h.controller.setLockEnabled(true))
        assertFalse(h.stored.kioskLock)
    }

    @Test
    fun `state c - lock on persists, and off persists until switched on again`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true))
        assertTrue(h.controller.setLockEnabled(true))
        runCurrent()
        assertTrue(h.stored.kioskLock)
        assertTrue(h.status.shouldLock)
        assertTrue(h.controller.setLockEnabled(false))
        runCurrent()
        assertFalse(h.stored.kioskLock)
        assertFalse(h.status.shouldLock)
    }

    @Test
    fun `removing the PIN switches the lock off`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true, kioskLock = true))
        assertTrue(h.status.shouldLock)
        h.settings.state.value = h.settings.state.value.let { it.copy(security = it.security.copy(pinEnabled = false)) }
        runCurrent()
        assertFalse(h.stored.kioskLock)
        assertFalse(h.status.shouldLock)
    }

    @Test
    fun `a stored lock without device owner is switched off`() = runTest {
        val h = harness(owner = false, SecuritySettings(pinEnabled = true, kioskLock = true))
        runCurrent()
        assertFalse(h.stored.kioskLock)
    }

    @Test
    fun `unlock for 15 minutes, then the dashboard is brought back and locks`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true, kioskLock = true))
        h.controller.unlockTemporarily()
        assertFalse(h.status.shouldLock)
        assertTrue(h.status.lockEnabled)
        advanceTimeBy(14.minutes + 59.seconds)
        assertFalse(h.status.shouldLock)
        advanceTimeBy(2.seconds)
        assertTrue(h.status.shouldLock)
        assertNull(h.status.unlockedUntilMillis)
        assertEquals(1, h.broughtToFront)
    }

    @Test
    fun `lock now ends the unlock early`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true, kioskLock = true))
        h.controller.unlockTemporarily()
        h.controller.relock()
        assertTrue(h.status.shouldLock)
        advanceTimeBy(20.minutes)
        assertEquals(1, h.broughtToFront)
    }

    @Test
    fun `unlocking does nothing while the lock is off`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true))
        h.controller.unlockTemporarily()
        assertNull(h.status.unlockedUntilMillis)
    }

    @Test
    fun `remove device owner - back to state a with the lock off`() = runTest {
        val h = harness(owner = true, SecuritySettings(pinEnabled = true, kioskLock = true))
        h.controller.removeDeviceOwner()
        runCurrent()
        assertTrue(h.policy.cleared)
        assertFalse(h.status.deviceOwner)
        assertFalse(h.stored.kioskLock)
        assertFalse(h.status.shouldLock)
    }

    @Test
    fun `provisioning while the app runs is picked up on refresh`() = runTest {
        val h = harness(owner = false, SecuritySettings(pinEnabled = true))
        h.policy.owner = true
        h.controller.refresh()
        assertTrue(h.status.deviceOwner)
        assertTrue(h.policy.allowlisted)
        assertTrue(h.controller.setLockEnabled(true))
    }
}
