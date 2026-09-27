package io.github.andy_walker_idfa.smarthome_dashboard.di

import android.content.Context
import android.content.Intent
import io.github.andy_walker_idfa.smarthome_dashboard.BuildConfig
import io.github.andy_walker_idfa.smarthome_dashboard.MainActivity
import io.github.andy_walker_idfa.smarthome_dashboard.core.AppLog
import io.github.andy_walker_idfa.smarthome_dashboard.core.Clock
import io.github.andy_walker_idfa.smarthome_dashboard.core.LogReader
import io.github.andy_walker_idfa.smarthome_dashboard.core.SystemClockImpl
import io.github.andy_walker_idfa.smarthome_dashboard.core.timeChanges
import io.github.andy_walker_idfa.smarthome_dashboard.device.DeviceIdentity
import io.github.andy_walker_idfa.smarthome_dashboard.display.DisplayController
import io.github.andy_walker_idfa.smarthome_dashboard.distribution.Distribution
import io.github.andy_walker_idfa.smarthome_dashboard.distribution.FlavorDistribution
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.DevicePolicyKioskPolicy
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskController
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.KioskPolicy
import io.github.andy_walker_idfa.smarthome_dashboard.kiosk.RelockAlarm
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.AppControl
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.DeviceStateSource
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.Discovery
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.EntityCatalog
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.HiveMqConnection
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttCommandHandler
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttConnection
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.MqttManager
import io.github.andy_walker_idfa.smarthome_dashboard.mqtt.StaticInfo
import io.github.andy_walker_idfa.smarthome_dashboard.network.ConnectivityNetworkMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.network.ConnectivityWifiStatus
import io.github.andy_walker_idfa.smarthome_dashboard.network.NetworkMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.AppRestarter
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.ForegroundTracker
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryKind
import io.github.andy_walker_idfa.smarthome_dashboard.recovery.RecoveryStore
import io.github.andy_walker_idfa.smarthome_dashboard.security.KeystoreSecretStore
import io.github.andy_walker_idfa.smarthome_dashboard.security.SecretStore
import io.github.andy_walker_idfa.smarthome_dashboard.security.SettingsLock
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.AndroidSystemInfo
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.BroadcastBatteryMonitor
import io.github.andy_walker_idfa.smarthome_dashboard.sensors.SettingsSystemBrightness
import io.github.andy_walker_idfa.smarthome_dashboard.settings.DataStoreSettingsRepository
import io.github.andy_walker_idfa.smarthome_dashboard.settings.Settings
import io.github.andy_walker_idfa.smarthome_dashboard.settings.SettingsRepository
import io.github.andy_walker_idfa.smarthome_dashboard.web.DashboardStatusStore
import io.github.andy_walker_idfa.smarthome_dashboard.web.WebCommandBus
import java.time.ZoneId
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual dependency injection: the single owner of app-wide singletons, created once in
 * [io.github.andy_walker_idfa.smarthome_dashboard.DashboardApplication]. Consumers depend on the
 * interfaces, never on this class's internals, except at composition roots (activities, services,
 * receivers and view model factories).
 */
class AppContainer(context: Context, logReader: LogReader) {
    private val appContext = context.applicationContext

    /** Lives as long as the process. Used for work that must outlive any single screen. */
    val appScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e ->
                    AppLog.e("App", "Uncaught in app scope: ${e.javaClass.simpleName}")
                }
        )

    val clock: Clock = SystemClockImpl

    /** The log files, for the viewer in Settings. */
    val logReader: LogReader = logReader

    /** Wall-clock start of this process, published to HA as "App started". */
    val appStartedMillis: Long = clock.nowMillis()

    val settingsRepository: SettingsRepository =
        DataStoreSettingsRepository.create(appContext, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    /** Current settings for synchronous readers. Starts with defaults until the file is loaded. */
    val settingsState: StateFlow<Settings> = settingsRepository.settings.stateIn(
        appScope,
        SharingStarted.Eagerly,
        Settings()
    )

    val networkMonitor: NetworkMonitor = ConnectivityNetworkMonitor(appContext, appScope)

    /** Credentials storage (MQTT password, settings PIN). */
    val secretStore: SecretStore by lazy { KeystoreSecretStore(appContext) }

    /** Optional PIN for Settings (fails open; see [SettingsLock]). */
    val settingsLock: SettingsLock by lazy {
        SettingsLock(secretStore, settingsRepository, clock, Dispatchers.IO)
    }

    /** Optional kiosk lock (device owner + Lock Task Mode). */
    val kioskPolicy: KioskPolicy = DevicePolicyKioskPolicy(appContext)

    val kioskController =
        KioskController(appScope, settingsRepository, kioskPolicy, clock, RelockAlarm(appContext)) {
            // As device owner the app may bring the dashboard back from the background.
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

    /** Last automatic recovery (watchdogs, crash handler), published to HA as "Last recovery". */
    val recoveryStore = RecoveryStore(appContext, clock)

    val foregroundTracker = ForegroundTracker { it is MainActivity }

    val appRestarter = AppRestarter(appContext, recoveryStore, foregroundTracker, clock, MainActivity::class.java.name)

    /** "Restart app" from Home Assistant: say goodbye on MQTT, then restart (only while the app is visible). */
    private val appControl = AppControl {
        if (!appRestarter.canRestart) return@AppControl false
        appScope.launch {
            recoveryStore.record(RecoveryKind.REMOTE_RESTART, null)
            mqttManager.goodbye()
            withContext(Dispatchers.Main) { appRestarter.restartOnRequest() }
        }
        true
    }

    val webCommandBus = WebCommandBus()

    /** What the dashboard shows; written by the dashboard, read by the MQTT publisher. */
    val dashboardStatus = DashboardStatusStore()

    private val systemBrightness = SettingsSystemBrightness(appContext, appScope)

    /** Brightness and night mode; rendered by the dashboard activity, controlled by HA. */
    val displayController: DisplayController =
        DisplayController(
            scope = appScope,
            settingsRepository = settingsRepository,
            clock = clock,
            zone = { ZoneId.systemDefault() },
            systemBrightnessPercent = systemBrightness.percent,
            timeChanges = timeChanges(appContext)
        )

    /** Channel-specific capabilities, provided by the `github` or `store` flavor source set. */
    val distribution: Distribution = FlavorDistribution

    /** Creates MQTT client connections (the live session and Settings' "Test connection"). */
    val mqttConnectionFactory: () -> MqttConnection = { HiveMqConnection() }

    val mqttManager: MqttManager by lazy {
        val systemInfo = AndroidSystemInfo(appContext)
        val staticInfo = StaticInfo(
            appVersion = "${BuildConfig.VERSION_NAME} (${distribution.channel})",
            androidVersion = systemInfo.androidVersion,
            sdkInt = systemInfo.sdkInt,
            securityPatch = systemInfo.securityPatch,
            appStartedMillis = appStartedMillis
        )
        val stateSource = DeviceStateSource(
            battery = BroadcastBatteryMonitor(appContext, appScope),
            wifi = ConnectivityWifiStatus(appContext, appScope),
            dashboard = dashboardStatus,
            display = displayController,
            recovery = recoveryStore,
            kiosk = kioskController,
            settings = settingsState,
            systemInfo = systemInfo,
            staticInfo = staticInfo
        )
        MqttManager(
            settings = settingsState,
            secretStore = secretStore,
            networkMonitor = networkMonitor,
            connectionFactory = mqttConnectionFactory,
            stateSource = stateSource,
            commandHandler =
                MqttCommandHandler(webCommandBus, displayController, appControl, entityCatalog) { settingsState.value },
            deviceInfo = { name ->
                Discovery.DeviceInfo(
                    name,
                    systemInfo.manufacturer,
                    systemInfo.model,
                    BuildConfig.VERSION_NAME,
                    distribution.channel
                )
            },
            clock = clock,
            catalog = entityCatalog
        )
    }

    /** Release builds publish only the small entity set; debug builds everything (user principle 2026-09-27). */
    private val entityCatalog = EntityCatalog(full = BuildConfig.DEBUG)

    init {
        displayController.start()
        kioskController.start()
        appScope.launch {
            DeviceIdentity.ensureStored(settingsRepository) { DeviceIdentity.androidId(appContext) }
        }
    }
}
