package io.github.andy_walker_idfa.smarthome_dashboard.web

import io.github.andy_walker_idfa.smarthome_dashboard.BuildConfig
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Backoff
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.network.NetworkMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryKind
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoverySink
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import io.github.andy_walker_idfa.smarthome_dashboard.settings.UrlValidator
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Decides what the dashboard WebView should do: initial load, retries with backoff, reloading after
 * reconnects, the page watchdog, and returning to the start URL when night starts. It never touches the WebView
 * itself. It publishes [WebRequest]s in [state] and receives page events through [WebViewHost.Listener].
 *
 * All methods must be called on the thread of [scope]'s dispatcher (the main thread in production).
 */
class DashboardController(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val commandBus: WebCommandBus,
    private val clock: Clock,
    private val statusSink: DashboardStatusSink,
    private val recovery: RecoverySink = RecoverySink { _, _ -> },
    private val backoff: Backoff = Backoff(),
    /** The page watchdog (liveness probe, HA check). Tests of unrelated timing switch it off. */
    private val watchdogEnabled: Boolean = true
) : WebViewHost.Listener {
    private val mutableState = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    private var web: WebSettings? = null
    private var started = false
    private var visible = false

    /** URL of the current main-frame navigation, used to retry and to reload after a renderer crash. */
    private var currentUrl: String? = null
    private var currentLoadFailed = false
    private var failedAttempts = 0
    private var retryJob: Job? = null

    private var online = true
    private var connectivityKnown = false
    private var offlineSinceMillis: Long? = null

    /**
     * True if the last page finished loading while offline. HA's service worker serves the app shell
     * from cache, so such a load "succeeds" but shows HA's own connection error and never recovers by itself.
     */
    private var pageLoadedWhileOffline = false

    private val rendererDeaths = ArrayDeque<Long>()

    /** Paused under the black night screen since this elapsed time, or null while running. */
    private var pausedSinceElapsed: Long? = null
    private var connectionCheckJob: Job? = null

    // Watchdog (Phase 4a)
    private var probeJob: Job? = null
    private var probeTimeoutJob: Job? = null
    private var nextProbeId = 1L
    private var healthCheckJob: Job? = null
    private var disconnectedChecks = 0

    /** A connection check is pending because the dashboard woke from a pause (reload on the first "disconnected"). */
    private var wakeCheckPending = false
    private var unresponsiveSinceElapsed: Long? = null

    /** The renderer is being terminated by the watchdog; its death is already recorded. */
    private var terminating = false

    /** The last night state seen by [onNightChanged], or null before the first. */
    private var lastNight: Boolean? = null

    /** The configured start URL, or null while the app is not set up. */
    private val startUrl: String? get() = web?.startUrl?.takeIf { it.isNotBlank() }
    private var nextRequestId = 1L

    fun start() {
        check(!started) { "start() must be called once" }
        started = true
        scope.launch {
            settingsRepository.settings
                .map { it.web }
                .distinctUntilChanged()
                .collect(::onWebSettings)
        }
        scope.launch { networkMonitor.isConnected.collect(::onConnectivity) }
        scope.launch { commandBus.commands.collect(::onCommand) }
    }

    // region Inputs from the activity

    /** The activity became visible (started) or hidden (stopped). The watchdog only runs while visible. */
    fun onVisibilityChanged(isVisible: Boolean) {
        visible = isVisible
        statusSink.update { it.copy(active = isVisible) }
        restartWatchdog()
    }

    /** Called on each touch-down. */
    fun onUserInteraction() {
        statusSink.update { it.copy(lastInteractionMillis = clock.nowMillis()) }
    }

    /**
     * The night state (from the display controller). When night starts, the dashboard goes back to the start page,
     * so the panel shows the main view in the morning whatever was open the evening before. The first value only
     * records the state (no load at app start in the middle of the night).
     */
    fun onNightChanged(night: Boolean) {
        val previous = lastNight
        lastNight = night
        if (previous != false || !night) return
        val url = startUrl ?: return
        if (mutableState.value.page == PageState.NotConfigured) return
        AppLog.i(TAG, "Night started; returning to the start page")
        retryJob?.cancel()
        enqueue(WebAction.LoadUrl(url))
    }

    /**
     * The dashboard was paused under the black night screen, or woke up. After a long pause, the HA
     * frontend normally reconnects its websocket by itself; if it hasn't after [CONNECTION_CHECK_DELAY_MS],
     * the page is reloaded.
     */
    fun onDashboardPaused(paused: Boolean) {
        val now = clock.elapsedRealtimeMillis()
        if (paused) {
            if (pausedSinceElapsed == null) pausedSinceElapsed = now
            connectionCheckJob?.cancel()
            return
        }
        val since = pausedSinceElapsed ?: return
        pausedSinceElapsed = null
        if (now - since >= LONG_PAUSE_MS && mutableState.value.page == PageState.Loaded) {
            connectionCheckJob?.cancel()
            connectionCheckJob =
                scope.launch {
                    delay(CONNECTION_CHECK_DELAY_MS)
                    wakeCheckPending = true
                    enqueue(WebAction.CheckHaConnection)
                }
        }
    }

    fun onRequestHandled(id: Long) {
        mutableState.update { s -> s.copy(requests = s.requests.filterNot { it.id == id }) }
    }

    fun retryNow() {
        retryJob?.cancel()
        val url = currentUrl ?: startUrl ?: return
        enqueue(WebAction.LoadUrl(url))
    }

    /** Pins the certificate from an [LoadProblem.UntrustedCertificate] error and retries. */
    fun trustCertificate(problem: LoadProblem.UntrustedCertificate) {
        if (!problem.trustable) return
        AppLog.i(TAG, "Pinning certificate for ${problem.host}: ${problem.sha256}")
        scope.launch {
            settingsRepository.update { s -> s.copy(web = s.web.copy(trustedCertSha256 = problem.sha256)) }
        }
    }

    // endregion

    // region WebViewHost.Listener

    override fun onPageStarted(url: String) {
        currentUrl = url
        statusSink.update { it.copy(currentUrl = url) }
        currentLoadFailed = false
        // Keep the error screen up while a retry is loading, to avoid flicker.
        if (mutableState.value.page !is PageState.Error) setPage(PageState.Loading)
    }

    override fun onPageFinished(url: String) {
        if (currentLoadFailed) return
        failedAttempts = 0
        retryJob?.cancel()
        pageLoadedWhileOffline = !online
        statusSink.update { it.copy(currentUrl = url) }
        setPage(PageState.Loaded)
    }

    override fun onMainFrameError(url: String, problem: LoadProblem) {
        // The first error of a navigation wins; WebView may report a generic follow-up error.
        if (currentLoadFailed) return
        currentLoadFailed = true
        currentUrl = url
        AppLog.w(TAG, "Main frame failed: $problem")
        scheduleRetry(withTrustability(problem), url)
    }

    override fun onRendererGone(crashed: Boolean) {
        cancelProbeTimeout()
        unresponsiveSinceElapsed = null
        when {
            terminating -> terminating = false
            crashed -> recovery.record(RecoveryKind.PAGE_CRASH, null)
            else -> recovery.record(RecoveryKind.PAGE_KILLED, null)
        }
        val now = clock.elapsedRealtimeMillis()
        rendererDeaths.addLast(now)
        while (rendererDeaths.isNotEmpty() && now - rendererDeaths.first() > CRASH_LOOP_WINDOW_MS) {
            rendererDeaths.removeFirst()
        }
        // Only web pages are reloaded; anything else (e.g. a debug chrome:// URL) falls back to the start page.
        val url = currentUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: startUrl ?: return
        if (rendererDeaths.size > CRASH_LOOP_MAX_DEATHS) {
            AppLog.e(TAG, "Renderer crash loop (${rendererDeaths.size} in 5 min); backing off")
            currentLoadFailed = true
            scheduleRetry(LoadProblem.RendererCrashLoop, url)
        } else {
            enqueue(WebAction.LoadUrl(url))
        }
    }

    override fun onViewportChanged(widthPx: Int, heightPx: Int, densityDpi: Int) {
        statusSink.update { it.copy(viewport = Viewport(widthPx, heightPx, densityDpi)) }
    }

    override fun onHaConnectionChecked(state: HaConnection) {
        val afterWake = wakeCheckPending
        wakeCheckPending = false
        if (afterWake) AppLog.i(TAG, "Home Assistant connection after wake: $state")
        when (state) {
            HaConnection.CONNECTED -> disconnectedChecks = 0

            HaConnection.UNKNOWN -> Unit

            HaConnection.DISCONNECTED -> {
                disconnectedChecks++
                // After a wake one answer is enough; the periodic check needs two in a row.
                if (pausedSinceElapsed == null && (afterWake || disconnectedChecks >= DISCONNECTED_CHECKS)) {
                    disconnectedChecks = 0
                    recovery.record(RecoveryKind.FRONTEND_DISCONNECTED, if (afterWake) "after wake" else null)
                    enqueue(WebAction.Reload)
                }
            }
        }
    }

    override fun onProbeAnswered(id: Long) {
        if (id == nextProbeId - 1) cancelProbeTimeout()
    }

    override fun onRendererUnresponsive() {
        val now = clock.elapsedRealtimeMillis()
        val since = unresponsiveSinceElapsed ?: now.also { unresponsiveSinceElapsed = it }
        if (now - since >= UNRESPONSIVE_LIMIT_MS) terminateRenderer("unresponsive to input")
    }

    override fun onRendererResponsive() {
        unresponsiveSinceElapsed = null
    }

    override fun shouldTrustCertificate(host: String, sha256: String): Boolean {
        val w = web ?: return false
        val trustedHost = trustedHost() ?: return false
        return w.trustedCertSha256.isNotBlank() &&
            host.equals(trustedHost, ignoreCase = true) &&
            sha256.equals(w.trustedCertSha256, ignoreCase = true)
    }

    // endregion

    private fun onWebSettings(newWeb: WebSettings) {
        val old = web
        web = newWeb
        mutableState.update { it.copy(web = newWeb) }

        if (old == null || old.startUrl != newWeb.startUrl) {
            val url = startUrl
            if (url == null) {
                AppLog.i(TAG, "No start URL configured")
                retryJob?.cancel()
                currentUrl = null
                setPage(PageState.NotConfigured)
            } else {
                currentUrl = url
                if (mutableState.value.page == PageState.NotConfigured) setPage(PageState.Loading)
                enqueue(WebAction.LoadUrl(url))
            }
        } else if (old.trustedCertSha256 != newWeb.trustedCertSha256 && mutableState.value.page is PageState.Error) {
            retryNow()
        }
    }

    private fun onConnectivity(connected: Boolean) {
        val wasOnline = online
        online = connected
        if (!connectivityKnown) {
            connectivityKnown = true
            if (!connected) offlineSinceMillis = clock.elapsedRealtimeMillis()
            return
        }
        if (!connected) {
            if (wasOnline) offlineSinceMillis = clock.elapsedRealtimeMillis()
            AppLog.i(TAG, "Network lost")
            val page = mutableState.value.page
            if (page is PageState.Error) {
                retryJob?.cancel()
                setPage(page.copy(retryAtElapsedMillis = null, waitingForNetwork = true))
            }
            return
        }
        if (wasOnline) return

        val outage = offlineSinceMillis?.let { clock.elapsedRealtimeMillis() - it } ?: 0
        offlineSinceMillis = null
        AppLog.i(TAG, "Network back after ${outage / 1000} s")
        when {
            mutableState.value.page is PageState.Error -> retryNow()

            // HA's frontend reconnects its websocket on its own after short drops, so only reload
            // after a long outage or when the page was (re)loaded without a network.
            pageLoadedWhileOffline || outage >= RELOAD_AFTER_OUTAGE_MS -> {
                pageLoadedWhileOffline = false
                enqueue(WebAction.Reload)
            }
        }
    }

    private fun onCommand(command: WebCommand) {
        AppLog.i(TAG, "Command: ${command.logName}")
        when (command) {
            WebCommand.ReloadPage -> enqueue(WebAction.Reload)
            WebCommand.LoadStartUrl -> startUrl?.let { enqueue(WebAction.LoadUrl(it)) }
            is WebCommand.LoadUrl -> enqueue(WebAction.LoadUrl(command.url))
            WebCommand.ClearCache -> enqueue(WebAction.ClearCacheAndReload)
            WebCommand.SimulateRendererCrash -> if (BuildConfig.DEBUG) enqueue(WebAction.CrashRenderer)
            WebCommand.SimulateRendererHang -> if (BuildConfig.DEBUG) enqueue(WebAction.HangRenderer)
        }
    }

    private fun scheduleRetry(problem: LoadProblem, url: String) {
        retryJob?.cancel()
        if (!online) {
            setPage(PageState.Error(problem, url, retryAtElapsedMillis = null, waitingForNetwork = true))
            return
        }
        val delayMillis = backoff.delayFor(failedAttempts++)
        setPage(PageState.Error(problem, url, clock.elapsedRealtimeMillis() + delayMillis, waitingForNetwork = false))
        retryJob =
            scope.launch {
                delay(delayMillis)
                enqueue(WebAction.LoadUrl(url))
            }
    }

    private fun withTrustability(problem: LoadProblem): LoadProblem {
        if (problem !is LoadProblem.UntrustedCertificate) return problem
        val trustedHost = trustedHost()
        val trustable =
            trustedHost != null &&
                problem.sha256.isNotEmpty() &&
                problem.host.equals(trustedHost, ignoreCase = true)
        return problem.copy(trustable = trustable)
    }

    /** A self-signed certificate can be pinned only for the start URL's host (since 0.8.0; no separate setting). */
    private fun trustedHost(): String? = startUrl?.let(UrlValidator::hostOf)?.takeIf { it.isNotBlank() }

    /**
     * Watchdog loops, running only while the dashboard is visible and not paused. The probe acts while a page is
     * loaded or loading; the HA check only on a loaded page with the network up.
     * - Liveness probe: a trivial script every [PROBE_INTERVAL_MS]; no answer within [PROBE_TIMEOUT_MS] = hung
     *   renderer. Needed because Android reports an unresponsive renderer only when input goes unanswered, and a
     *   wall panel is rarely touched.
     * - HA frontend check every [HEALTH_CHECK_INTERVAL_MS]; [DISCONNECTED_CHECKS] "disconnected" in a row = reload.
     */
    private fun restartWatchdog() {
        probeJob?.cancel()
        healthCheckJob?.cancel()
        cancelProbeTimeout()
        disconnectedChecks = 0
        if (!visible || !watchdogEnabled) return
        probeJob =
            scope.launch {
                while (true) {
                    delay(PROBE_INTERVAL_MS)
                    if (!probeActive() || probeTimeoutJob?.isActive == true) continue
                    val id = nextProbeId++
                    enqueue(WebAction.ProbeRenderer(id))
                    probeTimeoutJob =
                        scope.launch {
                            delay(PROBE_TIMEOUT_MS)
                            if (probeActive()) terminateRenderer("no answer to probe")
                        }
                }
            }
        healthCheckJob =
            scope.launch {
                while (true) {
                    delay(HEALTH_CHECK_INTERVAL_MS)
                    if (watchdogActive() && online) enqueue(WebAction.CheckHaConnection)
                }
            }
    }

    private fun watchdogActive() = visible && pausedSinceElapsed == null && mutableState.value.page == PageState.Loaded

    /** The probe also runs while a page is loading: a renderer can hang mid-load and never finish. */
    private fun probeActive() = visible && pausedSinceElapsed == null &&
        mutableState.value.page.let { it == PageState.Loaded || it == PageState.Loading }

    private fun cancelProbeTimeout() {
        probeTimeoutJob?.cancel()
        probeTimeoutJob = null
    }

    private fun terminateRenderer(reason: String) {
        if (terminating) return
        AppLog.w(TAG, "Page renderer hung ($reason); terminating")
        terminating = true
        unresponsiveSinceElapsed = null
        cancelProbeTimeout()
        recovery.record(RecoveryKind.PAGE_UNRESPONSIVE, reason)
        enqueue(WebAction.TerminateRenderer)
    }

    private fun enqueue(action: WebAction) {
        // A new navigation starts here. Reset the error flag even if onPageStarted never fires
        // (it may not for TLS failures), so the next error still schedules a retry.
        if (action.isNavigation) currentLoadFailed = false
        val request = WebRequest(nextRequestId++, action)
        mutableState.update { it.copy(requests = it.requests + request) }
    }

    private fun setPage(page: PageState) {
        mutableState.update { it.copy(page = page) }
        publishPageStatus(page)
    }

    /** Mirrors the page state into the app-scoped [DashboardStatusSink] for the MQTT publisher. */
    private fun publishPageStatus(page: PageState) {
        when (page) {
            is PageState.Error -> statusSink.update { status ->
                val since = status.pageError?.takeIf { it.url == page.url }?.sinceMillis ?: clock.nowMillis()
                status.copy(pageError = PageErrorInfo(page.problem.kind(), page.problem.describe(), page.url, since))
            }

            PageState.Loaded -> statusSink.update { it.copy(pageError = null) }

            PageState.NotConfigured -> statusSink.update { it.copy(currentUrl = null, pageError = null) }

            PageState.Loading -> Unit
        }
    }

    private companion object {
        const val TAG = "Dashboard"
        const val RELOAD_AFTER_OUTAGE_MS = 30_000L
        const val CRASH_LOOP_WINDOW_MS = 5 * 60_000L
        const val CRASH_LOOP_MAX_DEATHS = 3

        /** Watchdog: liveness probe interval and answer timeout, input-unresponsive limit, HA check interval. */
        const val PROBE_INTERVAL_MS = 60_000L
        const val PROBE_TIMEOUT_MS = 10_000L
        const val UNRESPONSIVE_LIMIT_MS = 15_000L
        const val HEALTH_CHECK_INTERVAL_MS = 5 * 60_000L
        const val DISCONNECTED_CHECKS = 2

        /** A pause at least this long gets the connection check on wake. */
        const val LONG_PAUSE_MS = 3 * 60_000L
        const val CONNECTION_CHECK_DELAY_MS = 10_000L
    }
}
