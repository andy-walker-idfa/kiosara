package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttStatus
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttTestResult
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.SettingsDraft.Field
import io.github.andy_walker_idfa.smarthome_dashboard.settings.ui.SettingsDraft.Section
import io.github.andy_walker_idfa.smarthome_dashboard.web.Viewport
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Battery-optimization status and the flavor-specific way to change it. */
class BatteryOptimizationUi(val exempt: Boolean, val hint: String?, val onChange: () -> Unit)

/**
 * Settings, ordered by importance: required Dashboard first, then the optional Home Assistant integration, screen,
 * protection and kiosk lock. Only what a normal user sets up (0.8.0; everything else has fixed defaults). One Save
 * button (in the fixed header) saves everything; Close/Back asks before discarding unsaved changes.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    versionName: String,
    channel: String,
    battery: BatteryOptimizationUi,
    pinLost: Boolean,
    kioskActions: KioskActions,
    onOpenLogs: () -> Unit,
    debugTools: DebugTools?,
    onMqttEnabling: () -> Unit,
    onSaved: (Settings) -> Unit,
    onClose: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        val saved = settings
        if (saved == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.settings_loading))
            }
        } else {
            SettingsContent(
                saved = saved,
                viewModel = viewModel,
                versionName = versionName,
                channel = channel,
                battery = battery,
                pinLost = pinLost,
                kioskActions = kioskActions,
                onOpenLogs = onOpenLogs,
                debugTools = debugTools,
                onMqttEnabling = onMqttEnabling,
                onSaved = onSaved,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun SettingsContent(
    saved: Settings,
    viewModel: SettingsViewModel,
    versionName: String,
    channel: String,
    battery: BatteryOptimizationUi,
    pinLost: Boolean,
    kioskActions: KioskActions,
    onOpenLogs: () -> Unit,
    debugTools: DebugTools?,
    onMqttEnabling: () -> Unit,
    onSaved: (Settings) -> Unit,
    onClose: () -> Unit
) {
    val kiosk by viewModel.kiosk.collectAsStateWithLifecycle()
    val pinBusy by viewModel.pinBusy.collectAsStateWithLifecycle()
    val pinResult by viewModel.pinResult.collectAsStateWithLifecycle()
    val hasPassword by viewModel.hasMqttPassword.collectAsStateWithLifecycle()
    val mqttStatus by viewModel.mqttStatus.collectAsStateWithLifecycle()
    val testState by viewModel.connectionTest.collectAsStateWithLifecycle()
    val viewport by viewModel.viewport.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()

    // Initialised once; later changes to the stored settings (e.g. the pinned certificate) don't overwrite unsaved
    // edits.
    var draft by remember { mutableStateOf(SettingsDraft.from(saved)) }
    var invalid by remember { mutableStateOf(emptySet<Field>()) }
    var message by remember { mutableStateOf<Int?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val sectionTop = remember { mutableStateMapOf<Section, Int>() }
    val dirty = draft.isDirty(saved)

    fun edit(transform: (SettingsDraft) -> SettingsDraft) {
        draft = transform(draft)
        message = null
    }

    fun editMqtt(transform: (MqttSettingsForm) -> MqttSettingsForm) {
        edit { it.copy(mqtt = transform(it.mqtt)) }
        viewModel.clearConnectionTest()
    }

    fun requestClose() {
        if (dirty) confirmDiscard = true else onClose()
    }

    fun scrollTo(section: Section) {
        scope.launch { scroll.animateScrollTo(sectionTop[section] ?: 0) }
    }

    fun save() {
        when (val result = draft.validate(saved, hasPassword)) {
            is SettingsDraft.Result.Valid -> {
                invalid = emptySet()
                message =
                    if (result.newPassword != null) R.string.settings_saved_with_password else R.string.settings_saved
                viewModel.saveAll(result) { stored ->
                    draft = SettingsDraft.from(stored)
                    onSaved(stored)
                }
            }

            is SettingsDraft.Result.Invalid -> {
                invalid = result.fields
                message = R.string.settings_invalid
                scrollTo(result.firstSection)
            }
        }
    }

    fun testConnection() {
        val problems =
            (draft.validate(saved, hasPassword) as? SettingsDraft.Result.Invalid)?.fields.orEmpty() intersect
                MQTT_FIELDS
        val mqttResult = draft.mqtt.validate()
        if (problems.isNotEmpty() || mqttResult !is MqttSettingsForm.Result.Valid) {
            invalid = problems
            return
        }
        viewModel.testConnection(mqttResult.settings, mqttResult.newPassword)
    }

    BackHandler(onBack = ::requestClose)

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(
            dirty = dirty,
            message = message,
            isError = message == R.string.settings_invalid,
            onSave = ::save,
            onClose = ::requestClose
        )
        HorizontalDivider()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pinLost && !saved.security.pinEnabled) PinLostNotice()
            StatusLine(saved.web.startUrl.isNotBlank(), mqttStatus)
            viewport?.let { ViewportLine(it) }

            SectionCard(
                R.string.settings_section_dashboard,
                R.string.settings_required,
                Section.DASHBOARD,
                sectionTop
            ) {
                SettingField(
                    R.string.settings_start_url,
                    R.string.settings_start_url_help,
                    draft.web.startUrl,
                    Field.START_URL in invalid,
                    keyboardType = KeyboardType.Uri
                ) { value -> edit { it.copy(web = it.web.copy(startUrl = value)) } }
                NumberField(
                    R.string.settings_text_zoom,
                    R.string.settings_text_zoom_help,
                    draft.web.textZoomPercent,
                    Field.TEXT_ZOOM in invalid
                ) { value -> edit { it.copy(web = it.web.copy(textZoomPercent = value)) } }
                if (saved.web.trustedCertSha256.isNotBlank()) {
                    Text(
                        stringResource(R.string.settings_pinned_certificate, saved.web.trustedCertSha256),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = viewModel::forgetPinnedCertificate) {
                        Text(stringResource(R.string.settings_forget_certificate))
                    }
                }
            }

            SectionCard(R.string.settings_section_mqtt, R.string.settings_optional, Section.INTEGRATION, sectionTop) {
                IntegrationSection(
                    draft = draft,
                    invalid = invalid,
                    hasPassword = hasPassword,
                    testState = testState,
                    battery = battery,
                    onEnabledChange = { enabled ->
                        editMqtt { it.copy(enabled = enabled) }
                        if (enabled) onMqttEnabling()
                    },
                    onMqttChange = ::editMqtt,
                    onTest = ::testConnection
                )
            }

            SectionCard(R.string.settings_section_screen, R.string.settings_optional, Section.SCREEN, sectionTop) {
                ScreenSection(
                    form = draft.screen,
                    invalid = invalid,
                    display = display
                ) { transform -> edit { it.copy(screen = transform(it.screen)) } }
            }

            SectionCard(R.string.settings_section_protection, R.string.settings_optional, section = null, sectionTop) {
                ProtectionSection(
                    pinEnabled = saved.security.pinEnabled,
                    kioskLockOn = saved.security.kioskLock,
                    busy = pinBusy,
                    onSetPin = viewModel::setPin,
                    onRemovePin = viewModel::removePin
                )
                pinResult?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            }

            SectionCard(R.string.settings_section_kiosk, R.string.settings_optional, section = null, sectionTop) {
                KioskSection(kiosk, kioskActions)
            }

            ActionsRow(viewModel)
            Text(stringResource(R.string.logs_title), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onOpenLogs) { Text(stringResource(R.string.logs_open)) }
                HelpText(R.string.logs_help)
            }
            if (debugTools != null) DebugSection(debugTools, viewModel, saved.device.id)
            Text(
                stringResource(R.string.settings_version, versionName, channel),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.settings_discard_title)) },
            text = { Text(stringResource(R.string.settings_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(stringResource(R.string.settings_discard)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                }) { Text(stringResource(R.string.settings_keep_editing)) }
            }
        )
    }
}

private val MQTT_FIELDS = setOf(Field.MQTT_ADDRESS, Field.MQTT_USERNAME, Field.MQTT_PASSWORD)

@Composable
private fun Header(dirty: Boolean, message: Int?, isError: Boolean, onSave: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Box(modifier = Modifier.weight(1f)) {
            message?.let {
                Text(
                    stringResource(it),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Button(onClick = onSave, enabled = dirty) { Text(stringResource(R.string.settings_save)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.settings_close)) }
    }
}

@Composable
private fun StatusLine(dashboardSetUp: Boolean, mqttStatus: MqttStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                if (dashboardSetUp) {
                    R.string.settings_status_dashboard_ok
                } else {
                    R.string.settings_status_dashboard_missing
                }
            ),
            color = if (dashboardSetUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleSmall
        )
        Text("·", style = MaterialTheme.typography.titleSmall)
        Text(mqttStatusText(mqttStatus), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ViewportLine(viewport: Viewport) {
    Text(
        stringResource(
            R.string.settings_status_viewport,
            viewport.cssWidth.toString(),
            viewport.cssHeight.toString(),
            viewport.densityDpi.toString(),
            String.format(java.util.Locale.ROOT, "%.2f", viewport.devicePixelRatio)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SectionCard(
    @StringRes title: Int,
    @StringRes tag: Int,
    section: Section?,
    sectionTop: MutableMap<Section, Int>,
    content: @Composable () -> Unit
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                if (section != null) sectionTop[section] = coordinates.positionInParent().y.roundToInt()
            }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(tag),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tag ==
                        R.string.settings_required
                    ) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            content()
        }
    }
}

@Composable
private fun IntegrationSection(
    draft: SettingsDraft,
    invalid: Set<Field>,
    hasPassword: Boolean,
    testState: ConnectionTestState,
    battery: BatteryOptimizationUi,
    onEnabledChange: (Boolean) -> Unit,
    onMqttChange: ((MqttSettingsForm) -> MqttSettingsForm) -> Unit,
    onTest: () -> Unit
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val mqtt = draft.mqtt
    SwitchField(R.string.settings_mqtt_enabled, R.string.settings_mqtt_enabled_help, mqtt.enabled, onEnabledChange)
    if (!mqtt.enabled) return

    SettingField(
        R.string.settings_mqtt_host,
        R.string.settings_mqtt_host_help,
        mqtt.address,
        Field.MQTT_ADDRESS in invalid,
        keyboardType = KeyboardType.Uri
    ) { v ->
        onMqttChange { it.copy(address = v) }
    }
    SettingField(
        R.string.settings_mqtt_username,
        R.string.settings_mqtt_username_help,
        mqtt.username,
        Field.MQTT_USERNAME in invalid
    ) { v -> onMqttChange { it.copy(username = v) } }
    SettingField(
        R.string.settings_mqtt_password,
        if (hasPassword) R.string.settings_mqtt_password_saved_help else R.string.settings_mqtt_password_help,
        mqtt.newPassword,
        Field.MQTT_PASSWORD in invalid,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
    ) { v -> onMqttChange { it.copy(newPassword = v) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(value = showPassword, role = Role.Checkbox, onValueChange = {
            showPassword = it
        })
    ) {
        Checkbox(checked = showPassword, onCheckedChange = null)
        Text(stringResource(R.string.settings_mqtt_show_password), modifier = Modifier.padding(start = 8.dp))
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onTest, enabled = testState != ConnectionTestState.Running) {
            Text(stringResource(R.string.settings_mqtt_test))
        }
        when (testState) {
            ConnectionTestState.Idle -> HelpText(R.string.settings_mqtt_test_help)

            ConnectionTestState.Running -> Text(stringResource(R.string.settings_mqtt_testing))

            is ConnectionTestState.Done -> when (val result = testState.result) {
                MqttTestResult.Success -> Text(
                    stringResource(R.string.settings_mqtt_test_ok),
                    color = MaterialTheme.colorScheme.primary
                )

                is MqttTestResult.Failure ->
                    Text(
                        stringResource(R.string.settings_mqtt_test_failed, result.reason),
                        color = MaterialTheme.colorScheme.error
                    )
            }
        }
    }

    // Shown only while Android restricts the app in the background (the only state that needs the user).
    if (battery.exempt) return
    Text(stringResource(R.string.settings_battery_title), style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings_battery_optimized), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = battery.onChange) { Text(stringResource(R.string.settings_battery_fix)) }
    }
    battery.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ActionsRow(viewModel: SettingsViewModel) {
    Text(stringResource(R.string.settings_section_actions), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::reloadDashboard) { Text(stringResource(R.string.settings_reload)) }
    }
}

@Composable
private fun DebugSection(tools: DebugTools, viewModel: SettingsViewModel, deviceId: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.debug_section_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = tools.openAndroidSettings) {
                    Text(stringResource(R.string.debug_open_android_settings))
                }
                Button(onClick = tools.openHomeAppPicker) { Text(stringResource(R.string.debug_open_home_picker)) }
                Button(onClick = tools.simulateRendererCrash) { Text(stringResource(R.string.debug_crash_renderer)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = tools.simulateRendererHang) { Text(stringResource(R.string.debug_hang_renderer)) }
                Button(onClick = tools.simulateAppCrash) { Text(stringResource(R.string.debug_crash_app)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::goToStartUrl) { Text(stringResource(R.string.settings_go_start)) }
                Button(onClick = viewModel::clearCache) { Text(stringResource(R.string.settings_clear_cache)) }
            }
            Text(
                stringResource(R.string.settings_device_id, deviceId.ifBlank { "…" }),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SettingField(
    @StringRes label: Int,
    @StringRes help: Int,
    value: String,
    isError: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = { Text(stringResource(help)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
    )
}

@Composable
private fun NumberField(
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
private fun SwitchField(@StringRes label: Int, @StringRes help: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
        ) {
            Text(stringResource(label), modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
        HelpText(help)
    }
}

@Composable
private fun HelpText(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun mqttStatusText(status: MqttStatus): String = when (status) {
    MqttStatus.Disabled -> stringResource(R.string.mqtt_status_disabled)

    MqttStatus.NotConfigured -> stringResource(R.string.mqtt_status_not_configured)

    MqttStatus.WaitingForNetwork -> stringResource(R.string.mqtt_status_waiting)

    MqttStatus.Connecting -> stringResource(R.string.mqtt_status_connecting)

    is MqttStatus.Connected -> stringResource(R.string.mqtt_status_connected)

    is MqttStatus.Failed -> {
        val seconds = ((status.retryAtMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        stringResource(R.string.mqtt_status_failed, status.message, seconds)
    }
}
