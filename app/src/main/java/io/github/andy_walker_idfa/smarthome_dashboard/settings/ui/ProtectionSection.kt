package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.security.PinHasher

/** Shown at the top of Settings when the saved PIN could not be read and Settings opened without it. */
@Composable
fun PinLostNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            stringResource(R.string.pin_lost_notice),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/**
 * "Settings protection": set, change or remove the optional PIN. Applied immediately (not part of the Save
 * draft). Being in Settings already proves access, so changing or removing needs no old PIN.
 */
@Composable
fun ProtectionSection(
    pinEnabled: Boolean,
    kioskLockOn: Boolean,
    busy: Boolean,
    onSetPin: (String) -> Unit,
    onRemovePin: () -> Unit
) {
    var dialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Text(
        stringResource(if (pinEnabled) R.string.protection_pin_set else R.string.protection_no_pin_notice),
        style = MaterialTheme.typography.bodyMedium
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { dialog = true }, enabled = !busy) {
            Text(stringResource(if (pinEnabled) R.string.protection_change_pin else R.string.protection_set_pin))
        }
        if (pinEnabled) {
            OutlinedButton(onClick = { if (kioskLockOn) confirmRemove = true else onRemovePin() }, enabled = !busy) {
                Text(stringResource(R.string.protection_remove_pin))
            }
        }
    }
    Text(
        stringResource(R.string.protection_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (confirmRemove) {
        // The kiosk lock needs a PIN, so removing the PIN switches the lock off.
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.protection_remove_pin)) },
            text = { Text(stringResource(R.string.pin_remove_unlocks_kiosk)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemovePin()
                }) { Text(stringResource(R.string.protection_remove_pin)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.pin_cancel)) }
            }
        )
    }
    if (dialog) {
        SetPinDialog(
            onSave = { pin ->
                dialog = false
                onSetPin(pin)
            },
            onCancel = { dialog = false }
        )
    }
}

@Composable
private fun SetPinDialog(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.protection_set_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(R.string.pin_new, pin) { pin = it.filter(Char::isDigit).take(MAX_DIGITS) }
                PinField(R.string.pin_repeat, repeat) { repeat = it.filter(Char::isDigit).take(MAX_DIGITS) }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error =
                    when {
                        !PinHasher.isValidPin(pin) -> R.string.pin_invalid
                        pin != repeat -> R.string.pin_mismatch
                        else -> null
                    }
                if (error == null) onSave(pin)
            }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.pin_cancel)) } }
    )
}

@Composable
private fun PinField(label: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

private const val MAX_DIGITS = 8
