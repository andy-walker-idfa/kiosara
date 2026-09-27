package io.github.andy_walker_idfa.smarthome_dashboard.device

import io.github.andy_walker_idfa.smarthome_dashboard.settings.DeviceSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.web.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    private val hex32 = Regex("^[0-9a-f]{32}$")

    @Test
    fun `same ANDROID_ID always gives the same 32-hex id`() {
        val a = DeviceIdentity.derive("a1b2c3d4e5f60718")
        assertTrue(a, hex32.matches(a))
        assertEquals(a, DeviceIdentity.derive(" A1B2C3D4E5F60718 "))
    }

    @Test
    fun `id is a hash, not the raw ANDROID_ID`() {
        val id = DeviceIdentity.derive("a1b2c3d4e5f60718")
        assertTrue(!id.contains("a1b2c3d4e5f60718"))
        assertNotEquals(id, DeviceIdentity.derive("a1b2c3d4e5f60719"))
    }

    @Test
    fun `missing or bogus ANDROID_ID falls back to the random UUID`() {
        val uuid = { "123e4567-e89b-12d3-a456-426614174000" }
        val expected = "123e4567e89b12d3a456426614174000"
        assertEquals(expected, DeviceIdentity.derive(null, uuid))
        assertEquals(expected, DeviceIdentity.derive("  ", uuid))
        assertEquals(expected, DeviceIdentity.derive("9774d56d682e549c", uuid))
    }

    @Test
    fun `ensureStored creates the id once and never overwrites it`() = runTest {
        val repo = FakeSettingsRepository()
        DeviceIdentity.ensureStored(repo) { "a1b2c3d4e5f60718" }
        val first = repo.state.value.device.id
        assertEquals(DeviceIdentity.derive("a1b2c3d4e5f60718"), first)

        DeviceIdentity.ensureStored(repo) { "ffffffffffffffff" }
        assertEquals(first, repo.state.value.device.id)
    }

    @Test
    fun `ensureStored keeps a user-edited id`() = runTest {
        val repo = FakeSettingsRepository(Settings(device = DeviceSettings(id = "custom-id")))
        DeviceIdentity.ensureStored(repo) { "a1b2c3d4e5f60718" }
        assertEquals("custom-id", repo.state.value.device.id)
    }
}
