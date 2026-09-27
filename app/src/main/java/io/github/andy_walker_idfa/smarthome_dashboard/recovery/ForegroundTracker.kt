package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Knows whether one of the app's activities is started (visible). Android only lets an app open an activity
 * while it has a visible window, so a self-restart is possible only then.
 */
class ForegroundTracker(private val isDashboard: (Activity) -> Boolean) : Application.ActivityLifecycleCallbacks {
    @Volatile private var started = 0

    @Volatile var dashboardStarted = false
        private set

    val isVisible: Boolean get() = started > 0

    override fun onActivityStarted(activity: Activity) {
        started++
        if (isDashboard(activity)) dashboardStarted = true
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
        if (isDashboard(activity)) dashboardStarted = false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
