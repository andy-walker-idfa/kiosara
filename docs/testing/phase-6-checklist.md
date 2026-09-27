# Phase 6 checklist (0.7.0 / versionCode 10): log files, App memory, alerts

| # | Step | Expected result |
|---|---|---|
| 1 | Settings (5 taps, PIN) → Logs → View logs. | The newest lines at the top, starting with "=== Start 0.7.0 …"; timestamps with the UTC offset. |
| 2 | Filters Warnings / Errors, Refresh. | Only W/E (or only E) lines; continuation lines of a crash stay with their entry. |
| 3 | With the kiosk lock on: open the viewer, then Back. | The lock stays on; Back returns to Settings. |
| 4 | Leave the viewer untouched for 10 minutes. | Returns straight to the dashboard (not to Settings). |
| 5 | Reboot the tablet (or update the app), then open the viewer. | A new start marker line with the version. |
| 6 | Look for passwords, tokens or login codes in the log. | None; addresses with query strings end in "?…". |
| 7 | After 30 minutes with MQTT on: the viewer. | A "Memory: app … MB, free … MB" line every 30 minutes. |
| 8 | HA device page (release build, 0.7.2+). | Only Battery, Battery temperature, Night mode, Screen brightness, Wake screen and Reload; the other entities are gone (not just unavailable). |
| 10 | Stop the MQTT broker for an hour, then check the log. | About 30 retry lines per hour (the wait grows to 2 minutes), not thousands; identical lines are summarised ("repeated N times"). |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug` (169 tests per flavor)
