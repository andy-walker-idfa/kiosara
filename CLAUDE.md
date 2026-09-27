# Kiosara: project memory

Kiosara (project and repo formerly `smarthome-dashboard`) is an open-source (Apache 2.0) Android kiosk app for a
wall-mounted tablet that shows a Home Assistant dashboard. Reference device: Lenovo Tab M9 (TB310FU). This is a clean-room implementation:
never copy code, assets, texts or branding from Fully Kiosk or other proprietary products.
The full requirements spec was given in the first session. Section numbers (§) below refer to it.

## Identity
- **applicationId = namespace = Kotlin root package: `io.github.andy_walker_idfa.smarthome_dashboard`.**
  It is permanent and never depends on the brand. (It replaced `andy.wallpanel` in Phase 1.5.)
- **Display name: "Kiosara"** (since 0.9.0; "Wall Panel" was the working name). It is defined in `app_name`
  (`res/values`, `res/values-cs`) and `fastlane/metadata/android/*/title.txt`; docs, blueprint display names and the
  MQTT discovery `origin` name use it too. Internal code names are brand-neutral: `DashboardApplication`,
  `AppTheme`, log prefix `SHDash/`, the MQTT protocol prefix `shdash`, and the HA device name **"Wall panel"**
  (a description, not the brand; kept so existing entity IDs like `sensor.wall_panel_battery` never change).
- License: Apache 2.0 (`LICENSE`). minSdk 29, compileSdk/targetSdk 37 (Android 17).
- **minSdk 29 (Android 10) is decided (2026-09-25); don't lower it without the user.** Reasons:
  - Chrome/WebView on Android 8-9 no longer gets updates, so the HA frontend would eventually break there.
  - Those Android versions get no security updates.
  - Extra compatibility and test effort.

  Lowering it later is possible if beta or community demand justifies it.
- **Personal and environment details** (the maintainer's HA URL, IPs, entity ids, device serial, local paths,
  household context) live only in the git-ignored **`CLAUDE.local.md`** (loaded automatically by Claude Code).
  **Never put them in tracked files**: the repository is public.
- Charging thresholds for the HA blueprint: on below 40 %, off above 80 %.
- MQTT credentials are entered in the app's settings (→ `SecretStore`), never in code.
- The charging blueprint has **no default plug** (user decision); the user selects it when creating the automation.

## Reference device (Lenovo TB310FU)
- Lenovo Tab M9 (TB310FU), **Android 13 / API 33**, arm64-v8a, ~3.9 GB RAM. Exact build, patch level and the
  maintainer's device setup are in `CLAUDE.local.md`.
- Panel 800x1340 (natural orientation is **portrait**), density 200 (≈ 640 dp smallest width).
- **Dashboard CSS viewport (measured 2026-09-25): 1072 × 640 CSS px** in landscape (1340 × 800 px, device pixel ratio
  1.25); no system bars are visible in immersive mode (window frame 0,0-1340,800). Visible bars would take 24 CSS px
  (status bar, 30 px) + 64 CSS px (Lenovo taskbar, 80 px) → 1072 × 552, which is what Android's configuration reports
  (`h552dp`).
- Sensors: accelerometer, light, significant motion. **No proximity sensor**. (The app uses none of them; see
  "Scope decisions".)
- WebView: Android System WebView updates only through the Play Store, which needs a Google account on the device
  (or a sideloaded Google-signed WebView APK). A factory reset rolls WebView back to the factory version (126).
- Device-specific: the Lenovo taskbar is visible on non-immersive screens (e.g. Settings), which use
  safe-drawing insets (a generic mechanism, not Lenovo-specific code).

## Toolchain (verified 2026-09-24)
| Tool | Version | Where |
|---|---|---|
| JDK | Temurin 21.0.12 | `JAVA_HOME` (local path in `CLAUDE.local.md`) |
| Android SDK | n/a | `ANDROID_HOME` (local path in `CLAUDE.local.md`) |
| cmdline-tools | latest (15859902) | `%ANDROID_HOME%\cmdline-tools\latest\bin` (on user PATH) |
| platform-tools | 37.0.1 | `%ANDROID_HOME%\platform-tools` (on user PATH) |
| Platform | android-37.0 | |
| Gradle | 9.7.1 via wrapper (sha256-pinned) | no global install |
| AGP | 9.4.1 | Kotlin support is built in, so there is no `kotlin-android` plugin |
| Kotlin | 2.4.20 | Pulled in by the compose/serialization plugins and overrides AGP's bundled KGP 2.2.10 |
| Compose BOM | 2026.09.00 | |
| Android Studio | 2025.3 | optional; use only for layout or debugging when needed |

Notes:
- `sdkmanager` is deprecated in favour of the `android sdk` CLI (same folder). It still works.
- Check that AGP, Gradle and Kotlin are compatible before bumping any of them
  (https://developer.android.com/build/releases/gradle-plugin).
- `gradlew` is marked executable in the git index (`git update-index --chmod=+x`), because some checkouts (e.g.
  exFAT) have no exec bit.
- The GitHub CLI `gh` is used for GitHub operations (releases, repo settings).
- In Git Bash, prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`.

## Build & deploy
See the `build-and-deploy` and `device-provisioning` skills in `.claude/skills/` for details.
- **The reference tablet is wall-mounted; adb runs over Wi-Fi** (Wireless debugging, paired; address in
  `CLAUDE.local.md`).
  Connect with `.\scripts\adb-wifi.ps1` (finds the random port via mDNS). Android turns wireless debugging **off at
  every reboot**; re-enable it on the tablet (Quick Settings tile) and run the script again. Details and recovery
  steps: build-and-deploy skill.
```powershell
.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug  # gate
.\gradlew.bat connectedGithubDebugAndroidTest           # on-device tests (tablet connected)
.\gradlew.bat assembleGithubRelease                     # the tablet build (signed, ~2 MB, R8)
adb install -r app\build\outputs\apk\github\release\app-github-release.apk
adb shell am start -n io.github.andy_walker_idfa.smarthome_dashboard/.MainActivity
```
- `local.properties` (git-ignored) holds `sdk.dir`, so builds work even in shells without `ANDROID_HOME`.
- **CI:** `.github/workflows/ci.yml` (ubuntu-24.04, Temurin 21, actions pinned to commit SHAs) runs the gate for
  both flavors and builds **unsigned** release APKs (artifact with `SHA256SUMS`). **No secrets in CI, ever.**
- **Release signing:** the release keystore (alias `smarthome_dashboard`, PKCS12, RSA 4096) is outside the repo;
  its path is in `CLAUDE.local.md`. The password (entered by the user) is only in the git-ignored
  `keystore.properties`. Signing certificate (public information, verified with keytool and apksigner on 2026-09-24):
  - SHA-256: `22:43:24:3E:9A:54:83:91:22:A5:D8:B6:0D:A9:ED:0B:4E:66:D3:C8:CE:84:3C:CE:F9:7B:CB:59:E1:90:90:F0`
  - SHA-1: `F8:1B:3F:61:93:06:EF:1C:F6:89:37:C5:52:FB:D0:25:86:10:27:9B`
  - Owner `CN=Andy Walker` (no O/OU); SHA384withRSA; valid 2026-09-24 → 2126-09-25 (100 years).
  - The first key (`wallpanel-release.jks`) was never used for a release and has been deleted.
  Self-update verification (after v1.0) and any store registration pin the SHA-256. The maintainer keeps the
  keystore and password backed up. Claude never copies the key anywhere.
- **The tablet runs `githubRelease`.** Debug builds use `applicationIdSuffix = ".debug"`, so they install next to it.
  On-device tests never touch the release install or its HA login. Debug builds also get their own device id,
  because ANDROID_ID is per signing key.

## Versioning
- `versionCode` and `versionName` are **literals** in `app/build.gradle.kts`, which suits F-Droid's update checker.
- `versionCode`: +1 per release and never reused. **Both flavors of a release share the same versionCode and
  versionName.**
- `versionName`: semver, 0.x until v1.0. Every phase end is a release.
- History: 0.1.0 = 1 (Phase 1), 0.2.0 = 2 (Phase 1.5), 0.3.0 = 3 (Phase 2), 0.3.1 = 4 (settings UX), 0.3.2 = 5 (viewport sensor), 0.4.0 = 6 (Phase 3), 0.4.1 = 7 (app-side auto brightness removed), 0.5.0 = 8 (Phase 4a), 0.6.0 = 9 (Phase 4b), 0.7.0 = 10 (Phase 6 + soak-test instrumentation), 0.7.1 = 11 (screen wakes when the dashboard returns),
  0.7.2 = 12 (release entity set reduced to six), 0.8.0 = 13 (settings reduced; feature freeze),
  0.8.1 = 14 (Wake screen button and wake-on-motion blueprint removed; five release entities),
  **0.9.0 = 15 (security fixes, rename to Kiosara; first public release, beta)**.
- `CHANGELOG.md` (Keep a Changelog) and `fastlane/metadata/android/{en-US,cs-CZ}/changelogs/<versionCode>.txt`
  (≤ 500 characters) are updated together.

## Architecture
Single `:app` module. Feature packages communicate only through small interfaces, so a Gradle
module split later stays mechanical. Manual constructor injection: `di/AppContainer` is created once
in `DashboardApplication` and is the only place that knows concrete classes.

| Package | Public surface | Notes |
|---|---|---|
| `core` | `Clock`, `AppLog` (`LogSink`), `FileLogSink` (`LogReader`), `LogRedactor`, `Backoff` | `AppLog` tags are `SHDash/<Tag>`; see "Log files (Phase 6)". |
| `settings` | `SettingsRepository`, `Settings` (+`DeviceSettings`, `WebSettings`, `MqttSettings`, `ScreenSettings`, `SecuritySettings`), `UrlValidator` | Typed DataStore + kotlinx JSON, `schemaVersion` = 1, unknown keys ignored (keys of settings removed in 0.8.0 are simply dropped), corrupt file → defaults. Default `startUrl` is **empty** ("Not set up" screen). |
| `settings.ui` | `SettingsActivity`, `SettingsDraft` | See "Settings screen". PIN protection in Phase 4a; wizard, import/export in beta preparation. |
| `device` | `DeviceIdentity` | `Settings.device.id` = the first 32 hex characters of SHA-256(`"shdash-device-id-v1:" + ANDROID_ID`), or a random UUID if ANDROID_ID is unavailable. Created once on start. |
| `distribution` | `Distribution` | Implemented by `FlavorDistribution` in `src/github` / `src/store`. Holds all channel-specific behaviour. |
| `security` | `SecretStore`, `SettingsLock`, `PinHasher`, `PinResetReceiver` | `KeystoreSecretStore`: AES-256-GCM Android Keystore key with **no user authentication** (there is no lock screen). Each value is bound to its entry name (GCM AAD). Any decryption failure → entry deleted, `null` returned ("missing, ask again"). |
| `network` | `NetworkMonitor` | "Connected" = default network has INTERNET; VALIDATED is **not** required (HA is on the LAN). Seeded synchronously. |
| `web` | `WebCommandBus` (+`WebCommand`), `DashboardController`, `WebViewHost` | See below. |
| `kiosk` | `KioskWindow`, `CornerTapDetector`, `PinPrompt`, `AppUpdateReceiver`, `KioskController` (`KioskStatusSource`), `KioskPolicy` (`DevicePolicyKioskPolicy`), `KioskAdminReceiver` | See "Kiosk lock (Phase 4b)". | `AppUpdateReceiver` also starts the connection service. |
| `recovery` | `RecoveryStore` (`RecoverySink`/`RecoverySource`), `AppRestarter`, `RestartActivity`, `CrashHandler`, `MainThreadWatchdog`, `ExitReasons`, `CrashLoopGuard`, `ForegroundTracker` | See "Recovery & settings PIN (Phase 4a)". |
| `display` | `DisplayController` (`DisplayStatusSource`, `DisplayCommands`), `DisplayPolicy`, `NightSchedule`, `NightOverlay` | See "Screen & display". |
| `sensors` | `BatteryMonitor`, `SystemInfo`, `SystemBrightness` | Battery from the sticky ACTION_BATTERY_CHANGED broadcast; pure `BatteryState.parse`. |
| `network` (+) | `WifiStatus` | RSSI (NetworkCapabilities on 31+, WifiManager on 29-30 via `ACCESS_WIFI_STATE` maxSdk 30) and IPv4. No SSID. |
| `mqtt` | `MqttManager`, `MqttConnection` (`HiveMqConnection`), `MqttNaming`, `Entities`, `Discovery`, `EntityStates`, `StateDiffer`, `MqttCommandHandler`, `RemoteUrlPolicy`, `DeviceStateSource` | See "MQTT (Phase 2)". |
| `service` | `ConnectionService`, `BootReceiver` | specialUse FGS; runs only while MQTT is enabled. |

**Dashboard flow:**
- `DashboardController` lives in `DashboardViewModel` and never touches the WebView. It publishes
  `DashboardState` (the settings, `PageState`, and a queue of `WebRequest`s).
- `MainActivity` renders the state while STARTED: it applies the settings, executes the queued requests
  on `WebViewHost`, acknowledges them, and shows the Compose overlay (`ErrorOverlay` or `SetupOverlay`).
- Requests are state, not events, so commands sent while Settings is open are not lost.
- Other features send `WebCommand`s through the buffered `WebCommandBus` (Settings and MQTT).

**Page-state rules** (all covered by `DashboardControllerTest`):
- No start URL → `NotConfigured`: the "Not set up yet" screen with an **Open settings** button, and nothing loads.
  There is deliberately **no adb or intent hook** for changing settings. The release build must never let
  another app change the start URL or settings.
- Main-frame network error, HTTP ≥ 500, or an untrusted certificate → error overlay, then retry with
  backoff 2 s → 60 s. Retries pause while offline and fire as soon as the network returns.
- Reconnect: reload if the page is in error, **or it last loaded while offline**, or the outage lasted
  ≥ 30 s. Shorter blips are left to HA's own websocket reconnect.
  - Why the offline case: HA's service worker serves a cached shell when offline, so WebView reports
    success but the page shows "Unable to fetch auth providers" and never recovers. Seen on the device.
- Renderer gone: `WebViewHost` destroys the dead WebView and creates a new one; the controller reloads
  the current URL. More than 3 deaths in 5 min counts as a crash loop: error overlay and backoff.
- When night starts (`onNightChanged` false → true; the first value after start only records the state), the
  start URL is loaded. There is no periodic reload and no idle return (removed in 0.8.0; the watchdog handles
  frozen pages).
- Self-signed TLS: accepted only when the host equals the **start URL's host** **and** the certificate's
  SHA-256 equals `trustedCertSha256`. The user pins it from the error screen; a new start URL host drops the pin.
  Untested on the device, because HA uses plain http.
- Fixed WebView behaviour (no settings since 0.8.0): sensorLandscape, the page's own scale, the WebView's default
  user agent, media autoplay allowed; only `textZoomPercent` is configurable.

**Manifest decisions:**
- `networkSecurityConfig` allows cleartext in the base config, because HA runs on `http://` and the host is user-set.
- `allowBackup=false` + `dataExtractionRules` + `fullBackupContent` exclude everything, including device-to-device transfer.
- `MainActivity` is `singleTask`, both LAUNCHER and HOME, and handles all `configChanges` so the WebView is never recreated.
- `ACCESS_LOCAL_NETWORK` is declared and requested at runtime on API 37+ only. It covers **all** sockets,
  MQTT included.
- Back is handled with `OnBackPressedDispatcher` and never leaves the app.

## MQTT (Phase 2)
- **Library:** HiveMQ MQTT Client 1.4.0 (MQTT 3.1.1, `Mqtt3AsyncClient` + `kotlinx.coroutines.future.await`).
  Its automatic reconnect is **off**: `MqttManager` owns the retry policy (`Backoff` 1 s → 120 s), paused while
  `NetworkMonitor` is offline, and `reconnectNow()` is called after a settings save.
  - R8 rules are in `app/proguard-rules.pro`: HiveMQ's keep rules plus R8's generated `-dontwarn` list for the
    optional Netty integrations. **Regenerate that list when HiveMQ or Netty versions change.**
  - `META-INF/INDEX.LIST` and `io.netty.versions.properties` are excluded from packaging.
  - Licenses: licensee allows `Apache-2.0` and `MIT-0` (reactive-streams).
  - APK: 2.2 MB → 3.0 MB.
- **Session:**
  - clean session, keep-alive 30 s, LWT `offline` retained on `<base>/availability`;
  - on connect: subscribe → discovery → `online` → all states;
  - HA birth `online` → re-publish after a random 1-5 s;
  - graceful stop publishes `offline` in a non-cancellable block.
- **Publishing:** retained, QoS 1, on change (500 ms debounce) with deadbands, plus a full refresh every 5 minutes
  (fixed; not a setting since 0.3.1). Details and the entity table are in the `ha-mqtt-discovery` skill.
- **Commands:** `<base>/set/<object_id>`. **Retained command messages are ignored.** `load_url` (debug builds
  only) goes through `RemoteUrlPolicy`: explicit http(s) only, the start URL's host only. Never written to
  settings. Command names are logged, payloads are not.
- **Fixed naming (0.8.0):** device name "Wall panel" (`DeviceSettings.DEFAULT_NAME`; users rename it in HA),
  discovery prefix `homeassistant`, status topic `homeassistant/status`, base topic `shdash/<short_id>`.
- **Page status:** `DashboardController` writes to the app-scoped `DashboardStatusStore` (`DashboardStatusSink`);
  the publisher reads `DashboardStatusSource`. So the service reports correctly without the activity.
- **Service lifecycle:** `ConnectionService.startIfEnabled()` is called from `MainActivity` (STARTED),
  `SettingsActivity` (after save), `BootReceiver`, and `AppUpdateReceiver`. The service stops itself when MQTT is
  disabled (it watches the DataStore flow, not the pre-seeded `settingsState`). `startForeground` failures →
  `stopSelf()`. START_STICKY.
- **Password:** `SecretStore` key `mqtt_password` (Keystore alias `shdash_secrets_v1`). Blank field in Settings = keep; "Show password" checkbox; a save with a new password shows an explicit confirmation. Broker CONNACK errors are mapped to readable English messages (`MqttRejectedException`).
- **Verified on the TB310FU (2026-09-24):**
  - HiveMQ works after R8.
  - Reached the user's Mosquitto; without credentials got `NOT_AUTHORIZED` and backed off 1 → 2 → 4 → 8 s.
  - The FGS is foreground with its notification.
  - The POST_NOTIFICATIONS prompt appears when MQTT is switched on.
  - With the user's credentials: connected. After a reboot the service started and reconnected within 0.5 s;
    `BootReceiver` (`exported=false`) receives BOOT_COMPLETED. After `adb install -r` the service restarts
    via MY_PACKAGE_REPLACED.
  - **Device quirk (TB310FU, Android 13):** the sticky ACTION_BATTERY_CHANGED replay is denied to a
    RECEIVER_NOT_EXPORTED receiver ("Exported Denial … from null (uid=-1)"); live broadcasts arrive. `BatteryMonitor`
    therefore takes the initial state from `registerReceiver`'s return value (generic fix, works everywhere).
  - Log names: R8 obfuscates class names in release, so logs use explicit names (`WebCommand.logName`), never
    `javaClass.simpleName` of app classes.

## Settings screen (0.8.0: reduced to what a normal user sets up)
- Order: **Dashboard** (required: HA URL; text size; the pinned certificate with "Forget" only while one is
  pinned) → **Home Assistant integration (MQTT)** (optional; on/off, broker address `host[:port]` (port 1883 when
  omitted, shown only when different), username, password required only while switched on; Test connection;
  the battery-optimization notice **only while restricted**) → **Screen** (brightness Follow Android / Fixed %,
  night mode on/off, from, until) → **Settings protection** (PIN) → **Kiosk lock** → Reload dashboard → Logs →
  version. Debug builds add a debug card (go to start page, clear web cache, device ID, crash/hang tools).
- **Do not add settings** (feature freeze, user principle). Everything else is a fixed default (see "Scope
  decisions" → 0.8.0).
- Required fields have `*` in the label and start their helper text with "Required". **Every field has a one-line
  helper text in EN and CS.**
- One Save in the fixed header saves the whole `SettingsDraft` (pure, unit-tested validation + dirty tracking);
  invalid → highlight and scroll to the first invalid section. Close/Back with unsaved changes asks "Discard
  changes?".
- "Test connection" (`MqttConnectionTester`) uses the unsaved values, its own client id (`<node>_test`) and no LWT,
  so it neither kicks the live session nor marks the panel offline; failures are mapped to precise English reasons.

## Device identity (for MQTT / HA)
- All HA identifiers come **only** from `Settings.device.id`, **never** from the package or display name:
  `short_id` = the first 12 characters, device identifier `shdash_<short_id>`, unique_ids
  `shdash_<short_id>_<object_id>`, base topic `shdash/<short_id>`. See the `ha-mqtt-discovery` skill.
- ANDROID_ID is scoped per signing key, user and device, so the id **survives reinstalls** with the same key.
  It changes only after a factory reset or with a different signing key (e.g. a debug build or F-Droid's own
  signature), and HA then sees a new device.
- Beta preparation adds "reset/edit device ID". Settings export never carries the id, so it is not duplicated on another tablet.

## Relaunch after update (verified on TB310FU, Android 13, 2026-09-24)
| Scenario | Result |
|---|---|
| `MY_PACKAGE_REPLACED` receiver, app **not** the Home app | Receiver runs; `startActivity` **blocked** ("Abort background activity starts"). |
| Same, app **is** the Home role holder (dashboard in front during the update) | **Blocked** as well. Afterwards the system resumed the next task in its stack (the stock launcher), not Home. |
| Same, with "Display over other apps" (`SYSTEM_ALERT_WINDOW`) granted via `appops` | **Allowed** ("allowed because SYSTEM_ALERT_WINDOW permission is granted"); the dashboard came back. |
| Reboot with app as Home role holder | Works. |
| `adb install -r` with the app **device owner** (lock off), 2026-09-26 | **Works**: the dashboard was in front 6 s later (before device owner, the stock launcher came up). |
| Same on the Android 13 emulator with the lock **on** | Works: back and locked at once. |
| `adb install -r` 0.7.0 on the **tablet**, device owner, lock on (2026-09-27) | Back and **locked** within 2 s, but the **screen stayed off** (Android's timeout had turned it off while Android Settings was open during an unlock). `setTurnScreenOn`/manifest `turnScreenOn` did **not** wake it; fixed in 0.7.1 with `KioskWindow.wakeScreenIfOff` (1 s `SCREEN_BRIGHT_WAKE_LOCK` + `ACQUIRE_CAUSES_WAKEUP` in `MainActivity.onResume` when not interactive): verified screen off → install → awake + locked within 2 s. |

Current state: the receiver is kept (harmless, and it works once an exemption applies). The `SYSTEM_ALERT_WINDOW`
fallback is **not** in the manifest. Not needed while updates go through adb (user decision); revisit together with
the self-updater after v1.0. Home role over adb:
`adb shell cmd role add-role-holder android.app.role.HOME io.github.andy_walker_idfa.smarthome_dashboard`.

## Publication readiness (rules)
The repository is **public** (https://github.com/andy-walker-idfa/kiosara) since v0.9.0 (beta). Don't create store
accounts, announcements or releases until the user says so.
- **Flavors** (`distribution` dimension, same applicationId, same versionCode per release):
  - `github` (default; the tablet build): the self-updater (after v1.0), `REQUEST_INSTALL_PACKAGES`, and the direct
    battery-optimization exemption request.
  - `store` (F-Droid / IzzyOnDroid / Google Play): no updater code and no install permission. Battery
    optimization opens the system settings list with instructions.
  - Flavor code lives **only** in `src/github` / `src/store` behind the `Distribution` interface, never behind
    runtime flags.
- **Store policy:**
  - Declare foreground-service types precisely and use the minimum: one `specialUse` service (MQTT/HA).
  - Every permission, FGS type and exported component is justified per flavor in `docs/store-policy-notes.md`.
    Update it in the same change that adds one.
- **targetSdk** must be raised to the current stable level **every year**.
- **Privacy:** `PRIVACY.md`. No data collection, analytics, crash reporting or third-party services. The app talks
  only to the user's own HA and MQTT broker. No camera, microphone, local API or screenshots (dropped; see "Scope decisions").
- **F-Droid / IzzyOnDroid:**
  - Permissive dependencies only (licensee). No Google Play Services, no Firebase, no binary blobs
    (only the standard `gradle-wrapper.jar`).
  - Reproducible builds:
    - pinned versions;
    - `dependenciesInfo { includeInApk = false }`;
    - release `vcsInfo.include = false`;
    - no timestamps or machine-specific values;
    - LF line endings (`.gitattributes`);
    - `packaging.jniLibs.keepDebugSymbols += "**/*.so"`: prebuilt native libraries are never stripped, because
      stripping depends on whether the machine has an NDK. The first CI run differed only in DataStore's `.so`.
  - Verified 2026-09-24: two clean local release builds are byte-identical, and the APK signing block holds only
    the v2 signature and padding. Baseline profiles (`assets/dexopt/baseline.prof*`) are included; compare them
    against the Linux CI build, and disable the ART-profile tasks only if they differ.
  - Fastlane metadata: `fastlane/metadata/android/{en-US,cs-CZ}/` (title, short and full description,
    changelogs). Screenshots come later (see `fastlane/README.md`).
- **Naming and trademark:** never use the Home Assistant logo or imply the app is official. "for Home Assistant"
  is fine. README and store descriptions carry "Not affiliated with or endorsed by Home Assistant or the Open
  Home Foundation". The launcher icon is a neutral green panel-with-tiles, not a house.
- **Portability:** no Lenovo- or Tab M9-specific behaviour without a fallback; isolate and document any
  device-specific handling. QA matrix: the real tablet plus tablet AVDs at API 29 and API 37 (see the
  qa-engineer agent).

## Scope decisions (user, 2026-09-25) — binding
**Guiding principle (user, 2026-09-27, overrides everything below where they conflict): this is a home appliance,
not business-critical software.** If the panel is unavailable for a while, the user will notice.
- Every setting and every HA entity must serve one of: **showing the dashboard, sleeping at night and waking on
  touch, protecting the battery, or keeping kids out.**
- **Troubleshooting data belongs in debug builds only.**
- **Fewer settings and entities are always preferred.** No alerting, no monitoring features.
- Applied 2026-09-27: release builds publish only Battery, Battery temperature, Night mode (switch), Screen
  brightness and Reload (button) (`EntityCatalog`; five since 0.8.1); everything else only in debug builds, and
  HA deletes it on release panels. The "Wall panel alerts" blueprint was removed.
- Applied in 0.8.0 (user-approved list, 2026-09-27): settings reduced to HA URL, text size (user exception), MQTT
  on/off + address incl. port + username + password (+ Test connection), battery-optimization notice (only while
  restricted), PIN + kiosk lock with escape hatches, night mode on/off/from/until, brightness Follow Android /
  Fixed %, Reload, Logs (release, behind the PIN; no adb log access). Fixed defaults: device name "Wall panel",
  HA's MQTT topics, no "Load URL any website", sensorLandscape, page scale, autoplay on, default user agent, no
  periodic reload or idle return but **back to the start page when night starts**, night display Black, 60 s
  stay-awake after a night touch, dashboard paused while black, self-signed pin for the start URL's host only.
  Debug-only: device ID, go to start page, clear web cache.

**Public beta (user, 2026-09-27):** the soak test continues in the background, but the app is published as
**v0.9.0 (beta)**, marked "in active testing; running daily on the author's wall panel". **v1.0.0** follows after
the soak test with bug fixes only.

**FEATURE FREEZE (user, 2026-09-27, after 0.8.0).** From now on only bug fixes found during the soak test: **no new
features, no new settings and no new HA entities unless the user explicitly asks.** The path to v1.0 is:
0.8.0 → soak test (2–4 weeks of normal use) → bug fixes only → final security review → README/user guide → v1.0.

Earlier framing: a wall panel that **shows an HA dashboard, sleeps at night, wakes on touch, protects its
battery, and runs unattended for months**. The original spec (§6, §7) is superseded where it conflicts with this
list. **Never re-add a dropped or postponed feature from the original spec without asking the user.**

**Planned phases**
- **Phase 4a (next):** hung-page watchdog, restart after a crash, PIN-protected settings, "Restart app" button (HA).
- **Phase 4b (planned for v1.0; user decision 2026-09-26):** device owner + Lock Task Mode. **New reason:** children
  in the home may use the tablet, so the panel must stay on the dashboard. (Earlier decision "not planned" is
  superseded.) **Safety is the first requirement.** Binding rules from the user:
  1. Check prerequisites first (accounts via `dumpsys account`; whether a factory reset is needed or removing and
     re-adding an account is enough).
  2. Never block the way back: never `DISALLOW_FACTORY_RESET`, never restrict debugging features or USB, no user
     restrictions beyond what Lock Task Mode itself needs; the adb PIN reset keeps working.
  3. Two separate steps: becoming device owner locks nothing. The kiosk lock is a separate setting, off by default,
     and can only be enabled while a PIN is set.
  4. PIN-protected escape hatches in Settings: "Unlock for 15 minutes" (lock resumes automatically), "Open Android
     settings", "Remove device owner permanently" (`clearDeviceOwnerApp`, back to a normal tablet).
  5. Lock Task features: block status bar, notifications, Home and Recents; keep the power menu (restart possible).
  6. The kiosk lock can't be turned off from HA; a diagnostic "Kiosk lock" state sensor is fine.
  7. Setup guide documents the last resort: factory reset via recovery mode, and that the Google account password
     is needed afterwards (FRP) if an account was on the device.
  8. Test sequence: full cycle on an emulator first (provision → lock → escape hatches → remove device owner), then
     on the tablet, verifying the escape hatches before enabling the lock.
     **Amended 2026-09-26 (user):** the recovery-mode and safe-mode checks on the tablet (checklist steps 8, 9) are
     **skipped** (an attempt landed in fastboot, which was harmless; the user doesn't want to use the key
     combinations). Verified ways back instead: PIN → escape hatches, adb PIN reset over Wi-Fi, adb over USB (PC
     authorized, timeout disabled; it re-enabled wireless debugging after a reboot). The recovery key combination
     stays documented as unverified.
  9. Kiosk mode is strictly optional. The app stays fully functional in all three states: (a) no device owner,
     (b) device owner with the lock off (a normal tablet: other apps, notifications, Home all work), (c) device
     owner with the lock on. Switching the lock off persists until it is switched on again. Test all three states
     and the transitions between them.
- **Principle for 4a (user):** never lock the owner out; this is a home panel, not a public kiosk. The PIN fails
  **open** (unreadable PIN → Settings opens with a notice to set a new one); wrong-PIN lockout is a fixed 30 s after
  5 attempts, not persisted; no PIN reset from HA; adb reset and "clear app data" are the documented fallbacks.
- **Phase 6 (reduced):** only rotating log files, viewable in Settings.
- **Phase 7:** 2–4 week soak test in normal use, bug fixes only, security review, README/user guide, v1.0
  (see FEATURE FREEZE above).
- **After v1.0:** self-updater (github flavor), together with "come back after an app update".

**Moved to beta preparation:** "Copy diagnostics", first-run setup wizard, settings export/import (see
"Beta preparation").

**Conditional:** daily scheduled WebView restart — only if the soak test shows memory growth.

**Removed 2026-09-27 (principle above):** the "Wall panel alerts" blueprint (offline/recovery notifications), all HA
entities except the six above in release builds (diagnostics, Last recovery, App memory, Kiosk lock, Restart app,
URL controls, …; still in debug builds). **0.8.0:** the daytime screensaver (all modes, timeout, brightness, clock
screen) and its entities; the "Home Assistant decides" night source and the day/night blueprint; additional
dashboards and the `dashboard` select; periodic reload and idle return; the settings listed as fixed defaults
above. **0.8.1 (user, 2026-09-27):** the wake-on-motion blueprint and the "Wake screen" button entity (it existed
only for that blueprint; never requested, untested). The only blueprint left is battery charge management.

**Dropped** (and why):
| Feature | Why dropped |
|---|---|
| App-side automatic brightness + ambient light sensor/entities (removed in 0.4.1) | Android's adaptive brightness ("Follow Android") already does it |
| Configurable Lock Task features | Fixed, sensible defaults are enough |
| Stay on while plugged in, setting the system screen timeout | The app keeps the screen on itself; "stay on while plugged in" conflicts with the charging plug |
| Real screen off (device owner) | A truly off screen can't be woken by touch; Black at lowest backlight serves "sleeps at night" |
| Volume control and mute | The panel plays no sound |
| Camera motion detection, motion sensor, camera snapshot | Privacy and store-policy cost; not part of the panel's job |
| Wake on motion (blueprint + "Wake screen" button, removed in 0.8.1) | Never requested by the user and untested; a touch wakes the panel |
| Proximity wake | The target tablet has no proximity sensor; HA sensors cover presence |
| Text-to-speech, play sound from URL, overlay/toast messages | HA has real speakers and can show messages on the dashboard itself |
| Screenshot entity | Privacy, little value |
| Microphone for HA Assist | Not part of the panel's job |
| JavaScript bridge (`window.wallPanel`) | HA already has these values via MQTT; the bridge is attack surface |
| Wi-Fi SSID sensor | Needs location permission |
| Remote settings change via MQTT (whitelisted keys) | The important settings are already HA entities |
| Local REST API + admin web page | Duplicates MQTT and adds an open port |
| In-app "Enable wireless debugging" (decided 2026-09-26) | Developer convenience only. Device owner can't do it (`setGlobalSetting` allows only `ADB_ENABLED` = USB debugging, not `adb_wifi_enabled`); it would need `WRITE_SECURE_SETTINGS` granted over adb, a broad permission that can change any secure setting. Manual way instead: Unlock for 15 minutes → Quick Settings tile; USB cable as fallback |

## Beta preparation (roadmap; do before the Stage 1 beta, after Phase 4a; not yet implemented)
- **"Copy diagnostics" button in Settings** copies a plain-text block to the clipboard containing:
  - app version and flavor; device manufacturer and model; Android version;
  - WebView version; dashboard viewport; device-owner status; MQTT status.

  It must contain **no secrets and no URLs with tokens**: no passwords, no HA URL query strings, no MQTT credentials.
- **GitHub issue templates** (`.github/ISSUE_TEMPLATE/`) for bug reports and device-compatibility reports. Both ask
  for the diagnostics block.
- **"Tested devices" table in the README**: model, Android version, WebView version, status and notes.
- **Setup guide:** add a link to https://dontkillmyapp.com for manufacturers with aggressive battery management.
- **First-run setup wizard** (postponed from Phase 6): asks **only for required fields** (Home Assistant URL; if the
  user opts in to the integration: broker address, username, password, with Test connection). Everything else uses
  defaults and stays reachable in Settings.
- **Settings export/import** (postponed from Phase 6): JSON, excluding secrets and the device id, carrying
  `schemaVersion`; plus "reset/edit device ID".

## Release roadmap (future steps; take no action until the user asks)
0. **Brand name:** done, "Kiosara" (0.9.0). The package name stays unchanged.
1. **Public repository and first release:** done for v0.9.0 (beta): a fresh single-commit snapshot in the public
   repo, a signed `github` APK with SHA-256 checksums as a GitHub pre-release. Commits use the GitHub no-reply
   e-mail. Next: v1.0.0 after the soak test (bug fixes only); a `store` APK for IzzyOnDroid when submitting.
2. **Announce** in the Home Assistant community (forum / Reddit).
3. **Android developer verification:** decide between limited distribution and a full account, and register the
   package name and signing certificate. Google's program has regional deadlines from 30 Sep 2026 and goes global in 2027.
4. **IzzyOnDroid / F-Droid:** submit the `store` flavor. Confirm that the Linux CI build matches F-Droid's build
   (reproducible builds let F-Droid ship our signature).
5. **Google Play (optional):** a developer account, the specialUse FGS declaration with a video, the Data safety
   form (see `docs/store-policy-notes.md`), and Play App Signing (upload our key, or accept that Play and
   GitHub installs are mutually exclusive).

## Conventions
- Kotlin only; Compose for native UI; coroutines/Flow; Gradle Kotlin DSL + `gradle/libs.versions.toml`.
- Style: ktlint 1.8 with `.editorconfig` (`android_studio` style, 120 columns). Run `ktlintFormat` before committing.
  `ktlint_standard_package-name` is disabled, because the permanent package contains underscores.
  detekt is **not** used, because no stable detekt release supports Kotlin 2.4.
- Android Lint runs with `warningsAsErrors`; dependency-freshness checks are disabled because versions are
  reviewed manually. Suppress a warning only with a comment explaining why.
- Dependencies must have permissive licenses only (Apache/MIT/BSD/EPL). licensee enforces this; add a
  license to its allow-list only when it is permissive. No Google Play Services, analytics or crash reporting.
  Record every dependency in `THIRD_PARTY_NOTICES.md`.
- All user-visible strings go in `res/values` **and** `res/values-cs`. **Exception (user's choice):** error
  messages stay English-only. Mark them `translatable="false"` and don't add them to `values-cs`.
- Tests: JVM tests use fakes (`FakeSettingsRepository`, `FakeNetworkMonitor`, `FakeClock`) and virtual time.
  On-device tests are in `androidTest`. Unit tests and lint run for both flavors.
- Small, meaningful commits. No TODOs without an entry under "Open issues" below.
- **Git remote:** `origin` = https://github.com/andy-walker-idfa/kiosara (public), branch `main`.
  **Push only `main`:** `git push origin main` (never `--all`; the maintainer's local clone may hold the private
  pre-release history, see `CLAUDE.local.md`). Push at the end of every change set the user approved.
- **Never commit keys, passwords or tokens** (keystores, `keystore.properties`, MQTT/API credentials,
  HA tokens) **or personal data** (IPs, entity ids, serials, local paths, e-mail addresses, household details).
  `.gitignore` covers `*.jks *.keystore *.p12 *.pfx keystore.properties local.properties CLAUDE.local.md private/`.
  Before a push, check that `git status` shows no secret files, and scan the diff for passwords and personal data.
- Before installing software or changing the system outside the repo, ask the user first.
- Subagents in `.claude/agents/` (android-architect reviews every phase plan; tell the user when delegating).

## Phase status
- [x] **Phase 0**: environment set up; hello-world built, installed and launched on the TB310FU.
- [x] **Phase 1**: skeleton and architecture, settings (DataStore + schemaVersion), secret store, full-screen
      WebView with HA login persistence, error overlay with backoff, reconnect/periodic/idle reload, renderer
      recovery, Home launcher and boot start, subagents, skills, release keystore.
      Checklist: `docs/testing/phase-1-checklist.md`.
- [x] **Phase 1.5** (0.2.0 / 2): rename to `io.github.andy_walker_idfa.smarthome_dashboard` / "Wall Panel",
      flavors, device identity, "Not set up" screen, reproducible builds, CI, privacy and store docs, Fastlane.
      Checklist: `docs/testing/phase-1_5-checklist.md`.
- [x] **Phase 2** (0.3.0 / 3): foreground service, MQTT with device discovery (24 entities), availability/LWT,
      battery/system/connectivity/dashboard sensors, brightness/reload/URL/dashboard commands, MQTT settings,
      battery-optimization flow per flavor, charging blueprint + status card.
      Checklist: `docs/testing/phase-2-checklist.md`.
- [x] **Phase 3** (0.4.0 / 6): screen & screensaver (Clock/Dim/Black), night mode (app schedule or HA, 30 min
      fallback), paused dashboard with reconnect check, new screen entities, wake-on-motion and day/night
      blueprints. **0.4.1 / 7:** app-side automatic brightness, the light sensor and their two entities removed
      (user decision: Android's adaptive brightness is enough). Installed on the TB310FU; the manual checklist is
      still to be run by the user.
      Checklist: `docs/testing/phase-3-checklist.md`.
- [x] **Phase 4a** (0.5.0 / 8): page watchdog, crash/freeze restart, "Restart app" button and "Last recovery"
      sensor, optional settings PIN (fails open), 5-tap gesture, Settings auto-close.
      Checklist: `docs/testing/phase-4a-checklist.md`.
- [x] **Phase 4b** (0.6.0 / 9): optional kiosk lock (device owner + Lock Task Mode, escape hatches, "Kiosk lock"
      sensor). Tested on the emulator (full cycle) and on the TB310FU (states a/b/c, steps 1–20; recovery/safe mode
      skipped by the user). Checklist: `docs/testing/phase-4b-checklist.md`.
- [x] **Phase 6** (0.7.0 / 10): rotating log files viewable in Settings, app memory logged every 30 min.
      (The "App memory" HA sensor is debug-only and the alerts blueprint was removed on 2026-09-27.)
      Checklist: `docs/testing/phase-6-checklist.md`.
- [x] **0.8.0** (13): settings reduced to the approved list, daytime screensaver / HA night source / additional
      dashboards dropped, back to the start page when night starts. **Feature freeze.**
      **0.8.1** (14): Wake screen button and wake-on-motion blueprint removed (five release entities).
      Checklist: `docs/testing/release-0.8.0-checklist.md`.
- [ ] Phase 7: soak test, bug fixes only, security review, README/user guide, v1.0 (`docs/testing/phase-7-plan.md`).
      Phase 5 is dropped.

## User-facing docs to keep current
- **`docs/home-assistant-setup.md`** (written by the user, linked from README). It must stay **generic**: no personal
  IPs, entity IDs or names.
  - **Phase 2 (0.3.0):** replace the "(available from v0.3)" placeholders with the real entity list, the blueprint
    import steps and the configuration options (MQTT settings names and defaults). Adjust the version if it changes.
  - **Every phase:** check that the settings gesture (currently: 5 taps within 4 s in the top-right corner, then
    the optional PIN) and the fixed defaults it mentions (device name "Wall panel", MQTT port 1883) match the app.
- `README.md` feature list, `PRIVACY.md` and `docs/store-policy-notes.md`: update them when features, permissions or data flows change.

## Missing hardware
No current feature needs optional hardware (the light sensor, camera and proximity features were dropped). If one is
ever added (with the user's approval), it checks availability at runtime, hides its settings with an explanation,
publishes no MQTT entities for missing hardware, and has unit tests for the "missing" path.

## Log files (Phase 6, 0.7.0)
- `FileLogSink` (plain JVM, unit-tested) installed by `DashboardApplication` **before** the container (main process
  only): `filesDir/logs/app.log` + `app.1.log`, 1 MB each. `AppLog` i/w/e always, d only in debug builds.
- One writer thread, bounded queue (2,000; overflow → "N lines dropped"); flush 5 s after the first unwritten line,
  at once for W/E; no fsync (the page cache survives a process kill). Identical consecutive lines → "(previous line
  repeated N times)".
- **Privacy:** `LogRedactor` masks URL query strings, `user:pass@` and `password=`/`token=`-style values; exceptions
  are written as class + 3 frames + "caused by" classes, **never messages**. Keep the rule: never log secrets,
  payloads or URLs with tokens.
- **Before every intentional process end** (`AppRestarter.restartNow`, `MainThreadWatchdog` kill, `CrashHandler`
  before chaining) `AppLog.flushNow()` (bounded 300 ms; skipped on the writer thread itself).
- Viewer: Settings → Logs → `LogViewerActivity` (non-exported, dashboard task, so it works under the kiosk lock);
  `readTail` runs on the writer thread (flush, then tail read from the file end); newest first; filter All /
  Warnings / Errors (`LogLines`, continuation lines inherit the level); 10-min idle → back to the dashboard.
- Start marker per process: version, build type, flavor, Android version, time zone, seconds since boot.
- `DeviceStateSource` logs app PSS and free memory every 30 min (soak test); HA sensor `app_memory`.

## Kiosk lock (Phase 4b, 0.6.0)
- **Three states, all fully usable (user rule 9):** (a) not device owner, (b) device owner + lock off (normal
  tablet), (c) device owner + lock on. `Settings.security.kioskLock` persists; `KioskController` switches it off
  whenever there is no PIN or no device owner (never a lock without a way in).
- **Mechanism:** device owner → the app allowlists only itself (`setLockTaskPackages`) with
  `LOCK_TASK_FEATURE_GLOBAL_ACTIONS` (power menu). Allowlisting locks nothing; `MainActivity` calls
  `startLockTask()` while RESUMED when `KioskStatus.shouldLock` and `isLockTaskPermitted`, `stopLockTask()` otherwise.
  **Never remove the allowlist while locked** (AOSP `LockTaskController` clears the locked task → closes the
  dashboard and Settings); escape hatches call `stopLockTask()` first (from SettingsActivity, same task).
- **Escape hatches** (Settings, behind the PIN): unlock 15 min (in memory; restart re-locks; when it ends the
  dashboard is started from the background, allowed for a device owner, and locks in onResume), "Lock now",
  open Android settings (= unlock + settings), remove device owner (`clearDeviceOwnerApp`, deprecated but the
  only in-app way back; clears allowlist, restrictions and admin).
- **Safety (user rules 2, 6):** no user restrictions or policies besides the allowlist; Android itself adds
  `no_add_managed_profile` and `no_add_clone_profile` for any device owner. HA: `kiosk_lock` binary sensor
  (device class lock), no command. adb PIN reset also switches the lock off (the lock needs a PIN).
- **Verified on the Android 13 tablet emulator (Tablet_API33, 2026-09-26):**
  - provisioning with 0 accounts; `userRestrictions: null`; effective restrictions only the two Android defaults;
  - state (b): Home → launcher, other apps, notification shade all work;
  - PIN set (emulator calibrated 162,992 PBKDF2 iterations), lock on → `mLockTaskModeState=LOCKED` without the
    pinning prompt; Home, Recents, other apps ("error code 101") and the notification shade are blocked;
  - Android refuses `am crash` ("Can not crash protected package") and `am force-stop` ("Ignoring request to
    force stop protected package") for a device-owner app;
  - "Open Android settings" → `NONE`, Android settings open, Home shows the dashboard unlocked, other apps work;
    **after exactly 15 min the dashboard was brought back from behind Android settings and locked** (in-memory
    timer; `RelockAlarm` is the backup if the process dies);
  - `adb install -r` while locked: the app comes back (Home + device owner) and locks at once (this also answers
    "relaunch after update" while device owner);
  - reboot while locked → starts locked; lock off → reboot → stays off (state b), other apps work;
  - adb PIN reset while locked → lock task exited, PIN removed, lock switched off;
  - "Remove device owner" while locked → `NONE`, `no owners`, effective restrictions `none`, Settings stays open
    (the task is not wiped because `stopLockTask()` comes first);
  - not testable on the emulator: the power menu (no long-press power) and safe mode (no way to boot into it);
    both are tablet checklist steps for the user.
- **Verified on the TB310FU (2026-09-26):** prerequisites 0 accounts / only user 0 / no owners; provisioning
  → only the two Android defaults; state (b) normal (other apps, shade, Lenovo Recents, Home → dashboard). User ran "Open Android settings" and "Remove device owner"
  (→ `no owners`, restrictions `none`, allowlist empty), then re-provisioned.
- **Lock on, tested by the user on the TB310FU (2026-09-27):** Lenovo taskbar, split-screen and floating windows
  blocked; power menu appears; 5 taps + PIN open Settings while locked; Unlock for 15 minutes → another app → after
  15 min the dashboard came back locked and the other app was closed; HA "Restart app" while locked → back locked;
  reboot while locked → starts locked; lock off + reboot → stays off; removing the PIN switches the lock off.
  Step 22 (update while locked) verified with the 0.8.0 install (2026-09-27): back, locked, screen awake and MQTT
  connected within 1 s. Not yet on the tablet: step 21 (adb PIN reset while locked; verified on the emulator). Other apps during an unlock are started via "Open Android settings" → Apps → the app → Open (the app is the
  Home app, so there is no launcher home screen).
- Ways back, in order: PIN → escape hatch; adb PIN reset over Wi-Fi; adb over USB; safe mode; factory reset from
  recovery (FRP if a Google account is on the tablet). See the `device-provisioning` skill.

## Recovery & settings PIN (Phase 4a, 0.5.0)
- **Principle (user):** never lock the owner out; this is a home panel, not a public kiosk.
- **Page watchdog** (`DashboardController`, only while visible, not paused, page loaded):
  - liveness probe `WebAction.ProbeRenderer` every 60 s; no answer in 10 s → `TerminateRenderer`. Needed because
    `onRenderProcessUnresponsive` fires only when input goes unanswered, and a wall panel is rarely touched;
  - `WebViewRenderProcessClient` unresponsive for ≥ 15 s → terminate;
  - HA frontend check every 5 min; 2× `disconnected` in a row → reload (after a wake: 1× is enough);
  - `terminate()` → `onRenderProcessGone(didCrash=false)` → existing rebuild + crash-loop guard. Without a
    renderer handle, `WebViewHost.replaceWebView` rebuilds directly. Tests of unrelated timing pass
    `watchdogEnabled = false`.
- **App restart:** Android blocks activity starts from the background (alarms too; being the HOME app doesn't
  help, see "Relaunch after update"). So the app restarts only **while one of its activities is visible**:
  `AppRestarter` → `RestartActivity` (translucent, own `:restart` process, which skips all app initialisation)
  kills the old pid and opens `MainActivity`. Used by `CrashHandler` (uncaught exceptions; avoids the "keeps
  stopping" dialog and AMS's "bad process" state), `MainThreadWatchdog` (tick every 5 s on uptime; frozen
  ≥ 20 s) and `restart_app`. `CrashLoopGuard`: max 5 automatic restarts per 30 min, then Android's handling.
  Not visible → Android's handling (the FGS restarts itself).
- **Last recovery:** `RecoveryStore` (SharedPreferences, synchronous writes) + `ExitReasons` (API 30+
  `ApplicationExitInfo`: crash, native crash, ANR, low memory, signal; our own restarts are marked "expected"
  and skipped). Crash logs contain the exception class and stack frames, never the message.
- **Settings PIN:** optional, 4–8 digits; `PinHasher` = PBKDF2-HMAC-SHA256, 16-byte salt, iterations calibrated
  to ~0.5 s (50k–600k), `pbkdf2-sha256$iter$salt$hash`, constant-time compare; stored in `SecretStore`
  (`settings_pin`), flag `Settings.security.pinEnabled`. **Fails open:** flag set but hash unreadable → flag
  cleared, Settings opens with a notice. `PinAttempts`: 5 wrong → fixed 30 s, in memory only. Forgotten PIN:
  `PinResetReceiver` (exported, `android.permission.DUMP` = adb shell only) or clear app data. No HA reset.
- **Gesture:** 5 taps within 4 s in the top-right 100 dp corner (`CornerTapDetector`); taps outside the corner are
  ignored (no reset); dots (`CornerTapHint`) show the count from the 2nd tap; taps still reach the page; a wake tap
  doesn't count. (0.6.0: was 64 dp / 3 s / reset on a miss, which worked only 1 in 3 times for the user.) Settings closes after 10 min without a touch (timer paused while a system screen
  opened from Settings is in front); the PIN prompt after 60 s.
- **Verified on the TB310FU (2026-09-25):**
  - `am crash <pid>` with the dashboard visible: back in ~0.3 s via the trampoline, MQTT connected ~0.6 s later,
    no dialog; the system's exit record (SIGNALED, expected) is skipped. (`am crash <package>` hits the WebView
    renderer instead: recorded as `page_crash`, page rebuilt.)
  - Crash-loop guard: restarts 1–5 worked; the 6th showed Android's "keeps stopping" dialog. The FGS did **not**
    restart by itself within 20 s after that (Android delays restarts after repeated crashes); pressing Home
    brought everything back. Acceptable: 5 restarts in 30 min means something is seriously wrong.
  - `chrome://hang` (debug build): the probe caught it after 70 s, terminated the renderer (`process known=true`),
    the WebView was rebuilt. A renderer can hang mid-load, so the probe also runs while a page is loading.
  - adb PIN reset broadcast works from the shell.
  - Still to check (user): calibrated PBKDF2 iterations (logged as "PIN set (N PBKDF2 iterations)"),
    `restart_app` from HA, frontend-disconnected reload.

## Screen & display (Phase 3, reduced in 0.8.0)
- **`DisplayController`** is app-scoped (created and started in `AppContainer`), thread-safe (`synchronized`),
  and never touches views. It publishes `DisplayStatus` (mode, window brightness, pauseDashboard, night,
  brightness %). `MainActivity` renders it: window brightness, `NightOverlay`, `WebViewHost.setCovered`,
  `DashboardController.onDashboardPaused` and `onNightChanged` (back to the start page when night starts).
- **Rules (`DisplayPolicy`):** dashboard not visible → `active`; night → `black`, except 60 s
  (`ScreenSettings.NIGHT_WAKE_SECONDS`) after the last touch; otherwise `active`. **No daytime screensaver.**
  Becoming visible (e.g. back from Settings) counts as a touch. There is no remote wake (removed in 0.8.1).
- **Touch handling:** `MainActivity.dispatchTouchEvent` swallows the whole gesture (DOWN → UP/CANCEL, multi-touch
  included) that wakes the black screen; keys likewise via `dispatchKeyEvent` (RestrictedApi lint suppression
  explained in the code).
- **Night:** app schedule only (`NightSchedule`, local time, start == end = never). HA's `night_mode` switch
  overrides it until the next transition (memory only); changing the times or switching night mode off drops the
  override. `night_mode` commands are rejected while night mode is disabled.
- **Timing:** one timer, re-planned on every input; durations on elapsed time, schedule on wall time
  (`ZoneId.systemDefault()`); `ACTION_TIME_CHANGED`/`TIMEZONE_CHANGED` re-evaluate (`core/TimeChanges.kt`);
  waits are capped at 15 min.
- **Brightness:** `BrightnessMode` SYSTEM (default: no window override, so Android's brightness including its
  adaptive brightness applies) / MANUAL. HA's `screen_brightness` sets MANUAL + the level (saved). No app-side
  automatic brightness and no light sensor use (0.4.1); old settings with `AUTO` decode to SYSTEM via
  `coerceInputValues`. Percent → window uses gamma 2.2; floor 0.01; 0.0 only for Black (behind the black overlay).
- **Paused dashboard:** under Black the WebView is `INVISIBLE` + `onPause()` (never the process-global
  `pauseTimers()`); the state survives renderer re-creation. After a pause ≥ 3 min, 10 s after wake the page is
  asked for `hass.connection.connected` (`WebAction.CheckHaConnection`); `disconnected` → reload, `unknown` →
  nothing.
- **Removed entities:** see `Entities.removed` (0.4.1: `ambient_light`, `auto_brightness`; 0.8.0: screensaver,
  night display and dashboard entities). Platform-only discovery components + cleared retained states; never reuse
  those object ids.
- Still open: decide on a screen-off-only partial wake lock after measuring MQTT keep-alive in Doze
  (`dumpsys deviceidle force-idle`). Real screen-off was dropped, so this matters only for the charge cycles and
  the soak test.

## Requirements carried forward
- **Wireless adb with the kiosk lock:** provisioning works over Wi-Fi. Never set `DISALLOW_DEBUGGING_FEATURES`.
  After a reboot, re-enable wireless debugging manually: Settings (PIN) → Unlock for 15 minutes → Quick Settings
  tile; USB cable as the fallback. No in-app button (scope decision). `dpm remove-active-admin` doesn't work for
  non-test apps; "Remove device owner" is in Settings.
- **Phase 2 FGS:** `specialUse` + subtype property + `FOREGROUND_SERVICE_SPECIAL_USE`. `POST_NOTIFICATIONS`
  is a runtime permission on API 33 (the service still runs if it is denied).

## Final security review (Phase 7, 2026-09-27, security-reviewer on 0.8.1)
No Critical or High findings. User decisions:
- **Fixed:**
  - L1: "Trust this certificate" needs the PIN (`MainActivity.withPin`).
  - L2: `KeystoreSecretStore` treats any exception (incl. `ProviderException`, `IOException`) as missing.
  - L4: no exception messages in MQTT logs (`logSafeReason`); command names logged only if `[a-z0-9_]{1,40}`;
    `FileLogSink` turns CR/LF into spaces.
  - L5: MQTT payloads > 1 KiB ignored (`MAX_PAYLOAD_BYTES`).
  - L6: WebView metrics opt-out in the manifest; **Safe Browsing stays on** and is disclosed in PRIVACY.md.
  - Docs: "Security notes" in the setup guide (section 11), SECURITY.md, stale comments.
- **Accepted, documented, no code change:**
  - M1: links inside the dashboard can lead to other websites even with the kiosk lock on. User responsibility
    (setup guide section 10 + Security notes).
  - L3: 4-digit PIN brute force with the fixed 30 s lockout. The guide recommends 6+ digits.
- **Not done:** Netty bump (flagged CVEs are in netty-codec-http/http2, not on the classpath; a bump overrides
  HiveMQ's pinned versions and needs new R8 rules).
- M2 (personal data in git history) is handled by publishing a fresh single-commit snapshot (release preparation).

## Open issues
- **HA-side validation of the blueprint:** the structure is checked locally. Still to confirm in HA:
  `states['<entity_id>']` lookups, templated `action:` for the notify target, and a real 40/80 % charge cycle
  (a v1.0 definition-of-done item).
- **Doze / screen-off:** MQTT keep-alive with the screen off is untested (the screen is always on in Phase 2).
- **Relaunch after update:** with device owner the dashboard comes back after `adb install -r`, locked if the lock
  is on, and (0.7.1) switches the screen on (tablet, 2026-09-27). Without device owner it still doesn't come back;
  revisit with the self-updater after v1.0.
- **Self-signed certificate flow** is covered by unit tests only; HA is plain http, so it can't be tested on the device.
- **Cross-platform reproducibility:** confirmed. After the `keepDebugSymbols` fix, the Linux CI and Windows APKs
  are identical entry for entry, including with HiveMQ and Netty (CI runs of 2026-09-24).
- Memory baseline (HA login page): app PSS ≈ 142 MB, WebView renderer ≈ 262 MB. Re-measure on the real
  dashboard during the Phase 7 soak test.
