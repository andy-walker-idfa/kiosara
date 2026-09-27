package io.github.andy_walker_idfa.smarthome_dashboard.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecretStore] backed by an AES-256-GCM key in the Android Keystore. Ciphertexts are stored in a
 * private SharedPreferences file, which is excluded from backup and device transfer.
 *
 * The key does not require user authentication: the kiosk has no lock screen, and a key bound to
 * authentication would be unusable. Each value is bound to its entry name (GCM associated data), so a
 * ciphertext copied to another entry fails to decrypt.
 */
class KeystoreSecretStore(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    prefsName: String = DEFAULT_PREFS_NAME
) : SecretStore {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /**
     * Any failure counts as "missing, ask again", never as a crash: besides [GeneralSecurityException], the Keystore
     * throws unchecked exceptions (e.g. `ProviderException` from keystore2) and `KeyStore.load` can throw
     * [IOException]. A crash here would restart the app on every attempt to open Settings (fail open instead).
     */
    @Synchronized
    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            decrypt(key, stored)
        } catch (e: GeneralSecurityException) {
            discardUnreadable(key, e)
        } catch (e: IOException) {
            discardUnreadable(key, e)
        } catch (e: RuntimeException) {
            // Includes IllegalArgumentException (bad Base64/format) and ProviderException.
            discardUnreadable(key, e)
        }
    }

    @Synchronized
    override fun put(key: String, value: String): Boolean = try {
        prefs.edit(commit = true) { putString(key, encrypt(key, value)) }
        true
    } catch (e: GeneralSecurityException) {
        encryptFailed(key, e)
    } catch (e: IOException) {
        encryptFailed(key, e)
    } catch (e: RuntimeException) {
        encryptFailed(key, e)
    }

    private fun encryptFailed(key: String, error: Exception): Boolean {
        AppLog.e(TAG, "Encrypting secret '$key' failed", error)
        return false
    }

    @Synchronized
    override fun remove(key: String) {
        prefs.edit(commit = true) { remove(key) }
    }

    private fun discardUnreadable(key: String, error: Exception): String? {
        // Never log the value or ciphertext. The entry is removed so the UI asks for it again.
        AppLog.w(TAG, "Secret '$key' can't be decrypted (${error.javaClass.simpleName}); treating as missing")
        prefs.edit(commit = true) { remove(key) }
        return null
    }

    private fun encrypt(entry: String, plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(entry.encodeToByteArray())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        val blob =
            ByteBuffer
                .allocate(2 + iv.size + ciphertext.size)
                .put(FORMAT_VERSION)
                .put(iv.size.toByte())
                .put(iv)
                .put(ciphertext)
                .array()
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decrypt(entry: String, stored: String): String {
        val buffer = ByteBuffer.wrap(Base64.decode(stored, Base64.NO_WRAP))
        require(buffer.remaining() > 2) { "Blob too short" }
        require(buffer.get() == FORMAT_VERSION) { "Unknown blob format" }
        val ivSize = buffer.get().toInt()
        require(ivSize in 12..16 && buffer.remaining() > ivSize) { "Invalid IV length" }
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val key = existingKey() ?: throw GeneralSecurityException("Keystore key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(entry.encodeToByteArray())
        return cipher.doFinal(ciphertext).decodeToString()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(keyAlias, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey = existingKey() ?: generateKey()

    private fun generateKey(): SecretKey {
        AppLog.i(TAG, "Generating Keystore key '$keyAlias'")
        val spec =
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    companion object {
        const val DEFAULT_KEY_ALIAS = "shdash_secrets_v1"
        const val DEFAULT_PREFS_NAME = "secrets"
        private const val TAG = "SecretStore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }
}
