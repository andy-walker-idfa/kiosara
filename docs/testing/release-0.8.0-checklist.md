# 0.8.0 / 0.8.1 checklist (versionCodes 13, 14): reduced settings, feature freeze

Install over the existing version (settings and the Home Assistant login must be kept). 0.8.1 also removes the
"Wake screen" button and the wake-on-motion blueprint.

| # | Step | Expected result |
|---|---|---|
| 1 | Open Settings (5 taps, PIN). | Sections: Dashboard (URL, text size), Home Assistant integration, Screen, Settings protection, Kiosk lock; then Reload dashboard, Logs and the version. No Display & behaviour, Additional dashboards or Advanced. |
| 2 | Check the kept values. | HA URL, text size, MQTT on, broker address (with `:port` only if it isn't 1883), user name, brightness, night times, PIN and kiosk lock are as before. MQTT reconnects without a new password. |
| 3 | Battery optimization. | The notice is not shown while the app is already allowed to run unrestricted. |
| 4 | Change the broker address to `<address>:1883`, Test connection, Save. | Test succeeds; after Save the field shows the address without `:1883`; MQTT stays connected. |
| 5 | Change the text size (e.g. 110), Save. | The dashboard text changes size at once. |
| 6 | Night mode: set "from" 2 minutes ahead, open another dashboard view, wait. | At the start time the screen goes black; a touch shows the **start page** (not the other view) for a minute, then black again. |
| 7 | HA: Night mode switch off, then on. | Off: the dashboard is back at once; on: black again. |
| 8 | HA device page (0.8.1). | Only Battery, Battery temperature, Night mode, Screen brightness and Reload; "Wake screen" is gone (not just unavailable). The device is still called "Wall panel" (or the name you gave it in HA). Delete any automation that used the wake-on-motion blueprint, and the blueprint itself. |
| 9 | By day, leave the panel untouched for 30 minutes. | The dashboard stays on (no screensaver). |
| 10 | Update while the kiosk lock is on (this install). | Back, locked and with the screen on within a few seconds (Phase 4b step 22). |

Automated:
- `.\gradlew.bat ktlintCheck testGithubDebugUnitTest testStoreDebugUnitTest lintGithubDebug lintStoreDebug`
