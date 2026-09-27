package io.github.andy_walker_idfa.smarthome_dashboard.security

import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.FakeSecretStore
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SecuritySettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeClock
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeSettingsRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun `hash has the versioned format and verifies`() {
        val stored = PinHasher.hash("1234", 1_000)
        val parts = stored.split('$')
        assertEquals(4, parts.size)
        assertEquals("pbkdf2-sha256", parts[0])
        assertEquals("1000", parts[1])
        assertTrue(PinHasher.verify("1234", stored))
        assertFalse(PinHasher.verify("1235", stored))
        assertFalse(PinHasher.verify("12345", stored))
    }

    @Test
    fun `salts differ, so equal PINs give different hashes`() {
        assertFalse(PinHasher.hash("1234", 1_000) == PinHasher.hash("1234", 1_000))
    }

    @Test
    fun `malformed stored values never verify`() {
        for (bad in listOf("", "x", "pbkdf2-sha256\$abc\$AA\$AA", "md5\$1000\$AA\$AA", "pbkdf2-sha256\$1000\$!!\$AA")) {
            assertFalse(bad, PinHasher.verify("1234", bad))
        }
    }

    @Test
    fun `PIN rules`() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }

    @Test
    fun `calibration stays within the limits`() {
        assertTrue(PinHasher.calibrate() in PinHasher.MIN_ITERATIONS..PinHasher.MAX_ITERATIONS)
    }
}

class PinAttemptsTest {
    @Test
    fun `five wrong PINs lock for a fixed 30 s`() = runTest {
        val attempts = PinAttempts(FakeClock(testScheduler))
        repeat(4) { attempts.onFailure() }
        assertEquals(0L, attempts.remainingLockoutMs())
        attempts.onFailure()
        assertEquals(30_000L, attempts.remainingLockoutMs())
        advanceTimeBy(30_000)
        assertEquals(0L, attempts.remainingLockoutMs())
        // The next lockout is again 30 s (no doubling).
        repeat(5) { attempts.onFailure() }
        assertEquals(30_000L, attempts.remainingLockoutMs())
    }

    @Test
    fun `a correct PIN resets the count`() = runTest {
        val attempts = PinAttempts(FakeClock(testScheduler))
        repeat(4) { attempts.onFailure() }
        attempts.onSuccess()
        repeat(4) { attempts.onFailure() }
        assertEquals(0L, attempts.remainingLockoutMs())
    }
}

class SettingsLockTest {
    private class Harness(val lock: SettingsLock, val secrets: FakeSecretStore, val settings: FakeSettingsRepository)

    private fun TestScope.harness(pinEnabled: Boolean = false, stored: String? = null): Harness {
        val secrets =
            FakeSecretStore(stored?.let { mutableMapOf(SecuritySettings.PIN_SECRET_KEY to it) } ?: mutableMapOf())
        val settings = FakeSettingsRepository(Settings(security = SecuritySettings(pinEnabled)))
        val lock = SettingsLock(secrets, settings, FakeClock(testScheduler), StandardTestDispatcher(testScheduler))
        return Harness(lock, secrets, settings)
    }

    @Test
    fun `no PIN opens directly`() = runTest {
        assertEquals(SettingsLock.Access.Open, harness().lock.access())
    }

    @Test
    fun `set, verify and remove a PIN`() = runTest {
        val h = harness()
        assertTrue(h.lock.setPin("4711"))
        assertTrue(h.settings.state.value.security.pinEnabled)
        assertEquals(SettingsLock.Access.NeedPin, h.lock.access())
        assertEquals(SettingsLock.Result.Wrong, h.lock.verify("0000"))
        assertEquals(SettingsLock.Result.Ok, h.lock.verify("4711"))
        h.lock.clearPin()
        assertFalse(h.settings.state.value.security.pinEnabled)
        assertNull(h.secrets.get(SecuritySettings.PIN_SECRET_KEY))
        assertEquals(SettingsLock.Access.Open, h.lock.access())
    }

    @Test
    fun `an unreadable PIN fails open and clears the flag`() = runTest {
        val h = harness(pinEnabled = true, stored = null)
        assertEquals(SettingsLock.Access.PinLost, h.lock.access())
        assertFalse(h.settings.state.value.security.pinEnabled)
        assertEquals(SettingsLock.Access.Open, h.lock.access())
    }

    @Test
    fun `a leftover hash without the flag is not a PIN`() = runTest {
        val h = harness(pinEnabled = false, stored = PinHasher.hash("1234", 1_000))
        assertEquals(SettingsLock.Access.Open, h.lock.access())
        assertNull(h.secrets.get(SecuritySettings.PIN_SECRET_KEY))
    }

    @Test
    fun `lockout after five wrong PINs, even for the right one`() = runTest {
        val h = harness(pinEnabled = true, stored = PinHasher.hash("4711", 1_000))
        repeat(4) { assertEquals(SettingsLock.Result.Wrong, h.lock.verify("0000")) }
        assertTrue(h.lock.verify("0000") is SettingsLock.Result.LockedOut)
        assertTrue(h.lock.verify("4711") is SettingsLock.Result.LockedOut)
        advanceTimeBy(30_001)
        assertEquals(SettingsLock.Result.Ok, h.lock.verify("4711"))
    }
}
