package io.github.andy_walker_idfa.smarthome_dashboard.sensors

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.StatFs
import android.os.SystemClock

/** Point-in-time system facts, sampled on demand (cheap calls, no listeners). */
interface SystemInfo {
    fun freeMemoryBytes(): Long

    fun freeStorageBytes(): Long

    /** Memory used by the app's own process (PSS). The web page's renderer is a separate process, not included. */
    fun appMemoryBytes(): Long

    /** Wall-clock time of the last boot, in epoch milliseconds. */
    fun bootTimeMillis(): Long

    fun isDeviceOwner(): Boolean

    val androidVersion: String
    val sdkInt: Int
    val securityPatch: String
    val manufacturer: String
    val model: String
}

class AndroidSystemInfo(context: Context) : SystemInfo {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)

    override fun freeMemoryBytes(): Long = ActivityManager.MemoryInfo().also {
        activityManager.getMemoryInfo(it)
    }.availMem

    override fun freeStorageBytes(): Long = StatFs(appContext.filesDir.absolutePath).availableBytes

    override fun appMemoryBytes(): Long = Debug.getPss() * BYTES_PER_KB

    override fun bootTimeMillis(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    override fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(appContext.packageName)

    override val androidVersion: String = Build.VERSION.RELEASE
    override val sdkInt: Int = Build.VERSION.SDK_INT
    override val securityPatch: String = Build.VERSION.SECURITY_PATCH
    override val manufacturer: String = Build.MANUFACTURER
    override val model: String = Build.MODEL
}

private const val BYTES_PER_KB = 1024L
