package io.github.andy_walker_idfa.smarthome_dashboard.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewRenderProcess
import android.webkit.WebViewRenderProcessClient
import android.widget.FrameLayout
import io.github.andy_walker_idfa.smarthome_dashboard.BuildConfig
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.settings.UrlValidator
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Owns the dashboard [WebView] inside [container]: creation, configuration, lifecycle, and
 * re-creation after the renderer process dies. Page events are forwarded to a [Listener]. The host
 * makes no decisions; it executes [WebAction]s.
 *
 * Must be used on the main thread.
 */
class WebViewHost(private val context: Context, private val container: FrameLayout, private val listener: Listener) {
    interface Listener {
        fun onPageStarted(url: String)

        fun onPageFinished(url: String)

        fun onMainFrameError(url: String, problem: LoadProblem)

        /** Called after a fresh WebView has replaced the dead one. */
        fun onRendererGone(crashed: Boolean)

        fun shouldTrustCertificate(host: String, sha256: String): Boolean

        /** The WebView was laid out with a new size (physical pixels) at [densityDpi]. */
        fun onViewportChanged(widthPx: Int, heightPx: Int, densityDpi: Int)

        fun onHaConnectionChecked(state: HaConnection)

        fun onProbeAnswered(id: Long)

        /** Android reports the renderer unresponsive to input (repeats about every 5 s while it lasts). */
        fun onRendererUnresponsive()

        fun onRendererResponsive()
    }

    private var applied: WebSettings? = null
    private var lastRequestedUrl: String? = null

    /** The activity is resumed. */
    private var activityResumed = false

    /** Covered by the black night screen: not rendered, not composited. */
    private var coveredPaused = false

    /** Whether [webView] is currently running (WebView.onResume) or paused (WebView.onPause). */
    private var running = true
    private var webView: WebView = createWebView()

    fun apply(settings: WebSettings) {
        if (settings == applied) return
        applied = settings
        configure(webView, settings)
    }

    fun execute(action: WebAction) {
        when (action) {
            is WebAction.LoadUrl -> {
                lastRequestedUrl = action.url
                webView.loadUrl(action.url)
            }

            WebAction.Reload -> webView.reload()

            WebAction.ClearCacheAndReload -> {
                // Only the HTTP cache. localStorage holds HA's auth tokens and is left alone.
                webView.clearCache(true)
                webView.reload()
            }

            WebAction.CrashRenderer -> if (BuildConfig.DEBUG) webView.loadUrl("chrome://crash")

            is WebAction.ProbeRenderer -> {
                val view = webView
                view.evaluateJavascript("1") { if (view === webView) listener.onProbeAnswered(action.id) }
            }

            WebAction.TerminateRenderer -> {
                val process = webView.webViewRenderProcess
                AppLog.w(TAG, "Terminating unresponsive renderer (process known=${process != null})")
                // terminate() leads to onRenderProcessGone; without a process handle, rebuild directly.
                if (process?.terminate() != true) replaceWebView(crashed = false)
            }

            WebAction.HangRenderer -> if (BuildConfig.DEBUG) webView.loadUrl("chrome://hang")

            WebAction.CheckHaConnection -> {
                val view = webView
                view.evaluateJavascript(HA_CONNECTION_SCRIPT) { result ->
                    if (view === webView) listener.onHaConnectionChecked(parseHaConnection(result))
                }
            }
        }
    }

    /**
     * Pauses the dashboard while the Black or Clock screen covers it: hidden (no compositing) and paused.
     * WebView.onPause() doesn't stop JavaScript, and the page sees itself as hidden. The process-wide
     * `pauseTimers()` is deliberately not used: a WebView re-created after a renderer crash could stay paused.
     */
    fun setCovered(covered: Boolean) {
        if (covered == coveredPaused) return
        coveredPaused = covered
        webView.visibility = if (covered) View.INVISIBLE else View.VISIBLE
        updateRunning()
    }

    /** Navigates back in WebView history; returns false if there is nowhere to go. */
    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    fun onResume() {
        activityResumed = true
        updateRunning()
    }

    fun onPause() {
        activityResumed = false
        updateRunning()
        // Persist cookies now rather than waiting for WebView's periodic flush.
        CookieManager.getInstance().flush()
    }

    private fun updateRunning() {
        val shouldRun = activityResumed && !coveredPaused
        if (shouldRun == running) return
        running = shouldRun
        if (shouldRun) webView.onResume() else webView.onPause()
    }

    fun destroy() {
        container.removeView(webView)
        webView.destroy()
    }

    // JavaScript and DOM storage are required by the Home Assistant frontend.
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val view = WebView(context)
        view.layoutParams =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        view.setBackgroundColor(Color.BLACK)
        view.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            if (v === webView && width > 0 && height > 0 &&
                (width != oldRight - oldLeft || height != oldBottom - oldTop)
            ) {
                listener.onViewportChanged(width, height, v.resources.displayMetrics.densityDpi)
            }
        }
        with(view.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            // Camera streams and media cards on the dashboard play without a tap (fixed since 0.8.0).
            mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, false)
        }
        // Keep the renderer at foreground priority so the OS doesn't kill it first under memory pressure.
        view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        view.webViewClient = Client()
        view.webChromeClient = ChromeClient()
        // Called on the UI thread (no executor given).
        view.setWebViewRenderProcessClient(
            object : WebViewRenderProcessClient() {
                override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                    if (view === webView) listener.onRendererUnresponsive()
                }

                override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {
                    if (view === webView) listener.onRendererResponsive()
                }
            }
        )
        applied?.let { configure(view, it) }
        container.addView(view, 0)
        return view
    }

    private fun configure(view: WebView, settings: WebSettings) {
        with(view.settings) {
            textZoom = settings.textZoomPercent
        }
    }

    private fun recreateAfterRendererDeath(dead: WebView, detail: RenderProcessGoneDetail) {
        AppLog.w(TAG, "Renderer gone: crashed=${detail.didCrash()}, priorityAtExit=${detail.rendererPriorityAtExit()}")
        // A dead WebView must not be used for anything except removal and destroy().
        container.removeView(dead)
        dead.destroy()
        rebuild()
        listener.onRendererGone(detail.didCrash())
    }

    /** Replaces a hung WebView whose renderer can't be terminated. */
    private fun replaceWebView(crashed: Boolean) {
        val old = webView
        container.removeView(old)
        old.destroy()
        rebuild()
        listener.onRendererGone(crashed)
    }

    private fun rebuild() {
        webView = createWebView()
        // A new WebView starts running; bring it into the current paused/covered state.
        running = true
        webView.visibility = if (coveredPaused) View.INVISIBLE else View.VISIBLE
        updateRunning()
    }

    private inner class Client : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view === webView) listener.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view === webView) listener.onPageFinished(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view !== webView || !request.isForMainFrame) return
            listener.onMainFrameError(request.url.toString(), LoadProblem.Network(error.description.toString()))
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse
        ) {
            if (view !== webView || !request.isForMainFrame) return
            // 4xx pages come from HA itself and are shown as-is. 5xx means HA (or a proxy) is unavailable.
            if (errorResponse.statusCode >= HTTP_SERVER_ERROR) {
                listener.onMainFrameError(request.url.toString(), LoadProblem.Http(errorResponse.statusCode))
            }
        }

        // Proceeds only for the configured host AND a pinned certificate fingerprint. Everything else is cancelled.
        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            val host = UrlValidator.hostOf(error.url)
            val sha256 = error.certificate.x509Certificate?.let(::sha256Fingerprint)
            if (host != null && sha256 != null && listener.shouldTrustCertificate(host, sha256)) {
                handler.proceed()
                return
            }
            handler.cancel()
            AppLog.w(TAG, "TLS error ${error.primaryError} for $host; certificate SHA-256 $sha256")
            val mainFrameHost = lastRequestedUrl?.let(UrlValidator::hostOf)
            if (view === webView && host != null && host == mainFrameHost) {
                listener.onMainFrameError(error.url, LoadProblem.UntrustedCertificate(host, sha256.orEmpty()))
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            // Kiosk: never hand off to other apps (intent:, tel:, market:, ...).
            AppLog.w(TAG, "Blocked navigation to non-web scheme '$scheme'")
            return true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === webView) {
                recreateAfterRendererDeath(view, detail)
            } else {
                view.destroy()
            }
            return true
        }
    }

    private class ChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            // Never granted: the app uses no camera or microphone (scope decision).
            AppLog.i(TAG, "Denied web permission request: ${request.resources.joinToString()}")
            request.deny()
        }
    }

    private companion object {
        const val TAG = "WebView"
        const val HTTP_SERVER_ERROR = 500

        /**
         * Reads `hass.connection.connected` from HA's root element (home-assistant-js-websocket). Returns
         * "unknown" on anything else, so a non-HA page or a changed frontend never triggers a reload.
         */
        const val HA_CONNECTION_SCRIPT =
            "(function(){try{var ha=document.querySelector('home-assistant');" +
                "if(!ha||!ha.hass||!ha.hass.connection)return 'unknown';" +
                "return ha.hass.connection.connected?'connected':'disconnected';}catch(e){return 'unknown';}})()"

        /** evaluateJavascript returns the JSON-encoded result, e.g. `"connected"` with quotes. */
        fun parseHaConnection(result: String?): HaConnection = when (result?.trim('"')) {
            "connected" -> HaConnection.CONNECTED
            "disconnected" -> HaConnection.DISCONNECTED
            else -> HaConnection.UNKNOWN
        }

        fun sha256Fingerprint(certificate: X509Certificate): String = MessageDigest
            .getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
    }
}
