package io.github.andy_walker_idfa.smarthome_dashboard.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import java.security.MessageDigest
import java.util.UUID

/**
 * Stable identifier of this panel for Home Assistant (MQTT unique_ids, device identifiers, default topics).
 *
 * Derived from `Settings.Secure.ANDROID_ID`, which on Android 8+ is scoped to the app's signing key,
 * user and device and survives reinstalls. It is hashed with a fixed domain-separation prefix, so the
 * published id is not the raw ANDROID_ID. It changes only on a factory reset or with a different signing
 * key; in those cases HA sees a new device. Falls back to a random UUID when ANDROID_ID is unavailable.
 * The id is stored in settings once, so a later reset/edit option (Phase 6) can override it.
 */
object DeviceIdentity {
    /** 32 hex chars = 128 bits. */
    const val ID_LENGTH = 32

    private const val HASH_PREFIX = "shdash-device-id-v1:"

    // Known bogus value reported by some old or broken builds; treated as unavailable.
    private val INVALID_ANDROID_IDS = setOf("9774d56d682e549c", "0000000000000000")

    /** Pure derivation, unit-tested. */
    fun derive(androidId: String?, randomUuid: () -> String = { UUID.randomUUID().toString() }): String {
        val usable = androidId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it !in INVALID_ANDROID_IDS }
        return if (usable != null) {
            sha256Hex(HASH_PREFIX + usable).take(ID_LENGTH)
        } else {
            randomUuid().replace("-", "").lowercase().take(ID_LENGTH)
        }
    }

    /** Stores a device id in settings if none exists yet. Safe to call on every start. */
    suspend fun ensureStored(settingsRepository: SettingsRepository, androidId: () -> String?) {
        settingsRepository.update { settings ->
            if (settings.device.id.isNotBlank()) {
                settings
            } else {
                val source = androidId()
                val id = derive(source)
                AppLog.i(TAG, "Device id created (${if (source.isNullOrBlank()) "random" else "from ANDROID_ID"})")
                settings.copy(device = settings.device.copy(id = id))
            }
        }
    }

    // ANDROID_ID is used only as hashed input for a per-app, per-signing-key identifier (see class doc).
    @SuppressLint("HardwareIds")
    fun androidId(context: Context): String? = Secure.getString(context.contentResolver, Secure.ANDROID_ID)

    private fun sha256Hex(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

    private const val TAG = "Device"
}
