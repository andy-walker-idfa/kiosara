package io.github.andy_walker_idfa.smarthome_dashboard.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "wallpanel_test_key"
    private val prefsName = "secrets_test"

    private fun store() = KeystoreSecretStore(context, alias, prefsName)

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)

    @After
    fun cleanUp() {
        prefs().edit().clear().commit()
        deleteKey()
    }

    @Test
    fun roundTripSurvivesNewInstance() {
        assertTrue(store().put("mqtt_password", "s3cr3t-ěščř"))
        assertEquals("s3cr3t-ěščř", store().get("mqtt_password"))
    }

    @Test
    fun valueIsNotStoredInPlaintext() {
        store().put("token", "plain-value")
        val raw = prefs().getString("token", null)!!
        assertTrue(!raw.contains("plain-value"))
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(store().get("never_set"))
    }

    @Test
    fun tamperedCiphertextIsTreatedAsMissingAndRemoved() {
        store().put("token", "value")
        val raw = prefs().getString("token", null)!!
        val tampered = raw.substring(0, raw.length - 4) + if (raw.endsWith("AAAA")) "BBBB" else "AAAA"
        prefs().edit().putString("token", tampered).commit()

        assertNull(store().get("token"))
        assertTrue(!prefs().contains("token"))
    }

    @Test
    fun lostKeystoreKeyIsTreatedAsMissing() {
        store().put("token", "value")
        deleteKey()
        assertNull(store().get("token"))
        // A new key is created transparently for the next write.
        assertTrue(store().put("token", "new"))
        assertEquals("new", store().get("token"))
    }

    @Test
    fun ciphertextCannotBeMovedToAnotherEntry() {
        store().put("a", "value-a")
        prefs().edit().putString("b", prefs().getString("a", null)).commit()
        assertNull(store().get("b"))
    }

    @Test
    fun garbageIsTreatedAsMissing() {
        prefs().edit().putString("token", "%%% not base64 %%%").commit()
        assertNull(store().get("token"))
    }
}
