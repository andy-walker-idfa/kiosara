package io.github.andy_walker_idfa.smarthome_dashboard.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test
    fun `accepts http and https URLs unchanged`() {
        assertEquals("http://192.168.1.10:8123/", UrlValidator.normalize("http://192.168.1.10:8123/"))
        assertEquals("https://ha.example.com/lovelace", UrlValidator.normalize("  https://ha.example.com/lovelace "))
    }

    @Test
    fun `adds http scheme when missing`() {
        assertEquals("http://192.168.1.10:8123", UrlValidator.normalize("192.168.1.10:8123"))
    }

    @Test
    fun `rejects other schemes, blanks and hostless input`() {
        assertNull(UrlValidator.normalize(""))
        assertNull(UrlValidator.normalize("file:///sdcard/x.html"))
        assertNull(UrlValidator.normalize("javascript:alert(1)"))
        assertNull(UrlValidator.normalize("http://"))
        assertNull(UrlValidator.normalize("http://bad host/"))
    }

    @Test
    fun `extracts lower-case host`() {
        assertEquals("ha.local", UrlValidator.hostOf("https://HA.local:8443/x"))
        assertNull(UrlValidator.hostOf("not a url"))
    }

    @Test
    fun `validates hosts for self-signed allowance`() {
        assertTrue(UrlValidator.isValidHostOrBlank(""))
        assertTrue(UrlValidator.isValidHostOrBlank("192.168.1.10"))
        assertTrue(UrlValidator.isValidHostOrBlank("ha.local"))
        assertFalse(UrlValidator.isValidHostOrBlank("https://ha.local"))
        assertFalse(UrlValidator.isValidHostOrBlank("ha.local:8123"))
    }
}
