package io.github.andy_walker_idfa.smarthome_dashboard.web

import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings

/**
 * Everything the dashboard screen renders. Pending WebView work is kept as state ([requests]), not as
 * one-shot events, so nothing is lost while the activity is stopped (e.g. while Settings is open).
 */
data class DashboardState(
    /** Null until settings have been loaded for the first time. */
    val web: WebSettings? = null,
    val page: PageState = PageState.Loading,
    val requests: List<WebRequest> = emptyList()
)

sealed interface PageState {
    data object Loading : PageState

    /** No start URL configured yet; the "Not set up" screen asks the user to open Settings. */
    data object NotConfigured : PageState

    data object Loaded : PageState

    data class Error(
        val problem: LoadProblem,
        val url: String,
        /** When the next automatic retry happens (elapsed-realtime ms), or null if none is scheduled. */
        val retryAtElapsedMillis: Long?,
        /** True while retries are paused because the device is offline. */
        val waitingForNetwork: Boolean
    ) : PageState
}

sealed interface LoadProblem {
    /** Connection-level failure reported by WebView (e.g. `net::ERR_CONNECTION_REFUSED`). */
    data class Network(val description: String) : LoadProblem

    data class Http(val statusCode: Int) : LoadProblem

    data class UntrustedCertificate(
        val host: String,
        val sha256: String,
        /** True when [host] is the configured self-signed host, so the user may pin this certificate. */
        val trustable: Boolean = false
    ) : LoadProblem

    /** The renderer crashed repeatedly in a short time; automatic reloads are backed off. */
    data object RendererCrashLoop : LoadProblem
}

/** Short machine-readable kind, used in MQTT attributes. */
fun LoadProblem.kind(): String = when (this) {
    is LoadProblem.Network -> "network"
    is LoadProblem.Http -> "http"
    is LoadProblem.UntrustedCertificate -> "certificate"
    LoadProblem.RendererCrashLoop -> "renderer_crash_loop"
}

/** One-line English description (error texts are English-only by project convention). */
fun LoadProblem.describe(): String = when (this) {
    is LoadProblem.Network -> description
    is LoadProblem.Http -> "HTTP $statusCode"
    is LoadProblem.UntrustedCertificate -> "Untrusted certificate for $host"
    LoadProblem.RendererCrashLoop -> "Web renderer crashed repeatedly"
}

sealed interface WebAction {
    data class LoadUrl(val url: String) : WebAction

    data object Reload : WebAction

    data object ClearCacheAndReload : WebAction

    /** Debug builds only: loads `chrome://crash` to exercise renderer-crash recovery. */
    data object CrashRenderer : WebAction

    /** Asks the Home Assistant frontend whether its websocket is connected (not a navigation). */
    data object CheckHaConnection : WebAction

    /** Liveness probe: evaluates a trivial script; the answer arrives via `onProbeAnswered(id)`. */
    data class ProbeRenderer(val id: Long) : WebAction

    /** Terminates the page's renderer process; the host then rebuilds the WebView (watchdog). */
    data object TerminateRenderer : WebAction

    /** Debug builds only: loads `chrome://hang` to exercise the watchdog. */
    data object HangRenderer : WebAction
}

/** Actions that are not navigations (they don't reset the page-load error state). */
val WebAction.isNavigation: Boolean
    get() = this !is WebAction.CheckHaConnection && this !is WebAction.ProbeRenderer &&
        this != WebAction.TerminateRenderer

/** Result of [WebAction.CheckHaConnection]. */
enum class HaConnection {
    CONNECTED,
    DISCONNECTED,

    /** Not a Home Assistant page, not logged in yet, or the frontend's internals changed. */
    UNKNOWN
}

data class WebRequest(val id: Long, val action: WebAction)
