package io.github.andy_walker_idfa.smarthome_dashboard.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 for the settings PIN, stored as `pbkdf2-sha256$<iterations>$<salt>$<hash>` (Base64).
 * The iteration count is part of the string, so it can be re-calibrated later without breaking stored PINs.
 *
 * A 4–8 digit PIN can always be brute-forced offline; the real protection is the Keystore encryption of the stored
 * string and the attempt limit. The hash keeps the PIN itself out of storage.
 */
object PinHasher {
    const val MIN_ITERATIONS = 50_000
    const val MAX_ITERATIONS = 600_000
    private const val PREFIX = "pbkdf2-sha256"
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val CALIBRATION_ITERATIONS = 20_000
    private const val TARGET_MS = 500.0
    private const val NANOS_PER_MS = 1_000_000.0

    private val base64 = Base64.getEncoder().withoutPadding()
    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    fun hash(pin: String, iterations: Int, salt: ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)): String {
        val hash = derive(pin, salt, iterations)
        return listOf(PREFIX, iterations.toString(), base64.encodeToString(salt), base64.encodeToString(hash))
            .joinToString("$")
    }

    /** Constant-time check of [pin] against a stored string; false for anything malformed. */
    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..MAX_ITERATIONS } ?: return false
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            MessageDigest.isEqual(derive(pin, salt, iterations), expected)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** Iterations that take about 0.5 s on this device, within [MIN_ITERATIONS]..[MAX_ITERATIONS]. */
    fun calibrate(): Int {
        val salt = ByteArray(SALT_BYTES)
        derive("0000", salt, CALIBRATION_ITERATIONS) // warm-up (JIT)
        val start = System.nanoTime()
        derive("0000", salt, CALIBRATION_ITERATIONS)
        val ms = ((System.nanoTime() - start) / NANOS_PER_MS).coerceAtLeast(1.0)
        return (CALIBRATION_ITERATIONS * TARGET_MS / ms).toInt().coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
