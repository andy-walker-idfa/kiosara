package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatus
import java.util.Date

/** Kiosk-lock actions, implemented by SettingsActivity (some need the activity to stop Lock Task Mode). */
class KioskActions(
    val setLockEnabled: (Boolean) -> Unit,
    val unlockTemporarily: () -> Unit,
    val relock: () -> Unit,
    val openAndroidSettings: () -> Unit,
    val removeDeviceOwner: () -> Unit
)

/**
 * "Kiosk lock": optional. Without device owner it only explains how to set it up. As device owner: the lock switch
 * (needs a PIN) and the escape hatches. Settings itself is PIN-protected, so the hatches need no extra PIN.
 */
@Composable
fun KioskSection(status: KioskStatus, actions: KioskActions) {
    var confirmRemove by remember { mutableStateOf(false) }
    if (!status.deviceOwner) {
        Help(stringResource(R.string.kiosk_not_owner))
        return
    }
    Text(stringResource(lockStateText(status)), style = MaterialTheme.typography.bodyMedium)
    status.unlockedUntilMillis?.let {
        val time = DateFormat.getTimeFormat(LocalContext.current).format(Date(it))
        Text(stringResource(R.string.kiosk_unlocked_until, time), style = MaterialTheme.typography.bodyMedium)
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(
                value = status.lockEnabled,
                enabled = status.lockEnabled || status.canEnable,
                role = Role.Switch,
                onValueChange = actions.setLockEnabled
            )
        ) {
            Text(stringResource(R.string.kiosk_lock), modifier = Modifier.weight(1f))
            Switch(
                checked = status.lockEnabled,
                onCheckedChange = null,
                enabled = status.lockEnabled || status.canEnable
            )
        }
        Help(stringResource(if (status.pinSet) R.string.kiosk_lock_help else R.string.kiosk_needs_pin))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (status.lockEnabled) {
            if (status.unlockedUntilMillis == null) {
                OutlinedButton(onClick = actions.unlockTemporarily) { Text(stringResource(R.string.kiosk_unlock)) }
            } else {
                OutlinedButton(onClick = actions.relock) { Text(stringResource(R.string.kiosk_relock)) }
            }
        }
        OutlinedButton(onClick = actions.openAndroidSettings) {
            Text(stringResource(R.string.kiosk_open_android_settings))
        }
    }
    if (status.lockEnabled) Help(stringResource(R.string.kiosk_locked_hint))
    OutlinedButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.kiosk_remove_owner)) }
    Help(stringResource(R.string.kiosk_remove_owner_help))

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.kiosk_remove_owner)) },
            text = { Text(stringResource(R.string.kiosk_remove_owner_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    actions.removeDeviceOwner()
                }) { Text(stringResource(R.string.kiosk_remove_owner_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.pin_cancel)) }
            }
        )
    }
}

private fun lockStateText(status: KioskStatus): Int = when {
    // Should be released but Android still reports the lock: never pretend it's unlocked.
    status.lockActive && !status.shouldLock -> R.string.kiosk_state_still_locked

    !status.lockEnabled -> R.string.kiosk_state_off

    status.unlockedUntilMillis != null -> R.string.kiosk_state_unlocked

    status.lockActive -> R.string.kiosk_state_active

    else -> R.string.kiosk_state_on
}

@Composable
private fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
