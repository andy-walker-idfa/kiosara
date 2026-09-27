package io.github.andy_walker_idfa.smarthome_dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatus
import io.github.andy_walker_idfa.smarthome_dashboard.display.NightOverlay
import io.github.andy_walker_idfa.smarthome_dashboard.display.ScreenMode
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.CornerTapDetector
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.CornerTapHint
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskWindow
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.PinPrompt
import io.github.andy_walker_idfa.smarthome_dashboard.security.SettingsLock
import io.github.andy_walker_idfa.smarthome_dashboard.service.ConnectionService
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.SettingsActivity
import io.github.andy_walker_idfa.smarthome_dashboard.ui.AppTheme
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardState
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardViewModel
import io.github.andy_walker_idfa.smarthome_dashboard.web.ErrorOverlay
import io.github.andy_walker_idfa.smarthome_dashboard.web.PageState
import io.github.andy_walker_idfa.smarthome_dashboard.web.SetupOverlay
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebViewHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The kiosk screen and the app's HOME activity: a full-screen dashboard WebView with a Compose overlay
 * for errors. Declared with `configChanges` so the WebView is never recreated on configuration changes.
 */
class MainActivity : ComponentActivity() {
    private val container by lazy { (application as DashboardApplication).container }
    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.factory(container) }
    private val controller get() = viewModel.controller
    private val display get() = container.displayController

    private lateinit var webViewHost: WebViewHost
    private lateinit var overlayView: ComposeView
    private lateinit var nightView: ComposeView
    private lateinit var pinView: ComposeView
    private val cornerTaps = CornerTapDetector()
    private lateinit var tapHintView: ComposeView
    private val tapProgress = mutableIntStateOf(0)
    private val tapHintHandler = Handler(Looper.getMainLooper())
    private val tapHintUpdate = Runnable { updateTapHint() }
    private val pinPromptState = mutableStateOf(false)

    /** What the PIN prompt unlocks: opening Settings or trusting a certificate. */
    private var afterPin: () -> Unit = {}
    private var pinPromptShown: Boolean
        get() = pinPromptState.value
        set(value) {
            pinPromptState.value = value
            pinView.visibility = if (value) View.VISIBLE else View.GONE
        }

    /** The touch that woke the screen is consumed until its last finger lifts, so nothing reaches HA. */
    private var swallowingTouch = false
    private var swallowedKeyCode: Int? = null

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppLog.i(TAG, "Local network permission granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KioskWindow.keepScreenOn(this)

        val webContainer = FrameLayout(this)
        overlayView = ComposeView(this).apply { visibility = View.GONE }
        nightView = ComposeView(this).apply { visibility = View.GONE }
        pinView = ComposeView(this).apply { visibility = View.GONE }
        tapHintView = ComposeView(this).apply { visibility = View.GONE }
        val root =
            FrameLayout(this).apply {
                addView(webContainer, MATCH_PARENT, MATCH_PARENT)
                addView(overlayView, MATCH_PARENT, MATCH_PARENT)
                addView(nightView, MATCH_PARENT, MATCH_PARENT)
                addView(tapHintView, MATCH_PARENT, MATCH_PARENT)
                addView(pinView, MATCH_PARENT, MATCH_PARENT)
            }
        setContentView(root)

        webViewHost = WebViewHost(this, webContainer, controller)
        overlayView.setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            AppTheme {
                when (val page = state.page) {
                    is PageState.Error ->
                        ErrorOverlay(
                            error = page,
                            clock = container.clock,
                            onRetryNow = controller::retryNow,
                            // Pinning a certificate is a security decision: it needs the PIN like Settings.
                            onTrustCertificate = { problem -> withPin { controller.trustCertificate(problem) } }
                        )

                    PageState.NotConfigured -> SetupOverlay(onOpenSettings = { requestSettings() })

                    PageState.Loading, PageState.Loaded -> Unit
                }
            }
        }

        nightView.setContent {
            val status by display.status.collectAsStateWithLifecycle()
            if (status.mode.coversDashboard) NightOverlay()
        }

        tapHintView.setContent { CornerTapHint(tapProgress.intValue, CornerTapDetector.TAPS) }
        pinView.setContent {
            if (pinPromptState.value) {
                AppTheme {
                    PinPrompt(
                        onCheck = container.settingsLock::verify,
                        onSuccess = {
                            pinPromptShown = false
                            afterPin()
                            afterPin = {}
                        },
                        onDismiss = {
                            pinPromptShown = false
                            afterPin = {}
                        }
                    )
                }
            }
        }

        // Kiosk: Back navigates inside the dashboard and never leaves the app.
        onBackPressedDispatcher.addCallback(this) {
            if (pinPromptShown) pinPromptShown = false else webViewHost.goBack()
        }

        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    controller.onVisibilityChanged(true)
                    display.onDashboardVisible(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    controller.onVisibilityChanged(false)
                    display.onDashboardVisible(false)
                }
            }
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { controller.state.collect(::render) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { display.status.collect(::renderDisplay) }
        }
        lifecycleScope.launch {
            // Kiosk lock: enter or leave Lock Task Mode while the dashboard is in front.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.kioskController.refresh()
                container.kioskController.status.collect { applyKioskLock(it.shouldLock) }
            }
        }
        lifecycleScope.launch {
            // The app is in the foreground here, so starting the foreground service is allowed.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConnectionService.startIfEnabled(this@MainActivity, container.settingsRepository.settings.first())
            }
        }
        requestLocalNetworkPermissionIfNeeded()
    }

    private fun render(state: DashboardState) {
        state.web?.let {
            KioskWindow.applyOrientation(this)
            webViewHost.apply(it)
        }
        for (request in state.requests) {
            webViewHost.execute(request.action)
            controller.onRequestHandled(request.id)
        }
        val overlayShown = state.page is PageState.Error || state.page == PageState.NotConfigured
        overlayView.visibility = if (overlayShown) View.VISIBLE else View.GONE
    }

    private fun renderDisplay(status: DisplayStatus) {
        val brightness =
            status.windowBrightness.takeIf { it >= 0 } ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window.attributes.screenBrightness != brightness) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        nightView.visibility = if (status.mode.coversDashboard) View.VISIBLE else View.GONE
        controller.onNightChanged(status.night)
        webViewHost.setCovered(status.pauseDashboard)
        controller.onDashboardPaused(status.pauseDashboard)
    }

    /**
     * Starts Lock Task Mode only when Android allows it without its manual pinning prompt (device owner +
     * allowlisted); stops it when the lock is off or temporarily unlocked.
     */
    private fun applyKioskLock(shouldLock: Boolean) {
        val policy = container.kioskPolicy
        val locked = policy.isInLockTask()
        try {
            when {
                shouldLock && !locked && policy.isLockTaskPermitted() -> startLockTask()
                !shouldLock && locked -> stopLockTask()
            }
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "Lock task change failed: ${e.javaClass.simpleName}")
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val asleep = display.status.value.mode != ScreenMode.ACTIVE
            controller.onUserInteraction()
            display.onUserActivity()
            if (asleep) {
                swallowingTouch = true
                cornerTaps.reset()
            } else {
                val cornerSize = CornerTapDetector.CORNER_SIZE_DP * resources.displayMetrics.density
                val inCorner = CornerTapDetector.isInTopRight(event.x, event.y, window.decorView.width, cornerSize)
                if (cornerTaps.onTap(inCorner, event.eventTime)) requestSettings()
                if (inCorner) updateTapHint()
            }
        }
        if (swallowingTouch) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                swallowingTouch = false
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    // Overrides the public Activity.dispatchKeyEvent. Lint reports RestrictedApi because androidx.core's
    // ComponentActivity marks its own override restricted; that restriction doesn't apply to subclasses.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val asleep = display.status.value.mode != ScreenMode.ACTIVE
            display.onUserActivity()
            if (asleep) swallowedKeyCode = event.keyCode
        }
        if (swallowedKeyCode == event.keyCode) {
            if (event.action == KeyEvent.ACTION_UP) swallowedKeyCode = null
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) KioskWindow.enterImmersive(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME pressed while already in front: nothing to do, and deliberately no reload.
        AppLog.d(TAG, "onNewIntent ${intent.action}")
    }

    override fun onResume() {
        super.onResume()
        KioskWindow.wakeScreenIfOff(this)
        webViewHost.onResume()
    }

    override fun onPause() {
        webViewHost.onPause()
        cornerTaps.reset()
        updateTapHint()
        super.onPause()
    }

    override fun onDestroy() {
        webViewHost.destroy()
        super.onDestroy()
    }

    /** Shows the dots from the 2nd corner tap on; re-checks when the tap window runs out. */
    private fun updateTapHint() {
        val count = cornerTaps.progress(SystemClock.uptimeMillis())
        tapProgress.intValue = count
        tapHintView.visibility = if (count >= TAP_HINT_FROM) View.VISIBLE else View.GONE
        tapHintHandler.removeCallbacks(tapHintUpdate)
        if (count > 0) tapHintHandler.postDelayed(tapHintUpdate, TAP_HINT_RECHECK_MS)
    }

    /** The way into Settings: straight in without a PIN, via the PIN prompt with one. Never locks the owner out. */
    private fun requestSettings() = withPin { pinLost -> openSettings(pinLost) }

    /**
     * Runs [action] straight away without a PIN, or after the PIN prompt with one. An unreadable PIN fails open
     * (`pinLost` = true), like everywhere else.
     */
    private fun withPin(action: (pinLost: Boolean) -> Unit) {
        if (pinPromptShown) return
        lifecycleScope.launch {
            when (container.settingsLock.access()) {
                SettingsLock.Access.Open -> action(false)

                SettingsLock.Access.PinLost -> action(true)

                SettingsLock.Access.NeedPin -> {
                    afterPin = { action(false) }
                    pinPromptShown = true
                }
            }
        }
    }

    private fun openSettings(pinLost: Boolean) {
        AppLog.i(TAG, "Opening settings")
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PIN_LOST, pinLost))
    }

    /**
     * Android 17+ requires a runtime permission for LAN access when targeting API 37. WebView inherits
     * the app's permission, so without it HA on the LAN would be unreachable. Not needed on older devices.
     */
    private fun requestLocalNetworkPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < API_ANDROID_17) return
        if (checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    private companion object {
        const val TAG = "MainActivity"
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val TAP_HINT_FROM = 2
        const val TAP_HINT_RECHECK_MS = 500L
        const val API_ANDROID_17 = 37
    }
}
