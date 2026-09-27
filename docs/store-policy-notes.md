# Store policy notes

Source material for the Google Play Console declarations (permissions, foreground services, data
safety) and for F-Droid / IzzyOnDroid reviews. **Keep this file current.** Every permission,
foreground-service type or exported component gets an entry here in the same change that adds it.

Last reviewed: 2026-09-24 (versionCode 3, 0.3.0). Checked against the merged release manifests of both flavors.

## Distribution flavors
| Flavor | Channels | Difference |
|---|---|---|
| `github` | GitHub Releases (sideload) | Requests the battery-optimization exemption directly. Will contain the self-updater and `REQUEST_INSTALL_PACKAGES` (after v1.0). |
| `store` | F-Droid, IzzyOnDroid, Google Play | No updater code, no install permission. Battery optimization: opens the system settings list with instructions. |

## Permissions

### Current, both flavors
| Permission | Type | Justification |
|---|---|---|
| `INTERNET` | normal | The app's core function is displaying the user's own Home Assistant web dashboard and exchanging state and commands with the user's own MQTT broker. No other servers are contacted. |
| `ACCESS_NETWORK_STATE` | normal | Detects when the network drops and comes back, so the dashboard and the MQTT connection can recover automatically instead of staying broken. |
| `ACCESS_LOCAL_NETWORK` | runtime (Android 17+ at targetSdk 37) | Home Assistant and the MQTT broker run on the user's local network. Without this permission Android 17+ blocks LAN connections for the app and its WebView. Requested only on Android 17+. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | normal | The connection service (see "Foreground services") keeps the panel reachable by Home Assistant while the screen is off. It runs only while the user has enabled MQTT. |
| `POST_NOTIFICATIONS` | runtime (Android 13+) | Shows the mandatory, minimal and silent notification of the foreground service. Requested when the user switches MQTT on; the service works if it is denied. |
| `WAKE_LOCK` | normal | Only to switch the screen on for one second when the dashboard comes back to the front with the screen off (after an update, a restart or the end of a kiosk unlock). No background or CPU wake locks. |
| `RECEIVE_BOOT_COMPLETED` | normal | Restarts the connection service after a reboot (the dashboard itself starts as the Home app). Does nothing unless MQTT is enabled. |
| `ACCESS_WIFI_STATE` (`maxSdkVersion` 30) | normal | Reads the Wi-Fi signal strength on Android 10–11, reported to the user's Home Assistant as a diagnostic sensor. Newer versions read it without this permission. The Wi-Fi name is never read. |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | signature (added by AndroidX Core) | Internal permission that AndroidX uses to protect the app's own non-exported dynamic receivers. Not visible to users. |

### Current, `github` flavor only
| Permission | Type | Justification |
|---|---|---|
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | normal | A dedicated, permanently powered wall panel must keep its Home Assistant connection when the screen is off. Play restricts this permission, so the `store` flavor instead opens the battery-optimization settings list and explains what to do. |

### Planned
| Permission | Flavor | Phase | Justification (draft) |
|---|---|---|---|
| `REQUEST_INSTALL_PACKAGES` | **github only** | after v1.0 | Self-updater for sideloaded installs; verifies that the downloaded APK is signed with the same certificate before installing. |


Not planned (dropped by the user, see CLAUDE.md "Scope decisions"): `CAMERA`, `FOREGROUND_SERVICE_CAMERA`,
`RECORD_AUDIO`, location permissions (Wi-Fi name), and any listening network port.

## Foreground services
One service, `ConnectionService`, type **`specialUse`**, with
`<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Always-on smart-home wall panel: reports device state to and receives commands from the user's own Home Assistant via MQTT"/>`.

- **When it runs:** only while the user has enabled the MQTT connection. It stops itself when MQTT is disabled.
- **How it starts:** from the visible dashboard or settings screen, after `BOOT_COMPLETED`, and after
  `MY_PACKAGE_REPLACED`. Those are exemptions from the background-start restrictions, and `specialUse`
  is not among the types Android 15+ forbids from `BOOT_COMPLETED`. If Android refuses a start, the
  service stops cleanly instead of crashing.
- **Notification:** a minimal, silent, minimum-priority notification ("Connected to your Home Assistant").

Justification: the app is a dedicated, always-on wall panel. When the screen is dark at night or the
device dozes during a battery charge cycle, Home Assistant must still be able
to switch night mode, read its sensors (battery temperature for charging safety) and send commands. No other
foreground-service type fits:
- `dataSync` has a daily time limit on Android 15+ and can't start at boot.
- `connectedDevice` requires Bluetooth/USB/network-change permissions and is meant for peripherals, not an MQTT broker.
- `systemExempted` requires device owner or similar and so is unavailable to the store build.

Play requires a specialUse declaration with a description and a short video.

## Exported components
| Component | Exported | Why |
|---|---|---|
| `MainActivity` | yes | LAUNCHER and HOME activity (the app can be the device's Home app). |
| `SettingsActivity` | no | Opened only from inside the app. |
| `ConnectionService` | no | Started only by the app itself. |
| `BootReceiver` (`BOOT_COMPLETED`) | no | System broadcast, delivered by the system. |
| `AppUpdateReceiver` (`MY_PACKAGE_REPLACED`) | no | Broadcast is delivered explicitly to the app. |
| `androidx.startup.InitializationProvider` | no | AndroidX library initialisation. |
| `androidx.profileinstaller.ProfileInstallReceiver` | yes, protected by `android.permission.DUMP` | AndroidX baseline-profile installer; only the system/adb can call it. |
| `KioskAdminReceiver` (device admin) | yes, protected by `android.permission.BIND_DEVICE_ADMIN` | Needed only for the optional kiosk lock. It declares **no policies**; the app becomes device owner only if the user runs `adb shell dpm set-device-owner` themselves. As device owner it only allowlists itself for Lock Task Mode (power menu kept) and sets no user restrictions. Play: device-admin apps need a policy declaration; the kiosk lock is optional and never requested at runtime. |
| `PinResetReceiver` | yes, protected by `android.permission.DUMP` | Clears a forgotten settings PIN. `DUMP` is signature/privileged/development: the adb shell holds it, and another app could only get it if the user grants it with `pm grant` over adb. Same pattern as the AndroidX receiver above. |
| `RestartActivity` (`:restart` process) | no | Restart trampoline after a crash, freeze or "Restart app"; started only by the app itself. |

## Data safety (Play) draft
- Data collected by the developer: **none**. Data shared with third parties: **none**.
- The app sends device state (battery, Wi-Fi signal, IP address, system info, the current dashboard URL
  without login codes, time of the last touch) **only to the MQTT broker the user configures**, i.e. the
  user's own Home Assistant. That's user-directed transfer to the user's own server, not collection.
- No analytics, advertising, crash reporting or third-party SDKs. See `PRIVACY.md`.

## Target SDK
targetSdk 37 (Android 17). It must be raised to the current stable level every year (Play's
target-API requirement). Review behaviour changes (foreground services, background starts,
local-network access) each time.
