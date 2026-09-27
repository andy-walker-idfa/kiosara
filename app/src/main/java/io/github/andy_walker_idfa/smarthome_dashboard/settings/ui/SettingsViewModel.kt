package io.github.andy_walker_idfa.smarthome_dashboard.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.andy_walker_idfa.smarthome_dashboard.R
import io.github.andy_walker_idfa.smarthome_dashboard.di.AppContainer
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatus
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayStatusSource
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatus
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskStatusSource
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttConnectionTester
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttManager
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttStatus
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttTestResult
import io.github.andy_walker_idfa.smarthome_dashboard.security.SecretStore
import io.github.andy_walker_idfa.smarthome_dashboard.security.SettingsLock
import io.github.andy_walker_idfa.smarthome_dashboard.settings.MqttSettings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import io.github.andy_walker_idfa.smarthome_dashboard.settings.UrlValidator
import io.github.andy_walker_idfa.smarthome_dashboard.settings.WebSettings
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardStatusSource
import io.github.andy_walker_idfa.smarthome_dashboard.web.Viewport
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommand
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the "Test connection" button. */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState

    data object Running : ConnectionTestState

    data class Done(val result: MqttTestResult) : ConnectionTestState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val commandBus: WebCommandBus,
    private val secretStore: SecretStore,
    private val mqttManager: MqttManager,
    private val connectionTester: MqttConnectionTester,
    private val settingsLock: SettingsLock,
    kioskStatus: KioskStatusSource,
    dashboardStatus: DashboardStatusSource,
    displayStatus: DisplayStatusSource
) : ViewModel() {
    /** Current brightness and night state (shown in the Screen section). */
    val display: StateFlow<DisplayStatus> = displayStatus.status

    /** Null until loaded. */
    val settings: StateFlow<Settings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val mqttStatus: StateFlow<MqttStatus> = mqttManager.status

    /** Dashboard WebView size as last laid out (shown in the status area). */
    val viewport: StateFlow<Viewport?> =
        dashboardStatus.status.map {
            it.viewport
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Kiosk lock state (device owner, switch, unlock window). */
    val kiosk: StateFlow<KioskStatus> = kioskStatus.status

    private val pinChanging = MutableStateFlow(false)

    /** True while a PIN is being set or removed (hashing takes about half a second). */
    val pinBusy: StateFlow<Boolean> = pinChanging.asStateFlow()

    private val pinMessage = MutableStateFlow<Int?>(null)

    /** Result of the last PIN change, as a string resource, or null. */
    val pinResult: StateFlow<Int?> = pinMessage.asStateFlow()

    fun setPin(pin: String) = changePin {
        pinMessage.value = if (settingsLock.setPin(pin)) R.string.pin_saved else R.string.pin_save_failed
    }

    fun removePin() = changePin {
        settingsLock.clearPin()
        pinMessage.value = R.string.pin_removed
    }

    private fun changePin(action: suspend () -> Unit) {
        if (pinChanging.value) return
        pinChanging.value = true
        viewModelScope.launch {
            try {
                action()
            } finally {
                pinChanging.value = false
            }
        }
    }

    private val passwordStored = MutableStateFlow(false)
    val hasMqttPassword: StateFlow<Boolean> = passwordStored.asStateFlow()

    private val testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTest: StateFlow<ConnectionTestState> = testState.asStateFlow()
    private var testJob: Job? = null

    init {
        viewModelScope.launch { refreshPasswordState() }
    }

    /**
     * Saves the whole draft: the password (if a new one was typed) before the settings, then reconnects
     * MQTT. [onSaved] receives the saved settings, e.g. to start the connection service.
     */
    fun saveAll(valid: SettingsDraft.Result.Valid, onSaved: (Settings) -> Unit) {
        viewModelScope.launch {
            valid.newPassword?.let { password ->
                withContext(Dispatchers.IO) { secretStore.put(MqttSettings.PASSWORD_SECRET_KEY, password) }
                refreshPasswordState()
            }
            // Merge into the latest stored settings: the pinned certificate is set from the error screen and is
            // not part of the draft. It stays valid only while the start URL's host is unchanged.
            settingsRepository.update { current ->
                val sameHost =
                    UrlValidator.hostOf(valid.settings.web.startUrl) == UrlValidator.hostOf(current.web.startUrl)
                current.copy(
                    mqtt = valid.settings.mqtt,
                    screen = valid.settings.screen,
                    web = valid.settings.web.copy(
                        trustedCertSha256 = if (sameHost) current.web.trustedCertSha256 else ""
                    )
                )
            }
            mqttManager.reconnectNow()
            onSaved(settingsRepository.settings.first())
        }
    }

    /** Tests [mqtt] as entered (unsaved). Uses [newPassword] if typed, otherwise the stored password. */
    fun testConnection(mqtt: MqttSettings, newPassword: String?) {
        testJob?.cancel()
        testState.value = ConnectionTestState.Running
        testJob =
            viewModelScope.launch {
                val password =
                    newPassword ?: withContext(Dispatchers.IO) { secretStore.get(MqttSettings.PASSWORD_SECRET_KEY) }
                val deviceId = settingsRepository.settings.first().device.id
                testState.value = ConnectionTestState.Done(connectionTester.test(mqtt, deviceId, password))
            }
    }

    fun clearConnectionTest() {
        testJob?.cancel()
        testState.value = ConnectionTestState.Idle
    }

    fun forgetPinnedCertificate() = updateWeb { it.copy(trustedCertSha256 = "") }

    fun reloadDashboard() = commandBus.send(WebCommand.ReloadPage)

    fun goToStartUrl() = commandBus.send(WebCommand.LoadStartUrl)

    fun clearCache() = commandBus.send(WebCommand.ClearCache)

    fun simulateRendererCrash() = commandBus.send(WebCommand.SimulateRendererCrash)

    fun simulateRendererHang() = commandBus.send(WebCommand.SimulateRendererHang)

    private suspend fun refreshPasswordState() {
        passwordStored.value =
            withContext(Dispatchers.IO) { secretStore.get(MqttSettings.PASSWORD_SECRET_KEY) != null }
    }

    private fun updateWeb(transform: (WebSettings) -> WebSettings) {
        viewModelScope.launch { settingsRepository.update { it.copy(web = transform(it.web)) } }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    commandBus = container.webCommandBus,
                    secretStore = container.secretStore,
                    mqttManager = container.mqttManager,
                    connectionTester = MqttConnectionTester(container.mqttConnectionFactory),
                    settingsLock = container.settingsLock,
                    kioskStatus = container.kioskController,
                    dashboardStatus = container.dashboardStatus,
                    displayStatus = container.displayController
                )
            }
        }
    }
}
