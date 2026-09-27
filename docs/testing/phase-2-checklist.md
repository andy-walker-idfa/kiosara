# Phase 2 manual test checklist (0.3.0, versionCode 3)

The tablet runs the **githubRelease** build. Setup steps are in `docs/home-assistant-setup.md` §6–7.

## Connection
| # | Step | Expected result |
|---|---|---|
| 1 | Settings → Home Assistant integration (MQTT): enter the MQTT username and password, Test connection, Save. | Status line: **MQTT: connected** within a few seconds. |
| 2 | HA → Settings → Devices & Services → MQTT. | Device **"Wall panel"** with 24 entities; manufacturer/model = LENOVO TB310FU; software "0.3.0 (github)". |
| 3 | Check the sensor values against the tablet. | Battery %, charging, temperature, IP, Wi-Fi signal and Android version look right. |
| 4 | Plug and unplug the charger. | "Charging" and "Power source" follow within a few seconds. |
| 5 | Touch the dashboard. | "Last interaction" updates (at most once a minute). |
| 6 | In the app, Settings → Additional dashboards → add one, then look at the "Dashboard" select in HA. | The new option appears. |

## Commands from HA
| # | Step | Expected result |
|---|---|---|
| 7 | Move "Screen brightness" to 10 %, then 100 %. | The tablet dims and brightens immediately. |
| 8 | Press **Reload**, **Go to start page**, **Clear web cache**. | Each acts on the dashboard; you stay logged in. |
| 9 | Select a dashboard in "Dashboard". | The tablet shows it; the select reflects it. |
| 10 | "Load URL" with a URL on your HA host. | It opens. |
| 11 | "Load URL" with `https://example.com`. | Nothing happens (rejected), unless "Load URL may open any website" is on. |
| 12 | Developer tools → MQTT: publish **retained** `PRESS` to `shdash/<id>/set/reload`, then restart the app. | No reload on reconnect (retained commands are ignored). Clear the retained message afterwards (publish an empty retained payload). |

## Robustness
| # | Step | Expected result |
|---|---|---|
| 13 | Restart Home Assistant. | Entities come back without an app restart (re-published on HA's birth message). |
| 14 | Restart the Mosquitto add-on. | Entities go unavailable, then come back within ~2 minutes. |
| 15 | Turn Wi-Fi off for 2 minutes. | Entities become unavailable (last will); after Wi-Fi returns they come back. |
| 16 | Reboot the tablet. | Dashboard appears; MQTT reconnects; entities available again. |
| 17 | Settings → switch MQTT off, Save. | The notification disappears; entities go unavailable immediately. Switch it on again. |
| 18 | Settings → Battery optimization → Change → allow. | The row shows "Not optimized". |

## Charging blueprint
| # | Step | Expected result |
|---|---|---|
| 19 | Create the Toggle helper, import the blueprint, create the automation with your plug. | No configuration errors. |
| 20 | Watch over a day (or lower "Stop charging at or above" temporarily). | Plug switches off at the high level and on at the low level, never faster than every 2 minutes. |
| 21 | Temporarily set "Maximum battery temperature" below the current temperature. | Plug off, overheat lock on, notification. Restore the setting: lock clears once below max − 5 °C. |
| 22 | Unplug the tablet's Wi-Fi for longer than the fail-safe time (or set it to 5 min). | Plug switches on, notification "Wall panel offline". |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug` (70 tests per flavor)
- GitHub Actions CI green.

## 0.3.1 settings layout
| # | Step | Expected result |
|---|---|---|
| 23 | Open Settings. | Sections in order: Dashboard (Required), Home Assistant integration, Display & behaviour, Additional dashboards; "Show advanced settings"; Actions and version at the bottom. Every field has a helper text. |
| 24 | Clear the Home Assistant URL, tap Save. | Save is refused, the field is highlighted and the screen scrolls to it. |
| 25 | Change any value, tap Close (or Back). | "Discard changes?" dialog; "Keep editing" keeps the edits, "Discard" closes. |
| 26 | Test connection with a wrong password, then with the right one. | "Connection failed: The broker rejected the login …", then "Connection successful". Nothing is saved until Save. |
| 27 | Test connection with a wrong broker address. | "Host … not found" or "Connection refused …" / "No answer … (timeout)". |
| 28 | Switch the tablet to Czech. | All settings labels and helper texts are Czech; failure messages stay English. |
| 29 | HA device page. | Free memory / storage, battery and Wi-Fi signal show no decimals; temperature one decimal. |
| 30 | Settings status area and HA "Dashboard viewport" (0.3.2). | "Dashboard viewport: 1072 × 640 CSS px · 200 dpi (device pixel ratio 1.25)"; HA state `1072x640`. |
