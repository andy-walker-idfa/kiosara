# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/) (0.x until v1.0). Each release has a unique
`versionCode`; the matching store changelog is `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.

## [0.9.0] - 2026-09-27
First public release, a **beta**: in active testing; running daily on the author's wall panel. v1.0.0 follows
after a longer test period, with bug fixes only.
### Changed
- New name: **Kiosara** (was the working name "Wall Panel"). The package name, the Home Assistant device name
  ("Wall panel"), entity IDs and MQTT topics are unchanged, so existing setups keep working.
- The charging blueprint is now called "Kiosara: battery charge guard" and can be imported with a button.
- The MQTT discovery "origin" is shown as Kiosara.
### Security
- "Trust this certificate" on the error screen now asks for the settings PIN (if one is set).
- A failing Android Keystore no longer crashes the app; the secret counts as missing and is asked for again.
- The log no longer contains exception messages from the MQTT connection, and log lines can't be split or
  forged through MQTT topic names.
- MQTT messages larger than 1 KB are ignored.
- Android System WebView's usage statistics are switched off. Safe Browsing stays on (disclosed in PRIVACY.md).
### Added
- SECURITY.md and a "Security notes" section in the setup guide; 6-digit PINs are recommended.

## [0.8.1] - 2026-09-27
### Removed
- The "Wake screen" button entity and the wake-on-motion blueprint (never requested, untested). Home Assistant
  deletes the entity. Release builds now publish five entities: Battery, Battery temperature, Night mode, Screen
  brightness and Reload.

## [0.8.0] - 2026-09-27
Settings reduced to what a home panel needs. Last feature release before v1.0 (feature freeze).
### Changed
- Settings show only: Home Assistant URL and text size; MQTT on/off, broker address (with an optional `:port`),
  user name and password; brightness; night mode on/off, from and until; PIN and kiosk lock; Reload; Logs.
- The battery-optimization notice appears only while Android restricts the app.
- Night mode: the screen is black, a touch wakes it for 60 s, and the dashboard returns to its start page when
  night starts. These are fixed now.
- Fixed defaults instead of settings: landscape either way up, the page's own scale and user agent, media
  autoplay, device name "Wall panel" (rename it in Home Assistant), Home Assistant's MQTT topics.
- A self-signed certificate can be trusted for the Home Assistant host only ("Trust this certificate" on the
  error screen).
- Go to start page, clear web cache and the device ID are in debug builds only.
### Removed
- The daytime screensaver (clock, dim, black; timeout and brightness).
- "Home Assistant decides" night schedule and the day/night blueprint.
- Additional dashboards, periodic reload and returning to the start page after inactivity.
- The screensaver, night display and dashboard entities (debug builds); Home Assistant deletes them.

## [0.7.2] - 2026-09-27 (versionCode 12)
### Changed
- Home Assistant sees only what a home panel needs: Battery, Battery temperature, Night mode, Screen brightness,
  Wake screen and Reload. All other entities (diagnostics, Last recovery, App memory, Kiosk lock, Restart app,
  screensaver and dashboard controls, …) are removed from Home Assistant automatically; debug builds keep them.

### Removed
- The "Wall panel alerts" blueprint.

## [0.7.1] - 2026-09-27 (versionCode 11)
### Fixed
- The dashboard switches the screen on when it comes back to the front (after an update, a restart or the end of
  "Unlock for 15 minutes"). Before, it could stay dark if Android's screen timeout had turned the screen off while
  another app was open.

## [0.7.0] - 2026-09-27 (versionCode 10)
### Added
- Log files on the tablet, viewable in Settings → Logs (behind the PIN; also while the kiosk lock is on): what the
  app did recently, filter by warnings and errors. Two files of up to 1 MB; sensitive parts are masked and error
  messages are never written. The lines that explain a crash or restart are saved before the app restarts.
- Home Assistant: "App memory" diagnostic sensor (the app's own memory use), also logged every 30 minutes.
- Blueprint "Wall panel alerts": notifies when the panel is offline for 15 minutes (adjustable) and when the app
  recovered from a problem by itself.

## [0.6.0] - 2026-09-26 (versionCode 9)
### Added
- Optional kiosk lock (Lock Task Mode) for when the app is device owner (set up once over adb; no factory
  reset needed). Being device owner locks nothing; the lock is a separate setting that needs a PIN and stays as
  set. Blocks the status bar, notifications, Home, Recents and other apps; the power menu keeps working.
- PIN-protected escape hatches: unlock for 15 minutes (locks again by itself), open Android settings, remove
  device owner permanently.
- Home Assistant: "Kiosk lock" diagnostic sensor (no command to unlock); "Device owner" now reflects the real state.
- The settings gesture is easier to hit: a larger corner (about 12 mm), 4 seconds for the 5 taps, taps outside
  the corner no longer start over, and dots in the corner show the counted taps.
- Setup guide: provisioning steps and the ways back in order (PIN, adb over Wi-Fi, adb over USB, safe mode,
  factory reset with the Factory Reset Protection warning).

## [0.5.0] - 2026-09-25 (versionCode 8)
### Added
- Watchdog: a frozen page (no answer to a check every minute, or unresponsive to touch for 15 s) is restarted
  and reloaded; a Home Assistant frontend that stays disconnected is reloaded.
- The app restarts itself after a crash or a frozen main thread while it is on screen (at most 5 times in
  30 minutes), without Android's "keeps stopping" dialog.
- Home Assistant: "Restart app" button and "Last recovery" sensor (what was fixed, and when; also reports
  Android's own records of crashes, ANRs and kills).
- Optional settings PIN (4–8 digits, stored as a salted hash in the Keystore-encrypted store). 5 wrong PINs
  block entry for 30 s. An unreadable PIN never locks the owner out. Forgotten PIN: an adb command, or clear
  the app's data.
- Settings close themselves after 10 minutes without a touch.

### Changed
- Settings now open with 5 quick taps in the top-right corner (was: hold the top-left corner for 3 s).

## [0.4.1] - 2026-09-25 (versionCode 7)
### Removed
- The app's own automatic brightness and the ambient light sensor. Brightness is now "Follow Android"
  (default; includes Android's adaptive brightness) or "Fixed". Settings saved with Automatic load as
  Follow Android.
- The Home Assistant entities "Ambient light" and "Automatic brightness"; Home Assistant deletes them
  automatically after the update.

## [0.4.0] - 2026-09-25 (versionCode 6)
### Added
- Screen & screensaver settings:
  - brightness: follow Android, fixed, or automatic from the ambient light sensor (minimum, maximum, smoothing);
  - screensaver after N minutes with Clock, Dim or Black;
  - night mode with its own night display and "stay awake after a touch" time;
  - night schedule in the app (22:00–06:30 by default) or decided by Home Assistant, falling back to the app's
    times after 30 minutes without a connection.
- A touch always wakes the screen and is not passed on to the dashboard.
- Under Black and Clock the dashboard is paused. After a long pause the app checks that Home Assistant
  reconnected and reloads the page if it hasn't within 10 seconds.
- New Home Assistant entities: Ambient light, Screen, Wake screen, Screensaver, Night mode, Automatic
  brightness, Screensaver timeout/brightness/mode, Night display. Light-sensor entities appear only on devices
  with a light sensor.
- Blueprints: wake on motion, and day/night by sun elevation or fixed times.

### Changed
- Brightness set from Home Assistant is saved as the fixed level and survives app restarts.
- Brightness percentages follow a perceptual curve, so 50 % looks like half brightness.
- The Settings screen keeps the screen on while open (it timed out after the Android screen timeout).

## [0.3.2] - 2026-09-25 (versionCode 5)
### Added
- The dashboard's viewport (size in CSS pixels, density and device pixel ratio) is shown in the Settings status
  area and published as the diagnostic sensor "Dashboard viewport" (e.g. `1072x640`), for designing dashboards
  that fit the panel exactly.

## [0.3.1] - 2026-09-25 (versionCode 4)
### Changed
- Settings reorganised by importance: Dashboard (required), Home Assistant integration (MQTT), Display &
  behaviour, Additional dashboards, and a collapsed Advanced section (user agent, self-signed certificate,
  MQTT port, discovery prefix, status topic, base topic, "Load URL" any website, device ID).
- Required fields are marked with `*`; every field has a short explanation (English and Czech).
- One Save button for all settings; closing with unsaved changes asks "Discard changes?".
- The MQTT full-refresh interval is no longer a setting (fixed 5 minutes; changes are sent immediately).
- Home Assistant shows memory, storage, battery level and Wi-Fi signal without decimals.

### Added
- "Test connection" in the MQTT section reports success or the exact failure (host not found, connection
  refused, wrong username or password, timeout) without saving.
- "Show password" checkbox; readable broker login errors.

## [0.3.0] - 2026-09-24 (versionCode 3)
### Added
- MQTT connection to your Home Assistant with automatic discovery: one device with 24 entities (battery,
  charging, battery temperature, health and voltage, Wi-Fi signal, IP, boot and app start times, memory,
  storage, versions, current URL, page errors, last interaction, screen brightness, reload / start page /
  clear-cache buttons, "Load URL" and a dashboard selector).
- Availability via MQTT last will; automatic re-publish when Home Assistant restarts; reconnect with backoff.
- Background connection service that runs only while MQTT is enabled and restarts after boot and updates.
- MQTT settings (encrypted password), battery-optimization status and fix.
- Home Assistant blueprint for battery charge management (40-80 %, overheat lock at 40 °C with emergency
  charging, offline fail-safe, low-battery warning) and an example status card.

### Security
- Remote "Load URL" only accepts http(s) addresses on configured hosts (opt-in to allow any website).
- Retained MQTT command messages are ignored.

### Changed
- Additional dashboard names must be unique.

## [0.2.0] - 2026-09-24 (versionCode 2)
### Changed
- New permanent app ID `io.github.andy_walker_idfa.smarthome_dashboard`. Existing installs must be
  uninstalled once and the new app installed.
- The working display name is now "Wall Panel", with a new neutral launcher icon.
- There is no built-in start URL any more. On first start a "Not set up yet" screen asks for your
  Home Assistant address.

### Added
- Device section in Settings: an editable device name and a stable device ID for Home Assistant.
- Two build flavors: `github` (sideload) and `store` (F-Droid / Google Play).
- Reproducible release builds and CI on Linux.
- Privacy policy, store metadata, README.

## [0.1.0] - 2026-09-24 (versionCode 1)
### Added
- Full-screen dashboard WebView with persistent Home Assistant login.
- Error screen with automatic retry, reload after reconnect, renderer-crash recovery.
- Periodic reload and return-to-start after inactivity.
- Home app (starts on boot), temporary Settings screen, English and Czech UI.
