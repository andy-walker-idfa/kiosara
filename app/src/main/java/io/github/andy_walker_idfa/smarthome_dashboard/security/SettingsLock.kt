package io.github.andy_walker_idfa.smarthome_dashboard.security

import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SecuritySettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Wrong-PIN limit: after [MAX_ATTEMPTS] wrong PINs, entry is blocked for a fixed [LOCKOUT_MS]. In memory only
 * (user decision: a restart may reset it; this is a home panel, not a public kiosk). Pure; unit-tested.
 */
class PinAttempts(private val clock: Clock) {
    private var failures = 0
    private var lockedUntilElapsed = 0L

    fun remainingLockoutMs(): Long = (lockedUntilElapsed - clock.elapsedRealtimeMillis()).coerceAtLeast(0)

    fun onSuccess() {
        failures = 0
    }

    fun onFailure() {
        failures++
        if (failures >= MAX_ATTEMPTS) {
            failures = 0
            lockedUntilElapsed = clock.elapsedRealtimeMillis() + LOCKOUT_MS
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }
}

/**
 * Protects Settings with an optional PIN. Principle (user): never lock the owner out.
 * - No PIN set: Settings opens directly.
 * - PIN set but unreadable (e.g. the Keystore key was lost): **fail open**. The flag is cleared and Settings
 *   opens with a notice asking for a new PIN.
 * - Forgotten PIN: the adb-only [PinResetReceiver], or "clear app data" in Android settings.
 */
class SettingsLock(
    private val secretStore: SecretStore,
    private val settingsRepository: SettingsRepository,
    clock: Clock,
    /** For the Keystore and the deliberately slow hash; never the main thread. */
    private val worker: CoroutineDispatcher
) {
    sealed interface Access {
        data object Open : Access

        /** The PIN could not be read and was cleared; tell the user to set a new one. */
        data object PinLost : Access

        data object NeedPin : Access
    }

    sealed interface Result {
        data object Ok : Result

        data object Wrong : Result

        data class LockedOut(val remainingMs: Long) : Result
    }

    private val attempts = PinAttempts(clock)

    suspend fun access(): Access {
        val enabled = settingsRepository.settings.first().security.pinEnabled
        val stored = withContext(worker) { secretStore.get(SecuritySettings.PIN_SECRET_KEY) }
        return when {
            enabled && stored != null -> Access.NeedPin

            enabled -> {
                AppLog.w(TAG, "PIN set but unreadable; opening settings (fail open)")
                setEnabled(false)
                Access.PinLost
            }

            else -> {
                // A leftover hash without the flag is not a PIN.
                if (stored != null) withContext(worker) { secretStore.remove(SecuritySettings.PIN_SECRET_KEY) }
                Access.Open
            }
        }
    }

    fun remainingLockoutMs(): Long = attempts.remainingLockoutMs()

    suspend fun verify(pin: String): Result {
        attempts.remainingLockoutMs().takeIf { it > 0 }?.let { return Result.LockedOut(it) }
        val stored = withContext(worker) { secretStore.get(SecuritySettings.PIN_SECRET_KEY) } ?: return Result.Ok
        val ok = withContext(worker) { PinHasher.verify(pin, stored) }
        if (ok) {
            attempts.onSuccess()
            return Result.Ok
        }
        attempts.onFailure()
        AppLog.w(TAG, "Wrong PIN")
        return attempts.remainingLockoutMs().takeIf { it > 0 }?.let { Result.LockedOut(it) } ?: Result.Wrong
    }

    /** Stores a new PIN. Returns false if it could not be stored (the old state is kept). */
    suspend fun setPin(pin: String): Boolean {
        require(PinHasher.isValidPin(pin))
        val iterations = withContext(worker) { PinHasher.calibrate() }
        val stored = withContext(worker) { PinHasher.hash(pin, iterations) }
        if (!withContext(worker) { secretStore.put(SecuritySettings.PIN_SECRET_KEY, stored) }) return false
        setEnabled(true)
        AppLog.i(TAG, "PIN set ($iterations PBKDF2 iterations)")
        return true
    }

    suspend fun clearPin() {
        setEnabled(false)
        withContext(worker) { secretStore.remove(SecuritySettings.PIN_SECRET_KEY) }
        AppLog.i(TAG, "PIN removed")
    }

    private suspend fun setEnabled(enabled: Boolean) {
        settingsRepository.update { it.copy(security = it.security.copy(pinEnabled = enabled)) }
    }

    private companion object {
        const val TAG = "SettingsLock"
    }
}
