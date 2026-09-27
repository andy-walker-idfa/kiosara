# Phase 4a checklist (0.5.0 / versionCode 8): recovery and settings PIN

Install the `githubRelease` build. Keep the HA device page open ("Last recovery", "Restart app").
adb commands use the package `io.github.andy_walker_idfa.smarthome_dashboard` ($PKG).

## Settings gesture and PIN
| # | Step | Expected result |
|---|---|---|
| 1 | Tap the top-right corner 5 times quickly. | Settings open (no PIN yet). "Settings protection" shows the notice recommending a PIN. |
| 2 | Hold the top-left corner for 3 s. | Nothing (the old gesture is gone). |
| 3 | Settings protection → Set PIN (4–8 digits, twice), then close Settings. | "PIN saved." The 5 taps now show the PIN keypad. |
| 4 | Enter a wrong PIN 5 times. | "Wrong PIN", then "Too many attempts. Try again in 30 s." After 30 s the right PIN works. |
| 5 | Leave the PIN keypad alone for 60 s. | It closes. |
| 6 | Leave Settings open without touching it for 10 min. | Settings close (unsaved changes are discarded). |
| 7 | `adb shell am broadcast -a $PKG.action.RESET_PIN -n $PKG/.security.PinResetReceiver` | Log "PIN reset requested via adb"; the 5 taps open Settings without a PIN. |
| 8 | Change or remove the PIN in Settings. | Works without the old PIN (you are already in). |

## Watchdog and restarts
| # | Step | Expected result |
|---|---|---|
| 9 | Dashboard visible: `adb shell am crash $PKG` | The dashboard is back within seconds, no "keeps stopping" dialog. HA "Last recovery" = app_crash. MQTT reconnects. |
| 10 | Repeat step 9 six times within a few minutes. | Restarts 1–5 work; the 6th shows Android's normal crash handling. MQTT still recovers (the service restarts itself). |
| 11 | Open another app (e.g. Android settings), then `am crash $PKG`. | No restart while hidden; pressing Home brings the dashboard back. |
| 12 | HA: press "Restart app" (dashboard visible, and again under Black). | Entities briefly unavailable, dashboard reloads, MQTT back; "Last recovery" = remote_restart. |
| 13 | HA: press "Restart app" while another app is in front. | Nothing happens (rejected; see logcat "Rejected command restart_app"). |
| 14 | Debug build: Settings → Simulate page hang. | Within about 70 s: log "Page renderer hung", the page reloads; "Last recovery" = page_unresponsive (debug device in HA). |
| 15 | Debug build: Settings → Simulate app crash. | The app restarts itself (as step 9). |
| 16 | Stop Home Assistant for 12 minutes (network stays up). | The dashboard is reloaded after two failed checks; "Last recovery" = frontend_disconnected. |
| 17 | Reboot the tablet. | Dashboard comes up; "Last recovery" is unchanged (a reboot is not a recovery). |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug` (149 tests per flavor)
