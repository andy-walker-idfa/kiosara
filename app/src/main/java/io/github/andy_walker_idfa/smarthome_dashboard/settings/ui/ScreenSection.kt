package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatus
import io.github.andy_walker_idfa.smarthome_dashboard.settings.BrightnessMode
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.SettingsDraft.Field

/** "Screen": brightness and night mode (black screen at night; a touch wakes it for a minute). */
@Composable
fun ScreenSection(
    form: ScreenSettingsForm,
    invalid: Set<Field>,
    display: DisplayStatus,
    onChange: ((ScreenSettingsForm) -> ScreenSettingsForm) -> Unit
) {
    ScreenHelp(stringResource(R.string.screen_status, display.brightnessPercent))

    Choice(
        R.string.screen_brightness_mode,
        R.string.screen_brightness_mode_help,
        listOf(
            Option(BrightnessMode.SYSTEM, R.string.screen_brightness_system),
            Option(BrightnessMode.MANUAL, R.string.screen_brightness_manual)
        ),
        form.brightnessMode
    ) { v -> onChange { it.copy(brightnessMode = v) } }
    if (form.brightnessMode == BrightnessMode.MANUAL) {
        Number(
            R.string.screen_manual_brightness,
            R.string.screen_manual_brightness_help,
            form.manualBrightnessPercent,
            Field.MANUAL_BRIGHTNESS in invalid
        ) { v -> onChange { it.copy(manualBrightnessPercent = v) } }
    }

    Toggle(R.string.screen_night, R.string.screen_night_help, form.nightEnabled) { v ->
        onChange { it.copy(nightEnabled = v) }
    }
    if (!form.nightEnabled) return
    ScreenHelp(
        stringResource(if (display.night) R.string.screen_night_now_night else R.string.screen_night_now_day)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TimeField(
            R.string.screen_night_start,
            R.string.screen_night_start_help,
            form.nightStart,
            Field.NIGHT_START in invalid,
            Modifier.weight(1f)
        ) { v -> onChange { it.copy(nightStart = v) } }
        TimeField(
            R.string.screen_night_end,
            R.string.screen_night_end_help,
            form.nightEnd,
            Field.NIGHT_END in invalid,
            Modifier.weight(1f)
        ) { v -> onChange { it.copy(nightEnd = v) } }
    }
}

private class Option<T>(val value: T, @StringRes val label: Int, val enabled: Boolean = true)

@Composable
private fun <T> Choice(
    @StringRes label: Int,
    @StringRes help: Int,
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        ScreenHelp(stringResource(help))
        for (option in options) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = option.value == selected,
                    enabled = option.enabled,
                    role = Role.RadioButton,
                    onClick = { onSelect(option.value) }
                )
            ) {
                RadioButton(selected = option.value == selected, onClick = null, enabled = option.enabled)
                Text(
                    stringResource(option.label),
                    modifier = Modifier.padding(start = 8.dp),
                    color = if (option.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun Number(
    @StringRes label: Int,
    @StringRes help: Int,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(help)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.widthIn(max = 520.dp)
    )
}

@Composable
private fun TimeField(
    @StringRes label: Int,
    @StringRes help: Int,
    value: String,
    isError: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(help)) },
        isError = isError,
        singleLine = true,
        modifier = modifier.widthIn(max = 260.dp)
    )
}

@Composable
private fun Toggle(@StringRes label: Int, @StringRes help: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
        ) {
            Text(stringResource(label), modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
        ScreenHelp(stringResource(help))
    }
}

@Composable
private fun ScreenHelp(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
