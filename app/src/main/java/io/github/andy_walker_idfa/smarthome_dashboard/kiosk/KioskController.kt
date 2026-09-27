package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SecuritySettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The kiosk lock as the app sees it (Settings, HA sensor, dashboard). */
data class KioskStatus(
    val deviceOwner: Boolean = false,
    /** The user's setting (persisted). */
    val lockEnabled: Boolean = false,
    val pinSet: Boolean = false,
    /** Wall-clock end of "Unlock for 15 minutes", or null. */
    val unlockedUntilMillis: Long? = null,
    /** Lock Task Mode is active right now. */
    val lockActive: Boolean = false
) {
    /** The dashboard should be in Lock Task Mode. */
    val shouldLock: Boolean get() = deviceOwner && lockEnabled && pinSet && unlockedUntilMillis == null

    /** The switch may be turned on (rule: device owner **and** a PIN). */
    val canEnable: Boolean get() = deviceOwner && pinSet
}

/** Read side for the MQTT publisher. */
interface KioskStatusSource {
    val status: StateFlow<KioskStatus>
}

/**
 * The optional kiosk lock (user rules in CLAUDE.md, "Scope decisions", Phase 4b). Three states, all fully usable:
 * (a) not device owner, (b) device owner with the lock off (a normal tablet), (c) device owner with the lock on.
 *
 * - Being device owner locks nothing: the app is only allowlisted for Lock Task Mode. The lock starts when the
 *   dashboard calls `startLockTask()` because [KioskStatus.shouldLock] is true, and ends with `stopLockTask()`.
 *   The allowlist is never removed while locked (that would close the dashboard's task).
 * - The lock needs a PIN: when the PIN goes away (removed, unreadable, adb reset), the lock is switched off.
 * - "Unlock for 15 minutes" is kept in memory: a process restart locks again (safe default). When the time is up,
 *   the dashboard is brought to the front ([bringDashboardToFront]; allowed for a device owner) and locks itself.
 * - Home Assistant can't switch the lock off; there is no command for it.
 */
class KioskController(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val policy: KioskPolicy,
    private val clock: Clock,
    /** Backup timer that survives the process (see [RelockAlarm]); no-op in tests. */
    private val relockAlarm: RelockScheduler = RelockScheduler.NONE,
    private val bringDashboardToFront: () -> Unit
) : KioskStatusSource {
    private val mutable = MutableStateFlow(KioskStatus())
    override val status: StateFlow<KioskStatus> = mutable.asStateFlow()

    private var security = SecuritySettings()
    private var unlockJob: Job? = null
    private var started = false

    fun start() {
        check(!started) { "start() must be called once" }
        started = true
        scope.launch {
            settingsRepository.settings.map { it.security }.distinctUntilChanged().collect(::onSecurity)
        }
    }

    /** Re-reads device-owner status (after provisioning, or on resume). */
    fun refresh() {
        val owner = policy.isDeviceOwner
        if (owner) policy.allowLockTask()
        change { it.copy(deviceOwner = owner, lockActive = policy.isInLockTask()) }
        enforcePinRule()
    }

    fun onLockTaskChanged(active: Boolean) {
        AppLog.i(TAG, "Lock task ${if (active) "entered" else "exited"}")
        change { it.copy(lockActive = active) }
    }

    /** Returns false if the lock can't be switched on (not device owner or no PIN). */
    fun setLockEnabled(enabled: Boolean): Boolean {
        if (enabled && !mutable.value.canEnable) return false
        if (!enabled) cancelUnlock()
        AppLog.i(TAG, "Kiosk lock ${if (enabled) "on" else "off"}")
        persist(enabled)
        return true
    }

    /** Escape hatch: the dashboard is released for [UNLOCK_MS], then locks again by itself. */
    fun unlockTemporarily() {
        if (!mutable.value.lockEnabled) return
        unlockJob?.cancel()
        change { it.copy(unlockedUntilMillis = clock.nowMillis() + UNLOCK_MS) }
        relockAlarm.schedule(UNLOCK_MS)
        AppLog.i(TAG, "Unlocked for ${UNLOCK_MS / 60_000} min")
        unlockJob =
            scope.launch {
                delay(UNLOCK_MS)
                relock()
            }
    }

    /** Ends a temporary unlock early. */
    fun relock() {
        unlockJob?.cancel()
        unlockJob = null
        relockAlarm.cancel()
        if (mutable.value.unlockedUntilMillis == null) return
        change { it.copy(unlockedUntilMillis = null) }
        AppLog.i(TAG, "Locking again")
        if (mutable.value.shouldLock) bringDashboardToFront()
    }

    /**
     * The backup alarm fired. The process may be new (the unlock window was only in memory), so this decides from
     * the stored settings: if the lock is on, the dashboard is brought to the front and locks itself.
     */
    suspend fun onUnlockExpired() {
        val stored = settingsRepository.settings.first().security
        unlockJob?.cancel()
        unlockJob = null
        change { it.copy(unlockedUntilMillis = null) }
        if (stored.kioskLock && stored.pinEnabled && policy.isDeviceOwner) bringDashboardToFront()
    }

    /**
     * Escape hatch: gives up device owner for good (the caller has already stopped Lock Task Mode). Returns false
     * if Android refused; the lock is off either way.
     */
    fun removeDeviceOwner(): Boolean {
        cancelUnlock()
        persist(false)
        val removed =
            try {
                policy.clearDeviceOwner()
                true
            } catch (e: RuntimeException) {
                AppLog.e(TAG, "Removing device owner failed: ${e.javaClass.simpleName}")
                false
            }
        refresh()
        return removed
    }

    private fun onSecurity(new: SecuritySettings) {
        security = new
        change { it.copy(lockEnabled = new.kioskLock, pinSet = new.pinEnabled) }
        if (!new.kioskLock) cancelUnlock()
        refresh()
    }

    /** The lock needs a PIN and device owner; otherwise it is switched off (never a lock without a way in). */
    private fun enforcePinRule() {
        val s = mutable.value
        if (s.lockEnabled && (!s.pinSet || !s.deviceOwner)) {
            AppLog.w(TAG, "Kiosk lock switched off (pin=${s.pinSet}, owner=${s.deviceOwner})")
            persist(false)
        }
    }

    private fun cancelUnlock() {
        unlockJob?.cancel()
        unlockJob = null
        relockAlarm.cancel()
        if (mutable.value.unlockedUntilMillis != null) change { it.copy(unlockedUntilMillis = null) }
    }

    private fun persist(enabled: Boolean) {
        change { it.copy(lockEnabled = enabled) }
        if (security.kioskLock == enabled) return
        scope.launch {
            settingsRepository.update { it.copy(security = it.security.copy(kioskLock = enabled)) }
        }
    }

    /** Atomic: inputs arrive from the main thread, the settings flow and the admin receiver. */
    private fun change(transform: (KioskStatus) -> KioskStatus) = mutable.update(transform)

    companion object {
        private const val TAG = "Kiosk"
        const val UNLOCK_MS = 15 * 60_000L
    }
}

/** Schedules the backup re-lock ([RelockAlarm] in production). */
interface RelockScheduler {
    fun schedule(delayMillis: Long)

    fun cancel()

    companion object {
        val NONE =
            object : RelockScheduler {
                override fun schedule(delayMillis: Long) = Unit

                override fun cancel() = Unit
            }
    }
}
