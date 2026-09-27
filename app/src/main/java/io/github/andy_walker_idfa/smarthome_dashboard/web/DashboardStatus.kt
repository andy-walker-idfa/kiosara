package io.github.andy_walker_idfa.smarthome_dashboard.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the dashboard is showing, readable by long-lived components (the MQTT service) that must not
 * depend on the activity or its ViewModel being alive.
 */
data class DashboardStatus(
    /** True while the dashboard activity is visible. */
    val active: Boolean = false,
    val currentUrl: String? = null,
    val pageError: PageErrorInfo? = null,
    /** Wall-clock time of the last touch, or null if none since app start. */
    val lastInteractionMillis: Long? = null,
    /** Size of the dashboard WebView as laid out; null until the first layout. */
    val viewport: Viewport? = null
)

/**
 * The WebView's size in physical pixels and the display density. CSS pixels = physical / devicePixelRatio,
 * where devicePixelRatio = densityDpi / 160 (what the page sees as window.devicePixelRatio).
 */
data class Viewport(val widthPx: Int, val heightPx: Int, val densityDpi: Int) {
    val devicePixelRatio: Double get() = densityDpi / BASELINE_DPI
    val cssWidth: Int get() = Math.round(widthPx / devicePixelRatio).toInt()
    val cssHeight: Int get() = Math.round(heightPx / devicePixelRatio).toInt()

    private companion object {
        const val BASELINE_DPI = 160.0
    }
}

data class PageErrorInfo(val kind: String, val description: String, val url: String, val sinceMillis: Long)

/** Read side, used by publishers. */
interface DashboardStatusSource {
    val status: StateFlow<DashboardStatus>
}

/** Write side, used by [DashboardController]. */
fun interface DashboardStatusSink {
    fun update(transform: (DashboardStatus) -> DashboardStatus)
}

/** App-scoped holder created once in `AppContainer`. */
class DashboardStatusStore :
    DashboardStatusSource,
    DashboardStatusSink {
    private val mutable = MutableStateFlow(DashboardStatus())
    override val status: StateFlow<DashboardStatus> = mutable.asStateFlow()

    override fun update(transform: (DashboardStatus) -> DashboardStatus) = mutable.update(transform)
}
