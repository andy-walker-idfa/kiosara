package io.github.andy_walker_idfa.smarthome_dashboard.kiosk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.security.SettingsLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PIN entry before Settings: a numeric keypad on a dimmed backdrop. Closes itself after [IDLE_TIMEOUT_MS]
 * without input, so the panel never stays on it.
 */
@Composable
fun PinPrompt(onCheck: suspend (String) -> SettingsLock.Result, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrongPin by remember { mutableStateOf(false) }
    var lockedSeconds by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastInput by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastInput) {
        delay(IDLE_TIMEOUT_MS)
        onDismiss()
    }

    fun submit() {
        if (pin.length < MIN_DIGITS || busy) return
        busy = true
        scope.launch {
            val result = onCheck(pin)
            wrongPin = result == SettingsLock.Result.Wrong
            lockedSeconds = (result as? SettingsLock.Result.LockedOut)?.let { (it.remainingMs + 999) / 1000 }
            if (result == SettingsLock.Result.Ok) onSuccess()
            pin = ""
            busy = false
        }
    }

    fun input(action: () -> Unit) {
        lastInput++
        action()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp).width(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.pin_prompt_title), style = MaterialTheme.typography.titleLarge)
                Text("●".repeat(pin.length).ifEmpty { " " }, fontSize = 28.sp, letterSpacing = 6.sp)
                val message =
                    when {
                        lockedSeconds != null -> stringResource(R.string.pin_locked, lockedSeconds.toString())
                        wrongPin -> stringResource(R.string.pin_wrong)
                        else -> ""
                    }
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                for (row in listOf("123", "456", "789")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (digit in row) {
                            Key(digit.toString()) {
                                input { if (pin.length < MAX_DIGITS) pin += digit }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Key("⌫") { input { pin = pin.dropLast(1) } }
                    Key("0") { input { if (pin.length < MAX_DIGITS) pin += '0' } }
                    Key(stringResource(R.string.pin_ok)) { input(::submit) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) }
            }
        }
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.size(width = 80.dp, height = 56.dp)) {
        Text(label, fontSize = 22.sp)
    }
}

private const val MIN_DIGITS = 4
private const val MAX_DIGITS = 8
private const val IDLE_TIMEOUT_MS = 60_000L
