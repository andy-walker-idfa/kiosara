package io.github.andy_walker_idfa.smarthome_dashboard.settings

import androidx.datastore.core.CorruptionException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSerializerTest {
    private suspend fun roundTrip(settings: Settings): Settings {
        val out = ByteArrayOutputStream()
        SettingsSerializer.writeTo(settings, out)
        return SettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
    }

    private suspend fun read(json: String): Settings =
        SettingsSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))

    @Test
    fun `round trip preserves every field`() = runTest {
        val settings =
            Settings(
                web =
                    WebSettings(
                        startUrl = "http://ha.local:8123/lovelace/0",
                        textZoomPercent = 90,
                        trustedCertSha256 = "AB:CD"
                    ),
                mqtt = MqttSettings(enabled = true, host = "ha.local", port = 1884, username = "panel"),
                screen =
                    ScreenSettings(
                        brightnessMode = BrightnessMode.MANUAL,
                        manualBrightnessPercent = 30,
                        nightEnabled = true,
                        nightStartMinutes = 1320,
                        nightEndMinutes = 420
                    ),
                security = SecuritySettings(pinEnabled = true, kioskLock = true)
            )
        assertEquals(settings, roundTrip(settings))
    }

    @Test
    fun `defaults have schema version, no start URL and no device id yet`() = runTest {
        val defaults = read("{}")
        assertEquals(Settings.CURRENT_SCHEMA_VERSION, defaults.schemaVersion)
        assertEquals("", defaults.web.startUrl)
        assertEquals("", defaults.device.id)
    }

    @Test
    fun `unknown keys from a newer version are ignored`() = runTest {
        val settings =
            read("""{"schemaVersion":1,"futureFeature":{"x":1},"web":{"textZoomPercent":80,"newKey":true}}""")
        assertEquals(80, settings.web.textZoomPercent)
    }

    @Test
    fun `settings from 0_4_0 with automatic brightness load as System`() = runTest {
        val settings =
            read(
                """{"schemaVersion":1,"screen":{"brightnessMode":"AUTO","manualBrightnessPercent":70,""" +
                    """"autoMinPercent":5,"autoMaxPercent":100,"autoLuxAtMax":500,"screensaverTimeoutMinutes":3}}"""
            )
        assertEquals(BrightnessMode.SYSTEM, settings.screen.brightnessMode)
        assertEquals(70, settings.screen.manualBrightnessPercent)
    }

    @Test
    fun `settings from 0_7_x keep what is still a setting and drop the rest`() = runTest {
        val settings =
            read(
                """{"schemaVersion":1,"device":{"id":"abc","name":"Kitchen"},""" +
                    """"web":{"startUrl":"http://ha.local:8123/","extraUrls":[{"name":"Cams","url":"http://x/"}],""" +
                    """"orientation":"PORTRAIT","textZoomPercent":120,"periodicReloadMinutes":30},""" +
                    """"mqtt":{"enabled":true,"host":"ha.local","port":1884,"baseTopic":"x/y"},""" +
                    """"screen":{"nightEnabled":true,"nightSource":"HOME_ASSISTANT","nightDisplayMode":"clock",""" +
                    """"screensaverTimeoutMinutes":3,"nightStartMinutes":1300}}"""
            )
        assertEquals("abc", settings.device.id)
        assertEquals("http://ha.local:8123/", settings.web.startUrl)
        assertEquals(120, settings.web.textZoomPercent)
        assertEquals(MqttSettings(enabled = true, host = "ha.local", port = 1884), settings.mqtt)
        assertTrue(settings.screen.nightEnabled)
        assertEquals(1300, settings.screen.nightStartMinutes)
    }

    @Test
    fun `garbage is reported as corruption so DataStore can reset it`() = runTest {
        val error = runCatching { read("not json") }.exceptionOrNull()
        assertTrue("expected CorruptionException, got $error", error is CorruptionException)
    }
}
