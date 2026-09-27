package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_HOME_SETTINGS
import android.provider.Settings.ACTION_SETTINGS
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.andy_walker_idfa.smarthome_dashboard.BuildConfig
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.distribution.Distribution
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskWindow
import io.github.andy_walker_idfa.smarthome_dashboard.service.ConnectionService
import io.github.andy_walker_idfa.smarthome_dashboard.ui.AppTheme

/**
 * Settings. Opened from the dashboard (5 taps in the top-right corner, then the PIN if one is set). Closes itself
 * after [AUTO_CLOSE_MS] without a touch, discarding unsaved changes, so the panel never stays on Settings. The
 * timer pauses while a system screen opened from here (permissions, battery) is in front.
 */
class SettingsActivity : ComponentActivity() {
    private val autoClose = Handler(Looper.getMainLooper())
    private val closeRunnable = Runnable {
        AppLog.i(TAG, "No touch for ${AUTO_CLOSE_MS / 60_000} min; closing settings")
        finish()
    }

    private val container by lazy { (application as DashboardApplication).container }
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.factory(container) }

    /** Re-read on every resume: the user changes it in a system screen. */
    private var batteryExempt by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // The service runs either way; the permission only makes its notification visible.
            AppLog.i(TAG, "Notification permission granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Like the dashboard: the screen must not time out while someone is editing settings.
        KioskWindow.keepScreenOn(this)
        val distribution = container.distribution
        setContent {
            AppTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    versionName = BuildConfig.VERSION_NAME,
                    channel = distribution.channel,
                    battery =
                        BatteryOptimizationUi(
                            exempt = batteryExempt,
                            hint = distribution.batteryOptimizationHint?.let {
                                getString(it, getString(R.string.app_name))
                            },
                            onChange = { openScreen(distribution.batteryOptimizationIntent(this)) }
                        ),
                    pinLost = intent.getBooleanExtra(EXTRA_PIN_LOST, false),
                    kioskActions = kioskActions(),
                    onOpenLogs = { startActivity(Intent(this, LogViewerActivity::class.java)) },
                    debugTools =
                        if (BuildConfig.DEBUG) {
                            DebugTools(
                                openAndroidSettings = { openScreen(Intent(ACTION_SETTINGS)) },
                                openHomeAppPicker = { openScreen(Intent(ACTION_HOME_SETTINGS)) },
                                simulateRendererCrash = {
                                    viewModel.simulateRendererCrash()
                                    finish()
                                },
                                simulateRendererHang = {
                                    viewModel.simulateRendererHang()
                                    finish()
                                },
                                simulateAppCrash = { error("Simulated app crash (debug build)") }
                            )
                        } else {
                            null
                        },
                    onMqttEnabling = ::requestNotificationPermissionIfNeeded,
                    // Settings is in the foreground, so starting the foreground service is allowed.
                    onSaved = { settings -> ConnectionService.startIfEnabled(this, settings) },
                    onClose = ::finish
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryExempt = Distribution.isIgnoringBatteryOptimizations(this)
        restartAutoClose()
    }

    override fun onPause() {
        autoClose.removeCallbacks(closeRunnable)
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) restartAutoClose()
        return super.dispatchTouchEvent(ev)
    }

    private fun restartAutoClose() {
        autoClose.removeCallbacks(closeRunnable)
        autoClose.postDelayed(closeRunnable, AUTO_CLOSE_MS)
    }

    /**
     * Escape hatches. Lock Task Mode is always stopped from here (Settings is in the locked task) *before* anything
     * else, because changing the allowlist of a locked task would close it.
     */
    private fun kioskActions(): KioskActions {
        val kiosk = container.kioskController
        return KioskActions(
            setLockEnabled = { enabled ->
                if (!enabled) stopLockTaskIfActive()
                kiosk.setLockEnabled(enabled)
            },
            unlockTemporarily = {
                kiosk.unlockTemporarily()
                stopLockTaskIfActive()
            },
            relock = kiosk::relock,
            openAndroidSettings = {
                kiosk.unlockTemporarily()
                stopLockTaskIfActive()
                openScreen(Intent(ACTION_SETTINGS))
            },
            removeDeviceOwner = {
                stopLockTaskIfActive()
                kiosk.removeDeviceOwner()
            }
        )
    }

    private fun stopLockTaskIfActive() {
        if (!container.kioskPolicy.isInLockTask()) return
        try {
            stopLockTask()
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "stopLockTask failed: ${e.javaClass.simpleName}")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openScreen(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            AppLog.w(TAG, "No activity for ${intent.action}", e)
        }
    }

    companion object {
        /** True when the saved PIN could not be read: show a notice asking for a new one. */
        const val EXTRA_PIN_LOST = "pin_lost"
        private const val TAG = "Settings"
        private const val AUTO_CLOSE_MS = 10 * 60_000L
    }
}

/** Debug-build escape hatches, so the default-launcher kiosk can't lock the developer out. */
class DebugTools(
    val openAndroidSettings: () -> Unit,
    val openHomeAppPicker: () -> Unit,
    val simulateRendererCrash: () -> Unit,
    val simulateRendererHang: () -> Unit,
    val simulateAppCrash: () -> Unit
)
