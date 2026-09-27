package io.github.andy_walker_idfa.smarthome_dashboard.recovery

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import kotlin.system.exitProcess

/**
 * Restart trampoline, running in its own `:restart` process (see the manifest). The dying main process starts
 * it while the app still has a visible window, which is the only time Android lets an app open an activity.
 * It is visible itself (translucent), so it may start the dashboard again once the old process is gone.
 *
 * The `:restart` process must never touch the app container (DataStore allows one process per file):
 * `DashboardApplication` skips all initialisation there.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val oldPid = intent.getIntExtra(EXTRA_PID, -1)
        if (oldPid > 0 && oldPid != Process.myPid()) Process.killProcess(oldPid)
        val target = intent.getStringExtra(EXTRA_TARGET)
        if (target != null) {
            startActivity(
                Intent().setClassName(packageName, target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
        finish()
        exitProcess(0)
    }

    companion object {
        private const val EXTRA_PID = "pid"
        private const val EXTRA_TARGET = "target"

        /** Starts the trampoline, which ends this process and opens [targetActivity] in a fresh one. */
        fun start(context: Context, targetActivity: String) {
            context.startActivity(
                Intent(context, RestartActivity::class.java)
                    .putExtra(EXTRA_PID, Process.myPid())
                    .putExtra(EXTRA_TARGET, targetActivity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}
