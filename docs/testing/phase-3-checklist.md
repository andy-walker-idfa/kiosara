# Phase 3 checklist (0.4.1 / versionCode 7): screen, screensaver, night mode

Install the `githubRelease` build on the tablet. Keep the Home Assistant device page open. "Settings" means
the app's Settings (hold the top-left corner for 3 s).

## Brightness
| # | Step | Expected result |
|---|---|---|
| 1 | Settings → Screen & screensaver. | Status line "Now: brightness N %". Brightness options Follow Android (incl. adaptive brightness, selected) and Fixed. |
| 2 | Choose Fixed, 20 %, Save; then 100 %. | The screen gets clearly darker, then full brightness. HA "Screen brightness" shows 20, then 100. |
| 3 | Follow Android, with adaptive brightness on in Android's display settings. Cover the light sensor. | Android dims the dashboard as usual. |
| 4 | HA: set "Screen brightness" to 40. | The app switches to Fixed 40 %. After an app restart (`adb shell am force-stop`, then open) the brightness is still 40 %. |
| 5 | After updating from 0.4.0: HA device page (reload the MQTT integration if needed). | "Ambient light" and "Automatic brightness" are gone (not just unavailable). |

## Screensaver and touch
| # | Step | Expected result |
|---|---|---|
| 7 | Screensaver after 1 minute, Clock, Save. Don't touch for a minute. | Clock on black with the date; dimmed to the screensaver brightness. HA "Screen" = `clock`, "Screensaver" = on. |
| 8 | Watch the clock for 3 minutes. | The time updates on the minute and the text moves slightly each minute. |
| 9 | Tap a button position of the dashboard while the clock shows. | The screen wakes; the button is **not** pressed. HA "Screensaver" turns off. |
| 10 | Multi-finger touch on the clock screen. | Wakes; nothing reaches the dashboard. |
| 11 | Mode Dim. Wait a minute. | The dashboard stays visible, darker. The first touch wakes without pressing anything. |
| 12 | HA: turn "Screensaver" on (timeout 0). Then touch the screen. | The screensaver shows immediately; a touch ends it and HA shows "Screensaver" off. |
| 13 | HA: press "Wake screen" while the screensaver shows. | It ends; the screensaver timer starts again. |
| 14 | Open Settings while the screensaver is forced from HA. | HA "Screen" = `active` while Settings is open. |

## Night mode
| # | Step | Expected result |
|---|---|---|
| 15 | Night mode on, In the app, from = now + 2 min, until = now + 10 min, Night display Black, stay awake 30 s. Save. | At the start time the screen goes black. HA "Night mode" on, "Screen" = `black`. |
| 16 | Touch the black screen. | Wakes; after 30 s it goes black again (also with screensaver timeout 0). |
| 17 | HA: switch "Night mode" off during the night. | Day until the next start time; at the end time nothing changes; the next start switches to night again. |
| 18 | Night schedule: Home Assistant decides. HA: Night mode on / off. | The screen follows the switch. Settings shows "Active: Home Assistant". |
| 19 | Same, then stop the Mosquitto add-on for 31 minutes. | Settings shows "Active: app schedule, because Home Assistant has not been connected for 30 minutes"; the app's times apply. Start Mosquitto: back to Home Assistant. |
| 20 | Night schedule In the app; change the tablet's time zone or time (Android settings, debug build). | The night state updates immediately. |
| 21 | Set From = Until. | Never night (app schedule). |

## Paused dashboard
| # | Step | Expected result |
|---|---|---|
| 22 | Black for 5 minutes, then wake. | The dashboard is live within a few seconds (entity states update). Log: "Home Assistant connection after wake: CONNECTED". |
| 23 | Black for 60 minutes, then wake. | Same; if HA hadn't reconnected within 10 s, the log shows a reload. Note how long HA took. |
| 24 | Debug build: chrome://inspect → Console `document.visibilityState` while Black. | `hidden`; `visible` after waking. |
| 25 | Debug build: crash the renderer (Settings → Simulate renderer crash) while Black, then wake. | The page loads again normally. |
| 26 | Clock mode: `adb shell dumpsys gfxinfo io.github.andy_walker_idfa.smarthome_dashboard` and `top` vs active. | Far fewer frames and less CPU while the clock shows. |

## Blueprints
| # | Step | Expected result |
|---|---|---|
| 29 | Import wake_on_motion.yaml, create an automation with a motion sensor and the Wake screen button. Trigger motion while the screensaver shows. | The screen wakes. |
| 30 | Import day_night.yaml (sun, −3°), app Night schedule = Home Assistant decides. Run the automation manually after sunset. | Night mode turns on; at sunrise off. After an HA restart the state is re-checked. |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug` (121 tests per flavor)
