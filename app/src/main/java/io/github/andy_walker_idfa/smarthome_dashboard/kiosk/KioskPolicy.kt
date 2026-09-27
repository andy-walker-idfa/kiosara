package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import android.app.ActivityManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog

/**
 * The device-policy calls the kiosk lock needs, behind an interface so the logic is unit-testable.
 *
 * Safety (user rules): the app sets **no user restrictions and no policies** besides the Lock Task allowlist and
 * features. Never add DISALLOW_FACTORY_RESET, DISALLOW_SAFE_BOOT, DISALLOW_DEBUGGING_FEATURES, USB restrictions or
 * account restrictions.
 */
interface KioskPolicy {
    val isDeviceOwner: Boolean

    /**
     * Allowlists this app for Lock Task Mode, with only the power menu available. Allowlisting alone locks
     * nothing: the lock starts only when the dashboard calls `startLockTask()`. Returns false if not device owner.
     */
    fun allowLockTask(): Boolean

    /** True if `startLockTask()` would enter Lock Task Mode without Android's manual pinning prompt. */
    fun isLockTaskPermitted(): Boolean

    /** True while any task is locked (or pinned). */
    fun isInLockTask(): Boolean

    /** Gives up device owner (the app stays installed; the tablet becomes a normal tablet). */
    fun clearDeviceOwner()
}

class DevicePolicyKioskPolicy(context: Context) : KioskPolicy {
    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val admin = ComponentName(appContext, KioskAdminReceiver::class.java)

    override val isDeviceOwner: Boolean get() = dpm?.isDeviceOwnerApp(appContext.packageName) == true

    override fun allowLockTask(): Boolean {
        val manager = dpm ?: return false
        if (!isDeviceOwner) return false
        return try {
            manager.setLockTaskPackages(admin, arrayOf(appContext.packageName))
            // Only the power menu (restart possible). Status bar, notifications, Home and Recents stay blocked;
            // the keyguard feature is off, so no lock screen appears while locked.
            manager.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
            true
        } catch (e: SecurityException) {
            AppLog.w(TAG, "Lock task allowlist refused: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun isLockTaskPermitted(): Boolean = dpm?.isLockTaskPermitted(appContext.packageName) == true

    override fun isInLockTask(): Boolean =
        activityManager?.lockTaskModeState?.let { it != ActivityManager.LOCK_TASK_MODE_NONE } == true

    // clearDeviceOwnerApp is deprecated ("for testing"), but it is the only way for the app to hand the device
    // back without a factory reset (user requirement: "Remove device owner permanently").
    @Suppress("DEPRECATION")
    override fun clearDeviceOwner() {
        val manager = dpm ?: return
        if (!isDeviceOwner) return
        try {
            manager.setLockTaskPackages(admin, emptyArray())
        } catch (e: SecurityException) {
            AppLog.w(TAG, "Could not clear the lock task allowlist: ${e.javaClass.simpleName}")
        }
        manager.clearDeviceOwnerApp(appContext.packageName)
        AppLog.w(TAG, "Device owner removed")
    }

    private companion object {
        const val TAG = "Kiosk"
    }
}

/**
 * Device admin component, needed to become device owner (`adb shell dpm set-device-owner …`). It declares no
 * policies. It reports device-owner and Lock Task changes to the [KioskController].
 */
class KioskAdminReceiver : DeviceAdminReceiver() {
    private fun controller(context: Context) =
        (context.applicationContext as? DashboardApplication)?.container?.kioskController

    override fun onEnabled(context: Context, intent: Intent) {
        AppLog.i(TAG, "Device admin enabled")
        controller(context)?.refresh()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        AppLog.i(TAG, "Device admin disabled")
        controller(context)?.refresh()
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        controller(context)?.onLockTaskChanged(true)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        controller(context)?.onLockTaskChanged(false)
    }

    private companion object {
        const val TAG = "KioskAdmin"
    }
}
