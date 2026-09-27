---
name: build-and-deploy
description: Build the Kiosara app (github or store flavor, debug or release) with Gradle, install it on the tablet via adb (USB or wireless), launch it, and tail filtered logcat. Use whenever you need to run the app on the device.
---

# Build and deploy

All commands run from the repo root in PowerShell. `adb` is on the user PATH
(`%ANDROID_HOME%\platform-tools`). `JAVA_HOME` points to Temurin 21.

Package / applicationId: `io.github.andy_walker_idfa.smarthome_dashboard` (the same for both flavors).
Below, `$PKG` stands for it: `$PKG = "io.github.andy_walker_idfa.smarthome_dashboard"`.

## Flavors
| Flavor | For | Notes |
|---|---|---|
| **`github`** (default) | The owner's tablet, GitHub releases | Self-updater + install permission arrive in Phase 7 |
| `store` | F-Droid, IzzyOnDroid, Google Play | No updater code, no install-packages permission |

Task names contain the flavor: `assemble<Flavor><BuildType>`, e.g. `assembleGithubRelease`.

## Build
```powershell
# Quality gate before every commit (both flavors)
.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug

.\gradlew.bat assembleGithubRelease   # app\build\outputs\apk\github\release\app-github-release.apk (signed, the tablet build)
.\gradlew.bat assembleStoreRelease    # app\build\outputs\apk\store\release\app-store-release.apk
.\gradlew.bat assembleGithubDebug     # app\build\outputs\apk\github\debug\app-github-debug.apk (package $PKG.debug, debug escape hatches)
.\gradlew.bat connectedGithubDebugAndroidTest   # instrumentation tests on the tablet
```
`ktlintFormat` fixes formatting automatically.

CI (`.github/workflows/ci.yml`) runs the same gate on Linux and builds **unsigned** release APKs of
both flavors (artifact `unsigned-release-apks` with `SHA256SUMS`). No secrets are used in CI.

## Connect to the tablet (wireless is the normal case)
The tablet is wall-mounted without USB. adb runs over **Wireless debugging** (Android 11+, TLS, paired).
Its fixed IP (DHCP reservation) is in `CLAUDE.local.md`.

**Everyday connect** (from the repo root, PowerShell):
```powershell
.\scripts\adb-wifi.ps1          # finds the current port via mDNS and runs `adb connect`
adb devices -l                  # -> <ip>:<port>  device  ... model:TB310FU
```
Use `-s <ip>:<port>` on adb commands if a USB connection or another device is also present. Gradle tasks
(`connected...AndroidTest`) use whatever single device is connected.

**Facts verified on the TB310FU (2026-09-25):**
- The PC is **paired once** (the pairing survives reboots and app updates).
- The port is **random** and changes whenever wireless debugging is switched on. Never hard-code it;
  `adb mdns services` shows it (`_adb-tls-connect._tcp`). adb does *not* auto-connect here, hence the script.
- **Android switches wireless debugging OFF at every reboot** (`adb_wifi_enabled=0`).
- The home Wi-Fi is marked "Always allow on this network", so `adb shell settings put global adb_wifi_enabled 1`
  works from any existing adb connection (e.g. USB), but that is useless after a reboot without USB.

**After a tablet reboot:** switch wireless debugging back on **on the tablet**, then run the script.
- Quick Settings tile "Wireless debugging": swipe down from the top edge (twice in immersive mode) and tap it.
  One-time setup: Developer options → Quick settings developer tiles → Wireless debugging.
- Or: Developer options → Wireless debugging → switch on.
- With the kiosk lock on (Quick Settings hidden): Settings (PIN) → Unlock for 15 minutes → tile; USB cable as the
  fallback. See the device-provisioning skill ("Wireless debugging while locked"). No in-app button by decision.

**If wireless debugging stays off / connect fails:**
1. `adb mdns services` shows nothing → it is off on the tablet, or the PC and tablet are on different networks.
2. The Wi-Fi network changed → Android asks "Allow wireless debugging on this network?" again on the tablet.
3. "failed to authenticate" / not listed under Paired devices → pair again: tablet → Developer options →
   Wireless debugging → **Pair device with pairing code**, then `adb pair <ip>:<pairing-port> <code>`
   (the pairing port differs from the connect port). On Windows `adb pair` may print
   "protocol fault (couldn't read status message)" even though pairing worked; check the tablet's
   "Paired devices" list.
4. Last resort: USB cable → `adb devices` (accept the prompt) → `adb shell settings put global adb_wifi_enabled 1`.

**Fault-injection tests over Wi-Fi** (e.g. `svc wifi disable`) cut adb itself. Run them detached on the tablet:
`adb shell "nohup sh -c 'sleep 5; svc wifi disable; sleep 120; svc wifi enable' >/dev/null 2>&1 &"`,
then reconnect with the script afterwards.

**USB** still works as before: `adb devices -l` lists `<serial> ... model:TB310FU` (serial in `CLAUDE.local.md`).

## Install and launch
The tablet runs the **githubRelease** build (signed with the release key).
```powershell
adb install -r app\build\outputs\apk\github\release\app-github-release.apk
adb shell am start -n "$PKG/.MainActivity"
adb shell am force-stop $PKG          # stop
```
Debug builds have the applicationId `$PKG.debug`, so they install **next to** the release build (both
appear as "Kiosara"; the debug one has the red "Debug build only" settings section). Tests
(`connectedGithubDebugAndroidTest`) run against the debug app and never touch the release install or its HA login.
Never `adb uninstall $PKG` on the tablet without a reason: it wipes the HA login, and on a provisioned
device-owner build it requires deprovisioning first (see the device-provisioning skill).

## Logs
All app log tags start with `SHDash/`.
```powershell
adb logcat -c                                                    # clear
adb logcat -v time --pid=$(adb shell pidof -s $PKG)              # app process only
adb logcat -v time | Select-String "SHDash/|AndroidRuntime"      # app tags + crashes
adb logcat -d -b crash                                           # crash buffer
```

## Screenshot
Use bash (PowerShell redirection corrupts binary output):
```bash
adb exec-out screencap -p > screen.png
```

## Release signing
`app/build.gradle.kts` reads `keystore.properties` in the repo root (git-ignored):
```
storeFile=<absolute path to the release keystore>
storePassword=...
keyAlias=smarthome_dashboard
keyPassword=...
```
If the file is missing (CI, F-Droid), release builds are produced unsigned. The owner keeps the keystore
and password backed up. Losing them means future updates cannot be installed over the existing app.

## Reproducibility check
Two clean builds must produce identical APKs, and the CI (Linux) unsigned APK must contain the same
entries as the local signed one (only the signing block differs):
```powershell
.\gradlew.bat clean assembleGithubRelease --no-build-cache
```
Compare entry by entry (e.g. with Python `zipfile` or `apksigcopier compare`), not the file hash of a
signed vs unsigned APK.
