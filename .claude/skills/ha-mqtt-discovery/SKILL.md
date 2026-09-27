---
name: ha-mqtt-discovery
description: Reference for the Kiosara app's MQTT topic layout, Home Assistant discovery payload format and entity naming conventions. Use whenever adding or changing an MQTT entity so all entities stay consistent.
---

# HA MQTT discovery conventions

> Implemented in Phase 2 (0.3.0): `mqtt/` package (`MqttNaming`, `Entities`, `Discovery`, `EntityStates`,
> `MqttManager`, `MqttCommandHandler`). Update this file together with any entity change, and also
> `docs/home-assistant-setup.md` (entity list).

## Identity rules (never break these)
- Identifiers are **never** derived from the package name (`io.github.andy_walker_idfa.smarthome_dashboard`)
  or the display name ("Kiosara").
- The source of truth is `Settings.device.id`: a 32-hex-char id created on first start by `DeviceIdentity`.
  It is a truncated SHA-256 of `ANDROID_ID` (scoped per signing key, stable across reinstalls), with a
  random-UUID fallback.
- `short_id` = the first 12 characters of `device.id`.
- `shdash` is a fixed protocol prefix, chosen once. It is not the brand and must not change with it.
- **Object ids are part of HA unique_ids: never rename one.** Add a new id and remove the old one instead
  (see "Removing an entity").

## Identifiers (settings; defaults shown)
| Setting | Default | Used for |
|---|---|---|
| Discovery prefix | `homeassistant` | discovery topic |
| HA status topic | `<prefix>/status` | birth message subscription |
| Node / device id | `shdash_<short_id>` | `device.identifiers`, MQTT client id, discovery node id |
| Device name | `Settings.device.name` ("Wall panel") | HA device name; entity ids follow it (`sensor.wall_panel_battery`) |
| Base topic | `shdash/<short_id>` | all state, attribute and command topics |

## Topics
```
<prefix>/device/shdash_<short_id>/config   retained device-based discovery (HA 2024.11+), all components
<base>/availability                        retained "online"/"offline"; LWT = "offline" (retained, QoS 1)
<base>/state/<object_id>                   retained state, QoS 1
<base>/attr/<object_id>                    retained JSON attributes (json_attributes_topic)
<base>/set/<object_id>                     commands from HA; never retained (retained messages are ignored)
<prefix>/status                            subscribed; on "online": wait 1-5 s, re-publish discovery → availability → states
```
Connection: MQTT 3.1.1, **clean session**, keep-alive 30 s, client id = node id, credentials from `SecretStore`.
Order on every (re)connect: subscribe → discovery → `online` → all states.

## Discovery payload
- One JSON object with `device` (`identifiers`, `name`, `manufacturer` = Build.MANUFACTURER, `model` = Build.MODEL,
  `sw_version` = "<versionName> (<flavor>)"; **no** `serial_number`), **`origin`** (`name: Kiosara`,
  `sw_version`) (mandatory for device discovery), root `availability_topic`, `qos: 1`, and `components`.
- `components` is keyed by object id. Each has `platform`, `unique_id` = `shdash_<short_id>_<object_id>`, `name`
  (entity name only; HA prefixes the device name), and the topics.
- **Don't use `object_id`**: it was removed in HA 2026.4. Use `default_entity_id` if an entity id must be forced;
  normally let HA derive it.
- Full keys, no abbreviations.
- `entity_category`: sensors and binary sensors may **only** be `diagnostic`; `config` is only for controls.
- Discovery depends on nothing the user can change (0.8.0: fixed device name "Wall panel", fixed topics:
  discovery prefix `homeassistant`, status `homeassistant/status`, base `shdash/<short_id>`); it is published on
  every (re)connect.

## Release vs debug (user principle 2026-09-27)
**Release builds publish only:** `battery_level`, `battery_temperature`, `night_mode`, `screen_brightness`,
`reload` (`EntityCatalog.RELEASE_IDS`). Every other entity below is published **only in debug builds**; release
panels list them as platform-only components (HA deletes them), clear their retained state/attributes and reject
their commands. Don't add entities to the release set without asking the user: each must serve showing the
dashboard, night mode, the battery or keeping kids out.

## Entities (all; debug builds)
| object_id | platform | name | device_class / state_class / unit | category | notes |
|---|---|---|---|---|---|
| battery_level | sensor | Battery | battery / measurement / % | – | |
| charging | binary_sensor | Charging | battery_charging | – | ON/OFF |
| plug_type | sensor | Power source | enum `ac,usb,wireless,dock,none` | diagnostic | |
| battery_temperature | sensor | Battery temperature | temperature / measurement / °C | – | precision 1, deadband 0.5 °C |
| battery_health | sensor | Battery health | enum `good,overheat,dead,over_voltage,failure,cold,unknown` | diagnostic | |
| battery_voltage | sensor | Battery voltage | voltage / measurement / V | diagnostic | precision 2, deadband 0.05 V |
| wifi_rssi | sensor | Wi-Fi signal | signal_strength / measurement / dBm | diagnostic | deadband 3 dB. No SSID (needs location) |
| ip_address | sensor | IP address | – | diagnostic | |
| last_boot | sensor | Last boot | timestamp | diagnostic | ISO 8601; deadband 5 s |
| app_started | sensor | App started | timestamp | diagnostic | |
| free_memory | sensor | Free memory | data_size / measurement / MB | diagnostic | sampled on refresh only |
| free_storage | sensor | Free storage | data_size / measurement / GB | diagnostic | sampled on refresh only |
| app_memory | sensor | App memory | data_size / measurement / MB | diagnostic | own process PSS (`Debug.getPss`), refresh only, precision 0, deadband 5 MB; also logged every 30 min |
| app_version | sensor | App version | – | diagnostic | "0.3.0 (github)" |
| android_version | sensor | Android version | – | diagnostic | attributes `sdk`, `security_patch` |
| device_owner | binary_sensor | Device owner | – | diagnostic | |
| current_url | sensor | Current URL | – | diagnostic | state = URL without query/fragment (≤ 255 chars); attribute `url` = full URL with login codes stripped |
| page_error | binary_sensor | Page error | problem | diagnostic | attributes `kind, description, url, since` |
| viewport | sensor | Dashboard viewport | – | diagnostic | state `<css_width>x<css_height>`; attributes `css_width, css_height, width_px, height_px, density_dpi, device_pixel_ratio`; from the WebView's real layout size |
| last_interaction | sensor | Last interaction | timestamp | – | at most one update per 60 s |
| screen_brightness | number | Screen brightness | % (0–100, slider) | – | state = level of the normal screen (system or fixed). Command sets **Fixed** brightness (saved) |
| reload | button | Reload | – | – | |
| go_home | button | Go to start page | – | – | |
| clear_cache | button | Clear web cache | – | config | |
| load_url | text | Load URL | pattern `^https?://.+` | config | state = current_url; validated by `RemoteUrlPolicy` (start URL host only) |
| restart_app | button | Restart app | – | config | accepted only while an app activity is visible (Android blocks background activity starts); says MQTT goodbye first |
| kiosk_lock | binary_sensor | Kiosk lock | lock | diagnostic | HA lock class: OFF = locked, ON = unlocked; attributes `enabled`, `unlocked_until`. **Never add a command** (user rule: HA must not unlock) |
| last_recovery | sensor | Last recovery | timestamp | diagnostic | attributes `kind` (see `RecoveryKind`) and `detail` (exception class or short reason; never messages or URLs); `None` before the first recovery |
| screen_state | sensor | Screen | enum `active,black` | – | effective mode (`active` while Settings is open) |
| night_mode | switch | Night mode | ON/OFF | – | state = night now. Rejected while Night mode is off in Settings. Overrides the app schedule until its next transition (memory only) |

Switches set `optimistic: false` and use the default `ON`/`OFF` payloads.

**Removed entities** (`Entities.removed`, never reuse these object ids): `ambient_light` (sensor) and
`auto_brightness` (switch), both 0.4.0 only; `screensaver` (switch), `screensaver_timeout` and
`screensaver_brightness` (numbers), `screensaver_mode`, `night_display_mode` and `dashboard` (selects), removed in
0.8.0; `wake` (button), removed in 0.8.1 together with the wake-on-motion blueprint.

Display precision (`suggested_display_precision`, set via `EntityDef.precision`, required for every numeric
sensor): battery 0, temperature 1, voltage 2, Wi-Fi signal 0, free memory 0, free storage 0.
HA applies `suggested_display_precision` only when an entity is (re)added; a discovery update doesn't change
existing entities. Without it, HA uses a per-device-class default (data_size shows 2 decimals). After changing a
precision, tell the user to reload the MQTT integration (or restart HA); a manually set "Display precision" in
the entity settings always wins.

Payload conventions: `ON`/`OFF` for binary sensors, `None` for unknown values, timestamps in UTC ISO 8601,
numbers with `.` as the decimal separator.

## Publishing
- Publish on change (debounced 500 ms), with deadbands on noisy values. Full refresh of everything every
  5 minutes (fixed).
- Battery entities are omitted until the first battery broadcast.

## Security of commands
- Retained command messages are ignored (a stale retained "clear_cache" must not replay on reconnect).
- `load_url`: only explicit `http://` / `https://` URLs, and by default only on hosts already configured
  (start URL, named dashboards). The opt-in "Load URL may open any website" setting lifts the host limit.
  Remote URLs are never written into settings.
- Log command names only, never payloads.

## Removing an entity
Implemented (first used in 0.4.1). Add the object id and platform to `Entities.removed` and delete its
`EntityDef`. `Discovery.build` then lists it as `{"platform": "<platform>"}`, which makes Home Assistant delete
the entity, and `MqttManager` publishes an empty retained message on its state topic so the broker drops the
stale state. The entries stay in the list permanently: harmless for panels that never had the entity, and
panels updated from any older version still get cleaned up.
