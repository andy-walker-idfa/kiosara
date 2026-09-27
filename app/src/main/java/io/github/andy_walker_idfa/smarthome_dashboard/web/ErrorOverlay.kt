package io.github.andy_walker_idfa.smarthome_dashboard.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import kotlinx.coroutines.delay

/**
 * Full-screen error shown on top of the WebView instead of Chrome's error page. It swallows every
 * touch so nothing reaches the page underneath.
 */
@Composable
fun ErrorOverlay(
    error: PageState.Error,
    clock: Clock,
    onRetryNow: () -> Unit,
    onTrustCertificate: (LoadProblem.UntrustedCertificate) -> Unit
) {
    // Surface supplies the background and the matching content colour for the text.
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(titleFor(error.problem)),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Text(text = error.url, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(
                    text = detailFor(error.problem),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                RetryStatus(error, clock)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = onRetryNow) { Text(stringResource(R.string.error_retry_now)) }
                    val problem = error.problem
                    if (problem is LoadProblem.UntrustedCertificate && problem.trustable) {
                        OutlinedButton(onClick = { onTrustCertificate(problem) }) {
                            Text(stringResource(R.string.error_trust_certificate))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryStatus(error: PageState.Error, clock: Clock) {
    val retryAt = error.retryAtElapsedMillis
    val secondsLeft by produceState(initialValue = secondsUntil(retryAt, clock), retryAt) {
        while (retryAt != null) {
            value = secondsUntil(retryAt, clock)
            delay(ONE_SECOND_MS)
        }
    }
    val text =
        when {
            error.waitingForNetwork -> stringResource(R.string.error_waiting_for_network)
            retryAt != null -> stringResource(R.string.error_retrying_in, secondsLeft)
            else -> return
        }
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

private fun secondsUntil(elapsedMillis: Long?, clock: Clock): Int {
    if (elapsedMillis == null) return 0
    return ((elapsedMillis - clock.elapsedRealtimeMillis() + ONE_SECOND_MS - 1) / ONE_SECOND_MS).toInt().coerceAtLeast(
        0
    )
}

private fun titleFor(problem: LoadProblem): Int = when (problem) {
    is LoadProblem.Network, is LoadProblem.Http -> R.string.error_title_unreachable
    is LoadProblem.UntrustedCertificate -> R.string.error_title_certificate
    LoadProblem.RendererCrashLoop -> R.string.error_title_crash_loop
}

@Composable
private fun detailFor(problem: LoadProblem): String = when (problem) {
    is LoadProblem.Network -> problem.description

    is LoadProblem.Http -> stringResource(R.string.error_detail_http, problem.statusCode)

    is LoadProblem.UntrustedCertificate ->
        if (problem.trustable) {
            stringResource(R.string.error_detail_certificate_trustable, problem.sha256)
        } else {
            stringResource(R.string.error_detail_certificate, problem.host)
        }

    LoadProblem.RendererCrashLoop -> stringResource(R.string.error_detail_crash_loop)
}

private const val ONE_SECOND_MS = 1_000L
