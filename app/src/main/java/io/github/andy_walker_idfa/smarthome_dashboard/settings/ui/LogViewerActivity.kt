package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication
import io.github.andy_walker_idfa.smarthome_dashboard.MainActivity
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.core.LogReader
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskWindow
import io.github.andy_walker_idfa.smarthome_dashboard.ui.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which lines the viewer shows. */
enum class LogFilter { ALL, WARNINGS, ERRORS }

/** Pure helpers for the viewer; unit-tested. */
object LogLines {
    /** `2026-09-26 11:02:08.242+02:00 W Tag: message`: the level letter follows the 29-character timestamp. */
    private const val LEVEL_INDEX = 30

    /** Level letter of each line; continuation lines (no timestamp) inherit the previous line's level. */
    fun levels(lines: List<String>): List<Char> {
        var current = 'I'
        return lines.map { line ->
            val level = line.getOrNull(LEVEL_INDEX) ?: ' '
            val isEntry = line.length > LEVEL_INDEX + 1 && line[4] == '-' && line[LEVEL_INDEX + 1] == ' ' &&
                level in "DIWE"
            if (isEntry) current = level
            current
        }
    }

    /** Newest first, filtered. */
    fun select(lines: List<String>, filter: LogFilter): List<String> {
        val levels = levels(lines)
        return lines.indices.reversed()
            .filter { index ->
                when (filter) {
                    LogFilter.ALL -> true
                    LogFilter.WARNINGS -> levels[index] == 'W' || levels[index] == 'E'
                    LogFilter.ERRORS -> levels[index] == 'E'
                }
            }.map { lines[it] }
    }
}

/**
 * Shows the app's log files (Settings → Logs). Opened only from Settings, so it is behind the PIN; it runs in the
 * dashboard's task and therefore also under the kiosk lock. After 10 minutes without a touch it returns straight to
 * the dashboard.
 */
class LogViewerActivity : ComponentActivity() {
    private val container by lazy { (application as DashboardApplication).container }
    private val autoClose = Handler(Looper.getMainLooper())
    private val closeRunnable = Runnable {
        // MainActivity is singleTask: starting it clears Settings and this viewer from the task.
        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KioskWindow.keepScreenOn(this)
        setContent { AppTheme { LogViewer(container.logReader, onClose = ::finish) } }
    }

    override fun onResume() {
        super.onResume()
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

    private companion object {
        const val AUTO_CLOSE_MS = 10 * 60_000L
    }
}

@Composable
private fun LogViewer(reader: LogReader, onClose: () -> Unit) {
    var lines by remember { mutableStateOf<List<String>?>(null) }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        lines = withContext(Dispatchers.IO) { reader.readTail(MAX_LINES) }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.logs_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f)
                )
                for (option in LogFilter.entries) {
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(stringResource(filterLabel(option))) }
                    )
                }
                OutlinedButton(onClick = { refresh++ }) { Text(stringResource(R.string.logs_refresh)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.settings_close)) }
            }
            HorizontalDivider()
            val all = lines
            when {
                all == null -> Text(stringResource(R.string.settings_loading), modifier = Modifier.padding(24.dp))

                all.isEmpty() -> Text(stringResource(R.string.logs_empty), modifier = Modifier.padding(24.dp))

                else -> {
                    val shown = remember(all, filter) { LogLines.select(all, filter) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    ) {
                        items(shown) { line ->
                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabel(filter: LogFilter): Int = when (filter) {
    LogFilter.ALL -> R.string.logs_filter_all
    LogFilter.WARNINGS -> R.string.logs_filter_warnings
    LogFilter.ERRORS -> R.string.logs_filter_errors
}

private const val MAX_LINES = 2_000
